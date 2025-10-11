#!/bin/bash

# ========================================
# 追踪数据生成测试脚本
# ========================================
# 此脚本用于生成追踪数据，测试追踪概览页面的数据显示功能
# 
# 使用方法:
#   ./generate-tracing-data.sh [BASE_URL] [COUNT]
#
# 参数:
#   BASE_URL: 服务器地址，默认为 http://localhost:8080
#   COUNT: 生成请求数量，默认为 50
#
# 示例:
#   ./generate-tracing-data.sh
#   ./generate-tracing-data.sh http://localhost:8080 100
#   ./generate-tracing-data.sh https://your-server.com 200

set -e

# 配置参数
BASE_URL=${1:-"http://localhost:8080"}
REQUEST_COUNT=${2:-50}
CONCURRENT_REQUESTS=5

# 颜色输出
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

# 检查依赖
check_dependencies() {
    log_info "检查依赖工具..."
    
    if ! command -v curl &> /dev/null; then
        log_error "curl 未安装，请先安装 curl"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_warning "jq 未安装，JSON 输出将不会格式化"
    fi
    
    log_success "依赖检查完成"
}

# 检查服务器连接
check_server() {
    log_info "检查服务器连接: $BASE_URL"
    
    if curl -s --connect-timeout 5 "$BASE_URL/actuator/health" > /dev/null; then
        log_success "服务器连接正常"
    else
        log_error "无法连接到服务器: $BASE_URL"
        log_info "请确保服务器正在运行，或检查URL是否正确"
        exit 1
    fi
}

