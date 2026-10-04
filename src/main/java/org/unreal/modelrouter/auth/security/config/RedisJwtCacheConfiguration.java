package org.unreal.modelrouter.auth.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

/**
 * JWT 侧的 Redis 健康检查配置。
 *
 * <p><b>issue #196（按「取消 JWT 独立 Redis」裁决整改）</b>：本类原先还会自建一套 JWT 专用 Redis
 * —— {@code jwtRedisConnectionFactory}（按 {@code jairouter.security.jwt.persistence.redis.*} 的
 * host/port/database 建 Lettuce 工厂）+ {@code jwtReactiveRedisTemplate}。它与 Spring Boot 自动
 * 装配的那套**并存**，于是同一类型出现两个候选：
 *
 * <ul>
 *   <li>2 个 {@code ReactiveRedisConnectionFactory}：{@code jwtRedisConnectionFactory} 与
 *       {@code LettuceConnectionConfiguration.redisConnectionFactory}；</li>
 *   <li>2 个 {@code ReactiveRedisTemplate<String, String>}：{@code jwtReactiveRedisTemplate} 与
 *       应用默认的 String 模板。</li>
 * </ul>
 *
 * <p>而全仓有十余处**未限定**的同类型注入点（配额、缓存、状态持久化、集群事件、限流等），
 * 于是按文档开启全套 Redis 开关（多副本必需）后应用直接启动失败。因此 JWT 改为**复用应用默认
 * Redis**：键本身带 {@code jwt:} 前缀，不需要独立 database。
 *
 * <p>本类只保留健康检查，模板一律使用应用默认的 String 序列化模板
 * （Spring Boot 的 {@code reactiveStringRedisTemplate}）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
public class RedisJwtCacheConfiguration {

    /**
     * Redis 健康检查 Bean（使用应用默认的 String 序列化模板，不再自建连接）
     */
    @Bean("redisJwtHealthChecker")
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.redis.enabled", havingValue = "true")
    public RedisJwtHealthChecker redisJwtHealthChecker(
            final ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        return new RedisJwtHealthChecker(reactiveRedisTemplate);
    }

    /**
     * Redis JWT健康检查器
     */
    public static class RedisJwtHealthChecker {
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private static final String HEALTH_CHECK_KEY = "jwt:health_check";

        public RedisJwtHealthChecker(final ReactiveRedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        /**
         * 检查Redis连接是否健康
         */
        public boolean isHealthy() {
            try {
                String testValue = "health_check_" + System.currentTimeMillis();

                // 尝试写入和读取测试值
                Boolean setResult = redisTemplate.opsForValue()
                    .set(HEALTH_CHECK_KEY, testValue, java.time.Duration.ofSeconds(10))
                    .block(java.time.Duration.ofSeconds(5));

                if (Boolean.TRUE.equals(setResult)) {
                    String getValue = redisTemplate.opsForValue()
                        .get(HEALTH_CHECK_KEY)
                        .block(java.time.Duration.ofSeconds(5));

                    if (testValue.equals(getValue)) {
                        // 清理测试键
                        redisTemplate.delete(HEALTH_CHECK_KEY).subscribe();
                        return true;
                    }
                }

                return false;

            } catch (Exception e) {
                log.warn("Redis JWT health check failed: {}", e.getMessage());
                return false;
            }
        }

        /**
         * 获取Redis连接信息
         */
        public String getConnectionInfo() {
            try {
                return "Redis JWT Cache - Connection established";
            } catch (Exception e) {
                return "Redis JWT Cache - Connection failed: " + e.getMessage();
            }
        }
    }
}
