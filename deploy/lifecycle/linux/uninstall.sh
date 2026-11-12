#!/usr/bin/env bash
#
# JAiRouter 卸载脚本（Linux）。
#
# 两条档位：
#   默认      只删「程序」—— 服务注册、unit、jar、容器与网络；数据、日志、密钥一律保留。
#   --purge   连数据一起删（H2 文件库、日志、环境文件里的密钥）。
#
# 刻意不做的事：
#   - 不执行 DROP TABLE、不清 Redis key。数据库是共享资源，它可能同时被别的实例或别的系统用着，
#     一个卸载脚本没有资格决定它的去留。要清请人工执行，脚本只把该看的命令打印出来。
#   - 不删除镜像。别的部署可能还在用同一个 tag。
#
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PREFIX="/opt/jairouter"
UNIT_NAME="jairouter"
ENV_FILE="/etc/jairouter/jairouter.env"
MODE="auto"
PURGE="false"
ASSUME_YES="false"

log()  { printf '[uninstall] %s\n' "$*"; }
warn() { printf '[uninstall] WARN: %s\n' "$*" >&2; }
die()  { printf '[uninstall] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
用法: uninstall.sh [选项]

  --prefix <目录>   安装目录，默认 /opt/jairouter
  --mode <模式>     auto（默认）/ systemd / docker / foreground
  --purge           连数据、日志、密钥一起删（不可恢复，会二次确认）
  -y, --yes         跳过确认
  -h, --help        显示本帮助
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --prefix) PREFIX="${2:-}"; shift 2 ;;
        --mode) MODE="${2:-}"; shift 2 ;;
        --purge) PURGE="true"; shift ;;
        -y|--yes) ASSUME_YES="true"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "未知参数: $1（--help 看用法）" ;;
    esac
done

confirm() {
    [[ "$ASSUME_YES" == "true" ]] && return 0
    local answer
    read -r -p "$1 [y/N] " answer
    [[ "$answer" == "y" || "$answer" == "Y" ]]
}

detect_mode() {
    [[ "$MODE" != "auto" ]] && return 0
    if [[ -f "/etc/systemd/system/${UNIT_NAME}.service" ]] && command -v systemctl >/dev/null 2>&1; then
        MODE="systemd"
    elif [[ -f "$PREFIX/docker/docker-compose.yml" ]]; then
        MODE="docker"
    elif [[ -f "$PREFIX/jairouter.pid" ]]; then
        MODE="foreground"
    else
        MODE="systemd"
        warn "没找到任何安装痕迹，按 systemd 模式尝试清理（如果什么都没装，下面的步骤都会安全地跳过）"
    fi
    log "卸载模式: $MODE"
}

stop_and_remove_program() {
    case "$MODE" in
        systemd)
            if systemctl list-unit-files 2>/dev/null | grep -q "^${UNIT_NAME}.service"; then
                log "停止并禁用 ${UNIT_NAME}.service"
                systemctl disable --now "$UNIT_NAME" || warn "停止服务返回非零（可能本来就没在跑）"
            else
                warn "没有注册 ${UNIT_NAME}.service，跳过服务停止"
            fi
            rm -f "/etc/systemd/system/${UNIT_NAME}.service"
            systemctl daemon-reload 2>/dev/null || true
            log "已移除 unit 定义"
            ;;
        docker)
            if [[ -f "$PREFIX/docker/docker-compose.yml" ]]; then
                # down 只删容器与网络，不动 named volume 与宿主机上的 bind mount 目录。
                docker compose -f "$PREFIX/docker/docker-compose.yml" down || warn "compose down 返回非零"
                log "已停止并移除容器与网络"
            fi
            ;;
        foreground)
            if [[ -f "$PREFIX/jairouter.pid" ]]; then
                local pid; pid="$(cat "$PREFIX/jairouter.pid")"
                if kill -0 "$pid" 2>/dev/null; then
                    log "向 pid $pid 发送 SIGTERM（优雅停机，最多等 35s）"
                    kill -TERM "$pid"
                    local waited=0
                    while kill -0 "$pid" 2>/dev/null && (( waited < 35 )); do sleep 1; waited=$(( waited + 1 )); done
                    if kill -0 "$pid" 2>/dev/null; then
                        warn "35s 内未退出，改发 SIGKILL —— 在途请求已中断"
                        kill -KILL "$pid" 2>/dev/null || true
                    fi
                else
                    warn "pid 文件里的 $pid 已不在运行"
                fi
                rm -f "$PREFIX/jairouter.pid"
            fi
            ;;
    esac

    # jar 属于「程序」，默认删；配置覆盖层属于「程序」也删，但数据目录保留。
    rm -f "$PREFIX/lib/model-router.jar"
    rmdir "$PREFIX/lib" 2>/dev/null || true
    log "已移除程序文件（$PREFIX/lib）"
}

purge_data() {
    warn "即将删除数据与密钥，这一步不可恢复"
    log "  数据目录: $PREFIX/data"
    log "  日志目录: $PREFIX/logs"
    log "  环境文件: $ENV_FILE（含 JWT_SECRET 与初始管理员密码）"
    confirm "确认继续？" || die "已取消，未删除任何数据"

    rm -rf "$PREFIX/data" "$PREFIX/logs" "$PREFIX/config" "$PREFIX/docker"
    rm -f "$ENV_FILE"
    rmdir /etc/jairouter 2>/dev/null || true
    rmdir "$PREFIX" 2>/dev/null || true
    log "已删除数据、日志、配置与密钥"
}

report_kept() {
    log "以下内容被保留（远程状态脚本无权处置）："
    log "  - PostgreSQL 里的库与表：卸载不会 DROP，需要人工清理"
    log "  - Redis 里的键：jairouter:* 会随 TTL 过期，但不会立即消失"
    log "  - 容器镜像：sodlinken/jairouter:<tag> 仍然存在，别的部署可能还在用"
    if [[ -d "$PREFIX/data" ]]; then
        log "  - 本地数据目录: $PREFIX/data（含 H2 文件库，需要 --purge 才会删）"
    fi
    if [[ -f "$ENV_FILE" ]]; then
        log "  - 环境文件: $ENV_FILE（含密钥，需要 --purge 才会删）"
    fi
}

main() {
    if [[ ! -d "$PREFIX" && ! -f "/etc/systemd/system/${UNIT_NAME}.service" ]]; then
        warn "看起来这里没有安装过 JAiRouter（$PREFIX 不存在）。"
        warn "如果确实装过，用 --prefix 指定实际安装目录。"
    fi

    detect_mode
    stop_and_remove_program

    if [[ "$PURGE" == "true" ]]; then
        purge_data
    else
        report_kept
    fi

    log "卸载完成"
}

main "$@"
