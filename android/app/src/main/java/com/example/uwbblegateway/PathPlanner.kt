package com.example.uwbblegateway

import kotlin.math.sqrt

/**
 * Dijkstra 路径规划器
 * 输入: 当前位置 + 未完成巡检点列表
 * 输出: 最短访问顺序（贪心最近邻，适合巡检点数量 < 50 的场景）
 */
object PathPlanner {

    /**
     * 计算从 [startX, startY] 出发，访问所有未完成 [NavPoint] 的最优顺序
     * 使用贪心最近邻算法（NNH），计算量小，适合实时更新
     */
    fun planPath(startX: Float, startY: Float, points: List<NavPoint>): List<NavPoint> {
        val remaining = points.filter { !it.isDone }.toMutableList()
        val result = mutableListOf<NavPoint>()
        var curX = startX
        var curY = startY

        while (remaining.isNotEmpty()) {
            val nearest = remaining.minByOrNull { dist(curX, curY, it.x, it.y) } ?: break
            result.add(nearest)
            remaining.remove(nearest)
            curX = nearest.x
            curY = nearest.y
        }
        return result
    }

    /**
     * 根据当前位置找到路径上的下一个目标点
     * 到达阈值：距目标点 < [arrivalThreshold] 米时视为到达
     */
    fun nextTarget(
        currentX: Float,
        currentY: Float,
        path: List<NavPoint>,
        arrivalThreshold: Float = 1.0f
    ): NavPoint? {
        for (point in path) {
            if (point.isDone) continue
            if (dist(currentX, currentY, point.x, point.y) < arrivalThreshold) {
                point.isDone = true  // 自动打卡
                continue
            }
            return point
        }
        return null // 全部完成
    }

    /** 计算导航方位角（相对于正北方向，顺时针，度） */
    fun bearingDegrees(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
        val dx = toX - fromX
        val dy = toY - fromY
        val angle = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
        return (angle + 360f) % 360f
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}
