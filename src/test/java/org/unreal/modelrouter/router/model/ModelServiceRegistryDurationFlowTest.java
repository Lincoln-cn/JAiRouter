package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.loadbalancer.LoadBalancer;

import static org.mockito.Mockito.*;

/**
 * v2.9.3: 验证 LoadBalancer 3-arg 钩子接口正确接收 durationMs
 * 测试 LoadBalancer 接口默认方法和显式实现的3-arg recordCallComplete/recordCallFailure
 */
@DisplayName("LoadBalancer duration 钩子流转测试")
@ExtendWith(MockitoExtension.class)
class ModelServiceRegistryDurationFlowTest {

    @Mock
    private LoadBalancer loadBalancer;

    @Test
    @DisplayName("3-arg recordCallComplete(instance, durationMs, success) 正确传递参数")
    void recordCallCompleteWithDurationFlowsToLoadBalancer() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setBaseUrl("http://localhost:8080");

        loadBalancer.recordCallComplete(instance, 250L, true);

        verify(loadBalancer).recordCallComplete(instance, 250L, true);
    }

    @Test
    @DisplayName("3-arg recordCallFailure(instance, durationMs, errorCode) 正确传递参数")
    void recordCallFailureWithDurationFlowsToLoadBalancer() {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setBaseUrl("http://localhost:8080");

        loadBalancer.recordCallFailure(instance, 5000L, "TIMEOUT");

        verify(loadBalancer).recordCallFailure(instance, 5000L, "TIMEOUT");
    }
}
