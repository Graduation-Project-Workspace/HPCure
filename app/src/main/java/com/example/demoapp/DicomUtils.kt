package com.example.demoapp

import android.graphics.Bitmap
import android.graphics.Color
import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object DicomUtils {
    fun convertDicomToBitmap(dicomFile: File): Bitmap {
        val dis = DicomInputStream(dicomFile)
        val attrs = dis.readDataset(-1, -1)

        // Get image dimensions
        val width = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0)
        val height = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0)

        // Get pixel data
        val pixelData = attrs.getBytes(org.dcm4che3.data.Tag.PixelData)

        // Create bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Convert pixel data to bitmap with window leveling
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x)
                var pixelValue = pixelData[index].toInt() and 0xFF
                pixelValue = normalizePixelValue(pixelValue)
                val color = Color.rgb(pixelValue, pixelValue, pixelValue)
                bitmap.setPixel(x, y, color)
            }
        }

        dis.close()

        // Convert to PNG and back to ensure proper image format
        val pngBytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngBytes)
        return BitmapFactory.decodeByteArray(pngBytes.toByteArray(), 0, pngBytes.size())
    }

    private fun normalizePixelValue(value: Int): Int {
        // Adjust these values based on your DICOM image characteristics
        val min = 0
        val max = 255
        return ((value.toFloat() - min) / (max - min) * 255).toInt().coerceIn(0, 255)
    }

    fun saveBitmapAsPng(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun readDicomAttributes(dicomFile: File): Attributes {
        val dis = DicomInputStream(dicomFile)
        return dis.readDataset(-1, -1)
    }
}