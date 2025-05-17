package com.example.demoapp

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.N)
class FuzzySystem {
    private var _alphaCutValue: Float = 0.0f
    fun estimateVolume(imageList : List<Bitmap>, seedPoints : List<Pair<Int, Int>>, alphaCutValue : Float): Int = runBlocking {
        _alphaCutValue = alphaCutValue
        val totalVolume: Int
        val time = measureTimeMillis {
            val jobs = imageList.zip(seedPoints).map { (img, seed) -> async(Dispatchers.Default) { calculateVolume(img, seed) } }
            val results = jobs.awaitAll()
            totalVolume = results.sum()
        }
        Log.d("ExecutionTime", "Execution time (FuzzySystem), ${imageList.size} images: $time ms")
        return@runBlocking totalVolume
    }

//    fun estimateVolume(imageList: List<Bitmap>, seedPoints: List<Pair<Int, Int>>, alphaCutValue: Float): Int {
//        _alphaCutValue = alphaCutValue
//        var totalVolume = 0
//
//        val time = measureTimeMillis {
//            for (i in imageList.indices) {
//                val img = imageList[i]
//                val seed = seedPoints[i]
//                totalVolume += calculateVolume(img, seed)
//            }
//        }
//
//        Log.d("ExecutionTime", "Execution time (FuzzySystem), ${imageList.size} images: $time ms")
//
//        return totalVolume
//    }

    fun calculateVolume(img: Bitmap, seed: Pair<Int, Int>): Int {
        // TODO: appy pre processing to the image

        val affinity_matrix_array = FuzzyConnectedness(img, listOf(seed)).run();

        return affinity_matrix_array.count { it >= _alphaCutValue / 100.0 };
    }
}