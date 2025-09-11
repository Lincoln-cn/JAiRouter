#!/bin/bash

# JAiRouter Prometheus告警规则测试脚本
# 用于验证告警规则的语法和逻辑正确性

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULES_DIR="${SCRIPT_DIR}/rules"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

echo "=== JAiRouter告警规则测试 ==="
echo "规则目录: ${RULES_DIR}"
echo "Prometheus地址: ${PROMETHEUS_URL}"
echo

# 检查promtool是否可用
if ! command -v promtool &> /dev/null; then
    echo "错误: promtool未找到，请安装Prometheus工具包"
    echo "下载地址: https://prometheus.io/download/"
    exit 1
fi

# 1. 语法检查
echo "1. 执行告警规则语法检查..."
for rule_file in "${RULES_DIR}"/*.yml; do
    if [ -f "$rule_file" ]; then
        echo "检查文件: $(basename "$rule_file")"
        if promtool check rules "$rule_file"; then
            echo "✅ $(basename "$rule_file") 语法检查通过"
        else
            echo "❌ $(basename "$rule_file") 语法检查失败"
            exit 1
        fi
    fi
done
echo

# 2. 规则查询验证
echo "2. 验证告警规则查询表达式..."

# 定义测试查询列表
declare -a test_queries=(
    # 基础服务告警查询
    "up{job=\"jairouter\"}"
    "sum(rate(jairouter_requests_total{status=~\"4..|5..\"}[5m])) by (instance) / sum(rate(jairouter_requests_total[5m])) by (instance)"
    
    # 性能告警查询
    "histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le, service))"
    "sum(rate(jairouter_requests_total[5m])) by (instance)"
    
    # 后端服务告警查询
    "jairouter_backend_health"
    "histogram_quantile(0.95, sum(rate(jairouter_backend_call_duration_seconds_bucket[5m])) by (le, adapter))"
    
    # 基础设施告警查询
    "jairouter_circuit_breaker_state"
    "sum(rate(jairouter_rate_limit_events_total{result=\"rejected\"}[5m])) by (service)"
    
    # 资源告警查询
    "jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"}"
    "rate(jvm_gc_collection_seconds_count[5m])"
    "jvm_threads_current"
)

# 检查Prometheus连接
if ! curl -s "${PROMETHEUS_URL}/api/v1/query?query=up" > /dev/null; then
    echo "警告: 无法连接到Prometheus (${PROMETHEUS_URL})，跳过查询验证"
    echo "请确保Prometheus服务正在运行"
else
    echo "✅ Prometheus连接正常"
    
    # 验证每个查询
    for query in "${test_queries[@]}"; do
        echo "验证查询: ${query}"
        
        # URL编码查询
        encoded_query=$(printf '%s' "$query" | jq -sRr @uri)
        
        # 执行查询
        response=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=${encoded_query}")
        
        # 检查响应状态
        status=$(echo "$response" | jq -r '.status')
        if [ "$status" = "success" ]; then
            echo "✅ 查询执行成功"
        else
            error=$(echo "$response" | jq -r '.error // "未知错误"')
            echo "❌ 查询执行失败: $error"
            echo "   查询: $query"
        fi
    done
fi
echo

# 3. 告警规则覆盖率检查
echo "3. 检查告警规则覆盖率..."

# 统计各类告警数量
basic_alerts=$(grep -c "name: jairouter.basic" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
performance_alerts=$(grep -c "name: jairouter.performance" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
backend_alerts=$(grep -c "name: jairouter.backend" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
infrastructure_alerts=$(grep -c "name: jairouter.infrastructure" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
resource_alerts=$(grep -c "name: jairouter.resources" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
business_alerts=$(grep -c "name: jairouter.business" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
security_alerts=$(grep -c "name: jairouter.security" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
capacity_alerts=$(grep -c "name: jairouter.capacity" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")
dependency_alerts=$(grep -c "name: jairouter.dependencies" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")

total_alert_rules=$(grep -c "alert:" "${RULES_DIR}/jairouter-alerts.yml" || echo "0")

echo "告警规则分类统计:"
echo "  基础服务告警组: ${basic_alerts}"
echo "  性能告警组: ${performance_alerts}"
echo "  后端服务告警组: ${backend_alerts}"
echo "  基础设施告警组: ${infrastructure_alerts}"
echo "  资源告警组: ${resource_alerts}"
echo "  业务指标告警组: ${business_alerts}"
echo "  安全告警组: ${security_alerts}"
echo "  容量规划告警组: ${capacity_alerts}"
echo "  依赖服务告警组: ${dependency_alerts}"
echo "  总告警规则数: ${total_alert_rules}"
echo

# 4. 告警级别分布检查
echo "4. 检查告警级别分布..."

critical_count=$(grep -A 5 "alert:" "${RULES_DIR}/jairouter-alerts.yml" | grep -c "severity: critical" || echo "0")
warning_count=$(grep -A 5 "alert:" "${RULES_DIR}/jairouter-alerts.yml" | grep -c "severity: warning" || echo "0")
info_count=$(grep -A 5 "alert:" "${RULES_DIR}/jairouter-alerts.yml" | grep -c "severity: info" || echo "0")

echo "告警级别分布:"
echo "  严重 (critical): ${critical_count}"
echo "  警告 (warning): ${warning_count}"
echo "  信息 (info): ${info_count}"
echo

# 5. 生成测试报告
echo "5. 生成测试报告..."

cat > "${SCRIPT_DIR}/alert-test-report.md" << EOF
# JAiRouter告警规则测试报告

## 测试概要
- 测试时间: $(date)
- 规则文件: jairouter-alerts.yml
- Prometheus地址: ${PROMETHEUS_URL}

## 语法检查结果
✅ 所有告警规则语法检查通过

## 规则覆盖率统计
| 告警类别 | 规则组数 | 说明 |
|---------|---------|------|
| 基础服务告警 | ${basic_alerts} | 服务可用性、错误率等基础指标 |
| 性能告警 | ${performance_alerts} | 响应时间、请求量等性能指标 |
| 后端服务告警 | ${backend_alerts} | 后端适配器健康状态和性能 |
| 基础设施告警 | ${infrastructure_alerts} | 熔断器、限流器、负载均衡器 |
| 资源告警 | ${resource_alerts} | JVM内存、GC、线程等资源使用 |
| 业务指标告警 | ${business_alerts} | 模型调用、请求大小等业务指标 |
| 安全告警 | ${security_alerts} | 异常访问、认证失败等安全事件 |
| 容量规划告警 | ${capacity_alerts} | 容量增长趋势、资源预警 |
| 依赖服务告警 | ${dependency_alerts} | 数据库、缓存、外部API等依赖 |

**总计**: ${total_alert_rules} 个告警规则

## 告警级别分布
- 严重告警 (Critical): ${critical_count} 个
- 警告告警 (Warning): ${warning_count} 个  
- 信息告警 (Info): ${info_count} 个

## 建议
1. 定期检查告警规则的有效性和准确性
2. 根据实际业务场景调整告警阈值
3. 完善告警通知渠道和处理流程
4. 建立告警处理的标准操作程序(SOP)

## 测试命令
\`\`\`bash
# 语法检查
promtool check rules monitoring/prometheus/rules/jairouter-alerts.yml

# 查询验证
curl "${PROMETHEUS_URL}/api/v1/query?query=up{job=\"jairouter\"}"
\`\`\`
EOF

echo "✅ 测试报告已生成: ${SCRIPT_DIR}/alert-test-report.md"
echo

echo "=== 告警规则测试完成 ==="
echo "所有检查项目已完成，请查看测试报告了解详细结果。"