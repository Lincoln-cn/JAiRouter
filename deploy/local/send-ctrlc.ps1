<#
.SYNOPSIS
    向指定进程所在的控制台发送 Ctrl+C —— stop.ps1 的辅助脚本，不供日常直接调用。

.DESCRIPTION
    Windows 没有 SIGTERM，进程外唯一能触发 JVM shutdown hook 的手段是向目标进程所在
    的控制台发送 CTRL_C_EVENT / CTRL_BREAK_EVENT。

    必须由一个**独立进程**执行：发送者要先 FreeConsole 脱离自己的控制台、再
    AttachConsole 到目标进程的控制台，此后它自己也落在目标控制台的事件组里，
    极可能被同一信号终止。因此本脚本用哨兵文件回传"是否已经成功 attach"，
    调用方据此区分"信号已发出"与"目标没有控制台"。

    信号发出后本进程可能立刻被杀，退出码不可靠 —— 判据只能是哨兵文件。

.PARAMETER TargetPid
    目标进程 ID（要用 java 进程自己的 pid，不是包装脚本的）。

.PARAMETER Sentinel
    哨兵文件路径。attach 成功、发信号之前写入。

.NOTES
    这段 P/Invoke 刻意单独放在一个固定文件里，而不是在 stop.ps1 里用 here-string 动态生成：
    某些环境下 AMSI 扫描含 DllImport 的内联脚本会抛 AccessViolationException，
    把主脚本一起带崩。拆开后即使本文件被拦，调用方也只是降级为强制终止。
#>
param(
    [Parameter(Mandatory = $true)][int]$TargetPid,
    [Parameter(Mandatory = $true)][string]$Sentinel
)

$ErrorActionPreference = 'Stop'

Add-Type -Namespace JaiRouter -Name NativeConsole -MemberDefinition @'
[DllImport("kernel32.dll", SetLastError = true)]
public static extern bool FreeConsole();

[DllImport("kernel32.dll", SetLastError = true)]
public static extern bool AttachConsole(uint dwProcessId);

[DllImport("kernel32.dll", SetLastError = true)]
public static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);
'@

# 先脱离自己的控制台，否则 AttachConsole 会失败（一个进程同时只能挂一个控制台）。
[JaiRouter.NativeConsole]::FreeConsole() | Out-Null

if (-not [JaiRouter.NativeConsole]::AttachConsole([uint32]$TargetPid)) {
    # 目标进程没有控制台（例如由 SCM 或计划任务托管），此路不通，由调用方降级。
    exit 2
}

# 先落标记再发信号：信号一旦发出，本进程随时可能被终止。
Set-Content -Path $Sentinel -Value 'attached' -Encoding ASCII
[JaiRouter.NativeConsole]::GenerateConsoleCtrlEvent(0, 0) | Out-Null
Start-Sleep -Milliseconds 400
exit 0
