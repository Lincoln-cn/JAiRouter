# Dynamic Configuration

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->

JAiRouter provides flexible configuration options to meet various deployment scenarios. This guide covers all aspects of configuration from basic setup to advanced features.

## Modular Configuration Overview

Starting from v1.0.0, JAiRouter adopts a modular configuration structure:

- Main configuration file: `application.yml`
- Base configuration modules: Files in the `config/base/` directory
- Feature configuration modules: Files in the `config/security/`, `config/tracing/` directories, etc.
- Environment configuration files: `application-dev.yml`, `application-prod.yml`, etc.

Although the configuration has been modularized, the dynamic configuration API can still be used to update instance configurations at runtime without affecting the modular structure.

## Configuration Overview
**1. Configuration Merging**: At startup, configuration documents in the config directory are read and automatically merged with application.yml configuration for dynamic updates.
**2. Instance Management API**: Update at runtime via REST API

## Configuration Merging
### Configuration File Naming Rules

```
config/
├── model-router-config@1.json    # Version 1 configuration file
├── model-router-config@2.json    # Version 2 configuration file
├── model-router-config@3.json    # Version 3 configuration file
└── backup_1640995200000/         # Backup directory (timestamp)
    ├── model-router-config@1.json
    └── model-router-config@2.json
```

### Configuration File Format

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

## Instance Management API

### API Endpoint Overview

| Operation | Method | Path | Notes |
|-----------|--------|------|-------|
| List instances | GET | `/api/config/instance/{serviceType}` | Every instance of the service type, as a plain array (no paging) |
| Add instance | POST | `/api/config/instance/{serviceType}` | Body = `CreateServiceInstanceRequest` |
| Update instance | PUT | `/api/config/instance/{serviceType}/{instanceId}` | `{instanceId}` = the **database id** returned by the list/add call |
| Delete instance | DELETE | `/api/config/instance/{serviceType}/{instanceId}` | Same database id |

**Authentication.** These are management endpoints: send the JWT in the **`Jairouter_Token`** header
(configurable via `jairouter.security.jwt.jwt-header`) — **not** `Authorization: Bearer`. Without it every
call answers `401 {"error":{"code":"AUTH_MISSING"}}`. Get a token once per shell:

```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMeOnFirstStartup123456"}' \
  http://localhost:8080/api/auth/jwt/login | jq -r .data.token)
```

> ⚠️ Earlier revisions of this page documented `/api/config/instance/{type,add,update,del,info}/…`, plus
> `timeout` / `maxRetries` in the body and an `instanceId` shaped like `"model@baseUrl"`. None of those
> exists: the sub-paths were never routes (Spring matched them against `/{serviceType}/{instanceId}` and
> answered `405`), `CreateServiceInstanceRequest` has no timeout/retries field, and an instance is
> identified by its numeric database `id`. Everything below was executed against a running instance.

### 1. List Instances

```bash
curl -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"
```

Each array element is a full `ServiceInstanceDTO` — there is **no separate "details" endpoint**, the list
already carries the whole configuration:

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
      "status": "HEALTHY",
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

### 2. Add an Instance

```bash
curl -X POST -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "qwen2:7b",
    "baseUrl": "http://gpu-server:8080",
    "path": "/v1/chat/completions",
    "weight": 2,
    "status": "HEALTHY",
    "adapter": "normal",
    "headers": {"Authorization": "Bearer your-token"}
  }' \
  http://localhost:8080/api/config/instance/chat
```

The response `data` is the created instance — keep its `id`, the next two calls need it:

```json
{ "success": true, "message": "...", "data": { "id": 11, "name": "qwen2:7b", "weight": 2 } }
```

### 3. Update an Instance

`{instanceId}` is that database `id` (look it up from the list response):

```bash
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')

curl -X PUT -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":3,"adapter":"normal"}' \
  "http://localhost:8080/api/config/instance/chat/$ID"
```

### 4. Delete an Instance

```bash
curl -X DELETE -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat/$ID"
```

Both the update and the delete above returned `200` when this page was reviewed against a running
instance (the round-trip was add → list → take `id` → update → delete).

