# 审计日志管理页面数据问题排查指南

## 问题描述

前端审计日志管理页面 (`frontend/src/views/security/AuditLogManagement.vue`) 没有显示数据。

## 问题分析

经过详细分析，发现了以下几个问题：

### 1. 审计服务未被实际调用

虽然 `ExtendedSecurityAuditServiceImpl` 服务已经实现，但在实际的JWT令牌操作和API Key操作中，并没有调用审计方法来记录事件。

**现状：**
- 审计服务接口和实现都存在
- 单元测试通过
- 但实际业务代码中没有集成审计调用

### 2. 前端API路径配置问题

前端配置的API基础路径与后端控制器路径不匹配：

**前端配置：** `/admin/api` (在 `frontend/.env` 中)
**后端控制器：** `/api/security/audit/extended`

### 3. 缺少测试数据

由于审计方法未被实际调用，数据库中没有审计事件数据。

## 解决方案

### 1. 修复前端API路径配置

已更新前端环境配置文件：

```bash
# frontend/.env
VITE_API_BASE_URL=/api

# frontend/.env.production  
VITE_API_BASE_URL=/api
```

### 2. 添加测试数据生成端点

在 `ExtendedSecurityAuditController` 中添加了测试数据生成端点：

```java
@PostMapping("/test-data/generate")
public Mono<RouterResponse<Map<String, Object>>> generateTestAuditData()
```

### 3. 创建测试工具

创建了多个测试工具来验证功能：

1. **Shell脚本：** `scripts/test-audit-functionality.sh`
2. **PowerShell脚本：** `scripts/test-audit-functionality.ps1`  
3. **HTML测试页面：** `test-audit-api.html`

## 测试步骤

### 方法1：使用测试脚本

```bash
# Linux/Mac
./scripts/test-audit-functionality.sh

# Windows PowerShell
.\scripts\test-audit-functionality.ps1
```

### 方法2：使用HTML测试页面

1. 启动应用程序
2. 在浏览器中打开 `test-audit-api.html`
3. 点击"生成测试审计数据"按钮
4. 点击各种查询按钮验证数据

### 方法3：直接API调用

```bash
# 生成测试数据
curl -X POST "http://localhost:8080/api/security/audit/extended/test-data/generate"

# 查询审计事件
curl -X POST "http://localhost:8080/api/security/audit/extended/query" \
  -H "Content-Type: application/json" \
  -d '{"page": 0, "size": 20}'
```

## 验证前端功能

1. 生成测试数据后，打开前端审计日志管理页面
2. 应该能看到生成的测试审计事件
3. 测试搜索、分页、详情查看等功能

## 集成审计功能

### ✅ 已完成的集成

现在审计功能已经完全集成到实际的JWT令牌和API Key操作中：

#### 1. JWT令牌操作审计
- **登录时**: 自动记录JWT令牌颁发事件
- **刷新时**: 记录令牌刷新事件（包含新旧令牌ID）
- **撤销时**: 记录令牌撤销事件（包含撤销原因）
- **验证失败**: 记录令牌验证失败事件

#### 2. API Key操作审计
- **使用时**: 记录API Key使用事件（成功/失败）
- **创建时**: 记录API Key创建事件
- **删除时**: 记录API Key撤销事件
- **验证失败**: 记录无效/过期API Key使用尝试

#### 3. 安全事件审计
- **认证失败**: 记录用户名密码错误
- **授权失败**: 记录权限不足事件
- **可疑活动**: 记录异常访问模式

### 🗂️ 持久化方案

#### 1. 多层存储架构
- **内存缓存**: 用于快速查询（最近10000个事件）
- **结构化日志**: JSON格式，便于分析和搜索
- **文件存储**: 按类型分离的日志文件

#### 2. 日志文件结构
```
logs/audit/
├── security-audit.log          # 所有审计事件
├── jwt-audit.log              # JWT相关事件
├── api-key-audit.log          # API Key相关事件
└── security-events-audit.log  # 安全事件
```

#### 3. 日志轮转配置
- 文件大小限制：100MB
- 保留天数：90天（安全事件180天）
- 自动压缩：gzip格式
- 总大小限制：10GB

### 🔧 配置说明

```yaml
jairouter:
  security:
    audit:
      enabled: true
      extended:
        enabled: true
        persistence:
          enabled: true
          storage-type: "file"
        structured-logging:
          enabled: true
          format: "json"
        memory-cache:
          enabled: true
          max-events: 10000
```

### 🧪 测试验证

使用集成测试脚本验证功能：

```bash
# Linux/Mac
./scripts/test-integrated-audit.sh

# Windows PowerShell
.\scripts\test-integrated-audit.ps1
```

测试覆盖：
- JWT登录/刷新/撤销审计
- API Key使用审计
- 认证失败审计
- 审计事件查询
- 日志文件生成

## 配置检查清单

- [ ] 审计功能已启用
- [ ] 前端API路径配置正确
- [ ] 后端控制器路径正确
- [ ] 测试数据生成成功
- [ ] 前端页面显示数据
- [ ] 搜索和分页功能正常
- [ ] 详情查看功能正常

## 常见问题

### Q: 前端显示网络错误
A: 检查API路径配置，确保前端 `VITE_API_BASE_URL` 与后端控制器路径匹配

### Q: 生成测试数据后仍无数据
A: 检查浏览器控制台错误，确认API调用成功

### Q: 审计功能未启用
A: 检查 `application.yml` 中的审计配置

### Q: 数据丢失
A: 当前使用内存存储，重启应用会丢失数据，建议配置持久化存储

## 技术细节

### 审计事件类型

- `JWT_TOKEN_ISSUED` - JWT令牌颁发
- `JWT_TOKEN_REFRESHED` - JWT令牌刷新  
- `JWT_TOKEN_REVOKED` - JWT令牌撤销
- `JWT_TOKEN_VALIDATED` - JWT令牌验证
- `API_KEY_CREATED` - API Key创建
- `API_KEY_USED` - API Key使用
- `API_KEY_REVOKED` - API Key撤销
- `SECURITY_ALERT` - 安全告警
- `SUSPICIOUS_ACTIVITY` - 可疑活动

### API端点

- `POST /api/security/audit/extended/test-data/generate` - 生成测试数据
- `POST /api/security/audit/extended/query` - 复杂查询
- `GET /api/security/audit/extended/jwt-tokens` - JWT事件查询
- `GET /api/security/audit/extended/api-keys` - API Key事件查询
- `GET /api/security/audit/extended/security-events` - 安全事件查询
- `GET /api/security/audit/extended/reports/security` - 安全报告