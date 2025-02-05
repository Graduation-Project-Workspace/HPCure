package com.example.demoapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeScreen : AppCompatActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private lateinit var uploadedImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)
        FileManager.initialize(this)

        val uploadContainer: RelativeLayout = findViewById(R.id.upload_container)
        val processButton: Button = findViewById(R.id.process_button)
        uploadedImage = findViewById(R.id.uploaded_image)

        uploadContainer.setOnClickListener {
            openDirectoryPicker()
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

    private fun handleDicomDirectory(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    FileManager.loadDirectory(this@HomeScreen, uri)
                }

                if (success) {
                    // Load and display first image
                    FileManager.getCurrentFile()?.let { file ->
                        val bitmap = FileManager.getProcessedImage(this@HomeScreen, file)
                        bitmap?.let {
                            uploadedImage.setImageBitmap(it)
                            uploadedImage.visibility = ImageView.VISIBLE
                            showToast("DICOM directory loaded successfully")
                        }
                    }
                } else {
                    showErrorDialog("Couldn't process your file, Please try again.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorDialog("Error processing directory: ${e.message}")
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