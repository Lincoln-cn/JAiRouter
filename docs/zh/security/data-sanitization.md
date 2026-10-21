# 数据脱敏规则配置文档

<!-- 版本信息 -->
> **文档版本**: 1.0.3  
> **最后更新**: 2026-09-15  
> **Git 提交**: 61384b4a  
> **作者**: 
<!-- /版本信息 -->



## 概述

JAiRouter 的数据脱敏功能可以自动识别和处理请求和响应中的敏感信息，包括个人身份信息（PII）、敏感词汇等。

### v3.2.0 起：记录侧主路径与控制台管理

**主战场**是「聊天调用历史 / 日志 / 追踪记录」中的用户数据，经记录侧
`SanitizationService.sanitizeForStorage` 处理；该路径**不受**网关
`request/response.enabled` 开关拖累（配置中的 PII/敏感词始终加载进规则库）。

| 能力 | 说明 |
|------|------|
| 控制台 | 安全 → **PII 脱敏管理**（`/security/sanitization`） |
| 权限 | `security:sanitization:manage`（ADMIN） |
| 管理 API | `GET/PUT /api/config/sanitization`、`GET .../rules`、`POST .../test` |
| 网关响应过滤器 | 默认关闭；`/api/**` 管理面与运维端点排除；`/v1/**` 的**非流式 JSON** 自 #560 起脱敏，SSE 仍不脱敏 |
| 试脱敏 | 样例非 JSON 时按 `text/plain` 掩码，便于粘贴聊天原文 |

**调用历史**：`record-level=SUMMARY` 时，适配器层用 `sanitizeForStorage` 落库前脱敏。

管理 API 详见 [管理 API](../api-reference/management-api.md) 与控制台手册
[web-console.md](../getting-started/web-console.md)。

## 功能特性

- **双向脱敏**：支持请求和响应数据脱敏
- **多种脱敏策略**：支持掩码、替换、删除、哈希等策略
- **正则表达式支持**：支持复杂的模式匹配
- **白名单机制**：支持用户和 IP 白名单
- **性能优化**：支持并行处理和缓存优化
- **服务差异化**：不同 AI 服务可以使用不同的脱敏规则

## 快速开始

### 1. 启用数据脱敏

```yaml
jairouter:
  security:
    enabled: true
    sanitization:
      request:
        enabled: true
      response:
        enabled: true
```

### 2. 基础配置

```yaml
jairouter:
  security:
    sanitization:
      request:
        enabled: true
        sensitive-words:
          - "password"
          - "secret"
        pii-patterns:
          - "(?<!\\d)1[3-9]\\d{9}(?!\\d)"                       # 手机号（数字边界，避免从长数字串中间截取）
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"  # 邮箱
        masking-char: "*"
```

### 3. 测试脱敏效果

发送包含敏感信息的请求：

```
curl -H "X-API-Key: your-api-key" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [{"role": "user", "content": "我的手机号是13812345678，邮箱是user@example.com"}]}' \
     http://localhost:8080/v1/chat/completions
```

实际发送到 AI 模型的内容将被脱敏为：
```
我的手机号是***********，邮箱是*******************
```

## 详细配置

> ⚠️ **本节的示例配置并非全部可用键**。当前实现只绑定这些键：`enabled`、`sensitive-words`、`pii-patterns`、`masking-char`、`whitelist-users`（仅请求侧）、`log-sanitization`、`fail-on-error`、`preserve-json-structure`（仅响应侧）、`scope`（仅响应侧）。
> 举例里出现的 `strategies`（`keep-prefix-suffix` / `keep-domain` 等）、`rule-priorities`、`whitelist-ips`、`service-specific-rules` **尚未实现**：写进 YAML 不会报错，但也不会生效。
> 实际可用的替换策略是规则上的 `strategy` 字段（`MASK` / `REPLACE` / `REMOVE` / `HASH`），默认规则统一用 `MASK`（整段等长替换，例如 11 位手机号 → `***********`）。

