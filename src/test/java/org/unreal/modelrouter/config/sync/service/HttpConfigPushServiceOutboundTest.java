package org.unreal.modelrouter.config.sync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;
import org.unreal.modelrouter.router.http.WebClientPool;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #281：配置推送此前自建 WebClient，既不经过 SSRF 门闩、也不经过连接期解析守卫。
 * 本类验证它已改由 WebClientPool 创建客户端，且门闩确实在生效。
 */
@DisplayName("HttpConfigPushService：出站经 WebClientPool")
class HttpConfigPushServiceOutboundTest {

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    @Test
    @DisplayName("目标可达时经池创建客户端并送达")
    void push_reachableTarget_delivers() {
        final AtomicReference<String> receivedPath = new AtomicReference<>();
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    receivedPath.set(request.uri());
                    return response.sendString(Mono.just("ok"));
                })
                .bindNow();

        final HttpConfigPushService service = serviceFor("http://127.0.0.1:" + server.port());

        final var result = service.pushToService("chat", Map.of("key", "value")).block();

        assertNotNull(result);
        assertTrue(result.success(), () -> "推送应成功，实际: " + result.message());
        assertEquals("/internal/config/push", receivedPath.get());
    }

    @Test
    @DisplayName("目标为云元数据地址时被出站门闩拒绝")
    void push_blockedTarget_isRejectedByGate() {
        final HttpConfigPushService service = serviceFor("http://169.254.169.254:8000");

        final var result = service.pushToService("chat", Map.of("key", "value")).block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("not allowed"),
                () -> "应由 SSRF 门闩拒绝、而不是连接失败，实际: " + result.message());
    }

    private static HttpConfigPushService serviceFor(final String baseUrl) {
        final ServiceConfigRepository configRepository = mock(ServiceConfigRepository.class);
        final ServiceInstanceRepository instanceRepository = mock(ServiceInstanceRepository.class);

        final ServiceConfigEntity config = new ServiceConfigEntity();
        config.setId(1L);
        config.setServiceType("chat");
        config.setIsLatest(true);

        final ServiceInstanceEntity instance = new ServiceInstanceEntity();
        instance.setId(1L);
        instance.setServiceConfigId(1L);
        instance.setBaseUrl(baseUrl);
        instance.setStatus("ACTIVE");

        when(configRepository.findFirstByServiceTypeAndIsLatestTrue("chat")).thenReturn(Optional.of(config));
        when(instanceRepository.findByServiceConfigId(1L)).thenReturn(List.of(instance));

        return new HttpConfigPushService(configRepository, instanceRepository, new WebClientPool());
    }
}
