package com.example.demoapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HomeScreenResults : AppCompatActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var imageView: ImageView
    private lateinit var predictionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.results_screen)

        // Find UI elements
        imageView = findViewById(R.id.mriImageView)
        predictionText = findViewById(R.id.predictionText)

        // Load the TFLite model
        tflite = Interpreter(loadModelFile("breast_mri_model.tflite"))

        // Load and preprocess the image
        val bitmap = loadImage("Breast_MRI_002_slice72.png")
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        imageView.setImageBitmap(bitmap)  // Show MRI image

        val input = convertBitmapToByteBuffer(resizedBitmap)

        // Run inference
        val output = Array(1) { FloatArray(2) } // (x, y) prediction
        tflite.run(input, output)

        val predX = (output[0][0] * bitmap.width).toInt()
        val predY = (output[0][1] * bitmap.height).toInt()

        // Show prediction in TextView
        predictionText.text = "Prediction: ($predX, $predY)"
        Log.d("Prediction", "Predicted Coordinates: ($predX, $predY)")

        // Draw prediction on image
        drawLabelOnPrediction(bitmap, predX, predY)

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

    private fun loadImage(fileName: String): Bitmap {
        assets.open(fileName).use { inputStream ->
            return BitmapFactory.decodeStream(inputStream)
        }
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

    private fun drawLabelOnPrediction(bitmap: Bitmap, x: Int, y: Int) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawCircle(x.toFloat(), y.toFloat(), 10f, paint)
        imageView.setImageBitmap(mutableBitmap)
    }
}
