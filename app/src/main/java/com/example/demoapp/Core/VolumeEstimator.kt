package com.example.demoapp.Core
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import com.example.domain.interfaces.IFuzzySystem
import com.example.domain.interfaces.IRoiPredictor
import com.example.domain.interfaces.ISeedPredictor
import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.network.coordinator.VolumeEstimateCoordinatorStrategy
import com.example.network.network.GrpcNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@RequiresApi(Build.VERSION_CODES.N)
class VolumeEstimator(
    private val fuzzySystem: IFuzzySystem,
    private val seedPredictor: ISeedPredictor,
    private val roiPredictor: IRoiPredictor,
    private val network: GrpcNetwork
) {
    private val coordinatorStrategy = VolumeEstimateCoordinatorStrategy(network)
    private val logs = mutableStateListOf<String>()
    private val scope = CoroutineScope(Dispatchers.Main)

    fun estimateVolume(mriSeq: MRISequence, alphaCutValue: Float): CancerVolume {
        val roiList = mriSeq.images.map { bitmap ->
            val roiArray = roiPredictor.predictRoi(bitmap)
            ROI(
                xMin = (roiArray[0][0] * bitmap.width).toInt(),
                yMin = (roiArray[0][1] * bitmap.height).toInt(),
                xMax = (roiArray[0][2] * bitmap.width).toInt(),
                yMax = (roiArray[0][3] * bitmap.height).toInt()
            )
        }
        val seedPoints = mutableListOf<Pair<Int, Int>>()
        for (i in mriSeq.images.indices) {
            val bitmap = mriSeq.images[i]
            val roi = roiList[i]
            val seedPoint = seedPredictor.predictSeed(bitmap, intArrayOf(roi.xMin, roi.xMax, roi.yMin, roi.yMax))
            seedPoint[0][0] = seedPoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin // Scale the seed point to the ROI dimensions
            seedPoint[0][1] = seedPoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin // Scale the seed point to the ROI dimensions
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
                logs = logs
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