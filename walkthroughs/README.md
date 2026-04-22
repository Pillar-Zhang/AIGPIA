# UwbBleGatewayApp 开发 Walkthrough 索引

> 电网数字化员工智能巡检手机端 APP 开发记录

## 项目概述

系统采用「AR 眼镜端轻量 APP + 手机端边缘计算主 APP」分离架构。本仓库为**手机端主 APP**，承担核心算力与业务枢纽功能。

| 技术 | 方案 |
|------|------|
| UI 框架 | Jetpack Compose |
| 状态管理 | ViewModel + StateFlow |
| BLE 通信 | Android BluetoothManager API |
| UWB 通信 | Jetpack UWB API（androidx.core.uwb） |
| AI 推理 | TFLite（hooks 已预留，可接入真实模型） |
| 地图渲染 | Canvas 2D |
| 网络通信 | Retrofit + OkHttp WebSocket |

---

## 开发阶段

| 阶段 | 文档 | 构建 | 主要内容 |
|------|------|------|---------|
| Phase 1 | [phase1_架构重构与BLE_UWB对接.md](./phase1_架构重构与BLE_UWB对接.md) | ✅ | ViewModel 重构、真实 BLE 扫描、Jetpack UWB 对接 |
| Phase 2 | [phase2_融合定位引擎与电子地图.md](./phase2_融合定位引擎与电子地图.md) | ✅ | WLS+KF 融合定位、Canvas 2D 地图、路径规划 |
| Phase 3 | [phase3_AI推理与巡检业务流程.md](./phase3_AI推理与巡检业务流程.md) | ✅ | YOLOv8/OCR/表计推理、任务步骤引擎、操作票校验 |

---

## 当前 App Tab 结构（8 Tab）

```
1. 连接管理     → PMS3.0 / BLE模组 / UWB模组 / AI眼镜
2. BLE 扫描    → 真实 BLE 设备扫描，RSSI 颜色编码
3. UWB 测距    → 真实/模拟 UWB 距离+方位角，自动降级
4. AI 眼镜     → 眼镜视频流（MJPEG）+ IMU 传感器数据
5. 数据统计    → 系统数据汇总 + 上传至后台
6. 定位地图    → Canvas 2D 变电站地图 + 融合定位 + 路径导航
7. AI 识别     → 本地 YOLOv8 设备识别 + OCR 铭牌 + 表计读数
8. 巡检任务    → 任务列表 + 步骤推进 + 操作票校验 + 报告
```

---

## 待实现（Phase 4）

- [ ] 国密算法（SM2/SM3/SM4）安全加密
- [ ] 断网加密缓存（≥50GB）+ 按优先级续传
- [ ] 人脸识别 + 三级角色权限管控
- [ ] 巡检报告 PDF 生成 + 一键上传
- [ ] APP OTA 自更新 + 日志管理（Timber）
