# 路由规则配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-08-23
> **Git 提交**: f8a2eebe
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 提供**规则引擎**（v2.8.5），允许通过 Web 页面或 YAML 配置**条件路由规则**，实现按模型名、请求头、来源 IP 等条件进行灵活的路由控制，无需编写代码。

规则引擎在**每次请求**时求值：请求到达后，按优先级从高到低匹配规则，首条命中的规则生效；无规则命中时走原有路由逻辑，**行为与未启用规则引擎时完全一致**。

## 适用场景

| 场景 | 示例 |
|------|------|
| 灰度发布 | 按来源 IP 将 10% 流量路由到新模型 |
| 租户/渠道隔离 | 按请求头 `x-tenant` 路由到不同实例组 |
| 来源限制 | 内网 IP（CIDR）走专用实例，公网走默认实例 |
| 模型名重写 | 请求 `gpt-4` 实际路由到 `claude-3` |
| 实例锁定 | 指定请求固定使用某个实例 |
| 适配器切换 | 按请求头切换 OpenAI/Ollama 适配器 |
| 权重分流 | 同一请求稳定命中同一规则（基于 IP+模型名哈希） |

## 规则匹配原理

### 条件组合

- **规则内 AND**：一条规则的所有条件都满足才命中
- **规则间 OR**：按 `priority` 降序匹配，**首条命中即终止**

### 规则字段

| 字段 | 说明 |
|------|------|
| `id` | 唯一标识（创建时自动生成 UUID） |
| `name` | 规则名称（必填） |
| `enabled` | 是否启用，默认 `true`，停用的规则完全跳过 |
| `priority` | 优先级，数值越大越先匹配（0-9999） |
| `conditions` | 条件列表（全部满足才命中） |
| `action` | 命中后执行的动作 |

### 条件类型（type）

| 类型 | 说明 | 示例 value |
|------|------|-----------|
| `MODEL_NAME` | 请求模型名 | `gpt-4` |
| `SERVICE_TYPE` | 服务类型（chat/embedding/rerank/tts/stt/imgGen/imgEdit） | `chat` |
| `HEADER` | 请求头（需额外指定 `field` 为 header 名） | `vllm` |
| `CLIENT_IP` | 客户端 IP | `10.0.0.0/8` |
| `WEIGHT` | 权重分流（0-100 百分比） | `50` |

### 操作符（operator）

| 操作符 | 说明 | 适用条件 |
|--------|------|----------|
| `EQUALS` | 等于（忽略大小写） | 全部 |
| `CONTAINS` | 包含 | MODEL_NAME/HEADER/CLIENT_IP |
| `STARTS_WITH` | 前缀匹配 | MODEL_NAME/HEADER/CLIENT_IP |
| `REGEX` | 正则部分匹配（find 语义） | MODEL_NAME/HEADER |
| `CIDR_MATCH` | CIDR 网段匹配，如 `192.168.1.0/24` | 仅 CLIENT_IP |

> **WEIGHT 条件**：基于 `(clientIp + "|" + modelName)` 稳定哈希，`hash % 100 < weight` 即命中。同一（IP, 模型）请求**始终命中同一结果**，适合按比例分流。`weight` 取条件上的 `weight` 字段，缺省读 `value`，再缺省为 50。

### 动作类型（action.type）

| 类型 | 说明 | 目标字段 |
|------|------|----------|
| `TARGET_MODEL` | 重写模型名，按新模型名选择实例 | `modelName` |
| `TARGET_INSTANCE` | 锁定实例（按 instanceId 或 name） | `instanceId` |
| `TARGET_ADAPTER` | 切换适配器（按名取用，未注册时回退实例级适配器） | `adapterName` |
| `LB_STRATEGY` | 覆盖负载均衡策略 | `lbStrategy` |

> **LB_STRATEGY 支持的值**：`random` / `round-robin` / `least-connections` / `ip-hash` / `consistent-hash`，未知策略自动回退原配置。

## Web 页面配置

1. 登录 JAiRouter 管理后台
2. 左侧菜单点击 **配置管理 → 路由规则**

### 新增规则

点击 **「新增规则」**，填写表单：

