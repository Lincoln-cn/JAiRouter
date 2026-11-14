#!/bin/sh
# SLA 工具统一入口：按环境自动降级，一层都不改客户的系统。
#
#   1. node  >= 18        —— 原生实现，保真度最高（无需容器）
#   2. docker             —— 用镜像跑 node 实现（离线用本地镜像，在线回退 node:22-alpine）
#   3. python 2.7+ / 3.8+ —— sla.py（标准库实现，覆盖 preflight / doctor / loadtest）
#   4. curl               —— preflight-curl.sh（仅契约自检，POSIX sh）
#   5. 都没有             —— 明确报错并说明缺什么
#
# 用法：
#   ./run.sh preflight --base http://127.0.0.1:8080 --user admin --pass '***'
#   ./run.sh doctor    --base-url http://127.0.0.1:8080 --user admin --pass '***'
#   ./run.sh loadtest  --url http://127.0.0.1:8080/actuator/health --method GET --concurrency 5 --duration 10
#   ./run.sh verify    --base http://127.0.0.1:8080 --user admin --pass '***' --allow-writes
#   ./run.sh --probe                 # 只打印环境探测结果，不执行工具
#
# 为什么 Python 要检查版本：2.6 没有 dict 推导与部分 urllib 行为，3.6/3.7 的
# argparse 与 ssl 行为也和实现不匹配。版本不够就继续往下降级，而不是让脚本
# 在中途抛一个让人看不懂的 ImportError。
set -u

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 3

MIN_NODE_MAJOR=18
PY2_MIN_MINOR=7
PY3_MIN_MINOR=8

say() { printf '%s\n' "$*" >&2; }
die() { printf '错误: %s\n' "$*" >&2; exit 3; }
have() { command -v "$1" >/dev/null 2>&1; }

node_ok() {
  have node || return 1
  node -e "process.exit(Number(process.versions.node.split('.')[0]) >= $MIN_NODE_MAJOR ? 0 : 1)" \
    >/dev/null 2>&1
}

python_ok() {
  "$1" -c "import sys
v = sys.version_info
ok = (v[0] == 2 and v[1] >= $PY2_MIN_MINOR) or (v[0] == 3 and v[1] >= $PY3_MIN_MINOR)
sys.exit(0 if ok else 1)" >/dev/null 2>&1
}

find_python() {
  for cand in python3 python python2; do
    have "$cand" || continue
    if python_ok "$cand"; then
      printf '%s' "$cand"
      return 0
    fi
  done
  return 1
}

python_version_of() {
  "$1" -c "import sys; sys.stdout.write('%d.%d.%d' % tuple(sys.version_info[:3]))" 2>/dev/null
}

node_version_of() {
  node -v 2>/dev/null
}

docker_usable() {
  have docker || return 1
  docker info >/dev/null 2>&1
}

sla_image() {
  ver=$(sed -n 's/.*"toolingVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    "$HERE/contract.json" 2>/dev/null | head -1)
  image="jairouter-sla:${ver:-dev}"
  if docker image inspect "$image" >/dev/null 2>&1; then
    printf '%s' "$image"
  else
    printf '%s' "node:22-alpine"
  fi
}

probe() {
  if node_ok; then
    say "node     可用 ($(node_version_of))"
  elif have node; then
    say "node     已安装但版本过低 ($(node_version_of))，需要 >= $MIN_NODE_MAJOR"
  else
    say "node     未安装"
  fi

  if docker_usable; then
    say "docker   可用，将使用镜像 $(sla_image)"
  elif have docker; then
    say "docker   已安装但 daemon 不可用（Docker Desktop 没起来 / 无权限）"
  else
    say "docker   未安装"
  fi

  py=$(find_python) || py=""
  if [ -n "$py" ]; then
    say "python   可用 ($py $(python_version_of "$py"))"
  else
    for cand in python3 python python2; do
      have "$cand" || continue
      say "python   $cand 版本过低 ($(python_version_of "$cand"))，需要 2.7+ 或 3.8+"
      break
    done
    say "python   无可用解释器"
  fi

  if have curl; then
    say "curl     可用（仅 preflight 降级）"
  else
    say "curl     未安装"
  fi

  if node_ok; then say "→ 将使用 node"
  elif docker_usable; then say "→ 将使用 docker"
  elif [ -n "$py" ]; then say "→ 将使用 python"
  elif have curl; then say "→ 将使用 curl（仅 preflight）"
  else say "→ 没有任何可用运行时"; fi
}

if [ "${1:-}" = "--probe" ] || [ "${1:-}" = "-p" ]; then
  probe
  exit 0
fi

if [ $# -eq 0 ]; then
  say "用法: $0 <preflight|doctor|loadtest|verify|audit> [参数...]"
  say "      $0 --probe    查看环境探测结果"
  exit 2
fi

TOOL=$1
shift

case "$TOOL" in
  preflight|doctor|loadtest|verify|audit) ;;
  *) die "未知子命令: $TOOL（可选 preflight / doctor / loadtest / verify）" ;;
esac

run_node() {
  case "$TOOL" in
    preflight) exec node "$HERE/preflight.mjs" "$@" ;;
    doctor) exec node "$HERE/diagnose/doctor.mjs" "$@" ;;
    loadtest) exec node "$HERE/capacity/loadtest.mjs" "$@" ;;
    verify) exec node "$HERE/verify/post-upgrade.mjs" "$@" ;;
  esac
}

run_docker() {
  image=$(sla_image)
  # --network host 让容器内的 127.0.0.1 指向宿主机；否则会把「连不上」报成服务故障。
  case "$TOOL" in
    preflight) exec docker run --rm --network host -v "$HERE:/sla:ro" -w /sla "$image" preflight.mjs "$@" ;;
    doctor) exec docker run --rm --network host -v "$HERE:/sla:ro" -w /sla "$image" diagnose/doctor.mjs "$@" ;;
    loadtest) exec docker run --rm --network host -v "$HERE:/sla:ro" -w /sla "$image" capacity/loadtest.mjs "$@" ;;
    verify) exec docker run --rm --network host -v "$HERE:/sla:ro" -w /sla "$image" verify/post-upgrade.mjs "$@" ;;
  esac
}

run_python() {
  # $1 是解释器路径，必须先 shift，否则 "$@" 会把它当成多余的位置参数再传一遍
  # （实测：不下 shift 时 argparse 报 "unrecognized arguments: python3"）
  py="$1"
  shift
  exec "$py" "$HERE/sla.py" "$TOOL" "$@"
}

run_curl() {
  if [ "$TOOL" = "preflight" ]; then
    exec sh "$HERE/preflight-curl.sh" "$@"
  fi
  die "$TOOL 没有 curl 实现（curl 只覆盖 preflight）；请安装 node>=18、docker 或 Python 2.7+/3.8+"
}

if node_ok; then
  run_node "$@"
fi

if docker_usable; then
  run_docker "$@"
fi

PY=$(find_python) || PY=""
if [ -n "$PY" ]; then
  run_python "$PY" "$@"
fi

if have curl; then
  run_curl "$@"
fi

die "没有可用的运行时。需要以下之一：node >= $MIN_NODE_MAJOR、docker（daemon 可用）、python 2.7+/3.8+、curl。"
