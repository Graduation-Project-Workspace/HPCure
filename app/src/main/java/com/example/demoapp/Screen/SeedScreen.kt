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
import com.example.demoapp.Utils.FileManager
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.MRISequence
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
    private lateinit var confirmButton: Button
    private lateinit var customizeButton: Button

    private lateinit var seedPredictor: ISeedPredictor
    private lateinit var parallelSeedPredictor: ParallelSeedPredictor
    private lateinit var sequentialSeedPredictor: SequentialSeedPredictor

    private lateinit var originalMriSequence: MRISequence
    private lateinit var tumorMriSequence: MRISequence
    private var selectedMode: String = "Parallel"
    private var roiTimeTaken: Long = 0

    private var roiList: List<ROI> = emptyList()
    private var tumorRoiList = emptyList<ROI>()
    private var seedList: Array<Pair<Int, Int>> = emptyArray()
    private val context = this
    private var sliceIndex = 0
    private var isGPUEnabled = true

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

        parallelSeedPredictor = ParallelSeedPredictor(context)
        sequentialSeedPredictor = SequentialSeedPredictor(context)
        seedPredictor = parallelSeedPredictor

        roiTimeTaken = intent.getLongExtra("roi_time_taken", 0)
        @Suppress("UNCHECKED_CAST")
        intent.getSerializableExtra("roi_list")?.let { extra ->
            val incomingRoiList = extra as? List<ROI>
            if (incomingRoiList != null) {
                roiList = incomingRoiList
            }
        }

        if (FileManager.getAllFiles().isEmpty()) {
            Toast.makeText(this, "No images loaded! Returning to upload screen.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, UploadScreen::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return
        }

        val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
            FileManager.getProcessedImage(this, file)
        }
        originalMriSequence = MRISequence(images = bitmaps, metadata = FileManager.getDicomMetadata())
        tumorMriSequence = MRISequence(images = emptyList(), metadata = FileManager.getDicomMetadata())

        for ((index, roi) in roiList.withIndex()) {
            if (roi.score > 0.3) {
                tumorMriSequence.images+= originalMriSequence.images[index]
                tumorRoiList+= roi
            }
        }

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
        predictButton.text = "Predict Seed"

        if (FileManager.getAllFiles().isNotEmpty()) {
            setupImageNavigation()
            loadCurrentImage()
            updateImageCount()
        } else {
            Toast.makeText(this, "No images available to display", Toast.LENGTH_SHORT).show()
            predictButton.isEnabled = false
        }
        setupPredictButton()
        setupBackButton()
        setupConfirmButton()
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
        confirmButton = findViewById(R.id.confirm_roi_button)
        confirmButton.text = "Confirm Seed"
        customizeButton = findViewById(R.id.customize_button)

        btnParallel.setOnClickListener { setMode("Parallel") }
        btnSerial.setOnClickListener { setMode("Sequential") }
        btnGpu.setOnClickListener { toggleGpu() }
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        seedPredictor = if (mode == "Parallel") parallelSeedPredictor else sequentialSeedPredictor

        btnParallel.setBackgroundColor(Color.parseColor(if (mode == "Parallel") "#B0BEC5" else "#455A64"))
        btnParallel.setTextColor(Color.parseColor(if (mode == "Parallel") "#000000" else "#FFFFFF"))

        btnSerial.setBackgroundColor(Color.parseColor(if (mode == "Sequential") "#B0BEC5" else "#455A64"))
        btnSerial.setTextColor(Color.parseColor(if (mode == "Sequential") "#000000" else "#FFFFFF"))
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
        predictButton.setOnClickListener {
            //btnGpu.visibility = View.GONE
            //btnParallel.visibility = View.GONE
            //btnSerial.visibility = View.GONE

            if (roiList.isEmpty()) {
                Toast.makeText(this, "No ROI received! Please return to ROI screen.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showLoadingState()
            CoroutineScope(Dispatchers.IO).launch {
                val startTime = System.currentTimeMillis()

                try {
                    seedList = seedPredictor.predictSeed(
                        mriSeq = tumorMriSequence,
                        roiList = tumorRoiList,
                        useGpuDelegate = isGPUEnabled
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
                    hideLoadingState()
                    loadCurrentImage()
                    patientName.text = "Time Taken: $timeTaken ms"
                    confirmButton.visibility = View.VISIBLE
                    customizeButton.visibility = View.VISIBLE
                    predictButton.text = "Re-Predict Seed"
                }
            }
        }
    }

    private fun setupConfirmButton() {
        confirmButton.setOnClickListener {
            val seedTimeTaken = patientName.text.toString().replace("Time Taken: ", "").replace(" ms", "").toLong()
            val intent = Intent(this, FuzzyAndResultScreen::class.java).apply {
                putExtra("seed_list", seedList)
                putExtra("roi_list", ArrayList(roiList))
                putExtra("roi_time_taken", roiTimeTaken)
                putExtra("seed_time_taken", seedTimeTaken)
                putExtra("shouldCleanup", false)
            }
            startActivity(intent)
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

    private fun loadCurrentImage() {
        val displayBitmap = if (roiList.size > sliceIndex && seedList.size > sliceIndex) {
            drawSeedPointInsideNormalizedRoi(
                tumorMriSequence.images[sliceIndex],
                tumorRoiList[sliceIndex],
                seedList[sliceIndex]
            )
        } else if (roiList.size > sliceIndex) {
            drawNormalizedRoiOnly(tumorMriSequence.images[sliceIndex], tumorRoiList[sliceIndex])
        } else {
            tumorMriSequence.images[sliceIndex]
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
        canvas.drawRect(roi.xMin.toFloat(), roi.yMin.toFloat(), roi.xMax.toFloat(), roi.yMax.toFloat(), roiPaint)

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
        imageCount.text = "${sliceIndex + 1}/${tumorMriSequence.images.size}"
    }

    private fun updateNavigationButtons() {
        prevImage.visibility = if (sliceIndex > 0) ImageButton.VISIBLE else ImageButton.INVISIBLE
        nextImage.visibility = if (sliceIndex < tumorMriSequence.images.size - 1) ImageButton.VISIBLE else ImageButton.INVISIBLE
    }
}
