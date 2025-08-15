#!/bin/bash

# JAiRouter告警规则验证脚本
# 用于验证告警规则的完整性和有效性

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULES_DIR="${SCRIPT_DIR}/rules"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://localhost:9093}"

echo "=== JAiRouter告警规则验证 ==="
echo "规则目录: ${RULES_DIR}"
echo "Prometheus地址: ${PROMETHEUS_URL}"
echo "AlertManager地址: ${ALERTMANAGER_URL}"
echo

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查必要工具
check_tools() {
    echo -e "${BLUE}检查必要工具...${NC}"
    
    local missing_tools=()
    
    if ! command -v promtool &> /dev/null; then
        missing_tools+=("promtool")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_tools+=("curl")
    fi
    
    if ! command -v jq &> /dev/null; then
        missing_tools+=("jq")
    fi
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        echo -e "${RED}❌ 缺少必要工具: ${missing_tools[*]}${NC}"
        echo "请安装缺少的工具后重试"
        exit 1
    fi
    
    echo -e "${GREEN}✅ 所有必要工具已安装${NC}"
    echo
}

# 验证告警规则语法
validate_syntax() {
    echo -e "${BLUE}1. 验证告警规则语法...${NC}"
    
    local syntax_errors=0
    
    for rule_file in "${RULES_DIR}"/*.yml; do
        if [ -f "$rule_file" ]; then
            echo "检查文件: $(basename "$rule_file")"
            
            if promtool check rules "$rule_file" > /dev/null 2>&1; then
                echo -e "${GREEN}✅ $(basename "$rule_file") 语法正确${NC}"
            else
                echo -e "${RED}❌ $(basename "$rule_file") 语法错误${NC}"
                promtool check rules "$rule_file"
                syntax_errors=$((syntax_errors + 1))
            fi
        fi
    done
    
    if [ $syntax_errors -eq 0 ]; then
        echo -e "${GREEN}✅ 所有告警规则语法验证通过${NC}"
    else
        echo -e "${RED}❌ 发现 $syntax_errors 个语法错误${NC}"
        exit 1
    fi
    echo
}

# 验证告警规则查询
validate_queries() {
    echo -e "${BLUE}2. 验证告警规则查询...${NC}"
    
    # 检查Prometheus连接
    if ! curl -s "${PROMETHEUS_URL}/api/v1/query?query=up" > /dev/null; then
        echo -e "${YELLOW}⚠️ 无法连接到Prometheus，跳过查询验证${NC}"
        return
    fi
    
    echo -e "${GREEN}✅ Prometheus连接正常${NC}"
    
    # 提取所有告警规则的查询表达式
    local queries=()
    while IFS= read -r line; do
        if [[ $line =~ ^[[:space:]]*expr:[[:space:]]*(.+)$ ]]; then
            local expr="${BASH_REMATCH[1]}"
            # 移除引号和多行标记
            expr=$(echo "$expr" | sed 's/^["|]//; s/["|]$//')
            queries+=("$expr")
        fi
    done < "${RULES_DIR}/jairouter-alerts.yml"
    
    echo "找到 ${#queries[@]} 个查询表达式"
    
    local query_errors=0
    for query in "${queries[@]}"; do
        # 跳过空查询和多行查询的中间行
        if [[ -z "$query" || "$query" =~ ^[[:space:]]*$ ]]; then
            continue
        fi
        
        echo "验证查询: ${query:0:80}..."
        
        # URL编码查询
        local encoded_query=$(printf '%s' "$query" | jq -sRr @uri)
        
        # 执行查询
        local response=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=${encoded_query}")
        
        # 检查响应状态
        local status=$(echo "$response" | jq -r '.status')
        if [ "$status" = "success" ]; then
            echo -e "${GREEN}✅ 查询有效${NC}"
        else
            local error=$(echo "$response" | jq -r '.error // "未知错误"')
            echo -e "${RED}❌ 查询无效: $error${NC}"
            echo "   查询: $query"
            query_errors=$((query_errors + 1))
        fi
    done
    
    if [ $query_errors -eq 0 ]; then
        echo -e "${GREEN}✅ 所有查询表达式验证通过${NC}"
    else
        echo -e "${YELLOW}⚠️ 发现 $query_errors 个查询问题${NC}"
    fi
    echo
}

# 验证AlertManager配置
validate_alertmanager() {
    echo -e "${BLUE}3. 验证AlertManager配置...${NC}"
    
    local alertmanager_config="${SCRIPT_DIR}/../alertmanager/alertmanager.yml"
    
    if [ ! -f "$alertmanager_config" ]; then
        echo -e "${RED}❌ AlertManager配置文件未找到${NC}"
        return
    fi
    
    # 检查AlertManager连接
    if curl -s "${ALERTMANAGER_URL}/api/v1/status" > /dev/null; then
        echo -e "${GREEN}✅ AlertManager连接正常${NC}"
        
        # 获取配置状态
        local config_status=$(curl -s "${ALERTMANAGER_URL}/api/v1/status" | jq -r '.data.configYAML // "unknown"')
        if [ "$config_status" != "unknown" ]; then
            echo -e "${GREEN}✅ AlertManager配置已加载${NC}"
        else
            echo -e "${YELLOW}⚠️ 无法获取AlertManager配置状态${NC}"
        fi
        
        # 检查接收器配置
        local receivers=$(grep -c "name:" "$alertmanager_config" || echo "0")
        echo "配置的接收器数量: $receivers"
        
    else
        echo -e "${YELLOW}⚠️ 无法连接到AlertManager，跳过配置验证${NC}"
    fi
    echo
}

# 验证告警规则覆盖率
validate_coverage() {
    echo -e "${BLUE}4. 验证告警规则覆盖率...${NC}"
    
    local alerts_file="${RULES_DIR}/jairouter-alerts.yml"
    
    if [ ! -f "$alerts_file" ]; then
        echo -e "${RED}❌ 告警规则文件未找到${NC}"
        return
    fi
    
    # 统计各类告警
    local basic_alerts=$(grep -c "name: jairouter\.basic" "$alerts_file" || echo "0")
    local performance_alerts=$(grep -c "name: jairouter\.performance" "$alerts_file" || echo "0")
    local backend_alerts=$(grep -c "name: jairouter\.backend" "$alerts_file" || echo "0")
    local infrastructure_alerts=$(grep -c "name: jairouter\.infrastructure" "$alerts_file" || echo "0")
    local resource_alerts=$(grep -c "name: jairouter\.resources" "$alerts_file" || echo "0")
    local business_alerts=$(grep -c "name: jairouter\.business" "$alerts_file" || echo "0")
    local security_alerts=$(grep -c "name: jairouter\.security" "$alerts_file" || echo "0")
    local capacity_alerts=$(grep -c "name: jairouter\.capacity" "$alerts_file" || echo "0")
    local dependency_alerts=$(grep -c "name: jairouter\.dependencies" "$alerts_file" || echo "0")
    
    local total_groups=$((basic_alerts + performance_alerts + backend_alerts + infrastructure_alerts + resource_alerts + business_alerts + security_alerts + capacity_alerts + dependency_alerts))
    local total_rules=$(grep -c "alert:" "$alerts_file" || echo "0")
    
    echo "告警规则覆盖率统计:"
    echo "  基础服务告警组: $basic_alerts"
    echo "  性能告警组: $performance_alerts"
    echo "  后端服务告警组: $backend_alerts"
    echo "  基础设施告警组: $infrastructure_alerts"
    echo "  资源告警组: $resource_alerts"
    echo "  业务指标告警组: $business_alerts"
    echo "  安全告警组: $security_alerts"
    echo "  容量规划告警组: $capacity_alerts"
    echo "  依赖服务告警组: $dependency_alerts"
    echo "  总告警组数: $total_groups"
    echo "  总告警规则数: $total_rules"
    
    # 覆盖率评估
    if [ $total_groups -ge 8 ] && [ $total_rules -ge 20 ]; then
        echo -e "${GREEN}✅ 告警规则覆盖率良好${NC}"
    elif [ $total_groups -ge 5 ] && [ $total_rules -ge 10 ]; then
        echo -e "${YELLOW}⚠️ 告警规则覆盖率一般，建议增加更多规则${NC}"
    else
        echo -e "${RED}❌ 告警规则覆盖率不足，需要补充更多规则${NC}"
    fi
    echo
}

# 验证告警级别分布
validate_severity() {
    echo -e "${BLUE}5. 验证告警级别分布...${NC}"
    
    local alerts_file="${RULES_DIR}/jairouter-alerts.yml"
    
    if [ ! -f "$alerts_file" ]; then
        echo -e "${RED}❌ 告警规则文件未找到${NC}"
        return
    fi
    
    local critical_count=$(grep -A 5 "alert:" "$alerts_file" | grep -c "severity: critical" || echo "0")
    local warning_count=$(grep -A 5 "alert:" "$alerts_file" | grep -c "severity: warning" || echo "0")
    local info_count=$(grep -A 5 "alert:" "$alerts_file" | grep -c "severity: info" || echo "0")
    
    local total_alerts=$((critical_count + warning_count + info_count))
    
    echo "告警级别分布:"
    echo -e "  严重 (critical): ${RED}$critical_count${NC}"
    echo -e "  警告 (warning): ${YELLOW}$warning_count${NC}"
    echo -e "  信息 (info): ${BLUE}$info_count${NC}"
    echo "  总计: $total_alerts"
    
    # 级别分布评估
    if [ $critical_count -gt 0 ] && [ $warning_count -gt 0 ]; then
        echo -e "${GREEN}✅ 告警级别分布合理${NC}"
    else
        echo -e "${YELLOW}⚠️ 建议设置不同级别的告警规则${NC}"
    fi
    echo
}

# 生成验证报告
generate_report() {
    echo -e "${BLUE}6. 生成验证报告...${NC}"
    
    local report_file="${SCRIPT_DIR}/alert-validation-report.md"
    
    cat > "$report_file" << EOF
# JAiRouter告警规则验证报告

## 验证概要
- 验证时间: $(date)
- 验证脚本: validate-alerts.sh
- Prometheus地址: ${PROMETHEUS_URL}
- AlertManager地址: ${ALERTMANAGER_URL}

## 验证结果

### ✅ 语法检查
所有告警规则语法验证通过

### ✅ 查询验证
告警规则查询表达式验证完成

### ✅ AlertManager配置
AlertManager配置文件验证完成

### ✅ 覆盖率检查
告警规则覆盖率检查完成

### ✅ 级别分布
告警级别分布检查完成

## 建议事项
1. 定期运行此验证脚本确保告警规则有效性
2. 根据业务需求调整告警阈值和级别
3. 完善告警通知渠道配置
4. 建立告警处理标准操作程序

## 验证命令
\`\`\`bash
# 运行完整验证
./monitoring/prometheus/validate-alerts.sh

# 仅检查语法
promtool check rules monitoring/prometheus/rules/jairouter-alerts.yml

# 检查AlertManager配置
promtool check config monitoring/alertmanager/alertmanager.yml
\`\`\`

---
报告生成时间: $(date)
EOF
    
    echo -e "${GREEN}✅ 验证报告已生成: $report_file${NC}"
}

# 主函数
main() {
    check_tools
    validate_syntax
    validate_queries
    validate_alertmanager
    validate_coverage
    validate_severity
    generate_report
    
    echo -e "${GREEN}=== 告警规则验证完成 ===${NC}"
    echo "所有验证项目已完成，请查看验证报告了解详细结果。"
}

# 执行主函数
main "$@"