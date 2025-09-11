# JAiRouter告警规则指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: f47f2607  
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

本文档详细介绍了JAiRouter项目的Prometheus告警规则配置，包括告警类型、触发条件、处理建议等。

## 告警规则分类

### 1. 基础服务告警 (jairouter.basic)

#### JAiRouterServiceDown
- **描述**: JAiRouter服务不可用
- **触发条件**: `up{job="jairouter"} == 0`
- **持续时间**: 1分钟
- **严重级别**: Critical
- **处理建议**:
  1. 检查JAiRouter服务进程状态
  2. 查看应用启动日志
  3. 验证端口占用情况
  4. 检查系统资源是否充足

#### JAiRouterHighErrorRate
- **描述**: JAiRouter错误率过高
- **触发条件**: 4xx/5xx错误率超过10%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查应用错误日志
  2. 验证后端服务状态
  3. 检查网络连接
  4. 分析错误类型分布

#### JAiRouterCriticalErrorRate
- **描述**: JAiRouter严重错误率过高
- **触发条件**: 5xx错误率超过5%
- **持续时间**: 1分钟
- **严重级别**: Critical
- **处理建议**:
  1. 立即检查服务器状态
  2. 查看应用异常日志
  3. 检查数据库连接
  4. 验证依赖服务可用性

### 2. 性能告警 (jairouter.performance)

#### JAiRouterHighLatency
- **描述**: JAiRouter响应时间过高
- **触发条件**: 95%分位响应时间超过2秒
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查系统资源使用情况
  2. 分析慢查询和性能瓶颈
  3. 验证后端服务响应时间
  4. 检查网络延迟

#### JAiRouterCriticalLatency
- **描述**: JAiRouter响应时间严重过高
- **触发条件**: 95%分位响应时间超过5秒
- **持续时间**: 1分钟
- **严重级别**: Critical
- **处理建议**:
  1. 立即检查系统负载
  2. 分析性能瓶颈
  3. 考虑临时限流
  4. 检查是否需要扩容

#### JAiRouterLowRequestVolume
- **描述**: JAiRouter请求量异常低
- **触发条件**: 请求率低于0.1 req/s
- **持续时间**: 5分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查客户端连接状态
  2. 验证负载均衡器配置
  3. 检查网络路由
  4. 确认是否为正常业务低峰

#### JAiRouterSlowQueriesDetected
- **描述**: JAiRouter检测到慢查询
- **触发条件**: 5分钟内慢查询数量超过5个
- **持续时间**: 1分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查慢查询日志
  2. 分析慢查询原因
  3. 优化相关查询或操作
  4. 考虑增加索引或缓存

#### JAiRouterHighSlowQueryRate
- **描述**: JAiRouter慢查询率过高
- **触发条件**: 慢查询速率超过1个/秒
- **持续时间**: 2分钟
- **严重级别**: Critical
- **处理建议**:
  1. 立即分析系统性能瓶颈
  2. 检查数据库连接和查询
  3. 评估是否需要扩容资源
  4. 考虑临时限流措施

### 3. 后端服务告警 (jairouter.backend)

#### JAiRouterBackendDown
- **描述**: JAiRouter后端服务不可用
- **触发条件**: `jairouter_backend_health == 0`
- **持续时间**: 1分钟
- **严重级别**: Critical
- **处理建议**:
  1. 检查后端服务状态
  2. 验证网络连接
  3. 检查服务配置
  4. 查看健康检查日志

#### JAiRouterBackendHighLatency
- **描述**: JAiRouter后端服务响应慢
- **触发条件**: 后端95%分位响应时间超过3秒
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查后端服务性能
  2. 分析网络延迟
  3. 验证后端资源使用
  4. 考虑调整超时配置

#### JAiRouterBackendHighErrorRate
- **描述**: JAiRouter后端服务错误率高
- **触发条件**: 后端错误率超过15%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查后端服务日志
  2. 验证API兼容性
  3. 检查认证配置
  4. 分析错误类型

### 4. 基础设施告警 (jairouter.infrastructure)

