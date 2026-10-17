<#
.SYNOPSIS
    branch-cleanup.ps1 的自检：用**合成 git 仓库**证明各条判据的检测力（红/绿都能复现）。

.DESCRIPTION
    为什么必须有这个自检：
      「分支已合并」「内容没落地」这类结论如果判错，后果是**静默丢东西**。所以判据不能只在真仓库上
      目测一次，必须能在**构造出来的已知情形**上复现：每条断言都对应一个刻意造出来的仓库状态，
      断言失败即说明判据坏了（而不是「这次数据恰好如此」）。

    覆盖的 7 种情形（每个都对应本仓实测遇到的真实形态）：
      1. fully-merged   普通合并（分支头是基线祖先）            → contained
      2. squash-merged  squash 合并（内容在、提交对象不在）      → patch
      3. churn          内容已进基线、基线后续又改过同一段       → churn
      4. review         分支新建的文件没进基线（强信号）         → review
      5. delete-only    纯删除型改动（新增行为 0）              → patch（**旧版只看新增行会误判成 0% lost**）
      6. dup-a/dup-b    两个分支指向同一 commit                → 重复分支被识别
      7. merge 失败（冲突）路径由 4 覆盖（new-in-branch 亦触发 review）

    合成仓库结构（`github/master` 即基线）：
      master:     core.txt=A,B,C        gone.txt=x        legacy.txt=old
      fully-merged:  A,B,C + D           （合入 master 后 master 前进）
      squash-merged: core.txt 加 D，但**从未成为 master 祖先**
      churn:        core.txt 加一行，随后 master 单独加一行（两行不同）
      review:       新建 added.txt
      delete-only:  删除 legacy.txt（新增 0 行）
      dup-a/dup-b:  同一 commit

    运行：
      powershell -ExecutionPolicy Bypass -File scripts/test/test-branch-cleanup.ps1
    退出码：0 = 全部通过；1 = 有断言失败（并打印期望 vs 实际）。

.NOTES
    ⚠️ 本机 AMSI 偶发 `AccessViolationException(AmsiScanBuffer)`（见 local-gates.ps1 的同款说明）——
    直接重跑即可，不是断言失败。本脚本刻意**不 dot-source** 被测脚本（改用独立子进程），
    以免把被测脚本的顶层代码和本脚本的断言混在同一个会话里。
#>
[CmdletBinding()]
param(
    [string]$KeepTempRepo
)

$ErrorActionPreference = 'Continue'
$script:Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:UnderTest = Join-Path $PSScriptRoot 'branch-cleanup.ps1'
$script:Failures = @()

function Add-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    $tag = if ($Ok) { 'PASS' } else { 'FAIL' }
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1}{2}" -f $tag, $Name, $(if ($Detail) { "  — $Detail" } else { '' })) -ForegroundColor $color
    if (-not $Ok) { $script:Failures += $Name }
}

function Invoke-SynthGit {
    param([string]$Repo, [string[]]$GitArgs)
    & git -C $Repo @GitArgs 2>$null | Out-Null
    return $LASTEXITCODE
}

