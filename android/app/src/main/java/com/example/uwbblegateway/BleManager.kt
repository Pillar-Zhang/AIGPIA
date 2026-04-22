package com.example.uwbblegateway

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.*

private const val TAG = "Glasses-BleManager"

/**
 * 专门用于 AI 眼镜连接与数据获取的管理器
 */
class BleManager(private val context: Context) {

    enum class DeviceStatus {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        READY,
        DISCONNECTING,
        ERROR
    }

    data class GlassesData(
        val battery: Int = 0,
        val isCharging: Boolean = false,
        val sensorInfo: String = "未连接",
        val deviceName: String? = null,
        val status: DeviceStatus = DeviceStatus.DISCONNECTED,
        val rssi: Int = 0,
        val error: String? = null,
        val streamUrl: String? = null,
        // 新增：实时姿态数据
        val pitch: Float = 0f,
        val yaw: Float = 0f,
        val roll: Float = 0f
    )

    private val _glassesState = MutableStateFlow(GlassesData())
    val glassesState = _glassesState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    // 模拟连接相关的 WebSocket
    private var mockWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient()

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothPermissions(): Boolean {
        return hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!checkBluetoothPermissions()) {
            _glassesState.value = _glassesState.value.copy(status = DeviceStatus.ERROR, error = "缺少蓝牙连接权限")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _glassesState.value = _glassesState.value.copy(status = DeviceStatus.ERROR, error = "蓝牙未开启或不支持")
            return
        }

        // 真实连接逻辑
        _glassesState.value = _glassesState.value.copy(status = DeviceStatus.CONNECTING, deviceName = "AI Glasses (Real)")
        bluetoothGatt = bluetoothAdapter?.getRemoteDevice(address)?.connectGatt(context, false, gattCallback)
    }

    /**
     * 调用服务端启用的模拟 AI 眼镜服务
     */
    fun connectMock() {
        Log.d(TAG, "Starting MOCK mode via WebSocket to dev server")
        _glassesState.value = _glassesState.value.copy(
            status = DeviceStatus.CONNECTING,
            deviceName = "Virtual AI Glasses (Simulation)"
        )

        val request = Request.Builder()
            .url("ws://172.20.6.37:3002") // 模拟器访问宿主机的 IP，对应模拟服务端接口
            .build()

        mockWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Mock WebSocket connected")
                _glassesState.value = _glassesState.value.copy(
                    status = DeviceStatus.READY,
                    sensorInfo = "模拟链路(WS)已建立",
                    streamUrl = "http://172.20.6.37:3003/video_feed" // 模拟视频流地址
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val systemStatus = json.optJSONObject("system_status")
                    val imuData = json.optJSONObject("imu_data")
                    
                    val battery = systemStatus?.optInt("battery_level") ?: _glassesState.value.battery
                    val euler = imuData?.optJSONObject("euler_angles")
                    
                    val pitch = euler?.optDouble("pitch")?.toFloat() ?: _glassesState.value.pitch
                    val yaw = euler?.optDouble("yaw")?.toFloat() ?: _glassesState.value.yaw
                    val roll = euler?.optDouble("roll")?.toFloat() ?: _glassesState.value.roll

                    _glassesState.value = _glassesState.value.copy(
                        battery = battery,
                        pitch = pitch,
                        yaw = yaw,
                        roll = roll
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse mock telemetry: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Mock WebSocket failure: ${t.message}")
                _glassesState.value = _glassesState.value.copy(
                    status = DeviceStatus.ERROR,
                    error = "无法连接到模拟服务器 (ws://172.20.6.37:3002)"
                )
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _glassesState.value = _glassesState.value.copy(status = DeviceStatus.DISCONNECTED)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        _glassesState.value = GlassesData(status = DeviceStatus.DISCONNECTING)
        
        // 关闭真实蓝牙连接
        bluetoothGatt?.disconnect()
        bluetoothGatt = null
        
        // 关闭模拟连接
        mockWebSocket?.close(1000, "User disconnected")
        mockWebSocket = null
        
        _glassesState.value = GlassesData(status = DeviceStatus.DISCONNECTED)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _glassesState.value = _glassesState.value.copy(status = DeviceStatus.ERROR, error = "连接失败代码: $status")
                gatt.close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _glassesState.value = _glassesState.value.copy(
                        status = DeviceStatus.CONNECTED,
                        deviceName = try { gatt.device.name } catch (e: Exception) { "AI Glasses" }
                    )
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _glassesState.value = GlassesData(status = DeviceStatus.DISCONNECTED)
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _glassesState.value = _glassesState.value.copy(status = DeviceStatus.READY, sensorInfo = "数据通道已建立")
                val service = gatt.getService(BATTERY_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BATTERY_LEVEL_UUID)
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                @Suppress("DEPRECATION")
                val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                _glassesState.value = _glassesState.value.copy(battery = batteryLevel)
            }
        }
    }  // end of gattCallback

    /**
     * 将当前定位坐标 + 下一导航点通过 BLE 下发眼镜端
     */
    @SuppressLint("MissingPermission")
    fun sendNavData(position: Position2D, nextPoint: NavPoint?) {
        val payload = buildString {
            append("{\"type\":\"nav\",")
            append("\"x\":${position.x},\"y\":${position.y},")
            append("\"conf\":${position.confidence}")
            nextPoint?.let { pt ->
                append(",\"next\":{\"id\":\"${pt.id}\",\"label\":\"${pt.label}\",\"x\":${pt.x},\"y\":${pt.y}}")
            }
            append("}")
        }
        // 真实连接：GATT 写入第一个可写特征值
        bluetoothGatt?.let { gatt ->
            gatt.services.forEach { svc ->
                svc.characteristics.firstOrNull {
                    it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                }?.let { char ->
                    char.value = payload.toByteArray(Charsets.UTF_8)
                    gatt.writeCharacteristic(char)
                    return@let
                }
            }
        }
        // 模拟连接：通过 WebSocket 发送
        mockWebSocket?.send(payload)
        Log.d(TAG, "Nav data sent: $payload")
    }
}
