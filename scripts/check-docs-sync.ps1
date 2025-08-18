# 文档内容同步检查脚本 (PowerShell 版本)
# 检查文档与代码的同步性，验证配置示例和 API 文档的准确性

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "",
    [switch]$FailOnError = $false
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 检查结果枚举
enum CheckResult {
    PASS
    WARN
    FAIL
}

# 同步问题类
class SyncIssue {
    [string]$FilePath
    [string]$IssueType
    [string]$Description
    [CheckResult]$Severity
    [int]$LineNumber
    [string]$Suggestion
    
    SyncIssue([string]$filePath, [string]$issueType, [string]$description, [CheckResult]$severity) {
        $this.FilePath = $filePath
        $this.IssueType = $issueType
        $this.Description = $description
        $this.Severity = $severity
        $this.LineNumber = 0
        $this.Suggestion = ""
    }
}

# 文档同步检查器类
class DocumentSyncChecker {
    [string]$ProjectRoot
    [System.Collections.ArrayList]$Issues
    [hashtable]$ConfigData
    [hashtable]$PomData
    
    DocumentSyncChecker([string]$projectRoot) {
        $this.ProjectRoot = $projectRoot
        $this.Issues = New-Object System.Collections.ArrayList
        $this.ConfigData = @{}
        $this.PomData = @{}
    }
    
    [void]LoadProjectConfig() {
        try {
            # 加载 application.yml
            $appConfigPath = Join-Path $this.ProjectRoot "src\main\resources\application.yml"
            if (Test-Path $appConfigPath) {
                $yamlContent = Get-Content $appConfigPath -Raw -Encoding UTF8
                $this.ConfigData = ConvertFrom-Yaml $yamlContent
            }
            
            # 加载 pom.xml
            $pomPath = Join-Path $this.ProjectRoot "pom.xml"
            if (Test-Path $pomPath) {
                [xml]$pomXml = Get-Content $pomPath -Encoding UTF8
                $this.PomData = $this.ParsePomXml($pomXml)
            }
        }
        catch {
            $this.AddIssue("项目配置加载", "CONFIG_LOAD_ERROR", "无法加载项目配置: $($_.Exception.Message)", [CheckResult]::FAIL)
        }
    }
    
    [hashtable]ParsePomXml([xml]$pomXml) {
        $this.PomData = @{
            'groupId' = ''
            'artifactId' = ''
            'version' = ''
            'dependencies' = @()
            'plugins' = @()
            'profiles' = @()
        }
        
        # 提取基本信息
        if ($pomXml.project.groupId) {
            $this.PomData.groupId = $pomXml.project.groupId
        }
        if ($pomXml.project.artifactId) {
            $this.PomData.artifactId = $pomXml.project.artifactId
        }
        if ($pomXml.project.version) {
            $this.PomData.version = $pomXml.project.version
        }
        
        # 提取依赖
        if ($pomXml.project.dependencies.dependency) {
            foreach ($dep in $pomXml.project.dependencies.dependency) {
                $this.PomData.dependencies += @{
                    'groupId' = $dep.groupId
                    'artifactId' = $dep.artifactId
                    'version' = $dep.version
                }
            }
        }
        
        return $this.PomData
    }
    
    [void]CheckConfigurationDocs() {
        $configDocsPath = Join-Path $this.ProjectRoot "docs\zh\configuration"
        
        if (-not (Test-Path $configDocsPath)) {
            $this.AddIssue($configDocsPath, "MISSING_CONFIG_DOCS", "配置文档目录不存在", [CheckResult]::FAIL)
            return
        }
        
        $this.CheckApplicationConfigDoc()
        $this.CheckLoadBalancingConfigDoc()
        $this.CheckRateLimitingConfigDoc()
        $this.CheckCircuitBreakerConfigDoc()
    }
    
    [void]CheckApplicationConfigDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\configuration\application-config.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "应用配置文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查端口配置
        if ($content -match 'server\.port') {
            $actualPort = 8080
            if ($this.ConfigData.server -and $this.ConfigData.server.port) {
                $actualPort = $this.ConfigData.server.port
            }
            
            if ($content -notmatch $actualPort.ToString()) {
                $this.AddIssue($docPath, "CONFIG_MISMATCH", "文档中的端口配置与实际配置不符，实际端口: $actualPort", [CheckResult]::WARN)
            }
        }
        
