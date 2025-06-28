//package com.example.demoapp.Core
//import android.os.Build
//import android.util.Log
//import androidx.annotation.RequiresApi
//import com.example.demoapp.Core.Interfaces.IFuzzySystem
//import com.example.demoapp.Core.Interfaces.IRoiPredictor
//import com.example.demoapp.Core.Interfaces.ISeedPrecitor
//import com.example.demoapp.Model.CancerVolume
//import com.example.demoapp.Model.MRISequence
//
//@RequiresApi(Build.VERSION_CODES.N)
//class VolumeEstimator(private val fuzzySystem: IFuzzySystem,
//                        private val seedPredictor: ISeedPrecitor,
//                        private val roiPredictor: IRoiPredictor
//) {
//
//    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
//        val roiList = roiPredictor.predictRoi(mriSeq)
//        val seedPoints = mutableListOf<Pair<Int, Int>>()
//        for (i in mriSeq.images.indices) {
//            val bitmap = mriSeq.images[i]
//            val roi = roiList[i]
//            Log.d("ROI", "Image $i ROI: (${roi.xMin}, ${roi.yMin}) to (${roi.xMax}, ${roi.yMax})")
//
//            // Pass ROI as [x1, y1, x2, y2] — top-left and bottom-right
//            val seedPoint = seedPredictor.predictSeed(
//                bitmap,
//                roi
//            )
//
//            // Scale the seed point from relative ROI [0-1] to full image coordinates
//            seedPoint[0][0] = seedPoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin
//            seedPoint[0][1] = seedPoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin
//
//            Log.d("SeedPoint", "Seed point for image $i: (${seedPoint[0][0]}, ${seedPoint[0][1]})")
//            seedPoints.add(Pair(seedPoint[0][0].toInt(), seedPoint[0][1].toInt()))
//        }
//        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
//    }
//}