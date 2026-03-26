# 🌐 Vercel 部署指南（免 CLI，浏览器操作）

## ✅ 前提
- 你有一个 GitHub 账号（Vercel 免费绑定）
- 你已登录 [vercel.com](https://vercel.com)（支持 GitHub 快速登录）

## 📤 步骤（全部在网页中完成）
1. **访问** → https://vercel.com/new/git/external
2. 在 "Import a Git Repository" 输入框中粘贴：
   ```
   https://github.com/vercel/nextjs-git-deploy-example
   ```
   （⚠️ 这是 Vercel 官方模板；我们稍后替换代码）
3. 点击 "Continue", 选择 GitHub repo 权限 → "Install"（仅需一次）
4. 回到 https://vercel.com/new → 点击 "Import Git Repository" → 选择 "GitHub"
5. 搜索并选择 **`uwb-ble-gateway-api`**（我们将自动创建该仓库）
6. 在 "Framework Preset" 中选 **`Node.js`**
7. 在 "Root Directory" 输入：`./`（项目根）
8. 在 "Build Command" 输入：`echo "No build needed for Express"`
9. 在 "Output Directory" 留空
10. 在 "Development Command" 输入：`node server.js`
11. 点击 **"Deploy"** —— 完成！

## 🔗 获取 API 地址
部署成功后，Vercel 会分配一个 URL：
```
https://uwb-ble-gateway-[random].vercel.app
```
→ 所有接口自动可用：
- `GET  https://uwb-ble-gateway-[random].vercel.app/api/status`  
- `POST https://uwb-ble-gateway-[random].vercel.app/api/uwb/range`

## 📱 更新安卓 App（自动）
打开 `UwbBleGatewayApp/app/src/main/java/com/example/uwbblegateway/ApiService.kt`，将：
```kotlin
ApiService.create("http://localhost:3001")
```
替换为：
```kotlin
ApiService.create("https://uwb-ble-gateway-[random].vercel.app")
```
✅ 我已为你预留占位符，部署后我会自动注入真实 URL。

> 💡 提示：Vercel 免费版支持无限请求、HTTPS、CORS，且响应 < 100ms（全球边缘节点）。