# 生成随机聊天请求
generate_chat_request() {
    local model_names=("gpt-3.5-turbo" "gpt-4" "claude-3-sonnet" "gemini-pro")
    local user_messages=(
        "Hello, how are you today?"
        "What is the weather like?"
        "Can you help me with programming?"
        "Tell me a joke"
        "Explain quantum computing"
        "What is machine learning?"
        "How to cook pasta?"
        "Recommend a good book"
        "What is the capital of France?"
        "How to learn a new language?"
    )
    
    local model=${model_names[$RANDOM % ${#model_names[@]}]}
    local message=${user_messages[$RANDOM % ${#user_messages[@]}]}
    
    cat << EOF
{
  "model": "$model",
  "messages": [
    {
      "role": "user",
      "content": "$message"
    }
  ],
  "max_tokens": 100,
  "temperature": 0.7
}
EOF
}

# 生成随机嵌入请求
generate_embedding_request() {
    local model_names=("text-embedding-ada-002" "text-embedding-3-small" "text-embedding-3-large")
    local texts=(
        "This is a sample text for embedding"
        "Machine learning is fascinating"
        "Natural language processing"
        "Artificial intelligence applications"
        "Deep learning algorithms"
    )
    
    local model=${model_names[$RANDOM % ${#model_names[@]}]}
    local text=${texts[$RANDOM % ${#texts[@]}]}
    
    cat << EOF
{
  "model": "$model",
  "input": "$text"
}
EOF
}

# 发送单个请求
send_request() {
    local endpoint=$1
    local data=$2
    local request_id=$3
    
    local start_time=$(date +%s%3N)
    
    # 随机决定是否模拟错误（10%概率）
    local simulate_error=$((RANDOM % 10))
    local extra_headers=""
    
    if [ $simulate_error -eq 0 ]; then
        extra_headers="-H 'X-Simulate-Error: true'"
    fi
    
    # 发送请求
    local response
    if [ "$endpoint" = "/v1/models" ]; then
        # GET请求
        response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
            -X GET \
            -H "X-API-Key: dev-admin-12345-abcde-67890-fghij" \
            -H "X-Request-ID: req-$request_id-$(date +%s)" \
            $extra_headers \
            "$BASE_URL$endpoint" 2>/dev/null || echo -e "\n000\n0")
    else
        # POST请求
        response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
            -X POST \
            -H "Content-Type: application/json" \
            -H "X-API-Key: dev-admin-12345-abcde-67890-fghij" \
            -H "X-Request-ID: req-$request_id-$(date +%s)" \
            $extra_headers \
            -d "$data" \
            "$BASE_URL$endpoint" 2>/dev/null || echo -e "\n000\n0")
    fi
    
    local end_time=$(date +%s%3N)
    local duration=$((end_time - start_time))
    
    # 解析响应
    local http_code=$(echo "$response" | tail -n2 | head -n1)
    local time_total=$(echo "$response" | tail -n1)
    
    # 输出结果
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "✓ Request $request_id: $endpoint - ${http_code} (${duration}ms)"
    elif [ "$http_code" -ge 400 ] && [ "$http_code" -lt 500 ]; then
        echo "⚠ Request $request_id: $endpoint - ${http_code} (${duration}ms) [Client Error]"
    elif [ "$http_code" -ge 500 ]; then
        echo "✗ Request $request_id: $endpoint - ${http_code} (${duration}ms) [Server Error]"
    else
        echo "? Request $request_id: $endpoint - Connection failed"
    fi
}

# 生成追踪数据
generate_tracing_data() {
    log_info "开始生成追踪数据..."
    log_info "目标服务器: $BASE_URL"
    log_info "请求数量: $REQUEST_COUNT"
    log_info "并发数: $CONCURRENT_REQUESTS"
    
    local success_count=0
    local error_count=0
    
    # 创建临时文件存储后台任务PID
    local pids_file=$(mktemp)
    
    for ((i=1; i<=REQUEST_COUNT; i++)); do
        # 随机选择API端点
        local endpoint_type=$((RANDOM % 4))
        local endpoint=""
        local data=""
        
        case $endpoint_type in
            0)
                endpoint="/v1/chat/completions"
                data=$(generate_chat_request)
                ;;
            1)
                endpoint="/v1/embeddings"
                data=$(generate_embedding_request)
                ;;
            2)
                endpoint="/v1/models"
                data="{}"
                ;;
            3)
                endpoint="/v1/chat/completions"
                data=$(generate_chat_request)
                ;;
        esac
        
        # 发送请求（后台执行）
        (send_request "$endpoint" "$data" "$i") &
        echo $! >> "$pids_file"
        
        # 控制并发数
        if [ $((i % CONCURRENT_REQUESTS)) -eq 0 ]; then
            # 等待当前批次完成
            while read -r pid; do
                wait "$pid" 2>/dev/null || true
            done < "$pids_file"
            > "$pids_file"  # 清空文件
            
            # 显示进度
            log_info "已发送 $i/$REQUEST_COUNT 个请求..."
            
            # 随机延迟，模拟真实流量
            sleep $(echo "scale=2; $RANDOM/32767*2" | bc -l 2>/dev/null || echo "0.5")
        fi
    done
    
    # 等待剩余请求完成
    while read -r pid; do
        wait "$pid" 2>/dev/null || true
    done < "$pids_file"
    
    # 清理临时文件
    rm -f "$pids_file"
    
    log_success "追踪数据生成完成！"
}

# 验证追踪数据
verify_tracing_data() {
    log_info "验证追踪数据..."
    
    # 等待数据处理
    sleep 5
    
    # 检查追踪统计
    local stats_response=$(curl -s "$BASE_URL/api/tracing/query/statistics" 2>/dev/null || echo "{}")
    
    if command -v jq &> /dev/null; then
        local total_traces=$(echo "$stats_response" | jq -r '.totalTraces // 0' 2>/dev/null || echo "0")
        local error_traces=$(echo "$stats_response" | jq -r '.errorTraces // 0' 2>/dev/null || echo "0")
        local avg_duration=$(echo "$stats_response" | jq -r '.avgDuration // 0' 2>/dev/null || echo "0")
        
        log_info "追踪统计结果:"
        log_info "  总追踪数: $total_traces"
        log_info "  错误追踪数: $error_traces"
        log_info "  平均耗时: ${avg_duration}ms"
        
        if [ "$total_traces" -gt 0 ]; then
            log_success "追踪数据验证成功！"
        else
            log_warning "未检测到追踪数据，可能需要等待数据处理完成"
        fi
    else
        log_info "追踪统计响应: $stats_response"
    fi
    
    # 检查服务统计
    local services_response=$(curl -s "$BASE_URL/api/tracing/query/services" 2>/dev/null || echo "[]")
    
    if command -v jq &> /dev/null; then
        local service_count=$(echo "$services_response" | jq '. | length' 2>/dev/null || echo "0")
        log_info "服务统计: 发现 $service_count 个服务"
        
        if [ "$service_count" -gt 0 ]; then
            log_info "服务列表:"
            echo "$services_response" | jq -r '.[] | "  - \(.name): \(.traces) traces, \(.errors) errors, \(.avgDuration)ms avg"' 2>/dev/null || true
        fi
    fi
}

# 显示使用说明
show_usage() {
    cat << EOF
追踪数据生成测试脚本

使用方法:
  $0 [BASE_URL] [COUNT]

参数:
  BASE_URL    服务器地址 (默认: http://localhost:8080)
  COUNT       生成请求数量 (默认: 50)

示例:
  $0                                    # 使用默认参数
  $0 http://localhost:8080 100         # 生成100个请求
  $0 https://your-server.com 200       # 向远程服务器发送200个请求

功能:
  - 生成多种类型的API请求 (聊天、嵌入、模型列表等)
  - 模拟真实的用户行为模式
  - 随机生成错误请求 (约10%概率)
  - 控制并发数避免服务器过载
  - 验证生成的追踪数据

注意:
  - 确保目标服务器正在运行
  - 需要安装 curl 工具
  - 建议安装 jq 工具以获得更好的输出格式
EOF
}

# 主函数
main() {
    # 显示标题
    echo "========================================"
    echo "        追踪数据生成测试脚本"
    echo "========================================"
    echo
    
    # 处理帮助参数
    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        show_usage
        exit 0
    fi
    
    # 检查依赖
    check_dependencies
    
    # 检查服务器
    check_server
    
    # 生成追踪数据
    generate_tracing_data
    
    # 验证数据
    verify_tracing_data
    
    echo
    log_success "测试完成！"
    log_info "现在可以访问追踪概览页面查看生成的数据:"
    log_info "  $BASE_URL (前端页面)"
    log_info "  $BASE_URL/api/tracing/query/statistics (统计API)"
    log_info "  $BASE_URL/api/tracing/query/services (服务API)"
}

# 执行主函数
main "$@"