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
├── model-router-config@1.json    # Version 1 configuration file
├── model-router-config@2.json    # Version 2 configuration file
├── model-router-config@3.json    # Version 3 configuration file
└── backup_1640995200000/         # Backup directory (timestamp)
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
| 获取实例列表 | GET | `/api/config/instance/type/{serviceType}` | 获取指定服务的所有实例 |
| 获取实例详情 | GET | `/api/config/instance/info/{serviceType}` | 获取单个实例的详细信息 |
| 添加实例 | POST | `/api/config/instance/add/{serviceType}` | 添加新的服务实例 |
| 更新实例 | PUT | `/api/config/instance/update/{serviceType}` | 更新现有实例配置 |
| 删除实例 | DELETE | `/api/config/instance/del/{serviceType}` | 删除指定实例 |

### 1. 获取实例列表

```bash
# Get all instances of Chat service
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

# Response Example
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
# Get detailed information of specific instance
curl -X GET "http://localhost:8080/api/config/instance/info/chat?modelName=llama3.2:3b&baseUrl=http://localhost:11434"

# Response Example
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
# Add new Chat service instance
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

# Response Example
{
  "success": true,
  "message": "Instance added successfully",
  "data": {
    "instanceId": "qwen2:7b@http://gpu-server:8080"
  }
}
```

### 4. 更新实例

```bash
# Update existing instance configuration
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

# Response Example
{
  "success": true,
  "message": "Instance updated successfully"
}
```

### 5. 删除实例

```bash
# Delete specified instance
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=qwen2:7b&baseUrl=http://gpu-server:8080"

# Response Example
{
  "success": true,
  "message": "Instance deleted successfully"
}
```

## 配置文件管理 API

### 配置合并功能

JAiRouter 提供强大的自动配置文件合并功能：

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
# Scan all version configuration files
curl -X GET "http://localhost:8080/api/config/merge/scan"

# Response Example
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
# Preview configuration file merge result
curl -X GET "http://localhost:8080/api/config/merge/preview"

# Response Example
{
  "success": true,
  "data": {
    "mergedConfig": {
      "services": {
        "chat": {
          "instances": [
            // Merged instance list
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
# Execute configuration file merge
curl -X POST "http://localhost:8080/api/config/merge/execute"

# Response Example
{
  "success": true,
  "message": "Configuration merge completed",
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
# Execute batch operation: backup + merge + cleanup
curl -X POST "http://localhost:8080/api/config/merge/batch?deleteOriginals=true"

# Response Example
{
  "success": true,
  "message": "Batch operation completed",
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

## 配置验证与监控

### 1. 配置验证

```bash
# Validate configuration file format and content
curl -X GET "http://localhost:8080/api/config/merge/validate"

# Response Example
{
  "success": true,
  "data": {
    "validationResults": [
      {
        "filename": "model-router-config@1.json",
        "valid": true,
        "errors": [],
        "warnings": [
          "Instance 'old-model@http://old-server:8080' may be unavailable"
        ]
      }
    ],
    "overallValid": true,
    "totalErrors": 0,
    "totalWarnings": 1
  }
}
```

### 2. 配置统计

```bash
# Get configuration statistics
curl -X GET "http://localhost:8080/api/config/merge/statistics"

# Response Example
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
# Get merge service status
curl -X GET "http://localhost:8080/api/config/merge/status"

# Response Example
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
# 1. Add new high-performance GPU instance
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.1:70b",
    "baseUrl": "http://gpu-cluster:8080",
    "path": "/v1/chat/completions",
    "weight": 5,
    "timeout": 60000
  }'

# 2. Verify instance addition success
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

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
curl -X GET "http://localhost:8080/api/config/instance/info/chat?modelName=qwen2:7b&baseUrl=http://gpu-server:8080"

# 2. Update instance weight (from 2 to 4)
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

# 3. Verify weight update
curl -X GET "http://localhost:8080/api/config/instance/type/chat"
```

### 场景 3：故障实例处理

```bash
# 1. Check instance health status
curl -X GET "http://localhost:8080/api/config/instance/type/chat"

# 2. Temporarily remove faulty instance
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=faulty-model&baseUrl=http://faulty-server:8080"

# 3. Add alternative instance
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
# 1. Backup current configuration
curl -X POST "http://localhost:8080/api/config/merge/backup"

# 2. Scan configuration file versions
curl -X GET "http://localhost:8080/api/config/merge/scan"

# 3. Preview merge result
curl -X GET "http://localhost:8080/api/config/merge/preview"

# 4. Execute configuration merge
curl -X POST "http://localhost:8080/api/config/merge/execute"

# 5. Clean old version files
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
# Progressive Instance Replacement
# 1. Add new instance (smaller weight)
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -d '{"name": "new-model", "weight": 1, ...}'

# 2. Observe new instance performance
# Monitor metrics, error rate, response time

# 3. Gradually increase new instance weight
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -d '{"instanceId": "new-model@...", "instance": {"weight": 3, ...}}'

# 4. Gradually decrease old instance weight
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -d '{"instanceId": "old-model@...", "instance": {"weight": 1, ...}}'

# 5. Remove old instance
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?..."
```

### 3. 配置监控

```bash
# Regularly check configuration status
curl -X GET "http://localhost:8080/api/config/merge/status"

# Validate configuration integrity
curl -X GET "http://localhost:8080/api/config/merge/validate"

# Monitor instance health status
curl -X GET "http://localhost:8080/actuator/health"
```

### 4. 错误处理

```bash
# Configuration rollback script example
#!/bin/bash

# Backup current configuration
BACKUP_RESULT=$(curl -s -X POST "http://localhost:8080/api/config/merge/backup")

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
curl -v "http://localhost:8080/api/config/instance/add/chat" \
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
