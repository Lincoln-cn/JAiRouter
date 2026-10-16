@echo off
REM ============================================================================
REM  branch-cleanup.cmd —— 绕过本机执行策略调用 branch-cleanup.ps1
REM  本机默认拦未签名脚本，直接 `scripts\test\branch-cleanup.ps1` 会被拒。
REM  用法与 .ps1 完全一致（参数原样透传），例如：
REM      scripts\test\branch-cleanup.cmd                        :: 只看，不改
REM      scripts\test\branch-cleanup.cmd -IncludeRemote          :: 连远端跟踪分支
REM      scripts\test\branch-cleanup.cmd -Apply                  :: 真正执行删除
REM ============================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0branch-cleanup.ps1" %*
exit /b %ERRORLEVEL%
