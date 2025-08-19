# 动态配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 提供灵活的配置选项以满足各种部署场景。本指南涵盖从基本设置到高级功能的所有配置方面。

## 配置概览
**1.配置合并**: 启动时，读取config目录下的配置文档，与application.yml配置进行自动合并，动态更新配置。
**2.实例管理 API**: 通过 REST API 在运行时更新

## 配置合并
### 配置文件命名规则

```
config/
├── model-router-config@1.json    # 版本 1 配置文件
├── model-router-config@2.json    # 版本 2 配置文件
├── model-router-config@3.json    # 版本 3 配置文件
└── backup_1640995200000/         # 备份目录（时间戳）
    ├── model-router-config@1.json
    └── model-router-config@2.json
```

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
        "type": "token-bucket",
        "capacity": 100,
        "refillRate": 10,
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
                "content": "服务暂时不可用，请稍后重试。"
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

## 配置文件管理 API

### 配置合并功能

JAiRouter 提供强大的配置文件自动合并功能：

| 功能 | API 端点 | 方法 | 说明 |
|------|----------|------|------|
| 扫描版本文件 | `/api/config/merge/scan` | GET | 扫描所有版本配置文件 |
| 预览合并结果 | `/api/config/merge/preview` | GET | 预览合并后的配置 |
| 执行合并 | `/api/config/merge/execute` | POST | 执行配置文件合并 |
| 备份配置 | `/api/config/merge/backup` | POST | 备份当前配置文件 |
| 批量操作 | `/api/config/merge/batch` | POST | 备份+合并+清理 |
| 清理文件 | `/api/config/merge/cleanup` | DELETE | 清理原始配置文件 |
| 验证配置 | `/api/config/merge/validate` | GET | 验证配置文件格式 |
| 统计信息 | `/api/config/merge/statistics` | GET | 获取配置统计信息 |
| 服务状态 | `/api/config/merge/status` | GET | 获取合并服务状态 |

### 1. 扫描配置文件版本

```bash
# 扫描所有版本配置文件
curl -X GET "http://localhost:8080/api/config/merge/scan"

# 响应示例
{
  "success": true,
  "data": {
    "configFiles": [
      {
        "filename": "model-router-config@1.json",
        "version": 1,
        "size": 2048,
        "lastModified": "2024-01-15T10:00:00Z",
        "servicesCount": 2,
        "instancesCount": 5
      },
      {
        "filename": "model-router-config@2.json",
        "version": 2,
        "size": 3072,
        "lastModified": "2024-01-15T11:00:00Z",
        "servicesCount": 3,
        "instancesCount": 8
      }
    ],
    "totalFiles": 2,
    "totalInstances": 13
  }
}
```

### 2. 预览合并结果

```bash
# 预览配置文件合并结果
curl -X GET "http://localhost:8080/api/config/merge/preview"

# 响应示例
{
  "success": true,
  "data": {
    "mergedConfig": {
      "services": {
        "chat": {
          "instances": [
            // 合并后的实例列表
          ]
        }
      }
    },
    "mergeStatistics": {
      "totalServices": 3,
      "totalInstances": 13,
      "duplicatesRemoved": 2,
      "conflictsResolved": 1
    }
  }
}
```

### 3. 执行配置合并

```bash
# 执行配置文件合并
curl -X POST "http://localhost:8080/api/config/merge/execute"

# 响应示例
{
  "success": true,
  "message": "配置合并完成",
  "data": {
    "mergedFile": "model-router-config@1.json",
    "originalFiles": [
      "model-router-config@1.json",
      "model-router-config@2.json"
    ],
    "statistics": {
      "servicesProcessed": 3,
      "instancesProcessed": 13,
      "duplicatesRemoved": 2
    }
  }
}
```

### 4. 批量操作

```bash
# 执行批量操作：备份 + 合并 + 清理
curl -X POST "http://localhost:8080/api/config/merge/batch?deleteOriginals=true"

# 响应示例
{
  "success": true,
  "message": "批量操作完成",
  "data": {
    "backupDirectory": "backup_1640995200000",
    "mergedFile": "model-router-config@1.json",
    "filesDeleted": [
      "model-router-config@2.json",
      "model-router-config@3.json"
    ]
  }
}
```

