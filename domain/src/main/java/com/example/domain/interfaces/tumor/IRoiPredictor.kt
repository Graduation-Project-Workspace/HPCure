package com.example.demoapp.Core.Interfaces
import android.graphics.Bitmap
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import org.tensorflow.lite.Interpreter


interface IRoiPredictor {
    fun predictRoi(
        sliceBitmap: Bitmap,
        tflite: Interpreter,
    ): ROI

    fun predictRoi(
        mriSequence: MRISequence,
        useGpuDelegate : Boolean = true,
        useAndroidNN : Boolean = true,
        numThreads : Int = 4) : List<ROI>
}