package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #850 守卫：降级缓存的「写键」必须复用装配期算好的请求键。
 *
 * <p>缺陷形态（#823 接线时留下）：键在 {@code processRequestWithFallback} 装配期算一次
 * （此时 {@code AffinityContextHolder.apiKeyId()} 有值），而写缓存时在响应到达的
 * {@code doOnNext} 里又算了一次 —— 那时 {@code ServiceRequestHandler} 的 finally 早已把
 * {@link AffinityContextHolder} 清空，调用方被归一成 {@code "anonymous"}，
 * 与读侧的真实 callerId 永不相等 ⇒ {@code strategy: cache} 的降级永远命中不了。</p>
 *
 * <p>本用例精确复现这个时序：装配 → 清空 ThreadLocal → 订阅（响应到达）→ 断言写键里
 * 仍是真实调用方。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#850 降级缓存写键复用装配期的键")
class BaseAdapterFallbackCacheKeyTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private FallbackManager fallbackManager;

    @Mock
    private CacheFallbackStrategy cacheStrategy;

    @AfterEach
    void clearThreadLocal() {
        AffinityContextHolder.clear();
    }

    @Test
    @DisplayName("850-001 响应到达时调用方身份已清空，写键仍是装配期的真实调用方")
    void writeKeyKeepsCallerIdAfterContextCleared() {
        final ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        when(registry.getServiceConfig(ModelServiceRegistry.ServiceType.chat)).thenReturn(serviceConfig);
        when(registry.getFallbackManager()).thenReturn(fallbackManager);
        when(fallbackManager.getFallbackStrategy("chat", serviceConfig)).thenReturn(cacheStrategy);

        AffinityContextHolder.set("key-xyz", "10.0.0.1", "chat", "model");

        final SuccessAdapter adapter = new SuccessAdapter(registry);
        // 装配：这一步之后请求键已经算好（含 callerId=key-xyz）
        final Mono<?> requestMono = adapter.invoke();

        // 复现真实时序：同步段结束，ServiceRequestHandler 的 finally 清空 ThreadLocal
        AffinityContextHolder.clear();

        StepVerifier.create(requestMono)
                .assertNext(response -> assertTrue(((ResponseEntity<?>) response).getStatusCode().is2xxSuccessful()))
                .verifyComplete();

        final ArgumentCaptor<FallbackRequestKey> keyCaptor = ArgumentCaptor.forClass(FallbackRequestKey.class);
        verify(cacheStrategy).cacheResponse(keyCaptor.capture(), any(ResponseEntity.class));
        final FallbackRequestKey written = keyCaptor.getValue();
        assertNotNull(written, "写键为 null：说明写侧没有复用装配期的键，而是按已清空的 ThreadLocal 重算了一次");
        final String writtenKey = written.asCacheKey();

        assertTrue(writtenKey.contains("key-xyz"),
                "写键里的调用方必须是装配期的真实 keyId，实际写的是：" + writtenKey);
        assertFalse(writtenKey.contains(":anonymous:"),
                "写键被归一成了 anonymous（响应到达时又重算了一次键）：" + writtenKey);
    }

    /**
     * 成功路径的适配器：{@code processRequest} 直接给 2xx，用来触发写缓存。
     */
    private static final class SuccessAdapter extends BaseAdapter {

        private SuccessAdapter(final ModelServiceRegistry registry) {
            super(
                    new AdapterContext(registry, new ObjectMapper(), null),
                    new RequestProcessingSupport(null, null, null, null, null, null, null),
                    new ResilienceSupport(null, null, null, null, null, null));
        }

        @Override
        protected String getAdapterType() {
            return "test";
        }

        @Override
        public AdapterCapabilities supportCapability() {
            return AdapterCapabilities.builder().chat(true).build();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> Mono processRequest(final T request, final String authorization,
                final ServerHttpRequest httpRequest,
                final ModelServiceRegistry.ServiceType serviceType,
                final String modelName, final RequestProcessor<T> processor) {
            return Mono.just(ResponseEntity.ok(Map.of("choices", "answer")));
        }

        private Mono<?> invoke() {
            final ServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/v1/chat/completions").build();
            return processRequestWithFallback("request-body", "Bearer token", httpRequest,
                    ModelServiceRegistry.ServiceType.chat, "model", null);
        }
    }
}
