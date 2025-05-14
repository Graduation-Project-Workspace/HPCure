package com.example.demoapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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


class HomeScreen : AppCompatActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private lateinit var uploadedImage: ImageView
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)
        FileManager.initialize(this)

//        benchmark()

        val uploadContainer: RelativeLayout = findViewById(R.id.upload_container)
        val processButton: Button = findViewById(R.id.process_button)
        uploadedImage = findViewById(R.id.uploaded_image)

        uploadContainer.setOnClickListener {
//            checkAndRequestPermissions()
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

//    external fun doOps(ops: Long) : Long;
//
//    companion object {
//        init {
//            System.loadLibrary("demoapp")
//        }
//    }

//    private fun benchmark() {
//        val jobCounts = listOf(3, 50, 100, 1000);
//        val ops = listOf(1e6.toInt(), 1e7.toInt(), 1e8.toInt(), 1e9.toInt());
//        val idleTimes = listOf(1000, 10000);
//
//
//        for(op in ops){
//            var time = measureTimeMillis {
//                val result = doOps(op.toLong())
//            }
//            Log.d("ExecutionTime", "Execution time (Native), $op ops: $time ms")
//
//            time = measureTimeMillis {
//                var sum = 0;
//                for(i in 1..op) {
//                    sum += i
//                }
//            }
//            Log.d("ExecutionTime", "Execution time (Sequential), $op ops: $time ms")
//        }


//        for (jobCount in jobCounts){
//            for(idleTime in idleTimes){
//                val concurrencyDemo = ConcurrencyDemo()
//                concurrencyDemo.runIdleTasks(jobCount, idleTime)
//
//                val parallelizeDemo = ParallelizeDemo()
//                parallelizeDemo.runIdleTasks(jobCount, idleTime)
//            }
//        }

//        for (jobCount in jobCounts) {
//            for (op in ops) {
//                val concurrencyDemo = ConcurrencyDemo()
//                concurrencyDemo.main(jobCount, op)
//
//                val parallelizeDemo = ParallelizeDemo()
//                parallelizeDemo.main(jobCount, op)
//
//                if(jobCount.toLong() * op.toLong() > 1e10.toLong())
//                    break
//
//                val time = measureTimeMillis {
//                    for(t in 1..jobCount) {
//                        var sum = 0;
//                        for (i in 1..op) {
//                            sum += i
//                        }
//                    }
//                }
//                Log.d("ExecutionTime", "Execution time (Sequential), $jobCount jobs, $op ops: $time ms")
//            }
//        }
//    }

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