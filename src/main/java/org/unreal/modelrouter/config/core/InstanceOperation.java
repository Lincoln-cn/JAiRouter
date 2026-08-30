package org.unreal.modelrouter.config.core;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * 实例操作类型
 */
enum InstanceOperationType {
    ADD, UPDATE, DELETE
}

/**
 * 实例操作定义
 */
public record InstanceOperation(InstanceOperationType type, String instanceId,
                                ModelRouterProperties.ModelInstance instanceConfig) {
}
