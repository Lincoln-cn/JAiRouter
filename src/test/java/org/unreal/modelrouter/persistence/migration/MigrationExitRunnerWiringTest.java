package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 迁移专用进程退出器的装配条件（issue #195）。
 *
 * <p>{@link MigrationExitRunner} 的唯一动作是调用 {@code System.exit}（迁移跑完后主动退出）。
 * {@code System.exit} 一旦在本进程内被断言就会杀掉测试 JVM，因此这里**只守装配条件**：
 * 开关打开时注册、默认不注册。真正「跑完即退出、以什么退出码退出」的行为由
 * {@link MigrateOnlyEntryProcessTest} 在**真实子进程**上覆盖（issue #193）；
 * 该开关现在由 {@code migrate} profile 自动打开，K8s Job 因此不再需要
 * {@code activeDeadlineSeconds} 兜底。
 */
@DisplayName("迁移专用进程退出器的装配条件（issue #195）")
class MigrationExitRunnerWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MigrationExitRunner.class);

    @Test
    @DisplayName("jairouter.migration.exit-after-run=true 时应注册退出器")
    void registeredWhenEnabled() {
        runner.withPropertyValues("jairouter.migration.exit-after-run=true")
                .run(context -> assertEquals(1,
                        context.getBeanNamesForType(MigrationExitRunner.class).length,
                        "开关打开时应注册退出器"));
    }

    @Test
    @DisplayName("未设置该开关时不应注册退出器（默认行为不变）")
    void absentByDefault() {
        runner.run(context -> assertEquals(0,
                context.getBeanNamesForType(MigrationExitRunner.class).length,
                "默认不应注册退出器，行为与引入本类之前一致"));
    }
}
