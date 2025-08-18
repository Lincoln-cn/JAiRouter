# Unified API Interface

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


JAiRouter provides a unified API interface compatible with OpenAI format, supporting multiple AI model services. All interfaces use the `/v1` prefix to ensure compatibility with OpenAI API.

## Chat Completion Interface

### `POST /v1/chat/completions`

Handles chat completion requests, supporting both streaming and non-streaming responses.

#### Request Parameters

```json
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant."
    },
    {
      "role": "user", 
      "content": "Hello!"
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

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | Yes | The name of the model to use |
| `messages` | array | Yes | List of conversation messages |
| [stream](file://D:\IdeaProjects\model-router\target\surefire-reports\2025-08-13T17-42-36_664.dumpstream) | boolean | No | Whether to enable streaming response, default is false |
| `max_tokens` | integer | No | Maximum number of tokens to generate |
| `temperature` | number | No | Sampling temperature between 0-2, default is 1 |
| `top_p` | number | No | Nucleus sampling parameter between 0-1, default is 1 |
| `top_k` | integer | No | Top-K sampling parameter |
| `frequency_penalty` | number | No | Frequency penalty between -2 to 2, default is 0 |
| `presence_penalty` | number | No | Presence penalty between -2 to 2, default is 0 |
| `stop` | string/array | No | Stop sequences |
| `user` | string | No | User identifier |

#### Message Format

```json
{
  "role": "user|assistant|system",
  "content": "Message content",
  "name": "Optional sender name"
}
```

#### Response Format

**Non-streaming Response:**

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
        "content": "Hello! I am an AI assistant, happy to serve you."
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

**Streaming Response:**

```json
data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

## Text Embedding Interface

### `POST /v1/embeddings`

Converts text into vector representations for semantic search, similarity calculations, and other tasks.

#### Request Parameters

```json
{
  "model": "text-embedding-ada-002",
  "input": "Text content to embed",
  "encoding_format": "float",
  "dimensions": 1536,
  "user": "user-123"
}
```

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | Yes | Embedding model name |
| `input` | string/array | Yes | Text to embed, can be a string or array of strings |
| `encoding_format` | string | No | Encoding format, supports "float" or "base64" |
| `dimensions` | integer | No | Output vector dimensions |
| `user` | string | No | User identifier |

#### Response Format

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

## Reranking Interface

### `POST /v1/rerank`

Reorders document lists based on relevance to a query.

#### Request Parameters

```json
{
  "model": "rerank-multilingual-v2.0",
  "query": "What is artificial intelligence?",
  "documents": [
    "Artificial intelligence is a branch of computer science",
    "Machine learning is a subfield of artificial intelligence",
    "Deep learning uses neural networks"
  ],
  "top_n": 3,
  "return_documents": true
}
```

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | Yes | Reranking model name |
| `query` | string | Yes | Query text |
| `documents` | array | Yes | List of documents to rank |
| `top_n` | integer | No | Number of documents to return |
| `return_documents` | boolean | No | Whether to return document content |

#### Response Format

```json
{
  "id": "rerank-123",
  "results": [
    {
      "index": 0,
      "score": 0.95,
      "document": "Artificial intelligence is a branch of computer science"
    },
    {
      "index": 1,
      "score": 0.87,
      "document": "Machine learning is a subfield of artificial intelligence"
    }
  ],
  "model": "rerank-multilingual-v2.0",
  "usage": {
    "total_tokens": 25
  }
}
```

## Text-to-Speech Interface

### `POST /v1/audio/speech`

Converts text into speech audio files.

#### Request Parameters

```json
{
  "model": "tts-1",
  "input": "Hello, this is a test voice.",
  "voice": "alloy",
  "response_format": "mp3",
  "speed": 1.0
}
```

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | Yes | TTS model name |
| `input` | string | Yes | Text to convert |
| `voice` | string | Yes | Voice type |
| `response_format` | string | No | Audio format, default is mp3 |
| `speed` | number | No | Speech speed between 0.25-4.0, default is 1.0 |