| 字段 | 说明 |
|------|------|
| **名称** | 规则名称 |
| **优先级** | 0-9999，越大越先 |
| **匹配条件** | 可添加多行：条件类型 → 操作符 → 值；HEADER 类型多一个 header 名输入；WEIGHT 类型用 0-100 数字 |
| **执行动作** | 单选动作类型 + 目标值（按类型提示输入模型名/实例ID/适配器名/LB策略） |

### 管理规则

- **启停**：表格"启用"开关即时生效
- **优先级调整**：编辑规则修改优先级（批量重排通过 API）
- **编辑/删除**：表格操作列

> 规则增删改后**立即热生效**，无需重启应用。

## YAML 配置文件

编辑文件 `src/main/resources/config/router/rules.yml`：

```yaml
model:
  rules:
    - id: route-vllm-header
      name: 按请求头路由到vLLM适配器
      enabled: true
      priority: 100
      conditions:
        - type: HEADER
          field: x-routing
          operator: EQUALS
          value: vllm
      action:
        type: TARGET_ADAPTER
        adapter-name: vllm

    - id: route-internal-ip
      name: 内网IP锁定专用实例
      enabled: true
      priority: 90
      conditions:
        - type: CLIENT_IP
          operator: CIDR_MATCH
          value: 10.0.0.0/8
      action:
        type: TARGET_INSTANCE
        instance-id: internal-gpu-1

    - id: route-model-rewrite
      name: gpt-4请求重写到claude-3
      enabled: true
      priority: 80
      conditions:
        - type: MODEL_NAME
          operator: EQUALS
          value: gpt-4
      action:
        type: TARGET_MODEL
        model-name: claude-3
```

> 默认为空列表 `model.rules: []`，即不启用任何规则。YAML 中的规则与 Web 页面创建的规则合并：**同 id 时 Web 页面（持久化）规则覆盖 YAML**。

## API 接口参考

基路径：`/api/config/rules`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/config/rules/list` | GET | 获取全部规则（按 priority 降序） |
| `/api/config/rules/{id}` | GET | 获取单条规则 |
| `/api/config/rules` | POST | 创建规则（同 id 返回 409） |
| `/api/config/rules/{id}` | PUT | 更新规则 |
| `/api/config/rules/{id}` | DELETE | 删除规则 |
| `/api/config/rules/{id}/enable` | PUT | 启用规则 |
| `/api/config/rules/{id}/disable` | PUT | 停用规则 |
| `/api/config/rules/priority` | PUT | 批量调整优先级 `[{id, priority}]` |

### 创建规则示例

```bash
curl -X POST http://localhost:8080/api/config/rules \
  -H "Content-Type: application/json" \
  -H "Jairouter_Token: your-admin-token" \
  -d '{
    "name": "按请求头路由到vLLM",
    "priority": 100,
    "enabled": true,
    "conditions": [
      {"type": "HEADER", "field": "x-routing", "operator": "EQUALS", "value": "vllm"}
    ],
    "action": {"type": "TARGET_ADAPTER", "adapterName": "vllm"}
  }'
```

### 批量调整优先级示例

```bash
curl -X PUT http://localhost:8080/api/config/rules/priority \
  -H "Content-Type: application/json" \
  -H "Jairouter_Token: your-admin-token" \
  -d '[{"id": "rule-id-1", "priority": 200}, {"id": "rule-id-2", "priority": 100}]'
```

## 验证规则

1. 通过 **AI 试验场 → 对话测试** 发送请求验证路由效果
2. 观察后端日志中的 `Selected adapter` / 路由选择信息确认命中规则
3. 或直接调用 `/v1/*` API 带/不带条件请求头对比路由结果

## 注意事项

1. **热生效**：规则增删改后立即生效，无需重启
2. **持久化**：Web 页面创建的规则存储在 StoreManager（key=`rule_definitions`），重启后自动恢复
3. **优先级**：同优先级时先添加的规则优先；规则间是"首条命中"语义，注意避免规则互相覆盖
4. **性能**：规则数量建议控制在 100 条以内，每请求求值开销可忽略
5. **TARGET_ADAPTER**：指定的适配器未注册时，会告警并回退到实例级适配器，不影响请求
6. **HEADER 条件**：仅对 `/v1/*` API 请求链路生效（请求头用于规则匹配，不影响出站转发）
