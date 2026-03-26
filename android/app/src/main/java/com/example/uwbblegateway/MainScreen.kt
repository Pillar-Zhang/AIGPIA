package com.example.uwbblegateway

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private const val TAG = "AI-Glasses-Helper"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(username: String) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("连接管理", "BLE 扫描", "UWB 测距", "数据统计")

    // 核心连接状态
    var pmsConnected by remember { mutableStateOf(false) }
    var bleConnected by remember { mutableStateOf(false) }
    var uwbConnected by remember { mutableStateOf(false) }
    var glassesConnected by remember { mutableStateOf(false) }

    // BLE 扫描持久状态 (存放在 MainScreen 以保持后台运行)
    var isBleScanning by remember { mutableStateOf(false) }
    val bleDevices = remember { mutableStateListOf<BleDeviceMock>() }

    // UWB 测距持久状态 (存放在 MainScreen 以保持后台运行)
    var isUwbRanging by remember { mutableStateOf(false) }
    var uwbDistance by remember { mutableStateOf(0.0) }
    var uwbAoa by remember { mutableStateOf(0) }
    var uwbLastTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    val apiService = remember { ApiService.create() }
    val scope = rememberCoroutineScope()

    // 持续运行 BLE 扫描逻辑 (使用 LaunchedEffect 确保 Tab 切换时不停止)
    LaunchedEffect(isBleScanning) {
        if (isBleScanning) {
            while (isBleScanning) {
                if (bleDevices.size < 15) {
                    bleDevices.add(BleDeviceMock(
                        "UWB-Tag-${Random.nextInt(10, 99)}",
                        "AA:BB:CC:DD:EE:${Random.nextInt(10, 99)}",
                        Random.nextInt(-90, -40),
                        timestamp = System.currentTimeMillis() / 1000 // 记录产生时的实时秒级时间戳
                    ))
                }
                delay(1500)
            }
        }
    }

    // 持续运行 UWB 测距逻辑
    LaunchedEffect(isUwbRanging) {
        if (isUwbRanging) {
            while (isUwbRanging) {
                uwbDistance = 2.0 + Random.nextDouble() * 5.0
                uwbAoa = Random.nextInt(-30, 30)
                uwbLastTimestamp = System.currentTimeMillis() // 记录产生时的实时毫秒级时间戳
                delay(100)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 眼镜电力助手", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(username, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                StatusIndicatorBar(pmsConnected, bleConnected, uwbConnected, glassesConnected)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { 
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(title, fontSize = 11.sp)
                                // 增加正在后台运行的绿色指示点
                                if ((index == 1 && isBleScanning) || (index == 2 && isUwbRanging)) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                }
                            }
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            when (selectedTab) {
                0 -> ConnectionPanel(
                    pmsConnected = pmsConnected,
                    bleConnected = bleConnected,
                    uwbConnected = uwbConnected,
                    glassesConnected = glassesConnected,
                    onConnectPms = {
                        scope.launch {
                            try {
                                val result = apiService.connectPms()
                                val statusVal = (result["status"] ?: result["code"]) as? Number
                                pmsConnected = statusVal?.toInt() == 1
                            } catch (e: Exception) { pmsConnected = false }
                        }
                    },
                    onConnectBle = {
                        scope.launch {
                            try {
                                val result = apiService.connectBle()
                                val statusVal = (result["status"] ?: result["code"]) as? Number
                                bleConnected = statusVal?.toInt() == 1
                            } catch (e: Exception) { bleConnected = false }
                        }
                    },
                    onConnectUwb = {
                        scope.launch {
                            try {
                                val result = apiService.connectUwb()
                                val statusVal = (result["status"] ?: result["code"]) as? Number
                                uwbConnected = statusVal?.toInt() == 1
                            } catch (e: Exception) { uwbConnected = false }
                        }
                    },
                    onConnectGlasses = {
                        scope.launch {
                            try {
                                val result = apiService.connectGlasses()
                                val statusVal = (result["status"] ?: result["code"]) as? Number
                                glassesConnected = statusVal?.toInt() == 1
                            } catch (e: Exception) { glassesConnected = false }
                        }
                    }
                )
                1 -> BleScanTab(
                    isScanning = isBleScanning,
                    devices = bleDevices,
                    canTest = bleConnected && uwbConnected, // 只有 BLE 和 UWB 连接才能测试
                    onToggleScan = { 
                        if (isBleScanning) {
                            // 停止并上传数据
                            if (bleDevices.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        val lastDevice = bleDevices.last()
                                        val bleData = mapOf(
                                            "device_info" to mapOf(
                                                "mac_address" to lastDevice.mac,
                                                "device_name" to lastDevice.name,
                                                "rssi" to lastDevice.rssi,
                                                "tx_power" to -12,
                                                "timestamp" to lastDevice.timestamp // 使用数据产生时的实时时间
                                            ),
                                            "connection_params" to mapOf(
                                                "connection_interval" to 7.5,
                                                "supervision_timeout" to 5000,
                                                "mtu" to 247
                                            ),
                                            "gatt_operations" to mapOf(
                                                "read_count" to 150,
                                                "error_count" to 0,
                                                "latency_ms" to 45
                                            )
                                        )
                                        apiService.uploadBleTestData(bleData)
                                        Log.d(TAG, "BLE data uploaded with real-time timestamp: ${lastDevice.timestamp}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed auto-upload BLE: ${e.message}")
                                    }
                                }
                            }
                            bleDevices.clear()
                            isBleScanning = false
                        } else {
                            isBleScanning = true
                        }
                    }
                )
                2 -> UwbRangingTab(
                    isRanging = isUwbRanging,
                    distance = uwbDistance,
                    aoa = uwbAoa,
                    canTest = bleConnected && uwbConnected, // 只有 BLE 和 UWB 连接才能测试
                    onToggleRanging = {
                        if (isUwbRanging) {
                            // 停止并上传
                            val uploadTimestamp = uwbLastTimestamp // 锁定当前最新的测量时间
                            scope.launch {
                                try {
                                    val uwbData = mapOf(
                                        "session_config" to mapOf(
                                            "session_id" to 1,
                                            "device_role" to "initiator",
                                            "device_address" to "0x1234"
                                        ),
                                        "measurement_data" to mapOf(
                                            "timestamp" to uploadTimestamp, // 使用数据产生时的实时时间
                                            "distance_m" to uwbDistance,
                                            "aoa_azimuth_deg" to uwbAoa,
                                            "nlos_indicator" to false
                                        ),
                                        "quality_metrics" to mapOf(
                                            "std_deviation_m" to 0.08,
                                            "success_rate_percent" to 98.5
                                        )
                                    )
                                    apiService.uploadUwbTestData(uwbData)
                                    Log.d(TAG, "UWB data uploaded with real-time timestamp: $uploadTimestamp")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed auto-upload UWB: ${e.message}")
                                }
                            }
                            isUwbRanging = false
                        } else {
                            isUwbRanging = true
                        }
                    }
                )
                3 -> DataStatisticsTab(apiService)
            }
        }
    }
}