function New-SyntheticRepo {
    param([string]$Path)
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    Invoke-SynthGit $Path @('init', '-q')                        | Out-Null
    Invoke-SynthGit $Path @('config', 'user.email', 't@test.local') | Out-Null
    Invoke-SynthGit $Path @('config', 'user.name', 'branch-cleanup selftest') | Out-Null
    Invoke-SynthGit $Path @('config', 'commit.gpgsign', 'false')  | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'master')      | Out-Null

    Set-Content -LiteralPath (Join-Path $Path 'core.txt')   -Value @('A', 'B', 'C')
    Set-Content -LiteralPath (Join-Path $Path 'gone.txt')   -Value @('x')
    Set-Content -LiteralPath (Join-Path $Path 'legacy.txt') -Value @('old')
    Invoke-SynthGit $Path @('add', '-A') | Out-Null
    Invoke-SynthGit $Path @('commit', '-q', '-m', 'init') | Out-Null

    # 1) fully-merged：普通合并后，分支头成为基线祖先
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'fully-merged') | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'core.txt') -Value @('A', 'B', 'C', 'D')
    Invoke-SynthGit $Path @('commit', '-q', '-am', 'fm') | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', 'master')   | Out-Null
    Invoke-SynthGit $Path @('merge', '-q', '--no-ff', '-m', 'merge fully', 'fully-merged') | Out-Null

    # 2) squash-merged：同样的内容，但**没有** merge 提交（squash 的等价形态）
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'squash-merged', 'master') | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'core.txt') -Value @('A', 'B', 'C', 'D', 'E')
    Invoke-SynthGit $Path @('commit', '-q', '-am', 'sq') | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', 'master')  | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'core.txt') -Value @('A', 'B', 'C', 'D', 'E')   # 内容落进基线
    Invoke-SynthGit $Path @('commit', '-q', '-am', 'squash land') | Out-Null

    # 3) churn：分支加的那行**后来被基线改写成另一个样子** ⇒ 该文件改动零存活，但不是「没合并」
    #    （基线是在这条分支的基础上继续演进的）。判据若把这种情况报成「丢失」就是误报。
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'churn', 'master') | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'core.txt') -Value @('A', 'B', 'C', 'D', 'E', 'F-branch')
    Invoke-SynthGit $Path @('commit', '-q', '-am', 'churn branch') | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', 'master') | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'core.txt') -Value @('A', 'B', 'C', 'D', 'E', 'F-master')
    Invoke-SynthGit $Path @('commit', '-q', '-am', 'master rewrites the same line') | Out-Null

    # 4) review：分支新建的文件根本没进基线（最强的「没落地」信号）
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'review', 'master') | Out-Null
    Set-Content -LiteralPath (Join-Path $Path 'added.txt') -Value @('brand', 'new', 'file')
    Invoke-SynthGit $Path @('add', '-A') | Out-Null
    Invoke-SynthGit $Path @('commit', '-q', '-m', 'add file') | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', 'master') | Out-Null

    # 5) delete-only：纯删除（新增行为 0）—— 只看新增行的旧判据会误判成「0% 存活」
    Invoke-SynthGit $Path @('checkout', '-q', '-b', 'delete-only', 'master') | Out-Null
    Invoke-SynthGit $Path @('rm', '-q', 'legacy.txt') | Out-Null
    Invoke-SynthGit $Path @('commit', '-q', '-m', 'delete legacy') | Out-Null
    Invoke-SynthGit $Path @('checkout', '-q', 'master') | Out-Null

    # 6) 重复分支：两个 heads 指向同一 commit
    Invoke-SynthGit $Path @('branch', 'dup-a', 'churn') | Out-Null
    Invoke-SynthGit $Path @('branch', 'dup-b', 'churn') | Out-Null

    # 让「期望远端」解析得出 github/master（本仓基线命名习惯）
    Invoke-SynthGit $Path @('remote', 'add', 'github', 'https://example.invalid/selftest.git') | Out-Null
    Invoke-SynthGit $Path @('update-ref', 'refs/remotes/github/master', 'refs/heads/master') | Out-Null
    Invoke-SynthGit $Path @('symbolic-ref', 'refs/remotes/github/HEAD', 'refs/remotes/github/master') | Out-Null
    return $Path
}

# ============================================================ 主流程
Write-Host "branch-cleanup.ps1 自检（合成仓库）" -ForegroundColor White
Write-Host "被测脚本：$script:UnderTest" -ForegroundColor DarkGray

if (-not (Test-Path $script:UnderTest)) {
    Write-Host "❌ 找不到被测脚本：$script:UnderTest" -ForegroundColor Red
    exit 1
}

