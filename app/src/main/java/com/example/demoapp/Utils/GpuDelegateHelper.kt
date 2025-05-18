package com.example.demoapp.Utils

import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

class GpuDelegateHelper {
    val isGpuDelegateAvailable: Boolean
    get() = CompatibilityList().isDelegateSupportedOnThisDevice

    fun createGpuDelegate(): Delegate? {
        return if (isGpuDelegateAvailable) {
            val options = GpuDelegate.Options().apply {
                setQuantizedModelsAllowed(true)
            }
            GpuDelegate(options)
        } else {
            null
        }
    }
}