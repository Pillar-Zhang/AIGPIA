package com.example.uwbblegateway

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapState(
    val currentPosition: Position2D = Position2D(5f, 5f, 0f, "INIT"),
    val anchors: List<UwbAnchor> = emptyList(),
    val inspectionPoints: List<NavPoint> = emptyList(),
    val plannedPath: List<NavPoint> = emptyList(),
    val nextTarget: NavPoint? = null,
    val historyTrail: List<Position2D> = emptyList(),  // 历史轨迹（最多 200 点）
    val isPositioning: Boolean = false,
    val isSimulated: Boolean = true,
    val navBearing: Float = 0f,  // 下一巡检点方位角（度）
    val completedCount: Int = 0
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    val positioningEngine = PositioningEngine()

    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    init {
        // 初始化默认变电站巡检点
        val defaultPoints = listOf(
            NavPoint("P1", 2f, 2f, "主变压器 T1"),
            NavPoint("P2", 8f, 2f, "断路器 CB1"),
            NavPoint("P3", 8f, 8f, "避雷器 LA1"),
            NavPoint("P4", 2f, 8f, "电流互感器 CT1"),
            NavPoint("P5", 5f, 5f, "控制柜 CC1")
        )
        _mapState.value = _mapState.value.copy(
            anchors = positioningEngine.anchors.toList(),
            inspectionPoints = defaultPoints
        )
        planPath()
    }

    fun startPositioning(simulated: Boolean = true) {
        _mapState.value = _mapState.value.copy(isPositioning = true, isSimulated = simulated)
        positioningEngine.start(simulated)

        viewModelScope.launch {
            positioningEngine.positionFlow.collect { pos ->
                val current = _mapState.value
                // 更新历史轨迹（限制 200 点）
                val newTrail = (current.historyTrail + pos).takeLast(200)
                // 更新导航目标
                val next = PathPlanner.nextTarget(pos.x, pos.y, current.plannedPath)
                val bearing = next?.let {
                    PathPlanner.bearingDegrees(pos.x, pos.y, it.x, it.y)
                } ?: current.navBearing
                val completed = current.inspectionPoints.count { it.isDone }

                _mapState.value = current.copy(
                    currentPosition = pos,
                    historyTrail = newTrail,
                    nextTarget = next,
                    navBearing = bearing,
                    completedCount = completed,
                    plannedPath = current.plannedPath  // 保持引用（NavPoint.isDone 已在内部修改）
                )
            }
        }
    }

    fun stopPositioning() {
        positioningEngine.stop()
        _mapState.value = _mapState.value.copy(isPositioning = false)
    }

    fun planPath() {
        val state = _mapState.value
        val path = PathPlanner.planPath(
            state.currentPosition.x,
            state.currentPosition.y,
            state.inspectionPoints
        )
        _mapState.value = state.copy(plannedPath = path)
    }

    fun resetInspection() {
        val points = _mapState.value.inspectionPoints.map { it.copy(isDone = false) }
        _mapState.value = _mapState.value.copy(
            inspectionPoints = points,
            historyTrail = emptyList(),
            completedCount = 0
        )
        planPath()
    }

    /** 将定位 + 导航数据通过 BleManager 下发眼镜端 */
    fun pushNavToGlasses(bleManager: BleManager) {
        val state = _mapState.value
        bleManager.sendNavData(state.currentPosition, state.nextTarget)
    }

    override fun onCleared() {
        super.onCleared()
        positioningEngine.stop()
    }
}
