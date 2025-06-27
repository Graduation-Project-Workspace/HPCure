package com.example.demoapp.Core.Interfaces

import android.graphics.Bitmap
import com.example.demoapp.Model.ROI

interface ISeedPrecitor {
    // Dummy Seed Predictor
    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: ROI
    ): Array<FloatArray>
}