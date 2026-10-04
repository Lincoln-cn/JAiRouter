package org.unreal.modelrouter.persistence.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 迁移专用进程的退出器（issue #195）。
 *
 * <p>以 {@code --spring.main.web-application-type=none} 启动、只跑一次启动期迁移的进程，
 * 在迁移跑完后**不会自行退出**：应用里有若干非守护线程（调度器/线程池）会一直把 JVM 留住。
 * 实测证据：非 Web 模式下 {@code Started ModelRouterApplication in 63.5s}、迁移日志已打出，
 * 但 150 秒后进程仍在运行 ⇒ 部署为 K8s Job 时只能等 {@code activeDeadlineSeconds} 强杀，
 * Job 永远不会以成功结束。
 *
 * <p>本类监听 {@link ApplicationReadyEvent}，因此**必然在所有 {@code ApplicationRunner}
 * （含 {@code CompatibilitySchemaMigrator}）之后**执行，不会与迁移抢时序。
 *
 * <p>默认关闭：不加该开关时行为与引入本类之前逐字相同。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "jairouter.migration.exit-after-run", havingValue = "true")
public class MigrationExitRunner {

    private final ConfigurableApplicationContext applicationContext;

    public MigrationExitRunner(final ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void exitAfterStartup() {
        log.info("jairouter.migration.exit-after-run=true：启动与迁移已完成，主动退出进程");
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