### 请求数据脱敏配置

```
jairouter:
  security:
    sanitization:
      request:
        # 启用请求脱敏
        enabled: true
        
        # 敏感词列表
        sensitive-words:
          - "password"      # 密码
          - "secret"        # 密钥
          - "token"         # 令牌
          - "credential"    # 凭证
          - "private"       # 私有信息
        
        # PII 正则表达式模式（列表顺序 = 执行顺序：先长后短，长号码在前，
        # 否则长串的前 11 位会被手机号规则先吃掉，尾部明文泄漏 —— 见 issue #561）
        pii-patterns:
          - "(?<!\\d)\\d{17}[\\dXx](?!\\d)"                             # 身份证（18 位，末位可为 X）
          - "(?<!\\d)(?:\\d{16}|\\d{19})(?!\\d)"                        # 银行卡（无分隔符 16/19 位）
          - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"       # 银行卡（4 位一组，空格/横线分隔）
          - "(?<!\\d)1[3-9]\\d{9}(?!\\d)"                               # 手机号（11 位，1[3-9] 开头）
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"           # 邮箱地址
        
        # 脱敏字符
        masking-char: "*"
        
        # 脱敏策略
        strategies:
          phone: "keep-prefix-suffix"    # 保留前缀和后缀
          email: "keep-domain"           # 保留域名
          id-card: "keep-prefix-suffix"  # 保留前缀和后缀
          default: "full-mask"           # 完全脱敏
        
        # 记录脱敏日志
        log-sanitization: true
        
        # 白名单用户
        whitelist-users:
          - "admin-key-001"
        
        # 白名单 IP
        whitelist-ips:
          - "127.0.0.1"
          - "192.168.0.0/16"
        
        # 规则优先级
        rule-priorities:
          phone: 1
          email: 2
          id-card: 3
          bank-card: 4
          sensitive-word: 5
```

### 响应数据脱敏配置

```
jairouter:
  security:
    sanitization:
      response:
        # 启用响应脱敏
        enabled: true
        
        # 响应敏感词
        sensitive-words:
          - "internal"      # 内部信息
          - "debug"         # 调试信息
          - "error"         # 错误详情
          - "exception"     # 异常信息
        
        # 响应 PII 模式（顺序 = 执行顺序，先长后短）
        pii-patterns:
          - "(?<!\\d)\\d{17}[\\dXx](?!\\d)"
          - "(?<!\\d)(?:\\d{16}|\\d{19})(?!\\d)"
          - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
          - "(?<!\\d)1[3-9]\\d{9}(?!\\d)"
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        
        # 保持 JSON 结构
        preserve-json-structure: true
        
        # 生效范围（只作用于 HTTP 响应脱敏过滤器）
        # mode=exclude（默认）时 patterns 是「不脱敏」的例外，留空则用内置排除集合；
        # mode=include 时只有命中 patterns 的路径才脱敏，留空 = 一条都不脱敏（会打 WARN）。
        scope:
          mode: exclude
          patterns:
            - "/actuator"
            - "/swagger-ui"
```

### 响应脱敏生效范围（scope）

响应脱敏只处理 `application/json`，并且会按路径过滤。默认排除集合覆盖管理台、Actuator、Swagger、静态资源等；`/v1` 的非流式 JSON **在生效范围内**，`text/event-stream`（SSE）按内容类型跳过。

```yaml
jairouter:
  security:
    sanitization:
      response:
        scope:
          mode: exclude       # exclude（默认）| include
          patterns: []        # 留空 = 用内置排除集合
```

