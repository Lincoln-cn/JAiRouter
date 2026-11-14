package org.unreal.modelrouter.config.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 健康探针的运行时配置。
 *
 * <p>刻意做成可绑定的 properties 而不是散落的 {@code @Value}：这些开关要在控制台里可读可改
 * （{@code /api/config/health}），{@code @Value} 字段改配置后不重启不生效。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.health")
public class HealthProbeProperties {

    /** 就绪探针相关开关。 */
    private Readiness readiness = new Readiness();

    @Data
    public static class Readiness {

        /**
         * 就绪探针是否聚合 {@code db} / {@code redis} 依赖。
         *
         * <p>默认关闭（与 3.8.6 之前的行为一致）。打开后依赖故障会让副本退出就绪集合，
         * 代价是依赖故障期间滚动发布会卡住 —— 所以只在「依赖故障必须摘流」的部署里开。</p>
         */
        private boolean includeDependencies = false;
    }
}
