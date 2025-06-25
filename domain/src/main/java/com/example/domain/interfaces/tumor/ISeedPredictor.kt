package com.example.domain.interfaces.tumor

import android.graphics.Bitmap

interface ISeedPredictor {
    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: IntArray
    ): Array<FloatArray>
}