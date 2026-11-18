#!/usr/bin/env bash
# 构建并启动 JAiRouter（Linux / macOS / Windows MSYS Bash）
#
# 与 run-dev.ps1 同一套行为，修掉旧版三个缺陷：
#   1. 默认端口 31080 → 8080；
#   2. `java -jar -Dserver.port=$PORT` → JVM 参数置于 -jar 之后不生效；
#   3. `-Pfast` 构建会跳过前端（frontend.build.skip=true），/admin/ 404。
#
# 用法：
#   ./scripts/dev/run-dev.sh                     # 8080 + dev
#   ./scripts/dev/run-dev.sh --port 8081
#   ./scripts/dev/run-dev.sh --skip-build
#   ./scripts/dev/run-dev.sh --skip-frontend
set -euo pipefail

PORT=8080
PROFILE=dev
SKIP_BUILD=0
SKIP_FRONTEND=0
ADMIN_PASSWORD=""

while [ $# -gt 0 ]; do
  case "$1" in
    --port) PORT="${2:?--port 需要数值}"; shift 2 ;;
    --profile) PROFILE="${2:?}"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --skip-frontend) SKIP_FRONTEND=1; shift ;;
    --admin-password) ADMIN_PASSWORD="${2:?}"; shift 2 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [ "$SKIP_BUILD" -eq 0 ]; then
  command -v java >/dev/null || { echo "❌ 未找到 java（需要 JDK 17+）" >&2; exit 1; }
  case "${OSTYPE:-}$(uname -s 2>/dev/null)" in
    msys*|cygwin*|MINGW*) MVNW="./mvnw.cmd" ;;   # Windows：.cmd 版，避免 MSYS 参数转换
    *) MVNW="./mvnw" ;;
  esac
  [ -f "$MVNW" ] || { echo "❌ 未找到 Maven Wrapper：$MVNW" >&2; exit 1; }
  ARGS=(package -Pdev -DskipTests)
  [ "$SKIP_FRONTEND" -eq 1 ] && ARGS+=(-Dfrontend.build.skip=true)
  echo "🔨 构建中：mvnw ${ARGS[*]}"
  $MVNW "${ARGS[@]}"
fi

JAR="$(ls -1t target/model-router-*.jar 2>/dev/null | grep -Ev -- '-(sources|javadoc|tests)\.jar$' | head -n1 || true)"
[ -n "$JAR" ] || { echo "❌ target 下没有 model-router-*.jar，请去掉 --skip-build 先构建" >&2; exit 1; }

[ -n "$ADMIN_PASSWORD" ] && export INITIAL_ADMIN_PASSWORD="$ADMIN_PASSWORD"

echo "📦 $(basename "$JAR")"
echo "🏃 java -jar … --server.port=$PORT --spring.profiles.active=$PROFILE"
echo "🔗 控制台 http://localhost:$PORT/admin/  |  Swagger http://localhost:$PORT/swagger-ui.html"
echo "⏹  Ctrl+C 停止"
exec java -jar "$JAR" "--server.port=$PORT" "--spring.profiles.active=$PROFILE"
