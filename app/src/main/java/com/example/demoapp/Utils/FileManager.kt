package com.example.demoapp.Utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileManager {
    private var dicomFiles = mutableListOf<File>()
    private var dicomFilesUris = mutableListOf<Uri>()
    private var currentIndex = 0
    private lateinit var cacheDir: File

    fun initialize(context: Context) {
        cacheDir = File(context.cacheDir, "processed_images").apply {
            if (!exists()) mkdirs()
        }
    }

    fun loadDirectory(context: Context, uri: Uri): Boolean {
        try {
            dicomFiles.clear()
            currentIndex = 0

            // Load all files from the directory
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)

            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )

            cursor?.use { c ->
                while (c.moveToNext()) {
                    val documentId = c.getString(0)
                    val name = c.getString(1)
                    val mimeType = c.getString(2)

                    // Only include DICOM files or handle by extension
                    if (name.endsWith(".dcm", ignoreCase = true)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                        val tempFile = createTempFile(context, documentUri, name)
                        dicomFiles.add(tempFile)
                        dicomFilesUris.add(documentUri)
                    }
                }
            }

            // Sort files by name to ensure consistent order
            dicomFiles.sortBy { it.name }
            return dicomFiles.isNotEmpty()
        } catch (e: Exception) {
            Log.e("FileManager", "Error loading directory", e)
            return false
        }
    }

    // Helper function to get file name from URI
    private fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return uri.lastPathSegment ?: ""
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
            // If file was cleared from cache, try to reprocess it
            if (!file.exists()) {
                reloadFiles(context)
            }

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

    fun cleanupTemporary() {
        // Only clear cache without resetting files array
        clearCache()
    }

    fun reloadFiles(context: Context) {
        dicomFiles = mutableListOf()
        for (uri in dicomFilesUris) {
            try {
                val name = getFileName(context, uri)
                val tempFile = createTempFile(context, uri, name)
                if (isValidDicomFile(tempFile)) {
                    dicomFiles.add(tempFile)
                } else {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e("FileManager", "Error reloading file from URI: $uri", e)
            }
        }
    }

    private fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun cleanup() {
        clearCache()
        dicomFiles.clear()
        currentIndex = 0
    }
}