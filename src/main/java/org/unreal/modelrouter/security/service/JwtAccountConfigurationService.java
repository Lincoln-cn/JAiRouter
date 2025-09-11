package org.unreal.modelrouter.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.JwtUserProperties;
import org.unreal.modelrouter.security.config.SecurityConfigurationChangeEvent;
import org.unreal.modelrouter.store.StoreManager;
import reactor.core.publisher.Mono;

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
public class JwtAccountConfigurationService {

    private final JwtUserProperties jwtUserProperties;
    private final StoreManager storeManager;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

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
        return Mono.fromCallable(() -> {
            log.debug("获取所有JWT账户");
            return new ArrayList<>(jwtUserProperties.getAccounts());
        });
    }

    /**
     * 根据用户名获取账户
     * @param username 用户名
     * @return 账户信息
     */
    public Mono<JwtUserProperties.UserAccount> getAccountByUsername(String username) {
        return Mono.fromCallable(() -> {
            log.debug("获取JWT账户: {}", username);
            return jwtUserProperties.getAccounts().stream()
                    .filter(account -> account.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);
        });
    }

    /**
     * 创建新的JWT账户
     * @param account 账户信息
     * @return 创建结果
     */
    public Mono<Void> createAccount(JwtUserProperties.UserAccount account) {
        return Mono.fromRunnable(() -> {
            log.info("创建新的JWT账户: {}", account.getUsername());
            
            try {
                // 验证账户信息
                validateAccount(account);
                
                // 检查用户名是否已存在
                boolean exists = jwtUserProperties.getAccounts().stream()
                        .anyMatch(existingAccount -> existingAccount.getUsername().equals(account.getUsername()));
                
                if (exists) {
                    throw new IllegalArgumentException("用户名已存在: " + account.getUsername());
                }
                
                // 加密密码
                JwtUserProperties.UserAccount encryptedAccount = encryptAccountPassword(account);
                
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                // 添加新账户
                accounts.add(encryptedAccount);
                currentConfig.put("accounts", accounts);
                
                // 保存为新版本
                saveAccountAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                jwtUserProperties.getAccounts().add(encryptedAccount);
                
                // 发布配置变更事件
                publishAccountChangeEvent("account-create", null, encryptedAccount);
                
                log.info("JWT账户创建成功: {}", account.getUsername());
                
            } catch (Exception e) {
                log.error("创建JWT账户失败: " + account.getUsername(), e);
                throw new RuntimeException("创建JWT账户失败", e);
            }
        }).then();
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
                
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                // 查找并更新账户
                boolean found = false;
                JwtUserProperties.UserAccount oldAccount = null;
                
                for (int i = 0; i < accounts.size(); i++) {
                    JwtUserProperties.UserAccount existingAccount = accounts.get(i);
                    if (existingAccount.getUsername().equals(username)) {
                        oldAccount = copyAccount(existingAccount);
                        
                        // 更新账户信息
                        existingAccount.setPassword(account.getPassword());
                        existingAccount.setRoles(new ArrayList<>(account.getRoles()));
                        existingAccount.setEnabled(account.isEnabled());
                        
                        // 如果密码发生变化，重新加密
                        if (!oldAccount.getPassword().equals(account.getPassword())) {
                            existingAccount.setPassword(encryptPassword(account.getPassword()));
                        }
                        
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    throw new IllegalArgumentException("账户不存在: " + username);
                }
                
                // 保存为新版本
                currentConfig.put("accounts", accounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 发布配置变更事件
                publishAccountChangeEvent("account-update", oldAccount, account);
                
                log.info("JWT账户更新成功: {}", username);
                
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
        return Mono.fromRunnable(() -> {
            log.info("删除JWT账户: {}", username);
            
            try {
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                // 查找并删除账户
                JwtUserProperties.UserAccount deletedAccount = null;
                boolean removed = false;
                
                Iterator<JwtUserProperties.UserAccount> iterator = accounts.iterator();
                while (iterator.hasNext()) {
                    JwtUserProperties.UserAccount account = iterator.next();
                    if (account.getUsername().equals(username)) {
                        deletedAccount = copyAccount(account);
                        iterator.remove();
                        removed = true;
                        break;
                    }
                }
                
                if (!removed) {
                    throw new IllegalArgumentException("账户不存在: " + username);
                }
                
                // 保存为新版本
                currentConfig.put("accounts", accounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 发布配置变更事件
                publishAccountChangeEvent("account-delete", deletedAccount, null);
                
                log.info("JWT账户删除成功: {}", username);
                
            } catch (Exception e) {
                log.error("删除JWT账户失败: " + username, e);
                throw new RuntimeException("删除JWT账户失败", e);
            }
        }).then();
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
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> accounts = getAccountsFromConfig(currentConfig);
                
                // 查找并更新账户状态
                boolean found = false;
                JwtUserProperties.UserAccount oldAccount = null;
                
                for (JwtUserProperties.UserAccount account : accounts) {
                    if (account.getUsername().equals(username)) {
                        oldAccount = copyAccount(account);
                        account.setEnabled(enabled);
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    throw new IllegalArgumentException("账户不存在: " + username);
                }
                
                // 保存为新版本
                currentConfig.put("accounts", accounts);
                saveAccountAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                refreshAccountRuntimeConfig(currentConfig);
                
                // 发布配置变更事件
                publishAccountChangeEvent("account-status-change", oldAccount, null);
                
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
                
                // 获取当前配置
                Map<String, Object> currentConfig = getCurrentPersistedAccountConfig();
                List<JwtUserProperties.UserAccount> oldAccounts = getAccountsFromConfig(currentConfig);
                
                // 更新配置
                currentConfig.put("accounts", encryptedAccounts);
                
                // 保存为新版本
                saveAccountAsNewVersion(currentConfig);
                
                // 更新内存中的配置
                jwtUserProperties.setAccounts(new ArrayList<>(encryptedAccounts));
                
                // 发布配置变更事件
                publishAccountChangeEvent("accounts-batch-update", oldAccounts, encryptedAccounts);
                
                log.info("JWT账户批量更新成功，数量: {}", accounts.size());
                
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
    @SuppressWarnings("unchecked")
    private List<JwtUserProperties.UserAccount> getAccountsFromConfig(Map<String, Object> config) {
        Object accountsObj = config.get("accounts");
        if (accountsObj instanceof List) {
            return new ArrayList<>((List<JwtUserProperties.UserAccount>) accountsObj);
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
    @SuppressWarnings("unchecked")
    private void refreshAccountRuntimeConfig(Map<String, Object> config) {
        try {
            if (config.containsKey("enabled")) {
                jwtUserProperties.setEnabled((Boolean) config.get("enabled"));
            }
            
            if (config.containsKey("accounts")) {
                List<JwtUserProperties.UserAccount> accounts = 
                        (List<JwtUserProperties.UserAccount>) config.get("accounts");
                jwtUserProperties.setAccounts(new ArrayList<>(accounts));
            }
            
            log.debug("运行时JWT账户配置已刷新");
        } catch (Exception e) {
            log.warn("刷新运行时JWT账户配置失败", e);
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
}