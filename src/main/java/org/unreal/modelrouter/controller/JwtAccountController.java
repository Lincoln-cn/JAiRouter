package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.JwtAccountConfigStatus;
import org.unreal.modelrouter.dto.JwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountResponse;
import org.unreal.modelrouter.security.config.properties.JwtUserProperties;
import org.unreal.modelrouter.security.service.JwtAccountService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * JWT账户管理控制器
 * 提供JWT用户账户的REST API管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/security/jwt/accounts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
@Tag(name = "JWT账户管理", description = "JWT用户账户的动态管理API")
public class JwtAccountController {

    private final JwtAccountService accountConfigurationService;
    private final JwtUserProperties jwtUserProperties;

    // ==================== 账户查询接口 ====================

    @GetMapping
    @Operation(summary = "获取所有JWT账户", description = "获取系统中所有JWT用户账户列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<List<JwtAccountResponse>>> getAllAccounts() {
        log.debug("获取所有JWT账户");
        
        return accountConfigurationService.getAllAccounts()
                .map(accounts -> accounts.stream()
                        .map(jwtUserProperties::convertToResponse)
                        .toList())
                .map(data -> RouterResponse.success(data, "成功获取JWT账户列表"))
                .onErrorResume(ex -> {
                    log.error("获取所有JWT账户失败", ex);
                    return Mono.just(RouterResponse.error("获取JWT账户列表失败: " + ex.getMessage(), "ACCOUNT_LIST_ERROR"));
                });
    }

    @GetMapping("/{username}")
    @Operation(summary = "根据用户名获取JWT账户", description = "根据用户名获取指定的JWT用户账户信息")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #username")
    public Mono<RouterResponse<JwtAccountResponse>> getAccountByUsername(
            @Parameter(description = "用户名", required = true)
            @PathVariable("username") @NotBlank String username) {
        log.debug("获取JWT账户: {}", username);
        
        return accountConfigurationService.getAccountByUsername(username)
                .map(account -> {
                    if (account == null) {
                        return RouterResponse.error("JWT账户不存在: " + username, "ACCOUNT_NOT_FOUND");
                    }
                    JwtAccountResponse response = jwtUserProperties.convertToResponse(account);
                    return RouterResponse.success(response, "成功获取JWT账户");
                });
    }

    // ==================== 账户管理接口 ====================

    @PostMapping
    @Operation(summary = "创建JWT账户", description = "创建新的JWT用户账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> createAccount(
            @Parameter(description = "账户信息", required = true)
            @RequestBody @Valid JwtAccountRequest request) {
        log.info("创建JWT账户: {}", request.getUsername());
        
        return accountConfigurationService.createAccount(jwtUserProperties.convertToAccount(request))
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户创建成功: " + request.getUsername())))
                .onErrorResume(ex -> {
                    log.error("创建JWT账户失败: " + request.getUsername(), ex);
                    return Mono.just(RouterResponse.error("创建JWT账户失败: " + ex.getMessage(), "ACCOUNT_CREATE_ERROR"));
                });
    }

    @PutMapping("/{username}")
    @Operation(summary = "更新JWT账户", description = "更新指定用户名的JWT账户信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> updateAccount(
            @Parameter(description = "用户名", required = true)
            @PathVariable("username") @NotBlank String username,
            @Parameter(description = "新的账户信息", required = true)
            @RequestBody @Valid JwtAccountRequest request) {
        log.info("更新JWT账户: {}", username);
        
        return accountConfigurationService.updateAccount(username, jwtUserProperties.convertToAccount(request))
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户更新成功: " + username)))
                .onErrorResume(ex -> {
                    log.error("更新JWT账户失败: " + username, ex);
                    return Mono.just(RouterResponse.error("更新JWT账户失败: " + ex.getMessage(), "ACCOUNT_UPDATE_ERROR"));
                });
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "删除JWT账户", description = "删除指定用户名的JWT账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> deleteAccount(
            @Parameter(description = "用户名", required = true)
            @PathVariable("username") @NotBlank String username) {
        log.info("删除JWT账户: {}", username);
        
        return accountConfigurationService.deleteAccount(username)
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户删除成功: " + username)))
                .onErrorResume(ex -> {
                    log.error("删除JWT账户失败: " + username, ex);
                    return Mono.just(RouterResponse.error("删除JWT账户失败: " + ex.getMessage(), "ACCOUNT_DELETE_ERROR"));
                });
    }

