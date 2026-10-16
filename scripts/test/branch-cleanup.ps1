<#
.SYNOPSIS
    清理「已合并」的本地/远端分支，并把「合并得不干净、有遗留」的分支单独梳理成清单。

.DESCRIPTION
    为什么不能直接用 `git branch --merged`：
      本仓合并 PR 用的是 **squash**（见 docs 的合并判据：`gh pr checks` 全绿 + `--squash`），
      分支的那一笔提交**从未成为基线祖先**。对 80 个本地分支实测：ancestry 判据只能认出
      「分支头恰好是基线祖先」的 6 个，而实际内容早已进主线的有 67 个
      ⇒ **ancestry 判据在本仓系统性失效**，据此删除会漏掉九成、又会误报一批。

    因此本脚本用两条**内容判据**（由强到弱）判定「这条分支的改动是否已经在基线里」：

      判据 A（最强：树等价）
        把分支「合并」到基线：`git merge-tree --write-tree --merge-base=<mb> <base> <branch>`
        若得到的树 **等于** 基线当前的树 ⇒ 分支带来的净增量已被完全吸收。
        该命令只写对象库、**不动工作区与索引**（需 Git ≥ 2.38；本仓实测 2.55）。

      判据 B（次强：改动行是否还在基线里）
        判据 A 不成立时（多因基线后续又改过同一个文件），取「分支相对 merge-base 的**新增行**」，
        逐行 `git grep -F` 到基线当前内容中，算**存活率**。
        为什么必须看比例：基线后续提交动过同一文件属正常。实测 `fix/301-…`（PR #309 已合并）
        新增 23 行中 9 行仍在 ⇒ 那是噪声；而 `fix/initial-admin-bootstrap` 新增 87 行 **0 行**在基线
        ⇒ 那才是真没落地。
        另有两点例外处理：① 基线**主动删除**的文件判为「被取代」而非丢失；
        ② 锁文件/版本清单/构建产物等生成物（见 -IgnorePath）不参与存活率。

      `git cherry` 只作辅助参考，**不作为判据**：squash 会改变 patch-id，实测它会把已合并的
      `fix/295-unbound-alert-config` 报成 `+`（未合并），也会把已合并的分支报成 `-`。

    分类结果：
      contained           判据 A 成立 ⇒ 可删（高置信）
      patch               判据 A 不成立但存活率 ≥ 阈值 ⇒ 可删（中置信，报告附证据）
      review              存活率 < 阈值 ⇒ **禁止直接删**，列出「哪几行没进基线」
      orphan              无 PR + 内容没落地 + 已落后 ≥ 14 天 ⇒ 疑从未合并，先别删
      has-open-pr         永不进删除清单
      protected-*         当前分支 / worktree 占用 / 命名受保护

    安全默认：
      **默认 dry-run**，只打印命令；必须显式 `-Apply` 才真正删。删除用 `git branch -D`
      （必需：squash 合并的分支用 `-d` 会被 git 拒绝），且只有分类为可删的分支进命令清单。
      远端服务端删除（`-IncludeRemote -Apply -DeleteRemote`）默认关闭。

.PARAMETER Base
    基线分支。默认自动解析（优先 <远端>/master）。本仓有 github（上游）+ origin（Gitee 镜像）
    两个 remote，故**不**用 origin/HEAD 作基线。

.PARAMETER Remote
    期望「已合并」的远端名。默认优先 github，否则 origin。

.PARAMETER IncludeRemote
    一并分析远端跟踪分支（如 github/fix/401-…）。

.PARAMETER Apply
    真正执行删除。不加本开关 = dry-run（只打印）。

.PARAMETER DeleteRemote
    连服务端分支一起删（`git push <remote> --delete`）。须与 -IncludeRemote -Apply 同用。

.PARAMETER Protect
    额外保护的模式（支持通配 `-like`），可重复。

.PARAMETER OlderThanDays
    只分析「最后提交早于 N 天」的分支（0 = 全部分支）。

.PARAMETER LostRatioThreshold
    判据 B 的存活率阈值，默认 0.5。

.PARAMETER IgnorePath
    内容判据忽略的路径正则（默认忽略锁文件、版本清单、构建产物）。

.PARAMETER NoFetch
    不执行 `git fetch --prune`。默认会刷新远端引用，让「远端已删除」可见。

.PARAMETER Format
    Text（默认）/ Markdown（可直接贴 issue）/ Json。

.PARAMETER OutDir
    报告落盘目录（写 branch-cleanup-<时间戳>.md 与 .json）。默认只打印。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1
    powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -Format Markdown -OutDir .tmp-branch-cleanup
    powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -Apply
    powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -IncludeRemote -Apply -DeleteRemote

.NOTES
    退出码：0=无待删；2=有可删候选；3=存在「需人工确认/孤儿」项；1=前置条件不满足。
    本脚本唯一的写操作是 -Apply 时的分支删除；其余全程只读。
