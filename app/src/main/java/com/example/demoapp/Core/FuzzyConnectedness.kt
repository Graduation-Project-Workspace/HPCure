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

    private fun ave(c: Int, d: Int): Float {
        return 0.5f * (pixels[c] + pixels[d])
    }

    private fun reldiff(c: Int, d: Int): Float {
        val fc = pixels[c].toFloat()
        val fd = pixels[d].toFloat()
        return if (fc == -fd) 0f else abs(fc - fd) / (fc + fd)
    }

    private fun gaussian(value: Float, mean: Float, sigma: Float): Float {
        val adjustedSigma = if (sigma == 0f) 0.000001f else sigma
        return exp(-((value - mean) * (value - mean)) / (2 * adjustedSigma * adjustedSigma))
    }

    private fun affinity(c: Int, d: Int): Float {
        val gAve = gaussian(ave(c, d), meanAve, sigmaAve)
        val gRelDiff = gaussian(reldiff(c, d), meanRelDiff, sigmaRelDiff)
        return minOf(gAve, gRelDiff)
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