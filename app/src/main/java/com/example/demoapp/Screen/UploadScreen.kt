package com.example.demoapp.Screen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.demoapp.Core.ParallelRoiPredictor
import com.example.demoapp.Core.ParallelSeedPredictor
import com.example.demoapp.Core.SequentialRoiPredictor
import com.example.demoapp.Core.SequentialSeedPredictor
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import com.example.demoapp.Utils.ResultsDataHolder
import com.example.domain.model.MRISequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadScreen : AppCompatActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private lateinit var uploadedImage: ImageView
    private lateinit var loadingOverlay: FrameLayout
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.upload_screen)
        FileManager.initialize(this)
        ParallelRoiPredictor.initialize(this)
        ParallelSeedPredictor.initialize(this)
        SequentialRoiPredictor.initialize(this)
        SequentialSeedPredictor.initialize(this)

        // Initialize gRPC components early
        try {
            SharedViewModel.getInstance(this)
            Log.d("UploadScreen", "gRPC components initialized successfully")
        } catch (e: Exception) {
            Log.e("UploadScreen", "Error initializing gRPC components", e)
        }

        val uploadContainer: RelativeLayout = findViewById(R.id.upload_container)
        uploadedImage = findViewById(R.id.uploaded_image)
        loadingOverlay = findViewById(R.id.loading_overlay)
        uploadContainer.setOnClickListener {
            openDirectoryPicker()
        }
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra("android.provider.extra.INITIAL_URI",
            "content://com.android.externalstorage.documents/document/primary:Download")
        startActivityForResult(intent, PICK_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                handleDicomDirectory(uri)
            }
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri))
            return docUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun handleDicomDirectory(uri: Uri) {
        showLoading()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        // Take persistent permissions
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)

                        val result = FileManager.loadDirectory(this@UploadScreen, uri)
                        val fileCount = FileManager.getTotalFiles()
                        Log.d("UploadScreen", "Files loaded: $fileCount")
                        // Store the full, unfiltered MRI sequence for later use
                        val bitmaps = FileManager.getAllFiles().mapNotNull { file ->
                            FileManager.getProcessedImage(this@UploadScreen, file)
                        }
                        ResultsDataHolder.fullMriSequence = MRISequence(images = bitmaps, metadata = FileManager.getDicomMetadata())
                        ResultsDataHolder.tumorMriSequence = MRISequence(images = bitmaps, metadata = FileManager.getDicomMetadata())
                        result && fileCount > 0
                    } catch (e: Exception) {
                        Log.e("UploadScreen", "Error in loadDirectory", e)
                        false
                    }
                }
                hideLoading()

                if (success) {
                    Log.d("UploadScreen", "Moving to RoiScreen with ${FileManager.getTotalFiles()} files")

                    // Directly go to the next screen
                    val intent = Intent(this@UploadScreen, RoiScreen::class.java)
                    startActivity(intent)

                    // Don't finish the activity yet - let them go back if needed
                    // finish()
                } else {
                    showErrorDialog("No valid DICOM files found in the selected directory.")
                }
            } catch (e: Exception) {
                hideLoading()
                Log.e("UploadScreen", "Error in handleDicomDirectory", e)
                showErrorDialog("Error processing directory: ${e.message}")
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE)
        } else {
            openDirectoryPicker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    openDirectoryPicker()
                } else {
                    showToast("Storage permissions are required")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FileManager.cleanupTemporary()
    }

    private fun showLoading() {
        loadingOverlay.alpha = 0f
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideLoading() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun showErrorDialog(message: String) {
        ErrorDialog(this).show(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}