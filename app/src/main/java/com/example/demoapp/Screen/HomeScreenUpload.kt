package com.example.demoapp.Screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateListOf
import androidx.appcompat.app.AppCompatActivity
import com.example.demoapp.Core.ParallelFuzzySystem
import com.example.demoapp.Core.RoiPredictor
import com.example.demoapp.Core.SeedPredictor
import com.example.demoapp.Core.VolumeEstimator
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import com.example.network.network.GrpcNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@RequiresApi(Build.VERSION_CODES.N)
class HomeScreenUpload : AppCompatActivity() {
    private lateinit var mriImage: ImageView
    private lateinit var alphaCutValue: TextView
    private lateinit var alphaCutSlider: SeekBar
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private var currentAlphaCutValue: Float = 50.00f
    private lateinit var cancerVolume: CancerVolume
    private lateinit var mriSequence: MRISequence
    private lateinit var network: GrpcNetwork

    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var calculateButton: Button
    private var context = this

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeApp()
        } else {
            Toast.makeText(this, "Storage permission required to load images", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize GrpcNetwork
        val logs = mutableStateListOf<String>()
        val seedPredictor = SeedPredictor(context = this)
        val roiPredictor = RoiPredictor(context = this)
        network = GrpcNetwork(logs, this, roiPredictor, seedPredictor)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.hs_upload)

        if (checkStoragePermission()) {
            initializeApp()
        } else {
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    private fun initializeApp() {
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
    private fun setupCalculateButton() {
        calculateButton.setOnClickListener {
            showLoadingState()

            // Perform the volume estimation in a background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Retrieve the list of bitmaps
                    val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
                        FileManager.getProcessedImage(this@HomeScreenUpload, file)
                    }

                    // Parse alphaCutValue from the TextView
                    val alphaCut = alphaCutValue.text.toString().replace("%", "").toFloat()

                    // Create MRI sequence
                    mriSequence = MRISequence(
                        images = bitmaps,
                        metadata = HashMap()
                    )

                    // Get available workers
                    val availableWorkers = network.getAvailableWorkers()
                    Log.d("GrpcNetwork", "Available workers: ${availableWorkers.size}")

                    // Call estimateVolumeGrpc
                    val seedPredictor = SeedPredictor(context = context)
                    val roiPredictor = RoiPredictor(context = context)
                    val volumeEstimator = VolumeEstimator(
                        seedPredictor = seedPredictor,
                        fuzzySystem = ParallelFuzzySystem(),
                        roiPredictor = roiPredictor,
                        network = network
                    )

                    cancerVolume = volumeEstimator.estimateVolumeGrpc(mriSequence, alphaCut)
                    Log.d("VolumeEstimate", "Estimated Volume: ${cancerVolume.volume}")

                    // Update the UI on the main thread
                    withContext(Dispatchers.Main) {
                        hideLoadingState()
                        navigateToResults()
                    }
                } catch (e: Exception) {
                    Log.e("VolumeEstimate", "Error estimating volume", e)
                    withContext(Dispatchers.Main) {
                        hideLoadingState()
                        Toast.makeText(this@HomeScreenUpload, "Error estimating volume: ${e.message}", Toast.LENGTH_LONG).show()
                    }
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
        val alphaCut = alphaCutValue.text.toString().replace("%", "").toFloat()

        // Create and add HomeScreenResults fragment
        val resultsFragment = HomeScreenResults.newInstance(mriSequence, cancerVolume, alphaCut)
        transaction.replace(R.id.fragment_container, resultsFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun preprocessMriSequence(){
        // preprocess the MRI DICOM sequence to have a 3d image array [][][# slices] [512][512][# slices]


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