#>
[CmdletBinding()]
param(
    [string]$Base,
    [string]$Remote,
    [switch]$IncludeRemote,
    [switch]$Apply,
    [switch]$DeleteRemote,
    [string[]]$Protect = @(),
    [int]$OlderThanDays = 0,
    [double]$LostRatioThreshold = 0.5,
    [string[]]$IgnorePath = @(
        '(^|/)package-lock\.json$',
        '(^|/)pnpm-lock\.yaml$',
        '(^|/)yarn\.lock$',
        '(^|/)pom\.xml$',
        '(^|/)docs-versions\.json$',
        '(^|/)sitemap\.xml$',
        '^docs/.*versions?\.json$',
        '(^|/)target/',
        '(^|/)node_modules/'
    ),
    [switch]$NoFetch,
    [switch]$ReviewChurn,
    [ValidateSet('Text', 'Markdown', 'Json')][string]$Format = 'Text',
    [string]$OutDir,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Continue'
# -RepoRoot 覆盖：自检脚本需要在**合成仓库**上跑同一套判据（否则只能分析本仓、无法构造已知情形）。
$script:Root = if ($RepoRoot) { (Resolve-Path $RepoRoot).Path } else { (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$script:IgnoreRegex = @($IgnorePath | ForEach-Object { [regex]$_ })
$script:DefaultProtect = @('master', 'main', 'develop', 'release/*', 'hotfix/*')
# 基线文件内容缓存（键 = 基线该路径的 blob 哈希）。跨分支复用，避免同一文件被反复 git show。
$script:BaselineTextCache = @{}

# ============================================================ git 基础封装
function Invoke-Git {
    # 返回 @{ Ok; Code; Out }。刻意把 stderr 丢弃：Windows PowerShell 5.1 会把原生程序的
    # stderr 升级成终止错误，故一律只看退出码，不看 stderr 文本。
    param([string[]]$GitArgs)
    $out = @(); $code = -1
    try {
        $out = & git -C $script:Root @GitArgs 2>$null
        $code = $LASTEXITCODE
    } catch {
        $out = @(); $code = -1
    }
    return [pscustomobject]@{ Ok = ($code -eq 0); Code = $code; Out = @($out) }
}

function Get-GitLine {
    param([string[]]$GitArgs)
    $r = Invoke-Git $GitArgs
    if (-not $r.Ok) { return $null }
    $first = @($r.Out | Where-Object { $_ -ne '' } | Select-Object -First 1)
    if ($first.Count -eq 0) { return $null }
    return [string]$first[0]
}

function Test-GitOk {
    param([string[]]$GitArgs)
    return (Invoke-Git $GitArgs).Ok
}

function Resolve-Ref {
    param([string]$Ref)
    return (Get-GitLine @('rev-parse', '--verify', '--quiet', "$Ref^{commit}"))
}

function Get-CommitDate {
    param([string]$Sha)
    $s = Get-GitLine @('show', '-s', '--format=%cI', $Sha)
    if (-not $s) { return $null }
    try { return [datetimeoffset]::Parse($s.Trim()) } catch { return $null }
}

# ============================================================ 基线 / 远端解析
function Resolve-PreferredRemote {
    param([string]$Explicit)
    if ($Explicit) { return $Explicit }
    $remotes = @((Invoke-Git @('remote')).Out | Where-Object { $_ })
    if ($remotes.Count -eq 0) { return $null }
    if ($remotes -contains 'github') { return 'github' }
    if ($remotes -contains 'origin') { return 'origin' }
    return [string]$remotes[0]
}

function Resolve-BaseBranch {
    param([string]$Explicit, [string]$PreferredRemote)
    if ($Explicit) { return $Explicit }
    # 刻意不优先 origin/HEAD：本仓 origin 曾是 Gitee 镜像。
    if ($PreferredRemote) {
        foreach ($cand in @("$PreferredRemote/master", "$PreferredRemote/main")) {
            if (Resolve-Ref $cand) { return $cand }
        }
    }
    foreach ($cand in @('origin/master', 'origin/main', 'master', 'main')) {
        if (Resolve-Ref $cand) { return $cand }
    }
    return $null
}

# ============================================================ 保护 / 忽略
function Test-ProtectedName {
    param([string]$Name)
    foreach ($p in (@($script:DefaultProtect) + @($Protect))) {
        if ([string]::IsNullOrWhiteSpace($p)) { continue }
        if ($Name -like $p) { return $true }
    }
    return $false
}

function Test-IgnoredPath {
    param([string]$Path)
    foreach ($re in $script:IgnoreRegex) { if ($re.IsMatch($Path)) { return $true } }
    return $false
}

function Get-WorktreeBranches {
    # 被 worktree 占用的分支：`git branch -D` 会拒绝，必须排除并单独提示。
    $map = @{}
    $r = Invoke-Git @('worktree', 'list', '--porcelain')
    if (-not $r.Ok) { return $map }
    $path = $null
    foreach ($line in $r.Out) {
        if ($line -like 'worktree *') { $path = $line.Substring(9).Trim() }
        elseif ($line -like 'branch refs/heads/*') {
            $b = $line.Substring('branch refs/heads/'.Length).Trim()
            if ($b) { $map[$b] = $path }
        }
    }
    return $map
}

# ============================================================ 核心：内容判据
function Test-ContainedInBase {
    <#
      返回 @{ Verdict; Ratio; Files; Residual }
        Verdict = 'contained' | 'patch' | 'review' | 'tree-conflict'
        Ratio   = 新增行存活率（1.0 = 全在基线上），null = 无法判定
        Files   = 明细 @( @{Path;NewLines;Found;Kind} )，Kind ∈ partial|deleted-in-base|new-in-branch
    #>
    param([string]$Base, [string]$Branch, [string]$MergeBase)

    $result = @{ Verdict = 'tree-conflict'; Ratio = $null; Files = @(); Residual = @(); Mergeable = $false }

    # 分支就是 merge-base 本身 ⇒ 无净增量
    if ($MergeBase -eq $Branch) { $result.Verdict = 'contained'; $result.Ratio = 1.0; return $result }

    $mt = Invoke-Git @('merge-tree', '--write-tree', "--merge-base=$MergeBase", $Base, $Branch)
    $mergedTree = $null
    if ($mt.Ok) {
        $cand = @($mt.Out | Where-Object { $_ -match '^[0-9a-f]{40,64}$' } | Select-Object -First 1)
        if ($cand.Count -gt 0) { $mergedTree = [string]$cand[0] }
    }

    if ($mergedTree) {
        $result.Mergeable = $true
        $baseTree = Get-GitLine @('rev-parse', "$Base^{tree}")
        if ($baseTree -and $mergedTree -eq $baseTree) {
            $result.Verdict = 'contained'; $result.Ratio = 1.0; return $result
        }
        # 合并树 vs 基线树 = 分支相对**当前基线**的剩余增量
        $residual = @((Invoke-Git @('diff', '--name-only', $Base, $mergedTree)).Out | Where-Object { $_ })
    } else {
        # merge-tree 冲突（或 git 过旧）：退化为「分支相对 merge-base 改了哪些文件」
        $residual = @((Invoke-Git @('diff', '--name-only', "$MergeBase..$Branch")).Out | Where-Object { $_ })
    }

    $residual = @($residual | Where-Object { -not (Test-IgnoredPath $_) })
    $result.Residual = $residual
    if ($residual.Count -eq 0) { $result.Verdict = 'patch'; $result.Ratio = 1.0; return $result }

    $findings = @()
    $totalChg = 0; $totalOk = 0
    $zeroSurvivorFiles = 0     # 整份文件零存活 = 「内容没落地」的强信号
    $newFilesMissing = 0       # 分支新建的文件，基线里没有
    $partialFiles = 0          # 部分存活 = 多半是后续 PR 改写过同一段（弱信号）

    # 性能：判据 B 原本是「每行一次 git grep」，在 80+ 分支上是几十万次子进程（实测跑不完）。
    # 改为「每个文件一次 git show 取基线内容 → 内存子串查找」，并在 blob 完全相同时走快路径。
    # 内容按 **blob 哈希**缓存到脚本作用域：多个分支上的同一文件在基线里是同一 blob
    # ⇒ 整个仓库只取一次（实测这是主要耗时项）。

    foreach ($p in $residual) {
        if (-not (Test-GitOk @('cat-file', '-e', "${Base}:$p"))) {
            if (Test-GitOk @('cat-file', '-e', "${MergeBase}:$p")) {
                # 基线**主动删除**了该文件 ⇒ 被取代，不是丢内容
                $findings += @{ Path = $p; Added = 0; AddedFound = 0; Removed = 0; RemovedGone = 0; Kind = 'deleted-in-base' }
            } else {
                # 分支新建的文件，基线里没有 ⇒ 最强信号：整个文件都没进基线
                $newFilesMissing++
                $findings += @{ Path = $p; Added = 0; AddedFound = 0; Removed = 0; RemovedGone = 0; Kind = 'new-in-branch' }
            }
            continue
        }

        # 快路径：基线该文件的 blob 与 merge-base 时完全一致 ⇒ 分支这部分改动必然原样在内。
        $baseBlob = Get-GitLine @('rev-parse', "${Base}:$p")
        $mbBlob = Get-GitLine @('rev-parse', "${MergeBase}:$p")
        if ($baseBlob -and $mbBlob -and $baseBlob -eq $mbBlob) {
            $diffTxt = @((Invoke-Git @('diff', "$MergeBase..$Branch", '--', $p)).Out)
            $nAdd = @($diffTxt | Where-Object { $_ -like '+*' -and $_ -notlike '+++*' }).Count
            $nDel = @($diffTxt | Where-Object { $_ -like '-*' -and $_ -notlike '---*' }).Count
            $totalChg += $nAdd + $nDel; $totalOk += $nAdd + $nDel
            continue
        }

        # 判据是**双向**的：新增行要在基线里，删除行要**不在**基线里。
        # 只看新增行会把「纯删除型重构」误判成「内容没落地」（实测 chore/327-remove-boundary-layer
        # 是 PR #332 的纯删除重构，只看新增行会得 0%）。
        $diffAll = @((Invoke-Git @('diff', "$MergeBase..$Branch", '--', $p)).Out)
        # ⚠️ 行长阈值刻意取 3（而不是 8）：自检实测「>8」会把 `F-branch` 这类短行整条滤掉，
        # 于是该文件被当成「无改动」、零存活信号丢失。短行同样可能是关键改动。
        $added = @($diffAll | Where-Object { $_ -like '+*' -and $_ -notlike '+++*' } |
            ForEach-Object { $_.Substring(1) } | Where-Object { $_.Trim().Length -ge 3 } | Sort-Object -Unique)
        $removed = @($diffAll | Where-Object { $_ -like '-*' -and $_ -notlike '---*' } |
            ForEach-Object { $_.Substring(1) } | Where-Object { $_.Trim().Length -ge 3 } | Sort-Object -Unique)
        if (($added.Count + $removed.Count) -eq 0) { continue }

        if (-not $script:BaselineTextCache.ContainsKey($baseBlob)) {
            $r = Invoke-Git @('show', "${Base}:$p")
            $script:BaselineTextCache[$baseBlob] = if ($r.Ok) { (@($r.Out) -join "`n") } else { '' }
        }
        $text = [string]$script:BaselineTextCache[$baseBlob]

        $addedFound = 0
        foreach ($line in $added) { if ($text.Contains($line)) { $addedFound++ } }
        $removedGone = 0
        foreach ($line in $removed) { if (-not $text.Contains($line)) { $removedGone++ } }

        $fileTotal = $added.Count + $removed.Count
        $fileOk = $addedFound + $removedGone
        $totalChg += $fileTotal; $totalOk += $fileOk

        if ($fileOk -eq 0) {
            # 整份文件的改动一行都没落在基线里 ⇒ 才判「内容没落地」
            $zeroSurvivorFiles++
            $findings += @{
                Path = $p; Added = $added.Count; AddedFound = 0
                Removed = $removed.Count; RemovedGone = 0; Kind = 'lost'
            }
        } elseif ($fileOk -lt $fileTotal) {
            $partialFiles++
            $findings += @{
                Path = $p; Added = $added.Count; AddedFound = $addedFound
                Removed = $removed.Count; RemovedGone = $removedGone; Kind = 'partial'
            }
        }
    }

    $result.Files = $findings
    $result.Ratio = if ($totalChg -gt 0) { [double]$totalOk / [double]$totalChg } else { 1.0 }
    $result.ZeroSurvivorFiles = $zeroSurvivorFiles
    $result.NewFilesMissing = $newFilesMissing
    $result.PartialFiles = $partialFiles

    # 判定口径（**宁松勿误报**：只有「整份文件零存活」或「整份新文件没进基线」才算内容没落地）：
    #   实测反例 fix/340-actuator-auth-chain：某文件新增 6 行只活 3 行 —— 那是后续 PR 改写过同一段，
    #   不是丢失；把它当「需人工确认」会让清理清单全是噪声。
    if ($zeroSurvivorFiles -gt 0 -or $newFilesMissing -gt 0) { $result.Verdict = 'review' }
    elseif ($partialFiles -gt 0) { $result.Verdict = 'churn' }
    else { $result.Verdict = 'patch' }
    return $result
}

# ============================================================ PR 台账（gh，可选）
function Get-PullRequestIndex {
    $idx = @{}
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { return $idx }
    $url = Get-GitLine @('remote', 'get-url', $script:Remote)
    if (-not $url) { return $idx }
    $repo = $null
    if ($url -match 'github\.com[:/](?<o>[^/]+)/(?<r>[^/.]+)') { $repo = "$($Matches['o'])/$($Matches['r'])" }
    if (-not $repo) { return $idx }
    try {
        $json = & gh pr list --repo $repo --state all --limit 500 --json number,headRefName,state,mergedAt,url 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $json) { return $idx }
        $prs = $json | ConvertFrom-Json
    } catch { return $idx }
    foreach ($pr in @($prs)) {
        $name = [string]$pr.headRefName
        if (-not $name) { continue }
        if (-not $idx.ContainsKey($name)) { $idx[$name] = @() }
        $idx[$name] = @($idx[$name]) + $pr
    }
    Write-Host ("  （已读取 GitHub PR 台账：{0} 个 head 分支）" -f $idx.Count) -ForegroundColor DarkGray
    return $idx
}

# ============================================================ 单分支分析
function Analyze-Branch {
    param(
        [string]$Name,
        [string]$Ref,
        [string]$Base,
        [string]$Kind,          # local | remote
        [hashtable]$PrIndex,
        [hashtable]$Worktrees,
        [string]$CurrentBranch
    )

    $sha = Resolve-Ref $Ref
    if (-not $sha) { return $null }
    $mb = Get-GitLine @('merge-base', $Base, $sha)
    if (-not $mb) { return $null }

    $ahead = 0
    $cnt = Get-GitLine @('rev-list', '--count', "$Base..$sha")
    if ($cnt) { $ahead = [int]$cnt }

    $isAncestor = Test-GitOk @('merge-base', '--is-ancestor', $sha, $Base)

    $verdict = 'contained'; $ratio = 1.0; $files = @(); $residual = @(); $c = $null
    if (-not $isAncestor -and $ahead -gt 0) {
        $c = Test-ContainedInBase -Base $Base -Branch $sha -MergeBase $mb
        $verdict = $c.Verdict; $ratio = $c.Ratio; $files = @($c.Files); $residual = @($c.Residual)
    }

    $cherryUnmerged = $null
    if ($ahead -gt 0 -and $ahead -le 50) {
        $cherryUnmerged = @((Invoke-Git @('cherry', $Base, $sha)).Out | Where-Object { $_ -like '+ *' }).Count
    }

    $prs = if ($PrIndex.ContainsKey($Name)) { @($PrIndex[$Name]) } else { @() }
    $openPr = @($prs | Where-Object { $_.state -eq 'OPEN' })
    $mergedPr = @($prs | Where-Object { $_.mergedAt })
    $prNumbers = @(@($prs | ForEach-Object { $_.number }) | Sort-Object -Unique)

    $upstream = Get-GitLine @('rev-parse', '--abbrev-ref', '--symbolic-full-name', "$Ref@{upstream}")
    $upstreamGone = $false
    if ($upstream -and $Kind -eq 'local' -and -not (Resolve-Ref $upstream)) { $upstreamGone = $true }

    $date = Get-CommitDate $sha
    $ageDays = $null
    if ($date) { $ageDays = [int]([datetimeoffset]::Now - $date).TotalDays }

    $isCurrent = ($Kind -eq 'local' -and $Name -eq $CurrentBranch)
    $inWorktree = ($Kind -eq 'local' -and $Worktrees.ContainsKey($Name))

    if ($isCurrent) { $bucket = 'protected-current' }
    elseif ($inWorktree) { $bucket = 'protected-worktree' }
    elseif (Test-ProtectedName $Name) { $bucket = 'protected-pattern' }
    elseif ($openPr.Count -gt 0) { $bucket = 'has-open-pr' }
    elseif ($ahead -eq 0 -and $isAncestor) { $bucket = 'contained' }
    elseif ($verdict -eq 'contained') { $bucket = 'contained' }
    elseif ($verdict -eq 'patch') { $bucket = 'patch' }
    elseif ($verdict -eq 'churn') { $bucket = 'churn' }   # 改动已进基线、但后续被改写 ⇒ 可删（报告附注）
    elseif ($verdict -eq 'review') {
        $bucket = if ($ageDays -ne $null -and $ageDays -ge 14) { 'orphan' } else { 'review' }
    } else { $bucket = 'review' }

    return [pscustomobject]@{
        Name = $Name; Kind = $Kind; Ref = $Ref
        Sha = $sha; Short = $sha.Substring(0, 8)
        MergeBase = $mb; Ahead = $ahead; Ancestor = $isAncestor
        Verdict = $verdict; Ratio = $ratio; Files = @($files); Residual = @($residual)
        ZeroSurvivorFiles = $(if ($c) { $c.ZeroSurvivorFiles } else { 0 })
        NewFilesMissing = $(if ($c) { $c.NewFilesMissing } else { 0 })
        PartialFiles = $(if ($c) { $c.PartialFiles } else { 0 })
        CherryUnmerged = $cherryUnmerged
        OpenPr = @($openPr); MergedPr = @($mergedPr); PrNumbers = @($prNumbers)
        Upstream = $upstream; UpstreamGone = $upstreamGone
        Date = $date; AgeDays = $ageDays
        IsCurrent = $isCurrent; InWorktree = $inWorktree
        Bucket = $bucket
    }
}

# ============================================================ 报告小工具
function Format-Duration {
    param($Days)
    if ($null -eq $Days) { return '—' }
    if ($Days -lt 1) { return '今天' }
    if ($Days -lt 30) { return "$Days 天" }
    if ($Days -lt 365) { return "$([math]::Round($Days / 30.0, 1)) 月" }
    return "$([math]::Round($Days / 365.0, 1)) 年"
}

function Format-Ratio {
    param($Ratio)
    if ($null -eq $Ratio) { return '—' }
    return ('{0:P0}' -f [double]$Ratio)
}

function Format-Evidence {
    param($Item)
    if ($Item.Bucket -eq 'contained') { return '判据A(树等价)' }
    if ($Item.Ratio -ne $null) {
        if ($Item.Bucket -eq 'churn') { return ('判据B(已在内,存活 {0},后续被改写)' -f (Format-Ratio $Item.Ratio)) }
        return ('判据B(存活 {0})' -f (Format-Ratio $Item.Ratio))
    }
    return '判据B(未判定)'
}

function Get-DeleteCommands {
    param($Items, [switch]$RemoteServer)
    $cmds = @()
    foreach ($i in $Items) {
        if ($RemoteServer) { $cmds += "git push $script:Remote --delete $($i.Name)" }
        else { $cmds += "git branch -D $($i.Name)" }
    }
    return $cmds
}

function Convert-BranchToJson {
    param($B)
    return [ordered]@{
        name           = $B.Name
        kind           = $B.Kind
        sha            = $B.Sha
        ahead          = $B.Ahead
        bucket         = $B.Bucket
        verdict        = $B.Verdict
        survivedRatio  = $B.Ratio
        zeroSurvivorFiles = $B.ZeroSurvivorFiles
        newFilesMissing   = $B.NewFilesMissing
        ancestorOfBase = $B.Ancestor
        cherryUnmerged = $B.CherryUnmerged
        prs            = @($B.PrNumbers)
        openPr         = @($B.OpenPr | ForEach-Object { $_.number })
        upstream       = $B.Upstream
        upstreamGone   = $B.UpstreamGone
        lastCommit     = if ($B.Date) { $B.Date.ToString('o') } else { $null }
        ageDays        = $B.AgeDays
        evidence       = @($B.Files | ForEach-Object {
                [ordered]@{
                    path = $_.Path; added = $_.Added; addedFound = $_.AddedFound
                    removed = $_.Removed; removedGone = $_.RemovedGone; kind = $_.Kind
                }
            })
    }
}

# ============================================================ 主流程
if (-not (Test-Path (Join-Path $script:Root '.git'))) {
    Write-Host "❌ 不是 git 仓库（或 .git 不可见）：$($script:Root)" -ForegroundColor Red
    exit 1
}

$script:Remote = Resolve-PreferredRemote $Remote
$baseBranch = Resolve-BaseBranch -Explicit $Base -PreferredRemote $script:Remote
if (-not $baseBranch -or -not (Resolve-Ref $baseBranch)) {
    Write-Host "❌ 无法解析基线分支（-Base '$Base'）。可先 git fetch --prune，或用 -Base <远端/分支> 指定。" -ForegroundColor Red
    exit 1
}

if (-not $NoFetch) {
    foreach ($rm in @(@($script:Remote) + @('origin')) | Where-Object { $_ } | Select-Object -Unique) {
        Write-Host "  (git fetch $rm --prune)" -ForegroundColor DarkGray
        Invoke-Git @('fetch', $rm, '--prune', '--quiet') | Out-Null
    }
}
$baseSha = Resolve-Ref $baseBranch   # fetch 后基线可能已前移

$currentBranch = Get-GitLine @('rev-parse', '--abbrev-ref', 'HEAD')
$worktrees = Get-WorktreeBranches
$prIndex = Get-PullRequestIndex

Write-Host ("分支清理分析中…（基线 {0} @ {1}；模式 {2}）" -f `
    $baseBranch, $baseSha.Substring(0, 8), $(if ($Apply) { 'APPLY（会真删）' } else { 'dry-run' })) -ForegroundColor Cyan

$refsToScan = @()
$refsToScan += @((Invoke-Git @('for-each-ref', '--format=%(refname:short)%09%(refname)', 'refs/heads/')).Out | Where-Object { $_ })
if ($IncludeRemote) {
    $refsToScan += @((Invoke-Git @('for-each-ref', '--format=%(refname:short)%09%(refname)', "refs/remotes/$script:Remote/")).Out | Where-Object { $_ })
}

$reports = @()
$scanWatch = [System.Diagnostics.Stopwatch]::StartNew()
$scanned = 0
foreach ($line in $refsToScan) {
    $parts = [string]$line -split "`t", 2
    if ($parts.Count -lt 2) { continue }
    $shortName = $parts[0].Trim()
    $fullRef = $parts[1].Trim()
    if ($fullRef -eq "refs/remotes/$script:Remote/HEAD") { continue }

    $kind = if ($fullRef -like 'refs/heads/*') { 'local' } else { 'remote' }
    $display = $shortName
    if ($kind -eq 'remote' -and $shortName -like "$script:Remote/*") {
        $display = $shortName.Substring($script:Remote.Length + 1)
    }
    if ($OlderThanDays -gt 0) {
        $d = Get-CommitDate (Resolve-Ref $fullRef)
        if ($d -and ([datetimeoffset]::Now - $d).TotalDays -lt $OlderThanDays) { continue }
    }

    $scanned++
    Write-Host ("  [{0,3}/{1,3}] {2,-50} …" -f $scanned, $refsToScan.Count, $display) -ForegroundColor DarkGray
    $rep = Analyze-Branch -Name $display -Ref $fullRef -Base $baseBranch -Kind $kind `
        -PrIndex $prIndex -Worktrees $worktrees -CurrentBranch $currentBranch
    if ($rep) {
        $reports += $rep
        Write-Host ("            => {0}{1}" -f $rep.Bucket,
            $(if ($rep.Ratio -ne $null) { "  存活率=$(Format-Ratio $rep.Ratio)" } else { '' })) -ForegroundColor DarkGray
    }
}
Write-Host ("  扫描完成：{0} 个分支，耗时 {1:N1}s" -f $scanned, $scanWatch.Elapsed.TotalSeconds) -ForegroundColor DarkGray

$localBranchReports = @($reports | Where-Object { $_.Kind -eq 'local' })
$remoteBranchReports = @($reports | Where-Object { $_.Kind -eq 'remote' })

$toDeleteLocal = @($localBranchReports | Where-Object { $_.OpenPr.Count -eq 0 -and ($_.Bucket -eq 'contained' -or $_.Bucket -eq 'patch' -or $_.Bucket -eq 'churn') })
$toDeleteRemote = @()
if ($IncludeRemote) {
    $toDeleteRemote = @($remoteBranchReports | Where-Object { $_.OpenPr.Count -eq 0 -and ($_.Bucket -eq 'contained' -or $_.Bucket -eq 'patch' -or $_.Bucket -eq 'churn') })
}
$reviewItems = @($reports | Where-Object { $_.Bucket -eq 'review' -or $_.Bucket -eq 'orphan' })
$churnItems = @($reports | Where-Object { $_.Bucket -eq 'churn' })
$openPrItems = @($reports | Where-Object { $_.Bucket -eq 'has-open-pr' })

$dupGroups = @($reports | Where-Object { $_.Sha } | Group-Object Sha | Where-Object { $_.Count -gt 1 } | Sort-Object Count -Descending)

# 谱系异常（只对**远端**分支判）：分支头与基线**各有独有提交**（既领先又落后）。
# ⚠️ 三个判据陷阱（本项实测连续踩了三次，务必按下面的口径）：
#   ① 不能只判「不是基线的后代」——squash 合并过的分支必然不是基线后代（提交被重写了），
#      那样会把 26/27 个远端分支全标异常。
#   ② 要先排除「已判定为可删」的分支：它们的改动已经在基线里（判据 A/B），谱系分歧无意义。
#   ③ 不能把本地分支一起判：本地分支落后是常态，会得出「99 个异常」这种噪声结论。
$divergent = @()
foreach ($r in $remoteBranchReports) {
    if ($r.Ahead -eq 0) { continue }                                     # 无独有提交 ⇒ 不是
    if ($r.Bucket -eq 'contained' -or $r.Bucket -eq 'patch' -or $r.Bucket -eq 'churn') { continue }  # 判定②
    $behind = 0
    $cnt = Get-GitLine @('rev-list', '--count', "$($r.Sha)..$baseSha")
    if ($cnt) { $behind = [int]$cnt }
    if ($behind -gt 0) { $divergent += $r }                              # 两边各有独有提交 ⇒ 独立谱系
}
$divergentRemote = $divergent

# ---- 后续开发提示 ---------------------------------------------------------
$hints = @()
$containedNoPr = @($localBranchReports | Where-Object { $_.Bucket -eq 'contained' -and $_.OpenPr.Count -eq 0 -and $_.PrNumbers.Count -eq 0 })
if ($containedNoPr.Count -gt 0) {
    $hints += ("有 {0} 个本地分支的内容已在基线、但查不到对应 PR 记录 —— 合并走的是本机操作，或 PR 已不可见。" -f $containedNoPr.Count)
    $hints += "  建议：合并一律经 PR，否则半年后无法回答「这条线为什么在主线里」。"
}
if ($reviewItems.Count -gt 0) {
    $hints += ("{0} 个分支有「整份改动没进基线」的强信号（报告第 3 节已列到文件）。" -f $reviewItems.Count)
    $hints += "  处置顺序：逐个确认这段改动今天还要不要 → 要 ⇒ 重新开分支补 PR；不要 ⇒ 删分支并在 PR/issue 写明放弃理由。"
    $hints += "  ⚠️ 直接删会静默丢掉这些内容，此后无人知道曾经有过这个改动。"
}
if ($dupGroups.Count -gt 0) {
    $hints += ("发现 {0} 组「多个分支指向同一 commit」—— 历次合并后没做本地清理的残留。" -f $dupGroups.Count)
    $hints += "  建议：合并 PR 时勾选 GitHub 的 Delete branch + 本地 git fetch --prune，并定期跑本脚本。"
}
$topicGroups = @($toDeleteLocal | Where-Object { $_.Name -match '^(?<t>[a-z]+)/' } | Group-Object { $_.Name.Split('/')[0] } | Where-Object { $_.Count -ge 3 })
if ($topicGroups.Count -gt 0) {
    $hints += ("待清分支的主题分布：{0} —— 本轮工作集中在这些主题。" -f (($topicGroups | ForEach-Object { "$($_.Name)/ ×$($_.Count)" }) -join '；'))
}
$hints += "分支命名建议带 issue 号（本仓惯例 fix/401-…）：脚本与 PR 都能直接对回台账，避免出现「无 PR 记录的已合并分支」。"
$stale = @($reviewItems | Where-Object { $_.AgeDays -ne $null -and $_.AgeDays -gt 90 })
if ($stale.Count -gt 0) {
    $hints += ("上述待确认项里有 {0} 个分支已停更 90 天以上 —— 趁上下文还在时尽快定夺，越往后越贵。" -f $stale.Count)
}
$goneUpstream = @($localBranchReports | Where-Object { $_.UpstreamGone })
if ($goneUpstream.Count -gt 0) {
    $hints += ("{0} 个本地分支的上游已被删除（远端已清）—— 这类分支的跟踪关系已断，可一并清。" -f $goneUpstream.Count)
}

# ---- 文本报告 -------------------------------------------------------------
function Get-TextReport {
    $sb = New-Object System.Text.StringBuilder
    $w = { param($s) [void]$sb.AppendLine([string]$s) }

    & $w ""
    & $w "================ 分支清理分析 ================"
    & $w ("仓库      ：{0}" -f $script:Root)
    & $w ("基线      ：{0} @ {1}" -f $baseBranch, $baseSha.Substring(0, 8))
    & $w ("期望远端  ：{0}    扫描范围：本地 {1} / 远端 {2}" -f $script:Remote, $localBranchReports.Count, $remoteBranchReports.Count)
    & $w ("判据      ：A = merge-tree 结果树 == 基线树；B = 分支新增行在基线中的存活率 ≥ {0:P0}" -f $LostRatioThreshold)
    & $w ""
    & $w "▶ 本地分支分布"
    foreach ($row in @(
            @{ T = '已合并(判据A)        可删'; B = 'contained' },
            @{ T = '内容已在主线(判据B)  可删'; B = 'patch' },
            @{ T = '已在内(后续被改写)   可删'; B = 'churn' },
            @{ T = '需人工确认           禁删'; B = 'review' },
            @{ T = '孤儿提交             禁删'; B = 'orphan' },
            @{ T = '有开放 PR            保留'; B = 'has-open-pr' })) {
        & $w ("   {0,-28} {1}" -f $row.T, @($localBranchReports | Where-Object { $_.Bucket -eq $row.B }).Count)
    }
    & $w ("   {0,-28} {1}" -f '受保护               保留', @($localBranchReports | Where-Object { $_.Bucket -like 'protected-*' }).Count)
    if ($IncludeRemote) {
        & $w ("▶ 远端跟踪分支：可删 {0}；谱系异常 {1}（既不含基线、也不被基线包含）" -f $toDeleteRemote.Count, $divergentRemote.Count)
    }
    & $w ""
    & $w "▶ 可删清单（本地）"
    if ($toDeleteLocal.Count -eq 0) { & $w "   （无）" }
    foreach ($i in (@($toDeleteLocal | Where-Object { $_.Bucket -ne 'churn' }) | Sort-Object AgeDays -Descending)) {
        & $w ("   {0,-50} ahead={1,-2} {2} 最后提交={3} PR={4}" -f `
                $i.Name, $i.Ahead, (Format-Evidence $i), (Format-Duration $i.AgeDays),
            $(if ($i.PrNumbers.Count) { '#' + ($i.PrNumbers -join ',#') } else { '—' }))
    }
    $churnLocal = @($toDeleteLocal | Where-Object { $_.Bucket -eq 'churn' })
    if ($churnLocal.Count -gt 0) {
        & $w ""
        & $w ("▶ 可删清单（本地，需留意：{0} 个 —— 改动已进基线，但同文件后续被改写过）" -f $churnLocal.Count)
        foreach ($i in ($churnLocal | Sort-Object AgeDays -Descending)) {
            & $w ("   {0,-50} ahead={1,-2} {2} PR={3}" -f `
                    $i.Name, $i.Ahead, (Format-Evidence $i),
                $(if ($i.PrNumbers.Count) { '#' + ($i.PrNumbers -join ',#') } else { '—' }))
        }
        & $w "   （这类是「合并成功 + 主线后来改过同一段」，正常清理即可；如不放心可用 -ReviewChurn 单独看明细）"
        if ($ReviewChurn) {
            foreach ($i in $churnLocal) {
                & $w ("     · {0}" -f $i.Name)
                foreach ($fl in @($i.Files | Where-Object { $_.Kind -eq 'partial' } | Select-Object -First 4)) {
                    & $w ("        - {0}：新增 {1}/{2} 仍在基线；删除 {3}/{4} 已从基线移除" -f `
                            $fl.Path, $fl.AddedFound, $fl.Added, $fl.RemovedGone, $fl.Removed)
                }
            }
        }
    }
    if ($IncludeRemote -and $toDeleteRemote.Count -gt 0) {
        & $w ""
        & $w "▶ 可删清单（远端跟踪）"
        foreach ($i in ($toDeleteRemote | Sort-Object AgeDays -Descending)) {
            & $w ("   {0,-50} ahead={1,-2} {2} 最后提交={3}" -f $i.Name, $i.Ahead, (Format-Evidence $i), (Format-Duration $i.AgeDays))
        }
    }
    & $w ""
    & $w "▶ ⚠️ 需人工确认 / 孤儿（不要直接删，先看证据）"
    if ($reviewItems.Count -eq 0) { & $w "   （无）" }
    foreach ($i in ($reviewItems | Sort-Object Ratio)) {
        & $w ("   [{0}] {1}   ahead={2} 存活率={3} PR={4}" -f $i.Bucket, $i.Name, $i.Ahead, (Format-Ratio $i.Ratio),
            $(if ($i.PrNumbers.Count) { '#' + ($i.PrNumbers -join ',#') } else { '无' }))
        foreach ($fl in @($i.Files | Where-Object { $_.Kind -ne 'partial' } | Select-Object -First 6)) {
            switch ($fl.Kind) {
                'new-in-branch' { & $w ("        - {0}（**分支新建的文件，基线里没有**）" -f $fl.Path) }
                'lost' { & $w ("        - {0}（整份改动零存活：新增 {1} / 删除 {2}）" -f $fl.Path, $fl.Added, $fl.Removed) }
                'deleted-in-base' { & $w ("        - {0}（基线已删除，视为被取代）" -f $fl.Path) }
                default { & $w ("        - {0}（{1}）" -f $fl.Path, $fl.Kind) }
            }
        }
    }
    & $w ""
    & $w "▶ 重复分支（heads 指向同一 commit，可整组清）"
    if ($dupGroups.Count -eq 0) { & $w "   （无）" }
    foreach ($g in $dupGroups) {
        & $w ("   {0}  ← {1}" -f $g.Group[0].Short, (($g.Group | ForEach-Object { $_.Name }) -join '  |  '))
    }
    if ($openPrItems.Count -gt 0) {
        & $w ""
        & $w "▶ 有开放 PR（永不进删除清单）"
        foreach ($i in $openPrItems) {
            & $w ("   {0}  → PR #{1}" -f $i.Name, (($i.OpenPr | ForEach-Object { $_.number }) -join ',#'))
        }
    }
    if ($divergent.Count -gt 0) {
        & $w ""
        & $w ("▶ 谱系异常：既不含基线、也不被基线包含（{0} 个；删前确认基线，别误删另一条发布线）" -f $divergent.Count)
        foreach ($i in $divergent) { & $w ("   [{0}] {1,-48} ahead={2} 最后提交={3}" -f $i.Kind, $i.Name, $i.Ahead, (Format-Duration $i.AgeDays)) }
    }
    & $w ""
    & $w "================ 后续开发提示 ================"
    foreach ($h in $hints) { & $w ("   · {0}" -f $h) }
    & $w ""
    & $w "================ 执行命令（确认后再跑） ================"
    & $w "   # 本地（-D 是必需的：squash 合并的分支用 -d 会被 git 拒绝）"
    $cl = Get-DeleteCommands $toDeleteLocal
    if ($cl.Count -eq 0) { & $w "   # （无）" } else { $cl | ForEach-Object { & $w ("   {0}" -f $_) } }
    if ($IncludeRemote -and $toDeleteRemote.Count -gt 0) {
        & $w ""
        & $w "   # 远端（在服务端删除分支）"
        (Get-DeleteCommands $toDeleteRemote -RemoteServer) | ForEach-Object { & $w ("   {0}" -f $_) }
    }
    & $w ""
    & $w ("   或由本脚本一键执行：powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -Apply")
    return $sb.ToString()
}

