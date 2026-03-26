我来帮你规划一个 BLE + UWB 测试软件 App 的数据结构和功能设计。这是一个专业的射频测距/定位测试工具，我会从实际测试场景出发进行设计。

一、核心数据结构设计

1. BLE 模块数据

```json
// BLE 广播数据
{
  "device_info": {
    "mac_address": "AA:BB:CC:DD:EE:FF",
    "device_name": "UWB-Tag-01",
    "rssi": -65,
    "tx_power": -12,
    "timestamp": 1712345678
  },
  "advertising_data": {
    "uuid": "180A",
    "major": 1001,
    "minor": 2001,
    "manufacturer_data": "0x4C000215...",
    "service_data": {...},
    "raw_bytes": [0x02, 0x01, 0x06, ...]
  },
  "connection_params": {
    "connection_interval": 7.5,
    "slave_latency": 0,
    "supervision_timeout": 5000,
    "mtu": 247,
    "phy": "2M",
    "data_length": 251
  },
  "service_characteristics": [
    {
      "service_uuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
      "characteristics": [
        {
          "uuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
          "properties": ["write", "write_without_response"],
          "value": "..."
        }
      ]
    }
  ],
  "gatt_operations": {
    "read_count": 150,
    "write_count": 50,
    "notify_count": 1200,
    "error_count": 2,
    "latency_ms": 45
  }
}
```

2. UWB 模块数据

```json
// UWB 测距数据
{
  "session_config": {
    "session_id": 1,
    "device_role": "initiator",  // 或 "responder"
    "device_address": "0x1234",
    "destination_address": "0x5678",
    "ranging_interval_ms": 100,
    "channel": 9,
    "preamble_code_index": 12,
    "data_rate": "6.81Mbps",
    "prf": "64MHz",
    "preamble_duration": "64symbols"
  },
  "measurement_data": {
    "timestamp": 1712345678000,
    "sequence_number": 1024,
    "distance_m": 2.345,
    "aoa_azimuth_deg": 15.3,
    "aoa_elevation_deg": -2.1,
    "rssi_dbm": -78.5,
    "fom": 85,  // Figure of Merit (0-100)
    "nlos_indicator": false,
    "raw_tof_ticks": 123456789
  },
  "quality_metrics": {
    "std_deviation_m": 0.08,
    "success_rate_percent": 98.5,
    "invalid_measurement_rate": 1.2,
    "average_latency_ms": 12
  },
  "calibration_data": {
    "antenna_delay": 16600,
    "tx_power_dbm": -14,
    "rx_gain_db": 12
  }
}
```

3. 融合测试数据

```json
// BLE + UWB 联合测试记录
{
  "test_id": "TEST_20240326_001",
  "test_type": "distance_accuracy",
  "environment": "indoor_office",
  "ground_truth": {
    "reference_distance_m": 5.0,
    "positions": {"x": 0, "y": 0, "z": 0}
  },
  "ble_data": [...],  // BLE RSSI序列
  "uwb_data": [...],   // UWB测距序列
  "fusion_result": {
    "fused_distance_m": 4.98,
    "confidence": 0.92,
    "algorithm": "kalman_filter"
  },
  "error_analysis": {
    "ble_error_m": 1.2,
    "uwb_error_m": 0.05,
    "fused_error_m": 0.02
  }
}
```

二、功能模块设计

1. BLE 测试功能模块

功能模块	具体功能	测试目的	
扫描测试	扫描过滤、RSSI采集、广播解析	验证BLE信号覆盖和广播质量	
连接测试	连接建立时间、重连机制、并发连接	验证连接稳定性和性能	
吞吐量测试	读写速率、通知吞吐量、MTU测试	验证数据传输能力	
功耗测试	连接态功耗、广播态功耗、峰值电流	评估电池寿命	
兼容性测试	多厂商设备配对、协议版本兼容	确保互操作性	
压力测试	长时间连接稳定性、异常处理	发现潜在问题	

2. UWB 测试功能模块

