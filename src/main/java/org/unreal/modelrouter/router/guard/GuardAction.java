package org.unreal.modelrouter.router.guard;

/**
 * 流式护栏对单个 chunk 的处置动作。
 *
 * @since 3.6.0
 */
public enum GuardAction {

    /** 原样放行。 */
    ALLOW,

    /** 改写后放行（脱敏）。 */
    REDACT,

    /** 命中阻断规则：发终止帧并结束该流。 */
    ABORT
}
