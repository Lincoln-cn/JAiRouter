package org.unreal.modelrouter.auth.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.common.dto.JwtAccountDTO;
import org.unreal.modelrouter.persistence.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.persistence.jpa.repository.JwtAccountRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JWT 账户服务
 * v1.5.2: 使用 JPA 实现，使用 DTO 替代 Map
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtAccountService {

    private final JwtAccountRepository jwtAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有账户
     */
    public List<JwtAccountDTO> getAllAccounts() {
        return jwtAccountRepository.findAll()
                .stream()
                // issue #789：软删除的行等于「已不存在」，不能出现在账户列表里
                .filter(account -> !account.isDeletedAccount())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个账户
     */
    public JwtAccountDTO getAccount(final String username) {
        return jwtAccountRepository.findByUsername(username)
                .filter(account -> !account.isDeletedAccount())
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Account not found: " + username));
    }

    /**
     * 创建账户
     */
    @Transactional
    public JwtAccountDTO createAccount(final CreateJwtAccountRequest request) {
        final Optional<JwtAccountEntity> existing = jwtAccountRepository.findByUsername(request.getUsername());
        if (existing.isPresent() && !existing.get().isDeletedAccount()) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        try {
            // issue #789：同名行若已被软删除，就地复活 —— 直接再 save 会撞 username 唯一键，
            // 而「覆盖密码/角色/启用状态」正是创建方想要的语义。
            final JwtAccountEntity entity = existing.orElseGet(() -> JwtAccountEntity.builder()
                    .username(request.getUsername())
                    .build());
            entity.setPassword(passwordEncoder.encode(request.getPassword()));
            entity.setRoles(objectMapper.writeValueAsString(request.getRoles()));
            entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
            entity.setDeleted(Boolean.FALSE);

            JwtAccountEntity saved = jwtAccountRepository.save(entity);
            log.info("Created JWT account: {} with enabled={}", request.getUsername(), entity.getEnabled());
            return convertToDTO(saved);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize roles", e);
        }
    }

    /**
     * 更新账户
     */
    @Transactional
    public JwtAccountDTO updateAccount(final String username, final CreateJwtAccountRequest request) {
        JwtAccountEntity entity = jwtAccountRepository.findByUsername(username)
                .filter(account -> !account.isDeletedAccount())
                .orElseThrow(() -> new RuntimeException("Account not found: " + username));

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRoles() != null) {
            try {
                entity.setRoles(objectMapper.writeValueAsString(request.getRoles()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize roles", e);
            }
        }
        // issue #789：PUT 请求体里的 enabled 此前被静默丢弃 —— 控制台「取消启用」后 save 成功，
        // 但库里 enabled 没变，运行时不生效（#776/#778 抓的是同族的「写入成功但运行时不生效」）。
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }

        JwtAccountEntity saved = jwtAccountRepository.save(entity);
        log.info("Updated JWT account: {}", username);
        return convertToDTO(saved);
    }

    /**
     * 删除账户
     */
    @Transactional
    public void deleteAccount(final String username) {
        jwtAccountRepository.findByUsername(username)
                .filter(account -> !account.isDeletedAccount())
                .ifPresentOrElse(entity -> {
                    // issue #789：软删除。物理删除会让 YAML 静态账户（默认 admin）在登录时重新
                    // 走 YAML 分支，等于「控制台删了还能登录」；保留行才能让删除持续生效。
                    entity.setDeleted(Boolean.TRUE);
                    jwtAccountRepository.save(entity);
                    log.info("Deleted JWT account (soft delete): {}", username);
                }, () -> log.info("JWT account not found or already deleted: {}", username));
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(final String username, final String password) {
        return jwtAccountRepository.findByUsername(username)
                .filter(account -> !account.isDeletedAccount())
                .map(entity -> passwordEncoder.matches(password, entity.getPassword()))
                .orElse(false);
    }

    /**
     * 切换账户状态
     */
    @Transactional
    public JwtAccountDTO toggleAccountStatus(final String username, final boolean enabled) {
        JwtAccountEntity entity = jwtAccountRepository.findByUsername(username)
                .filter(account -> !account.isDeletedAccount())
                .orElseThrow(() -> new RuntimeException("Account not found: " + username));
        entity.setEnabled(enabled);
        JwtAccountEntity saved = jwtAccountRepository.save(entity);
        log.info("Toggled JWT account status: {} -> {}", username, enabled);
        return convertToDTO(saved);
    }

    private JwtAccountDTO convertToDTO(final JwtAccountEntity entity) {
        List<String> roles = List.of();
        try {
            if (entity.getRoles() != null) {
                roles = objectMapper.readValue(entity.getRoles(), List.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse roles for user: {}", entity.getUsername());
        }

        return JwtAccountDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .roles(roles)
                .enabled(entity.getEnabled() != null ? entity.getEnabled() : true)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
