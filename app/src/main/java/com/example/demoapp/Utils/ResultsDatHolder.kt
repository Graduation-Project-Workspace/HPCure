package com.example.demoapp.Utils

import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI

object ResultsDataHolder {
    var mriSequence: MRISequence? = null
    var cancerVolume: CancerVolume? = null
    var alphaCut: Float = 50f
    var timeTaken: Long = 0
    var roiList: List<ROI>? = null
    var seedPoints: List<Pair<Int, Int>>? = null
}