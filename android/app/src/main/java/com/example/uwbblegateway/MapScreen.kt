package com.example.uwbblegateway

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapScreen(
    bleManager: BleManager,
    mapViewModel: MapViewModel = viewModel()
) {
    val mapState by mapViewModel.mapState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部控制栏 ──────────────────────────────────────────
        MapControlBar(
            isPositioning = mapState.isPositioning,
            isSimulated = mapState.isSimulated,
            completedCount = mapState.completedCount,
            totalCount = mapState.inspectionPoints.size,
            onStart = { mapViewModel.startPositioning(simulated = true) },
            onStop = { mapViewModel.stopPositioning() },
            onReset = { mapViewModel.resetInspection() },
            onPushNav = { mapViewModel.pushNavToGlasses(bleManager) }
        )

        // ── Canvas 地图 ──────────────────────────────────────────
        SubstationMapCanvas(
            mapState = mapState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // ── 底部巡检点列表 ───────────────────────────────────────
        InspectionPointList(
            points = mapState.inspectionPoints,
            nextTarget = mapState.nextTarget
        )
    }
}

// ─── 控制栏 ────────────────────────────────────────────────────────────────────

@Composable
fun MapControlBar(
    isPositioning: Boolean,
    isSimulated: Boolean,
    completedCount: Int,
    totalCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onPushNav: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 进度徽章
            Surface(
                color = if (completedCount == totalCount) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "$completedCount/$totalCount 完成",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (isSimulated && isPositioning) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(color = Color(0xFFFF9800), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "模拟定位",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 推送导航
            IconButton(onClick = onPushNav, enabled = isPositioning) {
                Icon(Icons.Default.Send, contentDescription = "推送至眼镜", tint = MaterialTheme.colorScheme.primary)
            }
            // 重置
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "重置")
            }
            // 开始/停止
            Button(
                onClick = if (isPositioning) onStop else onStart,
                colors = if (isPositioning) ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                         else ButtonDefaults.buttonColors()
            ) {
                Text(if (isPositioning) "停止" else "开始定位")
            }
        }
    }
}

// ─── Canvas 地图 ──────────────────────────────────────────────────────────────

@Composable
fun SubstationMapCanvas(mapState: MapState, modifier: Modifier = Modifier) {
    // 缩放/平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 场景尺寸（米），用于坐标映射
    val sceneW = 12f; val sceneH = 12f

    Canvas(
        modifier = modifier
            .background(Color(0xFF1A1A2E))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height

        // 坐标转换函数：场景坐标(米) → Canvas 像素
        fun toScreen(x: Float, y: Float): Offset {
            val sx = (x / sceneW * canvasW) * scale + offsetX + canvasW * (1 - scale) / 2
            val sy = (y / sceneH * canvasH) * scale + offsetY + canvasH * (1 - scale) / 2
            return Offset(sx, sy)
        }

        // 1. 网格背景
        drawGrid(canvasW, canvasH, scale, offsetX, offsetY, sceneW, sceneH)

        // 2. 历史轨迹（半透明）
        if (mapState.historyTrail.size >= 2) {
            val trailPath = Path()
            val first = toScreen(mapState.historyTrail[0].x, mapState.historyTrail[0].y)
            trailPath.moveTo(first.x, first.y)
            mapState.historyTrail.drop(1).forEach { pos ->
                val pt = toScreen(pos.x, pos.y)
                trailPath.lineTo(pt.x, pt.y)
            }
            drawPath(trailPath, Color(0x80AAAAAA), style = Stroke(width = 2f * scale, cap = StrokeCap.Round))
        }

        // 3. 规划路径（蓝色虚线）
        if (mapState.plannedPath.size >= 2) {
            val pos = mapState.currentPosition
            var prevOffset = toScreen(pos.x, pos.y)
            mapState.plannedPath.forEach { point ->
                val nextOffset = toScreen(point.x, point.y)
                val color = if (point.isDone) Color(0xFF4CAF50) else Color(0x882196F3)
                drawLine(color, prevOffset, nextOffset, strokeWidth = 2.5f * scale)
                prevOffset = nextOffset
            }
        }

        // 4. UWB 锚点（蓝色菱形）
        mapState.anchors.forEach { anchor ->
            val center = toScreen(anchor.x, anchor.y)
            drawAnchor(center, scale)
        }

        // 5. 巡检点（圆形 + 标号）
        mapState.inspectionPoints.forEach { point ->
            val center = toScreen(point.x, point.y)
            val isNext = point.id == mapState.nextTarget?.id
            drawInspectionPoint(center, point.isDone, isNext, scale)
        }

        // 6. 目标连线（当前位置 → 下一巡检点）
        mapState.nextTarget?.let { target ->
            val from = toScreen(mapState.currentPosition.x, mapState.currentPosition.y)
            val to = toScreen(target.x, target.y)
            drawLine(Color(0xFFFFEB3B), from, to, strokeWidth = 1.5f * scale,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f * scale, 6f * scale)))
        }

        // 7. 当前位置（红色圆点 + 方向箭头）
        val playerPos = toScreen(mapState.currentPosition.x, mapState.currentPosition.y)
        drawPlayer(playerPos, mapState.navBearing, scale)
    }
}