# ---- Markdown 报告 --------------------------------------------------------
function Get-MarkdownReport {
    $sb = New-Object System.Text.StringBuilder
    $w = { param($s) [void]$sb.AppendLine([string]$s) }

    & $w "# 分支清理报告（dry-run）"
    & $w ""
    & $w ("- 生成时间：{0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
    & $w ("- 仓库：``{0}``" -f $script:Root)
    & $w ("- 基线：``{0}`` @ ``{1}``" -f $baseBranch, $baseSha.Substring(0, 8))
    & $w ("- 判据 A：``git merge-tree --write-tree`` 的结果树 == 基线树")
    & $w ("- 判据 B：分支新增行在基线中的存活率 ≥ {0:P0}" -f $LostRatioThreshold)
    & $w ""
    & $w "## 1. 概览"
    & $w ""
    & $w "| 分类 | 本地 | 远端 |"
    & $w "| --- | --- | --- |"
    # 概览表：逐行显式统计（刻意不用动态 scriptblock：ConstrainedLanguage 下会被拦）
    $ovLocal = [ordered]@{
        'contained'    = @($localBranchReports | Where-Object { $_.Bucket -eq 'contained' }).Count
        'patch'        = @($localBranchReports | Where-Object { $_.Bucket -eq 'patch' }).Count
        'churn'        = @($localBranchReports | Where-Object { $_.Bucket -eq 'churn' }).Count
        'review'       = @($localBranchReports | Where-Object { $_.Bucket -eq 'review' }).Count
        'orphan'       = @($localBranchReports | Where-Object { $_.Bucket -eq 'orphan' }).Count
        'has-open-pr'  = @($localBranchReports | Where-Object { $_.Bucket -eq 'has-open-pr' }).Count
        'protected-*'  = @($localBranchReports | Where-Object { $_.Bucket -like 'protected-*' }).Count
    }
    $ovRemote = [ordered]@{
        'contained'    = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'contained' }).Count
        'patch'        = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'patch' }).Count
        'churn'        = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'churn' }).Count
        'review'       = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'review' }).Count
        'orphan'       = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'orphan' }).Count
        'has-open-pr'  = @($remoteBranchReports | Where-Object { $_.Bucket -eq 'has-open-pr' }).Count
        'protected-*'  = @($remoteBranchReports | Where-Object { $_.Bucket -like 'protected-*' }).Count
    }
    foreach ($b in @(
            @{ K = 'contained'; T = '已合并（判据 A，可删）' },
            @{ K = 'patch'; T = '内容已在主线（判据 B，可删）' },
            @{ K = 'churn'; T = '已在内、后续被改写（可删，附证据）' },
            @{ K = 'review'; T = '需人工确认（整份改动零存活）' },
            @{ K = 'orphan'; T = '孤儿提交（疑从未合并）' },
            @{ K = 'has-open-pr'; T = '有开放 PR' },
            @{ K = 'protected-*'; T = '受保护' })) {
        & $w ("| {0} | {1} | {2} |" -f $b.T, $ovLocal[$b.K], $ovRemote[$b.K])
    }
    & $w ""
    & $w "## 2. 可删清单（本地）"
    & $w ""
    & $w "| 分支 | ahead | 证据 | 最后提交 | 对应 PR |"
    & $w "| --- | --- | --- | --- | --- |"
    if ($toDeleteLocal.Count -eq 0) { & $w "| （无） | | | | |" }
    foreach ($i in ($toDeleteLocal | Sort-Object AgeDays -Descending)) {
        & $w ("| ``{0}`` | {1} | {2} | {3} | {4} |" -f $i.Name, $i.Ahead, (Format-Evidence $i), (Format-Duration $i.AgeDays),
            $(if ($i.PrNumbers.Count) { '#' + ($i.PrNumbers -join ',#') } else { '—' }))
    }
    & $w ""
    if ($toDeleteRemote.Count -gt 0) {
        & $w "## 2b. 可删清单（远端）"
        & $w ""
        & $w "| 分支 | ahead | 证据 | 最后提交 |"
        & $w "| --- | --- | --- | --- |"
        foreach ($i in ($toDeleteRemote | Sort-Object AgeDays -Descending)) {
            & $w ("| ``{0}`` | {1} | {2} | {3} |" -f $i.Name, $i.Ahead, (Format-Evidence $i), (Format-Duration $i.AgeDays))
        }
        & $w ""
    }
    & $w "## 3. 有问题 / 需人工确认（**不要直接删**）"
    & $w ""
    if ($reviewItems.Count -eq 0) { & $w "无。" }
    foreach ($i in ($reviewItems | Sort-Object Ratio)) {
        & $w ("### [{0}] ``{1}``" -f $i.Bucket, $i.Name)
        & $w ""
        & $w ("- 相对基线领先 {0} 个提交；最后提交 {1}；PR 记录：{2}" -f $i.Ahead, (Format-Duration $i.AgeDays),
            $(if ($i.PrNumbers.Count) { '#' + ($i.PrNumbers -join ',#') } else { '（无）' }))
        & $w ("- 改动存活率：**{0}**（口径：整份文件零存活，或分支新建的文件在基线里不存在）" -f (Format-Ratio $i.Ratio))
        & $w ("- 领先提交：")
        foreach ($c in @((Invoke-Git @('log', '--oneline', '--no-decorate', "$baseBranch..$($i.Sha)")).Out | Select-Object -First 5)) {
            & $w ("  - ``{0}``" -f $c)
        }
        if ($i.Files.Count -gt 0) {
            & $w ""
            & $w "  | 文件 | 新增行(仍在基线) | 删除行(已移除) | 判定 |"
            & $w "  | --- | --- | --- | --- |"
            foreach ($f in @($i.Files | Select-Object -First 12)) {
                & $w ("  | ``{0}`` | {1}/{2} | {3}/{4} | {5} |" -f $f.Path, $f.AddedFound, $f.Added, $f.RemovedGone, $f.Removed, $f.Kind)
            }
        }
        & $w ""
        & $w "- 处置建议：先确认这段改动今天是否仍需；需要 ⇒ 重新起分支补 PR；不需要 ⇒ 删除并在 PR/issue 写明放弃理由。"
        & $w ""
    }
    & $w "## 4. 重复分支（指向同一 commit）"
    & $w ""
    if ($dupGroups.Count -eq 0) { & $w "无。" }
    foreach ($g in $dupGroups) {
        & $w ("- ``{0}``：{1}" -f $g.Group[0].Short, (($g.Group | ForEach-Object { '`' + $_.Name + '`' }) -join '、'))
    }
    & $w ""
    if ($divergent.Count -gt 0) {
        & $w "## 5. 谱系异常（既不含基线、也不被基线包含；删前确认基线）"
        & $w ""
        & $w "| 类型 | 分支 | ahead | 最后提交 |"
        & $w "| --- | --- | --- | --- |"
        foreach ($i in $divergent) { & $w ("| {0} | ``{1}`` | {2} | {3} |" -f $i.Kind, $i.Name, $i.Ahead, (Format-Duration $i.AgeDays)) }
        & $w ""
    }
    & $w "## 6. 后续开发提示"
    & $w ""
    foreach ($h in $hints) { & $w ("- {0}" -f $h) }
    & $w ""
    & $w "## 7. 执行命令"
    & $w ""
    & $w '```bash'
    $cl = Get-DeleteCommands $toDeleteLocal
    if ($cl.Count -eq 0) { & $w "# （无）" } else { $cl | ForEach-Object { & $w $_ } }
    if ($IncludeRemote -and $toDeleteRemote.Count -gt 0) {
        & $w ""
        (Get-DeleteCommands $toDeleteRemote -RemoteServer) | ForEach-Object { & $w $_ }
    }
    & $w '```'
    return $sb.ToString()
}

