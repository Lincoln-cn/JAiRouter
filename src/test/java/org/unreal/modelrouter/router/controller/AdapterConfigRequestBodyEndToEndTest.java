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
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;
import org.unreal.modelrouter.router.adapter.persistence.AdapterDefinitionPersistenceService;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 「控制器 → 中央异常处理器 → 400」端到端用例（#443 的遗留项，#604 技术债批次 A）.
 *
 * <p>背景：#443 把「请求体缺失」从 500 改成 400，但当时只补到「控制器确实抛
 * {@link ServerWebInputException}」与「处理器把该异常渲染成 400」两段单元断言 ——
 * 中间这条链路（真实 WebFlux 请求进控制器 / 参数解析器抛异常 / 真实
 * {@link ReactiveGlobalExceptionHandler} 兜底渲染）没有一条走真实 HTTP 栈的用例守着。
 * 这条用例就是补那一环：<b>断言客户端实际收到的状态码与响应体</b>，而不是断言某个方法抛了什么。</p>
 *
 * <p>为什么不用 {@code @SpringBootTest}：本仓测试基座没有全量 Spring 上下文（见
 * {@code GracefulShutdownInFlightRequestTest} 的说明），故沿用「{@link SpringApplication#run}
 * 起一个隔离最小应用」的既有做法。隔离应用里注册的是**真实**控制器与**真实**中央处理器，
 * 只把控制器的 8 个协作对象与 {@link ErrorTracker} 换成 mock —— 因为本用例只关心
 * 「请求体缺失」这一条协议的失败路径，不关心业务。</p>
 *
 * <p>三条用例的分工：</p>
 * <ol>
 *   <li>{@code POST} 无请求体 ⇒ 400（参数解析器抛 {@code ServerWebInputException} 的真实路径）；</li>
 *   <li>{@code PUT} 无请求体 ⇒ 400 而非 500（#443 改的就是这条「被方法自己的兜底 catch 吞掉」的路径）；</li>
 *   <li>对照组：另一个端点抛 {@link IllegalStateException} ⇒ 仍必须是 500 —— 证明这条测试真的
 *       经过中央处理器，而且没有把「任何异常都变成 400」这种过度修正当成通过。</li>
 * </ol>
 */
@DisplayName("控制器→中央处理器→400 端到端测试")
class AdapterConfigRequestBodyEndToEndTest {

    /**
     * 隔离应用上下文（每个测试类只启动一次，WebFlux 冷启动 + Netty 端口绑定约 2~3s）。
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
    @DisplayName("POST 无请求体：客户端收到 400 + 统一错误体，而不是 500")
    void postWithoutBodyIsRejectedAsBadRequest() {
        final ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/config/adapter", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "请求体缺失属客户端可归因错误，必须是 400");
        final String body = response.getBody();
        assertNotNull(body, "错误响应体不应为空");
        assertTrue(body.contains("\"errorCode\":\"400\""), "错误体应带 errorCode=400，实际：" + body);
        assertTrue(body.contains("\"success\":false"), "错误体应是统一失败形状，实际：" + body);
        assertTrue(body.contains("请求体无效或缺失"), "消息应说明请求体缺失，实际：" + body);
        assertFalse(body.contains("ServerWebInputException"), "不得把内部异常类名透给客户端，实际：" + body);
        assertFalse(body.contains("系统异常"), "不得落进「系统异常 → 500」文案，实际：" + body);
    }

    @Test
    @DisplayName("PUT 无请求体：客户端收到 400，而不是被方法内部兜底 catch 包成 500")
    void putWithoutBodyIsRejectedAsBadRequest() {
        final ResponseEntity<String> response = exchange(HttpMethod.PUT, "/api/config/adapter/some-adapter", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "#443 修的就是这条路径：校验必须在控制器自己的 try 之外，否则被包成 500");
        assertTrue(response.getBody() != null && response.getBody().contains("\"errorCode\":\"400\""),
                "错误体应带 errorCode=400，实际：" + response.getBody());
    }

    @Test
    @DisplayName("对照组：非请求体类异常仍映射 500（证明链路真的经过中央处理器）")
    void unrelatedExceptionStillMapsToInternalServerError() {
        final ResponseEntity<String> response = exchange(HttpMethod.GET, "/boom", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "对照组必须保持 500，否则说明「400」可能来自别处（例如参数校验误伤）");
        assertTrue(response.getBody() != null && response.getBody().contains("系统异常"),
                "500 文案应是「系统异常: …」，实际：" + response.getBody());
    }

    @Test
    @DisplayName("控制器自身的 null 校验抛 ServerWebInputException（#443 的实修点）")
    void controllerGuardsThrowServerWebInputException() {
        final AdapterConfigController controller = context.getBean(AdapterConfigController.class);

        assertThrows(ServerWebInputException.class, () -> controller.createAdapter(null),
                "createAdapter 的 null 校验必须抛 ServerWebInputException（交给中央处理器）");
        assertThrows(ServerWebInputException.class, () -> controller.updateAdapter("some-adapter", null),
                "updateAdapter 的 null 校验必须抛 ServerWebInputException（交给中央处理器）");
    }

    /**
     * 发一个不带请求体的请求。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体（本类一律传 {@code null}）
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
     * 隔离应用：真实控制器 + 真实中央处理器 + mock 协作对象 + 一个对照组端点。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ReactiveGlobalExceptionHandler.class, AdapterConfigController.class, IsolatedApp.ControlGroupController.class})
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
         * 控制器协作对象 1。
         *
         * @return mock
         */
        @Bean
        AdapterRegistry adapterRegistry() {
            return mock(AdapterRegistry.class);
        }

        /**
         * 控制器协作对象 2。
         *
         * @return mock
         */
        @Bean
        AdapterDefinitionProperties adapterDefinitionProperties() {
            return mock(AdapterDefinitionProperties.class);
        }

        /**
         * 控制器协作对象 3。
         *
         * @return mock
         */
        @Bean
        AdapterDefinitionPersistenceService adapterDefinitionPersistenceService() {
            return mock(AdapterDefinitionPersistenceService.class);
        }

        /**
         * 控制器协作对象 4。
         *
         * @return mock
         */
        @Bean
        AdapterContext adapterContext() {
            return mock(AdapterContext.class);
        }

        /**
         * 控制器协作对象 5。
         *
         * @return mock
         */
        @Bean
        RequestProcessingSupport requestProcessingSupport() {
            return mock(RequestProcessingSupport.class);
        }

        /**
         * 控制器协作对象 6。
         *
         * @return mock
         */
        @Bean
        ResilienceSupport resilienceSupport() {
            return mock(ResilienceSupport.class);
        }

        /**
         * 控制器协作对象 7。
         *
         * @return mock
         */
        @Bean
        OpenAiRequestTransformer openAiRequestTransformer() {
            return mock(OpenAiRequestTransformer.class);
        }

        /**
         * 控制器协作对象 8。
         *
         * @return mock
         */
        @Bean
        OpenAiResponseTransformer openAiResponseTransformer() {
            return mock(OpenAiResponseTransformer.class);
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
