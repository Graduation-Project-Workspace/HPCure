package com.example.demoapp

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator {
    private val fuzzySystem: FuzzySystem = FuzzySystem();

    fun estimateVolume(imageList : List<Bitmap>, seedPoints : List<Pair<Int, Int>>, alphaCutValue : Float) : Int {
        return fuzzySystem.estimateVolume(imageList, seedPoints, alphaCutValue)
    }
}