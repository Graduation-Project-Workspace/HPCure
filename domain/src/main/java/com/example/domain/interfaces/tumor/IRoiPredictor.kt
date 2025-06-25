package com.example.domain.interfaces.tumor

import android.graphics.Bitmap

interface IRoiPredictor {
    fun predictRoi(
        sliceBitmap: Bitmap,
    ): Array<FloatArray>
}