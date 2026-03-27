package com.example.uwbblegateway

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
fun AiScreen(
    bleManager: BleManager,
    aiViewModel: AiViewModel = viewModel()
) {
    val aiState by aiViewModel.aiState.collectAsState()
    val result = aiState.latestResult

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部控制栏
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 设备识别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                if (result?.isSimulated == true) {
                    Surface(color = Color(0xFFFF9800), shape = RoundedCornerShape(4.dp)) {
                        Text("模拟推理", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                } else if (result != null) {
                    Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)) {
                        Text("真实模型", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 推理触发按钮 ──────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { aiViewModel.triggerSimulatedAnalysis() },
                        enabled = !aiState.isAnalyzing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (aiState.isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (aiState.isAnalyzing) "推理中..." else "触发 AI 推理")
                    }
                    OutlinedButton(
                        onClick = { aiViewModel.pushResultToGlasses(bleManager) },
                        enabled = result != null
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("推送眼镜")
                    }
                }
            }

            // ── 推理结果统计 ──────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AiStatBox("推理次数", "${aiState.frameCount}")
                    AiStatBox("延迟", if (result != null) "${result.inferenceMs}ms" else "--")
                    AiStatBox("识别目标", if (result != null) "${result.boxes.size}" else "--")
                }
            }

            // ── 检测框列表 ────────────────────────────────────────────────
            if (result != null && result.boxes.isNotEmpty()) {
                item {
                    Text("目标检测结果", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(result.boxes) { box ->
                    DetectionBoxCard(box = box)
                }
            }

            // ── OCR 铭牌 ─────────────────────────────────────────────────
            result?.ocr?.let { ocr ->
                item {
                    Text("OCR 铭牌识别", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("识别内容", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                ConfidencePill(ocr.confidence)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(ocr.text, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }

            // ── 表计读数 ──────────────────────────────────────────────────
            result?.meter?.let { meter ->
                item {
                    Text("表计读数识别", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${meter.meterType} 仪表", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    String.format(Locale.US, "%.1f %s", meter.value, meter.unit),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(32.dp),
                                tint = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            // 空状态
            if (result == null && !aiState.isAnalyzing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.size(64.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击「触发 AI 推理」开始识别", color = Color.Gray)
                            Text("无模型文件时自动使用模拟推理", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionBoxCard(box: DetectionBox) {
    val confColor = when {
        box.confidence >= 0.9f -> Color(0xFF4CAF50)
        box.confidence >= 0.75f -> Color(0xFF8BC34A)
        else -> Color(0xFFFF9800)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(4.dp, 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(confColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(box.label, fontWeight = FontWeight.Bold)
                Text(
                    "位置: [%.2f, %.2f, %.2f, %.2f]".format(box.bbox.left, box.bbox.top, box.bbox.right, box.bbox.bottom),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            ConfidencePill(box.confidence)
        }
    }
}

@Composable
fun ConfidencePill(confidence: Float) {
    val color = when {
        confidence >= 0.9f -> Color(0xFF4CAF50)
        confidence >= 0.75f -> Color(0xFF8BC34A)
        else -> Color(0xFFFF9800)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
        Text(
            "${(confidence * 100).toInt()}%",
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun AiStatBox(label: String, value: String) {
    Card {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
