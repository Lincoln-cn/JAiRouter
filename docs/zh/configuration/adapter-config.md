# Adapter配置指南

## 概述

JAiRouter 支持通过配置驱动的方式添加新的 adapter，无需编写代码。适用于所有 OpenAI 兼容的 API（如 DeepSeek、智谱、月之暗面等）。

## 适用场景

| 提供商 | API格式 | 是否适用 |
|--------|---------|----------|
| DeepSeek | OpenAI兼容 | ✅ |
| 智谱 (Zhipu/GLM) | OpenAI兼容 | ✅ |
| 月之暗面 (Moonshot) | OpenAI兼容 | ✅ |
| 百川 (Baichuan) | OpenAI兼容 | ✅ |
| 通义千问 (Qwen) | OpenAI兼容 | ✅ |
| Minimax | OpenAI兼容 | ✅ |
| Anthropic Claude | Claude格式 | ❌ 需使用内置 `claude` adapter |
| Google Gemini | Gemini格式 | ❌ 需使用内置 `gemini` adapter |
| Ollama | Ollama格式 | ❌ 需使用内置 `ollama` adapter |

---

## 方式一：Web页面配置（推荐）

### 步骤1：进入Adapter管理页面

1. 登录 JAiRouter 管理后台
2. 左侧菜单点击 **配置管理 → Adapter管理**

### 步骤2：新增Adapter

点击右上角 **「新增Adapter」** 按钮，填写表单：

| 字段 | 示例值 | 说明 |
|------|--------|------|
| **名称** | `deepseek` | 唯一标识，后续在实例配置中引用 |
| **类型** | `OpenAI兼容` | 大多数云端API选择此项 |
| **能力配置** | 勾选 `Chat`、`Embedding`、`流式` | 根据API支持的能力选择 |
| **Header名称** | `Authorization` | 默认值，无需修改 |
| **Header前缀** | `Bearer ` | 默认值，注意后面有空格 |

点击 **「保存」** 完成创建。

### 步骤3：在实例中使用

进入 **配置管理 → 实例管理**，添加或编辑实例：

- **适配器** 下拉框中选择刚创建的 adapter（如 `deepseek`）
- **基础URL** 填写 API 地址（如 `https://api.deepseek.com`）
- **自定义头** 添加认证信息：`Authorization: Bearer sk-你的密钥`

### 流程示意

```
┌─────────────────────┐     ┌─────────────────────┐
│  1. Adapter管理页面  │ ──→ │  2. 新增 adapter    │
│     配置 adapter     │     │     如 deepseek     │
└─────────────────────┘     └──────────┬──────────┘
                                       │
                                       ▼
┌─────────────────────┐     ┌─────────────────────┐
│  3. 实例管理页面     │ ──→ │  4. 选择 adapter    │
│     配置实例         │     │    填写URL和密钥    │
└─────────────────────┘     └─────────────────────┘
```

---

## 方式二：YAML配置文件

### 步骤1：编辑 adapter.yml

编辑文件 `src/main/resources/config/router/adapter.yml`：

```yaml
model:
  adapter: gpustack  # 全局默认adapter

adapter-definitions:
  deepseek:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### 步骤2：在 services.yml 中使用

编辑文件 `src/main/resources/config/router/services.yml`：

```yaml
model:
  services:
    chat:
      adapter: deepseek  # 使用刚定义的deepseek adapter
      instances:
        - name: deepseek-chat
          baseUrl: https://api.deepseek.com
          path: /v1/chat/completions
          headers:
            Authorization: "Bearer sk-your-api-key-here"
          weight: 1
          status: active
```

### 步骤3：重启应用

```bash
mvn spring-boot:run -Pfast
```

---

## 常用Adapter配置示例

### DeepSeek

```yaml
adapter-definitions:
  deepseek:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### 智谱GLM（Zhipu）

```yaml
adapter-definitions:
  zhipu:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### 月之暗面（Moonshot）

```yaml
adapter-definitions:
  moonshot:
    type: openai-compatible
    capabilities:
      chat: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### 自定义认证头的API

```yaml
adapter-definitions:
  custom-api:
    type: openai-compatible
    capabilities:
      chat: true
      streaming: true
    auth:
      header-name: X-API-Key    # 自定义认证头名称
      header-prefix: ""          # 无前缀
```

---

## 验证配置

### 通过Web页面验证

1. 进入 **AI试验场 → 对话测试**
2. 选择使用了新adapter的服务和模型
3. 发送测试消息

### 通过API验证

```bash
curl -X POST http://localhost:9900/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

---

## API接口参考

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/config/adapter/list` | GET | 获取所有adapter列表 |
| `/api/config/adapter/{name}` | GET | 获取adapter详情 |
| `/api/config/adapter` | POST | 创建adapter |
| `/api/config/adapter/{name}` | PUT | 更新adapter |
| `/api/config/adapter/{name}` | DELETE | 删除adapter |

### 创建Adapter API示例

```bash
curl -X POST http://localhost:9900/api/config/adapter \
  -H "Content-Type: application/json" \
  -d '{
    "name": "deepseek",
    "type": "openai-compatible",
    "capabilities": {
      "chat": true,
      "embedding": true,
      "streaming": true
    },
    "auth": {
      "headerName": "Authorization",
      "headerPrefix": "Bearer "
    }
  }'
```

---

## 注意事项

1. **adapter名称**：只能包含字母、数字、下划线和横线
2. **内置adapter不可删除**：`normal`、`claude`、`gemini`、`ollama` 等是内置的
3. **配置驱动adapter可随时修改**：通过Web页面或API修改后立即生效
4. **认证方式**：大多数云端API使用 `Authorization: Bearer {key}`，无需修改默认配置
