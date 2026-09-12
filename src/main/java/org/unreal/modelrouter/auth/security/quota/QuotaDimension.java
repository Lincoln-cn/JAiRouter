package org.unreal.modelrouter.auth.security.quota;

/**
 * 配额账本维度（租户 / API Key / 用户 / 服务类型 / 模型）。
 *
 * <p>账本按上述五维聚合统计请求数与 token 数。任一维度未知时统一折叠为空串
 * {@link #SENTINEL} 而不是 {@code null}，原因有两点：</p>
 * <ul>
 *   <li>数据库维度列声明为 NOT NULL，空串哨兵值避免每次落库前做空值判断；</li>
 *   <li>{@code null} 与空串在账本中语义相同（均为“未指定”），归一化后
 *       {@link #equals(Object)} / {@link #hashCode()} 保持稳定，避免出现
 *       两份内容相同但 key 不同的内存槽位。</li>
 * </ul>
 *
 * @param tenantId    租户 ID，未指定时为空串
 * @param apiKeyId    API Key ID，未指定时为空串
 * @param userId      用户 ID，未指定时为空串
 * @param serviceType 服务类型（chat / embedding / rerank / tts / stt），未指定时为空串
 * @param model       模型名称，未指定时为空串
 * @author JAiRouter Team
 * @since 3.1.0
 */
public record QuotaDimension(String tenantId, String apiKeyId, String userId, String serviceType, String model) {

    /** 维度缺省哨兵值（空串），与数据库维度列的 NOT NULL 约束配套使用 */
    public static final String SENTINEL = "";

    /**
     * 规范化构造：把任意 {@code null} 维度折叠为空串哨兵值。
     */
    public QuotaDimension {
        tenantId = normalize(tenantId);
        apiKeyId = normalize(apiKeyId);
        userId = normalize(userId);
        serviceType = normalize(serviceType);
        model = normalize(model);
    }

    /**
     * 仅指定 API Key 的维度，其余维度取哨兵值。
     * 适用于尚不知道该请求租户 / 用户 / 模型的管理侧查询。
     *
     * @param apiKeyId API Key ID，可为 {@code null}
     * @return 归一化后的维度
     */
    public static QuotaDimension ofApiKey(final String apiKeyId) {
        return new QuotaDimension(null, apiKeyId, null, null, null);
    }

    /**
     * 全部维度取哨兵值的空维度。
     *
     * @return 空维度
     */
    public static QuotaDimension empty() {
        return new QuotaDimension(null, null, null, null, null);
    }

    /**
     * 把 {@code null} 归一化为空串哨兵值。
     *
     * @param value 原始维度值
     * @return 非 {@code null} 的维度值
     */
    private static String normalize(final String value) {
        return value == null ? SENTINEL : value;
    }
}
