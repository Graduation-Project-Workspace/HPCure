package com.example.demoapp.Core.Interfaces

import android.graphics.Bitmap

interface ISeedPrecitor {
    // Dummy Seed Predictor
    class DummySeedPredictor : ISeedPrecitor {
        override fun predictSeed(slice_bitmap: Bitmap, roi: IntArray): Array<FloatArray> {
            throw UnsupportedOperationException("Seed already provided, should not be called.")
        }
    }
    fun predictSeed(
        slice_bitmap: Bitmap,
        roi: IntArray
    ): Array<FloatArray>
}