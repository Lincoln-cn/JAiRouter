# JAiRouter Playground 重构项目文档

## 项目概述

**项目名称**: JAiRouter Playground 互动页面重构

**开发日期**: 2026 年 3 月 19 日

**项目目标**: 将原有的配置测试型 Playground 页面改造为互动体验型页面，提供类似 ChatGPT 官网的友好交互体验。

---

## 一、需求分析

### 1.1 原有问题分析

1. **交互割裂**: 左侧配置表单 + 右侧结果展示的分离布局
2. **缺少即时互动**: 每次测试都需要手动配置完整参数
3. **视觉反馈弱**: 结果以原始 JSON 展示，不够直观
4. **学习成本高**: 新用户需要理解各种配置项才能使用

### 1.2 新页面设计目标

1. **聊天**: 对话式界面，消息气泡展示，支持多轮对话
2. **嵌入**: 输入文本后立即展示向量可视化
3. **图像编辑**: 上传图片后直接编辑预览
4. **TTS/STT**: 录音/上传音频后直接播放，波形展示

### 1.3 认证系统要求

- 兼容现有 JWT Token + API Key 双认证体系
- 支持下游服务认证头配置（实例配置 + 用户覆盖）

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    PlaygroundMain.vue                       │
│  (统一容器：路由管理、状态提供、响应式布局)                  │
├─────────────────────────────────────────────────────────────┤
│                    Experience Layer                         │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │   Chat   │Embedding │  Image   │  Audio   │  More... │  │
│  │Experience│Experience│Experience│Experience│ (可扩展) │  │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    Component Layer                          │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐ │
│  │  Universal  │  Service-   │ Visualization│  Debugging  │ │
│  │ Components  │  Specific   │  Components │  Components │ │
│  └─────────────┴─────────────┴─────────────┴─────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                    Composables Layer                        │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐ │
│  │useAuthen-   │useService   │useVisuali-  │usePlayground│ │
│  │tication     │Request      │zation       │Cache        │ │
│  └─────────────┴─────────────┴─────────────┴─────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
frontend/src/views/playground/
├── types/
│   ├── registry.ts              # 服务类型注册定义（核心）
│   └── playground.ts            # 原有类型定义（保留兼容）
├── composables/
│   ├── useAuthentication.ts     # 认证管理
│   └── useServiceRequest.ts     # 请求处理
├── components/
│   ├── universal/               # 通用组件
│   │   ├── ServiceSelector.vue
│   │   ├── HeaderConfigPanel.vue
│   │   ├── ModelParamsForm.vue
│   │   └── ResponseDebugPanel.vue
│   └── visualization/           # 可视化组件
│       ├── MarkdownRenderer.vue
│       ├── ChatBubble.vue
│       └── WaveformVisualizer.vue
├── experience/                  # 体验页面组件
│   ├── ChatExperience.vue
│   ├── EmbeddingExperience.vue
│   ├── ImageExperience.vue
│   ├── AudioExperience.vue
│   │   ├── TTSTab.vue
│   │   └── STTTab.vue
│   └── RerankExperience.vue
├── PlaygroundMain.vue           # 主容器（重构）
└── README.md                    # 使用文档
```

### 2.3 服务注册表设计

```typescript
// types/registry.ts
export interface ServiceCapability {
  id: string                          // 唯一标识
  name: string                        // 显示名称
  description: string                 // 功能描述
  icon: string                        // 图标名称
  endpoint: string                    // API 端点
  method: 'GET' | 'POST' | 'PUT'      // HTTP 方法
  contentType: string                 // 内容类型
  responseType: 'json' | 'blob' | 'text' | 'stream'
  supportsStreaming: boolean          // 是否支持流式响应
  inputSchema: InputSchema            // 输入参数 Schema
  outputSchema: OutputSchema          // 输出参数 Schema
  uiComponent: string                 // 对应的 UI 组件名
  defaultConfig: Record<string, any>  // 默认配置
  category?: 'conversation' | 'vision' | 'audio' | 'embedding' | 'other'
}

