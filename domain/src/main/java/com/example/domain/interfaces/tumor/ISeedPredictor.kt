package com.example.domain.interfaces.tumor

import com.example.domain.model.MRISequence
import com.example.domain.model.ROI

interface ISeedPredictor {
    fun predictSeed(
        mriSeq: MRISequence,
        roiList: List<ROI>,
        useGpuDelegate : Boolean = false,
        useAndroidNN : Boolean = true,
        numThreads : Int = 4
    ): Array<Pair<Int, Int>>
}