/**
 * 配置变更审计服务接口
 * 记录配置变更历史，支持版本追溯和回滚审计
 */
package org.unreal.modelrouter.audit;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ConfigChangeAuditService {

    /**
     * 记录配置创建
     * @param configKey 配置键
     * @param configType 配置类型
     * @param configValue 配置值
     * @param operator 操作人
     * @param operatorIp 操作人 IP
     * @param reason 变更原因
     * @return 记录操作结果
     */
    Mono<Void> recordConfigCreation(
        String configKey,
        String configType,
        Map<String, Object> configValue,
        String operator,
        String operatorIp,
        String reason
    );

    /**
     * 记录配置更新
     * @param configKey 配置键
     * @param configType 配置类型
     * @param oldValue 旧配置值
     * @param newValue 新配置值
     * @param operator 操作人
     * @param operatorIp 操作人 IP
     * @param reason 变更原因
     * @return 记录操作结果
     */
    Mono<Void> recordConfigUpdate(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String operator,
        String operatorIp,
        String reason
    );

    /**
     * 记录配置删除
     * @param configKey 配置键
     * @param configType 配置类型
     * @param oldValue 被删除的配置值
     * @param operator 操作人
     * @param operatorIp 操作人 IP
     * @param reason 变更原因
     * @return 记录操作结果
     */
    Mono<Void> recordConfigDeletion(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        String operator,
        String operatorIp,
        String reason
    );
}
