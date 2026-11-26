package org.unreal.modelrouter.auth.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.common.exception.ClientErrors;
import org.unreal.modelrouter.persistence.jpa.entity.LoginGuardConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.LoginGuardConfigRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 登录保护配置的持久化与生效（issue #1176）.
 *
 * <p><b>加载口径与 jwt_accounts 一致</b>：YAML 只提供「首次初始值」，一旦落库就以数据库为准。
 * 启动完成时 {@link #seedIfEmpty()} 检查表里有没有那一行 —— 没有就把 {@code @Value} 读到的默认值
 * 写进去；有就把库里的值推给 {@link LoginBruteForceGuard}。之后控制台的每次修改都「写库 + 立即热改」，
 * 不需要重启，也不存在「配置文件覆盖数据库」的歧义。</p>
 *
 * <p>与 {@code QuotaConfigController} 的差别：配额那边是「内存覆盖 + 快照返回」、改了不落库；
 * 这里的阈值属于安全策略，用户明确要求可持久化、可审计（见 issue #1176 的决策记录），所以走落库路线。</p>
 *
 * <p>阈值本身由 {@link LoginGuardSettings} 的紧凑构造器校验，非法值一律
 * {@link ErrorCode#INVALID_REQUEST}（400）。</p>
 *
 * @since 2026-11（issue #1176）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginGuardConfigService {

    private final LoginGuardConfigRepository repository;

    private final LoginBruteForceGuard guard;

    private final ExtendedSecurityAuditService auditService;

    /** 只加载一次：避免每次请求都读库。 */
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    /**
     * 启动完成后把「库里的值」或「YAML 默认值」落到内存里的 guard。
     *
     * <p>监听 {@link ApplicationReadyEvent} 而不是 {@code @PostConstruct}：这个动作要读库，
     * 而库可能由 Flyway / JPA 初始化在更晚的阶段才就绪。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        try {
            final LoginGuardSettings current = guard.settings();
            final LoginGuardConfigEntity existing = repository.findAll().stream().findFirst().orElse(null);
            if (existing == null) {
                final LoginGuardConfigEntity seeded = LoginGuardConfigEntity.builder()
                        .configId(LoginGuardConfigEntity.DEFAULT_ID)
                        .maxFailuresPerPrincipal(current.maxFailuresPerPrincipal())
                        .maxFailuresPerIp(current.maxFailuresPerIp())
                        .windowMinutes(current.windowMinutes())
                        .lockMinutes(current.lockMinutes())
                        .updatedBy("system")
                        .updatedAt(LocalDateTime.now())
                        .build();
                repository.save(seeded);
                log.info("登录保护配置首次落库（来源 YAML 默认值）: principal={}, ip={}, window={}min, lock={}min",
                        current.maxFailuresPerPrincipal(), current.maxFailuresPerIp(),
                        current.windowMinutes(), current.lockMinutes());
            } else {
                guard.applySettings(fromEntity(existing));
                log.info("登录保护配置从数据库加载: principal={}, ip={}, window={}min, lock={}min（更新于 {} by {}）",
                        existing.getMaxFailuresPerPrincipal(), existing.getMaxFailuresPerIp(),
                        existing.getWindowMinutes(), existing.getLockMinutes(),
                        existing.getUpdatedAt(), existing.getUpdatedBy());
            }
            loaded.set(true);
        } catch (RuntimeException ex) {
            // 启动期读库失败不得打断服务：保持 YAML 默认值继续跑，下一次请求会重试加载
            log.warn("登录保护配置加载失败，保持 YAML 默认值: {}", ex.getMessage());
            loaded.set(false);
        }
    }

    /**
     * 当前生效配置（含来源信息）。
     *
     * @return 配置快照
     */
    @Transactional(readOnly = true)
    public ConfigView current() {
        ensureLoaded();
        final LoginGuardSettings settings = guard.settings();
        final LoginGuardConfigEntity entity = repository.findAll().stream().findFirst().orElse(null);
        return new ConfigView(settings.maxFailuresPerPrincipal(), settings.maxFailuresPerIp(),
                settings.windowMinutes(), settings.lockMinutes(),
                entity == null ? null : entity.getUpdatedAt(),
                entity == null ? null : entity.getUpdatedBy());
    }

    /**
     * 修改配置：写库 → 立即热改 → 写审计。
     *
     * @param settings 新阈值（已校验）
     * @param operator 操作人
     * @return 热改后的配置快照
     */
    @Transactional
    public ConfigView update(final LoginGuardSettings settings, final String operator) {
        final LoginGuardSettings before = guard.settings();
        final LoginGuardConfigEntity entity = repository.findAll().stream().findFirst()
                .orElseGet(() -> LoginGuardConfigEntity.builder()
                        .configId(LoginGuardConfigEntity.DEFAULT_ID)
                        .build());
        entity.setMaxFailuresPerPrincipal(settings.maxFailuresPerPrincipal());
        entity.setMaxFailuresPerIp(settings.maxFailuresPerIp());
        entity.setWindowMinutes(settings.windowMinutes());
        entity.setLockMinutes(settings.lockMinutes());
        entity.setUpdatedBy(operator);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        guard.applySettings(settings);
        audit(AuditEventType.LOGIN_GUARD_CONFIG_CHANGED,
                "登录保护配置变更: before=" + describe(before) + ", after=" + describe(settings)
                        + ", operator=" + operator,
                operator);
        log.info("登录保护配置已更新 by {}: before={}, after={}", operator, describe(before), describe(settings));
        return new ConfigView(settings.maxFailuresPerPrincipal(), settings.maxFailuresPerIp(),
                settings.windowMinutes(), settings.lockMinutes(), entity.getUpdatedAt(), entity.getUpdatedBy());
    }

    /**
     * 当前被锁定的主体（只含仍在锁定窗口内的）。
     *
     * @return 被锁主体列表，按剩余时间升序
     */
    public List<LoginBruteForceGuard.LockedPrincipal> lockedPrincipals() {
        return guard.snapshotLocked();
    }

    /**
     * 手动解除锁定。
     *
     * @param username 用户名（可空）
     * @param ip       客户端 IP（可空）
     * @param operator 操作人
     * @return 实际清除的计数键数量
     */
    public int unlock(final String username, final String ip, final String operator) {
        final int cleared = guard.unlock(username, ip);
        audit(AuditEventType.LOGIN_GUARD_UNLOCKED,
                "手动解除登录锁定: username=" + nullSafe(username) + ", ip=" + nullSafe(ip)
                        + ", cleared=" + cleared + ", operator=" + operator,
                operator);
        log.info("手动解除登录锁定 by {}: username={}, ip={}, cleared={}", operator, username, ip, cleared);
        return cleared;
    }

    /**
     * 校验并构造参数；非法值转成 400。
     *
     * @return 合法参数
     */
    public static LoginGuardSettings validated(final Integer maxFailuresPerPrincipal, final Integer maxFailuresPerIp,
            final Integer windowMinutes, final Integer lockMinutes) {
        if (maxFailuresPerPrincipal == null || maxFailuresPerIp == null
                || windowMinutes == null || lockMinutes == null) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "四个阈值都必须提供", ErrorCode.INVALID_REQUEST.status());
        }
        try {
            return new LoginGuardSettings(maxFailuresPerPrincipal, maxFailuresPerIp, windowMinutes, lockMinutes);
        } catch (IllegalArgumentException ex) {
            // #1197：校验异常原文只进日志，对外只回固定短语
            throw ClientErrors.api(ex, ErrorCode.INVALID_REQUEST, "登录保护参数无效");
        }
    }

    private void ensureLoaded() {
        if (loaded.compareAndSet(false, true)) {
            try {
                repository.findAll().stream().findFirst()
                        .ifPresent(entity -> guard.applySettings(fromEntity(entity)));
            } catch (RuntimeException ex) {
                loaded.set(false);
                log.warn("登录保护配置懒加载失败: {}", ex.getMessage());
            }
        }
    }

    private static LoginGuardSettings fromEntity(final LoginGuardConfigEntity entity) {
        return new LoginGuardSettings(entity.getMaxFailuresPerPrincipal(), entity.getMaxFailuresPerIp(),
                entity.getWindowMinutes(), entity.getLockMinutes());
    }

    private static String describe(final LoginGuardSettings settings) {
        return "principal=" + settings.maxFailuresPerPrincipal()
                + ",ip=" + settings.maxFailuresPerIp()
                + ",window=" + settings.windowMinutes()
                + "min,lock=" + settings.lockMinutes() + "min";
    }

    private static String nullSafe(final String value) {
        return value == null ? "" : value.trim();
    }

    private void audit(final AuditEventType eventType, final String details, final String operator) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.auditSecurityEvent(eventType.name(), details, operator, null)
                    .onErrorResume(ex -> {
                        log.warn("记录 {} 审计失败: {}", eventType, ex.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe();
        } catch (RuntimeException ex) {
            log.warn("记录 {} 审计失败: {}", eventType, ex.getMessage());
        }
    }

    /**
     * 配置快照视图（含最后修改人与时间）。
     *
     * @param maxFailuresPerPrincipal 按 IP + 用户名的阈值
     * @param maxFailuresPerIp        按 IP 的阈值
     * @param windowMinutes           计数窗口（分钟）
     * @param lockMinutes             锁定时长（分钟）
     * @param updatedAt               最后修改时间（首次落库前可能为 null）
     * @param updatedBy               最后修改人
     */
    public record ConfigView(int maxFailuresPerPrincipal, int maxFailuresPerIp, int windowMinutes, int lockMinutes,
            LocalDateTime updatedAt, String updatedBy) {
    }
}