@Composable
fun BleScanTab(
    isScanning: Boolean,
    devices: SnapshotStateList<BleDeviceMock>,
    canTest: Boolean,
    onToggleScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("BLE 扫描发现", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isScanning) {
                    Text("正在后台持续扫描...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                } else if (!canTest) {
                    Text("需连接 BLE 和 UWB 模组后方可测试", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onToggleScan,
                enabled = canTest || isScanning, // 如果正在运行，允许停止；如果没运行，必须满足连接条件
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) Color(0xFFF44336) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (isScanning) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isScanning) "停止并上传" else "开始扫描")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.Bold)
                            Text(device.mac, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("${device.rssi} dBm", color = if (device.rssi > -60) Color(0xFF4CAF50) else Color.Gray)
                    }
                }
            }
        }
    }
}

data class BleDeviceMock(
    val name: String, 
    val mac: String, 
    val rssi: Int, 
    val timestamp: Long = System.currentTimeMillis() / 1000
)

@Composable
fun UwbRangingTab(
    isRanging: Boolean,
    distance: Double,
    aoa: Int,
    canTest: Boolean,
    onToggleRanging: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("UWB 实时测距仪表盘", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (isRanging) {
            Text("测距任务正在后台运行", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
        } else if (!canTest) {
            Text("需连接 BLE 和 UWB 模组后方可测试", style = MaterialTheme.typography.bodySmall, color = Color.Red)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.US, "%.2f", distance), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text("米 (m)", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            InfoBox(label = "AoA 方位角", value = "$aoa°")
            InfoBox(label = "刷新频率", value = "10 Hz")
            InfoBox(label = "测距模式", value = "DS-TWR")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onToggleRanging,
            enabled = canTest || isRanging,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRanging) Color(0xFFF44336) else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isRanging) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRanging) "停止测距并上传数据" else "开始实时测距")
        }
    }
}