#### JAiRouterCircuitBreakerOpen
- **描述**: JAiRouter熔断器开启
- **触发条件**: `jairouter_circuit_breaker_state == 2`
- **持续时间**: 30秒
- **严重级别**: Warning
- **处理建议**:
  1. 检查下游服务状态
  2. 分析失败率原因
  3. 验证熔断器配置
  4. 考虑手动恢复

#### JAiRouterRateLimitTriggered
- **描述**: JAiRouter限流器频繁触发
- **触发条件**: 限流拒绝率超过10 req/s
- **持续时间**: 1分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析请求来源
  2. 检查限流配置
  3. 评估是否需要调整阈值
  4. 考虑增加容量

#### JAiRouterLoadBalancerImbalance
- **描述**: JAiRouter负载均衡不均匀
- **触发条件**: 实例间请求量差异超过50%
- **持续时间**: 5分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查负载均衡策略
  2. 验证实例健康状态
  3. 分析实例性能差异
  4. 考虑调整权重配置

### 5. 资源告警 (jairouter.resources)

#### JAiRouterHighMemoryUsage
- **描述**: JAiRouter内存使用率高
- **触发条件**: JVM堆内存使用率超过80%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查内存泄漏
  2. 分析GC日志
  3. 考虑调整JVM参数
  4. 评估是否需要扩容

#### JAiRouterCriticalMemoryUsage
- **描述**: JAiRouter内存使用率严重过高
- **触发条件**: JVM堆内存使用率超过90%
- **持续时间**: 1分钟
- **严重级别**: Critical
- **处理建议**:
  1. 立即检查内存使用
  2. 考虑重启服务
  3. 增加内存配置
  4. 分析内存泄漏原因

#### JAiRouterHighGCRate
- **描述**: JAiRouter GC频率过高
- **触发条件**: GC频率超过0.2次/秒
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析GC日志
  2. 优化JVM参数
  3. 检查内存分配模式
  4. 考虑调整堆大小

#### JAiRouterHighThreadCount
- **描述**: JAiRouter线程数过多
- **触发条件**: 当前线程数超过200
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查线程池配置
  2. 分析线程堆栈
  3. 查找线程泄漏
  4. 优化并发处理

### 6. 业务指标告警 (jairouter.business)

#### JAiRouterModelCallFailureRate
- **描述**: JAiRouter模型调用失败率高
- **触发条件**: 模型调用失败率超过20%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查AI模型服务状态
  2. 验证API密钥和配置
  3. 分析失败原因
  4. 检查网络连接

#### JAiRouterLargeRequestSize
- **描述**: JAiRouter请求大小异常
- **触发条件**: 95%分位请求大小超过1MB
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析请求内容
  2. 检查客户端行为
  3. 考虑添加大小限制
  4. 优化数据传输

#### JAiRouterLargeResponseSize
- **描述**: JAiRouter响应大小异常
- **触发条件**: 95%分位响应大小超过5MB
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查响应内容
  2. 优化数据格式
  3. 考虑分页处理
  4. 检查是否有数据泄漏

### 7. 安全告警 (jairouter.security)

#### JAiRouterSuspiciousIPActivity
- **描述**: JAiRouter检测到可疑IP活动
- **触发条件**: 单个IP请求率超过100 req/s
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析IP访问模式
  2. 检查是否为攻击行为
  3. 考虑临时封禁
  4. 加强访问控制

#### JAiRouterHighAuthFailureRate
- **描述**: JAiRouter认证失败率高
- **触发条件**: 401错误率超过5%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查认证系统状态
  2. 分析失败原因
  3. 验证密钥配置
  4. 检查是否有暴力破解

#### JAiRouterHighClientErrorRate
- **描述**: JAiRouter客户端错误率高
- **触发条件**: 4xx错误率超过20%
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析客户端请求
  2. 检查API文档一致性
  3. 验证参数校验逻辑
  4. 提供更好的错误信息

### 8. 容量规划告警 (jairouter.capacity)

