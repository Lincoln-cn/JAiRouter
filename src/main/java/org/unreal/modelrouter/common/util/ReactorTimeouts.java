package org.unreal.modelrouter.common.util;

import java.time.Duration;

/**
 * 冷路径 Reactor {@code block} 统一超时（R4-P1）。
 *
 * <p>禁止无超时 {@code block()}：调度器/启动/后台清理路径一律带超时，
 * 避免存储故障时线程永久挂起。</p>
 */
public final class ReactorTimeouts {

    /** 一般冷路径 block（清理、健康、批量落盘） */
    public static final Duration BLOCK = Duration.ofSeconds(5);

    /** 启动恢复等允许更长但仍必须有界的 block */
    public static final Duration STARTUP_BLOCK = Duration.ofSeconds(30);

    private ReactorTimeouts() {
    }
}
