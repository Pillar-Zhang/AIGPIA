const express = require('express');
const fs = require('fs');
const path = require('path');
const app = express();
const PORT = 3001;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Enable CORS
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  next();
});
// Mock API endpoints
app.get('/', (req, res) => {
  res.send('你已经成功连接到 ai eye cloud');
});
app.get('/api/status', (req, res) => {
  res.json({
    status: 'online',
    timestamp: new Date().toISOString(),
    version: 'v0.1.0'
  });
});

// 新增：登录 (Login) 接口
app.post('/api/login', (req, res) => {
  console.log(`[API CALL ${new Date().toISOString()}] /api/login POST received payload:`, req.body);
  const { username, password } = req.body;
  
  if (!username || !password) {
    return res.status(400).json({ success: false, code: 0, message: "账号或密码不能为空" });
  }

  // 从独立文件中读取用户持久层数据
  let mockUsers = [];
  try {
    const userFilePath = path.join(__dirname, '../docs', 'user.json');
    if (fs.existsSync(userFilePath)) {
      mockUsers = JSON.parse(fs.readFileSync(userFilePath, 'utf8'));
    }
  } catch (error) {
    console.error("读取 user.json 失败:", error);
  }

  const user = mockUsers.find(u => u.username === username);
  
  if (!user) {
    return res.status(404).json({
      success: false,
      code: 404,
      message: "账户不存在"
    });
  }

  if (user.password !== password) {
    return res.status(401).json({
      success: false,
      code: 401,
      message: "密码错误"
    });
  }
  
  res.json({
    success: true,
    code: 1,
    message: "登录成功",
    data: {
      token: `mock_token_${Date.now()}_${user.id}`,
      userInfo: {
        id: user.id,
        username: user.username,
        name: user.name,
        role: user.role
      }
    },
    timestamp: new Date().toISOString()
  });
});

// 新增：PMS3.0 系统连接测试接口
app.get('/api/pms3/connect', (req, res) => {
  console.log(`[API CALL] /api/pms3/connect was called at ${new Date().toISOString()}`);
  res.json({
    success: true,
    code: 1,
    system: "PMS3.0",
    message: "成功连接到 PMS3.0 系统",
    timestamp: new Date().toISOString()
  });
});

// 新增：BLE 模组连接测试接口
app.get('/api/ble/connect', (req, res) => {
  console.log(`[API CALL] /api/ble/connect was called at ${new Date().toISOString()}`);
  res.json({
    success: true,
    code: 1,
    module: "BLE",
    message: "BLE 模组已成功连接",
    timestamp: new Date().toISOString()
  });
});

// 新增：UWB 模组连接测试接口
app.get('/api/uwb/connect', (req, res) => {
  console.log(`[API CALL] /api/uwb/connect was called at ${new Date().toISOString()}`);
  res.json({
    success: true,
    code: 1,
    module: "UWB",
    message: "UWB 模组已成功连接",
    timestamp: new Date().toISOString()
  });
});

// 新增：AI 智能眼镜连接测试接口
app.get('/api/aiglasses/connect', (req, res) => {
  console.log(`[API CALL] /api/aiglasses/connect was called at ${new Date().toISOString()}`);
  res.json({
    success: true,
    code: 1,
    device: "AIGlasses",
    message: "AI 智能眼镜已成功连接",
    timestamp: new Date().toISOString()
  });
});

// 模拟服务端保存的基准电气参数数据（变电站设备）
const serverEquipmentData = {
  "SUB-TR-01": {
    name: "1号主变压器",
    parameters: {
      voltage_kv: 110.5,
      current_a: 450.2,
      temperature_c: 65.3,
      active_power_kw: 8500
    }
  }
};

// 新增：通过 PMS3.0 系统读取设备电气参数接口
app.get('/api/pms3/equipment-data', (req, res) => {
  const equipmentId = req.query.equipment_id || "SUB-TR-01";
  console.log(`[API CALL] /api/pms3/equipment-data GET for equipment_id: ${equipmentId}`);
  const data = serverEquipmentData[equipmentId];
  
  if (data) {
    res.json({
      success: true,
      code: 1,
      equipment_id: equipmentId,
      name: data.name,
      parameters: data.parameters,
      timestamp: new Date().toISOString()
    });
  } else {
    res.status(404).json({ success: false, message: "设备不存在" });
  }
});

// 新增：上传现场设备数据并与服务端作对比接口
app.post('/api/pms3/compare-data', (req, res) => {
  console.log(`[API CALL] /api/pms3/compare-data POST received payload:`, req.body);
  const { equipment_id, field_parameters } = req.body;
  
  if (!equipment_id || !field_parameters) {
    return res.status(400).json({ success: false, message: "缺少设备ID或现场参数" });
  }

  const serverData = serverEquipmentData[equipment_id];
  if (!serverData) {
    return res.status(404).json({ success: false, message: "服务端未找到该设备的基准数据" });
  }

  // 计算告警和偏差值
  const deviations = {};
  let isMatch = true;
  let status = "NORMAL";

  for (const key in field_parameters) {
    const fieldVal = field_parameters[key];
    const serverVal = serverData.parameters[key];
    if (serverVal !== undefined) {
      const diff = fieldVal - serverVal;
      deviations[`${key}_diff`] = parseFloat(diff.toFixed(2));
      
      // 简单模拟异常判断逻辑：如果电压误差超 5% 或温度超 80度等
      if (Math.abs(diff) > serverVal * 0.05) {
        isMatch = false;
        status = "WARNING";
      }
    }
  }

  res.json({
    success: true,
    code: 1,
    equipment_id,
    comparison_result: {
      is_match: isMatch,
      deviations: deviations,
      status: status
    },
    message: "现场数据与服务端数据对比完成",
    timestamp: new Date().toISOString()
  });
});

