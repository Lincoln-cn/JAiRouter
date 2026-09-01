package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SelectInstanceOptimizer.filterByTags 单元测试
 * 验证 v2.9.7 标签过滤:AND 语义、缺失键排除、空 tags 原样、null 安全
 */
@DisplayName("SelectInstanceOptimizer.filterByTags 标签过滤测试")
@ExtendWith(MockitoExtension.class)
class SelectInstanceOptimizerTest {

    @Mock private ServiceStateManager serviceStateManager;
    @Mock private CircuitBreakerManager circuitBreakerManager;

    private SelectInstanceOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new SelectInstanceOptimizer(serviceStateManager, circuitBreakerManager);
    }

    private ModelInstance instance(final String id, final Map<String, String> tags) {
        ModelInstance i = new ModelInstance();
        i.setInstanceId(id);
        i.setName(id);
        i.setBaseUrl("http://" + id + ".local");
        i.setStatus("active");
        i.setTags(tags);
        return i;
    }

    @Nested
    @DisplayName("AND 语义")
    class AndSemanticsTests {

        @Test
        @DisplayName("TAG-UNIT-001: 实例 tags 包含全部所需键值对时保留")
        void testAllRequiredTagsPresent_retained() {
            ModelInstance gpu = instance("inst-gpu", Map.of("gpu_type", "a100", "region", "cn-north"));
            ModelInstance nonGpu = instance("inst-cpu", Map.of("region", "cn-north"));
            List<ModelInstance> instances = List.of(gpu, nonGpu);

            List<ModelInstance> result =
                    optimizer.filterByTags(instances, Map.of("gpu_type", "a100", "region", "cn-north"));

            assertEquals(1, result.size());
            assertEquals("inst-gpu", result.get(0).getInstanceId());
        }

        @Test
        @DisplayName("TAG-UNIT-002: 缺失所需键的实例被排除")
        void testMissingKey_excluded() {
            ModelInstance gpu = instance("inst-gpu", Map.of("gpu_type", "a100"));
            ModelInstance noTag = instance("inst-plain", Map.of());
            List<ModelInstance> instances = List.of(gpu, noTag);

            List<ModelInstance> result = optimizer.filterByTags(instances, Map.of("gpu_type", "a100"));

            assertEquals(1, result.size());
            assertEquals("inst-gpu", result.get(0).getInstanceId());
        }

        @Test
        @DisplayName("TAG-UNIT-003: 键存在但值不等的实例被排除")
        void testValueMismatch_excluded() {
            ModelInstance a100 = instance("inst-a100", Map.of("gpu_type", "a100"));
            ModelInstance h100 = instance("inst-h100", Map.of("gpu_type", "h100"));
            List<ModelInstance> instances = List.of(a100, h100);

            List<ModelInstance> result = optimizer.filterByTags(instances, Map.of("gpu_type", "a100"));

            assertEquals(1, result.size());
            assertEquals("inst-a100", result.get(0).getInstanceId());
        }

        @Test
        @DisplayName("TAG-UNIT-004: 实例含额外标签不影响匹配")
        void testExtraTags_doNotAffect() {
            ModelInstance rich = instance("inst-rich",
                    Map.of("gpu_type", "a100", "region", "cn-north", "tier", "premium"));
            List<ModelInstance> instances = List.of(rich);

            List<ModelInstance> result = optimizer.filterByTags(instances, Map.of("gpu_type", "a100"));

            assertEquals(1, result.size());
            assertEquals("inst-rich", result.get(0).getInstanceId());
        }
    }

    @Nested
    @DisplayName("null 安全与空输入")
    class NullSafetyTests {

        @Test
        @DisplayName("TAG-UNIT-005: 实例 tags 为 null 时被排除")
        void testInstanceTagsNull_excluded() {
            ModelInstance noTags = instance("inst-no-tags", null);
            ModelInstance withTags = instance("inst-with-tags", Map.of("gpu_type", "a100"));
            List<ModelInstance> instances = List.of(noTags, withTags);

            List<ModelInstance> result = optimizer.filterByTags(instances, Map.of("gpu_type", "a100"));

            assertEquals(1, result.size());
            assertEquals("inst-with-tags", result.get(0).getInstanceId());
        }

        @Test
        @DisplayName("TAG-UNIT-006: requiredTags 为 null 时原样返回")
        void testRequiredTagsNull_returnsOriginal() {
            List<ModelInstance> instances = List.of(
                    instance("inst-a", Map.of("gpu_type", "a100")),
                    instance("inst-b", null));

            List<ModelInstance> result = optimizer.filterByTags(instances, null);

            assertSame(instances, result, "requiredTags=null 应返回原列表引用");
        }

        @Test
        @DisplayName("TAG-UNIT-007: requiredTags 为空 Map 时原样返回")
        void testRequiredTagsEmpty_returnsOriginal() {
            List<ModelInstance> instances = List.of(
                    instance("inst-a", Map.of("gpu_type", "a100")),
                    instance("inst-b", null));

            List<ModelInstance> result = optimizer.filterByTags(instances, Map.of());

            assertSame(instances, result, "requiredTags 为空应返回原列表引用");
        }

        @Test
        @DisplayName("TAG-UNIT-008: instances 为 null 时返回 null")
        void testInstancesNull_returnsNull() {
            assertNull(optimizer.filterByTags(null, Map.of("gpu_type", "a100")));
        }

        @Test
        @DisplayName("TAG-UNIT-009: instances 为空列表时返回原列表")
        void testInstancesEmpty_returnsOriginal() {
            List<ModelInstance> empty = List.of();

            List<ModelInstance> result = optimizer.filterByTags(empty, Map.of("gpu_type", "a100"));

            assertSame(empty, result, "instances 为空应返回原列表引用");
        }
    }
}
