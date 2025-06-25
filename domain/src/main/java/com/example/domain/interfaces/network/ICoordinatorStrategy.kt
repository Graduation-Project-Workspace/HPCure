package com.example.domain.interfaces.network

import com.example.protos.AssignTaskRequest
import com.example.protos.AssignTaskResponse

interface ICoordinatorStrategy {
    fun distributeTasks(
        totalSize: Int,
        availableWorkers: List<Pair<String, String>>,
        logs: MutableList<String>
    ): Map<Pair<String, String>, IntRange>

    fun buildSubRequest(
        request: AssignTaskRequest,
        range: IntRange
    ): AssignTaskRequest

    fun aggregateResults(
        results: List<Pair<String, WorkerResult>>
    ): AssignTaskResponse

    fun logInput(request: AssignTaskRequest, logs: MutableList<String>)
}

data class WorkerResult(
    val response: AssignTaskResponse,
    val assignedRange: List<Int>,
    val computationTime: Long
) 