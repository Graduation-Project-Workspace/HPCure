package com.example.demoapp.Core

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import com.example.demoapp.Core.Interfaces.IRoiPredictor
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

class ParallelRoiPredictor : IRoiPredictor {
    private var modelFile : ByteBuffer? = null
    private lateinit var options: Interpreter.Options
    private val interpreterPool = ThreadLocal<Interpreter>()
    private val assetManager: AssetManager
    private val inputSize = 512 // Model expects 512x512 input
    private val outputShape = intArrayOf(1, 5, 5376) // Based on Python output shape
    private val modelName = "breast_roi_model.tflite"

    constructor(context: Context) {
        assetManager = context.assets
    }

    override fun predictRoi(mriSequence: MRISequence,
                            useGpuDelegate : Boolean,
                            useAndroidNN : Boolean,
                            numThreads : Int
    ) : List<ROI> = runBlocking {
        modelFile = loadModelFile()
        options = Interpreter.Options().apply {
            if(GpuDelegateHelper().isGpuDelegateAvailable && useGpuDelegate) {
                addDelegate(GpuDelegateHelper().createGpuDelegate())
            }
            useNNAPI = useAndroidNN
            setNumThreads(numThreads)
        }
        val jobs = mriSequence.images.map { sliceBitmap ->
            async(Dispatchers.Default) {
                val interpreter = loadModel()
                val roi = predictRoi(sliceBitmap, interpreter)
                interpreter.close()
                interpreterPool.set(null)
                return@async roi
            }
        }
        val jobsResults = jobs.awaitAll()
        modelFile = null

        return@runBlocking jobsResults
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

        val boxX = (xCenter * imgW).toInt()
        val boxY = (yCenter * imgH).toInt()
        val boxW = (width * imgW).toInt()
        val boxH = (height * imgH).toInt()

        val x1 = (boxX - boxW / 2).coerceAtLeast(0)
        val y1 = (boxY - boxH / 2).coerceAtLeast(0)
        val x2 = (boxX + boxW / 2).coerceAtMost(imgW - 1)
        val y2 = (boxY + boxH / 2).coerceAtMost(imgH - 1)

        return ROI(
            xMin = x1,
            xMax = x2,
            yMin = y1,
            yMax = y2
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
}