export const SERVICE_REGISTRY: Record<string, ServiceCapability> = {
  chat: { /* ... */ },
  embedding: { /* ... */ },
  // 扩展新服务只需在此添加
}
```

---

## 三、核心功能实现

### 3.1 认证管理 Composable

**文件**: `frontend/src/composables/useAuthentication.ts`

**功能**:
- JWT Token 自动获取（从 localStorage）
- API Key 临时切换
- 实例配置请求头继承
- 用户自定义请求头覆盖

**核心代码**:
```typescript
export function useAuthentication(): UseAuthenticationReturn {
  const jwtToken = ref<string | null>(localStorage.getItem('admin_token'))
  const apiKey = ref<string | null>(null)
  const useApiKeyMode = ref(false)
  
  const instanceHeaders = ref<AuthHeader[]>([])
  const customHeaders = ref<AuthHeader[]>([])
  
  // 获取最终请求头 = 系统认证头 + 实例头 + 自定义头
  const getAllRequestHeaders = (): Record<string, string> => {
    return {
      ...getAuthHeaders(),
      ...getDownstreamHeaders()
    }
  }
  
  return {
    jwtToken, apiKey, useApiKeyMode, isAuthenticated,
    instanceHeaders, customHeaders, allHeaders,
    refreshJwtToken, toggleApiKeyMode, setApiKey,
    setInstanceHeaders, addCustomHeader, removeCustomHeader,
    getAuthHeaders, getDownstreamHeaders, getAllRequestHeaders
  }
}
```

### 3.2 服务请求 Composable

**文件**: `frontend/src/composables/useServiceRequest.ts`

**功能**:
- 统一的请求发送逻辑
- 支持流式 SSE 响应
- 支持文件上传
- 支持请求取消

**核心代码**:
```typescript
export function useServiceRequest(): UseServiceRequestReturn {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const response = ref<ServiceResponse | null>(null)
  const isStreaming = ref(false)
  
  const sendRequest = async (config: ServiceRequestConfig) => { /* ... */ }
  const sendStreamRequest = async (config, onChunk) => { /* ... */ }
  const cancelRequest = () => { abortController?.abort() }
  
  return { loading, error, response, isStreaming, streamData, sendRequest, cancelRequest }
}
```

### 3.3 通用组件

#### ServiceSelector.vue
- 服务实例选择器
- 带请求头预览（脱敏显示）
- 支持刷新实例列表

#### HeaderConfigPanel.vue
- 可折叠的请求头配置面板
- 显示继承自实例的请求头
- 支持添加自定义覆盖

#### ModelParamsForm.vue
- 模型参数配置表单
- 支持 Temperature、Max Tokens 等参数
- 滑块 + 数字输入组合

#### ResponseDebugPanel.vue
- 请求/响应详细信息查看
- 支持 JSON 格式化
- 支持复制和下载

### 3.4 可视化组件

#### MarkdownRenderer.vue
- 使用 markdown-it 渲染 Markdown
- 使用 highlight.js 代码高亮
- 支持表格、引用、任务列表等

#### ChatBubble.vue
- 对话气泡组件
- 支持用户/AI/系统三种角色
- 流式响应光标动画
- 复制和重新生成操作

#### WaveformVisualizer.vue
- 使用 Web Audio API
- 静态波形绘制
- 实时频谱动画
- 播放进度显示

---

## 四、体验页面实现

### 4.1 聊天体验 (ChatExperience.vue)

**功能**:
- 对话式界面，消息列表 + 输入框布局
- 流式响应打字机效果
- Markdown 渲染和代码高亮
- 多轮对话历史维护
- 快捷提示词
- 键盘快捷键（Ctrl+Enter 发送）

**关键实现**:
```typescript
const sendMessage = async () => {
  // 添加用户消息
  messages.value.push({ role: 'user', content: inputText.value })
  // 添加 AI 消息占位
  messages.value.push({ role: 'assistant', content: '' })
  
  if (modelParams.value.stream) {
    await sendStreamRequest(config, (chunk) => {
      // 更新 AI 消息内容
      messages.value[lastIndex].content += chunk.choices?.[0]?.delta?.content || ''
    })
  }
}
```

### 4.2 文本嵌入体验 (EmbeddingExperience.vue)

**功能**:
- 文本输入
- 向量生成
- 向量条形图可视化（前 50 维）
- 完整向量查看/复制

**可视化实现**:
```vue
<div class="vector-bars">
  <div v-for="(value, index) in embedding.preview" :key="index" class="vector-bar">
    <span class="bar-index">{{ index }}</span>
    <div class="bar-fill" :class="value >= 0 ? 'positive' : 'negative'"
         :style="{ width: Math.abs(value) * 100 + '%' }" />
    <span class="bar-value">{{ value.toFixed(4) }}</span>
  </div>
