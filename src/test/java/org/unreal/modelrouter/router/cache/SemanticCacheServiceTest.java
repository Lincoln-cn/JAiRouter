package org.unreal.modelrouter.router.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SemanticCacheService} 的单元测试.
 *
 * <p>覆盖：关闭时的短路、归一化、近似命中、不相似不命中、长度差过滤、
 * 按模型分桶、TTL 过期、容量淘汰、以及指标计数。</p>
 */
class SemanticCacheServiceTest {

    private SemanticCacheProperties properties;
    private SimpleMeterRegistry registry;
    private SemanticCacheService service;

    @BeforeEach
    void setUp() {
        properties = new SemanticCacheProperties();
        properties.setEnabled(true);
        properties.setMinTextLength(4);
        registry = new SimpleMeterRegistry();
        service = new SemanticCacheService(properties, registry);
    }

    @Test
    void disabledServiceNeverHitsOrStores() {
        properties.setEnabled(false);

        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer");
        final Optional<Object> found = service.find("qwen3", "请介绍一下这个产品的功能特性");

        assertThat(found).isEmpty();
        assertThat(service.size()).isZero();
    }

    @Test
    void identicalTextHitsAfterPut() {
        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer");

        assertThat(service.find("qwen3", "请介绍一下这个产品的功能特性")).contains("answer");
    }

    @Test
    void punctuationAndCaseAreIgnored() {
        service.put("qwen3", "What is the deployment guide?", "answer");

        assertThat(service.find("qwen3", "what is   the deployment guide")).contains("answer");
    }

    @Test
    void veryDifferentTextDoesNotHit() {
        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer");

        final Optional<Object> found = service.find("qwen3", "今天天气怎么样适合出门吗");

        assertThat(found).isEmpty();
    }

    @Test
    void shortTextIsNotCached() {
        properties.setMinTextLength(12);

        service.put("qwen3", "你好", "answer");

        assertThat(service.size()).isZero();
        assertThat(service.find("qwen3", "你好")).isEmpty();
    }

    @Test
    void bucketsAreIsolatedByModel() {
        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer-a");

        assertThat(service.find("other-model", "请介绍一下这个产品的功能特性")).isEmpty();
        assertThat(service.find("qwen3", "请介绍一下这个产品的功能特性")).contains("answer-a");
    }

    @Test
    void expiredEntriesAreNotReturned() {
        properties.setTtl(Duration.ofMillis(1));
        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer");
        sleep(20);

        assertThat(service.find("qwen3", "请介绍一下这个产品的功能特性")).isEmpty();
    }

    @Test
    void capacityLimitEvictsOldestEntries() {
        properties.setMaxEntriesPerModel(2);
        service.put("qwen3", "第一个完全不同的问题文本内容", "a1");
        service.put("qwen3", "第二个完全不同的话题描述文字", "a2");
        service.put("qwen3", "第三个完全不同的叙述语句段落", "a3");

        assertThat(service.size()).isEqualTo(2);
        assertThat(service.find("qwen3", "第一个完全不同的问题文本内容")).isEmpty();
    }

    @Test
    void metricsCountHitsAndMisses() {
        service.put("qwen3", "请介绍一下这个产品的功能特性", "answer");

        service.find("qwen3", "请介绍一下这个产品的功能特性");
        service.find("qwen3", "完全不相干的另一个长句子啊");

        assertThat(registry.get(SemanticCacheService.METRIC_HITS + "").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(SemanticCacheService.METRIC_MISSES + "").counter().count()).isEqualTo(1.0);
    }

    @Test
    void normalizeCollapsesWhitespaceAndPunctuation() {
        assertThat(SemanticCacheService.normalize("  Hello,  World!  ")).isEqualTo("hello world");
        assertThat(SemanticCacheService.normalize(null)).isEmpty();
    }

    @Test
    void identicalTextProducesIdenticalFingerprint() {
        final String a = SemanticCacheService.normalize("请介绍一下这个产品的功能特性");
        final String b = SemanticCacheService.normalize("请介绍一下这个产品的功能特性！");

        assertThat(SemanticCacheService.simHash(a)).isEqualTo(SemanticCacheService.simHash(b));
    }

    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
