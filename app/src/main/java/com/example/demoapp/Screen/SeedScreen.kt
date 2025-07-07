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
import com.example.demoapp.Core.ParallelSeedPredictor
import com.example.demoapp.Core.SequentialSeedPredictor
import com.example.demoapp.R
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.demoapp.Utils.ResultsDataHolder
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.ROI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class SeedScreen : AppCompatActivity() {

    private lateinit var mriImage: ImageView
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var predictButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var btnParallel: Button
    private lateinit var btnSerial: Button
    private lateinit var btnGpu: Button
    private lateinit var mainContent: RelativeLayout
    private lateinit var patientName: TextView

    private lateinit var seedPredictor: ISeedPredictor
    private lateinit var parallelSeedPredictor: ParallelSeedPredictor
    private lateinit var sequentialSeedPredictor: SequentialSeedPredictor

    private var selectedMode: String = "Parallel"
    private var roiTimeTaken: Long = 0

    private val context = this
    private var sliceIndex = 0
    private var isGPUEnabled = true
    private var hasPredicted = false

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
        setContentView(R.layout.shared_roi_seed_screen)

        parallelSeedPredictor = ParallelSeedPredictor
        sequentialSeedPredictor = SequentialSeedPredictor
        seedPredictor = parallelSeedPredictor

        roiTimeTaken = intent.getLongExtra("roi_time_taken", 0)

        if (ResultsDataHolder.fullMriSequence == null || ResultsDataHolder.fullMriSequence!!.images.isEmpty()) {
            Toast.makeText(this, "No images loaded! Returning to upload screen.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, UploadScreen::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return
        }

        if (checkStoragePermission()) {
            initializeApp()
        } else {
            requestStoragePermission()
        }

        val predictButton = findViewById<Button>(R.id.predict_button)
        val nextButton = findViewById<Button>(R.id.next_button)
        predictButton.text = "PREDICT SEED"
        hasPredicted = false
        nextButton.isEnabled = false
        updateNextButtonStyle(nextButton, false)

        // Add ROI report entry if passed from previous screen and not present
        val roiTime = intent.getLongExtra("roi_time_taken", -1)
        val roiMode = if (intent.getStringExtra("roi_mode") != null) intent.getStringExtra("roi_mode")!! else "Parallel"
        if (roiTime > 0 && ResultsDataHolder.reportEntries.none { it.step == "ROI" }) {
            ResultsDataHolder.addOrUpdateReportEntry("ROI", roiMode, roiTime)
        }
        updateReportUI()

        nextButton.setOnClickListener {
            val intent = Intent(this, FuzzyAndResultScreen::class.java).apply {
                putExtra("roi_time_taken", roiTimeTaken)
                putExtra("seed_time_taken", ResultsDataHolder.reportEntries.findLast { it.step == "Seed" }?.parallelTime ?: 0)
                putExtra("shouldCleanup", false)
            }
            startActivity(intent)
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
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

        if (!GpuDelegateHelper().isGpuDelegateAvailable) {
            btnGpu.visibility = View.GONE
        }

        setMode("Parallel")
        toggleGpu()

        showLoadingState()
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                hideLoadingState()
                setupImageNavigation()
                loadCurrentImage()
                updateImageCount()
            }
        }

        setupPredictButton()
        setupBackButton()
    }

    private fun initializeViews() {
        mriImage = findViewById(R.id.mri_image)
        imageCount = findViewById(R.id.image_count)
        prevImage = findViewById(R.id.prev_image)
        nextImage = findViewById(R.id.next_image)
        loadingOverlay = findViewById(R.id.loading_overlay)
        predictButton = findViewById(R.id.predict_button)
        backButton = findViewById(R.id.back_button)
        mainContent = findViewById(R.id.main_content)
        btnParallel = findViewById(R.id.btn_parallel)
        btnSerial = findViewById(R.id.btn_serial)
        btnGpu = findViewById(R.id.btn_gpu)
        patientName = findViewById(R.id.patient_name)

        btnParallel.setOnClickListener { setMode("Parallel") }
        btnSerial.setOnClickListener { setMode("Serial") }
        btnGpu.setOnClickListener { toggleGpu() }
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        seedPredictor = if (mode == "Parallel") parallelSeedPredictor else sequentialSeedPredictor

        btnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
        btnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

        btnSerial.setBackgroundColor(Color.parseColor(if (mode == "Serial") "#B0BEC5" else "#455A64"))
        btnSerial.setTextColor(Color.parseColor(if (mode == "Serial") "#000000" else "#FFFFFF"))
    }

    private fun toggleGpu() {
        isGPUEnabled = !isGPUEnabled
        btnGpu.setBackgroundColor(Color.parseColor(if (isGPUEnabled) "#B0BEC5" else "#455A64"))
        btnGpu.setTextColor(Color.parseColor(if (isGPUEnabled) "#000000" else "#FFFFFF"))
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            val intent = Intent(this, RoiScreen::class.java)
            intent.putExtra("shouldCleanup", false)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun setupPredictButton() {
        val predictButton = findViewById<Button>(R.id.predict_button)
        val nextButton = findViewById<Button>(R.id.next_button)
        predictButton.setOnClickListener {
            if (ResultsDataHolder.fullRoiList.isEmpty()) {
                Toast.makeText(this, "No ROI received! Please return to ROI screen.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showLoadingState()
            val mode = selectedMode // capture mode at prediction time
            CoroutineScope(Dispatchers.IO).launch {
                val startTime = System.currentTimeMillis()

                try {
                    ResultsDataHolder.seedList = seedPredictor.predictSeed(
                        mriSeq = ResultsDataHolder.tumorMriSequence!!,
                        roiList = ResultsDataHolder.tumorRoiList,
                        useGpuDelegate = isGPUEnabled,
                        numThreads = 1
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideLoadingState()
                        Toast.makeText(context, "Error predicting seeds: ${e.message}", Toast.LENGTH_LONG).show()
                        return@withContext
                    }
                }

                val endTime = System.currentTimeMillis()
                val timeTaken = endTime - startTime

                withContext(Dispatchers.Main) {
                    ResultsDataHolder.addOrUpdateReportEntry("Seed", mode, timeTaken)
                    loadCurrentImage()
                    updateReportUI()
                    hideLoadingState()
                    hasPredicted = true
                    predictButton.text = "RE-PREDICT SEED"
                    nextButton.isEnabled = true
                    updateNextButtonStyle(nextButton, true)
                }
            }
        }
    }

    private fun updateReportUI() {
        val reportContainer = findViewById<LinearLayout>(R.id.report_container)
        reportContainer.removeAllViews()
        val context = this
        // Center the table horizontally
        reportContainer.gravity = android.view.Gravity.CENTER_HORIZONTAL
        // Table layout params
        val tableLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        // Fixed column width in pixels (adjust as needed)
        val COLUMN_WIDTH = 280
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
            tv.textSize = 18f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(24, 16, 24, 16)
            tv.layoutParams = cellParams
            tv.setBackgroundColor(Color.LTGRAY)
        }
        val stepHeader = TextView(context).apply { text = "Step"; headerStyle(this) }
        val parallelHeader = TextView(context).apply { text = "Parallel Time (ms)"; headerStyle(this) }
        val serialHeader = TextView(context).apply { text = "Serial Time (ms)"; headerStyle(this) }
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        headerRow.addView(stepHeader)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        headerRow.addView(parallelHeader)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        headerRow.addView(serialHeader)
        headerRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
        })
        reportContainer.addView(headerRow)
        // Add data rows
        val rowStyle = { tv: TextView ->
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
            tv.textSize = 17f
            tv.setTextColor(Color.WHITE)
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(24, 12, 24, 12)
            tv.layoutParams = cellParams
            tv.setBackgroundColor(Color.rgb(7, 30, 34))
        }
        ResultsDataHolder.reportEntries.forEachIndexed { idx, entry ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 0)
                layoutParams = tableLayoutParams
                if (idx % 2 == 0) setBackgroundColor(0xFFE0E0E0.toInt())
            }
            val stepView = TextView(context).apply { text = entry.step; rowStyle(this) }
            val parallelView = TextView(context).apply { text = entry.parallelTime?.toString() ?: "-"; rowStyle(this) }
            val serialView = TextView(context).apply { text = entry.serialTime?.toString() ?: "-"; rowStyle(this) }
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            row.addView(stepView)
            // Add vertical separator
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            row.addView(parallelView)
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            row.addView(serialView)
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.WHITE)
            })
            reportContainer.addView(row)
            // Add horizontal separator after each row
            reportContainer.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.WHITE)
            })
        }
    }

    private fun showLoadingState() {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate().alpha(1f).setDuration(200).start()
        predictButton.isEnabled = false
    }

    private fun hideLoadingState() {
        loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            loadingOverlay.visibility = View.GONE
            predictButton.isEnabled = true
        }.start()
    }

    private fun updateNextButtonStyle(button: Button, isEnabled: Boolean) {
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

    private fun loadCurrentImage() {
        val displayBitmap = if (ResultsDataHolder.tumorRoiList.size > sliceIndex && ResultsDataHolder.seedList.size > sliceIndex) {
            drawSeedPointInsideNormalizedRoi(
                ResultsDataHolder.tumorMriSequence!!.images[sliceIndex],
                ResultsDataHolder.tumorRoiList[sliceIndex],
                ResultsDataHolder.seedList[sliceIndex]
            )
        } else if (ResultsDataHolder.tumorRoiList.size > sliceIndex) {
            drawNormalizedRoiOnly(
                ResultsDataHolder.tumorMriSequence!!.images[sliceIndex],
                ResultsDataHolder.tumorRoiList[sliceIndex])
        } else {
            ResultsDataHolder.tumorMriSequence!!.images[sliceIndex]
        }

        mriImage.apply {
            setImageBitmap(displayBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        updateNavigationButtons()
    }

    private fun drawNormalizedRoiOnly(bitmap: Bitmap, roi: ROI): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(roi.xMin.toFloat(), roi.yMin.toFloat(), roi.xMax.toFloat(), roi.yMax.toFloat(), paint)
        return mutableBitmap
    }

    private fun drawSeedPointInsideNormalizedRoi(
        bitmap: Bitmap,
        roi: ROI,
        seed: Pair<Int, Int>
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val roiPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(
            roi.xMin.toFloat(),
            roi.yMin.toFloat(),
            roi.xMax.toFloat(),
            roi.yMax.toFloat(),
            roiPaint)

        val seedPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        canvas.drawCircle(seed.first.toFloat(), seed.second.toFloat(), 6f, seedPaint)

        return mutableBitmap
    }

    private fun setupImageNavigation() {
        prevImage.setOnClickListener {
            sliceIndex--
            loadCurrentImage()
            updateImageCount()
        }
        nextImage.setOnClickListener {
            sliceIndex++
            loadCurrentImage()
            updateImageCount()
        }
    }

    private fun updateImageCount() {
        imageCount.text = "${sliceIndex + 1}/${ResultsDataHolder.tumorMriSequence!!.images.size}"
    }

    private fun updateNavigationButtons() {
        prevImage.visibility = if (sliceIndex > 0) ImageButton.VISIBLE else ImageButton.INVISIBLE
        nextImage.visibility = if (sliceIndex < ResultsDataHolder.tumorMriSequence!!.images.size - 1) ImageButton.VISIBLE else ImageButton.INVISIBLE
    }
}