private fun DrawScope.drawGrid(
    w: Float, h: Float, scale: Float, ox: Float, oy: Float, sceneW: Float, sceneH: Float
) {
    val gridSteps = 11 // 10mx10m 场景，每米一格
    val gridColor = Color(0xFF2A2A4A)
    for (i in 0..gridSteps) {
        val xRaw = i.toFloat() / sceneW * w * scale + ox + w * (1 - scale) / 2
        val yRaw = i.toFloat() / sceneH * h * scale + oy + h * (1 - scale) / 2
        drawLine(gridColor, Offset(xRaw, 0f), Offset(xRaw, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, yRaw), Offset(w, yRaw), strokeWidth = 1f)
    }
}

private fun DrawScope.drawAnchor(center: Offset, scale: Float) {
    val s = 10f * scale
    val path = Path().apply {
        moveTo(center.x, center.y - s)
        lineTo(center.x + s, center.y)
        lineTo(center.x, center.y + s)
        lineTo(center.x - s, center.y)
        close()
    }
    drawPath(path, Color(0xFF2196F3))
    drawPath(path, Color.White, style = Stroke(1.5f))
}

private fun DrawScope.drawInspectionPoint(center: Offset, isDone: Boolean, isNext: Boolean, scale: Float) {
    val r = if (isNext) 14f * scale else 10f * scale
    val fillColor = when {
        isDone -> Color(0xFF4CAF50)
        isNext -> Color(0xFFFFEB3B)
        else -> Color(0x80FFFFFF)
    }
    drawCircle(fillColor, r, center)
    drawCircle(Color.White, r, center, style = Stroke(2f * scale))
    if (isNext) {
        // 外圈脉冲环
        drawCircle(Color(0x55FFEB3B), r + 8f * scale, center)
    }
}

private fun DrawScope.drawPlayer(center: Offset, bearingDeg: Float, scale: Float) {
    // 外光晕
    drawCircle(Color(0x33FF5252), 28f * scale, center)
    // 主点
    drawCircle(Color(0xFFFF5252), 12f * scale, center)
    drawCircle(Color.White, 12f * scale, center, style = Stroke(2.5f * scale))

    // 方向箭头
    val rad = Math.toRadians(bearingDeg.toDouble())
    val arrowLen = 22f * scale
    val tipX = center.x + (sin(rad) * arrowLen).toFloat()
    val tipY = center.y - (cos(rad) * arrowLen).toFloat()
    drawLine(Color(0xFFFFEB3B), center, Offset(tipX, tipY), strokeWidth = 3f * scale, cap = StrokeCap.Round)
}

// ─── 巡检点列表 ───────────────────────────────────────────────────────────────

@Composable
fun InspectionPointList(points: List<NavPoint>, nextTarget: NavPoint?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(points) { point ->
                val isNext = point.id == nextTarget?.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = when {
                            point.isDone -> Icons.Default.CheckCircle
                            isNext -> Icons.Default.LocationOn
                            else -> Icons.Default.Place
                        },
                        contentDescription = null,
                        tint = when {
                            point.isDone -> Color(0xFF4CAF50)
                            isNext -> Color(0xFFFFEB3B)
                            else -> Color.Gray
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = point.label,
                        fontSize = 13.sp,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (point.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isNext) {
                        Text("→ 前往", fontSize = 11.sp, color = Color(0xFFFF9800))
                    }
                    if (point.isDone) {
                        Text("已完成", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
