# ============================================================
# v2.9.9 响应缓存（Response Cache）冒烟测试脚本
#
# 用途：验证「相同确定性 chat 请求二次命中缓存 / 不触下游 / 不同
#       apiKeyId 不共享缓存 /（可选）TTL 过期重新走下游」。
# 说明：metric 以 /actuator/prometheus 的 jairouter_response_cache_
#       (misses|hits)_total{service="chat",...} 求和为准；data 一致性
#       比较基于 RouterResponse.data（timestamp 为包装元数据，不参与比较）。
#
# 前置：
#   1) 后端已 dev-up（http://127.0.0.1:8080），chat 下游可用；
#   2) 已临时开启 jairouter.response-cache.enabled=true（建议 ttl: 60s），
#      见 .mimocode/plans/1788339000000-v299-response-cache-smoke-runbook.md
#      第 0 步（冒烟后请还原该临时配置）；
#   3) KeyA/KeyB 为两个不同 keyId 且有 chat 权限的 API Key。
#
# 用法（PowerShell）：
#   .\scripts\test\test-response-cache-smoke.ps1 -Model "<chat模型名>"
#     [-BaseUrl http://127.0.0.1:8080]
#     [-KeyA dev-admin-12345-abcde-67890-fghij] [-KeyB "<另一key值>"]
#     [-Bearer "<下游Authorization值,可选>"] [-TtlSeconds 0]
#     [-LogPath logs/backend-dev.log]
#
# 退出码：0=全部通过；1=存在 FAIL。
# ============================================================

param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$KeyA = "dev-admin-12345-abcde-67890-fghij",
    [string]$KeyB = "",
    [string]$Model = "",
    [string]$Bearer = "",
    [string]$LogPath = "logs/backend-dev.log",
    [int]$TtlSeconds = 0,
    [string]$Content = "1+1=?"
)

$ErrorActionPreference = "Stop"
$script:fail = 0
$api = "$BaseUrl/api/v1/chat/completions"
$prom = "$BaseUrl/actuator/prometheus"

if (-not $Model) {
    Write-Host "必须提供 -Model <chat模型名>" -ForegroundColor Red
    exit 1
}

