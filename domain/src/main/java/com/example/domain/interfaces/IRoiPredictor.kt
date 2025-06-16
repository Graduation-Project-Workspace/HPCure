package com.example.domain.interfaces

import android.graphics.Bitmap

interface IRoiPredictor {
    fun predictRoi(
        sliceBitmap: Bitmap,
    ): Array<FloatArray>
}