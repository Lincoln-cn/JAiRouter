package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.security.dto.*;
import org.unreal.modelrouter.security.service.ApiKeyService;
import reactor.core.publisher.Mono;

/**
 * API 密钥管理控制器
 * 提供 API 密钥的增删改查等管理功能的 REST API
 * 
 * 安全改进：
 * 1. 使用强类型 DTO/VO，不使用 Map
 * 2. keyValue 使用 SHA-256 哈希存储
 * 3. 仅在创建时返回原始 keyValue
 * 4. 支持 IP 白名单和每日请求限制
 */
@Slf4j
@RestController
@RequestMapping("api/auth/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Key Management", description = "API密钥管理接口")
public class ApiKeyManagementController {

    private final ApiKeyService apiKeyService;

    /**
     * 获取所有 API 密钥列表
     * 返回不包含 keyValue 的安全副本
     */
    @GetMapping
    @Operation(summary = "获取所有API密钥", description = "获取系统中所有API密钥的信息（不包含实际密钥值）")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyListVO>> getAllApiKeys() {
        return apiKeyService.getAllApiKeysVO()
                .map(list -> RouterResponse.success(list, "获取API密钥列表成功"))
                .onErrorResume(e -> {
                    log.error("获取API密钥列表失败", e);
                    return Mono.just(RouterResponse.error("获取API密钥列表失败", "INTERNAL_ERROR"));
                });
    }

    /**
     * 根据ID获取 API 密钥详情
     * 返回不包含 keyValue 的安全副本
     */
    @GetMapping("/{keyId}")
    @Operation(summary = "获取指定API密钥信息", description = "根据API密钥ID获取详细信息（不包含实际的密钥值）")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyVO>> getApiKeyById(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.getApiKeyByIdVO(keyId)
                .map(vo -> RouterResponse.success(vo, "获取API密钥信息成功"))
                .onErrorResume(e -> {
                    log.error("获取API密钥信息失败: {}", keyId, e);
                    return Mono.just(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
                });
    }

    /**
     * 创建新的 API 密钥
     * 仅在创建时返回原始 keyValue，请前端弹窗提示用户保存
     */
    @PostMapping
    @Operation(summary = "创建新的API密钥", 
               description = "创建一个新的API密钥，返回的密钥值仅此一次显示，请妥善保存")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyCreationVO>> createApiKey(
            @Parameter(description = "API密钥创建请求") 
            @Valid @RequestBody ApiKeyCreateRequest request) {
        return apiKeyService.createApiKey(request)
                .map(vo -> RouterResponse.success(vo, 
                        "创建API密钥成功，请妥善保存密钥值，此密钥值仅此一次显示"))
                .onErrorResume(e -> {
                    log.error("创建API密钥失败", e);
                    return Mono.just(RouterResponse.error(
                            "创建API密钥失败: " + e.getMessage(), "INTERNAL_ERROR"));
                });
    }

    /**
     * 更新 API 密钥信息
     * 注意：keyValue 不可更新
     */
    @PutMapping("/{keyId}")
    @Operation(summary = "更新API密钥", 
               description = "更新指定API密钥的信息（不包含密钥值，密钥值不可更新）")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyVO>> updateApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId,
            @Parameter(description = "API密钥更新请求") 
            @Valid @RequestBody ApiKeyUpdateRequest request) {
        return apiKeyService.updateApiKey(keyId, request)
                .map(vo -> RouterResponse.success(vo, "更新API密钥成功"))
                .onErrorResume(e -> {
                    log.error("更新API密钥失败: {}", keyId, e);
                    return Mono.just(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
                });
    }

    /**
     * 删除 API 密钥
     */
    @DeleteMapping("/{keyId}")
    @Operation(summary = "删除API密钥", description = "删除指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> deleteApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.deleteApiKey(keyId)
                .then(Mono.just(RouterResponse.<Void>success(null, "删除API密钥成功")))
                .onErrorResume(e -> {
                    log.error("删除API密钥失败: {}", keyId, e);
                    return Mono.just(RouterResponse.<Void>error("API密钥不存在", "NOT_FOUND"));
                });
    }

    /**
     * 禁用 API 密钥
     */
    @PatchMapping("/{keyId}/disable")
    @Operation(summary = "禁用API密钥", description = "禁用指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyVO>> disableApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.disableApiKey(keyId)
                .map(vo -> RouterResponse.success(vo, "禁用API密钥成功"))
                .onErrorResume(e -> {
                    log.error("禁用API密钥失败: {}", keyId, e);
                    return Mono.just(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
                });
    }

    /**
     * 启用 API 密钥
     */
    @PatchMapping("/{keyId}/enable")
    @Operation(summary = "启用API密钥", description = "启用指定的API密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyVO>> enableApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        return apiKeyService.enableApiKey(keyId)
                .map(vo -> RouterResponse.success(vo, "启用API密钥成功"))
                .onErrorResume(e -> {
                    log.error("启用API密钥失败: {}", keyId, e);
                    return Mono.just(RouterResponse.error("API密钥不存在", "NOT_FOUND"));
                });
    }

    /**
     * 重置 API 密钥（生成新的 keyValue）
     * 旧的密钥值将失效，新的密钥值仅显示一次
     */
    @PostMapping("/{keyId}/reset")
    @Operation(summary = "重置API密钥", 
               description = "重置API密钥值，旧的密钥值将失效，新的密钥值仅此一次显示")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<ApiKeyCreationVO>> resetApiKey(
            @Parameter(description = "API密钥ID") @PathVariable("keyId") String keyId) {
        // 先删除旧的密钥，然后创建新的
        return apiKeyService.getApiKeyByIdVO(keyId)
                .flatMap(oldKey -> {
                    // 创建新的密钥请求，保留原有属性
                    ApiKeyCreateRequest newRequest = ApiKeyCreateRequest.builder()
                            .keyId(keyId)  // 保持相同的 keyId
                            .description(oldKey.getDescription())
                            .permissions(oldKey.getPermissions())
                            .enabled(oldKey.isEnabled())
                            .expiresAt(oldKey.getExpiresAt())
                            .build();
                    
                    // 先删除旧密钥
                    return apiKeyService.deleteApiKey(keyId)
                            .then(apiKeyService.createApiKey(newRequest));
                })
                .map(vo -> RouterResponse.success(vo, 
                        "重置API密钥成功，请妥善保存新的密钥值，此密钥值仅此一次显示"))
                .onErrorResume(e -> {
                    log.error("重置API密钥失败: {}", keyId, e);
                    return Mono.just(RouterResponse.error("重置API密钥失败: " + e.getMessage(), 
                            "INTERNAL_ERROR"));
                });
    }
}