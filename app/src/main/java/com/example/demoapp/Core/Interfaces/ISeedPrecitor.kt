package com.example.demoapp.Core.Interfaces

import android.graphics.Bitmap

interface ISeedPrecitor {
    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: IntArray
    ): Array<FloatArray>
}