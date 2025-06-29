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
        val rois = mutableListOf<ROI>()
        val seedPoints = mutableListOf<SeedPoint>()

        volumeRequest.slicesList.forEach { slice ->
            // Convert slice data to bitmap
            val bitmap = BitmapFactory.decodeByteArray(slice.imageData.toByteArray(), 0, slice.imageData.size())
            
            // Create MRISequence with single bitmap
            val mriSequence = MRISequence(listOf(bitmap), HashMap())
            
            // Get ROI prediction using the MRISequence overload
            val roiList = roiPredictor.predictRoi(mriSequence)
            val roi = roiList.firstOrNull() ?: ROI(0, 0, slice.width, slice.height)
            rois.add(roi)

            // Get seed point prediction using the MRISequence overload
            val seedPointArray = seedPredictor.predictSeed(mriSequence, listOf(roi))
            val seedPoint = seedPointArray.firstOrNull() ?: Pair(roi.xMin + (roi.xMax - roi.xMin) / 2, roi.yMin + (roi.yMax - roi.yMin) / 2)
            
            seedPoints.add(SeedPoint.newBuilder()
                .setX(seedPoint.first)
                .setY(seedPoint.second)
                .build())
        }

        return AssignTaskResponse.newBuilder()
            .setVolumeEstimateResponse(
                VolumeEstimateResponse.newBuilder()
                    .addAllRois(rois.map { roi ->
                        com.example.protos.ROI.newBuilder()
                            .setXMin(roi.xMin)
                            .setYMin(roi.yMin)
                            .setXMax(roi.xMax)
                            .setYMax(roi.yMax)
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