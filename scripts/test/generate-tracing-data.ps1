# ========================================
# 追踪数据生成测试脚本 (PowerShell版本)
# ========================================
# 此脚本用于生成追踪数据，测试追踪概览页面的数据显示功能
# 
# 使用方法:
#   .\generate-tracing-data.ps1 [BaseUrl] [Count]
#
# 参数:
#   BaseUrl: 服务器地址，默认为 http://localhost:8080
#   Count: 生成请求数量，默认为 50
#
# 示例:
#   .\generate-tracing-data.ps1
#   .\generate-tracing-data.ps1 -BaseUrl "http://localhost:8080" -Count 100
#   .\generate-tracing-data.ps1 -BaseUrl "https://your-server.com" -Count 200

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Count = 50,
    [int]$ConcurrentRequests = 5,
    [switch]$Help
)

# 显示帮助信息
if ($Help) {
    Write-Host @"
追踪数据生成测试脚本 (PowerShell版本)

使用方法:
  .\generate-tracing-data.ps1 [参数]

参数:
  -BaseUrl <string>           服务器地址 (默认: http://localhost:8080)
  -Count <int>               生成请求数量 (默认: 50)
  -ConcurrentRequests <int>  并发请求数 (默认: 5)
  -Help                      显示此帮助信息

示例:
  .\generate-tracing-data.ps1
  .\generate-tracing-data.ps1 -BaseUrl "http://localhost:8080" -Count 100
  .\generate-tracing-data.ps1 -BaseUrl "https://your-server.com" -Count 200

功能:
  - 生成多种类型的API请求 (聊天、嵌入、模型列表等)
  - 模拟真实的用户行为模式
  - 随机生成错误请求 (约10%概率)
  - 控制并发数避免服务器过载
  - 验证生成的追踪数据

注意:
  - 确保目标服务器正在运行
  - 需要PowerShell 5.0或更高版本
"@
    exit 0
}

# 日志函数
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# 检查服务器连接
function Test-ServerConnection {
    param([string]$Url)
    
    Write-Info "检查服务器连接: $Url"
    
    try {
        $response = Invoke-WebRequest -Uri "$Url/actuator/health" -Method GET -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Success "服务器连接正常"
            return $true
        }
    }
    catch {
        Write-Error "无法连接到服务器: $Url"
        Write-Info "请确保服务器正在运行，或检查URL是否正确"
        Write-Info "错误详情: $($_.Exception.Message)"
        return $false
    }
    
    return $false
}

# 生成随机聊天请求
function New-ChatRequest {
    $modelNames = @("gpt-3.5-turbo", "gpt-4", "claude-3-sonnet", "gemini-pro")
    $userMessages = @(
        "Hello, how are you today?",
        "What is the weather like?",
        "Can you help me with programming?",
        "Tell me a joke",
        "Explain quantum computing",
        "What is machine learning?",
        "How to cook pasta?",
        "Recommend a good book",
        "What is the capital of France?",
        "How to learn a new language?"
    )
    
    $model = $modelNames | Get-Random
    $message = $userMessages | Get-Random
    
    return @{
        model = $model
        messages = @(
            @{
                role = "user"
                content = $message
            }
        )
        max_tokens = 100
        temperature = 0.7
    } | ConvertTo-Json -Depth 3
}

# 生成随机嵌入请求
function New-EmbeddingRequest {
    $modelNames = @("text-embedding-ada-002", "text-embedding-3-small", "text-embedding-3-large")
    $texts = @(
        "This is a sample text for embedding",
        "Machine learning is fascinating",
        "Natural language processing",
        "Artificial intelligence applications",
        "Deep learning algorithms"
    )
    
    $model = $modelNames | Get-Random
    $text = $texts | Get-Random
    
    return @{
        model = $model
        input = $text
    } | ConvertTo-Json -Depth 2
}

