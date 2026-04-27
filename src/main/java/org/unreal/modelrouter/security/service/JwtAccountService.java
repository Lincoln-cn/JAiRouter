package org.unreal.modelrouter.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.CreateJwtAccountRequest;
import org.unreal.modelrouter.dto.JwtAccountDTO;
import org.unreal.modelrouter.jpa.entity.JwtAccountEntity;
import org.unreal.modelrouter.jpa.repository.JwtAccountRepository;

import java.util.List;
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
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个账户
     */
    public JwtAccountDTO getAccount(final String username) {
        return jwtAccountRepository.findByUsername(username)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Account not found: " + username));
    }

    /**
     * 创建账户
     */
    @Transactional
    public JwtAccountDTO createAccount(final CreateJwtAccountRequest request) {
        if (jwtAccountRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        try {
            JwtAccountEntity entity = JwtAccountEntity.builder()
                    .username(request.getUsername())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .roles(objectMapper.writeValueAsString(request.getRoles()))
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .build();

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
    public JwtAccountDTO updateAccount(final String username,final CreateJwtAccountRequest request) {
        JwtAccountEntity entity = jwtAccountRepository.findByUsername(username)
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

        JwtAccountEntity saved = jwtAccountRepository.save(entity);
        log.info("Updated JWT account: {}", username);
        return convertToDTO(saved);
    }

    /**
     * 删除账户
     */
    @Transactional
    public void deleteAccount(final String username) {
        jwtAccountRepository.findByUsername(username).ifPresent(entity -> {
            jwtAccountRepository.delete(entity);
            log.info("Deleted JWT account: {}", username);
        });
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(final String username,final String password) {
        return jwtAccountRepository.findByUsername(username)
                .map(entity -> passwordEncoder.matches(password, entity.getPassword()))
                .orElse(false);
    }

    /**
     * 切换账户状态
     */
    @Transactional
    public JwtAccountDTO toggleAccountStatus(final String username,final boolean enabled) {
        JwtAccountEntity entity = jwtAccountRepository.findByUsername(username)
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