- `exclude`：`patterns` 里的路径跳过脱敏，其余全脱敏；`patterns` 为空时用内置集合（与未配置 `scope` 完全等价）。
- `include`：只有命中 `patterns` 的路径脱敏；**`patterns` 为空时一条都不脱敏**，启动日志会 WARN。
- 匹配规则：不含通配符按前缀匹配（`/actuator` 命中 `/actuator/health`）；含 `*` / `**` 按 Ant 风格匹配，`/v1/**` 同时命中 `/v1` 本身。
- 该配置**目前只读**：控制台「PII 脱敏管理 → 响应脱敏生效范围」卡片会回显当前模式、来源（内置默认 / 配置指定）与规则列表，并提供路径预览（`POST /api/config/sanitization/scope/preview`）；修改需改配置文件并重启。
- 记录侧脱敏（调用历史 / 日志 / 追踪）不受该范围影响。

## 脱敏策略详解

### 1. 完全脱敏（full-mask）

将整个匹配内容替换为脱敏字符：

```
原文：password123
结果：***********
```

### 2. 保留前缀后缀（keep-prefix-suffix）

保留开头和结尾的部分字符：

```
原文：13812345678
结果：138****5678

原文：user@example.com
结果：u***@example.com
```

### 3. 保留域名（keep-domain）

仅用于邮箱地址，保留域名部分：

```
原文：username@company.com
结果：u*******@company.com
```

### 4. 哈希脱敏（hash）

使用哈希函数替换敏感信息：

```
原文：13812345678
结果：[HASH:a1b2c3d4]
```

### 5. 替换脱敏（replace）

使用预定义的替换文本：

```
原文：password123
结果：[REDACTED]
```

### 6. 删除脱敏（remove）

完全删除匹配的内容：

```
原文：我的密码是password123，请保密
结果：我的密码是，请保密
```

## 正则表达式模式

### 常用 PII 模式

#### 中国手机号

```
pii-patterns:
  - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
```

#### 中国身份证号

```
pii-patterns:
  - "\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b"
```

#### 邮箱地址

```
pii-patterns:
  - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
```

#### 银行卡号

```
pii-patterns:
  - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
```

#### IP 地址

```
pii-patterns:
  - "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
```

#### URL 地址

```
pii-patterns:
  - "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?"
```

### 自定义模式示例

#### 员工工号

```
pii-patterns:
  - "\\b[A-Z]{2}\\d{6}\\b"  # 如：AB123456
```

#### 订单号

```
pii-patterns:
  - "\\bORD\\d{10}\\b"  # 如：ORD1234567890
```

#### 车牌号

```
pii-patterns:
  - "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{4}[A-Z0-9挂学警港澳]"
```

## 白名单配置

### 用户白名单

指定的用户可以跳过脱敏处理：

```
jairouter:
  security:
    sanitization:
      request:
        whitelist-users:
          - "admin-key-001"      # API Key ID
          - "system-service"     # 系统服务
          - "data-analyst"       # 数据分析师
```

### IP 白名单

指定的 IP 地址可以跳过脱敏处理：

```
jairouter:
  security:
    sanitization:
      request:
        whitelist-ips:
          - "127.0.0.1"          # 本地回环
          - "::1"                # IPv6 本地回环
          - "192.168.0.0/16"     # 内网 IP 段
          - "10.0.0.0/8"         # 内网 IP 段
          - "172.16.0.0/12"      # 内网 IP 段
```

### 动态白名单

支持运行时动态添加白名单：

```
# 添加用户到白名单
curl -X POST http://localhost:8080/admin/security/whitelist/users \
     -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"user_id": "new-user", "reason": "临时数据分析需求"}'

# 添加 IP 到白名单
curl -X POST http://localhost:8080/admin/security/whitelist/ips \
     -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"ip": "203.0.113.1", "reason": "合作伙伴访问"}'
```

## 服务差异化配置

不同的 AI 服务可以使用不同的脱敏规则：

