# 测试并发更新修复效果

## 测试场景

### 1. 模拟并发更新请求

```bash
# 使用curl同时发送多个相同的更新请求
for i in {1..5}; do
  curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer YOUR_JWT_TOKEN" \
    -d '{
      "instanceId": "qwen3:1.7B@http://172.16.30.6:9090",
      "newInstanceId": "qwen3:1.7B@http://172.16.30.6:9090",
      "instance": {
        "name": "qwen3:1.7B",
        "baseUrl": "http://172.16.30.6:9090",
        "status": "inactive",
        "weight": 1
      }
    }' &
done
wait
```

### 2. 监控日志输出

修复后，应该看到类似的日志：

```
2025-09-23 19:45:10.264 [reactor-http-epoll-4] INFO  [] o.u.m.controller.ServiceInstanceController - 接收到更新实例请求
2025-09-23 19:45:10.265 [reactor-http-epoll-4] INFO  [] o.unreal.modelrouter.config.ConfigurationService - 更新服务 chat 的实例
2025-09-23 19:45:10.266 [reactor-http-epoll-5] INFO  [] o.unreal.modelrouter.config.ConfigurationService - 检测到重复的更新请求，忽略
2025-09-23 19:45:10.267 [reactor-http-epoll-6] INFO  [] o.unreal.modelrouter.config.ConfigurationService - 检测到重复的更新请求，忽略
```

### 3. 验证版本创建

修复前：每个请求都会创建新版本
```
版本 910224618 - 更新服务实例
版本 910271167 - 更新服务实例  
版本 910304673 - 更新服务实例
版本 910352776 - 更新服务实例
```

修复后：只创建一个版本
```
版本 910224618 - 更新服务实例
配置未发生变化，不创建新版本
配置未发生变化，不创建新版本
配置未发生变化，不创建新版本
```

## 关键指标

1. **版本创建频率**：应该显著降低
2. **重复请求检测**：日志中应该出现"检测到重复的更新请求"
3. **配置变化检测**：状态变化不应该触发新版本
4. **并发安全性**：不应该出现并发异常

## 测试状态变化场景

```bash
# 测试状态从active变为inactive不创建新版本
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "instanceId": "qwen3:1.7B@http://172.16.30.6:9090",
    "instance": {
      "name": "qwen3:1.7B",
      "baseUrl": "http://172.16.30.6:9090",
      "status": "active"
    }
  }'

sleep 2

curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "instanceId": "qwen3:1.7B@http://172.16.30.6:9090",
    "instance": {
      "name": "qwen3:1.7B",
      "baseUrl": "http://172.16.30.6:9090",
      "status": "inactive"
    }
  }'
```

预期结果：第二个请求应该显示"配置未发生变化，不创建新版本"