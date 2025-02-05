package com.example.demoapp

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HomeScreenUpload : AppCompatActivity() {

    private lateinit var mriImage: ImageView
    private lateinit var alphaCutValue: TextView
    private lateinit var alphaCutSlider: SeekBar
    private var currentImageUri: Uri? = null
    private var imageUris: List<Uri> = emptyList()
    private var currentAlphaCutValue: Float = 50.00f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hs_upload)

        initializeViews()
        setupImageNavigation()
        setupAlphaCutControl()
    }

    private fun initializeViews() {
        mriImage = findViewById(R.id.mri_image)
        alphaCutValue = findViewById(R.id.alpha_cut_value)
        alphaCutSlider = findViewById(R.id.alpha_cut_slider)

        // Set initial alpha cut value
        updateDisplay()

        // Get the URI from intent
        currentImageUri = intent.getStringExtra("image_uri")?.let { Uri.parse(it) }

        // Load and display the DICOM image
        currentImageUri?.let { uri ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val inputStream = contentResolver.openInputStream(uri)
                        val tempFile = File.createTempFile("dicom", null, cacheDir)

                        FileOutputStream(tempFile).use { output ->
                            inputStream?.copyTo(output)
                        }

                        try {
                            val bitmap = DicomUtils.convertDicomToBitmap(tempFile)
                            tempFile.delete()
                            bitmap
                        } catch (e: Exception) {
                            tempFile.delete()
                            throw Exception("Invalid DICOM file format: ${e.message}")
                        }
                    }

                    // Update UI with the DICOM image
                    mriImage.setImageBitmap(bitmap)

                } catch (e: Exception) {
                    e.printStackTrace()
                    showErrorDialog("Error loading DICOM file: ${e.message}")
                }
            }
        }

        imageUris = getImageUris()
    }

    private fun setupImageNavigation() {
        val prevImage: ImageButton = findViewById(R.id.prev_image)
        val nextImage: ImageButton = findViewById(R.id.next_image)

        prevImage.setOnClickListener {
            val prevUri = getPreviousImageUri(currentImageUri)
            prevUri?.let { uri ->
                loadDicomImage(uri)
                currentImageUri = uri
            }
        }

        nextImage.setOnClickListener {
            val nextUri = getNextImageUri(currentImageUri)
            nextUri?.let { uri ->
                loadDicomImage(uri)
                currentImageUri = uri
            }
        }
    }

    private fun loadDicomImage(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val tempFile = File.createTempFile("dicom", null, cacheDir)

                    FileOutputStream(tempFile).use { output ->
                        inputStream?.copyTo(output)
                    }

                    try {
                        val bitmap = DicomUtils.convertDicomToBitmap(tempFile)
                        tempFile.delete()
                        bitmap
                    } catch (e: Exception) {
                        tempFile.delete()
                        throw Exception("Invalid DICOM file format: ${e.message}")
                    }
                }

                // Update UI with the DICOM image
                mriImage.setImageBitmap(bitmap)
                mriImage.scaleType = ImageView.ScaleType.FIT_CENTER

            } catch (e: Exception) {
                e.printStackTrace()
                showErrorDialog("Error loading DICOM file: ${e.message}")
            }
        }
    }

    private fun setupAlphaCutControl() {
        // Set initial values
        alphaCutSlider.max = 10000 // For 2 decimal precision
        alphaCutSlider.progress = (currentAlphaCutValue * 100).toInt()
        updateDisplay()

        alphaCutSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentAlphaCutValue = progress / 100f
                updateDisplay()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Add any behavior when user starts moving the slider
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optional: Add any behavior when user stops moving the slider
            }
        })
    }

    private fun updateDisplay() {
        alphaCutValue.text = "%.2f%%".format(currentAlphaCutValue)
    }

    private fun showErrorDialog(message: String) {
        ErrorDialog(this).show(message)
    }

    private fun getImageUris(): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(uri)
            }
        }
        return imageUris
    }

    private fun getPreviousImageUri(currentUri: Uri?): Uri? {
        currentUri ?: return null
        val currentIndex = imageUris.indexOf(currentUri)
        return if (currentIndex > 0) imageUris[currentIndex - 1] else null
    }

    private fun getNextImageUri(currentUri: Uri?): Uri? {
        currentUri ?: return null
        val currentIndex = imageUris.indexOf(currentUri)
        return if (currentIndex < imageUris.size - 1) imageUris[currentIndex + 1] else null
    }
}