package org.unreal.modelrouter.config.core.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置来源追踪器
 * 记录配置值的来源，帮助用户了解配置加载优先级
 *
 * <p>v2.8.6 新增：配置来源追踪机制</p>
 *
 * <h3>配置加载优先级（从高到低）：</h3>
 * <ol>
 *   <li>命令行参数（--property=value）</li>
 *   <li>系统属性（-Dproperty=value）</li>
 *   <li>环境变量（PROPERTY_NAME）</li>
 *   <li>外部配置文件（/app/config/*.yml）</li>
 *   <li>环境特定配置（application-{profile}.yml）</li>
 *   <li>模块配置文件（classpath:config/*.yml）</li>
 *   <li>默认配置（application.yml）</li>
 * </ol>
 */
@Component
public class ConfigSourceTracker implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ConfigSourceTracker.class);

    private final Environment environment;

    /**
     * 关键配置项列表（用于追踪来源）
     */
    private static final String[] TRACKED_PROPERTIES = {
        "server.port",
        "jairouter.security.jwt.secret",
        "jairouter.security.jwt.expiration-minutes",
        "jairouter.security.jwt.jwt-header",
        "model.load-balance.type",
        "model.rate-limit.enabled",
        "model.circuit-breaker.enabled",
        "store.type",
        "monitoring.enabled",
        "spring.profiles.active"
    };

    public ConfigSourceTracker(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            log.warn("Environment 不是 ConfigurableEnvironment，无法追踪配置来源");
            return;
        }

        ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) environment;

        log.info("====== 配置加载来源追踪 ======");

        Map<String, String> configSources = new LinkedHashMap<>();

        for (String prop : TRACKED_PROPERTIES) {
            String value = environment.getProperty(prop);
            if (value != null) {
                String source = findPropertySource(prop);
                configSources.put(prop, source);

                // 对敏感配置隐藏值
                if (isSensitiveProperty(prop)) {
                    log.info("  {} = [SENSITIVE] (来源: {})", prop, source);
                } else {
                    log.info("  {} = '{}' (来源: {})", prop, value, source);
                }
            }
        }

        log.info("====== 追踪结束 ======");
    }

    /**
     * 查找配置值的来源
     */
    private String findPropertySource(String propertyName) {
        if (!(environment instanceof ConfigurableEnvironment)) {
            return "未知来源";
        }

        ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) environment;

        // 遍历所有 PropertySource 查找配置来源
        for (PropertySource<?> ps : configurableEnv.getPropertySources()) {
            if (ps.containsProperty(propertyName)) {
                return formatPropertySourceName(ps);
            }
        }
        return "默认值";
    }

    /**
     * 格式化 PropertySource 名称
     */
    private String formatPropertySourceName(PropertySource<?> ps) {
        String name = ps.getName();

        if (name.contains("commandLineArgs")) {
            return "命令行参数";
        }
        if (name.contains("systemProperties")) {
            return "系统属性";
        }
        if (name.contains("systemEnvironment")) {
            return "环境变量";
        }
        if (name.contains("/app/config")) {
            return "外部配置文件(" + extractFileName(name) + ")";
        }
        if (name.contains("applicationConfig")) {
            return "环境配置文件(" + extractFileName(name) + ")";
        }
        if (name.contains("classpath")) {
            return "模块配置文件(" + extractFileName(name) + ")";
        }
        if (name.contains("random")) {
            return "随机生成";
        }

        return name;
    }

    /**
     * 从 PropertySource 名称中提取文件名
     */
    private String extractFileName(String name) {
        if (name.contains("[")) {
            int start = name.indexOf("[");
            int end = name.indexOf("]");
            if (start > 0 && end > start) {
                return name.substring(start + 1, end);
            }
        }
        if (name.contains("/")) {
            int lastSlash = name.lastIndexOf("/");
            if (lastSlash >= 0) {
                return name.substring(lastSlash + 1);
            }
        }
        return name;
    }

    /**
     * 判断是否是敏感配置
     */
    private boolean isSensitiveProperty(String propertyName) {
        return propertyName.contains("secret") ||
               propertyName.contains("password") ||
               propertyName.contains("token") ||
               propertyName.contains("key");
    }

    /**
     * 获取所有配置来源信息
     * 用于 API 返回
     */
    public Map<String, Object> getConfigSourcesInfo() {
        Map<String, Object> result = new HashMap<>();

        result.put("priorityOrder", new String[]{
            "命令行参数",
            "系统属性",
            "环境变量",
            "外部配置文件",
            "环境特定配置",
            "模块配置文件",
            "默认配置"
        });

        Map<String, String> tracked = new LinkedHashMap<>();
        for (String prop : TRACKED_PROPERTIES) {
            String value = environment.getProperty(prop);
            if (value != null) {
                tracked.put(prop, findPropertySource(prop));
            }
        }
        result.put("trackedProperties", tracked);

        return result;
    }
}