# Rerank 级联异常风暴修复验证

## 问题描述
用户报告"只看到一次请求，但从日志来看，后端却像被连续重试/级联失败一样不停地抛出异常"。

## 根因分析
通过代码分析发现三个关键问题：

1. **BaseAdapter 自动重试机制**：rerank 服务配置了2次重试，503错误会触发重试
2. **WebFlux Request Body 不可重读**：重试时 body 已被消费，导致 `ServerWebInputException: No request body`
3. **异常处理器可能产生二次异常**：在设置响应头时可能出错

## 修复内容

### 1. 修改 BaseAdapter.shouldRetry() 方法
- 添加对 `ServerWebInputException` 的特殊处理，此类异常不重试
- 添加对 400 Bad Request 的特殊处理，不重试
- 添加对 401 Unauthorized 的特殊处理，不重试
- 保留对 5xx、429、408 错误的重试逻辑

### 2. 降低重试次数
- rerank 服务从 2 次重试降低到 1 次
- embedding 服务从 3 次重试降低到 2 次
- 默认重试次数从 2 次降低到 1 次

### 3. 增强 UniversalController.rerank() 方法
- 添加详细的请求日志，记录 model、query 长度、documents 数量
- 改进空请求体的错误日志

### 4. 增强 ServerExceptionHandler
- 添加对 `ServerWebInputException` 的特殊处理，返回 400 状态码
- 添加对 `ResponseStatusException` 的特殊处理，返回对应状态码
- 改进错误追踪的状态码记录

## 验证步骤

### 1. 运行测试脚本
```powershell
.\test-rerank-fix.ps1
```

### 2. 检查日志
确认以下行为：
- 400 错误不会触发重试
- `ServerWebInputException` 被正确处理为 400 错误
- 503 错误最多只重试 1 次
- 没有出现级联异常日志

### 3. 监控指标
- 重试次数应该显著减少
- 400 错误应该立即返回，不产生重试
- 异常处理应该更加稳定

## 预期效果

1. **消除级联异常风暴**：400 错误不再触发重试
2. **减少无效重试**：降低重试次数，避免资源浪费
3. **改进错误处理**：不同类型异常返回正确的状态码
4. **提高系统稳定性**：避免异常处理器产生二次异常

## 后续建议

1. **添加监控指标**：
   - 各服务的重试次数和成功率
   - 异常类型分布统计
   - 请求体大小分布

2. **考虑实现请求体缓存**：
   - 对于需要重试的场景，实现 WebFlux 请求体缓存
   - 使用 `ServerRequest.bodyToMono().cache()` 模式

3. **优化熔断器配置**：
   - 调整 `bge-reranker-v2-m3` 的熔断阈值
   - 区分不同类型错误的熔断策略

## 测试用例

### 正常请求
```json
{
  "model": "bge-reranker-v2-m3",
  "query": "hello world",
  "documents": [
    {"text": "hello there"},
    {"text": "goodbye world"}
  ]
}
```

### 异常请求
1. 空请求体 - 应返回 400
2. 无效 JSON - 应返回 400  
3. 缺少必要字段 - 应返回相应错误

所有异常请求都不应该触发重试机制。