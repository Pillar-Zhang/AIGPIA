# UwbBleGatewayApp (全栈射频定位项目)

此项目是一个集成了 Android 应用客户端、Node.js 模拟数据云端，以及预留前端管理平台的全栈 Monorepo 仓库。

## 目录结构
* `/android`: 原生 Android 客户端源码 (包含 UWB/BLE 测试采集App)
* `/server`: Node.js 基于 Express 的后台云端服务 (接受设备数据上传及提供下发)
* `/front`: [预留开发] Web或大屏数据展示大屏前端项目
* `/docs`: 需求设计、REST API 对接规范文档

## 启动指南

### 1. 启动 Backend (Node.js)
```bash
cd server
npm install
npm run dev
```

### 2. 启动 Android App
使用 Android Studio 直接打开 `android` 目录，或者在命令行中进入 `android` 目录并运行：
```bash
cd android
./gradlew installDebug
```

# AIGPIA