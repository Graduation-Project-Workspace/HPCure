package com.example.network.coordinator

import com.example.domain.interfaces.network.ICoordinatorStrategy
import com.example.domain.interfaces.network.WorkerResult
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.domain.interfaces.network.INetworkService
import com.example.protos.*
import kotlin.math.ceil
import java.io.ByteArrayOutputStream
import android.util.Log
import com.example.network.ui.UiEventWorkStatus
import com.example.network.util.WorkEventBus
import kotlinx.coroutines.*

class VolumeEstimateCoordinatorStrategy(
    private val networkService: INetworkService
) : ICoordinatorStrategy {
    
    fun convertMRISequenceToRequest(mriSeq: MRISequence, alphaCutValue: Float): AssignTaskRequest {
        // Convert MRISequence to protobuf format
        val slices = mriSeq.images.mapIndexed { index, bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            ImageSlice.newBuilder()
                .setImageData(com.google.protobuf.ByteString.copyFrom(stream.toByteArray()))
                .setWidth(bitmap.width)
                .setHeight(bitmap.height)
                .setSliceIndex(index)
                .build()
        }

        // Create the initial request
        return AssignTaskRequest.newBuilder()
            .setVolumeEstimateRequest(
                VolumeEstimateRequest.newBuilder()
                    .addAllSlices(slices)
                    .setAlphaCutValue(alphaCutValue)
                    .build()
            )
            .build()
    }

    override fun distributeTasks(
        totalSize: Int,
        availableWorkers: List<Pair<String, String>>,
        logs: MutableList<String>
    ): Map<Pair<String, String>, IntRange> {
        if (availableWorkers.isEmpty()) {
            logs.add("No workers available for task distribution")
            return emptyMap()
        }

        val numWorkers = availableWorkers.size
        val slicesPerWorker = ceil(totalSize.toDouble() / numWorkers).toInt()
        
        logs.add("Distributing $totalSize slices among $numWorkers workers")
        logs.add("Each worker will process approximately $slicesPerWorker slices")
        
        return availableWorkers.mapIndexed { index, worker ->
            val start = index * slicesPerWorker
            val end = minOf((index + 1) * slicesPerWorker - 1, totalSize - 1)
            logs.add("Worker ${worker.second} assigned slices $start to $end")
            worker to (start..end)
        }.toMap()
    }

    override fun buildSubRequest(
        request: AssignTaskRequest,
        range: IntRange
    ): AssignTaskRequest {
        val volumeRequest = request.volumeEstimateRequest
        val subRequestBuilder = VolumeEstimateRequest.newBuilder()
            .setAlphaCutValue(volumeRequest.alphaCutValue)

        range.forEach { index ->
            subRequestBuilder.addSlices(volumeRequest.getSlices(index))
        }

        return AssignTaskRequest.newBuilder()
            .setVolumeEstimateRequest(subRequestBuilder.build())
            .build()
    }

    override fun aggregateResults(
        results: List<Pair<String, WorkerResult>>
    ): AssignTaskResponse {
        val aggregatedRois = mutableListOf<ROI>()
        val aggregatedSeedPoints = mutableListOf<SeedPoint>()
        val workerInfo = mutableMapOf<String, RowIndices>()
        val friendlyNames = mutableMapOf<String, String>()

        results.forEach { (workerAddress, result) ->
            val volumeResponse = result.response.volumeEstimateResponse
            
            // Add ROIs and seed points in order based on the assigned range
            val startIndex = result.assignedRange.first()
            volumeResponse.roisList.forEachIndexed { index, roi ->
                aggregatedRois.add(startIndex + index, ROI(
                    xMin = roi.xMin,
                    yMin = roi.yMin,
                    xMax = roi.xMax,
                    yMax = roi.yMax
                ))
            }
            
            volumeResponse.seedPointsList.forEachIndexed { index, seedPoint ->
                aggregatedSeedPoints.add(startIndex + index, seedPoint)
            }

            // Add worker info
            workerInfo[workerAddress] = RowIndices.newBuilder()
                .addAllValues(result.assignedRange)
                .build()

            // Add friendly name
            friendlyNames[workerAddress] = result.response.friendlyNamesMap[workerAddress] ?: workerAddress
        }

        return AssignTaskResponse.newBuilder()
            .setVolumeEstimateResponse(
                VolumeEstimateResponse.newBuilder()
                    .addAllRois(aggregatedRois.map { roi ->
                        com.example.protos.ROI.newBuilder()
                            .setXMin(roi.xMin)
                            .setYMin(roi.yMin)
                            .setXMax(roi.xMax)
                            .setYMax(roi.yMax)
                            .build()
                    })
                    .addAllSeedPoints(aggregatedSeedPoints)
                    .build()
            )
            .putAllWorkerInfo(workerInfo)
            .putAllFriendlyNames(friendlyNames)
            .setStatus(TaskStatus.COMPLETED)
            .build()
    }

    override fun logInput(request: AssignTaskRequest, logs: MutableList<String>) {
        val volumeRequest = request.volumeEstimateRequest
        logs.add("Coordinating volume estimation for ${volumeRequest.slicesCount} slices")
        logs.add("Alpha cut value: ${volumeRequest.alphaCutValue}")
        
        volumeRequest.slicesList.forEachIndexed { index, slice ->
            logs.add("Slice $index dimensions: ${slice.width}x${slice.height}")
        }
    }
    
    fun start(
        request: AssignTaskRequest,
        availableWorkers: List<Pair<String, String>>,
        logs: MutableList<String>
    ): Pair<List<ROI>, List<Pair<Int, Int>>> {
        // Log the input request
        logInput(request, logs)
        val totalSlices = request.volumeEstimateRequest.slicesCount
        logs.add("Starting distributed computation for $totalSlices slices")
        // Distribute tasks among available workers
        val taskDistribution = distributeTasks(totalSlices, availableWorkers, logs)
        if (taskDistribution.isEmpty()) {
            throw Exception("Failed to distribute tasks among workers")
        }
        val results = mutableListOf<Pair<String, WorkerResult>>()
        var failedWorkers = 0
        runBlocking {
            val jobs = taskDistribution.map { (worker, range) ->
                async(Dispatchers.IO) {
                    try {
                        // Post task assignment event
                        WorkEventBus.post(
                            UiEventWorkStatus.TaskAssigned(
                                humanName = worker.second,
                                portions = range.toList()
                            )
                        )
                        // Build sub-request for this worker
                        val subRequest = buildSubRequest(request, range)
                        // Execute the task and collect results
                        val startTime = System.currentTimeMillis()
                        val response = executeTask(worker, subRequest)
                        val endTime = System.currentTimeMillis()
                        if (response != null) {
                            synchronized(results) {
                                results.add(worker.first to WorkerResult(
                                    response = response,
                                    assignedRange = range.toList(),
                                    computationTime = endTime - startTime
                                )
                                )
                            }
                            logs.add("Worker ${worker.second} successfully processed slices ${range.first} to ${range.last} in ${endTime - startTime}ms")
                            // Post task completed event
                            WorkEventBus.post(
                                UiEventWorkStatus.TaskCompleted(
                                    humanName = worker.second,
                                    portions = range.toList(),
                                    computationTime = endTime - startTime
                                )
                            )
                        } else {
                            synchronized(this@VolumeEstimateCoordinatorStrategy) { failedWorkers++ }
                            logs.add("Worker ${worker.second} failed to process slices ${range.first} to ${range.last}")
                            // Post error event
                            WorkEventBus.post(
                                UiEventWorkStatus.Error(
                                    humanName = worker.second,
                                    message = "Failed to process slices ${range.first} to ${range.last}"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        synchronized(this@VolumeEstimateCoordinatorStrategy) { failedWorkers++ }
                        logs.add("Error processing task for worker ${worker.second}: ${e.message}")
                        // Post error event
                        WorkEventBus.post(
                            UiEventWorkStatus.Error(
                                humanName = worker.second,
                                message = "Exception: ${e.message}"
                            )
                        )
                    }
                }
            }
            jobs.awaitAll()
        }
        if (failedWorkers > 0) {
            logs.add("Warning: $failedWorkers workers failed to process their tasks")
        }
        if (results.isEmpty()) {
            throw Exception("All workers failed to process their tasks")
        }
        // Aggregate results from all workers
        val aggregatedResponse = aggregateResults(results)
        // Convert aggregated results to ROI and seed points
        val roiList = aggregatedResponse.volumeEstimateResponse.roisList.map { roi ->
            ROI(
                xMin = roi.xMin,
                yMin = roi.yMin,
                xMax = roi.xMax,
                yMax = roi.yMax
            )
        }
        val seedPoints = aggregatedResponse.volumeEstimateResponse.seedPointsList.map { seedPoint ->
            Pair(seedPoint.x, seedPoint.y)
        }
        // Verify we have results for all slices
        if (roiList.size != totalSlices || seedPoints.size != totalSlices) {
            throw Exception("Missing results for some slices. Expected $totalSlices, got ${roiList.size} ROIs and ${seedPoints.size} seed points")
        }
        // Log the results
        logs.add("Successfully processed ${roiList.size} ROIs and ${seedPoints.size} seed points")
        // Return the ROI list and seed points
        return Pair(roiList, seedPoints)
    }

    private fun executeTask(
        worker: Pair<String, String>,
        request: AssignTaskRequest
    ): AssignTaskResponse? {
        return try {
            networkService.executeTask(worker.first, request)
        } catch (e: Exception) {
            Log.e("VolumeEstimateCoordinatorStrategy", "Error executing task on worker ${worker.second}: ${e.message}")
            null
        }
    }
} 