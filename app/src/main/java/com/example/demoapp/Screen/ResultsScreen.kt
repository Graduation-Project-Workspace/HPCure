//package com.example.demoapp.Screen
//
//import android.content.Intent
//import android.graphics.Bitmap
//import android.os.Bundle
//import android.util.Log
//import android.view.View
//import android.widget.*
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.graphics.get
//import androidx.core.graphics.set
//import com.example.demoapp.Core.Interfaces.IRoiPredictor
//import com.example.demoapp.Core.Interfaces.ISeedPrecitor
//import com.example.demoapp.Core.ParallelFuzzySystem
//import com.example.demoapp.Core.SequentialRoiPredictor
//import com.example.demoapp.Core.SeedPredictor
//import com.example.demoapp.Core.VolumeEstimator
//import com.example.demoapp.Model.CancerVolume
//import com.example.demoapp.Model.MRISequence
//import com.example.demoapp.R
//import com.example.demoapp.Utils.FileManager
//import com.example.demoapp.Utils.ResultsDataHolder
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class ResultsScreen : AppCompatActivity() {
//    private lateinit var mriImage: ImageView
//    private lateinit var imageCount: TextView
//    private lateinit var prevImage: ImageButton
//    private lateinit var nextImage: ImageButton
//    private lateinit var tumorVolume: TextView
//    private lateinit var patientNameTextView: TextView
//    private lateinit var recalculateButton: Button
//    private lateinit var loadingOverlay: View
//    private lateinit var fuzzySystem: ParallelFuzzySystem
//    private lateinit var volumeEstimator: VolumeEstimator
//    private lateinit var backButton: ImageButton
//
//
//    private var dicomBitmaps = mutableListOf<Bitmap>()
//    private var currentImageIndex = 0
//
//    private lateinit var mriSequence: MRISequence
//    private lateinit var cancerVolume: CancerVolume
//    private var alphaCut: Float = 50f
//    private var timeTaken: Long = 0
//
//    companion object {
//        const val TAG = "ResultsScreen"
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.results_screen)
//
//        try {
//            // Always use ResultsDataHolder as the source of truth for alphaCut and timeTaken
//            intent.getSerializableExtra("mri_sequence")?.let {
//                mriSequence = it as MRISequence
//            }
//            intent.getSerializableExtra("cancer_volume")?.let {
//                cancerVolume = it as CancerVolume
//            }
//            // Use ResultsDataHolder for alphaCut and timeTaken
//            alphaCut = ResultsDataHolder.alphaCut
//            timeTaken = ResultsDataHolder.timeTaken
//
//            // If serialization failed, try the DataHolder for mriSequence/cancerVolume
//            if (!::mriSequence.isInitialized || !::cancerVolume.isInitialized) {
//                Log.d(TAG, "Using ResultsDataHolder as fallback")
//                ResultsDataHolder.mriSequence?.let { mriSequence = it }
//                ResultsDataHolder.cancerVolume?.let { cancerVolume = it }
//            }
//
//            if (!::mriSequence.isInitialized || !::cancerVolume.isInitialized) {
//                throw Exception("Failed to retrieve required data")
//            }
//
//            // Initialize fuzzy system components
////            fuzzySystem = ParallelFuzzySystem()
////            volumeEstimator = VolumeEstimator(
////                fuzzySystem = fuzzySystem,
////                seedPredictor = ISeedPrecitor.DummySeedPredictor(),
////                roiPredictor = IRoiPredictor.DummyRoiPredictor()
////            )
//
//            initializeViews()
//            loadDicomFiles()
//            setupImageNavigation()
//            setupBackButton()
//            setupRecalculateButton()
//
//            // Show processing time and volume
//            patientNameTextView.text = "Processing Time: ${timeTaken}ms"
//            tumorVolume.text = "Tumor Volume: ${cancerVolume.volume} cm³"
//        } catch (e: Exception) {
//            Log.e(TAG, "Error initializing results screen", e)
//            Toast.makeText(this, "Error loading results", Toast.LENGTH_SHORT).show()
//            finish()
//        }
//    }
//
//    private fun initializeViews() {
//        mriImage = findViewById(R.id.mri_image)
//        imageCount = findViewById(R.id.image_count)
//        prevImage = findViewById(R.id.prev_image)
//        nextImage = findViewById(R.id.next_image)
//        tumorVolume = findViewById(R.id.tumor_volume)
//        patientNameTextView = findViewById(R.id.patient_name)
//        recalculateButton = findViewById(R.id.recalculate_button)
//        loadingOverlay = findViewById(R.id.loading_overlay)
//        backButton = findViewById(R.id.back_button)
//        loadingOverlay.visibility = View.GONE
//    }
//
//    private fun loadDicomFiles() {
//        dicomBitmaps.clear()
//        dicomBitmaps.addAll(mriSequence.images)
//
//        if (dicomBitmaps.isNotEmpty()) {
//            Log.d(TAG, "Loaded ${dicomBitmaps.size} bitmaps")
//            loadCurrentImage()
//            updateImageCount()
//        } else {
//            Log.e(TAG, "No bitmaps were loaded")
//            Toast.makeText(this, "No images could be loaded", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun setupRecalculateButton() {
//        recalculateButton.setOnClickListener {
//            showLoadingState()
//
//            val startTime = System.currentTimeMillis()
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
//                        FileManager.getProcessedImage(this@ResultsScreen, file)
//                    }
//
//                    alphaCut = intent.getFloatExtra("alpha_cut", 50f)
//                    //Alpha cut value
//                    Log.d(TAG, "Alpha cut value: $alphaCut")
//                    // Create fresh predictors
//                    val sequentialRoiPredictor = SequentialRoiPredictor(this@ResultsScreen)
//                    val seedPredictor = SeedPredictor(this@ResultsScreen)
//                    val fuzzySystem = ParallelFuzzySystem()
//
//                    // Create a volume estimator with the new predictors
//                    val volumeEstimator = VolumeEstimator(
//                        fuzzySystem = fuzzySystem,
//                        roiPredictor = sequentialRoiPredictor,
//                        seedPredictor = seedPredictor
//                    )
//                    mriSequence = MRISequence(
//                        images = bitmaps,
//                        metadata = HashMap()
//                    );
//
//                    // Simply use the existing estimateVolume method that handles all conversions
//                    val newCancerVolume = volumeEstimator.estimateVolume(mriSequence, alphaCut)
//
//                    // Update our data
//                    cancerVolume = newCancerVolume
//                    timeTaken = System.currentTimeMillis() - startTime
//
//                    withContext(Dispatchers.Main) {
//                        hideLoadingState()
//                        tumorVolume.text = "Tumor Volume: ${cancerVolume.volume} mm³"
//                        patientNameTextView.text = "Processing Time: ${timeTaken}ms"
//                        loadCurrentImage()
//                        Toast.makeText(this@ResultsScreen, "Recalculation complete", Toast.LENGTH_SHORT).show()
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "Error during recalculation", e)
//                    withContext(Dispatchers.Main) {
//                        hideLoadingState()
//                        Toast.makeText(this@ResultsScreen, "Error during recalculation", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//        }
//    }
//
//    private fun showLoadingState() {
//        loadingOverlay.visibility = View.VISIBLE
//        recalculateButton.isEnabled = false
//    }
//
//    private fun hideLoadingState() {
//        loadingOverlay.visibility = View.GONE
//        recalculateButton.isEnabled = true
//    }
//
//    private fun loadCurrentImage() {
//        if (dicomBitmaps.isEmpty() || currentImageIndex >= dicomBitmaps.size) {
//            Log.d(TAG, "No images to display")
//            return
//        }
//
//        try {
//            val originalBitmap = dicomBitmaps[currentImageIndex]
//            val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
//
//            // Highlight the cancer area
//            for (y in 0 until bitmap.height) {
//                for (x in 0 until bitmap.width) {
//                    val pixel = bitmap[x, y]
//                    if (cancerVolume.affinityMatrix[currentImageIndex][y][x] < alphaCut / 100.0f) {
//                        // Highlight cancer area with a bright red overlay
//                        val r = 255
//                        val g = (pixel shr 8 and 0xFF) * 0.3f // reduce green
//                        val b = (pixel and 0xFF) * 0.3f // reduce blue
//                        bitmap[x, y] = (0xFF shl 24) or
//                                (r shl 16) or
//                                (g.coerceAtMost(255f).toInt() shl 8) or
//                                b.coerceAtMost(255f).toInt()
//                    }
//                }
//            }
//
//            mriImage.apply {
//                setImageBitmap(bitmap)
//                scaleType = ImageView.ScaleType.FIT_CENTER
//                adjustViewBounds = true
//            }
//            updateNavigationButtons()
//        } catch (e: Exception) {
//            Log.e(TAG, "Error displaying image: ${e.message}")
//            Toast.makeText(this, "Error displaying image", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun setupImageNavigation() {
//        prevImage.setOnClickListener {
//            if (currentImageIndex > 0) {
//                currentImageIndex--
//                loadCurrentImage()
//                updateImageCount()
//            }
//        }
//
//        nextImage.setOnClickListener {
//            if (currentImageIndex < dicomBitmaps.size - 1) {
//                currentImageIndex++
//                loadCurrentImage()
//                updateImageCount()
//            }
//        }
//    }
//    private fun setupBackButton() {
//        backButton.setOnClickListener {
//            val intent = Intent(this, FuzzyAndResultScreen::class.java)
//            intent.putExtra("shouldCleanup", false)
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
//            startActivity(intent)
//            finish()
//        }
//    }
//
//    private fun updateImageCount() {
//        val total = dicomBitmaps.size
//        val current = currentImageIndex + 1
//        imageCount.text = "$current/$total"
//    }
//
//    private fun updateNavigationButtons() {
//        prevImage.visibility = if (currentImageIndex > 0) View.VISIBLE else View.INVISIBLE
//        nextImage.visibility = if (currentImageIndex < dicomBitmaps.size - 1) View.VISIBLE else View.INVISIBLE
//    }
//}