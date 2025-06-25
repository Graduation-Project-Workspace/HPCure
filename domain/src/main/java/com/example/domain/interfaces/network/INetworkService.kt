package com.example.domain.interfaces.network

import com.example.protos.AssignTaskRequest
import com.example.protos.AssignTaskResponse

interface INetworkService {
    fun executeTask(workerAddress: String, request: AssignTaskRequest): AssignTaskResponse
    fun getAvailableWorkers(): List<Pair<String, String>>
} 