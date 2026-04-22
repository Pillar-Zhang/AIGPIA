# Phase 2 — UWB+BLE 融合定位引擎 + 变电站电子地图

**构建结果：** ✅ `BUILD SUCCESSFUL`（1 个弃用警告，无错误）

## 背景

Phase 1 完成后，手机端有了真实 BLE 扫描和 UWB 测距能力，但缺乏坐标解算和地图可视化。Phase 2 实现手机端作为"边缘算力节点"完成融合定位，并通过 Canvas 2D 地图可视化巡检现场。

## 变更文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `PositioningEngine.kt` | 🆕 新建 | WLS 三边测量 + Kalman 滤波 + BLE RSSI 降级 + 10Hz 模拟椭圆轨迹 |
| `PathPlanner.kt` | 🆕 新建 | 贪心最近邻路径规划（NNH）+ 自动到达检测 + 方位角计算 |
| `MapViewModel.kt` | 🆕 新建 | 订阅定位流、管理巡检点状态、历史轨迹（最多 200 点）、导航推送 |
| `MapScreen.kt` | 🆕 新建 | Canvas 2D 地图：双指缩放/单指平移、UWB 锚点、路径着色、玩家点+方向箭头、巡检点列表 |
| `BleManager.kt` | ⬆ 修改 | 新增 `sendNavData(position, nextPoint)` — 通过 GATT 或 WebSocket 下发 JSON 导航数据 |
| `MainScreen.kt` | ⬆ 修改 | 新增「📍 定位地图」第 6 个 Tab |

## 算法设计

### WLS 三边测量
- 以第一个锚点为参考，构建线性方程组
- 权重 `w = 1 / (d² + ε)`（近距离优先）
- ≥ 3 个锚点时触发解算

### Kalman Filter（简化匀速模型）
```
预测：x' = x + vx·dt，Pxx' = Pxx + σ_process
更新：Kx = Pxx' / (Pxx' + σ_measure)
      x  = x' + Kx·(meas - x')
```

### 模拟轨迹
```kotlin
x = cx + rx·cos(t)   // 椭圆中心(5,5), 半轴(3.5, 3.0)
y = cy + ry·sin(t)   // 10Hz 更新
```

## 导航数据下发 JSON 格式

```json
{
  "type": "nav",
  "x": 3.25,
  "y": 7.18,
  "conf": 0.90,
  "next": { "id": "P2", "label": "断路器 CB1", "x": 8.0, "y": 2.0 }
}
```

## 验证

1. 进入「定位地图」Tab → 看到变电站深色背景网格 + 4 个蓝色菱形锚点
2. 点「开始定位」→ 红色圆点沿椭圆轨迹移动（「模拟定位」橙色徽章）
3. 手指缩放/平移地图 → 正常响应
4. 底部巡检点列表 → 「→ 前往」高亮当前目标点
5. 旋转屏幕 → 地图状态保持不丢失
