package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次请求的配额预留凭据（v3.1 PR-2，请求属性载体）。
 *
 * <p>预留在实例选择之前完成，结算发生在链路末端（流式 / 非流式的 token 落库点，或异常 / 中断时），
 * 两处相距较远，因此凭据随请求传递——沿用仓库既有做法（{@code API_KEY_ID_ATTRIBUTE}、
 * {@code CACHE_KEY_ATTRIBUTE}）存放在 {@link ServerHttpRequest#getAttributes()} 中，
 * 由处理器在结算点取回。凭据本身持有账本引用，因此处理器无需额外注入协作方。</p>
 *
 * <p><b>恰一次语义</b>：成功结算与失败回滚都可能被多条路径触发（成功回调、错误回调、取消回调），
 * 凭据用 {@link AtomicBoolean} 保证只有第一次调用生效，避免重复冲正。账本未启用时不会创建凭据，
 * 处理器取到 {@code null} 即天然空操作——这就是“{@code enabled=false} 零行为变更”的落点。</p>
 *
 * <p>所有静态入口对 {@code reservation == null} 均安全：账本未启用 / 未预扣的请求直接返回。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
public final class QuotaReservation {

    /** 请求属性键：本次请求的配额预留凭据 */
    public static final String ATTRIBUTE = "JAIR_QUOTA_RESERVATION";

    private final QuotaLedgerService ledgerService;
    private final QuotaDimension dimension;
    private final long estimatedTokens;
    private final AtomicBoolean settled = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);

    /**
     * 构造预留凭据。
     *
     * @param ledgerService   账本服务
     * @param dimension       账本维度
     * @param estimatedTokens 预留时使用的估算 token 数
     */
    public QuotaReservation(final QuotaLedgerService ledgerService,
                            final QuotaDimension dimension,
                            final long estimatedTokens) {
        this.ledgerService = ledgerService;
        this.dimension = dimension;
        this.estimatedTokens = Math.max(0L, estimatedTokens);
    }

    /**
     * 把凭据挂载到请求属性（供链路末端的结算点取回）。
     *
     * <p>请求为 {@code null} 或属性不可写时静默忽略：配额是旁路能力，不得影响主链路。</p>
     *
     * @param request     请求，可为 {@code null}
     * @param reservation 凭据，{@code null} 时不做任何处理
     */
    public static void attach(final ServerHttpRequest request, final QuotaReservation reservation) {
        if (request == null || reservation == null) {
            return;
        }
        try {
            request.getAttributes().put(ATTRIBUTE, reservation);
        } catch (Exception e) {
            log.warn("配额预留凭据挂载失败（忽略，结算将回退为不冲正）: error={}", e.toString());
        }
    }

    /**
     * 从请求属性取回预留凭据。
     *
     * @param request 请求，可为 {@code null}
     * @return 预留凭据；未预扣（账本未启用 / 缓存命中 / 属性缺失）时返回 {@code null}
     */
    public static QuotaReservation from(final ServerHttpRequest request) {
        if (request == null) {
            return null;
        }
        try {
            final Object reservation = request.getAttributes().get(ATTRIBUTE);
            return reservation instanceof QuotaReservation quotaReservation ? quotaReservation : null;
        } catch (Exception e) {
            log.debug("读取配额预留凭据失败: error={}", e.toString());
            return null;
        }
    }

    /**
     * 按实际用量结算（token 冲正量 = 实际 − 估算；请求数不变）。
     *
     * @param reservation  预留凭据，{@code null} 时空操作
     * @param actualTokens 实际消耗 token 数（负数按 0 处理）
     */
    public static void settleSuccess(final QuotaReservation reservation, final long actualTokens) {
        if (reservation != null) {
            reservation.settle(actualTokens, false);
        }
    }

    /**
     * 回滚整笔预留（token 冲正量 = −估算，请求数冲正量 = −1）。
     *
     * @param reservation 预留凭据，{@code null} 时空操作
     */
    public static void settleFailure(final QuotaReservation reservation) {
        if (reservation != null) {
            reservation.settle(0L, true);
        }
    }

    /**
     * 预留时使用的估算 token 数。
     *
     * @return 估算 token 数
     */
    public long estimatedTokens() {
        return estimatedTokens;
    }

    /**
     * 账本维度。
     *
     * @return 维度
     */
    public QuotaDimension dimension() {
        return dimension;
    }

    /**
     * 是否已完成结算（成功或回滚）。
     *
     * @return 已结算返回 {@code true}
     */
    public boolean isSettled() {
        return settled.get();
    }

    /**
     * 结算是否为失败回滚。
     *
     * @return 已按失败回滚返回 {@code true}
     */
    public boolean isFailed() {
        return failed.get();
    }

    /**
     * 执行恰一次结算（内部实现，异常被吞掉——账本是旁路能力，不影响请求结果）。
     *
     * @param actualTokens 实际 token 数（失败回滚时忽略）
     * @param isFailed     true = 回滚整笔预留
     */
    private void settle(final long actualTokens, final boolean isFailed) {
        if (!settled.compareAndSet(false, true)) {
            log.debug("配额结算重复调用（忽略）: apiKeyId={}", dimension.apiKeyId());
            return;
        }
        failed.set(isFailed);
        try {
            ledgerService.settle(new QuotaSettlement(dimension, estimatedTokens, actualTokens, isFailed));
        } catch (Exception e) {
            log.warn("配额结算异常（忽略，不影响请求结果）: apiKeyId={}, failed={}, error={}",
                dimension.apiKeyId(), isFailed, e.toString());
        }
    }
}
