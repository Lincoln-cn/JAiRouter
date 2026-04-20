#!/bin/bash

# JAiRouter 监控栈启动脚本 v1.9.5
# 用于快速启动完整的监控体系（Prometheus + Grafana + AlertManager + Loki + Tempo）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Docker 是否安装
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    
    log_success "Docker 环境检查通过"
}

# 检查网络是否存在
check_network() {
    local network_name="jairouter-network"
    
    if ! docker network ls | grep -q "$network_name"; then
        log_info "创建 Docker 网络：$network_name"
        docker network create "$network_name"
    else
        log_info "Docker 网络已存在：$network_name"
    fi
}

# 创建必要的目录
create_directories() {
    log_info "创建监控配置目录..."
    mkdir -p monitoring/loki
    mkdir -p monitoring/tempo
    mkdir -p monitoring/promtail
    mkdir -p monitoring/blackbox
    mkdir -p monitoring/alertmanager
    mkdir -p monitoring/grafana/provisioning/datasources
    mkdir -p monitoring/grafana/provisioning/dashboards
    mkdir -p monitoring/grafana/dashboards
    mkdir -p monitoring/prometheus/rules
    mkdir -p data/prometheus
    mkdir -p data/grafana
    mkdir -p data/loki
    mkdir -p data/tempo
    mkdir -p data/redis
    
    log_success "目录创建完成"
}

# 生成环境变量文件
generate_env() {
    if [ ! -f .env ]; then
        log_info "生成 .env 环境变量文件..."
        cat > .env << EOF
# JAiRouter 监控栈环境变量配置

# Grafana 配置
GRAFANA_USER=admin
GRAFANA_PASSWORD=admin

# Redis 配置
REDIS_PASSWORD=jairouter_redis_password_2026

# MySQL Exporter 配置（可选）
MYSQL_EXPORTER_USER=exporter
MYSQL_EXPORTER_PASSWORD=exporter

# JAiRouter 配置
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
EOF
        log_success ".env 文件生成完成"
    else
        log_info ".env 文件已存在，跳过生成"
    fi
}

# 启动监控栈
start_monitoring() {
    log_info "启动 JAiRouter 监控栈..."

    # 检测 docker compose 命令
    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi

    # 使用项目根目录的 docker-compose 文件
    $COMPOSE_CMD -f "$SCRIPT_DIR/../docker-compose.monitoring.yml" up -d

    log_success "监控栈启动完成"
}

# 显示访问信息
show_access_info() {
    echo ""
    log_success "=========================================="
    log_success "   JAiRouter 监控栈已启动"
    log_success "=========================================="
    echo ""
    echo "访问地址:"
    echo "  ${BLUE}Prometheus:${NC}     http://localhost:9090"
    echo "  ${BLUE}Grafana:${NC}        http://localhost:3000 (admin/admin)"
    echo "  ${BLUE}AlertManager:${NC}   http://localhost:9093"
    echo "  ${BLUE}Loki:${NC}           http://localhost:3100"
    echo "  ${BLUE}Tempo:${NC}          http://localhost:3200"
    echo "  ${BLUE}Node Exporter:${NC}  http://localhost:9100"
    echo "  ${BLUE}cAdvisor:${NC}       http://localhost:8080"
    echo "  ${BLUE}Redis Commander:${NC} http://localhost:8081"
    echo ""
    echo "使用说明:"
    echo "  1. 访问 Grafana 配置 Prometheus 数据源"
    echo "  2. 导入异常管理仪表盘 (monitoring/grafana/dashboards/exception-management.json)"
    echo "  3. 在 Prometheus 中验证指标：jairouter_errors_*"
    echo ""
    echo "停止监控栈:"
    echo "  $0 stop"
    echo ""
    echo "查看日志:"
    echo "  $0 logs [service_name]"
    echo ""
}

# 停止监控栈
stop_monitoring() {
    log_info "停止 JAiRouter 监控栈..."

    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi

    $COMPOSE_CMD -f "$SCRIPT_DIR/../docker-compose.monitoring.yml" down
    log_success "监控栈已停止"
}

# 查看日志
view_logs() {
    local service=$1

    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi

    if [ -z "$service" ]; then
        log_info "查看所有服务日志..."
        $COMPOSE_CMD -f "$SCRIPT_DIR/../docker-compose.monitoring.yml" logs -f
    else
        log_info "查看 $service 服务日志..."
        $COMPOSE_CMD -f "$SCRIPT_DIR/../docker-compose.monitoring.yml" logs -f "$service"
    fi
}

# 清理数据
clean_data() {
    log_warning "警告：此操作将删除所有监控数据！"
    read -p "确定要继续吗？(y/N): " confirm
    
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        log_info "清理数据..."
        rm -rf data/prometheus/*
        rm -rf data/grafana/*
        rm -rf data/loki/*
        rm -rf data/tempo/*
        rm -rf data/redis/*
        log_success "数据清理完成"
    else
        log_info "操作已取消"
    fi
}

# 显示帮助
show_help() {
    echo "JAiRouter 监控栈管理脚本 v1.9.5"
    echo ""
    echo "用法：$0 [command]"
    echo ""
    echo "命令:"
    echo "  start       启动监控栈"
    echo "  stop        停止监控栈"
    echo "  restart     重启监控栈"
    echo "  logs        查看日志 (可指定服务名)"
    echo "  clean       清理所有数据"
    echo "  status      查看服务状态"
    echo "  help        显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 start                    # 启动监控栈"
    echo "  $0 logs prometheus          # 查看 Prometheus 日志"
    echo "  $0 stop                     # 停止监控栈"
    echo ""
}

# 查看状态
check_status() {
    log_info "服务状态:"

    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    else
        COMPOSE_CMD="docker-compose"
    fi

    $COMPOSE_CMD -f "$SCRIPT_DIR/../docker-compose.monitoring.yml" ps
}

# 主函数
main() {
    case "${1:-start}" in
        start)
            check_docker
            check_network
            create_directories
            generate_env
            start_monitoring
            show_access_info
            ;;
        stop)
            stop_monitoring
            ;;
        restart)
            stop_monitoring
            sleep 2
            start_monitoring
            ;;
        logs)
            view_logs "$2"
            ;;
        clean)
            clean_data
            ;;
        status)
            check_status
            ;;
        help|-h|--help)
            show_help
            ;;
        *)
            log_error "未知命令：$1"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