#### Response Format

Returns binary data of the audio file with Content-Type set according to `response_format`.

## Speech-to-Text Interface

### `POST /v1/audio/transcriptions`

Converts audio files into text.

#### Request Parameters

Using `multipart/form-data` format:

```
file: (audio file)
model: whisper-1
language: zh
response_format: json
temperature: 0
```

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| [file](file://D:\IdeaProjects\model-router\Makefile) | file | Yes | Audio file |
| `model` | string | Yes | STT model name |
| `language` | string | No | Audio language code |
| `response_format` | string | No | Response format, default is json |
| `temperature` | number | No | Sampling temperature |

#### Response Format

```json
{
  "text": "Transcribed text content"
}
```

## Image Generation Interface

### `POST /v1/images/generations`

Generates images based on text descriptions.

#### Request Parameters

```json
{
  "model": "dall-e-3",
  "prompt": "A cute little cat playing in a garden",
  "n": 1,
  "size": "1024x1024",
  "quality": "standard",
  "response_format": "url",
  "style": "vivid",
  "user": "user-123"
}
```

#### Parameter Description

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `model` | string | Yes | Image generation model name |
| `prompt` | string | Yes | Image description text |
| [n](file://D:\IdeaProjects\model-router\monitoring\grafana\dashboards\infrastructure.json) | integer | No | Number of images to generate, default is 1 |
| `size` | string | No | Image dimensions, default is 1024x1024 |
| `quality` | string | No | Image quality, standard or hd |
| `response_format` | string | No | Response format, url or b64_json |
| `style` | string | No | Image style, vivid or natural |
| `user` | string | No | User identifier |

#### Response Format

```json
{
  "created": 1677652288,
  "data": [
    {
      "url": "https://example.com/image.png",
      "revised_prompt": "Revised prompt"
    }
  ]
}
```

## Image Editing Interface

### `POST /v1/images/edits`

Edits existing images.

#### Request Parameters

Using `multipart/form-data` format:

```
image: (original image file)
mask: (optional mask file)
prompt: Edit description
model: dall-e-2
n: 1
size: 1024x1024
response_format: url
user: user-123
```

#### Response Format

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

## Error Handling

### Error Response Format

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

### Common Error Types

| Error Type | Description |
|------------|-------------|
| `invalid_request_error` | Request parameter error |
| `authentication_error` | Authentication failed |
| `permission_error` | Insufficient permissions |
| `not_found_error` | Resource not found |
| `rate_limit_error` | Request frequency exceeded |
| `api_error` | API internal error |
| `overloaded_error` | Service overloaded |

## Usage Examples

### cURL Examples

```bash
# Chat Completion
curl -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {"role": "user", "content": "Hello!"}
    ]
  }'

# Text Embedding
curl -X POST "http://localhost:8080/v1/embeddings" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "Text to embed"
  }'
```

### Python Examples

```python
import requests

# Configuration
base_url = "http://localhost:8080"
api_key = "your-api-key"
headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}"
}

# Chat Completion
response = requests.post(
    f"{base_url}/v1/chat/completions",
    headers=headers,
    json={
        "model": "gpt-3.5-turbo",
        "messages": [
            {"role": "user", "content": "Hello!"}
        ]
    }
)
print(response.json())

# Text Embedding
response = requests.post(
    f"{base_url}/v1/embeddings",
    headers=headers,
    json={
        "model": "text-embedding-ada-002",
        "input": "Text to embed"
    }
)
print(response.json())
```

### JavaScript Examples

```javascript
const baseUrl = 'http://localhost:8080';
const apiKey = 'your-api-key';

// Chat Completion
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
        { role: 'user', content: 'Hello!' }
      ]
    })
  });
  
  const data = await response.json();
  console.log(data);
}

// Text Embedding
async function embedding() {
  const response = await fetch(`${baseUrl}/v1/embeddings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: 'text-embedding-ada-002',
      input: 'Text to embed'
    })
  });
  
  const data = await response.json();
  console.log(data);
}
```