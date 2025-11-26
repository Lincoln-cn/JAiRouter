package org.unreal.modelrouter.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.SecurityConfigurationChangeEvent;
import org.unreal.modelrouter.security.config.properties.JwtUserProperties;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.store.entity.JwtAccountEntity;
import org.unreal.modelrouter.store.repository.JwtAccountRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT账户配置管理服务
 * 提供JWT用户账户的动态管理功能，支持持久化和版本管理
 * 参考 ConfigurationService 的实现模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtAccountService {

    private final JwtUserProperties jwtUserProperties;
    private final StoreManager storeManager;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final JwtAccountRepository jwtAccountRepository;
    private final ObjectMapper objectMapper;

    private static final String JWT_ACCOUNTS_CONFIG_KEY = "jwt-accounts-config";

    // ==================== 版本管理 ====================

    /**
     * 获取所有JWT账户配置版本号
     * @return 版本号列表
     */
    public List<Integer> getAllAccountVersions() {
        return storeManager.getConfigVersions(JWT_ACCOUNTS_CONFIG_KEY);
    }

    /**
     * 获取指定版本的JWT账户配置
     * @param version 版本号，0表示YAML原始配置
     * @return 配置内容
     */
    public Map<String, Object> getAccountVersionConfig(int version) {
        if (version == 0) {
            return getDefaultAccountConfig(); // YAML 原始配置
        }
        return storeManager.getConfigByVersion(JWT_ACCOUNTS_CONFIG_KEY, version);
    }

    /**
     * 保存当前JWT账户配置为新版本
     * @param config 配置内容
     * @return 新版本号
     */
    public int saveAccountAsNewVersion(Map<String, Object> config) {
        int version = getNextAccountVersion();
        storeManager.saveConfigVersion(JWT_ACCOUNTS_CONFIG_KEY, config, version);
        log.info("已保存JWT账户配置为新版本：{}", version);
        return version;
    }

    /**
     * 应用指定版本的JWT账户配置
     * @param version 版本号
     */
    public void applyAccountVersion(int version) {
        Map<String, Object> config = getAccountVersionConfig(version);
        if (config == null) {
            throw new IllegalArgumentException("JWT账户配置版本不存在: " + version);
        }
        
        // 验证配置
        if (!validateAccountConfig(config)) {
            throw new IllegalArgumentException("JWT账户配置版本 " + version + " 验证失败");
        }
        
        storeManager.saveConfig(JWT_ACCOUNTS_CONFIG_KEY, new HashMap<>(config));
        refreshAccountRuntimeConfig(config);
        
        log.info("已应用JWT账户配置版本：{}", version);
    }

    /**
     * 获取当前最新JWT账户配置版本号
     * @return 当前版本号
     */
    public int getCurrentAccountVersion() {
        List<Integer> versions = getAllAccountVersions();
        return versions.isEmpty() ? 0 : versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * 获取下一个版本号
     * @return 下一个版本号
     */
    private int getNextAccountVersion() {
        return getAllAccountVersions().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    // ==================== 账户管理操作 ====================

    /**
     * 获取所有JWT账户
     * @return 账户列表
     */
    public Mono<List<JwtUserProperties.UserAccount>> getAllAccounts() {
        return jwtAccountRepository.findAll()
                .map(entity -> {
                    try {
                        List<String> roles = objectMapper.readValue(entity.getRoles(), 
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        
                        JwtUserProperties.UserAccount account = new JwtUserProperties.UserAccount();
                        account.setUsername(entity.getUsername());
                        account.setPassword(entity.getPassword());
                        account.setRoles(roles);
                        account.setEnabled(entity.getEnabled());
                        return account;
                    } catch (JsonProcessingException e) {
                        log.error("反序列化JWT账户角色失败: {}", entity.getUsername(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collectList()
                .doOnSuccess(accounts -> {
                    log.info("从数据库获取到 {} 个JWT账户", accounts.size());
                    for (JwtUserProperties.UserAccount acc : accounts) {
                        log.debug("  - 账户: {}, 角色: {}, 启用: {}", acc.getUsername(), acc.getRoles(), acc.isEnabled());
                    }
                });
    }

    /**
     * 根据用户名获取账户
     * @param username 用户名
     * @return 账户信息
     */
    public Mono<JwtUserProperties.UserAccount> getAccountByUsername(String username) {
        return jwtAccountRepository.findByUsername(username)
                .map(entity -> {
                    try {
                        List<String> roles = objectMapper.readValue(entity.getRoles(), 
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        
                        JwtUserProperties.UserAccount account = new JwtUserProperties.UserAccount();
                        account.setUsername(entity.getUsername());
                        account.setPassword(entity.getPassword());
                        account.setRoles(roles);
                        account.setEnabled(entity.getEnabled());
                        return account;
                    } catch (JsonProcessingException e) {
                        log.error("反序列化JWT账户角色失败: {}", entity.getUsername(), e);
                        return null;
                    }
                })
                .doOnSuccess(account -> {
                    if (account != null) {
                        log.debug("从数据库获取JWT账户: {}", username);
                    } else {
                        log.debug("数据库中未找到JWT账户: {}", username);
                    }
                });
    }

    /**
     * 创建新的JWT账户
     * @param account 账户信息
     * @return 创建结果
     */
    public Mono<Void> createAccount(JwtUserProperties.UserAccount account) {
        log.info("创建新的JWT账户: {}", account.getUsername());
        
        // 验证账户信息
        try {
            validateAccount(account);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("账户信息验证失败", e));
        }
        
        // 检查用户名是否已存在（检查数据库）
        return jwtAccountRepository.existsByUsername(account.getUsername())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new IllegalArgumentException("用户名已存在: " + account.getUsername()));
                    }
                    
                    try {
                        // 加密密码
                        JwtUserProperties.UserAccount encryptedAccount = encryptAccountPassword(account);
                        
                        // 1. 保存到 jwt_accounts 表
                        JwtAccountEntity entity = JwtAccountEntity.builder()
                                .username(encryptedAccount.getUsername())
                                .password(encryptedAccount.getPassword())
                                .roles(objectMapper.writeValueAsString(encryptedAccount.getRoles()))
                                .enabled(encryptedAccount.isEnabled())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        
                        return jwtAccountRepository.save(entity)
                                .doOnSuccess(saved -> {
                                    log.info("JWT账户已保存到数据库表: {}", account.getUsername());
                                    
                                    // 2. 更新内存中的配置（同步操作，在响应式链外执行）
                                    List<JwtUserProperties.UserAccount> currentAccounts = jwtUserProperties.getAccounts();
                                    if (currentAccounts == null) {
                                        currentAccounts = new ArrayList<>();
                                    }
                                    List<JwtUserProperties.UserAccount> updatedAccounts = new ArrayList<>(currentAccounts);
                                    updatedAccounts.add(encryptedAccount);
                                    jwtUserProperties.setAccounts(updatedAccounts);
                                    
                                    log.info("JWT账户已添加到内存配置，当前账户数量: {}", updatedAccounts.size());
                                    
                                    // 3. 发布配置变更事件
                                    publishAccountChangeEvent("account-create", null, encryptedAccount);
                                    
                                    log.info("JWT账户创建成功: {}", account.getUsername());
                                })
                                .then();
                    } catch (JsonProcessingException e) {
                        log.error("序列化JWT账户角色失败: " + account.getUsername(), e);
                        return Mono.error(new RuntimeException("创建JWT账户失败", e));
                    }
                })
                .onErrorMap(e -> {
                    if (e instanceof IllegalArgumentException || e instanceof RuntimeException) {
                        return e;
                    }
                    log.error("创建JWT账户失败: " + account.getUsername(), e);
                    return new RuntimeException("创建JWT账户失败", e);
                });
    }

    /**
     * 更新JWT账户
     * @param username 用户名
     * @param account 新的账户信息
     * @return 更新结果
     */
    public Mono<Void> updateAccount(String username, JwtUserProperties.UserAccount account) {
        return Mono.fromRunnable(() -> {
            log.info("更新JWT账户: {}", username);
            
            try {
                // 验证账户信息
                validateAccount(account);
                
                // 1. 更新数据库表
                JwtAccountEntity existingEntity = jwtAccountRepository.findByUsername(username).block();
                if (existingEntity == null) {
                    throw new IllegalArgumentException("账户不存在: " + username);
                }
                
                JwtUserProperties.UserAccount oldAccount = new JwtUserProperties.UserAccount();
                oldAccount.setUsername(existingEntity.getUsername());
                oldAccount.setPassword(existingEntity.getPassword());
                oldAccount.setEnabled(existingEntity.getEnabled());
                
                // 如果密码发生变化，重新加密
                String newPassword = account.getPassword();
                if (!existingEntity.getPassword().equals(account.getPassword())) {
                    newPassword = encryptPassword(account.getPassword());
                }
                
                existingEntity.setPassword(newPassword);
                existingEntity.setRoles(objectMapper.writeValueAsString(account.getRoles()));
                existingEntity.setEnabled(account.isEnabled());
                existingEntity.setUpdatedAt(LocalDateTime.now());
                
                jwtAccountRepository.save(existingEntity).block();
                log.info("JWT账户已更新到数据库表: {}", username);
                
                // 2. 更新配置存储
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                boolean found = false;
                for (int i = 0; i < accounts.size(); i++) {
                    JwtUserProperties.UserAccount existingAccount = accounts.get(i);
                    if (existingAccount.getUsername().equals(username)) {
                        existingAccount.setPassword(newPassword);
                        existingAccount.setRoles(new ArrayList<>(account.getRoles()));
                        existingAccount.setEnabled(account.isEnabled());
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    // 如果配置中不存在，添加进去
                    JwtUserProperties.UserAccount updatedAccount = new JwtUserProperties.UserAccount();
                    updatedAccount.setUsername(username);
                    updatedAccount.setPassword(newPassword);
                    updatedAccount.setRoles(new ArrayList<>(account.getRoles()));
                    updatedAccount.setEnabled(account.isEnabled());
                    accounts.add(updatedAccount);
                }
                
                currentConfig.put("accounts", accounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 3. 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 4. 发布配置变更事件
                publishAccountChangeEvent("account-update", oldAccount, account);
                
                log.info("JWT账户更新成功: {}", username);
                
            } catch (JsonProcessingException e) {
                log.error("序列化JWT账户角色失败: " + username, e);
                throw new RuntimeException("更新JWT账户失败", e);
            } catch (Exception e) {
                log.error("更新JWT账户失败: " + username, e);
                throw new RuntimeException("更新JWT账户失败", e);
            }
        }).then();
    }

    /**
     * 删除JWT账户
     * @param username 用户名
     * @return 删除结果
     */
    public Mono<Void> deleteAccount(String username) {
        log.info("删除JWT账户: {}", username);
        
        return jwtAccountRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("账户不存在: " + username)))
                .flatMap(existingEntity -> {
                    JwtUserProperties.UserAccount deletedAccount = new JwtUserProperties.UserAccount();
                    deletedAccount.setUsername(existingEntity.getUsername());
                    deletedAccount.setPassword(existingEntity.getPassword());
                    deletedAccount.setEnabled(existingEntity.getEnabled());
                    
                    return jwtAccountRepository.deleteByUsername(username)
                            .doOnSuccess(v -> {
                                log.info("JWT账户已从数据库表删除: {}", username);
                                
                                // 2. 更新内存中的配置
                                List<JwtUserProperties.UserAccount> currentAccounts = jwtUserProperties.getAccounts();
                                if (currentAccounts != null) {
                                    List<JwtUserProperties.UserAccount> updatedAccounts = new ArrayList<>(currentAccounts);
                                    updatedAccounts.removeIf(acc -> acc.getUsername().equals(username));
                                    jwtUserProperties.setAccounts(updatedAccounts);
                                    log.info("JWT账户已从内存配置删除，当前账户数量: {}", updatedAccounts.size());
                                }
                                
                                // 3. 发布配置变更事件
                                publishAccountChangeEvent("account-delete", deletedAccount, null);
                                
                                log.info("JWT账户删除成功: {}", username);
                            });
                })
                .onErrorMap(e -> {
                    if (e instanceof IllegalArgumentException) {
                        return e;
                    }
                    log.error("删除JWT账户失败: " + username, e);
                    return new RuntimeException("删除JWT账户失败", e);
                });
    }

    /**
     * 启用/禁用JWT账户
     * @param username 用户名
     * @param enabled 是否启用
     * @return 操作结果
     */
    public Mono<Void> setAccountEnabled(String username, boolean enabled) {
        return Mono.fromRunnable(() -> {
            log.info("{}JWT账户: {}", enabled ? "启用" : "禁用", username);
            
            try {
                // 1. 更新数据库表
                JwtAccountEntity existingEntity = jwtAccountRepository.findByUsername(username).block();
                if (existingEntity == null) {
                    throw new IllegalArgumentException("账户不存在: " + username);
                }
                
                JwtUserProperties.UserAccount oldAccount = new JwtUserProperties.UserAccount();
                oldAccount.setUsername(existingEntity.getUsername());
                oldAccount.setPassword(existingEntity.getPassword());
                oldAccount.setEnabled(existingEntity.getEnabled());
                
                existingEntity.setEnabled(enabled);
                existingEntity.setUpdatedAt(LocalDateTime.now());
                jwtAccountRepository.save(existingEntity).block();
                log.info("JWT账户状态已更新到数据库表: {} -> {}", username, enabled ? "启用" : "禁用");
                
                // 2. 更新配置存储
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                boolean found = false;
                JwtUserProperties.UserAccount newAccount = null;
                
                for (JwtUserProperties.UserAccount account : accounts) {
                    if (account.getUsername().equals(username)) {
                        account.setEnabled(enabled);
                        newAccount = account;
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    throw new IllegalArgumentException("配置中账户不存在: " + username);
                }
                
                currentConfig.put("accounts", accounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 3. 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 4. 发布配置变更事件
                publishAccountChangeEvent("account-status-change", oldAccount, newAccount);
                
                log.info("JWT账户状态更新成功: {} -> {}", username, enabled ? "启用" : "禁用");
                
            } catch (Exception e) {
                log.error("更新JWT账户状态失败: " + username, e);
                throw new RuntimeException("更新JWT账户状态失败", e);
            }
        }).then();
    }

    /**
     * 批量更新JWT账户
     * @param accounts 账户列表
     * @return 更新结果
     */
    public Mono<Void> batchUpdateAccounts(List<JwtUserProperties.UserAccount> accounts) {
        return Mono.fromRunnable(() -> {
            log.info("批量更新JWT账户，数量: {}", accounts.size());
            
            try {
                // 验证所有账户
                for (JwtUserProperties.UserAccount account : accounts) {
                    validateAccount(account);
                }
                
                // 检查用户名重复
                Set<String> usernames = accounts.stream()
                        .map(JwtUserProperties.UserAccount::getUsername)
                        .collect(Collectors.toSet());
                
                if (usernames.size() != accounts.size()) {
                    throw new IllegalArgumentException("账户列表中存在重复的用户名");
                }
                
                // 加密所有账户的密码
                List<JwtUserProperties.UserAccount> encryptedAccounts = accounts.stream()
                        .map(this::encryptAccountPassword)
                        .collect(Collectors.toList());
                
                // 1. 清空数据库表
                jwtAccountRepository.deleteAll().block();
                log.info("已清空JWT账户数据库表");
                
                // 2. 批量保存到数据库表
                for (JwtUserProperties.UserAccount account : encryptedAccounts) {
                    JwtAccountEntity entity = JwtAccountEntity.builder()
                            .username(account.getUsername())
                            .password(account.getPassword())
                            .roles(objectMapper.writeValueAsString(account.getRoles()))
                            .enabled(account.isEnabled())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    jwtAccountRepository.save(entity).block();
                }
                log.info("已批量保存 {} 个JWT账户到数据库表", encryptedAccounts.size());
                
                // 3. 更新配置存储
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> oldAccounts = getAccountsFromConfig(currentConfig);
                
                currentConfig.put("accounts", encryptedAccounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 4. 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 5. 发布配置变更事件
                publishAccountChangeEvent("accounts-batch-update", oldAccounts, encryptedAccounts);
                
                log.info("JWT账户批量更新成功，数量: {}", accounts.size());
                
            } catch (JsonProcessingException e) {
                log.error("序列化JWT账户角色失败", e);
                throw new RuntimeException("批量更新JWT账户失败", e);
            } catch (Exception e) {
                log.error("批量更新JWT账户失败", e);
                throw new RuntimeException("批量更新JWT账户失败", e);
            }
        }).then();
    }

    /**
     * 重置JWT账户配置为YAML默认值
     * @return 重置结果
     */
    public Mono<Void> resetAccountsToDefault() {
        return Mono.fromRunnable(() -> {
            log.info("重置JWT账户配置为YAML默认值");
            
            try {
                // 清除持久化配置
                List<Integer> versions = storeManager.getConfigVersions(JWT_ACCOUNTS_CONFIG_KEY);
                for (Integer version : versions) {
                    storeManager.deleteConfigVersion(JWT_ACCOUNTS_CONFIG_KEY, version);
                }
                
                if (storeManager.exists(JWT_ACCOUNTS_CONFIG_KEY)) {
                    storeManager.deleteConfig(JWT_ACCOUNTS_CONFIG_KEY);
                }
                
                // 重新加载YAML配置
                // 这里需要重新初始化JwtUserProperties，但由于Spring的限制，
                // 实际上需要重启应用才能完全重置
                
                log.info("JWT账户配置已重置为默认值");
                
            } catch (Exception e) {
                log.error("重置JWT账户配置失败", e);
                throw new RuntimeException("重置JWT账户配置失败", e);
            }
        }).then();
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前持久化的JWT账户配置
     */
    private Map<String, Object> getCurrentPersistedAccountConfig() {
        try {
            // 首先尝试获取最新版本的配置
            List<Integer> versions = storeManager.getConfigVersions(JWT_ACCOUNTS_CONFIG_KEY);
            if (!versions.isEmpty()) {
                int latestVersion = versions.stream().mapToInt(Integer::intValue).max().orElse(0);
                Map<String, Object> config = storeManager.getConfigByVersion(JWT_ACCOUNTS_CONFIG_KEY, latestVersion);
                if (config != null) {
                    return config;
                }
            }
            
            // 如果没有版本配置，尝试获取当前配置
            if (storeManager.exists(JWT_ACCOUNTS_CONFIG_KEY)) {
                Map<String, Object> config = storeManager.getConfig(JWT_ACCOUNTS_CONFIG_KEY);
                if (config != null) {
                    return config;
                }
            }
            
            // 如果没有持久化配置，使用默认配置
            return getDefaultAccountConfig();
        } catch (Exception e) {
            log.warn("加载持久化JWT账户配置时发生错误: {}", e.getMessage());
            return getDefaultAccountConfig();
        }
    }

    /**
     * 获取默认JWT账户配置
     */
    private Map<String, Object> getDefaultAccountConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", jwtUserProperties.isEnabled());
        config.put("accounts", jwtUserProperties.getAccounts() != null ? new ArrayList<>(jwtUserProperties.getAccounts()) : new ArrayList<>());
        return config;
    }

    /**
     * 从配置中获取账户列表
     */
    private List<JwtUserProperties.UserAccount> getAccountsFromConfig(Map<String, Object> config) {
        Object accountsObj = config.get("accounts");
        if (accountsObj instanceof List) {
            List<?> accountsList = (List<?>) accountsObj;
            
            // 如果列表为空，直接返回空列表
            if (accountsList.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 检查第一个元素的类型
            Object firstElement = accountsList.get(0);
            
            // 如果已经是UserAccount类型，直接返回（需要复制以避免修改原对象）
            if (firstElement instanceof JwtUserProperties.UserAccount) {
                List<JwtUserProperties.UserAccount> accounts = new ArrayList<>();
                for (Object obj : accountsList) {
                    if (obj instanceof JwtUserProperties.UserAccount) {
                        accounts.add(copyAccount((JwtUserProperties.UserAccount) obj));
                    }
                }
                return accounts;
            }
            
            // 如果是Map类型，需要转换
            if (firstElement instanceof Map) {
                List<JwtUserProperties.UserAccount> accounts = new ArrayList<>();
                for (Object obj : accountsList) {
                    if (obj instanceof Map) {
                        Map<String, Object> accountMap = (Map<String, Object>) obj;
                        JwtUserProperties.UserAccount account = new JwtUserProperties.UserAccount();
                        account.setUsername((String) accountMap.get("username"));
                        account.setPassword((String) accountMap.get("password"));
                        
                        // 处理enabled字段，默认为true
                        Object enabledObj = accountMap.get("enabled");
                        account.setEnabled(enabledObj != null ? (Boolean) enabledObj : true);
                        
                        // 处理角色列表
                        Object rolesObj = accountMap.get("roles");
                        if (rolesObj instanceof List) {
                            List<String> roles = new ArrayList<>();
                            for (Object role : (List<?>) rolesObj) {
                                roles.add((String) role);
                            }
                            account.setRoles(roles);
                        } else {
                            account.setRoles(new ArrayList<>());
                        }
                        
                        accounts.add(account);
                    }
                }
                return accounts;
            }
        }
        return new ArrayList<>();
    }

    /**
     * 验证账户信息
     */
    private void validateAccount(JwtUserProperties.UserAccount account) {
        if (account.getUsername() == null || account.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        
        if (account.getPassword() == null || account.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        if (account.getRoles() == null || account.getRoles().isEmpty()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        
        // 验证角色格式
        for (String role : account.getRoles()) {
            if (role == null || role.trim().isEmpty()) {
                throw new IllegalArgumentException("角色名称不能为空");
            }
        }
    }

    /**
     * 验证账户配置
     */
    private boolean validateAccountConfig(Map<String, Object> config) {
        try {
            if (config == null || config.isEmpty()) {
                return false;
            }
            
            List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(config);
            for (JwtUserProperties.UserAccount account : accounts) {
                validateAccount(account);
            }
            
            return true;
        } catch (Exception e) {
            log.error("验证JWT账户配置失败", e);
            return false;
        }
    }

    /**
     * 加密账户密码
     */
    private JwtUserProperties.UserAccount encryptAccountPassword(JwtUserProperties.UserAccount account) {
        JwtUserProperties.UserAccount encrypted = copyAccount(account);
        
        // 如果密码不是已加密的格式，则进行加密
        if (!encrypted.getPassword().startsWith("{") || !encrypted.getPassword().contains("}")) {
            encrypted.setPassword(passwordEncoder.encode(encrypted.getPassword()));
        }
        
        return encrypted;
    }

    /**
     * 加密密码
     */
    private String encryptPassword(String password) {
        if (password.startsWith("{") && password.contains("}")) {
            // 已经是加密格式
            return password;
        }
        return passwordEncoder.encode(password);
    }

    /**
     * 复制账户对象
     */
    private JwtUserProperties.UserAccount copyAccount(JwtUserProperties.UserAccount source) {
        JwtUserProperties.UserAccount copy = new JwtUserProperties.UserAccount();
        copy.setUsername(source.getUsername());
        copy.setPassword(source.getPassword());
        copy.setRoles(new ArrayList<>(source.getRoles()));
        copy.setEnabled(source.isEnabled());
        return copy;
    }

    /**
     * 刷新运行时JWT账户配置
     */
    private void refreshAccountRuntimeConfig(Map<String, Object> config) {
        try {
            log.info("=== 开始刷新运行时JWT账户配置 ===");
            log.info("配置内容: {}", config);
            
            if (config.containsKey("enabled")) {
                jwtUserProperties.setEnabled((Boolean) config.get("enabled"));
                log.info("已更新enabled状态: {}", config.get("enabled"));
            }
            
            if (config.containsKey("accounts")) {
                log.info("配置中包含accounts字段");
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(config);
                log.info("从配置中解析出的账户数量: {}", accounts.size());
                for (JwtUserProperties.UserAccount acc : accounts) {
                    log.info("  - 解析账户: {}, 角色: {}", acc.getUsername(), acc.getRoles());
                }
                
                jwtUserProperties.setAccounts(new ArrayList<>(accounts));
                log.info("已设置到jwtUserProperties，当前账户数量: {}", jwtUserProperties.getAccounts().size());
            } else {
                log.warn("配置中不包含accounts字段！");
            }
            
            log.info("=== 运行时JWT账户配置刷新完成 ===");
        } catch (Exception e) {
            log.error("刷新运行时JWT账户配置失败", e);
        }
    }

    /**
     * 发布账户变更事件
     */
    private void publishAccountChangeEvent(String eventType, Object oldValue, Object newValue) {
        try {
            SecurityConfigurationChangeEvent event = new SecurityConfigurationChangeEvent(
                this, 
                "jwt-account-" + System.currentTimeMillis(), 
                eventType, 
                oldValue, 
                newValue
            );
            eventPublisher.publishEvent(event);
            log.debug("已发布JWT账户变更事件: {}", eventType);
        } catch (Exception e) {
            log.warn("发布JWT账户变更事件失败", e);
        }
    }

    /**
     * 检查是否存在持久化JWT账户配置
     * @return true如果存在持久化配置
     */
    public boolean hasPersistedAccountConfig() {
        try {
            List<Integer> versions = storeManager.getConfigVersions(JWT_ACCOUNTS_CONFIG_KEY);
            if (!versions.isEmpty()) {
                return true;
            }
            return storeManager.exists(JWT_ACCOUNTS_CONFIG_KEY);
        } catch (Exception e) {
            log.warn("检查持久化JWT账户配置存在性时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从YAML配置初始化JWT账户持久化存储
     */
    public void initializeJwtAccountFromYaml() {
        log.info("首次启动，将YAML JWT账户配置保存为版本1");

        try {
            // 获取YAML默认JWT账户配置
            Map<String, Object> defaultAccountConfig = getAccountVersionConfig(0);

            // 保存为第一个版本
            saveAccountAsNewVersion(defaultAccountConfig);

            log.info("YAML JWT账户配置已保存为版本1");

        } catch (Exception e) {
            log.error("从YAML配置初始化JWT账户失败", e);
            throw new RuntimeException("Failed to initialize JWT accounts from YAML config", e);
        }
    }

    /**
     * 加载最新的持久化JWT账户配置
     */
    public void loadLatestJwtAccountConfig() {
        log.info("发现持久化JWT账户配置，加载最新版本");

        try {
            int currentVersion = getCurrentAccountVersion();
            if (currentVersion > 0) {
                Map<String, Object> config = getAccountVersionConfig(currentVersion);
                if (config != null) {
                    log.info("加载JWT账户配置版本 {}", currentVersion);
                    refreshAccountRuntimeConfig(config);
                    log.info("已将JWT账户配置版本 {} 应用到运行时", currentVersion);
                } else {
                    log.warn("JWT账户配置版本 {} 不存在，使用YAML默认配置", currentVersion);
                }
            } else {
                log.info("没有持久化的JWT账户配置版本，使用YAML默认配置");
            }

        } catch (Exception e) {
            log.error("加载持久化JWT账户配置失败", e);
            throw new RuntimeException("Failed to load persisted JWT account config", e);
        }
    }

}