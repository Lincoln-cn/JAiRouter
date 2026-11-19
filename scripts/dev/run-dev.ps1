# 构建并启动 JAiRouter（Windows PowerShell 5.1 / pwsh 通用）
#
# 修的是旧版三个真实缺陷（2026-11-18）：
#   1. 默认端口写成 31080 —— 浏览器/前端代理都按 8080 访问，于是"看起来没起来"；
#   2. `java -jar -Dserver.port=$Port ...` —— JVM 参数写在 -jar 之后会被当成应用参数，
#      端口设置整条失效（应用仍监听 8080）；
#   3. 用 `-Pfast` 构建 —— fast profile 里 frontend.build.skip=true，产出的 jar
#      不含 admin 控制台静态资源，/admin/ 直接 404。
#
# 用法：
#   .\scripts\dev\run-dev.ps1                      # 8080 + dev profile（构建含前端）
#   .\scripts\dev\run-dev.ps1 -SkipBuild           # 直接用已构建的 jar
#   .\scripts\dev\run-dev.ps1 -SkipFrontend        # 跳过前端构建（/admin 无页面，仅接口）
#   .\scripts\dev\run-dev.ps1 -Port 8081 -AdminPassword 'xxx'
param(
    [int]$Port = 8080,
    [string]$Profile = 'dev',
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [string]$AdminPassword = ''
)

$ErrorActionPreference = 'Stop'

# scripts/dev -> 仓库根
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $root
try {
    $jar = $null

    if (-not $SkipBuild) {
        $mvnw = Join-Path $root 'mvnw.cmd'
        if (-not (Test-Path $mvnw)) { throw "未找到 Maven Wrapper：$mvnw" }

        $mvnArgs = @('package', '-Pdev', '-DskipTests')
        if ($SkipFrontend) { $mvnArgs += '-Dfrontend.build.skip=true' }

        Write-Host "🔨 构建中：mvnw $($mvnArgs -join ' ')" -ForegroundColor Yellow
        & $mvnw @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw "构建失败（exit $LASTEXITCODE）" }
    }

    # 排除 sources / javadoc / tests 附属包，否则可能选错
    $jar = Get-ChildItem (Join-Path $root 'target') -Filter 'model-router-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw "target 下没有 model-router-*.jar，请先去掉 -SkipBuild 构建一次"
    }

    if ($AdminPassword) { $env:INITIAL_ADMIN_PASSWORD = $AdminPassword }

    Write-Host "📦 $($jar.Name)" -ForegroundColor Green
    Write-Host "🏃 java -jar … --server.port=$Port --spring.profiles.active=$Profile" -ForegroundColor Yellow
    Write-Host "🔗 控制台 http://localhost:$Port/admin/  |  Swagger http://localhost:$Port/swagger-ui.html" -ForegroundColor Cyan
    Write-Host "⏹  Ctrl+C 停止" -ForegroundColor Cyan
    Write-Host ""

    # 关键：应用参数放在 -jar 之后，JVM 参数走 JAVA_TOOL_OPTIONS 或 -D 前置；
    # 这里全部用 Spring 命令行参数，语义最清楚。
    & java -jar $jar.FullName "--server.port=$Port" "--spring.profiles.active=$Profile"
}
finally {
    Pop-Location
}
