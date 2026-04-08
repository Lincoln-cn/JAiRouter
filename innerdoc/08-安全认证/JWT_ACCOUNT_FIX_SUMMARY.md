# JWT账号创建和列表刷新问题修复总结

## 问题描述

账号创建页面提示成功，但是没有正确保存，并且列表刷新后也没有新创建的账号。

## 根本原因

1. **数据存储不一致**：JWT账号的创建逻辑只更新了配置存储（`config_data`表），但没有同步更新实体存储（`jwt_accounts`表）
2. **数据读取源错误**：`getAllAccounts()` 方法从内存配置读取，而不是从数据库表读取
3. **响应式编程问题**：在WebFlux响应式环境中使用了阻塞调用（`.block()`），导致运行时错误

## 修复方案

### 1. 修改数据存储架构

**修改文件**: `src/main/java/org/unreal/modelrouter/security/service/JwtAccountService.java`

- 创建账号时同时保存到 `jwt_accounts` 表和内存配置
- 删除账号时同时从 `jwt_accounts` 表和内存配置删除
- 查询账号时直接从 `jwt_accounts` 表读取

### 2. 修复响应式编程问题

将所有阻塞调用改为响应式调用：

**修改前**:
```java
public Mono<Void> createAccount(JwtUserProperties.UserAccount account) {
    return Mono.fromRunnable(() -> {
        // 使用 .block() 阻塞调用
        Boolean exists = jwtAccountRepository.existsByUsername(username).block();
        jwtAccountRepository.save(entity).block();
    }).then();
}
```

**修改后**:
```java
public Mono<Void> createAccount(JwtUserProperties.UserAccount account) {
    return jwtAccountRepository.existsByUsername(account.getUsername())
            .flatMap(exists -> {
                // 完全响应式，不使用阻塞调用
                return jwtAccountRepository.save(entity)
                        .doOnSuccess(saved -> {
                            // 更新内存配置
                        })
                        .then();
          