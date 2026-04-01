#!/bin/bash

echo "=== 详细验证代码修复是否正确 ==="

echo "1. 检查 ServiceInstanceVO 是否包含 CircuitBreakerVO 类和 circuitBreaker 字段..."
if grep -n "public static class CircuitBreakerVO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceVO 包含 CircuitBreakerVO 类"
else
    echo "✗ ServiceInstanceVO 缺少 CircuitBreakerVO 类"
    # 检查实际的拼写
    if grep -n "CircuitBreakerVO\|CircuitBreakerVO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java >/dev/null 2>&1; then
        echo "  提示: 找到了类似拼写的类"
    fi
fi

if grep -n "private CircuitBreakerVO circuitBreaker;" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceVO 包含 circuitBreaker 字段"
else
    echo "✗ ServiceInstanceVO 缺少 circuitBreaker 字段"
    # 检查实际的拼写
    if grep -n "circuitBreaker" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java >/dev/null 2>&1; then
        echo "  提示: 找到了类似拼写的字段"
    fi
fi

echo ""
echo "2. 检查 ServiceInstanceEntity 是否包含熔断器相关字段..."
if grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceEntity 包含 circuitBreakerEnabled 字段"
else
    echo "✗ ServiceInstanceEntity 缺少 circuitBreakerEnabled 字段"
fi

if grep -n "circuitBreakerFailureThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceEntity 包含 circuitBreakerFailureThreshold 字段"
else
    echo "✗ ServiceInstanceEntity 缺少 circuitBreakerFailureThreshold 字段"
fi

if grep -n "circuitBreakerTimeout" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceEntity 包含 circuitBreakerTimeout 字段"
else
    echo "✗ ServiceInstanceEntity 缺少 circuitBreakerTimeout 字段"
fi

if grep -n "circuitBreakerSuccessThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java >/dev/null 2>&1; then
    echo "✓ ServiceInstanceEntity 包含 circuitBreakerSuccessThreshold 字段"
else
    echo "✗ ServiceInstanceEntity 缺少 circuitBreakerSuccessThreshold 字段"
fi

echo ""
echo "3. 检查 DatabaseConfigService 中的 buildInstanceVO 方法是否处理熔断器配置..."
if grep -A 10 -B 2 "circuitBreakerEnabled() != null && instance.getCircuitBreakerEnabled()" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
    echo "✓ buildInstanceVO 方法处理熔断器配置"
else
    echo "✗ buildInstanceVO 方法未处理熔断器配置"
    # 检查实际实现
    if grep -A 15 "构建熔断器配置 VO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
        echo "  提示: 找到了熔断器配置构建代码"
    fi
fi

echo ""
echo "4. 检查 DatabaseConfigService 中的 buildInstanceMap 方法是否返回熔断器配置..."
if grep -n "result.put(\"circuitBreaker\", vo.getCircuitBreaker())" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
    echo "✓ buildInstanceMap 方法返回熔断器配置"
else
    echo "✗ buildInstanceMap 方法未返回熔断器配置"
    # 检查实际实现
    if grep -A 2 -B 2 "circuitBreaker.*vo.getCircuitBreaker" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
        echo "  提示: 找到了熔断器配置返回代码"
    fi
fi

echo ""
echo "5. 检查 InstanceConfigController 是否处理熔断器配置..."
if grep -n "buildCircuitBreakerConfig" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/controller/InstanceConfigController.java >/dev/null 2>&1; then
    echo "✓ InstanceConfigController 包含 buildCircuitBreakerConfig 方法"
else
    echo "✗ InstanceConfigController 缺少 buildCircuitBreakerConfig 方法"
fi

echo ""
echo "6. 检查 DTO 类是否包含熔断器配置字段..."
if grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceCreateRequest.java >/dev/null 2>&1 && \
   grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceUpdateRequest.java >/dev/null 2>&1; then
    echo "✓ DTO 类包含熔断器配置字段"
else
    echo "✗ DTO 类缺少熔断器配置字段"
fi

echo ""
echo "7. 检查 InstanceConfigService 是否处理熔断器配置..."
if grep -A 10 -B 2 "circuitBreaker.*instance.get.*circuitBreaker" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/service/InstanceConfigService.java >/dev/null 2>&1; then
    echo "✓ InstanceConfigService 处理熔断器配置"
else
    echo "✗ InstanceConfigService 未处理熔断器配置"
    # 检查实际实现
    if grep -A 15 "CircuitBreakerVO circuitBreaker = null" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/service/InstanceConfigService.java >/dev/null 2>&1; then
        echo "  提示: 找到了熔断器配置处理代码"
    fi
fi

echo ""
echo "8. 检查 DatabaseConfigService 中的 buildInstanceEntityFromMap 方法是否处理熔断器配置..."
if grep -A 10 -B 2 "circuitBreakerEnabled.*instanceConfig" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
    echo "✓ buildInstanceEntityFromMap 方法处理熔断器配置"
else
    echo "✗ buildInstanceEntityFromMap 方法未处理熔断器配置"
    # 检查实际实现
    if grep -A 20 "熔断器配置 - 支持嵌套和扁平两种格式" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
        echo "  提示: 找到了熔断器配置处理代码"
    fi
fi

echo ""
echo "=== 代码修复验证完成 ==="