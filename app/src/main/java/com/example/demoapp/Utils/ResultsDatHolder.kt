package com.example.demoapp.Utils

import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI

data class ReportEntry(
    val step: String, // e.g., ROI, Seed, Fuzzy
    var parallelTime: Long? = null,
    var serialTime: Long? = null,
    var grpcTime: Long? = null
)

object ResultsDataHolder {
    var mriSequence: MRISequence? = null
    var fullMriSequence: MRISequence? = null
    var cancerVolume: CancerVolume? = null
    var alphaCut: Float = 50f
    var timeTaken: Long = 0
    var roiList: List<ROI>? = null
    var seedPoints: List<Pair<Int, Int>>? = null
    var wholeProcessParallelTime: Long? = null
    var wholeProcessSerialTime: Long? = null
    var wholeProcessGrpcTime: Long? = null

    fun setWholeProcessTime(mode: String, time: Long) {
        when (mode) {
            "Parallel" -> wholeProcessParallelTime = time
            "Serial" -> wholeProcessSerialTime = time
            "GRPC", "gRPC" -> wholeProcessGrpcTime = time
        }
    }

    // Report entries for cumulative reporting
    val reportEntries: MutableList<ReportEntry> = mutableListOf()

    fun addOrUpdateReportEntry(step: String, mode: String, time: Long) {
        val entry = reportEntries.find { it.step == step }
        if (entry != null) {
            when (mode) {
                "Parallel" -> entry.parallelTime = time
                "Serial" -> entry.serialTime = time
                "gRPC" -> entry.grpcTime = time
            }
        } else {
            reportEntries.add(
                ReportEntry(
                    step = step,
                    parallelTime = if (mode == "Parallel") time else null,
                    serialTime = if (mode == "Serial") time else null,
                    grpcTime = if (mode == "gRPC") time else null
                )
            )
        }
    }

    fun addOrUpdateWholeProcessEntry(mode: String, time: Long) {
        val entry = reportEntries.find { it.step == "Whole process" }
        if (entry != null) {
            // Reset other modes to null, ensuring only the current recalculation is shown
            when (mode) {
                "Parallel" -> {
                    entry.parallelTime = time
                    entry.serialTime = null
                    entry.grpcTime = null
                }
                "Serial" -> {
                    entry.parallelTime = null
                    entry.serialTime = time
                    entry.grpcTime = null
                }
                "gRPC", "GRPC" -> {
                    entry.parallelTime = null
                    entry.serialTime = null
                    entry.grpcTime = time
                }
            }
        } else {
            reportEntries.add(
                ReportEntry(
                    step = "Whole process",
                    parallelTime = if (mode == "Parallel") time else null,
                    serialTime = if (mode == "Serial") time else null,
                    grpcTime = if (mode.equals("gRPC", ignoreCase = true)) time else null
                )
            )
        }
    }


    fun getTotalParallel(): Long = reportEntries.sumOf { it.parallelTime ?: 0 }
    fun getTotalSerial(): Long = reportEntries.sumOf { it.serialTime ?: 0 }
    fun getTotalGrpc(): Long = reportEntries.sumOf { it.grpcTime ?: 0 }
}