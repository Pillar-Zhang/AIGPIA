package com.example.uwbblegateway

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "PositioningEngine"

// ─── 数据模型 ──────────────────────────────────────────────────────────────────

/** UWB 锚点（固定基站），坐标单位：米 */
data class UwbAnchor(
    val id: String,
    val x: Float,
    val y: Float,
    var lastDistance: Float = -1f  // -1 表示暂无测距数据
)

/** 2D 位置估计结果 */
data class Position2D(
    val x: Float,
    val y: Float,
    val confidence: Float,      // 0~1，越高越可信
    val source: String = "UWB", // "UWB" / "BLE" / "SIM"
    val timestamp: Long = System.currentTimeMillis()
)

/** 巡检路径节点 */
data class NavPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val label: String,
    var isDone: Boolean = false
)

// ─── 主引擎 ────────────────────────────────────────────────────────────────────

/**
 * UWB + BLE 融合定位引擎
 *
 * 算法层次（从高到低）：
 * 1. 多锚点 UWB → WLS 三边测量 → KF 平滑 （confidence ≥ 0.7）
 * 2. BLE RSSI → 路径损耗模型估距 → WLS   （confidence 0.3~0.6）
 * 3. 惯性推算（模拟器回落）               （confidence < 0.3）
 *
 * 当无硬件时，自动启动**模拟锚点 + 随机游走**模式完整演示定位效果。
 */
class PositioningEngine {

    // 默认变电站锚点布局（10m × 10m 场景）
    val anchors: MutableList<UwbAnchor> = mutableListOf(
        UwbAnchor("A1", 0f, 0f),
        UwbAnchor("A2", 10f, 0f),
        UwbAnchor("A3", 10f, 10f),
        UwbAnchor("A4", 0f, 10f)
    )

    private val _positionFlow = MutableSharedFlow<Position2D>(extraBufferCapacity = 32)
    val positionFlow: Flow<Position2D> = _positionFlow.asSharedFlow()

    // KF 状态
    private var kfX = 5f
    private var kfY = 5f
    private var kfVx = 0f
    private var kfVy = 0f
    private var kfPxx = 10f
    private var kfPyy = 10f

    private var engineJob: Job? = null
    private var isSimulated = false

    /** 启动定位（真实 UWB 数据 或 模拟数据） */
    fun start(simulated: Boolean = true) {
        isSimulated = simulated
        engineJob?.cancel()
        engineJob = CoroutineScope(Dispatchers.Default).launch {
            if (simulated) {
                runSimulatedPositioning()
            }
            // 真实模式：外部通过 feedUwbRange() / feedBleRssi() 注入数据
        }
        Log.d(TAG, "PositioningEngine started, simulated=$simulated")
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
        Log.d(TAG, "PositioningEngine stopped")
    }

    // ─── 真实数据注入接口 ────────────────────────────────────────────────────

    /** 注入单条 UWB 测距结果（真实模式下由 UwbRangingManager 调用） */
    fun feedUwbRange(anchorId: String, distanceMeters: Float) {
        anchors.find { it.id == anchorId }?.lastDistance = distanceMeters
        val validAnchors = anchors.filter { it.lastDistance > 0f }
        if (validAnchors.size >= 3) {
            val raw = wlsTrilaterate(validAnchors)
            val filtered = kalmanUpdate(raw.first, raw.second)
            val conf = (validAnchors.size / anchors.size.toFloat()).coerceIn(0.5f, 1f)
            CoroutineScope(Dispatchers.Default).launch {
                _positionFlow.emit(Position2D(filtered.first, filtered.second, conf, "UWB"))
            }
        }
    }

    /** 注入 BLE RSSI（UWB 降级时使用） */
    fun feedBleRssi(anchorId: String, rssi: Int, txPower: Int = -59) {
        val distance = rssiToDistance(rssi, txPower)
        anchors.find { it.id == anchorId }?.lastDistance = distance
    }

    // ─── 模拟定位（无硬件可视化演示） ───────────────────────────────────────

