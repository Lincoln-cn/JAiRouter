# 统一 API 接口

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


JAiRouter 提供兼容 OpenAI 格式的统一 API 接口，支持多种 AI 模型服务。所有接口都使用 `/v1` 前缀，确保与 OpenAI API 的兼容性。

## 聊天完成接口

### `POST /v1/chat/completions`

处理聊天完成请求，支持流式和非流式响应。

#### 请求参数

```json
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "system",
      "content": "你是一个有用的助手。"
    },
    {
      "role": "user", 
      "content": "你好！"
    }
  ],
  "stream": false,
  "max_tokens": 1000,
  "temperature": 0.7,
  "top_p": 1.0,
  "top_k": 50,
  "frequency_penalty": 0.0,
  "presence_penalty": 0.0,
  "stop": null,
  "user": "user-123"
}
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 要使用的模型名称 |
| `messages` | array | 是 | 对话消息列表 |
| `stream` | boolean | 否 | 是否启用流式响应，默认 false |
| `max_tokens` | integer | 否 | 生成的最大令牌数 |
| `temperature` | number | 否 | 采样温度，0-2 之间，默认 1 |
| `top_p` | number | 否 | 核采样参数，0-1 之间，默认 1 |
| `top_k` | integer | 否 | Top-K 采样参数 |
| `frequency_penalty` | number | 否 | 频率惩罚，-2 到 2 之间，默认 0 |
| `presence_penalty` | number | 否 | 存在惩罚，-2 到 2 之间，默认 0 |
| `stop` | string/array | 否 | 停止序列 |
| `user` | string | 否 | 用户标识符 |

#### 消息格式

```json
{
  "role": "user|assistant|system",
  "content": "消息内容",
  "name": "可选的消息发送者名称"
}
```

#### 响应格式

**非流式响应：**

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-3.5-turbo",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！我是一个AI助手，很高兴为你服务。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 15,
    "total_tokens": 35
  },
  "system_fingerprint": "fp_44709d6fcb"
}
```

**流式响应：**

```json
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"role":"assistant","content":"你好"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"！"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

## 文本嵌入接口

### `POST /v1/embeddings`

将文本转换为向量表示，用于语义搜索、相似度计算等任务。

#### 请求参数

```json
{
  "model": "text-embedding-ada-002",
  "input": "要嵌入的文本内容",
  "encoding_format": "float",
  "dimensions": 1536,
  "user": "user-123"
}
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 嵌入模型名称 |
| `input` | string/array | 是 | 要嵌入的文本，可以是字符串或字符串数组 |
| `encoding_format` | string | 否 | 编码格式，支持 "float" 或 "base64" |
| `dimensions` | integer | 否 | 输出向量的维度 |
| `user` | string | 否 | 用户标识符 |

#### 响应格式

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.0023064255, -0.009327292, ...],
      "index": 0
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

## 重排序接口

### `POST /v1/rerank`

对文档列表进行重新排序，根据查询的相关性进行排名。

#### 请求参数

```json
{
  "model": "rerank-multilingual-v2.0",
  "query": "什么是人工智能？",
  "documents": [
    "人工智能是计算机科学的一个分支",
    "机器学习是人工智能的子领域",
    "深度学习使用神经网络"
  ],
  "top_n": 3,
  "return_documents": true
}
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 重排序模型名称 |
| `query` | string | 是 | 查询文本 |
| `documents` | array | 是 | 要排序的文档列表 |
| `top_n` | integer | 否 | 返回的文档数量 |
| `return_documents` | boolean | 否 | 是否返回文档内容 |

#### 响应格式

```json
{
  "id": "rerank-123",
  "results": [
    {
      "index": 0,
      "score": 0.95,
      "document": "人工智能是计算机科学的一个分支"
    },
    {
      "index": 1,
      "score": 0.87,
      "document": "机器学习是人工智能的子领域"
    }
  ],
  "model": "rerank-multilingual-v2.0",
  "usage": {
    "total_tokens": 25
  }
}
```

## 文本转语音接口

### `POST /v1/audio/speech`

将文本转换为语音音频文件。

#### 请求参数

```json
{
  "model": "tts-1",
  "input": "你好，这是一段测试语音。",
  "voice": "alloy",
  "response_format": "mp3",
  "speed": 1.0
}
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | 是 | TTS 模型名称 |
| `input` | string | 是 | 要转换的文本 |
| `voice` | string | 是 | 语音类型 |
| `response_format` | string | 否 | 音频格式，默认 mp3 |
| `speed` | number | 否 | 语音速度，0.25-4.0，默认 1.0 |

