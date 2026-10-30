package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #824：服务级限流桶是「按服务类型共享」的，默认配置下所有客户端共用一个配额。
 *
 * <p>这里只守「启动提示」这一层的可见性：提示必须点名受影响的服务，并且明确说出
 * 「全局上限」与「client-ip-enable」这两个关键事实，否则运维看到日志也不知怎么改。
 * 限流本身的行为（按 serviceType 取桶）不在本测试的射程内——改它是行为变更，
 * 需要单独定夺（见 issue #824）。
 */
class RateLimitManagerSharedBucketNoticeTest {

    @Test
    @DisplayName("824-001：没有共享桶的服务时不打印提示")
    void noSharedServicesProducesNoWarning() {
        assertThat(RateLimitManager.sharedBucketWarning(List.of())).isEmpty();
    }

    @Test
    @DisplayName("824-002：null 输入不抛异常")
    void nullInputProducesNoWarning() {
        assertThat(RateLimitManager.sharedBucketWarning(null)).isEmpty();
    }

    @Test
    @DisplayName("824-003：提示点名单个服务并说明共享语义")
    void warningNamesServiceAndExplainsSharedSemantics() {
        final Optional<String> warning = RateLimitManager.sharedBucketWarning(List.of("chat"));

        assertThat(warning).isPresent();
        final String message = warning.get();
        assertThat(message).contains("chat");
        assertThat(message).contains("client-ip-enable");
        assertThat(message).contains("全局上限");
        assertThat(message).contains("key-dimension");
    }

    @Test
    @DisplayName("824-004：多个服务全部点名，不漏")
    void warningListsEveryService() {
        final Optional<String> warning =
                RateLimitManager.sharedBucketWarning(List.of("chat", "embedding", "imgGen"));

        assertThat(warning).isPresent();
        assertThat(warning.get()).contains("chat", "embedding", "imgGen");
    }
}
