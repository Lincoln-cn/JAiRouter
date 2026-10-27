package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StartupSecretKeyChecker#removedEnvNamesPresent(Set)} 的单元测试（issue #674）。
 *
 * <p>被守护的性质：启动时能准确认出「仍在用已移除兼容变量名」的部署，从而打出一条明确的
 * warn，而不是让运维以为设置成功。抽成纯函数是为了能在测试里喂任意变量名集合 ——
 * {@code System.getenv()} 在进程内不可控。</p>
 */
@DisplayName("已移除兼容环境变量名的识别（#674）")
class StartupSecretKeyCheckerRemovedEnvTest {

    @Test
    @DisplayName("命中旧名字时按登记顺序返回，正常名字不误报")
    void reportsRemovedNamesOnly() {
        final List<String> present = StartupSecretKeyChecker.removedEnvNamesPresent(Set.of(
                "JWT_SECRET", "INITIAL_ADMIN_PASSWORD", "PROD_JWT_SECRET", "DEV_JWT_SECRET"));

        assertEquals(List.of("DEV_JWT_SECRET", "PROD_JWT_SECRET"), present);
    }

    @Test
    @DisplayName("四个旧名字全部命中时都报出来（含 ADMIN_PASSWORD）")
    void reportsAllFourRemovedNames() {
        final List<String> present = StartupSecretKeyChecker.removedEnvNamesPresent(Set.of(
                "ADMIN_PASSWORD", "PROD_JWT_SECRET", "STAGING_JWT_SECRET", "DEV_JWT_SECRET"));

        assertEquals(StartupSecretKeyChecker.REMOVED_ENV_NAMES, present);
    }

    @Test
    @DisplayName("只设受支持的名字时保持安静")
    void quietWhenOnlySupportedNames() {
        assertTrue(StartupSecretKeyChecker.removedEnvNamesPresent(
                Set.of("JWT_SECRET", "INITIAL_ADMIN_PASSWORD", "PATH", "HOME")).isEmpty(),
                "受支持的名字不得被误判成已移除的别名");
    }

    @Test
    @DisplayName("空集合与 null 不炸")
    void handlesEmptyInput() {
        assertTrue(StartupSecretKeyChecker.removedEnvNamesPresent(Set.of()).isEmpty());
        assertTrue(StartupSecretKeyChecker.removedEnvNamesPresent(null).isEmpty());
    }
}
