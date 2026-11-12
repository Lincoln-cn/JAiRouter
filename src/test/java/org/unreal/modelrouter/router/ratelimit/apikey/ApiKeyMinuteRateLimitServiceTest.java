package org.unreal.modelrouter.router.ratelimit.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitCounter;
import org.unreal.modelrouter.router.ratelimit.enduser.LocalEndUserRateLimitCounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ApiKeyMinuteRateLimitService} 单元测试（v3.8.6）.
 *
 * <p>被测对象是「API Key 级每分钟限流」的唯一判定点：它接替了认证链上那份进程内滑动窗口
 * （已删除的 {@code auth.security.service.TokenBucketRateLimiter}）与配额账本的 MINUTE 窗口。
 * 因此这里必须把四条边界钉住：</p>
 * <ul>
 *   <li><b>恰好 N 次</b>：限额 2 时前两次放行、第三次拒绝（固定窗口，不是「大约」）；</li>
 *   <li><b>不判定即放行</b>：未启用 / 控制台 JWT / 找不到 Key / 限额 &le; 0 都返回 empty，
 *       调用方据此直接放行 —— 这几条正是原 {@code validateRateLimit} 的容错语义，
 *       换实现不能把它们换没了；</li>
 *   <li><b>读回一致</b>：{@code currentUsage} 与判定共用同一个计数器（管理面展示的数据源）；</li>
 *   <li><b>reset 语义</b>：管理面重置后本窗口重新可用。</li>
 * </ul>
 *
 * <p>计数后端用真实的 {@link LocalEndUserRateLimitCounter}（而不是 mock）：窗口对齐、CAS
 * 判定这些行为恰恰是需要验证的部分，mock 掉它们等于只测了个 if。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@DisplayName("API Key 每分钟限流服务")
class ApiKeyMinuteRateLimitServiceTest {

    private static final String KEY_ID = "key-1";
    private static final String KEY_HASH = "hash-1";

    private ApiKeyMinuteRateLimitProperties properties;
    private EndUserRateLimitCounter counter;
    private ApiKeyService apiKeyService;
    private ApiKeyMinuteRateLimitService service;
    private Map<String, ApiKey> apiKeyCache;
    private Map<String, String> keyIdIndex;

    @BeforeEach
    void setUp() {
        properties = new ApiKeyMinuteRateLimitProperties();
        counter = new LocalEndUserRateLimitCounter(1000);
        apiKeyService = mock(ApiKeyService.class);
        apiKeyCache = new HashMap<>();
        keyIdIndex = new HashMap<>();
        when(apiKeyService.getApiKeyCache()).thenReturn(apiKeyCache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(keyIdIndex);
        service = new ApiKeyMinuteRateLimitService(properties, counter, apiKeyService, null);
    }

    private void registerKey(final int limitPerMinute) {
        final ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.getRateLimitPerMinute()).thenReturn(limitPerMinute);
        apiKeyCache.put(KEY_HASH, apiKey);
        keyIdIndex.put(KEY_ID, KEY_HASH);
    }

    private EndUserRateLimitCounter.Decision evaluateOnce() {
        final Optional<EndUserRateLimitCounter.Decision> decision =
                service.evaluate(KEY_ID, ServiceType.chat, false);
        assertTrue(decision.isPresent(), "命中限额判定时不应返回 empty");
        return decision.get();
    }

    @Nested
    @DisplayName("固定窗口判定")
    class FixedWindowTests {

        @Test
        @DisplayName("限额 2：前两次放行，第三次拒绝且 used=limit")
        void allowsExactlyLimitTimes() {
            registerKey(2);

            assertTrue(evaluateOnce().allowed(), "第 1 次应放行");
            assertTrue(evaluateOnce().allowed(), "第 2 次应放行");

            final EndUserRateLimitCounter.Decision rejected = evaluateOnce();

            assertFalse(rejected.allowed(), "第 3 次必须拒绝（限额 2/分钟）");
            assertEquals(2, rejected.limit());
            assertEquals(2L, rejected.used(), "被拒时 used 报的是已用满的上限");
            assertEquals(0L, rejected.remaining());
            assertTrue(rejected.retryAfterSeconds() >= 1L, "Retry-After 至少 1 秒");
            assertFalse(rejected.degraded(), "本地后端不应标记降级");
        }

