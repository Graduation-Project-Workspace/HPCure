package com.example.network.ui

/** Every message the coordinator or a worker wants to surface to the UI */
sealed class UiEventDeviceStatus {
    abstract val timestamp: Long   // millis since epoch

    data class WorkerStatusChanged(
        override val timestamp: Long = System.currentTimeMillis(),
        val humanName: String,
        val online: Boolean            // true = READY, false = OFFLINE/DEAD
    ) : UiEventDeviceStatus()
}

sealed class UiEventWorkStatus {
    abstract val timestamp: Long

    data class TaskAssigned(
        override val timestamp: Long = System.currentTimeMillis(),
        val humanName: String,
        val portions: List<Int>
    ) : UiEventWorkStatus()

    data class TaskNotAssigned(
        override val timestamp: Long = System.currentTimeMillis(),
        val humanName: String,
    ) : UiEventWorkStatus()

    data class TaskCompleted(
        override val timestamp: Long = System.currentTimeMillis(),
        val humanName: String,
        val portions: List<Int>,
        val computationTime: Long,
        val reassignedFrom: String? = null // Optional: who this was reassigned from
    ) : UiEventWorkStatus()

    data class Error(
        override val timestamp: Long = System.currentTimeMillis(),
        val humanName: String,
        val message: String
    ) : UiEventWorkStatus()
}
