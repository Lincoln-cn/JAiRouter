package org.unreal.modelrouter.persistence.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 迁移专用进程退出器的装配条件（issue #195）。
 *
 * <p>{@link MigrationExitRunner} 的唯一动作是调用 {@code System.exit}（迁移跑完后主动退出，
 * 否则 Job 只能等 {@code activeDeadlineSeconds} 强杀）。{@code System.exit} 一旦在本进程内被
 * 断言就会杀掉测试 JVM，因此这里**只守装配条件**：开关打开时注册、默认不注册。真正"跑完即退出"
 * 的行为由进程级实测覆盖（非 Web 模式实测：启动 66 秒、迁移日志已打出、全程 106 秒后进程自行退出）。
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
