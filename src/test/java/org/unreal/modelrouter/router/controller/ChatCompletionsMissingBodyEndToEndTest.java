package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.exceptionhandler.GlobalControllerExceptionHandler;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.router.handler.ServiceEndpoint;
import org.unreal.modelrouter.router.handler.ServiceRequestExecutor;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「{@code POST /api/v1/chat/completions} 缺请求体 → 400」端到端用例（#683）.
 *
 * <p>背景：#683 记录 {@code UniversalController#chatCompletions} 是同一文件里唯一没有
 * 请求体判空的端点 —— 缺体时 {@code request.model()} 直接 NPE，被中央处理器兜成
 * 500「系统异常」，而 {@code embeddings} / {@code rerank} / {@code audio/speech} /
 * {@code images/generations} 都是 400。</p>
 *
 * <p>为什么必须走真实 HTTP 栈：<b>NPE 只在「参数解析器把 null 交给控制器、控制器真的执行了」
 * 这条路径上才发生</b>。直接调控制器方法（见 {@code UniversalControllerTest#UNIVERSAL-007}）
 * 能锁住「抛什么异常」，但锁不住「客户端实际收到什么状态码」—— 而 #683 的缺陷正是
 * 客户端可观测的状态码。本类断言的是 {@link TestRestTemplate} 拿到的状态码与错误体。
 * 隔离应用里同时注册了生产用的两个处理器：注解控制器内抛出的
 * {@code ServerWebInputException} 先由 {@link GlobalControllerExceptionHandler}
 * （{@code @RestControllerAdvice}）接住并渲染 400；没有 advice 时才会落到
 * {@link ReactiveGlobalExceptionHandler}（其 {@code "请求体无效或缺失"} 分支）。
 * 两个都注册才与生产一致。</p>
 *
 * <p>三条用例的分工：</p>
 * <ol>
 *   <li>无请求体 ⇒ 400 + 统一错误体，且不落「系统异常」（#683 的实修点）；</li>
 *   <li>对照组：合法请求体仍必须 200 且真的委托给 handler —— 证明没有把正常请求一起拒掉；</li>
 *   <li>对照组：另一端点抛 {@link IllegalStateException} 仍是 500 —— 证明 400 不是
 *       「任何异常都变 400」的过度修正。</li>
 * </ol>
 */
@DisplayName("聊天补全缺请求体 → 400 端到端测试")
class ChatCompletionsMissingBodyEndToEndTest {

    /**
     * 隔离应用上下文（每个测试类只启动一次）。
     */
    private static ConfigurableApplicationContext context;

    /**
     * 随机端口上的 HTTP 客户端。
     */
    private static TestRestTemplate restTemplate;

    /**
     * 启动隔离应用（关闭与本用例无关的自动装配，避免去连数据库 / Redis / 安全链）。
     */
    @BeforeAll
    static void startIsolatedApp() {
        context = SpringApplication.run(IsolatedApp.class,
                "--server.port=0",
                "--management.endpoints.enabled=false",
                "--spring.main.banner-mode=off",
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
        final Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port, "应拿到随机端口");
        // TestRestTemplate 默认不把 4xx/5xx 当异常抛（内部就是 NoOpResponseErrorHandler），故无需再配
        restTemplate = new TestRestTemplate();
    }

    /**
     * 关闭上下文。
     */
    @AfterAll
    static void stopIsolatedApp() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    @DisplayName("无请求体：客户端收到 400 + 统一错误体，而不是 500「系统异常」")
    void chatCompletionsWithoutBodyIsRejectedAsBadRequest() {
        final ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/chat/completions", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "#683：缺体属客户端可归因错误，必须与同文件其它端点一样是 400");
        final String body = response.getBody();
        assertNotNull(body, "错误响应体不应为空");
        assertTrue(body.contains("\"errorCode\":\"400\""), "错误体应带 errorCode=400，实际：" + body);
        assertTrue(body.contains("\"success\":false"), "错误体应是统一失败形状，实际：" + body);
        assertTrue(body.contains("Request body is required"), "消息应说明请求体缺失，实际：" + body);
        assertFalse(body.contains("系统异常"), "不得落进「系统异常 → 500」文案，实际：" + body);
        assertFalse(body.contains("NullPointerException"), "不得把 NPE 透给客户端，实际：" + body);
        assertFalse(body.contains("ServerWebInputException"), "不得把内部异常类名透给客户端，实际：" + body);
    }

    @Test
    @DisplayName("对照组：合法请求体仍 200 且委托给 handler")
    void chatCompletionsWithBodyStillDelegates() {
        final ServiceRequestHandler handler = context.getBean(ServiceRequestHandler.class);
        when(handler.handleRequest(eq(ServiceEndpoint.CHAT), eq("test-model"), any(),
                any(ServerWebExchange.class), any(ServiceRequestExecutor.class)))
                .thenReturn(Mono.just(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"delegated\":true}")));

        final ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/chat/completions",
                "{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "判空只该拦缺体请求，合法请求不得被拒，实际体：" + response.getBody());
        assertEquals("{\"delegated\":true}", response.getBody(), "合法请求应原样透传 handler 的响应");
    }

    @Test
    @DisplayName("对照组：非请求体类异常仍映射 500（证明链路真的经过中央处理器）")
    void unrelatedExceptionStillMapsToInternalServerError() {
        final ResponseEntity<String> response = exchange(HttpMethod.GET, "/boom", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "对照组必须保持 500，否则说明「400」可能来自别处");
        assertTrue(response.getBody() != null && response.getBody().contains("系统异常"),
                "500 文案应是「系统异常: …」，实际：" + response.getBody());
    }

    /**
     * 发一个请求。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体（{@code null} 表示不带请求体）
     * @return 响应（不因 4xx/5xx 抛异常）
     */
    private static ResponseEntity<String> exchange(final HttpMethod method, final String path, final String body) {
        final int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("http://127.0.0.1:" + port + path, method, entity, String.class);
    }

    /**
     * 隔离应用：真实控制器 + 真实两个中央处理器 + mock 协作对象 + 一个对照组端点。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({GlobalControllerExceptionHandler.class, ReactiveGlobalExceptionHandler.class,
            UniversalController.class, IsolatedApp.ControlGroupController.class})
    static class IsolatedApp {

        /**
         * 中央处理器依赖的错误追踪器（本用例不校验其行为）。
         *
         * @return mock
         */
        @Bean
        ErrorTracker errorTracker() {
            return mock(ErrorTracker.class);
        }

        /**
         * 控制器的唯一协作对象：本用例只关心「请求是否被委托」，不跑真实路由。
         *
         * @return mock
         */
        @Bean
        ServiceRequestHandler serviceRequestHandler() {
            return mock(ServiceRequestHandler.class);
        }

        /**
         * 对照组端点：抛非请求体类异常，验证 500 分支仍然可达。
         */
        @RestController
        static class ControlGroupController {

            /**
             * 抛运行时异常。
             *
             * @return 不会返回
             */
            @GetMapping("/boom")
            String boom() {
                throw new IllegalStateException("control-group");
            }
        }
    }
}
