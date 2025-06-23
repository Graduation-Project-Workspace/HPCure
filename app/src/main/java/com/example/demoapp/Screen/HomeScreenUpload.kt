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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.demoapp.Core.ParallelFuzzySystem
import com.example.demoapp.Core.RoiPredictor
import com.example.demoapp.Core.SeedPredictor
import com.example.demoapp.Core.VolumeEstimator
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.demoapp.R
import com.example.domain.usecase.LogRepository
import com.example.demoapp.Utils.FileManager
import com.example.network.network.GrpcNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class HomeScreenUpload : BaseActivity() {
    private lateinit var mriImage: ImageView
    private lateinit var alphaCutValue: TextView
    private lateinit var alphaCutSlider: SeekBar
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private var currentAlphaCutValue: Float = 50.00f
    private lateinit var cancerVolume: CancerVolume
    private lateinit var mriSequence: MRISequence

    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var calculateButton: Button

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(this, "Storage permission required to load images", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize GrpcNetwork
        val seedPredictor = SeedPredictor(context = this)
        val roiPredictor = RoiPredictor(context = this)

        super.onCreate(savedInstanceState)

        if (!checkStoragePermission()) {
            requestStoragePermission()
        }
    }

    override fun getMainContent(): @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = View.inflate(ctx, R.layout.hs_upload, null)
                mriImage = view.findViewById(R.id.mri_image)
                alphaCutValue = view.findViewById(R.id.alpha_cut_value)
                alphaCutSlider = view.findViewById(R.id.alpha_cut_slider)
                imageCount = view.findViewById(R.id.image_count)
                prevImage = view.findViewById(R.id.prev_image)
                nextImage = view.findViewById(R.id.next_image)
                calculateButton = view.findViewById(R.id.calculate_volume)
                loadingOverlay = view.findViewById(R.id.loading_overlay)
                val menuButton = view.findViewById<ImageButton?>(R.id.menu_button)
                menuButton?.setOnClickListener { openDrawer() }

                fun updateDisplay() {
                    alphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
                }
                fun updateImageCount() {
                    imageCount.text = "${FileManager.getCurrentIndex()}/${FileManager.getTotalFiles()}"
                }
                fun updateNavigationButtons() {
                    val currentIndex = FileManager.getCurrentIndex()
                    val totalFiles = FileManager.getTotalFiles()
                    prevImage.visibility = if (currentIndex > 1) ImageButton.VISIBLE else ImageButton.INVISIBLE
                    nextImage.visibility = if (currentIndex < totalFiles) ImageButton.VISIBLE else ImageButton.INVISIBLE
                }
                fun loadCurrentImage() {
                    FileManager.getCurrentFile()?.let { file ->
                        val bitmap = FileManager.getProcessedImage(ctx, file)
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
                fun showLoadingState() {
                    loadingOverlay.alpha = 0f
                    loadingOverlay.visibility = View.VISIBLE
                    loadingOverlay.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                    calculateButton.isEnabled = false
                }
                fun hideLoadingState() {
                    loadingOverlay.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            loadingOverlay.visibility = View.GONE
                            calculateButton.isEnabled = true
                        }
                        .start()
                }
                fun navigateToResults() {
                    val alphaCut = alphaCutValue.text.toString().replace("%", "").toFloat()
                    // Store data in singleton for passing to HomeScreenResults
                    ResultsHolder.mriSequence = mriSequence
                    ResultsHolder.cancerVolume = cancerVolume
                    ResultsHolder.alphaCut = alphaCut
                    // Start HomeScreenResults activity
                    val intent = Intent(ctx, HomeScreenResults::class.java)
                    ctx.startActivity(intent)
                }
                fun setupCalculateButton() {
                    val mainViewModel = SharedViewModel.getInstance(this)
                    val network = mainViewModel.network
                    calculateButton.setOnClickListener {
                        showLoadingState()
                        // Record computation start time
                        ResultsHolder.computationStartTime = System.currentTimeMillis()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
                                    FileManager.getProcessedImage(ctx, file)
                                }
                                val alphaCut = alphaCutValue.text.toString().replace("%", "").toFloat()
                                mriSequence = MRISequence(
                                    images = bitmaps,
                                    metadata = HashMap()
                                )
                                val seedPredictor = SeedPredictor(context = ctx)
                                val roiPredictor = RoiPredictor(context = ctx)
                                val volumeEstimator = VolumeEstimator(
                                    seedPredictor = seedPredictor,
                                    fuzzySystem = ParallelFuzzySystem(),
                                    roiPredictor = roiPredictor,
                                    network = network
                                )
                                cancerVolume = volumeEstimator.estimateVolumeGrpc(mriSequence, alphaCut)
                                withContext(Dispatchers.Main) {
                                    hideLoadingState()
                                    // Record computation end time
                                    ResultsHolder.computationEndTime = System.currentTimeMillis()
                                    navigateToResults()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    hideLoadingState()
                                    Toast.makeText(ctx, "Error estimating volume: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                fun setupImageNavigation() {
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
                fun setupAlphaCutControl() {
                    alphaCutSlider.max = 10000 // For 2 decimal precision
                    alphaCutSlider.progress = (currentAlphaCutValue * 100).toInt()
                    updateDisplay()
                    alphaCutSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            currentAlphaCutValue = progress / 100f
                            updateDisplay()
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }
                // Initial setup
                updateDisplay()
                setupImageNavigation()
                setupAlphaCutControl()
                loadCurrentImage()
                updateImageCount()
                setupCalculateButton()
                view
            }
        )
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

    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanup()
    }
}