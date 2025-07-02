package com.example.demoapp.Utils

import com.example.domain.model.CancerVolume
import com.example.domain.model.MRISequence
import com.example.domain.model.ROI

data class ReportEntry(
    val step: String, // e.g., ROI, Seed, Fuzzy
    var parallelTime: Long? = null,
    var serialTime: Long? = null
)

object ResultsDataHolder {
    var mriSequence: MRISequence? = null
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
            if (mode == "Parallel") entry.parallelTime = time
            else if (mode == "Serial") entry.serialTime = time
        } else {
            reportEntries.add(
                ReportEntry(
                    step = step,
                    parallelTime = if (mode == "Parallel") time else null,
                    serialTime = if (mode == "Serial") time else null
                )
            )
        }
    }
}