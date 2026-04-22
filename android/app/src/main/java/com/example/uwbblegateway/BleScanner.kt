package com.example.uwbblegateway

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BleScanner"

/**
 * 真实 BLE 设备模型（替代原 BleDeviceMock）
 */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * 真实 Android BLE LE 扫描器
 * - 使用 BluetoothLeScanner 接口，累积设备列表（去重，按 address 更新 RSSI）
 * - 权限不足或蓝牙未开启时，Flow 静默关闭（不抛出异常，由调用方处理）
 */
class BleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var activeScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan(): Flow<List<BleDevice>> = callbackFlow {
        if (!hasPermissions()) {
            Log.w(TAG, "BLE scan skipped: missing permissions")
            close()
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "BLE scan skipped: Bluetooth disabled or unavailable")
            close()
            return@callbackFlow
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            Log.w(TAG, "BLE scan skipped: bluetoothLeScanner is null")
            close()
            return@callbackFlow
        }

        // 累积设备表（address → BleDevice），避免 UI 列表频繁抖动
        val deviceMap = mutableMapOf<String, BleDevice>()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: "未知设备"
                val address = result.device.address ?: return
                val device = BleDevice(
                    name = name,
                    address = address,
                    rssi = result.rssi,
                    isConnectable = result.isConnectable,
                    lastSeen = System.currentTimeMillis()
                )
                deviceMap[address] = device
                trySend(deviceMap.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error: $errorCode")
                close()
            }
        }

        activeScanCallback = callback
        leScanner.startScan(null, scanSettings, callback)
        Log.d(TAG, "BLE scan started")

        awaitClose {
            try {
                leScanner.stopScan(callback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scan: ${e.message}")
            }
            activeScanCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        activeScanCallback?.let { cb ->
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(cb)
            } catch (e: Exception) {
                Log.w(TAG, "stopScan error: ${e.message}")
            }
        }
        activeScanCallback = null
    }

    private fun hasPermissions(): Boolean {
        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}