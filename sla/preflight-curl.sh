#!/bin/sh
# 零依赖契约自检 —— 只要求 curl，用于既没有 Node、也没有 Docker 的离线环境。
#
# 与 preflight.mjs 检查同一份契约（contract.json），但只用 curl + grep 实现：
# 它能回答「网关还在不在说这套话」，不能替代 preflight.mjs 的完整字段校验。
# 完整校验请用镜像：docker run --rm --network host IMG preflight.mjs ...
#
#   ./preflight-curl.sh --base http://gw:8080 --user admin --pass '***'
#   SLA_BASE=... SLA_USER=... SLA_PASS=... ./preflight-curl.sh
#
# 退出码：0 全部通过 / 2 有失败
set -u

BASE=${SLA_BASE:-http://127.0.0.1:8080}
USER=${SLA_USER:-admin}
PASS=${SLA_PASS:-}
VERSION=${SLA_VERSION:-}
HERE=$(dirname "$0")

while [ $# -gt 0 ]; do
  case "$1" in
    --base)    BASE=${2:-}; shift 2 ;;
    --user)    USER=${2:-}; shift 2 ;;
    --pass)    PASS=${2:-}; shift 2 ;;
    --version) VERSION=${2:-}; shift 2 ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

BASE=$(printf '%s' "$BASE" | sed 's:/*$::')
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT INT TERM

PASSED=0
FAILED=0
ok()   { PASSED=$((PASSED + 1)); printf '[ OK ] %-22s %s\n' "$1" "$2"; }
fail() { FAILED=$((FAILED + 1)); printf '[FAIL] %-22s %s\n' "$1" "$2"; }

command -v curl >/dev/null 2>&1 || { echo "缺少 curl" >&2; exit 2; }

# 一次 GET，把状态码写进 $CODE，响应体留在 $BODY
get() {
  CODE=$(curl -s -o "$TMP/body" -w '%{http_code}' --max-time 20 "$@" 2>/dev/null)
  CODE=${CODE:-000}
  BODY=$(cat "$TMP/body" 2>/dev/null || true)
}

echo "契约自检 $BASE（contract $(sed -n 's/.*"toolingVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$HERE/contract.json" 2>/dev/null | head -1)）"

# 1. 登录（契约文件里不列它，但所有管理面都要它）
printf '{"username":"%s","password":"%s"}' "$USER" "$PASS" > "$TMP/login.json"
CODE=$(curl -s -o "$TMP/login-res" -w '%{http_code}' --max-time 20 \
  -X POST "$BASE/api/auth/jwt/login" -H 'Content-Type: application/json' \
  --data-binary "@$TMP/login.json" 2>/dev/null)
CODE=${CODE:-000}
TOKEN=$(sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$TMP/login-res" 2>/dev/null | head -1)
if [ "$CODE" = "200" ] && [ -n "$TOKEN" ]; then
  ok auth.login "HTTP 200, token ${#TOKEN} 字符"
else
  fail auth.login "HTTP $CODE（检查 --user/--pass）"
fi

AUTH="X-Jairouter-Token: $TOKEN"

# 2. health（公开端点）
get "$BASE/actuator/health"
if [ "$CODE" = "200" ] && printf '%s' "$BODY" | grep -q '"status":"UP"'; then
  ok health "HTTP 200, status=UP"
else
  fail health "HTTP $CODE"
fi

# 3. 需要认证的四个契约面
get -H "$AUTH" "$BASE/api/config/instance/chat"
if [ "$CODE" = "200" ] && printf '%s' "$BODY" | grep -q '"data"'; then
  ok instance.chat "/api/config/instance/chat → data"
else
  fail instance.chat "HTTP $CODE"
fi

get -H "$AUTH" "$BASE/api/models/stats"
if [ "$CODE" = "200" ] && printf '%s' "$BODY" | grep -q '"totalInstances"'; then
  ok models.stats "/api/models/stats → data.totalInstances"
else
  fail models.stats "HTTP $CODE"
fi

get -H "$AUTH" "$BASE/api/auth/api-keys"
if [ "$CODE" = "200" ] && printf '%s' "$BODY" | grep -q '"data"'; then
  ok api-keys "/api/auth/api-keys → data"
else
  fail api-keys "HTTP $CODE"
fi

get -H "$AUTH" "$BASE/actuator/prometheus"
if [ "$CODE" = "200" ] && printf '%s' "$BODY" | grep -q '^jvm_'; then
  ok metrics.prometheus "Prometheus 指标存在"
else
  fail metrics.prometheus "HTTP $CODE"
fi

# 4. 版本范围（有 --version 才判；工具过时报「工具不兼容」，不报「服务故障」）
RANGE=$(sed -n 's/.*"targetRange"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$HERE/contract.json" 2>/dev/null | head -1)
# sort -V 排序后的第一行就是较小者；据此实现 >= 与 <
ge() { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -1)" = "$2" ]; }
lt() { [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -1)" = "$1" ]; }
if [ -n "$VERSION" ] && [ -n "$RANGE" ]; then
  LO=$(printf '%s' "$RANGE" | sed -n 's/.*>=[[:space:]]*\([0-9][0-9.]*\).*/\1/p')
  HI=$(printf '%s' "$RANGE" | sed -n 's/.*<[[:space:]]*\([0-9][0-9.]*\).*/\1/p')
  if [ -n "$LO" ] && [ -n "$HI" ]; then
    if ge "$VERSION" "$LO" && lt "$VERSION" "$HI"; then
      ok version.range "$VERSION ∈ $RANGE"
    else
      fail version.range "$VERSION ∉ $RANGE（工具与网关版本不兼容，请升级工具链）"
    fi
  fi
elif [ -z "$VERSION" ]; then
  printf '[WARN] %-22s %s\n' version.range "未提供 --version，跳过范围校验"
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "全部通过（$PASSED 项）"
  exit 0
fi
echo "失败 $FAILED 项 / 通过 $PASSED 项"
exit 2
