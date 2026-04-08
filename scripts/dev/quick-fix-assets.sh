#!/bin/bash

# =============================================================================
# JAiRouter 快速修复脚本 - 解决静态资源版本混乱问题
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATIC_DIR="${PROJECT_ROOT}/target/classes/static/admin"

echo -e "${BLUE}[INFO]${NC} 正在修复静态资源版本混乱问题..."

# 1. 停止服务
echo -e "${BLUE}[INFO]${NC} 停止 Spring Boot 服务..."
pkill -f 'modelrouter.ModelRouterApplication' 2>/dev/null || true
sleep 2

# 2. 清理所有静态资源
echo -e "${BLUE}[INFO]${NC} 清理静态资源目录..."
if [ -d "${STATIC_DIR}" ]; then
    rm -rf "${STATIC_DIR}"/*
    echo -e "${GREEN}[SUCCESS]${NC} 静态资源已清理"
fi

# 3. 重新编译项目（包含前端构建）
echo -e "${BLUE}[INFO]${NC} 重新编译项目..."
cd "${PROJECT_ROOT}"
./mvnw clean compile -DskipTests -P fast

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR]${NC} 编译失败"
    exit 1
fi

# 4. 验证文件
echo -e "${BLUE}[INFO]${NC} 验证静态资源..."
INSTANCE_COUNT=$(find "${STATIC_DIR}/assets" -name "InstanceManagement-*.js" 2>/dev/null | wc -l)
if [ "$INSTANCE_COUNT" -gt 1 ]; then
    echo -e "${YELLOW}[WARNING]${NC} 发现 $INSTANCE_COUNT 个 InstanceManagement 文件，正在清理..."
    cd "${STATIC_DIR}/assets"
    ls -t InstanceManagement-*.js | tail -n +2 | xargs -r rm -f
    echo -e "${GREEN}[SUCCESS]${NC} 已保留最新文件"
fi

# 5. 启动服务
echo -e "${BLUE}[INFO]${NC} 启动 Spring Boot 服务..."
nohup ./mvnw spring-boot:run -P fast > "${PROJECT_ROOT}/logs/spring-boot.log" 2>&1 &

sleep 15

# 6. 验证服务
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
if [ "$HEALTH_STATUS" = "200" ]; then
    echo -e "${GREEN}[SUCCESS]${NC} 服务启动成功！"
    echo -e "${BLUE}[INFO]${NC} 访问地址: http://localhost:8080/admin/config/instances"
else
    echo -e "${YELLOW}[WARNING]${NC} 服务可能还在启动中"
fi

echo -e "${GREEN}[SUCCESS]${NC} 修复完成！"
echo -e "${BLUE}[INFO]${NC} 请强制刷新浏览器 (Ctrl+Shift+R) 查看最新界面"
