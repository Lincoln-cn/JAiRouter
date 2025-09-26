package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.ApiKeyCreationResponse;
import org.unreal.modelrouter.dto.CreateApiKeyRequest;
import org.unreal.modelrouter.dto.UpdateApiKeyRequest;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API密钥管理控制器
 * 提供API密钥的增删改查等管理功能的REST API
 */
@Slf4j
@RestController
@RequestMapping("api/auth/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Key Management", description = "API密钥管理接口")
public class ApiKeyManagementController {

    private final ApiKeyService apiKeyService;

    /**
     * 获取所有API密钥（不包含密钥值）
     */
    @GetMapping
    @Operation(summary = "获取所有API密钥", description = "获取系统中所有API密钥的信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<List<ApiKeyInfo>>> getAllApiKeys() {
        return apiKeyService.getAllApiKeys()
                .map(apiKeys -> {
                    // 清除密钥值以确保安全
                    apiKeys.forEach(key -> key.setKeyValue(null));
                    return RouterResponse.success(apiKeys, "获取API密钥列表成功");
                })
                .onErrorReturn(RouterResponse.error("获取API密钥列表失败", "INTERNAL_ERROR"));
    }

    /**
     * 根据ID获取API密钥信息
     */
    @GetMapping("/{keyId}")
    @Operation(summary = "获取指定API密钥信息", description = "根据API密钥ID获取详细信息（不包含实际的密钥值）")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyInfo>> getApiKeyById(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.getApiKeyById(keyId)
                .map(apiKey -> {
                    // 清除密钥值以确保安全
                    apiKey.setKeyValue(null);
                    return RouterResponse.success(apiKey, "获取API密钥信息成功");
                })
                .onErrorReturn(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
    }

    /**
     * 创建新的API密钥
     */
    @PostMapping
    @Operation(summary = "创建新的API密钥", description = "创建一个新的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyCreationResponse>> createApiKey(
            @Parameter(description = "API密钥信息") @RequestBody CreateApiKeyRequest request) {
        
        // 生成安全的API密钥
        String keyValue = ApiKeyInfo.generateApiKey();
        String keyId = request.getKeyId() != null ? request.getKeyId() : "key-" + UUID.randomUUID();
        
        ApiKeyInfo apiKeyInfo = ApiKeyInfo.builder()
                .keyId(keyId)
                .keyValue(keyValue)
                .description(request.getDescription())
                .permissions(request.getPermissions())
                .enabled(request.isEnabled() != null ? request.isEnabled() : true)
                .expiresAt(request.getExpiresAt())
                .createdAt(LocalDateTime.now())
                .build();

        return apiKeyService.createApiKey(apiKeyInfo)
                .map(createdKey -> {
                    ApiKeyCreationResponse response = new ApiKeyCreationResponse();
                    response.setKeyId(createdKey.getKeyId());
                    response.setKeyValue(keyValue); // 只在创建时返回密钥值
                    response.setDescription(createdKey.getDescription());
                    response.setCreatedAt(createdKey.getCreatedAt());
                    return RouterResponse.success(response, "创建API密钥成功");
                })
                .onErrorReturn(RouterResponse.error("创建API密钥失败", "INTERNAL_ERROR"));
    }

    /**
     * 更新API密钥信息
     */
    @PutMapping("/{keyId}")
    @Operation(summary = "更新API密钥", description = "更新指定API密钥的信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyInfo>> updateApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId,
            @Parameter(description = "更新的API密钥信息") @RequestBody UpdateApiKeyRequest request) {
        
        ApiKeyInfo updateInfo = ApiKeyInfo.builder()
                .description(request.getDescription())
                .permissions(request.getPermissions())
                .enabled(request.isEnabled())
                .expiresAt(request.getExpiresAt())
                .build();

        return apiKeyService.updateApiKey(keyId, updateInfo)
                .map(updatedKey -> {
                    // 清除密钥值以确保安全
                    updatedKey.setKeyValue(null);
                    return RouterResponse.success(updatedKey, "更新API密钥成功");
                })
                .onErrorReturn(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
    }

    /**
     * 删除API密钥
     */
    @DeleteMapping("/{keyId}")
    @Operation(summary = "删除API密钥", description = "删除指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> deleteApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.deleteApiKey(keyId)
                .then(Mono.just(RouterResponse.success((Void) null, "删除API密钥成功")))
                .onErrorReturn(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
    }

    /**
     * 禁用API密钥
     */
    @PatchMapping("/{keyId}/disable")
    @Operation(summary = "禁用API密钥", description = "禁用指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyInfo>> disableApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.getApiKeyById(keyId)
                .flatMap(apiKey -> {
                    apiKey.setEnabled(false);
                    return apiKeyService.updateApiKey(keyId, apiKey);
                })
                .map(updatedKey -> {
                    updatedKey.setKeyValue(null);
                    return RouterResponse.success(updatedKey, "禁用API密钥成功");
                })
                .onErrorReturn(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
    }

    /**
     * 启用API密钥
     */
    @PatchMapping("/{keyId}/enable")
    @Operation(summary = "启用API密钥", description = "启用指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyInfo>> enableApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.getApiKeyById(keyId)
                .flatMap(apiKey -> {
                    apiKey.setEnabled(true);
                    return apiKeyService.updateApiKey(keyId, apiKey);
                })
                .map(updatedKey -> {
                    updatedKey.setKeyValue(null);
                    return RouterResponse.success(updatedKey, "启用API密钥成功");
                })
                .onErrorReturn(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
    }

}
