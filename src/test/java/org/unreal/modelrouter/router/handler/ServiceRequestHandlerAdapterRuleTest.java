package org.unreal.modelrouter.router.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ServiceRequestHandler 规则引擎 TARGET_ADAPTER 动作测试
 * 验证:规则指定适配器名时按名取用、无规则回退原逻辑、未注册降级不抛
 */
@DisplayName("ServiceRequestHandler 规则适配器选择测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestHandlerAdapterRuleTest {

    private AdapterRegistry adapterRegistry;
    private ModelServiceRegistry registry;
    private ServiceStateManager serviceStateManager;
    private ServiceRequestHandler handler;

    @BeforeEach
    void setUp() {
        adapterRegistry = mock(AdapterRegistry.class);
        registry = mock(ModelServiceRegistry.class);
        serviceStateManager = mock(ServiceStateManager.class);
        handler = new ServiceRequestHandler(adapterRegistry, registry, serviceStateManager, null, null);

        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);

        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setInstanceId("inst-1");
        instance.setName("gpt-4");
        instance.setBaseUrl("http://local");
        instance.setAdapter("default-adapter");
        when(registry.selectInstance(any(), anyString(), anyString(), any())).thenReturn(instance);

        ServiceCapability defaultAdapter = mock(ServiceCapability.class);
        when(adapterRegistry.getAdapter(any(ModelServiceRegistry.ServiceType.class), any()))
                .thenReturn(defaultAdapter);
    }

    private Mono<ResponseEntity<?>> invoke(final String modelName) throws Exception {
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions")
                .header("x-routing", "vllm")
                .build();
        ServiceRequestExecutor executor = mock(ServiceRequestExecutor.class);
        when(executor.execute(any(), any(), any())).thenReturn(Mono.just(ResponseEntity.ok("ok")));
        return handler.handleRequest(ServiceEndpoint.CHAT, modelName, "Bearer token", httpRequest, executor);
    }

    @Nested
    @DisplayName("TARGET_ADAPTER 规则命中测试")
    class RuleAdapterHitTests {

        @Test
        @DisplayName("ADAPTER-RULE-001: 规则指定适配器名时按名取用")
        void testRuleAdapterName_used() throws Exception {
            ServiceCapability ruleAdapter = mock(ServiceCapability.class);
            when(registry.resolveRuleAdapterName(any(), anyString(), any(), anyMap())).thenReturn("openai-compat");
            when(adapterRegistry.getAdapterByName("openai-compat")).thenReturn(ruleAdapter);

            ResponseEntity<?> response = invoke("gpt-4").block();

            assertNotNull(response);
            verify(adapterRegistry).getAdapterByName("openai-compat");
            verify(adapterRegistry, never())
                    .getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        }

        @Test
        @DisplayName("ADAPTER-RULE-002: 无规则时走原逻辑")
        void testNoRule_fallbackToInstanceAdapter() throws Exception {
            when(registry.resolveRuleAdapterName(any(), anyString(), any(), anyMap())).thenReturn(null);

            ResponseEntity<?> response = invoke("gpt-4").block();

            assertNotNull(response);
            verify(adapterRegistry, never()).getAdapterByName(anyString());
            verify(adapterRegistry)
                    .getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        }

        @Test
        @DisplayName("ADAPTER-RULE-003: 规则指定适配器未注册时降级不抛异常")
        void testRuleAdapterNotRegistered_fallbacks() throws Exception {
            when(registry.resolveRuleAdapterName(any(), anyString(), any(), anyMap()))
                    .thenReturn("not-registered");
            when(adapterRegistry.getAdapterByName("not-registered")).thenReturn(null);

            ResponseEntity<?> response = invoke("gpt-4").block();

            assertNotNull(response, "未注册适配器应降级而非抛错");
            verify(adapterRegistry).getAdapterByName("not-registered");
            verify(adapterRegistry)
                    .getAdapter(any(ModelServiceRegistry.ServiceType.class), any());
        }
    }
}
