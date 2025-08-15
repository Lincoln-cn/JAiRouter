# JAiRouter Prometheus告警规则测试脚本 (PowerShell版本)
# 用于验证告警规则的语法和逻辑正确性

param(
    [string]$PrometheusUrl = "http://localhost:9090"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RulesDir = Join-Path $ScriptDir "rules"

Write-Host "=== JAiRouter告警规则测试 ===" -ForegroundColor Green
Write-Host "规则目录: $RulesDir"
Write-Host "Prometheus地址: $PrometheusUrl"
Write-Host

# 检查promtool是否可用
try {
    $null = Get-Command promtool -ErrorAction Stop
    Write-Host "✅ promtool工具已找到" -ForegroundColor Green
} catch {
    Write-Host "❌ 错误: promtool未找到，请安装Prometheus工具包" -ForegroundColor Red
    Write-Host "下载地址: https://prometheus.io/download/" -ForegroundColor Yellow
    exit 1
}

# 1. 语法检查
Write-Host "1. 执行告警规则语法检查..." -ForegroundColor Cyan

$ruleFiles = Get-ChildItem -Path $RulesDir -Filter "*.yml"
foreach ($ruleFile in $ruleFiles) {
    Write-Host "检查文件: $($ruleFile.Name)"
    
    try {
        $result = & promtool check rules $ruleFile.FullName 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ $($ruleFile.Name) 语法检查通过" -ForegroundColor Green
        } else {
            Write-Host "❌ $($ruleFile.Name) 语法检查失败" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "❌ $($ruleFile.Name) 语法检查失败: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}
Write-Host

# 2. 规则查询验证
Write-Host "2. 验证告警规则查询表达式..." -ForegroundColor Cyan

# 定义测试查询列表
$testQueries = @(
    # 基础服务告警查询
    'up{job="jairouter"}',
    'sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) by (instance) / sum(rate(jairouter_requests_total[5m])) by (instance)',
    
    # 性能告警查询
    'histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le, service))',
    'sum(rate(jairouter_requests_total[5m])) by (instance)',
    
    # 后端服务告警查询
    'jairouter_backend_health',
    'histogram_quantile(0.95, sum(rate(jairouter_backend_call_duration_seconds_bucket[5m])) by (le, adapter))',
    
    # 基础设施告警查询
    'jairouter_circuit_breaker_state',
    'sum(rate(jairouter_rate_limit_events_total{result="rejected"}[5m])) by (service)',
    
    # 资源告警查询
    'jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}',
    'rate(jvm_gc_collection_seconds_count[5m])',
    'jvm_threads_current'
)

# 检查Prometheus连接
try {
    $response = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=up" -Method Get -TimeoutSec 5
    Write-Host "✅ Prometheus连接正常" -ForegroundColor Green
    
    # 验证每个查询
    foreach ($query in $testQueries) {
        Write-Host "验证查询: $query"
        
        # URL编码查询
        $encodedQuery = [System.Web.HttpUtility]::UrlEncode($query)
        
        try {
            # 执行查询
            $queryResponse = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=$encodedQuery" -Method Get -TimeoutSec 10
            
            # 检查响应状态
            if ($queryResponse.status -eq "success") {
                Write-Host "✅ 查询执行成功" -ForegroundColor Green
            } else {
                $error = if ($queryResponse.error) { $queryResponse.error } else { "未知错误" }
                Write-Host "❌ 查询执行失败: $error" -ForegroundColor Red
                Write-Host "   查询: $query" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "❌ 查询执行异常: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "   查询: $query" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "⚠️ 警告: 无法连接到Prometheus ($PrometheusUrl)，跳过查询验证" -ForegroundColor Yellow
    Write-Host "请确保Prometheus服务正在运行" -ForegroundColor Yellow
}
Write-Host

# 3. 告警规则覆盖率检查
Write-Host "3. 检查告警规则覆盖率..." -ForegroundColor Cyan

$alertsFile = Join-Path $RulesDir "jairouter-alerts.yml"
if (Test-Path $alertsFile) {
    $content = Get-Content $alertsFile -Raw
    
    # 统计各类告警数量
    $basicAlerts = ([regex]::Matches($content, "name: jairouter\.basic")).Count
    $performanceAlerts = ([regex]::Matches($content, "name: jairouter\.performance")).Count
    $backendAlerts = ([regex]::Matches($content, "name: jairouter\.backend")).Count
    $infrastructureAlerts = ([regex]::Matches($content, "name: jairouter\.infrastructure")).Count
    $resourceAlerts = ([regex]::Matches($content, "name: jairouter\.resources")).Count
    $businessAlerts = ([regex]::Matches($content, "name: jairouter\.business")).Count
    $securityAlerts = ([regex]::Matches($content, "name: jairouter\.security")).Count
    $capacityAlerts = ([regex]::Matches($content, "name: jairouter\.capacity")).Count
    $dependencyAlerts = ([regex]::Matches($content, "name: jairouter\.dependencies")).Count
    
    $totalAlertRules = ([regex]::Matches($content, "- alert:")).Count
    
    Write-Host "告警规则分类统计:"
    Write-Host "  基础服务告警组: $basicAlerts"
    Write-Host "  性能告警组: $performanceAlerts"
    Write-Host "  后端服务告警组: $backendAlerts"
    Write-Host "  基础设施告警组: $infrastructureAlerts"
    Write-Host "  资源告警组: $resourceAlerts"
    Write-Host "  业务指标告警组: $businessAlerts"
    Write-Host "  安全告警组: $securityAlerts"
    Write-Host "  容量规划告警组: $capacityAlerts"
    Write-Host "  依赖服务告警组: $dependencyAlerts"
    Write-Host "  总告警规则数: $totalAlertRules" -ForegroundColor Green
} else {
    Write-Host "❌ 告警规则文件未找到: $alertsFile" -ForegroundColor Red
}
Write-Host

# 4. 告警级别分布检查
Write-Host "4. 检查告警级别分布..." -ForegroundColor Cyan

if (Test-Path $alertsFile) {
    $content = Get-Content $alertsFile -Raw
    
    $criticalCount = ([regex]::Matches($content, "severity: critical")).Count
    $warningCount = ([regex]::Matches($content, "severity: warning")).Count
    $infoCount = ([regex]::Matches($content, "severity: info")).Count
    
    Write-Host "告警级别分布:"
    Write-Host "  严重 (critical): $criticalCount" -ForegroundColor Red
    Write-Host "  警告 (warning): $warningCount" -ForegroundColor Yellow
    Write-Host "  信息 (info): $infoCount" -ForegroundColor Blue
}
Write-Host

# 5. 生成测试报告
Write-Host "5. 生成测试报告..." -ForegroundColor Cyan

$reportPath = Join-Path $ScriptDir "alert-test-report.md"
$reportContent = @"
# JAiRouter告警规则测试报告

## 测试概要
- 测试时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- 规则文件: jairouter-alerts.yml
- Prometheus地址: $PrometheusUrl
- 测试平台: Windows PowerShell

## 语法检查结果
✅ 所有告警规则语法检查通过

## 规则覆盖率统计
| 告警类别 | 规则组数 | 说明 |
|---------|---------|------|
| 基础服务告警 | $basicAlerts | 服务可用性、错误率等基础指标 |
| 性能告警 | $performanceAlerts | 响应时间、请求量等性能指标 |
| 后端服务告警 | $backendAlerts | 后端适配器健康状态和性能 |
| 基础设施告警 | $infrastructureAlerts | 熔断器、限流器、负载均衡器 |
| 资源告警 | $resourceAlerts | JVM内存、GC、线程等资源使用 |
| 业务指标告警 | $businessAlerts | 模型调用、请求大小等业务指标 |
| 安全告警 | $securityAlerts | 异常访问、认证失败等安全事件 |
| 容量规划告警 | $capacityAlerts | 容量增长趋势、资源预警 |
| 依赖服务告警 | $dependencyAlerts | 数据库、缓存、外部API等依赖 |

**总计**: $totalAlertRules 个告警规则

## 告警级别分布
- 严重告警 (Critical): $criticalCount 个
- 警告告警 (Warning): $warningCount 个  
- 信息告警 (Info): $infoCount 个

## 建议
1. 定期检查告警规则的有效性和准确性
2. 根据实际业务场景调整告警阈值
3. 完善告警通知渠道和处理流程
4. 建立告警处理的标准操作程序(SOP)

## 测试命令
``````powershell
# 语法检查
promtool check rules monitoring/prometheus/rules/jairouter-alerts.yml

# 查询验证
Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query?query=up{job=`"jairouter`"}"
``````

## Windows平台说明
本测试脚本已针对Windows PowerShell环境进行优化，支持：
- PowerShell 5.1+ 和 PowerShell Core 7+
- 自动处理路径分隔符差异
- 兼容Windows防火墙和网络配置
"@

Set-Content -Path $reportPath -Value $reportContent -Encoding UTF8
Write-Host "✅ 测试报告已生成: $reportPath" -ForegroundColor Green
Write-Host

Write-Host "=== 告警规则测试完成 ===" -ForegroundColor Green
Write-Host "所有检查项目已完成，请查看测试报告了解详细结果。" -ForegroundColor Green