package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import com.example.demoapp.Utils.GpuDelegateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ParallelSeedPredictor : ISeedPrecitor {
    private val assetManager: AssetManager
    private val inputSize = 512 // Model expects 512x512 input
    private var modelFile : ByteBuffer? = null
    private val interpreterPool = ThreadLocal<Interpreter>()
    private lateinit var options: Interpreter.Options
    private val modelName = "seed-pose.tflite"

    constructor(context: Context) {
        assetManager = context.assets
    }

    override fun predictSeed(
        mriSeq: MRISequence,
        roi: List<ROI>,
        useGpuDelegate : Boolean,
        useAndroidNN : Boolean,
        numThreads : Int
    ): Array<Pair<Int, Int>> = runBlocking {
        options = Interpreter.Options().apply {
            if (GpuDelegateHelper().isGpuDelegateAvailable && useGpuDelegate) {
                addDelegate(GpuDelegateHelper().createGpuDelegate())
            }
            useNNAPI = useAndroidNN
            setNumThreads(numThreads)
        }

        modelFile = loadModelFile()

        val jobs = mriSeq.images.mapIndexed { index, sliceBitmap ->
            async(Dispatchers.Default) {
                val interpreter = loadModel()
                val seed = predictSeed(sliceBitmap, roi[index], interpreter)
                interpreter.close()
                interpreterPool.set(null)
                return@async seed
            }
        }
        val jobsResults = jobs.awaitAll()
        modelFile = null

        return@runBlocking jobsResults.toTypedArray()
    }

    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: ROI,
        tflite : Interpreter
    ): Pair<Int, Int> {
        // Crop the bitmap to the ROI
        var slice_bitmap = Bitmap.createBitmap(
            slice_bitmap,
            roi.xMin,
            roi.yMin,
            roi.xMax - roi.xMin,
            roi.yMax - roi.yMin
        )

        // Resize to model input size (512x512)
        slice_bitmap = slice_bitmap.scale(inputSize, inputSize)

        // Convert bitmap to normalized float array with CHW format (3, 512, 512)
        val inputBuffer = convertBitmapToFloatBuffer(slice_bitmap)

        // Prepare output array based on model's output shape (1, 8, 5376)
        val output = Array(1) { Array(8) { FloatArray(5376) } }

        // Run inference
        tflite.run(inputBuffer, output)

        val probabilities = output[0][4] // Adjust this index if probability is in a different channel

        // Find the index with maximum probability
        var maxProbIndex = 0
        var maxProb = 0f
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxProbIndex = i
            }
        }

        // Get the corresponding x,y coordinates
        val seedX = output[0][5][maxProbIndex]
        val seedY = output[0][6][maxProbIndex]

        Log.d("SeedPredictor", "Predicted seed point: ($seedX, $seedY) with probability $maxProb")

        // Rescale coordinates to original image size
        val normalizedX = seedX / inputSize
        val normalizedY = seedY / inputSize
        val rescaledSeedX = roi.xMin + (roi.xMax - roi.xMin) * normalizedX
        val rescaledSeedY = roi.yMin + (roi.yMax - roi.yMin) * normalizedY
        Log.d("SeedPredictor", "Rescaled seed point: ($rescaledSeedX, $rescaledSeedY)")

        // Return just the x,y coordinates
        return Pair(rescaledSeedX.toInt(), rescaledSeedY.toInt())
    }

    private fun convertBitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val rChannel = FloatArray(inputSize * inputSize)
        val gChannel = FloatArray(inputSize * inputSize)
        val bChannel = FloatArray(inputSize * inputSize)

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

    private fun loadModel() : Interpreter {
        try {
            if (interpreterPool.get() != null) {
                return interpreterPool.get()!!
            }

            val interpreter = Interpreter(modelFile!!, options)
            interpreterPool.set(interpreter)
            return interpreter
        } catch (e: Exception) {
            Log.e("SeedPredictor", "Error loading model", e)
            throw RuntimeException("Failed to load TensorFlow Lite model", e)
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
}