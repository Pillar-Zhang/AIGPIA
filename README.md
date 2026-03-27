# UwbBleGatewayApp - 电网数字化员工智能巡检系统

本项目是一个集成了 **Android 边缘计算主 APP**、**Node.js 模拟数据云端** 以及 **AR 眼镜虚拟化服务** 的全栈技术方案。

## 1. 系统架构
系统采用 **“AR眼镜端轻量APP + 手机端边缘计算主APP”** 分离架构：
- **手机端 (边缘计算网关)**：承担核心算力。负责 UWB+BLE 融合定位解算 (WLS+Kalman)、本地 AI 推理 (YOLOv8/OCR/表计)、以及与电网 PMS3.0/OMS 系统对接。
- **眼镜端 (作业终端)**：聚焦现场交互。负责 4K 图像采集、AR 信息叠加显示、语音/手势交互。
- **云端 (模拟服务)**：提供 PMS3.0 接口模拟、数据持久化及 AI 眼镜硬件虚拟化。

## 2. 目录结构
- `/android`: 原生 Android 客户端 (Jetpack Compose + MVVM)。支持 8 大功能 Tab。
- `/server`: Node.js 后台服务。包含主 API 服务及虚拟眼镜驱动。
- `/walkthroughs`: **[重要]** 包含 Phase 1-3 的详细架构重构与开发记录。 
- `/docs`: 整体需求文档、接口规范及用户数据。

## 3. 快速启动

### 3.1 启动服务端 (Server & Mock)
进入 `server` 目录并安装依赖：
```bash
cd server
npm install
```

**启动主后端 API 服务 (Port: 3001):**
```bash
node server.js
```

**启动虚拟 AI 眼镜服务 (Port: 3002, 3003):**
```bash
node virtual_glasses.js
```
> [!IMPORTANT]
> **必须操作**：启动 `virtual_glasses.js` 后，请务必在 Mac 浏览器中打开 [http://localhost:3003](http://localhost:3003) 并允许摄像头权限。这是 App 端获取实时模拟视频流的唯一方式。

### 3.2 启动 Android App
使用 Android Studio 打开 `android` 目录。建议使用真机测试以获得真实 BLE/UWB 体验；模拟器会自动降级到模拟数据模式。

## 4. 核心功能模块 (手机端 8 Tab)
1. **连接管理**: PMS/BLE/UWB/眼镜连接状态监控。
2. **BLE 扫描**: 实时扫描四周蓝牙设备及信号强度。
3. **UWB 测距**: 10Hz 实时获取硬件层距离与方位角。
4. **AI 眼镜**: 接收眼镜端实时 MJPEG 视频流与 IMU 姿态。
5. **数据统计**: 汇总巡检测试数据一键上传。
6. **定位地图**: 变电站 2D 电子地图，基于 UWB+BLE 融合定位。
7. **AI 识别**: 本地 YOLOv8 设备识别、OCR 铭牌提取及表计读数。
8. **巡检任务**: PMS3.0 任务流管理、操作票 AI 校验、在线报告生成。

## 5. 开发文档
详细的各阶段开发记录请参考 [walkthroughs/README.md](./walkthroughs/README.md)。
- [Phase 1: 架构重构与硬件对接](./walkthroughs/phase1_架构重构与BLE_UWB对接.md)
- [Phase 2: 融合定位引擎与电子地图](./walkthroughs/phase2_融合定位引擎与电子地图.md)
- [Phase 3: AI 推理与巡检业务流程](./walkthroughs/phase3_AI推理与巡检业务流程.md)

---
# AIGPIA