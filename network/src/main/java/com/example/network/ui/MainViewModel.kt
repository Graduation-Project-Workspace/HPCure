package com.example.network.ui

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.domain.interfaces.IComputationStrategy
import com.example.domain.interfaces.IRoiPredictor
import com.example.domain.interfaces.ISeedPredictor
import com.example.domain.usecase.LogRepository
import com.example.network.computation.*
import com.example.network.network.*
import com.example.network.util.*
import kotlinx.coroutines.launch

class MainViewModel(
    val network: GrpcNetwork
) : ViewModel() {
    private val _isComputing = MutableStateFlow(false)
    val isComputing: StateFlow<Boolean> = _isComputing

    // Expose the logs directly since it's already a SnapshotStateList
    val debugLogs: SnapshotStateList<String> = network.logs

    val deviceLogFeed: StateFlow<Map<String, String>> =
        DeviceEventBus.events
            .scan(emptyMap<String, String>()) { acc, event ->
                when (event) {
                    is UiEventDeviceStatus.WorkerStatusChanged -> {
                        acc + (event.humanName to event.toPrettyString())
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val workLogFeed: StateFlow<Map<String, String>> =
        WorkEventBus.events
            .scan(emptyMap<String, String>()) { acc, event ->
                val key = when (event) {
                    is UiEventWorkStatus.TaskAssigned    -> event.humanName
                    is UiEventWorkStatus.TaskNotAssigned    -> event.humanName
                    is UiEventWorkStatus.TaskCompleted -> event.humanName
                    is UiEventWorkStatus.Error -> event.humanName
                }

                acc + (key to event.toPrettyString())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Convert any UiEvent to a human‐readable line */
    private fun UiEventDeviceStatus.toPrettyString(): String = when (this) {
        is UiEventDeviceStatus.WorkerStatusChanged -> if (online) "✅ Online" else "🔴 Offline"
    }

    private fun UiEventWorkStatus.toPrettyString(): String = when (this) {
        is UiEventWorkStatus.TaskAssigned  -> "➡️  Assigned portions [${portions.joinToString(", ")}]"
        is UiEventWorkStatus.TaskNotAssigned  -> "\uD83D\uDE34 is idle now"
        is UiEventWorkStatus.TaskCompleted -> "✔️ Computed portions [${portions.joinToString(", ")}] in ${computationTime}ms"
        is UiEventWorkStatus.Error        -> "⚠️  Cannot Compute tasks (device is offline)"
    }

    fun getLocalIpAddress(): String = network.getLocalIpAddress()

    fun startComputation(strategy: IComputationStrategy) {
        viewModelScope.launch {
            try {
                _isComputing.value = true
                network.startAsClient(viewModelScope, strategy) {
                    viewModelScope.launch {
                        _isComputing.value = false
                    }
                }
            } catch (e: Exception) {
                debugLogs.add("Error starting computation: ${e.message}")
                _isComputing.value = false
            }
        }
    }
}

class MainViewModelFactory(
    private val context: Context,
    private val roiPredictor: IRoiPredictor,
    private val seedPredictor: ISeedPredictor
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val network = GrpcNetwork(LogRepository.sharedLogs, context, roiPredictor, seedPredictor)
            return MainViewModel(network) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
