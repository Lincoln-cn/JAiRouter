package org.unreal.modelrouter.router.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.guard.GuardProperties.Pattern;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 护栏链的行为契约（#961）。
 *
 * @since 3.6.0
 */
@DisplayName("流式护栏链")
class GuardChainTest {

    private static Pattern pattern(final String name, final String regex, final String replacement) {
        final Pattern p = new Pattern();
        p.setName(name);
        p.setRegex(regex);
        p.setReplacement(replacement);
        return p;
    }

    @Test
    @DisplayName("空链无条件放行")
    void emptyChainAllows() {
        final GuardDecision decision = GuardChain.EMPTY.evaluate("{\"choices\":[]}");

        assertThat(decision.action()).isEqualTo(GuardAction.ALLOW);
        assertThat(decision.output()).isEqualTo("{\"choices\":[]}");
    }

    @Test
    @DisplayName("脱敏算子把命中文本替换成占位符")
    void redactReplacesMatchedText() {
        final GuardChain chain = new GuardChain(List.of(
                new RegexRedactGuard(List.of(pattern("phone", "\\d{11}", "[PHONE]")))));

        final GuardDecision decision = chain.evaluate("联系我 13800138000 谢谢");

        assertThat(decision.action()).isEqualTo(GuardAction.REDACT);
        assertThat(decision.policy()).isEqualTo("phone");
        assertThat(decision.output()).isEqualTo("联系我 [PHONE] 谢谢");
    }

    @Test
    @DisplayName("阻断算子命中即终止")
    void abortStopsTheStream() {
        final GuardChain chain = new GuardChain(List.of(
                new RegexAbortGuard(List.of(pattern("secret", "(?i)sk-[a-z0-9]{8,}", null)))));

        final GuardDecision decision = chain.evaluate("key = sk-abcdef123456");

        assertThat(decision.aborting()).isTrue();
        assertThat(decision.policy()).isEqualTo("secret");
        assertThat(decision.output()).isNull();
    }

    @Test
    @DisplayName("未命中的规则不改写内容")
    void noMatchLeavesChunkUntouched() {
        final GuardChain chain = new GuardChain(List.of(
                new RegexRedactGuard(List.of(pattern("phone", "\\d{11}", "[PHONE]")))));

        final String chunk = "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}";

        assertThat(chain.evaluate(chunk).action()).isEqualTo(GuardAction.ALLOW);
        assertThat(chain.evaluate(chunk).output()).isSameAs(chunk);
    }

    @Test
    @DisplayName("链按登记顺序执行：脱敏在前时，阻断看到的是改写后的文本")
    void orderMatters() {
        // 先脱敏再阻断：阻断规则匹配漏出的密钥形态，脱敏不处理它，因此仍然阻断；
        // 这条断言守的是「顺序可观察」——若实现把顺序反过来，脱敏会先吃掉手机号，
        // 而阻断规则恰好要求「原文里同时出现手机号和密钥」，就会从 ABORT 变成 ALLOW。
        final GuardChain redactThenAbort = new GuardChain(List.of(
                new RegexRedactGuard(List.of(pattern("phone", "\\d{11}", "[PHONE]"))),
                new RegexAbortGuard(List.of(pattern("combo", "\\[PHONE][\\s\\S]*sk-", null)))));

        final GuardDecision decision = redactThenAbort.evaluate("13800138000 sk-abcdef");

        assertThat(decision.aborting()).isTrue();
        assertThat(decision.policy()).isEqualTo("combo");
    }

    @Test
    @DisplayName("阻断短路：其后的算子不再执行")
    void abortShortCircuits() {
        final CountingGuard after = new CountingGuard();
        final GuardChain chain = new GuardChain(List.of(
                new RegexAbortGuard(List.of(pattern("block", "forbidden", null))),
                after));

        final GuardDecision decision = chain.evaluate("forbidden text");

        assertThat(decision.aborting()).isTrue();
        assertThat(after.calls).isZero();
    }

    @Test
    @DisplayName("多个脱敏规则依次叠加")
    void multipleRedactRulesStack() {
        final GuardChain chain = new GuardChain(List.of(
                new RegexRedactGuard(List.of(
                        pattern("phone", "\\d{11}", "[PHONE]"),
                        pattern("idcard", "X{3}", "[ID]")))));

        final GuardDecision decision = chain.evaluate("13800138000 XXX");

        assertThat(decision.output()).isEqualTo("[PHONE] [ID]");
        assertThat(decision.policy()).isEqualTo("idcard");
    }

    @Test
    @DisplayName("非法正则在构建时被跳过，不影响其它规则")
    void invalidRegexIsSkipped() {
        final GuardChain chain = new GuardChain(List.of(
                new RegexRedactGuard(List.of(
                        pattern("broken", "([unclosed", "[X]"),
                        pattern("phone", "\\d{11}", "[PHONE]")))));

        final GuardDecision decision = chain.evaluate("13800138000");

        assertThat(decision.redacted()).isTrue();
        assertThat(decision.output()).isEqualTo("[PHONE]");
    }

    /** 记录被调用次数的算子，用于断言短路。 */
    private static final class CountingGuard implements ChunkGuard {

        private int calls;

        @Override
        public GuardDecision evaluate(final String chunk) {
            calls++;
            return GuardDecision.allow(chunk);
        }

        @Override
        public String name() {
            return "counting";
        }
    }
}
