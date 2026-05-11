package org.unreal.modelrouter.config.core.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置完整性校验器
 * 校验各模块配置的完整性和正确性
 *
 * <p>v2.8.7 新增：配置校验机制</p>
 *
 * <h3>校验规则：</h3>
 * <ul>
 *   <li>服务器端口：1-65535 范围</li>
 *   <li>负载均衡类型：round-robin, weighted, least-connections, ip-hash</li>
 *   <li>限流算法：token-bucket, sliding-window</li>
 *   <li>熔断器阈值：failureThreshold > 0, timeout > 0</li>
 *   <li>存储类型：jpa, redis</li>
 * </ul>
 */

/**
 * 配置完整性校验器
 * 校验各模块配置的完整性和正确性
 *
 * <p>v2.8.7 新增：配置校验机制</p>
 *
 * <h3>校验规则：</h3>
 * <ul>
 *   <li>服务器端口：1-65535 范围</li>
 *   <li>负载均衡类型：round-robin, weighted, least-connections, ip-hash</li>
 *   <li>限流算法：token-bucket, sliding-window</li>
 *   <li>熔断器阈值：failureThreshold > 0, timeout > 0</li>
 *   <li>存储类型：jpa, redis</li>
 * </ul>
 */
@Component
public class ConfigIntegrityValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ConfigIntegrityValidator.class);

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${model.load-balance.type:round-robin}")
    private String loadBalanceType;

    @Value("${model.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${model.rate-limit.algorithm:token-bucket}")
    private String rateLimitAlgorithm;

    @Value("${model.circuit-breaker.enabled:true}")
    private boolean circuitBreakerEnabled;

    @Value("${model.circuit-breaker.failureThreshold:5}")
    private int failureThreshold;

    @Value("${model.circuit-breaker.timeout:60000}")
    private long circuitBreakerTimeout;

    @Value("${store.type:jpa}")
    private String storeType;

    @Value("${monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${jairouter.tracing.enabled:true}")
    private boolean tracingEnabled;

    @Value("${jairouter.config.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!validationEnabled) {
            log.info("配置校验已禁用（jairouter.config.validation.enabled=false）");
            return;
        }

        log.info("====== 配置完整性校验 ======");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 校验服务器配置
        validateServerConfig(errors, warnings);

        // 校验路由配置
        validateRouterConfig(errors, warnings);

        // 校验存储配置
        validateStorageConfig(errors, warnings);

        // 校验监控配置
        validateMonitorConfig(warnings);

        // 输出结果
        if (!errors.isEmpty()) {
            log.error("====== 配置错误 ======");
            errors.forEach(e -> log.error("  ❌ {}", e));
            log.error("====== 错误结束 ======");
            throw new IllegalStateException("配置校验失败，存在 " + errors.size() + " 个错误");
        }

        if (!warnings.isEmpty()) {
            log.warn("====== 配置警告 ======");
            warnings.forEach(w -> log.warn("  ⚠️ {}", w));
            log.warn("====== 警告结束 ======");
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            log.info("✓ 所有配置校验通过");
        } else if (errors.isEmpty()) {
            log.info("✓ 配置校验完成（{} 个警告）", warnings.size());
        }

        log.info("====== 校验结束 ======");
    }

    private void validateServerConfig(List<String> errors, List<String> warnings) {
        // 端口范围校验
        if (serverPort < 1 || serverPort > 65535) {
            errors.add("server.port 无效（当前: " + serverPort + "，范围应为 1-65535）");
        } else if (serverPort < 1024) {
            warnings.add("server.port 使用特权端口（" + serverPort + "），可能需要 root 权限");
        } else {
            log.info("✓ server.port = {}", serverPort);
        }
    }

    private void validateRouterConfig(List<String> errors, List<String> warnings) {
        // 负载均衡类型校验
        List<String> validLbTypes = List.of("round-robin", "weighted", "least-connections", "ip-hash", "random");
        if (!validLbTypes.contains(loadBalanceType.toLowerCase())) {
            errors.add("model.load-balance.type 无效（当前: " + loadBalanceType
                    + "，有效值: " + validLbTypes + ")");
        } else {
            log.info("✓ model.load-balance.type = {}", loadBalanceType);
        }

        // 限流算法校验
        if (rateLimitEnabled) {
            List<String> validAlgorithms = List.of("token-bucket", "sliding-window", "fixed-window");
            if (!validAlgorithms.contains(rateLimitAlgorithm.toLowerCase())) {
                errors.add("model.rate-limit.algorithm 无效（当前: " + rateLimitAlgorithm
                        + "，有效值: " + validAlgorithms + ")");
            } else {
                log.info("✓ model.rate-limit.algorithm = {}", rateLimitAlgorithm);
            }
        }

        // 熔断器阈值校验
        if (circuitBreakerEnabled) {
            if (failureThreshold <= 0) {
                errors.add("model.circuit-breaker.failureThreshold 必须大于 0（当前: " + failureThreshold + ")");
            } else if (failureThreshold > 100) {
                warnings.add("model.circuit-breaker.failureThreshold 过高（" + failureThreshold
                        + "），可能导致熔断器难以触发");
            } else {
                log.info("✓ model.circuit-breaker.failureThreshold = {}", failureThreshold);
            }

            if (circuitBreakerTimeout <= 0) {
                errors.add("model.circuit-breaker.timeout 必须大于 0（当前: " + circuitBreakerTimeout + "ms)");
            } else if (circuitBreakerTimeout > 300000) {
                warnings.add("model.circuit-breaker.timeout 过长（" + circuitBreakerTimeout
                        + "ms），可能导致恢复缓慢");
            } else {
                log.info("✓ model.circuit-breaker.timeout = {}ms", circuitBreakerTimeout);
            }
        }
    }

    private void validateStorageConfig(List<String> errors, List<String> warnings) {
        // 存储类型校验 (v2.26.1: h2也是有效类型，通过JPA实现)
        List<String> validStoreTypes = List.of("jpa", "redis", "memory", "h2");
        if (!validStoreTypes.contains(storeType.toLowerCase())) {
            errors.add("store.type 无效（当前: " + storeType + "，有效值: " + validStoreTypes + ")");
        } else {
            log.info("✓ store.type = {}", storeType);
        }
    }

    private void validateMonitorConfig(List<String> warnings) {
        // 监控配置建议
        if (!monitoringEnabled) {
            warnings.add("monitoring.enabled = false，生产环境建议启用监控");
        } else {
            log.info("✓ monitoring.enabled = {}", monitoringEnabled);
        }

        if (!tracingEnabled) {
            warnings.add("jairouter.tracing.enabled = false，生产环境建议启用追踪");
        } else {
            log.info("✓ jairouter.tracing.enabled = {}", tracingEnabled);
        }
    }
}