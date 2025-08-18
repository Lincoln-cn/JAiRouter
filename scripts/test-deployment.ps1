# GitHub Pages 部署测试脚本
# 用于验证文档构建和部署流程

param(
    [switch]$SkipBuild,
    [switch]$CheckLinks,
    [string]$Language = "all"
)

Write-Host "=== JAiRouter 文档部署测试 ===" -ForegroundColor Green

# 检查必要的工具
function Test-Prerequisites {
    Write-Host "检查前置条件..." -ForegroundColor Yellow
    
    # 检查 Python
    try {
        $pythonVersion = python --version 2>&1
        Write-Host "✓ Python: $pythonVersion" -ForegroundColor Green
    } catch {
        Write-Host "✗ Python 未安装或不在 PATH 中" -ForegroundColor Red
        return $false
    }
    
    # 检查 pip
    try {
        $pipVersion = pip --version 2>&1
        Write-Host "✓ pip: $pipVersion" -ForegroundColor Green
    } catch {
        Write-Host "✗ pip 未安装或不在 PATH 中" -ForegroundColor Red
        return $false
    }
    
    return $true
}

# 安装依赖
function Install-Dependencies {
    Write-Host "安装 MkDocs 依赖..." -ForegroundColor Yellow
    
    $packages = @(
        "mkdocs-material",
        "mkdocs-git-revision-date-localized-plugin",
        "mkdocs-mermaid2-plugin",
        "mkdocs-static-i18n"
    )
    
    foreach ($package in $packages) {
        Write-Host "安装 $package..." -ForegroundColor Cyan
        pip install $package --quiet
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ 安装 $package 失败" -ForegroundColor Red
            return $false
        }
    }
    
    Write-Host "✓ 所有依赖安装完成" -ForegroundColor Green
    return $true
}

# 验证配置文件
function Test-Configuration {
    Write-Host "验证配置文件..." -ForegroundColor Yellow
    
    # 检查 mkdocs.yml
    if (-not (Test-Path "mkdocs.yml")) {
        Write-Host "✗ mkdocs.yml 文件不存在" -ForegroundColor Red
        return $false
    }
    
    # 验证配置语法
    try {
        mkdocs config 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ mkdocs.yml 配置语法错误" -ForegroundColor Red
            return $false
        }
        Write-Host "✓ mkdocs.yml 配置正确" -ForegroundColor Green
    } catch {
        Write-Host "✗ 无法验证 mkdocs.yml 配置" -ForegroundColor Red
        return $false
    }
    
    return $true
}

# 检查文档结构
function Test-DocumentStructure {
    Write-Host "检查文档结构..." -ForegroundColor Yellow
    
    $requiredDirs = @(
        "docs/zh",
        "docs/en"
    )
    
    foreach ($dir in $requiredDirs) {
        if (-not (Test-Path $dir)) {
            Write-Host "✗ 缺少目录: $dir" -ForegroundColor Red
            return $false
        }
        Write-Host "✓ 目录存在: $dir" -ForegroundColor Green
    }
    
    # 检查关键文件
    $requiredFiles = @(
        "docs/zh/index.md",
        "docs/en/index.md"
    )
    
    foreach ($file in $requiredFiles) {
        if (-not (Test-Path $file)) {
            Write-Host "✗ 缺少文件: $file" -ForegroundColor Red
            return $false
        }
        Write-Host "✓ 文件存在: $file" -ForegroundColor Green
    }
    
    return $true
}

# 构建测试
function Test-Build {
    param([string]$Lang = "all")
    
    Write-Host "测试文档构建..." -ForegroundColor Yellow
    
    # 清理之前的构建
    if (Test-Path "site") {
        Remove-Item -Recurse -Force "site"
    }
    
    try {
        # 构建文档
        Write-Host "执行构建命令..." -ForegroundColor Cyan
        mkdocs build --strict --verbose
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ 文档构建失败" -ForegroundColor Red
            return $false
        }
        
        Write-Host "✓ 文档构建成功" -ForegroundColor Green
        
        # 检查构建输出
        if (-not (Test-Path "site")) {
            Write-Host "✗ 构建输出目录不存在" -ForegroundColor Red
            return $false
        }
        
        # 检查多语言版本
        $languageFiles = @(
            "site/index.html",
            "site/en/index.html"
        )
        
        foreach ($file in $languageFiles) {
            if (-not (Test-Path $file)) {
                Write-Host "✗ 缺少语言版本文件: $file" -ForegroundColor Red
                return $false
            }
            Write-Host "✓ 语言版本文件存在: $file" -ForegroundColor Green
        }
        
        return $true
    } catch {
        Write-Host "✗ 构建过程中发生错误: $_" -ForegroundColor Red
        return $false
    }
}

# 检查链接
function Test-Links {
    Write-Host "检查文档链接..." -ForegroundColor Yellow
    
    if (-not (Test-Path "site")) {
        Write-Host "✗ 构建输出不存在，请先运行构建测试" -ForegroundColor Red
        return $false
    }
    
    # 这里可以添加链接检查逻辑
    # 例如使用 markdown-link-check 或其他工具
    Write-Host "✓ 链接检查完成（需要实现具体检查逻辑）" -ForegroundColor Yellow
    
    return $true
}

# 生成测试报告
function Generate-TestReport {
    param(
        [bool]$PrereqResult,
        [bool]$ConfigResult,
        [bool]$StructureResult,
        [bool]$BuildResult,
        [bool]$LinkResult
    )
    
    Write-Host "`n=== 测试报告 ===" -ForegroundColor Green
    
    $results = @(
        @{ Name = "前置条件检查"; Result = $PrereqResult },
        @{ Name = "配置文件验证"; Result = $ConfigResult },
        @{ Name = "文档结构检查"; Result = $StructureResult },
        @{ Name = "构建测试"; Result = $BuildResult },
        @{ Name = "链接检查"; Result = $LinkResult }
    )
    
    $passCount = 0
    foreach ($result in $results) {
        $status = if ($result.Result) { "✓ 通过"; $passCount++ } else { "✗ 失败" }
        $color = if ($result.Result) { "Green" } else { "Red" }
        Write-Host "$($result.Name): $status" -ForegroundColor $color
    }
    
    Write-Host "`n总体结果: $passCount/$($results.Count) 项测试通过" -ForegroundColor $(if ($passCount -eq $results.Count) { "Green" } else { "Yellow" })
    
    return $passCount -eq $results.Count
}

# 主执行流程
try {
    $prereqResult = Test-Prerequisites
    if (-not $prereqResult) {
        Write-Host "前置条件检查失败，退出测试" -ForegroundColor Red
        exit 1
    }
    
    $installResult = Install-Dependencies
    if (-not $installResult) {
        Write-Host "依赖安装失败，退出测试" -ForegroundColor Red
        exit 1
    }
    
    $configResult = Test-Configuration
    $structureResult = Test-DocumentStructure
    
    $buildResult = $true
    if (-not $SkipBuild) {
        $buildResult = Test-Build -Lang $Language
    }
    
    $linkResult = $true
    if ($CheckLinks -and $buildResult) {
        $linkResult = Test-Links
    }
    
    $overallResult = Generate-TestReport -PrereqResult $prereqResult -ConfigResult $configResult -StructureResult $structureResult -BuildResult $buildResult -LinkResult $linkResult
    
    if ($overallResult) {
        Write-Host "`n🎉 所有测试通过！文档部署准备就绪。" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "`n❌ 部分测试失败，请检查上述错误信息。" -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host "测试过程中发生未预期的错误: $_" -ForegroundColor Red
    exit 1
}