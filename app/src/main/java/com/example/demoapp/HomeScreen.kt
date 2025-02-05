package com.example.demoapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HomeScreen : AppCompatActivity() {

    private val PICK_FILE_REQUEST_CODE = 1
    private lateinit var uploadedImage: ImageView
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        // Find views by ID
        val uploadContainer: RelativeLayout = findViewById(R.id.upload_container)
        val processButton: Button = findViewById(R.id.process_button)
        uploadedImage = findViewById(R.id.uploaded_image)

        // Set click listener for upload container
        uploadContainer.setOnClickListener {
            openFilePicker()
        }

        // Set click listener for process button
        processButton.setOnClickListener {
            if (selectedImageUri != null) {
                val intent = Intent(this, HomeScreenUpload::class.java)
                intent.putExtra("image_uri", selectedImageUri?.toString())
                startActivity(intent)
            } else {
                showToast("Please select a DICOM file first")
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                if (isDicomFile(uri)) {
                    handleDicomFile(uri)
                } else {
                    showErrorDialog("Please select a DICOM file (.dcm)")
                }
            }
        }
    }

    private fun isDicomFile(uri: Uri): Boolean {
        val fileName = getFileName(uri).lowercase()
        return fileName.endsWith(".dcm") || fileName.endsWith(".dicom")
    }

    private fun handleDicomFile(uri: Uri) {
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

                // Update UI
                uploadedImage.setImageBitmap(bitmap)
                uploadedImage.visibility = ImageView.VISIBLE
                selectedImageUri = uri
                showToast("DICOM file loaded successfully")

            } catch (e: Exception) {
                e.printStackTrace()
                showErrorDialog("Error processing DICOM file: ${e.message}")
            }
        }
    }

    private fun showErrorDialog(message: String) {
        ErrorDialog(this).show(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}