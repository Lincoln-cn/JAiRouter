package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.persistence.jpa.entity.SlaBootstrapTokenEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SlaRefreshTokenEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SlaServiceAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaBootstrapTokenRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaRefreshTokenRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaServiceAccountRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaAccessService} 的行为测试（issue #1174）。
 *
 * <p>重点守四件事：一次性令牌只能用一次；刷新凭据被吊销后立刻失效；库里只存哈希；
 * 每一次成功的凭据动作都写审计。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlaAccessServiceTest {

    @Mock
    private SlaServiceAccountRepository accountRepository;
    @Mock
    private SlaBootstrapTokenRepository bootstrapTokenRepository;
    @Mock
    private SlaRefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtAccountRepository jwtAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountManager accountManager;
    @Mock
    private SecurityAuditService securityAuditService;


    private SlaAccessService service;

    private static final String ACCOUNT_ID = "acc-1";
    private static final String SUBJECT = "sla:builder";
    private static final String PLAIN_BOOTSTRAP = "bootstrap-plain-token";
    private static final String PLAIN_REFRESH = "refresh-plain-token";

    @BeforeEach
    void setUp() {
        service = new SlaAccessService(accountRepository, bootstrapTokenRepository, refreshTokenRepository,
                jwtAccountRepository, passwordEncoder, accountManager, securityAuditService);
        when(accountManager.issueTokenForAccount(SUBJECT)).thenReturn(Mono.just("access-jwt"));
        when(accountManager.accessTokenExpirationMinutes()).thenReturn(60L);
        when(securityAuditService.recordEvent(any())).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("{noop}unusable");
        when(jwtAccountRepository.findByUsername(anyString())).thenReturn(Optional.empty());
    }

    private SlaServiceAccountEntity account(final boolean enabled, final String template) {
        return SlaServiceAccountEntity.builder()
                .accountId(ACCOUNT_ID)
                .name("builder")
                .roleTemplate(template)
                .enabled(enabled)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SlaBootstrapTokenEntity bootstrapRow(final LocalDateTime expiresAt, final LocalDateTime usedAt,
                                                final String allowedIp) {
        return SlaBootstrapTokenEntity.builder()
                .tokenId("bt-1")
                .accountId(ACCOUNT_ID)
                .tokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP))
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .allowedIp(allowedIp)
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    private SlaRefreshTokenEntity refreshRow(final LocalDateTime expiresAt, final LocalDateTime revokedAt) {
        return SlaRefreshTokenEntity.builder()
                .tokenId("rt-1")
                .accountId(ACCOUNT_ID)
                .tokenHash(SlaAccessService.sha256(PLAIN_REFRESH))
                .issuedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    @Nested
    @DisplayName("bootstrap：一次性接入令牌换长期凭据")
    class BootstrapTests {

        @Test
        @DisplayName("成功：标记已用、建刷新凭据、签发访问令牌、写消费审计")
        void happyPath() {
            SlaBootstrapTokenEntity row = bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null);
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(row));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            SlaAccessService.BootstrapResult result = service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block();

            assertNotNull(result);
            assertEquals("access-jwt", result.accessToken());
            assertEquals(SUBJECT, result.subject());
            assertNotNull(result.refreshToken());
            assertEquals(3600L, result.expiresIn());
            assertNotNull(row.getUsedAt(), "令牌必须被标记为已使用");

            // 刷新凭据只存哈希：落库的 tokenHash 必须等于明文摘要，而不是明文本身
            ArgumentCaptor<SlaRefreshTokenEntity> refreshCaptor = ArgumentCaptor.forClass(SlaRefreshTokenEntity.class);
            verify(refreshTokenRepository).save(refreshCaptor.capture());
            assertEquals(SlaAccessService.sha256(result.refreshToken()), refreshCaptor.getValue().getTokenHash());
            assertFalse(refreshCaptor.getValue().getTokenHash().contains(PLAIN_REFRESH));

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService, atLeastOnce()).recordEvent(auditCaptor.capture());
            assertTrue(auditCaptor.getAllValues().stream()
                    .anyMatch(event -> AuditEventType.SLA_BOOTSTRAP_CONSUMED.name().equals(event.getEventType())
                            && SUBJECT.equals(event.getUserId())));
        }

        @Test
        @DisplayName("已消费的令牌再用一次被拒")
        void rejectsReuse() {
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), LocalDateTime.now(), null)));

            ApiException error = assertThrows(ApiException.class, () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已被使用"));
            verify(refreshTokenRepository, never()).save(any());
            verify(securityAuditService, never()).recordEvent(any());
        }

        @Test
        @DisplayName("过期令牌被拒")
        void rejectsExpired() {
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().minusSeconds(1), null, null)));

            ApiException error = assertThrows(ApiException.class, () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已过期"));
            verify(accountManager, never()).issueTokenForAccount(anyString());
        }

        @Test
        @DisplayName("绑定了 IP 的令牌从别处使用被拒")
        void rejectsForeignIp() {
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, "10.0.0.7")));

            ApiException error = assertThrows(ApiException.class, () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.9").block());

            assertTrue(error.getMessage().contains("绑定来源 IP"));
            verify(accountManager, never()).issueTokenForAccount(anyString());
        }

        @Test
        @DisplayName("账号被禁用时拒绝换取")
        void rejectsDisabledAccount() {
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null)));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(false, "SLA_READONLY")));

            ApiException error = assertThrows(ApiException.class, () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已禁用"));
        }

        @Test
        @DisplayName("未知令牌被拒")
        void rejectsUnknownToken() {
            when(bootstrapTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            ApiException error = assertThrows(ApiException.class, () -> service.bootstrap("nope", "10.0.0.7").block());

            assertTrue(error.getMessage().contains("无效"));
        }
    }

    @Nested
    @DisplayName("refresh：刷新凭据换访问令牌")
    class RefreshTests {

        @Test
        @DisplayName("成功：滚动续期并写刷新审计")
        void happyPath() {
            SlaRefreshTokenEntity row = refreshRow(LocalDateTime.now().plusDays(30), null);
            when(refreshTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_REFRESH)))
                    .thenReturn(Optional.of(row));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            SlaAccessService.RefreshResult result = service.refresh(PLAIN_REFRESH, "10.0.0.7").block();

            assertNotNull(result);
            assertEquals("access-jwt", result.accessToken());
            assertEquals("10.0.0.7", row.getClientIp());
            assertNotNull(row.getLastUsedAt());
            assertTrue(row.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)), "刷新应滚动延长有效期");

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(auditCaptor.capture());
            assertEquals(AuditEventType.SLA_REFRESH.name(), auditCaptor.getValue().getEventType());
        }

        @Test
        @DisplayName("已吊销的凭据立即失效")
        void rejectsRevoked() {
            when(refreshTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_REFRESH)))
                    .thenReturn(Optional.of(refreshRow(LocalDateTime.now().plusDays(30), LocalDateTime.now())));

            ApiException error = assertThrows(ApiException.class, () -> service.refresh(PLAIN_REFRESH, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已被吊销"));
            verify(accountManager, never()).issueTokenForAccount(anyString());
        }

        @Test
        @DisplayName("过期的凭据被拒")
        void rejectsExpired() {
            when(refreshTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_REFRESH)))
                    .thenReturn(Optional.of(refreshRow(LocalDateTime.now().minusDays(1), null)));

            ApiException error = assertThrows(ApiException.class, () -> service.refresh(PLAIN_REFRESH, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已过期"));
        }
    }

    @Nested
    @DisplayName("管理端：账号与令牌签发")
    class AdminTests {

        @Test
        @DisplayName("账号名重复被拒")
        void rejectsDuplicateName() {
            when(accountRepository.existsByName("dup")).thenReturn(true);

            ApiException error = assertThrows(ApiException.class,
                    () -> service.createAccount("dup", "SLA_READONLY", "admin"));

            assertTrue(error.getMessage().contains("已存在"));
        }

        @Test
        @DisplayName("不支持的角色模板被拒")
        void rejectsUnknownTemplate() {
            ApiException error = assertThrows(ApiException.class,
                    () -> service.createAccount("builder", "SLA_ROOT", "admin"));

            assertTrue(error.getMessage().contains("不支持的角色模板"));
        }

        @Test
        @DisplayName("签发一次性令牌：明文只回一次、库里存哈希、写签发审计")
        void issuesBootstrapToken() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            SlaAccessService.IssueResult result = service.issueBootstrap(ACCOUNT_ID, 30, "10.0.0.7", "admin");

            assertNotNull(result.bootstrapToken());
            assertEquals(1800L, result.expiresIn());

            ArgumentCaptor<SlaBootstrapTokenEntity> captor = ArgumentCaptor.forClass(SlaBootstrapTokenEntity.class);
            verify(bootstrapTokenRepository).save(captor.capture());
            assertEquals(SlaAccessService.sha256(result.bootstrapToken()), captor.getValue().getTokenHash());
            assertEquals("10.0.0.7", captor.getValue().getAllowedIp());

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(auditCaptor.capture());
            assertEquals(AuditEventType.SLA_BOOTSTRAP_ISSUED.name(), auditCaptor.getValue().getEventType());
        }

        @Test
        @DisplayName("吊销：置 revokedAt 并写吊销审计")
        void revokesAll() {
            SlaRefreshTokenEntity first = refreshRow(LocalDateTime.now().plusDays(30), null);
            SlaRefreshTokenEntity second = refreshRow(LocalDateTime.now().plusDays(30), null);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_OPERATOR")));
            when(refreshTokenRepository.findByAccountIdAndRevokedAtIsNull(ACCOUNT_ID))
                    .thenReturn(List.of(first, second));

            int revoked = service.revokeAll(ACCOUNT_ID, "admin");

            assertEquals(2, revoked);
            assertNotNull(first.getRevokedAt());
            assertNotNull(second.getRevokedAt());
            verify(refreshTokenRepository).saveAll(any());

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(auditCaptor.capture());
            assertEquals(AuditEventType.SLA_CREDENTIAL_REVOKED.name(), auditCaptor.getValue().getEventType());
        }

        @Test
        @DisplayName("列表带出未消费令牌数与有效凭据数")
        void listsAccounts() {
            when(accountRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(account(true, "SLA_READONLY")));
            when(refreshTokenRepository.countByAccountIdAndRevokedAtIsNull(ACCOUNT_ID)).thenReturn(2L);
            when(bootstrapTokenRepository.countByAccountIdAndUsedAtIsNull(ACCOUNT_ID)).thenReturn(1L);

            List<SlaAccessService.AccountView> views = service.listAccounts();

            assertEquals(1, views.size());
            assertEquals(2L, views.get(0).activeRefreshTokens());
            assertEquals(1L, views.get(0).unusedBootstrapTokens());
            assertEquals("builder", views.get(0).name());
        }

        @Test
        @DisplayName("删除账号会带走凭据、令牌与 jwt 主体")
        void deletesEverything() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(refreshTokenRepository.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(refreshRow(LocalDateTime.now(), null)));
            when(bootstrapTokenRepository.findByAccountId(ACCOUNT_ID))
                    .thenReturn(List.of(bootstrapRow(LocalDateTime.now(), null, null)));

            service.deleteAccount(ACCOUNT_ID, "admin");

            verify(refreshTokenRepository).deleteAll(any());
            verify(bootstrapTokenRepository).deleteAll(any());
            verify(accountRepository).delete(any(SlaServiceAccountEntity.class));
            verify(jwtAccountRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("摘要稳定且不泄漏明文")
    void hashIsStableAndOpaque() {
        String first = SlaAccessService.sha256("abc");
        String second = SlaAccessService.sha256("abc");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertFalse(first.contains("abc"));
        assertFalse(SlaAccessService.sha256("abd").equals(first));
        assertEquals("sla:", SlaAccessService.SUBJECT_PREFIX);
    }
}
