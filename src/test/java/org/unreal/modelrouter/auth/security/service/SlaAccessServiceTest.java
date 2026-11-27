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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SlaBootstrapTokenEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SlaRefreshTokenEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SlaServiceAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaBootstrapTokenRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaRefreshTokenRepository;
import org.unreal.modelrouter.persistence.jpa.repository.SlaServiceAccountRepository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private SlaBootstrapTokenEntity revokedBootstrapRow() {
        final SlaBootstrapTokenEntity row = bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null);
        row.setRevokedAt(LocalDateTime.now());
        return row;
    }

    private JwtAccountEntity jwtSubject(final boolean enabled, final boolean deleted) {
        return JwtAccountEntity.builder()
                .username(SUBJECT)
                .roles("[\"SLA_READONLY\"]")
                .enabled(enabled)
                .deleted(deleted)
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

        @Test
        @DisplayName("被吊销的接入令牌不能再用（#1210）")
        void rejectsRevoked() {
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(revokedBootstrapRow()));

            ApiException error = assertThrows(ApiException.class,
                    () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已被吊销"));
            verify(accountManager, never()).issueTokenForAccount(anyString());
        }

        @Test
        @DisplayName("并发消费同一令牌：乐观锁冲突翻译成「已被使用」（#1221）")
        void rejectsConcurrentDoubleSpend() {
            SlaBootstrapTokenEntity row = bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null);
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(row));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(bootstrapTokenRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(SlaBootstrapTokenEntity.class, "bt-1"));

            ApiException error = assertThrows(ApiException.class,
                    () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("已被使用"));
            // 只有一方能换到凭据：冲突方不得再落一条刷新凭据
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("签发新接入令牌时恢复被停用的主体（#1212）")
        void reactivatesSuspendedSubject() {
            JwtAccountEntity subject = jwtSubject(false, false);
            when(jwtAccountRepository.findByUsername(SUBJECT)).thenReturn(Optional.of(subject));
            when(bootstrapTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_BOOTSTRAP)))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null)));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            assertNotNull(service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());

            assertTrue(Boolean.TRUE.equals(subject.getEnabled()), "重新接入应恢复主体");
            verify(jwtAccountRepository).save(subject);
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

        @Test
        @DisplayName("主体被停用时刷新被拒且不自行复活（#1212）")
        void rejectsSuspendedSubject() {
            JwtAccountEntity subject = jwtSubject(false, false);
            when(refreshTokenRepository.findByTokenHash(SlaAccessService.sha256(PLAIN_REFRESH)))
                    .thenReturn(Optional.of(refreshRow(LocalDateTime.now().plusDays(30), null)));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(jwtAccountRepository.findByUsername(SUBJECT)).thenReturn(Optional.of(subject));

            ApiException error = assertThrows(ApiException.class,
                    () -> service.refresh(PLAIN_REFRESH, "10.0.0.7").block());

            assertTrue(error.getMessage().contains("主体已停用"));
            assertFalse(Boolean.TRUE.equals(subject.getEnabled()), "刷新不得自行恢复主体");
            verify(accountManager, never()).issueTokenForAccount(anyString());
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
        @DisplayName("新建账号写一条 SLA_ACCOUNT_CREATED，主体记操作员（#1220）")
        void createsAccountWithAudit() {
            service.createAccount("builder", "SLA_READONLY", "alice");

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(auditCaptor.capture());
            assertEquals(AuditEventType.SLA_ACCOUNT_CREATED.name(), auditCaptor.getValue().getEventType());
            assertEquals("alice", auditCaptor.getValue().getUserId());
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
        @DisplayName("吊销同时作废未消费的接入令牌（#1210）")
        void revokesPendingBootstrapTokens() {
            SlaBootstrapTokenEntity pending = bootstrapRow(LocalDateTime.now().plusMinutes(10), null, null);
            SlaBootstrapTokenEntity consumed = bootstrapRow(LocalDateTime.now().plusMinutes(10), LocalDateTime.now(), null);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(bootstrapTokenRepository.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(pending, consumed));

            service.revokeAll(ACCOUNT_ID, "admin");

            assertNotNull(pending.getRevokedAt(), "未消费的接入令牌必须被作废");
            assertTrue(consumed.getRevokedAt() == null, "已消费的令牌不需要再打吊销标记");
        }

        @Test
        @DisplayName("吊销同时停用 jwt 主体，已签发访问令牌随即失效（#1211）")
        void revokeSuspendsJwtSubject() {
            JwtAccountEntity subject = jwtSubject(true, false);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(jwtAccountRepository.findByUsername(SUBJECT)).thenReturn(Optional.of(subject));

            service.revokeAll(ACCOUNT_ID, "admin");

            assertTrue(Boolean.FALSE.equals(subject.getEnabled()), "吊销应停用主体");
            verify(jwtAccountRepository).save(subject);
        }

        @Test
        @DisplayName("禁用账号：置 enabled=false、吊销凭据、停用主体并写审计（#1212）")
        void disablesAccount() {
            SlaServiceAccountEntity account = account(true, "SLA_READONLY");
            SlaRefreshTokenEntity active = refreshRow(LocalDateTime.now().plusDays(30), null);
            JwtAccountEntity subject = jwtSubject(true, false);
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(refreshTokenRepository.findByAccountIdAndRevokedAtIsNull(ACCOUNT_ID)).thenReturn(List.of(active));
            when(jwtAccountRepository.findByUsername(SUBJECT)).thenReturn(Optional.of(subject));

            SlaAccessService.AccountView view = service.setAccountEnabled(ACCOUNT_ID, false, "admin");

            assertFalse(view.enabled());
            assertFalse(Boolean.TRUE.equals(account.getEnabled()));
            assertNotNull(active.getRevokedAt());
            assertTrue(Boolean.FALSE.equals(subject.getEnabled()));

            ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(auditCaptor.capture());
            assertEquals(AuditEventType.SLA_ACCOUNT_DISABLED.name(), auditCaptor.getValue().getEventType());
            assertEquals("admin", auditCaptor.getValue().getUserId(), "账号状态类事件记操作员，不记 sla: 主体");
        }

        @Test
        @DisplayName("重复启用是幂等的：不重复吊销、不写审计")
        void enableIsIdempotent() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            SlaAccessService.AccountView view = service.setAccountEnabled(ACCOUNT_ID, true, "admin");

            assertTrue(view.enabled());
            verify(securityAuditService, never()).recordEvent(any());
            verify(refreshTokenRepository, never()).findByAccountIdAndRevokedAtIsNull(anyString());
        }

        @Test
        @DisplayName("列表带出未消费令牌数与有效凭据数，计数来自一次批量聚合")
        void listsAccounts() {
            when(accountRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(new PageImpl<>(List.of(account(true, "SLA_READONLY")),
                            PageRequest.of(0, 20), 1));
            when(refreshTokenRepository.countActiveByAccountIds(anyCollection()))
                    .thenReturn(List.<Object[]>of(new Object[] {ACCOUNT_ID, 2L}));
            when(bootstrapTokenRepository.countUnusedByAccountIds(anyCollection(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] {ACCOUNT_ID, 1L}));

            PagedResult<SlaAccessService.AccountView> views = service.listAccounts(0, 20, null);

            assertEquals(1, views.getContent().size());
            assertEquals(2L, views.getContent().get(0).activeRefreshTokens());
            assertEquals(1L, views.getContent().get(0).unusedBootstrapTokens());
            assertEquals("builder", views.getContent().get(0).name());
            assertEquals(1L, views.getTotalElements());
            // 聚合各查一次，不再按账号逐个 count（#1219）
            verify(refreshTokenRepository, never()).countByAccountIdAndRevokedAtIsNull(anyString());
            verify(bootstrapTokenRepository, never())
                    .countByAccountIdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any());
        }

        @Test
        @DisplayName("带关键字时走名称模糊查询，页码与条数原样透传给分页对象")
        void listsAccountsWithKeyword() {
            when(accountRepository.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(eq("buil"), any()))
                    .thenReturn(new PageImpl<>(List.of(account(true, "SLA_READONLY")),
                            PageRequest.of(1, 5), 6));
            when(refreshTokenRepository.countActiveByAccountIds(anyCollection())).thenReturn(List.of());
            when(bootstrapTokenRepository.countUnusedByAccountIds(anyCollection(), any())).thenReturn(List.of());

            PagedResult<SlaAccessService.AccountView> views = service.listAccounts(1, 5, "  buil ");

            assertEquals(1, views.getPage());
            assertEquals(5, views.getSize());
            assertEquals(6L, views.getTotalElements());
            assertEquals(0L, views.getContent().get(0).activeRefreshTokens(), "聚合没返回时按 0 处理");
            assertEquals(0L, views.getContent().get(0).unusedBootstrapTokens());
        }

        @Test
        @DisplayName("账号为空页时不再发起聚合查询")
        void listAccountsSkipsAggregationOnEmptyPage() {
            when(accountRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PagedResult<SlaAccessService.AccountView> views = service.listAccounts(0, 20, null);

            assertTrue(views.getContent().isEmpty());
            verify(refreshTokenRepository, never()).countActiveByAccountIds(anyCollection());
            verify(bootstrapTokenRepository, never()).countUnusedByAccountIds(anyCollection(), any());
        }

        @Test
        @DisplayName("汇总指标取全量计数，不受分页影响")
        void summarizeCountsEverything() {
            when(accountRepository.count()).thenReturn(37L);
            when(accountRepository.countByEnabledTrue()).thenReturn(29L);
            when(refreshTokenRepository.countByRevokedAtIsNull()).thenReturn(12L);
            when(bootstrapTokenRepository
                    .countByUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(any(LocalDateTime.class)))
                    .thenReturn(4L);

            SlaAccessService.AccountSummary summary = service.summarize();

            assertEquals(37L, summary.accounts());
            assertEquals(29L, summary.enabled());
            assertEquals(12L, summary.refreshTokens());
            assertEquals(4L, summary.pendingTokens());
        }

        @Test
        @DisplayName("删除账号会带走凭据、令牌与 jwt 主体")
        void deletesEverything() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(refreshTokenRepository.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(refreshRow(LocalDateTime.now(), null)));
            when(bootstrapTokenRepository.findByAccountId(ACCOUNT_ID))
                    .thenReturn(List.of(bootstrapRow(LocalDateTime.now(), null, null)));
            JwtAccountEntity subject = jwtSubject(true, false);
            when(jwtAccountRepository.findByUsername(SUBJECT)).thenReturn(Optional.of(subject));

            service.deleteAccount(ACCOUNT_ID, "admin");

            verify(refreshTokenRepository).deleteAll(any());
            verify(bootstrapTokenRepository).deleteAll(any());
            verify(accountRepository).delete(any(SlaServiceAccountEntity.class));
            verify(jwtAccountRepository).delete(subject);
        }
    }

    @Nested
    @DisplayName("凭据策略：有效期上限、来源绑定与回收（#1213 #1214 #1217 #1218）")
    class CredentialPolicyTests {

        @Test
        @DisplayName("签发时把超过上限的 TTL 夹取到 60 分钟（#1213）")
        void clampsBootstrapTtl() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            SlaAccessService.IssueResult result = service.issueBootstrap(ACCOUNT_ID, 1440, null, "admin");

            assertEquals(Duration.ofMinutes(60).toSeconds(), result.expiresIn());
        }

        @Test
        @DisplayName("签发时拒绝非法 allowedIp（#1213）")
        void rejectsMalformedAllowedIp() {
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            ApiException ex = assertThrows(ApiException.class,
                    () -> service.issueBootstrap(ACCOUNT_ID, 15, "10.0.0.0/33", "admin"));

            assertEquals(ErrorCode.INVALID_REQUEST.code(), ex.getErrorCode());
        }

        @Test
        @DisplayName("绑定 CIDR 时按网段匹配，而非字符串全等（#1213）")
        void matchesBoundCidr() {
            when(bootstrapTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, "10.0.0.0/24")));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            assertNotNull(service.bootstrap(PLAIN_BOOTSTRAP, "10.0.0.7").block());
        }

        @Test
        @DisplayName("来源 IP 落在网段之外时拒绝（#1213）")
        void rejectsClientOutsideCidr() {
            when(bootstrapTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, "10.0.0.0/24")));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            ApiException ex = assertThrows(ApiException.class,
                    () -> service.bootstrap(PLAIN_BOOTSTRAP, "10.0.1.7").block());

            assertEquals(ErrorCode.FORBIDDEN.code(), ex.getErrorCode());
        }

        @Test
        @DisplayName("IPv6 绑定按规范化形式比较，压缩写法不再误判（#1213）")
        void matchesEquivalentIpv6() {
            when(bootstrapTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, "2001:0db8::1")));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            assertNotNull(service.bootstrap(PLAIN_BOOTSTRAP, "2001:db8:0:0:0:0:0:1").block());
        }

        @Test
        @DisplayName("刷新凭据缺失时返回 401，而不是把空指针变成 500（#1214）")
        void refreshWithoutTokenIsUnauthorized() {
            ApiException ex = assertThrows(ApiException.class, () -> service.refresh(null, "1.2.3.4").block());

            assertEquals(ErrorCode.TOKEN_REQUIRED.code(), ex.getErrorCode());
        }

        @Test
        @DisplayName("超过绝对有效期的刷新凭据被拒，滚动续期不能无限延长（#1214）")
        void rejectsPastAbsoluteLifetime() {
            SlaRefreshTokenEntity row = refreshRow(LocalDateTime.now().plusDays(30), null);
            row.setIssuedAt(LocalDateTime.now().minusDays(400));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(row));

            ApiException ex = assertThrows(ApiException.class,
                    () -> service.refresh(PLAIN_REFRESH, "1.2.3.4").block());

            assertEquals(ErrorCode.TOKEN_REQUIRED.code(), ex.getErrorCode());
            assertTrue(ex.getMessage().contains("绝对有效期"));
        }

        @Test
        @DisplayName("续期不能越过绝对有效期（#1214）")
        void capsRollingExpiry() {
            LocalDateTime absolute = LocalDateTime.now().plusDays(10);
            SlaRefreshTokenEntity row = refreshRow(LocalDateTime.now().plusDays(30), null);
            row.setAbsoluteExpiresAt(absolute);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(row));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            service.refresh(PLAIN_REFRESH, "1.2.3.4").block();

            assertEquals(absolute, row.getExpiresAt());
            assertEquals(absolute, row.getAbsoluteExpiresAt());
        }

        @Test
        @DisplayName("来源 IP 变更时记录审计但不阻断刷新（#1214）")
        void recordsIpChange() {
            SlaRefreshTokenEntity row = refreshRow(LocalDateTime.now().plusDays(30), null);
            row.setClientIp("1.1.1.1");
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(row));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));

            assertNotNull(service.refresh(PLAIN_REFRESH, "2.2.2.2").block());

            ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService, atLeastOnce()).recordEvent(captor.capture());
            assertTrue(captor.getAllValues().stream()
                    .anyMatch(event -> "REFRESH_IP_CHANGED".equals(event.getAction())), "IP 变更必须留审计痕迹");
            assertEquals("2.2.2.2", row.getClientIp());
        }

        @Test
        @DisplayName("签发访问令牌失败时不留下刷新凭据行（#1217）")
        void doesNotPersistRefreshTokenWhenSigningFails() {
            when(bootstrapTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(bootstrapRow(LocalDateTime.now().plusMinutes(5), null, null)));
            when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "SLA_READONLY")));
            when(accountManager.issueTokenForAccount(SUBJECT)).thenReturn(Mono.error(new IllegalStateException("boom")));

            assertThrows(IllegalStateException.class,
                    () -> service.bootstrap(PLAIN_BOOTSTRAP, "1.2.3.4").block());

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("回收任务按保留期删除两类凭据并回报行数（#1218）")
        void cleansUpExpiredCredentials() {
            when(refreshTokenRepository.deleteStale(any())).thenReturn(3);
            when(bootstrapTokenRepository.deleteStale(any())).thenReturn(5);

            SlaAccessService.CleanupResult result = service.cleanupExpiredCredentials(30);

            assertEquals(3, result.refreshTokens());
            assertEquals(5, result.bootstrapTokens());
            verify(refreshTokenRepository).deleteStale(any());
            verify(bootstrapTokenRepository).deleteStale(any());
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
