#!/usr/bin/env pwsh
# 文档链接检查脚本 (PowerShell 版本)
# 检查文档中的所有链接，生成详细的检查报告

param(
    [string]$DocsDir = "docs",
    [string]$OutputFile = "link-check-report.json",
    [switch]$FailOnError
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 忽略的链接模式
$IgnorePatterns = @(
    '^mailto:',
    '^tel:',
    '^#',
    '^javascript:',
    '^http://localhost',
    '^https://localhost',
    '^http://127\.0\.0\.1',
    '^https://127\.0\.0\.1',
    '\.(jpg|jpeg|png|gif|svg|ico|pdf)$'
)

# 全局变量
$CheckedUrls = @{}
$Results = @{
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    summary = @{
        total_files = 0
        total_links = 0
        valid_links = 0
        invalid_links = 0
        skipped_links = 0
    }
    files = @()
    invalid_links = @()
    skipped_patterns = $IgnorePatterns
}

function Should-IgnoreLink {
    param([string]$Url)
    
    foreach ($pattern in $IgnorePatterns) {
        if ($Url -match $pattern) {
            return $true
        }
    }
    return $false
}

function Extract-LinksFromFile {
    param([string]$FilePath)
    
    $links = @()
    
    try {
        $content = Get-Content -Path $FilePath -Raw -Encoding UTF8
        
        # 匹配 Markdown 链接格式 [text](url)
        $markdownMatches = [regex]::Matches($content, '\[([^\]]*)\]\(([^)]+)\)')
        foreach ($match in $markdownMatches) {
            $url = $match.Groups[2].Value
            $lineNum = ($content.Substring(0, $match.Index) -split "`n").Count
            $links += @{ url = $url; line = $lineNum }
        }
        
        # 匹配 HTML 链接格式 <a href="url">
        $htmlMatches = [regex]::Matches($content, '<a[^>]+href=["\']([^"\']+)["\'][^>]*>', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        foreach ($match in $htmlMatches) {
            $url = $match.Groups[1].Value
            $lineNum = ($content.Substring(0, $match.Index) -split "`n").Count
            $links += @{ url = $url; line = $lineNum }
        }
        
        # 匹配直接的 URL
        $urlMatches = [regex]::Matches($content, 'https?://[^\s<>"''`\[\](){}]+')
        foreach ($match in $urlMatches) {
            $url = $match.Value
            $lineNum = ($content.Substring(0, $match.Index) -split "`n").Count
            # 避免重复添加已经在 Markdown 或 HTML 链接中的 URL
            $alreadyExists = $false
            foreach ($existingLink in $links) {
                if ($existingLink.url -eq $url) {
                    $alreadyExists = $true
                    break
                }
            }
            if (-not $alreadyExists) {
                $links += @{ url = $url; line = $lineNum }
            }
        }
        
    } catch {
        Write-Host "❌ 读取文件失败 $FilePath`: $_" -ForegroundColor Red
    }
    
    return $links
}

function Test-InternalLink {
    param([string]$Url, [string]$CurrentFile)
    
    try {
        $currentDir = Split-Path -Parent $CurrentFile
        
        # 处理相对路径
        if ($Url.StartsWith('./') -or $Url.StartsWith('../') -or (-not ($Url.StartsWith('http://') -or $Url.StartsWith('https://') -or $Url.StartsWith('/')))) {
            # 相对于当前文件的路径
            $targetPath = Join-Path $currentDir $Url
        } elseif ($Url.StartsWith('/')) {
            # 相对于文档根目录的路径
            $targetPath = Join-Path $DocsDir $Url.TrimStart('/')
        } else {
            return $true  # 不是内部链接
        }
        
        # 移除锚点
        if ($targetPath.Contains('#')) {
            $targetPath = $targetPath.Split('#')[0]
        }
        
        # 规范化路径
        $targetPath = [System.IO.Path]::GetFullPath($targetPath)
        
        # 检查文件是否存在
        if (Test-Path $targetPath -PathType Leaf) {
            return $true
        }
        
        # 如果是目录，检查是否有 index.md
        if (Test-Path $targetPath -PathType Container) {
            $indexFile = Join-Path $targetPath "index.md"
            return Test-Path $indexFile
        }
        
        # 尝试添加 .md 扩展名
        if (-not [System.IO.Path]::HasExtension($targetPath)) {
            $mdFile = $targetPath + ".md"
            return Test-Path $mdFile
        }
        
        return $false
        
    } catch {
        return $false
    }
}

function Test-ExternalLink {
    param([string]$Url)
    
    if ($CheckedUrls.ContainsKey($Url)) {
        return @{ valid = $true; message = "已检查过" }
    }
    
    try {
        $request = [System.Net.WebRequest]::Create($Url)
        $request.Method = "HEAD"
        $request.Timeout = 30000
        $request.UserAgent = "Mozilla/5.0 (compatible; DocumentationLinkChecker/1.0)"
        
        $response = $request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()
        
        $CheckedUrls[$Url] = $true
        
        if ($statusCode -in @(200, 301, 302, 403)) {
            return @{ valid = $true; message = "HTTP $statusCode" }
        } else {
            return @{ valid = $false; message = "HTTP $statusCode" }
        }
        
    } catch [System.Net.WebException] {
        $CheckedUrls[$Url] = $true
        $statusCode = [int]$_.Exception.Response.StatusCode
        
        if ($statusCode -in @(403, 429)) {
            return @{ valid = $true; message = "HTTP $statusCode (可能被防爬虫保护)" }
        }
        return @{ valid = $false; message = "HTTP $statusCode" }
        
    } catch {
        $CheckedUrls[$Url] = $true
        return @{ valid = $false; message = "检查失败: $($_.Exception.Message)" }
    }
}

function Test-Link {
    param([string]$Url, [string]$CurrentFile)
    
    # 清理 URL
    $Url = $Url.Trim()
    
    # 移除查询参数和锚点进行检查
    $cleanUrl = $Url -split '\?' | Select-Object -First 1
    $cleanUrl = $cleanUrl -split '#' | Select-Object -First 1
    
    if ($Url.StartsWith('http://') -or $Url.StartsWith('https://')) {
        return Test-ExternalLink $Url
    } else {
        $isValid = Test-InternalLink $cleanUrl $CurrentFile
        return @{ 
            valid = $isValid
            message = if ($isValid) { "内部链接" } else { "文件不存在" }
        }
    }
}

function Test-FileLinks {
    param([string]$FilePath)
    
    Write-Host "📄 检查文件: $FilePath" -ForegroundColor Cyan
    
    $fileResult = @{
        file = (Resolve-Path $FilePath -Relative)
        links = @()
        summary = @{
            total = 0
            valid = 0
            invalid = 0
            skipped = 0
        }
    }
    
    $links = Extract-LinksFromFile $FilePath
    $fileResult.summary.total = $links.Count
    
    foreach ($linkInfo in $links) {
        $url = $linkInfo.url
        $lineNum = $linkInfo.line
        
        if (Should-IgnoreLink $url) {
            $fileResult.summary.skipped++
            $fileResult.links += @{
                url = $url
                line = $lineNum
                status = "skipped"
                message = "匹配忽略模式"
            }
            continue
        }
        
        $checkResult = Test-Link $url $FilePath
        
        $linkResult = @{
            url = $url
            line = $lineNum
            status = if ($checkResult.valid) { "valid" } else { "invalid" }
            message = $checkResult.message
        }
        
        $fileResult.links += $linkResult
        
        if ($checkResult.valid) {
            $fileResult.summary.valid++
            Write-Host "  ✅ $url" -ForegroundColor Green
        } else {
            $fileResult.summary.invalid++
            Write-Host "  ❌ $url - $($checkResult.message)" -ForegroundColor Red
            
            # 添加到全局无效链接列表
            $Results.invalid_links += @{
                file = (Resolve-Path $FilePath -Relative)
                url = $url
                line = $lineNum
                message = $checkResult.message
            }
        }
        
        # 添加延迟避免请求过快
        Start-Sleep -Milliseconds 500
    }
    
    return $fileResult
}

function Start-LinkCheck {
    Write-Host "🔍 开始检查文档链接..." -ForegroundColor Yellow
    Write-Host "📁 检查目录: $DocsDir" -ForegroundColor Yellow
    
    if (-not (Test-Path $DocsDir)) {
        Write-Host "❌ 目录不存在: $DocsDir" -ForegroundColor Red
        return $false
    }
    
    # 查找所有 Markdown 文件
    $mdFiles = @()
    $mdFiles += Get-ChildItem -Path $DocsDir -Filter "*.md" -Recurse
    
    # 也检查根目录的 README 文件
    foreach ($readme in @("README.md", "README-ZH.md")) {
        if (Test-Path $readme) {
            $mdFiles += Get-Item $readme
        }
    }
    
    if ($mdFiles.Count -eq 0) {
        Write-Host "⚠️  未找到 Markdown 文件" -ForegroundColor Yellow
        return $true
    }
    
    Write-Host "📋 找到 $($mdFiles.Count) 个 Markdown 文件" -ForegroundColor Yellow
    
    $Results.summary.total_files = $mdFiles.Count
    
    # 检查每个文件
    foreach ($file in $mdFiles) {
        $fileResult = Test-FileLinks $file.FullName
        $Results.files += $fileResult
        
        # 更新总计
        $Results.summary.total_links += $fileResult.summary.total
        $Results.summary.valid_links += $fileResult.summary.valid
        $Results.summary.invalid_links += $fileResult.summary.invalid
        $Results.summary.skipped_links += $fileResult.summary.skipped
    }
    
    # 生成报告
    try {
        $Results | ConvertTo-Json -Depth 10 | Out-File -FilePath $OutputFile -Encoding UTF8
        Write-Host "📄 报告已生成: $OutputFile" -ForegroundColor Green
    } catch {
        Write-Host "❌ 生成报告失败: $_" -ForegroundColor Red
    }
    
    # 输出总结
    $summary = $Results.summary
    Write-Host "`n📊 检查完成:" -ForegroundColor Yellow
    Write-Host "  📁 文件数量: $($summary.total_files)" -ForegroundColor White
    Write-Host "  🔗 链接总数: $($summary.total_links)" -ForegroundColor White
    Write-Host "  ✅ 有效链接: $($summary.valid_links)" -ForegroundColor Green
    Write-Host "  ❌ 无效链接: $($summary.invalid_links)" -ForegroundColor Red
    Write-Host "  ⏭️  跳过链接: $($summary.skipped_links)" -ForegroundColor Yellow
    
    if ($summary.invalid_links -gt 0) {
        Write-Host "`n❌ 发现 $($summary.invalid_links) 个无效链接:" -ForegroundColor Red
        foreach ($invalidLink in $Results.invalid_links) {
            Write-Host "  - $($invalidLink.file):$($invalidLink.line) - $($invalidLink.url)" -ForegroundColor Red
            Write-Host "    错误: $($invalidLink.message)" -ForegroundColor Red
        }
    }
    
    return $summary.invalid_links -eq 0
}

# 主执行逻辑
$success = Start-LinkCheck

if ($FailOnError -and -not $success) {
    exit 1
}