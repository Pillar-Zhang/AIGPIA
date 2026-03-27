package com.example.uwbblegateway

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InspectionUiState(
    val tasks: List<InspectionTask> = emptyList(),
    val activeTask: InspectionTask? = null,
    val pathDeviationAlert: Boolean = false
)

class InspectionViewModel(application: Application) : AndroidViewModel(application) {

    val repository = InspectionRepository()

    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.tasks.collect { tasks ->
                _uiState.value = _uiState.value.copy(
                    tasks = tasks,
                    activeTask = tasks.find { it.status == TaskStatus.IN_PROGRESS }
                )
            }
        }
    }

    fun startTask(taskId: String) {
        repository.startTask(taskId)
    }

    fun pauseTask(taskId: String) {
        repository.pauseTask(taskId)
    }

    fun abortTask(taskId: String) {
        repository.abortTask(taskId)
    }

    fun completeCurrentStep(taskId: String) {
        val task = repository.getTask(taskId) ?: return
        val step = task.currentStep ?: return
        repository.completeStep(taskId, step.id)
    }

    /** 检测巡检人员是否偏离规划路径 */
    fun checkPathDeviation(currentX: Float, currentY: Float, plannedPath: List<NavPoint>) {
        val nextPoint = plannedPath.firstOrNull { !it.isDone } ?: return
        val dist = kotlin.math.sqrt(
            (currentX - nextPoint.x) * (currentX - nextPoint.x) +
            (currentY - nextPoint.y) * (currentY - nextPoint.y)
        )
        _uiState.value = _uiState.value.copy(pathDeviationAlert = dist > 5f)
    }

    /** 校验操作票：AI 识别结果与步骤目标设备匹配 */
    fun validateOperationStep(task: InspectionTask, aiResult: InferenceResult): TicketValidation? {
        val step = task.currentStep ?: return null
        val detected = aiResult.boxes.maxByOrNull { it.confidence }
        return TicketValidation(
            stepId = step.id,
            expectedDevice = step.targetDevice,
            detectedDevice = detected?.label ?: "未识别",
            isMatch = detected?.label?.contains(step.targetDevice.take(4)) == true,
            confidence = detected?.confidence ?: 0f
        )
    }

    /** 生成巡检报告摘要 */
    fun generateReport(taskId: String): String {
        val task = repository.getTask(taskId) ?: return "任务不存在"
        return buildString {
            appendLine("═══ 巡检报告 ═══")
            appendLine("任务: ${task.title}")
            appendLine("变电站: ${task.substationName}")
            appendLine("巡检员: ${task.assignee}")
            appendLine("完成步骤: ${task.completedSteps}/${task.totalSteps}")
            appendLine("状态: ${task.status.name}")
            appendLine()
            task.steps.forEachIndexed { i, step ->
                val status = if (step.isCompleted) "✅" else "❌"
                appendLine("$status 步骤${i + 1}: ${step.action}")
            }
        }
    }
}
