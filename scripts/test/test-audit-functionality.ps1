# 测试审计日志功能脚本
# 用于验证审计日志管理页面的数据显示

$BaseUrl = "http://localhost:8080/api"

Write-Host "=== 测试审计日志功能 ===" -ForegroundColor Green
Write-Host "基础URL: $BaseUrl"
Write-Host

# 1. 生成测试审计数据
Write-Host "1. 生成测试审计数据..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/test-data/generate" -Method POST -ContentType "application/json"
    Write-Host "生成测试数据成功:" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 3
} catch {
    Write-Host "生成测试数据请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host
Write-Host "等待 2 秒让数据生成完成..."
Start-Sleep -Seconds 2

# 2. 查询所有审计事件
Write-Host
Write-Host "2. 查询所有审计事件..." -ForegroundColor Yellow
try {
    $body = @{
        page = 0
        size = 20
    } | ConvertTo-Json
    
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/query" -Method POST -Body $body -ContentType "application/json"
    Write-Host "查询审计事件成功，共找到 $($response.data.totalElements) 条记录:" -ForegroundColor Green
    $response.data.events | Select-Object -First 3 | ConvertTo-Json -Depth 2
} catch {
    Write-Host "查询审计事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. 查询JWT令牌审计事件
Write-Host
Write-Host "3. 查询JWT令牌审计事件..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/jwt-tokens?page=0&size=10" -Method GET
    Write-Host "查询JWT令牌事件成功，共找到 $($response.data.totalElements) 条记录:" -ForegroundColor Green
    $response.data.events | Select-Object -First 2 | ConvertTo-Json -Depth 2
} catch {
    Write-Host "查询JWT令牌事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. 查询API Key审计事件
Write-Host
Write-Host "4. 查询API Key审计事件..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/api-keys?page=0&size=10" -Method GET
    Write-Host "查询API Key事件成功，共找到 $($response.data.totalElements) 条记录:" -ForegroundColor Green
    $response.data.events | Select-Object -First 2 | ConvertTo-Json -Depth 2
} catch {
    Write-Host "查询API Key事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 5. 查询安全事件
Write-Host
Write-Host "5. 查询安全事件..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/security-events?page=0&size=10" -Method GET
    Write-Host "查询安全事件成功，共找到 $($response.data.totalElements) 条记录:" -ForegroundColor Green
    $response.data.events | Select-Object -First 2 | ConvertTo-Json -Depth 2
} catch {
    Write-Host "查询安全事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 6. 生成安全报告
Write-Host
Write-Host "6. 生成安全报告..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/reports/security" -Method GET
    Write-Host "生成安全报告成功:" -ForegroundColor Green
    $response.data | ConvertTo-Json -Depth 2
} catch {
    Write-Host "生成安全报告失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host
Write-Host "=== 测试完成 ===" -ForegroundColor Green
Write-Host "如果所有请求都成功，说明审计功能正常工作"
Write-Host "现在可以打开前端页面测试数据显示"
Write-Host "Swagger UI: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan