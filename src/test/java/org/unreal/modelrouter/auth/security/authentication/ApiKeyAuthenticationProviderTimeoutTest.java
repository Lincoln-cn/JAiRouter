package org.unreal.modelrouter.auth.security.authentication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * R2-P1-02：API Key 认证 block 必须带超时，避免 EventLoop/认证线程无限挂起。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyAuthenticationProvider block 超时")
class ApiKeyAuthenticationProviderTimeoutTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityAuditService auditService;

    @Test
    @DisplayName("下游 validateApiKey 永不完成时，认证在超时后失败（不永久阻塞）")
    void authenticate_timesOutInsteadOfHanging() {
        when(apiKeyService.validateApiKey(anyString())).thenReturn(Mono.never());
        ApiKeyAuthenticationProvider provider =
                new ApiKeyAuthenticationProvider(apiKeyService, eventPublisher, auditService);

        ApiKeyAuthentication auth = new ApiKeyAuthentication("sk-hanging-key-value");
        long start = System.nanoTime();
        Exception ex = assertThrows(Exception.class, () -> provider.authenticate(auth));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 10_000L, "必须在超时内返回，而不是永久阻塞: " + elapsedMs + "ms");
        assertTrue(ex != null);
    }

    @Test
    @DisplayName("超时常量存在且合理（1–30s）")
    void timeoutConstant_isReasonable() {
        Duration timeout = ApiKeyAuthenticationProvider.AUTH_TIMEOUT;
        assertTrue(timeout.toSeconds() >= 1 && timeout.toSeconds() <= 30,
                "AUTH_TIMEOUT 应在 1-30s: " + timeout);
    }
}
