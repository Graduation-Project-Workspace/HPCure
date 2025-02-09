package com.example.demoapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager


class HomeScreen : AppCompatActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private lateinit var uploadedImage: ImageView
    private val PERMISSION_REQUEST_CODE = 123


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)
        FileManager.initialize(this)

        val uploadContainer: RelativeLayout = findViewById(R.id.upload_container)
        val processButton: Button = findViewById(R.id.process_button)
        uploadedImage = findViewById(R.id.uploaded_image)

        uploadContainer.setOnClickListener {
            checkAndRequestPermissions()
        }

        processButton.setOnClickListener {
            if (FileManager.getTotalFiles() > 0) {
                val intent = Intent(this, HomeScreenUpload::class.java)
                startActivity(intent)
            } else {
                showToast("Please select a DICOM directory first")
            }
        }
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        // Suggest the Downloads directory as a starting point
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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        // Take persistent permissions
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)

                        FileManager.loadDirectory(this@HomeScreen, uri)
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error in loadDirectory", e)
                        false
                    }
                }

                if (success) {
                    FileManager.getCurrentFile()?.let { file ->
                        try {
                            val bitmap = FileManager.getProcessedImage(this@HomeScreen, file)
                            bitmap?.let {
                                uploadedImage.setImageBitmap(it)
                                uploadedImage.visibility = ImageView.VISIBLE
                                showToast("DICOM directory loaded successfully")
                            } ?: run {
                                showErrorDialog("Failed to process image")
                            }
                        } catch (e: Exception) {
                            Log.e("HomeScreen", "Error processing image", e)
                            showErrorDialog("Error processing image: ${e.message}")
                        }
                    }
                } else {
                    showErrorDialog("Couldn't process your files. Please try again.")
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error in handleDicomDirectory", e)
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
        FileManager.cleanup()
    }

    private fun showErrorDialog(message: String) {
        ErrorDialog(this).show(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}