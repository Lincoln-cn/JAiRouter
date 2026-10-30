package org.unreal.modelrouter.router.checker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP 探活语义（#833）。
 *
 * <p>默认探活方式改为 HTTP 后，判定口径是「状态码 &lt; 500 视为健康」：
 * 404 / 405 说明端点不存在或方法不允许，但进程确实在处理请求，算健康；
 * 5xx 说明上游自己出错，算不健康。这两条是这个默认值能成立的前提，
 * 所以用真实的本地 HTTP 服务钉住，而不是桩掉 probe。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCheckerHttpModeTest {

    @Mock
    private ModelServiceRegistry modelServiceRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HCM-001: 默认 http 模式请求 /v1/models，404 也算健康")
    void missingEndpointStillCountsAsHealthy() throws IOException {
        final String baseUrl = startServer(404);
        runCheck(baseUrl, true);
    }

    @Test
    @DisplayName("HCM-002: 上游 5xx 判不健康")
    void serverErrorMarksUnhealthy() throws IOException {
        final String baseUrl = startServer(500);
        runCheck(baseUrl, false);
    }

    @Test
    @DisplayName("HCM-003: 上游 200 判健康")
    void okMarksHealthy() throws IOException {
        final String baseUrl = startServer(200);
        runCheck(baseUrl, true);
    }

    private String startServer(final int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void runCheck(final String baseUrl, final boolean expectedHealthy) {
        final ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName("http-probe");
        instance.setBaseUrl(baseUrl);
        instance.setPath("/v1/chat/completions");

        final Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> registry =
                new HashMap<>();
        registry.put(ModelServiceRegistry.ServiceType.chat, new ArrayList<>(List.of(instance)));
        when(modelServiceRegistry.getAllInstances()).thenReturn(registry);
        when(serviceStateManager.isInstanceHealthy(anyString(), any())).thenReturn(true);

        final HealthCheckProperties properties = new HealthCheckProperties();
        properties.setTimeoutMs(2_000);
        // 本类测的是单轮探活语义（#833）；#855 引入的连续失败滞回默认阈值是 2，
        // 这里显式降为 1，好让「一次 5xx 即判不健康」这条判定能在单轮内被断言
        // （滞回本身由 ServerCheckerHysteresisTest 覆盖）。
        properties.setFailureThreshold(1);
        // 不显式设置 mode：这里测的就是 #833 之后的默认值。
        new ServerChecker(modelServiceRegistry, serviceStateManager, properties).runHealthCheckBatch();

        verify(serviceStateManager).updateInstanceHealthStatus("chat", instance, expectedHealthy);
    }
}