功能模块	具体功能	测试目的	
基础测距	单点测距、连续测距、精度统计	验证基本测距功能	
AoA测试	角度测量精度、多径影响、校准验证	验证方向感知能力	
NLOS检测	非视距识别、误差补偿算法	提高复杂环境适应性	
多设备测试	TWR/DS-TWR测距、多锚点定位	验证网络扩展性	
性能边界	最大距离、刷新率、抗干扰	确定性能极限	
功耗分析	不同角色功耗、测距频率影响	优化电源管理	

3. 融合测试功能模块

功能模块	具体功能	测试目的	
数据同步	BLE时间戳与UWB时间戳对齐	确保数据融合准确性	
联合定位	BLE粗定位+UWB精定位融合	提高定位鲁棒性	
场景模拟	不同距离/角度/障碍物组合	全面评估系统性能	
算法验证	卡尔曼滤波、粒子滤波效果对比	优化融合算法	
故障注入	BLE断开、UWB丢包、信号干扰	验证容错机制	

三、App 界面与交互设计

1. 主界面布局

```
┌─────────────────────────────────────┐
│  [BLE状态]  [UWB状态]  [系统时间]    │
├─────────────────────────────────────┤
│  扫描列表  │  实时测距  │  测试报告  │
│  (设备发现) │  (距离/AoA) │  (统计分析) │
├─────────────────────────────────────┤
│  实时数据可视化区域 (图表/仪表盘)      │
├─────────────────────────────────────┤
│  [开始测试] [配置参数] [导出数据]    │
└─────────────────────────────────────┘
```

2. 关键测试流程

单设备测距测试流程：
1. 扫描发现BLE设备 → 连接并读取UWB服务
2. 配置UWB会话参数（角色、地址、测距间隔）
3. 启动连续测距，实时显示距离/AoA/RSSI
4. 记录数据并计算统计指标（均值、标准差、成功率）
5. 生成测试报告，支持CSV/JSON导出

多设备定位测试流程：
1. 配置多个锚点（Anchor）坐标
2. 扫描并连接所有待测标签（Tag）
3. 同步启动多设备测距
4. 实时计算并显示标签位置（三边定位）
5. 对比真实位置，计算定位误差

四、技术实现建议

1. 架构设计
- 分层架构：UI层 → 业务逻辑层 → 设备抽象层 → 原生驱动层
- BLE模块：使用系统API（iOS CoreBluetooth / Android BluetoothGatt）
- UWB模块：需专用芯片SDK（如Qorvo DWM3000、NXP SR150）
- 数据存储：SQLite本地存储 + 云端同步（可选）

2. 关键配置参数

参数类型	BLE配置	UWB配置	
扫描参数	扫描间隔100ms，窗口50ms	测距间隔100ms	
连接参数	间隔7.5ms，超时5s	通道9，前导64符号	
功耗模式	平衡模式	低功耗/高性能切换	
数据格式	通知方式，MTU 247	TWR模式，6.81Mbps	

3. 测试场景建议

必测场景：
- 视距（LOS）1-50米精度测试
- 非视距（NLOS）穿透测试（玻璃/木板/墙体）
- 多径干扰环境（金属反射）
- 多设备并发测距（5+设备）
- 动态场景（移动中测距稳定性）

评估指标：
- 测距精度：±10cm（90%置信区间）
- AoA精度：±3°（LOS条件）
- 测距成功率：>95%
- 系统延迟：<20ms

五、开发注意事项

1. 权限管理：BLE需定位权限，UWB需精确位置权限（iOS）
2. 后台限制：iOS/Android后台扫描/测距有严格限制
3. 硬件差异：不同UWB芯片参数和API差异大，需抽象层封装
4. 校准重要性：天线延迟校准直接影响测距精度，必须提供校准工具
5. 数据同步：BLE和UWB时钟不同源，需设计时间同步机制

这个规划覆盖了从底层数据到上层功能的完整设计，你可以根据具体芯片平台（如Apple U1、Qorvo、NXP）和操作系统进行细化实现。需要我针对某个具体模块展开详细设计吗？