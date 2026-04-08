package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountDTO;
import org.unreal.modelrouter.security.service.JwtAccountService;

import java.util.List;
import java.util.Map;

/**
 * JWT 账户控制器
 * v1.5.2: 使用 JPA 实现，使用 DTO 替代 Map
 * v1.5.3: 使用 RouterResponse 包装响应
 */
@Slf4j
@RestController
@RequestMapping("/api/security/jwt/accounts")
@RequiredArgsConstructor
public class JwtAccountController {

    private final JwtAccountService jwtAccountService;

    /**
     * 获取所有账户
     */
    @GetMapping
    public ResponseEntity<RouterResponse<List<JwtAccountDTO>>> getAllAccounts() {
        log.debug("Getting all JWT accounts");
        List<JwtAccountDTO> accounts = jwtAccountService.getAllAccounts();
        return ResponseEntity.ok(RouterResponse.success(accounts, "获取账户列表成功"));
    }

    /**
     * 获取单个账户
     */
    @GetMapping("/{username}")
    public ResponseEntity<RouterResponse<JwtAccountDTO>> getAccount(@PathVariable String username) {
        log.debug("Getting JWT account: {}", username);
        JwtAccountDTO account = jwtAccountService.getAccount(username);
        return ResponseEntity.ok(RouterResponse.success(account, "获取账户信息成功"));
    }

    /**
     * 创建账户
     */
    @PostMapping
    public ResponseEntity<RouterResponse<JwtAccountDTO>> createAccount(@RequestBody CreateJwtAccountRequest request) {
        log.info("Creating JWT account: {}", request.getUsername());
        JwtAccountDTO created = jwtAccountService.createAccount(request);
        return ResponseEntity.ok(RouterResponse.success(created, "账户创建成功"));
    }

    /**
     * 更新账户
     */
    @PutMapping("/{username}")
    public ResponseEntity<RouterResponse<JwtAccountDTO>> updateAccount(
            @PathVariable String username,
            @RequestBody CreateJwtAccountRequest request) {
        log.info("Updating JWT account: {}", username);
        JwtAccountDTO updated = jwtAccountService.updateAccount(username, request);
        return ResponseEntity.ok(RouterResponse.success(updated, "账户更新成功"));
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<RouterResponse<Void>> deleteAccount(@PathVariable String username) {
        log.info("Deleting JWT account: {}", username);
        jwtAccountService.deleteAccount(username);
        return ResponseEntity.ok(RouterResponse.success(null, "账户删除成功"));
    }

    /**
     * 验证密码
     */
    @PostMapping("/{username}/verify")
    public ResponseEntity<RouterResponse<Boolean>> verifyPassword(
            @PathVariable String username,
            @RequestBody Map<String, String> credentials) {
        String password = credentials.get("password");
        boolean valid = jwtAccountService.verifyPassword(username, password);
        return ResponseEntity.ok(RouterResponse.success(valid, "密码验证完成"));
    }

    /**
     * 切换账户状态
     */
    @PatchMapping("/{username}/status")
    public ResponseEntity<RouterResponse<Void>> toggleAccountStatus(
            @PathVariable String username,
            @RequestParam boolean enabled) {
        log.info("Toggling JWT account status: {} -> {}", username, enabled);
        // TODO: 实现状态切换逻辑
        return ResponseEntity.ok(RouterResponse.success(null, "账户状态更新成功"));
    }

    /**
     * 获取账户配置版本列表
     */
    @GetMapping("/versions")
    public ResponseEntity<RouterResponse<List<Integer>>> getAccountVersions() {
        // TODO: 实现版本管理
        return ResponseEntity.ok(RouterResponse.success(List.of(1), "获取版本列表成功"));
    }

    /**
     * 获取指定版本配置
     */
    @GetMapping("/versions/{version}")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getAccountVersionConfig(
            @PathVariable int version) {
        // TODO: 实现版本配置获取
        return ResponseEntity.ok(RouterResponse.success(Map.of("version", version), "获取版本配置成功"));
    }

    /**
     * 应用指定版本配置
     */
    @PostMapping("/versions/{version}/apply")
    public ResponseEntity<RouterResponse<Void>> applyAccountVersion(@PathVariable int version) {
        // TODO: 实现版本应用
        return ResponseEntity.ok(RouterResponse.success(null, "版本应用成功"));
    }

    /**
     * 获取当前版本号
     */
    @GetMapping("/versions/current")
    public ResponseEntity<RouterResponse<Integer>> getCurrentAccountVersion() {
        // TODO: 实现当前版本获取
        return ResponseEntity.ok(RouterResponse.success(1, "获取当前版本成功"));
    }

    /**
     * 重置账户配置为默认值
     */
    @PostMapping("/reset")
    public ResponseEntity<RouterResponse<Void>> resetAccountsToDefault() {
        // TODO: 实现重置逻辑
        return ResponseEntity.ok(RouterResponse.success(null, "账户配置已重置"));
    }

    /**
     * 获取账户配置状态
     */
    @GetMapping("/config/status")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getAccountConfigStatus() {
        Map<String, Object> status = Map.of(
            "hasPersistedConfig", true,
            "currentVersion", 1,
            "totalVersions", 1
        );
        return ResponseEntity.ok(RouterResponse.success(status, "获取配置状态成功"));
    }
}