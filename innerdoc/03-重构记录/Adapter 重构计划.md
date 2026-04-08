# Adapter 重构计划

**创建日期**: 2026-03-25  
**版本**: V1.4.2  
**状态**: 进行中

---

## 📋 背景分析

### 当前问题

`BaseAdapter.java` 当前代码量约 62KB，存在以下问题：

1. **代码量大** - 单一文件包含过多功能
2. **职责不清晰** - 混合了协议转换、错误处理、指标收集等多种职责
3. **难以测试** - 庞大的基类导致单元测试困难
4. **扩展性差** - 添加新功能需要修改基类

### 代码结构分析

```
BaseAdapter.java (62KB)
├── 协议转换逻辑
├── 错误处理逻辑
├── 指标收集逻辑
├── 重试逻辑
├── 日志记录逻辑
└── 适配器注册逻辑
```

---

## 🎯 重构目标

### 第一阶段：代码分析和计划（V1.4.2）
- ✅ 创建重构计划文档
- ✅ 分析 BaseAdapter 代码结构
- ✅ 制定分阶段重构方案

### 第二阶段：功能拆分（V1.5.0）
- 拆分 BaseAdapter 按功能分类
- 创建独立的组件类：
  - `ProtocolConverter` - 协议转换
  - `ErrorHandler` - 错误处理
  - `AdapterMetrics` - 指标收集
  - `RetryHandler` - 重试处理
  - `AdapterLogger` - 日志记录

### 第三阶段：简化实现（V1.6.0）
- 简化 Adapter 实现类
- 使用组合模式替代继承
- 目标代码量：
  - BaseAdapter: 62KB → ~300 行
  - 各 Adapter 实现类代码减少 50%

---

## 🏗️ 重构设计方案

### 当前架构

```
┌─────────────────────────────────────┐
│         BaseAdapter (62KB)          │
│  ┌─────────────────────────────┐    │
│  │ - 协议转换                   │    │
│  │ - 错误处理                   │    │
│  │ - 指标收集                   │    │
│  │ - 重试逻辑                   │    │
│  │ - 日志记录                   │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
           ▲
           │ 继承
    ┌──────┴──────┐
    │             │
┌───▼───┐   ┌───▼───┐
│ Ollama│   │ Xinfer│
│ Adapter│   │ Adapter│
└───────┘   └───────┘
```

### 目标架构

```
┌──────────────────────┐
│   BaseAdapter        │
│   (协调器，~300 行)    │
└──────────────────────┘
         │
         │ 组合
    ┌────┴────┬──────────┬──────────┬──────────┐
    │         │          │          │          │
┌───▼───┐ ┌──▼────┐ ┌──▼────┐ ┌──▼────┐ ┌──▼────┐
│Protocol│ │Error  │ │Metrics│ │Retry  │ │Logger │
│Converter│ │Handler│ │Collector│ │Handler│ │       │
└───────┘ └───────┘ └───────┘ └───────┘ └───────┘

    ▲
    │ 继承
┌───┴────┐
│ Specific│
│ Adapters│
└────────┘
```

---

## 📝 实施步骤

### 步骤 1: 创建独立组件类

```java
// ProtocolConverter.java
public interface ProtocolConverter {
    ChatCompletionRequest convertToTarget(ChatCompletionRequest request);
    ChatCompletionResponse convertFromTarget(ChatCompletionResponse response);
}

// ErrorHandler.java
public interface ErrorHandler {
    AdapterException handleException(Exception e, String adapterType);
    boolean isRetryable(Exception e);
}

// MetricsCollector.java
public interface MetricsCollector {
    void recordRequest(String adapterType, long duration);
    void recordSuccess(String adapterType);
    void recordFailure(String adapterType, String reason);
}
```

### 步骤 2: 重构 BaseAdapter

```java
public abstract class BaseAdapter {
    // 使用组合替代继承
    private final ProtocolConverter protocolConverter;
    private final ErrorHandler errorHandler;
    private final MetricsCollector metricsCollector;
    private final RetryHandler retryHandler;
    
    // 构造函数注入
    protected BaseAdapter(ProtocolConverter protocolConverter,
                         ErrorHandler errorHandler,
                         MetricsCollector metricsCollector,
                         RetryHandler retryHandler) {
        this.protocolConverter = protocolConverter;
        this.errorHandler = errorHandler;
        this.metricsCollector = metricsCollector;
        this.retryHandler = retryHandler;
    }
    
    // 委托给组件处理
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        // ...
    }
}
```

### 步骤 3: 更新 Adapter 实现类

```java
public class OllamaAdapter extends BaseAdapter {
    public OllamaAdapter() {
        super(
            new OllamaProtocolConverter(),
            new OllamaErrorHandler(),
            new DefaultMetricsCollector(),
            new DefaultRetryHandler()
        );
    }
}
```

---

## 📊 预期收益

| 指标 | 重构前 | 重构后 | 改进 |
|------|--------|--------|------|
| BaseAdapter 代码量 | 62KB | ~300 行 | ↓ 95% |
| 单一职责符合度 | 低 | 高 | ✅ |
| 可测试性 | 低 | 高 | ✅ |
| 可扩展性 | 中 | 高 | ✅ |
| 代码重复率 | 30% | <10% | ↓ 67% |

---

## ⚠️ 风险评估

### 风险 1: 破坏现有功能
**缓解措施**:
- 保留现有接口，确保向后兼容
- 编写完整的回归测试
- 分阶段发布，逐步验证

### 风险 2: 工作量超出预期
**缓解措施**:
- 分 3 个版本完成 (V1.4.2, V1.5.0, V1.6.0)
- 优先重构问题最严重的部分
- 保持现有功能可用

### 风险 3: 团队学习曲线
**缓解措施**:
- 提供详细的设计文档
- 组织代码审查会议
- 编写使用示例

---

## 📅 时间计划

| 阶段 | 版本 | 工作内容 | 预计时间 |
|------|------|----------|----------|
| 第一阶段 | V1.4.2 | 计划文档、代码分析 | 0.5 天 |
| 第二阶段 | V1.5.0 | 功能拆分、组件创建 | 3 天 |
| 第三阶段 | V1.6.0 | 简化实现、测试完善 | 3 天 |

---

## 📚 参考资料

- [设计模式 - 组合模式](https://refactoringguru.cn/design-patterns/composite)
- [重构 - 以组合取代继承](https://refactoringguru.cn/replace-inheritance-with-composition)
- [单一职责原则](https://en.wikipedia.org/wiki/Single-responsibility_principle)

---

**文档维护者**: Development Team  
**最后更新**: 2026-03-25
