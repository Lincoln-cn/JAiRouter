package org.unreal.modelrouter.common.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.config.event.AuditLogEvent;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 控制台写操作审计过滤器（#1160）
 *
 * <p>核心断言只有一条：**写操作必须发出审计事件、读操作必须不发**。
 * 这条链路此前断在「没有发布点」，一旦这里回归，管理操作页的操作人会静默退回
 * 「未知」，而不会有任何报错 —— 所以用测试把它钉住。</p>
 */
class ConsoleAuditWebFilterTest {

    @Test
    @DisplayName("AUDIT-001 控制台写操作发布一条 serviceConfig 审计事件")
    void writeOperationPublishesAuditEvent() {
        final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        final ConsoleAuditWebFilter filter = new ConsoleAuditWebFilter(publisher);

        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/config/health").build());

        filter.filter(exchange, passthrough()).block();

        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        assertTrue(captor.getValue() instanceof AuditLogEvent, "应发布 AuditLogEvent");
        assertEquals("health.config.update", ((AuditLogEvent) captor.getValue()).operation());
    }

    @Test
    @DisplayName("AUDIT-002 读操作不发布审计事件")
    void readOperationDoesNotPublish() {
        final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        final ConsoleAuditWebFilter filter = new ConsoleAuditWebFilter(publisher);

        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/config/health").build());

        filter.filter(exchange, passthrough()).block();

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AUDIT-003 非控制台路径的写操作不发布审计事件")
    void nonConsolePathDoesNotPublish() {
        final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        final ConsoleAuditWebFilter filter = new ConsoleAuditWebFilter(publisher);

        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/v1/chat/completions").build());

        filter.filter(exchange, passthrough()).block();

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AUDIT-004 未登记操作名的写操作不发布审计事件")
    void unregisteredOperationDoesNotPublish() {
        final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        final ConsoleAuditWebFilter filter = new ConsoleAuditWebFilter(publisher);

        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/unknown/thing").build());

        filter.filter(exchange, passthrough()).block();

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private static WebFilterChain passthrough() {
        return exchange -> Mono.empty();
    }
}
