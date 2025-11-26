# 测试集成审计功能脚本
# 验证JWT令牌和API Key操作的审计记录

$BaseUrl = "http://localhost:8080/api"

Write-Host "=== 测试集成审计功能 ===" -ForegroundColor Green
Write-Host "基础URL: $BaseUrl"
Write-Host

# 1. 测试JWT登录审计
Write-Host "1. 测试JWT登录审计..." -ForegroundColor Yellow
try {
    $loginBody = @{
        username = "admin"
        password = "admin123"
    } | ConvertTo-Json
    
    $loginResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/jwt/login" -Method POST -Body $loginBody -ContentType "application/json"
    Write-Host "登录成功" -ForegroundColor Green
    
    $jwtToken = $loginResponse.data.token
    if ($jwtToken) {
        Write-Host "JWT令牌获取成功: $($jwtToken.Substring(0, [Math]::Min(20, $jwtToken.Length)))..." -ForegroundColor Green
    }
} catch {
    Write-Host "JWT登录失败: $($_.Exception.Message)" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# 2. 测试JWT令牌刷新审计
if ($jwtToken) {
    Write-Host "2. 测试JWT令牌刷新审计..." -ForegroundColor Yellow
    try {
        $refreshBody = @{
            token = $jwtToken
        } | ConvertTo-Json
        
        $headers = @{
            "Jairouter_token" = $jwtToken
        }
        
        $refreshResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/jwt/refresh" -Method POST -Body $refreshBody -ContentType "application/json" -Headers $headers
        Write-Host "令牌刷新成功" -ForegroundColor Green
        
        $newJwtToken = $refreshResponse.data.token
        if ($newJwtToken) {
            Write-Host "新JWT令牌获取成功: $($newJwtToken.Substring(0, [Math]::Min(20, $newJwtToken.Length)))..." -ForegroundColor Green
            $jwtToken = $newJwtToken
        }
    } catch {
        Write-Host "JWT令牌刷新失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

# 3. 测试API Key使用审计
Write-Host "3. 测试API Key使用审计..." -ForegroundColor Yellow
try {
    $apiKey = "dev-admin-12345-abcde-67890-fghij"
    $headers = @{
        "X-API-Key" = $apiKey
    }
    
    $apiResponse = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/statistics/extended" -Method GET -Headers $headers
    Write-Host "API Key使用成功" -ForegroundColor Green
} catch {
    Write-Host "API Key使用失败: $($_.Exception.Message)" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# 4. 测试JWT令牌撤销审计
if ($jwtToken) {
    Write-Host "4. 测试JWT令牌撤销审计..." -ForegroundColor Yellow
    try {
        $revokeBody = @{
            token = $jwtToken
            reason = "测试撤销"
        } | ConvertTo-Json
        
        $headers = @{
            "Jairouter_token" = $jwtToken
        }
        
        $revokeResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/jwt/revoke" -Method POST -Body $revokeBody -ContentType "application/json" -Headers $headers
        Write-Host "令牌撤销成功" -ForegroundColor Green
    } catch {
        Write-Host "JWT令牌撤销失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

# 5. 测试认证失败审计
Write-Host "5. 测试认证失败审计..." -ForegroundColor Yellow
try {
    $failBody = @{
        username = "invalid_user"
        password = "wrong_password"
    } | ConvertTo-Json
    
    $failResponse = Invoke-RestMethod -Uri "$BaseUrl/auth/jwt/login" -Method POST -Body $failBody -ContentType "application/json"
} catch {
    Write-Host "认证失败测试完成（预期失败）" -ForegroundColor Green
}

Start-Sleep -Seconds 3

# 6. 查询审计事件验证
Write-Host "6. 查询审计事件验证..." -ForegroundColor Yellow

# 查询所有审计事件
try {
    $queryBody = @{
        page = 0
        size = 50
    } | ConvertTo-Json
    
    $allEvents = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/query" -Method POST -Body $queryBody -ContentType "application/json"
    Write-Host "所有审计事件数量: $($allEvents.data.totalElements)" -ForegroundColor Green
} catch {
    Write-Host "查询所有审计事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 查询JWT事件
try {
    $jwtEvents = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/jwt-tokens?page=0&size=20" -Method GET
    Write-Host "JWT审计事件数量: $($jwtEvents.data.totalElements)" -ForegroundColor Green
} catch {
    Write-Host "查询JWT审计事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 查询API Key事件
try {
    $apiKeyEvents = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/api-keys?page=0&size=20" -Method GET
    Write-Host "API Key审计事件数量: $($apiKeyEvents.data.totalElements)" -ForegroundColor Green
} catch {
    Write-Host "查询API Key审计事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 查询安全事件
try {
    $securityEvents = Invoke-RestMethod -Uri "$BaseUrl/security/audit/extended/security-events?page=0&size=20" -Method GET
    Write-Host "安全审计事件数量: $($securityEvents.data.totalElements)" -ForegroundColor Green
} catch {
    Write-Host "查询安全审计事件失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host
Write-Host "7. 检查审计日志文件..." -ForegroundColor Yellow

$auditLogDir = "logs/audit"
if (Test-Path $auditLogDir) {
    Write-Host "审计日志目录存在: $auditLogDir" -ForegroundColor Green
    Get-ChildItem $auditLogDir | Format-Table Name, Length, LastWriteTime
    
    # 显示最新的审计日志条目
    $auditLogFile = Join-Path $auditLogDir "security-audit.log"
    if (Test-Path $auditLogFile) {
        Write-Host
        Write-Host "最新的审计日志条目:" -ForegroundColor Cyan
        Get-Content $auditLogFile -Tail 10
    }
} else {
    Write-Host "审计日志目录不存在: $auditLogDir" -ForegroundColor Red
}

Write-Host
Write-Host "=== 集成审计功能测试完成 ===" -ForegroundColor Green
Write-Host
Write-Host "测试总结:" -ForegroundColor Cyan
Write-Host "- JWT登录审计: 已测试" -ForegroundColor White
Write-Host "- JWT令牌刷新审计: 已测试" -ForegroundColor White
Write-Host "- JWT令牌撤销审计: 已测试" -ForegroundColor White
Write-Host "- API Key使用审计: 已测试" -ForegroundColor White
Write-Host "- 认证失败审计: 已测试" -ForegroundColor White
Write-Host "- 审计事件查询: 已测试" -ForegroundColor White
Write-Host "- 审计日志文件: 已检查" -ForegroundColor White
Write-Host
Write-Host "请检查:" -ForegroundColor Yellow
Write-Host "1. 审计事件是否正确记录到数据库/内存" -ForegroundColor White
Write-Host "2. 审计日志文件是否正确生成" -ForegroundColor White
Write-Host "3. 前端审计日志管理页面是否显示数据" -ForegroundColor White