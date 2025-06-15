package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Utils.GpuDelegateHelper
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SeedPredictor : ISeedPrecitor {
    private lateinit var tflite: Interpreter
    private val assetManager: AssetManager
    private val roi = IntArray(4)
    constructor(context: Context) {
        assetManager = context.assets
        val options = Interpreter.Options().apply{
            if(GpuDelegateHelper().isGpuDelegateAvailable){
                addDelegate(GpuDelegateHelper().createGpuDelegate())
            }
            useNNAPI = true
            setNumThreads(4)
        }
        tflite = Interpreter(loadModelFile("breast_mri_model.tflite"), options)
        val inputTensor = tflite.getInputTensor(0)
        Log.d("ModelInput", "Shape: ${inputTensor.shape().contentToString()}")
    }
    override fun predictSeed(
        slice_bitmap: Bitmap,
        roi: IntArray,
    ): Array<FloatArray> {
        val roiBitmap = extractRoiFromBitmap(slice_bitmap, roi)
        val resizedRoiBitmap = Bitmap.createScaledBitmap(roiBitmap, 256, 256, true) // resizing
        val input = convertBitmapToByteBuffer(resizedRoiBitmap)
        val output = Array(1) { FloatArray(2)}
        tflite.run(input, output)
        Log.d("SeedPredictor", "Predicted seed point: (${output[0][0]}, ${output[0][1]})")
        return output
    }
    private fun extractRoiFromBitmap(bitmap: Bitmap, roi: IntArray): Bitmap {
        var xMin = roi[0]
        var xMax = roi[1]
        var yMin = roi[2]
        var yMax = roi[3]

        // Clamp coordinates to bitmap bounds
        xMin = xMin.coerceIn(0, bitmap.width - 1)
        xMax = xMax.coerceIn(xMin + 1, bitmap.width)
        yMin = yMin.coerceIn(0, bitmap.height - 1)
        yMax = yMax.coerceIn(yMin + 1, bitmap.height)

        val width = (xMax - xMin).coerceAtLeast(1)
        val height = (yMax - yMin).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, xMin, yMin, width, height)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(256 * 256 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(256 * 256)
        bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        for (pixel in pixels) {
            val grayscale = (pixel and 0xFF).toFloat() / 255.0f
            inputBuffer.putFloat(grayscale)
        }
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