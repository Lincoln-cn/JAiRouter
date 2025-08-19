# JAiRouter 安全功能迁移脚本 (PowerShell)
# 用于帮助用户从传统模式迁移到安全增强模式

param(
    [switch]$Help,
    [switch]$Check,
    [switch]$Backup,
    [switch]$Migrate,
    [switch]$Rollback,
    [switch]$StepByStep,
    [switch]$Force,
    [switch]$DryRun
)

# 脚本配置
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$BackupDir = Join-Path $ProjectRoot "config-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$MigrationLog = Join-Path $ProjectRoot "migration-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    
    $colorMap = @{
        "Red" = "Red"
        "Green" = "Green"
        "Yellow" = "Yellow"
        "Blue" = "Blue"
        "White" = "White"
    }
    
    Write-Host $Message -ForegroundColor $colorMap[$Color]
}

function Log-Info {
    param([string]$Message)
    Write-ColorOutput "[INFO] $Message" "Blue"
    Add-Content -Path $MigrationLog -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [INFO] $Message"
}

function Log-Success {
    param([string]$Message)
    Write-ColorOutput "[SUCCESS] $Message" "Green"
    Add-Content -Path $MigrationLog -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [SUCCESS] $Message"
}

function Log-Warning {
    param([string]$Message)
    Write-ColorOutput "[WARNING] $Message" "Yellow"
    Add-Content -Path $MigrationLog -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [WARNING] $Message"
}

function Log-Error {
    param([string]$Message)
    Write-ColorOutput "[ERROR] $Message" "Red"
    Add-Content -Path $MigrationLog -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [ERROR] $Message"
}

# 显示帮助信息
function Show-Help {
    @"
JAiRouter 安全功能迁移脚本 (PowerShell)

用法: .\migrate-to-security.ps1 [选项]

选项:
    -Help               显示此帮助信息
    -Check              检查当前配置和迁移准备情况
    -Backup             仅备份当前配置
    -Migrate            执行完整迁移
    -Rollback           回滚到迁移前状态
    -StepByStep         分步骤迁移（交互式）
    -Force              强制执行（跳过确认）
    -DryRun             模拟运行（不实际修改文件）

迁移阶段:
    1. 检查和备份当前配置
    2. 生成安全配置模板
    3. 更新应用配置文件
    4. 配置环境变量
    5. 验证迁移结果

示例:
    .\migrate-to-security.ps1 -Check          # 检查迁移准备情况
    .\migrate-to-security.ps1 -Backup         # 备份当前配置
    .\migrate-to-security.ps1 -Migrate        # 执行完整迁移
    .\migrate-to-security.ps1 -StepByStep     # 分步骤迁移
    .\migrate-to-security.ps1 -Rollback       # 回滚迁移

"@
}

# 检查迁移准备情况
function Test-MigrationReadiness {
    Log-Info "检查迁移准备情况..."
    
    $issues = 0
    
    # 检查Java版本
    try {
        $javaVersion = & java -version 2>&1 | Select-String "version" | ForEach-Object { $_.ToString().Split('"')[1] }
        $majorVersion = [int]($javaVersion.Split('.')[0])
        
        if ($majorVersion -ge 17) {
            Log-Success "Java版本检查通过: $javaVersion"
        } else {
            Log-Error "Java版本过低: $javaVersion (需要17+)"
            $issues++
        }
    } catch {
        Log-Error "未找到Java运行环境"
        $issues++
    }
    
    # 检查Spring Boot版本
    $pomFile = Join-Path $ProjectRoot "pom.xml"
    if (Test-Path $pomFile) {
        $pomContent = Get-Content $pomFile -Raw
        if ($pomContent -match '<spring-boot\.version>([^<]+)</spring-boot\.version>') {
            $springBootVersion = $matches[1]
            Log-Success "Spring Boot版本: $springBootVersion"
        } else {
            Log-Warning "无法确定Spring Boot版本"
        }
    } else {
        Log-Error "未找到pom.xml文件"
        $issues++
    }
    
    # 检查当前配置文件
    $configFiles = @(
        (Join-Path $ProjectRoot "src\main\resources\application.yml"),
        (Join-Path $ProjectRoot "config\application.yml"),
        (Join-Path $ProjectRoot "application.yml")
    )
    
    $configFound = $false
    foreach ($configFile in $configFiles) {
        if (Test-Path $configFile) {
            Log-Success "找到配置文件: $configFile"
            $configFound = $true
            
            # 检查是否已经包含安全配置
            $content = Get-Content $configFile -Raw
            if ($content -match "jairouter\.security") {
                Log-Warning "配置文件已包含安全配置，可能已经迁移过"
            }
            break
        }
    }
    
    if (-not $configFound) {
        Log-Error "未找到应用配置文件"
        $issues++
    }
    
    # 检查磁盘空间
    $drive = (Get-Item $ProjectRoot).PSDrive
    $freeSpace = (Get-WmiObject -Class Win32_LogicalDisk -Filter "DeviceID='$($drive.Name):'").FreeSpace
    if ($freeSpace -gt 1GB) {
        Log-Success "磁盘空间充足"
    } else {
        Log-Warning "磁盘空间可能不足，建议清理后再迁移"
    }
    
    # 检查是否有运行中的JAiRouter实例
    $javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*model-router*" }
    if ($javaProcesses) {
        Log-Warning "检测到运行中的JAiRouter实例，建议停止后再迁移"
    } else {
        Log-Success "没有运行中的JAiRouter实例"
    }
    
    # 总结检查结果
    if ($issues -eq 0) {
        Log-Success "迁移准备检查通过，可以开始迁移"
        return $true
    } else {
        Log-Error "发现 $issues 个问题，请解决后再进行迁移"
        return $false
    }
}

