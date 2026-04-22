package com.example.uwbblegateway

// ─── 枚举 ──────────────────────────────────────────────────────────────────────

enum class TaskType {
    ROUTINE,           // 常规巡检
    OPERATION_TICKET   // 倒闸操作票
}

enum class TaskStatus {
    PENDING,      // 待开始
    IN_PROGRESS,  // 进行中
    PAUSED,       // 已暂停
    COMPLETED,    // 已完成
    ABORTED       // 已终止
}

// ─── 数据模型 ──────────────────────────────────────────────────────────────────

data class InspectionStep(
    val id: String,
    val stepNo: Int,
    val action: String,           // 操作描述（"检查变压器油位"）
    val targetDevice: String,     // 目标设备名称（用于 AI 匹配校验）
    val safetyNote: String = "",  // 安全注意事项
    val requirePhoto: Boolean = true,
    val photoPath: String? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

data class InspectionTask(
    val id: String,
    val title: String,
    val substationName: String,
    val taskType: TaskType,
    val priority: Int = 2,        // 1=紧急 2=普通 3=低优先
    val steps: List<InspectionStep>,
    val status: TaskStatus = TaskStatus.PENDING,
    val assignee: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val reportPath: String? = null
) {
    val completedSteps: Int get() = steps.count { it.isCompleted }
    val totalSteps: Int get() = steps.size
    val progressPercent: Float get() = if (totalSteps == 0) 0f else completedSteps / totalSteps.toFloat()
    val currentStep: InspectionStep? get() = steps.firstOrNull { !it.isCompleted }
}

// ─── 操作票（倒闸操作专用） ────────────────────────────────────────────────────

data class OperationTicket(
    val ticketNo: String,
    val taskId: String,
    val operationType: String,   // "送电" / "停电" / "倒闸"
    val validationResults: List<TicketValidation> = emptyList()
)

data class TicketValidation(
    val stepId: String,
    val expectedDevice: String,
    val detectedDevice: String,   // AI 识别结果
    val isMatch: Boolean,
    val confidence: Float
)
