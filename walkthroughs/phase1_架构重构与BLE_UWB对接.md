# Phase 1 — 架构重构 + BLE/UWB 真实对接

**构建结果：** ✅ `BUILD SUCCESSFUL`

## 背景

原有代码将所有状态散落在 `MainScreen.kt` 的 `remember` 中，BLE 扫描和 UWB 测距均为模拟数据生成循环，无法应对屏幕旋转状态丢失等问题。

## 变更文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `MainViewModel.kt` | 🆕 新建 | 状态全部提升至 ViewModel，暴露 `ConnectionState` / `BleScanState` / `UwbState` 三个 `StateFlow` |
| `BleScanner.kt` | ⬆ 升级 | 替换模拟循环为真实 `BluetoothLeScanner` API，累积去重设备列表，按 RSSI 排序 |
| `UwbRangingManager.kt` | ⬆ 升级 | 接入 Jetpack UWB API（`androidx.core.uwb`），不支持时自动降级 10Hz 模拟数据，`isSimulated=true` 标注 |
| `MainScreen.kt` | ⬆ 重构 | 所有状态改为 `collectAsState()`，移除模拟 `LaunchedEffect`，BLE 设备卡片 RSSI 颜色编码，UWB Tab 显示真实/模拟徽章 |
| `build.gradle.kts` | ⬆ 修改 | 新增 `lifecycle-viewmodel-ktx/runtime-ktx/viewmodel-compose:2.8.7` |

## 新增数据模型

```kotlin
data class BleDevice(val name: String, val address: String, val rssi: Int,
                     val isConnectable: Boolean, val lastSeen: Long)
data class UwbRangingData(val distanceMeters: Float, val azimuthDegrees: Float?,
                          val elevationDegrees: Float?, val isSimulated: Boolean)
data class ConnectionState(val pmsConnected: Boolean, val bleConnected: Boolean, val uwbConnected: Boolean)
data class BleScanState(val isScanning: Boolean, val devices: List<BleDevice>)
data class UwbState(val isRanging: Boolean, val isSupported: Boolean, val isSimulated: Boolean,
                    val distanceMeters: Float, val azimuthDegrees: Float)
```

## 验证

- **屏幕旋转**：扫描状态和设备列表不丢失（ViewModel 跨重建保持）
- **模拟器**：BLE 无结果但不崩溃；UWB 自动降级并显示「模拟模式」橙色徽章
- **真机（Android 12+）**：BLE 扫描到真实设备 MAC，UWB 支持机型显示「硬件 UWB」绿色徽章
