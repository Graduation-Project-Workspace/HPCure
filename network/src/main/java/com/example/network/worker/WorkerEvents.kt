package com.example.network.worker

import com.example.protos.AssignTaskResponse
import io.grpc.ManagedChannel

interface WorkerEvents {
    fun onWorkerMetricsUpdated(workerId: String, computationTime: Long)
    fun logWorkerContributions(response: AssignTaskResponse)
    fun shutdownWorkerChannel(channel: ManagedChannel)
} 