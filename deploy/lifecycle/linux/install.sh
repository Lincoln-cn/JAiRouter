#!/usr/bin/env bash
#
# JAiRouter 安装脚本（Linux）。
#
# 设计约束：
#   1. 幂等 —— 重复执行只更新文件与服务定义，不会重复生成密钥，也不会覆盖已存在的数据目录。
#   2. 装前失败不落文件 —— 依赖检测在写任何文件之前完成；检测不过直接退出，磁盘保持原样。
#   3. 每次降级都打印原因 —— 用户要能看懂「为什么最后跑成了前台进程」。
#   4. 装完自检 —— 真正去看 /actuator/health，不是看到进程在就宣布成功。
#   5. 能力检测要诚实 —— 「有 docker」不等于「有 docker compose」。只装了 docker 引擎、
#      没有 compose 插件的机器很常见（Ubuntu 的 docker.io 包就是这样），这种情况必须判成
#      docker 模式不可用，而不是装完才发现 `docker compose up` 根本不存在。
#   6. 不碰别人的安装 —— 目标目录已存在内容且没有本脚本写的 jairouter.json 标记时，
#      除非显式 --force，否则拒绝写入。
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
FORCE="false"
IMAGE_TAG="latest"

log()  { printf '[install] %s\n' "$*"; }
warn() { printf '[install] WARN: %s\n' "$*" >&2; }
die()  { printf '[install] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    # 头部注释的结束位置会随改动漂移，按内容截取而不是数行号。
    awk '/^# JAiRouter 安装脚本/{f=1} /^set -euo pipefail/{f=0} f' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    cat <<'EOF'

选项：
  --jar <路径>       必填（除非 --mode docker）。要安装的 model-router.jar
  --prefix <目录>    安装目录，默认 /opt/jairouter
  --port <端口>      监听端口，默认 9900
  --mode <模式>      auto（默认）/ systemd / docker / foreground
  --profile <名>     Spring profile，默认 prod
  --user <用户名>    systemd 运行用户，默认 jairouter（不存在则创建）
  --health-timeout <秒>  启动后等待 /actuator/health 的时长，默认 60
  --image-tag <tag>  docker 模式使用的镜像 tag，默认 latest（生产建议固定版本）
  --force            目标目录已有内容但不是本脚本装的，仍然写入
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
        --image-tag) IMAGE_TAG="${2:-}"; shift 2 ;;
        --force) FORCE="true"; shift ;;
        --skip-start) SKIP_START="true"; shift ;;
        --dry-run) DRY_RUN="true"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "未知参数: $1（--help 看用法）" ;;
    esac
done

# 安装目录名参与两件事：环境文件名与容器名。同一台机器上装第二个实例时，
# 两者都必须区分开，否则第二个实例会复用第一个的密钥、顶掉第一个的容器。
PREFIX="$(printf '%s' "$PREFIX" | sed 's:/*$::')"
readonly PREFIX_NAME="$(basename "$PREFIX")"
ENV_DIR="/etc/jairouter"
ENV_FILE=""
if [[ "$PREFIX_NAME" == "jairouter" ]]; then
    ENV_FILE="$ENV_DIR/jairouter.env"
else
    ENV_FILE="$ENV_DIR/${PREFIX_NAME}.env"
fi
CONTAINER_NAME="$PREFIX_NAME"

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
COMPOSE=()              # 非空表示 compose 可用：("docker" "compose") 或 ("docker-compose")
detect_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        warn "没有 docker"
        return 1
    fi
    if ! docker info >/dev/null 2>&1; then
        warn "docker 命令存在但守护进程不可用（缺权限或没启动）"
        return 1
    fi

    # 「有 docker」不代表「有 compose」：只装引擎不装 compose 插件的机器很常见。
    # 这里必须分别判断，否则会选中一个装完就起不来的模式。
    if docker compose version >/dev/null 2>&1; then
        COMPOSE=("docker" "compose")
    elif command -v docker-compose >/dev/null 2>&1 && docker-compose version >/dev/null 2>&1; then
        COMPOSE=("docker-compose")
        warn "docker compose 插件不存在，回退到独立的 docker-compose 命令"
    else
        warn "docker 守护进程可用，但没有 compose（docker compose 与 docker-compose 都不可用）—— 无法用容器方式安装"
        return 1
    fi

    HAVE_DOCKER="true"
    log "检测到 docker（compose: ${COMPOSE[*]}）"
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

