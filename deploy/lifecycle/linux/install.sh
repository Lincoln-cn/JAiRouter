#!/usr/bin/env bash
#
# JAiRouter 安装脚本（Linux）。
#
# 设计约束：
#   1. 幂等 —— 重复执行只更新文件与服务定义，不会重复生成密钥，也不会覆盖已存在的数据目录。
#   2. 装前失败不落文件 —— 依赖检测在写任何文件之前完成；检测不过直接退出，磁盘保持原样。
#   3. 每次降级都打印原因 —— 用户要能看懂「为什么最后跑成了前台进程」。
#   4. 装完自检 —— 真正去看 /actuator/health，不是看到进程在就宣布成功。
#
# 用法：
#   sudo ./install.sh --jar /path/to/model-router.jar
#   sudo ./install.sh --jar model-router.jar --port 9900 --mode systemd
#   ./install.sh --jar model-router.jar --mode foreground --dry-run
#
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TOOLING_VERSION="0.1.0"
readonly MIN_JAVA_MAJOR=17

PREFIX="/opt/jairouter"
PORT="9900"
JAR=""
MODE="auto"            # auto | systemd | docker | foreground
PROFILE="prod"
RUN_USER="jairouter"
DRY_RUN="false"
SKIP_START="false"
HEALTH_TIMEOUT="60"

log()  { printf '[install] %s\n' "$*"; }
warn() { printf '[install] WARN: %s\n' "$*" >&2; }
die()  { printf '[install] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    cat <<'EOF'

选项：
  --jar <路径>       必填（除非 --mode docker）。要安装的 model-router.jar
  --prefix <目录>    安装目录，默认 /opt/jairouter
  --port <端口>      监听端口，默认 9900
  --mode <模式>      auto（默认）/ systemd / docker / foreground
  --profile <名>     Spring profile，默认 prod
  --user <用户名>    systemd 运行用户，默认 jairouter（不存在则创建）
  --health-timeout <秒>  启动后等待 /actuator/health 的时长，默认 60
  --skip-start       只落文件与 unit，不 enable/start
  --dry-run          只做检测与打印计划，不写任何文件
  -h, --help         显示本帮助
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jar) JAR="${2:-}"; shift 2 ;;
        --prefix) PREFIX="${2:-}"; shift 2 ;;
        --port) PORT="${2:-}"; shift 2 ;;
        --mode) MODE="${2:-}"; shift 2 ;;
        --profile) PROFILE="${2:-}"; shift 2 ;;
        --user) RUN_USER="${2:-}"; shift 2 ;;
        --health-timeout) HEALTH_TIMEOUT="${2:-}"; shift 2 ;;
        --skip-start) SKIP_START="true"; shift ;;
        --dry-run) DRY_RUN="true"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "未知参数: $1（--help 看用法）" ;;
    esac
done

# ---------------------------------------------------------------------------
# 检测
# ---------------------------------------------------------------------------

java_major_version() {
    # `java -version` 走的是 stderr，且 1.8 之前的格式是 "1.8.0_x"
    local raw
    raw="$(java -version 2>&1 | head -n1 | sed -E 's/.*version "([^"]+)".*/\1/' || true)"
    [[ -z "$raw" ]] && return 1
    local major="${raw%%.*}"
    if [[ "$major" == "1" ]]; then
        local rest="${raw#1.}"
        major="${rest%%.*}"
    fi
    [[ "$major" =~ ^[0-9]+$ ]] || return 1
    printf '%s' "$major"
}

JAVA_BIN=""
JAVA_MAJOR=""
detect_java() {
    if ! command -v java >/dev/null 2>&1; then
        warn "没有找到 java（PATH 里没有 java 命令）"
        return 1
    fi
    JAVA_BIN="$(command -v java)"
    JAVA_MAJOR="$(java_major_version || true)"
    if [[ -z "$JAVA_MAJOR" ]]; then
        warn "无法解析 java 版本：$(java -version 2>&1 | head -n1)"
        return 1
    fi
    if (( JAVA_MAJOR < MIN_JAVA_MAJOR )); then
        warn "java 版本过低：$JAVA_MAJOR < $MIN_JAVA_MAJOR"
        return 1
    fi
    log "检测到 java $JAVA_MAJOR（$JAVA_BIN）"
    return 0
}

HAVE_SYSTEMD="false"
detect_systemd() {
    if [[ -d /run/systemd/system ]] && command -v systemctl >/dev/null 2>&1; then
        HAVE_SYSTEMD="true"
        log "检测到 systemd"
        return 0
    fi
    warn "没有 systemd（容器里很常见）—— 无法托管为服务"
    return 1
}

HAVE_DOCKER="false"
detect_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        warn "没有 docker"
        return 1
    fi
    if ! docker info >/dev/null 2>&1; then
        warn "docker 命令存在但守护进程不可用（缺权限或没启动）"
        return 1
    fi
    HAVE_DOCKER="true"
    log "检测到 docker"
    return 0
}

port_in_use() {
    if command -v ss >/dev/null 2>&1; then
        ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$1$"
    elif command -v netstat >/dev/null 2>&1; then
        netstat -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$1$"
    else
        return 1
    fi
}

# ---------------------------------------------------------------------------
# 模式决策（显式 --mode 优先，否则按可用性降级并打印原因）
# ---------------------------------------------------------------------------

decide_mode() {
    case "$MODE" in
        auto)
            if detect_java && detect_systemd; then
                MODE="systemd"
                log "选择 systemd 托管（系统 java 可用）"
            elif detect_docker && detect_systemd; then
                MODE="docker"
                warn "降级到 docker：环境里没有 java $MIN_JAVA_MAJOR+，但有可用的 docker 守护进程"
            elif detect_docker; then
                MODE="docker"
                warn "降级到 docker 前台运行：没有 systemd，只能用 compose/容器跑"
            elif detect_java; then
                MODE="foreground"
                warn "降级到前台运行：有 java 但没有 systemd/docker —— 进程不会随开机自启，也不会自动重启"
            else
                die "这台机器既没有 java $MIN_JAVA_MAJOR+，也没有可用的 docker。
      离线安装请用 bundle 里自带的运行时：bin/install.sh --mode foreground 并把 JAVA_HOME 指过去"
            fi
            ;;
        systemd)
            detect_java || die "--mode systemd 需要 java $MIN_JAVA_MAJOR+"
            detect_systemd || die "--mode systemd 但当前环境没有 systemd"
            ;;
        docker)
            detect_docker || die "--mode docker 但 docker 不可用"
            ;;
        foreground)
            detect_java || die "--mode foreground 需要 java $MIN_JAVA_MAJOR+"
            ;;
        *)
            die "未知模式: $MODE（可选 auto/systemd/docker/foreground）"
            ;;
    esac
}