## 配置验证和监控

### 1. 配置验证

```bash
# 验证配置文件格式和内容
curl -X GET "http://localhost:8080/api/config/merge/validate"

# 响应示例
{
  "success": true,
  "data": {
    "validationResults": [
      {
        "filename": "model-router-config@1.json",
        "valid": true,
        "errors": [],
        "warnings": [
          "实例 'old-model@http://old-server:8080' 可能不可用"
        ]
      }
    ],
    "overallValid": true,
    "totalErrors": 0,
    "totalWarnings": 1
  }
}
```

### 2. 配置统计信息

```bash
# 获取配置统计信息
curl -X GET "http://localhost:8080/api/config/merge/statistics"

# 响应示例
{
  "success": true,
  "data": {
    "configFiles": 3,
    "totalServices": 5,
    "totalInstances": 15,
    "serviceBreakdown": {
      "chat": 6,
      "embedding": 4,
      "tts": 3,
      "stt": 2
    },
    "loadBalanceStrategies": {
      "round-robin": 2,
      "least-connections": 2,
      "random": 1
    },
    "rateLimitAlgorithms": {
      "token-bucket": 4,
      "sliding-window": 1
    }
  }
}
```

### 3. 服务状态监控

```bash
# 获取合并服务状态
curl -X GET "http://localhost:8080/api/config/merge/status"

# 响应示例
{
  "success": true,
  "data": {
    "serviceStatus": "RUNNING",
    "lastMergeTime": "2024-01-15T12:00:00Z",
    "lastBackupTime": "2024-01-15T11:30:00Z",
    "configDirectory": "/app/config",
    "backupDirectory": "/app/config/backup_1640995200000",
    "activeConfigFile": "model-router-config@1.json",
    "pendingChanges": false
  }
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

### 场景 4：配置文件维护

```bash
# 1. 备份当前配置
curl -X POST "http://localhost:8080/api/config/merge/backup"

# 2. 扫描配置文件版本
curl -X GET "http://localhost:8080/api/config/merge/scan"

# 3. 预览合并结果
curl -X GET "http://localhost:8080/api/config/merge/preview"

# 4. 执行配置合并
curl -X POST "http://localhost:8080/api/config/merge/execute"

# 5. 清理旧版本文件
curl -X DELETE "http://localhost:8080/api/config/merge/cleanup?deleteOriginals=true"
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
# 定期检查配置状态
curl -X GET "http://localhost:8080/api/config/merge/status"

# 验证配置完整性
curl -X GET "http://localhost:8080/api/config/merge/validate"

# 监控实例健康状态
curl -X GET "http://localhost:8080/actuator/health"
```

### 4. 错误处理

```bash
# 配置回滚脚本示例
#!/bin/bash

# 备份当前配置
BACKUP_RESULT=$(curl -s -X POST "http://localhost:8080/api/config/merge/backup")

if [[ $? -eq 0 ]]; then
    echo "配置备份成功"
    
    # 执行配置变更
    # ... 配置变更操作 ...
    
    # 验证变更结果
    HEALTH_CHECK=$(curl -s "http://localhost:8080/actuator/health")
    
    if [[ $(echo $HEALTH_CHECK | jq -r '.status') != "UP" ]]; then
        echo "健康检查失败，开始回滚"
        # 执行回滚操作
        # ... 回滚逻辑 ...
    fi
else
    echo "配置备份失败，取消变更"
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
# 查看详细错误信息
curl -v "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{"name": "test", ...}'

# 检查服务日志
docker logs jairouter

# 验证配置文件
cat config/model-router-config@1.json | jq .
```

## 下一步

完成动态配置学习后，您可以继续了解：

- **[负载均衡配置](load-balancing.md)** - 配置负载均衡策略
- **[限流配置](rate-limiting.md)** - 设置流量控制
- **[熔断器配置](circuit-breaker.md)** - 配置故障保护
- **[监控指南](../monitoring/index.md)** - 设置监控和告警