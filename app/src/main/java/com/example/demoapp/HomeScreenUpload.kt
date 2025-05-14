package com.example.demoapp

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeScreenUpload : AppCompatActivity() {
    private lateinit var mriImage: ImageView
    private lateinit var alphaCutValue: TextView
    private lateinit var alphaCutSlider: SeekBar
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private var currentAlphaCutValue: Float = 50.00f

    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var calculateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hs_upload)

        initializeViews()
        setupImageNavigation()
        setupAlphaCutControl()
        loadCurrentImage()
        updateImageCount()
        setupCalculateButton()
    }

    private fun initializeViews() {
        mriImage = findViewById(R.id.mri_image)
        alphaCutValue = findViewById(R.id.alpha_cut_value)
        alphaCutSlider = findViewById(R.id.alpha_cut_slider)
        imageCount = findViewById(R.id.image_count)
        prevImage = findViewById(R.id.prev_image)
        nextImage = findViewById(R.id.next_image)
        calculateButton = findViewById(R.id.calculate_volume)
        loadingOverlay = findViewById(R.id.loading_overlay)

        updateDisplay()
    }

@RequiresApi(Build.VERSION_CODES.N)
private fun setupCalculateButton() {
    calculateButton.setOnClickListener {
        showLoadingState()

        // Perform the volume estimation in a background thread
        CoroutineScope(Dispatchers.IO).launch {
            // Retrieve the list of bitmaps
            val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
                FileManager.getProcessedImage(this@HomeScreenUpload, file)
            }

            // Generate seed points (center of each image)
            val seedPoints = bitmaps.map { bitmap ->
                Pair(bitmap.width / 2, bitmap.height / 2)
            }

            // Parse alphaCutValue from the TextView
            val alphaCut = alphaCutValue.text.toString().replace("%", "").toFloat()

            // Call estimateVolume
            val volumeEstimator = VolumeEstimator()
            val total_volume = volumeEstimator.estimateVolume(bitmaps, seedPoints, alphaCut)

            // Log the result
            Log.d("VolumeEstimate", "Estimated Volume: $total_volume")

            // Update the UI on the main thread
            withContext(Dispatchers.Main) {
                hideLoadingState()
                navigateToResults()
            }
        }
    }
}

    private fun showLoadingState() {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        calculateButton.isEnabled = false
    }

    private fun hideLoadingState() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                calculateButton.isEnabled = true
            }
            .start()
    }

    private fun navigateToResults() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        // Create and add HomeScreenResults fragment
        val resultsFragment = HomeScreenResults.newInstance()
        transaction.replace(R.id.fragment_container, resultsFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun loadCurrentImage() {
        FileManager.getCurrentFile()?.let { file ->
            val bitmap = FileManager.getProcessedImage(this, file)
            bitmap?.let {
                mriImage.apply {
                    setImageBitmap(it)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            }
        }
        updateNavigationButtons()
    }

    private fun setupImageNavigation() {
        prevImage.setOnClickListener {
            if (FileManager.moveToPrevious()) {
                loadCurrentImage()
                updateImageCount()
            }
        }

        nextImage.setOnClickListener {
            if (FileManager.moveToNext()) {
                loadCurrentImage()
                updateImageCount()
            }
        }
    }

    private fun updateImageCount() {
        imageCount.text = "${FileManager.getCurrentIndex()}/${FileManager.getTotalFiles()}"
    }

    private fun updateNavigationButtons() {
        val currentIndex = FileManager.getCurrentIndex()
        val totalFiles = FileManager.getTotalFiles()

        prevImage.visibility = if (currentIndex > 1) ImageButton.VISIBLE else ImageButton.INVISIBLE
        nextImage.visibility = if (currentIndex < totalFiles) ImageButton.VISIBLE else ImageButton.INVISIBLE
    }

    private fun setupAlphaCutControl() {
        alphaCutSlider.max = 10000 // For 2 decimal precision
        alphaCutSlider.progress = (currentAlphaCutValue * 100).toInt()
        updateDisplay()

        alphaCutSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentAlphaCutValue = progress / 100f
                updateDisplay()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Add any behavior when user starts moving the slider
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optional: Add any behavior when user stops moving the slider
            }
        })
    }

    private fun updateDisplay() {
        alphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
    }

    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanup()
    }
}