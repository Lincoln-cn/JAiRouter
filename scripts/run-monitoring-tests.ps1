#!/usr/bin/env pwsh

# 监控系统集成测试和性能测试运行脚本
# 支持Windows PowerShell和跨平台PowerShell Core

param(
    [string]$TestType = "all",  # all, integration, performance, concurrency
    [switch]$SkipBuild = $false,
    [switch]$Verbose = $false,
    [string]$Profile = "integration-test"
)

Write-Host "=== JAiRouter 监控系统测试运行器 ===" -ForegroundColor Green
Write-Host "测试类型: $TestType" -ForegroundColor Yellow
Write-Host "跳过构建: $SkipBuild" -ForegroundColor Yellow
Write-Host "详细输出: $Verbose" -ForegroundColor Yellow

# 设置错误处理
$ErrorActionPreference = "Stop"

# 获取脚本目录和项目根目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# 切换到项目根目录
Set-Location $ProjectRoot

try {
    # 构建项目（除非跳过）
    if (-not $SkipBuild) {
        Write-Host "`n=== 构建项目 ===" -ForegroundColor Blue
        if ($IsWindows -or $env:OS -eq "Windows_NT") {
            & .\mvnw.cmd clean compile test-compile -q
        } else {
            & ./mvnw clean compile test-compile -q
        }
        
        if ($LASTEXITCODE -ne 0) {
            throw "项目构建失败"
        }
        Write-Host "项目构建成功" -ForegroundColor Green
    }

    # 设置测试参数
    $TestArgs = @(
        "compiler:compile",
        "compiler:testCompile", 
        "surefire:test",
        "-Dspring.profiles.active=$Profile",
        "-Dtest.monitoring.enabled=true"
    )

    if ($Verbose) {
        $TestArgs += "-X"
    } else {
        $TestArgs += "-q"
    }

    # 根据测试类型选择要运行的测试
    switch ($TestType.ToLower()) {
        "integration" {
            Write-Host "`n=== 运行集成测试 ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=PrometheusEndpointIntegrationTest,MonitoringDataFlowEndToEndTest"
        }
        "performance" {
            Write-Host "`n=== 运行性能测试 ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=MonitoringPerformanceBenchmarkTest"
        }
        "concurrency" {
            Write-Host "`n=== 运行并发稳定性测试 ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=MonitoringConcurrencyStabilityTest"
        }
        "prometheus" {
            Write-Host "`n=== 运行Prometheus端点测试 ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=PrometheusEndpointIntegrationTest"
        }
        "all" {
            Write-Host "`n=== 运行所有监控测试 ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=**/monitoring/*IntegrationTest,**/monitoring/*BenchmarkTest,**/monitoring/*StabilityTest"
        }
        default {
            Write-Host "`n=== 运行指定测试: $TestType ===" -ForegroundColor Blue
            $TestArgs += "-Dtest=$TestType"
        }
    }

    # 执行测试
    Write-Host "执行命令: mvnw $($TestArgs -join ' ')" -ForegroundColor Gray
    
    if ($IsWindows -or $env:OS -eq "Windows_NT") {
        & .\mvnw.cmd @TestArgs
    } else {
        & ./mvnw @TestArgs
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n=== 测试执行成功 ===" -ForegroundColor Green
        
        # 显示测试报告位置
        $ReportPath = Join-Path $ProjectRoot "target/surefire-reports"
        if (Test-Path $ReportPath) {
            Write-Host "测试报告位置: $ReportPath" -ForegroundColor Yellow
        }
        
        # 显示覆盖率报告位置（如果存在）
        $CoverageReport = Join-Path $ProjectRoot "target/site/jacoco/index.html"
        if (Test-Path $CoverageReport) {
            Write-Host "覆盖率报告: $CoverageReport" -ForegroundColor Yellow
        }
        
    } else {
        throw "测试执行失败，退出码: $LASTEXITCODE"
    }

} catch {
    Write-Host "`n=== 测试执行失败 ===" -ForegroundColor Red
    Write-Host "错误信息: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "`n=== 监控测试完成 ===" -ForegroundColor Green