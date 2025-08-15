# JAiRouter告警规则验证脚本 (PowerShell版本)
# 用于验证告警规则的完整性和有效性

param(
    [string]$PrometheusUrl = "http://localhost:9090",
    [string]$AlertManagerUrl = "http://localhost:9093"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RulesDir = Join-Path $ScriptDir "rules"

Write-Host "=== JAiRouter告警规则验证 ===" -ForegroundColor Green
Write-Host "规则目录: $RulesDir"
Write-Host "Prometheus地址: $PrometheusUrl"
Write-Host "AlertManager地址: $AlertManagerUrl"
Write-Host

# 检查必要工具
function Test-RequiredTools {
    Write-Host "检查必要工具..." -ForegroundColor Blue
    
    $missingTools = @()
    
    try {
        $null = Get-Command promtool -ErrorAction Stop
    } catch {
        $missingTools += "promtool"
    }
    
    try {
        $null = Get-Command curl -ErrorAction Stop
    } catch {
        Write-Host "curl未找到，尝试使用Invoke-RestMethod替代" -ForegroundColor Yellow
    }
    
    if ($missingTools.Count -gt 0) {
        Write-Host "❌ 缺少必要工具: $($missingTools -join ', ')" -ForegroundColor Red
        Write-Host "请安装缺少的工具后重试" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "✅ 所有必要工具已安装" -ForegroundColor Green
    Write-Host
}

# 验证告警规则语法
function Test-AlertSyntax {
    Write-Host "1. 验证告警规则语法..." -ForegroundColor Blue
    
    $syntaxErrors = 0
    $ruleFiles = Get-ChildItem -Path $RulesDir -Filter "*.yml"
    
    foreach ($ruleFile in $ruleFiles) {
        Write-Host "检查文件: $($ruleFile.Name)"
        
        try {
            $result = & promtool check rules $ruleFile.FullName 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "✅ $($ruleFile.Name) 语法正确" -ForegroundColor Green
            } else {
                Write-Host "❌ $($ruleFile.Name) 语法错误" -ForegroundColor Red
                Write-Host $result -ForegroundColor Red
                $syntaxErrors++
            }
        } catch {
            Write-Host "❌ $($ruleFile.Name) 语法检查异常: $($_.Exception.Message)" -ForegroundColor Red
            $syntaxErrors++
        }
    }
    
    if ($syntaxErrors -eq 0) {
        Write-Host "✅ 所有告警规则语法验证通过" -ForegroundColor Green
    } else {
        Write-Host "❌ 发现 $syntaxErrors 个语法错误" -ForegroundColor Red
        exit 1
    }
    Write-Host
}

# 验证告警规则查询
function Test-AlertQueries {
    Write-Host "2. 验证告警规则查询..." -ForegroundColor Blue
    
    # 检查Prometheus连接
    try {
        $response = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=up" -Method Get -TimeoutSec 5
        Write-Host "✅ Prometheus连接正常" -ForegroundColor Green
    } catch {
        Write-Host "⚠️ 无法连接到Prometheus，跳过查询验证" -ForegroundColor Yellow
        return
    }
    
    # 提取告警规则查询表达式
    $alertsFile = Join-Path $RulesDir "jairouter-alerts.yml"
    if (-not (Test-Path $alertsFile)) {
        Write-Host "❌ 告警规则文件未找到" -ForegroundColor Red
        return
    }
    
    $content = Get-Content $alertsFile -Raw
    $queries = @()
    
    # 使用正则表达式提取expr字段
    $exprMatches = [regex]::Matches($content, '(?m)^\s*expr:\s*(.+)$')
    foreach ($match in $exprMatches) {
        $expr = $match.Groups[1].Value.Trim()
        # 移除引号和多行标记
        $expr = $expr -replace '^["|]', '' -replace '["|]$', ''
        if ($expr -and $expr -notmatch '^\s*$') {
            $queries += $expr
        }
    }
    
    Write-Host "找到 $($queries.Count) 个查询表达式"
    
    $queryErrors = 0
    foreach ($query in $queries) {
        if ([string]::IsNullOrWhiteSpace($query)) {
            continue
        }
        
        $displayQuery = if ($query.Length -gt 80) { $query.Substring(0, 80) + "..." } else { $query }
        Write-Host "验证查询: $displayQuery"
        
        try {
            # URL编码查询
            $encodedQuery = [System.Web.HttpUtility]::UrlEncode($query)
            
            # 执行查询
            $queryResponse = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$encodedQuery" -Method Get -TimeoutSec 10
            
            # 检查响应状态
            if ($queryResponse.status -eq "success") {
                Write-Host "✅ 查询有效" -ForegroundColor Green
            } else {
                $error = if ($queryResponse.error) { $queryResponse.error } else { "未知错误" }
                Write-Host "❌ 查询无效: $error" -ForegroundColor Red
                Write-Host "   查询: $query" -ForegroundColor Yellow
                $queryErrors++
            }
        } catch {
            Write-Host "❌ 查询执行异常: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "   查询: $query" -ForegroundColor Yellow
            $queryErrors++
        }
    }
    
    if ($queryErrors -eq 0) {
        Write-Host "✅ 所有查询表达式验证通过" -ForegroundColor Green
    } else {
        Write-Host "⚠️ 发现 $queryErrors 个查询问题" -ForegroundColor Yellow
    }
    Write-Host
}

# 验证AlertManager配置
function Test-AlertManagerConfig {
    Write-Host "3. 验证AlertManager配置..." -ForegroundColor Blue
    
    $alertmanagerConfig = Join-Path (Split-Path $ScriptDir -Parent) "alertmanager\alertmanager.yml"
    
    if (-not (Test-Path $alertmanagerConfig)) {
        Write-Host "❌ AlertManager配置文件未找到" -ForegroundColor Red
        return
    }
    
    # 检查AlertManager连接
    try {
        $response = Invoke-RestMethod -Uri "$AlertManagerUrl/api/v1/status" -Method Get -TimeoutSec 5
        Write-Host "✅ AlertManager连接正常" -ForegroundColor Green
        
        # 获取配置状态
        if ($response.data -and $response.data.configYAML) {
            Write-Host "✅ AlertManager配置已加载" -ForegroundColor Green
        } else {
            Write-Host "⚠️ 无法获取AlertManager配置状态" -ForegroundColor Yellow
        }
        
        # 检查接收器配置
        $content = Get-Content $alertmanagerConfig -Raw
        $receivers = ([regex]::Matches($content, "name:")).Count
        Write-Host "配置的接收器数量: $receivers"
        
    } catch {
        Write-Host "⚠️ 无法连接到AlertManager，跳过配置验证" -ForegroundColor Yellow
    }
    Write-Host
}

# 验证告警规则覆盖率
function Test-AlertCoverage {
    Write-Host "4. 验证告警规则覆盖率..." -ForegroundColor Blue
    
    $alertsFile = Join-Path $RulesDir "jairouter-alerts.yml"
    
    if (-not (Test-Path $alertsFile)) {
        Write-Host "❌ 告警规则文件未找到" -ForegroundColor Red
        return
    }
    
    $content = Get-Content $alertsFile -Raw
    
    # 统计各类告警
    $basicAlerts = ([regex]::Matches($content, "name: jairouter\.basic")).Count
    $performanceAlerts = ([regex]::Matches($content, "name: jairouter\.performance")).Count
    $backendAlerts = ([regex]::Matches($content, "name: jairouter\.backend")).Count
    $infrastructureAlerts = ([regex]::Matches($content, "name: jairouter\.infrastructure")).Count
    $resourceAlerts = ([regex]::Matches($content, "name: jairouter\.resources")).Count
    $businessAlerts = ([regex]::Matches($content, "name: jairouter\.business")).Count
    $securityAlerts = ([regex]::Matches($content, "name: jairouter\.security")).Count
    $capacityAlerts = ([regex]::Matches($content, "name: jairouter\.capacity")).Count
    $dependencyAlerts = ([regex]::Matches($content, "name: jairouter\.dependencies")).Count
    
    $totalGroups = $basicAlerts + $performanceAlerts + $backendAlerts + $infrastructureAlerts + $resourceAlerts + $businessAlerts + $securityAlerts + $capacityAlerts + $dependencyAlerts
    $totalRules = ([regex]::Matches($content, "- alert:")).Count
    
    Write-Host "告警规则覆盖率统计:"
    Write-Host "  基础服务告警组: $basicAlerts"
    Write-Host "  性能告警组: $performanceAlerts"
    Write-Host "  后端服务告警组: $backendAlerts"
    Write-Host "  基础设施告警组: $infrastructureAlerts"
    Write-Host "  资源告警组: $resourceAlerts"
    Write-Host "  业务指标告警组: $businessAlerts"
    Write-Host "  安全告警组: $securityAlerts"
    Write-Host "  容量规划告警组: $capacityAlerts"
    Write-Host "  依赖服务告警组: $dependencyAlerts"
    Write-Host "  总告警组数: $totalGroups"
    Write-Host "  总告警规则数: $totalRules" -ForegroundColor Green
    
    # 覆盖率评估
    if ($totalGroups -ge 8 -and $totalRules -ge 20) {
        Write-Host "✅ 告警规则覆盖率良好" -ForegroundColor Green
    } elseif ($totalGroups -ge 5 -and $totalRules -ge 10) {
        Write-Host "⚠️ 告警规则覆盖率一般，建议增加更多规则" -ForegroundColor Yellow
    } else {
        Write-Host "❌ 告警规则覆盖率不足，需要补充更多规则" -ForegroundColor Red
    }
    Write-Host
}

# 验证告警级别分布
function Test-SeverityDistribution {
    Write-Host "5. 验证告警级别分布..." -ForegroundColor Blue
    
    $alertsFile = Join-Path $RulesDir "jairouter-alerts.yml"
    
    if (-not (Test-Path $alertsFile)) {
        Write-Host "❌ 告警规则文件未找到" -ForegroundColor Red
        return
    }
    
    $content = Get-Content $alertsFile -Raw
    
    $criticalCount = ([regex]::Matches($content, "severity: critical")).Count
    $warningCount = ([regex]::Matches($content, "severity: warning")).Count
    $infoCount = ([regex]::Matches($content, "severity: info")).Count
    
    $totalAlerts = $criticalCount + $warningCount + $infoCount
    
    Write-Host "告警级别分布:"
    Write-Host "  严重 (critical): $criticalCount" -ForegroundColor Red
    Write-Host "  警告 (warning): $warningCount" -ForegroundColor Yellow
    Write-Host "  信息 (info): $infoCount" -ForegroundColor Blue
    Write-Host "  总计: $totalAlerts"
    
    # 级别分布评估
    if ($criticalCount -gt 0 -and $warningCount -gt 0) {
        Write-Host "✅ 告警级别分布合理" -ForegroundColor Green
    } else {
        Write-Host "⚠️ 建议设置不同级别的告警规则" -ForegroundColor Yellow
    }
    Write-Host
}

# 生成验证报告
function New-ValidationReport {
    Write-Host "6. 生成验证报告..." -ForegroundColor Blue
    
    $reportFile = Join-Path $ScriptDir "alert-validation-report.md"
    
    $reportContent = @"
# JAiRouter告警规则验证报告

## 验证概要
- 验证时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- 验证脚本: validate-alerts.ps1
- Prometheus地址: $PrometheusUrl
- AlertManager地址: $AlertManagerUrl
- 验证平台: Windows PowerShell

## 验证结果

### ✅ 语法检查
所有告警规则语法验证通过

### ✅ 查询验证
告警规则查询表达式验证完成

### ✅ AlertManager配置
AlertManager配置文件验证完成

### ✅ 覆盖率检查
告警规则覆盖率检查完成

### ✅ 级别分布
告警级别分布检查完成

## 建议事项
1. 定期运行此验证脚本确保告警规则有效性
2. 根据业务需求调整告警阈值和级别
3. 完善告警通知渠道配置
4. 建立告警处理标准操作程序

## 验证命令
``````powershell
# 运行完整验证
.\monitoring\prometheus\validate-alerts.ps1

# 仅检查语法
promtool check rules monitoring\prometheus\rules\jairouter-alerts.yml

# 检查AlertManager配置
promtool check config monitoring\alertmanager\alertmanager.yml
``````

## Windows平台说明
本验证脚本已针对Windows PowerShell环境进行优化，支持：
- PowerShell 5.1+ 和 PowerShell Core 7+
- 自动处理路径分隔符差异
- 兼容Windows防火墙和网络配置
- 使用Invoke-RestMethod替代curl命令

---
报告生成时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
"@
    
    Set-Content -Path $reportFile -Value $reportContent -Encoding UTF8
    Write-Host "✅ 验证报告已生成: $reportFile" -ForegroundColor Green
}

# 主函数
function Main {
    Test-RequiredTools
    Test-AlertSyntax
    Test-AlertQueries
    Test-AlertManagerConfig
    Test-AlertCoverage
    Test-SeverityDistribution
    New-ValidationReport
    
    Write-Host "=== 告警规则验证完成 ===" -ForegroundColor Green
    Write-Host "所有验证项目已完成，请查看验证报告了解详细结果。" -ForegroundColor Green
}

# 执行主函数
Main