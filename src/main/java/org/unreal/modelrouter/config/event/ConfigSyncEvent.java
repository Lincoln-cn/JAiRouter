package org.unreal.modelrouter.config.event;

import java.time.Instant;
import java.util.Map;

/**
 * 配置同步事件
 *
 * 用于通知配置需要同步到其他服务或组件。
 *
 * @since v2.12.0
 */
public record ConfigSyncEvent(
    /** 同步类型: "REFRESH", "ROLLBACK", "MIGRATE", "INSTANCE_UPDATE" */
    String syncType,

    /** 配置数据 */
    Map<String, Object> config,

    /** 目标服务（可选，null表示广播） */
    String targetService,

    /** 事件发生时间 */
    Instant timestamp
) {

    /**
     * 创建刷新同步事件（广播到所有服务）
     */
    public static ConfigSyncEvent refresh(Map<String, Object> config) {
        return new ConfigSyncEvent("REFRESH", config, null, Instant.now());
    }

    /**
     * 创建回滚同步事件
     */
    public static ConfigSyncEvent rollback(Map<String, Object> config, String targetService) {
        return new ConfigSyncEvent("ROLLBACK", config, targetService, Instant.now());
    }

    /**
     * 创建实例更新同步事件
     */
    public static ConfigSyncEvent instanceUpdate(Map<String, Object> config, String serviceType) {
        return new ConfigSyncEvent("INSTANCE_UPDATE", config, serviceType, Instant.now());
    }

    /**
     * 判断是否为刷新操作
     */
    public boolean isRefresh() {
        return "REFRESH".equals(syncType);
    }

    /**
     * 判断是否为回滚操作
     */
    public boolean isRollback() {
        return "ROLLBACK".equals(syncType);
    }

    /**
     * 判断是否需要广播（无特定目标）
     */
    public boolean isBroadcast() {
        return targetService == null;
    }
}