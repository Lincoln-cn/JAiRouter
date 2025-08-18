# 测试文档管理脚本的基本功能
# 用于验证重构后的脚本是否正常工作

Write-Host "🧪 测试文档管理脚本..." -ForegroundColor Green

# 测试帮助命令
Write-Host "`n1. 测试帮助命令..." -ForegroundColor Yellow
try {
    & "$PSScriptRoot\docs-manager.ps1" help
    Write-Host "✅ 帮助命令测试通过" -ForegroundColor Green
} catch {
    Write-Host "❌ 帮助命令测试失败: $_" -ForegroundColor Red
}

# 测试验证命令
Write-Host "`n2. 测试验证命令..." -ForegroundColor Yellow
try {
    & "$PSScriptRoot\docs-manager.ps1" validate
    Write-Host "✅ 验证命令测试通过" -ForegroundColor Green
} catch {
    Write-Host "❌ 验证命令测试失败: $_" -ForegroundColor Red
}

# 测试链接检查命令（如果存在相关脚本）
Write-Host "`n3. 测试链接检查命令..." -ForegroundColor Yellow
if (Test-Path "$PSScriptRoot\check-links.py") {
    try {
        & "$PSScriptRoot\docs-manager.ps1" check-links -Output "test-report.json"
        if (Test-Path "test-report.json") {
            Remove-Item "test-report.json" -Force
            Write-Host "✅ 链接检查命令测试通过" -ForegroundColor Green
        } else {
            Write-Host "⚠️ 链接检查命令执行但未生成报告" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "❌ 链接检查命令测试失败: $_" -ForegroundColor Red
    }
} else {
    Write-Host "⏭️ 跳过链接检查测试（缺少 check-links.py）" -ForegroundColor Yellow
}

# 测试版本管理命令（如果存在相关脚本）
Write-Host "`n4. 测试版本管理命令..." -ForegroundColor Yellow
if (Test-Path "$PSScriptRoot\docs-version-manager.ps1") {
    try {
        & "$PSScriptRoot\docs-manager.ps1" version -Scan
        Write-Host "✅ 版本管理命令测试通过" -ForegroundColor Green
    } catch {
        Write-Host "❌ 版本管理命令测试失败: $_" -ForegroundColor Red
    }
} else {
    Write-Host "⏭️ 跳过版本管理测试（缺少 docs-version-manager.ps1）" -ForegroundColor Yellow
}

Write-Host "`n🎉 测试完成！" -ForegroundColor Green