# 发送单个请求
function Send-Request {
    param(
        [string]$Endpoint,
        [string]$Data,
        [int]$RequestId,
        [string]$BaseUrl
    )
    
    $startTime = Get-Date
    
    # 随机决定是否模拟错误（10%概率）
    $simulateError = (Get-Random -Maximum 10) -eq 0
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-API-Key" = "dev-admin-12345-abcde-67890-fghij"
        "X-Request-ID" = "req-$RequestId-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
    }
    
    if ($simulateError) {
        $headers["X-Simulate-Error"] = "true"
    }
    
    try {
        $uri = "$BaseUrl$Endpoint"
        
        if ($Endpoint -eq "/v1/models" -or $Endpoint -eq "/actuator/health") {
            $response = Invoke-WebRequest -Uri $uri -Method GET -Headers $headers -TimeoutSec 30 -UseBasicParsing
        } else {
            $response = Invoke-WebRequest -Uri $uri -Method POST -Body $Data -Headers $headers -TimeoutSec 30 -UseBasicParsing
        }
        
        $endTime = Get-Date
        $duration = [math]::Round(($endTime - $startTime).TotalMilliseconds)
        
        $statusCode = $response.StatusCode
        
        if ($statusCode -ge 200 -and $statusCode -lt 300) {
            Write-Host "✓ Request $RequestId`: $Endpoint - $statusCode (${duration}ms)" -ForegroundColor Green
        } elseif ($statusCode -ge 400 -and $statusCode -lt 500) {
            Write-Host "⚠ Request $RequestId`: $Endpoint - $statusCode (${duration}ms) [Client Error]" -ForegroundColor Yellow
        } else {
            Write-Host "✗ Request $RequestId`: $Endpoint - $statusCode (${duration}ms) [Server Error]" -ForegroundColor Red
        }
        
        return @{ Success = $true; StatusCode = $statusCode; Duration = $duration }
    }
    catch {
        $endTime = Get-Date
        $duration = [math]::Round(($endTime - $startTime).TotalMilliseconds)
        
        Write-Host "? Request $RequestId`: $Endpoint - Connection failed (${duration}ms)" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor DarkRed
        
        return @{ Success = $false; StatusCode = 0; Duration = $duration }
    }
}

# 生成追踪数据
function Start-TracingDataGeneration {
    param(
        [string]$BaseUrl,
        [int]$RequestCount,
        [int]$ConcurrentRequests
    )
    
    Write-Info "开始生成追踪数据..."
    Write-Info "目标服务器: $BaseUrl"
    Write-Info "请求数量: $RequestCount"
    Write-Info "并发数: $ConcurrentRequests"
    
    $jobs = @()
    $results = @()
    
    for ($i = 1; $i -le $RequestCount; $i++) {
        # 随机选择API端点
        $endpointType = Get-Random -Maximum 4
        $endpoint = ""
        $data = ""
        
        switch ($endpointType) {
            0 {
                $endpoint = "/v1/chat/completions"
                $data = New-ChatRequest
            }
            1 {
                $endpoint = "/v1/embeddings"
                $data = New-EmbeddingRequest
            }
            2 {
                $endpoint = "/v1/models"
                $data = "{}"
            }
            3 {
                $endpoint = "/actuator/health"
                $data = "{}"
            }
        }
        
        # 创建后台作业
        $job = Start-Job -ScriptBlock {
            param($Endpoint, $Data, $RequestId, $BaseUrl, $SendRequestFunction)
            
            # 重新定义函数（因为作业中无法访问外部函数）
            function Send-Request {
                param(
                    [string]$Endpoint,
                    [string]$Data,
                    [int]$RequestId,
                    [string]$BaseUrl
                )
                
                $startTime = Get-Date
                
                $simulateError = (Get-Random -Maximum 10) -eq 0
                
                $headers = @{
                    "Content-Type" = "application/json"
                    "X-API-Key" = "dev-admin-12345-abcde-67890-fghij"
                    "X-Request-ID" = "req-$RequestId-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
                }
                
                if ($simulateError) {
                    $headers["X-Simulate-Error"] = "true"
                }
                
                try {
                    $uri = "$BaseUrl$Endpoint"
                    
                    if ($Endpoint -eq "/v1/models" -or $Endpoint -eq "/actuator/health") {
                        $response = Invoke-WebRequest -Uri $uri -Method GET -Headers $headers -TimeoutSec 30 -UseBasicParsing
                    } else {
                        $response = Invoke-WebRequest -Uri $uri -Method POST -Body $Data -Headers $headers -TimeoutSec 30 -UseBasicParsing
                    }
                    
                    $endTime = Get-Date
                    $duration = [math]::Round(($endTime - $startTime).TotalMilliseconds)
                    
                    return @{ 
                        RequestId = $RequestId
                        Endpoint = $Endpoint
                        Success = $true
                        StatusCode = $response.StatusCode
                        Duration = $duration
                    }
                }
                catch {
                    $endTime = Get-Date
                    $duration = [math]::Round(($endTime - $startTime).TotalMilliseconds)
                    
                    return @{ 
                        RequestId = $RequestId
                        Endpoint = $Endpoint
                        Success = $false
                        StatusCode = 0
                        Duration = $duration
                        Error = $_.Exception.Message
                    }
                }
            }
            
            Send-Request -Endpoint $Endpoint -Data $Data -RequestId $RequestId -BaseUrl $BaseUrl
        } -ArgumentList $endpoint, $data, $i, $BaseUrl
        
        $jobs += $job
        
        # 控制并发数
        if (($i % $ConcurrentRequests) -eq 0 -or $i -eq $RequestCount) {
            # 等待当前批次完成
            $batchResults = $jobs | Wait-Job | Receive-Job
            $jobs | Remove-Job
            $jobs = @()
            
            # 处理结果
            foreach ($result in $batchResults) {
                $results += $result
                
                if ($result.Success) {
                    if ($result.StatusCode -ge 200 -and $result.StatusCode -lt 300) {
                        Write-Host "✓ Request $($result.RequestId): $($result.Endpoint) - $($result.StatusCode) ($($result.Duration)ms)" -ForegroundColor Green
                    } elseif ($result.StatusCode -ge 400 -and $result.StatusCode -lt 500) {
                        Write-Host "⚠ Request $($result.RequestId): $($result.Endpoint) - $($result.StatusCode) ($($result.Duration)ms) [Client Error]" -ForegroundColor Yellow
                    } else {
                        Write-Host "✗ Request $($result.RequestId): $($result.Endpoint) - $($result.StatusCode) ($($result.Duration)ms) [Server Error]" -ForegroundColor Red
                    }
                } else {
                    Write-Host "? Request $($result.RequestId): $($result.Endpoint) - Connection failed ($($result.Duration)ms)" -ForegroundColor Red
                    if ($result.Error) {
                        Write-Host "  Error: $($result.Error)" -ForegroundColor DarkRed
                    }
                }
            }
            
            # 显示进度
            Write-Info "已发送 $i/$RequestCount 个请求..."
            
            # 随机延迟，模拟真实流量
            Start-Sleep -Milliseconds (Get-Random -Maximum 2000)
        }
    }
    
    Write-Success "追踪数据生成完成！"
    return $results
}

