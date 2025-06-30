package com.example.demoapp.Core

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.domain.model.ROI
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.N)
class FuzzyConnectedness(private val img: Bitmap, private val roi : ROI, private val seeds: List<Pair<Int, Int>>) {
    private val width = img.width
    private val height = img.height
    private val pixels = IntArray(width * height)
    private val conScene = FloatArray(width * height) { 0f }
    private val queue = PriorityQueue<Pair<Int, Float>>(compareByDescending { it.second })

    init {
        img.getPixels(pixels, 0, width, 0, 0, width, height)
        initializeSeeds()
    }

    private fun initializeSeeds() {
        for ((x, y) in seeds) {
            val index = y * width + x
            conScene[index] = 1.0f
            queue.add(Pair(index, 1.0f))
        }
    }

    private fun getNeighbors(index: Int): List<Int> {
        val x = index % width
        val y = index / width
        val neighbors = mutableListOf<Int>()

        if (x > roi.xMin) neighbors.add(index - 1) // Left
        if (x < roi.xMax) neighbors.add(index + 1) // Right
        if (y > roi.yMin) neighbors.add(index - width) // Up
        if (y < roi.yMax) neighbors.add(index + width) // Down

        return neighbors
    }

    private fun getIntensity(index: Int): Float {
        val color = pixels[index]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f  // Normalize to [0,1]
    }

    private fun ave(c: Int, d: Int): Float {
        return 0.5f * (getIntensity(c) + getIntensity(d))
    }

    private fun reldiff(c: Int, d: Int): Float {
        val fc = getIntensity(c)
        val fd = getIntensity(d)
        return if (fc + fd == 0f) 0f else abs(fc - fd) / (fc + fd)
    }

    private fun gaussian(value: Float, mean: Float, sigma: Float): Float {
        val adjustedSigma = maxOf(sigma, 0.1f)  // More reasonable minimum
        val exponent = -((value - mean) * (value - mean)) / (2 * adjustedSigma * adjustedSigma)
        // Limit extreme exponents to prevent underflow
        return exp(maxOf(exponent, -10.0f))
    }

    private fun affinity(c: Int, d: Int): Float {
        val gAve = gaussian(ave(c, d), meanAve, sigmaAve)
        val gRelDiff = gaussian(reldiff(c, d), meanRelDiff, sigmaRelDiff)

        // Weight the components or use a different combination formula
        // return 0.7f * gAve + 0.3f * gRelDiff  // Weighted average

        // OR use a softened minimum that's not as harsh as min()
        val weight = 0.8f  // Controls the "softness" of the minimum
        return weight * minOf(gAve, gRelDiff) + (1 - weight) * maxOf(gAve, gRelDiff)
    }
    private var meanAve = 0f
    private var sigmaAve = 0f
    private var meanRelDiff = 0f
    private var sigmaRelDiff = 0f

    private fun calculateMeansAndSigmas() {
        val spels = mutableSetOf<Int>()
        for (seed in seeds) {
            val index = seed.second * width + seed.first
            spels.addAll(getNeighbors(index))
        }

        val aves = mutableListOf<Float>()
        val reldiffs = mutableListOf<Float>()

        val spelsList = spels.toList()
        for (i in spelsList.indices) {
            for (j in i + 1 until spelsList.size) {
                aves.add(ave(spelsList[i], spelsList[j]))
                reldiffs.add(reldiff(spelsList[i], spelsList[j]))
            }
        }

        meanAve = aves.average().toFloat()
        sigmaAve = calculateStandardDeviation(aves, meanAve)
        meanRelDiff = reldiffs.average().toFloat()
        sigmaRelDiff = calculateStandardDeviation(reldiffs, meanRelDiff)
    }

    private fun calculateStandardDeviation(values: List<Float>, mean: Float): Float {
        val variance = values.sumOf { (it - mean).toDouble() * (it - mean).toDouble() } / (values.size - 1)
        return sqrt(variance).toFloat()
    }

    fun run(): FloatArray {
        calculateMeansAndSigmas()

        while (queue.isNotEmpty()) {
            val (c, value) = queue.poll()

            if (conScene[c] > value) continue

            for (e in getNeighbors(c)) {
                val affCE = affinity(c, e)
                val fMin = minOf(conScene[c], affCE)
                if (fMin > conScene[e]) {
                    conScene[e] = fMin
                    queue.add(Pair(e, fMin))
                }
            }
        }

        return conScene
    }
}