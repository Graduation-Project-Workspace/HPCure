package com.example.domain.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object LogRepository {
    val sharedLogs: SnapshotStateList<String> = mutableStateListOf()
}