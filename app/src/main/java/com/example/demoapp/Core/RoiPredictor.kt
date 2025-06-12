package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.demoapp.Core.Interfaces.IRoiPredictor
import com.example.demoapp.Utils.GpuDelegateHelper
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale

class RoiPredictor : IRoiPredictor {
    private lateinit var tflite: Interpreter
    private val assetManager: AssetManager
      constructor(context: Context) {
        assetManager = context.assets
        val options = Interpreter.Options().apply{
            if(GpuDelegateHelper().isGpuDelegateAvailable){
                addDelegate(GpuDelegateHelper().createGpuDelegate())
            }
            useNNAPI = true
            setNumThreads(4)
        }
        tflite = Interpreter(loadModelFile("breast_roi_model.tflite"), options)
        val inputTensor = tflite.getInputTensor(0)
        Log.d("ModelInput", "Shape: ${inputTensor.shape().contentToString()}")
    }
    override fun predictRoi(
        sliceBitmap: Bitmap,
    ): Array<FloatArray> {
        val resizedBitmap = sliceBitmap.scale(128, 128)
        val input = convertBitmapToByteBuffer(resizedBitmap)
        val output = Array(1) { FloatArray(4) } // Assuming the model outputs 4 values for ROI
        tflite.run(input, output)
        Log.d("RoiPredictor", "Predicted ROI: ${output[0].contentToString()}")
        return output
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
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Allocate buffer for 128x128 grayscale image (4 bytes per float)
        val inputBuffer = ByteBuffer.allocateDirect(128 * 128 * 1 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Get all pixels at once for better performance
        val pixels = IntArray(128 * 128)
        bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)

        // Convert each pixel to grayscale and normalize to [0,1]
        for (pixel in pixels) {
            // Proper RGB to grayscale conversion (NTSC formula)
            val r = Color.red(pixel) / 255.0f
            val g = Color.green(pixel) / 255.0f
            val b = Color.blue(pixel) / 255.0f
            val grayscale = 0.299f * r + 0.587f * g + 0.114f * b

            inputBuffer.putFloat(grayscale)
        }

        // Rewind buffer before returning (important!)
        inputBuffer.rewind()
        return inputBuffer
    }
}