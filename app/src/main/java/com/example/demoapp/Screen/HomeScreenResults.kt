package com.example.demoapp.Screen

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.graphics.get
import androidx.core.graphics.set
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.demoapp.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

object ResultsHolder {
    var mriSequence: MRISequence? = null
    var cancerVolume: CancerVolume? = null
    var alphaCut: Float = 0f
}

class HomeScreenResults() : BaseActivity() {
    private lateinit var mriImage: ImageView
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private lateinit var tumorVolume: TextView
    private lateinit var downloadButton: Button

    private var dicomBitmaps = mutableListOf<Bitmap>()
    private var currentImageIndex = 0

    companion object {
        private const val TAG = "HomeScreenResults"
    }

    override fun getMainContent(): @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = View.inflate(ctx, R.layout.results_screen, null)
                mriImage = view.findViewById(R.id.mri_image)
                imageCount = view.findViewById(R.id.image_count)
                prevImage = view.findViewById(R.id.prev_image)
                nextImage = view.findViewById(R.id.next_image)
                tumorVolume = view.findViewById(R.id.tumor_volume)
                downloadButton = view.findViewById(R.id.download_button)
                val menuButton = view.findViewById<ImageButton?>(R.id.menu_button)
                menuButton?.setOnClickListener { openDrawer() }

                mriImage.setBackgroundColor(android.graphics.Color.LTGRAY)

                // Get data from singleton
                val mriSequence = ResultsHolder.mriSequence
                val cancerVolume = ResultsHolder.cancerVolume
                val alphaCut = ResultsHolder.alphaCut

                if (mriSequence == null || cancerVolume == null) {
                    Toast.makeText(ctx, "No results data available", Toast.LENGTH_SHORT).show()
                    finish()
                    return@AndroidView view
                }

                fun showToast(message: String) {
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }

                fun updateImageCount() {
                    val total = dicomBitmaps.size
                    val current = currentImageIndex + 1
                    imageCount.text = "$current/$total"
                }

                fun updateNavigationButtons() {
                    prevImage.visibility = if (currentImageIndex > 0) View.VISIBLE else View.INVISIBLE
                    nextImage.visibility = if (currentImageIndex < dicomBitmaps.size - 1) View.VISIBLE else View.INVISIBLE
                }

                fun loadCurrentImage() {
                    if (dicomBitmaps.isEmpty() || currentImageIndex >= dicomBitmaps.size) {
                        Log.d(TAG, "No images to display")
                        return
                    }
                    try {
                        val bitmap = dicomBitmaps[currentImageIndex]
                        for (y in 0 until bitmap.height) {
                            for (x in 0 until bitmap.width) {
                                val pixel = bitmap[x, y]
                                if (cancerVolume.affinityMatrix[currentImageIndex][y][x] < alphaCut / 100.0f) {
                                    val red = (pixel shr 16 and 0xFF) * 0.5f
                                    val green = (pixel shr 8 and 0xFF) * 0.5f
                                    val blue = (pixel and 0xFF) * 0.5f
                                    bitmap[x, y] =
                                        (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
                                }
                            }
                        }
                        Log.d(TAG, "Setting bitmap for image ${currentImageIndex + 1}")
                        mriImage.post {
                            mriImage.apply {
                                setImageBitmap(bitmap)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                adjustViewBounds = true
                            }
                            Log.d(TAG, "Image view dimensions: ${mriImage.width}x${mriImage.height}")
                        }
                        updateNavigationButtons()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error displaying image", e)
                        showToast("Error displaying image")
                    }
                }

                fun loadDicomFiles() {
                    dicomBitmaps.clear()
                    for (i in 0 until mriSequence.images.size) {
                        dicomBitmaps.add(mriSequence.images[i])
                    }
                    if (dicomBitmaps.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${dicomBitmaps.size} bitmaps")
                        loadCurrentImage()
                        updateImageCount()
                    } else {
                        Log.e(TAG, "No bitmaps were loaded")
                        showToast("No DICOM files could be loaded")
                    }
                }

                fun setupImageNavigation() {
                    prevImage.setOnClickListener {
                        if (currentImageIndex > 0) {
                            currentImageIndex--
                            loadCurrentImage()
                            updateImageCount()
                        }
                    }
                    nextImage.setOnClickListener {
                        if (currentImageIndex < dicomBitmaps.size - 1) {
                            currentImageIndex++
                            loadCurrentImage()
                            updateImageCount()
                        }
                    }
                }

                fun setupDownloadButton() {
                    downloadButton.setOnClickListener {
                        showToast("Downloading...")
                    }
                }

                // Initial setup
                loadDicomFiles()
                setupImageNavigation()
                setupDownloadButton()
                tumorVolume.text = "Estimated Tumor Volume: ${cancerVolume.volume} cm³"
                view
            }
        )
    }
}