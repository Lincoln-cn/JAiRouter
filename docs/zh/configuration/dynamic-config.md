# 动态配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-28
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供灵活的配置选项以满足各种部署场景。本指南涵盖从基本设置到高级功能的所有配置方面。

## 模块化配置说明

从 v1.0.0 版本开始，JAiRouter 采用模块化配置结构：

- 主配置文件: [application.yml](file://D:/IdeaProjects/model-router/src/main/resources/application.yml)
- 基础配置模块: [config/base/](file://D:/IdeaProjects/model-router/src/main/resources/config/base/) 目录下的文件
- 功能配置模块: [config/security/](file://D:/IdeaProjects/model-router/src/main/resources/config/security/)、[config/tracing/](file://D:/IdeaProjects/model-router/src/main/resources/config/tracing/) 等目录下的文件
- 环境配置文件: [application-dev.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-dev.yml)、[application-prod.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-prod.yml) 等

虽然配置已模块化，但动态配置 API 仍然可以用于运行时更新实例配置，不影响模块化结构。

## 配置概览

JAiRouter 提供完整的动态配置管理能力：

**1. 版本管理**: 支持配置版本历史查看、回滚和对比。
**2. 实例管理 API**: 通过 REST API 在运行时动态管理服务实例。

## 版本管理

JAiRouter 使用数据库存储配置版本，提供完整的版本历史管理：

- 自动创建配置版本快照
- 支持版本回滚和对比
- 保留完整的变更历史

### 版本管理 API

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取版本列表 | GET | `/api/config/versions` | 获取所有版本信息 |
| 获取版本详情 | GET | `/api/config/versions/{version}` | 获取指定版本配置 |
| 应用版本 | POST | `/api/config/versions/{version}/apply` | 回滚到指定版本 |
| 删除版本 | DELETE | `/api/config/versions/{version}` | 删除指定版本 |

## 实例管理 API

### API 端点概览

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取实例列表 | GET | `/api/config/instance/type/{serviceType}` | 获取指定服务的所有实例 |
| 获取实例详情 | GET | `/api/config/instance/info/{serviceType}` | 获取单个实例的详细信息 |
| 添加实例 | POST | `/api/config/instance/add/{serviceType}` | 添加新的服务实例 |
| 更新实例 | PUT | `/api/config/instance/update/{serviceType}` | 更新现有实例配置 |
| 删除实例 | DELETE | `/api/config/instance/del/{serviceType}` | 删除指定实例 |

### 1. 获取实例列表

```bash
# 获取 Chat 服务的所有实例
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

# 响应示例
{
  "success": true,
  "data": [
    {
      "instanceId": "llama3.2:3b@http://localhost:11434",
      "name": "llama3.2:3b",
      "baseUrl": "http://localhost:11434",
      "path": "/v1/chat/completions",
      "weight": 1,
      "timeout": 30000,
      "maxRetries": 3,
      "status": "HEALTHY"
    }
  ]
}
```

### 2. 获取实例详情

```bash
# 获取特定实例的详细信息
curl -X GET "http://localhost:8080/api/config/instance/info/chat?modelName=llama3.2:3b&baseUrl=http://localhost:11434"

# 响应示例
{
  "success": true,
  "data": {
    "instanceId": "llama3.2:3b@http://localhost:11434",
    "name": "llama3.2:3b",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 1,
    "timeout": 30000,
    "maxRetries": 3,
    "headers": {},
    "status": "HEALTHY",
    "lastHealthCheck": "2024-01-15T10:30:00Z",
    "requestCount": 1250,
    "errorCount": 5,
    "avgResponseTime": 850
  }
}
```

### 3. 添加实例

```bash
# 添加新的 Chat 服务实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "qwen2:7b",
    "baseUrl": "http://gpu-server:8080",
    "path": "/v1/chat/completions",
    "weight": 2,
    "timeout": 45000,
    "maxRetries": 3,
    "headers": {
      "Authorization": "Bearer your-token",
      "X-Custom-Header": "custom-value"
    }
  }'

# 响应示例
{
  "success": true,
  "message": "实例添加成功",
  "data": {
    "instanceId": "qwen2:7b@http://gpu-server:8080"
  }
}
```

### 4. 更新实例

```bash
# 更新现有实例配置
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "qwen2:7b@http://gpu-server:8080",
    "instance": {
      "name": "qwen2:7b",
      "baseUrl": "http://gpu-server:8080",
      "path": "/v1/chat/completions",
      "weight": 3,
      "timeout": 60000,
      "maxRetries": 5
    }
  }'

# 响应示例
{
  "success": true,
  "message": "实例更新成功"
}
```

### 5. 删除实例

```bash
# 删除指定实例
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=qwen2:7b&baseUrl=http://gpu-server:8080"

# 响应示例
{
  "success": true,
  "message": "实例删除成功"
}
```

## 实际使用场景

### 场景 1：添加新的 AI 服务实例

```bash
# 1. 添加新的高性能 GPU 实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.1:70b",
    "baseUrl": "http://gpu-cluster:8080",
    "path": "/v1/chat/completions",
    "weight": 5,
    "timeout": 60000
  }'

# 2. 验证实例添加成功
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

# 3. 测试新实例
curl -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.1:70b",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 场景 2：动态调整负载均衡权重

```bash
# 1. 获取当前实例配置
curl -X GET "http://localhost:8080/api/config/instance/info/chat?modelName=qwen2:7b&baseUrl=http://gpu-server:8080"

# 2. 更新实例权重（从 2 调整到 4）
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "qwen2:7b@http://gpu-server:8080",
    "instance": {
      "name": "qwen2:7b",
      "baseUrl": "http://gpu-server:8080",
      "path": "/v1/chat/completions",
      "weight": 4
    }
  }'

# 3. 验证权重更新
curl -X GET "http://localhost:8080/api/config/instance/type/chat"
```

### 场景 3：故障实例处理

```bash
# 1. 检查实例健康状态
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

# 2. 临时移除故障实例
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=faulty-model&baseUrl=http://faulty-server:8080"

# 3. 添加替代实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "backup-model",
    "baseUrl": "http://backup-server:8080",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
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
# 渐进式实例替换
# 1. 添加新实例（权重较小）
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -d '{"name": "new-model", "weight": 1, ...}'

# 2. 观察新实例表现
# 监控指标、错误率、响应时间

# 3. 逐步增加新实例权重
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -d '{"instanceId": "new-model@...", "instance": {"weight": 3, ...}}'

# 4. 逐步减少旧实例权重
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -d '{"instanceId": "old-model@...", "instance": {"weight": 1, ...}}'

# 5. 移除旧实例
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?..."
```

### 3. 配置监控

```bash
# 监控实例健康状态
curl -X GET "http://localhost:8080/actuator/health"

# 查看当前配置状态
curl -X GET "http://localhost:8080/api/config/versions"
```

### 4. 错误处理

```bash
# 配置回滚脚本示例
#!/bin/bash

# 获取当前版本
CURRENT_VERSION=$(curl -s "http://localhost:8080/api/config/versions" | jq -r '.data[0].version')

if [[ -z "$CURRENT_VERSION" ]]; then
    echo "无法获取当前版本"
    exit 1
fi

echo "当前版本: $CURRENT_VERSION"

# 执行配置变更
# ... 配置变更操作 ...

# 验证变更结果
HEALTH_CHECK=$(curl -s "http://localhost:8080/actuator/health")

if [[ $(echo $HEALTH_CHECK | jq -r '.status') != "UP" ]]; then
    echo "健康检查失败，开始回滚到版本 $CURRENT_VERSION"
    curl -X POST "http://localhost:8080/api/config/versions/$CURRENT_VERSION/apply"
fi
```

## 故障排查

### 常见问题

1. **配置不生效**
   - 检查 API 响应是否成功
   - 验证配置是否正确保存
   - 确认服务实例是否健康

2. **实例添加失败**
   - 检查网络连通性
   - 验证 URL 格式是否正确
   - 确认后端服务是否可用

3. **版本回滚失败**
   - 检查目标版本是否存在
   - 验证版本配置格式是否正确
   - 查看服务日志获取详细错误信息

### 调试命令

```bash
# 查看详细错误信息
curl -v "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{"name": "test", ...}'

# 检查服务日志
docker logs jairouter

# 查看当前配置
curl -s "http://localhost:8080/api/config/versions" | jq .
```

## 下一步

完成动态配置学习后，您可以继续了解：

- **[负载均衡配置](load-balancing.md)** - 配置负载均衡策略
- **[限流配置](rate-limiting.md)** - 设置流量控制
- **[熔断器配置](circuit-breaker.md)** - 配置故障保护
- **[监控指南](../monitoring/index.md)** - 设置监控和告警