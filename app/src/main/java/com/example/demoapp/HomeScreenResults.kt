package com.example.demoapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dcm4che3.io.DicomInputStream

class HomeScreenResults : Fragment() {
    private lateinit var mriImage: ImageView
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private lateinit var tumorVolume: TextView
    private lateinit var downloadButton: Button
    private lateinit var patientName: TextView
    private lateinit var predictor: MRIPredictor

    private var dicomBitmaps = mutableListOf<Bitmap>()
    private var currentImageIndex = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "HomeScreenResults"
        fun newInstance(): HomeScreenResults = HomeScreenResults()
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
        predictor = MRIPredictor(requireContext())
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
        patientName = view.findViewById(R.id.patient_name)

        // Set initial states
        imageCount.text = "0/0"
        tumorVolume.text = "Tumor Volume: -- mm³"
        patientName.text = "Results"
    }

    private fun loadDicomFiles() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                context?.assets?.let { assets ->
                    val files = assets.list("dicom")
                    Log.d(TAG, "Files in dicom directory: ${files?.joinToString() ?: "null"}")

                    for (i in 0..3) {
                        val filename = "dicom/output$i.dcm"
                        try {
                            assets.open(filename).use { inputStream ->
                                val dis = DicomInputStream(inputStream)
                                val bitmap = DicomUtils.convertDicomStreamToBitmap(dis)
                                bitmap?.let {
                                    dicomBitmaps.add(it)
                                    Log.d(TAG, "Successfully loaded bitmap for $filename")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading $filename", e)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (dicomBitmaps.isNotEmpty()) {
                            loadCurrentImage()
                            updateImageCount()
                        } else {
                            showToast("No DICOM files could be loaded")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadDicomFiles", e)
                withContext(Dispatchers.Main) {
                    showToast("Error loading files: ${e.message}")
                }
            }
        }
    }

    private fun loadCurrentImage() {
        if (dicomBitmaps.isEmpty() || currentImageIndex >= dicomBitmaps.size) {
            Log.d(TAG, "No images to display")
            return
        }

        try {
            val bitmap = dicomBitmaps[currentImageIndex]
            mriImage.post {
                mriImage.apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                processImageWithModel(bitmap)
            }
            updateNavigationButtons()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying image", e)
            showToast("Error displaying image")
        }
    }

    private fun processImageWithModel(bitmap: Bitmap) {
        coroutineScope.launch {
            try {
                val processedBitmap = preprocessImageForModel(bitmap)

                predictor.predictSeedLocationAsync(processedBitmap).fold(
                    onSuccess = { prediction ->
                        updateTumorInfo(prediction)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Prediction error", error)
                        showToast("Error processing image")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in processing", e)
                showToast("Processing error: ${e.message}")
            }
        }
    }

    private fun preprocessImageForModel(bitmap: Bitmap): Bitmap {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(matrix)
        val paint = Paint()
        paint.colorFilter = filter

        val grayscaleBitmap = Bitmap.createBitmap(
            bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscaleBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return Bitmap.createScaledBitmap(grayscaleBitmap, 256, 256, true)
    }

    private fun updateTumorInfo(prediction: Pair<Float, Float>) {
        // Calculate tumor volume based on prediction
        val volume = calculateTumorVolume(prediction)
        tumorVolume.text = "Tumor Volume: ${String.format("%.1f", volume)} mm³"
    }

    private fun calculateTumorVolume(prediction: Pair<Float, Float>): Float {
        // Implement your volume calculation logic here
        return 140.0f // Placeholder value matching your layout
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
            // Implement download functionality
            showToast("Downloading results...")
        }
    }

    private fun showToast(message: String) {
        context?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        predictor.close()
        coroutineScope.cancel()
    }
}