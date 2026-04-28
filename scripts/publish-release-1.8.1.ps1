# GitHub Release 发布脚本 - JAiRouter v1.8.1
# 使用方法：.\publish-release-1.8.1.ps1

$ErrorActionPreference = "Stop"

# 配置变量
$repo = "Lincoln-cn/JAiRouter"
$tag = "v1.8.1"
$releaseName = "JAiRouter v1.8.1"
$releaseNotesFile = "RELEASE-NOTES-1.8.1.md"
$branch = "master"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  JAiRouter v1.8.1 GitHub Release 发布脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Git 状态
Write-Host "[1/5] 检查 Git 状态..." -ForegroundColor Yellow
git status | Select-String "working tree clean"
if ($LASTEXITCODE -ne 0) {
    Write-Host "警告：工作区有未提交的更改！" -ForegroundColor Red
    $continue = Read-Host "是否继续发布？(y/n)"
    if ($continue -ne "y") { exit 1 }
}

# 创建 Tag
Write-Host ""
Write-Host "[2/5] 创建 Git Tag $tag..." -ForegroundColor Yellow
$existingTag = git tag -l $tag
if ($existingTag) {
    Write-Host "Tag $tag 已存在，是否删除并重新创建？" -ForegroundColor Yellow
    $deleteTag = Read-Host "删除旧 Tag (y/n)"
    if ($deleteTag -eq "y") {
        git tag -d $tag
        git push origin :refs/tags/$tag 2>$null
    } else {
        Write-Host "取消发布" -ForegroundColor Red
        exit 1
    }
}

git tag -a $tag -m "Release $tag"
Write-Host "✓ Tag 创建成功" -ForegroundColor Green

# 推送 Tag 到 GitHub
Write-Host ""
Write-Host "[3/5] 推送 Tag 到 GitHub..." -ForegroundColor Yellow
git push github $tag
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ 推送失败，请检查 GitHub 远程配置" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Tag 推送成功" -ForegroundColor Green

# 读取 Release Notes
Write-Host ""
Write-Host "[4/5] 读取 Release Notes..." -ForegroundColor Yellow
if (-not (Test-Path $releaseNotesFile)) {
    Write-Host "✗ Release Notes 文件不存在：$releaseNotesFile" -ForegroundColor Red
    exit 1
}
$releaseNotes = Get-Content $releaseNotesFile -Raw
Write-Host "✓ Release Notes 读取成功" -ForegroundColor Green

# 创建 GitHub Release
Write-Host ""
Write-Host "[5/5] 创建 GitHub Release..." -ForegroundColor Yellow
Write-Host ""
Write-Host "请按以下步骤在 GitHub 上创建 Release:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 访问：https://github.com/$repo/releases/new" -ForegroundColor White
Write-Host "2. Tag version: 选择 '$tag'" -ForegroundColor White
Write-Host "3. Release title: 输入 '$releaseName'" -ForegroundColor White
Write-Host "4. 复制以下 Release Notes 到描述框:" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Gray
Write-Host $releaseNotes
Write-Host "========================================" -ForegroundColor Gray
Write-Host ""
Write-Host "或者使用以下命令（需要安装 gh CLI）:" -ForegroundColor Cyan
Write-Host ""
Write-Host "gh release create $tag --title `"$releaseName`" --notes-file $releaseNotesFile --target $branch" -ForegroundColor Yellow
Write-Host ""

# 完成
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  发布准备完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "下一步操作:" -ForegroundColor Cyan
Write-Host "  1. 访问 GitHub Release 页面创建 Release" -ForegroundColor White
Write-Host "  2. 或者安装 gh CLI: winget install GitHub.cli" -ForegroundColor White
Write-Host "  3. 然后运行：gh release create $tag --title `"$releaseName`" --notes-file $releaseNotesFile" -ForegroundColor White
Write-Host ""
