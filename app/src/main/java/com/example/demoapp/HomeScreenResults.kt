package com.example.demoapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HomeScreenResults : AppCompatActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var imageView: ImageView
    private lateinit var predictionText: TextView
    private val ROI = IntArray(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.results_screen)

        // Find UI elements
        imageView = findViewById(R.id.mriImageView)
        predictionText = findViewById(R.id.predictionText)

        // ROI values are passed to me
        ROI[0] = 108  // x_min
        ROI[1] = 136  // x_max
        ROI[2] = 251  // y_min
        ROI[3] = 294  // y_max

        // Load the TFLite model
        tflite = Interpreter(loadModelFile("breast_mri_model.tflite"))

        // Load and preprocess the image
        val fullSlice = loadBinSlice("Breast_MRI_002/Breast_MRI_002_slice_72.bin")
        val fullBitmap = convertSliceToBitmap(fullSlice)
        val roiSlice = extractRoi(fullSlice, ROI)
        val roiBitmap = convertSliceToBitmap(roiSlice)
        val resizedRoiBitmap = Bitmap.createScaledBitmap(roiBitmap, 256, 256, true)
        imageView.setImageBitmap(fullBitmap)  // Show full MRI image

        val input = convertBitmapToByteBuffer(resizedRoiBitmap)

        // Run inference
        val output = Array(1) { FloatArray(2) } // (x, y) prediction
        tflite.run(input, output)

        // Convert prediction to ROI coordinates
        val roiWidth = roiBitmap.width
        val roiHeight = roiBitmap.height
        val predX_roi = (output[0][0] * roiWidth).toInt()
        val predY_roi = (output[0][1] * roiHeight).toInt()

        // Convert ROI coordinates to full image coordinates
        val predX_full = ROI[0] + predX_roi
        val predY_full = ROI[2] + predY_roi

        // Show prediction in TextView
        predictionText.text = "Prediction: ($predX_full, $predY_full)"
        Log.d("Prediction", "Predicted Coordinates: ($predX_full, $predY_full)")

        // Draw prediction on full image
        drawLabelOnPrediction(fullBitmap, predX_full, predY_full)
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val assetFileDescriptor = assets.openFd(modelName)
        val inputStream = assetFileDescriptor.createInputStream()
        val modelBuffer = ByteArray(assetFileDescriptor.length.toInt())
        inputStream.read(modelBuffer)
        return ByteBuffer.allocateDirect(modelBuffer.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBuffer)
        }
    }

    private fun loadBinSlice(fileName: String): Array<DoubleArray> {
        val fileSize = assets.open(fileName).available()
        val buffer = ByteBuffer.allocate(fileSize)

        // Read the entire file into the buffer
        assets.open(fileName).use { inputStream ->
            val dataInputStream = DataInputStream(inputStream)
            dataInputStream.readFully(buffer.array())
        }

        // Set the byte order to match the system's native order
        buffer.order(ByteOrder.nativeOrder())

        // The slice is 512x512
        val width = 512
        val height = 512

        val slice = Array(height) { DoubleArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                slice[y][x] = buffer.double
            }
        }

        return slice
    }

    private fun convertSliceToBitmap(slice: Array<DoubleArray>): Bitmap {
        val width = slice[0].size
        val height = slice.size
        val pixels = IntArray(width * height)

        val minValue = slice.minOf { it.minOrNull() ?: Double.MAX_VALUE }
        val maxValue = slice.maxOf { it.maxOrNull() ?: Double.MIN_VALUE }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val normalizedValue = ((slice[y][x] - minValue) / (maxValue - minValue) * 255).toInt()
                pixels[y * width + x] = Color.rgb(normalizedValue, normalizedValue, normalizedValue)
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(256 * 256 * 4) // Float32 input
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(256 * 256)
        bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)

        for (pixel in pixels) {
            val grayscale = (pixel and 0xFF).toFloat() / 255.0f
            inputBuffer.putFloat(grayscale)
        }

        return inputBuffer
    }

    private fun extractRoi(slice: Array<DoubleArray>, roi: IntArray): Array<DoubleArray> {
        val x_min = roi[0]
        val y_min = roi[2]
        val x_max = roi[1]
        val y_max = roi[3]
        val roiWidth = x_max - x_min
        val roiHeight = y_max - y_min

        return Array(roiHeight) { y ->
            DoubleArray(roiWidth) { x ->
                slice[y + y_min][x + x_min]
            }
        }
    }

    private fun drawLabelOnPrediction(bitmap: Bitmap, x: Int, y: Int) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawCircle(x.toFloat(), y.toFloat(), 20f, paint)
        imageView.setImageBitmap(mutableBitmap)
    }
}