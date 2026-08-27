package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.9.0: KV 缓存 token 解析逻辑测试
 * 验证两种 cache-token 字段形态的解析
 */
@DisplayName("KV Cache Token Parsing Tests")
class CacheTokenParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("DeepSeek 形态: prompt_cache_hit_tokens / prompt_cache_miss_tokens")
    void parseDeepSeekCacheTokens() throws Exception {
        String usageJson = """
                {
                    "prompt_tokens": 1000,
                    "completion_tokens": 200,
                    "total_tokens": 1200,
                    "prompt_cache_hit_tokens": 800,
                    "prompt_cache_miss_tokens": 200
                }
                """;
        JsonNode usage = objectMapper.readTree(usageJson);

        long cacheHit = usage.has("prompt_cache_hit_tokens")
                ? usage.get("prompt_cache_hit_tokens").asLong() : 0;
        long cacheMiss = usage.has("prompt_cache_miss_tokens")
                ? usage.get("prompt_cache_miss_tokens").asLong() : 0;

        assertEquals(800, cacheHit);
        assertEquals(200, cacheMiss);
    }

    @Test
    @DisplayName("OpenAI/vLLM 形态: prompt_tokens_details.cached_tokens")
    void parseOpenAICacheTokens() throws Exception {
        String usageJson = """
                {
                    "prompt_tokens": 1000,
                    "completion_tokens": 200,
                    "total_tokens": 1200,
                    "prompt_tokens_details": {
                        "cached_tokens": 600
                    }
                }
                """;
        JsonNode usage = objectMapper.readTree(usageJson);

        long cacheHit = 0;
        if (usage.has("prompt_tokens_details")) {
            JsonNode details = usage.get("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cacheHit = details.get("cached_tokens").asLong();
            }
        }

        assertEquals(600, cacheHit);
    }

    @Test
    @DisplayName("缺字段时缓存 token 记录 0(容错)")
    void missingCacheFieldsRecordZero() throws Exception {
        String usageJson = """
                {
                    "prompt_tokens": 500,
                    "completion_tokens": 100,
                    "total_tokens": 600
                }
                """;
        JsonNode usage = objectMapper.readTree(usageJson);

        long cacheHit = usage.has("prompt_cache_hit_tokens")
                ? usage.get("prompt_cache_hit_tokens").asLong() : 0;
        long cacheMiss = usage.has("prompt_cache_miss_tokens")
                ? usage.get("prompt_cache_miss_tokens").asLong() : 0;
        if (cacheHit == 0 && usage.has("prompt_tokens_details")) {
            JsonNode details = usage.get("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cacheHit = details.get("cached_tokens").asLong();
            }
        }

        assertEquals(0, cacheHit);
        assertEquals(0, cacheMiss);
    }

    @Test
    @DisplayName("只有 DeepSeek 形态时只解析 DeepSeek 字段")
    void deepseekOnlyFields() throws Exception {
        String usageJson = """
                {
                    "prompt_tokens": 1000,
                    "completion_tokens": 200,
                    "total_tokens": 1200,
                    "prompt_cache_hit_tokens": 300,
                    "prompt_cache_miss_tokens": 700
                }
                """;
        JsonNode usage = objectMapper.readTree(usageJson);

        long cacheHit = usage.has("prompt_cache_hit_tokens")
                ? usage.get("prompt_cache_hit_tokens").asLong() : 0;
        long cacheMiss = usage.has("prompt_cache_miss_tokens")
                ? usage.get("prompt_cache_miss_tokens").asLong() : 0;

        // OpenAI 形态不存在，fallback 逻辑不触发
        if (cacheHit == 0 && usage.has("prompt_tokens_details")) {
            JsonNode details = usage.get("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cacheHit = details.get("cached_tokens").asLong();
            }
        }

        assertEquals(300, cacheHit);
        assertEquals(700, cacheMiss);
    }

    @Test
    @DisplayName("prompt_tokens_details 无 cached_tokens 字段时回退为 0")
    void openAIDetailsWithoutCachedTokens() throws Exception {
        String usageJson = """
                {
                    "prompt_tokens": 1000,
                    "completion_tokens": 200,
                    "total_tokens": 1200,
                    "prompt_tokens_details": {
                        "cached_tokens": 0
                    }
                }
                """;
        JsonNode usage = objectMapper.readTree(usageJson);

        long cacheHit = usage.has("prompt_cache_hit_tokens")
                ? usage.get("prompt_cache_hit_tokens").asLong() : 0;
        if (cacheHit == 0 && usage.has("prompt_tokens_details")) {
            JsonNode details = usage.get("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cacheHit = details.get("cached_tokens").asLong();
            }
        }

        assertEquals(0, cacheHit);
    }
}
