package com.example.demoapp.Screen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeScreen : BaseActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private val PERMISSION_REQUEST_CODE = 123

    private var uploadedImage: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileManager.initialize(this)
    }

    override fun getMainContent(): @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = View.inflate(ctx, R.layout.home_screen, null)
                val uploadContainer = view.findViewById<RelativeLayout>(R.id.upload_container)
                val processButton = view.findViewById<Button>(R.id.process_button)
                val uploadedImageView = view.findViewById<ImageView>(R.id.uploaded_image)
                val nextButton = view.findViewById<Button>(R.id.go_to_next_screen)
                val menuButton = view.findViewById<ImageButton?>(R.id.menu_button)
                uploadedImage = uploadedImageView
                menuButton?.setOnClickListener { openDrawer() }

                uploadContainer.setOnClickListener {
                    openDirectoryPicker()
                }
                processButton.setOnClickListener {
                    if (FileManager.getTotalFiles() > 0) {
                        val intent = Intent(ctx, HomeScreenUpload::class.java)
                        ctx.startActivity(intent)
                    } else {
                        Toast.makeText(ctx, "Please select a DICOM directory first", Toast.LENGTH_SHORT).show()
                    }
                }
                nextButton.setOnClickListener {
                    val intent = Intent(ctx, ModelScreen::class.java)
                    ctx.startActivity(intent)
                }
                view
            }
        )
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
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri))
            docUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleDicomDirectory(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
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
                                uploadedImage?.setImageBitmap(it)
                                uploadedImage?.visibility = ImageView.VISIBLE
                                Toast.makeText(this@HomeScreen, "DICOM directory loaded successfully", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Storage permissions are required", Toast.LENGTH_SHORT).show()
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

}