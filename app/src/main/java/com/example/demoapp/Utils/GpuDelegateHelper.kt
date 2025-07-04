package com.example.demoapp.Utils

import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

class GpuDelegateHelper {

    companion object {
        private val activeDelegates = mutableListOf<GpuDelegate>()

        fun closeAllDelegates() {
            activeDelegates.forEach {
                try {
                    it.close()
                    Log.d("GpuDelegateHelper", "Closed GPU delegate successfully.")
                } catch (e: Exception) {
                    Log.w("GpuDelegateHelper", "Failed to close GPU delegate.", e)
                }
            }
            activeDelegates.clear()
        }
    }

    val isGpuDelegateAvailable: Boolean
        get() = CompatibilityList().isDelegateSupportedOnThisDevice

    fun createGpuDelegate(): Delegate? {
        return if (isGpuDelegateAvailable) {
            val options = GpuDelegate.Options().apply {
                setQuantizedModelsAllowed(true)
            }
            val gpuDelegate = GpuDelegate(options)
            activeDelegates.add(gpuDelegate)  // Track for explicit closing
            gpuDelegate
        } else {
            null
        }
    }
}
