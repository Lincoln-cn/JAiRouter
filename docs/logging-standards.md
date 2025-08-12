# JAiRouter 日志使用规范

## 📋 日志级别使用标准

### 1. ERROR 级别
**使用场景**: 系统错误、异常情况、需要立即关注的问题
```java
// ✅ 正确使用
logger.error("配置文件加载失败: {}", configPath, exception);
logger.error("数据库连接失败，服务不可用", exception);
logger.error("关键业务操作失败: userId={}, operation={}", userId, operation, exception);

// ❌ 错误使用
logger.error("用户输入验证失败"); // 应该使用WARN
logger.error("调试信息: {}", debugInfo); // 应该使用DEBUG
```

### 2. WARN 级别
**使用场景**: 警告信息、潜在问题、需要关注但不影响系统运行
```java
// ✅ 正确使用
logger.warn("服务实例健康检查失败: {}", instanceUrl);
logger.warn("限流触发: serviceType={}, clientIp={}", serviceType, clientIp);
logger.warn("熔断器开启: service={}, failureCount={}", serviceName, failureCount);
logger.warn("配置项缺失，使用默认值: key={}, defaultValue={}", key, defaultValue);

// ❌ 错误使用
logger.warn("用户登录成功"); // 应该使用INFO
logger.warn("详细的调试信息"); // 应该使用DEBUG
```

### 3. INFO 级别
**使用场景**: 重要的业务操作、系统状态变更、关键流程节点
```java
// ✅ 正确使用
logger.info("服务启动完成: port={}", serverPort);
logger.info("配置更新成功: serviceType={}, instanceCount={}", serviceType, count);
logger.info("用户操作: userId={}, action={}, result={}", userId, action, result);
logger.info("定时任务执行完成: taskName={}, duration={}ms", taskName, duration);

// ❌ 错误使用
logger.info("进入方法: methodName={}", methodName); // 应该使用DEBUG
logger.info("循环处理: index={}", i); // 应该使用DEBUG
```

### 4. DEBUG 级别
**使用场景**: 详细的执行流程、调试信息、开发阶段的跟踪信息
```java
// ✅ 正确使用
logger.debug("负载均衡选择实例: algorithm={}, selectedInstance={}", algorithm, instance);
logger.debug("限流器状态: capacity={}, tokens={}, rate={}", capacity, tokens, rate);
logger.debug("请求详情: method={}, url={}, params={}", method, url, params);
logger.debug("缓存操作: key={}, hit={}, size={}", key, hit, cacheSize);

// ❌ 错误使用
logger.debug("系统启动"); // 应该使用INFO
logger.debug("发生严重错误"); // 应该使用ERROR
```

## 🎯 不同组件的日志策略

### 1. Controller 层
```java
@RestController
public class ExampleController {
    private static final Logger logger = LoggerFactory.getLogger(ExampleController.class);
    
    @PostMapping("/api/example")
    public ResponseEntity<?> handleRequest(@RequestBody ExampleRequest request) {
        // INFO: 记录重要的API调用
        logger.info("接收到请求: endpoint=/api/example, type={}", request.getType());
        
        try {
            // DEBUG: 记录详细的处理过程
            logger.debug("处理请求详情: {}", request);
            
            ExampleResponse response = exampleService.process(request);
            
            // INFO: 记录成功的业务操作
            logger.info("请求处理成功: type={}, resultCount={}", request.getType(), response.getCount());
            
            return ResponseEntity.ok(response);
            
        } catch (ValidationException e) {
            // WARN: 记录业务验证失败
            logger.warn("请求验证失败: type={}, error={}", request.getType(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
            
        } catch (Exception e) {
            // ERROR: 记录系统异常
            logger.error("请求处理失败: type={}", request.getType(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

### 2. Service 层
```java
@Service
public class ExampleService {
    private static final Logger logger = LoggerFactory.getLogger(ExampleService.class);
    
    public ExampleResponse process(ExampleRequest request) {
        // INFO: 记录重要的业务操作开始
        logger.info("开始处理业务逻辑: type={}", request.getType());
        
        try {
            // DEBUG: 记录详细的处理步骤
            logger.debug("验证请求参数: {}", request);
            validateRequest(request);
            
            logger.debug("执行业务逻辑: step=1");
            ExampleData data = processStep1(request);
            
            logger.debug("执行业务逻辑: step=2, dataSize={}", data.size());
            ExampleResponse response = processStep2(data);
            
            // INFO: 记录业务操作完成
            logger.info("业务逻辑处理完成: type={}, resultCount={}", request.getType(), response.getCount());
            
            return response;
            
        } catch (BusinessException e) {
            // WARN: 记录业务异常
            logger.warn("业务处理异常: type={}, error={}", request.getType(), e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // ERROR: 记录系统异常
            logger.error("业务处理失败: type={}", request.getType(), e);
            throw new ServiceException("处理失败", e);
        }
    }
}
```

### 3. 定时任务
```java
@Component
public class ScheduledTask {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledTask.class);
    
