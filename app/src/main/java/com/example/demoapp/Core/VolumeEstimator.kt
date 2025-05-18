package com.example.demoapp.Core

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator {
    private val fuzzySystem: ParallelFuzzySystem = ParallelFuzzySystem();

    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val seedPoints = mriSeq.images.map { bitmap ->
            Pair(bitmap.width / 2, bitmap.height / 2)
        }

        val roi = ROI(
            start_slice = 0,
            end_slice = 0,
            start_row = 0,
            end_row = 0,
            start_col = 0,
            end_col = 0
        )

        return fuzzySystem.estimateVolume(mriSeq, roi, seedPoints, alphaCutValue)
    }
}