# JAiRouter 文档管理统一脚本
# 整合了文档服务、链接检查、版本管理、结构验证等功能

param(
    [Parameter(Position=0)]
    [ValidateSet("serve", "check-links", "fix-links", "check-sync", "version", "validate", "help")]
    [string]$Command = "help",
    
    [string]$HostAddress = "localhost",
    [string]$Port = "8000",
    [string]$Output = "",
    [switch]$FailOnError,
    [switch]$AutoFix,
    [switch]$Apply,
    [switch]$Scan,
    [switch]$AddHeaders,
    [int]$Cleanup = 0,
    [string]$Export = "",
    [int]$CheckOutdated = 30
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

function Show-Help {
    Write-ColorOutput "JAiRouter 文档管理工具" "Green"
    Write-ColorOutput "========================" "Green"
    Write-Host ""
    Write-ColorOutput "用法: .\docs-manager.ps1 <命令> [选项]" "Cyan"
    Write-Host ""
    Write-ColorOutput "可用命令:" "Yellow"
    Write-Host "  serve          启动本地文档服务器"
    Write-Host "  check-links    检查文档链接有效性"
    Write-Host "  fix-links      修复无效链接"
    Write-Host "  check-sync     检查文档与代码同步性"
    Write-Host "  version        管理文档版本"
    Write-Host "  validate       验证文档结构和配置"
    Write-Host "  help           显示此帮助信息"
    Write-Host ""
    Write-ColorOutput "serve 命令选项:" "Yellow"
    Write-Host "  -HostAddress <地址>   监听地址 (默认: localhost)"
    Write-Host "  -Port <端口>   监听端口 (默认: 8000)"
    Write-Host ""
    Write-ColorOutput "check-links 命令选项:" "Yellow"
    Write-Host "  -Output <文件> 输出报告文件"
    Write-Host "  -FailOnError   发现问题时退出码为1"
    Write-Host ""
    Write-ColorOutput "fix-links 命令选项:" "Yellow"
    Write-Host "  -AutoFix       自动修复不询问确认"
    Write-Host "  -Apply         应用修复建议"
    Write-Host ""
    Write-ColorOutput "version 命令选项:" "Yellow"
    Write-Host "  -Scan          扫描并更新版本信息"
    Write-Host "  -AddHeaders    添加版本头信息"
    Write-Host "  -Cleanup <天数> 清理指定天数前的变更记录"
    Write-Host "  -Export <文件> 导出版本数据"
    Write-Host ""
    Write-ColorOutput "示例:" "Yellow"
    Write-Host "  .\docs-manager.ps1 serve -HostAddress 0.0.0.0 -Port 3000"
    Write-Host "  .\docs-manager.ps1 check-links -Output report.json"
    Write-Host "  .\docs-manager.ps1 fix-links -Apply -AutoFix"
    Write-Host "  .\docs-manager.ps1 version -Scan -AddHeaders"
}

function Start-DocsServer {
    Write-ColorOutput "🚀 启动本地文档服务器..." "Green"
    
    # 切换到项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    Push-Location $projectRoot
    
    try {
        # 检查 Python
        try {
            $pythonVersion = python --version 2>&1
            Write-ColorOutput "检测到 Python: $pythonVersion" "Blue"
        } catch {
            Write-ColorOutput "❌ 错误: 未找到 Python，请先安装 Python 3.x" "Red"
            exit 1
        }
        
        # 检查 requirements.txt
        if (-not (Test-Path "requirements.txt")) {
            Write-ColorOutput "❌ 错误: 未找到 requirements.txt 文件" "Red"
            exit 1
        }
    
    # 安装依赖
    Write-ColorOutput "📦 安装文档依赖..." "Yellow"
    pip install -r requirements.txt
    
    if ($LASTEXITCODE -ne 0) {
        Write-ColorOutput "❌ 错误: 依赖安装失败" "Red"
        exit 1
    }
    
        # 启动服务器
        Write-ColorOutput "🌐 启动文档服务器，监听地址: $HostAddress`:$Port" "Green"
        Write-ColorOutput "📖 访问地址: http://$HostAddress`:$Port" "Cyan"
        Write-ColorOutput "⏹️  按 Ctrl+C 停止服务器" "Yellow"
        
        mkdocs serve --dev-addr "$HostAddress`:$Port"
    }
    finally {
        Pop-Location
    }
}

function Invoke-LinkCheck {
    Write-ColorOutput "🔍 检查文档链接..." "Green"
    
    $scriptPath = Join-Path $PSScriptRoot "check-links.py"
    if (-not (Test-Path $scriptPath)) {
        Write-ColorOutput "❌ 错误: 链接检查脚本不存在" "Red"
        exit 1
    }
    
    # 切换到项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    Push-Location $projectRoot
    
    try {
        $args = @()
        if ($Output) {
            $args += "--output"
            $args += $Output
        }
        if ($FailOnError) {
            $args += "--fail-on-error"
        }
        
        python $scriptPath @args
    }
    finally {
        Pop-Location
    }
}

function Invoke-LinkFix {
    Write-ColorOutput "🔧 修复文档链接..." "Green"
    
    $scriptPath = Join-Path $PSScriptRoot "fix-links.py"
    if (-not (Test-Path $scriptPath)) {
        Write-ColorOutput "❌ 错误: 链接修复脚本不存在" "Red"
        exit 1
    }
    
    # 切换到项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    Push-Location $projectRoot
    
    try {
        $args = @()
        if ($AutoFix) {
            $args += "--auto"
        }
        if ($Apply) {
            $args += "--apply"
        }
        
        python $scriptPath @args
    }
    finally {
        Pop-Location
    }
}

function Invoke-SyncCheck {
    Write-ColorOutput "🔄 检查文档同步性..." "Green"
    
    $scriptPath = Join-Path $PSScriptRoot "check-docs-sync.ps1"
    if (-not (Test-Path $scriptPath)) {
        Write-ColorOutput "❌ 错误: 同步检查脚本不存在" "Red"
        exit 1
    }
    
    # 获取项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    
    $args = @("-ProjectRoot", $projectRoot)
    if ($Output) {
        $args += "-OutputFile"
        $args += $Output
    }
    if ($FailOnError) {
        $args += "-FailOnError"
    }
    
    & $scriptPath @args
}

function Invoke-VersionManagement {
    Write-ColorOutput "📋 管理文档版本..." "Green"
    
    $scriptPath = Join-Path $PSScriptRoot "docs-version-manager.ps1"
    if (-not (Test-Path $scriptPath)) {
        Write-ColorOutput "❌ 错误: 版本管理脚本不存在" "Red"
        exit 1
    }
    
    # 获取项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    
    $args = @("-ProjectRoot", $projectRoot)
    if ($Scan) {
        $args += "-Scan"
    }
    if ($AddHeaders) {
        $args += "-AddHeaders"
    }
    if ($Cleanup -gt 0) {
        $args += "-Cleanup"
        $args += $Cleanup
    }
    if ($Export) {
        $args += "-Export"
        $args += $Export
    }
    $args += "-CheckOutdated"
    $args += $CheckOutdated
    
    & $scriptPath @args
}

function Invoke-Validation {
    Write-ColorOutput "✅ 验证文档结构..." "Green"
    
    # 切换到项目根目录
    $projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    Push-Location $projectRoot
    
    try {
        # 验证 MkDocs 配置
        $configScript = Join-Path $PSScriptRoot "validate-docs-config.py"
        if (Test-Path $configScript) {
            Write-ColorOutput "📋 验证 MkDocs 配置..." "Cyan"
            python $configScript
        }
        
        # 验证文档结构
        $structureScript = Join-Path $PSScriptRoot "validate-structure.ps1"
        if (Test-Path $structureScript) {
            Write-ColorOutput "📁 验证文档结构..." "Cyan"
            & $structureScript
        }
    }
    finally {
        Pop-Location
    }
}

# 主执行逻辑
try {
    switch ($Command.ToLower()) {
        "serve" {
            Start-DocsServer
        }
        "check-links" {
            Invoke-LinkCheck
        }
        "fix-links" {
            Invoke-LinkFix
        }
        "check-sync" {
            Invoke-SyncCheck
        }
        "version" {
            Invoke-VersionManagement
        }
        "validate" {
            Invoke-Validation
        }
        "help" {
            Show-Help
        }
        default {
            Write-ColorOutput "❌ 未知命令: $Command" "Red"
            Show-Help
            exit 1
        }
    }
} catch {
    Write-ColorOutput "❌ 执行失败: $($_.Exception.Message)" "Red"
    exit 1
}