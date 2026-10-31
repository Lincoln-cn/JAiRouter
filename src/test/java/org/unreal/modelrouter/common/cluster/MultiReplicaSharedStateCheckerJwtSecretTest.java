package org.unreal.modelrouter.common.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多副本下 JWT 签名密钥必须共用的启动告警（#919）。
 *
 * <p>{@code jairouter.security.jwt.secret} 的默认值是 {@code ${JWT_SECRET:${random.uuid}}}：
 * 未设置环境变量时每个副本各自随机生成，副本 A 签发的令牌在副本 B 上验签失败（间歇性 401）。
 * 生产 profile 有门闩兜底，dev / staging 没有，因此补一条启动告警。</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class MultiReplicaSharedStateCheckerJwtSecretTest {

    private static MultiReplicaSharedStateChecker checker() {
        return new MultiReplicaSharedStateChecker(new MockEnvironment());
    }

    @Test
    @DisplayName("919-001: 多副本且未设置 JWT_SECRET → 告警说明令牌无法跨副本")
    void warnsWhenMultiReplicaWithoutJwtSecret(final CapturedOutput output) {
        checker().warnIfJwtSecretNotShared(true, null);

        assertTrue(output.getOut().contains("未设置 JWT_SECRET"), output.getOut());
        assertTrue(output.getOut().contains("间歇性 401"), output.getOut());
    }

    @Test
    @DisplayName("919-002: 设置了 JWT_SECRET 就不再告警")
    void staysSilentWhenJwtSecretConfigured(final CapturedOutput output) {
        checker().warnIfJwtSecretNotShared(true, "shared-secret-for-all-replicas");

        assertFalse(output.getOut().contains("未设置 JWT_SECRET"), output.getOut());
    }

    @Test
    @DisplayName("919-003: 单副本不打扰")
    void staysSilentForSingleReplica(final CapturedOutput output) {
        checker().warnIfJwtSecretNotShared(false, null);

        assertFalse(output.getOut().contains("未设置 JWT_SECRET"), output.getOut());
    }
}
