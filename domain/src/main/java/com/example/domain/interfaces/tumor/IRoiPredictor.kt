package com.example.domain.interfaces.tumor
import android.graphics.Bitmap
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
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