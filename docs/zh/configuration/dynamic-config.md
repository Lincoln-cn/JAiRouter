# 动态配置

<!-- 版本信息 -->
> **文档版本**: 1.0.2  
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供灵活的配置选项以满足各种部署场景。本指南涵盖从基本设置到高级功能的所有配置方面。

## 模块化配置说明

从 v1.0.0 版本开始，JAiRouter 采用模块化配置结构：

- 主配置文件：`application.yml`
- 基础配置模块：`config/base/` 目录下的文件
- 功能配置模块：`config/security/`、`config/tracing/` 等目录下的文件
- 环境配置文件：`application-dev.yml`、`application-prod.yml` 等

虽然配置已经模块化，动态配置 API 仍然可以在运行时更新实例配置，且不影响模块化结构。

## 配置概览
**1. 配置合并**：启动时读取 config 目录中的配置文档，并自动与 application.yml 配置合并，以实现动态更新。
**2. 实例管理 API**：通过 REST API 在运行时更新配置。

## 配置合并
### 配置文件命名规则

```
config/
├── model-router-config.json          # 当前生效的配置（配置存储键 `model-router-config`）
├── model-router-config@1.json        # 1 号版本快照
├── model-router-config@2.json        # 2 号版本快照
└── model-router-config@3.json        # 3 号版本快照
```

> 版本快照与当前配置文件同目录，命名为 `model-router-config@<version>.json`
> （`persistence/store/FileStoreManager.java`）。本页此前的版本还画了一个 `backup_<时间戳>/`
> 备份目录（内含版本文件副本）—— **代码里不会产生这样的目录**：备份是另一项能力，
> 它以加密形式存在配置存储的 `security.backup.<id>` 键下
> （`auth/security/config/SecurityConfigurationBackupService.java:38`），
> id 形如 `backup-yyyyMMdd-HHmmss-<n>`。

### 配置文件格式

```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "llama3.2:3b",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/chat/completions",
          "weight": 1,
          "timeout": 30000,
          "maxRetries": 3,
          "headers": {
            "Authorization": "Bearer token"
          }
        }
      ],
      "loadBalance": {
        "type": "round-robin",
        "hashAlgorithm": "md5"
      },
      "rateLimit": {
        "algorithm": "token-bucket",
        "capacity": 100,
        "rate": 10,
        "clientIpEnable": true
      },
      "circuitBreaker": {
        "failureThreshold": 5,
        "recoveryTimeout": 60000,
        "successThreshold": 3,
        "timeout": 30000
      },
      "fallback": {
        "type": "default",
        "response": {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "Service is temporarily unavailable. Please try again later."
              }
            }
          ]
        }
      }
    }
  },
  "store": {
    "type": "file",
    "path": "config/"
  }
}
```

## 实例管理 API

### API 端点概览

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取实例列表 | GET | `/api/config/instance/{serviceType}` | 该服务类型的全部实例，返回普通数组（不分页） |
| 添加实例 | POST | `/api/config/instance/{serviceType}` | 请求体 = `CreateServiceInstanceRequest` |
| 更新实例 | PUT | `/api/config/instance/{serviceType}/{instanceId}` | `{instanceId}` 是列表/新增返回的**数据库 id** |
| 删除实例 | DELETE | `/api/config/instance/{serviceType}/{instanceId}` | 同一个数据库 id |

**认证。** 这些是管理面端点：JWT 要放在 **`Jairouter_Token`** 头里（可用
`jairouter.security.jwt.jwt-header` 配置），**不是** `Authorization: Bearer`。不带它每个请求都会得到
`401 {"error":{"code":"AUTH_MISSING"}}`。每个 shell 先取一次 token：

```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMeOnFirstStartup123456"}' \
  http://localhost:8080/api/auth/jwt/login | jq -r .data.token)
```

> ⚠️ 本页此前的版本教的是 `/api/config/instance/{type,add,update,del,info}/…`，请求体里还有
> `timeout` / `maxRetries`、`instanceId` 写成 `"model@baseUrl"`。**这些都不存在**：
> 那些子路径从来不是路由（Spring 会把它们匹配到 `/{serviceType}/{instanceId}` 上，返回 `405`），
> `CreateServiceInstanceRequest` 没有 timeout/retries 字段，实例的身份是**数据库数值 id**。
> 下面每条命令都在真实运行的实例上执行过。

### 1. 获取实例列表

```bash
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"
```

数组里每个元素都是一个完整的 `ServiceInstanceDTO` —— **没有单独的「实例详情」端点**，列表里已经带着全量配置：

```json
{
  "success": true,
  "message": "...",
  "data": [
    {
      "id": 10,
      "serviceConfigId": 1,
      "name": "llama3.2:3b",
      "baseUrl": "http://localhost:11434",
      "path": "/v1/chat/completions",
      "weight": 1,
      "status": "active",
      "healthStatus": "HEALTHY",
      "adapter": "ollama",
      "headers": {},
      "tags": {},
      "createdAt": "2026-10-14T01:20:00",
      "updatedAt": "2026-10-14T01:20:00"
    }
  ],
  "timestamp": "2026-10-14 01:20:00"
}
```

