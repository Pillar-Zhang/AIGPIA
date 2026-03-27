package com.example.uwbblegateway

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

private const val TAG = "UwbRangingManager"

data class UwbRangingData(
    val distanceMeters: Float,
    val azimuthDegrees: Float?,
    val elevationDegrees: Float?,
    val isSimulated: Boolean = false
)

/**
 * UWB 测距管理器
 * - 设备支持 UWB 时：使用 Jetpack UWB API（androidx.core.uwb）
 * - 设备/模拟器不支持时：自动降级为模拟数据，并在数据中标注 [isSimulated = true]
 */
class UwbRangingManager(private val context: Context) {

    private val _rangingFlow = MutableSharedFlow<UwbRangingData>(extraBufferCapacity = 64)
    private var simulationJob: Job? = null
    private var uwbSessionScope: UwbControllerSessionScope? = null

    fun isUwbSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
        } else {
            false
        }
    }

    fun getUwbStatus(): String = if (isUwbSupported()) "硬件可用" else "不支持 (将使用模拟)"

    /**
     * 启动测距，返回实时测距数据流。
     * 调用方通过 collect 处理数据，调用 stopRanging() 停止。
     */
    fun startRangingFlow(): Flow<UwbRangingData> {
        if (isUwbSupported()) {
            startRealRanging()
        } else {
            startSimulatedRanging()
        }
        return _rangingFlow.asSharedFlow()
    }

    private fun startRealRanging() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uwbManager = UwbManager.createInstance(context)
                val sessionScope = uwbManager.controllerSessionScope()
                uwbSessionScope = sessionScope

                // 构建测距参数（对端地址在真实场景通过 BLE 带外协商，此处使用固定示例地址）
                val peerAddress = UwbAddress(byteArrayOf(0x01, 0x02))
                val rangingParameters = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = 0x12345678,
                    subSessionId = 0,
                    sessionKeyInfo = null,
                    subSessionKeyInfo = null,
                    complexChannel = UwbComplexChannel(9, 11),
                    peerDevices = listOf(androidx.core.uwb.UwbDevice(peerAddress)),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                )

                sessionScope.prepareSession(rangingParameters).collect { result ->
                    when (result) {
                        is RangingResult.RangingResultPosition -> {
                            val position = result.position
                            _rangingFlow.tryEmit(
                                UwbRangingData(
                                    distanceMeters = position.distance?.value ?: 0f,
                                    azimuthDegrees = position.azimuth?.value,
                                    elevationDegrees = position.elevation?.value,
                                    isSimulated = false
                                )
                            )
                        }
                        is RangingResult.RangingResultPeerDisconnected -> {
                            Log.w(TAG, "UWB peer disconnected")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Real UWB ranging failed, falling back to simulation: ${e.message}")
                startSimulatedRanging()
            }
        }
    }

    /**
     * 模拟模式：每 100ms 发射一次模拟测距数据（10Hz），在模拟器上正常运行
     */
    private fun startSimulatedRanging() {
        simulationJob?.cancel()
        simulationJob = CoroutineScope(Dispatchers.Default).launch {
            var baseDistance = 3.0f
            while (true) {
                // 模拟缓慢移动的距离（随机游走）
                baseDistance = (baseDistance + (Random.nextFloat() - 0.5f) * 0.1f).coerceIn(0.5f, 10f)
                val noise = (Random.nextFloat() - 0.5f) * 0.05f
                _rangingFlow.tryEmit(
                    UwbRangingData(
                        distanceMeters = baseDistance + noise,
                        azimuthDegrees = Random.nextFloat() * 60 - 30, // -30° ~ +30°
                        elevationDegrees = null,
                        isSimulated = true
                    )
                )
                delay(100) // 10Hz
            }
        }
    }

    fun stopRanging() {
        simulationJob?.cancel()
        simulationJob = null
        uwbSessionScope = null
        Log.d(TAG, "UWB ranging stopped")
    }

    // Legacy compat
    @Deprecated("Use startRangingFlow()", ReplaceWith("startRangingFlow()"))
    fun startRanging(): Flow<UwbRangingData> = startRangingFlow()
}