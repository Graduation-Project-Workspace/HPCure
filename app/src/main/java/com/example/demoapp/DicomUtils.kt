package com.example.demoapp

import android.graphics.Bitmap
import android.graphics.Color
import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object DicomUtils {
    fun convertDicomToBitmap(dicomFile: File): Bitmap {
        val dis = DicomInputStream(dicomFile)
        val attrs = dis.readDataset(-1, -1)

        // Get image dimensions
        val width = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0)
        val height = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0)

        // Get bit depth and other relevant attributes
        val bitsStored = attrs.getInt(org.dcm4che3.data.Tag.BitsStored, 8)
        val bitsAllocated = attrs.getInt(org.dcm4che3.data.Tag.BitsAllocated, 8)
        val pixelRepresentation = attrs.getInt(org.dcm4che3.data.Tag.PixelRepresentation, 0)
        val rescaleIntercept = attrs.getFloat(org.dcm4che3.data.Tag.RescaleIntercept, 0f)
        val rescaleSlope = attrs.getFloat(org.dcm4che3.data.Tag.RescaleSlope, 1f)

        // Get pixel data
        val pixelData = attrs.getBytes(org.dcm4che3.data.Tag.PixelData)

        // Create bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Calculate histogram for better windowing
        val histogram = IntArray(65536) // For 16-bit values
        var minValue = Int.MAX_VALUE
        var maxValue = Int.MIN_VALUE

        // First pass: build histogram and find min/max
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x)
                if (index < pixelData.size - 1) {
                    // Handle 16-bit values properly
                    val value = if (bitsAllocated > 8) {
                        ((pixelData[index * 2 + 1].toInt() and 0xFF) shl 8) or
                                (pixelData[index * 2].toInt() and 0xFF)
                    } else {
                        pixelData[index].toInt() and 0xFF
                    }

                    histogram[value]++
                    minValue = min(minValue, value)
                    maxValue = max(maxValue, value)
                }
            }
        }

        // Calculate window values using histogram
        val windowWidth = (maxValue - minValue) * 1.0f  // Changed from 0.8f to 1.0f
        val windowCenter = (maxValue + minValue) / 2f

        // Second pass: apply windowing and create bitmap
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x)
                if (index < pixelData.size - 1) {
                    // Get pixel value
                    val value = if (bitsAllocated > 8) {
                        ((pixelData[index * 2 + 1].toInt() and 0xFF) shl 8) or
                                (pixelData[index * 2].toInt() and 0xFF)
                    } else {
                        pixelData[index].toInt() and 0xFF
                    }

                    // Apply rescale
                    val rescaledValue = value * rescaleSlope + rescaleIntercept

                    // Apply windowing with enhanced contrast
                    val windowedValue = applyWindowing(
                        rescaledValue,
                        windowCenter,
                        windowWidth
                    )

                    // Create grayscale color
                    val color = Color.rgb(windowedValue, windowedValue, windowedValue)
                    bitmap.setPixel(x, y, color)
                }
            }
        }

        dis.close()
        return bitmap
    }

    private fun applyWindowing(value: Float, center: Float, width: Float): Int {
        val bottom = center - (width / 2.0f)
        val top = center + (width / 2.0f)

        val normalized = when {
            value <= bottom -> 0f
            value >= top -> 255f
            else -> {
                ((value - bottom) / width) * 255f
            }
        }

        return normalized.toInt().coerceIn(0, 255)
    }

    fun readDicomAttributes(dicomFile: File): Attributes {
        val dis = DicomInputStream(dicomFile)
        return dis.readDataset(-1, -1)
    }
    fun convertDicomStreamToBitmap(dis: DicomInputStream): Bitmap? {
        try {
            val attrs = dis.readDataset(-1, -1)

            // Get image dimensions
            val width = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0)
            val height = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0)

            // Get bit depth and other relevant attributes
            val bitsStored = attrs.getInt(org.dcm4che3.data.Tag.BitsStored, 8)
            val bitsAllocated = attrs.getInt(org.dcm4che3.data.Tag.BitsAllocated, 8)
            val rescaleIntercept = attrs.getFloat(org.dcm4che3.data.Tag.RescaleIntercept, 0f)
            val rescaleSlope = attrs.getFloat(org.dcm4che3.data.Tag.RescaleSlope, 1f)

            // Get pixel data
            val pixelData = attrs.getBytes(org.dcm4che3.data.Tag.PixelData)

            // Create bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Calculate histogram for better windowing
            val histogram = IntArray(65536) // For 16-bit values
            var minValue = Int.MAX_VALUE
            var maxValue = Int.MIN_VALUE

            // First pass: build histogram and find min/max
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = (y * width + x)
                    if (index < pixelData.size - 1) {
                        // Handle 16-bit values properly
                        val value = if (bitsAllocated > 8) {
                            ((pixelData[index * 2 + 1].toInt() and 0xFF) shl 8) or
                                    (pixelData[index * 2].toInt() and 0xFF)
                        } else {
                            pixelData[index].toInt() and 0xFF
                        }

                        histogram[value]++
                        minValue = min(minValue, value)
                        maxValue = max(maxValue, value)
                    }
                }
            }

            // Calculate window values using histogram
            val windowWidth = (maxValue - minValue) * 1.0f
            val windowCenter = (maxValue + minValue) / 2f

            // Second pass: apply windowing and create bitmap
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = (y * width + x)
                    if (index < pixelData.size - 1) {
                        // Get pixel value
                        val value = if (bitsAllocated > 8) {
                            ((pixelData[index * 2 + 1].toInt() and 0xFF) shl 8) or
                                    (pixelData[index * 2].toInt() and 0xFF)
                        } else {
                            pixelData[index].toInt() and 0xFF
                        }

                        // Apply rescale
                        val rescaledValue = value * rescaleSlope + rescaleIntercept

                        // Apply windowing
                        val windowedValue = applyWindowing(
                            rescaledValue,
                            windowCenter,
                            windowWidth
                        )

                        // Create grayscale color
                        val color = Color.rgb(windowedValue, windowedValue, windowedValue)
                        bitmap.setPixel(x, y, color)
                    }
                }
            }

            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}