### 2. 添加实例

```bash
curl -X POST -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "qwen2:7b",
    "baseUrl": "http://gpu-server:8080",
    "path": "/v1/chat/completions",
    "weight": 2,
    "adapter": "normal",
    "headers": {"Authorization": "Bearer your-token"}
  }' \
  http://localhost:8080/api/config/instance/chat
```

> ⚠️ **请求体里的 `status` 只接受 `active` / `inactive`（大小写不敏感）。**
> 其他取值（例如 `HEALTHY`）会被**直接拒收 —— 400 `INVALID_REQUEST`**，不会再被静默存成别的值：
> `status` 是生命周期标志，路由只认 `active`，一旦被写坏，该实例会被可用性过滤整体排除，
> 调用随后以 `404 No instances found for model …` 失败，而这条文案会把排查方向引向「模型不存在」。
> **不写该字段即沿用默认 `active`**；健康状态是另一个字段 `healthStatus`。

响应里的 `data` 就是新建的实例 —— **记住它的 `id`**，下面两步都要用：

```json
{ "success": true, "message": "...", "data": { "id": 11, "name": "qwen2:7b", "weight": 2 } }
```

### 3. 更新实例

`{instanceId}` 就是上面那个数据库 `id`（也可以从列表响应里查）：

```bash
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')

curl -X PUT -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":3,"adapter":"normal"}' \
  "http://localhost:8080/api/config/instance/chat/$ID"
```

### 4. 删除实例

```bash
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="faulty-model") | .id')
curl -X DELETE -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat/$ID"
```

上面两步（更新、删除）在本页复查时都对着真实运行的实例执行过，均返回 `200`
（完整回路 = 新增 → 列表取 `id` → 更新 → 删除）。

## 配置版本管理 API

运行期配置保存在配置**存储**的 `model-router-config` 键下，每次记录变更都会留下编号快照
（`model-router-config@<version>.json`，由 `persistence/store/FileStoreManager.java` 落盘）。
下面的版本 API 操作的就是这些快照。

> ⚠️ 本页此前的版本教的是 `/api/config/merge/**` 工作流 —— `scan` / `preview` / `execute` / `backup` /
> `batch` / `cleanup` / `validate` / `statistics` / `status`。**这些端点全都不存在**：没有任何控制器声明它们，
> `config/core/ConfigMergeService.java` 也没有对应方法（它的公开成员只有 `getPersistedConfig`、
> `deepMergeConfigs`、`resetToYamlConfig`、`hasPersistedConfig`、`getDefaultConfig`）。
> 真正存在的是**版本管理**，即下面这套 API。两者不是同一种能力：版本管理负责
> **快照 / 应用 / 比较**，它不会去合并磁盘上的文件。

### 端点概览

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 列出全部版本 | GET | `/api/config/version` | 已保存的全部版本号 |
| 取某个版本 | GET | `/api/config/version/{version}` | 该版本的配置快照 |
| 当前版本 | GET | `/api/config/version/current` | 正在生效的版本 |
| 版本元信息 | GET | `/api/config/version/info` | 元数据与版本历史 |
| 应用某个版本 | POST | `/api/config/version/apply/{version}` | 把运行期配置切到该版本 |
| 比较两个版本 | GET | `/api/config/version/compare/{source}/{target}` | 两个已存版本的差异 |
| 与当前版本比较 | GET | `/api/config/version/compare/{version}` | 该版本与当前版本的差异 |
| 删除某个版本 | DELETE | `/api/config/version/{version}` | 删除某个已存快照 |

实现位置：`config/controller/ConfigurationVersionController.java`（类级
`@RequestMapping("/api/config/version")`），背后是 `config/version/VersionManagementService.java`。

