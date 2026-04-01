#!/bin/bash

echo "=== 验证代码修复是否正确 ==="

echo "1. 检查 ServiceInstanceVO 是否包含 CircuitBreakerVO 类和 circuitBreaker 字段..."
if grep -n "public static class CircuitBreakerVO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java && \
   grep -n "private CircuitBreakerVO circuitBreaker;" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java; then
    echo "✓ ServiceInstanceVO 包含 CircuitBreakerVO 类和 circuitBreaker 字段"
else
    echo "✗ ServiceInstanceVO 缺少 CircuitBreakerVO 类或 circuitBreaker 字段"
fi

echo ""
echo "2. 检查 ServiceInstanceEntity 是否包含熔断器相关字段..."
if grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -n "circuitBreakerFailureThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -n "circuitBreakerTimeout" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -n "circuitBreakerSuccessThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java; then
    echo "✓ ServiceInstanceEntity 包含熔断器相关字段"
else
    echo "✗ ServiceInstanceEntity 缺少熔断器相关字段"
fi

echo ""
echo "3. 检查 DatabaseConfigService 中的 buildInstanceVO 方法是否处理熔断器配置..."
if grep -n "circuitBreakerEnabled() != null && instance.getCircuitBreakerEnabled()" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java && \
   grep -n "CircuitBreakerVO.builder()" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java; then
    echo "✓ buildInstanceVO 方法处理熔断器配置"
else
    echo "✗ buildInstanceVO 方法未处理熔断器配置"
fi

echo ""
echo "4. 检查 DatabaseConfigService 中的 buildInstanceMap 方法是否返回熔断器配置..."
if grep -n "put(\"circuitBreaker\", vo.getCircuitBreaker())" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java; then
    echo "✓ buildInstanceMap 方法返回熔断器配置"
else
    echo "✗ buildInstanceMap 方法未返回熔断器配置"
fi

echo ""
echo "5. 检查 InstanceConfigController 是否处理熔断器配置..."
if grep -n "buildCircuitBreakerConfig" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/controller/InstanceConfigController.java; then
    echo "✓ InstanceConfigController 包含 buildCircuitBreakerConfig 方法"
else
    echo "✗ InstanceConfigController 缺少 buildCircuitBreakerConfig 方法"
fi

echo ""
echo "6. 检查 DTO 类是否包含熔断器配置字段..."
if grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceCreateRequest.java && \
   grep -n "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceUpdateRequest.java; then
    echo "✓ DTO 类包含熔断器配置字段"
else
    echo "✗ DTO 类缺少熔断器配置字段"
fi

echo ""
echo "7. 检查 InstanceConfigService 是否处理熔断器配置..."
if grep -n "circuitBreaker instanceof Map" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/service/InstanceConfigService.java && \
   grep -n "circuitBreakerVO.builder()" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/service/InstanceConfigService.java; then
    echo "✓ InstanceConfigService 处理熔断器配置"
else
    echo "✗ InstanceConfigService 未处理熔断器配置"
fi

echo ""
echo "=== 代码修复验证完成 ==="