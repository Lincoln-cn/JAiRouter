package org.unreal.modelrouter.tracing.sampler;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SamplingStrategyTest {
    
    @Test
    void testRatioBasedSamplingStrategy() {
        // 测试采样率为1.0的情况
        RatioBasedSamplingStrategy sampler1 = new RatioBasedSamplingStrategy(1.0);
        assertEquals(1.0, sampler1.getRatio());
        assertEquals("RatioBasedSampling{1.0}", sampler1.getDescription());
        
        SamplingResult result1 = sampler1.shouldSample(
                Context.current(),
                "test-trace-id-1",
                "test-span",
                SpanKind.INTERNAL,
                new HashMap<>(),
                new HashMap<>()
        );
        assertEquals(SamplingResult.Decision.RECORD_AND_SAMPLE, result1.getDecision());
        
        // 测试采样率为0.0的情况
        RatioBasedSamplingStrategy sampler2 = new RatioBasedSamplingStrategy(0.0);
        assertEquals(0.0, sampler2.getRatio());
        
        SamplingResult result2 = sampler2.shouldSample(
                Context.current(),
                "test-trace-id-2",
                "test-span",
                SpanKind.INTERNAL,
                new HashMap<>(),
                new HashMap<>()
        );
        assertEquals(SamplingResult.Decision.DROP, result2.getDecision());
        
        // 测试无效采样率
        assertThrows(IllegalArgumentException.class, () -> new RatioBasedSamplingStrategy(1.5));
        assertThrows(IllegalArgumentException.class, () -> new RatioBasedSamplingStrategy(-0.5));
    }
    
    @Test
    void testRuleBasedSamplingStrategy() {
        // 创建采样配置
        TracingConfiguration.SamplingConfig samplingConfig = new TracingConfiguration.SamplingConfig();
        samplingConfig.setRatio(0.5);
        samplingConfig.getAlwaysSample().add("always-sample-span");
        samplingConfig.getNeverSample().add("never-sample-span");
        samplingConfig.getServiceRatios().put("chat", 0.8);
        
        TracingConfiguration.SamplingConfig.SamplingRule rule = new TracingConfiguration.SamplingConfig.SamplingRule();
        rule.setCondition("http.status_code >= 400");
        rule.setRatio(1.0);
        samplingConfig.getRules().add(rule);
        
        RuleBasedSamplingStrategy sampler = new RuleBasedSamplingStrategy(samplingConfig);
        assertEquals("RuleBasedSampling", sampler.getDescription());
        
        // 测试始终采样的操作
        SamplingResult result1 = sampler.shouldSample(
                Context.current(),
                "test-trace-id-1",
                "always-sample-span",
                SpanKind.INTERNAL,
                new HashMap<>(),
                new HashMap<>()
        );
        assertEquals(SamplingResult.Decision.RECORD_AND_SAMPLE, result1.getDecision());
        
        // 测试从不采样的操作
        SamplingResult result2 = sampler.shouldSample(
                Context.current(),
                "test-trace-id-2",
                "never-sample-span",
                SpanKind.INTERNAL,
                new HashMap<>(),
                new HashMap<>()
        );
        assertEquals(SamplingResult.Decision.DROP, result2.getDecision());
        
        // 测试服务类型特定采样率
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("service.type", "chat");
        SamplingResult result3 = sampler.shouldSample(
                Context.current(),
                "test-trace-id-3",
                "chat-span",
                SpanKind.INTERNAL,
                attributes,
                new HashMap<>()
        );
        assertEquals(SamplingResult.Decision.RECORD_AND_SAMPLE, result3.getDecision());
    }
    
    @Test
    void testAdaptiveSamplingStrategy() {
        AdaptiveSamplingStrategy sampler = new AdaptiveSamplingStrategy(
                0.5,  // 默认采样率
                1.0,  // 最大采样率
                0.1,  // 最小采样率
                100,  // 调整阈值
                30    // 调整间隔
        );
        
        assertEquals("AdaptiveSampling", sampler.getDescription());
        
        // 测试采样决策
        SamplingResult result = sampler.shouldSample(
                Context.current(),
                "test-trace-id",
                "test-span",
                SpanKind.INTERNAL,
                new HashMap<>(),
                new HashMap<>()
        );
        
        // 由于是基于比例的采样，结果可能是RECORD_AND_SAMPLE或DROP
        assertTrue(result.getDecision() == SamplingResult.Decision.RECORD_AND_SAMPLE || 
                   result.getDecision() == SamplingResult.Decision.DROP);
    }
    
    @Test
    void testSamplingResult() {
        // 测试创建采样结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("test-key", "test-value");
        
        SamplingResult result1 = SamplingResult.create(
                SamplingResult.Decision.RECORD_AND_SAMPLE,
                attributes,
                "test-trace-state"
        );
        
        assertEquals(SamplingResult.Decision.RECORD_AND_SAMPLE, result1.getDecision());
        assertEquals("test-trace-state", result1.getTraceState());
        assertEquals("test-value", result1.getAttributes().get("test-key"));
        
        // 测试简单采样结果创建
        SamplingResult result2 = SamplingResult.create(SamplingResult.Decision.DROP);
        assertEquals(SamplingResult.Decision.DROP, result2.getDecision());
        assertTrue(result2.getAttributes().isEmpty());
        assertNull(result2.getTraceState());
    }
}