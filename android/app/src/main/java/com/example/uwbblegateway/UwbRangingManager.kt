package com.example.uwbblegateway

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class UwbRangingManager(private val context: Context) {

    /**
     * Checks if UWB is supported on this device.
     */
    fun isUwbSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
        } else {
            false
        }
    }

    /**
     * Returns a string representation of the UWB status.
     */
    fun getUwbStatus(): String {
        return if (isUwbSupported()) "可用" else "不支持"
    }

    fun startRanging(): Flow<Any> = emptyFlow()
}