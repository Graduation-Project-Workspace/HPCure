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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.demoapp.Core.ParallelRoiPredictor
import com.example.demoapp.Core.SequentialRoiPredictor
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import com.example.demoapp.Utils.GpuDelegateHelper
import com.example.domain.interfaces.tumor.IRoiPredictor
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class RoiScreen : AppCompatActivity() {

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

    private lateinit var roiPredictor: IRoiPredictor
    private lateinit var parallelRoiPredictor: ParallelRoiPredictor
    private lateinit var sequentialRoiPredictor: SequentialRoiPredictor

    private lateinit var originalMriSequence: MRISequence
    private lateinit var tumorMriSequence: MRISequence

    private var selectedMode: String = "Parallel"

    private var roiList: List<ROI> = emptyList()
    private var tumorRoiList: List<ROI> = emptyList()
    private var sliceIndex = 0
    private val context = this
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

        parallelRoiPredictor = ParallelRoiPredictor(context = context)
        sequentialRoiPredictor = SequentialRoiPredictor(context = context)
        roiPredictor = parallelRoiPredictor

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
        tumorMriSequence = MRISequence(images = bitmaps, metadata = FileManager.getDicomMetadata())

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

        Log.d("RoiScreen", "GPU Delegate available: ${GpuDelegateHelper().isGpuDelegateAvailable}")
        if (!GpuDelegateHelper().isGpuDelegateAvailable) {
            btnGpu.visibility = View.GONE
        }

        setMode("Parallel")
        toggleGpu()

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
        confirmButton.text = "Confirm ROI"
        customizeButton = findViewById(R.id.customize_button)

        btnParallel.setOnClickListener { setMode("Parallel") }
        btnSerial.setOnClickListener { setMode("Serial") }
        btnGpu.setOnClickListener { toggleGpu() }
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        roiPredictor = if (mode == "Parallel") parallelRoiPredictor else sequentialRoiPredictor

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
            val intent = Intent(this, UploadScreen::class.java)
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

            showLoadingState()
            CoroutineScope(Dispatchers.IO).launch {
                val startTime = System.currentTimeMillis()
                try {
                    roiList = roiPredictor.predictRoi(
                        mriSequence = originalMriSequence,
                        useGpuDelegate = isGPUEnabled
                    )
                } catch (e: Exception) {
                    Log.e("RoiScreen", "Error during ROI prediction: ${e.message}")
                    withContext(Dispatchers.Main) {
                        hideLoadingState()
                        Toast.makeText(context, "Error during ROI prediction: ${e.message}", Toast.LENGTH_LONG).show()
                        return@withContext
                    }
                }
                tumorMriSequence.images = emptyList()
                for ((index, roi) in roiList.withIndex()) {
                    if (roi.score > 0.3) {
                        tumorMriSequence.images+= originalMriSequence.images[index]
                        tumorRoiList+= roi
                    }
                }
                sliceIndex = 0

                val endTime = System.currentTimeMillis()
                val timeTaken = endTime - startTime

                withContext(Dispatchers.Main) {
                    updateImageCount()
                    updateNavigationButtons()
                    hideLoadingState()
                    loadCurrentImage()
                    patientName.text = "Time Taken: $timeTaken ms"
                    confirmButton.visibility = View.VISIBLE
                    customizeButton.visibility = View.VISIBLE
                    predictButton.text = "Re-Predict ROI"
                }
            }
        }
    }

    private fun setupConfirmButton() {
        confirmButton.setOnClickListener {
            val intent = Intent(this, SeedScreen::class.java)
            intent.putExtra("roi_list", ArrayList(roiList))
            intent.putExtra("roi_time_taken", patientName.text.toString().replace("Time Taken: ", "").replace(" ms", "").toLong())
            intent.putExtra("shouldCleanup", false)
            startActivity(intent)
        }
    }

    private fun showLoadingState() {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        predictButton.isEnabled = false
    }

    private fun hideLoadingState() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                predictButton.isEnabled = true
            }
            .start()
    }

    private fun loadCurrentImage() {
        val displayBitmap = roiList.getOrNull(sliceIndex)?.let {
            drawRoiRectangleOnBitmap(tumorMriSequence.images[sliceIndex], tumorRoiList[sliceIndex])
        } ?: tumorMriSequence.images[sliceIndex]

        mriImage.apply {
            setImageBitmap(displayBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        updateNavigationButtons()
    }

    private fun drawRoiRectangleOnBitmap(originalBitmap: Bitmap, roi: ROI): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(roi.xMin.toFloat(), roi.yMin.toFloat(), roi.xMax.toFloat(), roi.yMax.toFloat(), paint)
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
