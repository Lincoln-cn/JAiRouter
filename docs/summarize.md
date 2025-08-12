# JAiRouter 项目概要

JAiRouter 是一个基于 Spring Boot 的 AI 模型服务路由和负载均衡网关，用于统一管理和路由各种 AI 模型服务（如 Chat、Embedding、Rerank、TTS 等），支持多种负载均衡策略、限流、熔断、健康检查、动态配置更新等功能。

## 项目核心功能

1. **统一 API 网关**：提供 OpenAI 兼容格式的统一 `/v1/*` 接口
2. **多服务类型支持**：Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing
3. **负载均衡策略**：Random、Round Robin、Least Connections、IP Hash
4. **限流算法**：Token Bucket、Leaky Bucket、Sliding Window、Warm Up
5. **熔断机制**：失败阈值、恢复检测、降级策略
6. **健康检查**：服务状态监控与自动剔除不可用实例，定时清理不活跃限流器
7. **多适配器支持**：GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI
8. **动态配置更新**：运行时更新服务实例、权重、限流、熔断等配置
9. **配置持久化**：支持内存存储和文件存储两种后端
10. **全面的监控和管理接口**：提供配置版本管理、模型信息服务、统计信息等 REST API

## 核心架构组件

### 1. 主要入口点
- [ModelRouterApplication](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ModelRouterApplication.java#L8-L18)：Spring Boot 启动类

### 2. 核心服务注册与管理
- [ModelServiceRegistry](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L30-L656)：模型服务注册中心，负责管理所有服务实例、负载均衡、限流、熔断等核心功能
- [ServiceType](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L40-L42)：定义支持的服务类型（chat, embedding, rerank, tts, stt, imgGen, imgEdit）

### 3. 统一控制器
- [UniversalController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\UniversalController.java#L19-L190)：处理所有 `/v1/*` 路径的请求，根据请求类型分发到对应的服务

### 4. 管理控制器
- [ConfigurationVersionController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ConfigurationVersionController.java#L15-L110)：配置版本管理控制器
- [ModelInfoController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ModelInfoController.java#L16-L60)：模型信息服务控制器
- [ModelStatsController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ModelStatsController.java#L17-L53)：模型统计信息控制器
- [ServiceInstanceController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ServiceInstanceController.java#L21-L140)：服务实例管理控制器
- [ServiceTypeController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\ServiceTypeController.java#L22-L120)：服务类型管理控制器

### 5. 适配器系统
- [BaseAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\BaseAdapter.java#L18-L397)：适配器基类，定义了统一的请求处理流程
- [AdapterRegistry](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\AdapterRegistry.java#L11-L78)：适配器注册中心，管理各种适配器实现
- 适配器实现：
  - [NormalOpenAiAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\NormalOpenAiAdapter.java#L13-L177)：标准 OpenAI 适配器
  - [GpuStackAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\GpuStackAdapter.java#L16-L269)：GPUStack 适配器
  - [OllamaAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\OllamaAdapter.java#L16-L204)：Ollama 适配器
  - [VllmAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\VllmAdapter.java#L17-L274)：VLLM 适配器
  - [XinferenceAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\XinferenceAdapter.java#L17-L221)：Xinference 适配器
  - [LocalAiAdapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\adapter\impl\LocalAiAdapter.java#L17-L272)：LocalAI 适配器

### 6. 负载均衡
- [LoadBalancer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\LoadBalancer.java#L13-L46)：负载均衡接口
- [LoadBalancerManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\LoadBalancerManager.java#L11-L46)：负载均衡器管理器
- 实现类：
  - [RandomLoadBalancer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\RandomLoadBalancer.java#L19-L97)：随机负载均衡
  - [RoundRobinLoadBalancer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\RoundRobinLoadBalancer.java#L16-L56)：轮询负载均衡
  - [LeastConnectionsLoadBalancer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\LeastConnectionsLoadBalancer.java#L15-L74)：最少连接负载均衡
  - [IpHashLoadBalancer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\impl\IpHashLoadBalancer.java#L16-L83)：IP 哈希负载均衡

### 7. 限流系统
- [RateLimiter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimiter.java#L5-L18)：限流器接口
- [RateLimitManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitManager.java#L17-L105)：限流管理器，支持实例级、服务级、全局级、客户端IP级限流
- 实现类：
  - [TokenBucketRateLimiter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\impl\TokenBucketRateLimiter.java#L8-L38)：令牌桶算法
  - [LeakyBucketRateLimiter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\impl\LeakyBucketRateLimiter.java#L12-L42)：漏桶算法
  - [SlidingWindowRateLimiter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\impl\SlidingWindowRateLimiter.java#L12-L31)：滑动窗口算法
  - [WarmUpRateLimiter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\impl\WarmUpRateLimiter.java#L12-L79)：预热算法

### 8. 熔断器
- [CircuitBreaker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\circuitbreaker\CircuitBreaker.java#L2-L13)：熔断器接口
- [CircuitBreakerManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\circuitbreaker\CircuitBreakerManager.java#L9-L34)：熔断器管理器
- [DefaultCircuitBreaker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\circuitbreaker\DefaultCircuitBreaker.java#L2-L84)：默认熔断器实现
- 状态：CLOSED（正常）、OPEN（熔断开启）、HALF_OPEN（半开）

### 9. 配置管理
- [ConfigurationService](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\config\ConfigurationService.java#L18-L572)：配置服务，处理配置的增删改查
- [StoreManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\store\StoreManager.java#L8-L49)：存储管理接口，支持内存和文件两种存储方式
- [FileStoreManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\store\FileStoreManager.java#L19-L114)：文件存储实现
- [MemoryStoreManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\store\MemoryStoreManager.java#L10-L55)：内存存储实现
- [StoreManagerFactory](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\store\StoreManagerFactory.java#L11-L47)：存储管理器工厂

### 10. 健康检查与定时任务
- [ServiceStateManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\checker\ServiceStateManager.java#L14-L79)：服务状态管理器
- [ServerChecker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\checker\ServerChecker.java#L16-L122)：服务检查器，定时检查服务实例健康状态
- [RateLimiterCleanupChecker](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\checker\RateLimiterCleanupChecker.java#L12-L35)：限流器清理检查器，定时清理不活跃的客户端IP限流器

### 11. 降级策略
- [FallbackStrategy](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\fallback\FallbackStrategy.java#L5-L12)：降级策略接口
- [FallbackManager](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\fallback\FallbackManager.java#L11-L43)：降级策略管理器
- 实现类：
  - [DefaultFallbackStrategy](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\fallback\impl\DefaultFallbackStrategy.java#L10-L28)：默认降级策略
  - [CacheFallbackStrategy](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\fallback\impl\CacheFallbackStrategy.java#L22-L142)：缓存降级策略

## 定时任务系统

JAiRouter 集成了完善的定时任务系统，用于维护系统健康状态和性能优化：

### 1. 服务健康检查任务
- **执行频率**：每30秒
- **功能**：检查所有配置的服务实例的网络连接状态
- **实现**：使用 Socket 连接测试，支持 HTTP/HTTPS 协议
- **作用**：自动发现和剔除不可用的服务实例，确保负载均衡只分发到健康实例

### 2. 限流器清理任务
- **执行频率**：每5分钟
- **功能**：清理30分钟内未活跃的客户端IP限流器
- **目的**：防止长期运行导致的内存泄漏问题
- **策略**：基于最后访问时间进行清理，保留活跃的限流器实例

### 3. 任务管理特性
- **Spring 集成**：基于 `@Scheduled` 注解，由 Spring 容器统一管理
- **异常处理**：任务执行异常不会影响其他任务和主服务
- **日志记录**：详细的执行日志，便于监控和调试
- **资源优化**：合理的执行频率设计，平衡性能和资源消耗

## 工作流程

1. 客户端发送请求到 `/v1/*` 路径
2. [UniversalController](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\controller\UniversalController.java#L19-L190) 接收请求并根据路径分发到对应服务
3. 根据服务类型获取对应的适配器
4. 适配器通过 [ModelServiceRegistry](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\model\ModelServiceRegistry.java#L30-L656) 选择合适的实例：
   - 检查实例健康状态
   - 检查熔断器状态
   - 进行限流检查
   - 使用负载均衡算法选择实例
5. 向选中的实例发送请求并返回结果
6. 记录调用结果用于负载均衡和熔断器状态更新

## 配置方式

支持两种配置方式：
1. **静态配置**：通过 [application.yml](file://D:\IdeaProjects\model-router\src\main\resources\application.yml) 文件定义
2. **动态配置**：通过 REST API 在运行时动态更新

配置优先级：动态配置 > 静态配置

## 主要特性

- **高可用性**：通过负载均衡、健康检查、熔断机制保证服务高可用
- **可扩展性**：支持多种适配器，易于扩展新的 AI 服务
- **灵活性**：支持多种负载均衡和限流算法，可根据需求配置
- **可观测性**：通过健康检查和状态监控提供服务可观测性
- **动态性**：支持运行时动态配置更新，无需重启服务
- **资源优化**：定时清理不活跃的限流器，防止内存泄漏
- **全面的管理接口**：提供丰富的 REST API 用于服务管理和监控
- **完善的测试覆盖**：包含针对核心组件的单元测试