#### 响应格式

返回音频文件的二进制数据，Content-Type 根据 `response_format` 设置。

## 语音转文本接口

### `POST /v1/audio/transcriptions`

将音频文件转换为文本。

#### 请求参数

使用 `multipart/form-data` 格式：

```
file: (音频文件)
model: whisper-1
language: zh
response_format: json
temperature: 0
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `file` | file | 是 | 音频文件 |
| `model` | string | 是 | STT 模型名称 |
| `language` | string | 否 | 音频语言代码 |
| `response_format` | string | 否 | 响应格式，默认 json |
| `temperature` | number | 否 | 采样温度 |

#### 响应格式

```json
{
  "text": "转录的文本内容"
}
```

## 图像生成接口

### `POST /v1/images/generations`

根据文本描述生成图像。

#### 请求参数

```json
{
  "model": "dall-e-3",
  "prompt": "一只可爱的小猫在花园里玩耍",
  "n": 1,
  "size": "1024x1024",
  "quality": "standard",
  "response_format": "url",
  "style": "vivid",
  "user": "user-123"
}
```

#### 参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 图像生成模型名称 |
| `prompt` | string | 是 | 图像描述文本 |
| `n` | integer | 否 | 生成图像数量，默认 1 |
| `size` | string | 否 | 图像尺寸，默认 1024x1024 |
| `quality` | string | 否 | 图像质量，standard 或 hd |
| `response_format` | string | 否 | 响应格式，url 或 b64_json |
| `style` | string | 否 | 图像风格，vivid 或 natural |
| `user` | string | 否 | 用户标识符 |

#### 响应格式

```json
{
  "created": 1677652288,
  "data": [
    {
      "url": "https://example.com/image.png",
      "revised_prompt": "修订后的提示词"
    }
  ]
}
```

## 图像编辑接口

### `POST /v1/images/edits`

编辑现有图像。

#### 请求参数

使用 `multipart/form-data` 格式：

```
image: (原始图像文件)
mask: (可选的遮罩文件)
prompt: 编辑描述
model: dall-e-2
n: 1
size: 1024x1024
response_format: url
user: user-123
```

#### 响应格式

```json
{
  "created": 1677652288,
  "data": [
    {
      "url": "https://example.com/edited-image.png"
    }
  ]
}
```

## 错误处理

### 错误响应格式

```json
{
  "error": {
    "message": "Invalid request: missing required parameter 'model'",
    "type": "invalid_request_error",
    "param": "model",
    "code": "missing_parameter"
  }
}
```

### 常见错误类型

| 错误类型 | 说明 |
|----------|------|
| `invalid_request_error` | 请求参数错误 |
| `authentication_error` | 认证失败 |
| `permission_error` | 权限不足 |
| `not_found_error` | 资源不存在 |
| `rate_limit_error` | 请求频率超限 |
| `api_error` | API 内部错误 |
| `overloaded_error` | 服务过载 |

## 使用示例

### cURL 示例

```bash
# 聊天完成
curl -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {"role": "user", "content": "你好！"}
    ]
  }'

# 文本嵌入
curl -X POST "http://localhost:8080/v1/embeddings" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "要嵌入的文本"
  }'
```

### Python 示例

```python
import requests

# 配置
base_url = "http://localhost:8080"
api_key = "your-api-key"
headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}"
}

# 聊天完成
response = requests.post(
    f"{base_url}/v1/chat/completions",
    headers=headers,
    json={
        "model": "gpt-3.5-turbo",
        "messages": [
            {"role": "user", "content": "你好！"}
        ]
    }
)
print(response.json())

# 文本嵌入
response = requests.post(
    f"{base_url}/v1/embeddings",
    headers=headers,
    json={
        "model": "text-embedding-ada-002",
        "input": "要嵌入的文本"
    }
)
print(response.json())
```

### JavaScript 示例

```javascript
const baseUrl = 'http://localhost:8080';
const apiKey = 'your-api-key';

// 聊天完成
async function chatCompletion() {
  const response = await fetch(`${baseUrl}/v1/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: 'gpt-3.5-turbo',
      messages: [
        { role: 'user', content: '你好！' }
      ]
    })
  });
  
  const data = await response.json();
  console.log(data);
}

// 文本嵌入
async function embedding() {
  const response = await fetch(`${baseUrl}/v1/embeddings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: 'text-embedding-ada-002',
      input: '要嵌入的文本'
    })
  });
  
  const data = await response.json();
  console.log(data);
}
```