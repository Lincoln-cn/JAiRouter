package org.unreal.modelrouter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.unreal.modelrouter.auth.cli.KeyGeneratorCommandLine;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan({
    "org.unreal.modelrouter.config",
    "org.unreal.modelrouter.monitor.callhistory.config"
})
public class ModelRouterApplication {
    /** Private constructor to prevent instantiation. */
    private ModelRouterApplication() {}

    public static void main(final String[] args) {
        if (KeyGeneratorCommandLine.tryGenerate(args)) {
            return;
        }
        try {
            SpringApplication.run(ModelRouterApplication.class, args);
            // 正常模式下 run 返回后应用继续运行（Web 模式监听端口；迁移专用模式由
            // MigrationExitRunner 在 ApplicationReadyEvent 里主动退出），此处不做任何收尾。
        } catch (Throwable ex) {
            // 启动或迁移失败必须以**非 0** 退出：否则非守护线程（调度器 / 线程池）会把 JVM 留住，
            // 部署成 K8s Job 时只能靠 activeDeadlineSeconds 强杀，Job 的成败无从判读（issue #193）。
            // 完整的失败分析与堆栈已由 Spring Boot 打印，这里只补一行可搜的结论。
            log.error("应用启动失败，进程以退出码 1 结束：{}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            System.exit(1);
        }
    }

}