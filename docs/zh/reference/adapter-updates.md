# 适配器更新文档

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-03-11
> **作者**: Lincoln
<!-- /版本信息 -->

本文档详细记录了对 JAiRouter 中所有适配器的更新，以支持最新的 API 特性。

## 更新概述

为了支持最新的 AI 服务 API 特性，我们对以下适配器进行了全面更新：

- **VllmAdapter**: 支持最新的 vLLM OpenAI 兼容 API
- **GpuStackAdapter**: 支持最新的 GPUStack API 特性
- **LocalAiAdapter**: 支持最新的 LocalAI API 特性
- **NormalOpenAiAdapter**: 支持完整的 OpenAI API 特性
- **OllamaAdapter**: 支持最新的 Ollama API 特性
- **XinferenceAdapter**: 支持最新的 Xinference API 特性

## VllmAdapter 更新

### 新增功能

- **完整的 OpenAI 参数支持**:
  - `temperature`, `max_tokens`, `top_p`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`, `include_stop_str_in_output`
  - `ignore_eos`, `min_tokens`, `skip_special_tokens`

- **vLLM 扩展参数**:
  - `use_beam_search`, `do_sample`
  - `spaces_between_special_tokens`, `truncate_prompt_tokens`
  - `echo`, `add_generation_prompt`, `continue_final_message`

- **流式响应处理**:
  - 改进的 SSE 格式处理
  - 标准化的 chunk 格式转换
  - 更好的错误处理

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: vllm
      instances:
        - name: "vllm-model"
          base-url: "http://vllm-server:8000"
          path: "/v1/chat/completions"
          adapter-config:
            # vLLM 特定配置
            use-beam-search: false
            do-sample: true
```

## GpuStackAdapter 更新

### 新增功能

- **完整的 OpenAI 参数支持**:
  - 标准参数: `temperature`, `max_tokens`, `top_p`, `stop`
  - 高级参数: `top_k`, `min_p`, `repetition_penalty`

- **GPUStack 扩展参数**:
  - `use_beam_search`, `length_penalty`
  - `include_stop_str_in_output`, `ignore_eos`
  - `min_tokens`, `skip_special_tokens`

- **响应格式标准化**:
  - 标准的 OpenAI 响应格式
  - 完整的 usage 信息
  - 统一的错误处理

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: gpustack
      instances:
        - name: "gpustack-model"
          base-url: "http://gpustack-server:8000"
          path: "/v1/chat/completions"
          adapter-config:
            # GPUStack 特定配置
            use-beam-search: false
            skip-special-tokens: true
```

## LocalAiAdapter 更新

### 新增功能

- **完整的 OpenAI 兼容性**:
  - 支持所有标准 OpenAI 参数
  - 兼容 LocalAI 的扩展功能
  - 统一的错误处理机制

- **扩展参数支持**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `truncate_prompt_tokens`, `echo`

- **多服务支持**:
  - 聊天、嵌入、重排序、TTS、STT
  - 统一的请求/响应处理

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: localai
      instances:
        - name: "localai-model"
          base-url: "http://localai-server:8080"
          path: "/v1/chat/completions"
          adapter-config:
            # LocalAI 特定配置
            temperature: 0.7
            max-tokens: 1000
```

## NormalOpenAiAdapter 更新

### 新增功能

- **完整的 OpenAI API 支持**:
  - 所有标准参数和选项
  - 完整的错误处理
  - 标准化的响应格式

- **扩展参数**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `structured_outputs`, `priority`

- **多服务类型**:
  - 聊天、嵌入、重排序、TTS、STT
  - 统一的适配器接口

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: normal
      instances:
        - name: "openai-model"
          base-url: "https://api.openai.com"
          path: "/v1/chat/completions"
          adapter-config:
            # OpenAI 特定配置
            temperature: 0.7
            max-tokens: 1000
```

## OllamaAdapter 更新

### 新增功能

- **最新的 Ollama API 支持**:
  - 完整的选项支持 (`temperature`, `top_p`, `top_k`)
  - `num_predict`, `frequency_penalty`, `presence_penalty`
  - `repeat_penalty`, `seed`, `num_keep`

- **Ollama 扩展参数**:
  - `use_beam_search`, `min_p`, `repetition_penalty`
  - `length_penalty`, `truncate_prompt_tokens`
  - `echo`, `add_generation_prompt`

- **选项处理**:
  - 统一的选项参数处理
  - 与 OpenAI 参数的映射

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: ollama
      instances:
        - name: "ollama-model"
          base-url: "http://ollama-server:11434"
          path: "/api/chat"
          adapter-config:
            # Ollama 特定配置
            num-predict: 1000
            temperature: 0.7
            top-p: 0.9
```

## XinferenceAdapter 更新

### 新增功能

- **最新的 Xinference API 支持**:
  - 完整的 OpenAI 兼容参数
  - Xinference 特定选项
  - 统一的错误处理

- **扩展参数**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `truncate_prompt_tokens`, `echo`

- **多模型支持**:
  - 支持多种 Xinference 模型类型
  - 统一的适配器接口

### 配置示例

```yaml
model:
  services:
    chat:
      adapter: xinference
      instances:
        - name: "xinference-model"
          base-url: "http://xinference-server:9997"
          path: "/v1/chat/completions"
          adapter-config:
            # Xinference 特定配置
            temperature: 0.7
            max-tokens: 1000
```

## 通用改进

### 统一的参数处理

所有适配器现在支持统一的参数处理机制：

```java
// 通用参数映射
if (request.temperature() != null) {
    adapterRequest.put("temperature", request.temperature());
}
if (request.maxTokens() != null) {
    adapterRequest.put("max_tokens", request.maxTokens());
}
// ... 其他参数
```

### 扩展参数支持

所有适配器都支持通过 `extra_body` 传递扩展参数：

```json
{
  "model": "model-name",
  "messages": [...],
  "extra_body": {
    "use_beam_search": true,
    "min_p": 0.05,
    "repetition_penalty": 1.1
  }
}
```

### 响应格式标准化

所有适配器现在返回标准的 OpenAI 格式响应：

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "model-name",
  "choices": [...],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

## 向后兼容性

所有更新都保持了向后兼容性：

- 旧的配置文件仍然有效
- 现有的 API 调用不受影响
- 仅在需要新功能时才需要更新配置

## 性能优化

- 减少了不必要的参数转换
- 优化了 JSON 处理性能
- 改进了流式响应处理

## 故障排除

### 常见问题

1. **参数不生效**:
   - 检查适配器类型配置
   - 确认参数名称是否正确

2. **响应格式错误**:
   - 检查适配器是否正确配置
   - 确认后端服务支持相应参数

3. **流式响应问题**:
   - 检查 SSE 格式处理
   - 确认后端服务流式支持

## 下一步

- [API 参考](../api-reference/index.md) - 详细的 API 文档
- [配置指南](../configuration/index.md) - 完整的配置选项
- [故障排查](../troubleshooting/index.md) - 问题诊断和解决