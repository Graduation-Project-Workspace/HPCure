package com.example.demoapp.Core
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.demoapp.Core.Interfaces.IFuzzySystem
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(private val fuzzySystem: IFuzzySystem,
                        private val seedPredictor: ISeedPrecitor,
                        private val context: Context) {

    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val roiList = mriSeq.images.map { bitmap ->
            ROI(
                xMin = 0,
                xMax = bitmap.height - 1,
                yMin = 0,
                yMax = bitmap.width - 1,
            )
        }
        val seedPoints = mutableListOf<Pair<Int, Int>>()
        var seedpoint = Array(1) { FloatArray(2) }
        for (i in mriSeq.images.indices) {
            val bitmap = mriSeq.images[i]
            val roi = roiList[i]
            seedpoint = seedPredictor.predictSeed(bitmap, intArrayOf(roi.xMin, roi.xMax, roi.yMin, roi.yMax))
            // Log the seed point for debugging
            seedpoint[0][0] = seedpoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin // Scale the seed point to the ROI dimensions
            seedpoint[0][1] = seedpoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin // Scale the seed point to the ROI dimensions
            Log.d("SeedPoint", "Seed point for image $i: (${seedpoint[0][0]}, ${seedpoint[0][1]})")
            seedPoints.add(Pair(seedpoint[0][0].toInt(), seedpoint[0][1].toInt()))
        }
        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
    }
}