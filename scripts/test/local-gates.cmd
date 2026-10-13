@echo off
REM ============================================================================
REM Local gate runner (wrapper).
REM
REM Why this wrapper exists:
REM   1. The Windows default execution policy blocks unsigned .ps1 files
REM      ("... is not digitally signed"). The policy is relaxed only for the
REM      powershell process started here; no system state is changed.
REM   2. This machine's AMSI crashes intermittently while compiling larger
REM      scripts (System.AccessViolationException in AmsiScanBuffer), killing
REM      the run before any code executes. That failure is fast and random, so
REM      we retry up to 3 times when the crash signature is seen in the output.
REM
REM Output is captured to a log because the retry needs to inspect it; the
REM runner itself also mirrors the Maven output to %TEMP%\local-gates-maven.log,
REM so a long test run can be followed separately.
REM
REM NOTE: keep this file ASCII-only. cmd.exe decodes it in the OEM/ANSI code
REM page, so non-ASCII bytes here get mangled and can be parsed as commands.
REM
REM Usage:
REM   scripts\test\local-gates.cmd
REM   scripts\test\local-gates.cmd -SkipMaven
REM   scripts\test\local-gates.cmd -SpellBase HEAD~3
REM ============================================================================
setlocal enabledelayedexpansion

set "OUT=%TEMP%\local-gates-run.log"
set "ATTEMPT=0"
set "MAXATTEMPTS=3"

:retry
set /a ATTEMPT+=1
if %ATTEMPT% gtr 1 echo.
echo [local-gates] attempt !ATTEMPT! of %MAXATTEMPTS% ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0local-gates.ps1" %* > "%OUT%" 2>&1
set "RC=%ERRORLEVEL%"

if %RC% neq 0 if %ATTEMPT% lss %MAXATTEMPTS% (
    findstr /C:"AmsiScanBuffer" "%OUT%" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo [local-gates] AMSI crashed while loading the script ^(known local defect, not a gate failure^).
        echo [local-gates] retrying...
        goto retry
    )
)

type "%OUT%"
echo.
echo [local-gates] full run log: %OUT%
exit /b %RC%