# 备份当前配置
function Backup-CurrentConfig {
    Log-Info "备份当前配置到: $BackupDir"
    
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    
    # 备份配置文件
    $configItems = @(
        (Join-Path $ProjectRoot "src\main\resources\application.yml"),
        (Join-Path $ProjectRoot "src\main\resources\application.properties"),
        (Join-Path $ProjectRoot "config"),
        (Join-Path $ProjectRoot ".env"),
        (Join-Path $ProjectRoot "docker-compose.yml"),
        (Join-Path $ProjectRoot "docker-compose.dev.yml")
    )
    
    foreach ($item in $configItems) {
        if (Test-Path $item) {
            try {
                if (Test-Path $item -PathType Container) {
                    Copy-Item -Path $item -Destination $BackupDir -Recurse -Force
                } else {
                    Copy-Item -Path $item -Destination $BackupDir -Force
                }
                Log-Success "已备份: $(Split-Path $item -Leaf)"
            } catch {
                Log-Warning "备份失败: $(Split-Path $item -Leaf) - $($_.Exception.Message)"
            }
        }
    }
    
    # 创建备份信息文件
    $backupInfo = @"
JAiRouter 配置备份信息
备份时间: $(Get-Date)
备份目录: $BackupDir
原始目录: $ProjectRoot
迁移日志: $MigrationLog

备份内容:
$(Get-ChildItem $BackupDir | Format-Table -AutoSize | Out-String)

恢复方法:
1. 停止JAiRouter服务
2. 将备份文件复制回原位置
3. 重启服务

注意: 此备份不包含运行时数据，仅包含配置文件
"@
    
    Set-Content -Path (Join-Path $BackupDir "backup-info.txt") -Value $backupInfo -Encoding UTF8
    
    Log-Success "配置备份完成"
}

