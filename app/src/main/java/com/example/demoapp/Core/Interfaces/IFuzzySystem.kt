package com.example.demoapp.Core.Interfaces

import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

interface IFuzzySystem {
    fun estimateVolume(
        mriSequence: MRISequence,
        roiList: List<ROI>,
        seedPoints : List<Pair<Int, Int>>,
        alphaCut: Float,
    ): CancerVolume
}