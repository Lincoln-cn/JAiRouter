package org.unreal.modelrouter.auth.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.persistence.jpa.entity.LoginGuardConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.LoginGuardConfigRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginGuardConfigService} 的加载与更新语义（issue #1176）.
 *
 * <p>守的是「yml 只给首次初始值、之后以 DB 为准」这条口径：表空时把默认值落库，
 * 表里已有行时**以库为准**（不能又被 yml 覆盖回去），改配置要同时做到「写库、热改、审计」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginGuardConfigServiceTest {

    private static final int PRINCIPAL = 5;

    private static final int PER_IP = 30;

    private static final int WINDOW = 15;

    private static final int LOCK = 15;

    @Mock
    private LoginGuardConfigRepository repository;

    @Mock
    private ExtendedSecurityAuditService auditService;

    private LoginBruteForceGuard guard;

    private LoginGuardConfigService service;

    @BeforeEach
    void setUp() {
        guard = new LoginBruteForceGuard(auditService);
        guard.applySettings(new LoginGuardSettings(PRINCIPAL, PER_IP, WINDOW, LOCK));
        service = new LoginGuardConfigService(repository, guard, auditService);
        when(auditService.auditSecurityEvent(anyString(), anyString(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("表空时把当前默认值落库，updatedBy 记为 system")
    void seedsWhenTableEmpty() {
        when(repository.findAll()).thenReturn(List.of());

        service.seedIfEmpty();

        final ArgumentCaptor<LoginGuardConfigEntity> captor = ArgumentCaptor.forClass(LoginGuardConfigEntity.class);
        verify(repository).save(captor.capture());
        final LoginGuardConfigEntity saved = captor.getValue();
        assertEquals(LoginGuardConfigEntity.DEFAULT_ID, saved.getConfigId());
        assertEquals(PRINCIPAL, saved.getMaxFailuresPerPrincipal());
        assertEquals(PER_IP, saved.getMaxFailuresPerIp());
        assertEquals(WINDOW, saved.getWindowMinutes());
        assertEquals(LOCK, saved.getLockMinutes());
        assertEquals("system", saved.getUpdatedBy());
        assertTrue(saved.getUpdatedAt() != null);
    }

    @Test
    @DisplayName("表里已有行时以库为准：guard 的生效参数被数据库值覆盖")
    void databaseWinsOverYamlDefaults() {
        when(repository.findAll()).thenReturn(List.of(entity(20, 100, 5, 5)));

        service.seedIfEmpty();

        final LoginGuardSettings applied = guard.settings();
        assertEquals(20, applied.maxFailuresPerPrincipal());
        assertEquals(100, applied.maxFailuresPerIp());
        assertEquals(5, applied.windowMinutes());
        assertEquals(5, applied.lockMinutes());
    }

    @Test
    @DisplayName("current() 返回生效值与最后修改人")
    void currentReturnsEffectiveValues() {
        when(repository.findAll()).thenReturn(List.of(entity(20, 100, 5, 5)));

        final LoginGuardConfigService.ConfigView view = service.current();

        assertEquals(20, view.maxFailuresPerPrincipal());
        assertEquals("tester", view.updatedBy());
    }

    @Test
    @DisplayName("update() 写库 + 立即热改 + 写 LOGIN_GUARD_CONFIG_CHANGED 审计")
    void updatePersistsHotAppliesAndAudits() {
        when(repository.findAll()).thenReturn(List.of(entity(PRINCIPAL, PER_IP, WINDOW, LOCK)));

        service.update(new LoginGuardSettings(3, 10, 2, 1), "operator");

        final ArgumentCaptor<LoginGuardConfigEntity> captor = ArgumentCaptor.forClass(LoginGuardConfigEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(3, captor.getValue().getMaxFailuresPerPrincipal());
        assertEquals("operator", captor.getValue().getUpdatedBy());

        assertEquals(3, guard.settings().maxFailuresPerPrincipal());
        assertEquals(1, guard.settings().lockMinutes());

        verify(auditService).auditSecurityEvent(
                eq(AuditEventType.LOGIN_GUARD_CONFIG_CHANGED.name()), anyString(), eq("operator"), any());
    }

    @Test
    @DisplayName("update() 在表空时新建默认行为 config_id=default")
    void updateCreatesRowWhenMissing() {
        when(repository.findAll()).thenReturn(List.of());

        service.update(new LoginGuardSettings(4, 40, 10, 10), "operator");

        final ArgumentCaptor<LoginGuardConfigEntity> captor = ArgumentCaptor.forClass(LoginGuardConfigEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(LoginGuardConfigEntity.DEFAULT_ID, captor.getValue().getConfigId());
    }

    @Test
    @DisplayName("unlock() 清计数并写 LOGIN_GUARD_UNLOCKED 审计")
    void unlockClearsAndAudits() {
        guard.applySettings(new LoginGuardSettings(1, 30, 15, 15));
        guard.recordFailure("alice", "127.0.0.1");

        final int cleared = service.unlock("alice", "127.0.0.1", "operator");

        assertEquals(1, cleared);
        assertTrue(guard.snapshotLocked().isEmpty());
        verify(auditService).auditSecurityEvent(
                eq(AuditEventType.LOGIN_GUARD_UNLOCKED.name()), anyString(), eq("operator"), any());
    }

    @Test
    @DisplayName("validated() 拒绝缺字段与非法取值（400）")
    void validatedRejectsInvalidInput() {
        assertThrows(ApiException.class, () -> LoginGuardConfigService.validated(null, 30, 15, 15));
        assertThrows(ApiException.class, () -> LoginGuardConfigService.validated(5, 30, 15, null));

        final ApiException zeroThreshold = assertThrows(ApiException.class,
                () -> LoginGuardConfigService.validated(0, 30, 15, 15));
        assertInstanceOf(ApiException.class, zeroThreshold);
        assertEquals(HttpStatus.BAD_REQUEST, zeroThreshold.getStatus());
        assertTrue(zeroThreshold.getMessage().contains("maxFailuresPerPrincipal"));

        final LoginGuardSettings ok = LoginGuardConfigService.validated(5, 30, 0, 0);
        assertEquals(0, ok.windowMinutes());
        assertEquals(0, ok.lockMinutes());
    }

    @Test
    @DisplayName("加载失败不阻断启动：保持 YAML 默认值，后续请求会重试")
    void loadFailureKeepsYamlDefaults() {
        when(repository.findAll()).thenThrow(new IllegalStateException("db down"));

        service.seedIfEmpty();

        assertEquals(PRINCIPAL, guard.settings().maxFailuresPerPrincipal());
    }

    private static LoginGuardConfigEntity entity(final int principal, final int perIp, final int window,
            final int lock) {
        return LoginGuardConfigEntity.builder()
                .configId(LoginGuardConfigEntity.DEFAULT_ID)
                .maxFailuresPerPrincipal(principal)
                .maxFailuresPerIp(perIp)
                .windowMinutes(window)
                .lockMinutes(lock)
                .updatedBy("tester")
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