app.post('/api/uwb/range', (req, res) => {
  const { device_id, distance_m, confidence } = req.body;
  console.log('[UWB] Range request:', { device_id, distance_m, confidence });
  
  res.json({
    success: true,
    code: 1,
    range_id: `rng_${Date.now()}`,
    measured_at: new Date().toISOString(),
    distance_m: distance_m || 1.42,
    confidence: confidence || 0.96
  });
});

// ==========================================
// 专业的射频测距定位测试工具 - 服务端接口实现
// ==========================================

const DB_DIR = path.join(__dirname, 'db');
if (!fs.existsSync(DB_DIR)) {
  fs.mkdirSync(DB_DIR, { recursive: true });
}

const loadDb = (filename) => {
  const filePath = path.join(DB_DIR, filename);
  if (fs.existsSync(filePath)) {
    try {
      return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (e) {
      return [];
    }
  }
  return [];
};

const saveDb = (filename, data) => {
  fs.writeFileSync(path.join(DB_DIR, filename), JSON.stringify(data, null, 2), 'utf8');
};

// 本地文件数据库：存储测试数据
const testDatabase = {
  bleData: loadDb('bleData.json'),
  uwbData: loadDb('uwbData.json'),
  fusionData: loadDb('fusionData.json')
};

// 1. 接收 BLE 模块测试数据上传
app.post('/api/test-data/ble', (req, res) => {
  console.log(`\n[API CALL ${new Date().toISOString()}] /api/test-data/ble POST received`);
  console.log(`[BLE UPLOAD DATA]:`, JSON.stringify(req.body, null, 2));
  const data = req.body;
  data._server_id = `ble_record_${Date.now()}`;
  data._received_at = new Date().toISOString();
  testDatabase.bleData.push(data);
  saveDb('bleData.json', testDatabase.bleData);
  
  res.json({ 
    success: true, 
    code: 1, 
    message: "BLE测试数据上传成功", 
    record_id: data._server_id 
  });
});

// 2. 接收 UWB 模块测试数据上传
app.post('/api/test-data/uwb', (req, res) => {
  console.log(`\n[API CALL ${new Date().toISOString()}] /api/test-data/uwb POST received`);
  console.log(`[UWB UPLOAD DATA]:`, JSON.stringify(req.body, null, 2));
  const data = req.body;
  data._server_id = `uwb_record_${Date.now()}`;
  data._received_at = new Date().toISOString();
  testDatabase.uwbData.push(data);
  saveDb('uwbData.json', testDatabase.uwbData);
  
  res.json({ 
    success: true, 
    code: 1, 
    message: "UWB测试数据上传成功", 
    record_id: data._server_id 
  });
});

// 3. 接收 BLE+UWB 融合测试记录上传
app.post('/api/test-data/fusion', (req, res) => {
  console.log(`\n[API CALL ${new Date().toISOString()}] /api/test-data/fusion POST received`);
  console.log(`[FUSION UPLOAD DATA]:`, JSON.stringify(req.body, null, 2));
  const data = req.body;
  if (!data.test_id) {
    data.test_id = `TEST_${Date.now()}_AUTO`;
  }
  data._received_at = new Date().toISOString();
  testDatabase.fusionData.push(data);
  saveDb('fusionData.json', testDatabase.fusionData);
  
  res.json({ 
    success: true, 
    code: 1, 
    message: "融合测试记录上传成功", 
    test_id: data.test_id 
  });
});

// 4. 获取 融合 测试记录列表（简略信息）
app.get('/api/test-data/fusion', (req, res) => {
  console.log(`[API CALL] /api/test-data/fusion GET list`);
  const summaries = testDatabase.fusionData.map(d => ({
    test_id: d.test_id,
    test_type: d.test_type,
    environment: d.environment,
    fused_distance_m: d.fusion_result?.fused_distance_m,
    received_at: d._received_at
  }));
  res.json({ 
    success: true, 
    code: 1, 
    total: summaries.length,
    data: summaries 
  });
});

// 5. 获取某条 融合 测试记录详细信息
app.get('/api/test-data/fusion/:test_id', (req, res) => {
  const testId = req.params.test_id;
  console.log(`[API CALL] /api/test-data/fusion GET detail for ${testId}`);
  const record = testDatabase.fusionData.find(d => d.test_id === testId);
  
  if (record) {
    res.json({ success: true, code: 1, data: record });
  } else {
    res.status(404).json({ success: false, code: 404, message: "未找到该测试记录" });
  }
});

app.listen(PORT, () => {
  console.log(`✅ aieyecloud Backend running at http://localhost:${PORT}`);
  console.log(`➡️  Try: curl http://localhost:${PORT}/`);
});