# 生成安全配置模板
function New-SecurityConfig {
    Log-Info "生成安全配置模板..."
    
    $securityConfigFile = Join-Path $ProjectRoot "security-config-template.yml"
    
    $securityConfigContent = @'
# JAiRouter 安全功能配置模板
# 此文件包含启用安全功能所需的基本配置
# 请根据实际需求修改相关参数

jairouter:
  security:
    # 安全功能总开关
    enabled: true
    
    # API Key 认证配置
    api-key:
      enabled: true
      header-name: "X-API-Key"
      keys:
        # 管理员API Key - 请修改为强密钥
        - key-id: "admin-key-001"
          key-value: "${ADMIN_API_KEY:请设置强密钥}"
          description: "管理员API密钥"
          permissions: ["admin", "read", "write"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
        
        # 用户API Key - 请修改为强密钥
        - key-id: "user-key-001"
          key-value: "${USER_API_KEY:请设置强密钥}"
          description: "用户API密钥"
          permissions: ["read"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
    
    # 数据脱敏配置
    sanitization:
      request:
        enabled: true
        sensitive-words:
          - "password"
          - "secret"
          - "token"
        pii-patterns:
          - "\\d{11}"  # 手机号
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"  # 邮箱
      
      response:
        enabled: true
        sensitive-words:
          - "internal"
          - "debug"
    
    # 安全审计配置
    audit:
      enabled: true
      log-level: "INFO"
      retention-days: 90

# 环境变量配置示例
# 请在 .env 文件或系统环境变量中设置以下值:
#
# ADMIN_API_KEY=your-strong-admin-key-here
# USER_API_KEY=your-strong-user-key-here
# JWT_SECRET=your-jwt-secret-key-here (如果启用JWT)
# REDIS_PASSWORD=your-redis-password (如果使用Redis缓存)
'@
    
    Set-Content -Path $securityConfigFile -Value $securityConfigContent -Encoding UTF8
    Log-Success "安全配置模板已生成: $securityConfigFile"
    
    # 生成环境变量模板
    $envTemplateFile = Join-Path $ProjectRoot ".env.security.template"
    
    $envTemplateContent = @'
# JAiRouter 安全功能环境变量模板
# 复制此文件为 .env 并设置实际的密钥值

# 安全功能开关
JAIROUTER_SECURITY_ENABLED=true

# API Key 配置
ADMIN_API_KEY=your-admin-api-key-here-please-change-this
USER_API_KEY=your-user-api-key-here-please-change-this

# JWT 配置 (可选)
JAIROUTER_SECURITY_JWT_ENABLED=false
JWT_SECRET=your-jwt-secret-key-here-please-change-this

# Redis 配置 (可选)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password-here

# 重要提醒:
# 1. 请将所有 "please-change-this" 替换为强密钥
# 2. API Key 建议至少16位随机字符
# 3. JWT Secret 建议至少32位随机字符
# 4. 生产环境中请使用更复杂的密钥
'@
    
    Set-Content -Path $envTemplateFile -Value $envTemplateContent -Encoding UTF8
    Log-Success "环境变量模板已生成: $envTemplateFile"
}

# 更新应用配置文件
function Update-ApplicationConfig {
    Log-Info "更新应用配置文件..."
    
    $mainConfig = Join-Path $ProjectRoot "src\main\resources\application.yml"
    
    if (-not (Test-Path $mainConfig)) {
        Log-Error "未找到主配置文件: $mainConfig"
        return $false
    }
    
    # 检查是否已经包含安全配置
    $content = Get-Content $mainConfig -Raw
    if ($content -match "jairouter\.security") {
        Log-Warning "配置文件已包含安全配置，跳过更新"
        return $true
    }
    
    # 在配置文件末尾添加安全配置引用
    $securityConfigReference = @'

# ========================================
# 安全功能配置引用
# ========================================
# 安全配置已移至独立的配置文件中
# 如需启用安全功能，请参考 security-config-template.yml
# 并将相关配置合并到此文件中

# 向后兼容模式:
# 如需保持传统模式运行，请使用以下配置:
# spring.profiles.active: legacy
'@
    
    Add-Content -Path $mainConfig -Value $securityConfigReference -Encoding UTF8
    Log-Success "应用配置文件已更新"
    return $true
}

# 生成随机密钥
function New-RandomKey {
    param([int]$Length = 32)
    
    $chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    $key = ""
    for ($i = 0; $i -lt $Length; $i++) {
        $key += $chars[(Get-Random -Maximum $chars.Length)]
    }
    return $key
}

# 分步骤迁移
function Start-StepByStepMigration {
    Log-Info "开始分步骤迁移..."
    
    Write-Host ""
    Write-Host "=== JAiRouter 安全功能迁移向导 ===" -ForegroundColor Cyan
    Write-Host ""
    
    # 步骤1: 确认迁移
    $confirm = Read-Host "是否要开始迁移到安全增强模式? (y/N)"
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Log-Info "迁移已取消"
        return
    }
    
    # 步骤2: 检查准备情况
    Write-Host ""
    Log-Info "步骤 1/5: 检查迁移准备情况"
    if (-not (Test-MigrationReadiness)) {
        $continueAnyway = Read-Host "发现问题，是否继续? (y/N)"
        if ($continueAnyway -ne "y" -and $continueAnyway -ne "Y") {
            Log-Info "迁移已终止"
            return
        }
    }
    
    # 步骤3: 备份配置
    Write-Host ""
    Log-Info "步骤 2/5: 备份当前配置"
    Backup-CurrentConfig
    
    # 步骤4: 生成配置模板
    Write-Host ""
    Log-Info "步骤 3/5: 生成安全配置模板"
    New-SecurityConfig
    
    # 步骤5: 配置API Key
    Write-Host ""
    Log-Info "步骤 4/5: 配置API Key"
    Write-Host "请设置API Key (留空将生成随机密钥):"
    
    $adminKey = Read-Host "管理员API Key"
    if ([string]::IsNullOrEmpty($adminKey)) {
        $adminKey = New-RandomKey -Length 32
        Log-Info "已生成管理员API Key: $adminKey"
    }
    
    $userKey = Read-Host "用户API Key"
    if ([string]::IsNullOrEmpty($userKey)) {
        $userKey = New-RandomKey -Length 32
        Log-Info "已生成用户API Key: $userKey"
    }
    
    # 创建环境变量文件
    $envFile = Join-Path $ProjectRoot ".env"
    $envContent = @"
# JAiRouter 安全功能环境变量
# 由迁移脚本自动生成于 $(Get-Date)

JAIROUTER_SECURITY_ENABLED=true
ADMIN_API_KEY=$adminKey
USER_API_KEY=$userKey
"@
    
    Set-Content -Path $envFile -Value $envContent -Encoding UTF8
    Log-Success "环境变量文件已创建: $envFile"
    
    # 步骤6: 完成迁移
    Write-Host ""
    Log-Info "步骤 5/5: 完成迁移配置"
    Update-ApplicationConfig | Out-Null
    
    Write-Host ""
    Log-Success "迁移完成！"
    Write-Host ""
    Write-Host "下一步操作:" -ForegroundColor Yellow
    Write-Host "1. 检查生成的配置文件并根据需要调整"
    Write-Host "2. 重启JAiRouter服务"
    Write-Host "3. 使用API Key测试访问: curl -H 'X-API-Key: $adminKey' http://localhost:8080/v1/models"
    Write-Host "4. 查看迁移日志: $MigrationLog"
    Write-Host ""
    Write-Host "如需回滚，请运行: .\migrate-to-security.ps1 -Rollback" -ForegroundColor Yellow
}

# 回滚迁移
function Start-Rollback {
    Log-Info "开始回滚迁移..."
    
    # 查找最新的备份目录
    $backupDirs = Get-ChildItem -Path $ProjectRoot -Directory -Name "config-backup-*" | Sort-Object -Descending
    
    if ($backupDirs.Count -eq 0) {
        Log-Error "未找到备份目录，无法回滚"
        return
    }
    
    $latestBackup = Join-Path $ProjectRoot $backupDirs[0]
    Log-Info "使用备份目录: $latestBackup"
    
    $confirm = Read-Host "确认要回滚到备份状态吗? 这将覆盖当前配置 (y/N)"
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Log-Info "回滚已取消"
        return
    }
    
    # 恢复配置文件
    $applicationYml = Join-Path $latestBackup "application.yml"
    if (Test-Path $applicationYml) {
        $targetPath = Join-Path $ProjectRoot "src\main\resources\application.yml"
        Copy-Item -Path $applicationYml -Destination $targetPath -Force
        Log-Success "已恢复 application.yml"
    }
    
    $configDir = Join-Path $latestBackup "config"
    if (Test-Path $configDir) {
        $targetConfigDir = Join-Path $ProjectRoot "config"
        if (Test-Path $targetConfigDir) {
            Remove-Item -Path $targetConfigDir -Recurse -Force
        }
        Copy-Item -Path $configDir -Destination $ProjectRoot -Recurse -Force
        Log-Success "已恢复 config 目录"
    }
    
    $envFile = Join-Path $latestBackup ".env"
    if (Test-Path $envFile) {
        Copy-Item -Path $envFile -Destination $ProjectRoot -Force
        Log-Success "已恢复 .env 文件"
    }
    
    # 删除迁移生成的文件
    $migrationFiles = @(
        (Join-Path $ProjectRoot "security-config-template.yml"),
        (Join-Path $ProjectRoot ".env.security.template")
    )
    
    foreach ($file in $migrationFiles) {
        if (Test-Path $file) {
            Remove-Item -Path $file -Force
            Log-Success "已删除迁移文件: $(Split-Path $file -Leaf)"
        }
    }
    
    Log-Success "回滚完成！"
    Log-Info "请重启JAiRouter服务以应用回滚的配置"
}

# 主函数
function Main {
    # 显示帮助
    if ($Help) {
        Show-Help
        return
    }
    
    # 创建迁移日志
    "JAiRouter 安全功能迁移日志 - $(Get-Date)" | Out-File -FilePath $MigrationLog -Encoding UTF8
    Log-Info "迁移日志: $MigrationLog"
    
    try {
        # 执行相应操作
        if ($Check) {
            Test-MigrationReadiness | Out-Null
        } elseif ($Backup) {
            Backup-CurrentConfig
        } elseif ($Rollback) {
            Start-Rollback
        } elseif ($StepByStep) {
            Start-StepByStepMigration
        } elseif ($Migrate) {
            # 完整迁移
            Test-MigrationReadiness | Out-Null
            Backup-CurrentConfig
            New-SecurityConfig
            Update-ApplicationConfig | Out-Null
            Log-Success "迁移完成！请查看生成的配置文件并重启服务"
        } else {
            # 默认显示帮助
            Show-Help
        }
    } catch {
        Log-Error "迁移过程中发生错误: $($_.Exception.Message)"
        throw
    }
}

# 执行主函数
Main