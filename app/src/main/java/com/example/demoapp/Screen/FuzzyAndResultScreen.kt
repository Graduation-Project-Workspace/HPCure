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
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.demoapp.Core.ParallelFuzzySystem
import com.example.demoapp.Core.ParallelRoiPredictor
import com.example.demoapp.Core.ParallelSeedPredictor
import com.example.demoapp.Core.SerialFuzzySystem
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
class FuzzyAndResultScreen : AppCompatActivity() {

    // Views
    private lateinit var btnParallel: Button
    private lateinit var btnSerial: Button
    private lateinit var fuzzyMriImage: ImageView
    private lateinit var fuzzyAlphaCutValue: TextView
    private lateinit var fuzzyAlphaCutSlider: SeekBar
    private lateinit var fuzzyImageCount: TextView
    private lateinit var fuzzyPrevImage: ImageButton
    private lateinit var fuzzyNextImage: ImageButton
    private lateinit var fuzzyCalculateButton: Button
    private lateinit var fuzzyCalculateVolumeButton: Button
    private lateinit var fuzzyPatientName: TextView
    private lateinit var fuzzyBackButton: ImageButton
    private lateinit var resultsMriImage: ImageView
    private lateinit var resultsImageCount: TextView
    private lateinit var resultsPrevImage: ImageButton
    private lateinit var resultsNextImage: ImageButton
    private lateinit var resultsTumorVolume: TextView
    private lateinit var resultsPatientName: TextView
    private lateinit var resultsRecalculateButton: Button
    private lateinit var resultsBackButton: ImageButton
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var fuzzyLayout: RelativeLayout
    private lateinit var resultsLayout: RelativeLayout

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
            initializeApp()
        } else {
            Toast.makeText(this, "Storage permission required to load images", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fuzzy_and_result_screen)

        roiTimeTaken = intent.getLongExtra("roi_time_taken", 0)
        seedTimeTaken = intent.getLongExtra("seed_time_taken", 0)

        // Extract roi_list and seed_list from intent
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
        originalMriSequence = MRISequence(images = bitmaps, metadata = FileManager.getDicomMetadata())
        tumorMriSequence = MRISequence(images = emptyList(), metadata = FileManager.getDicomMetadata())

        for ((index, roi) in roiList.withIndex()) {
            if (roi.score > 0.3) {
                tumorMriSequence.images+= originalMriSequence.images[index]
                tumorRoiList+= roi
            }
        }

        // Initialize predictors after context is available
        roiPredictor = ParallelRoiPredictor(this)
        seedPredictor = ParallelSeedPredictor(this)

        parallelFuzzySystem = ParallelFuzzySystem()
        sequentialFuzzySystem = SerialFuzzySystem()

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

    private fun initializeApp() {
        initializeViews()
        setMode("Parallel")
        setupImageNavigation()
        setupAlphaCutControl()
        setupCalculateButton()
        setupBackButtons()
        loadCurrentImage()
        updateImageCount()
        showFuzzyLayout()
    }

    private fun initializeViews() {
        fuzzyLayout = findViewById(R.id.fuzzy_layout)
        fuzzyMriImage = findViewById(R.id.fuzzy_mri_image)
        fuzzyAlphaCutValue = findViewById(R.id.fuzzy_alpha_cut_value)
        fuzzyAlphaCutSlider = findViewById(R.id.fuzzy_alpha_cut_slider)
        fuzzyImageCount = findViewById(R.id.fuzzy_image_count)
        fuzzyPrevImage = findViewById(R.id.fuzzy_prev_image)
        fuzzyNextImage = findViewById(R.id.fuzzy_next_image)
        fuzzyCalculateButton = findViewById(R.id.fuzzy_calculate_button)
        fuzzyCalculateVolumeButton = findViewById(R.id.fuzzy_calculate_volume_button)
        fuzzyPatientName = findViewById(R.id.fuzzy_patient_name)
        fuzzyBackButton = findViewById(R.id.fuzzy_back_button)
        btnParallel = findViewById(R.id.btn_parallel)
        btnSerial = findViewById(R.id.btn_serial)
        resultsLayout = findViewById(R.id.results_layout)
        resultsMriImage = findViewById(R.id.results_mri_image)
        resultsImageCount = findViewById(R.id.results_image_count)
        resultsPrevImage = findViewById(R.id.results_prev_image)
        resultsNextImage = findViewById(R.id.results_next_image)
        resultsTumorVolume = findViewById(R.id.results_tumor_volume)
        resultsPatientName = findViewById(R.id.results_patient_name)
        resultsRecalculateButton = findViewById(R.id.results_recalculate_button)
        resultsBackButton = findViewById(R.id.results_back_button)
        loadingOverlay = findViewById(R.id.loading_overlay)

        btnParallel.setOnClickListener { setMode("Parallel") }
        btnSerial.setOnClickListener { setMode("Serial") }

        resultsRecalculateButton.setOnClickListener {
            performRecalculation()
        }
    }

    private fun showFuzzyLayout() {
        fuzzyLayout.visibility = View.VISIBLE
        resultsLayout.visibility = View.GONE
    }

    private fun showResultsLayout() {
        fuzzyLayout.visibility = View.GONE
        resultsLayout.visibility = View.VISIBLE
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        fuzzySystem = if (mode == "Parallel") parallelFuzzySystem else sequentialFuzzySystem

        btnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
        btnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

        btnSerial.setBackgroundColor(Color.parseColor(if (mode == "Serial") "#B0BEC5" else "#455A64"))
        btnSerial.setTextColor(Color.parseColor(if (mode == "Serial") "#000000" else "#FFFFFF"))
    }

    private fun setupCalculateButton() {
        fuzzyCalculateButton.setOnClickListener {
            if (roiList.isEmpty() || seedList.isEmpty()) {
                Toast.makeText(this, "No ROI or Seed data available!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            if (fuzzySystem == null) {
                Toast.makeText(this, "FuzzySystem not initialized!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            showLoadingState()
            val startTime = System.currentTimeMillis()

            CoroutineScope(Dispatchers.Default).launch {
                val alphaCut = currentAlphaCutValue
                cancerVolume = fuzzySystem!!.estimateVolume(tumorMriSequence, tumorRoiList, seedList.toList(), alphaCut)
                val elapsed = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    hideLoadingState()
                    val totalTime = roiTimeTaken + seedTimeTaken + elapsed

                    resultsTumorVolume.text = "Tumor Volume: ${cancerVolume.volume} mm³"
                    resultsPatientName.text = "Total Time: ${totalTime}ms"

                    showResultsLayout()
                    loadCurrentResultsImage()
                }
            }
        }
    }

    private fun performRecalculation() {
        showLoadingState()

        fuzzySystem = ParallelFuzzySystem()

        CoroutineScope(Dispatchers.Default).launch {
            val alphaCut = currentAlphaCutValue
            val startTime = System.currentTimeMillis()

            roiList = roiPredictor.predictRoi(tumorMriSequence)
            seedList = seedPredictor.predictSeed(tumorMriSequence, tumorRoiList)

            cancerVolume = fuzzySystem!!.estimateVolume(tumorMriSequence, tumorRoiList, seedList.toList(), alphaCut)

            val elapsed = System.currentTimeMillis() - startTime
            val totalTime = roiTimeTaken + seedTimeTaken + elapsed

            withContext(Dispatchers.Main) {
                hideLoadingState()
                resultsTumorVolume.text = "Tumor Volume: ${cancerVolume.volume} mm³"
                resultsPatientName.text = "Total Time: ${totalTime}ms"
                loadCurrentResultsImage()
            }
        }
    }

    private fun setupBackButtons() {
        fuzzyBackButton.setOnClickListener {
            finish()
        }

        resultsBackButton.setOnClickListener {
            showFuzzyLayout()
        }
    }

    private fun setupImageNavigation() {
        // Fuzzy navigation
        fuzzyPrevImage.setOnClickListener { navigateImage(-1) }
        fuzzyNextImage.setOnClickListener { navigateImage(1) }

        // Results navigation
        resultsPrevImage.setOnClickListener { navigateImage(-1) }
        resultsNextImage.setOnClickListener { navigateImage(1) }
    }

    private fun navigateImage(direction: Int) {
        sliceIndex += direction
        updateImageCount()
        loadCurrentImage()
        if (resultsLayout.visibility == View.VISIBLE) {
            loadCurrentResultsImage()
        }
    }

    private fun loadCurrentImage() {
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

        updateNavigationButtons()
    }

    private fun loadCurrentResultsImage() {

        if (::cancerVolume.isInitialized) {
            val displayBitmap = highlightCancerArea(tumorMriSequence.images[sliceIndex], sliceIndex)
            resultsMriImage.setImageBitmap(displayBitmap)
        }
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

    private fun showLoadingState() {
        loadingOverlay.visibility = View.VISIBLE
        fuzzyCalculateButton.isEnabled = false
        resultsRecalculateButton.isEnabled = false
    }

    private fun hideLoadingState() {
        loadingOverlay.visibility = View.GONE
        fuzzyCalculateButton.isEnabled = true
        resultsRecalculateButton.isEnabled = true
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


    private fun updateImageCount() {
        val countText = "${sliceIndex + 1}/${tumorMriSequence.images.size}"
        fuzzyImageCount.text = countText
        resultsImageCount.text = countText
    }

    private fun updateNavigationButtons() {
        val currentIndex = sliceIndex + 1
        val totalFiles = tumorMriSequence.images.size

        // Update fuzzy navigation
        fuzzyPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        fuzzyNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE

        // Update results navigation
        resultsPrevImage.visibility = if (currentIndex > 1) View.VISIBLE else View.INVISIBLE
        resultsNextImage.visibility = if (currentIndex < totalFiles) View.VISIBLE else View.INVISIBLE
    }

    private fun setupAlphaCutControl() {
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
    }

    private fun updateAlphaCutDisplay() {
        fuzzyAlphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
    }

    /*
    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanup()
    }
     */
}