package com.example.demoapp.Core

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.demoapp.Core.Interfaces.IFuzzySystem
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(private val fuzzySystem: IFuzzySystem) {

    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val seedPoints = mriSeq.images.map { bitmap ->
            Pair(bitmap.width / 2, bitmap.height / 2)
        }

        val roiList = mriSeq.images.map { bitmap ->
            ROI(
                xMin = 0,
                xMax = bitmap.height - 1,
                yMin = 0,
                yMax = bitmap.width - 1,
            )
        }

        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
    }
}