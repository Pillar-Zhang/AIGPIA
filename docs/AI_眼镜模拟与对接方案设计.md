# AI 智能眼镜模拟与对接方案设计 (Scheme A)

## 一、真实的硬件通讯架构 (BLE + Wi-Fi 双链路)

在真实的商用 AI 眼镜（例如 Ray-Ban Meta、魅族 MYVU）中，考虑到功耗与带宽的极致平衡，通讯架构必定是**双链路解耦**的：

1. **低功耗指令通道 (BLE GATT)**：
   - 维持长连接，负责眼镜与手机的心跳保活。
   - 传输低频、小包数据：电量、温度、传感器参数（IMU）、佩戴检测、触摸板交互指令。
   - 负责握手建立视频图传通道（例如通过 BLE 把手机热点的 SSID 和密码发给眼镜，眼镜连入局域网）。
2. **高带宽数据通道 (Wi-Fi Direct / 局域网 Socket)**：
   - 由于 1080p@30fps 的视频码率（即使经过 H.264/H.265 压缩）也高达数 Mbps，完全超出了 BLE 的物理极限。
   - 视频流和高频无损音频（拾音）必须走 Wi-Fi 网络（如 RTSP 协议或实时 WebRTC）。

---

## 二、开发期模拟器实现架构 (Mock 方案)

由于 Android Studio 模拟器无法完美透传真实硬件蓝牙和 Wi-Fi Direct，我们在纯软件层（电脑 + 模拟器）采用**同构模拟方案**：

### 1. 模拟端 (后台运行在开发电脑上)
用一个 Node.js 或 Python 脚本在开发者电脑本地充当“虚拟眼镜”：
* **[虚拟 BLE 端] WebSocket 服务 (`ws://localhost:3002`)**：以 60Hz 频率不断向外发送模拟的头部姿态(IMU)数据，并处理手机下发的指令。
* **[虚拟 Wi-Fi 端] 图传流媒体服务 (`http://localhost:3003`)**：调用您的 MacBook 前置摄像头，将其画面以极简的 **MJPEG (Motion JPEG)** 格式转流。MJPEG 兼容性极高，甚至能在网页端直接播放。

### 2. Android App 端 (手机环境)
App 代码采用**防腐层模式 (Repository Pattern)** 隔离底层通讯：
* **注入真实或模拟蓝牙**：定义统一接口 `IGlassesBleController`。如果是真机编译，使用真实的 `BluetoothGatt` 连接；如果是模拟器运行，底层直接替换为连接 `ws://10.0.2.2:3002`（`10.0.2.2` 是模拟器访问电脑宿主机的专有 IP）。这对上层 UI 和业务逻辑是完全透明的。
* **视频流拉取**：图传渲染组件直接拉取 `http://10.0.2.2:3003/video_feed`，假装正在解析从眼镜 Wi-Fi 传过来的帧。

---

## 三、详细数据结构设计 (模拟 BLE 数据)

为了在后台（即 `ws://10.0.2.2:3002`）向 App 源源不断地汇报眼镜状态，我们可以设计如下的 JSON 数据载荷，这相当于真实场景下把这些字段拆分成多个 BLE Characteristic 的组合：

### 1. 眼镜遥测数据 (定期 20Hz~60Hz 频率发送给 App)

```json
{
  "packet_id": 100234,
  "timestamp_ms": 1711000000000,
  "system_status": {
    "battery_level": 84,              // 电量 0-100%
    "temperature_c": 35.2,            // 镜腿温度
    "is_wearing": true,               // 是否佩戴中 (光距传感器判断)
    "wifi_connected": true            // 图传通道是否已建立
  },
  "imu_data": {
    "acceleration": {"x": 0.01, "y": -9.81, "z": 0.05}, // 加速度计 (m/s^2)
    "gyroscope": {"x": 0.0, "y": 0.0, "z": 0.01},       // 陀螺仪 (rad/s)
    "euler_angles": {"pitch": 5.2, "yaw": 14.5, "roll": -1.2} // 头部姿态(偏航/俯仰/滚转)
  }
}
```

### 2. 眼镜交互事件 (事件驱动，用户触碰眼镜时推送给 App)

```json
{
  "event_type": "touch_action",
  "payload": {
    "action": "double_tap",  // swipe_forward, swipe_backward, long_press
    "zone": "right_temple"   // 触摸区域：右侧镜腿
  },
  "timestamp_ms": 1711000005000
}
```

### 3. App 下发指令 (App 向眼镜发送)

```json
{
  "command": "start_streaming",
  "params": {
    "resolution": "1080p",
    "fps": 30,
    "mic_enabled": true
  }
}
```

---

## 四、对接建议与下一步

如果您认可这套 **“方案 A”（双链路分离与同构 Mock）**，为了让您的客户端同事能够马上开始画界面并接收这些实时数据，我们可以按以下步骤实施：

1. **我为您写一个几十行的模拟脚本 (比如 `virtual-glasses.js`)**：直接整合进我们刚整理好的 `server/` 项目里。
2. 该脚本启动时会自动调取您电脑的摄像头将画面转码推流，并在对应端口起好 WebSocket。
3. 您的 App 就像调用普通网络接口一样去拉它们，一旦跑通，未来把底层替换成真正的蓝牙读写 API，上层业务一行代码都不用改！
