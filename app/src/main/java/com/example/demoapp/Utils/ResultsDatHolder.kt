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

    fun getTotalParallel(): Long = reportEntries.sumOf { it.parallelTime ?: 0 }
    fun getTotalSerial(): Long = reportEntries.sumOf { it.serialTime ?: 0 }
    fun getTotalGrpc(): Long = reportEntries.sumOf { it.grpcTime ?: 0 }
}