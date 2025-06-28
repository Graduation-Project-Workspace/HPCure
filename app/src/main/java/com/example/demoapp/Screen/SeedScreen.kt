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
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Core.ParallelSeedPredictor
import com.example.demoapp.Core.SequentialSeedPredictor
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
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
    private lateinit var optionsButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var optionsPopup: LinearLayout
    private lateinit var btnParallel: Button
    private lateinit var btnSerial: Button
    private lateinit var btnGrpc: Button
    private lateinit var mainContent: RelativeLayout
    private lateinit var patientName: TextView
    private lateinit var confirmButton: Button
    private lateinit var customizeButton: Button
    private lateinit var mriSequence: MRISequence
    private var selectedMode: String = "Parallel"
    private var roiTimeTaken: Long = 0


    private var roiList : List<ROI> = emptyList()
    private var seedList : Array<Pair<Int, Int>> = emptyArray()
    private val context = this
    private lateinit var seedPredictor : ISeedPrecitor
    private lateinit var parallelSeedPredictor: ParallelSeedPredictor
    private lateinit var sequentialSeedPredictor: SequentialSeedPredictor

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
        seedPredictor = parallelSeedPredictor // Default to Parallel mode
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
        mriSequence = MRISequence(images = bitmaps, metadata = HashMap())

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
        if (FileManager.getAllFiles().isNotEmpty()) {
            setupImageNavigation()
            loadCurrentImage()
            updateImageCount()
        } else {
            Toast.makeText(this, "No images available to display", Toast.LENGTH_SHORT).show()
            predictButton.isEnabled = false
        }
        setupPredictButton()
        setupOptionsPopup()
        setupBackButton()
        setupPopupCloseOnBackground()
        setupConfirmButton()
    }

    private fun initializeViews() {
        mriImage = findViewById(R.id.mri_image)
        imageCount = findViewById(R.id.image_count)
        prevImage = findViewById(R.id.prev_image)
        nextImage = findViewById(R.id.next_image)
        loadingOverlay = findViewById(R.id.loading_overlay)
        predictButton = findViewById(R.id.predict_button)
        predictButton.text = "Predict Seed"
        optionsButton = findViewById(R.id.options_button)
        backButton = findViewById(R.id.back_button)
        mainContent = findViewById(R.id.main_content)
        optionsPopup = findViewById(R.id.options_popup)
        btnParallel = findViewById(R.id.btn_parallel)
        btnSerial = findViewById(R.id.btn_serial)
        btnGrpc = findViewById(R.id.btn_grpc)
        patientName = findViewById(R.id.patient_name)
        confirmButton = findViewById(R.id.confirm_roi_button)
        confirmButton.text = "Confirm Seed"
        customizeButton = findViewById(R.id.customize_button)

    }

    // Only use ROI received from RoiScreen, and predict SEED using it
    private fun setupPredictButton() {
        predictButton.setOnClickListener {
            if (roiList.isEmpty()) {
                Toast.makeText(this, "No ROI received! Please return to ROI screen.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showLoadingState()
            CoroutineScope(Dispatchers.IO).launch {
                val startTime = System.currentTimeMillis()

                seedList = seedPredictor.predictSeed(
                    mriSeq = mriSequence,
                    roiList = roiList
                )

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
        val index = FileManager.getCurrentIndex() - 1
        val displayBitmap = if (roiList.size > index && seedList.size > index) {
            drawSeedPointInsideNormalizedRoi(
                mriSequence.images[index],
                roiList[index],
                seedList[index]
            )
        } else if (roiList.size > index) {
            drawNormalizedRoiOnly(mriSequence.images[index], roiList[index])
        } else {
            mriSequence.images[index]
        }

        mriImage.apply {
            setImageBitmap(displayBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        updateNavigationButtons()
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

    private fun drawSeedPointInsideNormalizedRoi(
        bitmap: Bitmap,
        roi: ROI,
        seed: Pair<Int, Int>
    ): Bitmap {

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

        // Draw seed inside ROI (normalized to ROI dimensions)
        val seedX = seed.first.toFloat()
        val seedY = seed.second.toFloat()

        Log.d("SeedScreen", "Drawing seed at: ($seedX, $seedY) within ROI: ($x1, $y1, $x2, $y2)")

        val seedPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        canvas.drawCircle(seedX, seedY, 6f, seedPaint)

        return mutableBitmap
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

    private fun setupOptionsPopup() {
        optionsButton.setOnClickListener {
            optionsPopup.visibility = if (optionsPopup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnParallel.setOnClickListener {
            selectedMode = "Parallel"
            Toast.makeText(this, "Parallel mode selected", Toast.LENGTH_SHORT).show()
            optionsPopup.visibility = View.GONE
            seedPredictor = parallelSeedPredictor
        }
        btnSerial.setOnClickListener {
            selectedMode = "Sequential"
            Toast.makeText(this, "Sequential mode selected", Toast.LENGTH_SHORT).show()
            optionsPopup.visibility = View.GONE
            seedPredictor = sequentialSeedPredictor
        }
    }

    private fun setupPopupCloseOnBackground() {
        mainContent.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && optionsPopup.visibility == View.VISIBLE) {
                val location = IntArray(2)
                optionsPopup.getLocationOnScreen(location)
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val lx = location[0]
                val ly = location[1]
                val w = optionsPopup.width
                val h = optionsPopup.height
                if (!(x in lx..(lx + w) && y in ly..(ly + h))) {
                    optionsPopup.visibility = View.GONE
                }
            }
            false
        }
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

    /*
    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanupTemporary()
    }
     */
}