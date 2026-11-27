package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
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
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SLA 接入服务（issue #1174）.
 *
 * <p>把「外部巡检工具怎么拿到凭据」收敛成一条链路：
 * 管理员签发<b>一次性接入令牌</b>（15 分钟、可绑 IP、库里只存哈希）→ SLA 部署时用
 * {@code POST /api/auth/sla/bootstrap} 换成<b>访问令牌 + 长期刷新凭据</b> → 之后每 45~60 分钟
 * 用 {@code POST /api/auth/sla/refresh} 换新的访问令牌。整条链路上没有任何密码。</p>
 *
 * <p><b>为什么不复用 API Key</b>：{@code ApiKeyAuthentication} 把 permissions 映射成
 * {@code ROLE_<服务类型>}，而控制面判的是权限码，API Key 调 {@code /api/**} 只会 403；
 * 反过来把权限码塞进 API Key 的 permissions 会让 {@code /v1/**} 的服务类型判定出现歧义。
 * 两个面的判权语义不同，不该共用一种凭据。</p>
 *
 * <p><b>为什么不新增判权逻辑</b>：访问令牌由 {@link AccountManager#issueTokenForAccount}
 * 签发，claims 与人类账号完全一致，权限码仍从 {@code role_permissions} 里按
 * {@code SLA_READONLY} / {@code SLA_OPERATOR} 两张模板查 —— 只有一份判权真相。</p>
 *
 * <p><b>审计</b>：签发、消费、刷新、吊销四类事件都落 {@code security_audit}，主体记
 * {@code sla:<账号>}，从此能和「人」的操作分开统计。审计写入失败只告警，不影响凭据链路
 * （否则审计表不可用会连带把凭据续期打断）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true", matchIfMissing = true)
public class SlaAccessService {

    /** JWT 主体名前缀：SLA 账号在 {@code jwt_accounts} 里以 {@code sla:<name>} 存在。 */
    public static final String SUBJECT_PREFIX = "sla:";

    /** 一次性接入令牌默认有效期。 */
    static final Duration BOOTSTRAP_TTL = Duration.ofMinutes(15);

    /** 长期刷新凭据默认有效期（滚动续期）。 */
    static final Duration REFRESH_TTL = Duration.ofDays(90);

    /** 默认角色模板。 */
    static final String ROLE_READONLY = "SLA_READONLY";

    /** 允许的角色模板集合。 */
    static final Set<String> ROLE_TEMPLATES = Set.of("SLA_READONLY", "SLA_OPERATOR");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SlaServiceAccountRepository accountRepository;
    private final SlaBootstrapTokenRepository bootstrapTokenRepository;
    private final SlaRefreshTokenRepository refreshTokenRepository;
    private final JwtAccountRepository jwtAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountManager accountManager;
    private final SecurityAuditService securityAuditService;

    /**
     * 用一次性接入令牌换访问令牌与长期刷新凭据。
     *
     * @param bootstrapToken 明文一次性令牌
     * @param clientIp       来源 IP（用于与令牌绑定的地址比对）
     * @return 访问令牌、刷新凭据与过期时间
     */
    /**
     * 用一次性接入令牌换访问令牌与长期刷新凭据。
     *
     * <p><b>为什么这里没有 {@code @Transactional}</b>：本方法返回 {@code Mono}，在 JPA 事务管理器
     * （非响应式）下注解只包住「组装」那一段、包不住订阅后的真实写入，反而让「标记已用」与
     * 「建刷新凭据」看起来像原子的。这里改成依赖每步自身事务 + {@code sla_bootstrap_tokens.version}
     * 的乐观锁：并发拿同一枚令牌时，后提交的一方在 flush 撞版本冲突，被翻译成「令牌已被使用」。</p>
     *
     * @param bootstrapToken 明文一次性令牌
     * @param clientIp       来源 IP（用于与令牌绑定的地址比对）
     * @return 访问令牌、刷新凭据与过期时间
     */
    public Mono<BootstrapResult> bootstrap(final String bootstrapToken, final String clientIp) {
        return Mono.fromCallable(() -> {
                    final LocalDateTime now = LocalDateTime.now();
                    final SlaBootstrapTokenEntity row = requireValidBootstrapToken(bootstrapToken, clientIp, now);
                    final SlaServiceAccountEntity account = requireEnabledAccount(row.getAccountId());
                    // 签发新接入令牌 = 管理员主动批准重新接入，允许把被吊销的主体恢复回来（#1212）。
                    ensureJwtAccount(account, true);
                    row.setUsedAt(now);
                    try {
                        bootstrapTokenRepository.save(row);
                    } catch (ObjectOptimisticLockingFailureException e) {
                        throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 接入令牌已被使用", ErrorCode.TOKEN_REQUIRED.status());
                    }
                    final String refreshToken = createRefreshToken(account, clientIp, now);
                    return new PreparedBootstrap(account, refreshToken);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(prepared -> accountManager.issueTokenForAccount(subjectOf(prepared.account()))
                        .map(accessToken -> {
                            touchLastSeen(prepared.account());
                            audit(AuditEventType.SLA_BOOTSTRAP_CONSUMED, subjectOf(prepared.account()),
                                    "BOOTSTRAP", Map.of("clientIp", nullSafe(clientIp)));
                            return new BootstrapResult(accessToken, prepared.refreshToken(),
                                    accessTokenTtlSeconds(), subjectOf(prepared.account()));
                        }));
    }

    /**
     * 用长期刷新凭据换新的访问令牌（滚动续期）。
     *
     * <p>与 {@link #bootstrap} 一样不用 {@code @Transactional}：滚动续期是「读一行 → 改两个时间列 →
     * 写回」，自身事务足够；并发刷新最坏是两次都成功、续期时间取后写者的值，不影响安全性
     * （凭据没有一次性语义）。</p>
     *
     * @param refreshToken 明文刷新凭据
     * @param clientIp     来源 IP（记录到 {@code last_used_at}/{@code client_ip} 便于排查异地使用）
     * @return 新的访问令牌
     */
    public Mono<RefreshResult> refresh(final String refreshToken, final String clientIp) {
        return Mono.fromCallable(() -> {
                    final LocalDateTime now = LocalDateTime.now();
                    final SlaRefreshTokenEntity row = refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                            .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 刷新凭据无效", ErrorCode.TOKEN_REQUIRED.status()));
                    if (row.getRevokedAt() != null) {
                        throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 刷新凭据已被吊销", ErrorCode.TOKEN_REQUIRED.status());
                    }
                    if (row.getExpiresAt().isBefore(now)) {
                        throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 刷新凭据已过期", ErrorCode.TOKEN_REQUIRED.status());
                    }
                    final SlaServiceAccountEntity account = requireEnabledAccount(row.getAccountId());
                    // 主体被停用（凭据被吊销或账户被禁用）时不允许靠 refresh 自己复活（#1212）。
                    ensureJwtAccount(account, false);
                    row.setLastUsedAt(now);
                    row.setClientIp(clientIp);
                    row.setExpiresAt(now.plus(REFRESH_TTL));
                    refreshTokenRepository.save(row);
                    return account;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(account -> accountManager.issueTokenForAccount(subjectOf(account))
                        .map(accessToken -> {
                            touchLastSeen(account);
                            audit(AuditEventType.SLA_REFRESH, subjectOf(account), "REFRESH",
                                    Map.of("clientIp", nullSafe(clientIp)));
                            return new RefreshResult(accessToken, accessTokenTtlSeconds());
                        }));
    }

    /**
     * 列出全部 SLA 账号（含未消费令牌数与有效凭据数）。
     *
     * @return 账号视图列表
     */
    @Transactional(readOnly = true)
    public List<AccountView> listAccounts() {
        return accountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 新建 SLA 账号。
     *
     * @param name         账号名（唯一）
     * @param roleTemplate 角色模板（{@code SLA_READONLY} 或 {@code SLA_OPERATOR}）
     * @param createdBy    创建人
     * @return 账号视图
     */
    @Transactional
    public AccountView createAccount(final String name, final String roleTemplate, final String createdBy) {
        final String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            throw ApiException.of(ErrorCode.INVALID_NAME.code(), "账号名长度必须在 1~64 之间", ErrorCode.INVALID_NAME.status());
        }
        final String template = roleTemplate == null || roleTemplate.isBlank() ? ROLE_READONLY : roleTemplate.trim();
        if (!ROLE_TEMPLATES.contains(template)) {
            throw ApiException.of(ErrorCode.INVALID_ROLE.code(), "不支持的角色模板: " + template, ErrorCode.INVALID_ROLE.status());
        }
        if (accountRepository.existsByName(trimmed)) {
            throw ApiException.of(ErrorCode.CONFLICT_EXISTS.code(), "SLA 账号已存在: " + trimmed, ErrorCode.CONFLICT_EXISTS.status());
        }
        final LocalDateTime now = LocalDateTime.now();
        final SlaServiceAccountEntity account = SlaServiceAccountEntity.builder()
                .accountId(UUID.randomUUID().toString())
                .name(trimmed)
                .roleTemplate(template)
                .enabled(true)
                .createdBy(createdBy)
                .createdAt(now)
                .build();
        accountRepository.save(account);
        audit(AuditEventType.SLA_ACCOUNT_CREATED, nullSafe(createdBy), "CREATE",
                Map.of("account", trimmed, "roleTemplate", template));
        return new AccountView(account.getAccountId(), account.getName(), account.getRoleTemplate(),
                true, createdBy, now, null, 0L, 0L);
    }

    /**
     * 为账号签发一次性接入令牌（明文只返回一次）。
     *
     * @param accountId    账号主键
     * @param ttlMinutes   有效期分钟数（为空或非正数时用 15 分钟）
     * @param allowedIp    绑定的来源 IP（为空表示不限制）
     * @param createdBy    签发人
     * @return 明文令牌与过期时间
     */
    @Transactional
    public IssueResult issueBootstrap(final String accountId, final Integer ttlMinutes,
                                      final String allowedIp, final String createdBy) {
        final SlaServiceAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND.code(), "SLA 账号不存在: " + accountId, ErrorCode.NOT_FOUND.status()));
        final LocalDateTime now = LocalDateTime.now();
        final Duration ttl = ttlMinutes != null && ttlMinutes > 0 ? Duration.ofMinutes(ttlMinutes) : BOOTSTRAP_TTL;
        final String plain = newToken();
        bootstrapTokenRepository.save(SlaBootstrapTokenEntity.builder()
                .tokenId(UUID.randomUUID().toString())
                .accountId(account.getAccountId())
                .tokenHash(sha256(plain))
                .expiresAt(now.plus(ttl))
                .allowedIp(allowedIp == null || allowedIp.isBlank() ? null : allowedIp.trim())
                .createdBy(createdBy)
                .createdAt(now)
                .build());
        audit(AuditEventType.SLA_BOOTSTRAP_ISSUED, subjectOf(account), "ISSUE",
                Map.of("ttlMinutes", ttl.toMinutes(), "allowedIp", nullSafe(allowedIp),
                        "operator", nullSafe(createdBy)));
        return new IssueResult(plain, now.plus(ttl), ttl.toSeconds());
    }

    /**
     * 吊销账号名下全部有效刷新凭据（账号本身保留，可用于轮换后的旧凭据清理）。
     *
     * @param accountId 账号主键
     * @param operator  操作人
     * @return 被吊销的凭据数
     */
    @Transactional
    public int revokeAll(final String accountId, final String operator) {
        final SlaServiceAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND.code(), "SLA 账号不存在: " + accountId, ErrorCode.NOT_FOUND.status()));
        final LocalDateTime now = LocalDateTime.now();
        final int revoked = revokeCredentials(account, now);
        suspendJwtSubject(account);
        audit(AuditEventType.SLA_CREDENTIAL_REVOKED, subjectOf(account), "REVOKE",
                Map.of("revoked", revoked, "operator", nullSafe(operator)));
        return revoked;
    }

    /**
     * 启用或禁用 SLA 账号（issue #1212）。
     *
     * <p>禁用与「吊销」同义：吊销全部刷新凭据、作废全部未消费接入令牌、停用 {@code jwt_accounts}
     * 里的主体，这样已签发的访问令牌在 {@code JwtAccountStatusVerifier} 复核时立刻失效。
     * 启用只恢复账号本身的开关，<b>不会</b>自动恢复主体 —— 恢复接入需要重新签发接入令牌，
     * 让「谁能重新进来」这件事始终留下一条签发记录。</p>
     *
     * @param accountId 账号主键
     * @param enabled   目标状态
     * @param operator  操作人
     * @return 账号最新视图
     */
    @Transactional
    public AccountView setAccountEnabled(final String accountId, final boolean enabled, final String operator) {
        final SlaServiceAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND.code(), "SLA 账号不存在: " + accountId, ErrorCode.NOT_FOUND.status()));
        if (Boolean.TRUE.equals(account.getEnabled()) == enabled) {
            return toView(account);
        }
        account.setEnabled(enabled);
        accountRepository.save(account);
        if (!enabled) {
            revokeCredentials(account, LocalDateTime.now());
            suspendJwtSubject(account);
        }
        audit(enabled ? AuditEventType.SLA_ACCOUNT_ENABLED : AuditEventType.SLA_ACCOUNT_DISABLED,
                nullSafe(operator), enabled ? "ENABLE" : "DISABLE",
                Map.of("account", account.getName(), "subject", subjectOf(account)));
        return toView(account);
    }

    /**
     * 删除 SLA 账号（连同其全部凭据与未消费令牌）。
     *
     * @param accountId 账号主键
     * @param operator  操作人
     */
    @Transactional
    public void deleteAccount(final String accountId, final String operator) {
        final SlaServiceAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND.code(), "SLA 账号不存在: " + accountId, ErrorCode.NOT_FOUND.status()));
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByAccountId(accountId));
        bootstrapTokenRepository.deleteAll(bootstrapTokenRepository.findByAccountId(accountId));
        accountRepository.delete(account);
        jwtAccountRepository.findByUsername(subjectOf(account)).ifPresent(jwtAccountRepository::delete);
        audit(AuditEventType.SLA_CREDENTIAL_REVOKED, subjectOf(account), "DELETE",
                Map.of("operator", nullSafe(operator)));
    }

    /**
     * 访问令牌还剩多少秒（与 {@code jwt.expiration-minutes} 对齐，供客户端安排续期节奏）。
     *
     * @return 秒数
     */
    long accessTokenTtlSeconds() {
        return Duration.ofMinutes(accountManager.accessTokenExpirationMinutes()).toSeconds();
    }

    private SlaBootstrapTokenEntity requireValidBootstrapToken(final String token, final String clientIp,
                                                              final LocalDateTime now) {
        if (token == null || token.isBlank()) {
            throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "缺少 SLA 接入令牌", ErrorCode.TOKEN_REQUIRED.status());
        }
        final SlaBootstrapTokenEntity row = bootstrapTokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 接入令牌无效", ErrorCode.TOKEN_REQUIRED.status()));
        if (row.getUsedAt() != null) {
            throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 接入令牌已被使用", ErrorCode.TOKEN_REQUIRED.status());
        }
        if (row.getRevokedAt() != null) {
            throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 接入令牌已被吊销", ErrorCode.TOKEN_REQUIRED.status());
        }
        if (row.getExpiresAt().isBefore(now)) {
            throw ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 接入令牌已过期", ErrorCode.TOKEN_REQUIRED.status());
        }
        if (row.getAllowedIp() != null && !row.getAllowedIp().equals(clientIp)) {
            throw ApiException.of(ErrorCode.FORBIDDEN.code(),
                    "SLA 接入令牌绑定来源 IP 为 " + row.getAllowedIp() + "，当前来源 " + clientIp,
                    ErrorCode.FORBIDDEN.status());
        }
        return row;
    }

    private SlaServiceAccountEntity requireEnabledAccount(final String accountId) {
        final SlaServiceAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_REQUIRED.code(), "SLA 账号不存在或被删除", ErrorCode.TOKEN_REQUIRED.status()));
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw ApiException.of(ErrorCode.FORBIDDEN.code(), "SLA 账号已禁用: " + account.getName(), ErrorCode.FORBIDDEN.status());
        }
        return account;
    }

    private String createRefreshToken(final SlaServiceAccountEntity account, final String clientIp,
                                      final LocalDateTime now) {
        final String plain = newToken();
        refreshTokenRepository.save(SlaRefreshTokenEntity.builder()
                .tokenId(UUID.randomUUID().toString())
                .accountId(account.getAccountId())
                .tokenHash(sha256(plain))
                .issuedAt(now)
                .expiresAt(now.plus(REFRESH_TTL))
                .clientIp(clientIp)
                .build());
        return plain;
    }

    /**
     * 确保 {@code jwt_accounts} 里有 {@code sla:<name>} 行，角色为账号当前的模板。
     *
     * <p>访问令牌的签发仍然走 {@code AccountManager.loadUserByUsername}，所以主体必须能在
     * 账户表里被解析到；这里存的密码是随机值，意味着该账号<b>永远无法用密码登录</b>控制台，
     * 只能靠一次性令牌换来的凭据工作。</p>
     */
    private void ensureJwtAccount(final SlaServiceAccountEntity account, final boolean reactivate) {
        final String username = subjectOf(account);
        final JwtAccountEntity existing = jwtAccountRepository.findByUsername(username).orElse(null);
        final String rolesJson = writeRoles(account.getRoleTemplate());
        if (existing == null) {
            jwtAccountRepository.save(JwtAccountEntity.builder()
                    .username(username)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .roles(rolesJson)
                    .enabled(true)
                    .deleted(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
            return;
        }
        final boolean suspended = existing.isDeletedAccount() || Boolean.FALSE.equals(existing.getEnabled());
        if (suspended && !reactivate) {
            throw ApiException.of(ErrorCode.FORBIDDEN.code(),
                    "SLA 主体已停用（凭据被吊销或账号被禁用），请重新签发接入令牌",
                    ErrorCode.FORBIDDEN.status());
        }
        existing.setRoles(rolesJson);
        existing.setEnabled(true);
        existing.setDeleted(false);
        existing.setUpdatedAt(LocalDateTime.now());
        jwtAccountRepository.save(existing);
    }

    /**
     * 角色列表转成 {@code jwt_accounts.roles} 需要的 JSON 数组字符串。
     *
     * <p>刻意不注入 {@code ObjectMapper}：{@code migrate} profile 的容器里没有该 bean，
     * 构造注入会让「迁移专用入口」直接起不来（实测 
     * {@code APPLICATION FAILED TO START … required a bean of type ObjectMapper}）。
     * 这里只有两个固定角色模板、且都已在上游按白名单校验过，直接拼 JSON 字面量即可；
     * 解析侧 {@code AccountManager.parseRoles} 仍按 JSON 数组读。
     */
    private static String writeRoles(final String roleTemplate) {
        return "[\"" + roleTemplate + "\"]";
    }

    /**
     * 吊销账号名下的全部刷新凭据，并作废全部「尚未被消费」的接入令牌（issue #1210）。
     *
     * <p>接入令牌不删行只置 {@code revokedAt}：删了就看不出「这枚令牌曾经存在、被谁拔掉」，
     * 审计链条会断。已消费或已吊销的行不重复处理，返回值只统计本次真正被吊销的刷新凭据数，
     * 与接口既有的 {@code revoked} 字段含义保持一致。</p>
     */
    private int revokeCredentials(final SlaServiceAccountEntity account, final LocalDateTime now) {
        final List<SlaRefreshTokenEntity> active = refreshTokenRepository
                .findByAccountIdAndRevokedAtIsNull(account.getAccountId());
        active.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
        final List<SlaBootstrapTokenEntity> pending = bootstrapTokenRepository
                .findByAccountId(account.getAccountId()).stream()
                .filter(token -> token.getUsedAt() == null && token.getRevokedAt() == null)
                .toList();
        pending.forEach(token -> token.setRevokedAt(now));
        bootstrapTokenRepository.saveAll(pending);
        return active.size();
    }

    /**
     * 停用 {@code jwt_accounts} 里的 SLA 主体，让已签发的访问令牌立即失效（issue #1211）。
     *
     * <p>{@link JwtAccountStatusVerifier} 每个请求复核库里的账户状态，{@code enabled=false}
     * 即拒绝，因此不必等访问令牌自然过期，也就不用引入一套令牌黑名单。</p>
     */
    private void suspendJwtSubject(final SlaServiceAccountEntity account) {
        jwtAccountRepository.findByUsername(subjectOf(account)).ifPresent(existing -> {
            if (Boolean.FALSE.equals(existing.getEnabled()) && !existing.isDeletedAccount()) {
                return;
            }
            existing.setEnabled(false);
            existing.setUpdatedAt(LocalDateTime.now());
            jwtAccountRepository.save(existing);
        });
    }

    private AccountView toView(final SlaServiceAccountEntity account) {
        return new AccountView(
                account.getAccountId(),
                account.getName(),
                account.getRoleTemplate(),
                Boolean.TRUE.equals(account.getEnabled()),
                account.getCreatedBy(),
                account.getCreatedAt(),
                account.getLastSeenAt(),
                refreshTokenRepository.countByAccountIdAndRevokedAtIsNull(account.getAccountId()),
                bootstrapTokenRepository.countByAccountIdAndUsedAtIsNull(account.getAccountId()));
    }

    private void touchLastSeen(final SlaServiceAccountEntity account) {
        account.setLastSeenAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    private void audit(final AuditEventType eventType, final String subject, final String action,
                       final Map<String, Object> data) {
        if (securityAuditService == null) {
            return;
        }
        try {
            final SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventType(eventType.name())
                    .userId(subject)
                    .resource("sla")
                    .action(action)
                    .success(true)
                    .additionalData(data)
                    .build();
            securityAuditService.recordEvent(event)
                    .subscribe(null, err -> log.warn("SLA 审计写入失败: {}", err.getMessage()));
        } catch (RuntimeException e) {
            log.warn("SLA 审计事件构造失败: {}", e.getMessage());
        }
    }

    private static String subjectOf(final SlaServiceAccountEntity account) {
        return SUBJECT_PREFIX + account.getName();
    }

    private static String newToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算令牌摘要（SHA-256 hex）。
     *
     * @param plain 明文令牌
     * @return 十六进制摘要
     */
    static String sha256(final String plain) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR.code(), "SHA-256 不可用", ErrorCode.INTERNAL_ERROR.status());
        }
    }

    private static String nullSafe(final String value) {
        return value == null ? "" : value;
    }

    /** bootstrap 第一段（数据库事务内）的中间结果。 */
    private record PreparedBootstrap(SlaServiceAccountEntity account, String refreshToken) {
    }

    /**
     * bootstrap 的返回体。
     *
     * @param accessToken  访问令牌
     * @param refreshToken 长期刷新凭据（只在此处返回一次）
     * @param expiresIn    访问令牌剩余秒数
     * @param subject      JWT 主体名（形如 {@code sla:<name>}）
     */
    public record BootstrapResult(String accessToken, String refreshToken, long expiresIn, String subject) {
    }

    /**
     * refresh 的返回体。
     *
     * @param accessToken 访问令牌
     * @param expiresIn   访问令牌剩余秒数
     */
    public record RefreshResult(String accessToken, long expiresIn) {
    }

    /**
     * 一次性令牌签发结果。
     *
     * @param bootstrapToken 明文令牌（只返回一次）
     * @param expiresAt      过期时间
     * @param expiresIn      剩余秒数
     */
    public record IssueResult(String bootstrapToken, LocalDateTime expiresAt, long expiresIn) {
    }

    /**
     * 账号视图。
     *
     * @param accountId            账号主键
     * @param name                 账号名
     * @param roleTemplate         角色模板
     * @param enabled              是否启用
     * @param createdBy            创建人
     * @param createdAt            创建时间
     * @param lastSeenAt           最后换取凭据时间
     * @param activeRefreshTokens  有效刷新凭据数
     * @param unusedBootstrapTokens 未消费的一次性令牌数
     */
    public record AccountView(String accountId, String name, String roleTemplate, boolean enabled,
                              String createdBy, LocalDateTime createdAt, LocalDateTime lastSeenAt,
                              long activeRefreshTokens, long unusedBootstrapTokens) {
    }
}
