package com.example.demoapp.Core
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.domain.usecase.LogRepository
import com.example.domain.interfaces.IFuzzySystem
import com.example.domain.interfaces.INetworkService
import com.example.domain.interfaces.IRoiPredictor
import com.example.domain.interfaces.ISeedPredictor
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.network.coordinator.VolumeEstimateCoordinatorStrategy

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(
    private val fuzzySystem: IFuzzySystem,
    private val seedPredictor: ISeedPredictor,
    private val roiPredictor: IRoiPredictor,
    private val network: INetworkService
) {
    private val coordinatorStrategy = VolumeEstimateCoordinatorStrategy(network)

    fun estimateVolume(mriSeq: MRISequence, alphaCutValue: Float): CancerVolume {
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
                yMin = x2,
                xMax = y1,
                yMax = y2
            )
        }
        val seedPoints = mutableListOf<Pair<Int, Int>>()
        for (i in mriSeq.images.indices) {
            val bitmap = mriSeq.images[i]
            val roi = roiList[i]
            Log.d("ROI", "Image $i ROI: (${roi.xMin}, ${roi.yMin}) to (${roi.xMax}, ${roi.yMax})")

            // Pass ROI as [x1, y1, x2, y2] — top-left and bottom-right
            val seedPoint = seedPredictor.predictSeed(
                bitmap,
                intArrayOf(roi.xMin, roi.yMin, roi.xMax, roi.yMax)
            )

            // Scale the seed point from relative ROI [0-1] to full image coordinates
            seedPoint[0][0] = seedPoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin
            seedPoint[0][1] = seedPoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin

            Log.d("SeedPoint", "Seed point for image $i: (${seedPoint[0][0]}, ${seedPoint[0][1]})")
            seedPoints.add(Pair(seedPoint[0][0].toInt(), seedPoint[0][1].toInt()))
        }
        return fuzzySystem.estimateVolume(mriSeq, roiList, seedPoints, alphaCutValue)
    }

    fun estimateVolumeGrpc(mriSeq: MRISequence, alphaCutValue: Float): CancerVolume {
        try {
            // Convert MRISequence to protobuf request
            val request = coordinatorStrategy.convertMRISequenceToRequest(mriSeq, alphaCutValue)
            
            // Get available workers from the network
            val availableWorkers = network.getAvailableWorkers()
            
            if (availableWorkers.isEmpty()) {
                Log.w("VolumeEstimator", "No workers available, falling back to local computation")
                return estimateVolume(mriSeq, alphaCutValue)
            }
            
            // Start distributed computation
            val (roiList, seedPoints) = coordinatorStrategy.start(
                request = request,
                availableWorkers = availableWorkers,
                logs = LogRepository.sharedLogs
            )

            // Log the results for debugging
            roiList.forEachIndexed { index, roi ->
                Log.d("VolumeEstimator", "ROI for slice $index: (${roi.xMin}, ${roi.yMin}, ${roi.xMax}, ${roi.yMax})")
            }
            seedPoints.forEachIndexed { index, point ->
                Log.d("VolumeEstimator", "Seed point for slice $index: (${point.first}, ${point.second})")
            }
            
            // Use fuzzy system to compute final volume
            return fuzzySystem.estimateVolume(
                mriSequence = mriSeq,
                roiList = roiList,
                seedPoints = seedPoints,
                alphaCut = alphaCutValue
            )
            
        } catch (e: Exception) {
            Log.e("VolumeEstimator", "Error in distributed computation, falling back to local computation", e)
            return estimateVolume(mriSeq, alphaCutValue)
        }
    }
}