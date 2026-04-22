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
import kotlin.random.Random

private const val TAG = "UwbRangingManager"

data class UwbRangingData(
    val distanceMeters: Float,
    val azimuthDegrees: Float?,
    val elevationDegrees: Float?,
    val isSimulated: Boolean = false
)

class UwbRangingManager(private val context: Context) {

    private val _rangingFlow = MutableSharedFlow<UwbRangingData>(extraBufferCapacity = 64)
    private var simulationJob: Job? = null
    private var uwbSessionScope: UwbControllerSessionScope? = null

    fun isUwbSupported(): Boolean {
        // 1. 标准 Android 检测（华为通常会返回 false）
        val hasStandardUwb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
        } else {
            false
        }
        
        // 2. 厂商与机型白名单检测
        val isHuawei = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
        val isXiaomi = Build.MANUFACTURER.equals("XIAOMI", ignoreCase = true)
        val model = Build.MODEL
        
        // 更新检测逻辑，确保包含 "Max" 机型
        val isKnownUwbModel = model.run {
            // 华为机型 (Mate/P 系列)
            contains("PAL-") || contains("ALN-") || contains("CLS-") || contains("JAD-") ||contains("PLR-") ||
            contains("Mate 70") || contains("P50") || contains("Mate 60") ||
            // 小米机型：增加 "Max" 适配 (如 17 Pro Max)
            (isXiaomi && (contains("Pro") || contains("Ultra") || contains("Max")))
        }
        
        Log.d(TAG, "Model: $model | Manufacturer: ${Build.MANUFACTURER}")
        Log.d(TAG, "Standard UWB Feature: $hasStandardUwb | WhiteList Match: $isKnownUwbModel")
        
        // 3. 决策逻辑：如果是华为已知机型且安卓版本>=12，强制启用
        if (isHuawei && isKnownUwbModel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "✓ White-list hit: Forcing UWB support for $model")
            return true
        }
        
        return hasStandardUwb
    }

    fun getUwbStatus(): String = if (isUwbSupported()) "硬件可用" else "不支持 (将使用模拟)"

    fun startRangingFlow(): Flow<UwbRangingData> {
        val supported = isUwbSupported()
        Log.i(TAG, ">>> Starting UWB ranging flow, supported=$supported")
        
        if (supported) {
            Log.i(TAG, "Attempting REAL UWB ranging...")
            startRealRanging()
        } else {
            Log.w(TAG, "UWB not supported, falling back to SIMULATED ranging")
            startSimulatedRanging()
        }
        return _rangingFlow.asSharedFlow()
    }

    private fun startRealRanging() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "[Real UWB] Creating UwbManager instance...")
                val uwbManager = UwbManager.createInstance(context)
                Log.d(TAG, "[Real UWB] UwbManager created successfully")

                // 小米设备需要特殊的 Session 创建方式
                Log.d(TAG, "[Real UWB] Attempting to create session with peer address...")
                
                val peerAddress = UwbAddress(byteArrayOf(0x01, 0x02))
                val complexChannel = UwbComplexChannel(9, 11)
                
                // 尝试使用 prepareSession 直接创建
                val rangingParameters = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = 0x12345678,
                    subSessionId = 0,
                    sessionKeyInfo = null,
                    subSessionKeyInfo = null,
                    complexChannel = complexChannel,
                    peerDevices = listOf(androidx.core.uwb.UwbDevice(peerAddress)),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                )

                Log.d(TAG, "[Real UWB] Ranging parameters built, trying to start session...")
                
                // 关键修改：不使用 controllerSessionScope，直接调用 uwbManager 的方法
                // 注意：这可能需要根据小米的实际 API 调整
                val sessionFlow = try {
                    // 方式 1: 如果小米支持标准 API
                    uwbManager.controllerSessionScope()?.prepareSession(rangingParameters)
                } catch (e: NullPointerException) {
                    Log.w(TAG, "[Real UWB] controllerSessionScope returned null")
                    Log.w(TAG, "[Real UWB] Trying alternative approach for Xiaomi...")
                    
                    // 方式 2: 对于小米，可能需要使用不同的配置
                    val xiaomiRangingParams = RangingParameters(
                        uwbConfigType = RangingParameters.CONFIG_MULTICAST, // 尝试组播模式
                        sessionId = 0x12345678,
                        subSessionId = 0,
                        sessionKeyInfo = null,
                        subSessionKeyInfo = null,
                        complexChannel = complexChannel,
                        peerDevices = listOf(androidx.core.uwb.UwbDevice(peerAddress)),
                        updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                    )
                    uwbManager.controllerSessionScope()?.prepareSession(xiaomiRangingParams)
                }

                if (sessionFlow != null) {
                    Log.i(TAG, "[Real UWB] ✓ Session started, collecting results...")
                    sessionFlow.collect { result ->
                        when (result) {
                            is RangingResult.RangingResultPosition -> {
                                val position = result.position
                                val distance = position.distance?.value ?: 0f
                                val azimuth = position.azimuth?.value
                                Log.d(TAG, "[Real UWB] 📍 Position: distance=${distance}m, azimuth=${azimuth}°")
                                _rangingFlow.tryEmit(
                                    UwbRangingData(
                                        distanceMeters = distance,
                                        azimuthDegrees = azimuth,
                                        elevationDegrees = position.elevation?.value,
                                        isSimulated = false
                                    )
                                )
                            }
                            is RangingResult.RangingResultPeerDisconnected -> {
                                Log.w(TAG, "[Real UWB] ⚠ Peer disconnected")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "[Real UWB] ✗ Failed to create session on this Xiaomi device")
                    startSimulatedRanging()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Real UWB] ✗ FAILED: ${e.message}", e)
                startSimulatedRanging()
            }
        }
    }

    private fun startSimulatedRanging() {
        simulationJob?.cancel()
        Log.d(TAG, "[Simulation] Starting simulated ranging mode (10Hz)")
        simulationJob = CoroutineScope(Dispatchers.Default).launch {
            var baseDistance = 3.0f
            var count = 0
            while (true) {
                baseDistance = (baseDistance + (Random.nextFloat() - 0.5f) * 0.1f).coerceIn(0.5f, 10f)
                val noise = (Random.nextFloat() - 0.5f) * 0.05f
                val distance = baseDistance + noise
                val azimuth = Random.nextFloat() * 60 - 30
                
                if (count % 10 == 0) {
                    Log.d(TAG, "[Simulation] 📊 Simulated data: distance=${String.format("%.2f", distance)}m, azimuth=${String.format("%.1f", azimuth)}°")
                }
                
                _rangingFlow.tryEmit(
                    UwbRangingData(
                        distanceMeters = distance,
                        azimuthDegrees = azimuth,
                        elevationDegrees = null,
                        isSimulated = true
                    )
                )
                count++
                delay(100)
            }
        }
    }

    fun stopRanging() {
        Log.d(TAG, "=== Stopping UWB ranging ===")
        simulationJob?.cancel()
        simulationJob = null
        uwbSessionScope = null
        Log.d(TAG, "UWB ranging stopped")
    }

    @Deprecated("Use startRangingFlow()", ReplaceWith("startRangingFlow()"))
    fun startRanging(): Flow<UwbRangingData> = startRangingFlow()
}
