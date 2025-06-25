package com.example.domain.interfaces.tumor

import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI

interface IFuzzySystem {
    fun estimateVolume(
        mriSequence: MRISequence,
        roiList: List<ROI>,
        seedPoints : List<Pair<Int, Int>>,
        alphaCut: Float,
    ): CancerVolume
}