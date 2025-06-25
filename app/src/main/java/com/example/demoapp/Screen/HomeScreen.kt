package com.example.demoapp.Screen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.example.demoapp.R
import com.example.demoapp.Utils.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeScreen : BaseActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private var uploadedImage: ImageView? = null
    private lateinit var loadingOverlay: FrameLayout
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        FileManager.initialize(this)
        loadingOverlay = findViewById(R.id.loading_overlay)

        val uploadContainer = findViewById<RelativeLayout>(R.id.upload_container)
        val uploadedImageView = findViewById<ImageView>(R.id.uploaded_image)
        val menuButton = findViewById<ImageButton>(R.id.menu_button)

        uploadedImage = uploadedImageView

        menuButton.setOnClickListener { openDrawer() }

        uploadContainer.setOnClickListener {
            openDirectoryPicker()
        }
    }

    override fun getMainContent(): @Composable () -> Unit = {

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

                        val result = FileManager.loadDirectory(this@HomeScreen, uri)
                        val fileCount = FileManager.getTotalFiles()
                        Log.d("UploadScreen", "Files loaded: $fileCount")
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
                    val intent = Intent(this@HomeScreen, CalculateScreen::class.java)
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

    private fun showLoading() {
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
    }

    private fun showErrorDialog(message: String) {
        ErrorDialog(this).show(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}