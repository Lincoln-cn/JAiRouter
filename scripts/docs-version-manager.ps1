# 文档版本管理脚本 (PowerShell 版本)
# 实现文档版本标识和更新提醒，追踪文档变更

param(
    [string]$ProjectRoot = ".",
    [switch]$Scan = $false,
    [string]$Report = "",
    [switch]$AddHeaders = $false,
    [int]$Cleanup = 0,
    [string]$Export = "",
    [int]$CheckOutdated = 30
)

# 设置错误处理
$ErrorActionPreference = "Stop"

# 文档版本信息类
class DocumentVersion {
    [string]$FilePath
    [string]$Version
    [string]$LastModified
    [string]$ContentHash
    [string]$GitCommit
    [string]$Author
    [string]$ChangeSummary
    [string[]]$Dependencies
    
    DocumentVersion([string]$filePath, [string]$version, [string]$lastModified, [string]$contentHash) {
        $this.FilePath = $filePath
        $this.Version = $version
        $this.LastModified = $lastModified
        $this.ContentHash = $contentHash
        $this.GitCommit = ""
        $this.Author = ""
        $this.ChangeSummary = ""
        $this.Dependencies = @()
    }
}

# 版本变更信息类
class VersionChange {
    [string]$FilePath
    [string]$OldVersion
    [string]$NewVersion
    [string]$ChangeType
    [string]$Timestamp
    [string]$Description
    
    VersionChange([string]$filePath, [string]$oldVersion, [string]$newVersion, [string]$changeType, [string]$timestamp) {
        $this.FilePath = $filePath
        $this.OldVersion = $oldVersion
        $this.NewVersion = $newVersion
        $this.ChangeType = $changeType
        $this.Timestamp = $timestamp
        $this.Description = ""
    }
}

# 文档版本管理器类
class DocumentVersionManager {
    [string]$ProjectRoot
    [string]$VersionFile
    [hashtable]$Versions
    [System.Collections.ArrayList]$Changes
    
    DocumentVersionManager([string]$projectRoot) {
        $this.ProjectRoot = $projectRoot
        $this.VersionFile = Join-Path $projectRoot ".kiro\docs-versions.json"
        $this.Versions = @{}
        $this.Changes = New-Object System.Collections.ArrayList
        
        # 确保版本文件目录存在
        $versionDir = Split-Path $this.VersionFile -Parent
        if (-not (Test-Path $versionDir)) {
            New-Item -ItemType Directory -Path $versionDir -Force | Out-Null
        }
        
        # 加载现有版本信息
        $this.LoadVersions()
    }
    
