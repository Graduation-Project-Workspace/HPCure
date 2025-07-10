package com.example.demoapp.Screen

import android.Manifest
import android.annotation.SuppressLint
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
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.demoapp.Utils.ResultsDataHolder
import com.example.demoapp.Utils.ResultsDataHolder.reportEntries
import com.example.domain.interfaces.tumor.IFuzzySystem
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
    private lateinit var parallelFuzzySystem: ParallelFuzzySystem
    private lateinit var sequentialFuzzySystem: SerialFuzzySystem
    private var fuzzySystem: IFuzzySystem? = null
    private var selectedMode: String = "Parallel"
    private lateinit var roiPredictor: ParallelRoiPredictor
    private lateinit var seedPredictor: ParallelSeedPredictor
    private lateinit var sequentialRoiPredictor: SequentialRoiPredictor
    private lateinit var sequentialSeedPredictor: SequentialSeedPredictor
    private var sliceIndex = 0
    private var showWholeProcessRow = false

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

        roiTimeTaken = intent.getLongExtra("roi_time_taken", 0)
        seedTimeTaken = intent.getLongExtra("seed_time_taken", 0)

        // Check if we have the full MRI sequence in ResultsDataHolder
        val fullMriSeq = ResultsDataHolder.fullMriSequence
        if (fullMriSeq == null || fullMriSeq.images.isEmpty()) {
            Toast.makeText(this, "No images found. Returning to upload screen.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, UploadScreen::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        // Initialize predictors after context is available
        roiPredictor = ParallelRoiPredictor
        seedPredictor = ParallelSeedPredictor
        sequentialRoiPredictor = SequentialRoiPredictor
        sequentialSeedPredictor = SequentialSeedPredictor

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

        if (sliceIndex < ResultsDataHolder.cancerVolume.affinityMatrix.size) {
            val affinityMatrix = ResultsDataHolder.cancerVolume.affinityMatrix[sliceIndex]
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
        val countText = "${sliceIndex + 1}/${ResultsDataHolder.tumorMriSequence!!.images.size}"
        fuzzyImageCount.text = countText
        resultsImageCount.text = countText
    }

    fun updateNavigationButtons(fuzzyPrevImage: ImageButton, fuzzyNextImage: ImageButton, resultsPrevImage: ImageButton, resultsNextImage: ImageButton) {
        val currentIndex = sliceIndex + 1
        val totalFiles = ResultsDataHolder.tumorMriSequence!!.images.size

        // Update fuzzy navigation
        fuzzyPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        fuzzyNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE

        // Update results navigation
        resultsPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        resultsNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE
    }

    fun loadCurrentImage(fuzzyMriImage: ImageView, fuzzyPrevImage: ImageButton, fuzzyNextImage: ImageButton, resultsPrevImage: ImageButton, resultsNextImage: ImageButton) {
        val displayBitmap = when {
            sliceIndex < ResultsDataHolder.tumorRoiList.size && sliceIndex < ResultsDataHolder.seedList.size -> {
                drawSeedPointInsideNormalizedRoi(
                    ResultsDataHolder.tumorMriSequence!!.images[sliceIndex],
                    ResultsDataHolder.tumorRoiList[sliceIndex],
                    ResultsDataHolder.seedList[sliceIndex])
            }
            sliceIndex < ResultsDataHolder.tumorRoiList.size -> {
                drawNormalizedRoiOnly(
                    ResultsDataHolder.tumorMriSequence!!.images[sliceIndex],
                    ResultsDataHolder.tumorRoiList[sliceIndex])
            }
            else -> {
                ResultsDataHolder.tumorMriSequence!!.images[sliceIndex]
            }
        }
        fuzzyMriImage.setImageBitmap(displayBitmap)
        updateNavigationButtons(fuzzyPrevImage, fuzzyNextImage, resultsPrevImage, resultsNextImage)
    }

    fun loadCurrentResultsImage(resultsMriImage: ImageView) {
        if (ResultsDataHolder.cancerVolume.affinityMatrix.isNotEmpty()
            && sliceIndex < ResultsDataHolder.cancerVolume.affinityMatrix.size) {
            val displayBitmap = highlightCancerArea(ResultsDataHolder.tumorMriSequence!!.images[sliceIndex], sliceIndex)
            resultsMriImage.setImageBitmap(displayBitmap)
        }
    }

    fun showLoadingState(loadingOverlay :RelativeLayout, fuzzyCalculateButton: Button) {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        fuzzyCalculateButton.isEnabled = false
    }

    fun hideLoadingState(loadingOverlay :RelativeLayout, fuzzyCalculateButton: Button) {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                fuzzyCalculateButton.isEnabled = true
            }
            .start()
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
                val fuzzyPatientName = view.findViewById<TextView>(R.id.fuzzy_patient_name)
                val fuzzyBackButton = view.findViewById<ImageButton>(R.id.fuzzy_back_button)
                val btnParallel = view.findViewById<Button>(R.id.btn_parallel)
                val btnSerial = view.findViewById<Button>(R.id.btn_serial)
                val fuzzyTimeText = view.findViewById<TextView>(R.id.fuzzy_time_text)
                val fuzzyShowResultsButton = view.findViewById<Button>(R.id.fuzzy_show_results_button)
                val fuzzyReportContainer = view.findViewById<LinearLayout>(R.id.fuzzy_report_container)
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
                val resultsReportContainer = view.findViewById<LinearLayout>(R.id.report_container)

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
                    fuzzySystem = when (mode) {
                        "Parallel" -> parallelFuzzySystem
                        "Serial" -> sequentialFuzzySystem
                        else -> parallelFuzzySystem
                    }


                    btnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
                    btnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

                    btnSerial.setBackgroundColor(Color.parseColor(if (mode == "Serial") "#B0BEC5" else "#455A64"))
                    btnSerial.setTextColor(Color.parseColor(if (mode == "Serial") "#000000" else "#FFFFFF"))

                    val resultsBtnParallel = view.findViewById<Button>(R.id.results_btn_parallel)
                    val resultsBtnSerial = view.findViewById<Button>(R.id.results_btn_serial)
                    val resultsBtnGrpc = view.findViewById<Button>(R.id.results_btn_grpc)

                    resultsBtnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
                    resultsBtnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

                    resultsBtnSerial.setBackgroundColor(Color.parseColor(if (mode == "Serial") "#B0BEC5" else "#455A64"))
                    resultsBtnSerial.setTextColor(Color.parseColor(if (mode == "Serial") "#000000" else "#FFFFFF"))

                    resultsBtnGrpc.setBackgroundColor(Color.parseColor(if (mode == "GRPC") "#B0BEC5" else "#455A64"))
                    resultsBtnGrpc.setTextColor(Color.parseColor(if (mode == "GRPC") "#000000" else "#FFFFFF"))
                }

                @SuppressLint("SetTextI18n")
                fun performRecalculation() {
                    Log.d("Recalculate", "Started recalculation, mode: $selectedMode")

                    showLoadingState(loadingOverlay, fuzzyCalculateButton)
                    fuzzyCalculateButton.isEnabled = false
                    resultsRecalculateButton.isEnabled = false

                    val activityContext = this@FuzzyAndResultScreen

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val fullSequence = ResultsDataHolder.fullMriSequence
                            Log.d("Recalculate", "FullSequence loaded, images count: ${fullSequence?.images?.size}")

                            if (fullSequence == null || fullSequence.images.isEmpty()) {
                                Log.e("Recalculate", "Full MRI sequence null or empty, throwing exception")
                                throw IllegalStateException("Full MRI sequence not available, please reload images.")
                            }

                            var startTime: Long
                            var elapsed: Long
                            sliceIndex = 0

                            fun filterImagesAndRois(
                                mriSequence: MRISequence,
                                roiList: List<ROI>
                            ): Pair<MRISequence, List<ROI>> {
                                val filteredImages = mutableListOf<Bitmap>()
                                val filteredRois = mutableListOf<ROI>()
                                for ((index, roi) in roiList.withIndex()) {
                                    if (roi.score > 0.3) {
                                        filteredImages += mriSequence.images[index]
                                        filteredRois += roi
                                    }
                                }
                                Log.d("Recalculate", "Filtered images count: ${filteredImages.size}, ROIs count: ${filteredRois.size}")
                                return MRISequence(images = filteredImages, metadata = mriSequence.metadata) to filteredRois
                            }

                            if (selectedMode == "Parallel") {
                                startTime = System.currentTimeMillis()
                                Log.d("Recalculate", "Starting Parallel ROI prediction")

                                val roiListParallel = roiPredictor.predictRoi(
                                    fullSequence,
                                    useGpuDelegate = true,
                                    useAndroidNN = true,
                                    numThreads = 4
                                )

                                Log.d("Recalculate", "Parallel ROI prediction done, count: ${roiListParallel.size}")

                                val (filteredMriSequence, filteredRois) = filterImagesAndRois(fullSequence, roiListParallel)

                                val seedListParallel = seedPredictor.predictSeed(
                                    filteredMriSequence,
                                    filteredRois,
                                    useGpuDelegate = true,
                                    useAndroidNN = true,
                                    numThreads = 4
                                ).toList()

                                Log.d("Recalculate", "Parallel seed prediction done, count: ${seedListParallel.size}")

                                ResultsDataHolder.cancerVolume = parallelFuzzySystem.estimateVolume(
                                    filteredMriSequence,
                                    filteredRois,
                                    seedListParallel,
                                    currentAlphaCutValue
                                )
                                Log.d("Recalculate", "Parallel volume estimation done, volume: ${ResultsDataHolder.cancerVolume.volume}")

                                ResultsDataHolder.tumorMriSequence = filteredMriSequence
                                ResultsDataHolder.tumorRoiList = filteredRois
                                ResultsDataHolder.seedList = seedListParallel.toTypedArray()

                                elapsed = System.currentTimeMillis() - startTime
                                roiPredictor.close()
                                seedPredictor.close()
                                GpuDelegateHelper.closeAllDelegates()

                            } else if (selectedMode == "Serial") {
                                startTime = System.currentTimeMillis()
                                Log.d("Recalculate", "Starting Serial ROI prediction")
                                val roiListSerial = sequentialRoiPredictor.predictRoi(
                                    fullSequence,
                                    useGpuDelegate = true,
                                    useAndroidNN = true,
                                    numThreads = 1
                                )
                                Log.d("ROI", "${System.currentTimeMillis() - startTime}")
                                val ck1 = System.currentTimeMillis()

                                Log.d("Recalculate", "Serial ROI prediction done, count: ${roiListSerial.size}")

                                val (filteredMriSequence, filteredRois) = filterImagesAndRois(fullSequence, roiListSerial)

                                val seedListSerial = sequentialSeedPredictor.predictSeed(
                                    filteredMriSequence,
                                    filteredRois,
                                    useGpuDelegate = true,
                                    useAndroidNN = true,
                                    numThreads = 1
                                ).toList()
                                Log.d("Seed", "${System.currentTimeMillis() - ck1}")
                                val ck2 = System.currentTimeMillis()


                                Log.d("Recalculate", "Serial seed prediction done, count: ${seedListSerial.size}")

                                ResultsDataHolder.cancerVolume = sequentialFuzzySystem.estimateVolume(
                                    filteredMriSequence,
                                    filteredRois,
                                    seedListSerial,
                                    currentAlphaCutValue
                                )
                                Log.d("Fuzzy", "${System.currentTimeMillis() - ck2}")
                                Log.d("Recalculate", "Serial volume estimation done, volume: ${ResultsDataHolder.cancerVolume.volume}")

                                ResultsDataHolder.tumorMriSequence = filteredMriSequence
                                ResultsDataHolder.tumorRoiList = filteredRois
                                ResultsDataHolder.seedList = seedListSerial.toTypedArray()

                                elapsed = System.currentTimeMillis() - startTime

                            } else if (selectedMode.equals("GRPC", ignoreCase = true)) {
                                startTime = System.currentTimeMillis()
                                Log.d("Recalculate", "Starting GRPC volume estimation")

                                val volumeEstimator = VolumeEstimator(
                                    fuzzySystem = parallelFuzzySystem,
                                    seedPredictor = seedPredictor,
                                    roiPredictor = roiPredictor,
                                    network = SharedViewModel.getInstance(activityContext).network
                                )

                                val (cancerVolume, filteredMriSeq) = volumeEstimator.estimateVolumeGrpc(
                                    mriSeq = fullSequence,
                                    alphaCutValue = currentAlphaCutValue
                                )
                                Log.d("Recalculate", "GRPC volume estimation done, volume: ${cancerVolume.volume}")

                                // Use the filtered data from gRPC result for display
                                ResultsDataHolder.tumorMriSequence = filteredMriSeq
                                ResultsDataHolder.cancerVolume.volume = cancerVolume.volume

                                elapsed = System.currentTimeMillis() - startTime

                            } else {
                                Log.e("Recalculate", "Unknown mode detected, throwing exception")
                                throw IllegalStateException("Unknown mode: $selectedMode")
                            }

                            Log.d("Recalculate", "Completed recalculation in mode: $selectedMode, Images: ${ResultsDataHolder.tumorMriSequence!!.images.size}, Time: ${elapsed}ms")

                            withContext(Dispatchers.Main) {
                                hideLoadingState(loadingOverlay, fuzzyCalculateButton)
                                fuzzyCalculateButton.isEnabled = true
                                resultsRecalculateButton.isEnabled = true

                                resultsTumorVolume.text = "Tumor Volume: ${ResultsDataHolder.cancerVolume.volume} mm³"
                                resultsPatientName.text = "$selectedMode Computation Time: ${elapsed}ms"

                                ResultsDataHolder.setWholeProcessTime(selectedMode, elapsed)

                                showWholeProcessRow = true
                                updateReportUI(view, resultsReportContainer, showWholeProcessRow)
                                showResultsLayout()

                                Log.d("Recalculate", "Calling loadCurrentResultsImage")
                                loadCurrentResultsImage(resultsMriImage)
                            }

                        } catch (e: Exception) {
                            Log.e("FuzzyAndResultScreen", "Error during recalculation in mode: $selectedMode", e)
                            withContext(Dispatchers.Main) {
                                hideLoadingState(loadingOverlay, fuzzyCalculateButton)
                                fuzzyCalculateButton.isEnabled = true
                                resultsRecalculateButton.isEnabled = true
                                Toast.makeText(activityContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }



                fun navigateImage(direction: Int) {
                    val totalSlices = ResultsDataHolder.tumorMriSequence!!.images.size
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

                fun showLoadingState() {
                    loadingOverlay.alpha = 0f
                    loadingOverlay.visibility = View.VISIBLE
                    loadingOverlay.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                    fuzzyCalculateButton.isEnabled = false
                }

                fun hideLoadingState() {
                    loadingOverlay.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            loadingOverlay.visibility = View.GONE
                            fuzzyCalculateButton.isEnabled = true
                        }
                        .start()
                }


                btnParallel.setOnClickListener { setMode("Parallel") }
                btnSerial.setOnClickListener { setMode("Serial") }

                val resultsBtnParallel = view.findViewById<Button>(R.id.results_btn_parallel)
                val resultsBtnSerial = view.findViewById<Button>(R.id.results_btn_serial)
                val resultsBtnGrpc = view.findViewById<Button>(R.id.results_btn_grpc)

                resultsBtnParallel.setOnClickListener { setMode("Parallel") }
                resultsBtnSerial.setOnClickListener { setMode("Serial") }
                resultsBtnGrpc.setOnClickListener { setMode("GRPC") }

                // Set up recalculate button
                resultsRecalculateButton.setOnClickListener {
                    showLoadingState()
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
                    if (ResultsDataHolder.fullRoiList.isEmpty() || ResultsDataHolder.seedList.isEmpty()) {
                        Toast.makeText(ctx, "No ROI or Seed data available!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    if (fuzzySystem == null) {
                        Toast.makeText(ctx, "FuzzySystem not initialized!", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    showLoadingState(loadingOverlay, fuzzyCalculateButton)
                    fuzzyCalculateButton.isEnabled = false
                    val startTime = System.currentTimeMillis()
                    CoroutineScope(Dispatchers.Default).launch {
                        val alphaCut = currentAlphaCutValue
                        ResultsDataHolder.cancerVolume = fuzzySystem!!.estimateVolume(
                            ResultsDataHolder.tumorMriSequence!!,
                            ResultsDataHolder.tumorRoiList,
                            ResultsDataHolder.seedList.toList(), alphaCut)

                        val elapsed = System.currentTimeMillis() - startTime
                        withContext(Dispatchers.Main) {
                            hideLoadingState(loadingOverlay, fuzzyCalculateButton)
                            fuzzyCalculateButton.isEnabled = true
                            fuzzyShowResultsButton.isEnabled = true
                            resultsTumorVolume.text = "Tumor Volume: ${ResultsDataHolder.cancerVolume.volume} mm³"
                            updateShowResultsButtonStyle(fuzzyShowResultsButton, true)
                            fuzzyCalculateButton.text = "Re-calculate Volume"
                            fuzzyTimeText.text = "${selectedMode} Time: ${elapsed}ms"
                            ResultsDataHolder.addOrUpdateReportEntry("Fuzzy", selectedMode, elapsed)
                            updateReportUI(view, fuzzyReportContainer, false)
                        }
                    }
                }

                fuzzyShowResultsButton.setOnClickListener {
                    showWholeProcessRow = false
                    updateReportUI(view, resultsReportContainer, showWholeProcessRow)
                    showResultsLayout()
                    loadCurrentResultsImage(resultsMriImage)
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

                showLoadingState()
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.Main) {
                        // Initial setup
                        hideLoadingState()
                        setMode("Parallel")
                        if (ResultsDataHolder.tumorMriSequence != null
                            && ResultsDataHolder.tumorMriSequence!!.images.isNotEmpty()) {
                            loadCurrentImage(fuzzyMriImage, fuzzyPrevImage, fuzzyNextImage, resultsPrevImage, resultsNextImage)
                            updateImageCount(fuzzyImageCount, resultsImageCount)
                        } else {
                            Toast.makeText(ctx, "No valid slices found for display.", Toast.LENGTH_LONG).show()
                        }
                        showFuzzyLayout()
                    }
                }

                fuzzyShowResultsButton.isEnabled = false
                updateShowResultsButtonStyle(fuzzyShowResultsButton, false)
                fuzzyCalculateButton.text = "Calculate Volume"
                fuzzyTimeText.text = ""
                updateReportUI(view, fuzzyReportContainer, false)
                updateReportUI(view, resultsReportContainer, false)

                view
            }
        )
    }

    private fun updateReportUI(view: View, reportContainer: LinearLayout, showWholeProcessRow: Boolean) {
        reportContainer.removeAllViews()
        val context = view.context
        
        // Check if gRPC column should be shown
        val hasGrpcData = reportEntries.any { it.grpcTime != null } || 
                         (showWholeProcessRow && ResultsDataHolder.wholeProcessGrpcTime != null)
        
        // Center the table horizontally
        reportContainer.gravity = android.view.Gravity.CENTER_HORIZONTAL
        // Table layout params
        val tableLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        // Fixed column width in pixels (adjust as needed)
        val COLUMN_WIDTH = 270
        // Add table header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            layoutParams = tableLayoutParams
            setBackgroundResource(android.R.color.white)
        }
        val cellParams = TableRow.LayoutParams(
            COLUMN_WIDTH,
            TableRow.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(2, 2, 2, 2)
        }
        val headerStyle = { tv: TextView ->
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
            tv.textSize = 16f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(20, 14, 20, 14)
            tv.layoutParams = cellParams
            tv.setBackgroundColor(Color.LTGRAY)
        }
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        val stepHeader = TextView(context).apply { text = "Step"; headerStyle(this) }
        headerRow.addView(stepHeader)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        val parallelHeader = TextView(context).apply { text = "Parallel Time (ms)"; headerStyle(this) }
        headerRow.addView(parallelHeader)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        val serialHeader = TextView(context).apply { text = "Serial Time (ms)"; headerStyle(this) }
        headerRow.addView(serialHeader)
        
        // Only add gRPC header if there's gRPC data
        if (hasGrpcData) {
            headerRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            val grpcHeader = TextView(context).apply { text = "gRPC Time (ms)"; headerStyle(this) }
            headerRow.addView(grpcHeader)
            headerRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
        }
        
        reportContainer.addView(headerRow)
        val rowStyle = { tv: TextView ->
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
            tv.textSize = 15f
            tv.setTextColor(Color.WHITE)
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(20, 10, 20, 10)
            tv.layoutParams = cellParams
            tv.setBackgroundColor(Color.rgb(7, 30, 34))
        }
        reportEntries
            .filter { it.step != "Whole process" }
            .forEachIndexed { idx, entry ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, 0)
                    layoutParams = tableLayoutParams
                    if (idx % 2 == 0) setBackgroundColor(0xFFE0E0E0.toInt())
                }
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
                val stepView = TextView(context).apply { text = entry.step; rowStyle(this) }
                row.addView(stepView)
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
                val parallelView = TextView(context).apply { text = entry.parallelTime?.toString() ?: "-"; rowStyle(this) }
                row.addView(parallelView)
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
                val serialView = TextView(context).apply { text = entry.serialTime?.toString() ?: "-"; rowStyle(this) }
                row.addView(serialView)
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
                
                // Only add gRPC column if there's gRPC data
                if (hasGrpcData) {
                    val grpcView = TextView(context).apply { text = entry.grpcTime?.toString() ?: "-"; rowStyle(this) }
                    row.addView(grpcView)
                    row.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(Color.WHITE)
                    })
                }
                reportContainer.addView(row)
                // Add horizontal separator after each row
                reportContainer.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    setBackgroundColor(Color.WHITE)
                })
            }
        // Only add the 'Whole process' row if showWholeProcessRow is true
        if (showWholeProcessRow) {
            val totalParallel = ResultsDataHolder.wholeProcessParallelTime ?: 0
            val totalSerial = ResultsDataHolder.wholeProcessSerialTime ?: 0
            val totalGrpc = ResultsDataHolder.wholeProcessGrpcTime ?: 0
            val totalRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 0)
                layoutParams = tableLayoutParams
                setBackgroundColor(0xFFB0BEC5.toInt())
            }
            totalRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            val totalStep = TextView(context).apply { text = "Whole process"; rowStyle(this) }
            totalRow.addView(totalStep)
            totalRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            val totalParallelView = TextView(context).apply { text = if (totalParallel > 0) totalParallel.toString() else "-"; rowStyle(this) }
            totalRow.addView(totalParallelView)
            totalRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            val totalSerialView = TextView(context).apply { text = if (totalSerial > 0) totalSerial.toString() else "-"; rowStyle(this) }
            totalRow.addView(totalSerialView)
            
            // Only add gRPC column if there's gRPC data
            if (hasGrpcData) {
                totalRow.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
                val totalGrpcView = TextView(context).apply { text = if (totalGrpc > 0) totalGrpc.toString() else "-"; rowStyle(this) }
                totalRow.addView(totalGrpcView)
                totalRow.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.WHITE)
                })
            }
            reportContainer.addView(totalRow)
            reportContainer.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.WHITE)
            })
        }
    }

    private fun updateShowResultsButtonStyle(button: Button, isEnabled: Boolean) {
        if (isEnabled) {
            button.background = getDrawable(R.drawable.rounded_bg)?.apply {
                setTint(Color.parseColor("#B8BEBF"))
            }
            button.setTextColor(Color.parseColor("#071E22"))
        } else {
            button.background = getDrawable(R.drawable.rounded_bg)?.apply {
                setTint(Color.parseColor("#CCCCCC"))
            }
            button.setTextColor(Color.parseColor("#666666"))
        }
    }
}