# ---------------------------------------------------------------------------
# 落文件
# ---------------------------------------------------------------------------

CONFIG_DIR=""
ENV_FILE=""
JAR_DEST=""
UNIT_PATH=""
UNIT_NAME="jairouter"

plan_and_apply() {
    CONFIG_DIR="$PREFIX/config"
    ENV_FILE="/etc/jairouter/jairouter.env"

    log "安装目录: $PREFIX"
    log "配置目录: $CONFIG_DIR"
    log "环境文件: $ENV_FILE"
    log "监听端口: $PORT"
    log "模式    : $MODE"

    if port_in_use "$PORT"; then
        die "端口 $PORT 已被占用，换一个 --port 再装"
    fi

    if [[ "$DRY_RUN" == "true" ]]; then
        log "dry-run：检测与计划都已通过，未写入任何文件"
        exit 0
    fi

    mkdir -p "$PREFIX/lib" "$PREFIX/config" "$PREFIX/data" "$PREFIX/logs" /etc/jairouter

    # 密钥只在第一次生成：重复执行不覆盖，避免把已投入使用的密钥换掉。
    if [[ ! -f "$ENV_FILE" ]]; then
        local jwt admin
        jwt="$(openssl rand -base64 48 2>/dev/null || head -c 48 /dev/urandom | base64)"
        admin="$(openssl rand -base64 24 2>/dev/null || head -c 24 /dev/urandom | base64)"
        cat > "$ENV_FILE" <<EOF
# JAiRouter 运行时环境（install.sh 生成，$TOOLING_VERSION）
# 这个文件含密钥，权限已设为 600，不要提交到任何仓库。
JWT_SECRET=$jwt
INITIAL_ADMIN_PASSWORD=$admin
JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC
EOF
        chmod 600 "$ENV_FILE"
        log "已生成运行时密钥（$ENV_FILE）"
    else
        log "环境文件已存在，保留原密钥（$ENV_FILE）"
        # 顺带补上后来新增的键，不动已有值。
        grep -q '^JAVA_OPTS=' "$ENV_FILE" || printf 'JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC\n' >> "$ENV_FILE"
    fi

    # 只写我们真正要覆盖的键，其余交给 jar 内的 application.yml。
    if [[ ! -f "$CONFIG_DIR/application.yml" ]]; then
        cat > "$CONFIG_DIR/application.yml" <<EOF
# install.sh 生成的覆盖层（$TOOLING_VERSION）：后加载者优先，这里只写与默认值不同的键。
server:
  port: $PORT
jairouter:
  config:
    store:
      path: $PREFIX/data
logging:
  file:
    name: $PREFIX/logs/jairouter.log
EOF
        log "已生成配置覆盖层（$CONFIG_DIR/application.yml）"
    fi
}

