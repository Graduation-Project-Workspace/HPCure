package com.example.demoapp.Core.Interfaces
import android.graphics.Bitmap


interface IRoiPredictor {
    class DummyRoiPredictor : IRoiPredictor {
        override fun predictRoi(sliceBitmap: Bitmap): Array<FloatArray> {
            throw UnsupportedOperationException("ROI already provided, should not be called.")
        }
    }
    fun predictRoi(
        sliceBitmap: Bitmap,
    ): Array<FloatArray>
}