```
jairouter:
  security:
    sanitization:
      response:
        service-specific-rules:
          # 聊天服务
          chat:
            enabled: true
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
              - "\\b(?:支付宝|Alipay)[:：]?\\s*[\\w\\d]+\\b"
            strategies:
              social-media: "keep-prefix-suffix"
          
          # 向量服务（通常不需要脱敏）
          embedding:
            enabled: false
          
          # 重排序服务
          rerank:
            enabled: true
            preserve-ranking-scores: true
            additional-patterns:
              - "\\b(?:评分|得分|分数)[:：]?\\s*\\d+(\\.\\d+)?\\b"
          
          # 语音合成服务
          tts:
            enabled: true
            additional-patterns:
              - "\\b(?:声音|语音|音色)[:：]?\\s*[\\w\\d]+\\b"
          
          # 语音识别服务
          stt:
            enabled: true
            preserve-timestamps: true
          
          # 图像生成服务
          image-generation:
            enabled: true
            additional-patterns:
              - "\\b(?:风格|style)[:：]?\\s*[\\w\\d]+\\b"
```

## 性能影响与容量评估

脱敏**不是零成本的旁路**：每一次命中都要对整段文本做正则匹配与文本重写。请求侧的设计位置在**入站**（`SanitizationService.sanitizeRequest`），响应侧在**出站**（`ResponseSanitizationFilter`）；此外记录侧（`sanitizeForStorage`，用于调用历史与链路追踪落库前）也执行同一套匹配。

### 当前实际覆盖面（按现状评估，不要按「全量覆盖」估算）

| 位置 | 生效点 | 现状 |
|------|--------|------|
| 响应侧 | 出站响应体过滤器 | **只处理 `application/json` 响应**；SSE / 流式响应（`text/event-stream`）不经过该路径 |
| 响应侧路径 | 路径过滤 | `/v1/**` 的**非流式 JSON** 自 issue #560 起**纳入**脱敏；`/api/**`、`/admin/**`、`/actuator/**` 与静态资源仍在 `ExcludedPathsConfig` 中被**默认排除** |
| 请求侧 | 入站请求体（`sanitizeRequest`） | 开关与白名单逻辑已实现，但**当前网关请求链路上没有调用方**，因此今天不产生入站开销 |
| 记录侧 | 落库前（`sanitizeForStorage`） | 受 `record-level` 与调用历史配置影响，与 `request/response.enabled` 无关 |

因此，打开响应侧开关后，容量评估要按**两档**算：① 所有非 SSE 的 `application/json` 响应 —— 含 AI 实时推理主入口（`/v1/chat/completions`）的**非流式**返回 —— 都会过一次脱敏（issue #560 方案 C 第一阶段已把 `/v1/**` 的 JSON 放开）；② 管理面（`/api/**`）、运维端点与静态资源仍不改写，SSE / 流式响应（`text/event-stream`）也仍不经过该路径（SSE 覆盖留待 issue #560 的后续阶段）。做容量评估时不要按「所有响应都被脱敏」来估算，也不要再按「`/v1` 全都不脱敏」来估算。

另外，JSON 模式下 `traceId` / `spanId` / `requestId` / `traceparent` / `tracestate` 这几个**键**的值默认原样保留（键名大小写不敏感）：把排障标识打码会让响应体与日志对不上，属于「多破坏」（issue #561）。其它键的值照旧按规则脱敏，非 JSON 文本没有键名可推断、行为不变。

### 开销随什么增长

- **规则条数**：每条启用规则都会对每段待检文本至少尝试一次匹配，成本大体按「规则条数 × 文本长度」叠加。
- **正则复杂度**：含嵌套量词或大量可回溯分支的表达式，在长文本上可能退化成灾难性回溯，单次匹配就能消耗数十毫秒。
- **文本体积**：过滤器会把整个响应体读入内存再重写，成本与字节数近似线性；MB 级响应体还会额外占用一份堆内存。
- **请求速率**：单次开销在并发下放大为 CPU 占用上升与平均 / 尾部延迟抬高；`fail-on-error=true` 时失败重试会再放大一次。

