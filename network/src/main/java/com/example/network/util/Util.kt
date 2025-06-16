package com.example.network.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

@SuppressLint("HardwareIds")
fun Context.loadFriendlyName(): String {
    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getString("friendly_name", null)
        ?: ("Tablet-" + Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ).takeLast(4))
}