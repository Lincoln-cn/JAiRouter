package org.unreal.modelrouter.config.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HealthSharingResolver} 的语义测试（#1137）。
 *
 * <p>热点断言的是「异常路径」：本地探测抖动、共享上报过期、共享视图为空 ——
 * 这三类情形决定多副本下会不会误摘实例。</p>
 */
@DisplayName("上游健康共享判定语义（#1137）")
class HealthSharingResolverTest {

    private static final long GRACE = 30_000L;
    private static final long NOW = 1_700_000_000_000L;

    @Nested
    @DisplayName("local 语义：只看本副本")
    class LocalMode {

        @Test
        @DisplayName("本地健康即健康，忽略共享视图")
        void localHealthyIgnoresShared() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_LOCAL, 5, GRACE, true, 0, 0L, NOW));
        }

        @Test
        @DisplayName("本地不健康即不健康，即使共享视图全健康")
        void localUnhealthyIgnoresShared() {
            assertFalse(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_LOCAL, 1, GRACE, false, 10, NOW, NOW));
        }
    }

    @Nested
    @DisplayName("shared 语义：共享视图优先")
    class SharedMode {

        @Test
        @DisplayName("共享视图有健康副本即健康")
        void sharedHealthyWins() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_SHARED, 3, GRACE, false, 1, NOW, NOW));
        }

        @Test
        @DisplayName("共享视图为空时退回本地判定，避免全判不健康")
        void emptySharedFallsBackToLocal() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_SHARED, 1, GRACE, true, 0, 0L, NOW));
            assertFalse(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_SHARED, 1, GRACE, false, 0, 0L, NOW));
        }

        @Test
        @DisplayName("共享上报过期后不再采信")
        void staleReportIsIgnored() {
            assertFalse(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_SHARED, 1, GRACE, false, 1, NOW - GRACE - 1, NOW));
        }
    }

    @Nested
    @DisplayName("quorum 语义：本地健康或达到容忍值")
    class QuorumMode {

        @Test
        @DisplayName("本地健康直接放行，不看共享")
        void localHealthyShortCircuits() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_QUORUM, 3, GRACE, true, 0, 0L, NOW));
        }

        @Test
        @DisplayName("本地不健康但共享健康副本数达到容忍值")
        void sharedReachesTolerance() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_QUORUM, 2, GRACE, false, 2, NOW, NOW));
        }

        @Test
        @DisplayName("共享健康副本数不足容忍值仍判不健康")
        void sharedBelowTolerance() {
            assertFalse(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_QUORUM, 2, GRACE, false, 1, NOW, NOW));
        }

        @Test
        @DisplayName("共享上报过期后即使副本数够也不采信")
        void staleSharedFailsQuorum() {
            assertFalse(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_QUORUM, 1, GRACE, false, 5, NOW - GRACE - 1, NOW));
        }

        @Test
        @DisplayName("容忍值小于 1 时按 1 兜底，不能被配置成 0 从而永不健康")
        void toleranceIsClampedToOne() {
            assertTrue(HealthSharingResolver.resolve(
                    HealthSharingResolver.MODE_QUORUM, 0, GRACE, false, 1, NOW, NOW));
        }

        @Test
        @DisplayName("未知语义按 quorum 处理")
        void unknownModeFallsBackToQuorum() {
            assertTrue(HealthSharingResolver.resolve(
                    "bogus", 1, GRACE, false, 1, NOW, NOW));
        }
    }
}
