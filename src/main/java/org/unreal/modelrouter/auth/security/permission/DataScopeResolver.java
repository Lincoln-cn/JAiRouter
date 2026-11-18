package org.unreal.modelrouter.auth.security.permission;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.dto.ApiKeyListVO;
import org.unreal.modelrouter.auth.security.dto.ApiKeyVO;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;

/**
 * 把「当前主体 + 全局读码 + 本人读码」解析成 {@link DataScope}。
 *
 * <p>判定顺序：持全局码 ⇒ 不受限；只持本人码 ⇒ 按并集过滤；两者都不持 ⇒ 不受限（正常情况下
 * URL 规则已经 403，走到这里说明规则配错，此时放开比错误地收紧更安全 —— 收紧会造成「有权限却看不到数据」
 * 这类更难排查的故障）。</p>
 *
 * <p>密钥列表来自 {@link ApiKeyService#getAllApiKeysVO()}，按 {@code createdBy} 过滤，
 * 取 {@code keyId}。这一步是响应式的，因此只在 <b>本人读</b> 分支上执行，管理员路径完全不碰它。</p>
 */
@Component
public final class DataScopeResolver {

    private final ApiKeyService apiKeyService;

    public DataScopeResolver(final ApiKeyService apiKeyService) {
        this.apiKeyService = Objects.requireNonNull(apiKeyService, "apiKeyService");
    }

    /**
     * 仅判定「是否受限」，不查密钥（同步，可在任意线程调用）。
     */
    public DataScope resolve(final Authentication authentication,
                             final String globalCode,
                             final String selfCode) {
        if (PermissionChecks.hasAny(authentication, globalCode)) {
            return DataScope.unrestricted();
        }
        if (PermissionChecks.hasAny(authentication, selfCode)) {
            return DataScope.of(authentication.getName());
        }
        return DataScope.unrestricted();
    }

    /**
     * 补齐密钥列表后的完整解析结果。
     */
    public reactor.core.publisher.Mono<DataScope> resolveWithKeys(final Authentication authentication,
                                                                 final String globalCode,
                                                                 final String selfCode) {
        final DataScope base = resolve(authentication, globalCode, selfCode);
        if (!base.restricted()) {
            return reactor.core.publisher.Mono.just(base);
        }
        final String subject = base.userId();
        return apiKeyService.getAllApiKeysVO()
                .map(vo -> base.withApiKeyIds(keyIdsCreatedBy(vo, subject)))
                .onErrorResume(error -> reactor.core.publisher.Mono.just(base));
    }

    private static Set<String> keyIdsCreatedBy(final ApiKeyListVO view, final String subject) {
        if (view == null || view.getItems() == null || subject == null) {
            return Set.of();
        }
        return view.getItems().stream()
                .filter(key -> subject.equals(key.getCreatedBy()))
                .map(ApiKeyVO::getKeyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
