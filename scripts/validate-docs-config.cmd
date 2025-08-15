@echo off
echo 验证 MkDocs 配置文件...
echo.

REM 检查 Python 是否可用
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到 Python，请先安装 Python 3.x
    pause
    exit /b 1
)

REM 运行验证脚本
python scripts\validate-docs-config.py

pause