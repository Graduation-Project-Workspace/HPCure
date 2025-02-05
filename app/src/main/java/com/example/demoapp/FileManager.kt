package com.example.demoapp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream

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
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri))

            processDirectory(context, docUri)
            dicomFiles.isNotEmpty() // Return true only if valid DICOM files were found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun processDirectory(context: Context, uri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
            DocumentsContract.getDocumentId(uri))

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (isDicomFile(name)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                        val tempFile = createTempFile(context, docUri, name)
                        if (isValidDicomFile(tempFile)) {
                            dicomFiles.add(tempFile)
                        } else {
                            tempFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
            // Try to read DICOM attributes to verify it's a valid DICOM file
            DicomUtils.readDicomAttributes(file)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createTempFile(context: Context, uri: Uri, name: String): File {
        val tempFile = File(cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun getCurrentFile(): File? {
        return if (dicomFiles.isNotEmpty() && currentIndex in dicomFiles.indices) {
            dicomFiles[currentIndex]
        } else null
    }

    fun getProcessedImage(context: Context, file: File): Bitmap? {
        return try {
            // First verify it's a valid DICOM file
            if (!isValidDicomFile(file)) {
                throw Exception("Invalid DICOM file")
            }

            val bitmap = DicomUtils.convertDicomToBitmap(file)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
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