# API Key管理重构文档

## 概述

本次重构将原来的 `ApiKeyProperties` 和 `ApiKeyInfo` 两个相似的类合并为一个统一的 `ApiKey` 类，并实现了更安全的API Key管理机制。

## 主要变更

### 1. 类结构合并

**之前：**
- `ApiKeyProperties` - 用于配置和存储
- `ApiKeyInfo` - 用于业务逻辑和API响应
- 两个类功能重复，需要相互转换

**现在：**
- `ApiKey` - 统一的API Key数据模型
- 包含所有必要字段和业务逻辑
- 提供安全的副本创建方法

### 2. 安全性增强

#### keyValue字段安全控制
- **创建时**：返回完整的 `ApiKey` 对象，包含 `keyValue`，用于前端弹窗展示
- **其他操作**：返回安全副本，`keyValue` 字段为 `null`
- **JSON序列化**：`keyValue` 字段标记为 `READ_ONLY`

#### 前端展示要求
- 创建API Key成功后，前端必须弹窗展示 `keyValue`
- 提示用户这是唯一一次显示机会，需要妥善保存
- 后续查询操作不再返回 `keyValue`

### 3. API变更

#### 创建API Key响应
```json
{
  "code": 200,
  "message": "创建API密钥成功，请妥善保存密钥值，此密钥值仅此一次显示",
  "data": {
    "keyId": "key-12345",
    "keyValue": "sk-abcdef123456...",  // 仅创建时返回
    "description": "测试密钥",
    "permissions": ["READ", "WRITE"],
    "enabled": true,
    "expiresAt": "2025-12-31 23:59:59",
    "createdAt": "2024-01-01 10:00:00",
    "metadata": {}
  }
}
```

#### 查询API Key响应
```json
{
  "code": 200,
  "message": "获取API密钥信息成功",
  "data": {
    "keyId": "key-12345",
    "keyValue": null,  // 安全起见，不返回
    "description": "测试密钥",
    "permissions": ["READ", "WRITE"],
    "enabled": true,
    "expiresAt": "2025-12-31 23:59:59",
    "createdAt": "2024-01-01 10:00:00",
    "metadata": {}
  }
}
```

## 新的ApiKey类特性

### 核心方法

```java
public class ApiKey {
    // 创建安全副本（不包含keyValue）
    public ApiKey createSecureCopy();
    
    // 创建包含keyValue的副本（仅用于创建响应）
    public ApiKey createCreationResponse();
    
    // 检查是否过期
    public boolean isExpired();
    
    // 检查是否有效（启用且未过期）
    public boolean isValid();
    
    // 检查权限
    public boolean hasPermission(String permission);
    
    // 生成API Key值
    public static String generateApiKey();
    public static String generateApiKey(String prefix, int length);
}
```

### JSON注解

```java
@JsonInclude(JsonInclude.Include.NON_NULL)  // 不序列化null值
@JsonProperty(access = JsonProperty.Access.READ_ONLY)  // keyValue只读
```

## 服务层变更

### ApiKeyService主要变更

1. **统一数据模型**：所有方法使用 `ApiKey` 类型
2. **安全返回**：除创建操作外，所有方法返回安全副本
3. **兼容性**：支持从旧格式 `ApiKeyProperties` 转换
4. **缓存优化**：使用统一的 `ApiKey` 对象缓存

### 方法签名变更

```java
// 之前
public Mono<ApiKeyInfo> validateApiKey(String keyValue);
public Mono<ApiKeyInfo> createApiKey(ApiKeyInfo apiKeyInfo);
public Mono<List<ApiKeyInfo>> getAllApiKeys();

// 现在
public Mono<ApiKey> validateApiKey(String keyValue);
public Mono<ApiKey> createApiKey(ApiKey apiKey);
public Mono<List<ApiKey>> getAllApiKeys();
```

## 控制器变更

### ApiKeyManagementController

1. **类型统一**：所有方法使用 `ApiKey` 类型
2. **创建响应**：使用专门的 `ApiKeyCreationResponse` DTO
3. **安全提示**：创建成功消息提醒用户保存密钥值

## 数据迁移

### 自动兼容

服务启动时会自动处理数据格式兼容：

1. **新格式优先**：优先尝试解析为 `ApiKey` 格式
2. **旧格式兼容**：解析失败时尝试 `ApiKeyProperties` 格式并转换
3. **无缝升级**：用户无需手动迁移数据

### 转换逻辑

```java
private ApiKey convertFromOldFormat(ApiKeyProperties oldFormat) {
    return ApiKey.builder()
            .keyId(oldFormat.getKeyId())
            .keyValue(oldFormat.getKeyValue())
            .description(oldFormat.getDescription())
            .permissions(oldFormat.getPermissions())
            .expiresAt(oldFormat.getExpiresAt())
            .createdAt(oldFormat.getCreatedAt())
            .enabled(oldFormat.isEnabled())
            .metadata(oldFormat.getMetadata())
            .build();
}
```

## 前端适配指南

### 创建API Key流程

1. **发送创建请求**
2. **接收响应**：包含 `keyValue` 字段
3. **弹窗展示**：显示完整的API Key值
4. **用户确认**：提示用户复制并保存
5. **关闭弹窗**：确认后关闭，不再显示

### 示例代码

```javascript
// 创建API Key
async function createApiKey(requestData) {
    const response = await fetch('/api/auth/api-keys', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData)
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
        // 弹窗展示API Key值
        showApiKeyModal({
            keyId: result.data.keyId,
            keyValue: result.data.keyValue,
            message: '请妥善保存此API Key，关闭弹窗后将无法再次查看'
        });
    }
}

// 弹窗组件
function showApiKeyModal({ keyId, keyValue, message }) {
    // 显示模态框
    // 包含复制按钮
    // 包含确认按钮
    // 强调这是唯一一次显示机会
}
```

## 测试更新

所有相关测试已更新以使用新的 `ApiKey` 类：

- `ApiKeyServiceTest`
- `ApiKeyAuthenticationProviderTest`
- `ApiKeyManagementControllerTest`

## 向后兼容性

1. **旧类保留**：`ApiKeyProperties` 和 `ApiKeyInfo` 标记为 `@Deprecated`
2. **数据兼容**：自动转换旧格式数据
3. **API兼容**：响应格式保持兼容，仅增强安全性

## 注意事项

1. **密钥安全**：确保前端正确处理 `keyValue` 的显示逻辑
2. **用户体验**：创建成功后必须提醒用户保存密钥
3. **日志安全**：避免在日志中记录完整的API Key值
4. **缓存清理**：旧的缓存数据会自动转换为新格式

## 升级建议

1. **立即升级**：新功能向后兼容，建议立即升级
2. **前端更新**：更新前端代码以正确处理创建响应
3. **测试验证**：验证API Key创建和使用流程
4. **监控观察**：观察系统运行状况，确保迁移成功