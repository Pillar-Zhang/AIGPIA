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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

private const val TAG = "AI-Glasses-Helper"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    username: String,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("连接管理", "BLE 扫描", "UWB 测距", "AI 眼镜", "数据统计", "定位地图", "AI 识别", "巡检任务")

    // 从 ViewModel 收集状态
    val connectionState by mainViewModel.connectionState.collectAsState()
    val bleScanState by mainViewModel.bleScanState.collectAsState()
    val uwbState by mainViewModel.uwbState.collectAsState()
    val glassesData by mainViewModel.bleManager.glassesState.collectAsState()

    // 权限处理
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            mainViewModel.bleManager.connect("AA:BB:CC:DD:EE:FF")
        }
    }

    fun requestBluetoothAndConnect() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            mainViewModel.bleManager.connect("AA:BB:CC:DD:EE:FF")
        } else {
            bluetoothPermissionLauncher.launch(required)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(username, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                StatusIndicatorBar(
                    pms = connectionState.pmsConnected,
                    ble = connectionState.bleConnected,
                    uwb = connectionState.uwbConnected,
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
                                // 活动状态指示点
                                if ((index == 1 && bleScanState.isScanning) ||
                                    (index == 2 && uwbState.isRanging)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
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
                    pmsConnected = connectionState.pmsConnected,
                    bleConnected = connectionState.bleConnected,
                    uwbConnected = connectionState.uwbConnected,
                    glassesData = glassesData,
                    onConnectPms = { mainViewModel.connectPms() },
                    onConnectBle = { mainViewModel.connectBle() },
                    onConnectUwb = { mainViewModel.connectUwb() },
                    onConnectGlassesReal = {
                        if (glassesData.status == BleManager.DeviceStatus.DISCONNECTED ||
                            glassesData.status == BleManager.DeviceStatus.ERROR
                        ) {
                            requestBluetoothAndConnect()
                        } else {
                            mainViewModel.bleManager.disconnect()
                        }
                    },
                    onConnectGlassesMock = {
                        if (glassesData.status == BleManager.DeviceStatus.DISCONNECTED ||
                            glassesData.status == BleManager.DeviceStatus.ERROR
                        ) {
                            mainViewModel.bleManager.connectMock()
                        } else {
                            mainViewModel.bleManager.disconnect()
                        }
                    }
                )
                1 -> BleScanTab(
                    isScanning = bleScanState.isScanning,
                    devices = bleScanState.devices,
                    canScan = true, // 扫描不强制要求后台连接
                    onToggleScan = { mainViewModel.toggleBleScan() }
                )
                2 -> UwbRangingTab(
                    uwbState = uwbState,
                    canTest = connectionState.bleConnected && connectionState.uwbConnected,
                    onToggleRanging = { mainViewModel.toggleUwbRanging() }
                )
                3 -> AiGlassesTab(glassesData = glassesData)
                4 -> DataStatisticsTab(
                    glassesData = glassesData,
                    uwbState = uwbState,
                    onUploadReport = { mainViewModel.uploadFusionReport(glassesData) }
                )
                5 -> MapScreen(bleManager = mainViewModel.bleManager)
                6 -> AiScreen(bleManager = mainViewModel.bleManager)
                7 -> InspectionListScreen()
            }
        }
    }
}

// ─── AI 眼镜 Tab ──────────────────────────────────────────────────────────────

@Composable
fun AiGlassesTab(glassesData: BleManager.GlassesData) {
    val isConnected = glassesData.status == BleManager.DeviceStatus.READY

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI 眼镜实时视频流", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

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

                // 模拟 AI 识别框（Phase 3 替換为真实推理结果）
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset(x = 40.dp, y = 50.dp)
                            .size(80.dp)
                            .border(2.dp, Color.Cyan, RoundedCornerShape(4.dp))
                    ) {
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
            InfoBox(label = "帧率", value = if (isConnected) "20 FPS" else "--")
            InfoBox(label = "延迟", value = if (isConnected) "50 ms" else "--")
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
                        String.format(
                            Locale.US,
                            "P: %.1f° Y: %.1f° R: %.1f°",
                            glassesData.pitch, glassesData.yaw, glassesData.roll
                        )
                    } else "N/A"
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

// ─── 数据统计 Tab ─────────────────────────────────────────────────────────────

