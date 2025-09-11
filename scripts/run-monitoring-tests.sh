#!/bin/bash

# 监控系统集成测试和性能测试运行脚本
# 支持Linux和macOS系统

set -e  # 遇到错误立即退出

# 默认参数
TEST_TYPE="all"
SKIP_BUILD=false
VERBOSE=false
PROFILE="integration-test"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --test-type)
            TEST_TYPE="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo "选项:"
            echo "  --test-type TYPE    测试类型 (all|integration|performance|concurrency|prometheus)"
            echo "  --skip-build        跳过项目构建"
            echo "  --verbose           详细输出"
            echo "  --profile PROFILE   Spring配置文件 (默认: integration-test)"
            echo "  -h, --help          显示帮助信息"
            exit 0
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "=== JAiRouter 监控系统测试运行器 ==="
echo "测试类型: $TEST_TYPE"
echo "跳过构建: $SKIP_BUILD"
echo "详细输出: $VERBOSE"
echo "配置文件: $PROFILE"

# 获取脚本目录和项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 切换到项目根目录
cd "$PROJECT_ROOT"

# 构建项目（除非跳过）
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo "=== 构建项目 ==="
    ./mvnw clean compile test-compile -q
    echo "项目构建成功"
fi

# 设置测试参数
TEST_ARGS=(
    "compiler:compile"
    "compiler:testCompile"
    "surefire:test"
    "-Dspring.profiles.active=$PROFILE"
    "-Dtest.monitoring.enabled=true"
)

if [ "$VERBOSE" = true ]; then
    TEST_ARGS+=("-X")
else
    TEST_ARGS+=("-q")
fi

# 根据测试类型选择要运行的测试
case "${TEST_TYPE,,}" in
    "integration")
        echo ""
        echo "=== 运行集成测试 ==="
        TEST_ARGS+=("-Dtest=PrometheusEndpointIntegrationTest,MonitoringDataFlowEndToEndTest")
        ;;
    "performance")
        echo ""
        echo "=== 运行性能测试 ==="
        TEST_ARGS+=("-Dtest=MonitoringPerformanceBenchmarkTest")
        ;;
    "concurrency")
        echo ""
        echo "=== 运行并发稳定性测试 ==="
        TEST_ARGS+=("-Dtest=MonitoringConcurrencyStabilityTest")
        ;;
    "prometheus")
        echo ""
        echo "=== 运行Prometheus端点测试 ==="
        TEST_ARGS+=("-Dtest=PrometheusEndpointIntegrationTest")
        ;;
    "all")
        echo ""
        echo "=== 运行所有监控测试 ==="
        TEST_ARGS+=("-Dtest=**/monitoring/*IntegrationTest,**/monitoring/*BenchmarkTest,**/monitoring/*StabilityTest")
        ;;
    *)
        echo ""
        echo "=== 运行指定测试: $TEST_TYPE ==="
        TEST_ARGS+=("-Dtest=$TEST_TYPE")
        ;;
esac

# 执行测试
echo "执行命令: ./mvnw ${TEST_ARGS[*]}"
./mvnw "${TEST_ARGS[@]}"

echo ""
echo "=== 测试执行成功 ==="

# 显示测试报告位置
REPORT_PATH="$PROJECT_ROOT/target/surefire-reports"
if [ -d "$REPORT_PATH" ]; then
    echo "测试报告位置: $REPORT_PATH"
fi

# 显示覆盖率报告位置（如果存在）
COVERAGE_REPORT="$PROJECT_ROOT/target/site/jacoco/index.html"
if [ -f "$COVERAGE_REPORT" ]; then
    echo "覆盖率报告: $COVERAGE_REPORT"
fi

echo ""
echo "=== 监控测试完成 ==="