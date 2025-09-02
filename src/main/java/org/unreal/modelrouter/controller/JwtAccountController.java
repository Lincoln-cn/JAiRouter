package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.JwtAccountConfigStatus;
import org.unreal.modelrouter.dto.JwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountResponse;
import org.unreal.modelrouter.security.config.JwtUserProperties;
import org.unreal.modelrouter.security.service.JwtAccountConfigurationService;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

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

    private final JwtAccountConfigurationService accountConfigurationService;
    private final JwtUserProperties jwtUserProperties;

    // ==================== 账户查询接口 ====================

    @GetMapping
    @Operation(summary = "获取所有JWT账户", description = "获取系统中所有JWT用户账户列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<List<JwtAccountResponse>>> getAllAccounts() {
        log.debug("获取所有JWT账户");
        
        return accountConfigurationService.getAllAccounts()
                .map(accounts -> accounts.stream()
                        .map(jwtUserProperties::convertToResponse)
                        .toList())
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.debug("成功获取 {} 个JWT账户", result.getBody().size()))
                .doOnError(error -> log.error("获取JWT账户列表失败", error));
    }

    @GetMapping("/{username}")
    @Operation(summary = "根据用户名获取JWT账户", description = "根据用户名获取指定的JWT用户账户信息")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #username")
    public Mono<ResponseEntity<JwtAccountResponse>> getAccountByUsername(
            @Parameter(description = "用户名", required = true)
            @PathVariable @NotBlank String username) {
        log.debug("获取JWT账户: {}", username);
        
        return accountConfigurationService.getAccountByUsername(username)
                .map(account -> {
                    if (account == null) {
                        return ResponseEntity.notFound().<JwtAccountResponse>build();
                    }
                    return ResponseEntity.ok(jwtUserProperties.convertToResponse(account));
                })
                .doOnSuccess(result -> {
                    if (result.getStatusCode().is2xxSuccessful()) {
                        log.debug("成功获取JWT账户: {}", username);
                    } else {
                        log.debug("JWT账户不存在: {}", username);
                    }
                })
                .doOnError(error -> log.error("获取JWT账户失败: " + username, error));
    }

    // ==================== 账户管理接口 ====================

    @PostMapping
    @Operation(summary = "创建JWT账户", description = "创建新的JWT用户账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> createAccount(
            @Parameter(description = "账户信息", required = true)
            @RequestBody @Valid JwtAccountRequest request) {
        log.info("创建JWT账户: {}", request.getUsername());
        
        return accountConfigurationService.createAccount(jwtUserProperties.convertToAccount(request))
                .then(Mono.just(ResponseEntity.ok("JWT账户创建成功: " + request.getUsername())))
                .doOnSuccess(result -> log.info("JWT账户创建成功: {}", request.getUsername()))
                .doOnError(error -> log.error("创建JWT账户失败: " + request.getUsername(), error));
    }

    @PutMapping("/{username}")
    @Operation(summary = "更新JWT账户", description = "更新指定用户名的JWT账户信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> updateAccount(
            @Parameter(description = "用户名", required = true)
            @PathVariable @NotBlank String username,
            @Parameter(description = "新的账户信息", required = true)
            @RequestBody @Valid JwtAccountRequest request) {
        log.info("更新JWT账户: {}", username);
        
        return accountConfigurationService.updateAccount(username, jwtUserProperties.convertToAccount(request))
                .then(Mono.just(ResponseEntity.ok("JWT账户更新成功: " + username)))
                .doOnSuccess(result -> log.info("JWT账户更新成功: {}", username))
                .doOnError(error -> log.error("更新JWT账户失败: " + username, error));
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "删除JWT账户", description = "删除指定用户名的JWT账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> deleteAccount(
            @Parameter(description = "用户名", required = true)
            @PathVariable @NotBlank String username) {
        log.info("删除JWT账户: {}", username);
        
        return accountConfigurationService.deleteAccount(username)
                .then(Mono.just(ResponseEntity.ok("JWT账户删除成功: " + username)))
                .doOnSuccess(result -> log.info("JWT账户删除成功: {}", username))
                .doOnError(error -> log.error("删除JWT账户失败: " + username, error));
    }

    @PatchMapping("/{username}/status")
    @Operation(summary = "启用/禁用JWT账户", description = "启用或禁用指定用户名的JWT账户")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> setAccountEnabled(
            @Parameter(description = "用户名", required = true)
            @PathVariable @NotBlank String username,
            @Parameter(description = "是否启用", required = true)
            @RequestParam boolean enabled) {
        log.info("{}JWT账户: {}", enabled ? "启用" : "禁用", username);
        
        return accountConfigurationService.setAccountEnabled(username, enabled)
                .then(Mono.just(ResponseEntity.ok(
                        String.format("JWT账户%s成功: %s", enabled ? "启用" : "禁用", username))))
                .doOnSuccess(result -> log.info("JWT账户状态更新成功: {} -> {}", username, enabled ? "启用" : "禁用"))
                .doOnError(error -> log.error("更新JWT账户状态失败: " + username, error));
    }

    @PutMapping("/batch")
    @Operation(summary = "批量更新JWT账户", description = "批量更新JWT用户账户列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> batchUpdateAccounts(
            @Parameter(description = "账户列表", required = true)
            @RequestBody @Valid @NotEmpty List<JwtAccountRequest> requests) {
        log.info("批量更新JWT账户，数量: {}", requests.size());
        
        List<JwtUserProperties.UserAccount> accounts = requests.stream()
                .map(jwtUserProperties::convertToAccount)
                .toList();
        
        return accountConfigurationService.batchUpdateAccounts(accounts)
                .then(Mono.just(ResponseEntity.ok("JWT账户批量更新成功，数量: " + requests.size())))
                .doOnSuccess(result -> log.info("JWT账户批量更新成功，数量: {}", requests.size()))
                .doOnError(error -> log.error("批量更新JWT账户失败", error));
    }

    // ==================== 版本管理接口 ====================

    @GetMapping("/versions")
    @Operation(summary = "获取JWT账户配置版本列表", description = "获取所有JWT账户配置的版本号列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<List<Integer>>> getAllAccountVersions() {
        log.debug("获取JWT账户配置版本列表");
        
        return Mono.fromCallable(() -> accountConfigurationService.getAllAccountVersions())
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.debug("成功获取 {} 个JWT账户配置版本", result.getBody().size()))
                .doOnError(error -> log.error("获取JWT账户配置版本列表失败", error));
    }

    @GetMapping("/versions/{version}")
    @Operation(summary = "获取指定版本的JWT账户配置", description = "获取指定版本号的JWT账户配置内容")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> getAccountVersionConfig(
            @Parameter(description = "版本号，0表示YAML原始配置", required = true)
            @PathVariable int version) {
        log.debug("获取JWT账户配置版本: {}", version);
        
        return Mono.fromCallable(() -> accountConfigurationService.getAccountVersionConfig(version))
                .map(config -> {
                    if (config == null) {
                        return ResponseEntity.notFound().<Map<String, Object>>build();
                    }
                    return ResponseEntity.ok(config);
                })
                .doOnSuccess(result -> {
                    if (result.getStatusCode().is2xxSuccessful()) {
                        log.debug("成功获取JWT账户配置版本: {}", version);
                    } else {
                        log.debug("JWT账户配置版本不存在: {}", version);
                    }
                })
                .doOnError(error -> log.error("获取JWT账户配置版本失败: " + version, error));
    }

    @PostMapping("/versions/{version}/apply")
    @Operation(summary = "应用指定版本的JWT账户配置", description = "将指定版本的JWT账户配置应用为当前配置")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> applyAccountVersion(
            @Parameter(description = "版本号", required = true)
            @PathVariable int version) {
        log.info("应用JWT账户配置版本: {}", version);
        
        return Mono.fromRunnable(() -> accountConfigurationService.applyAccountVersion(version))
                .then(Mono.just(ResponseEntity.ok("JWT账户配置版本应用成功: " + version)))
                .doOnSuccess(result -> log.info("JWT账户配置版本应用成功: {}", version))
                .doOnError(error -> log.error("应用JWT账户配置版本失败: " + version, error));
    }

    @GetMapping("/versions/current")
    @Operation(summary = "获取当前JWT账户配置版本号", description = "获取当前使用的JWT账户配置版本号")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Integer>> getCurrentAccountVersion() {
        log.debug("获取当前JWT账户配置版本号");
        
        return Mono.fromCallable(() -> accountConfigurationService.getCurrentAccountVersion())
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.debug("当前JWT账户配置版本号: {}", result.getBody()))
                .doOnError(error -> log.error("获取当前JWT账户配置版本号失败", error));
    }

    // ==================== 配置管理接口 ====================

    @PostMapping("/reset")
    @Operation(summary = "重置JWT账户配置", description = "重置JWT账户配置为YAML默认值")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> resetAccountsToDefault() {
        log.info("重置JWT账户配置为默认值");
        
        return accountConfigurationService.resetAccountsToDefault()
                .then(Mono.just(ResponseEntity.ok("JWT账户配置已重置为默认值")))
                .doOnSuccess(result -> log.info("JWT账户配置重置成功"))
                .doOnError(error -> log.error("重置JWT账户配置失败", error));
    }

    @GetMapping("/config/status")
    @Operation(summary = "获取JWT账户配置状态", description = "获取JWT账户配置的状态信息")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<JwtAccountConfigStatus>> getAccountConfigStatus() {
        log.debug("获取JWT账户配置状态");
        
        return Mono.fromCallable(() -> {
            JwtAccountConfigStatus status = new JwtAccountConfigStatus();
            status.setHasPersistedConfig(accountConfigurationService.hasPersistedAccountConfig());
            status.setCurrentVersion(accountConfigurationService.getCurrentAccountVersion());
            status.setTotalVersions(accountConfigurationService.getAllAccountVersions().size());
            return status;
        })
        .map(ResponseEntity::ok)
        .doOnSuccess(result -> log.debug("成功获取JWT账户配置状态"))
        .doOnError(error -> log.error("获取JWT账户配置状态失败", error));
    }
}