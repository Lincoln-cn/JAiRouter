package org.unreal.modelrouter.monitor.callhistory.config;

/**
 * 记录内容级别枚举
 * 控制 API 调用历史中请求/响应体的记录方式
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
public enum RecordLevel {

    /**
     * 仅记录元数据（默认）
     * 不记录请求体和响应体内容
     */
    METADATA_ONLY,

    /**
     * 摘要模式（脱敏）
     * 记录经脱敏处理后的请求/响应体摘要
     */
    SUMMARY,

    /**
     * 完整模式（加密）
     * 记录完整的请求/响应体，使用 AES-256-GCM 加密存储
     */
    FULL
}
