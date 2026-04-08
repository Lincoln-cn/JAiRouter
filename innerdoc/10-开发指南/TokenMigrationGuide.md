# Token类迁移指南

## 概述

为了简化代码结构和避免重复，我们将以下三个类合并为一个统一的 `JwtTokenInfo` 类：

- `TokenResponse` - 基础令牌响应类
- `UserTokenInfo` - 用户令牌信息类  
- `JwtTokenInfo` - JWT令牌详细信息类

## 迁移映射

### 从 TokenResponse 迁移

```java
// 旧代码
TokenResponse response = new TokenResponse(token, "Bearer", "Success", LocalDateTime.now());

// 新代码
JwtTokenInfo tokenInfo = new JwtTokenInfo(token, "Bearer", "Success", LocalDateTime.now());
```

### 从 UserTokenInfo 迁移

```java
// 旧代码
UserTokenInfo userToken = new UserTokenInfo(userId, token, issuedAt, expiresAt, "active");

// 新代码
JwtTokenInfo tokenInfo = new JwtTokenInfo(userId, token, issuedAt, expiresAt, TokenStatus.ACTIVE);
// 或者使用字符串状态（向后兼容）
tokenInfo.setStatusString("active");
```

### 从旧 JwtTokenInfo 迁移

新的 `JwtTokenInfo` 类包含了所有旧功能，并添加了更多字段和便利方法。

## 新增功能

### 1. 状态检查便利方法

```java
JwtTokenInfo tokenInfo = ...;

// 检查令牌状态
boolean isActive = tokenInfo.isActive();
boolean isRevoked = tokenInfo.isRevoked();
boolean isExpired = tokenInfo.isExpired();
```

### 2. 向后兼容的状态处理

```java
// 支持字符串状态（兼容旧代码）
tokenInfo.setStatusString("active");
String status = tokenInfo.getStatusString(); // 返回 "active"

// 支持枚举状态（推荐新代码）
tokenInfo.setStatus(TokenStatus.ACTIVE);
TokenStatus status = tokenInfo.getStatus();
```

### 3. 新增字段

- `userAgent` - 用户代理信息
- 更完整的时间戳管理
- 更好的元数据支持

## 建议的迁移步骤

1. **第一阶段**: 更新所有使用 `TokenResponse` 的地方
2. **第二阶段**: 更新所有使用 `UserTokenInfo` 的地方
3. **第三阶段**: 验证所有功能正常工作
4. **第四阶段**: 删除旧的类文件（可选）

## 注意事项

- 新的 `JwtTokenInfo` 类完全向后兼容
- 所有旧的getter/setter方法都保留
- 添加了新的便利方法来简化常见操作
- toString方法已优化，不会泄露敏感信息（令牌值被截断）