#### JAiRouterRequestVolumeGrowth
- **描述**: JAiRouter请求量显著增长
- **触发条件**: 相比24小时前增长超过50%
- **持续时间**: 5分钟
- **严重级别**: Info
- **处理建议**:
  1. 分析增长原因
  2. 评估系统容量
  3. 考虑扩容计划
  4. 监控资源使用

#### JAiRouterLowDiskSpace
- **描述**: JAiRouter服务器磁盘空间不足
- **触发条件**: 可用磁盘空间低于20%
- **持续时间**: 5分钟
- **严重级别**: Warning
- **处理建议**:
  1. 清理临时文件
  2. 归档历史日志
  3. 检查磁盘使用
  4. 考虑扩容

#### JAiRouterHighCPUUsage
- **描述**: JAiRouter服务器CPU使用率高
- **触发条件**: CPU使用率超过80%
- **持续时间**: 3分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析CPU使用情况
  2. 检查进程状态
  3. 优化性能瓶颈
  4. 考虑扩容

### 9. 依赖服务告警 (jairouter.dependencies)

#### JAiRouterDatabaseConnectionIssue
- **描述**: JAiRouter数据库连接池使用率高
- **触发条件**: 连接池使用率超过80%
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查数据库状态
  2. 分析连接泄漏
  3. 优化连接池配置
  4. 检查慢查询

#### JAiRouterLowCacheHitRate
- **描述**: JAiRouter缓存命中率低
- **触发条件**: 缓存命中率低于70%
- **持续时间**: 5分钟
- **严重级别**: Warning
- **处理建议**:
  1. 分析缓存策略
  2. 检查缓存配置
  3. 优化缓存键设计
  4. 考虑预热缓存

#### JAiRouterExternalAPITimeout
- **描述**: JAiRouter外部API调用超时频繁
- **触发条件**: 超时频率超过5次/秒
- **持续时间**: 2分钟
- **严重级别**: Warning
- **处理建议**:
  1. 检查外部服务状态
  2. 分析网络延迟
  3. 调整超时配置
  4. 考虑重试策略

## 告警处理流程

### 1. 告警接收
- 通过邮件、Slack、钉钉等渠道接收告警通知
- 查看告警详细信息和严重级别
- 确认告警的真实性和紧急程度

### 2. 初步诊断
- 访问Grafana仪表板查看详细指标
- 检查Prometheus告警页面了解相关告警
- 查看应用日志和系统日志

### 3. 问题处理
- 根据告警类型执行相应的处理步骤
- 记录处理过程和结果
- 必要时联系相关团队协助

### 4. 验证恢复
- 确认问题已解决
- 验证相关指标恢复正常
- 等待告警自动解除

### 5. 事后分析
- 分析问题根本原因
- 评估是否需要调整告警规则
- 完善预防措施和处理流程

## 告警规则维护

### 定期检查
- 每月检查告警规则的有效性
- 根据业务变化调整阈值
- 清理过时或无效的告警规则

### 阈值调优
- 基于历史数据分析合理阈值
- 避免过多的误报和漏报
- 考虑业务特点和用户体验

### 文档更新
- 及时更新告警处理文档
- 记录常见问题和解决方案
- 分享最佳实践和经验教训

## 测试和验证

### 语法检查
```bash
# Linux/macOS
./monitoring/prometheus/test-alerts.sh

# Windows
.\monitoring\prometheus\test-alerts.ps1
```

### 完整验证
```bash
# Linux/macOS
./monitoring/prometheus/validate-alerts.sh

# Windows
.\monitoring\prometheus\validate-alerts.ps1
```

### 手动测试
- 模拟故障场景触发告警
- 验证通知渠道是否正常
- 测试告警恢复机制

## 相关链接

- [Prometheus告警规则文档](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [AlertManager配置文档](https://prometheus.io/docs/alerting/latest/configuration/)
- [Grafana仪表板](http://localhost:3000/d/jairouter-overview)
- [Prometheus Web界面](http://localhost:9090/alerts)

## 联系方式

如有问题或建议，请联系：
- 运维团队: ops-team@example.com
- 开发团队: dev-team@example.com
- JAiRouter团队: jairouter-team@example.com