package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.fallback.FallbackManager;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * #816：降级策略在 {@code processRequestWithFallback} 里只能被求值一次。
 *
 * <p>旧写法是 {@code fallback() != null ? Mono.just(fallback()) : error}，对同一个异常调了两次
 * {@code fallback()}。现在的两个实现（cache / default）都是无状态方法，所以今天不会炸；但
 * {@link FallbackStrategy} 的契约是「给定异常返回一个降级响应」，完全允许多一步内部状态
 * （按请求取缓存条目、一次性计数、消耗型资源）。一旦有状态，第二次求值就可能拿到 null
 * 或拿到别的请求的条目 —— 前者退化成 503，后者是串响应。这里用「只允许被调用一次」的
 * 策略把这个隐患钉死。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#816 降级策略单次求值")
class BaseAdapterFallbackEvaluationTest {

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private FallbackManager fallbackManager;

    @Mock
    private FallbackStrategy<ResponseEntity<?>> strategy;

    @Mock
    private InstanceSelector instanceSelector;

    @Test
    @DisplayName("816-001 降级策略只被调用一次（非空返回）")
    void fallbackIsEvaluatedOnce() {
        final AtomicInteger calls = new AtomicInteger();
        when(strategy.fallback(any(FallbackRequestKey.class), any(Exception.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("degraded");
        });
        final TestAdapter adapter = newAdapterWithStrategy();

        StepVerifier.create(adapter.invokeFallback())
                .assertNext(response -> assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                        ((ResponseEntity<?>) response).getStatusCode()))
                .verifyComplete();

        assertEquals(1, calls.get(), "降级策略必须只求值一次，否则有状态策略会拿到不同的结果");
    }

    @Test
    @DisplayName("816-002 降级策略返回 null 时退化为 503，且同样只调用一次")
    void nullFallbackBecomesServiceUnavailable() {
        final AtomicInteger calls = new AtomicInteger();
        when(strategy.fallback(any(FallbackRequestKey.class), any(Exception.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return null;
        });
        final TestAdapter adapter = newAdapterWithStrategy();

        StepVerifier.create(adapter.invokeFallback())
                .expectErrorSatisfies(error -> {
                    assertEquals(ResponseStatusException.class, error.getClass());
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ResponseStatusException) error).getStatusCode());
                })
                .verify();

        assertEquals(1, calls.get(), "为空的分支也不该重复求值");
    }

    private TestAdapter newAdapterWithStrategy() {
        final ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        when(registry.getServiceConfig(ModelServiceRegistry.ServiceType.chat)).thenReturn(serviceConfig);
        when(registry.getFallbackManager()).thenReturn(fallbackManager);
        when(fallbackManager.getFallbackStrategy("chat", serviceConfig)).thenReturn(strategy);
        return new TestAdapter(registry);
    }

    /**
     * 只用于本测试的适配器：{@code processRequest} 直接失败，从而进入降级分支。
     */
    private static final class TestAdapter extends BaseAdapter {

        private TestAdapter(final ModelServiceRegistry registry) {
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
            return Mono.error(new IllegalStateException("upstream down"));
        }

        private Mono<?> invokeFallback() {
            final ServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/v1/chat/completions").build();
            return processRequestWithFallback("request", "Bearer token", httpRequest,
                    ModelServiceRegistry.ServiceType.chat, "model", null);
        }
    }
}