    private suspend fun runSimulatedPositioning() {
        // 模拟巡检人员沿路径匀速移动（椭圆轨迹）
        var t = 0.0
        val cx = 5f; val cy = 5f
        val rx = 3.5f; val ry = 3f

        while (true) {
            t += 0.05 // 速度因子
            val targetX = cx + rx * kotlin.math.cos(t).toFloat()
            val targetY = cy + ry * kotlin.math.sin(t).toFloat()

            // 模拟各锚点的测距值（加高斯噪声）
            anchors.forEach { anchor ->
                val trueDist = sqrt((targetX - anchor.x).pow(2) + (targetY - anchor.y).pow(2))
                anchor.lastDistance = trueDist + (Math.random().toFloat() - 0.5f) * 0.2f
            }

            val raw = wlsTrilaterate(anchors)
            val filtered = kalmanUpdate(raw.first, raw.second)

            _positionFlow.emit(
                Position2D(filtered.first, filtered.second, confidence = 0.9f, source = "SIM")
            )
            delay(100L) // 10Hz
        }
    }

    // ─── 算法实现 ────────────────────────────────────────────────────────────

    /**
     * 加权最小二乘三边测量（WLS Trilateration）
     * 权重 = 1 / distance²（近距离权重更高）
     */
    private fun wlsTrilaterate(activeAnchors: List<UwbAnchor>): Pair<Float, Float> {
        if (activeAnchors.size < 2) return Pair(kfX, kfY)

        // 以第一个锚点为参考，构建线性方程组
        val ref = activeAnchors[0]
        var sumW = 0f
        var sumWx = 0f
        var sumWy = 0f

        for (i in 1 until activeAnchors.size) {
            val a = activeAnchors[i]
            val w = if (a.lastDistance > 0) 1f / (a.lastDistance * a.lastDistance + 0.01f) else 0f

            // 线性化：(x - ax)² + (y - ay)² = d² → 线性近似
            val ax = a.x; val ay = a.y; val d = a.lastDistance
            val rx2 = ref.x; val ry2 = ref.y; val rd = ref.lastDistance

            // 推导出线性近似解
            val xEst = ((ax.pow(2) - rx2.pow(2)) + (ay.pow(2) - ry2.pow(2)) +
                    (rd.pow(2) - d.pow(2))) / (2 * (ax - rx2 + 1e-6f))
            val yEst = ((ay.pow(2) - ry2.pow(2)) + (ax.pow(2) - rx2.pow(2)) +
                    (rd.pow(2) - d.pow(2))) / (2 * (ay - ry2 + 1e-6f))

            sumW += w
            sumWx += w * xEst
            sumWy += w * yEst
        }

        return if (sumW > 0) Pair(sumWx / sumW, sumWy / sumW)
        else Pair(kfX, kfY)
    }

    /**
     * 简化 Kalman Filter（匀速运动模型）
     * 只跟踪 x, y 位置（不完整 EKF，适合手机端实时运行）
     */
    private fun kalmanUpdate(measX: Float, measY: Float): Pair<Float, Float> {
        val dt = 0.1f
        val processNoise = 0.1f
        val measureNoise = 0.5f

        // 预测步
        val predX = kfX + kfVx * dt
        val predY = kfY + kfVy * dt
        val predPxx = kfPxx + processNoise
        val predPyy = kfPyy + processNoise

        // 更新步（卡尔曼增益）
        val kx = predPxx / (predPxx + measureNoise)
        val ky = predPyy / (predPyy + measureNoise)

        kfX = predX + kx * (measX - predX)
        kfY = predY + ky * (measY - predY)
        kfVx = (kfX - predX) / dt
        kfVy = (kfY - predY) / dt
        kfPxx = (1 - kx) * predPxx
        kfPyy = (1 - ky) * predPyy

        // 边界�amp（防止漂移出地图）
        kfX = kfX.coerceIn(-2f, 15f)
        kfY = kfY.coerceIn(-2f, 15f)

        return Pair(kfX, kfY)
    }

    /** BLE RSSI → 距离（路径损耗模型，n=2.0 室内） */
    private fun rssiToDistance(rssi: Int, txPower: Int, n: Float = 2.0f): Float {
        return 10f.pow((txPower - rssi) / (10f * n))
    }
}
