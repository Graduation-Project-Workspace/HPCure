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
            //setUseDynamicShapes(true) //TODO
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
        return output
    }
    private fun extractRoiFromBitmap(bitmap: Bitmap, roi: IntArray): Bitmap {
        val x_min = roi[0]
        val y_min = roi[2]
        val x_max = roi[1]
        val y_max = roi[3]
        return Bitmap.createBitmap(bitmap, x_min, y_min, x_max - x_min, y_max - y_min)
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