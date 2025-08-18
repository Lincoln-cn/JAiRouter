# 文档结构验证脚本（不需要 Python）
# 用于验证文档目录结构和关键文件

Write-Host "=== JAiRouter 文档结构验证 ===" -ForegroundColor Green

# 检查必要的目录
$requiredDirs = @(
    "docs\zh",
    "docs\en",
    "docs\zh\getting-started",
    "docs\zh\configuration",
    "docs\zh\api-reference",
    "docs\zh\deployment",
    "docs\zh\monitoring",
    "docs\zh\development",
    "docs\zh\troubleshooting",
    "docs\zh\reference",
    "docs\en\getting-started",
    "docs\en\configuration",
    "docs\en\api-reference",
    "docs\en\deployment",
    "docs\en\monitoring",
    "docs\en\development",
    "docs\en\troubleshooting",
    "docs\en\reference"
)

Write-Host "检查目录结构..." -ForegroundColor Yellow
$missingDirs = @()
foreach ($dir in $requiredDirs) {
    if (Test-Path $dir) {
        Write-Host "✓ 目录存在: $dir" -ForegroundColor Green
    } else {
        Write-Host "✗ 缺少目录: $dir" -ForegroundColor Red
        $missingDirs += $dir
    }
}

# 检查关键文件
$requiredFiles = @(
    "mkdocs.yml",
    "docs\CNAME",
    "docs\zh\index.md",
    "docs\en\index.md",
    "docs\zh\deployment\github-pages.md",
    "docs\en\deployment\github-pages.md",
    "docs\zh\development\deployment-testing.md",
    "docs\en\development\deployment-testing.md"
)

Write-Host "`n检查关键文件..." -ForegroundColor Yellow
$missingFiles = @()
foreach ($file in $requiredFiles) {
    if (Test-Path $file) {
        Write-Host "✓ 文件存在: $file" -ForegroundColor Green
    } else {
        Write-Host "✗ 缺少文件: $file" -ForegroundColor Red
        $missingFiles += $file
    }
}

# 检查 GitHub Actions 工作流
$workflowFiles = @(
    ".github\workflows\docs.yml",
    ".github\workflows\deployment-test.yml"
)

Write-Host "`n检查 GitHub Actions 工作流..." -ForegroundColor Yellow
$missingWorkflows = @()
foreach ($workflow in $workflowFiles) {
    if (Test-Path $workflow) {
        Write-Host "✓ 工作流存在: $workflow" -ForegroundColor Green
    } else {
        Write-Host "✗ 缺少工作流: $workflow" -ForegroundColor Red
        $missingWorkflows += $workflow
    }
}

# 检查测试脚本
$testScripts = @(
    "scripts\test-deployment.ps1",
    "scripts\test-deployment.sh",
    "scripts\test-deployment.cmd"
)

Write-Host "`n检查测试脚本..." -ForegroundColor Yellow
$missingScripts = @()
foreach ($script in $testScripts) {
    if (Test-Path $script) {
        Write-Host "✓ 脚本存在: $script" -ForegroundColor Green
    } else {
        Write-Host "✗ 缺少脚本: $script" -ForegroundColor Red
        $missingScripts += $script
    }
}

# 生成报告
Write-Host "`n=== 验证报告 ===" -ForegroundColor Green

$totalChecks = $requiredDirs.Count + $requiredFiles.Count + $workflowFiles.Count + $testScripts.Count
$failedChecks = $missingDirs.Count + $missingFiles.Count + $missingWorkflows.Count + $missingScripts.Count
$passedChecks = $totalChecks - $failedChecks

Write-Host "总检查项: $totalChecks" -ForegroundColor Cyan
Write-Host "通过检查: $passedChecks" -ForegroundColor Green
Write-Host "失败检查: $failedChecks" -ForegroundColor $(if ($failedChecks -eq 0) { "Green" } else { "Red" })

if ($failedChecks -eq 0) {
    Write-Host "`n🎉 所有结构验证通过！文档结构完整。" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n❌ 发现 $failedChecks 个问题，请检查上述错误信息。" -ForegroundColor Red
    
    if ($missingDirs.Count -gt 0) {
        Write-Host "`n缺少的目录:" -ForegroundColor Yellow
        foreach ($dir in $missingDirs) {
            Write-Host "  - $dir" -ForegroundColor Red
        }
    }
    
    if ($missingFiles.Count -gt 0) {
        Write-Host "`n缺少的文件:" -ForegroundColor Yellow
        foreach ($file in $missingFiles) {
            Write-Host "  - $file" -ForegroundColor Red
        }
    }
    
    exit 1
}