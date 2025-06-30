package com.example.network.util

import com.example.network.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object DeviceEventBus {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _events = MutableSharedFlow<UiEventDeviceStatus>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Non-suspend version for use in non-coroutine contexts
    fun post(event: UiEventDeviceStatus) {
        scope.launch {
            _events.emit(event)
        }
    }
}

object WorkEventBus {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _events = MutableSharedFlow<UiEventWorkStatus>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Non-suspend version for use in non-coroutine contexts
    fun post(event: UiEventWorkStatus) {
        scope.launch {
            _events.emit(event)
        }
    }
}