package com.example.demoapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
            val intent = Intent(this, HomeScreenUpload::class.java)
            intent.putExtra("image_uri", selectedImageUri?.toString())
            startActivity(intent)
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                handleFileUpload(uri)
            }
        }
    }

    private fun handleFileUpload(uri: Uri) {
        uploadedImage.setImageURI(uri)
        uploadedImage.visibility = ImageView.VISIBLE

        selectedImageUri = uri

        val fileName = getFileName(uri)
        Toast.makeText(this, "Selected file: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}