@Composable
fun DataStatisticsTab(
    glassesData: BleManager.GlassesData,
    uwbState: UwbState,
    onUploadReport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("测试数据统计分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // UWB 统计
        val distanceText = if (uwbState.isRanging) {
            String.format(Locale.US, "%.2f m%s", uwbState.distanceMeters, if (uwbState.isSimulated) " [模拟]" else "")
        } else "--"
        StatisticCard(label = "UWB 实时距离", value = distanceText, color = Color(0xFF2196F3))
        StatisticCard(
            label = "UWB 方位角",
            value = if (uwbState.isRanging) String.format(Locale.US, "%.1f°", uwbState.azimuthDegrees) else "--",
            color = Color(0xFF2196F3)
        )

        // 眼镜统计
        StatisticCard(
            label = "眼镜连接状态",
            value = if (glassesData.status == BleManager.DeviceStatus.READY) "已就绪" else "待连接",
            color = Color(0xFF4CAF50)
        )
        StatisticCard(
            label = "眼镜实时电量",
            value = "${glassesData.battery}%",
            color = if (glassesData.battery > 20) Color(0xFF4CAF50) else Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onUploadReport,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导出全系统报告 (同步至后台)")
        }

        Text(
            "提示：报告将包含 UWB 测距（${if (uwbState.isSimulated) "模拟数据" else "真实数据"}）及 AI 眼镜传感器融合数据",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ─── 状态栏 ───────────────────────────────────────────────────────────────────

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
        val isGlassesActive = glassesState == BleManager.DeviceStatus.CONNECTED ||
                glassesState == BleManager.DeviceStatus.READY
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

// ─── 连接管理 Tab ─────────────────────────────────────────────────────────────

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
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        text = when (data.status) {
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
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (data.battery > 20) Color(0xFF4CAF50) else Color.Red
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isConnected || data.status == BleManager.DeviceStatus.CONNECTING) {
                    Button(
                        onClick = onConnectReal,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text(if (data.status == BleManager.DeviceStatus.CONNECTING) "取消" else "断开", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = onConnectReal, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("真实连接", fontSize = 12.sp)
                    }
                    Button(onClick = onConnectMock) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("模拟连接", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─── BLE 扫描 Tab ─────────────────────────────────────────────────────────────

@Composable
fun BleScanTab(
    isScanning: Boolean,
    devices: List<BleDevice>,
    canScan: Boolean,
    onToggleScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BLE 扫描发现", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
            }
            Button(onClick = onToggleScan) {
                Text(if (isScanning) "停止" else "开始扫描")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isScanning) "正在扫描... 发现 ${devices.size} 台设备" else "共发现 ${devices.size} 台设备",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            if (devices.isEmpty() && isScanning) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("搜索附近 BLE 设备中...", color = Color.Gray)
                        }
                    }
                }
            }
            items(devices, key = { it.address }) { device ->
                BleDeviceCard(device = device)
            }
        }
    }
}

@Composable
fun BleDeviceCard(device: BleDevice) {
    val rssiColor = when {
        device.rssi >= -60 -> Color(0xFF4CAF50)  // 强信号
        device.rssi >= -80 -> Color(0xFFFF9800)  // 中信号
        else -> Color(0xFFF44336)                 // 弱信号
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(rssiColor))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${device.rssi} dBm", color = rssiColor, fontWeight = FontWeight.Bold)
                Text(
                    if (device.isConnectable) "可连接" else "仅广播",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ─── UWB 测距 Tab ─────────────────────────────────────────────────────────────

@Composable
fun UwbRangingTab(
    uwbState: UwbState,
    canTest: Boolean,
    onToggleRanging: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("UWB 实时测距", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            if (uwbState.isSimulated) {
                Surface(
                    color = Color(0xFFFF9800),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "模拟模式",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (uwbState.isSupported) {
                Surface(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "硬件 UWB",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format(Locale.US, "%.2f", uwbState.distanceMeters),
                    style = MaterialTheme.typography.displayMedium
                )
                Text("米 (m)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(String.format(Locale.US, "AoA 方位角: %.1f°", uwbState.azimuthDegrees))
        Spacer(modifier = Modifier.height(8.dp))
        if (!uwbState.isSupported) {
            Text(
                "⚠ 当前设备不支持 UWB，数据为模拟值",
                fontSize = 12.sp,
                color = Color(0xFFFF9800)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onToggleRanging,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uwbState.isRanging) "停止测距" else "开始测距")
        }
    }
}

// ─── 通用组件 ─────────────────────────────────────────────────────────────────

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
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFB0BEC5)
                )
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
