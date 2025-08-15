#!/bin/bash

# JAiRouter监控栈一键部署脚本
# 该脚本用于快速部署完整的监控环境，包括Prometheus、Grafana、AlertManager等组件

set -e  # 遇到错误立即退出

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

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 命令未找到，请先安装 $1"
        exit 1
    fi
}

# 检查Docker和Docker Compose
check_prerequisites() {
    log_info "检查系统依赖..."
    
    check_command "docker"
    check_command "docker-compose"
    
    # 检查Docker是否运行
    if ! docker info &> /dev/null; then
        log_error "Docker服务未运行，请启动Docker服务"
        exit 1
    fi
    
    log_success "系统依赖检查通过"
}

# 创建必要的目录结构
create_directories() {
    log_info "创建监控目录结构..."
    
    # 创建数据目录
    mkdir -p monitoring/data/{prometheus,grafana,alertmanager}
    mkdir -p monitoring/prometheus/rules
    mkdir -p monitoring/grafana/{dashboards,provisioning/{datasources,dashboards,plugins}}
    mkdir -p monitoring/alertmanager/templates
    mkdir -p logs
    
    # 设置目录权限
    chmod 755 monitoring/data/{prometheus,grafana,alertmanager}
    
    # Grafana需要特定的用户权限
    sudo chown -R 472:472 monitoring/data/grafana 2>/dev/null || {
        log_warning "无法设置Grafana目录权限，可能需要手动调整"
    }
    
    log_success "目录结构创建完成"
}

# 验证配置文件
validate_configs() {
    log_info "验证配置文件..."
    
    # 检查必要的配置文件是否存在
    local required_files=(
        "docker-compose-monitoring.yml"
        "monitoring/prometheus/prometheus.yml"
        "monitoring/grafana/provisioning/datasources/prometheus.yml"
        "monitoring/grafana/provisioning/dashboards/jairouter-dashboards.yml"
        "monitoring/alertmanager/alertmanager.yml"
    )
    
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            log_error "配置文件 $file 不存在"
            exit 1
        fi
    done
    
    # 验证Docker Compose配置
    if ! docker-compose -f docker-compose-monitoring.yml config &> /dev/null; then
        log_error "Docker Compose配置文件验证失败"
        exit 1
    fi
    
    log_success "配置文件验证通过"
}

# 拉取Docker镜像
pull_images() {
    log_info "拉取Docker镜像..."
    
    docker-compose -f docker-compose-monitoring.yml pull
    
    log_success "Docker镜像拉取完成"
}

# 启动监控服务
start_services() {
    log_info "启动监控服务..."
    
    # 停止可能存在的旧服务
    docker-compose -f docker-compose-monitoring.yml down 2>/dev/null || true
    
    # 启动服务
    docker-compose -f docker-compose-monitoring.yml up -d
    
    log_success "监控服务启动完成"
}