### 粗略口径（仅用于初始估算，不作为结论）

- 单次小响应（**< 10KB**）+ 常用 3~5 条 PII 正则：开销通常在**亚毫秒级**（< 1 ms），相对模型推理耗时可忽略。
- **MB 级**响应体，或启用**几十条**复杂正则：开销会进入**毫秒到数十毫秒**量级，**必须按实际压测结果**决定是否开启、以及先开哪一侧。

### 上线建议

1. **先在预发用真实流量压测**：用生产样本（长会话、大响应体）对比开关前后的平均 / 尾部延迟与 CPU，再决定上线。
2. **从最小规则集开始**：只启用必需的 PII 模式，验证效果后逐条加，不要一次性搬入规则库里的全部示例。
3. **避免灾难性回溯的正则**：引擎侧有 `SafeRegexValidator` 做危险模式拦截，但它**不是性能保证**，也不覆盖所有退化形态；自定义正则仍应用长文本自行验证最坏情况。
4. **按侧开关**：请求侧与响应侧可独立启用，先打开收益最明确的一侧。
5. **控制台会提示**：「PII 脱敏管理」页打开开关前会先弹出二次确认（提示性能开销），启用期间配置卡顶部保持一条常驻黄色警示。

### 关闭时零开销

`request.enabled` / `response.enabled` 为 `false` 时，过滤器在入口直接放行（`ResponseSanitizationFilter.filter()` 的第一个判断），既不读响应体也不做正则匹配与文本重写。

注意「零开销」只对网关路径成立：记录侧（`sanitizeForStorage`）**不检查这两个开关**，规则始终加载，只要存在可用规则就会在落库前执行匹配（`DefaultSanitizationService.loadRequestRules` 的注释写明这一取舍）。关闭开关能消除出站响应开销，但不会消除记录侧开销 —— 若调用历史/trace 量大，规则条数仍会影响写入侧延迟。

## 性能优化

### 并行处理

```
jairouter:
  security:
```

### 正则表达式缓存

```
jairouter:
  security:
```

### 批量处理

```
jairouter:
  security:
```

### 3. Redis 缓存

```
jairouter:
  security:
```

### 4. 应用安全配置

创建 `config/application-security.yml`：

```
# 安全配置
security:
  # API Key 配置
  api-key:
    enabled: true
    header: X-API-Key
    keys:
      # 生产环境使用环境变量配置
      - name: "prod-admin"
        value: "${PROD_ADMIN_API_KEY:}"
      - name: "prod-service"
        value: "${PROD_SERVICE_API_KEY:}"
      - name: "prod-readonly"
        value: "${PROD_READONLY_API_KEY:}"
  
  # JWT 配置
  jwt:
    enabled: true
    secret: "${JWT_SECRET:${PROD_JWT_SECRET:}}"
    algorithm: HS256
    expiration-minutes: 15
    issuer: jairouter-prod
    accounts:
      - username: admin
        password: "{bcrypt}your-bcrypt-hashed-password"
        roles: [ADMIN, USER]
        enabled: true
      - username: user
        password: "{bcrypt}your-bcrypt-hashed-password"
        roles: [USER]
        enabled: true

  # CORS 配置
  cors:
    allowed-origins: "*"
    allowed-methods: "*"
    allowed-headers: "*"
    allow-credentials: false

# HTTPS 配置
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: password
    key-store-type: PKCS12
    key-alias: jairouter
```


## 监控和审计

### 脱敏指标

```
jairouter:
  security:
    monitoring:
      metrics:
        sanitization:
          enabled: true
          histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
```

### 审计日志

```
jairouter:
  security:
    audit:
      enabled: true
```

### 监控指标

