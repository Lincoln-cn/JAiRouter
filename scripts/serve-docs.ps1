# PowerShell script for serving documentation locally
param(
    [string]$HostAddress = "localhost",
    [string]$Port = "8000"
)

Write-Host "启动本地文档服务器..." -ForegroundColor Green

# Check if Python is installed
try {
    $pythonVersion = python --version 2>&1
    Write-Host "检测到 Python: $pythonVersion" -ForegroundColor Blue
} catch {
    Write-Host "错误: 未找到 Python，请先安装 Python 3.x" -ForegroundColor Red
    exit 1
}

# Check if requirements.txt exists
if (-not (Test-Path "requirements.txt")) {
    Write-Host "错误: 未找到 requirements.txt 文件" -ForegroundColor Red
    exit 1
}

# Install dependencies
Write-Host "安装文档依赖..." -ForegroundColor Yellow
pip install -r requirements.txt

if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: 依赖安装失败" -ForegroundColor Red
    exit 1
}

# Serve documentation
Write-Host "启动文档服务器，监听地址: $HostAddress:$Port" -ForegroundColor Green
Write-Host "访问地址: http://$HostAddress:$Port" -ForegroundColor Cyan
Write-Host "按 Ctrl+C 停止服务器" -ForegroundColor Yellow

mkdocs serve --dev-addr "$HostAddress:$Port"