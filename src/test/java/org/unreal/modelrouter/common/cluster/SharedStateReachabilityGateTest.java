package org.unreal.modelrouter.common.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享态依赖可达性判定（#825）。
 */
class SharedStateReachabilityGateTest {

    @Test
    @DisplayName("RA-825-001: 未配置时默认 FAIL，无法识别的值回落 WARN")
    void parseModeDefaults() {
        assertEquals(SharedStateReachabilityGate.Mode.FAIL, SharedStateReachabilityGate.parseMode(null));
        assertEquals(SharedStateReachabilityGate.Mode.FAIL, SharedStateReachabilityGate.parseMode("  "));
        assertEquals(SharedStateReachabilityGate.Mode.OFF, SharedStateReachabilityGate.parseMode("off"));
        assertEquals(SharedStateReachabilityGate.Mode.WARN, SharedStateReachabilityGate.parseMode("Warn"));
        assertEquals(SharedStateReachabilityGate.Mode.FAIL, SharedStateReachabilityGate.parseMode("FAIL"));
        assertEquals(SharedStateReachabilityGate.Mode.WARN, SharedStateReachabilityGate.parseMode("bogus"));
    }

    @Test
    @DisplayName("RA-825-002: 只有 staging / prod 触发检查")
    void appliesToGuardedProfilesOnly() {
        assertTrue(SharedStateReachabilityGate.appliesTo(new String[] {"prod"}));
        assertTrue(SharedStateReachabilityGate.appliesTo(new String[] {"staging"}));
        assertTrue(SharedStateReachabilityGate.appliesTo(new String[] {"production"}));
        assertTrue(SharedStateReachabilityGate.appliesTo(new String[] {"dev", "PROD"}));
        assertFalse(SharedStateReachabilityGate.appliesTo(new String[] {"dev"}));
        assertFalse(SharedStateReachabilityGate.appliesTo(new String[] {"test"}));
        assertFalse(SharedStateReachabilityGate.appliesTo(new String[] {}));
        assertFalse(SharedStateReachabilityGate.appliesTo(null));
    }

    @Test
    @DisplayName("RA-825-003: 只挑出已开启的共享态开关，顺序与清单一致")
    void enabledSwitchesPicksOnlyEnabledOnes() {
        final Map<String, Boolean> switches = new LinkedHashMap<>();
        switches.put("jairouter.security.jwt.persistence.redis.enabled", true);
        switches.put("jairouter.security.cache.redis.enabled", false);
        switches.put("jairouter.persistence.redis.enabled", true);

        final List<String> enabled = SharedStateReachabilityGate.enabledSwitches(switches);
        assertEquals(List.of(
                "jairouter.security.jwt.persistence.redis.enabled",
                "jairouter.persistence.redis.enabled"), enabled);
        assertTrue(SharedStateReachabilityGate.enabledSwitches(null).isEmpty());
        assertTrue(SharedStateReachabilityGate.enabledSwitches(Map.of()).isEmpty());
    }

    @Test
    @DisplayName("RA-825-004: 超时配置越界钳制，非法值回落默认")
    void parseTimeoutClamps() {
        assertEquals(2000L, SharedStateReachabilityGate.parseTimeoutMs(null));
        assertEquals(2000L, SharedStateReachabilityGate.parseTimeoutMs("abc"));
        assertEquals(200L, SharedStateReachabilityGate.parseTimeoutMs("50"));
        assertEquals(30000L, SharedStateReachabilityGate.parseTimeoutMs("999999"));
        assertEquals(3000L, SharedStateReachabilityGate.parseTimeoutMs("3000"));
    }

    @Test
    @DisplayName("RA-825-005: 端口可连接判可达，关闭的端口/非法入参判不可达")
    void isReachableProbesTcp() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            assertTrue(SharedStateReachabilityGate.isReachable("127.0.0.1", serverSocket.getLocalPort(), 1000L),
                    "监听中的端口必须判可达");
        }
        // 端口关闭：bind 后立即释放，拿到一个大概率没人监听的端口
        final int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            closedPort = serverSocket.getLocalPort();
        }
        assertFalse(SharedStateReachabilityGate.isReachable("127.0.0.1", closedPort, 500L),
                "没有监听的端口必须判不可达");
        assertFalse(SharedStateReachabilityGate.isReachable(null, 6379, 100L));
        assertFalse(SharedStateReachabilityGate.isReachable("127.0.0.1", 0, 100L));
        assertFalse(SharedStateReachabilityGate.isReachable("127.0.0.1", 70000, 100L));
    }

    @Test
    @DisplayName("RA-825-006: 失败消息要含依赖地址、开关清单与逃生口")
    void describeCarriesActionableDetails() {
        final String message = SharedStateReachabilityGate.describe("redis.internal", 6380,
                List.of("jairouter.security.jwt.persistence.redis.enabled"),
                new String[] {"staging"});
        assertTrue(message.contains("redis.internal:6380"), message);
        assertTrue(message.contains("jairouter.security.jwt.persistence.redis.enabled"), message);
        assertTrue(message.contains("staging"), message);
        assertTrue(message.contains(SharedStateReachabilityGate.MODE_KEY), message);
    }
}
