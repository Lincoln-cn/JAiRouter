#!/bin/bash

# ============================================================================
# JAiRouter 快速启动脚本（开发环境）
# ============================================================================

echo "========================================"
echo "  JAiRouter 开发环境启动"
echo "========================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 检查 Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java 已安装"

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}✗ Node.js 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Node.js 已安装"

# 停止现有进程
echo ""
echo "停止现有进程..."
pkill -f "model-router-1.2.5.jar" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true
sleep 2

# 启动后端
echo ""
echo "========================================"
echo "  启动后端服务（端口 8080）"
echo "========================================"
cd /home/ubuntu/jairouter/modelrouter
nohup java -jar target/model-router-1.2.5.jar \
  --server.port=8080 \
  --spring.profiles.active=dev \
  --store.type=h2 \
  > /tmp/backend.log 2>&1 &

echo "后端启动中..."
sleep 15

# 检查后端状态
if curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo -e "${GREEN}✓${NC} 后端启动成功 (http://localhost:8080)"
else
    echo -e "${RED}✗${NC} 后端启动失败，请查看日志：/tmp/backend.log"
    exit 1
fi

# 启动前端
echo ""
echo "========================================"
echo "  启动前端服务（端口 3000）"
echo "========================================"
cd /home/ubuntu/jairouter/modelrouter/frontend

# 检查 node_modules
if [ ! -d "node_modules" ]; then
    echo "安装依赖..."
    npm install
fi

nohup npm run dev > /tmp/frontend.log 2>&1 &

echo "前端启动中..."
sleep 10

# 检查前端状态
if curl -s http://localhost:3000/admin/ > /dev/null; then
    echo -e "${GREEN}✓${NC} 前端启动成功 (http://localhost:3000/admin/)"
else
    echo -e "${YELLOW}⚠${NC} 前端可能还未完全启动，请查看日志：/tmp/frontend.log"
fi

# 总结
echo ""
echo "========================================"
echo "  启动完成"
echo "========================================"
echo ""
echo "访问地址:"
echo "  前端管理页面：http://localhost:8080/admin/index.html"
echo "  后端 API: http://localhost:8080"
echo "  Swagger 文档：http://localhost:8080/swagger-ui.html"
echo ""
echo "日志文件:"
echo "  后端：/tmp/backend.log"
echo "  前端：/tmp/frontend.log"
echo ""
echo "停止服务:"
echo "  pkill -f 'model-router-1.2.5.jar'"
echo "  pkill -f 'vite'"
echo ""