    [string]GetRelativePath([string]$basePath, [string]$fullPath) {
        # 兼容旧版本 PowerShell 的相对路径计算
        $basePath = [System.IO.Path]::GetFullPath($basePath).TrimEnd('\', '/')
        $fullPath = [System.IO.Path]::GetFullPath($fullPath)
        
        # 使用 .NET 方法计算相对路径
        try {
            $uri1 = New-Object System.Uri($basePath + [System.IO.Path]::DirectorySeparatorChar)
            $uri2 = New-Object System.Uri($fullPath)
            $relativeUri = $uri1.MakeRelativeUri($uri2)
            return [System.Uri]::UnescapeDataString($relativeUri.ToString()).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        }
        catch {
            # 回退到简单方法
            if ($fullPath.StartsWith($basePath)) {
                return $fullPath.Substring($basePath.Length).TrimStart('\', '/')
            }
            return $fullPath
        }
    }
    
    [void]LoadVersions() {
        if (Test-Path $this.VersionFile) {
            try {
                $data = Get-Content $this.VersionFile -Raw -Encoding UTF8 | ConvertFrom-Json
                
                $this.Versions = @{}
                if ($data.versions) {
                    foreach ($property in $data.versions.PSObject.Properties) {
                        $filePath = $property.Name
                        $versionData = $property.Value
                        
                        $version = [DocumentVersion]::new(
                            $versionData.FilePath,
                            $versionData.Version,
                            $versionData.LastModified,
                            $versionData.ContentHash
                        )
                        $version.GitCommit = $versionData.GitCommit
                        $version.Author = $versionData.Author
                        $version.ChangeSummary = $versionData.ChangeSummary
                        $version.Dependencies = $versionData.Dependencies
                        
                        $this.Versions[$filePath] = $version
                    }
                }
                
                $this.Changes = New-Object System.Collections.ArrayList
                if ($data.changes) {
                    foreach ($changeData in $data.changes) {
                        $change = [VersionChange]::new(
                            $changeData.FilePath,
                            $changeData.OldVersion,
                            $changeData.NewVersion,
                            $changeData.ChangeType,
                            $changeData.Timestamp
                        )
                        $change.Description = $changeData.Description
                        $this.Changes.Add($change) | Out-Null
                    }
                }
            }
            catch {
                Write-Warning "加载版本信息失败: $($_.Exception.Message)"
                $this.Versions = @{}
                $this.Changes = New-Object System.Collections.ArrayList
            }
        }
    }
    
    [void]SaveVersions() {
        try {
            $data = @{
                versions = @{}
                changes = @()
                last_updated = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
            }
            
            foreach ($kvp in $this.Versions.GetEnumerator()) {
                $data.versions[$kvp.Key] = @{
                    FilePath = $kvp.Value.FilePath
                    Version = $kvp.Value.Version
                    LastModified = $kvp.Value.LastModified
                    ContentHash = $kvp.Value.ContentHash
                    GitCommit = $kvp.Value.GitCommit
                    Author = $kvp.Value.Author
                    ChangeSummary = $kvp.Value.ChangeSummary
                    Dependencies = $kvp.Value.Dependencies
                }
            }
            
            foreach ($change in $this.Changes) {
                $data.changes += @{
                    FilePath = $change.FilePath
                    OldVersion = $change.OldVersion
                    NewVersion = $change.NewVersion
                    ChangeType = $change.ChangeType
                    Timestamp = $change.Timestamp
                    Description = $change.Description
                }
            }
            
            $json = $data | ConvertTo-Json -Depth 10
            $json | Out-File -FilePath $this.VersionFile -Encoding UTF8
        }
        catch {
            Write-Error "保存版本信息失败: $($_.Exception.Message)"
        }
    }
    
    [string]CalculateContentHash([string]$filePath) {
        try {
            $fullPath = Join-Path $this.ProjectRoot $filePath
            if (Test-Path $fullPath) {
                $content = Get-Content $fullPath -Raw -Encoding UTF8
                $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($content))
                return [System.BitConverter]::ToString($hash).Replace("-", "").Substring(0, 16).ToLower()
            }
        }
        catch {
            # 忽略错误
        }
        return ""
    }
    
    [hashtable]GetGitInfo([string]$filePath) {
        $result = @{
            commit = ""
            author = ""
        }
        
        try {
            $fullPath = Join-Path $this.ProjectRoot $filePath
            $gitOutput = & git log -1 --format="%H|%an" -- $fullPath 2>$null
            
            if ($LASTEXITCODE -eq 0 -and $gitOutput) {
                $parts = $gitOutput.Split('|', 2)
                if ($parts.Length -ge 1) {
                    $result.commit = $parts[0].Substring(0, [Math]::Min(8, $parts[0].Length))
                }
                if ($parts.Length -ge 2) {
                    $result.author = $parts[1]
                }
            }
        }
        catch {
            # 忽略 Git 错误
        }
        
        return $result
    }
    
    [string]GenerateVersionNumber([string]$filePath, [string]$contentHash) {
        $existingVersion = $this.Versions[$filePath]
        
        if (-not $existingVersion) {
            # 新文档，版本从 1.0.0 开始
            return "1.0.0"
        }
        
        if ($existingVersion.ContentHash -eq $contentHash) {
            # 内容未变化，保持原版本
            return $existingVersion.Version
        }
        
        # 内容有变化，递增版本号
        try {
            $parts = $existingVersion.Version.Split('.')
            if ($parts.Length -eq 3) {
                $major = [int]$parts[0]
                $minor = [int]$parts[1]
                $patch = [int]$parts[2]
                
                # 简单的版本递增策略：补丁版本 +1
                $patch++
                
                return "$major.$minor.$patch"
            }
        }
        catch {
            # 版本号格式错误，重新开始
        }
        
        return "1.0.0"
    }
    
    [string[]]DetectDocumentDependencies([string]$filePath) {
        $dependencies = @()
        
        try {
            $fullPath = Join-Path $this.ProjectRoot $filePath
            if (Test-Path $fullPath) {
                $content = Get-Content $fullPath -Raw -Encoding UTF8
                
                # 检测 Markdown 链接
                $mdLinks = [regex]::Matches($content, '\[.*?\]\(([^)]+)\)')
                foreach ($match in $mdLinks) {
                    $link = $match.Groups[1].Value
                    if ($link -notmatch '^(https?://|mailto:)' -and $link.EndsWith('.md')) {
                        $depPath = Join-Path (Split-Path $fullPath -Parent) $link
                        if (Test-Path $depPath) {
                            $relPath = $this.GetRelativePath($this.ProjectRoot, $depPath)
                            $dependencies += $relPath.Replace('\', '/')
                        }
                    }
                }
                
                # 检测文件引用 #[[file:...]]
                $fileRefs = [regex]::Matches($content, '#\[\[file:([^\]]+)\]\]')
                foreach ($match in $fileRefs) {
                    $ref = $match.Groups[1].Value
                    $refPath = Join-Path $this.ProjectRoot $ref
                    if (Test-Path $refPath) {
                        $dependencies += $ref
                    }
                }
            }
        }
        catch {
            Write-Warning "检测依赖关系失败 ${filePath}: $($_.Exception.Message)"
        }
        
        return ($dependencies | Sort-Object -Unique)
    }
    
    [string[]]ScanDocuments() {
        $documents = @()
        
        # 扫描文档目录
        $docPatterns = @(
            "docs\**\*.md",
            "README*.md",
            "*.md"
        )
        
        foreach ($pattern in $docPatterns) {
            try {
                if ($pattern.Contains("**")) {
                    # 递归搜索
                    $basePath = $pattern.Split("**")[0].TrimEnd('\', '/')
                    $fullBasePath = Join-Path $this.ProjectRoot $basePath
                    if (Test-Path $fullBasePath) {
                        $files = Get-ChildItem -Path $fullBasePath -Filter "*.md" -File -Recurse -ErrorAction SilentlyContinue
                        foreach ($file in $files) {
                            $relPath = $this.GetRelativePath($this.ProjectRoot, $file.FullName)
                            $documents += $relPath.Replace('\', '/')
                        }
                    }
                } else {
                    # 简单模式匹配
                    $fullPattern = Join-Path $this.ProjectRoot $pattern
                    $files = Get-ChildItem -Path $fullPattern -File -ErrorAction SilentlyContinue
                    foreach ($file in $files) {
                        $relPath = $this.GetRelativePath($this.ProjectRoot, $file.FullName)
                        $documents += $relPath.Replace('\', '/')
                    }
                }
            }
            catch {
                Write-Warning "扫描模式失败 ${pattern}: $($_.Exception.Message)"
            }
        }
        
        return ($documents | Sort-Object -Unique)
    }
    
    [VersionChange]UpdateDocumentVersion([string]$filePath) {
        $fullPath = Join-Path $this.ProjectRoot $filePath
        
        if (-not (Test-Path $fullPath)) {
            # 文档被删除
            if ($this.Versions.ContainsKey($filePath)) {
                $oldVersion = $this.Versions[$filePath].Version
                $this.Versions.Remove($filePath)
                
                $change = [VersionChange]::new(
                    $filePath,
                    $oldVersion,
                    "",
                    "DELETED",
                    (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
                )
                $change.Description = "文档已删除"
                $this.Changes.Add($change) | Out-Null
                return $change
            }
            
            return $null
        }
        
        # 计算当前文档信息
        $contentHash = $this.CalculateContentHash($filePath)
        $gitInfo = $this.GetGitInfo($filePath)
        $dependencies = $this.DetectDocumentDependencies($filePath)
        
        # 生成版本号
        $newVersion = $this.GenerateVersionNumber($filePath, $contentHash)
        
        # 检查是否有变化
        $existingVersion = $this.Versions[$filePath]
        
        if ($existingVersion) {
            if ($existingVersion.ContentHash -eq $contentHash) {
                # 内容未变化，但可能需要更新其他信息
                $existingVersion.GitCommit = $gitInfo.commit
                $existingVersion.Author = $gitInfo.author
                $existingVersion.Dependencies = $dependencies
                return $null
            }
            
            # 内容有变化
            $changeType = "MODIFIED"
            $oldVersion = $existingVersion.Version
        }
        else {
            # 新文档
            $changeType = "CREATED"
            $oldVersion = ""
        }
        
        # 更新版本信息
        $version = [DocumentVersion]::new(
            $filePath,
            $newVersion,
            (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss"),
            $contentHash
        )
        $version.GitCommit = $gitInfo.commit
        $version.Author = $gitInfo.author
        $version.Dependencies = $dependencies
        
        $this.Versions[$filePath] = $version
        
        # 记录变更
        $change = [VersionChange]::new(
            $filePath,
            $oldVersion,
            $newVersion,
            $changeType,
            (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
        )
        $change.Description = "文档$($changeType.ToLower())"
        $this.Changes.Add($change) | Out-Null
        
        return $change
    }
    
    [VersionChange[]]UpdateAllVersions() {
        Write-Host "🔍 扫描文档文件..." -ForegroundColor Cyan
        $documents = $this.ScanDocuments()
        
        Write-Host "📄 发现 $($documents.Count) 个文档文件" -ForegroundColor Green
        
        $allChanges = @()
        
        foreach ($docPath in $documents) {
            $change = $this.UpdateDocumentVersion($docPath)
            if ($change) {
                $allChanges += $change
                Write-Host "  📝 $($change.ChangeType): $($change.FilePath) ($($change.OldVersion) → $($change.NewVersion))" -ForegroundColor Yellow
            }
        }
        
        # 检查已删除的文档
        $existingPaths = $documents
        $toRemove = @()
        foreach ($kvp in $this.Versions.GetEnumerator()) {
            if ($kvp.Key -notin $existingPaths) {
                $toRemove += $kvp.Key
            }
        }
        
        foreach ($filePath in $toRemove) {
            $change = $this.UpdateDocumentVersion($filePath)
            if ($change) {
                $allChanges += $change
                Write-Host "  🗑️ $($change.ChangeType): $($change.FilePath)" -ForegroundColor Red
            }
        }
        
        return $allChanges
    }
    
    [string[]]CheckOutdatedDocuments([int]$daysThreshold) {
        $outdated = @()
        $thresholdDate = (Get-Date).AddDays(-$daysThreshold)
        
        foreach ($kvp in $this.Versions.GetEnumerator()) {
            try {
                $lastModified = [DateTime]::Parse($kvp.Value.LastModified)
                if ($lastModified -lt $thresholdDate) {
                    $outdated += $kvp.Key
                }
            }
            catch {
                # 日期格式错误，认为是过期的
                $outdated += $kvp.Key
            }
        }
        
        return $outdated
    }
    
    [int]AddVersionHeaders() {
        $addedCount = 0
        $configPath = Join-Path $this.ProjectRoot ".kiro\docs-version-config.yml"
        
        # 加载配置
        $config = @{
            version_headers = @{
                enabled = $true
                template = @"
<!-- 版本信息 -->
> **文档版本**: {version}  
> **最后更新**: {last_modified}  
> **Git 提交**: {git_commit}  
> **作者**: {author}
<!-- /版本信息 -->
"@
                position = "after_title"
            }
        }
        
        if (Test-Path $configPath) {
            try {
                # 简单的 YAML 解析（仅支持基本格式）
                $yamlContent = Get-Content $configPath -Raw -Encoding UTF8
                # 这里可以添加更复杂的 YAML 解析逻辑
            }
            catch {
                Write-Warning "配置文件解析失败，使用默认配置"
            }
        }
        
        if (-not $config.version_headers.enabled) {
            return 0
        }
        
        foreach ($kvp in $this.Versions.GetEnumerator()) {
            $filePath = $kvp.Key
            $versionInfo = $kvp.Value
            $fullPath = Join-Path $this.ProjectRoot $filePath
            
            if (-not (Test-Path $fullPath)) {
                continue
            }
            
            try {
                $content = Get-Content $fullPath -Raw -Encoding UTF8
                
                # 检查是否已有版本头
                if ($content -match "<!-- 版本信息 -->") {
                    # 更新现有版本头
                    $versionHeader = $config.version_headers.template
                    $versionHeader = $versionHeader -replace '\{version\}', $versionInfo.Version
                    $versionHeader = $versionHeader -replace '\{last_modified\}', $versionInfo.LastModified.Substring(0, 10)
                    $versionHeader = $versionHeader -replace '\{git_commit\}', $versionInfo.GitCommit
                    $versionHeader = $versionHeader -replace '\{author\}', $versionInfo.Author
                    
                    $newContent = $content -replace '<!-- 版本信息 -->.*?<!-- /版本信息 -->', $versionHeader, 'Singleline'
                    
                    if ($newContent -ne $content) {
                        $newContent | Out-File -FilePath $fullPath -Encoding UTF8 -NoNewline
                        $addedCount++
                    }
                }
                else {
                    # 添加新版本头
                    $versionHeader = $config.version_headers.template
                    $versionHeader = $versionHeader -replace '\{version\}', $versionInfo.Version
                    $versionHeader = $versionHeader -replace '\{last_modified\}', $versionInfo.LastModified.Substring(0, 10)
                    $versionHeader = $versionHeader -replace '\{git_commit\}', $versionInfo.GitCommit
                    $versionHeader = $versionHeader -replace '\{author\}', $versionInfo.Author
                    
                    # 根据位置插入版本头
                    if ($config.version_headers.position -eq "after_title") {
                        # 在第一个标题后插入
                        $lines = $content -split "`n"
                        $titleIndex = -1
                        for ($i = 0; $i -lt $lines.Length; $i++) {
                            if ($lines[$i] -match "^#\s+") {
                                $titleIndex = $i
                                break
                            }
                        }
                        
                        if ($titleIndex -ge 0) {
                            $newLines = @()
                            $newLines += $lines[0..$titleIndex]
                            $newLines += ""
                            $newLines += $versionHeader
                            $newLines += ""
                            if ($titleIndex + 1 -lt $lines.Length) {
                                $newLines += $lines[($titleIndex + 1)..($lines.Length - 1)]
                            }
                            
                            $newContent = $newLines -join "`n"
                            $newContent | Out-File -FilePath $fullPath -Encoding UTF8 -NoNewline
                            $addedCount++
                        }
                    }
                    elseif ($config.version_headers.position -eq "top") {
                        # 在文档顶部插入
                        $newContent = $versionHeader + "`n`n" + $content
                        $newContent | Out-File -FilePath $fullPath -Encoding UTF8 -NoNewline
                        $addedCount++
                    }
                }
            }
            catch {
                Write-Warning "添加版本头失败 ${filePath}: $($_.Exception.Message)"
            }
        }
        
        return $addedCount
    }
    
    [void]ExportVersionData([string]$exportPath) {
        try {
            $exportData = @{
                metadata = @{
                    export_time = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
                    project_root = $this.ProjectRoot
                    total_documents = $this.Versions.Count
                    total_changes = $this.Changes.Count
                }
                versions = @{}
                changes = @()
                statistics = @{
                    by_type = @{}
                    by_month = @{}
                    outdated_count = ($this.CheckOutdatedDocuments(30)).Count
                }
            }
            
            # 导出版本信息
            foreach ($kvp in $this.Versions.GetEnumerator()) {
                $exportData.versions[$kvp.Key] = @{
                    version = $kvp.Value.Version
                    last_modified = $kvp.Value.LastModified
                    content_hash = $kvp.Value.ContentHash
                    git_commit = $kvp.Value.GitCommit
                    author = $kvp.Value.Author
                    change_summary = $kvp.Value.ChangeSummary
                    dependencies = $kvp.Value.Dependencies
                }
            }
            
            # 导出变更历史
            foreach ($change in $this.Changes) {
                $exportData.changes += @{
                    file_path = $change.FilePath
                    old_version = $change.OldVersion
                    new_version = $change.NewVersion
                    change_type = $change.ChangeType
                    timestamp = $change.Timestamp
                    description = $change.Description
                }
            }
            
            # 生成统计信息
            $changesByType = $this.Changes | Group-Object ChangeType
            foreach ($group in $changesByType) {
                $exportData.statistics.by_type[$group.Name] = $group.Count
            }
            
            # 按月份统计变更
            $changesByMonth = $this.Changes | Group-Object { 
                try {
                    ([DateTime]::Parse($_.Timestamp)).ToString("yyyy-MM")
                } catch {
                    "unknown"
                }
            }
            foreach ($group in $changesByMonth) {
                $exportData.statistics.by_month[$group.Name] = $group.Count
            }
            
            # 根据文件扩展名确定导出格式
            $extension = [System.IO.Path]::GetExtension($exportPath).ToLower()
            
            if ($extension -eq ".json") {
                $json = $exportData | ConvertTo-Json -Depth 10
                $json | Out-File -FilePath $exportPath -Encoding UTF8
            }
            elseif ($extension -eq ".csv") {
                # 导出为 CSV 格式
                $csvData = @()
                foreach ($kvp in $this.Versions.GetEnumerator()) {
                    $csvData += [PSCustomObject]@{
                        FilePath = $kvp.Key
                        Version = $kvp.Value.Version
                        LastModified = $kvp.Value.LastModified
                        GitCommit = $kvp.Value.GitCommit
                        Author = $kvp.Value.Author
                    }
                }
                $csvData | Export-Csv -Path $exportPath -NoTypeInformation -Encoding UTF8
            }
            else {
                # 默认导出为 JSON
                $json = $exportData | ConvertTo-Json -Depth 10
                $json | Out-File -FilePath $exportPath -Encoding UTF8
            }
        }
        catch {
            Write-Error "导出版本数据失败: $($_.Exception.Message)"
        }
    }
    
    [string]GenerateVersionReport() {
        $report = @()
        $report += "# 文档版本管理报告`n"
        
        # 统计信息
        $totalDocs = $this.Versions.Count
        $recentChanges = ($this.Changes | Where-Object { 
            try {
                $changeDate = [DateTime]::Parse($_.Timestamp)
                $changeDate -gt (Get-Date).AddDays(-7)
            } catch {
                $false
            }
        }).Count
        
        $report += "## 版本统计`n"
        $report += "- 总文档数: $totalDocs"
        $report += "- 近7天变更: $recentChanges"
        $report += "- 版本文件: $($this.VersionFile)"
        $report += "- 最后扫描: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n"
        
        # 最近变更
        if ($recentChanges -gt 0) {
            $report += "## 最近变更`n"
            $recentChangeList = $this.Changes | Where-Object { 
                try {
                    $changeDate = [DateTime]::Parse($_.Timestamp)
                    $changeDate -gt (Get-Date).AddDays(-7)
                } catch {
                    $false
                }
            } | Sort-Object Timestamp -Descending | Select-Object -First 10
            
            foreach ($change in $recentChangeList) {
                $timestamp = $change.Timestamp.Substring(0, 10)
                $report += "- **$($change.ChangeType)**: $($change.FilePath) ($($change.OldVersion) → $($change.NewVersion)) - $timestamp"
            }
            
            $report += ""
        }
        
        # 过期文档检查
        $outdatedDocs = $this.CheckOutdatedDocuments(30)
        if ($outdatedDocs.Count -gt 0) {
            $report += "## 过期文档 (30天未更新)`n"
            foreach ($docPath in $outdatedDocs) {
                $versionInfo = $this.Versions[$docPath]
                $lastModified = $versionInfo.LastModified.Substring(0, 10)
                $report += "- $docPath (版本: $($versionInfo.Version), 最后更新: $lastModified)"
            }
            $report += ""
        }
        
        # 依赖关系分析
        $report += "## 依赖关系分析`n"
        $dependencyCount = 0
        foreach ($kvp in $this.Versions.GetEnumerator()) {
            if ($kvp.Value.Dependencies -and $kvp.Value.Dependencies.Count -gt 0) {
                $dependencyCount += $kvp.Value.Dependencies.Count
                $report += "- **$($kvp.Key)**: 依赖 $($kvp.Value.Dependencies.Count) 个文档"
                foreach ($dep in $kvp.Value.Dependencies) {
                    $report += "  - $dep"
                }
            }
        }
        if ($dependencyCount -eq 0) {
            $report += "- 未发现文档依赖关系"
        }
        $report += ""
        
        # 所有文档版本
        $report += "## 所有文档版本`n"
        $sortedVersions = $this.Versions.GetEnumerator() | Sort-Object { $_.Value.LastModified } -Descending
        
        foreach ($kvp in $sortedVersions) {
            $filePath = $kvp.Key
            $versionInfo = $kvp.Value
            $lastModified = $versionInfo.LastModified.Substring(0, 10)
            $gitInfo = if ($versionInfo.GitCommit) { " ($($versionInfo.GitCommit))" } else { "" }
            $report += "- **$filePath**: v$($versionInfo.Version) - $lastModified$gitInfo"
        }
        
        return ($report -join "`n")
    }
}

# 主执行逻辑
try {
    # 创建版本管理器
    $manager = [DocumentVersionManager]::new($ProjectRoot)
    
    if ($Scan) {
        Write-Host "🔄 更新文档版本信息..." -ForegroundColor Cyan
        $changes = $manager.UpdateAllVersions()
        
        if ($changes.Count -gt 0) {
            Write-Host "📝 发现 $($changes.Count) 个变更" -ForegroundColor Green
        } else {
            Write-Host "✅ 没有发现变更" -ForegroundColor Green
        }
        
        $manager.SaveVersions()
    }
    
    if ($AddHeaders) {
        Write-Host "📋 添加版本头信息..." -ForegroundColor Cyan
        $addedCount = $manager.AddVersionHeaders()
        Write-Host "✅ 为 $addedCount 个文档添加了版本头信息" -ForegroundColor Green
        $manager.SaveVersions()
    }
    
    if ($Cleanup -gt 0) {
        Write-Host "🧹 清理 $Cleanup 天前的变更记录..." -ForegroundColor Cyan
        $oldCount = $manager.Changes.Count
        
        $thresholdDate = (Get-Date).AddDays(-$Cleanup)
        $manager.Changes = $manager.Changes | Where-Object {
            try {
                $changeDate = [DateTime]::Parse($_.Timestamp)
                $changeDate -gt $thresholdDate
            } catch {
                $false
            }
        }
        
        $newCount = $manager.Changes.Count
        Write-Host "✅ 清理了 $($oldCount - $newCount) 条旧记录" -ForegroundColor Green
        $manager.SaveVersions()
    }
    
    if ($Report) {
        Write-Host "📊 生成版本报告..." -ForegroundColor Cyan
        $reportContent = $manager.GenerateVersionReport()
        
        $reportContent | Out-File -FilePath $Report -Encoding UTF8
        Write-Host "📄 报告已保存到: $Report" -ForegroundColor Green
    }
    
    if ($Export) {
        Write-Host "📤 导出版本数据..." -ForegroundColor Cyan
        $manager.ExportVersionData($Export)
        Write-Host "📄 数据已导出到: $Export" -ForegroundColor Green
    }
    
    # 检查过期文档
    $outdatedDocs = $manager.CheckOutdatedDocuments($CheckOutdated)
    if ($outdatedDocs.Count -gt 0) {
        Write-Host "`n⚠️ 发现 $($outdatedDocs.Count) 个过期文档 (超过 $CheckOutdated 天未更新):" -ForegroundColor Yellow
        foreach ($doc in $outdatedDocs) {
            $versionInfo = $manager.Versions[$doc]
            $lastModified = $versionInfo.LastModified.Substring(0, 10)
            Write-Host "  - $doc (版本: $($versionInfo.Version), 最后更新: $lastModified)" -ForegroundColor Yellow
        }
    }
    
    Write-Host "✅ 文档版本管理完成" -ForegroundColor Green
}
catch {
    Write-Host "❌ 文档版本管理失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}