# ---- JSON 报告 ------------------------------------------------------------
function Get-JsonReport {
    $payload = [ordered]@{
        generatedAt = (Get-Date -Format 'o')
        repoRoot    = $script:Root
        base        = $baseBranch
        baseSha     = $baseSha
        remoteName  = $script:Remote
        criteria    = [ordered]@{
            A = 'merge-tree --write-tree 结果树 == 基线树'
            B = "分支新增行在基线中的存活率 >= $LostRatioThreshold"
        }
        counts      = [ordered]@{
            localTotal      = $localBranchReports.Count
            remoteTotal     = $remoteBranchReports.Count
            deletableLocal  = $toDeleteLocal.Count
            deletableRemote = $toDeleteRemote.Count
            churnLocal      = @($localBranchReports | Where-Object { $_.Bucket -eq 'churn' }).Count
            needReview      = $reviewItems.Count
            protectedLocal  = @($localBranchReports | Where-Object { $_.Bucket -like 'protected-*' }).Count
            divergent       = $divergent.Count
        }
        local       = @($localBranchReports | ForEach-Object { Convert-BranchToJson $_ })
        remote      = @($remoteBranchReports | ForEach-Object { Convert-BranchToJson $_ })
        duplicates  = @(foreach ($g in $dupGroups) {
                [ordered]@{ branches = @($g.Group | ForEach-Object { $_.Name }) }
            })
        hints       = @($hints)
    }
    return ($payload | ConvertTo-Json -Depth 6)
}

