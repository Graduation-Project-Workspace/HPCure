package com.example.domain.interfaces

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.protos.AssignTaskRequest
import com.example.protos.AssignTaskResponse


interface IComputationStrategy {
    fun generateInput(): Pair<Any, Any>
    fun buildRequest(input1: Any, input2: Any): AssignTaskRequest
    fun computeTask(request: AssignTaskRequest): AssignTaskResponse
    fun logInput(input1: Any, input2: Any, logs: SnapshotStateList<String>)
    fun logOutput(response: AssignTaskResponse, logs: SnapshotStateList<String>)
    fun getWorkerName(): String
} 