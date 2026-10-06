package org.unreal.modelrouter.monitor.tracing.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.unreal.modelrouter.monitor.tracing.async.AsyncTracingProcessor;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.memory.TracingMemoryManager;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@code /actuator/info} 的 tracing 回显契约（issue #224）。
 *
 * <p>背景：tracing 配置里有一批字段**没有任何行为消费方**，只被 {@link TracingInfoContributor} 回显。
 * #224 对它们做了「保留 + 文档标注」而不是删除，前提就是这条回显真实存在。本测试把该前提钉住：
 * 回显一旦被删掉，这些字段就真的成了死配置，届时应连同字段一起删除 —— 那时本测试会先红，
 * 提醒维护者做那个决定，而不是让字段继续悄悄躺着。</p>
 *
 * <p>与之相对，「零消费方」的那批字段（{@code components.database/cache/messaging}、
 * {@code open-telemetry.sdk.trace.processors.batch.*}、{@code logging.custom-fields} 等）已直接删除，
 * 因此不在这里出现。</p>
 */
@DisplayName("/actuator/info 的 tracing 回显契约（issue #224）")
class TracingInfoContributorReportContractTest {

    @Test
    @DisplayName("「只被回显」的字段确实出现在 info 输出里")
    void reportOnlyFieldsAreEchoed() {
        TracingConfiguration configuration = new TracingConfiguration();
        configuration.setEnabled(false);   // 跳过需要真实依赖的运行时统计段

        TracingInfoContributor contributor = new TracingInfoContributor(
                configuration,
                mock(TracingPerformanceMonitor.class),
                mock(AsyncTracingProcessor.class),
                mock(TracingMemoryManager.class));

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Map<String, Object> tracing = details(builder);

        assertFalse(tracing.containsKey("error"),
                "contribute 不应走异常分支，实际错误：" + tracing.get("error"));

        // ThreadPoolConfig.maxSize：boundedElastic 只有一个上限（core-size），maxSize 只被这里回显
        assertTrue(nested(tracing, "performance", "threadPool").containsKey("maxSize"),
                "performance.threadPool.maxSize 应当被回显");

        // components.http 的四个字段：只被这里回显，不改变采集行为
        Map<String, Object> http = nested(tracing, "components", "http");
        for (String key : new String[] {"enabled", "captureHeaders", "captureBody", "excludedPathsCount"}) {
            assertTrue(http.containsKey(key), "components.http." + key + " 应当被回显");
        }

        // security.encryption.enabled：字段默认 false，而 @ConditionalOnProperty 是「缺省即开启」，
        // 两者语义相反 ⇒ 该字段只作回显保留
        assertTrue(nested(tracing, "security", "encryption").containsKey("enabled"),
                "security.encryption.enabled 应当被回显");

        // sanitization / accessControl 里同样只有回显的字段
        assertTrue(nested(tracing, "security", "sanitization").containsKey("inheritGlobalRules"),
                "security.sanitization.inheritGlobalRules 应当被回显");
        assertTrue(nested(tracing, "security", "accessControl").containsKey("enableRoleBasedFiltering"),
                "security.accessControl.enableRoleBasedFiltering 应当被回显");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(final Info.Builder builder) {
        return (Map<String, Object>) builder.build().getDetails().get("tracing");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(final Map<String, Object> root, final String... path) {
        Map<String, Object> current = root;
        for (String key : path) {
            current = (Map<String, Object>) current.get(key);
        }
        return current;
    }
}
