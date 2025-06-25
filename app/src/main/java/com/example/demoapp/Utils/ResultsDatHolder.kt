package com.example.demoapp.Utils

import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

object ResultsDataHolder {
    var mriSequence: MRISequence? = null
    var cancerVolume: CancerVolume? = null
    var alphaCut: Float = 50f
    var timeTaken: Long = 0
    var roiList: List<ROI>? = null
    var seedPoints: List<Pair<Int, Int>>? = null
}