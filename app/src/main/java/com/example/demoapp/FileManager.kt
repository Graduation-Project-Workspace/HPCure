package com.example.demoapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileManager {
    private var dicomFiles = mutableListOf<File>()
    private var currentIndex = 0
    private lateinit var cacheDir: File

    fun initialize(context: Context) {
        cacheDir = File(context.cacheDir, "processed_images").apply {
            if (!exists()) mkdirs()
        }
    }

    fun loadDirectory(context: Context, uri: Uri): Boolean {
        clearCache()
        dicomFiles.clear()
        currentIndex = 0

        return try {
            // Take persistent permissions
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            val docId = DocumentsContract.getTreeDocumentId(uri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)

            processDirectory(context, uri, childrenUri)
            dicomFiles.isNotEmpty()
        } catch (e: Exception) {
            Log.e("FileManager", "Error loading directory", e)
            false
        }
    }

    private fun processDirectory(context: Context, treeUri: Uri, childrenUri: Uri) {
        Log.d("FileManager", "Processing directory: $childrenUri")

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val docIdIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                try {
                    val docId = cursor.getString(docIdIndex)
                    val name = cursor.getString(nameIndex)

                    Log.d("FileManager", "Found file: $name with docId: $docId")

                    if (isDicomFile(name)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        Log.d("FileManager", "Processing DICOM file: $name with URI: $documentUri")

                        val tempFile = createTempFile(context, documentUri, name)
                        if (isValidDicomFile(tempFile)) {
                            dicomFiles.add(tempFile)
                            Log.d("FileManager", "Successfully added DICOM file: $name")
                        } else {
                            tempFile.delete()
                            Log.w("FileManager", "Invalid DICOM file: $name")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FileManager", "Error processing file", e)
                }
            }
        }
    }
    private fun requestStoragePermission(context: Context) {

    }

    private fun isDicomFile(name: String): Boolean {
        return name.endsWith(".dcm", ignoreCase = true) ||
                name.endsWith(".dicom", ignoreCase = true)
    }

    private fun isValidDicomFile(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() == 0L || file.length() > 100 * 1024 * 1024) { // 100MB limit
                return false
            }
            DicomUtils.readDicomAttributes(file)
            true
        } catch (e: Exception) {
            Log.e("FileManager", "Error validating DICOM file: ${file.name}", e)
            false
        }
    }

    private fun createTempFile(context: Context, documentUri: Uri, name: String): File {
        val tempFile = File(cacheDir, name)
        try {
            context.contentResolver.openInputStream(documentUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Could not open input stream for $name")

            Log.d("FileManager", "Successfully created temp file: $name")
            return tempFile
        } catch (e: Exception) {
            Log.e("FileManager", "Error creating temp file: $name", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    fun getCurrentFile(): File? {
        return if (dicomFiles.isNotEmpty() && currentIndex in dicomFiles.indices) {
            dicomFiles[currentIndex]
        } else null
    }

    fun getAllFiles(): List<File> {
        return dicomFiles
    }

    fun getProcessedImage(context: Context, file: File): Bitmap? {
        return try {
            if (!isValidDicomFile(file)) {
                Log.e("FileManager", "Invalid DICOM file: ${file.name}")
                throw Exception("Invalid DICOM file")
            }

            System.gc() // Suggest garbage collection before processing large files
            val bitmap = DicomUtils.convertDicomToBitmap(file)
            bitmap
        } catch (e: Exception) {
            Log.e("FileManager", "Error processing image", e)
            null
        }
    }

    fun moveToNext(): Boolean {
        if (currentIndex < dicomFiles.size - 1) {
            currentIndex++
            return true
        }
        return false
    }

    fun moveToPrevious(): Boolean {
        if (currentIndex > 0) {
            currentIndex--
            return true
        }
        return false
    }

    fun getCurrentIndex(): Int = currentIndex + 1

    fun getTotalFiles(): Int = dicomFiles.size

    private fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun cleanup() {
        clearCache()
        dicomFiles.clear()
        currentIndex = 0
    }
}