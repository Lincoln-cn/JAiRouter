# Playground 快速参考指南

## 目录结构

```
frontend/src/views/playground/
├── types/
│   ├── registry.ts              # 服务注册表（核心）
│   └── playground.ts            # 原有类型（兼容）
├── composables/
│   ├── useAuthentication.ts     # 认证管理
│   └── useServiceRequest.ts     # 请求处理
├── components/
│   ├── universal/               # 通用组件
│   └── visualization/           # 可视化组件
├── experience/                  # 体验页面
└── PlaygroundMain.vue           # 主容器
```

## 快速开始

### 1. 使用聊天功能

```vue
<template>
  <ChatExperience />
</template>

<script setup lang="ts">
import { ChatExperience } from '@/views/playground/experience'
</script>
```

### 2. 使用认证管理

```typescript
import { useAuthentication } from '@/composables/useAuthentication'

const {
  jwtToken,           // JWT Token (Ref)
  apiKey,             // API Key (Ref)
  useApiKeyMode,      // 是否使用 API Key 模式 (Ref)
  isAuthenticated,    // 是否已认证 (Computed)
  instanceHeaders,    // 实例请求头 (Ref)
  customHeaders,      // 自定义请求头 (Ref)
  
  // 方法
  refreshJwtToken,    // 刷新 JWT Token
  toggleApiKeyMode,   // 切换 API Key 模式
  setApiKey,          // 设置 API Key
  setInstanceHeaders, // 设置实例请求头
  addCustomHeader,    // 添加自定义请求头
  getAllRequestHeaders // 获取所有请求头
} = useAuthentication()
```

### 3. 使用服务请求

```typescript
import { useServiceRequest } from '@/composables/useServiceRequest'

const {
  loading,        // 加载中状态
  error,          // 错误信息
  response,       // 响应结果
  isStreaming,    // 是否流式接收
  streamData,     // 流式数据累积
  
  // 方法
  sendRequest,        // 发送普通请求
  sendStreamRequest,  // 发送流式请求
  cancelRequest,      // 取消请求
  resetState          // 重置状态
} = useServiceRequest()

// 发送请求
const result = await sendRequest({
  endpoint: '/v1/chat/completions',
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: { model: 'gpt-3.5', messages: [...] }
})

// 发送流式请求
await sendStreamRequest(config, (chunk) => {
  console.log('收到数据块:', chunk)
})
```

### 4. 注册新服务

```typescript
// types/registry.ts
SERVICE_REGISTRY.newService = {
  id: 'newService',
  name: '新服务',
  description: '服务描述',
  icon: 'Document',  // Element Plus 图标名
  endpoint: '/v1/new-service',
  method: 'POST',
  contentType: 'application/json',
  responseType: 'json',
  supportsStreaming: false,
  inputSchema: { /* ... */ },
  outputSchema: { /* ... */ },
  uiComponent: 'NewServiceExperience',
  defaultConfig: { /* ... */ },
  category: 'other'
}
```

## 组件使用

### ServiceSelector

```vue
<template>
  <ServiceSelector
    v-model="selectedInstanceId"
    label="选择实例"
    :instances="availableInstances"
    :loading="instancesLoading"
    @instance-change="handleInstanceChange"
    @refresh="fetchInstances"
  />
</template>
```

### HeaderConfigPanel

```vue
<template>
  <HeaderConfigPanel
    :inherited-headers="instanceHeaders"
    :custom-headers="customHeaders"
    @update:custom-headers="handleCustomHeadersUpdate"
  />
</template>
```

### ModelParamsForm

```vue
<template>
  <ModelParamsForm
    v-model="modelParams"
    :show-temperature="true"
    :show-max-tokens="true"
    :show-stream="true"
    :default-params="{ stream: true, temperature: 0.7 }"
  />
</template>
```

### ResponseDebugPanel

```vue
<template>
  <ResponseDebugPanel
    :request="lastRequest"
    :response="lastResponse"
    :error="errorMessage"
    :request-size="requestSize"
    default-expanded
  />
</template>
```

### MarkdownRenderer

```vue
<template>
  <MarkdownRenderer :content="markdownContent" />
</template>
```

### ChatBubble

```vue
<template>
  <ChatBubble
    :message="{ role: 'assistant', content: 'Hello' }"
    :is-streaming="false"
    @copy="handleCopy"
    @regenerate="handleRegenerate"
  />
</template>
```

### WaveformVisualizer

```vue
<template>
  <WaveformVisualizer
    :audio-url="audioUrl"
    :is-playing="isPlaying"
    :progress="playProgress"
  />
</template>
```

## 认证流程

### JWT Token 模式（默认）

```typescript
// 自动从 localStorage 获取
const token = localStorage.getItem('admin_token')

// 刷新 Token
await refreshJwtToken()

// 获取请求头
const headers = getAllRequestHeaders()
// 输出：{ 'Jairouter_Token': 'xxx...' }
```

### API Key 模式

```typescript
// 切换模式
toggleApiKeyMode(true)

// 设置 API Key
setApiKey('your-api-key')

// 获取请求头
const headers = getAllRequestHeaders()
// 输出：{ 'X-API-Key': 'your-api-key' }
```

