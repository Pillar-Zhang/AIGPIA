# BLE + UWB 测距定位测试工具 - 后端接口对接文档

本接口文档提供了对由 `aieyecloud` 提供的 BLE + UWB 测试数据同步服务的 API 接入指南，匹配《我来帮你规划一个 BLE + UWB 测试软件 App 的数据结构和功能设计》中的规范结构。

> **基础URL (Base URL)**: `http://localhost:3001` 或您的远程服务器地址
> **要求 Header**: 所有 `POST` 接口必须在 Header 携带 `Content-Type: application/json`

---

## 0. 用户验证与登录接口

用于 App 初始化时进行账号验证和凭证获取。所有的账号信息均存储在服务端的 `需求文档/user.json` 文件中。

* **接口路径**：`/api/login`
* **请求方式**：`POST`

**请求示例 (Body)**:
```json
{
  "username": "louguofeng",
  "password": "123456"
}
```

**成功返回结果 (账号密码匹配)**:
```json
{
  "success": true,
  "code": 1,
  "message": "登录成功",
  "data": {
    "token": "mock_token_171123456789_user_001",
    "userInfo": {
      "id": "user_001",
      "username": "louguofeng",
      "name": "楼博士",
      "role": "admin"
    }
  },
  "timestamp": "2024-03-26T14:32:00.000Z"
}
```

**失败返回结果 (密码错误)**:
```json
{
  "success": false,
  "code": 401,
  "message": "密码错误"
}
```

**失败返回结果 (账户不存在)**:
```json
{
  "success": false,
  "code": 404,
  "message": "账户不存在"
}
```

---

## 1. BLE 模块测试数据上传接口

用于上传 App 扫描以及性能压力测试产生的 BLE 相关测试日志数据。

* **接口路径**：`/api/test-data/ble`
* **请求方式**：`POST`
* **业务描述**：当测试一轮BLE并发、吞吐或连接后，整体上传相关广播及特征信息。

**请求示例 (Body)**:
```json
{
  "device_info": {
    "mac_address": "AA:BB:CC:DD:EE:FF",
    "device_name": "UWB-Tag-01",
    "rssi": -65,
    "tx_power": -12,
    "timestamp": 1712345678
  },
  "connection_params": {
    "connection_interval": 7.5,
    "supervision_timeout": 5000,
    "mtu": 247
  },
  "gatt_operations": {
    "read_count": 150,
    "error_count": 2,
    "latency_ms": 45
  }
}
```

**成功返回结果**:
```json
{
  "success": true,
  "code": 1,
  "message": "BLE测试数据上传成功",
  "record_id": "ble_record_171123456789"
}
```

---

## 2. UWB 模块测试数据上传接口

用于上传 UWB 会话中的参数配置、测距指标、角度（AoA）以及天线校准数据等。

* **接口路径**：`/api/test-data/uwb`
* **请求方式**：`POST`
* **业务描述**：提交完整的设备会话指标与精度统计指标。

**请求示例 (Body)**:
```json
{
  "session_config": {
    "session_id": 1,
    "device_role": "initiator",
    "device_address": "0x1234"
  },
  "measurement_data": {
    "timestamp": 1712345678000,
    "distance_m": 2.345,
    "aoa_azimuth_deg": 15.3,
    "nlos_indicator": false
  },
  "quality_metrics": {
    "std_deviation_m": 0.08,
    "success_rate_percent": 98.5
  }
}
```

**成功返回结果**:
```json
{
  "success": true,
  "code": 1,
  "message": "UWB测试数据上传成功",
  "record_id": "uwb_record_171123456789"
}
```

---

## 3. BLE + UWB 联合融合测试记录上传接口

用于测试完成后，打包完整环境、误差分析、过滤算法后的融合分析。

* **接口路径**：`/api/test-data/fusion`
* **请求方式**：`POST`
* **业务描述**：上传全量融合计算数据，要求最好携带 `test_id` 以便将来溯源；如未携带，服务端会自动指派。

**请求示例 (Body)**:
```json
{
  "test_id": "TEST_20240326_001",
  "test_type": "distance_accuracy",
  "environment": "indoor_office",
  "ground_truth": {
    "reference_distance_m": 5.0
  },
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

**成功返回结果**:
```json
{
  "success": true,
  "code": 1,
  "message": "融合测试记录上传成功",
  "test_id": "TEST_20240326_001"
}
```

---

## 4. 获取历史融合测试记录列表

用于在 App 中的“测试报告”大厅拉取过去记录的聚合信息。

* **接口路径**：`/api/test-data/fusion`
* **请求方式**：`GET`

**成功返回结果**:
```json
{
  "success": true,
  "code": 1,
  "total": 5,
  "data": [
    {
      "test_id": "TEST_20240326_001",
      "test_type": "distance_accuracy",
      "environment": "indoor_office",
      "fused_distance_m": 4.98,
      "received_at": "2024-03-26T14:32:00.000Z"
    }
  ]
}
```

---

## 5. 获取某次联合测试详细记录

进入某条测试历史后获取底层详细全量 JSON 报告。

* **接口路径**：`/api/test-data/fusion/{test_id}`
* **请求方式**：`GET`

**成功返回结果**:
```json
{
  "success": true,
  "code": 1,
  "data": {
    "test_id": "TEST_20240326_001",
    "test_type": "distance_accuracy",
    "environment": "indoor_office",
    ... (全量的完整数据)
  }
}
```

*(当查询不到该 `test_id` 对应的记录时，将返回 `code: 404` 且 `success: false`)*