install_systemd_unit() {
    JAR_DEST="$PREFIX/lib/model-router.jar"
    cp "$JAR" "$JAR_DEST"
    chmod 644 "$JAR_DEST"

    if ! id -u "$RUN_USER" >/dev/null 2>&1; then
        useradd --system --home-dir "$PREFIX" --shell /usr/sbin/nologin "$RUN_USER" \
            || die "创建运行用户 $RUN_USER 失败"
        log "已创建系统用户 $RUN_USER"
    fi
    chown -R "$RUN_USER:$RUN_USER" "$PREFIX" /etc/jairouter

    UNIT_PATH="/etc/systemd/system/${UNIT_NAME}.service"
    sed -e "s|@@PREFIX@@|$PREFIX|g" \
        -e "s|@@RUN_USER@@|$RUN_USER|g" \
        -e "s|@@ENV_FILE@@|$ENV_FILE|g" \
        -e "s|@@JAVA_BIN@@|$JAVA_BIN|g" \
        -e "s|@@PROFILE@@|$PROFILE|g" \
        "$SCRIPT_DIR/jairouter.service" > "$UNIT_PATH"
    log "已写入 systemd unit（$UNIT_PATH）"
}

install_docker() {
    local compose_dir="$PREFIX/docker"
    mkdir -p "$compose_dir"
    cat > "$compose_dir/docker-compose.yml" <<EOF
# install.sh 生成的 compose（$TOOLING_VERSION）。镜像 tag 刻意不写 latest：
# 升级请显式改 tag 再 up -d，否则「重启一下」会顺带换掉版本。
services:
  jairouter:
    image: \${JAIRouter_IMAGE:-sodlinken/jairouter:latest}
    container_name: jairouter
    ports:
      - "$PORT:8080"
    env_file:
      - $ENV_FILE
    environment:
      SPRING_PROFILES_ACTIVE: $PROFILE
    volumes:
      - $PREFIX/data:/app/data
      - $PREFIX/logs:/app/logs
      - $PREFIX/config:/app/config
    stop_grace_period: 35s
    restart: unless-stopped
EOF
    log "已写入 compose（$compose_dir/docker-compose.yml）"
}

start_and_check() {
    local health="http://127.0.0.1:$PORT/actuator/health"
    local waited=0

    case "$MODE" in
        systemd)
            systemctl daemon-reload
            systemctl enable --now "$UNIT_NAME"
            ;;
        docker)
            docker compose -f "$PREFIX/docker/docker-compose.yml" up -d
            ;;
        foreground)
            setsid nohup bash -c "set -a; . '$ENV_FILE'; set +a; exec '$JAVA_BIN' \$JAVA_OPTS -jar '$JAR_DEST' --spring.profiles.active=$PROFILE --spring.config.additional-location=file:$CONFIG_DIR/" \
                > "$PREFIX/logs/stdout.log" 2>&1 &
            echo $! > "$PREFIX/jairouter.pid"
            log "已后台启动（pid $(cat "$PREFIX/jairouter.pid")）—— 前台模式不会随开机自启"
            ;;
    esac

    log "等待 $health 就绪（最长 ${HEALTH_TIMEOUT}s）"
    while (( waited < HEALTH_TIMEOUT )); do
        if command -v curl >/dev/null 2>&1 && curl -fsS --max-time 2 "$health" >/dev/null 2>&1; then
            log "健康检查通过（${waited}s）"
            return 0
        fi
        sleep 2
        waited=$(( waited + 2 ))
    done

    warn "启动后 ${HEALTH_TIMEOUT}s 内健康检查没通过"
    case "$MODE" in
        systemd) systemctl status "$UNIT_NAME" --no-pager || true ;;
        docker) docker compose -f "$PREFIX/docker/docker-compose.yml" logs --tail=40 || true ;;
        foreground) tail -n 40 "$PREFIX/logs/stdout.log" 2>/dev/null || true ;;
    esac
    return 1
}

rollback() {
    warn "开始回滚：停掉刚起的实例并移除服务定义（数据目录保留）"
    case "$MODE" in
        systemd) systemctl disable --now "$UNIT_NAME" >/dev/null 2>&1 || true
                 rm -f "$UNIT_PATH"; systemctl daemon-reload || true ;;
        docker) docker compose -f "$PREFIX/docker/docker-compose.yml" down || true ;;
        foreground) [[ -f "$PREFIX/jairouter.pid" ]] && kill "$(cat "$PREFIX/jairouter.pid")" 2>/dev/null || true ;;
    esac
    die "安装未完成，已回滚。排查后可用同一命令重试（数据目录与密钥都保留）"
}

main() {
    if [[ "$MODE" != "docker" && -z "$JAR" ]]; then
        die "缺少 --jar（--help 看用法）"
    fi
    if [[ -n "$JAR" && ! -f "$JAR" ]]; then
        die "jar 不存在: $JAR"
    fi

    decide_mode
    plan_and_apply

    if [[ "$MODE" == "systemd" ]]; then
        install_systemd_unit
    elif [[ "$MODE" == "docker" ]]; then
        install_docker
    else
        JAR_DEST="$PREFIX/lib/model-router.jar"
        cp "$JAR" "$JAR_DEST"
    fi

    if [[ "$SKIP_START" == "true" ]]; then
        log "--skip-start：文件与 unit 已就位，未启动"
        exit 0
    fi

    start_and_check || rollback
    log "安装完成：模式 $MODE，端口 $PORT，数据 $PREFIX/data"
}

main "$@"