# ---- 输出 -----------------------------------------------------------------
switch ($Format) {
    'Text' { Write-Host (Get-TextReport) }
    'Markdown' { Write-Host (Get-MarkdownReport) }
    'Json' { Write-Host (Get-JsonReport) }
}

if ($OutDir) {
    if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $mdPath = Join-Path $OutDir "branch-cleanup-$stamp.md"
    $jsPath = Join-Path $OutDir "branch-cleanup-$stamp.json"
    Set-Content -LiteralPath $mdPath -Value (Get-MarkdownReport) -Encoding UTF8
    Set-Content -LiteralPath $jsPath -Value (Get-JsonReport) -Encoding UTF8
    Write-Host "报告已写入：`n  $mdPath`n  $jsPath" -ForegroundColor Green
}

# ---- 执行 -----------------------------------------------------------------
if ($Apply) {
    Write-Host ""
    Write-Host "== 执行删除 ==" -ForegroundColor Cyan
    $failed = @()
    foreach ($i in $toDeleteLocal) {
        $r = Invoke-Git @('branch', '-D', $i.Name)
        if ($r.Ok) { Write-Host ("  [OK]   已删除本地分支 {0}" -f $i.Name) -ForegroundColor Green }
        else {
            Write-Host ("  [FAIL] 删除失败 {0}：{1}" -f $i.Name, (@($r.Out) -join ' ')) -ForegroundColor Red
            $failed += $i.Name
        }
    }
    if ($DeleteRemote -and $IncludeRemote) {
        foreach ($i in $toDeleteRemote) {
            $r = Invoke-Git @('push', $script:Remote, '--delete', $i.Name)
            if ($r.Ok) { Write-Host ("  [OK]   已删除远端分支 {0}/{1}" -f $script:Remote, $i.Name) -ForegroundColor Green }
            else {
                Write-Host ("  [FAIL] 远端删除失败 {0}：{1}" -f $i.Name, (@($r.Out) -join ' ')) -ForegroundColor Red
                $failed += "$script:Remote/$($i.Name)"
            }
        }
    }
    if ($failed.Count -gt 0) { Write-Host ("  失败 {0} 个：{1}" -f $failed.Count, ($failed -join ', ')) -ForegroundColor Yellow }
} elseif ($toDeleteLocal.Count -gt 0) {
    Write-Host ""
    Write-Host "（dry-run：以上均为建议，未改动任何分支；确认后加 -Apply）" -ForegroundColor Yellow
}

if ($reviewItems.Count -gt 0) { exit 3 }
elseif ($toDeleteLocal.Count -gt 0 -or $toDeleteRemote.Count -gt 0) { exit 2 }
else { exit 0 }
