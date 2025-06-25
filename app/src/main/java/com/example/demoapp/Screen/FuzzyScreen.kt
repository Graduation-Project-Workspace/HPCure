package com.example.demoapp.Screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import com.example.demoapp.Core.Interfaces.IRoiPredictor
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Core.ParallelFuzzySystem
import com.example.demoapp.Core.VolumeEstimator
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import com.example.demoapp.Utils.ResultsDataHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class FuzzyScreen : AppCompatActivity() {
    private lateinit var mriImage: ImageView
    private lateinit var alphaCutValue: TextView
    private lateinit var alphaCutSlider: SeekBar
    private lateinit var imageCount: TextView
    private lateinit var prevImage: ImageButton
    private lateinit var nextImage: ImageButton
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var calculateButton: Button
    private lateinit var calculateVolumeButton: Button
    private lateinit var patientNameTextView: TextView
    private var fuzzyCalculationTime: Long = 0
    private var currentAlphaCutValue: Float = 50.00f
    private lateinit var cancerVolume: CancerVolume
    private lateinit var mriSequence: MRISequence
    private lateinit var backButton: ImageButton
    // These are filled from intent extras
    private val roiMap: MutableMap<Int, FloatArray> = mutableMapOf()
    private val seedMap: MutableMap<Int, FloatArray> = mutableMapOf()
    private val context = this

    // Store fuzzy highlight state per slice
    private val fuzzyHighlightMap: MutableMap<Int, Boolean> = mutableMapOf()

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
        setContentView(R.layout.fuzzy_screen)

        @Suppress("UNCHECKED_CAST")
        intent.getSerializableExtra("roi_map")?.let { extra ->
            val incomingMap = extra as? HashMap<Int, FloatArray>
            if (incomingMap != null) {
                roiMap.clear()
                roiMap.putAll(incomingMap)
            }
        }
        @Suppress("UNCHECKED_CAST")
        intent.getSerializableExtra("seed_map")?.let { extra ->
            val incomingSeedMap = extra as? HashMap<Int, FloatArray>
            if (incomingSeedMap != null) {
                seedMap.clear()
                seedMap.putAll(incomingSeedMap)
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
        setupImageNavigation()
        setupAlphaCutControl()
        loadCurrentImage()
        updateImageCount()
        setupCalculateButton()
        setupBackButton()
        setupCalculateVolumeButton()
    }

    private fun initializeViews() {
        mriImage = findViewById(R.id.mri_image)
        alphaCutValue = findViewById(R.id.alpha_cut_value)
        alphaCutSlider = findViewById(R.id.alpha_cut_slider)
        imageCount = findViewById(R.id.image_count)
        prevImage = findViewById(R.id.prev_image)
        nextImage = findViewById(R.id.next_image)
        calculateButton = findViewById(R.id.calculate_button)
        loadingOverlay = findViewById(R.id.loading_overlay)
        calculateVolumeButton = findViewById(R.id.calculate_volume_button)
        patientNameTextView = findViewById(R.id.patient_name)
        backButton = findViewById(R.id.back_button)
        calculateVolumeButton.visibility = View.GONE
        updateDisplay()
    }

    private fun setupCalculateButton() {
        calculateButton.setOnClickListener {
            showLoadingState()
            val startTime = System.currentTimeMillis()

            // Use a coroutine for the fuzzy calculation
            CoroutineScope(Dispatchers.Default).launch {
                // Simulating fuzzy calculation
                highlightFuzzyOnAllSlices()

                fuzzyCalculationTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    hideLoadingState()
                    loadCurrentImage()
                    calculateVolumeButton.visibility = View.VISIBLE
                    calculateButton.text = "Re-calculate Fuzzy"

                    // Update patient name TextView to show calculation time
                    patientNameTextView.text = "Fuzzy calculation: ${fuzzyCalculationTime}ms"
                }
            }
        }
    }

    private fun setupCalculateVolumeButton() {
        calculateVolumeButton.setOnClickListener {
            showLoadingState()
            val startTime = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch {
                val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
                    FileManager.getProcessedImage(this@FuzzyScreen, file)
                }
                val alphaCut = currentAlphaCutValue

                val roiList = bitmaps.indices.map { idx ->
                    roiMap[idx]?.let { roiArr ->
                        val imgW = bitmaps[idx].width
                        val imgH = bitmaps[idx].height
                        val boxX = roiArr[0] * imgW
                        val boxY = roiArr[1] * imgH
                        val boxW = roiArr[2] * imgW
                        val boxH = roiArr[3] * imgH
                        val x1 = (boxX - boxW / 2).coerceAtLeast(0f).toInt()
                        val y1 = (boxY - boxH / 2).coerceAtLeast(0f).toInt()
                        val x2 = (boxX + boxW / 2).coerceAtMost(imgW.toFloat()).toInt()
                        val y2 = (boxY + boxH / 2).coerceAtMost(imgH.toFloat()).toInt()
                        ROI(xMin = x1, yMin = y1, xMax = x2, yMax = y2)
                    } ?: ROI(0, 0, 0, 0)
                }
                val seedPoints = bitmaps.indices.map { idx ->
                    seedMap[idx]?.let { seedArr ->
                        val roi = roiList[idx]
                        val x = (seedArr[0] * (roi.xMax - roi.xMin) + roi.xMin).toInt()
                        val y = (seedArr[1] * (roi.yMax - roi.yMin) + roi.yMin).toInt()
                        Pair(x, y)
                    } ?: Pair(0, 0)
                }

                mriSequence = MRISequence(images = bitmaps, metadata = HashMap())
                val fuzzySystem = ParallelFuzzySystem()
                val volumeEstimator = VolumeEstimator(
                    fuzzySystem = fuzzySystem,
                    seedPredictor = ISeedPrecitor.DummySeedPredictor(),
                    roiPredictor = IRoiPredictor.DummyRoiPredictor()
                )

                cancerVolume = fuzzySystem.estimateVolume(mriSequence, roiList, seedPoints, alphaCut)
                val elapsed = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    hideLoadingState()

                    // Store in data holder for reliable transfer
                    ResultsDataHolder.mriSequence = mriSequence
                    ResultsDataHolder.cancerVolume = cancerVolume
                    ResultsDataHolder.alphaCut = alphaCut
                    ResultsDataHolder.timeTaken = elapsed
                    ResultsDataHolder.roiList = roiList
                    ResultsDataHolder.seedPoints = seedPoints

                    val intent = Intent(this@FuzzyScreen, ResultsScreen::class.java)
                    intent.putExtra("shouldCleanup", false)
                    startActivity(intent)
                }
            }
        }
    }

    // Mark all slices as having fuzzy highlight
    private fun highlightFuzzyOnAllSlices() {
        val total = FileManager.getTotalFiles()
        for (i in 0 until total) {
            fuzzyHighlightMap[i] = true
        }
    }

    private fun showLoadingState() {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate().alpha(1f).setDuration(200).start()
        calculateButton.isEnabled = false
        calculateVolumeButton.isEnabled = false
    }

    private fun hideLoadingState() {
        loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            loadingOverlay.visibility = View.GONE
            calculateButton.isEnabled = true
            calculateVolumeButton.isEnabled = true
        }.start()
    }

    private fun loadCurrentImage() {
        val file = FileManager.getCurrentFile()
        val index = FileManager.getCurrentIndex() - 1
        file?.let {
            val bitmap = FileManager.getProcessedImage(this, it)
            if (bitmap != null) {
                val roi = roiMap[index]
                val seed = seedMap[index]
                val fuzzy = fuzzyHighlightMap[index] == true
                val displayBitmap = when {
                    roi != null && seed != null && fuzzy -> drawFuzzyHighlight(drawSeedPointInsideNormalizedRoi(bitmap, roi, seed), roi)
                    roi != null && seed != null -> drawSeedPointInsideNormalizedRoi(bitmap, roi, seed)
                    roi != null && fuzzy -> drawFuzzyHighlight(drawNormalizedRoiOnly(bitmap, roi), roi)
                    roi != null -> drawNormalizedRoiOnly(bitmap, roi)
                    else -> bitmap
                }
                mriImage.apply {
                    setImageBitmap(displayBitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            }
        }
        updateNavigationButtons()
    }

    private fun drawNormalizedRoiOnly(bitmap: Bitmap, roi: FloatArray): Bitmap {
        val (xCenter, yCenter, width, height) = roi

        val imgW = bitmap.width
        val imgH = bitmap.height

        val boxX = xCenter * imgW
        val boxY = yCenter * imgH
        val boxW = width * imgW
        val boxH = height * imgH

        val x1 = (boxX - boxW / 2).coerceAtLeast(0f)
        val y1 = (boxY - boxH / 2).coerceAtLeast(0f)
        val x2 = (boxX + boxW / 2).coerceAtMost(imgW.toFloat())
        val y2 = (boxY + boxH / 2).coerceAtMost(imgH.toFloat())

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
        roi: FloatArray,
        seed: FloatArray
    ): Bitmap {
        val (xCenter, yCenter, width, height) = roi

        val imgW = bitmap.width
        val imgH = bitmap.height

        val boxX = xCenter * imgW
        val boxY = yCenter * imgH
        val boxW = width * imgW
        val boxH = height * imgH

        val x1 = (boxX - boxW / 2).coerceAtLeast(0f)
        val y1 = (boxY - boxH / 2).coerceAtLeast(0f)
        val x2 = (boxX + boxW / 2).coerceAtMost(imgW.toFloat())
        val y2 = (boxY + boxH / 2).coerceAtMost(imgH.toFloat())

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
        val seedX = x1 + seed[0] * (x2 - x1)
        val seedY = y1 + seed[1] * (y2 - y1)

        val seedPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        canvas.drawCircle(seedX, seedY, 6f, seedPaint)

        return mutableBitmap
    }

    // Draw a blue border around the ROI to highlight fuzzy
    private fun drawFuzzyHighlight(bitmap: Bitmap, roi: FloatArray): Bitmap {
        val (xCenter, yCenter, width, height) = roi

        val imgW = bitmap.width
        val imgH = bitmap.height

        val boxX = xCenter * imgW
        val boxY = yCenter * imgH
        val boxW = width * imgW
        val boxH = height * imgH

        val x1 = (boxX - boxW / 2).coerceAtLeast(0f)
        val y1 = (boxY - boxH / 2).coerceAtLeast(0f)
        val x2 = (boxX + boxW / 2).coerceAtMost(imgW.toFloat())
        val y2 = (boxY + boxH / 2).coerceAtMost(imgH.toFloat())

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }

        // Draw an ellipse/oval instead of a rectangle
        val oval = RectF(x1, y1, x2, y2)
        canvas.drawOval(oval, paint)

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
    private fun setupBackButton() {
        backButton.setOnClickListener {
            val intent = Intent(this, SeedScreen::class.java)
            intent.putExtra("shouldCleanup", false)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
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
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateDisplay() {
        alphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
    }

    /*
    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanup()
    }
     */
}