认证：与本页其它管理面 API 一样，JWT 放在 **`Jairouter_Token`** 头里
（见[实例管理 API](#实例管理-api)）；不带它这些调用会返回 `401 {"error":{"code":"AUTH_MISSING"}}`。

### 示例

```bash
# 当前生效的版本
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/current"

# 全部已存版本
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version"

# 元信息 + 历史
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/info"

# 当前配置与 3 号版本的差异
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/compare/3"

# 把运行期配置回滚到 3 号版本
curl -X POST -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/apply/3"
```

**版本是怎么产生的。** 不需要另外跑「合并文件」这一步：通过本页各管理面 API 做的配置变更，
会由 `VersionManagementService`（`generateNewVersion` / `recordVersion`）记录为编号快照。
所以流程是 **变更 → 查看（`info` / `compare`）→ 必要时回滚（`apply`）**。

## 配置验证与监控

本节原先描述的 `/api/config/merge/validate` · `/statistics` · `/status` 三个端点**并不存在**
（见[配置版本管理 API](#配置版本管理-api) 的说明）。配置侧真正可以用来查看的是：

```bash
# 当前生效的配置来源，以及应用知道的校验规则 / 环境变量
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/sources"
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/validation-rules"
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/environment-variables"

# 版本状态（当前版本 / 历史 / 元信息）
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/current"
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/info"
```

需要运行期健康状态时，用（公开的）健康端点：`curl http://localhost:8080/actuator/health`。

## 实际使用场景

### 场景 1：添加新的 AI 服务实例

```bash
# 1. Add new high-performance GPU instance
curl -X POST "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.1:70b",
    "baseUrl": "http://gpu-cluster:8080",
    "path": "/v1/chat/completions",
    "weight": 5
  }'

# 2. Verify instance addition success
curl -X GET -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"

# 3. Test new instance
curl -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.1:70b",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 场景 2：动态调整负载均衡权重

```bash
# 1. Get current instance configuration
curl -X GET -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"

# 2. Update instance weight (from 2 to 4)
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')
curl -X PUT "http://localhost:8080/api/config/instance/chat/$ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":4}'

# 3. Verify weight update
curl -X GET -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"
```

### 场景 3：故障实例处理

```bash
# 1. Check instance health status
curl -X GET -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"

# 2. Temporarily remove faulty instance
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')
curl -X DELETE "http://localhost:8080/api/config/instance/chat/$ID"

# 3. Add alternative instance
curl -X POST "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "backup-model",
    "baseUrl": "http://backup-server:8080",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
```

### 场景 4：查看版本与回滚

本场景原先演示的文件维护工作流（`merge/backup` → `scan` → `preview` → `execute` → `cleanup`）
并不存在。实际能做的是查看已记录的版本并按需回滚：

```bash
# 当前生效的版本，以及存在哪些版本
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/current"
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version"

# 切换前先看清差异
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/compare/3"

# 回滚到某个更早的版本
curl -X POST -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/apply/3"
```

## 最佳实践

### 1. 配置变更流程

1. **变更前备份**：始终在变更前备份配置
2. **小步快跑**：一次只变更一个配置项
3. **验证测试**：变更后立即验证功能
4. **监控观察**：观察变更后的系统表现
5. **文档记录**：记录变更原因和结果

### 2. 实例管理策略

```bash
# Progressive Instance Replacement
# 1. Add new instance (smaller weight)
curl -X POST "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "new-model", "weight": 1}'

# 2. Observe new instance performance
# Monitor metrics, error rate, response time

# 3. Gradually increase new instance weight
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="new-model") | .id')
curl -X PUT "http://localhost:8080/api/config/instance/chat/$ID" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"new-model","weight":3}'

# 4. Gradually decrease old instance weight
curl -X PUT "http://localhost:8080/api/config/instance/chat/$ID" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"old-model","weight":1}'

# 5. Remove old instance
curl -X DELETE "http://localhost:8080/api/config/instance/chat/$ID"
```

### 3. 配置监控

```bash
# Regularly check configuration status
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/current"

# Validate configuration integrity
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/sources"

# Monitor instance health status
curl -X GET "http://localhost:8080/actuator/health"
```

### 4. 错误处理

```bash
# Configuration rollback script example
#!/bin/bash

# Backup current configuration
CURRENT_VERSION=$(curl -s -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/version/current")

if [[ $? -eq 0 ]]; then
    echo "Configuration backup successful"
    
    # Execute configuration change
    # ... configuration change operations ...
    
    # Verify change result
    HEALTH_CHECK=$(curl -s "http://localhost:8080/actuator/health")
    
    if [[ $(echo $HEALTH_CHECK | jq -r '.status') != "UP" ]]; then
        echo "Health check failed, starting rollback"
        # Execute rollback operation
        # ... rollback logic ...
    fi
else
    echo "Configuration backup failed, canceling change"
    exit 1
fi
```

## 故障排查

### 常见问题

1. **配置不生效**
    - 检查 API 响应是否成功
    - 验证配置文件是否正确保存
    - 确认服务实例是否健康

2. **实例添加失败**
    - 检查网络连通性
    - 验证 URL 格式是否正确
    - 确认后端服务是否可用

3. **配置合并失败**
    - 检查配置文件格式是否正确
    - 验证磁盘空间是否充足
    - 确认文件权限是否正确

### 调试命令

```bash
# View detailed error information
curl -v "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "test", ...}'

# Check service logs
docker logs jairouter

# Validate configuration file
cat config/model-router-config@1.json | jq .
```

## 下一步

完成动态配置学习后，您可以继续了解：

- **[负载均衡配置](load-balancing.md)** - 配置负载均衡策略
- **[限流配置](rate-limiting.md)** - 设置流量控制
- **[熔断器配置](circuit-breaker.md)** - 配置故障保护
- **[监控指南](../monitoring/index.md)** - 设置监控和告警