### 下游认证头配置

```typescript
// 设置实例请求头（自动继承）
setInstanceHeaders({
  'Authorization': 'Bearer xxx',
  'X-API-Key': 'yyy'
})

// 添加自定义覆盖（优先级更高）
addCustomHeader('Authorization', 'Bearer zzz')

// 获取最终请求头
const headers = getAllRequestHeaders()
// 输出：{
//   'Jairouter_Token': '...',
//   'Authorization': 'Bearer zzz',  // 用户覆盖优先
//   'X-API-Key': 'yyy'
// }
```

## 请求处理

### 普通请求

```typescript
const { sendRequest, loading, response, error } = useServiceRequest()

try {
  const result = await sendRequest({
    endpoint: '/v1/embeddings',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: { model: 'text-embedding', input: 'Hello' }
  })
  
  if (result?.data) {
    console.log('嵌入向量:', result.data.data[0].embedding)
  }
} catch (err) {
  console.error('请求失败:', err)
}
```

### 流式请求

```typescript
const { sendStreamRequest, streamData, isStreaming } = useServiceRequest()

await sendStreamRequest(
  {
    endpoint: '/v1/chat/completions',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: { model: 'gpt-3.5', messages: [...], stream: true }
  },
  (chunk) => {
    // 实时处理每个数据块
    const delta = chunk.choices?.[0]?.delta?.content || ''
    console.log('收到:', delta)
  }
)

// 流式完成后
console.log('完整响应:', streamData.value)
```

### 文件上传请求

```typescript
const { sendRequest } = useServiceRequest()

const result = await sendRequest({
  endpoint: '/v1/audio/transcriptions',
  method: 'POST',
  headers: { 'Jairouter_Token': '...' },
  body: { model: 'whisper', language: 'zh' },
  files: [audioFile]  // File 对象数组
})
```

### 取消请求

```typescript
const { cancelRequest } = useServiceRequest()

// 在组件卸载或用户取消时
onUnmounted(() => {
  cancelRequest()
})
```

## 服务注册表

### 获取服务信息

```typescript
import { SERVICE_REGISTRY, getServiceCapability } from '@/views/playground/types/registry'

// 获取所有服务
const services = Object.values(SERVICE_REGISTRY)

// 获取特定服务
const chatService = getServiceCapability('chat')
console.log(chatService?.endpoint) // '/v1/chat/completions'
```

### 服务类型

| 服务 ID | 名称 | 端点 | 流式支持 |
|--------|------|------|---------|
| chat | 智能对话 | /v1/chat/completions | ✅ |
| embedding | 文本嵌入 | /v1/embeddings | ❌ |
| rerank | 重排序 | /v1/rerank | ❌ |
| tts | 文本转语音 | /v1/audio/speech | ❌ |
| stt | 语音转文本 | /v1/audio/transcriptions | ❌ |
| imageGenerate | 图像生成 | /v1/images/generations | ❌ |
| imageEdit | 图像编辑 | /v1/images/edits | ❌ |

## 键盘快捷键

| 快捷键 | 功能 |
|-------|------|
| Ctrl + Enter | 发送消息/请求 |
| Esc | 取消请求/关闭对话框 |

## 常见问题

### Q: 如何添加新的模型参数？

A: 在 `ModelParamsForm.vue` 中添加新的表单项：

```vue
<el-form-item label="新参数">
  <el-input v-model="modelParams.newParam" />
</el-form-item>
```

### Q: 如何修改请求头优先级？

A: 在 `useAuthentication.ts` 的 `getAllRequestHeaders` 中调整合并顺序。

### Q: 如何禁用流式响应？

A: 设置 `modelParams.value.stream = false`。

### Q: 如何获取实例列表？

A: 调用实例管理 API：

```typescript
const res = await fetch(`${API_BASE_URL}/api/v1/instances?serviceType=chat`, {
  headers: { 'Jairouter_Token': token }
})
const data = await res.json()
availableInstances.value = data.data
```

## 样式定制

### 修改主题色

```css
:root {
  --el-color-primary: #409EFF;
  --el-color-primary-light-3: #66b1ff;
  /* ... */
}
```

### 修改聊天气泡样式

```css
.chat-bubble.role-user .message-body {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.chat-bubble.role-assistant .message-body {
  background-color: #f5f7fa;
}
```

## 性能优化建议

1. **大数据量向量渲染**: 使用虚拟滚动
2. **Markdown 渲染**: 缓存已渲染内容
3. **波形可视化**: 降低采样率
4. **组件懒加载**: 使用 `defineAsyncComponent`

## 测试建议

```typescript
// 单元测试示例
import { describe, it, expect } from 'vitest'
import { useAuthentication } from '@/composables/useAuthentication'

describe('useAuthentication', () => {
  it('should get JWT token from localStorage', () => {
    localStorage.setItem('admin_token', 'test-token')
    const { jwtToken } = useAuthentication()
    expect(jwtToken.value).toBe('test-token')
  })
})
```

---

**最后更新**: 2026-03-19

**版本**: 1.0
