package org.unreal.modelrouter.store;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 响应式版本化存储管理器接口
 * 扩展 ReactiveStoreManager 添加版本控制功能
 */
public interface ReactiveVersionedStoreManager extends ReactiveStoreManager {

    /**
     * 保存配置的版本
     *
     * @param key     配置键
     * @param config  配置内容
     * @param version 版本号
     * @return 操作完成的信号
     */
    Mono<Void> saveConfigVersion(String key, Map<String, Object> config, int version);

    /**
     * 获取配置的所有版本号
     *
     * @param key 配置键
     * @return 版本号的 Flux
     */
    Flux<Integer> getConfigVersions(String key);

    /**
     * 获取指定版本的配置
     *
     * @param key     配置键
     * @param version 版本号
     * @return 配置内容的 Mono
     */
    Mono<Map<String, Object>> getConfigByVersion(String key, int version);

    /**
     * 删除指定版本的配置
     *
     * @param key     配置键
     * @param version 版本号
     * @return 操作完成的信号
     */
    Mono<Void> deleteConfigVersion(String key, int version);

    /**
     * 验证指定版本是否存在
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本是否存在的 Mono
     */
    Mono<Boolean> versionExists(String key, int version);

    /**
     * 获取指定版本的文件路径
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本文件路径的 Mono
     */
    Mono<String> getVersionFilePath(String key, int version);

    /**
     * 获取指定版本的创建时间
     *
     * @param key     配置键
     * @param version 版本号
     * @return 版本创建时间的 Mono
     */
    Mono<LocalDateTime> getVersionCreatedTime(String key, int version);
}
