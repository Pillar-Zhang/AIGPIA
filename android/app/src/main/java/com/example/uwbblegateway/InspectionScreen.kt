package com.example.uwbblegateway

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun InspectionListScreen(
    inspectionViewModel: InspectionViewModel = viewModel()
) {
    val uiState by inspectionViewModel.uiState.collectAsState()
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    if (selectedTaskId != null) {
        InspectionDetailScreen(
            taskId = selectedTaskId!!,
            inspectionViewModel = inspectionViewModel,
            onBack = { selectedTaskId = null }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题栏
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("巡检任务列表", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("共 ${uiState.tasks.size} 条", fontSize = 12.sp, color = Color.Gray)
                }
            }

            // 进行中任务提示
            uiState.activeTask?.let { active ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { selectedTaskId = active.id },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前进行中", fontSize = 11.sp, color = Color(0xFF81C784))
                            Text(active.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "步骤 ${active.completedSteps + 1}/${active.totalSteps}：${active.currentStep?.action ?: ""}",
                                fontSize = 12.sp, color = Color(0xFF81C784),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // 任务列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.tasks) { task ->
                    TaskCard(task = task, onClick = { selectedTaskId = task.id })
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: InspectionTask, onClick: () -> Unit) {
    val statusColor = when (task.status) {
        TaskStatus.PENDING -> Color.Gray
        TaskStatus.IN_PROGRESS -> Color(0xFF4CAF50)
        TaskStatus.PAUSED -> Color(0xFFFF9800)
        TaskStatus.COMPLETED -> Color(0xFF2196F3)
        TaskStatus.ABORTED -> Color(0xFFF44336)
    }
    val typeLabel = when (task.taskType) {
        TaskType.ROUTINE -> "常规巡检"
        TaskType.OPERATION_TICKET -> "操作票"
    }
    val priorityColor = when (task.priority) {
        1 -> Color(0xFFF44336)
        2 -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 优先级指示条
                Box(modifier = Modifier.size(4.dp, 40.dp).clip(RoundedCornerShape(2.dp)).background(priorityColor))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(task.substationName, fontSize = 12.sp, color = Color.Gray)
                }
                // 类型徽章
                Surface(color = if (task.taskType == TaskType.OPERATION_TICKET) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)) {
                    Text(typeLabel, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (task.taskType == TaskType.OPERATION_TICKET) Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { task.progressPercent },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = statusColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(4.dp))
                Text(task.status.name, fontSize = 11.sp, color = statusColor)
                Spacer(modifier = Modifier.weight(1f))
                Text("${task.completedSteps}/${task.totalSteps} 步骤",
                    fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text("负责人: ${task.assignee}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ─── 任务详情页 ───────────────────────────────────────────────────────────────

@Composable
fun InspectionDetailScreen(
    taskId: String,
    inspectionViewModel: InspectionViewModel,
    onBack: () -> Unit
) {
    val uiState by inspectionViewModel.uiState.collectAsState()
    val task = uiState.tasks.find { it.id == taskId }

    if (task == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("任务不存在")
        }
        return
    }

    var showReport by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.substationName, fontSize = 12.sp, color = Color.Gray)
                }
                if (task.taskType == TaskType.OPERATION_TICKET) {
                    Surface(color = Color(0xFF7B1FA2), shape = RoundedCornerShape(4.dp)) {
                        Text("操作票", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // 进度总览
            item {
                ProgressOverviewCard(task = task)
            }

            // 路径偏离警告
            if (uiState.pathDeviationAlert) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF44336))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("⚠ 您已偏离巡检规划路径，请返回正确路线", color = Color(0xFFC62828), fontSize = 13.sp)
                        }
                    }
                }
            }

            // 步骤列表
            items(task.steps) { step ->
                StepCard(
                    step = step,
                    isCurrent = step.id == task.currentStep?.id
                )
            }
        }

        // 底部操作栏
        Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (task.status) {
                    TaskStatus.PENDING -> {
                        Button(onClick = { inspectionViewModel.startTask(taskId) },
                            modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始巡检")
                        }
                    }
                    TaskStatus.IN_PROGRESS -> {
                        OutlinedButton(onClick = { inspectionViewModel.pauseTask(taskId) },
                            modifier = Modifier.weight(0.4f)) {
                            Text("暂停")
                        }
                        Button(
                            onClick = { inspectionViewModel.completeCurrentStep(taskId) },
                            enabled = task.currentStep != null,
                            modifier = Modifier.weight(0.6f)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("完成此步骤")
                        }
                    }
                    TaskStatus.PAUSED -> {
                        OutlinedButton(onClick = { inspectionViewModel.abortTask(taskId) },
                            modifier = Modifier.weight(0.4f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))) {
                            Text("终止")
                        }
                        Button(onClick = { inspectionViewModel.startTask(taskId) },
                            modifier = Modifier.weight(0.6f)) {
                            Text("继续巡检")
                        }
                    }
                    TaskStatus.COMPLETED -> {
                        Button(
                            onClick = {
                                reportText = inspectionViewModel.generateReport(taskId)
                                showReport = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("查看 & 导出报告")
                        }
                    }
                    else -> { /* ABORTED - nothing */ }
                }
            }
        }
    }

    // 报告弹窗
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("巡检报告") },
            text = { Text(reportText, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showReport = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun ProgressOverviewCard(task: InspectionTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("巡检进度", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${(task.progressPercent * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = if (task.status == TaskStatus.COMPLETED) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progressPercent },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when (task.status) {
                    TaskStatus.COMPLETED -> Color(0xFF4CAF50)
                    TaskStatus.PAUSED -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已完成 ${task.completedSteps} 步", fontSize = 12.sp, color = Color.Gray)
                Text("剩余 ${task.totalSteps - task.completedSteps} 步", fontSize = 12.sp, color = Color.Gray)
            }
            task.currentStep?.let { step ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前：步骤 ${step.stepNo}", fontSize = 11.sp, color = Color.Gray)
                Text(step.action, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (step.safetyNote.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("⚠ ${step.safetyNote}", fontSize = 11.sp, color = Color(0xFFF57C00))
                }
            }
        }
    }
}

@Composable
fun StepCard(step: InspectionStep, isCurrent: Boolean) {
    val borderColor = when {
        step.isCompleted -> Color(0xFF4CAF50)
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                step.isCompleted -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isCurrent || step.isCompleted)
            CardDefaults.outlinedCardBorder().copy(width = 1.dp)
        else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // 步骤序号圆圈
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(when {
                        step.isCompleted -> Color(0xFF4CAF50)
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> Color.Gray
                    }),
                contentAlignment = Alignment.Center
            ) {
                if (step.isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("${step.stepNo}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(step.action, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                Text("目标设备: ${step.targetDevice}", fontSize = 11.sp, color = Color.Gray)
                if (step.safetyNote.isNotEmpty()) {
                    Text("⚠ ${step.safetyNote}", fontSize = 11.sp, color = Color(0xFFF57C00))
                }
                if (step.requirePhoto) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Text("需要拍照确认", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
