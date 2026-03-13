package org.unreal.modelrouter.store;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 响应式存储管理器接口
 * 所有方法返回 Mono/Flux，遵循响应式编程原则
 */
public interface ReactiveStoreManager {

    /**
     * 保存配置
     *
     * @param key    配置键
     * @param config 配置内容
     * @return 操作完成的信号
     */
    Mono<Void> saveConfig(String key, Map<String, Object> config);

    /**
     * 获取配置
     *
     * @param key 配置键
     * @return 配置内容的 Mono
     */
    Mono<Map<String, Object>> getConfig(String key);

    /**
     * 删除配置
     *
     * @param key 配置键
     * @return 操作完成的信号
     */
    Mono<Void> deleteConfig(String key);

    /**
     * 获取所有配置键
     *
     * @return 配置键的 Flux
     */
    Flux<String> getAllKeys();

    /**
     * 检查配置是否存在
     *
     * @param key 配置键
     * @return 是否存在的 Mono
     */
    Mono<Boolean> exists(String key);

    /**
     * 更新配置
     *
     * @param key    配置键
     * @param config 配置内容
     * @return 操作完成的信号
     */
    Mono<Void> updateConfig(String key, Map<String, Object> config);

    /**
     * 获取最新配置
     *
     * @param configKey 配置键
     * @return 配置内容的 Mono
     */
    Mono<Map<String, Object>> getLatestConfig(String configKey);
}