# 目标目录里是不是已经有别人的东西。本脚本安装成功会写一个 json 标记，
# 有标记说明是我们装的，可以继续；没标记又有内容，就必须显式 --force。
check_prefix_ownership() {
    [[ "$FORCE" == "true" ]] && return 0
    [[ -d "$PREFIX" ]] || return 0
    [[ -z "$(ls -A "$PREFIX" 2>/dev/null)" ]] && return 0
    [[ -f "$PREFIX/jairouter.json" ]] && return 0
    die "安装目录 $PREFIX 已存在且有内容，但没有 jairouter.json 标记 —— 它可能是别的部署的数据目录。
      换一个 --prefix，或确认可以覆盖后加 --force"
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
                warn "降级到 docker：环境里没有 java $MIN_JAVA_MAJOR+，但有可用的 docker + compose"
            elif detect_docker; then
                MODE="docker"
                warn "降级到 docker 前台运行：没有 systemd，只能用 compose/容器跑"
            elif detect_java; then
                MODE="foreground"
                warn "降级到前台运行：有 java 但没有 systemd/docker —— 进程不会随开机自启，也不会自动重启"
            else
                die "这台机器既没有 java $MIN_JAVA_MAJOR+，也没有可用的 docker + compose。
      离线安装请用 bundle 里自带的运行时：bin/install.sh --mode foreground 并把 JAVA_HOME 指过去"
            fi
            ;;
        systemd)
            detect_java || die "--mode systemd 需要 java $MIN_JAVA_MAJOR+"
            detect_systemd || die "--mode systemd 但当前环境没有 systemd"
            ;;
        docker)
            detect_docker || die "--mode docker 但 docker + compose 不可用（见上面的检测原因）"
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

CONFIG_DIR="$PREFIX/config"
JAR_DEST="$PREFIX/lib/model-router.jar"
UNIT_PATH="/etc/systemd/system/jairouter.service"

plan_and_apply() {
    log "安装目录: $PREFIX"
    log "配置目录: $CONFIG_DIR"
    log "环境文件: $ENV_FILE"
    log "监听端口: $PORT"
    log "模式    : $MODE"

    check_prefix_ownership

    if port_in_use "$PORT"; then
        die "端口 $PORT 已被占用，换一个 --port 再装"
    fi

    if [[ "$DRY_RUN" == "true" ]]; then
        log "dry-run：检测与计划都已通过，未写入任何文件"
        exit 0
    fi

    mkdir -p "$PREFIX/lib" "$CONFIG_DIR" "$PREFIX/data" "$PREFIX/logs" "$ENV_DIR"

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
# JAVA_OPTS 必须带引号：这是被 set -a 后 source 的 shell 文件，
# 不加引号时 shell 会把 -Xmx2g 当成独立命令执行。
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
EOF
        chmod 600 "$ENV_FILE"
        log "已生成运行时密钥（$ENV_FILE）"
    else
        log "环境文件已存在，保留原密钥（$ENV_FILE）"
        # 顺带补上后来新增的键，不动已有值。
        grep -q '^JAVA_OPTS=' "$ENV_FILE" || printf 'JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"\n' >> "$ENV_FILE"
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

    # 安装标记：既是「这个目录归本脚本管」的凭据，也是排障时看安装参数的地方。
    # containerName 只在 docker 模式有意义，其它模式写空行（避免运维误以为有容器）。
    container_line=""
    [[ "$MODE" == "docker" ]] && container_line="  \"containerName\": \"$CONTAINER_NAME\","
    cat > "$PREFIX/jairouter.json" <<EOF
{
  "schemaVersion": 1,
  "toolingVersion": "$TOOLING_VERSION",
  "mode": "$MODE",
  "platform": "linux",
  "prefix": "$PREFIX",
  "port": $PORT,
  "profile": "$PROFILE",
  "envFile": "$ENV_FILE",
$container_line
  "dataDirectory": "$PREFIX/data",
  "logDirectory": "$PREFIX/logs"
}
EOF
}

