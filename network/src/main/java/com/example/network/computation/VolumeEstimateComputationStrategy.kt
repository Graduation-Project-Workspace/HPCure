package com.example.network.computation

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.domain.interfaces.IComputationStrategy
import com.example.domain.interfaces.IRoiPredictor
import com.example.domain.interfaces.ISeedPredictor
import com.example.domain.model.ROI
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
            
            // Get ROI prediction
            val roiArray = roiPredictor.predictRoi(bitmap)
            val roi = ROI(
                xMin = (roiArray[0][0] * slice.width).toInt(),
                yMin = (roiArray[0][1] * slice.height).toInt(),
                xMax = (roiArray[0][2] * slice.width).toInt(),
                yMax = (roiArray[0][3] * slice.height).toInt()
            )
            rois.add(roi)

            // Get seed point prediction
            val seedPoint = seedPredictor.predictSeed(bitmap, intArrayOf(roi.xMin, roi.xMax, roi.yMin, roi.yMax))
            val scaledX = seedPoint[0][0] * (roi.xMax - roi.xMin) + roi.xMin
            val scaledY = seedPoint[0][1] * (roi.yMax - roi.yMin) + roi.yMin
            
            seedPoints.add(SeedPoint.newBuilder()
                .setX(scaledX.toInt())
                .setY(scaledY.toInt())
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