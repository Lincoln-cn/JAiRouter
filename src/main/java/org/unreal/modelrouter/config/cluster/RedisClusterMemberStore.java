package org.unreal.modelrouter.config.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的成员表实现（#1136）。
 *
 * <p>每个成员占一个带 TTL 的 key（{@code jairouter:cluster:member:<instanceId>}），
 * 心跳即 {@code SET ... EX ttl} 续期；副本被 {@code kill -9} 后最迟在 TTL 到期时消失，
 * 不需要任何清理任务。</p>
 *
 * <p>Redis 不可用时不阻断请求：{@link #put} 打一条限频告警后放弃本次心跳，
 * {@link #list()} 返回空列表并记日志 —— 集群状态页会显示「无法读取」而不是假数据。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "jairouter.cluster.member.store",
        havingValue = "redis",
        matchIfMissing = true)
public class RedisClusterMemberStore implements ClusterMemberStore {

    /** 成员 key 前缀。 */
    static final String KEY_PREFIX = "jairouter:cluster:member:";

    /** 单次 Redis 调用的等待上限。 */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(3);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 构造器。
     *
     * @param redisTemplate 响应式 Redis 模板
     * @param objectMapper  JSON 序列化器（Jackson 3）
     */
    public RedisClusterMemberStore(final ReactiveStringRedisTemplate redisTemplate,
                                   final ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(final ClusterMember member, final Duration ttl) {
        try {
            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + member.instanceId(), objectMapper.writeValueAsString(member), ttl)
                    .block(BLOCK_TIMEOUT);
        } catch (DataAccessException | JacksonException | IllegalStateException e) {
            log.warn("[cluster] 成员心跳写入失败，本副本暂不出现在成员表: {}", e.getMessage());
        }
    }

    @Override
    public List<ClusterMember> list() {
        try {
            return redisTemplate.scan(ScanOptions.scanOptions()
                            .match(KEY_PREFIX + "*")
                            .count(200)
                            .build())
                    .flatMap(key -> redisTemplate.opsForValue().get(key))
                    .map(this::deserialize)
                    .filter(member -> member != null)
                    .collectList()
                    .block(BLOCK_TIMEOUT);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("[cluster] 读取成员表失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void remove(final String instanceId) {
        try {
            redisTemplate.delete(KEY_PREFIX + instanceId).block(BLOCK_TIMEOUT);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("[cluster] 移除成员失败: {}", e.getMessage());
        }
    }

    private ClusterMember deserialize(final String json) {
        try {
            return objectMapper.readValue(json, ClusterMember.class);
        } catch (JacksonException e) {
            log.warn("[cluster] 成员记录无法解析，已忽略: {}", e.getMessage());
            return null;
        }
    }
}