</div>
```

### 4.3 音频体验 (AudioExperience.vue)

**TTS 功能**:
- 文本输入
- 语音选择（6 种语音）
- 语速调节
- 波形可视化
- 播放控制

**STT 功能**:
- 文件上传/拖拽
- 多语言支持
- 多格式输出（JSON/Text/SRT/VTT）
- 转写文本编辑

### 4.4 图像体验 (ImageExperience.vue)

**功能**:
- 图像生成（文生图）
- 提示词输入
- 尺寸和质量选择
- 结果画廊展示
- 图像下载

### 4.5 重排序体验 (RerankExperience.vue)

**功能**:
- 查询文本输入
- 文档列表输入
- 相关性排序
- 排序结果展示

---

## 五、认证系统设计

### 5.1 系统认证（前端 → JAiRouter）

| 认证方式 | 请求头 | 说明 |
|---------|-------|------|
| JWT Token | `Jairouter_Token` | 自动使用当前登录态 |
| API Key | `X-API-Key` | 临时切换使用 |

### 5.2 下游认证（JAiRouter → 模型服务）

| 配置来源 | 优先级 | 说明 |
|---------|-------|------|
| 实例配置 | 低 | 在实例管理中配置 |
| 用户覆盖 | 高 | 在 Playground 中临时添加 |

### 5.3 请求头合并逻辑

```typescript
function buildRequestHeaders(instance, customOverride) {
  const headers = {}
  
  // 1. 系统认证头
  if (useApiKeyMode && apiKey) {
    headers['X-API-Key'] = apiKey
  } else if (jwtToken) {
    headers['Jairouter_Token'] = jwtToken
  }
  
  // 2. 实例配置的 headers
  Object.assign(headers, instance.headers || {})
  
  // 3. 用户覆盖的 headers（优先级最高）
  Object.assign(headers, customOverride)
  
  return headers
}
```

---

## 六、技术栈

| 技术 | 版本 | 用途 |
|-----|------|------|
| Vue 3 | 3.5.13 | 前端框架 |
| TypeScript | 5.7.3 | 类型系统 |
| Element Plus | 2.9.7 | UI 组件库 |
| markdown-it | latest | Markdown 渲染 |
| highlight.js | latest | 代码高亮 |
| Web Audio API | native | 音频可视化 |

---

## 七、开发过程

### 7.1 第一阶段：基础架构（2 天）

**任务**:
1. 创建服务注册表类型定义
2. 实现 useAuthentication composable
3. 实现 useServiceRequest composable
4. 搭建 PlaygroundMain 主容器框架

**产出**:
- `types/registry.ts`
- `composables/useAuthentication.ts`
- `composables/useServiceRequest.ts`
- `PlaygroundMain.vue`

### 7.2 第二阶段：通用组件（1 天）

**任务**:
1. ServiceSelector 组件
2. HeaderConfigPanel 组件
3. ModelParamsForm 组件
4. ResponseDebugPanel 组件

**产出**:
- `components/universal/*.vue` (4 个组件)

### 7.3 第三阶段：可视化组件（1 天）

**任务**:
1. MarkdownRenderer 组件
2. ChatBubble 组件
3. WaveformVisualizer 组件

**产出**:
- `components/visualization/*.vue` (3 个组件)

### 7.4 第四阶段：体验页面（3 天）

**任务**:
1. ChatExperience 聊天体验
2. AudioExperience 音频体验
3. EmbeddingExperience 嵌入体验
4. ImageExperience 图像体验
5. RerankExperience 重排序体验

**产出**:
- `experience/*.vue` (5 个体验页 + 2 个子组件)

### 7.5 第五阶段：测试优化（1 天）

**任务**:
1. 安装依赖（markdown-it, highlight.js）
2. TypeScript 类型检查
3. 前端构建测试
4. 修复所有类型错误

**结果**:
- 类型检查通过 ✅
- 构建成功 ✅
- 耗时 40.25 秒

---

## 八、文件清单

### 新建文件（20 个）

| 文件路径 | 说明 |
|---------|------|
| `types/registry.ts` | 服务类型注册定义 |
| `composables/useAuthentication.ts` | 认证管理 composable |
| `composables/useServiceRequest.ts` | 请求处理 composable |
| `components/universal/ServiceSelector.vue` | 实例选择器 |
| `components/universal/HeaderConfigPanel.vue` | 请求头面板 |
| `components/universal/ModelParamsForm.vue` | 参数表单 |
| `components/universal/ResponseDebugPanel.vue` | 调试面板 |
| `components/visualization/MarkdownRenderer.vue` | Markdown 渲染 |
| `components/visualization/ChatBubble.vue` | 聊天气泡 |
| `components/visualization/WaveformVisualizer.vue` | 波形可视化 |
| `experience/ChatExperience.vue` | 聊天体验页 |
| `experience/EmbeddingExperience.vue` | 嵌入体验页 |
| `experience/ImageExperience.vue` | 图像体验页 |
| `experience/AudioExperience.vue` | 音频体验页 |
| `experience/RerankExperience.vue` | 重排序体验页 |
| `experience/audio/TTSTab.vue` | TTS 选项卡 |
| `experience/audio/STTTab.vue` | STT 选项卡 |
| `experience/index.ts` | 体验组件导出 |
| `components/universal/index.ts` | 通用组件导出 |
| `components/visualization/index.ts` | 可视化组件导出 |

### 修改文件（1 个）

| 文件路径 | 说明 |
|---------|------|
| `PlaygroundMain.vue` | 重构为互动体验主容器 |

### 备份文件（1 个）

| 文件路径 | 说明 |
|---------|------|
| `PlaygroundMain.legacy.vue` | 原有配置测试版备份 |

### 文档文件（2 个）

| 文件路径 | 说明 |
|---------|------|
| `README.md` | 用户使用文档 |
| `DEVELOPMENT.md` | 本文档 |

---

## 九、构建产物

### 构建统计

```
✓ built in 40.25s

主要文件:
- ChatExperience.js         978.94 kB (包含 Markdown 渲染)
- AudioExperience.js         18.55 kB
- EmbeddingExperience.js      5.54 kB
- ImageExperience.js          4.06 kB
- RerankExperience.js         3.76 kB
- PlaygroundMain.js          23.95 kB
- WaveformVisualizer.js     106.92 kB
```

### 依赖包

```json
{
  "dependencies": {
    "markdown-it": "^14.x",
    "highlight.js": "^11.x"
  },
  "devDependencies": {
    "@types/markdown-it": "^14.x"
  }
}
```

---

## 十、扩展指南

### 10.1 添加新服务类型

**步骤 1**: 在 `types/registry.ts` 中注册服务能力

```typescript
SERVICE_REGISTRY.videoProcess = {
  id: 'videoProcess',
  name: '视频处理',
  description: '视频分析、转码、生成',
  icon: 'VideoCamera',
  endpoint: '/v1/video/process',
  // ... 其他配置
  uiComponent: 'VideoExperience'
}
```

**步骤 2**: 创建体验组件 `experience/VideoExperience.vue`

**步骤 3**: 在 `PlaygroundMain.vue` 中注册组件

```typescript
const VideoExperience = defineAsyncComponent(
  () => import('./experience/VideoExperience.vue')
)
```

### 10.2 添加新的可视化组件

1. 在 `components/visualization/` 创建组件
2. 在 `visualization/index.ts` 导出
3. 在体验页面中引入使用

### 10.3 修改认证逻辑

1. 编辑 `composables/useAuthentication.ts`
2. 更新 `UseAuthenticationReturn` 接口
3. 修改使用认证的组件

---

## 十一、已知问题与后续计划

### 11.1 已完成功能

- ✅ 聊天体验（流式响应、Markdown 渲染）
- ✅ 文本嵌入（向量可视化）
- ✅ 音频处理（TTS/STT、波形可视化）
- ✅ 图像生成
- ✅ 重排序
- ✅ 认证系统（JWT/API Key 切换）
- ✅ 请求头管理（继承 + 覆盖）

### 11.2 待实现功能

- [ ] 图像编辑（Canvas 画布、遮罩绘制）
- [ ] 向量 2D/3D 散点图（ECharts）
- [ ] 视频处理
- [ ] 文档分析
- [ ] 代码生成
- [ ] 配置保存
- [ ] 历史记录

### 11.3 优化建议

1. **性能优化**: 大数据量向量渲染优化
2. **用户体验**: 添加加载骨架屏
3. **国际化**: 支持多语言切换
4. **主题**: 支持深色模式
5. **测试**: 添加单元测试和 E2E 测试

---

## 十二、团队成员

| 角色 | 职责 |
|-----|------|
| 前端开发 | Playground 页面重构、组件开发 |
| 后端支持 | API 接口支持、认证系统 |
| 产品设计 | 用户体验设计、交互流程 |
| 测试 | 功能测试、兼容性测试 |

---

## 十三、参考资料

- [Vue 3 官方文档](https://vuejs.org/)
- [Element Plus 组件库](https://element-plus.org/)
- [markdown-it](https://markdown-it.github.io/)
- [highlight.js](https://highlightjs.org/)
- [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)

---

**文档版本**: 1.0

**最后更新**: 2026-03-19

**维护者**: JAiRouter 开发团队