    @PatchMapping("/{username}/status")
    @Operation(summary = "启用/禁用JWT账户", description = "启用或禁用指定用户名的JWT账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> setAccountEnabled(
            @Parameter(description = "用户名", required = true)
            @PathVariable("username") @NotBlank String username,
            @Parameter(description = "是否启用", required = true)
            @RequestParam boolean enabled) {
        log.info("{}JWT账户: {}", enabled ? "启用" : "禁用", username);
        
        return accountConfigurationService.setAccountEnabled(username, enabled)
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(
                        RouterResponse.<Void>success(String.format("JWT账户%s成功: %s", enabled ? "启用" : "禁用", username))))
                .onErrorResume(ex -> {
                    log.error("{}JWT账户失败: {}", enabled ? "启用" : "禁用", username, ex);
                    return Mono.just(RouterResponse.error(
                        String.format("JWT账户%s失败: %s", enabled ? "启用" : "禁用", ex.getMessage()), 
                        "ACCOUNT_STATUS_ERROR"));
                });
    }

    @PutMapping("/batch")
    @Operation(summary = "批量更新JWT账户", description = "批量更新JWT用户账户列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> batchUpdateAccounts(
            @Parameter(description = "账户列表", required = true)
            @RequestBody @Valid @NotEmpty List<JwtAccountRequest> requests) {
        log.info("批量更新JWT账户，数量: {}", requests.size());
        
        List<JwtUserProperties.UserAccount> accounts = requests.stream()
                .map(jwtUserProperties::convertToAccount)
                .toList();
        
        return accountConfigurationService.batchUpdateAccounts(accounts)
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户批量更新成功，数量: " + requests.size())))
                .onErrorResume(ex -> {
                    log.error("批量更新JWT账户失败，数量: " + requests.size(), ex);
                    return Mono.just(RouterResponse.error("批量更新JWT账户失败: " + ex.getMessage(), "ACCOUNT_BATCH_UPDATE_ERROR"));
                });
    }

    // ==================== 版本管理接口 ====================

    @GetMapping("/versions")
    @Operation(summary = "获取JWT账户配置版本列表", description = "获取所有JWT账户配置的版本号列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<List<Integer>>> getAllAccountVersions() {
        log.debug("获取JWT账户配置版本列表");
        
        return Mono.fromCallable(() -> accountConfigurationService.getAllAccountVersions())
                .map(data -> RouterResponse.success(data, "成功获取JWT账户配置版本列表"))
                .onErrorResume(ex -> {
                    log.error("获取JWT账户配置版本列表失败", ex);
                    return Mono.just(RouterResponse.error("获取JWT账户配置版本列表失败: " + ex.getMessage(), "VERSION_LIST_ERROR"));
                });
    }

    @GetMapping("/versions/{version}")
    @Operation(summary = "获取指定版本的JWT账户配置", description = "获取指定版本号的JWT账户配置内容")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Map<String, Object>>> getAccountVersionConfig(
            @Parameter(description = "版本号，0表示YAML原始配置", required = true)
            @PathVariable("version") int version) {
        log.debug("获取JWT账户配置版本: {}", version);
        
        return Mono.fromCallable(() -> accountConfigurationService.getAccountVersionConfig(version))
                .map(config -> {
                    if (config == null) {
                        return RouterResponse.<Map<String, Object>>error("JWT账户配置版本不存在: " + version, "VERSION_NOT_FOUND");
                    }
                    return RouterResponse.success(config, "成功获取JWT账户配置版本");
                })
                .onErrorResume(ex -> {
                    log.error("获取JWT账户配置版本失败", ex);
                    return Mono.just(RouterResponse.error("获取JWT账户配置版本失败: " + ex.getMessage(), "VERSION_CONFIG_ERROR"));
                });
    }

    @PostMapping("/versions/{version}/apply")
    @Operation(summary = "应用指定版本的JWT账户配置", description = "将指定版本的JWT账户配置应用为当前配置")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> applyAccountVersion(
            @Parameter(description = "版本号", required = true)
            @PathVariable("version") int version) {
        log.info("应用JWT账户配置版本: {}", version);
        
        return Mono.fromRunnable(() -> accountConfigurationService.applyAccountVersion(version))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户配置版本应用成功: " + version)))
                .onErrorResume(ex -> {
                    log.error("应用JWT账户配置版本失败: " + version, ex);
                    return Mono.just(RouterResponse.error("应用JWT账户配置版本失败: " + ex.getMessage(), "VERSION_APPLY_ERROR"));
                });
    }

    @GetMapping("/versions/current")
    @Operation(summary = "获取当前JWT账户配置版本号", description = "获取当前使用的JWT账户配置版本号")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Integer>> getCurrentAccountVersion() {
        log.debug("获取当前JWT账户配置版本号");
        
        return Mono.fromCallable(() -> accountConfigurationService.getCurrentAccountVersion())
                .map(data -> RouterResponse.success(data, "成功获取当前JWT账户配置版本号"))
                .onErrorResume(ex -> {
                    log.error("获取当前JWT账户配置版本号失败", ex);
                    return Mono.just(RouterResponse.error("获取当前JWT账户配置版本号失败: " + ex.getMessage(), "CURRENT_VERSION_ERROR"));
                });
    }

    // ==================== 配置管理接口 ====================

    @PostMapping("/reset")
    @Operation(summary = "重置JWT账户配置", description = "重置JWT账户配置为YAML默认值")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<Void>> resetAccountsToDefault() {
        log.info("重置JWT账户配置为默认值");
        
        return accountConfigurationService.resetAccountsToDefault()
                .then(Mono.fromRunnable(() -> {}))
                .then(Mono.just(RouterResponse.<Void>success("JWT账户配置已重置为默认值")))
                .onErrorResume(ex -> {
                    log.error("重置JWT账户配置为默认值失败", ex);
                    return Mono.just(RouterResponse.error("重置JWT账户配置为默认值失败: " + ex.getMessage(), "RESET_DEFAULT_ERROR"));
                });
    }

    @GetMapping("/config/status")
    @Operation(summary = "获取JWT账户配置状态", description = "获取JWT账户配置的状态信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RouterResponse<JwtAccountConfigStatus>> getAccountConfigStatus() {
        log.debug("获取JWT账户配置状态");
        
        return Mono.fromCallable(() -> {
            JwtAccountConfigStatus status = new JwtAccountConfigStatus();
            status.setHasPersistedConfig(accountConfigurationService.hasPersistedAccountConfig());
            status.setCurrentVersion(accountConfigurationService.getCurrentAccountVersion());
            status.setTotalVersions(accountConfigurationService.getAllAccountVersions().size());
            return status;
        })
        .map(data -> RouterResponse.success(data, "成功获取JWT账户配置状态"))
        .onErrorResume(ex -> {
            log.error("获取JWT账户配置状态失败", ex);
            return Mono.just(RouterResponse.error("获取JWT账户配置状态失败: " + ex.getMessage(), "CONFIG_STATUS_ERROR"));
        });
    }
}
