package org.unreal.modelrouter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountDTO;
import org.unreal.modelrouter.security.service.JwtAccountService;

import java.util.List;
import java.util.Map;

/**
 * JWT 账户控制器
 * v1.5.2: 使用 JPA 实现，使用 DTO 替代 Map
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class JwtAccountController {

    private final JwtAccountService jwtAccountService;

    /**
     * 获取所有账户
     */
    @GetMapping
    public ResponseEntity<List<JwtAccountDTO>> getAllAccounts() {
        log.debug("Getting all JWT accounts");
        return ResponseEntity.ok(jwtAccountService.getAllAccounts());
    }

    /**
     * 获取单个账户
     */
    @GetMapping("/{username}")
    public ResponseEntity<JwtAccountDTO> getAccount(@PathVariable String username) {
        log.debug("Getting JWT account: {}", username);
        return ResponseEntity.ok(jwtAccountService.getAccount(username));
    }

    /**
     * 创建账户
     */
    @PostMapping
    public ResponseEntity<JwtAccountDTO> createAccount(@RequestBody CreateJwtAccountRequest request) {
        log.info("Creating JWT account: {}", request.getUsername());
        JwtAccountDTO created = jwtAccountService.createAccount(request);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新账户
     */
    @PutMapping("/{username}")
    public ResponseEntity<JwtAccountDTO> updateAccount(
            @PathVariable String username,
            @RequestBody CreateJwtAccountRequest request) {
        log.info("Updating JWT account: {}", username);
        JwtAccountDTO updated = jwtAccountService.updateAccount(username, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String username) {
        log.info("Deleting JWT account: {}", username);
        jwtAccountService.deleteAccount(username);
        return ResponseEntity.ok().build();
    }

    /**
     * 验证密码
     */
    @PostMapping("/{username}/verify")
    public ResponseEntity<Boolean> verifyPassword(
            @PathVariable String username,
            @RequestBody Map<String, String> credentials) {
        String password = credentials.get("password");
        boolean valid = jwtAccountService.verifyPassword(username, password);
        return ResponseEntity.ok(valid);
    }
}
