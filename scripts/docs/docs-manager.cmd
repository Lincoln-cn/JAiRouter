@echo off
REM JAiRouter 文档管理统一脚本 (Windows 批处理版本)
REM 提供基本的文档管理功能

setlocal enabledelayedexpansion

REM 设置默认参数
set COMMAND=%1
set HOST=localhost
set PORT=8000
set OUTPUT=
set FAIL_ON_ERROR=false

REM 检查命令参数
if "%COMMAND%"=="" goto show_help
if "%COMMAND%"=="help" goto show_help

REM 解析其他参数
shift
:parse_args
if "%~1"=="" goto execute_command
if "%~1"=="--host" (
    set HOST=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--port" (
    set PORT=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--output" (
    set OUTPUT=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--fail-on-error" (
    set FAIL_ON_ERROR=true
    shift
    goto parse_args
)
shift
goto parse_args

:show_help
echo JAiRouter 文档管理工具
echo ========================
echo.
echo 用法: %~nx0 ^<命令^> [选项]
echo.
echo 可用命令:
echo   serve          启动本地文档服务器
echo   check-links    检查文档链接有效性
echo   validate       验证文档结构和配置
echo   help           显示此帮助信息
echo.
echo serve 命令选项:
echo   --host ^<地址^>   监听地址 (默认: localhost)
echo   --port ^<端口^>   监听端口 (默认: 8000)
echo.
echo 示例:
echo   %~nx0 serve --port 3000
echo   %~nx0 check-links --output report.json
echo   %~nx0 validate
goto end

:execute_command
if "%COMMAND%"=="serve" goto start_server
if "%COMMAND%"=="check-links" goto check_links
if "%COMMAND%"=="validate" goto validate_docs
echo ❌ 未知命令: %COMMAND%
goto show_help

:start_server
echo 🚀 启动本地文档服务器...

REM 切换到项目根目录
pushd "%~dp0..\.."

REM 检查 Python
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 未找到 Python，请先安装 Python 3.x
    popd
    exit /b 1
)

REM 检查 requirements.txt
if not exist "requirements.txt" (
    echo ❌ 错误: 未找到 requirements.txt 文件
    popd
    exit /b 1
)

REM 安装依赖
echo 📦 安装文档依赖...
pip install -r requirements.txt
if errorlevel 1 (
    echo ❌ 错误: 依赖安装失败
    popd
    exit /b 1
)

REM 启动服务器
echo 🌐 启动文档服务器，监听地址: %HOST%:%PORT%
echo 📖 访问地址: http://%HOST%:%PORT%
echo ⏹️  按 Ctrl+C 停止服务器

mkdocs serve --dev-addr "%HOST%:%PORT%"
popd
goto end

:check_links
echo 🔍 检查文档链接...

REM 检查 PowerShell 是否可用
powershell -Command "Get-Host" >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 需要 PowerShell 支持
    exit /b 1
)

REM 调用 PowerShell 版本的统一脚本
set PS_COMMAND=& '%~dp0docs-manager.ps1' check-links

if not "%OUTPUT%"=="" (
    set PS_COMMAND=!PS_COMMAND! -Output '%OUTPUT%'
)

if "%FAIL_ON_ERROR%"=="true" (
    set PS_COMMAND=!PS_COMMAND! -FailOnError
)

powershell -ExecutionPolicy Bypass -Command "!PS_COMMAND!"
goto end

:validate_docs
echo ✅ 验证文档结构...

REM 检查 PowerShell 是否可用
powershell -Command "Get-Host" >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 需要 PowerShell 支持
    exit /b 1
)

REM 调用 PowerShell 版本的统一脚本
powershell -ExecutionPolicy Bypass -Command "& '%~dp0docs-manager.ps1' validate"
goto end

:end