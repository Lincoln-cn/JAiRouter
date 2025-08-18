@echo off
REM 文档内容同步检查批处理脚本
REM 适用于 Windows 环境

setlocal enabledelayedexpansion

REM 设置默认参数
set PROJECT_ROOT=%~dp0..
set OUTPUT_FILE=
set FAIL_ON_ERROR=false

REM 解析命令行参数
:parse_args
if "%~1"=="" goto run_check
if "%~1"=="--project-root" (
    set PROJECT_ROOT=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--output" (
    set OUTPUT_FILE=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--fail-on-error" (
    set FAIL_ON_ERROR=true
    shift
    goto parse_args
)
if "%~1"=="--help" (
    goto show_help
)
shift
goto parse_args

:show_help
echo 文档内容同步检查工具
echo.
echo 用法: check-docs-sync.cmd [选项]
echo.
echo 选项:
echo   --project-root PATH    项目根目录路径 (默认: 当前目录的上级目录)
echo   --output FILE          输出报告文件路径
echo   --fail-on-error        发现严重问题时退出码为1
echo   --help                 显示此帮助信息
echo.
echo 示例:
echo   check-docs-sync.cmd
echo   check-docs-sync.cmd --output report.md
echo   check-docs-sync.cmd --project-root . --fail-on-error
goto end

:run_check
echo 🔍 开始文档内容同步检查...
echo 项目根目录: %PROJECT_ROOT%

REM 检查 PowerShell 是否可用
powershell -Command "Get-Host" >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 需要 PowerShell 支持
    exit /b 1
)

REM 构建 PowerShell 命令
set PS_COMMAND=& '%~dp0check-docs-sync.ps1' -ProjectRoot '%PROJECT_ROOT%'

if not "%OUTPUT_FILE%"=="" (
    set PS_COMMAND=!PS_COMMAND! -OutputFile '%OUTPUT_FILE%'
)

if "%FAIL_ON_ERROR%"=="true" (
    set PS_COMMAND=!PS_COMMAND! -FailOnError
)

REM 运行 PowerShell 脚本
powershell -ExecutionPolicy Bypass -Command "!PS_COMMAND!"
set EXIT_CODE=%errorlevel%

if %EXIT_CODE% equ 0 (
    echo ✅ 文档同步检查完成
) else (
    echo ❌ 文档同步检查发现问题
)

exit /b %EXIT_CODE%

:end