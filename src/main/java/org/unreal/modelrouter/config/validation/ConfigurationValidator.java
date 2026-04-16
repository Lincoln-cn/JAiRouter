package org.unreal.modelrouter.config.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 配置验证器
 * 在应用启动时验证关键配置的合理性
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.config.validation.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigurationValidator {

    private static final String ASCII_ART_BORDER = "\n" +
            "╔══════════════════════════════════════════════════════════════════════════════╗\n" +
            "║                                                                              ║\n" +
            "║                            JAiRouter 配置验证                                ║\n" +
            "║                                                                              ║\n" +
            "╚══════════════════════════════════════════════════════════════════════════════╝\n";

    private final List<ConfigurationValidationRule> validationRules = new ArrayList<>();

    public ConfigurationValidator() {
        // 初始化验证规则
        initValidationRules();
    }

    /**
     * 初始化验证规则
     */
    private void initValidationRules() {
        // 限流配置验证规则
        validationRules.add(createRule(
            "rate-limit-capacity",
            "限流容量",
            () -> {
                String capacity = System.getenv("RATE_LIMIT_CAPACITY");
                if (capacity != null) {
                    try {
                        int cap = Integer.parseInt(capacity);
                        if (cap < 1) {
                            return ValidationResult.error("限流容量必须大于 0");
                        }
                        if (cap > 100000) {
                            return ValidationResult.warn("限流容量过大，可能导致内存压力");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("限流容量必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        validationRules.add(createRule(
            "rate-limit-rate",
            "限流速率",
            () -> {
                String rate = System.getenv("RATE_LIMIT_RATE");
                if (rate != null) {
                    try {
                        int r = Integer.parseInt(rate);
                        if (r < 1) {
                            return ValidationResult.error("限流速率必须大于 0");
                        }
                        if (r > 10000) {
                            return ValidationResult.warn("限流速率过大，可能导致系统过载");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("限流速率必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        // 熔断器配置验证规则
        validationRules.add(createRule(
            "circuit-breaker-threshold",
            "熔断器失败阈值",
            () -> {
                String threshold = System.getenv("CIRCUIT_BREAKER_FAILURE_THRESHOLD");
                if (threshold != null) {
                    try {
                        int t = Integer.parseInt(threshold);
                        if (t < 1) {
                            return ValidationResult.error("熔断器失败阈值必须大于 0");
                        }
                        if (t > 100) {
                            return ValidationResult.warn("熔断器失败阈值过大，可能无法及时熔断");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("熔断器失败阈值必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        validationRules.add(createRule(
            "circuit-breaker-timeout",
            "熔断器超时时间",
            () -> {
                String timeout = System.getenv("CIRCUIT_BREAKER_TIMEOUT");
                if (timeout != null) {
                    try {
                        long t = Long.parseLong(timeout);
                        if (t < 1000) {
                            return ValidationResult.error("熔断器超时时间不能小于 1000ms");
                        }
                        if (t > 300000) {
                            return ValidationResult.warn("熔断器超时时间过长（>5 分钟）");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("熔断器超时时间必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        // JWT 配置验证规则
        validationRules.add(createRule(
            "jwt-expiration",
            "JWT 过期时间",
            () -> {
                String expiration = System.getenv("JWT_EXPIRATION_MINUTES");
                if (expiration != null) {
                    try {
                        int exp = Integer.parseInt(expiration);
                        if (exp < 1) {
                            return ValidationResult.error("JWT 过期时间必须大于 0 分钟");
                        }
                        if (exp > 1440) {
                            return ValidationResult.warn("JWT 过期时间过长（>24 小时）");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("JWT 过期时间必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        // 服务器端口验证规则
        validationRules.add(createRule(
            "server-port",
            "服务器端口",
            () -> {
                String port = System.getenv("SERVER_PORT");
                if (port != null) {
                    try {
                        int p = Integer.parseInt(port);
                        if (p < 1 || p > 65535) {
                            return ValidationResult.error("服务器端口必须在 1-65535 之间");
                        }
                        if (p < 1024) {
                            return ValidationResult.warn("服务器端口小于 1024，可能需要 root 权限");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("服务器端口必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));

        // 线程池配置验证规则
        validationRules.add(createRule(
            "thread-pool-size",
            "线程池大小",
            () -> {
                String poolSize = System.getenv("SERVER_TOMCAT_THREADS_MAX");
                if (poolSize != null) {
                    try {
                        int size = Integer.parseInt(poolSize);
                        if (size < 1) {
                            return ValidationResult.error("线程池大小必须大于 0");
                        }
                        if (size > 500) {
                            return ValidationResult.warn("线程池过大，可能导致资源浪费");
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.error("线程池大小必须是数字");
                    }
                }
                return ValidationResult.ok();
            }
        ));
    }

    /**
     * 创建验证规则
     */
    private ConfigurationValidationRule createRule(String name, String displayName, Supplier<ValidationResult> validator) {
        return new ConfigurationValidationRule() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public ValidationResult validate() {
                return validator.get();
            }
        };
    }

    /**
     * 应用启动完成后执行验证
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        log.info(ASCII_ART_BORDER);
        
        int totalRules = validationRules.size();
        int passedRules = 0;
        int warningRules = 0;
        int errorRules = 0;

        for (ConfigurationValidationRule rule : validationRules) {
            try {
                ValidationResult result = rule.validate();
                
                switch (result.getLevel()) {
                    case ERROR:
                        log.error("❌ [{}] {} - {}", rule.getName(), rule.getDisplayName(), result.getMessage());
                        errorRules++;
                        break;
                    case WARN:
                        log.warn("⚠️  [{}] {} - {}", rule.getName(), rule.getDisplayName(), result.getMessage());
                        warningRules++;
                        passedRules++;  // 警告也算通过，不阻止启动
                        break;
                    case OK:
                        log.debug("✓ [{}] {} - 通过", rule.getName(), rule.getDisplayName());
                        passedRules++;
                        break;
                }
            } catch (Exception e) {
                log.error("❌ [{}] {} - 验证异常：{}", 
                    rule.getName(), rule.getDisplayName(), e.getMessage());
                errorRules++;
            }
        }

        // 打印汇总
        log.info("\n" +
            "╔══════════════════════════════════════════════════════════════════════════════╗\n" +
            "║  配置验证汇总                                                                ║\n" +
            "║                                                                              ║\n" +
            "║  总规则数：" + String.format("%-54d", totalRules) + "║\n" +
            "║  通过：" + String.format("%-55d", passedRules) + "║\n" +
            "║  警告：" + String.format("%-55d", warningRules) + "║\n" +
            "║  错误：" + String.format("%-55d", errorRules) + "║\n" +
            "╚══════════════════════════════════════════════════════════════════════════════╝\n");

        if (errorRules > 0) {
            log.error("⚠️  检测到 {} 个配置错误，请检查并修正配置后重启应用", errorRules);
        }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final Level level;
        private final String message;

        private ValidationResult(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(Level.OK, "配置正确");
        }

        public static ValidationResult warn(String message) {
            return new ValidationResult(Level.WARN, message);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(Level.ERROR, message);
        }

        public Level getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 验证级别
     */
    public enum Level {
        OK, WARN, ERROR
    }

    /**
     * 验证规则接口
     */
    public interface ConfigurationValidationRule {
        String getName();
        String getDisplayName();
        ValidationResult validate();
    }
}
