package com.example.demoapp.Core.Interfaces

import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

interface ISeedPrecitor {
    fun predictSeed(
        mriSeq: MRISequence,
        roiList: List<ROI>,
        useGpuDelegate : Boolean = true,
        useAndroidNN : Boolean = true,
        numThreads : Int = 4
    ): Array<Pair<Int, Int>>
}