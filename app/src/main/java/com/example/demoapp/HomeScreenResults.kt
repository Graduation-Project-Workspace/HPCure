package com.example.demoapp

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import org.dcm4che3.io.DicomInputStream

class HomeScreenResults : Fragment() {
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

        fun newInstance(): HomeScreenResults {
            return HomeScreenResults()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.results_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        loadDicomFiles()
        setupImageNavigation()
        setupDownloadButton()
    }

    private fun initializeViews(view: View) {
        mriImage = view.findViewById(R.id.mri_image)
        imageCount = view.findViewById(R.id.image_count)
        prevImage = view.findViewById(R.id.prev_image)
        nextImage = view.findViewById(R.id.next_image)
        tumorVolume = view.findViewById(R.id.tumor_volume)
        downloadButton = view.findViewById(R.id.download_button)

        // Set a default background or placeholder
        mriImage.setBackgroundColor(android.graphics.Color.LTGRAY)
    }

    private fun loadDicomFiles() {
        try {
            context?.assets?.let { assets ->
                // Check if the dicom directory exists
                val files = assets.list("dicom")
                Log.d(TAG, "Files in dicom directory: ${files?.joinToString() ?: "null"}")

                for (i in 0..3) {
                    val filename = "dicom/output$i.dcm"
                    try {
                        Log.d(TAG, "Attempting to load: $filename")
                        assets.open(filename).use { inputStream ->
                            val dis = DicomInputStream(inputStream)
                            val bitmap = DicomUtils.convertDicomStreamToBitmap(dis)
                            if (bitmap != null) {
                                dicomBitmaps.add(bitmap)
                                Log.d(TAG, "Successfully loaded bitmap for $filename")
                            } else {
                                Log.e(TAG, "Failed to convert $filename to bitmap")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading $filename", e)
                    }
                }

                if (dicomBitmaps.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${dicomBitmaps.size} bitmaps")
                    loadCurrentImage()
                    updateImageCount()
                } else {
                    Log.e(TAG, "No bitmaps were loaded")
                    showToast("No DICOM files could be loaded")
                }
            } ?: run {
                Log.e(TAG, "Assets is null")
                showToast("Error: Cannot access assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadDicomFiles", e)
            showToast("Error loading files: ${e.message}")
        }
    }

    private fun loadCurrentImage() {
        if (dicomBitmaps.isEmpty() || currentImageIndex >= dicomBitmaps.size) {
            Log.d(TAG, "No images to display")
            return
        }

        try {
            val bitmap = dicomBitmaps[currentImageIndex]
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

    private fun setupImageNavigation() {
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

    private fun updateImageCount() {
        val total = dicomBitmaps.size
        val current = currentImageIndex + 1
        imageCount.text = "$current/$total"
    }

    private fun updateNavigationButtons() {
        prevImage.visibility = if (currentImageIndex > 0) View.VISIBLE else View.INVISIBLE
        nextImage.visibility = if (currentImageIndex < dicomBitmaps.size - 1) View.VISIBLE else View.INVISIBLE
    }

    private fun setupDownloadButton() {
        downloadButton.setOnClickListener {
            showToast("Downloading...")
        }
    }

    private fun showToast(message: String) {
        context?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}