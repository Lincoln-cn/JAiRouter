// 临时修复：将所有不存在的 MetricsCollector 方法调用注释掉

// 替换模式：
// metricsCollector.recordRequestStart(adapterType, instanceName);
// 改为：
// // TODO: 实现请求开始指标 - metricsCollector.recordRequestStart(adapterType, instanceName);

// metricsCollector.recordResponseTime(adapterType, instanceName, duration);
// 改为：
// metricsCollector.recordBackendCall(adapterType, instanceName, duration, true);

// metricsCollector.recordError(adapterType, instanceName, errorMessage, duration);
// 改为：
// metricsCollector.recordBackendCall(adapterType, instanceName, duration, false);

// metricsCollector.recordRetry(adapterType, instanceName, retryCount);
// 改为：
// metricsCollector.recordBackendCall(adapterType, instanceName, 0, false);