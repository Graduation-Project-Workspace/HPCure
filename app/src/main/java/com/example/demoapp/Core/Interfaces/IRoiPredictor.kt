package com.example.demoapp.Core.Interfaces
import android.graphics.Bitmap


interface IRoiPredictor {
    fun predictRoi(
        sliceBitmap: Bitmap,
    ): Array<FloatArray>
}