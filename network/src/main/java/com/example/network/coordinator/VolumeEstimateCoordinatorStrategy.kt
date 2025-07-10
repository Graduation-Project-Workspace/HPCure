package com.example.network.coordinator

import android.util.Log
import com.example.domain.interfaces.network.ICoordinatorStrategy
import com.example.domain.interfaces.network.INetworkService
import com.example.domain.interfaces.network.WorkerResult
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI
import com.example.network.ui.UiEventWorkStatus
import com.example.network.util.WorkEventBus
import com.example.protos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

class VolumeEstimateCoordinatorStrategy(
    private val networkService: INetworkService
) : ICoordinatorStrategy {
    
    fun convertMRISequenceToRequest(mriSeq: MRISequence, alphaCutValue: Float): AssignTaskRequest {
        // Parallel image serialization
        val slices = runBlocking {
            mriSeq.images.mapIndexed { index, bitmap ->
                async(Dispatchers.Default) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    ImageSlice.newBuilder()
                        .setImageData(com.google.protobuf.ByteString.copyFrom(stream.toByteArray()))
                        .setWidth(bitmap.width)
                        .setHeight(bitmap.height)
                        .setSliceIndex(index)
                        .build()
                }
            }.awaitAll()
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
        // Calculate total size needed
        val totalSize = results.maxOfOrNull { (_, result) -> 
            result.assignedRange.maxOrNull() ?: 0 
        }?.plus(1) ?: 0
        
        // Pre-allocate lists with the correct size
        val aggregatedRois = MutableList(totalSize) { ROI(0, 0, 0, 0, 0f, -1) }
        val aggregatedSeedPoints = MutableList(totalSize) { SeedPoint.newBuilder().setX(0).setY(0).build() }
        val workerInfo = mutableMapOf<String, RowIndices>()
        val friendlyNames = mutableMapOf<String, String>()

        // Parallel processing of worker results
        runBlocking {
            val jobs = results.map { (workerAddress, result) ->
                async(Dispatchers.Default) {
                    val volumeResponse = result.response.volumeEstimateResponse
                    val startIndex = result.assignedRange.first()
                    
                    // Process ROIs for this worker
                    val workerRois = volumeResponse.roisList.mapIndexed { index, roi ->
                        val globalIndex = startIndex + index
                        if (globalIndex < totalSize) {
                            globalIndex to ROI(
                                xMin = roi.xMin,
                                yMin = roi.yMin,
                                xMax = roi.xMax,
                                yMax = roi.yMax,
                                sliceIndex = roi.sliceIndex,
                                score = roi.score
                            )
                        } else null
                    }.filterNotNull()
                    
                    // Process seed points for this worker
                    val workerSeeds = volumeResponse.seedPointsList.mapIndexed { index, seedPoint ->
                        val globalIndex = startIndex + index
                        if (globalIndex < totalSize) {
                            globalIndex to seedPoint
                        } else null
                    }.filterNotNull()
                    
                    // Create worker info
                    val workerInfoEntry = workerAddress to RowIndices.newBuilder()
                        .addAllValues(result.assignedRange)
                        .build()
                    
                    // Create friendly name entry
                    val friendlyNameEntry = workerAddress to (result.response.friendlyNamesMap[workerAddress] ?: workerAddress)
                    
                    Triple(workerRois, workerSeeds, Pair(workerInfoEntry, friendlyNameEntry))
                }
            }
            
            // Collect all results and merge them
            val processedResults = jobs.awaitAll()
            
            // Merge ROIs and seeds in parallel
            processedResults.forEach { (rois, seeds, info) ->
                rois.forEach { (index, roi) ->
                    aggregatedRois[index] = roi
                }
                seeds.forEach { (index, seed) ->
                    aggregatedSeedPoints[index] = seed
                }
                workerInfo[info.first.first] = info.first.second
                friendlyNames[info.second.first] = info.second.second
            }
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
                            .setScore(roi.score)
                            .setSliceIndex(roi.sliceIndex)
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
                            // redistribute these slices
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
                yMax = roi.yMax,
                score = roi.score,
                sliceIndex = roi.sliceIndex
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