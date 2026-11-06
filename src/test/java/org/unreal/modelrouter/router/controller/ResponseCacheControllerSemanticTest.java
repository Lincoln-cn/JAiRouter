package org.unreal.modelrouter.router.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.cache.SemanticCacheProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 语义缓存状态端点.
 *
 * <p>只读：语义缓存的隔离层级与准入参数改了要重启，所以这里不提供写入接口，
 * 端点只把当前生效值摊开给控制台看。</p>
 */
@DisplayName("语义缓存状态端点")
class ResponseCacheControllerSemanticTest {

    @Test
    @DisplayName("装配完整时返回各层级开关与条目数")
    void reportsConfiguredValues() {
        final SemanticCacheProperties props = new SemanticCacheProperties();
        props.setEnabled(true);
        props.setTenantScopeEnabled(true);
        props.setPublicScopeEnabled(false);
        props.setMaxPublicChars(128);
        props.setMaxHammingDistance(3);
        props.setTtl(Duration.ofMinutes(10));

        final org.unreal.modelrouter.router.cache.SemanticCacheService service =
                mock(org.unreal.modelrouter.router.cache.SemanticCacheService.class);
        when(service.size()).thenReturn(7);

        final ResponseCacheController controller = new ResponseCacheController(
                mock(ResponseCacheService.class), service, props);

        final ResponseEntity<RouterResponse<Map<String, Object>>> entity =
                controller.getSemanticCacheStatus();
        final Map<String, Object> data = entity.getBody().getData();

        assertThat(data).containsEntry("enabled", true);
        assertThat(data).containsEntry("tenantScopeEnabled", true);
        assertThat(data).containsEntry("publicScopeEnabled", false);
        assertThat(data).containsEntry("maxPublicChars", 128);
        assertThat(data).containsEntry("ttlSeconds", 600L);
        assertThat(data).containsEntry("size", 7);
    }

    @Test
    @DisplayName("未装配时返回 enabled=false 而不是抛异常")
    void toleratesMissingBeans() {
        final ResponseCacheController controller = new ResponseCacheController(
                mock(ResponseCacheService.class), null, null);

        final Map<String, Object> data = controller.getSemanticCacheStatus().getBody().getData();

        assertThat(data).containsEntry("enabled", false);
        assertThat(data).containsEntry("size", 0);
        assertThat(data.get("publicAllowedTenants")).isEqualTo(List.of());
    }
}
