package org.unreal.modelrouter.auth.security.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据面可见范围（行级过滤条件）。
 *
 * <p>语义分两档：</p>
 * <ul>
 *   <li>{@link #unrestricted()} —— 持全局读码，看全部记录；</li>
 *   <li>{@link #of(String)} —— 只持「本人读」码，只看自己的记录。</li>
 * </ul>
 *
 * <p><b>「自己的记录」按并集判定</b>（用户裁定）：记录里的 {@code userId} 等于当前账号名，
 * <b>或</b> 记录的 {@code apiKeyId} 属于当前账号创建的密钥。只看其中一个维度会漏数据 ——
 * 网关代调用方写入的记录可能只有 userId，而人工用密钥发起的调用可能只有 apiKeyId。</p>
 *
 * <p>两条约束：</p>
 * <ol>
 *   <li>{@code apiKeyIds} 为空集合表示「没有可匹配的密钥」，仍然按 userId 过滤，不能当成不限制；</li>
 *   <li>过滤只做在服务端查询里，前端传什么 scope 参数都不改变这里的判定。</li>
 * </ol>
 */
public record DataScope(boolean restricted, String userId, Set<String> apiKeyIds) {

    public DataScope {
        apiKeyIds = apiKeyIds == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(apiKeyIds));
    }

    /** 不受限：看全部数据。 */
    public static DataScope unrestricted() {
        return new DataScope(false, null, Set.of());
    }

    /** 只按 userId 过滤（密钥列表稍后补齐）。 */
    public static DataScope of(final String userId) {
        return new DataScope(true, userId, Set.of());
    }

    /** 补齐「当前账号创建的密钥」，用于并集判定。 */
    public DataScope withApiKeyIds(final Set<String> ids) {
        return restricted ? new DataScope(true, userId, ids) : this;
    }

    /** 是否按 userId 过滤。 */
    public boolean filterByUser() {
        return restricted && userId != null && !userId.isBlank();
    }

    /** 是否按密钥集合过滤。 */
    public boolean filterByKeys() {
        return restricted && !apiKeyIds.isEmpty();
    }
}
