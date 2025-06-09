package com.example.demoapp.Utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

object DicomUtils {
    private const val TAG = "DicomUtils"

    /**
     * Converts a DICOM file to Bitmap with proper pixel value scaling
     */
    fun convertDicomToBitmap(dicomFile: File): Bitmap {
        try {
            if (!dicomFile.exists() || !dicomFile.canRead()) {
                throw IOException("Cannot read DICOM file")
            }

            DicomInputStream(dicomFile).use { dis ->
                val attrs = dis.readDataset(-1, -1)

                // Validate required DICOM attributes
                require(attrs.contains(org.dcm4che3.data.Tag.Columns)) { "Missing Columns attribute" }
                require(attrs.contains(org.dcm4che3.data.Tag.Rows)) { "Missing Rows attribute" }
                require(attrs.contains(org.dcm4che3.data.Tag.PixelData)) { "Missing PixelData attribute" }

                // Get basic image attributes
                val width = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0)
                val height = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0)
                val bitsAllocated = attrs.getInt(org.dcm4che3.data.Tag.BitsAllocated, 8)
                val bitsStored = attrs.getInt(org.dcm4che3.data.Tag.BitsStored, bitsAllocated)
                val pixelRepresentation = attrs.getInt(org.dcm4che3.data.Tag.PixelRepresentation, 0)
                val samplesPerPixel = attrs.getInt(org.dcm4che3.data.Tag.SamplesPerPixel, 1)
                val photometricInterpretation = attrs.getString(org.dcm4che3.data.Tag.PhotometricInterpretation, "MONOCHROME2")

                // Get optional rescale attributes
                val rescaleIntercept = attrs.getDouble(org.dcm4che3.data.Tag.RescaleIntercept, 0.0)
                val rescaleSlope = attrs.getDouble(org.dcm4che3.data.Tag.RescaleSlope, 1.0)
                val windowCenter = attrs.getDoubles(org.dcm4che3.data.Tag.WindowCenter)
                val windowWidth = attrs.getDoubles(org.dcm4che3.data.Tag.WindowWidth)

                Log.d(TAG, "DICOM attributes: width=$width, height=$height, bitsAllocated=$bitsAllocated, " +
                        "bitsStored=$bitsStored, pixelRep=$pixelRepresentation, samplesPP=$samplesPerPixel, " +
                        "photoInterp=$photometricInterpretation, rescale=$rescaleIntercept/$rescaleSlope")

                // Get pixel data
                val pixelData = attrs.getBytes(org.dcm4che3.data.Tag.PixelData)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // First pass: find min and max pixel values
                var minValue = Double.MAX_VALUE
                var maxValue = Double.MIN_VALUE

                for (i in 0 until width * height) {
                    val value = when {
                        bitsAllocated > 8 -> {
                            if (i * 2 + 1 >= pixelData.size) 0.0
                            else {
                                val rawValue = ((pixelData[i * 2 + 1].toInt() and 0xFF) shl 8) or
                                        (pixelData[i * 2].toInt() and 0xFF)
                                val signedValue = if (pixelRepresentation == 1) rawValue.toShort().toInt() else rawValue
                                (signedValue * rescaleSlope + rescaleIntercept)
                            }
                        }
                        i < pixelData.size -> (pixelData[i].toInt() and 0xFF) * rescaleSlope + rescaleIntercept
                        else -> 0.0
                    }

                    minValue = min(minValue, value)
                    maxValue = max(maxValue, value)
                }

                // Handle case where all pixels have same value
                if (maxValue <= minValue) {
                    maxValue = minValue + 1.0
                }

                Log.d(TAG, "Pixel value range: min=$minValue, max=$maxValue")

                // Second pass: create bitmap with scaled values
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val i = y * width + x
                        val value = when {
                            bitsAllocated > 8 -> {
                                if (i * 2 + 1 >= pixelData.size) 0.0
                                else {
                                    val rawValue = ((pixelData[i * 2 + 1].toInt() and 0xFF) shl 8) or
                                            (pixelData[i * 2].toInt() and 0xFF)
                                    val signedValue = if (pixelRepresentation == 1) rawValue.toShort().toInt() else rawValue
                                    (signedValue * rescaleSlope + rescaleIntercept)
                                }
                            }
                            i < pixelData.size -> (pixelData[i].toInt() and 0xFF) * rescaleSlope + rescaleIntercept
                            else -> 0.0
                        }

                        // Scale to 0-255 range
                        val scaledValue = ((value - minValue) / (maxValue - minValue) * 255).toInt()
                        val clampedValue = scaledValue.coerceIn(0, 255)
                        bitmap.setPixel(x, y, Color.rgb(clampedValue, clampedValue, clampedValue))
                    }
                }

                return bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting DICOM to bitmap", e)
            throw IOException("Failed to convert DICOM to bitmap", e)
        }
    }

    /**
     * Reads DICOM attributes from file
     */
    fun readDicomAttributes(dicomFile: File): Attributes {
        DicomInputStream(dicomFile).use { dis ->
            return dis.readDataset(-1, -1)
        }
    }

}