        @Test
        @DisplayName("放行时 used 递增、remaining 递减")
        void reportsUsageOnAllowed() {
            registerKey(3);

            final EndUserRateLimitCounter.Decision first = evaluateOnce();
            final EndUserRateLimitCounter.Decision second = evaluateOnce();

            assertEquals(1L, first.used());
            assertEquals(2L, second.used());
            assertEquals(2L, first.remaining());
            assertEquals(1L, second.remaining());
        }
    }

    @Nested
    @DisplayName("不做判定的四种情形（等价于放行）")
    class SkipTests {

        @Test
        @DisplayName("限额为 0 或负数 ⇒ empty（0 表示不限制）")
        void zeroOrNegativeLimitSkips() {
            registerKey(0);
            assertTrue(service.evaluate(KEY_ID, ServiceType.chat, false).isEmpty(), "限额 0 不得拦截");

            registerKey(-5);
            assertTrue(service.evaluate(KEY_ID, ServiceType.chat, false).isEmpty(), "负数限额不得拦截");
        }

        @Test
        @DisplayName("控制台 JWT 调用方 ⇒ empty（不参与 API Key 速率）")
        void jwtCallerSkips() {
            registerKey(1);
            assertTrue(service.evaluate(KEY_ID, ServiceType.chat, true).isEmpty());
        }

        @Test
        @DisplayName("Key ID 为空 或 找不到 Key 配置 ⇒ empty")
        void unknownKeySkips() {
            assertTrue(service.evaluate(null, ServiceType.chat, false).isEmpty(), "null ID 不得判定");
            assertTrue(service.evaluate("  ", ServiceType.chat, false).isEmpty(), "空白 ID 不得判定");
            assertTrue(service.evaluate("missing", ServiceType.chat, false).isEmpty(), "未登记 Key 不得判定");
        }

        @Test
        @DisplayName("开关关闭 ⇒ empty（且不消耗任何计数）")
        void disabledSkips() {
            registerKey(1);
            properties.setEnabled(false);

            assertTrue(service.evaluate(KEY_ID, ServiceType.chat, false).isEmpty());
            assertFalse(service.isEnabled());
            assertEquals(0L, service.currentUsage(KEY_ID), "关闭时不得计数");
        }
    }

    @Nested
    @DisplayName("管理面读回与重置")
    class AdminTests {

        @Test
        @DisplayName("currentUsage 反映本窗口已用次数")
        void currentUsageReflectsDecisions() {
            registerKey(5);

            assertEquals(0L, service.currentUsage(KEY_ID), "初始为 0");
            evaluateOnce();
            evaluateOnce();
            assertEquals(2L, service.currentUsage(KEY_ID));
        }

        @Test
        @DisplayName("currentUsage 在未启用 / 未知 Key 时返回 0（不回退进程内计数）")
        void currentUsageReturnsZeroWhenUnavailable() {
            assertEquals(0L, service.currentUsage("missing"));
            assertEquals(0L, service.currentUsage(null));
        }

        @Test
        @DisplayName("reset 后本窗口重新可用")
        void resetClearsCurrentWindow() {
            registerKey(1);

            assertTrue(evaluateOnce().allowed());
            assertFalse(evaluateOnce().allowed(), "限额 1 时第二次应被拒");

            assertTrue(service.reset(KEY_ID), "本地后端支持单键清零");
            assertEquals(0L, service.currentUsage(KEY_ID));

            assertTrue(evaluateOnce().allowed(), "清零后应重新放行");
        }

        @Test
        @DisplayName("reset 对未知 Key / 关闭状态返回 false（不做假成功）")
        void resetReturnsFalseWhenNotApplicable() {
            assertFalse(service.reset("missing"));
            properties.setEnabled(false);
            assertFalse(service.reset(KEY_ID));
        }

        @Test
        @DisplayName("resetAll 走本地后端并报告清零数量")
        void resetAllClearsLocalBackend() {
            registerKey(5);
            evaluateOnce();
            evaluateOnce();

            assertTrue(service.resetAll() >= 1, "本地后端应报告至少清掉一个桶");
            assertEquals(0L, service.currentUsage(KEY_ID));
        }
    }
}
