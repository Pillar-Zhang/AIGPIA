Write-Host "=== 华为设备 UWB 支持检测 ===" -ForegroundColor Cyan
Write-Host ""

# 1. 检查设备连接
Write-Host "[1/6] 检查设备连接..." -ForegroundColor Yellow
$devices = & adb devices
if ($devices -match "device") {
    Write-Host "✓ 设备已连接" -ForegroundColor Green
} else {
    Write-Host "✗ 未检测到设备，请检查USB连接" -ForegroundColor Red
    Write-Host "提示：确保已开启USB调试并授权" -ForegroundColor Yellow
    exit
}

# 2. 获取设备信息
Write-Host "`n[2/6] 设备信息:" -ForegroundColor Yellow
$model = & adb shell getprop ro.product.model
$manufacturer = & adb shell getprop ro.product.manufacturer
$sdk = & adb shell getprop ro.build.version.sdk
$androidVer = & adb shell getprop ro.build.version.release

Write-Host "  型号: $model" -ForegroundColor White
Write-Host "  厂商: $manufacturer" -ForegroundColor White
Write-Host "  Android: $androidVer (SDK $sdk)" -ForegroundColor White

# 3. 检查 UWB 系统特性
Write-Host "`n[3/6] 检查 UWB 系统特性..." -ForegroundColor Yellow
$uwbFeatures = & adb shell pm list features | Select-String "uwb"
if ($uwbFeatures) {
    Write-Host "✓ 发现 UWB 特性:" -ForegroundColor Green
    $uwbFeatures | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
} else {
    Write-Host "✗ 未检测到标准 UWB 特性" -ForegroundColor Red
    Write-Host "  ℹ 华为可能使用自定义实现" -ForegroundColor Yellow
}

# 4. 检查 UWB 相关属性
Write-Host "`n[4/6] 检查 UWB 系统属性..." -ForegroundColor Yellow
$uwbProps = & adb shell getprop | Select-String "uwb"
if ($uwbProps) {
    Write-Host "✓ 发现 UWB 相关属性:" -ForegroundColor Green
    $uwbProps | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
} else {
    Write-Host "✗ 未发现 UWB 相关属性" -ForegroundColor Red
}

# 5. 检查内核模块
Write-Host "`n[5/6] 检查 UWB 内核模块..." -ForegroundColor Yellow
try {
    $uwbModules = & adb shell lsmod 2>$null | Select-String "uwb"
    if ($uwbModules) {
        Write-Host "✓ 发现 UWB 内核模块:" -ForegroundColor Green
        $uwbModules | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
    } else {
        Write-Host "⚠ 未发现 UWB 内核模块（可能集成在其他模块中）" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠ 无法检查内核模块（需要 root 权限）" -ForegroundColor Yellow
}

# 6. 检查 UWB 权限
Write-Host "`n[6/6] 检查 UWB 权限支持..." -ForegroundColor Yellow
$permissions = & adb shell pm list permissions | Select-String "uwb"
if ($permissions) {
    Write-Host "✓ 系统支持 UWB 权限:" -ForegroundColor Green
    $permissions | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
} else {
    Write-Host "✗ 系统未定义 UWB 权限" -ForegroundColor Red
}

Write-Host "`n=== 检测完成 ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "结论：" -ForegroundColor Yellow
if ($uwbFeatures -or $uwbProps) {
    Write-Host "✓ 该设备很可能支持 UWB" -ForegroundColor Green
} else {
    Write-Host "⚠ 未检测到标准 UWB 支持" -ForegroundColor Yellow
    Write-Host "  可能原因：" -ForegroundColor White
    Write-Host "  1. 华为使用自定义 UWB 实现" -ForegroundColor White
    Write-Host "  2. UWB 功能被系统隐藏" -ForegroundColor White
    Write-Host "  3. 该型号确实不支持 UWB" -ForegroundColor White
}
