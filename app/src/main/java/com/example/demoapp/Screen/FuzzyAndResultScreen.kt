package com.example.demoapp.Screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.demoapp.Core.*
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import com.example.domain.interfaces.tumor.IFuzzySystem
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class FuzzyAndResultScreen : BaseActivity() {

    // Data
    private var fuzzyCalculationTime: Long = 0
    private var currentAlphaCutValue: Float = 50.00f
    private var roiTimeTaken: Long = 0
    private var seedTimeTaken: Long = 0
    private lateinit var cancerVolume: CancerVolume
    private lateinit var originalMriSequence: MRISequence
    private lateinit var tumorMriSequence: MRISequence
    private var roiList: List<ROI> = emptyList()
    private var tumorRoiList: List<ROI> = emptyList()
    private var seedList: Array<Pair<Int, Int>> = emptyArray()
    private lateinit var parallelFuzzySystem: ParallelFuzzySystem
    private lateinit var sequentialFuzzySystem: SerialFuzzySystem
    private var fuzzySystem: IFuzzySystem? = null
    private var selectedMode: String = "Parallel"
    private lateinit var roiPredictor: ParallelRoiPredictor
    private lateinit var seedPredictor: ParallelSeedPredictor
    private var sliceIndex = 0

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // initializeApp() - will be handled in AndroidView factory
        } else {
            Toast.makeText(this, "Storage permission required to load images", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("FuzzyAndResultScreen", "onCreate called for FuzzyAndResultScreen")
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.fuzzy_and_result_screen) // Removed to allow Compose setContent

        roiTimeTaken = intent.getLongExtra("roi_time_taken", 0)
        seedTimeTaken = intent.getLongExtra("seed_time_taken", 0)

        @Suppress("UNCHECKED_CAST")
        intent.getSerializableExtra("roi_list")?.let { extra ->
            val incomingRoiList = extra as? List<ROI>
            if (incomingRoiList != null) {
                roiList = incomingRoiList
            }
        }

        @Suppress("UNCHECKED_CAST")
        intent.getSerializableExtra("seed_list")?.let { extra ->
            val incomingSeedList = extra as? Array<Pair<Int, Int>>
            if (incomingSeedList != null) {
                seedList = incomingSeedList
            }
        }

        if (FileManager.getAllFiles().isEmpty()) {
            Toast.makeText(this, "No images loaded! Returning to upload screen.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, UploadScreen::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        val bitmaps = FileManager.getAllFiles().mapNotNull {
            FileManager.getProcessedImage(this, it)
        }
        val mriSequence = MRISequence(
            images = bitmaps,
            metadata = FileManager.getDicomMetadata()
        )
        tumorMriSequence = MRISequence(
            images = emptyList(),
            metadata = FileManager.getDicomMetadata()
        )

        for ((index, roi) in roiList.withIndex()) {
            if (roi.score > 0.3) {
                tumorMriSequence.images+= mriSequence.images[index]
                tumorRoiList+= roi
            }
        }

        // Initialize predictors after context is available
        roiPredictor = ParallelRoiPredictor(this)
        seedPredictor = ParallelSeedPredictor(this)

        parallelFuzzySystem = ParallelFuzzySystem()
        sequentialFuzzySystem = SerialFuzzySystem()

        if (checkStoragePermission()) {
            // initializeApp() - will be handled in AndroidView factory
        } else {
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
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

    private fun setMode(mode: String) {
        selectedMode = mode
        fuzzySystem = if (mode == "Parallel") parallelFuzzySystem else sequentialFuzzySystem

        // Update button colors - this will be handled in the AndroidView context
    }



    private fun highlightCancerArea(bitmap: Bitmap, sliceIndex: Int): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        if (sliceIndex < cancerVolume.affinityMatrix.size) {
            val affinityMatrix = cancerVolume.affinityMatrix[sliceIndex]
            for (y in 0 until mutableBitmap.height) {
                for (x in 0 until mutableBitmap.width) {
                    if (y < affinityMatrix.size && x < affinityMatrix[y].size) {
                        if (affinityMatrix[y][x] < currentAlphaCutValue / 100.0f) {
                            val pixel = mutableBitmap.getPixel(x, y)
                            val r = (Color.red(pixel) * 1.5f).coerceAtMost(255f).toInt()
                            val g = (Color.green(pixel) * 0.7f).coerceAtMost(255f).toInt()
                            val b = (Color.blue(pixel) * 0.7f).coerceAtMost(255f).toInt()
                            mutableBitmap.setPixel(x, y, Color.rgb(r, g, b))
                        }
                    }
                }
            }
        }
        return mutableBitmap
    }

    private fun drawNormalizedRoiOnly(bitmap: Bitmap, roi: ROI): Bitmap {
        val x1 = roi.xMin.toFloat()
        val y1 = roi.yMin.toFloat()
        val x2 = roi.xMax.toFloat()
        val y2 = roi.yMax.toFloat()

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(x1, y1, x2, y2, paint)
        return mutableBitmap
    }

    private fun drawSeedPointInsideNormalizedRoi(bitmap: Bitmap, roi: ROI, seed: Pair<Int, Int>): Bitmap {

        val x1 = roi.xMin.toFloat()
        val y1 = roi.yMin.toFloat()
        val x2 = roi.xMax.toFloat()
        val y2 = roi.yMax.toFloat()

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Draw ROI
        val roiPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(x1, y1, x2, y2, roiPaint)

        // Draw seed inside ROI
        val seedX = seed.first.toFloat()
        val seedY = seed.second.toFloat()

        val seedPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        canvas.drawCircle(seedX, seedY, 6f, seedPaint)

        return mutableBitmap
    }

    fun updateImageCount(fuzzyImageCount: TextView, resultsImageCount: TextView) {
        val countText = "${sliceIndex + 1}/${tumorMriSequence.images.size}"
        fuzzyImageCount.text = countText
        resultsImageCount.text = countText
    }

    fun updateNavigationButtons(fuzzyPrevImage: ImageButton, fuzzyNextImage: ImageButton, resultsPrevImage: ImageButton, resultsNextImage: ImageButton) {
        val currentIndex = sliceIndex + 1
        val totalFiles = tumorMriSequence.images.size

        // Update fuzzy navigation
        fuzzyPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        fuzzyNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE

        // Update results navigation
        resultsPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        resultsNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE
    }

    fun loadCurrentImage(fuzzyMriImage: ImageView, fuzzyPrevImage: ImageButton, fuzzyNextImage: ImageButton, resultsPrevImage: ImageButton, resultsNextImage: ImageButton) {
        val displayBitmap = when {
            sliceIndex < tumorRoiList.size && sliceIndex < seedList.size -> {
                drawSeedPointInsideNormalizedRoi(tumorMriSequence.images[sliceIndex], tumorRoiList[sliceIndex], seedList[sliceIndex])
            }
            sliceIndex < tumorRoiList.size -> {
                drawNormalizedRoiOnly(tumorMriSequence.images[sliceIndex], roiList[sliceIndex])
            }
            else -> {
                tumorMriSequence.images[sliceIndex]
            }
        }
        fuzzyMriImage.setImageBitmap(displayBitmap)
        updateNavigationButtons(fuzzyPrevImage, fuzzyNextImage, resultsPrevImage, resultsNextImage)
    }

    fun loadCurrentResultsImage(resultsMriImage: ImageView) {
        if (::cancerVolume.isInitialized) {
            val displayBitmap = highlightCancerArea(tumorMriSequence.images[sliceIndex], sliceIndex)
            resultsMriImage.setImageBitmap(displayBitmap)
        }
    }

    override fun getMainContent(): @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = View.inflate(ctx, R.layout.fuzzy_and_result_screen, null)
                
                // Initialize all views
                val fuzzyLayout = view.findViewById<RelativeLayout>(R.id.fuzzy_layout)
                val fuzzyMriImage = view.findViewById<ImageView>(R.id.fuzzy_mri_image)
                val fuzzyAlphaCutValue = view.findViewById<TextView>(R.id.fuzzy_alpha_cut_value)
                val fuzzyAlphaCutSlider = view.findViewById<SeekBar>(R.id.fuzzy_alpha_cut_slider)
                val fuzzyImageCount = view.findViewById<TextView>(R.id.fuzzy_image_count)
                val fuzzyPrevImage = view.findViewById<ImageButton>(R.id.fuzzy_prev_image)
                val fuzzyNextImage = view.findViewById<ImageButton>(R.id.fuzzy_next_image)
                val fuzzyCalculateButton = view.findViewById<Button>(R.id.fuzzy_calculate_button)
                val fuzzyCalculateVolumeButton = view.findViewById<Button>(R.id.fuzzy_calculate_volume_button)
                val fuzzyPatientName = view.findViewById<TextView>(R.id.fuzzy_patient_name)
                val fuzzyBackButton = view.findViewById<ImageButton>(R.id.fuzzy_back_button)
                val btnParallel = view.findViewById<Button>(R.id.btn_parallel)
                val btnSerial = view.findViewById<Button>(R.id.btn_serial)
                val resultsLayout = view.findViewById<RelativeLayout>(R.id.results_layout)
                val resultsMriImage = view.findViewById<ImageView>(R.id.results_mri_image)
                val resultsImageCount = view.findViewById<TextView>(R.id.results_image_count)
                val resultsPrevImage = view.findViewById<ImageButton>(R.id.results_prev_image)
                val resultsNextImage = view.findViewById<ImageButton>(R.id.results_next_image)
                val resultsTumorVolume = view.findViewById<TextView>(R.id.results_tumor_volume)
                val resultsPatientName = view.findViewById<TextView>(R.id.results_patient_name)
                val resultsRecalculateButton = view.findViewById<Button>(R.id.results_recalculate_button)
                val resultsBackButton = view.findViewById<ImageButton>(R.id.results_back_button)
                val loadingOverlay = view.findViewById<RelativeLayout>(R.id.loading_overlay)

                // Local functions for UI operations
                fun showFuzzyLayout() {
                    fuzzyLayout.visibility = View.VISIBLE
                    resultsLayout.visibility = View.GONE
                }

                fun showResultsLayout() {
                    fuzzyLayout.visibility = View.GONE
                    resultsLayout.visibility = View.VISIBLE
                }

                fun setMode(mode: String) {
                    selectedMode = mode
                    fuzzySystem = if (mode == "Parallel") parallelFuzzySystem else sequentialFuzzySystem

                    btnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
                    btnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

                    btnSerial.setBackgroundColor(Color.parseColor(if (mode == "Serial") "#B0BEC5" else "#455A64"))
                    btnSerial.setTextColor(Color.parseColor(if (mode == "Serial") "#000000" else "#FFFFFF"))
                }

                fun performRecalculation() {
                    loadingOverlay.visibility = View.VISIBLE
                    fuzzyCalculateButton.isEnabled = false
                    resultsRecalculateButton.isEnabled = false
                    
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val startTime = System.currentTimeMillis()
                            
                            // Create VolumeEstimator instance
                            val volumeEstimator = VolumeEstimator(
                                fuzzySystem = parallelFuzzySystem,
                                seedPredictor = seedPredictor,
                                roiPredictor = roiPredictor,
                                network = SharedViewModel.getInstance(this@FuzzyAndResultScreen).network
                            )
                            
                            // Use gRPC volume estimation
                            cancerVolume = volumeEstimator.estimateVolumeGrpc(
                                mriSeq = tumorMriSequence,
                                alphaCutValue = currentAlphaCutValue
                            )
                            
                            val elapsed = System.currentTimeMillis() - startTime
                            
                            withContext(Dispatchers.Main) {
                                loadingOverlay.visibility = View.GONE
                                fuzzyCalculateButton.isEnabled = true
                                resultsRecalculateButton.isEnabled = true
                                resultsTumorVolume.text = "Tumor Volume: ${cancerVolume.volume} mm³"
                                resultsPatientName.text = "gRPC Computation Time: ${elapsed}ms"
                                showResultsLayout()
                                loadCurrentResultsImage(resultsMriImage)
                            }
                        } catch (e: Exception) {
                            Log.e("FuzzyAndResultScreen", "Error in gRPC volume estimation", e)
                            withContext(Dispatchers.Main) {
                                loadingOverlay.visibility = View.GONE
                                fuzzyCalculateButton.isEnabled = true
                                resultsRecalculateButton.isEnabled = true
                                Toast.makeText(ctx, "Error in volume calculation: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                fun navigateImage(direction: Int) {
                    val totalSlices = tumorMriSequence.images.size
                    val newIndex = (sliceIndex + direction).coerceIn(0, totalSlices - 1)
                    if (newIndex != sliceIndex) {
                        sliceIndex = newIndex
                        updateImageCount(fuzzyImageCount, resultsImageCount)
                        loadCurrentImage(fuzzyMriImage, fuzzyPrevImage, fuzzyNextImage, resultsPrevImage, resultsNextImage)
                        if (resultsLayout.visibility == View.VISIBLE) {
                            loadCurrentResultsImage(resultsMriImage)
                        }
                    }
                }

                fun updateAlphaCutDisplay() {
                    fuzzyAlphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
                }

                // Set up mode buttons
                btnParallel.setOnClickListener { setMode("Parallel") }
                btnSerial.setOnClickListener { setMode("Serial") }

                // Set up recalculate button
                resultsRecalculateButton.setOnClickListener {
                    performRecalculation()
                }

                // Set arrow icons and tint programmatically
                fuzzyPrevImage.setImageResource(R.drawable.ic_left_arrow_vector)
                fuzzyNextImage.setImageResource(R.drawable.ic_right_arrow_vector)
                fuzzyPrevImage.setColorFilter(Color.BLACK)
                fuzzyNextImage.setColorFilter(Color.BLACK)

                resultsPrevImage.setImageResource(R.drawable.ic_left_arrow_vector)
                resultsNextImage.setImageResource(R.drawable.ic_right_arrow_vector)
                resultsPrevImage.setColorFilter(Color.BLACK)
                resultsNextImage.setColorFilter(Color.BLACK)

                resultsBackButton.setImageResource(R.drawable.ic_arrow_back)
                resultsBackButton.setColorFilter(Color.WHITE)

                fuzzyBackButton.setImageResource(R.drawable.ic_arrow_back)
                fuzzyBackButton.setColorFilter(Color.WHITE)

                // Set up menu button
                val menuButton = resultsLayout.findViewById<ImageButton?>(R.id.menu_button)
                menuButton?.setImageResource(R.drawable.menu_button)
                menuButton?.setColorFilter(Color.WHITE)
                menuButton?.visibility = View.VISIBLE
                menuButton?.isClickable = true
                menuButton?.isFocusable = true
                menuButton?.setOnClickListener {
                    Log.d("resultttttt", "the menu button is tapped")
                    openDrawer()
                }
                menuButton?.bringToFront()

                // Set up back buttons
                fuzzyBackButton.setOnClickListener {
                    finish()
                }

                resultsBackButton.setOnClickListener {
                    showFuzzyLayout()
                }

                // Set up image navigation
                fuzzyPrevImage.setOnClickListener { navigateImage(-1) }
                fuzzyNextImage.setOnClickListener { navigateImage(1) }
                resultsPrevImage.setOnClickListener { navigateImage(-1) }
                resultsNextImage.setOnClickListener { navigateImage(1) }

                // Set up calculate button
                fuzzyCalculateButton.setOnClickListener {
                    if (roiList.isEmpty() || seedList.isEmpty()) {
                        Toast.makeText(ctx, "No ROI or Seed data available!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    if (fuzzySystem == null) {
                        Toast.makeText(ctx, "FuzzySystem not initialized!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    loadingOverlay.visibility = View.VISIBLE
                    fuzzyCalculateButton.isEnabled = false
                    resultsRecalculateButton.isEnabled = false
                    val startTime = System.currentTimeMillis()
                    CoroutineScope(Dispatchers.Default).launch {
                        val alphaCut = currentAlphaCutValue
                        cancerVolume = fuzzySystem!!.estimateVolume(tumorMriSequence, tumorRoiList, seedList.toList(), alphaCut)
                        val elapsed = System.currentTimeMillis() - startTime
                        withContext(Dispatchers.Main) {
                            loadingOverlay.visibility = View.GONE
                            fuzzyCalculateButton.isEnabled = true
                            resultsRecalculateButton.isEnabled = true
                            val totalTime = roiTimeTaken + seedTimeTaken + elapsed
                            resultsTumorVolume.text = "Tumor Volume: ${cancerVolume.volume} mm³"
                            resultsPatientName.text = "Total Time: ${totalTime}ms"
                            showResultsLayout()
                            loadCurrentResultsImage(resultsMriImage)
                        }
                    }
                }

                // Set up alpha cut control
                fuzzyAlphaCutSlider.max = 10000
                fuzzyAlphaCutSlider.progress = (currentAlphaCutValue * 100).toInt()
                updateAlphaCutDisplay()

                fuzzyAlphaCutSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        currentAlphaCutValue = progress / 100f
                        updateAlphaCutDisplay()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                // Initial setup
                setMode("Parallel")
                loadCurrentImage(fuzzyMriImage, fuzzyPrevImage, fuzzyNextImage, resultsPrevImage, resultsNextImage)
                updateImageCount(fuzzyImageCount, resultsImageCount)
                showFuzzyLayout()

                view
            }
        )
    }
}