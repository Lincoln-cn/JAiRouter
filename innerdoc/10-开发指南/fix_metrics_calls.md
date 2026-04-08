# MetricsCollector 方法调用修复策略

## 需要修复的方法调用
根据编译错误，以下方法在 MetricsCollector 接口中不存在：

1. `recordRequestStart(String, String)` - 不存在
2. `recordResponseTime(String, String, long)` - 不存在  
3. `recordError(String, String, String, long)` - 不存在
4. `recordRetry(String, String, int)` - 不存在

## 可用的方法
根据 MetricsCollector 接口，可用的方法有：
- `recordRequest(String service, String method, long duration, String status)`
- `recordBackendCall(String adapter, String instance, long duration, boolean success)`
- `recordRateLimit(String service, String algorithm, boolean allowed)`
- `recordCircuitBreaker(String service, String state, String event)`
- `recordLoadBalancer(String service, String strategy, String selectedInstance)`
- `recordHealthCheck(String adapter, String instance, boolean healthy, long responseTime)`
- `recordRequestSize(String service, long requestSize, long responseSize)`

## 修复策略
1. 将 `recordRequestStart` 调用注释掉或替换为合适的方法
2. 将 `recordResponseTime` 替换为 `recordBackendCall`
3. 将 `recordError` 替换为 `recordBackendCall` (success=false)
4. 将 `recordRetry` 替换为 `recordBackendCall` (success=false)