- `jairouter_security_sanitization_operations_total`：脱敏操作总数
- `jairouter_security_sanitization_duration_seconds`：脱敏操作耗时
- `jairouter_security_sanitization_patterns_matched_total`：匹配的模式总数
- `jairouter_security_sanitization_bytes_processed_total`：处理的字节总数

## 最佳实践

### 1. 规则设计

- **精确匹配**：使用精确的正则表达式避免误匹配
- **性能考虑**：避免过于复杂的正则表达式
- **优先级设置**：合理设置规则优先级
- **测试验证**：充分测试脱敏规则的效果

### 2. 白名单管理

- **最小权限原则**：只为必要的用户和 IP 设置白名单
- **定期审查**：定期审查白名单的必要性
- **临时白名单**：为临时需求设置有时限的白名单
- **审计记录**：记录白名单的使用情况

### 3. 性能优化

- **合理配置线程池**：根据系统资源配置线程池大小
- **启用缓存**：启用正则表达式缓存提升性能
- **流式处理**：对大文件启用流式处理
- **监控性能**：持续监控脱敏操作的性能影响

### 4. 安全考虑

- **日志安全**：确保脱敏日志本身不包含敏感信息
- **规则保密**：保护脱敏规则配置的安全性
- **定期更新**：根据新的数据类型更新脱敏规则
- **合规检查**：确保脱敏规则符合相关法规要求

## 故障排除

### 常见问题

#### 1. 脱敏不生效

**可能原因**：
- 脱敏功能未启用
- 正则表达式不匹配
- 用户在白名单中
- 规则优先级问题

**解决方案**：
1. 检查脱敏功能是否启用
2. 测试正则表达式是否正确
3. 检查白名单配置
4. 调整规则优先级

#### 2. 性能问题

**可能原因**：
- 正则表达式过于复杂
- 线程池配置不当
- 缓存未启用
- 处理数据量过大

**解决方案**：
1. 优化正则表达式
2. 调整线程池大小
3. 启用缓存功能
4. 启用流式处理

#### 3. 误脱敏

**可能原因**：
- 正则表达式过于宽泛
- 规则冲突
- 策略配置错误

**解决方案**：
1. 精确化正则表达式
2. 调整规则优先级
3. 修正脱敏策略

### 调试技巧

#### 1. 启用详细日志

```
logging:
  level:
    org.unreal.modelrouter.auth.security.sanitization: DEBUG
```

#### 2. 测试正则表达式

使用在线工具测试正则表达式：
- https://regex101.com/
- https://regexr.com/

#### 3. 监控脱敏效果

```
# 查看脱敏日志
tail -f logs/security-audit.log | grep sanitization

# 查看脱敏指标
curl http://localhost:8080/actuator/prometheus | grep sanitization
```

## 示例配置

### 基础配置

```
jairouter:
  security:
    enabled: true
    sanitization:
      request:
        enabled: true
        sensitive-words: ["password", "secret", "token"]
        pii-patterns: ["\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"]
        masking-char: "*"
      response:
        enabled: true
        sensitive-words: ["internal", "debug", "error"]
        preserve-json-structure: true
```

### 高级配置

```
jairouter:
  security:
    sanitization:
      request:
        enabled: true
        sensitive-words:
          - "password"
          - "secret"
          - "token"
          - "credential"
        pii-patterns:
          - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
          - "\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b"
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        strategies:
          phone: "keep-prefix-suffix"
          email: "keep-domain"
          id-card: "keep-prefix-suffix"
          default: "full-mask"
        whitelist-users: ["admin-key-001"]
        whitelist-ips: ["127.0.0.1", "192.168.0.0/16"]
        rule-priorities:
          phone: 1
          email: 2
          id-card: 3
          sensitive-word: 4
      response:
        enabled: true
        service-specific-rules:
          chat:
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
          embedding:
            enabled: false
```

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [JWT 认证配置说明](jwt-authentication.md)
- [安全功能故障排除指南](troubleshooting.md)
- [安全监控和告警](../monitoring/alerts.md)