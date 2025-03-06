package com.example.demoapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MRIPredictor(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val options = Interpreter.Options()
            // Use GPU if available
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                options.addDelegate(GpuDelegate())
            }
            options.setNumThreads(4)
            interpreter = Interpreter(loadModelFile(), options)
        } catch (e: Exception) {
            Log.e("MRIPredictor", "Error loading model", e)
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val modelPath = "breast_mri_model.tflite"
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun predictSeedLocationAsync(bitmap: Bitmap): Result<Pair<Float, Float>> =
        withContext(Dispatchers.Default) {
            try {
                val prediction = predictSeedLocation(bitmap)
                Result.success(prediction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun predictSeedLocation(bitmap: Bitmap): Pair<Float, Float> {
        val preprocessedImage = preprocessImage(bitmap)
        val outputArray = Array(1) { FloatArray(2) }
        interpreter?.run(preprocessedImage, outputArray)
        return Pair(outputArray[0][0], outputArray[0][1])
    }

    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<Float>>> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val input = Array(256) { Array(256) { Array(1) { 0f } } }

        for (x in 0 until 256) {
            for (y in 0 until 256) {
                val pixel = resizedBitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
                input[x][y][0] = gray / 255f
            }
        }
        return input
    }

    fun close() {
        interpreter?.close()
    }
}