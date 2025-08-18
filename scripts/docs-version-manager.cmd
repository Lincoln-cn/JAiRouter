@echo off
REM 文档版本管理脚本 (Windows 批处理版本)
REM 实现文档版本标识和更新提醒，追踪文档变更

setlocal enabledelayedexpansion

REM 默认参数
set "PROJECT_ROOT=."
set "SCAN=false"
set "REPORT="
set "ADD_HEADERS=false"
set "CLEANUP=0"
set "EXPORT="
set "CHECK_OUTDATED=30"

REM 解析命令行参数
:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="--project-root" (
    set "PROJECT_ROOT=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--scan" (
    set "SCAN=true"
    shift
    goto :parse_args
)
if "%~1"=="--report" (
    set "REPORT=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--add-headers" (
    set "ADD_HEADERS=true"
    shift
    goto :parse_args
)
if "%~1"=="--cleanup" (
    set "CLEANUP=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--export" (
    set "EXPORT=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--check-outdated" (
    set "CHECK_OUTDATED=%~2"
    shift
    shift
    goto :parse_args
)
if "%~1"=="--help" (
    goto :show_help
)
echo 未知参数: %~1
goto :show_help

:args_done

REM 检查 Python 是否可用
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python 未安装或不在 PATH 中
    exit /b 1
)

REM 检查 Git 是否可用
git --version >nul 2>&1
if errorlevel 1 (
    echo ⚠️ Git 未安装或不在 PATH 中，Git 信息收集将被跳过
)

REM 确保项目结构存在
if not exist "%PROJECT_ROOT%\.kiro" (
    echo 📁 创建 .kiro 目录...
    mkdir "%PROJECT_ROOT%\.kiro"
)

REM 检查 Python 脚本是否存在
set "SCRIPT_DIR=%~dp0"
set "PYTHON_SCRIPT=%SCRIPT_DIR%docs-version-manager.py"

if not exist "%PYTHON_SCRIPT%" (
    echo ❌ Python 版本管理脚本不存在: %PYTHON_SCRIPT%
    exit /b 1
)

REM 构建 Python 脚本参数
set "PYTHON_ARGS=--project-root "%PROJECT_ROOT%""

if "%SCAN%"=="true" (
    set "PYTHON_ARGS=!PYTHON_ARGS! --scan"
)

if not "%REPORT%"=="" (
    set "PYTHON_ARGS=!PYTHON_ARGS! --report "%REPORT%""
)

if "%ADD_HEADERS%"=="true" (
    set "PYTHON_ARGS=!PYTHON_ARGS! --add-headers"
)

if not "%CLEANUP%"=="0" (
    set "PYTHON_ARGS=!PYTHON_ARGS! --cleanup %CLEANUP%"
)

if not "%EXPORT%"=="" (
    set "PYTHON_ARGS=!PYTHON_ARGS! --export "%EXPORT%""
)

set "PYTHON_ARGS=!PYTHON_ARGS! --check-outdated %CHECK_OUTDATED%"

REM 执行 Python 脚本
echo 🚀 启动文档版本管理...
python "%PYTHON_SCRIPT%" !PYTHON_ARGS!

if errorlevel 1 (
    echo ❌ 文档版本管理失败
    exit /b 1
)

echo ✅ 文档版本管理完成
goto :eof

:show_help
echo 文档版本管理脚本
echo.
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo     --project-root PATH     项目根目录 (默认: .)
echo     --scan                  扫描并更新版本信息
echo     --report PATH           生成版本报告到指定文件
echo     --add-headers           添加版本头信息
echo     --cleanup DAYS          清理指定天数前的变更记录
echo     --export PATH           导出版本数据到指定文件
echo     --check-outdated DAYS   检查过期文档的天数阈值 (默认: 30)
echo     --help                  显示此帮助信息
echo.
echo 示例:
echo     %~nx0 --scan --report report.md
echo     %~nx0 --add-headers --export data.json
echo     %~nx0 --cleanup 90 --check-outdated 30
goto :eof