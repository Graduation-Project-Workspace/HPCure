package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.domain.interfaces.tumor.IRoiPredictor
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ParallelRoiPredictor : IRoiPredictor {
    private lateinit var options: Interpreter.Options
    private val inputSize = 512 // Model expects 512x512 input
    private val outputShape = intArrayOf(1, 5, 5376) // Based on Python output shape
    private val modelName = "breast_roi_model.tflite"
    // Constants

    const val MAX_PARALLEL_REQUESTS = 4  // Optimal for most devices
    const val MODEL_INPUT_SIZE = 512

    // Shared model state (loaded once)
    private val modelFile by lazy { loadModelFile() }
    private val modelLock = Any()

    // Thread-safe interpreter pool
    private val interpreterPool = ThreadLocal<Interpreter>()
    private lateinit var assetManager: AssetManager

    fun initialize(context: Context) {
        assetManager = context.assets
    }

    override fun predictRoi(
        mriSequence: MRISequence,
        useGpuDelegate: Boolean,
        useAndroidNN: Boolean,
        numThreads: Int
    ): List<ROI> = runBlocking {
        // 1. Configure with safe defaults
        val options = createSafeInterpreterOptions(useGpuDelegate, useAndroidNN, numThreads)

        // 2. Use semaphore to limit concurrent work
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)

        // 3. Process with memory awareness
        val jobs = mriSequence.images.map { sliceBitmap ->
            async(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    processSingleSlice(sliceBitmap, options)
                } finally {
                    semaphore.release()
                }
            }
        }

        return@runBlocking jobs.awaitAll()
    }

    private fun createSafeInterpreterOptions(
        useGpuDelegate: Boolean,
        useAndroidNN: Boolean,
        numThreads: Int
    ): Interpreter.Options {
        return Interpreter.Options().apply {
            // GPU with fallback
            if (useGpuDelegate && GpuDelegateHelper().isGpuDelegateAvailable) {
                try {
                    addDelegate(GpuDelegateHelper().createGpuDelegate())
                } catch (e: Exception) {
                    Log.w("RoiPredictor", "GPU delegate failed, using CPU", e)
                }
            }

            // Thread management
            setNumThreads(numThreads.coerceIn(1, 4))
            setUseNNAPI(useAndroidNN)
            setUseXNNPACK(false)
        }
    }

    private suspend fun processSingleSlice(
        sliceBitmap: Bitmap,
        options: Interpreter.Options
    ): ROI = withContext(Dispatchers.IO) {
        // 1. Load model safely
        val interpreter = loadModel(options)

        try {
            // 2. Process with memory-efficient bitmap handling
            val configBitmap = ensureBitmapConfig(sliceBitmap)
            val resizedBitmap = configBitmap.scale(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

            return@withContext try {
                predictRoi(resizedBitmap, interpreter)
            } finally {
                if (resizedBitmap != configBitmap) resizedBitmap.recycle()
                if (configBitmap != sliceBitmap) configBitmap.recycle()
            }
        } finally {
            interpreter.close()
            interpreterPool.remove()
        }
    }

    private fun ensureBitmapConfig(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                if (it != bitmap) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun loadModel(options: Interpreter.Options): Interpreter {
        return interpreterPool.get() ?: synchronized(modelLock) {
            interpreterPool.get() ?: Interpreter(modelFile, options).also {
                interpreterPool.set(it)
            }
        }
    }
    override fun predictRoi(
        sliceBitmap: Bitmap,
        tflite : Interpreter
    ): ROI {
        // Resize to model input size (512x512)
        val resizedBitmap = sliceBitmap.scale(inputSize, inputSize)

        // Convert bitmap to normalized float array with CHW format (3, 512, 512)
        val inputBuffer = convertBitmapToFloatBuffer(resizedBitmap)

        // Prepare output array based on model's output shape (1, 5, 5376)
        val output = Array(1) { Array(5) { FloatArray(5376) } }

        // Run inference
        tflite.run(inputBuffer, output)

        // Log output shape for debugging
        Log.d("ModelDetails", "Output shape after inference: ${output[0].size} x ${output[0][0].size}")

        // Find the box with highest confidence
        val confidences = output[0][4] // Confidence scores are at index 4
        var maxConfIdx = 0
        var maxConf = 0f

        for (i in confidences.indices) {
            if (confidences[i] > maxConf) {
                maxConf = confidences[i]
                maxConfIdx = i
            }
        }

        // Extract ROI coordinates
        val xCenter = output[0][0][maxConfIdx]
        val yCenter = output[0][1][maxConfIdx]
        val width = output[0][2][maxConfIdx]
        val height = output[0][3][maxConfIdx]

        Log.d("RoiPredictor", "Predicted ROI: x=$xCenter, y=$yCenter, w=$width, h=$height, conf=$maxConf")

        // Return as array of FloatArray (you might want to adjust this based on your needs)
        // convert to normalized coordinates

        val imgW = sliceBitmap.width
        val imgH = sliceBitmap.height

        val boxX = (xCenter).toInt()
        val boxY = (yCenter).toInt()
        val boxW = (width).toInt()
        val boxH = (height).toInt()

        val x1 = (boxX - boxW / 2).coerceAtLeast(0)
        val y1 = (boxY - boxH / 2).coerceAtLeast(0)
        val x2 = (boxX + boxW / 2).coerceAtMost(imgW - 1)
        val y2 = (boxY + boxH / 2).coerceAtMost(imgH - 1)

        return ROI(
            xMin = x1,
            xMax = x2,
            yMin = y1,
            yMax = y2,
            score = maxConf
        )
    }

    private fun loadModel() : Interpreter {
        try {
            if (interpreterPool.get() != null) {
                return interpreterPool.get()!!
            }

            val interpreter = Interpreter(modelFile!!, options)
            interpreterPool.set(interpreter)
            return interpreter
        } catch (e: Exception) {
            Log.e("RoiPredictor", "Error loading model file", e)
            throw e
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = assetManager.openFd(modelName)
        val inputStream = assetFileDescriptor.createInputStream()
        val modelBuffer = ByteArray(assetFileDescriptor.length.toInt())
        inputStream.read(modelBuffer)
        return ByteBuffer.allocateDirect(modelBuffer.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBuffer)
        }
    }

    private fun convertBitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * 512 * 512 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(512 * 512)
        bitmap.getPixels(pixels, 0, 512, 0, 0, 512, 512)

        val rChannel = FloatArray(512 * 512)
        val gChannel = FloatArray(512 * 512)
        val bChannel = FloatArray(512 * 512)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            rChannel[i] = Color.red(pixel) / 255.0f
            gChannel[i] = Color.green(pixel) / 255.0f
            bChannel[i] = Color.blue(pixel) / 255.0f
        }

        // Fill buffer in CHW order
        rChannel.forEach { inputBuffer.putFloat(it) }
        gChannel.forEach { inputBuffer.putFloat(it) }
        bChannel.forEach { inputBuffer.putFloat(it) }

        inputBuffer.rewind()
        return inputBuffer
    }

    fun close() {
        interpreterPool.get()?.close()
        interpreterPool.remove()
    }
}