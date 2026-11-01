package org.unreal.modelrouter.router.guard;

/**
 * 一个 chunk 经过护栏链后的处置结果。
 *
 * @param action 处置动作，永不为 {@code null}
 * @param policy 命中的规则名；{@link GuardAction#ALLOW} 时为 {@code null}
 * @param output 应当写给下游的 chunk 文本；{@link GuardAction#ABORT} 时为终止帧的内容
 * @param reason 便于排查的简短说明；{@link GuardAction#ALLOW} 时为 {@code null}
 * @since 3.6.0
 */
public record GuardDecision(GuardAction action, String policy, String output, String reason) {

    /**
     * 原样放行。
     *
     * @param chunk 当前 chunk 文本
     * @return 放行结果
     */
    public static GuardDecision allow(final String chunk) {
        return new GuardDecision(GuardAction.ALLOW, null, chunk, null);
    }

    /**
     * 改写后放行。
     *
     * @param policy 命中的规则名
     * @param chunk  改写后的 chunk 文本
     * @param reason 简短说明
     * @return 改写结果
     */
    public static GuardDecision redact(final String policy, final String chunk, final String reason) {
        return new GuardDecision(GuardAction.REDACT, policy, chunk, reason);
    }

    /**
     * 阻断当前流。
     *
     * @param policy 命中的规则名
     * @param reason 简短说明
     * @return 阻断结果
     */
    public static GuardDecision abort(final String policy, final String reason) {
        return new GuardDecision(GuardAction.ABORT, policy, null, reason);
    }

    /**
     * 是否命中阻断。
     *
     * @return 动作为 {@link GuardAction#ABORT} 时为 true
     */
    public boolean aborting() {
        return action == GuardAction.ABORT;
    }

    /**
     * 是否改写了内容。
     *
     * @return 动作为 {@link GuardAction#REDACT} 时为 true
     */
    public boolean redacted() {
        return action == GuardAction.REDACT;
    }
}
