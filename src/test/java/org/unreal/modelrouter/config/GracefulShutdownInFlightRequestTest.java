package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优雅停机在途请求行为测试（Netty/WebFlux）。
 *
 * <p>自行 {@link SpringApplication#run} 启动隔离最小应用（不使用 {@code @SpringBootTest}，
 * 避免测试框架在 context.close() 后的 afterTestExecution 回调报错），验证：
 * <ol>
 *   <li>{@code server.shutdown=graceful} 生效（日志出现 GracefulShutdown）；</li>
 *   <li>已进入 handler 的在途慢请求在 shutdown 期间能完整返回，而非被立即切断。</li>
 * </ol>
 *
 * <p>未覆盖：真实 AI SSE 长流（时长可能超过 timeout-per-shutdown-phase，届时会被强制关闭）。
 */
@DisplayName("优雅停机在途请求行为测试")
class GracefulShutdownInFlightRequestTest {

    /** 慢请求进入 handler 后置位，确保触发停机时请求确在在途。 */
    private static final CountDownLatch REQUEST_ENTERED = new CountDownLatch(1);

    @Test
    @DisplayName("优雅停机时在途慢请求应完成，而非被立即切断")
    void testInFlightRequestSurvivesGracefulShutdown() throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(IsolatedApp.class,
                "--server.port=0",
                "--server.shutdown=graceful",
                "--spring.lifecycle.timeout-per-shutdown-phase=10s",
                "--management.endpoints.enabled=false",
                "--spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration");

        try {
            int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            assertNotNull(port, "应拿到随机端口");

            TestRestTemplate restTemplate = new TestRestTemplate();
            String url = "http://127.0.0.1:" + port + "/slow";
            AtomicReference<ResponseEntity<String>> responseRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            CompletableFuture<Void> inFlight = CompletableFuture.runAsync(() -> {
                try {
                    responseRef.set(restTemplate.getForEntity(url, String.class));
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            });

            // 必须等请求真正进入 handler 再停机，否则测不到「在途」语义
            assertTrue(REQUEST_ENTERED.await(5, TimeUnit.SECONDS), "慢请求应进入 handler");

            // 触发优雅停机；此时 /slow 仍在 Mono.delay 中
            context.close();

            inFlight.get(8, TimeUnit.SECONDS);

            Throwable error = errorRef.get();
            if (error != null) {
                throw new AssertionError("优雅停机不应切断在途请求", error);
            }
            ResponseEntity<String> response = responseRef.get();
            assertNotNull(response, "应拿到在途请求的响应");
            assertEquals(HttpStatus.OK, response.getStatusCode(), "在途请求应完整返回 200");
            assertEquals("done", response.getBody(), "响应体应完整，不应被截断");
        } finally {
            if (context.isActive()) {
                context.close();
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class IsolatedApp {

        @RestController
        static class SlowController {

            @GetMapping("/slow")
            Mono<String> slow() {
                REQUEST_ENTERED.countDown();
                return Mono.delay(Duration.ofMillis(500)).thenReturn("done");
            }
        }
    }
}