@Composable
fun InfoBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DataStatisticsTab(apiService: ApiService) {
    val scope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("测试数据统计分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        StatisticCard(label = "平均测距误差", value = "0.08 m", color = Color(0xFF4CAF50))
        StatisticCard(label = "测距成功率", value = "98.5%", color = Color(0xFF4CAF50))
        StatisticCard(label = "AoA 平均偏离", value = "2.4°", color = Color(0xFF2196F3))
        StatisticCard(label = "信号覆盖质量 (RSSI)", value = "-68 dBm", color = Color(0xFFFF9800))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(
            onClick = { 
                val currentTimestamp = System.currentTimeMillis() // 立即捕捉手机实时时间
                scope.launch {
                    try {
                        val fusionData = mapOf(
                            "test_id" to "TEST_$currentTimestamp",
                            "test_type" to "distance_accuracy",
                            "environment" to "indoor_office",
                            "ground_truth" to mapOf("reference_distance_m" to 5.0),
                            "fusion_result" to mapOf(
                                "fused_distance_m" to 4.98,
                                "confidence" to 0.92,
                                "algorithm" to "kalman_filter"
                            ),
                            "error_analysis" to mapOf(
                                "ble_error_m" to 1.2,
                                "uwb_error_m" to 0.05,
                                "fused_error_m" to 0.02
                            ),
                            "timestamp" to currentTimestamp // 增加实时时间戳字段
                        )
                        apiService.uploadFusionTestData(fusionData)
                        Log.d(TAG, "Fusion data uploaded with real-time timestamp: $currentTimestamp")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to upload Fusion data: ${e.message}")
                    }
                }
            }, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导出测试报告 (同步至后端)")
        }
    }
}

@Composable
fun StatisticCard(label: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 24.dp).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
        }
    }
}

@Composable
fun StatusIndicatorBar(pms: Boolean, ble: Boolean, uwb: Boolean, glasses: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem(label = "PMS", connected = pms)
        StatusItem(label = "BLE", connected = ble)
        StatusItem(label = "UWB", connected = uwb)
        StatusItem(label = "眼镜", connected = glasses)
    }
}

@Composable
fun StatusItem(label: String, connected: Boolean) {
    val color by animateColorAsState(if (connected) Color(0xFF4CAF50) else Color(0xFFF44336))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = if (connected) Color(0xFF2E7D32) else Color(0xFFC62828))
    }
}

@Composable
fun ConnectionPanel(
    pmsConnected: Boolean,
    bleConnected: Boolean,
    uwbConnected: Boolean,
    glassesConnected: Boolean,
    onConnectPms: () -> Unit,
    onConnectBle: () -> Unit,
    onConnectUwb: () -> Unit,
    onConnectGlasses: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("核心组件连接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ConnectionCard(title = "AI 智能眼镜", description = "蓝牙终端配对", isConnected = glassesConnected, onConnect = onConnectGlasses)
        ConnectionCard(title = "PMS 3.0 系统", description = "后台业务同步", isConnected = pmsConnected, onConnect = onConnectPms)
        ConnectionCard(title = "BLE 蓝牙模组", description = "射频扫描组件", isConnected = bleConnected, onConnect = onConnectBle)
        ConnectionCard(title = "UWB 定位模组", description = "厘米级测距引擎", isConnected = uwbConnected, onConnect = onConnectUwb)
    }
}

@Composable
fun ConnectionCard(title: String, description: String, isConnected: Boolean, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFB0BEC5))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Button(onClick = onConnect, modifier = Modifier.height(36.dp)) {
                Text(if (isConnected) "重连" else "连接", fontSize = 11.sp)
            }
        }
    }
}
