package org.unreal.modelrouter.auth.security.quota;

import org.springframework.dao.DataIntegrityViolationException;
import org.unreal.modelrouter.persistence.jpa.entity.QuotaLedgerEntity;
import org.unreal.modelrouter.persistence.jpa.repository.QuotaLedgerRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用内存账本仓库（v3.1 PR-1 测试夹具）。
 *
 * <p>这是手写的真实实现而不是 Mockito mock：内部用 {@link LinkedHashMap} 模拟七列唯一键的
 * 账本表，并实现 {@code accumulate} 的原子累加语义、{@code save} 的唯一约束语义、
 * 过期删除与按 Key 删除语义。通过 {@link #setFailing(boolean)} 可注入“数据库不可用”
 * 故障以验证 fail-open 行为。</p>
 *
 * <p>使用方法：{@code QuotaLedgerRepository repo = fake.proxy();}（动态代理实现接口，
 * 避免为 {@code JpaRepository} 的数十个方法编写样板代码）。</p>
 */
public final class InMemoryQuotaLedgerRepository implements InvocationHandler {

    /** 七列精确查询方法名（与 {@link QuotaLedgerRepository} 保持一致） */
    private static final String FIND_BY_DIMENSION =
        "findByTenantIdAndApiKeyIdAndUserIdAndServiceTypeAndModelAndWindowTypeAndWindowStart";

    private final Map<String, QuotaLedgerEntity> rows = new LinkedHashMap<>();
    private final AtomicInteger invocations = new AtomicInteger();

    private volatile boolean failing;
    private long nextId = 1L;

    /**
     * 创建假仓库。
     *
     * @return 假仓库实例
     */
    public static InMemoryQuotaLedgerRepository create() {
        return new InMemoryQuotaLedgerRepository();
    }

    /**
     * 获取代理后的仓库接口。
     *
     * @return 仓库代理
     */
    public QuotaLedgerRepository proxy() {
        return (QuotaLedgerRepository) Proxy.newProxyInstance(
            QuotaLedgerRepository.class.getClassLoader(),
            new Class<?>[]{QuotaLedgerRepository.class},
            this);
    }

    /**
     * 模拟数据库不可用（所有方法抛异常）。
     *
     * @param value true = 抛异常
     */
    public void setFailing(final boolean value) {
        this.failing = value;
    }

    /**
     * 累计被调用的方法次数（用于验证“未启用时零数据库访问”）。
     *
     * @return 调用次数
     */
    public int invocationCount() {
        return invocations.get();
    }

    /**
     * 当前全部账本行快照。
     *
     * @return 行列表
     */
    public List<QuotaLedgerEntity> rows() {
        return new ArrayList<>(rows.values());
    }

    /**
     * 读取一行。
     *
     * @param apiKeyId    API Key ID
     * @param windowType  窗口类型
     * @param windowStart 窗口起点
     * @return 命中的行，不存在时返回 {@code null}
     */
    public QuotaLedgerEntity row(final String apiKeyId, final String windowType, final LocalDateTime windowStart) {
        return rows.get(key("", apiKeyId, "", "", "", windowType, windowStart));
    }

    /**
     * 直接写入一行（模拟历史快照）。
     *
     * @param apiKeyId    API Key ID
     * @param windowType  窗口类型
     * @param windowStart 窗口起点
     * @param requests    请求数
     * @param tokens      token 数
     */
    public void seed(final String apiKeyId,
                     final String windowType,
                     final LocalDateTime windowStart,
                     final long requests,
                     final long tokens) {
        final QuotaLedgerEntity entity = QuotaLedgerEntity.builder()
            .id(nextId++)
            .tenantId("")
            .apiKeyId(apiKeyId)
            .userId("")
            .serviceType("")
            .model("")
            .windowType(windowType)
            .windowStart(windowStart)
            .requestCount(requests)
            .tokenCount(tokens)
            .updatedAt(windowStart)
            .build();
        rows.put(key(entity), entity);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        invocations.incrementAndGet();
        final String name = method.getName();
        if ("toString".equals(name)) {
            return "InMemoryQuotaLedgerRepository(rows=" + rows.size() + ")";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }
        if (failing) {
            throw new IllegalStateException("模拟账本数据库不可用: " + name);
        }
        switch (name) {
            case FIND_BY_DIMENSION:
                return Optional.ofNullable(rows.get(key((String) args[0], (String) args[1], (String) args[2],
                    (String) args[3], (String) args[4], (String) args[5], (LocalDateTime) args[6])));
            case "findByApiKeyId":
                return findByApiKeyId((String) args[0]);
            case "accumulate":
                return accumulate(args);
            case "deleteByApiKeyId":
                return deleteByApiKeyId((String) args[0]);
            case "deleteExpired":
                return deleteExpired((String) args[0], (LocalDateTime) args[1]);
            case "save":
                return save((QuotaLedgerEntity) args[0]);
            case "count":
            case "countAll":
                return (long) rows.size();
            default:
                throw new UnsupportedOperationException("测试假实现未覆盖的仓库方法: " + name);
        }
    }

    private List<QuotaLedgerEntity> findByApiKeyId(final String apiKeyId) {
        final List<QuotaLedgerEntity> result = new ArrayList<>();
        for (final QuotaLedgerEntity entity : rows.values()) {
            if (apiKeyId.equals(entity.getApiKeyId())) {
                result.add(entity);
            }
        }
        return result;
    }

    private int accumulate(final Object[] args) {
        final QuotaLedgerEntity entity = rows.get(key((String) args[0], (String) args[1], (String) args[2],
            (String) args[3], (String) args[4], (String) args[5], (LocalDateTime) args[6]));
        if (entity == null) {
            return 0;
        }
        final long requests = (Long) args[7];
        final long tokens = (Long) args[8];
        entity.setRequestCount(nullToZero(entity.getRequestCount()) + requests);
        entity.setTokenCount(nullToZero(entity.getTokenCount()) + tokens);
        entity.setUpdatedAt((LocalDateTime) args[9]);
        return 1;
    }

    private int deleteByApiKeyId(final String apiKeyId) {
        final List<String> keys = new ArrayList<>();
        for (final Map.Entry<String, QuotaLedgerEntity> entry : rows.entrySet()) {
            if (apiKeyId.equals(entry.getValue().getApiKeyId())) {
                keys.add(entry.getKey());
            }
        }
        keys.forEach(rows::remove);
        return keys.size();
    }

    private int deleteExpired(final String windowType, final LocalDateTime cutoff) {
        final List<String> keys = new ArrayList<>();
        for (final Map.Entry<String, QuotaLedgerEntity> entry : rows.entrySet()) {
            final QuotaLedgerEntity entity = entry.getValue();
            if (windowType.equals(entity.getWindowType()) && entity.getWindowStart() != null
                && entity.getWindowStart().isBefore(cutoff)) {
                keys.add(entry.getKey());
            }
        }
        keys.forEach(rows::remove);
        return keys.size();
    }

    private QuotaLedgerEntity save(final QuotaLedgerEntity entity) {
        if (rows.containsKey(key(entity))) {
            throw new DataIntegrityViolationException("duplicate quota_ledger row: " + key(entity));
        }
        if (entity.getId() == null) {
            entity.setId(nextId++);
        }
        rows.put(key(entity), entity);
        return entity;
    }

    private static String key(final QuotaLedgerEntity entity) {
        return key(entity.getTenantId(), entity.getApiKeyId(), entity.getUserId(), entity.getServiceType(),
            entity.getModel(), entity.getWindowType(), entity.getWindowStart());
    }

    private static String key(final String tenantId, final String apiKeyId, final String userId,
                              final String serviceType, final String model, final String windowType,
                              final LocalDateTime windowStart) {
        return String.join("|", String.valueOf(tenantId), String.valueOf(apiKeyId), String.valueOf(userId),
            String.valueOf(serviceType), String.valueOf(model), String.valueOf(windowType),
            String.valueOf(windowStart));
    }

    private static long nullToZero(final Long value) {
        return value == null ? 0L : value;
    }
}
