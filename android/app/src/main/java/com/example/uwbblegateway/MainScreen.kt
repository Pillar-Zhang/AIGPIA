package com.example.uwbblegateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private const val TAG = "AI-Glasses-Helper"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(username: String) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("连接管理", "BLE 扫描", "UWB 测距", "AI 眼镜", "数据统计")

    // 核心连接状态
    var pmsConnected by remember { mutableStateOf(false) }
    var bleConnected by remember { mutableStateOf(false) }
    var uwbConnected by remember { mutableStateOf(false) }

    val bleManager = remember { BleManager(context) }
    val glassesData by bleManager.glassesState.collectAsState()

    // 模拟数据状态
    var isBleScanning by remember { mutableStateOf(false) }
    val bleDevices = remember { mutableStateListOf<BleDeviceMock>() }
    var isUwbRanging by remember { mutableStateOf(false) }
    var uwbDistance by remember { mutableDoubleStateOf(0.0) }
    var uwbAoa by remember { mutableIntStateOf(0) }
    var uwbLastTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val apiService = remember { ApiService.create() }
    val scope = rememberCoroutineScope()

    // --- 权限处理逻辑 ---
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bleManager.connect("AA:BB:CC:DD:EE:FF")
        }
    }

    fun requestBluetoothAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bleManager.connect("AA:BB:CC:DD:EE:FF")
            } else {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            bleManager.connect("AA:BB:CC:DD:EE:FF")
        }
    }

    // 持续运行逻辑 (模拟数据)
    LaunchedEffect(isBleScanning) {
        if (isBleScanning) {
            while (isBleScanning) {
                if (bleDevices.size < 15) {
                    bleDevices.add(BleDeviceMock(
                        "UWB-Tag-${Random.nextInt(10, 99)}",
                        "AA:BB:CC:DD:EE:${Random.nextInt(10, 99)}",
                        Random.nextInt(-90, -40)
                    ))
                }
                delay(1500)
            }
        }
    }

    LaunchedEffect(isUwbRanging) {
        if (isUwbRanging) {
            while (isUwbRanging) {
                uwbDistance = 2.0 + Random.nextDouble() * 5.0
                uwbAoa = Random.nextInt(-30, 30)
                uwbLastTimestamp = System.currentTimeMillis()
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
                StatusIndicatorBar(
                    pms = pmsConnected, 
                    ble = bleConnected, 
                    uwb = uwbConnected, 
                    glassesState = glassesData.status
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { 
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(title, fontSize = 11.sp)
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
                    glassesData = glassesData,
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
                    onConnectGlassesReal = {
                        if (glassesData.status == BleManager.DeviceStatus.DISCONNECTED || glassesData.status == BleManager.DeviceStatus.ERROR) {
                            requestBluetoothAndConnect()
                        } else {
                            bleManager.disconnect()
                        }
                    },
                    onConnectGlassesMock = {
                        if (glassesData.status == BleManager.DeviceStatus.DISCONNECTED || glassesData.status == BleManager.DeviceStatus.ERROR) {
                            bleManager.connectMock()
                        } else {
                            bleManager.disconnect()
                        }
                    }
                )
                1 -> BleScanTab(isScanning = isBleScanning, devices = bleDevices, canTest = bleConnected && uwbConnected, onToggleScan = { isBleScanning = !isBleScanning })
                2 -> UwbRangingTab(isRanging = isUwbRanging, distance = uwbDistance, aoa = uwbAoa, canTest = bleConnected && uwbConnected, onToggleRanging = { isUwbRanging = !isUwbRanging })
                3 -> AiGlassesTab(glassesData = glassesData)
                4 -> DataStatisticsTab(apiService, glassesData)
            }
        }
    }
}

