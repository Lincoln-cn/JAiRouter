<#
.SYNOPSIS
    在 GitHub Actions 不可用时，本地复刻 CI 门闩并给出统一结论。

.DESCRIPTION
    2026-10 起 Actions 配额受限，本月无法依赖 CI 验证（见 issue「Actions 配额耗尽：本月改用本地替代门闩」）。
    本脚本把相关 workflow 的检查项在本地**逐条复刻**，命令与开关都按 CI 的实际调用对齐：

        CI 行                  本地复刻
        ---------------------  ---------------------------------------------------------------
        Java 测试              redis:7-alpine + postgres:16-alpine（CI 同款 health-cmd）
                               → mvnw test（默认离线 -o）→ 三个门控测试「不得被跳过」
        文档质量检查           mkdocs build --strict（需 5 个插件，见 -DocsPython）
                               + markdownlint-cli2（.markdownlint.json）
                               + python scripts/docs/check-links.py --dir docs --fail-on-error
        文档代码同步检查       python scripts/docs/check-docs-sync.py --project-root . --fail-on-error
        拼写检查               npx cspell@8 --config .cspell.json <相对基线改动的 *.md>
        前端基线               npm run type-check（在 frontend/ 下）

    ⚠️ 两个**必须知道**的本地差异（否则会误判）：
      1. 本仓库的**会话本地产物**（`innerdoc/mimo-memory/`、`innerdoc/session-digests/`）已归置到 `innerdoc/` 下
         （2026-10-16 从 `docs/` 移出；`innerdoc/` 整目录被 .gitignore，CI 里并不存在）。
         历史提示保留：若今后再把这类目录放回 `docs/`，它们会让 mkdocs 的 git-revision 插件批量报警告、
         并让两个 docs 扫描器误报 ⇒ 本脚本会在跑文档检查期间把 `docs/` 下**被忽略的路径**临时移到仓库外、
         结束后还原（见 Move-IgnoredDocsAside；该逻辑按 git status --ignored 动态探测，当前已是空操作）。
      2. CI 用 `cspell-action@v5`（自带词典），本地用 `cspell@8`，两者不一致：**本地 0 问题不蕴含 CI 绿**。

    ⚠️ 本机执行策略默认拦未签名脚本。要么用 `scripts/test/local-gates.cmd`，要么：
        powershell -ExecutionPolicy Bypass -File scripts/test/local-gates.ps1

    ⚠️ 已知环境缺陷（偶发，非脚本错误）：Windows PowerShell 5.1 + 本机 AMSI 组合下，运行较长脚本时
       可能抛 `System.AccessViolationException ... AmsiUtils.AmsiNativeMethods.AmsiScanBuffer`
        并直接结束进程。**实测同一命令重跑即可通过**（同一份脚本在本机时好时坏）。
        判据 = 输出里出现 `AmsiScanBuffer`/`AccessViolationException` 就直接重跑，不要误判成门闩失败。

.PARAMETER DocsPython
    跑 mkdocs / docs 脚本用的解释器。默认用仓库外的文档 venv（含 CI 装的 5 个插件），
    没有就退回 PATH 上的 python（此时 mkdocs 相关项会记为 SKIP，而不是假装通过）。

.EXAMPLE
    ./scripts/test/local-gates.cmd
    ./scripts/test/local-gates.cmd -SkipMaven
    pwsh -File scripts/test/local-gates.ps1 -SpellBase HEAD~3
#>
param(
    [string]$SpellBase = "github/master",
    [string]$DocsPython = "C:\Users\Administrator\.dsh-venvs\jairouter-docs\Scripts\python.exe",
    [switch]$SkipServices,
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipDocs,
    [switch]$SkipLinks,
    [switch]$Online,
    [int]$RedisPort = 6379,
    [int]$PostgresPort = 5432
)

