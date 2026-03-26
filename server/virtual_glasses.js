const express = require('express');
const { Server } = require('ws');
const http = require('http');

// ==========================================
// 1. [控制通道] 模拟 BLE GATT (WebSocket 端口 3002)
// ==========================================
const bleServer = new Server({ port: 3002, host: '0.0.0.0' });

bleServer.on('connection', (socket) => {
    console.log('\n[BLE 模拟] ✅ 手机 App 成功连接到 虚拟 AI 眼镜的蓝牙通道!');
    
    // 模拟眼镜 IMU 数据以 20Hz 发送
    const interval = setInterval(() => {
        const payload = {
            packet_id: Date.now(),
            timestamp_ms: Date.now(),
            system_status: {
                battery_level: 85,
                temperature_c: 36.5,
                is_wearing: true,
                wifi_connected: true
            },
            imu_data: {
                acceleration: { 
                    x: (Math.random() * 0.2 - 0.1).toFixed(3), 
                    y: -9.81, 
                    z: (Math.random() * 0.2 - 0.1).toFixed(3) 
                },
                gyroscope: { 
                    x: (Math.random() * 0.05).toFixed(3), 
                    y: (Math.random() * 0.05).toFixed(3), 
                    z: (Math.random() * 0.05).toFixed(3) 
                },
                euler_angles: { 
                    pitch: (Math.random() * 5).toFixed(2), 
                    yaw: (Math.random() * 10 - 5).toFixed(2), 
                    roll: 0 
                }
            }
        };
        // 向 App 发送 JSON
        if (socket.readyState === 1) { 
            socket.send(JSON.stringify(payload));
        }
    }, 50);

    // 接收 App 下发的控制指令
    socket.on('message', (msg) => {
        console.log('[BLE 模拟] 收到 App 中控端发来的蓝牙指令:', msg.toString());
    });

    socket.on('close', () => {
        console.log('[BLE 模拟] ❌ 手机 App 断开连接.');
        clearInterval(interval);
    });
});
console.log('🌟 [BLE 模拟] 蓝牙 WebSocket 服务已启动监听 ws://localhost:3002');


// ==========================================
// 2. [数据通道] 模拟 Wi-Fi 图传 (HTTP/WS 端口 3003)
// ==========================================
// 架构说明: 
// 为了完美调取您电脑的摄像头且无需安装复杂的 C++ 驱动和底层 FFmpeg，
// 我们采用一招“网页桥接”：开发者在网页端打开本地 http://3003 以调取物理摄像头，
// 浏览器把画面发往后台，后台负责转化成 MJPEG 视频流暴露给 Android App。

const app = express();
const httpServer = http.createServer(app);
const wss = new Server({ server: httpServer });

let viewerResponses = []; // 保存当前所有正在看推流的 App 的响应句柄
let latestFrame = null; // 缓存最新的一帧用于快照拉取

// 专供原生 Android 直接拉取 JPEG 单帧快照的接口 (无兼容性问题)
app.get('/snapshot', (req, res) => {
    if (latestFrame) {
        res.setHeader('Content-Type', 'image/jpeg');
        res.setHeader('Cache-Control', 'no-store, max-age=0');
        res.send(latestFrame);
    } else {
        res.status(404).send('Camera not ready');
    }
});

// 这是给手机 App 直接调用的拉流接口
app.get('/video_feed', (req, res) => {
    console.log('\n[图传模拟] 📹 Android App 开始拉取视频流 (MJPEG)...');
    
    // 使用 MJPEG 标准的推流协议头
    res.writeHead(200, {
        'Content-Type': 'multipart/x-mixed-replace; boundary=myboundary',
        'Cache-Control': 'no-cache',
        'Connection': 'close',
        'Pragma': 'no-cache'
    });
    
    viewerResponses.push(res);
    
    req.on('close', () => {
        viewerResponses = viewerResponses.filter(r => r !== res);
        console.log('[图传模拟] ⏹️ Android App 停止了拉取视频流.');
    });
});

// 将从网页摄像头收到的单帧画面广播给所有连入的 App，同时缓存为快照
const broadcastFrame = (jpgBuffer) => {
    latestFrame = jpgBuffer; // 保存最新帧供 /snapshot 抓取
    
    if (viewerResponses.length === 0) return; // 没 App 连的时候没必要消耗性能推流
    
    const boundaryHeader = `--myboundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpgBuffer.length}\r\n\r\n`;
    viewerResponses.forEach(res => {
        res.write(boundaryHeader);
        res.write(jpgBuffer);
        res.write('\r\n');
    });
}

// 接收来自宿主机网页浏览器的摄像头画面帧数据
wss.on('connection', (ws, req) => {
    if (req.url === '/camera-uplink') {
        console.log('[宿主桥接] 🎥 电脑本地网页已成功激活主机摄像头连入！');
        ws.on('message', (data) => {
            // data 已经是图片 JPEG 二进制格式
            broadcastFrame(data);
        });
        ws.on('close', () => console.log('[宿主桥接] ❌ 电脑本机网页摄像头断开。'));
    }
});

// 专门提供给 Android WebView 加载的安全壳页面 (解决高版本 WebView 拦截 img 直连的问题)
app.get('/viewer', (req, res) => {
    res.send(`
        <html>
        <body style="margin:0;padding:0;background-color:black;display:flex;justify-content:center;align-items:center;">
            <img src="/video_feed" style="width:100%;height:100%;object-fit:contain;" />
        </body>
        </html>
    `);
});

// 这是提供给开发者在电脑上打开的中间层抓包页面
app.get('/', (req, res) => {
    res.send(`
        <html>
        <head><title>AI 眼镜虚拟化摄像头驱动</title></head>
        <body style="font-family: Arial, sans-serif; text-align: center; background: #1e1e1e; color: #fff; margin-top: 50px;">
            <h2>🕶️ 虚拟 AI 眼镜：摄像头桥接模块</h2>
            <p>请点击浏览器弹窗<strong>允许摄像头权限</strong>。本网页将隐性抓取您的物理电脑摄像头并编码推流至后端。</p>
            <video id="video" autoplay playsinline style="width: 640px; height: 480px; background: #000; border-radius: 12px; border: 2px solid #555;"></video>
            <canvas id="canvas" style="display:none;"></canvas>
            <script>
                const wsUrl = 'ws://' + location.hostname + ':3003/camera-uplink';
                const ws = new WebSocket(wsUrl);
                const video = document.getElementById('video');
                const canvas = document.getElementById('canvas');
                const ctx = canvas.getContext('2d');
                
                // 强制调用摄像头
                navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480, facingMode: "user" } })
                    .then(stream => { video.srcObject = stream; })
                    .catch(err => {
                        console.error('Camera error:', err);
                        alert('无法调用摄像头！请确保电脑有摄像头且您授予了权限。');
                    });
                
                ws.onopen = () => {
                    setInterval(() => {
                        if (video.videoWidth > 0 && ws.readyState === WebSocket.OPEN) {
                            canvas.width = video.videoWidth;
                            canvas.height = video.videoHeight;
                            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                            canvas.toBlob(blob => { ws.send(blob); }, 'image/jpeg', 0.6);
                        }
                    }, 1000 / 20); 
                };
            </script>
        </body>
        </html>
    `);
});

httpServer.listen(3003, '0.0.0.0', () => {
    console.log('🌟 [图传模拟] MJPEG 图传微服务已启动监听 0.0.0.0:3003 端口');
    console.log('================================================================');
    console.log('👉 [必须操作] 请立刻在您的 Mac 电脑浏览器中打开： http://localhost:3003');
    console.log('================================================================\n');
});
