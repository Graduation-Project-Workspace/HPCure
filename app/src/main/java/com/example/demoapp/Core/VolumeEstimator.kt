package com.example.demoapp.Core
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import com.example.demoapp.Core.SeedPredictor
import android.content.Context
import com.example.demoapp.Core.Interfaces.ISeedPrecitor

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator {

    private val fuzzySystem: ParallelFuzzySystem = ParallelFuzzySystem();
    private lateinit var seedPredictor: ISeedPrecitor
    private lateinit var context: Context
     constructor(context: Context , seedPredictor: ISeedPrecitor) {
         this.context = context
         this.seedPredictor = seedPredictor;
         this.context = context
    }
    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val roi = ROI(
            start_slice = 0,
            end_slice = 0,
            start_row = 0,
            end_row = 0,
            start_col = 0,
            end_col = 0
        )
        var seedPoints = Array(1) { FloatArray(2)}
        var roi_slice = roi.getRoi();
        for (i in 0 until mriSeq.size()) {
            val slice = mriSeq.getSlice(i)
            seedPoints = seedPredictor.predictSeed(slice, roi_slice)
        }
        val seedPointsList = mutableListOf<Pair<Int, Int>>()
        for (i in seedPoints.indices) {
            val x = seedPoints[i][0].toInt()
            val y = seedPoints[i][1].toInt()
            seedPointsList.add(Pair(x, y))
        }
        return fuzzySystem.estimateVolume(mriSeq, roi, seedPointsList, alphaCutValue)
    }
}