# ⚠️ 这里刻意用 Continue 而不是 Stop：Windows PowerShell 5.1 会把**原生程序写 stderr**
# 升级成终止错误（例如 `mkdocs --version`、`docker`、`git` 的常规提示），把脚本打断。
# 本脚本对每个原生调用都显式检查退出码，因此不需要 Stop 兜底。
$ErrorActionPreference = "Continue"
$script:Results = @()
$script:Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:RedisContainer = "jairouter-localgates-redis"
$script:PgContainer = "jairouter-localgates-postgres"
$script:MovedAside = @()

function Add-Result {
    param([string]$Name, [ValidateSet("PASS", "FAIL", "SKIP")][string]$Status, [string]$Detail)
    $script:Results += [pscustomobject]@{ Check = $Name; Status = $Status; Detail = $Detail }
    $color = switch ($Status) { "PASS" { "Green" } "FAIL" { "Red" } default { "Yellow" } }
    Write-Host ("  [{0,-4}] {1} — {2}" -f $Status, $Name, $Detail) -ForegroundColor $color
}

function Invoke-Logged {
    param([string]$Exe, [string[]]$Arguments, [string]$WorkDir = $script:Root)
    Push-Location $WorkDir
    try {
        $output = & $Exe @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    return [pscustomobject]@{ ExitCode = $code; Output = @($output) }
}

# ---------------------------------------------------------------- 容器与清理
function Remove-LocalGateContainers {
    foreach ($name in @($script:RedisContainer, $script:PgContainer)) {
        $existing = docker ps -a --filter "name=^/$name$" --format "{{.ID}}" 2>$null
        if ($existing) {
            docker rm -f $name | Out-Null
            Write-Host "  (已清理容器 $name)" -ForegroundColor DarkGray
        }
    }
}

function Wait-ContainerHealthy {
    param([string]$Name, [int]$TimeoutSeconds = 120, [string]$Label)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $status = "unknown"
    while ((Get-Date) -lt $deadline) {
        $status = docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $Name 2>$null
        if ($status -eq "healthy") { return $true }
        if ($status -eq "unhealthy") {
            Write-Host "  $Label 容器进入 unhealthy：" -ForegroundColor Red
            docker logs --tail 20 $Name 2>&1 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
            return $false
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "  $Label 在 ${TimeoutSeconds}s 内未变为 healthy（最后状态：$status）" -ForegroundColor Red
    return $false
}

function Start-Services {
    Write-Host "`n== 启动测试依赖容器（对齐 java-tests.yml 的 services:） ==" -ForegroundColor Cyan
    Remove-LocalGateContainers

    # health-cmd / interval / timeout / retries 与 workflow 的 options 逐字一致
    docker run -d --name $script:RedisContainer -p "${RedisPort}:6379" `
        --health-cmd "redis-cli ping" --health-interval 10s --health-timeout 5s --health-retries 5 `
        redis:7-alpine | Out-Null
    docker run -d --name $script:PgContainer -p "${PostgresPort}:5432" `
        -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=postgres `
        --health-cmd "pg_isready -U postgres" --health-interval 10s --health-timeout 5s --health-retries 10 `
        postgres:16-alpine | Out-Null

    $redisOk = Wait-ContainerHealthy -Name $script:RedisContainer -Label "redis"
    $pgOk = Wait-ContainerHealthy -Name $script:PgContainer -Label "postgres"
    if (-not ($redisOk -and $pgOk)) {
        Add-Result "测试依赖容器" "FAIL" "redis=$redisOk postgres=$pgOk"
        return $false
    }
    Add-Result "测试依赖容器" "PASS" "redis:7-alpine + postgres:16-alpine 均 healthy"
    return $true
}

# ---------------------------------------------------------------- 被忽略产物
function Get-IgnoredDocsPaths {
    $lines = & git -C $script:Root status --ignored=matching --porcelain -- docs 2>$null
    $paths = @()
    foreach ($line in @($lines)) {
        if ($line -like "!! *") {
            $p = $line.Substring(3).Trim()
            if ($p -like "docs/*") { $paths += $p }
        }
    }
    return $paths
}

function Move-IgnoredDocsAside {
    $paths = Get-IgnoredDocsPaths
    if ($paths.Count -eq 0) { return }
    $stash = Join-Path $env:TEMP ("local-gates-docs-stash-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $stash -Force | Out-Null
    foreach ($rel in $paths) {
        $src = Join-Path $script:Root $rel
        if (-not (Test-Path $src)) { continue }
        $dst = Join-Path $stash (Split-Path $rel -Leaf)
        Move-Item -LiteralPath $src -Destination $dst -Force
        $script:MovedAside += [pscustomobject]@{ Original = $src; Stash = $dst }
    }
    if ($script:MovedAside.Count -gt 0) {
        $names = ($script:MovedAside | ForEach-Object { Split-Path $_.Original -Leaf }) -join ", "
        Write-Host "  (临时移开被忽略的本地产物：$names → $stash)" -ForegroundColor DarkGray
    }
}

function Restore-IgnoredDocsAside {
    foreach ($m in $script:MovedAside) {
        if (Test-Path $m.Stash) {
            Move-Item -LiteralPath $m.Stash -Destination $m.Original -Force
        }
    }
    $script:MovedAside = @()
}

# ---------------------------------------------------------------- Java
function Invoke-MavenTests {
    Write-Host "`n== Java 测试（复刻 java-tests.yml 的 test job） ==" -ForegroundColor Cyan
    # 与 CI env: 逐字一致
    $env:REDIS_TEST = "true"
    $env:REDIS_TEST_HOST = "localhost"
    $env:REDIS_TEST_PORT = "$RedisPort"
    $env:PG_TEST = "true"
    $env:PG_TEST_URL = "jdbc:postgresql://localhost:${PostgresPort}/postgres"
    $env:PG_TEST_USERNAME = "postgres"
    $env:PG_TEST_PASSWORD = "postgres"

    $mvn = Join-Path $script:Root "mvnw.cmd"
    $mvnArgs = @("-B", "test")
    $log = Join-Path $env:TEMP "local-gates-maven.log"
    if (-not $Online) { $mvnArgs += "-o" }
    Write-Host "  > mvnw $($mvnArgs -join ' ')   （日志：$log）" -ForegroundColor DarkGray

    # 直接串流到日志文件，避免长时间无输出；再按需回读尾部
    Push-Location $script:Root
    try {
        & $mvn @mvnArgs 2>&1 | Tee-Object -FilePath $log | Out-Null
        $exit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($exit -eq 0) {
        Add-Result "Java 测试" "PASS" "BUILD SUCCESS（日志：$log）"
    } else {
        $tail = (Get-Content $log -Tail 25) -join " | "
        Add-Result "Java 测试" "FAIL" "mvn 退出码 $exit；尾部：$tail"
    }
    return $exit -eq 0
}

function Assert-GatedTestsRan {
    Write-Host "`n== 校验门控测试未被跳过（复刻 java-tests.yml 的 check_gated） ==" -ForegroundColor Cyan
    $reportDir = Join-Path $script:Root "target\surefire-reports"
    $gated = @(
        @{ Name = "QuotaDistributedRedisIntegrationTest"; Flag = "REDIS_TEST" },
        @{ Name = "PostgresCompatibilityIntegrationTest"; Flag = "PG_TEST" },
        @{ Name = "PostgresFlywayMigrationEquivalenceTest"; Flag = "PG_TEST" }
    )
    $ok = $true
    foreach ($g in $gated) {
        $files = @(Get-ChildItem -Path $reportDir -Filter "*$($g.Name)*.txt" -ErrorAction SilentlyContinue)
        if ($files.Count -eq 0) {
            Add-Result "门控测试 $($g.Name)" "FAIL" "未找到 surefire 报告 —— 该测试根本没跑"
            $ok = $false
            continue
        }
        $skipped = $false
        foreach ($f in $files) {
            if (Select-String -Path $f.FullName -Pattern "Tests run:.*Skipped: [1-9]" -Quiet) { $skipped = $true }
        }
        if ($skipped) {
            Add-Result "门控测试 $($g.Name)" "FAIL" "$($g.Flag)=true 时仍被跳过"
            $ok = $false
        } else {
            $summary = (Select-String -Path $files[0].FullName -Pattern "Tests run:" | Select-Object -First 1).Line.Trim()
            Add-Result "门控测试 $($g.Name)" "PASS" "已执行（$summary）"
        }
    }
    return $ok
}

# ---------------------------------------------------------------- 前端
function Invoke-FrontendBaseline {
    Write-Host "`n== 前端基线 ==" -ForegroundColor Cyan
    $frontend = Join-Path $script:Root "frontend"
    if (-not (Test-Path (Join-Path $frontend "package.json"))) {
        Add-Result "前端 type-check" "SKIP" "frontend/package.json 不存在"
        return
    }
    $r = Invoke-Logged -Exe "npm" -Arguments @("run", "type-check") -WorkDir $frontend
    if ($r.ExitCode -eq 0) {
        Add-Result "前端 type-check" "PASS" "vue-tsc --noEmit 无错误"
    } else {
        Add-Result "前端 type-check" "FAIL" (($r.Output | Select-Object -Last 12) -join " | ")
    }
}

# ---------------------------------------------------------------- 文档与拼写
function Get-ChangedMarkdown {
    # ⚠️ 必须同时覆盖三处，否则会漏检（实测踩到：只比 base...HEAD 时，还没 commit 就完全看不到改动）：
    #   1) 已提交的提交区间（base...HEAD）
    #   2) 已暂存/未暂存相对 base 的改动
    #   3) 尚未纳入 git 的新增 *.md（CI 里它们已经是提交的一部分，本地必须显式带上）
    $committed = & git -C $script:Root diff --name-only "$SpellBase...HEAD" -- "*.md" 2>$null
    $dirty = & git -C $script:Root diff --name-only $SpellBase -- "*.md" 2>$null
    $untracked = & git -C $script:Root ls-files --others --exclude-standard "*.md" 2>$null
    $all = @($committed) + @($dirty) + @($untracked) |
        Where-Object { $_ } | Sort-Object -Unique
    return @($all)
}

function Invoke-SpellCheck {
    Write-Host "`n== 拼写检查（复刻 docs-quality.yml 的 cspell-action） ==" -ForegroundColor Cyan
    $changed = Get-ChangedMarkdown
    if ($changed.Count -eq 0) {
        Add-Result "拼写检查" "SKIP" "相对 $SpellBase 没有改动的 *.md"
        return
    }
    $files = @($changed | Where-Object { Test-Path (Join-Path $script:Root $_) })
    Write-Host "  检查 $($files.Count) 个改动的 md" -ForegroundColor DarkGray
    $r = Invoke-Logged -Exe "npx" -Arguments (@("--yes", "cspell@8", "--config", ".cspell.json") + $files)
    if ($r.ExitCode -eq 0) {
        Add-Result "拼写检查" "PASS" "0 问题（本地 cspell@8；CI 用 cspell-action@v5，词典不完全一致）"
    } else {
        $hits = @($r.Output | Select-String -Pattern "Unknown word") | Select-Object -First 5
        Add-Result "拼写检查" "FAIL" ($hits -join " | ")
    }
}

function Invoke-DocsGates {
    Write-Host "`n== 文档门闩（mkdocs --strict + markdownlint + 链接 + 同步检查） ==" -ForegroundColor Cyan
    $py = $DocsPython
    if (-not (Test-Path $py)) { $py = "python" }

    $hasMkdocs = $false
    try { $r = Invoke-Logged -Exe $py -Arguments @("-m", "mkdocs", "--version"); $hasMkdocs = ($r.ExitCode -eq 0) } catch { }

    if ($hasMkdocs) {
        $r = Invoke-Logged -Exe $py -Arguments @("-m", "mkdocs", "build", "--strict")
        if ($r.ExitCode -eq 0) {
            Add-Result "mkdocs --strict" "PASS" "站点构建无警告/错误"
        } else {
            $warn = @($r.Output | Select-String -Pattern "^\s*WARNING|^Aborted|^ERROR") | Select-Object -First 4
            Add-Result "mkdocs --strict" "FAIL" (($warn -join " | ") + "  （完整输出：$py -m mkdocs build --strict）")
        }
    } else {
        Add-Result "mkdocs --strict" "SKIP" "$py 没有 mkdocs 模块 —— 需先建文档 venv（见脚本说明）"
    }

    # 文档脚本的语法冒烟（issue #345 顺带发现：`docs-version-manager.py` 由
    # docs-version-management.yml 在 CI 上直接执行，但本地门闩此前**完全不覆盖**它 ——
    # 它一旦语法坏了，CI 的「文档版本管理」整条 job 会红，而本地不会提前发现）。
    $scriptsToCompile = @("scripts/docs/docs-version-manager.py", "scripts/docs/check-docs-sync.py",
                          "scripts/docs/check-links.py", "scripts/docs/check-fence-parity.py")
    $compileFails = @()
    foreach ($s in $scriptsToCompile) {
        if (-not (Test-Path (Join-Path $script:Root $s))) { continue }
        $r = Invoke-Logged -Exe $py -Arguments @("-m", "py_compile", $s)
        if ($r.ExitCode -ne 0) { $compileFails += (@($r.Output) | Select-Object -Last 2) -join " | " }
    }
    if ($compileFails.Count -eq 0) {
        Add-Result "文档脚本语法" "PASS" "$($scriptsToCompile.Count) 个脚本 py_compile 通过"
    } else {
        Add-Result "文档脚本语法" "FAIL" ($compileFails -join " ;; ")
    }

    # 围栏配平（issue #324）：CI 目前**不检查**这一项 —— 实测它一直绿，而文档里躺着 6 个文件 / 10 处
    # 围栏错字（开栏/闭栏写成两个反引号、块未闭合），这些会让整篇起错位点后渲染成代码。
    # 这里把它作为**附加**检查跑；是否进 CI 由维护者定夺（见 #324）。
    $fenceScript = Join-Path $script:Root "scripts/docs/check-fence-parity.py"
    if (Test-Path $fenceScript) {
        $r = Invoke-Logged -Exe $py -Arguments @("scripts/docs/check-fence-parity.py", "--dir", "docs", "--fail-on-error")
        if ($r.ExitCode -eq 0) {
            $summary = @($r.Output | Select-String -Pattern "^总计") | Select-Object -Last 1
            Add-Result "围栏配平" "PASS" ("" + $summary)
        } else {
            $bad = @($r.Output | Select-String -Pattern "^❌|two-backtick|odd fence count") | Select-Object -First 5
            Add-Result "围栏配平" "FAIL" ($bad -join " | ")
        }
    } else {
        Add-Result "围栏配平" "SKIP" "未找到 scripts/docs/check-fence-parity.py"
    }

    # markdownlint-cli2（CI 用 DavidAnson/markdownlint-cli2-action@v20 + .markdownlint.json）
    # 检查面刻意用 `git ls-files`（**跟踪的** *.md），而不是 `**/*.md` glob：
    # 本地 docs/ 与仓库根都有被 .gitignore 的本地产物（如 AGENTS.md），CI 里不存在这些文件，
    # 用 glob 会把它们的 markdown 小瑕疵算成门闩失败（已实测踩到）。
    if (Test-Path (Join-Path $script:Root ".markdownlint.json")) {
        $tracked = @(& git -C $script:Root ls-files "*.md")
        if ($tracked.Count -eq 0) {
            Add-Result "markdownlint" "SKIP" "git ls-files 没有跟踪的 *.md"
        } else {
            Write-Host "  检查 $($tracked.Count) 个跟踪的 md" -ForegroundColor DarkGray
            $args = @("--yes", "markdownlint-cli2@0.18.1", "--config", ".markdownlint.json") + $tracked
            $r = Invoke-Logged -Exe "npx" -Arguments $args
            if ($r.ExitCode -eq 0) {
                Add-Result "markdownlint" "PASS" "$($tracked.Count) 个跟踪的 md 无违规"
            } else {
                $fail = @($r.Output | Select-String -Pattern "MD[0-9]+|error") | Select-Object -First 5
                Add-Result "markdownlint" "FAIL" (($fail -join " | ") + "  （改动是否引入？请与基线对比）")
            }
        }
    } else {
        Add-Result "markdownlint" "SKIP" "未找到 .markdownlint.json"
    }

    # 自定义链接检查（CI：python3 scripts/docs/check-links.py --dir docs --output ... --fail-on-error）
    if ($SkipLinks) {
        Add-Result "链接检查" "SKIP" "-SkipLinks：未跑（联网扫全站链接，CI 里约 6 分钟；发 PR 前应至少跑一次）"
    } else {
        $r = Invoke-Logged -Exe $py -Arguments @("scripts/docs/check-links.py", "--dir", "docs", "--output", "link-check-report.json", "--fail-on-error")
        if ($r.ExitCode -eq 0) {
            Add-Result "链接检查" "PASS" "check-links.py 退出码 0"
        } else {
            $bad = @($r.Output | Select-String -Pattern "无效链接|HTTP 5|HTTP 4|❌") | Select-Object -Last 4
            Add-Result "链接检查" "FAIL" ($bad -join " | ")
        }
    }

    # 文档代码同步（CI 带 --fail-on-error；不带它时即使有 CRITICAL 也返回 0）
    $r = Invoke-Logged -Exe $py -Arguments @("scripts/docs/check-docs-sync.py", "--project-root", ".", "--fail-on-error")
    if ($r.ExitCode -eq 0) {
        Add-Result "文档代码同步检查" "PASS" "check-docs-sync.py --fail-on-error 退出码 0"
    } else {
        $bad = @($r.Output | Select-String -Pattern "CRITICAL|总计" ) | Select-Object -First 3
        Add-Result "文档代码同步检查" "FAIL" ($bad -join " | ")
    }
}

# ============================== 主流程 ==============================
Write-Host "本地替代门闩（GitHub Actions 不可用期间）" -ForegroundColor White
Write-Host "仓库根：$script:Root" -ForegroundColor DarkGray
Write-Host "参数：SpellBase=$SpellBase DocsPython=$DocsPython SkipMaven=$SkipMaven SkipFrontend=$SkipFrontend SkipDocs=$SkipDocs"

$servicesUp = $false
try {
    if (-not $SkipServices -and -not $SkipMaven) { $servicesUp = Start-Services }
    if (-not $SkipMaven) {
        if ($servicesUp) {
            Invoke-MavenTests | Out-Null
            Assert-GatedTestsRan | Out-Null
        } else {
            Add-Result "Java 测试" "FAIL" "依赖容器未就绪，未执行（门控测试会静默跳过，不能当绿）"
        }
    }
    if (-not $SkipFrontend) { Invoke-FrontendBaseline }
    if (-not $SkipDocs) {
        Invoke-SpellCheck
        Move-IgnoredDocsAside
        try { Invoke-DocsGates } finally { Restore-IgnoredDocsAside }
    }
} finally {
    Restore-IgnoredDocsAside
    if ($servicesUp) { Remove-LocalGateContainers }
}

Write-Host "`n================ 汇总 ================" -ForegroundColor White
$script:Results | Format-Table -AutoSize | Out-String | Write-Host
$failed = @($script:Results | Where-Object Status -eq "FAIL")
$skipped = @($script:Results | Where-Object Status -eq "SKIP")
Write-Host ("PASS {0} / FAIL {1} / SKIP {2}" -f `
    @($script:Results | Where-Object Status -eq "PASS").Count, $failed.Count, $skipped.Count)

if ($skipped.Count -gt 0) {
    Write-Host "`n⚠️ 存在 SKIP 项 —— 必须在本 PR 正文里写明「哪一项没跑、为什么」，不得当成绿。" -ForegroundColor Yellow
}
if ($failed.Count -gt 0) {
    Write-Host "`n结论：不通过。" -ForegroundColor Red
    exit 1
}
Write-Host "`n结论：通过（SKIP 项已如上列出，请在 PR 正文申报）。" -ForegroundColor Green
exit 0
