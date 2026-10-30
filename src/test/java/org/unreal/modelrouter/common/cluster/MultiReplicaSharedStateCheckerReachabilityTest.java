package org.unreal.modelrouter.common.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动自检里「共享态依赖可达性」的接线（#825）。
 *
 * <p>钉住四件事：staging/prod 下连不上 Redis 会 fail-fast；dev 下不检查；
 * WARN 模式只告警；Redis 真在监听时通过。</p>
 */
class MultiReplicaSharedStateCheckerReachabilityTest {

    private static MockEnvironment stagingEnvironment(final int redisPort, final String mode) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");
        environment.setProperty("spring.data.redis.host", "127.0.0.1");
        environment.setProperty("spring.data.redis.port", String.valueOf(redisPort));
        environment.setProperty("jairouter.security.jwt.persistence.redis.enabled", "true");
        if (mode != null) {
            environment.setProperty(SharedStateReachabilityGate.MODE_KEY, mode);
        }
        return environment;
    }

    private static int closedPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    @Test
    @DisplayName("SRC-825-001: staging 下开关开启且 Redis 连不上 → fail-fast")
    void stagingFailsFastWhenRedisMissing() throws IOException {
        final MockEnvironment environment = stagingEnvironment(closedPort(), null);
        final MultiReplicaSharedStateChecker checker = new MultiReplicaSharedStateChecker(environment);
        final IllegalStateException error = assertThrows(IllegalStateException.class, checker::check);
        assertTrue(error.getMessage().contains("jairouter.security.jwt.persistence.redis.enabled"),
                error.getMessage());
    }

    @Test
    @DisplayName("SRC-825-002: WARN 模式连不上只告警，不阻断启动")
    void warnModeDoesNotBlockStartup() throws IOException {
        final MockEnvironment environment = stagingEnvironment(closedPort(), "WARN");
        final MultiReplicaSharedStateChecker checker = new MultiReplicaSharedStateChecker(environment);
        assertDoesNotThrow(checker::check);
    }

    @Test
    @DisplayName("SRC-825-003: dev profile 不做依赖检查")
    void devProfileSkipsCheck() throws IOException {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.data.redis.host", "127.0.0.1");
        environment.setProperty("spring.data.redis.port", String.valueOf(closedPort()));
        environment.setProperty("jairouter.security.jwt.persistence.redis.enabled", "true");
        final MultiReplicaSharedStateChecker checker = new MultiReplicaSharedStateChecker(environment);
        assertDoesNotThrow(checker::check);
    }

    @Test
    @DisplayName("SRC-825-004: Redis 真在监听时通过")
    void reachableRedisPasses() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            final MockEnvironment environment = stagingEnvironment(serverSocket.getLocalPort(), null);
            final MultiReplicaSharedStateChecker checker = new MultiReplicaSharedStateChecker(environment);
            assertDoesNotThrow(checker::check);
        }
    }

    @Test
    @DisplayName("SRC-825-005: 没有任何 Redis 开关开启时跳过探测（prod 默认即如此）")
    void noEnabledSwitchSkipsProbe() throws IOException {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.data.redis.host", "127.0.0.1");
        environment.setProperty("spring.data.redis.port", String.valueOf(closedPort()));
        final MultiReplicaSharedStateChecker checker = new MultiReplicaSharedStateChecker(environment);
        assertDoesNotThrow(checker::check);
    }
}