    @Scheduled(fixedRate = 300000) // 5分钟
    public void cleanupTask() {
        // INFO: 记录定时任务开始
        logger.info("开始执行清理任务");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        try {
            // DEBUG: 记录详细的执行过程
            logger.debug("扫描需要清理的数据");
            
            cleanedCount = performCleanup();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // INFO: 记录任务完成情况
            logger.info("清理任务完成: cleanedCount={}, duration={}ms", cleanedCount, duration);
            
        } catch (Exception e) {
            // ERROR: 记录任务执行失败
            logger.error("清理任务执行失败", e);
        }
    }
}
```

### 4. 健康检查组件
```java
@Component
public class HealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
    
    public void checkServiceHealth(String serviceUrl) {
        try {
            // DEBUG: 记录检查过程
            logger.debug("开始健康检查: url={}", serviceUrl);
            
            boolean isHealthy = performHealthCheck(serviceUrl);
            
            if (isHealthy) {
                // DEBUG: 正常情况使用DEBUG级别
                logger.debug("服务健康检查通过: url={}", serviceUrl);
            } else {
                // WARN: 健康检查失败使用WARN级别
                logger.warn("服务健康检查失败: url={}", serviceUrl);
            }
            
        } catch (Exception e) {
            // ERROR: 检查过程异常使用ERROR级别
            logger.error("健康检查异常: url={}", serviceUrl, e);
        }
    }
}
```

## 🔧 性能优化最佳实践

### 1. 使用参数化日志
```java
// ❌ 避免字符串拼接
logger.info("用户 " + userId + " 执行了操作 " + operation + " 结果: " + result);

// ✅ 使用参数化日志
logger.info("用户 {} 执行了操作 {} 结果: {}", userId, operation, result);
```

### 2. 条件日志输出
```java
// ❌ 避免不必要的对象创建
logger.debug("复杂对象详情: " + complexObject.toDetailString());

// ✅ 使用条件判断
if (logger.isDebugEnabled()) {
    logger.debug("复杂对象详情: {}", complexObject.toDetailString());
}
```

### 3. 避免敏感信息泄露
```java
// ❌ 避免记录敏感信息
logger.info("用户登录: username={}, password={}", username, password);

// ✅ 脱敏或省略敏感信息
logger.info("用户登录: username={}, passwordLength={}", username, password.length());
```

### 4. 合理使用异常堆栈
```java
// ❌ 不必要的堆栈信息
logger.warn("业务验证失败: {}", e.getMessage(), e);

// ✅ 业务异常通常不需要堆栈
logger.warn("业务验证失败: {}", e.getMessage());

// ✅ 系统异常需要完整堆栈
logger.error("系统处理异常: {}", e.getMessage(), e);
```

## 📊 日志监控建议

### 1. 关键指标监控
- ERROR 日志数量/频率
- WARN 日志数量/频率  
- 关键业务操作成功率
- 系统性能指标

### 2. 告警规则示例
```yaml
# 错误日志告警
error_log_alert:
  condition: "ERROR日志1分钟内超过10条"
  action: "立即通知运维团队"

# 警告日志告警  
warn_log_alert:
  condition: "WARN日志5分钟内超过100条"
  action: "通知开发团队"

# 业务操作告警
business_failure_alert:
  condition: "关键业务操作失败率超过5%"
  action: "立即通知业务团队"
```

## 📝 日志审查清单

### 开发阶段
- [ ] 是否使用了正确的日志级别
- [ ] 是否包含了必要的上下文信息
- [ ] 是否避免了敏感信息泄露
- [ ] 是否使用了参数化日志
- [ ] 是否避免了过度日志输出

### 测试阶段
- [ ] 不同环境的日志级别是否合适
- [ ] 日志文件大小和轮转是否正常
- [ ] 异步日志性能是否满足要求
- [ ] 日志格式是否便于分析

### 生产部署
- [ ] 生产环境日志级别是否为WARN或更高
- [ ] 日志监控和告警是否配置完成
- [ ] 日志存储和备份策略是否就绪
- [ ] 日志分析工具是否集成完成