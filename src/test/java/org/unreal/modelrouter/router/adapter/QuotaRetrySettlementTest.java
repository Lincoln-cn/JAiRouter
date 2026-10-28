package org.unreal.modelrouter.router.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.auth.security.quota.QuotaSettlement;

/**
 * #719 守卫测试：重试期间不得结算，结算只落在最终结果上。
 *
 * <p>修复前 {@code NonStreamingRequestProcessor} 在每次尝试的 {@code doOnError} 里就
 * {@code settleFailure}，凭据的 CAS 随即置成「已结算」，于是第二次尝试成功后的
 * {@code settleSuccess} 被静默忽略：账本 requests −1 / tokens 0，而 token_usage 表记了真实用量。</p>
 */
@DisplayName("#719 重试与配额结算")
class QuotaRetrySettlementTest {

    private static final long ESTIMATED = 100L;

    private QuotaReservation newReservation(final QuotaLedgerService ledger) {
        return new QuotaReservation(ledger, QuotaDimension.ofApiKey("key-719"), ESTIMATED);
    }

    @Test
    @DisplayName("还有下一次尝试时不结算（重试期间账本保持预留状态）")
    void retryingAttemptDoesNotSettle() {
        final QuotaLedgerService ledger = mock(QuotaLedgerService.class);
        final QuotaReservation reservation = newReservation(ledger);

        BaseAdapter.settleOnFinalGiveUp(reservation, true);

        verifyNoInteractions(ledger);
        assertFalse(reservation.isSettled(), "重试期间凭据必须保持未结算，否则后续成功结算会被 CAS 吞掉");
    }

    @Test
    @DisplayName("确定不再重试时回滚整笔预留")
    void finalGiveUpRollsBackTheReservation() {
        final QuotaLedgerService ledger = mock(QuotaLedgerService.class);
        final QuotaReservation reservation = newReservation(ledger);

        BaseAdapter.settleOnFinalGiveUp(reservation, false);

        final ArgumentCaptor<QuotaSettlement> captor = ArgumentCaptor.forClass(QuotaSettlement.class);
        verify(ledger).settle(captor.capture());
        final QuotaSettlement settlement = captor.getValue();
        assertTrue(settlement.failed());
        assertEquals(-ESTIMATED, settlement.tokenDelta(), "失败回滚必须退回整笔估算");
        assertEquals(-1L, settlement.requestDelta(), "失败回滚必须退回那一次请求计数");
    }

    @Test
    @DisplayName("重试后成功：只有一次结算，且按实际用量冲正、请求数不退回")
    void retryThenSuccessSettlesOnceWithActualUsage() {
        final QuotaLedgerService ledger = mock(QuotaLedgerService.class);
        final QuotaReservation reservation = newReservation(ledger);

        // 第 1 次尝试失败：修复后不再结算（willRetry = true）
        BaseAdapter.settleOnFinalGiveUp(reservation, true);
        verify(ledger, never()).settle(any());

        // 第 2 次尝试成功：token 落库点按实际用量结算
        QuotaReservation.settleSuccess(reservation, 42L);

        final ArgumentCaptor<QuotaSettlement> captor = ArgumentCaptor.forClass(QuotaSettlement.class);
        verify(ledger).settle(captor.capture());
        final QuotaSettlement settlement = captor.getValue();
        assertFalse(settlement.failed());
        assertEquals(42L, settlement.actualTokens());
        assertEquals(42L - ESTIMATED, settlement.tokenDelta(), "token 冲正量 = 实际 − 估算");
        assertEquals(0L, settlement.requestDelta(), "成功结算不退回请求计数");
        assertFalse(reservation.isFailed());
    }

    @Test
    @DisplayName("没有凭据（配额未启用）时任何结算入口都是空操作")
    void nullReservationIsNoOp() {
        BaseAdapter.settleOnFinalGiveUp(null, false);
        BaseAdapter.settleOnFinalGiveUp(null, true);
        QuotaReservation.settleFailure(null);
        QuotaReservation.settleSuccess(null, 10L);
    }
}
