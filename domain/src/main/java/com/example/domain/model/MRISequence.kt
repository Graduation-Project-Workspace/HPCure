package com.example.domain.model

import android.graphics.Bitmap

class MRISequence(var images: List<Bitmap>, var metadata: Map<String, String>)
{
    fun getSlice(index: Int): Bitmap {
        return images[index]
    }
    fun size(): Int {
        return images.size
    }
}