        # 检查适配器配置
        if ($content -match 'adapter:') {
            $actualAdapter = "gpustack"
            if ($this.ConfigData.model -and $this.ConfigData.model.adapter) {
                $actualAdapter = $this.ConfigData.model.adapter
            }
            
            if ($content -notmatch $actualAdapter) {
                $this.AddIssue($docPath, "CONFIG_MISMATCH", "文档中缺少实际使用的适配器: $actualAdapter", [CheckResult]::WARN)
            }
        }
    }
    
    [void]CheckLoadBalancingConfigDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\configuration\load-balancing.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "负载均衡配置文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查负载均衡策略
        $supportedTypes = @('random', 'round-robin', 'least-connections', 'ip-hash')
        
        foreach ($lbType in $supportedTypes) {
            if ($content -notmatch $lbType) {
                $this.AddIssue($docPath, "INCOMPLETE_DOC", "文档中缺少负载均衡类型说明: $lbType", [CheckResult]::WARN)
            }
        }
    }
    
    [void]CheckRateLimitingConfigDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\configuration\rate-limiting.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "限流配置文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查限流算法
        $supportedAlgorithms = @('token-bucket', 'leaky-bucket', 'sliding-window', 'warm-up')
        
        foreach ($algorithm in $supportedAlgorithms) {
            if ($content -notmatch $algorithm) {
                $this.AddIssue($docPath, "INCOMPLETE_DOC", "文档中缺少限流算法说明: $algorithm", [CheckResult]::WARN)
            }
        }
    }
    
    [void]CheckCircuitBreakerConfigDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\configuration\circuit-breaker.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "熔断器配置文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查熔断器配置参数
        $requiredParams = @('failureThreshold', 'timeout', 'successThreshold')
        
        foreach ($param in $requiredParams) {
            if ($content -notmatch $param) {
                $this.AddIssue($docPath, "INCOMPLETE_DOC", "文档中缺少熔断器参数说明: $param", [CheckResult]::WARN)
            }
        }
    }
    
    [void]CheckApiDocumentation() {
        $apiDocsPath = Join-Path $this.ProjectRoot "docs\zh\api-reference"
        
        if (-not (Test-Path $apiDocsPath)) {
            $this.AddIssue($apiDocsPath, "MISSING_API_DOCS", "API 文档目录不存在", [CheckResult]::FAIL)
            return
        }
        
        $this.CheckUniversalApiDoc()
        $this.CheckManagementApiDoc()
    }
    
    [void]CheckUniversalApiDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\api-reference\universal-api.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "统一 API 文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查服务端点
        $expectedEndpoints = @(
            '/v1/chat/completions',
            '/v1/embeddings',
            '/v1/rerank',
            '/v1/audio/speech',
            '/v1/audio/transcriptions',
            '/v1/images/generations',
            '/v1/images/edits'
        )
        
        foreach ($endpoint in $expectedEndpoints) {
            if ($content -notmatch [regex]::Escape($endpoint)) {
                $this.AddIssue($docPath, "INCOMPLETE_API_DOC", "文档中缺少 API 端点说明: $endpoint", [CheckResult]::WARN)
            }
        }
    }
    
    [void]CheckManagementApiDoc() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\api-reference\management-api.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "管理 API 文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查 Actuator 端点
        if ($this.ConfigData.management -and $this.ConfigData.management.endpoints -and 
            $this.ConfigData.management.endpoints.web -and $this.ConfigData.management.endpoints.web.exposure -and
            $this.ConfigData.management.endpoints.web.exposure.include) {
            
            $exposedEndpoints = $this.ConfigData.management.endpoints.web.exposure.include -split ','
            
            foreach ($endpoint in $exposedEndpoints) {
                $endpoint = $endpoint.Trim()
                if ($content -notmatch $endpoint) {
                    $this.AddIssue($docPath, "INCOMPLETE_API_DOC", "文档中缺少管理端点说明: $endpoint", [CheckResult]::WARN)
                }
            }
        }
    }
    
    [void]CheckDockerDocumentation() {
        $docPath = Join-Path $this.ProjectRoot "docs\zh\deployment\docker.md"
        
        if (-not (Test-Path $docPath)) {
            $this.AddIssue($docPath, "MISSING_DOC", "Docker 部署文档不存在", [CheckResult]::FAIL)
            return
        }
        
        $content = Get-Content $docPath -Raw -Encoding UTF8
        
        # 检查 docker-compose 文件
        $composeFiles = @(
            "docker-compose.yml",
            "docker-compose.dev.yml",
            "docker-compose-monitoring.yml"
        )
        
        foreach ($composeFile in $composeFiles) {
            $composePath = Join-Path $this.ProjectRoot $composeFile
            if ((Test-Path $composePath) -and ($content -notmatch $composeFile)) {
                $this.AddIssue($docPath, "MISSING_COMPOSE_DOC", "文档中缺少 Docker Compose 文件说明: $composeFile", [CheckResult]::WARN)
            }
        }
    }
    
    [void]AddIssue([string]$filePath, [string]$issueType, [string]$description, [CheckResult]$severity) {
        $issue = [SyncIssue]::new($filePath, $issueType, $description, $severity)
        $this.Issues.Add($issue) | Out-Null
    }
    
    [bool]RunAllChecks() {
        Write-Host "🔍 开始文档内容同步检查..." -ForegroundColor Cyan
        
        # 加载项目配置
        $this.LoadProjectConfig()
        
        # 运行各项检查
        $this.CheckConfigurationDocs()
        $this.CheckApiDocumentation()
        $this.CheckDockerDocumentation()
        
        $failCount = ($this.Issues | Where-Object { $_.Severity -eq [CheckResult]::FAIL }).Count
        return $failCount -eq 0
    }
    
    [string]GenerateReport() {
        $report = @()
        $report += "# 文档内容同步检查报告`n"
        
        # 统计信息
        $totalIssues = $this.Issues.Count
        $failCount = ($this.Issues | Where-Object { $_.Severity -eq [CheckResult]::FAIL }).Count
        $warnCount = ($this.Issues | Where-Object { $_.Severity -eq [CheckResult]::WARN }).Count
        $passCount = ($this.Issues | Where-Object { $_.Severity -eq [CheckResult]::PASS }).Count
        
        $report += "## 检查统计`n"
        $report += "- 总问题数: $totalIssues"
        $report += "- 严重问题: $failCount"
        $report += "- 警告问题: $warnCount"
        $report += "- 通过检查: $passCount`n"
        
        if ($totalIssues -eq 0) {
            $report += "✅ 所有检查都通过了！`n"
            return ($report -join "`n")
        }
        
        # 按严重程度分组显示问题
        foreach ($severity in @([CheckResult]::FAIL, [CheckResult]::WARN)) {
            $severityIssues = $this.Issues | Where-Object { $_.Severity -eq $severity }
            if ($severityIssues.Count -eq 0) {
                continue
            }
            
            $severityName = if ($severity -eq [CheckResult]::FAIL) { "严重问题" } else { "警告问题" }
            $report += "## $severityName`n"
            
            foreach ($issue in $severityIssues) {
                $report += "### $($issue.IssueType)"
                $report += "**文件**: $($issue.FilePath)"
                $report += "**描述**: $($issue.Description)"
                if ($issue.LineNumber -gt 0) {
                    $report += "**行号**: $($issue.LineNumber)"
                }
                if ($issue.Suggestion) {
                    $report += "**建议**: $($issue.Suggestion)"
                }
                $report += ""
            }
        }
        
        return ($report -join "`n")
    }
}

