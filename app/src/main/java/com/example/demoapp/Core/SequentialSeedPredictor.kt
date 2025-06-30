package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.demoapp.Utils.GpuDelegateHelper
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SequentialSeedPredictor : ISeedPredictor {
    private lateinit var tflite: Interpreter
    private val assetManager: AssetManager
    private val inputSize = 512 // Model expects 512x512 input

    constructor(context: Context) {
        assetManager = context.assets
    }

    override fun predictSeed(
        mriSeq: MRISequence,
        roi: List<ROI>,
        useGpuDelegate: Boolean,
        useAndroidNN: Boolean,
        numThreads: Int
    ): Array<Pair<Int, Int>> {
        val options = Interpreter.Options().apply {
            if (GpuDelegateHelper().isGpuDelegateAvailable && useGpuDelegate) {
                addDelegate(GpuDelegateHelper().createGpuDelegate())
            }
            useNNAPI = useAndroidNN
            setNumThreads(numThreads)
        }

        try {
            tflite = Interpreter(loadModelFile("seed-pose.tflite"), options)
        } catch (e: Exception) {
            Log.e("SeedPredictor", "Error loading model file", e)
            throw RuntimeException("Failed to load TensorFlow Lite model", e)
        }


        val seedPoints = mutableListOf<Pair<Int, Int>>()

        for ((index, sliceBitmap) in mriSeq.images.withIndex()) {
            try {
                val seedPoint = predictSeed(sliceBitmap, roi[index])
                seedPoints.add(seedPoint)
            } catch (e: Exception) {
                Log.e("SeedPredictor", "Error predicting seed for slice $index", e)
                // Handle error, e.g., add a default value or skip
                seedPoints.add(Pair(0, 0)) // Default value if prediction fails
            }
        }
        tflite.close()

        return seedPoints.toTypedArray()
    }

    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: ROI
    ): Pair<Int, Int> {
        // Crop the bitmap to the ROI
        val croppedBitmap = Bitmap.createBitmap(
            slice_bitmap,
            roi.xMin,
            roi.yMin,
            roi.xMax - roi.xMin,
            roi.yMax - roi.yMin
        )

        // Resize to model input size (512x512)
        val resizedBitmap = croppedBitmap.scale(inputSize, inputSize)

        // Convert bitmap to normalized float array with CHW format (3, 512, 512)
        val inputBuffer = convertBitmapToFloatBuffer(resizedBitmap)

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

    private fun loadModelFile(modelName: String): ByteBuffer {
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