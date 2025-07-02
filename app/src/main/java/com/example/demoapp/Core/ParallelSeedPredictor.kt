package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels

class ParallelSeedPredictor : ISeedPredictor {
    private val inputSize = 512 // Model expects 512x512 input
    private lateinit var options: Interpreter.Options
    private val modelName = "seed-pose.tflite"
    private companion object {
        const val MAX_PARALLEL_REQUESTS = 4
        const val MODEL_INPUT_SIZE = 512
        const val OUTPUT_DIMENSIONS = 5376
    }

    private val assetManager: AssetManager
    private val modelFile by lazy { loadModelFile() }
    private val modelLock = Any()
    private val interpreterPool = ThreadLocal<Interpreter>()

    constructor(context: Context) {
        assetManager = context.assets
    }

    override fun predictSeed(
        mriSeq: MRISequence,
        roi: List<ROI>,
        useGpuDelegate: Boolean,
        useAndroidNN: Boolean,
        numThreads: Int
    ): Array<Pair<Int, Int>> = runBlocking {
        validateInput(mriSeq, roi)

        val options = createInterpreterOptions(useGpuDelegate, useAndroidNN, numThreads)
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)

        val jobs = mriSeq.images.mapIndexed { index, sliceBitmap ->
            async(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    processSlice(sliceBitmap, roi[index], options)
                } finally {
                    semaphore.release()
                }
            }
        }

        return@runBlocking jobs.awaitAll().toTypedArray()
    }

    private fun validateInput(mriSeq: MRISequence, roi: List<ROI>) {
        require(mriSeq.images.size == roi.size) {
            "MRI sequence and ROI lists must be the same size"
        }
    }

    private fun createInterpreterOptions(
        useGpuDelegate: Boolean,
        useAndroidNN: Boolean,
        numThreads: Int
    ): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(numThreads.coerceIn(1, 4))
            useNNAPI = useAndroidNN

            if (useGpuDelegate && GpuDelegateHelper().isGpuDelegateAvailable) {
                try {
                    addDelegate(GpuDelegateHelper().createGpuDelegate())
                } catch (e: Exception) {
                    Log.w("SeedPredictor", "GPU delegate failed, falling back to CPU", e)
                }
            }
        }
    }

    private suspend fun processSlice(
        sliceBitmap: Bitmap,
        roi: ROI,
        options: Interpreter.Options
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val interpreter = loadModel(options)
        try {
            val croppedBitmap = try {
                Bitmap.createBitmap(
                    sliceBitmap,
                    roi.xMin.coerceAtLeast(0),
                    roi.yMin.coerceAtLeast(0),
                    (roi.xMax - roi.xMin).coerceAtMost(sliceBitmap.width),
                    (roi.yMax - roi.yMin).coerceAtMost(sliceBitmap.height)
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid ROI coordinates", e)
            }

            val processedBitmap = try {
                val configBitmap = ensureBitmapConfig(croppedBitmap)
                val resized = configBitmap.scale(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
                if (configBitmap != croppedBitmap) configBitmap.recycle()
                resized
            } finally {
                if (croppedBitmap != sliceBitmap) croppedBitmap.recycle()
            }

            return@withContext try {
                predictSeedCoordinates(processedBitmap, roi, interpreter)
            } finally {
                processedBitmap.recycle()
            }
        } finally {
            interpreter.close()
            interpreterPool.remove()
        }
    }

    private fun ensureBitmapConfig(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun predictSeedCoordinates(
        bitmap: Bitmap,
        roi: ROI,
        interpreter: Interpreter
    ): Pair<Int, Int> {
        val inputBuffer = convertBitmapToFloatBuffer(bitmap)
        val output = Array(1) { Array(8) { FloatArray(OUTPUT_DIMENSIONS) } }

        interpreter.run(inputBuffer, output)

        val probabilities = output[0][4]
        val (maxProbIndex, maxProb) = findMaxProbability(probabilities)

        val seedX = output[0][5][maxProbIndex]
        val seedY = output[0][6][maxProbIndex]
        Log.d("SeedPredictor", "Raw prediction: ($seedX, $seedY) prob: $maxProb")

        return rescaleCoordinates(seedX, seedY, roi)
    }

    private fun findMaxProbability(probabilities: FloatArray): Pair<Int, Float> {
        var maxIndex = 0
        var maxValue = 0f
        probabilities.forEachIndexed { index, value ->
            if (value > maxValue) {
                maxValue = value
                maxIndex = index
            }
        }
        return Pair(maxIndex, maxValue)
    }

    private fun rescaleCoordinates(
        x: Float,
        y: Float,
        roi: ROI
    ): Pair<Int, Int> {
        val normalizedX = x / MODEL_INPUT_SIZE
        val normalizedY = y / MODEL_INPUT_SIZE

        return Pair(
            (roi.xMin + (roi.xMax - roi.xMin) * normalizedX).toInt(),
            (roi.yMin + (roi.yMax - roi.yMin) * normalizedY).toInt()
        )
    }

    private fun convertBitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        // Process channels directly into buffer
        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    private fun loadModel(options: Interpreter.Options): Interpreter {
        return interpreterPool.get() ?: synchronized(modelLock) {
            interpreterPool.get() ?: Interpreter(modelFile, options).also {
                interpreterPool.set(it)
            }
        }
    }

    private fun loadModelFile(): ByteBuffer {
        return assetManager.openFd(modelName).use { fd ->
            ByteBuffer.allocateDirect(fd.length.toInt()).also { buffer ->
                buffer.order(ByteOrder.nativeOrder())
                fd.createInputStream().use { stream ->
                    val channel = Channels.newChannel(stream)
                    channel.read(buffer)
                    buffer.rewind()
                }
            }
        }
    }
}