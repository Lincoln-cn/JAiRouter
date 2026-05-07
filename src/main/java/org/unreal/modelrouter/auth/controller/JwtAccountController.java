package org.unreal.modelrouter.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.common.dto.JwtAccountDTO;
import org.unreal.modelrouter.auth.security.service.JwtAccountService;

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
    public ResponseEntity<RouterResponse<JwtAccountDTO>> getAccount(@PathVariable final String username) {
        log.debug("Getting JWT account: {}", username);
        JwtAccountDTO account = jwtAccountService.getAccount(username);
        return ResponseEntity.ok(RouterResponse.success(account, "获取账户信息成功"));
    }

    /**
     * 创建账户
     */
    @PostMapping
    public ResponseEntity<RouterResponse<JwtAccountDTO>> createAccount(@RequestBody final CreateJwtAccountRequest request) {
        log.info("Creating JWT account: {}", request.getUsername());
        JwtAccountDTO created = jwtAccountService.createAccount(request);
        return ResponseEntity.ok(RouterResponse.success(created, "账户创建成功"));
    }

    /**
     * 更新账户
     */
    @PutMapping("/{username}")
    public ResponseEntity<RouterResponse<JwtAccountDTO>> updateAccount(
            @PathVariable final String username,
            @RequestBody final CreateJwtAccountRequest request) {
        log.info("Updating JWT account: {}", username);
        JwtAccountDTO updated = jwtAccountService.updateAccount(username, request);
        return ResponseEntity.ok(RouterResponse.success(updated, "账户更新成功"));
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<RouterResponse<Void>> deleteAccount(@PathVariable final String username) {
        log.info("Deleting JWT account: {}", username);
        jwtAccountService.deleteAccount(username);
        return ResponseEntity.ok(RouterResponse.success(null, "账户删除成功"));
    }

    /**
     * 验证密码
     */
    @PostMapping("/{username}/verify")
    public ResponseEntity<RouterResponse<Boolean>> verifyPassword(
            @PathVariable final String username,
            @RequestBody final Map<String, String> credentials) {
        String password = credentials.get("password");
        boolean valid = jwtAccountService.verifyPassword(username, password);
        return ResponseEntity.ok(RouterResponse.success(valid, "密码验证完成"));
    }

    /**
     * 切换账户状态
     */
    @PatchMapping("/{username}/status")
    public ResponseEntity<RouterResponse<JwtAccountDTO>> toggleAccountStatus(
            @PathVariable final String username,
            @RequestParam final boolean enabled) {
        log.info("Toggling JWT account status: {} -> {}", username, enabled);
        JwtAccountDTO updated = jwtAccountService.toggleAccountStatus(username, enabled);
        String message = enabled ? "账户已启用" : "账户已禁用";
        return ResponseEntity.ok(RouterResponse.success(updated, message));
    }
}