# 验证追踪数据
function Test-TracingData {
    param([string]$BaseUrl)
    
    Write-Info "验证追踪数据..."
    
    # 等待数据处理
    Start-Sleep -Seconds 2
    
    try {
        # 检查追踪统计
        $statsResponse = Invoke-WebRequest -Uri "$BaseUrl/api/tracing/query/statistics" -Method GET -UseBasicParsing
        $statsData = $statsResponse.Content | ConvertFrom-Json
        
        Write-Info "追踪统计结果:"
        Write-Info "  总追踪数: $($statsData.totalTraces)"
        Write-Info "  错误追踪数: $($statsData.errorTraces)"
        Write-Info "  平均耗时: $($statsData.avgDuration)ms"
        
        if ($statsData.totalTraces -gt 0) {
            Write-Success "追踪数据验证成功！"
        } else {
            Write-Warning "未检测到追踪数据，可能需要等待数据处理完成"
        }
    }
    catch {
        Write-Warning "无法获取追踪统计数据: $($_.Exception.Message)"
    }
    
    try {
        # 检查服务统计
        $servicesResponse = Invoke-WebRequest -Uri "$BaseUrl/api/tracing/query/services" -Method GET -UseBasicParsing
        $servicesData = $servicesResponse.Content | ConvertFrom-Json
        
        Write-Info "服务统计: 发现 $($servicesData.Count) 个服务"
        
        if ($servicesData.Count -gt 0) {
            Write-Info "服务列表:"
            foreach ($service in $servicesData) {
                Write-Info "  - $($service.name): $($service.traces) traces, $($service.errors) errors, $($service.avgDuration)ms avg"
            }
        }
    }
    catch {
        Write-Warning "无法获取服务统计数据: $($_.Exception.Message)"
    }
}

# 主函数
function Main {
    # 显示标题
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "        追踪数据生成测试脚本" -ForegroundColor Cyan
    Write-Host "        (PowerShell版本)" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host
    
    # 检查PowerShell版本
    if ($PSVersionTable.PSVersion.Major -lt 5) {
        Write-Error "需要PowerShell 5.0或更高版本"
        exit 1
    }
    
    # 检查服务器连接
    if (-not (Test-ServerConnection -Url $BaseUrl)) {
        exit 1
    }
    
    # 生成追踪数据
    $results = Start-TracingDataGeneration -BaseUrl $BaseUrl -RequestCount $Count -ConcurrentRequests $ConcurrentRequests
    
    # 验证数据
    Test-TracingData -BaseUrl $BaseUrl
    
    # 统计结果
    $successCount = ($results | Where-Object { $_.Success -and $_.StatusCode -ge 200 -and $_.StatusCode -lt 300 }).Count
    $errorCount = ($results | Where-Object { -not $_.Success -or $_.StatusCode -ge 400 }).Count
    
    Write-Host
    Write-Success "测试完成！"
    Write-Info "请求统计: 成功 $successCount, 失败 $errorCount"
    Write-Info "现在可以访问追踪概览页面查看生成的数据:"
    Write-Info "  $BaseUrl (前端页面)"
    Write-Info "  $BaseUrl/api/tracing/query/statistics (统计API)"
    Write-Info "  $BaseUrl/api/tracing/query/services (服务API)"
}

# 执行主函数
Main