## Configuration File Management API

### Configuration Merging Functionality

JAiRouter provides powerful automatic configuration file merging functionality:

| Function | API Endpoint | Method | Description |
|----------|--------------|--------|-------------|
| Scan Version Files | `/api/config/merge/scan` | GET | Scan all version configuration files |
| Preview Merge Result | `/api/config/merge/preview` | GET | Preview merged configuration |
| Execute Merge | `/api/config/merge/execute` | POST | Execute configuration file merge |
| Backup Configuration | `/api/config/merge/backup` | POST | Backup current configuration files |
| Batch Operation | `/api/config/merge/batch` | POST | Backup+Merge+Cleanup |
| Clean Files | `/api/config/merge/cleanup` | DELETE | Clean original configuration files |
| Validate Configuration | `/api/config/merge/validate` | GET | Validate configuration file format |
| Statistics | `/api/config/merge/statistics` | GET | Get configuration statistics |
| Service Status | `/api/config/merge/status` | GET | Get merge service status |

### 1. Scan Configuration File Versions

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

### 2. Preview Merge Result

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

### 3. Execute Configuration Merge

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

### 4. Batch Operation

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

## Configuration Validation and Monitoring

### 1. Configuration Validation

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

### 2. Configuration Statistics

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

### 3. Service Status Monitoring

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

## Practical Usage Scenarios

### Scenario 1: Adding New AI Service Instance

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

### Scenario 2: Dynamically Adjust Load Balancing Weight

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

### Scenario 3: Fault Instance Handling

```bash
# 1. Check instance health status
curl -X GET -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat"

# 2. Temporarily remove faulty instance (the id comes from the list call in step 1)
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="faulty-model") | .id')
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

### Scenario 4: Configuration File Maintenance

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

## Best Practices

### 1. Configuration Change Process

1. **Backup Before Changes**: Always backup configuration before making changes
2. **Small Steps**: Change only one configuration item at a time
3. **Validation Testing**: Immediately verify functionality after changes
4. **Monitoring Observation**: Observe system performance after changes
5. **Documentation Recording**: Record change reasons and results

### 2. Instance Management Strategy

```bash
# Progressive Instance Replacement
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="new-model") | .id')
# 1. Add new instance (smaller weight)
curl -X POST "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "new-model", "weight": 1}'

# 2. Observe new instance performance
# Monitor metrics, error rate, response time

# 3. Gradually increase new instance weight
curl -X PUT "http://localhost:8080/api/config/instance/chat/$ID" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"new-model","weight":3}'

# 4. Gradually decrease old instance weight
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="new-model") | .id')
curl -X PUT "http://localhost:8080/api/config/instance/chat/$ID" \
  -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"old-model","weight":1}'

# 5. Remove old instance
curl -X DELETE "http://localhost:8080/api/config/instance/chat/$ID"
```

### 3. Configuration Monitoring

```bash
# Regularly check configuration status
curl -X GET "http://localhost:8080/api/config/merge/status"

# Validate configuration integrity
curl -X GET "http://localhost:8080/api/config/merge/validate"

# Monitor instance health status
curl -X GET "http://localhost:8080/actuator/health"
```

### 4. Error Handling

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

## Troubleshooting

### Common Issues

1. **Configuration Not Taking Effect**
    - Check if API response is successful
    - Verify if configuration file is correctly saved
    - Confirm if service instance is healthy

2. **Instance Addition Failed**
    - Check network connectivity
    - Verify if URL format is correct
    - Confirm if backend service is available

3. **Configuration Merge Failed**
    - Check if configuration file format is correct
    - Verify if disk space is sufficient
    - Confirm if file permissions are correct

### Debugging Commands

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

## Next Steps

After completing dynamic configuration learning, you can continue to learn about:

- **[Load Balancing Configuration](load-balancing.md)** - Configure load balancing strategies
- **[Rate Limiting Configuration](rate-limiting.md)** - Set traffic control
- **[Circuit Breaker Configuration](circuit-breaker.md)** - Configure fault protection
- **[Monitoring Guide](../monitoring/index.md)** - Set up monitoring and alerts