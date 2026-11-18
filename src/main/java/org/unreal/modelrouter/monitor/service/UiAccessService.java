package org.unreal.modelrouter.monitor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.dto.UiPageViewSummary;
import org.unreal.modelrouter.persistence.jpa.entity.UiPageViewEntity;
import org.unreal.modelrouter.persistence.jpa.repository.UiPageViewRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 控制台页面访问统计（B4，2026-11-18）。
 *
 * <p>数据落在独立的 {@code ui_page_views} 表（见 {@link UiPageViewEntity} 的类注释：为什么不用审计表）。
 *
 * <p>三处刻意的取舍：
 * <ul>
 *   <li><b>不记 IP</b>：本能力只回答「谁看过哪一页」，客户端地址属于额外识别信息，收窄到最小</li>
 *   <li><b>写入失败静默</b>：上报是观测行为，不能因为它失败而影响用户正在做的事</li>
 *   <li><b>失败不打断路由</b>：{@code recordAccess} 永远返回完成信号，前端不需要处理错误分支</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.3.8
 */
@Slf4j
@Service
public class UiAccessService {

    /**
     * 允许上报的路径形态：仅路由段字符，禁止查询串、协议、空格。
     *
     * <p>负向前瞻 {@code (?!/)} 用来挡住 {@code //evil.example.com/x} 这类协议相对地址
     * ——它同样以 {@code /} 开头，但会被浏览器当成外部主机。
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("^/(?!/)[A-Za-z0-9/_.-]{0,199}$");

    /** 角色名白名单形态（大写字母与下划线，长度受限） */
    private static final Pattern ROLE_PATTERN = Pattern.compile("^[A-Z_]{2,32}$");

    private static final int MAX_PATH_LENGTH = 200;

    private static final int DEFAULT_SUMMARY_DAYS = 7;

    private static final int MAX_SUMMARY_DAYS = 90;

    private static final int DEFAULT_SUMMARY_LIMIT = 20;

    private static final int MAX_SUMMARY_LIMIT = 100;

    private final UiPageViewRepository repository;

    public UiAccessService(final UiPageViewRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一次页面访问。
     *
     * @param userId 认证主体（用户名），为空时直接忽略
     * @param path   路由路径
     * @param role   上报的角色名（可为空）
     * @return 完成信号（无论成功失败都会完成）
     */
    public Mono<Void> recordAccess(final String userId, final String path, final String role) {
        if (userId == null || userId.isBlank() || !isValidPath(path)) {
            return Mono.empty();
        }
        final UiPageViewEntity entity = UiPageViewEntity.builder()
                .userId(userId.length() > 128 ? userId.substring(0, 128) : userId)
                .path(path)
                .role(normalizeRole(role))
                .viewedAt(LocalDateTime.now())
                .build();
        return Mono.fromRunnable(() -> repository.save(entity))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(error -> {
                    log.debug("页面访问上报失败（忽略）: path={}, error={}", path, error.toString());
                    return Mono.empty();
                });
    }

    /**
     * 页面访问热度聚合（按路径 × 角色）。
     *
     * @param days  统计窗口（天），越界会被夹到 [1, 90]
     * @param limit 返回行数上限，越界会被夹到 [1, 100]
     * @return 按访问次数降序的聚合结果
     */
    public Mono<List<UiPageViewSummary>> summarize(final Integer days, final Integer limit) {
        final int windowDays = clamp(days, DEFAULT_SUMMARY_DAYS, 1, MAX_SUMMARY_DAYS);
        final int rowLimit = clamp(limit, DEFAULT_SUMMARY_LIMIT, 1, MAX_SUMMARY_LIMIT);
        final LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
        return Mono.fromCallable(() -> repository.summarize(since, PageRequest.of(0, rowLimit)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toSummaries);
    }

    /**
     * 删除超出保留期的访问记录。
     *
     * @param retentionDays 保留天数，非正数表示不清理
     * @return 删除行数
     */
    public Mono<Integer> purge(final int retentionDays) {
        if (retentionDays <= 0) {
            return Mono.just(0);
        }
        final LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return Mono.fromCallable(() -> repository.deleteOlderThan(cutoff))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<UiPageViewSummary> toSummaries(final List<Object[]> rows) {
        final List<UiPageViewSummary> result = new ArrayList<>(rows.size());
        for (final Object[] row : rows) {
            final String path = row[0] == null ? "" : row[0].toString();
            final String role = row[1] == null ? "" : row[1].toString();
            final long views = row[2] == null ? 0L : ((Number) row[2]).longValue();
            final long users = row[3] == null ? 0L : ((Number) row[3]).longValue();
            result.add(new UiPageViewSummary(path, role, views, users));
        }
        return result;
    }

    /** 路径校验：必须是站内路由，且不含查询串（防止把 token 之类拼进 URL 被记下来） */
    boolean isValidPath(final String path) {
        return path != null
                && path.length() <= MAX_PATH_LENGTH
                && path.indexOf('?') < 0
                && path.indexOf('#') < 0
                && PATH_PATTERN.matcher(path).matches();
    }

    /** 角色名归一：不在白名单形态内的一律丢弃，避免把任意字符串写进统计表 */
    String normalizeRole(final String role) {
        if (role == null) {
            return null;
        }
        final String trimmed = role.trim().toUpperCase(java.util.Locale.ROOT);
        return ROLE_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static int clamp(final Integer value, final int fallback, final int min, final int max) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
