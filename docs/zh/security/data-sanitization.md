# 数据脱敏规则配置文档

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



## 概述

JAiRouter 的数据脱敏功能可以自动识别和处理请求和响应中的敏感信息，包括个人身份信息（PII）、敏感词汇等。通过配置脱敏规则，您可以确保敏感数据不会泄露到 AI 模型或返回给客户端。

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
          - "\\d{11}"  # 手机号
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"  # 邮箱
        masking-char: "*"
```

### 3. 测试脱敏效果

发送包含敏感信息的请求：

```bash
curl -H "X-API-Key: your-api-key" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [{"role": "user", "content": "我的手机号是13812345678，邮箱是user@example.com"}]}' \
     http://localhost:8080/v1/chat/completions
```

实际发送到 AI 模型的内容将被脱敏为：
```
我的手机号是138****5678，邮箱是u***@example.com
```

## 详细配置

### 请求数据脱敏配置

```yaml
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
        
        # PII 正则表达式模式
        pii-patterns:
          - "\\d{11}"                                                    # 手机号码
          - "\\d{18}"                                                    # 身份证号码
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"          # 邮箱地址
          - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"      # 银行卡号
        
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

```yaml
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
        
        # 响应 PII 模式
        pii-patterns:
          - "\\d{11}"
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        
        # 保持 JSON 结构
        preserve-json-structure: true
        
        # 服务特定规则
        service-specific-rules:
          chat:
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
          embedding:
            enabled: false
          rerank:
            preserve-ranking-scores: true
```

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

```yaml
pii-patterns:
  - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
```

#### 中国身份证号

```yaml
pii-patterns:
  - "\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b"
```

#### 邮箱地址

```yaml
pii-patterns:
  - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
```

#### 银行卡号

```yaml
pii-patterns:
  - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
```

#### IP 地址

```yaml
pii-patterns:
  - "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
```

#### URL 地址

```yaml
pii-patterns:
  - "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?"
```

### 自定义模式示例

#### 员工工号

```yaml
pii-patterns:
  - "\\b[A-Z]{2}\\d{6}\\b"  # 如：AB123456
```

#### 订单号

```yaml
pii-patterns:
  - "\\bORD\\d{10}\\b"  # 如：ORD1234567890
```

#### 车牌号

```yaml
pii-patterns:
  - "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{4}[A-Z0-9挂学警港澳]"
```

## 白名单配置

### 用户白名单

指定的用户可以跳过脱敏处理：

```yaml
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

```yaml
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

```bash
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

```yaml
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

## 性能优化

### 并行处理

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # 启用并行处理
        parallel-enabled: true
        # 线程池大小
        thread-pool-size: 8
        # 流式处理阈值
        streaming-threshold: 2097152  # 2MB
```

### 正则表达式缓存

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # 正则表达式缓存大小
        regex-cache-size: 500
        # 缓存过期时间（秒）
        regex-cache-expiration: 3600
```

### 批量处理

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # 批量处理大小
        batch-size: 100
        # 批量处理超时（毫秒）
        batch-timeout: 1000
```

## 监控和审计

### 脱敏指标

```yaml
jairouter:
  security:
    monitoring:
      metrics:
        sanitization:
          enabled: true
          histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
```

### 审计日志

```yaml
jairouter:
  security:
    audit:
      enabled: true
      event-types:
        sanitization-applied: true
        sanitization-skipped: true
        sanitization-failed: true
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

```yaml
logging:
  level:
    org.unreal.modelrouter.security.sanitization: DEBUG
```

#### 2. 测试正则表达式

使用在线工具测试正则表达式：
- https://regex101.com/
- https://regexr.com/

#### 3. 监控脱敏效果

```bash
# 查看脱敏日志
tail -f logs/security-audit.log | grep sanitization

# 查看脱敏指标
curl http://localhost:8080/actuator/prometheus | grep sanitization
```

## 示例配置

### 基础配置

```yaml
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

```yaml
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
    performance:
      sanitization:
        parallel-enabled: true
        thread-pool-size: 8
        streaming-threshold: 2097152
        regex-cache-size: 500
```

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [JWT 认证配置说明](jwt-authentication.md)
- [安全功能故障排除指南](troubleshooting.md)
- [安全监控和告警](../monitoring/alerts.md)