install_systemd_unit() {
    cp "$JAR" "$JAR_DEST"
    chmod 644 "$JAR_DEST"

    if ! id -u "$RUN_USER" >/dev/null 2>&1; then
        useradd --system --home-dir "$PREFIX" --shell /usr/sbin/nologin "$RUN_USER" \
            || die "创建运行用户 $RUN_USER 失败"
        log "已创建系统用户 $RUN_USER"
    fi
    # 只改自己的目录与自己的环境文件 —— /etc/jairouter 下可能还有别的实例的 env。
    chown -R "$RUN_USER:$RUN_USER" "$PREFIX"
    chown "$RUN_USER:$RUN_USER" "$ENV_FILE"

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
# install.sh 生成的 compose（$TOOLING_VERSION）。
# 镜像 tag 默认 latest 只为快速起步：生产请用 --image-tag 固定版本，
# 否则某天重新 up 会把版本一起换掉。
# 注意：heredoc 是展开的，这段注释里不能出现反引号 —— 反引号里的内容会被当成命令执行。
services:
  jairouter:
    image: \${JAIRouter_IMAGE:-sodlinken/jairouter:$IMAGE_TAG}
    container_name: $CONTAINER_NAME
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

compose_down_safely() {
    "${COMPOSE[@]}" -f "$PREFIX/docker/docker-compose.yml" down >/dev/null 2>&1 || true
}

start_and_check() {
    local health="http://127.0.0.1:$PORT/actuator/health"
    local waited=0

    case "$MODE" in
        systemd)
            systemctl daemon-reload
            systemctl enable --now jairouter
            ;;
        docker)
            "${COMPOSE[@]}" -f "$PREFIX/docker/docker-compose.yml" up -d
            ;;
        foreground)
            # 先 cd 到 $PREFIX：logback-spring.xml 的 LOG_PATH 是相对路径（logs），
            # 不切换工作目录时日志会落到进程当前目录（常见是 /），而 install.json
            # 里登记的 logDirectory 指向 $PREFIX/logs，运维会找不到日志。
            # --server.port 必须走命令行参数：jar 内 application.yml 用 spring.config.import
            # 导入了 config/common/server.yml（port: 8080），而 import 的优先级高于
            # additional-location —— 只写 $CONFIG_DIR/application.yml 的话端口仍是 8080。
            setsid nohup bash -c "cd '$PREFIX' && set -a; . '$ENV_FILE'; set +a; exec '$JAVA_BIN' \$JAVA_OPTS -jar '$JAR_DEST' --server.port=$PORT --spring.profiles.active=$PROFILE --spring.config.additional-location=file:$CONFIG_DIR/" \
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
        systemd) systemctl status jairouter --no-pager || true ;;
        docker) "${COMPOSE[@]}" -f "$PREFIX/docker/docker-compose.yml" logs --tail=40 || true ;;
        foreground) tail -n 40 "$PREFIX/logs/stdout.log" 2>/dev/null || true ;;
    esac
    return 1
}

rollback() {
    warn "开始回滚：停掉刚起的实例并移除服务定义（数据目录保留）"
    case "$MODE" in
        systemd) systemctl disable --now jairouter >/dev/null 2>&1 || true
                 rm -f "$UNIT_PATH"; systemctl daemon-reload || true ;;
        docker) compose_down_safely ;;
        foreground)
            if [[ -f "$PREFIX/jairouter.pid" ]]; then
                pid="$(cat "$PREFIX/jairouter.pid")"
                # 按进程组发信号：setsid 起的 bash 是组 leader，java 是它的子进程。
                # 只杀 bash 会把 java 留成孤儿，继续占用 H2 文件锁（下次安装报 file is locked）。
                kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
                sleep 1
                kill -KILL -- "-$pid" 2>/dev/null || true
            fi
            ;;
    esac
    die "安装未完成，已回滚。排查后可用同一命令重试（数据目录与密钥都保留）"
}

main() {
    # jar 的存在性只在「真的要用它」时才检查：docker 模式不用 jar，
    # dry-run 模式根本不写盘，两者都不该被一个 jar 路径拦住。
    if [[ "$MODE" != "docker" && "$DRY_RUN" != "true" && -z "$JAR" ]]; then
        # auto 模式可能最终落到 docker，所以先放行，decide_mode 之后再判一次。
        [[ "$MODE" == "auto" ]] || die "缺少 --jar（--help 看用法）"
    fi
    if [[ -n "$JAR" && ! -f "$JAR" ]]; then
        die "jar 不存在: $JAR"
    fi

    decide_mode

    if [[ "$MODE" != "docker" && -z "$JAR" ]]; then
        die "已选定 $MODE 模式，它需要 --jar（docker 模式才不需要 jar）"
    fi
    if [[ "$MODE" != "docker" && -n "$JAR" && ! -f "$JAR" ]]; then
        die "jar 不存在: $JAR"
    fi

    plan_and_apply

    if [[ "$MODE" == "systemd" ]]; then
        install_systemd_unit
    elif [[ "$MODE" == "docker" ]]; then
        install_docker
    else
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
