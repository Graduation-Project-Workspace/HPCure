package com.example.demoapp.Model

import android.graphics.Bitmap

class MRISequence(var images: List<Bitmap>, var metadata: HashMap<String, String>)
{
    fun getSlice(index: Int): Bitmap {
        return images[index]
    }
    fun size(): Int {
        return images.size
    }
}