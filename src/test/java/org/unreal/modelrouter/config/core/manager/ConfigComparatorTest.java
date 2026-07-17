package org.unreal.modelrouter.config.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigComparatorTest {

    private ConfigComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new ConfigComparator();
    }

    @Test
    void compareVersions_withSameConfig_returnsNotChanged() {
        Map<String, Object> config = Map.of("key", "value");

        Map<String, Object> result = comparator.compareVersions(1, 2, config, config);

        assertNotNull(result);
        assertEquals(1, result.get("version1"));
        assertEquals(2, result.get("version2"));
        assertFalse((Boolean) result.get("isChanged"));
    }

    @Test
    void compareVersions_withDifferentConfig_returnsChanged() {
        Map<String, Object> config1 = Map.of("key", "value1");
        Map<String, Object> config2 = Map.of("key", "value2");

        Map<String, Object> result = comparator.compareVersions(1, 2, config1, config2);

        assertNotNull(result);
        assertTrue((Boolean) result.get("isChanged"));
    }

    @Test
    void compareVersions_withNullConfigs_returnsNotChanged() {
        Map<String, Object> result = comparator.compareVersions(1, 2, null, null);

        assertNotNull(result);
        assertFalse((Boolean) result.get("isChanged"));
    }

    @Test
    void isChanged_withSameMaps_returnsFalse() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key", "value");
        map1.put("number", 42);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("key", "value");
        map2.put("number", 42);

        boolean result = comparator.isChanged(map1, map2);

        assertFalse(result);
    }

    @Test
    void isChanged_withDifferentMaps_returnsTrue() {
        Map<String, Object> map1 = Map.of("key", "value1");
        Map<String, Object> map2 = Map.of("key", "value2");

        boolean result = comparator.isChanged(map1, map2);

        assertTrue(result);
    }

    @Test
    void isChanged_withNullMaps_returnsFalse() {
        boolean result = comparator.isChanged(null, null);
        assertFalse(result);
    }

    @Test
    void isChanged_withOneNull_returnsTrue() {
        Map<String, Object> map = Map.of("key", "value");

        boolean result1 = comparator.isChanged(null, map);
        boolean result2 = comparator.isChanged(map, null);

        assertTrue(result1);
        assertTrue(result2);
    }

    @Test
    void isChanged_withNestedMaps_detectsChange() {
        Map<String, Object> nested1 = new HashMap<>();
        nested1.put("a", 1);
        Map<String, Object> map1 = new HashMap<>();
        map1.put("nested", nested1);

        Map<String, Object> nested2 = new HashMap<>();
        nested2.put("a", 2);
        Map<String, Object> map2 = new HashMap<>();
        map2.put("nested", nested2);

        boolean result = comparator.isChanged(map1, map2);

        assertTrue(result);
    }

    @Test
    void isChanged_withNestedMaps_noChange() {
        Map<String, Object> nested1 = new HashMap<>();
        nested1.put("a", 1);
        Map<String, Object> map1 = new HashMap<>();
        map1.put("nested", nested1);

        Map<String, Object> nested2 = new HashMap<>();
        nested2.put("a", 1);
        Map<String, Object> map2 = new HashMap<>();
        map2.put("nested", nested2);

        boolean result = comparator.isChanged(map1, map2);

        assertFalse(result);
    }

    @Test
    void isChanged_withAddedKey_returnsTrue() {
        Map<String, Object> map1 = Map.of("key1", "value1");
        Map<String, Object> map2 = new HashMap<>(map1);
        map2.put("key2", "value2");

        boolean result = comparator.isChanged(map1, map2);

        assertTrue(result);
    }

    @Test
    void isChanged_withRemovedKey_returnsTrue() {
        Map<String, Object> map1 = new HashMap<>();
        map1.put("key1", "value1");
        map1.put("key2", "value2");

        Map<String, Object> map2 = Map.of("key1", "value1");

        boolean result = comparator.isChanged(map1, map2);

        assertTrue(result);
    }
}
