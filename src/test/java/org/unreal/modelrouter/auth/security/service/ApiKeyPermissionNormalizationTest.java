package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ApiKeyService#validatePermissions(List)} 的入库口径。
 *
 * <p>回归背景：该方法早期用 {@code permission.trim().toLowerCase()} 归一后直接入库，
 * 把服务类型里的驼峰值 {@code imgGen} / {@code imgEdit} 存成了 {@code imggen} / {@code imgedit}。
 * 后端判权用 {@code equalsIgnoreCase} 所以不影响鉴权，但前端的权限标签按 i18n 键
 * {@code apiKeys.permissions.imgGen} 查不到，页面上就直接显示原始小写串。
 *
 * <p>入库必须用枚举的权威值；查询侧仍要不区分大小写（{@code ServiceType.isValid} 保证）。
 */
@DisplayName("API Key 服务类型权限归一")
class ApiKeyPermissionNormalizationTest {

    /** 构造器只保存 SecurityProperties，校验逻辑不依赖任何字段，传 null 即可。 */
    private final ApiKeyService service = new ApiKeyService(null, null, null);

    @Test
    @DisplayName("小写 imggen / imgedit 入库后归一为驼峰权威值")
    void lowerCaseImageTypesAreStoredAsCanonical() {
        assertEquals(List.of("imgGen", "imgEdit"), service.validatePermissions(List.of("imggen", "imgedit")));
    }

    @Test
    @DisplayName("驼峰输入保持不变（幂等）")
    void canonicalValuesAreIdempotent() {
        assertEquals(List.of("imgGen", "imgEdit"), service.validatePermissions(List.of("imgGen", "imgEdit")));
        assertEquals(List.of("chat", "embedding"), service.validatePermissions(List.of("chat", "embedding")));
    }

    @Test
    @DisplayName("前后空白与大小写混合都按权威值落库")
    void whitespaceAndMixedCaseAreNormalized() {
        assertEquals(List.of("imgGen"), service.validatePermissions(List.of("  IMGGEN  ")));
    }

    @Test
    @DisplayName("无效值被忽略，不污染权限列表")
    void unknownValuesAreDropped() {
        assertEquals(List.of("chat"), service.validatePermissions(List.of("chat", "not-a-service")));
    }

    @Test
    @DisplayName("旧权限格式（read/write/...）仍展开为全部服务类型")
    void legacyPermissionStillExpandsToAllServiceTypes() {
        List<String> expanded = service.validatePermissions(List.of("read"));
        assertEquals(7, expanded.size());
        assertEquals(true, expanded.contains("imgGen"));
        assertEquals(true, expanded.contains("imgEdit"));
    }
}