# 等待服务就绪
wait_for_services() {
    log_info "等待服务启动..."
    
    local services=("prometheus:9090" "grafana:3000" "alertmanager:9093")
    local max_attempts=30
    local attempt=0
    
    for service in "${services[@]}"; do
        local name=${service%:*}
        local port=${service#*:}
        
        log_info "等待 $name 服务启动..."
        attempt=0
        
        while ! curl -s http://localhost:$port > /dev/null; do
            if [[ $attempt -ge $max_attempts ]]; then
                log_error "$name 服务启动超时"
                return 1
            fi
            
            sleep 2
            ((attempt++))
            echo -n "."
        done
        
        echo ""
        log_success "$name 服务已就绪"
    done
}

# 验证服务状态
verify_services() {
    log_info "验证服务状态..."
    
    # 检查容器状态
    local containers=("prometheus" "grafana" "alertmanager")
    
    for container in "${containers[@]}"; do
        if ! docker ps | grep -q $container; then
            log_error "$container 容器未运行"
            return 1
        fi
    done
    
    # 检查服务健康状态
    local health_checks=(
        "http://localhost:9090/-/healthy|Prometheus"
        "http://localhost:3000/api/health|Grafana"
        "http://localhost:9093/-/healthy|AlertManager"
    )
    
    for check in "${health_checks[@]}"; do
        local url=${check%|*}
        local name=${check#*|}
        
        if curl -s "$url" > /dev/null; then
            log_success "$name 健康检查通过"
        else
            log_warning "$name 健康检查失败，但服务可能仍在启动中"
        fi
    done
}

# 显示访问信息
show_access_info() {
    log_success "JAiRouter监控栈部署完成！"
    echo ""
    echo "=========================================="
    echo "服务访问信息："
    echo "=========================================="
    echo "🎯 Grafana仪表板:     http://localhost:3000"
    echo "   用户名: admin"
    echo "   密码: jairouter2024"
    echo ""
    echo "📊 Prometheus:        http://localhost:9090"
    echo "🚨 AlertManager:      http://localhost:9093"
    echo "📈 JAiRouter指标:     http://localhost:8080/actuator/prometheus"
    echo ""
    echo "🖥️  系统监控:"
    echo "   Node Exporter:     http://localhost:9100/metrics"
    echo "   cAdvisor:          http://localhost:8081"
    echo ""
    echo "=========================================="
    echo "常用命令："
    echo "=========================================="
    echo "查看服务状态:   docker-compose -f docker-compose-monitoring.yml ps"
    echo "查看服务日志:   docker-compose -f docker-compose-monitoring.yml logs -f [service]"
    echo "停止监控栈:     docker-compose -f docker-compose-monitoring.yml down"
    echo "重启监控栈:     docker-compose -f docker-compose-monitoring.yml restart"
    echo ""
    echo "🔧 配置文件位置："
    echo "   Prometheus:        monitoring/prometheus/prometheus.yml"
    echo "   Grafana:           monitoring/grafana/provisioning/"
    echo "   AlertManager:      monitoring/alertmanager/alertmanager.yml"
    echo "   告警规则:          monitoring/prometheus/rules/jairouter-alerts.yml"
    echo ""
    echo "📚 文档和工具："
    echo "   告警规则指南:      monitoring/prometheus/ALERT_RULES_GUIDE.md"
    echo "   测试告警规则:      ./monitoring/prometheus/test-alerts.sh"
    echo "   验证告警规则:      ./monitoring/prometheus/validate-alerts.sh"
    echo ""
}

# 清理函数
cleanup() {
    if [[ $? -ne 0 ]]; then
        log_error "部署过程中出现错误，正在清理..."
        docker-compose -f docker-compose-monitoring.yml down 2>/dev/null || true
    fi
}

# 主函数
main() {
    echo "=========================================="
    echo "🚀 JAiRouter监控栈部署脚本"
    echo "=========================================="
    echo ""
    
    # 设置错误处理
    trap cleanup EXIT
    
    # 执行部署步骤
    check_prerequisites
    create_directories
    validate_configs
    pull_images
    start_services
    wait_for_services
    verify_services
    
    # 验证告警规则
    log_info "验证告警规则配置..."
    if command -v promtool &> /dev/null; then
        ./monitoring/prometheus/test-alerts.sh
    else
        log_warning "promtool未安装，跳过告警规则验证"
    fi
    
    show_access_info
    
    # 取消错误处理陷阱
    trap - EXIT
    
    log_success "监控栈部署成功完成！"
}

# 处理命令行参数
case "${1:-}" in
    --help|-h)
        echo "JAiRouter监控栈部署脚本"
        echo ""
        echo "用法: $0 [选项]"
        echo ""
        echo "选项:"
        echo "  --help, -h     显示帮助信息"
        echo "  --clean        清理现有部署"
        echo "  --status       显示服务状态"
        echo ""
        exit 0
        ;;
    --clean)
        log_info "清理现有监控栈..."
        docker-compose -f docker-compose-monitoring.yml down -v
        docker system prune -f
        log_success "清理完成"
        exit 0
        ;;
    --status)
        log_info "检查监控栈状态..."
        docker-compose -f docker-compose-monitoring.yml ps
        exit 0
        ;;
    "")
        main
        ;;
    *)
        log_error "未知选项: $1"
        echo "使用 --help 查看帮助信息"
        exit 1
        ;;
esac