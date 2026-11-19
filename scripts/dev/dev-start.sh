#!/usr/bin/env bash
# JAiRouter 开发环境一键启动（后端 jar + 前端 Vite dev server）
#
# 旧版本是给某台 Linux 服务器写死的：`cd /home/ubuntu/jairouter/modelrouter`、
# `target/model-router-1.2.5.jar`（当前版本 3.8.8，文件根本不存在）、
# 以及 `pkill -f "model-router-1.2.5.jar"`。在任何其它机器上都必然失败。
#
# 现在改为：路径由脚本位置推导、jar 由通配符查找、端口可覆盖。
#
# 用法：
#   ./scripts/dev/dev-start.sh                    # 后端 8080 + 前端 3000
#   ./scripts/dev/dev-start.sh --backend-port 8081 --frontend-port 3001
#   ./scripts/dev/dev-start.sh --no-frontend
#   ./scripts/dev/dev-start.sh --admin-password 'Admin@123456'
set -euo pipefail

BACKEND_PORT=8080
FRONTEND_PORT=3000
PROFILE=dev
START_FRONTEND=1
ADMIN_PASSWORD=""
LOG_DIR="${TMPDIR:-/tmp}/jairouter-dev"

while [ $# -gt 0 ]; do
  case "$1" in
    --backend-port) BACKEND_PORT="${2:?}"; shift 2 ;;
    --frontend-port) FRONTEND_PORT="${2:?}"; shift 2 ;;
    --profile) PROFILE="${2:?}"; shift 2 ;;
    --admin-password) ADMIN_PASSWORD="${2:?}"; shift 2 ;;
    --no-frontend) START_FRONTEND=0; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$LOG_DIR"
cd "$ROOT"

say() { printf '\033[%sm%s\033[0m\n' "$1" "$2"; }

command -v java >/dev/null || { say '0;31' "✗ 未找到 java（需要 JDK 17+）"; exit 1; }
command -v node >/dev/null || { say '0;31' "✗ 未找到 node"; exit 1; }

say '0;32' "✓ 环境检查通过（java / node）"

# 停旧进程：按 jar 名与 vite 匹配，避免误杀
pkill -f 'model-router-.*\.jar' 2>/dev/null || true
pkill -f 'node .*vite' 2>/dev/null || true
sleep 1

if ! ls target/model-router-*.jar >/dev/null 2>&1; then
  say '1;33' '未找到已构建的 jar，先构建一次（含前端静态资源）…'
  case "${OSTYPE:-}$(uname -s 2>/dev/null)" in
    msys*|cygwin*|MINGW*) ./mvnw.cmd package -Pdev -DskipTests ;;
    *) ./mvnw package -Pdev -DskipTests ;;
  esac
fi

JAR="$(ls -1t target/model-router-*.jar 2>/dev/null | grep -Ev -- '-(sources|javadoc|tests)\.jar$' | head -n1 || true)"
[ -n "$JAR" ] || { say '0;31' '✗ target 下没有 model-router-*.jar'; exit 1; }

say '0;32' "✓ 后端 jar：$(basename "$JAR")"
[ -n "$ADMIN_PASSWORD" ] && export INITIAL_ADMIN_PASSWORD="$ADMIN_PASSWORD"

say '1;33' "启动后端（端口 $BACKEND_PORT，profile $PROFILE）…"
nohup java -jar "$JAR" "--server.port=$BACKEND_PORT" "--spring.profiles.active=$PROFILE" \
  > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

for i in $(seq 1 60); do
  if curl -fsS "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null 2>&1; then
    say '0;32' "✓ 后端就绪（${i}0s 内）http://localhost:$BACKEND_PORT"
    break
  fi
  sleep 2
  [ "$i" -eq 60 ] && { say '0;31' "✗ 后端未就绪，日志：$LOG_DIR/backend.log"; exit 1; }
done

if [ "$START_FRONTEND" -eq 1 ]; then
  cd "$ROOT/frontend"
  [ -d node_modules ] || { say '1;33' '安装前端依赖…'; npm install; }
  say '1;33' "启动前端（端口 $FRONTEND_PORT，代理到后端 $BACKEND_PORT）…"
  # vite 的 dev 端口由 vite.config.ts 的 server.port 决定；用 --port 覆盖
  VITE_API_URL="http://127.0.0.1:$BACKEND_PORT" nohup npx vite --port "$FRONTEND_PORT" \
    > "$LOG_DIR/frontend.log" 2>&1 &
  sleep 6
  if curl -fsS "http://localhost:$FRONTEND_PORT/admin/" >/dev/null 2>&1; then
    say '0;32' "✓ 前端就绪 http://localhost:$FRONTEND_PORT/admin/"
  else
    say '1;33' "⚠ 前端可能仍在启动，日志：$LOG_DIR/frontend.log"
  fi
  cd "$ROOT"
fi

cat <<EOF

================================================
  JAiRouter 开发环境
================================================
  控制台（后端集成）: http://localhost:$BACKEND_PORT/admin/
  控制台（Vite HMR） : http://localhost:$FRONTEND_PORT/admin/
  后端 API          : http://localhost:$BACKEND_PORT
  Swagger           : http://localhost:$BACKEND_PORT/swagger-ui.html
  健康检查          : http://localhost:$BACKEND_PORT/actuator/health

  日志: $LOG_DIR/backend.log , $LOG_DIR/frontend.log
  停止: kill $BACKEND_PID$( [ "$START_FRONTEND" -eq 1 ] && echo ' ; pkill -f "node .*vite"' )
================================================
EOF