# YAML 解析函数 (简化版本)
function ConvertFrom-Yaml {
    param([string]$YamlContent)
    
    # 这是一个简化的 YAML 解析器，仅用于基本配置解析
    # 在实际使用中，建议安装 powershell-yaml 模块
    
    $result = @{}
    $lines = $YamlContent -split "`n"
    $currentSection = $result
    $sectionStack = @()
    
    foreach ($line in $lines) {
        $line = $line.Trim()
        if ([string]::IsNullOrEmpty($line) -or $line.StartsWith('#')) {
            continue
        }
        
        if ($line -match '^(\s*)([^:]+):\s*(.*)$') {
            $indent = $matches[1].Length
            $key = $matches[2].Trim()
            $value = $matches[3].Trim()
            
            # 处理缩进层级
            while ($sectionStack.Count -gt 0 -and $sectionStack[-1].Indent -ge $indent) {
                $sectionStack.RemoveAt($sectionStack.Count - 1)
            }
            
            if ($sectionStack.Count -gt 0) {
                $currentSection = $sectionStack[-1].Section
            } else {
                $currentSection = $result
            }
            
            if ([string]::IsNullOrEmpty($value)) {
                # 这是一个新的节
                $newSection = @{}
                $currentSection[$key] = $newSection
                $sectionStack += @{ Section = $newSection; Indent = $indent }
            } else {
                # 这是一个键值对
                if ($value -match '^\d+$') {
                    $currentSection[$key] = [int]$value
                } elseif ($value -match '^(true|false)$') {
                    $currentSection[$key] = [bool]::Parse($value)
                } else {
                    $currentSection[$key] = $value.Trim('"', "'")
                }
            }
        }
    }
    
    return $result
}

# 主执行逻辑
try {
    # 创建检查器并运行检查
    $checker = [DocumentSyncChecker]::new($ProjectRoot)
    $success = $checker.RunAllChecks()
    
    # 生成报告
    $report = $checker.GenerateReport()
    
    if ($OutputFile) {
        $report | Out-File -FilePath $OutputFile -Encoding UTF8
        Write-Host "📄 报告已保存到: $OutputFile" -ForegroundColor Green
    } else {
        Write-Host $report
    }
    
    # 根据检查结果设置退出码
    if ($FailOnError -and -not $success) {
        Write-Host "❌ 发现严重问题，检查失败" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "✅ 文档同步检查完成" -ForegroundColor Green
        exit 0
    }
}
catch {
    Write-Host "❌ 检查过程中发生错误: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}