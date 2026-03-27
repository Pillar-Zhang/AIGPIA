package com.example.uwbblegateway

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "InspectionRepository"

/**
 * 巡检任务数据仓库
 * - 模拟从 PMS3.0 拉取任务
 * - 本地管理任务状态（Phase 4 升级为 Room 持久化）
 */
class InspectionRepository {

    private val _tasks = MutableStateFlow<List<InspectionTask>>(generateSampleTasks())
    val tasks: Flow<List<InspectionTask>> = _tasks.asStateFlow()

    fun getTask(taskId: String): InspectionTask? = _tasks.value.find { it.id == taskId }

    /** 开始任务 */
    fun startTask(taskId: String) {
        updateTask(taskId) { it.copy(status = TaskStatus.IN_PROGRESS, startedAt = System.currentTimeMillis()) }
    }

    /** 暂停任务 */
    fun pauseTask(taskId: String) {
        updateTask(taskId) { it.copy(status = TaskStatus.PAUSED) }
    }

    /** 终止任务 */
    fun abortTask(taskId: String) {
        updateTask(taskId) { it.copy(status = TaskStatus.ABORTED) }
    }

    /** 完成当前步骤，推进到下一步 */
    fun completeStep(taskId: String, stepId: String, photoPath: String? = null) {
        updateTask(taskId) { task ->
            val updatedSteps = task.steps.map { step ->
                if (step.id == stepId) step.copy(
                    isCompleted = true,
                    completedAt = System.currentTimeMillis(),
                    photoPath = photoPath
                ) else step
            }
            val allDone = updatedSteps.all { it.isCompleted }
            task.copy(
                steps = updatedSteps,
                status = if (allDone) TaskStatus.COMPLETED else task.status,
                completedAt = if (allDone) System.currentTimeMillis() else null
            )
        }
        Log.d(TAG, "Step $stepId completed in task $taskId")
    }

    private fun updateTask(taskId: String, transform: (InspectionTask) -> InspectionTask) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
    }

    // ─── 样本数据（模拟 PMS3.0 下发） ─────────────────────────────────────────

    private fun generateSampleTasks(): List<InspectionTask> = listOf(
        InspectionTask(
            id = "T001",
            title = "110kV 变电站日常巡检",
            substationName = "城西 110kV 变电站",
            taskType = TaskType.ROUTINE,
            priority = 2,
            assignee = "张工",
            steps = listOf(
                InspectionStep("S001-1", 1, "检查主变压器外观及油位", "主变压器 T1", "注意高压区域，保持安全距离≥1m", requirePhoto = true),
                InspectionStep("S001-2", 2, "检查断路器分合闸状态", "断路器 CB1", "不得触碰带电设备"),
                InspectionStep("S001-3", 3, "读取 110kV 电压表读数", "电压互感器 PT1", requirePhoto = true),
                InspectionStep("S001-4", 4, "检查避雷器放电计数器", "避雷器 LA1"),
                InspectionStep("S001-5", 5, "检查控制柜指示灯状态", "控制柜 CC1", requirePhoto = true)
            )
        ),
        InspectionTask(
            id = "T002",
            title = "变压器 T2 停电倒闸操作",
            substationName = "城西 110kV 变电站",
            taskType = TaskType.OPERATION_TICKET,
            priority = 1,
            assignee = "李工",
            steps = listOf(
                InspectionStep("S002-1", 1, "确认变压器 T2 负荷已转移", "主变压器 T2", "操作前必须核实负荷转移完毕"),
                InspectionStep("S002-2", 2, "拉开 110kV 断路器 CB2", "断路器 CB2", "确认线路无电流后操作"),
                InspectionStep("S002-3", 3, "拉开 110kV 隔离开关 GK2", "隔离开关 GK2", "断路器断开后方可操作"),
                InspectionStep("S002-4", 4, "合上接地刀闸 GND2", "接地刀闸 GND2", "验电确认无电压后挂接地线"),
                InspectionStep("S002-5", 5, "悬挂「禁止合闸」标示牌", "断路器 CB2", "标示牌须悬挂在操作手柄上")
            )
        ),
        InspectionTask(
            id = "T003",
            title = "35kV 配电室月度巡检",
            substationName = "城东 35kV 配电室",
            taskType = TaskType.ROUTINE,
            priority = 3,
            assignee = "王工",
            steps = listOf(
                InspectionStep("S003-1", 1, "检查 35kV 开关柜外观", "开关柜 KG1"),
                InspectionStep("S003-2", 2, "检查电缆桥架绝缘情况", "电缆桥架"),
                InspectionStep("S003-3", 3, "检查室内温湿度记录仪", "温湿度记录仪", requirePhoto = true)
            )
        )
    )
}
