package com.example.demoapp.Core

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.domain.interfaces.tumor.IFuzzySystem
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.N)
class SerialFuzzySystem : IFuzzySystem {
    private var _alphaCutValue: Float = 0.0f
    private lateinit var affinityMatrix: Array<Array<FloatArray>>

    override fun estimateVolume(mriSequence: MRISequence, roiList: List<ROI>, seedPoints : List<Pair<Int, Int>>, alphaCut : Float): CancerVolume = runBlocking {
        _alphaCutValue = alphaCut
        var totalVolume = 0f
        val time = measureTimeMillis {
            for (i in mriSequence.images.indices) {
                val image = mriSequence.images[i]
                val seed = seedPoints[i]
                val roi = roiList[i]
                val volume = calculateVolume(i, image, seed, roi)
                totalVolume += volume;
            }
        }
        Log.d("ExecutionTime", "Execution time (SerialFuzzySystem), ${mriSequence.images.size} images: $time ms")

        val spacingBetweenSlices = mriSequence.metadata["SpacingBetweenSlices"]?.toFloat() ?: 1.0f
        val sliceThickness = mriSequence.metadata["SliceThickness"]?.toFloat() ?: 1.0f
        val pixelSpacingX = mriSequence.metadata["PixelSpacingX"]?.toFloat() ?: 1.0f
        val pixelSpacingY = mriSequence.metadata["PixelSpacingY"]?.toFloat() ?: 1.0f

        // Calculate the total volume based on the pixel spacing and slice thickness
        val sliceArea = pixelSpacingX * pixelSpacingY
        totalVolume = (totalVolume * sliceArea * sliceThickness * spacingBetweenSlices)

        return@runBlocking CancerVolume(
            volume = totalVolume,
            sequence = mriSequence,
            affinityMatrix = affinityMatrix
        )
    }

    fun calculateVolume(imageIndex: Int, img: Bitmap, seed: Pair<Int, Int>, roi: ROI): Int {
        // TODO: appy pre processing to the image

        val flatAffinityArray = FuzzyConnectedness(img, roi, listOf(seed)).run()

        val width = img.width
        val height = img.height

        var volume = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val affinity = flatAffinityArray[y * width + x]
                affinityMatrix[imageIndex][y][x] = affinity
                if (affinity >= _alphaCutValue / 100.0f) volume++
            }
        }

        return volume
    }
}