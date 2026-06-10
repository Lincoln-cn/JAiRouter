# Adapter Updates Documentation

<!-- Version Information -->
> **Document Version**: 1.1.0
> **Last Updated**: 2026-06-10
> **Author**: Lincoln
<!-- /Version Information -->

This document details updates to all adapters in JAiRouter to support the latest API features.

## Update Overview

To support the latest AI service API features, we have comprehensively updated the following adapters:

- **VllmAdapter**: Supports latest vLLM OpenAI-compatible API
- **GpuStackAdapter**: Supports latest GPUStack API features
- **LocalAiAdapter**: Supports latest LocalAI API features
- **NormalOpenAiAdapter**: Supports complete OpenAI API features
- **OllamaAdapter**: Supports latest Ollama API features
- **XinferenceAdapter**: Supports latest Xinference API features

## VllmAdapter Updates

### New Features

- **Complete OpenAI Parameter Support**:
  - `temperature`, `max_tokens`, `top_p`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`, `include_stop_str_in_output`
  - `ignore_eos`, `min_tokens`, `skip_special_tokens`

- **vLLM Extended Parameters**:
  - `use_beam_search`, `do_sample`
  - `spaces_between_special_tokens`, `truncate_prompt_tokens`
  - `echo`, `add_generation_prompt`, `continue_final_message`

- **Streaming Response Handling**:
  - Improved SSE format processing
  - Standardized chunk format conversion
  - Better error handling

### Configuration Example

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
            # vLLM specific config
            use-beam-search: false
            do-sample: true
```

## GpuStackAdapter Updates

### New Features

- **Complete OpenAI Parameter Support**:
  - Standard params: `temperature`, `max_tokens`, `top_p`, `stop`
  - Advanced params: `top_k`, `min_p`, `repetition_penalty`

- **GPUStack Extended Parameters**:
  - `use_beam_search`, `length_penalty`
  - `include_stop_str_in_output`, `ignore_eos`
  - `min_tokens`, `skip_special_tokens`

- **Response Format Standardization**:
  - Standard OpenAI response format
  - Complete usage information
  - Unified error handling

### Configuration Example

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
            # GPUStack specific config
            use-beam-search: false
            skip-special-tokens: true
```

## LocalAiAdapter Updates

### New Features

- **Complete OpenAI Compatibility**:
  - Supports all standard OpenAI parameters
  - Compatible with LocalAI extended features
  - Unified error handling mechanism

- **Extended Parameter Support**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `truncate_prompt_tokens`, `echo`

- **Multi-service Support**:
  - Chat, embedding, rerank, TTS, STT
  - Unified request/response handling

### Configuration Example

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
            # LocalAI specific config
            temperature: 0.7
            max-tokens: 1000
```

## NormalOpenAiAdapter Updates

### New Features

- **Complete OpenAI API Support**:
  - All standard parameters and options
  - Complete error handling
  - Standardized response format

- **Extended Parameters**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `structured_outputs`, `priority`

- **Multi-service Types**:
  - Chat, embedding, rerank, TTS, STT
  - Unified adapter interface

### Configuration Example

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
            # OpenAI specific config
            temperature: 0.7
            max-tokens: 1000
```

## OllamaAdapter Updates

### New Features

- **Latest Ollama API Support**:
  - Complete options support (`temperature`, `top_p`, `top_k`)
  - `num_predict`, `frequency_penalty`, `presence_penalty`
  - `repeat_penalty`, `seed`, `num_keep`

- **Ollama Extended Parameters**:
  - `use_beam_search`, `min_p`, `repetition_penalty`
  - `length_penalty`, `truncate_prompt_tokens`
  - `echo`, `add_generation_prompt`

- **Options Handling**:
  - Unified options parameter processing
  - Mapping with OpenAI parameters

### Configuration Example

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
            # Ollama specific config
            num-predict: 1000
            temperature: 0.7
            top-p: 0.9
```

## XinferenceAdapter Updates

### New Features

- **Latest Xinference API Support**:
  - Complete OpenAI-compatible parameters
  - Xinference specific options
  - Unified error handling

- **Extended Parameters**:
  - `use_beam_search`, `top_k`, `min_p`
  - `repetition_penalty`, `length_penalty`
  - `truncate_prompt_tokens`, `echo`

- **Multi-model Support**:
  - Supports various Xinference model types
  - Unified adapter interface

### Configuration Example

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
            # Xinference specific config
            temperature: 0.7
            max-tokens: 1000
```

## Common Improvements

### Unified Parameter Handling

All adapters now support a unified parameter handling mechanism:

```java
// Generic parameter mapping
if (request.temperature() != null) {
    adapterRequest.put("temperature", request.temperature());
}
if (request.maxTokens() != null) {
    adapterRequest.put("max_tokens", request.maxTokens());
}
// ... other parameters
```

### Extended Parameter Support

All adapters support passing extended parameters via `extra_body`:

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

### Response Format Standardization

All adapters now return standard OpenAI format responses:

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

## Backward Compatibility

All updates maintain backward compatibility:

- Old configuration files remain valid
- Existing API calls unaffected
- Configuration updates only needed for new features

## Performance Optimization

- Reduced unnecessary parameter conversion
- Optimized JSON processing performance
- Improved streaming response handling

## Troubleshooting

### Common Issues

1. **Parameters Not Working**:
   - Check adapter type configuration
   - Confirm parameter names are correct

2. **Response Format Errors**:
   - Check adapter is configured correctly
   - Confirm backend service supports corresponding parameters

3. **Streaming Response Issues**:
   - Check SSE format processing
   - Confirm backend service streaming support

## Next Steps

- [API Reference](../api-reference/index.md) - Detailed API documentation
- [Configuration Guide](../configuration/index.md) - Complete configuration guide
- [Troubleshooting](../troubleshooting/index.md) - Common issues and solutions