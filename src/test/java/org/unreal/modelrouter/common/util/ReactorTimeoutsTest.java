package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-P1：冷路径 Reactor block 统一超时常量。
 */
@DisplayName("ReactorTimeouts 常量")
class ReactorTimeoutsTest {

    @Test
    @DisplayName("BLOCK 为 5s，STARTUP_BLOCK 为 30s")
    void constants_areReasonable() {
        assertEquals(Duration.ofSeconds(5), ReactorTimeouts.BLOCK);
        assertEquals(Duration.ofSeconds(30), ReactorTimeouts.STARTUP_BLOCK);
        assertTrue(ReactorTimeouts.BLOCK.toSeconds() >= 1);
        assertTrue(ReactorTimeouts.STARTUP_BLOCK.toSeconds() >= ReactorTimeouts.BLOCK.toSeconds());
    }

    @Test
    @DisplayName("JwtCleanup / JwtDataRecovery 引用统一超时")
    void consumers_referenceTimeout() throws Exception {
        Class<?> cleanup = Class.forName(
                "org.unreal.modelrouter.auth.security.service.impl.JwtCleanupServiceImpl");
        assertEquals(ReactorTimeouts.BLOCK,
                cleanup.getField("BLOCK_TIMEOUT").get(null));
        Class<?> recovery = Class.forName(
                "org.unreal.modelrouter.auth.security.config.JwtDataRecoveryConfiguration");
        assertEquals(ReactorTimeouts.STARTUP_BLOCK,
                recovery.getField("BLOCK_TIMEOUT").get(null));
    }
}
