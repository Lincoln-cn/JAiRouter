#!/bin/bash
# Shell script for serving documentation locally on Linux/macOS

PORT=${2:-8000}       # 第二个位置参数，默认 8000
HOST=${1:-localhost}  # 第一个位置参数，默认 localhost

echo "启动本地文档服务器..."

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到 Python 3，请先安装 Python 3.x"
    exit 1
fi

echo "检测到 Python: $(python3 --version)"

# Check if requirements.txt exists
if [ ! -f "requirements.txt" ]; then
    echo "错误: 未找到 requirements.txt 文件"
    exit 1
fi

# Install dependencies
echo "安装文档依赖..."
pip3 install -r requirements.txt

if [ $? -ne 0 ]; then
    echo "错误: 依赖安装失败"
    exit 1
fi

# Serve documentation
echo "启动文档服务器，监听地址: $HOST:$PORT"
echo "访问地址: http://$HOST:$PORT"
echo "按 Ctrl+C 停止服务器"

mkdocs serve --dev-addr "$HOST:$PORT"