package com.example.demoapp.Core

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.demoapp.Core.Interfaces.IFuzzySystem
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.N)
class SerialFuzzySystem : IFuzzySystem {
    private var _alphaCutValue: Float = 0.0f
    override fun estimateVolume(mriSequence: MRISequence, roiList: List<ROI>, seedPoints : List<Pair<Int, Int>>, alphaCutValue : Float): CancerVolume = runBlocking {
        _alphaCutValue = alphaCutValue
        var totalVolume = 0
        val time = measureTimeMillis {
            for (i in mriSequence.images.indices) {
                val image = mriSequence.images[i]
                val seed = seedPoints[i]
                val roi = roiList[i]
                val volume = calculateVolume(image, roi, seed);
                totalVolume += volume;
            }
        }
        Log.d("ExecutionTime", "Execution time (SerialFuzzySystem), ${mriSequence.images.size} images: $time ms")

        return@runBlocking CancerVolume(
            volume = totalVolume,
            sequence = mriSequence,
            affinityMatrix = Array<Array<Float>> (0) { Array<Float>(0) { 0.0f } }
        )
    }

    fun calculateVolume(img: Bitmap, roi: ROI, seed: Pair<Int, Int>): Int {
        // TODO: appy pre processing to the image

        val affinity_matrix_array = FuzzyConnectedness(img, roi, listOf(seed)).run();

        return affinity_matrix_array.count { it >= _alphaCutValue / 100.0 };
    }
}