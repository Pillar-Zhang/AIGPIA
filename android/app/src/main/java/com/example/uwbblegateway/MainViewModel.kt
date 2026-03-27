package com.example.uwbblegateway

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 连接状态集合（PMS、BLE模组、UWB模组）
 */
data class ConnectionState(
    val pmsConnected: Boolean = false,
    val bleConnected: Boolean = false,
    val uwbConnected: Boolean = false,
)

/**
 * BLE 扫描状态
 */
data class BleScanState(
    val isScanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
)

/**
 * UWB 测距状态
 */
data class UwbState(
    val isRanging: Boolean = false,
    val isSupported: Boolean = false,
    val isSimulated: Boolean = false,
    val distanceMeters: Float = 0f,
    val azimuthDegrees: Float = 0f,
    val lastUpdatedMs: Long = 0L,
)

/**
 * 主 ViewModel：管理连接状态、BLE 扫描、UWB 测距
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleManager(application)
    val bleScanner = BleScanner(application)
    val uwbRangingManager = UwbRangingManager(application)
    val apiService = ApiService.create()

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bleScanState = MutableStateFlow(BleScanState())
    val bleScanState: StateFlow<BleScanState> = _bleScanState.asStateFlow()

    private val _uwbState = MutableStateFlow(UwbState(isSupported = uwbRangingManager.isUwbSupported()))
    val uwbState: StateFlow<UwbState> = _uwbState.asStateFlow()

    // ─── 连接操作 ───────────────────────────────────────────────

    fun connectPms() {
        viewModelScope.launch {
            try {
                val result = apiService.connectPms()
                val statusVal = (result["status"] ?: result["code"]) as? Number
                _connectionState.value = _connectionState.value.copy(pmsConnected = statusVal?.toInt() == 1)
            } catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(pmsConnected = false)
            }
        }
    }

    fun connectBle() {
        viewModelScope.launch {
            try {
                val result = apiService.connectBle()
                val statusVal = (result["status"] ?: result["code"]) as? Number
                _connectionState.value = _connectionState.value.copy(bleConnected = statusVal?.toInt() == 1)
            } catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(bleConnected = false)
            }
        }
    }

    fun connectUwb() {
        viewModelScope.launch {
            try {
                val result = apiService.connectUwb()
                val statusVal = (result["status"] ?: result["code"]) as? Number
                _connectionState.value = _connectionState.value.copy(uwbConnected = statusVal?.toInt() == 1)
            } catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(uwbConnected = false)
            }
        }
    }

    // ─── BLE 扫描 ────────────────────────────────────────────────

    fun toggleBleScan() {
        val current = _bleScanState.value
        if (current.isScanning) {
            stopBleScan()
        } else {
            startBleScan()
        }
    }

    private fun startBleScan() {
        _bleScanState.value = _bleScanState.value.copy(isScanning = true, devices = emptyList())
        viewModelScope.launch {
            bleScanner.startScan().collect { devices ->
                _bleScanState.value = _bleScanState.value.copy(devices = devices)
            }
        }
    }

    fun stopBleScan() {
        bleScanner.stopScan()
        _bleScanState.value = _bleScanState.value.copy(isScanning = false)
    }

    // ─── UWB 测距 ────────────────────────────────────────────────

    fun toggleUwbRanging() {
        val current = _uwbState.value
        if (current.isRanging) {
            stopUwbRanging()
        } else {
            startUwbRanging()
        }
    }

    private fun startUwbRanging() {
        _uwbState.value = _uwbState.value.copy(isRanging = true)
        viewModelScope.launch {
            uwbRangingManager.startRangingFlow().collect { data ->
                _uwbState.value = _uwbState.value.copy(
                    distanceMeters = data.distanceMeters,
                    azimuthDegrees = data.azimuthDegrees ?: 0f,
                    isSimulated = data.isSimulated,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun stopUwbRanging() {
        uwbRangingManager.stopRanging()
        _uwbState.value = _uwbState.value.copy(isRanging = false)
    }

    // ─── 数据上传 ─────────────────────────────────────────────────

    fun uploadFusionReport(glassesData: BleManager.GlassesData) {
        viewModelScope.launch {
            try {
                val uwb = _uwbState.value
                val fusionData = mapOf(
                    "test_id" to "TEST_${System.currentTimeMillis()}",
                    "test_type" to "full_system_test",
                    "environment" to "indoor_substation",
                    "glasses_info" to mapOf(
                        "device_name" to (glassesData.deviceName ?: "Unknown"),
                        "battery" to glassesData.battery,
                        "status" to glassesData.status.name
                    ),
                    "fusion_result" to mapOf(
                        "fused_distance_m" to uwb.distanceMeters,
                        "azimuth_degrees" to uwb.azimuthDegrees,
                        "is_simulated" to uwb.isSimulated,
                        "confidence" to if (uwb.isSimulated) 0.5 else 0.95
                    ),
                    "timestamp" to System.currentTimeMillis()
                )
                apiService.uploadFusionTestData(fusionData)
            } catch (e: Exception) {
                // 静默失败，不阻塞 UI
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
        uwbRangingManager.stopRanging()
    }
}