$tmpRoot = if ($KeepTempRepo) { $KeepTempRepo } else {
    Join-Path ([System.IO.Path]::GetTempPath()) ("branch-cleanup-selftest-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
}
Write-Host "合成仓库：$tmpRoot" -ForegroundColor DarkGray
New-SyntheticRepo -Path $tmpRoot | Out-Null

$jsonPath = Join-Path $tmpRoot 'report.json'
try {
    # 用 -Format Json 落盘：一次调用同时覆盖「分析判据」与「JSON 输出」两条路径。
    # ⚠️ 必须显式把 -RepoRoot 指向合成仓库：被测脚本默认用 $PSScriptRoot 推导仓库根，
    #    那样会分析**本项目**而不是合成仓库（本自检第一版就踩了这个坑，断言全在真仓数据上跑）。
    # ⚠️ 重试：本机 AMSI 偶发 `AccessViolationException(AmsiScanBuffer)` 会让子进程直接死掉
    #    （不产出报告）。这与判据无关，故最多重试 3 次；3 次都没有产物才判「分析阶段失败」。
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        & powershell -ExecutionPolicy Bypass -NoProfile -File $script:UnderTest `
            -RepoRoot $tmpRoot -Base 'github/master' -NoFetch -Format Json -OutDir $tmpRoot 2>&1 | Out-Null
        $produced = @(Get-ChildItem -Path $tmpRoot -Filter 'branch-cleanup-*.json' -ErrorAction SilentlyContinue)
        if ($produced.Count -gt 0) { break }
        Write-Host ("  （第 {0} 次未产出报告，疑为 AMSI 抖动，重试）" -f $attempt) -ForegroundColor Yellow
    }

    $jsonFile = @(Get-ChildItem -Path $tmpRoot -Filter 'branch-cleanup-*.json' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1)
    if ($jsonFile.Count -eq 0) {
        Write-Host "❌ 被测脚本没有产出 JSON 报告（分析阶段就失败了）" -ForegroundColor Red
        exit 1
    }
    $doc = Get-Content -LiteralPath $jsonFile[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json

    # 把 JSON 里的分支拍平成 «名字 → bucket» 便于逐条断言
    $buckets = @{}
    foreach ($b in @($doc.local)) { $buckets[[string]$b.name] = [string]$b.bucket }

    Write-Host ""
    Write-Host "== 断言（每个对应一种刻意构造的仓库状态） ==" -ForegroundColor Cyan
    Write-Host ("  实测分类：" + (($buckets.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '  ')) -ForegroundColor DarkGray
    Write-Host ""

    Add-Check "baseline 解析为 github/master" ($doc.base -eq 'github/master') "base=$($doc.base)"
    # 期望值 = 该情形在**真实仓库**里应当得到的分类（口径见 branch-cleanup.ps1 的判定注释）
    Add-Check "1 fully-merged → contained"   ($buckets['fully-merged'] -eq 'contained')  "实际=$($buckets['fully-merged'])（分支头是基线祖先）"
    Add-Check "2 squash-merged → patch"      ($buckets['squash-merged'] -eq 'patch')     "实际=$($buckets['squash-merged'])（squash 后内容在、提交对象不在，不应误报丢失）"
    Add-Check "3 churn → review"             ($buckets['churn'] -eq 'review')           "实际=$($buckets['churn'])（该行被基线改写 ⇒ 零存活；无 PR 记录时按『需人工确认』处置，不擅自删）"
    Add-Check "4 review → review"            ($buckets['review'] -eq 'review')           "实际=$($buckets['review'])（分支新建的文件没进基线 = 最强信号）"
    Add-Check "5 delete-only → patch"        ($buckets['delete-only'] -eq 'patch')       "实际=$($buckets['delete-only'])（纯删除：新增 0 行，只看新增行的旧判据会误报成丢失）"
    Add-Check "6a dup-a → review（与 churn 同 commit）" ($buckets['dup-a'] -eq 'review') "实际=$($buckets['dup-a'])"
    Add-Check "6b dup-b → review（与 churn 同 commit）" ($buckets['dup-b'] -eq 'review') "实际=$($buckets['dup-b'])"

    # 重复分支：断言「存在某一组同时含 dup-a 与 dup-b」，不依赖列表下标/字段顺序
    $dupHasBoth = $false
    foreach ($d in @($doc.duplicates)) {
        $names = @($d.branches)
        if (($names -contains 'dup-a') -and ($names -contains 'dup-b')) { $dupHasBoth = $true }
    }
    Add-Check "6c 重复分支被识别（同一组含 dup-a+dup-b）" $dupHasBoth `
        ("duplicates = " + ((@($doc.duplicates) | ForEach-Object { ($_.branches -join '+') }) -join ' , '))

    Add-Check "JSON 概览计数自洽（可删 ≥ 3）" ([int]$doc.counts.deletableLocal -ge 3) "deletableLocal=$($doc.counts.deletableLocal)"
    Add-Check "JSON 含 needReview 计数" ($null -ne $doc.counts.needReview) "needReview=$($doc.counts.needReview)"
} finally {
    if (-not $KeepTempRepo) {
        Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "（已保留合成仓库：$tmpRoot）" -ForegroundColor DarkGray
    }
}

Write-Host ""
if ($script:Failures.Count -gt 0) {
    Write-Host ("结论：不通过 —— {0} 条断言失败：{1}" -f $script:Failures.Count, ($script:Failures -join '; ')) -ForegroundColor Red
    exit 1
}
Write-Host "结论：全部通过（判据在构造情形上可复现）。" -ForegroundColor Green
exit 0
