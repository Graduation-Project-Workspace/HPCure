package com.example.demoapp.Core
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.demoapp.Core.Interfaces.IFuzzySystem
import com.example.demoapp.Core.Interfaces.IRoiPredictor
import com.example.demoapp.Core.Interfaces.ISeedPrecitor
import com.example.demoapp.Model.CancerVolume
import com.example.demoapp.Model.MRISequence
import com.example.demoapp.Model.ROI

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(private val fuzzySystem: IFuzzySystem,
                        private val seedPredictor: ISeedPrecitor,
                        private val roiPredictor: IRoiPredictor
) {

    fun estimateVolume(mriSeq : MRISequence, alphaCutValue : Float) : CancerVolume {
        val roiList = mriSeq.images.map { bitmap ->
            val roiArray = roiPredictor.predictRoi(bitmap)
            val roi = roiArray[0]

            val xCenter = roi[0]
            val yCenter = roi[1]
            val width = roi[2]
            val height = roi[3]

            val imgW = bitmap.width
            val imgH = bitmap.height

            val boxX = (xCenter * imgW).toInt()
            val boxY = (yCenter * imgH).toInt()
            val boxW = (width * imgW).toInt()
            val boxH = (height * imgH).toInt()

            val x1 = (boxX - boxW / 2).coerceAtLeast(0)
            val y1 = (boxY - boxH / 2).coerceAtLeast(0)
            val x2 = (boxX + boxW / 2).coerceAtMost(imgW - 1)
            val y2 = (boxY + boxH / 2).coerceAtMost(imgH - 1)

            ROI(
                xMin = x1,
                xMax = x2,
                yMin = y1,
                yMax = y2
            )
        }
        val seedPoints = mutableListOf<Pair<Int, Int>>()
        for (i in mriSeq.images.indices) {
            val bitmap = mriSeq.images[i]
            val roi = roiList[i]
            Log.d("ROI", "Image $i ROI: (${roi.xMin}, ${roi.yMin}) to (${roi.xMax}, ${roi.yMax})")
            val seedPoint = seedPredictor.predictSeed(bitmap, intArrayOf(roi.xMin, roi.xMax, roi.yMin, roi.yMax))
            seedPoint[0][0] = seedPoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin // Scale the seed point to the ROI dimensions
            seedPoint[0][1] = seedPoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin // Scale the seed point to the ROI dimensions
            Log.d("SeedPoint", "Seed point for image $i: (${seedPoint[0][0]}, ${seedPoint[0][1]})")
            seedPoints.add(Pair(seedPoint[0][0].toInt(), seedPoint[0][1].toInt()))
        }
        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
    }
}