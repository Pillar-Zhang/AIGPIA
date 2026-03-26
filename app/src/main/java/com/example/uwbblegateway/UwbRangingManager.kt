package com.example.uwbblegateway

import android.content.Context
import android.os.Build
import android.uwb.UwbManager
import android.uwb.UwbRangingConfiguration
import android.uwb.UwbRangingSession
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UwbRangingManager(private val context: Context) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun isUwbAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.getSystemService(Context.UWB_SERVICE) is UwbManager &&
                (context.getSystemService(Context.UWB_SERVICE) as UwbManager).isAvailable
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun startRanging(): Flow<UwbRangingSession> = flow {
        if (!isUwbAvailable()) {
            throw UnsupportedOperationException("UWB not available on this device")
        }

        val uwbManager = context.getSystemService(Context.UWB_SERVICE) as UwbManager
        val config = UwbRangingConfiguration.Builder()
            .setDestinationAddresses(listOf(byteArrayOf(0x01, 0x02, 0x03)))
            .build()

        val session = uwbManager.openRangingSession(config)
        emit(session)
        session.close()
    }
}