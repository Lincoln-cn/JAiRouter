# 实例管理请求头配置功能指南

## 功能概述

在实例管理界面中新增了请求头配置功能，允许用户为每个服务实例配置自定义的HTTP请求头，特别是 `Authorization` 头部用于API认证。

## 功能特性

### 1. 请求头管理
- ✅ 添加自定义请求头
- ✅ 编辑现有请求头
- ✅ 删除请求头
- ✅ 批量清除所有请求头
- ✅ 快速添加 Authorization 头部

### 2. 界面功能
- **添加请求头**: 点击"添加请求头"按钮可以添加新的键值对
- **添加Authorization**: 点击"添加Authorization"按钮快速添加认证头部
- **密码保护**: Authorization 头部的值会以密码形式显示，保护敏感信息
- **实时验证**: 输入时实时验证和保存配置

### 3. 数据持久化
- 请求头配置会保存到配置文件中
- 支持版本管理，每次修改都会创建新的配置版本
- 配置会实时同步到运行时环境

## 使用方法

### 1. 添加实例时配置请求头

1. 在实例管理页面点击"添加实例"
2. 填写基本信息（名称、URL等）
3. 在"请求头配置"部分：
   - 点击"添加请求头"添加自定义头部
   - 点击"添加Authorization"快速添加认证头部
   - 输入请求头名称和值
4. 点击"保存"完成配置

### 2. 编辑现有实例的请求头

1. 在实例列表中点击要编辑的实例的"编辑"按钮
2. 在弹出的对话框中找到"请求头配置"部分
3. 修改现有请求头或添加新的请求头
4. 点击"保存"应用更改

### 3. 常用请求头示例

#### OpenAI API
```
Authorization: Bearer sk-your-api-key-here
Content-Type: application/json
```

#### Anthropic API
```
x-api-key: your-api-key-here
Content-Type: application/json
anthropic-version: 2023-06-01
```

#### Azure OpenAI
```
api-key: your-api-key-here
Content-Type: application/json
```

#### 自定义认证
```
Authorization: Bearer your-token-here
X-API-Version: v1
X-Client-ID: your-client-id
```

## 技术实现

### 后端实现
- 更新了 `UpdateInstanceDTO` 类，添加了 `headers` 字段
- 配置服务正确处理和持久化请求头配置
- 支持配置验证和版本管理

### 前端实现
- 在实例管理界面添加了请求头配置组件
- 支持动态添加/删除请求头
- Authorization 字段使用密码输入框保护敏感信息
- 实时同步配置到后端

### 数据结构
```json
{
  "name": "example-instance",
  "baseUrl": "https://api.example.com",
  "path": "/v1/chat/completions",
  "weight": 1,
  "status": "active",
  "headers": {
    "Authorization": "Bearer your-api-key-here",
    "Content-Type": "application/json",
    "X-Custom-Header": "custom-value"
  }
}
```

## 注意事项

1. **安全性**: Authorization 等敏感头部会在界面上以密码形式显示
2. **验证**: 系统会验证请求头的格式和有效性
3. **版本管理**: 每次修改都会创建新的配置版本，支持回滚
4. **实时生效**: 配置保存后会立即生效，无需重启服务

## 故障排除

### 1. 请求头不生效
- 检查请求头名称和值是否正确
- 确认实例状态为"启用"
- 查看服务日志确认配置是否正确加载

### 2. 认证失败
- 检查 Authorization 头部的格式是否正确
- 确认 API 密钥是否有效
- 检查目标服务是否支持该认证方式

### 3. 配置丢失
- 检查配置版本管理，可能需要回滚到之前的版本
- 确认配置保存操作是否成功完成

## 更新日志

### v1.0.0 (当前版本)
- ✅ 新增请求头配置功能
- ✅ 支持 Authorization 头部快速添加
- ✅ 界面优化和用户体验改进
- ✅ 数据持久化和版本管理
- ✅ 安全性增强（敏感信息保护）