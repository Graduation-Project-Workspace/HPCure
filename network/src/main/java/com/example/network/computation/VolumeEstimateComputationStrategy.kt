package com.example.network.computation

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.domain.interfaces.network.IComputationStrategy
import com.example.domain.interfaces.tumor.IRoiPredictor
import com.example.domain.interfaces.tumor.ISeedPredictor
import com.example.domain.model.ROI
import com.example.domain.model.MRISequence
import com.example.protos.AssignTaskRequest
import com.example.protos.AssignTaskResponse
import com.example.protos.VolumeEstimateResponse
import com.example.protos.SeedPoint
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers

class VolumeEstimateComputationStrategy(
    private val roiPredictor: IRoiPredictor,
    private val seedPredictor: ISeedPredictor
) : IComputationStrategy {
    override fun generateInput(): Pair<Any, Any> {
        // Not needed for this implementation
        return Pair(0, 0)
    }

    override fun buildRequest(input1: Any, input2: Any): AssignTaskRequest {
        // Not needed for this implementation
        return AssignTaskRequest.getDefaultInstance()
    }

    override fun computeTask(request: AssignTaskRequest): AssignTaskResponse {
        val volumeRequest = request.volumeEstimateRequest
        
        Log.d("GRPC_WORKER", "Received ${volumeRequest.slicesCount} slices for parallel ROI/Seed computation")

        // Parallel processing for each slice
        val results = runBlocking {
            volumeRequest.slicesList.mapIndexed { sliceIdx, slice ->
                async(Dispatchers.Default) {
                    // Convert slice data to bitmap
                    val bitmap = BitmapFactory.decodeByteArray(slice.imageData.toByteArray(), 0, slice.imageData.size())
                    
                    // Create MRISequence with single bitmap
                    val mriSequence = MRISequence(listOf(bitmap), HashMap())
                    
                    // Get ROI prediction using the MRISequence overload
                    val roiList = roiPredictor.predictRoi(mriSequence, true)
                    val roi = roiList.firstOrNull() ?: ROI(0, 0, slice.width, slice.height)
                    roi.sliceIndex = slice.sliceIndex // Use the original global slice index

                    // Only process if ROI score > 0.3
                    if (roi.score > 0.3) {
                        val seedPointArray = seedPredictor.predictSeed(mriSequence, listOf(roi), true)
                        val seedPoint = seedPointArray.firstOrNull() ?: Pair(
                            roi.xMin + (roi.xMax - roi.xMin) / 2, 
                            roi.yMin + (roi.yMax - roi.yMin) / 2
                        )
                        
                        val protoSeed = SeedPoint.newBuilder()
                            .setX(seedPoint.first)
                            .setY(seedPoint.second)
                            .build()
                        
                        Log.d("GRPC_WORKER", "Slice ${slice.sliceIndex} PASSED threshold: ROI(${roi.xMin},${roi.yMin},${roi.xMax},${roi.yMax}, score=${roi.score}), Seed(${seedPoint.first},${seedPoint.second})")
                        
                        // Return global slice index with results for proper aggregation
                        Triple(slice.sliceIndex, roi, protoSeed)
                    } else {
                        Log.d("GRPC_WORKER", "Slice ${slice.sliceIndex} FAILED threshold: ROI score=${roi.score} <= 0.3, skipping")
                        null
                    }
                }
            }.map { it.await() }.filterNotNull()
        }

        val rois = mutableListOf<ROI>()
        val seedPoints = mutableListOf<SeedPoint>()

        results.forEach { (sliceIdx, roi, seed) ->
            // Add slice index to ROI for proper aggregation
            val roiWithIndex = roi.copy(sliceIndex = sliceIdx)
            rois.add(roiWithIndex)
            seedPoints.add(seed)
        }

        Log.d("GRPC_WORKER", "Returning ${rois.size} valid ROIs and ${seedPoints.size} seeds (filtered from ${volumeRequest.slicesCount} slices)")

        return AssignTaskResponse.newBuilder()
            .setVolumeEstimateResponse(
                VolumeEstimateResponse.newBuilder()
                    .addAllRois(rois.map { roi ->
                        com.example.protos.ROI.newBuilder()
                            .setXMin(roi.xMin)
                            .setYMin(roi.yMin)
                            .setXMax(roi.xMax)
                            .setYMax(roi.yMax)
                            .setScore(roi.score)
                            .setSliceIndex(roi.sliceIndex)
                            .build()
                    })
                    .addAllSeedPoints(seedPoints)
                    .build()
            )
            .setStatus(com.example.protos.TaskStatus.COMPLETED)
            .build()
    }

    override fun logInput(input1: Any, input2: Any, logs: SnapshotStateList<String>) {
        // Not needed for this implementation
    }

    override fun logOutput(response: AssignTaskResponse, logs: SnapshotStateList<String>) {
        val volumeResponse = response.volumeEstimateResponse
        logs.add("Processed ${volumeResponse.roisCount} slices")
        logs.add("Generated ${volumeResponse.seedPointsCount} seed points")
    }

    override fun getWorkerName(): String {
        return "VolumeEstimateWorker"
    }
}