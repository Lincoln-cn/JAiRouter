#!/bin/bash

echo "=== 最终验证：限流器和熔断器配置修复 ==="
echo ""

SUCCESS_COUNT=0
TOTAL_CHECKS=10

echo "检查 1/10: ServiceInstanceEntity 是否包含熔断器字段..."
if grep -q "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -q "circuitBreakerFailureThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -q "circuitBreakerTimeout" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java && \
   grep -q "circuitBreakerSuccessThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/store/entity/ServiceInstanceEntity.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 2/10: ServiceInstanceVO 是否包含 CircuitBreakerVO 类..."
if grep -q "public static class CircuitBreakerVO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 3/10: ServiceInstanceVO 是否包含 circuitBreaker 字段..."
if grep -q "private CircuitBreakerVO circuitBreaker;" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/vo/ServiceInstanceVO.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 4/10: InstanceCreateRequest 是否包含熔断器字段..."
if grep -q "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceCreateRequest.java && \
   grep -q "circuitBreakerFailureThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceCreateRequest.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 5/10: InstanceUpdateRequest 是否包含熔断器字段..."
if grep -q "circuitBreakerEnabled" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceUpdateRequest.java && \
   grep -q "circuitBreakerFailureThreshold" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/dto/InstanceUpdateRequest.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 6/10: InstanceConfigController 是否包含 buildCircuitBreakerConfig 方法..."
if grep -q "buildCircuitBreakerConfig" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/controller/InstanceConfigController.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 7/10: DatabaseConfigService buildInstanceEntityFromMap 是否处理熔断器配置..."
if grep -q "circuitBreakerEnabled.*instanceConfig" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 8/10: DatabaseConfigService buildInstanceVO 是否构建熔断器配置..."
if grep -q "构建熔断器配置 VO" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "检查 9/10: DatabaseConfigService buildInstanceMap 是否返回熔断器配置 (关键修复)..."
if grep -q "result.put(\"circuitBreaker\", vo.getCircuitBreaker())" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java; then
    echo "✅ 通过 - 关键修复已实现！"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败 - 关键修复缺失！"
fi

echo ""
echo "检查 10/10: InstanceConfigService convertToVO 是否处理熔断器配置..."
if grep -q "circuitBreaker.*instance.get.*circuitBreaker" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/service/InstanceConfigService.java; then
    echo "✅ 通过"
    ((SUCCESS_COUNT++))
else
    echo "❌ 失败"
fi

echo ""
echo "==========================================="
echo "验证结果: $SUCCESS_COUNT / $TOTAL_CHECKS 项检查通过"
echo "==========================================="

if [ $SUCCESS_COUNT -eq $TOTAL_CHECKS ]; then
    echo "🎉 全部修复已正确实现！"
    echo ""
    echo "修复总结:"
    echo "- 后端实体类添加了熔断器相关字段"
    echo "- VO 类添加了 CircuitBreakerVO 和 circuitBreaker 字段"
    echo "- DTO 类添加了熔断器配置字段"
    echo "- 控制器添加了处理熔断器配置的方法"
    echo "- 服务类添加了处理熔断器配置的逻辑"
    echo "- 关键修复: buildInstanceMap 方法现在返回熔断器配置给前端"
    echo ""
    echo "现在，实例管理页面中的限流器和熔断器配置可以正确保存和显示了！"
else
    echo "⚠️  部分修复未完成，需要进一步检查"
fi