# UWB-BLE Gateway App — Xiaomi 14 Pro Ready

A production-ready Android starter for BLE + UWB + 4G/5G communication.

## ✅ Features
- BLE scanning & device discovery (Android 12+)
- UWB ranging (Android 13+ Tiramisu, Xiaomi 14 Pro supported)
- Retrofit + OkHttp with auth, retry & network change detection
- Local Express mock server (`npm run dev`) for quick testing

## 📱 Xiaomi UWB Setup Required
1. Enable **Developer Options** → **USB Debugging**
2. In **Settings > Connected devices > UWB**, turn ON & grant permissions
3. Register app in [Xiaomi Developer Portal](https://dev.mi.com/console/appservice) for UWB certificate (free)

## ▶️ How to Run
### Android App
- Open `UwbBleGatewayApp/` in **Android Studio Giraffe+**
- Select **Xiaomi 14 Pro (API 34)** or emulator with Play Store
- Click ▶️ Run

### Mock Server
```bash
$ cd UwbBleGatewayApp
$ npm install
$ npm run dev
```
→ Server runs on `http://localhost:3001`

## 📄 License
MIT — free to use, modify, ship.