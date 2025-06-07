package com.example.demoapp.Screen

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.example.demoapp.Core.SeedPredictor
import com.example.demoapp.R
import com.example.demoapp.Utils.GpuDelegateHelper
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelScreen : AppCompatActivity() {

    private lateinit var seedPredictor: SeedPredictor
    private lateinit var viewPager: ViewPager
    private lateinit var predictionText: TextView
    private val roiBitmaps = mutableListOf<Bitmap?>() // Allow nulls for uninitialized bitmaps
    @OptIn(ExperimentalCoroutinesApi::class)
    private val imageProcessingDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val ROI = IntArray(4)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_screen)
        viewPager = findViewById(R.id.sliceViewPager)
        predictionText = findViewById(R.id.predictionText)
        ROI[0] = 108  // x_min
        ROI[1] = 136  // x_max
        ROI[2] = 251  // y_min
        ROI[3] = 294  // y_max
        seedPredictor = SeedPredictor(context = this);
        viewPager.adapter = SlicePagerAdapter()
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                val sliceNumber = position + 52
                val fileName = "Breast_MRI_002/image_slice_$sliceNumber.bin"
                CoroutineScope(imageProcessingDispatcher).launch {
                    var inferenceTime: Long = 0 // Declare inferenceTime variable
                    try {
                        val startTime = System.currentTimeMillis() // Alternative way to measure time
                        val fullSlice = loadBinSlice(fileName)
                        val fullBitmap = convertSliceToBitmap(fullSlice) // Convert the full slice to a bitmap to
                        val roiSlice = extractRoi(fullSlice, ROI)
                        val roiBitmap = convertSliceToBitmap(roiSlice)
                        var output = Array(1) { FloatArray(2) } //array of size 2
                        output = seedPredictor.predictSeed(fullBitmap, ROI)
                        val endTime = System.currentTimeMillis()
                        inferenceTime = endTime - startTime // Calculate inference time
                        val roiWidth = roiBitmap.width
                        val roiHeight = roiBitmap.height
                        val predX_roi = (output[0][0] * roiWidth).toInt()
                        val predY_roi = (output[0][1] * roiHeight).toInt()
                        val predX_full = ROI[0] + predX_roi
                        val predY_full = ROI[2] + predY_roi
                        val frameWithPrediction = drawLabelOnPrediction(fullBitmap, predX_full, predY_full, ROI)
                        withContext(Dispatchers.Main) {
                            predictionText.text = "Prediction for slice $sliceNumber: ($predX_full, $predY_full) - Inference time: $inferenceTime ms"
                            (viewPager.adapter as SlicePagerAdapter).updateImage(position, frameWithPrediction)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreenResults", "Error processing slice: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            predictionText.text = "Error loading slice $sliceNumber: ${e.message}"
                        }
                    }
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
    }
    private fun loadBinSlice(fileName: String): Array<DoubleArray> {
        val fileSize = assets.open(fileName).available()
        val buffer = ByteBuffer.allocate(fileSize)
        assets.open(fileName).use { inputStream ->
            val dataInputStream = DataInputStream(inputStream)
            dataInputStream.readFully(buffer.array())
        }
        buffer.order(ByteOrder.nativeOrder())
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
    private fun extractRoiFromBitmap(bitmap: Bitmap, roi: IntArray): Bitmap {
        val x_min = roi[0]
        val y_min = roi[2]
        val x_max = roi[1]
        val y_max = roi[3]
        return Bitmap.createBitmap(bitmap, x_min, y_min, x_max - x_min, y_max - y_min)
    }

    private fun drawLabelOnPrediction(bitmap: Bitmap, x: Int, y: Int, roi: IntArray): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val rect = Rect(roi[0], roi[2], roi[1], roi[3])
        canvas.drawRect(rect, paint)

        // Changed to RED circle with only stroke, no fill
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(x.toFloat(), y.toFloat(), 5f, paint)

        return mutableBitmap
    }
    inner class SlicePagerAdapter : PagerAdapter() {
        private val images = mutableListOf<Bitmap?>() // Allow nulls

        override fun getCount(): Int = 21 // Number of slices from 52 to 72

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val imageView = ImageView(this@ModelScreen)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.tag = position // Set tag for easy access
            container.addView(imageView)
            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        fun updateImage(position: Int, bitmap: Bitmap) {
            while (images.size <= position) {
                images.add(null)
            }
            images[position] = bitmap
            (viewPager.findViewWithTag(position) as? ImageView)?.setImageBitmap(bitmap)
        }
    }
}