function AssertTrue($cond, $msg) {
    if ($cond) { Write-Host "  [PASS] $msg" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $msg" -ForegroundColor Red; $script:fail++ }
}

function Send-ChatJson($key) {
    # 返回 @{ code=HTTP状态码; body=响应文本 }
    $payload = @{
        model    = $Model
        messages = @(@{ role = "user"; content = $Content })
        temperature = 0
        stream   = $false
    } | ConvertTo-Json -Depth 10
    $bodyFile = Join-Path $env:TEMP ("rc-body-" + [guid]::NewGuid().ToString() + ".json")
    # PS5.1 Set-Content -Encoding UTF8 会写 BOM，破坏 JSON body → 用无 BOM 写入
    [System.IO.File]::WriteAllText($bodyFile, $payload, (New-Object System.Text.UTF8Encoding($false)))
    $outFile = Join-Path $env:TEMP ("rc-resp-" + [guid]::NewGuid().ToString() + ".json")
    $args = @("-s", "-o", $outFile, "-w", "%{http_code}", "-X", "POST", $api,
        "-H", "Content-Type: application/json", "-H", "X-API-Key: $key")
    if ($Bearer) { $args += @("-H", "Authorization: Bearer $Bearer") }
    $args += @("--data-binary", "@$bodyFile")
    $code = & curl.exe @args
    $body = ""
    if (Test-Path $outFile) {
        $body = Get-Content $outFile -Raw -Encoding UTF8
        Remove-Item $outFile -Force
    }
    Remove-Item $bodyFile -Force
    return @{ code = $code; body = $body }
}

function Get-ChatMetricTotal($metricName) {
    $text = & curl.exe -s $prom
    $total = 0.0
    foreach ($line in ($text -split "`r?`n")) {
        if ($line -match ('^' + [regex]::Escape($metricName) + '\{') -and
            $line -match 'service="chat"' -and
            $line -match '(\d+(\.\d+)?)\s*$') {
            $total += [double]$Matches[1]
        }
    }
    return $total
}

function Get-DataJson($respBody) {
    try {
        $obj = $respBody | ConvertFrom-Json
        if ($null -eq $obj.data) { return "" }
        return ($obj.data | ConvertTo-Json -Depth 100 -Compress)
    } catch { return "" }
}

function Get-LogHitCount() {
    if (-not (Test-Path $LogPath)) { return -1 }
    $count = 0
    foreach ($line in (Get-Content $LogPath)) {
        if ($line -match "Response cache hit: service=chat") { $count++ }
    }
    return $count
}

Write-Host "== v2.9.9 响应缓存冒烟 (base=$BaseUrl model=$Model) =="

# ---- S0: 依赖可达 ----
$probe = & curl.exe -s -o NUL -w "%{http_code}" $prom
AssertTrue ($probe -eq "200") "S0 /actuator/prometheus 可达 (http=$probe)"
if ($script:fail -gt 0) { exit 1 }

# ---- S1: 基线 ----
$miss0 = Get-ChatMetricTotal "jairouter_response_cache_misses_total"
$hit0 = Get-ChatMetricTotal "jairouter_response_cache_hits_total"
$logHit0 = Get-LogHitCount
Write-Host "  基线: chat miss=$miss0 hit=$hit0 logHit=$logHit0"

# ---- S2: KeyA 第一次（预期 miss，走下游）----
$r1 = Send-ChatJson $KeyA
AssertTrue ($r1.code -eq "200") "S2 第一次请求 HTTP 200 (code=$($r1.code))"
AssertTrue ($r1.body -match '"success":\s*true') "S2 响应 success=true"
$miss1 = Get-ChatMetricTotal "jairouter_response_cache_misses_total"
$hit1 = Get-ChatMetricTotal "jairouter_response_cache_hits_total"
AssertTrue (($miss1 - $miss0) -eq 1.0) "S2 miss +1 (miss=$miss0 -> $miss1)"
AssertTrue (($hit1 - $hit0) -eq 0.0) "S2 hit 不变 (hit=$hit0 -> $hit1)"
$data1 = Get-DataJson $r1.body
AssertTrue ($data1 -ne "") "S2 响应含 data 内容"

# ---- S3: KeyA 第二次完全相同（预期 hit，data 一致）----
$r2 = Send-ChatJson $KeyA
AssertTrue ($r2.code -eq "200") "S3 第二次请求 HTTP 200 (code=$($r2.code))"
$data2 = Get-DataJson $r2.body
AssertTrue ($data2 -eq $data1) "S3 第二次 data 与第一次一致"
$miss2 = Get-ChatMetricTotal "jairouter_response_cache_misses_total"
$hit2 = Get-ChatMetricTotal "jairouter_response_cache_hits_total"
AssertTrue (($hit2 - $hit1) -eq 1.0) "S3 hit +1 (hit=$hit1 -> $hit2)"
AssertTrue (($miss2 - $miss1) -eq 0.0) "S3 miss 不再增加"
if ($logHit0 -ge 0) {
    $logHit2 = Get-LogHitCount
    AssertTrue (($logHit2 - $logHit0) -ge 1) "S3 日志出现 'Response cache hit: service=chat' (logHit=$logHit0 -> $logHit2)"
} else {
    Write-Host "  [WARN] 未提供日志文件($LogPath)，跳过日志断言（metrics 已覆盖）"
}

# ---- S4: KeyB 同请求（预期 miss：不同租户不共享）----
if ($KeyB) {
    $r3 = Send-ChatJson $KeyB
    AssertTrue ($r3.code -eq "200") "S4 KeyB 请求 HTTP 200 (code=$($r3.code))"
    $miss3 = Get-ChatMetricTotal "jairouter_response_cache_misses_total"
    $hit3 = Get-ChatMetricTotal "jairouter_response_cache_hits_total"
    AssertTrue (($miss3 - $miss2) -eq 1.0) "S4 miss +1 (不同 keyId 不命中 KeyA 缓存)"
    AssertTrue (($hit3 - $hit2) -eq 0.0) "S4 hit 不变"
    $data3 = Get-DataJson $r3.body
    AssertTrue ($data3 -ne "") "S4 KeyB 响应含 data 内容"
} else {
    Write-Host "  [WARN] 未提供 -KeyB，跳过 S4 租户隔离步骤"
}

# ---- S5（可选）: TTL 过期后 KeyA 重发（预期 miss）----
if ($TtlSeconds -gt 0) {
    Write-Host "  等待 $($TtlSeconds + 5)s 使 TTL 过期..."
    Start-Sleep -Seconds ($TtlSeconds + 5)
    $r4 = Send-ChatJson $KeyA
    AssertTrue ($r4.code -eq "200") "S5 过期后请求 HTTP 200"
    $miss4 = Get-ChatMetricTotal "jairouter_response_cache_misses_total"
    $hit4 = Get-ChatMetricTotal "jairouter_response_cache_hits_total"
    AssertTrue (($miss4 - $miss3) -ge 1.0) "S5 TTL 过期后 miss +1 (重新走下游)"
    AssertTrue (($hit4 - $hit3) -eq 0.0) "S5 hit 不变"
} else {
    Write-Host "  [WARN] -TtlSeconds 未设置，跳过 S5 TTL 过期步骤"
}

Write-Host ""
if ($script:fail -eq 0) {
    Write-Host "== 冒烟全部通过 ==" -ForegroundColor Green
} else {
    Write-Host "== 冒烟存在 $script:fail 项失败，请对照 runbook 排查 ==" -ForegroundColor Red
}
exit $script:fail
