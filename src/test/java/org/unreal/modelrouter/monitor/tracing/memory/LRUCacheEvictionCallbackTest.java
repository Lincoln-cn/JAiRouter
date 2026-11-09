package org.unreal.modelrouter.monitor.tracing.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * LRUCache 淘汰回调测试（issue #1055）
 *
 * <p>回调是「缓存外部的内存记账」能扣减的唯一通道：只要有一条「值离开缓存」的路径不回调，
 * 调用方那边的总量就会永久偏高，进而让基于总量的阈值判断一直为真。</p>
 */
class LRUCacheEvictionCallbackTest {

    @Test
    void capacityEvictionInvokesCallback() {
        List<String> evicted = new CopyOnWriteArrayList<>();
        LRUCache<String, String> cache = new LRUCache<>(2, evicted::add);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        assertEquals(2, cache.size());
        assertEquals(List.of("1"), evicted, "容量淘汰最老的值时必须回调");
    }

    @Test
    void removeEldestInvokesCallback() {
        List<String> evicted = new CopyOnWriteArrayList<>();
        LRUCache<String, String> cache = new LRUCache<>(4, evicted::add);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.removeEldest();

        assertEquals(1, cache.size());
        assertEquals(List.of("1"), evicted);
    }

    @Test
    void removeIfInvokesCallbackForMatchedEntries() {
        List<String> evicted = new CopyOnWriteArrayList<>();
        LRUCache<String, String> cache = new LRUCache<>(4, evicted::add);
        cache.put("a", "1");
        cache.put("b", "2");

        cache.removeIf(entry -> entry.getKey().equals("a"));

        assertEquals(1, cache.size());
        assertEquals(List.of("1"), evicted);
    }

    @Test
    void overwriteInvokesCallbackForPreviousValue() {
        List<String> evicted = new CopyOnWriteArrayList<>();
        LRUCache<String, String> cache = new LRUCache<>(4, evicted::add);

        cache.put("a", "1");
        cache.put("a", "2");

        assertEquals(1, cache.size());
        assertEquals(List.of("1"), evicted, "同 key 覆盖时被替换的旧值也要回调，否则同一 traceId 反复写入会让总量只增不减");
    }

    @Test
    void cacheWithoutCallbackKeepsWorking() {
        LRUCache<String, String> cache = new LRUCache<>(1);
        cache.put("a", "1");
        cache.put("b", "2");

        assertEquals(1, cache.size());
        assertEquals("2", cache.get("b"));
        assertFalse(cache.isEmpty());
    }
}