@Composable
fun AiGlassesTab(glassesData: BleManager.GlassesData) {
    val isConnected = glassesData.status == BleManager.DeviceStatus.READY
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AI 眼镜实时视频流", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // 视频播放器区域占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                // 使用纯原生 Compose Image + 轮询拉取的方式，100% 避开 WebView 的各种安全和黑屏拦截！
                var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                var frameError by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        while (true) {
                            try {
                                val url = java.net.URL("http://10.0.2.2:3003/snapshot?t=" + System.currentTimeMillis())
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.connectTimeout = 1000
                                connection.readTimeout = 1000
                                if (connection.responseCode == 200) {
                                    connection.inputStream.use { stream ->
                                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                        if (bitmap != null) {
                                            imageBitmap = bitmap.asImageBitmap()
                                            frameError = null
                                        }
                                    }
                                } else {
                                    frameError = "Server returned ${connection.responseCode}"
                                }
                            } catch (e: Exception) {
                                frameError = e.message
                            }
                            kotlinx.coroutines.delay(50) // 20 FPS
                        }
                    }
                }

                if (imageBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Live Video",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("获取原生画面流中...", color = Color.White)
                        frameError?.let {
                            Text("网络异常: $it", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
                
                // 模拟 AI 识别框
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.offset(x = 40.dp, y = 50.dp).size(80.dp).border(2.dp, Color.Cyan, RoundedCornerShape(4.dp))) {
                        Text("电力变压器", color = Color.Cyan, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("眼镜未连接，无法获取视频", color = Color.Gray)
                    Text("请先在连接管理页面完成配对", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            InfoBox(label = "帧率", value = if (isConnected) "30 FPS" else "--")
            InfoBox(label = "延迟", value = if (isConnected) "45 ms" else "--")
            InfoBox(label = "分辨率", value = if (isConnected) "1920x1080" else "--")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("眼镜端传感器数据", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IMU 姿态:", fontSize = 12.sp)
                    val imuText = if (isConnected) {
                        String.format(java.util.Locale.US, "P: %.1f° Y: %.1f° R: %.1f°", glassesData.pitch, glassesData.yaw, glassesData.roll)
                    } else {
                        "N/A"
                    }
                    Text(imuText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.padding(top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("环境光强度:", fontSize = 12.sp)
                    Text(if (isConnected) "450 Lux" else "N/A", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DataStatisticsTab(apiService: ApiService, glassesData: BleManager.GlassesData) {
    val scope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("测试数据统计分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        StatisticCard(label = "平均测距误差", value = "0.08 m", color = Color(0xFF4CAF50))
        StatisticCard(label = "测距成功率", value = "98.5%", color = Color(0xFF4CAF50))
        
        // 新增 AI 眼镜统计
        StatisticCard(label = "眼镜连接稳定性", value = if (glassesData.status == BleManager.DeviceStatus.READY) "优秀" else "待测试", color = Color(0xFF2196F3))
        StatisticCard(label = "眼镜实时电量", value = "${glassesData.battery}%", color = if (glassesData.battery > 20) Color(0xFF4CAF50) else Color.Red)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(
            onClick = { 
                val currentTimestamp = System.currentTimeMillis()
                scope.launch {
                    try {
                        val fusionData = mapOf(
                            "test_id" to "TEST_$currentTimestamp",
                            "test_type" to "full_system_test",
                            "environment" to "indoor_substation",
                            "glasses_info" to mapOf(
                                "device_name" to (glassesData.deviceName ?: "Unknown"),
                                "battery" to glassesData.battery,
                                "status" to glassesData.status.name
                            ),
                            "fusion_result" to mapOf(
                                "fused_distance_m" to 4.98,
                                "confidence" to 0.95
                            ),
                            "timestamp" to currentTimestamp
                        )
                        apiService.uploadFusionTestData(fusionData)
                        Log.d(TAG, "Full system data synchronized to backend.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync data: ${e.message}")
                    }
                }
            }, 
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导出全系统报告 (同步至后台)")
        }
        
        Text(
            "提示：报告将包含 UWB 测距、BLE 扫描及 AI 眼镜传感器融合数据",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun StatusIndicatorBar(pms: Boolean, ble: Boolean, uwb: Boolean, glassesState: BleManager.DeviceStatus) {
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
        
        val glassesLabel = when (glassesState) {
            BleManager.DeviceStatus.CONNECTING -> "连接中..."
            BleManager.DeviceStatus.READY -> "眼镜(就绪)"
            BleManager.DeviceStatus.ERROR -> "眼镜(故障)"
            else -> "眼镜"
        }
        val isGlassesActive = glassesState == BleManager.DeviceStatus.CONNECTED || glassesState == BleManager.DeviceStatus.READY
        StatusItem(label = glassesLabel, connected = isGlassesActive, specialStatus = glassesState)
    }
}

@Composable
fun StatusItem(label: String, connected: Boolean, specialStatus: BleManager.DeviceStatus? = null) {
    val color = when {
        specialStatus == BleManager.DeviceStatus.ERROR -> Color(0xFFF44336)
        specialStatus == BleManager.DeviceStatus.CONNECTING -> Color(0xFFFF9800)
        connected -> Color(0xFF4CAF50)
        else -> Color.Gray
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = color)
    }
}

@Composable
fun ConnectionPanel(
    pmsConnected: Boolean,
    bleConnected: Boolean,
    uwbConnected: Boolean,
    glassesData: BleManager.GlassesData,
    onConnectPms: () -> Unit,
    onConnectBle: () -> Unit,
    onConnectUwb: () -> Unit,
    onConnectGlassesReal: () -> Unit,
    onConnectGlassesMock: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("核心组件连接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        GlassesConnectionCard(data = glassesData, onConnectReal = onConnectGlassesReal, onConnectMock = onConnectGlassesMock)
        ConnectionCard(title = "PMS 3.0 系统", description = "后台业务同步", isConnected = pmsConnected, onConnect = onConnectPms)
        ConnectionCard(title = "BLE 蓝牙模组", description = "射频扫描组件", isConnected = bleConnected, onConnect = onConnectBle)
        ConnectionCard(title = "UWB 定位模组", description = "厘米级测距引擎", isConnected = uwbConnected, onConnect = onConnectUwb)
    }
}

@Composable
fun GlassesConnectionCard(data: BleManager.GlassesData, onConnectReal: () -> Unit, onConnectMock: () -> Unit) {
    val isConnected = data.status == BleManager.DeviceStatus.CONNECTED || data.status == BleManager.DeviceStatus.READY
    val bgColor = when (data.status) {
        BleManager.DeviceStatus.READY -> Color(0xFFE8F5E9)
        BleManager.DeviceStatus.CONNECTING -> Color(0xFFFFF3E0)
        BleManager.DeviceStatus.ERROR -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Info else Icons.Default.Warning, 
                    contentDescription = null, 
                    tint = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 智能眼镜", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = when(data.status) {
                            BleManager.DeviceStatus.CONNECTING -> "正在寻找设备并握手..."
                            BleManager.DeviceStatus.READY -> "已连接: ${data.deviceName ?: "未知"} | 电量: ${data.battery}%"
                            BleManager.DeviceStatus.ERROR -> "连接出错: ${data.error}"
                            else -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            if (data.status == BleManager.DeviceStatus.READY) {
                LinearProgressIndicator(
                    progress = { data.battery / 100f },
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (data.battery > 20) Color(0xFF4CAF50) else Color.Red
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isConnected || data.status == BleManager.DeviceStatus.CONNECTING) {
                    Button(
                        onClick = onConnectReal, // 统一调用断开逻辑
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text(if (data.status == BleManager.DeviceStatus.CONNECTING) "取消" else "断开", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onConnectReal,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("真实连接", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onConnectMock
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("模拟连接", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BleScanTab(isScanning: Boolean, devices: SnapshotStateList<BleDeviceMock>, canTest: Boolean, onToggleScan: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BLE 扫描发现", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onToggleScan, enabled = canTest || isScanning) {
                Text(if (isScanning) "停止" else "开始扫描")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(devices) { device ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Column {
                            Text(device.name, fontWeight = FontWeight.Bold)
                            Text(device.mac, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${device.rssi} dBm")
                    }
                }
            }
        }
    }
}

@Composable
fun UwbRangingTab(isRanging: Boolean, distance: Double, aoa: Int, canTest: Boolean, onToggleRanging: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("UWB 实时测距", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.US, "%.2f", distance), style = MaterialTheme.typography.displayMedium)
                Text("米 (m)")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("AoA 方位角: $aoa°")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onToggleRanging, enabled = canTest || isRanging, modifier = Modifier.fillMaxWidth()) {
            Text(if (isRanging) "停止测距" else "开始测距")
        }
    }
}

@Composable
fun StatisticCard(label: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 24.dp).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold, color = color)
        }
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

@Composable
fun InfoBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

data class BleDeviceMock(val name: String, val mac: String, val rssi: Int)
