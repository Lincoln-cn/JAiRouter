# Data Sanitization Rule Configuration Document

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



## Overview

JAiRouter's data sanitization feature can automatically identify and process sensitive information in requests and responses, including Personally Identifiable Information (PII) and sensitive words. By configuring sanitization rules, you can ensure that sensitive data does not leak to AI models or be returned to clients.

## Features

- **Bidirectional Sanitization**: Supports sanitization of both request and response data
- **Multiple Sanitization Strategies**: Supports masking, replacement, deletion, hashing, and other strategies
- **Regular Expression Support**: Supports complex pattern matching
- **Whitelist Mechanism**: Supports user and IP whitelists
- **Performance Optimization**: Supports parallel processing and cache optimization
- **Service Differentiation**: Different AI services can use different sanitization rules

## Quick Start

### 1. Enable Data Sanitization

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

### 2. Basic Configuration

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
          - "\\d{11}"  # Phone number
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"  # Email
        masking-char: "*"
```

### 3. Test Sanitization Effect

Send a request containing sensitive information:

```bash
curl -H "X-API-Key: your-api-key" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [{"role": "user", "content": "My phone number is 13812345678, email is user@example.com"}]}' \
     http://localhost:8080/v1/chat/completions
```

The actual content sent to the AI model will be sanitized to:
```
My phone number is 138****5678, email is u***@example.com
```

## Detailed Configuration

### Request Data Sanitization Configuration

```yaml
jairouter:
  security:
    sanitization:
      request:
        # Enable request sanitization
        enabled: true
        
        # Sensitive word list
        sensitive-words:
          - "password"      # Password
          - "secret"        # Secret
          - "token"         # Token
          - "credential"    # Credential
          - "private"       # Private information
        
        # PII regex patterns
        pii-patterns:
          - "\\d{11}"                                                    # Phone number
          - "\\d{18}"                                                    # ID card number
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"          # Email address
          - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"      # Bank card number
        
        # Masking character
        masking-char: "*"
        
        # Sanitization strategies
        strategies:
          phone: "keep-prefix-suffix"    # Keep prefix and suffix
          email: "keep-domain"           # Keep domain
          id-card: "keep-prefix-suffix"  # Keep prefix and suffix
          default: "full-mask"           # Full mask
        
        # Log sanitization
        log-sanitization: true
        
        # Whitelist users
        whitelist-users:
          - "admin-key-001"
        
        # Whitelist IPs
        whitelist-ips:
          - "127.0.0.1"
          - "192.168.0.0/16"
        
        # Rule priorities
        rule-priorities:
          phone: 1
          email: 2
          id-card: 3
          bank-card: 4
          sensitive-word: 5
```

### Response Data Sanitization Configuration

```yaml
jairouter:
  security:
    sanitization:
      response:
        # Enable response sanitization
        enabled: true
        
        # Response sensitive words
        sensitive-words:
          - "internal"      # Internal information
          - "debug"         # Debug information
          - "error"         # Error details
          - "exception"     # Exception information
        
        # Response PII patterns
        pii-patterns:
          - "\\d{11}"
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        
        # Preserve JSON structure
        preserve-json-structure: true
        
        # Service-specific rules
        service-specific-rules:
          chat:
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
          embedding:
            enabled: false
          rerank:
            preserve-ranking-scores: true
```

## Sanitization Strategy Details

### 1. Full Mask (full-mask)

Replace the entire matched content with masking characters:

```
Original: password123
Result: ***********
```

### 2. Keep Prefix Suffix (keep-prefix-suffix)

Keep some characters at the beginning and end:

```
Original: 13812345678
Result: 138****5678

Original: user@example.com
Result: u***@example.com
```

### 3. Keep Domain (keep-domain)

Only for email addresses, keep the domain part:

```
Original: username@company.com
Result: u*******@company.com
```

### 4. Hash Sanitization (hash)

Replace sensitive information using a hash function:

```
Original: 13812345678
Result: [HASH:a1b2c3d4]
```

### 5. Replace Sanitization (replace)

Use predefined replacement text:

```
Original: password123
Result: [REDACTED]
```

### 6. Remove Sanitization (remove)

Completely remove the matched content:

```
Original: My password is password123, please keep it secret
Result: My password is , please keep it secret
```

## Regular Expression Patterns

### Common PII Patterns

#### Chinese Phone Numbers

```yaml
pii-patterns:
  - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
```

#### Chinese ID Card Numbers

```yaml
pii-patterns:
  - "\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b"
```

#### Email Addresses

```yaml
pii-patterns:
  - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
```

#### Bank Card Numbers

```yaml
pii-patterns:
  - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
```

#### IP Addresses

```yaml
pii-patterns:
  - "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
```

#### URLs

```yaml
pii-patterns:
  - "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?"
```

### Custom Pattern Examples

#### Employee ID

```yaml
pii-patterns:
  - "\\b[A-Z]{2}\\d{6}\\b"  # e.g.: AB123456
```

#### Order Number

```yaml
pii-patterns:
  - "\\bORD\\d{10}\\b"  # e.g.: ORD1234567890
```

#### License Plate Number

```yaml
pii-patterns:
  - "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{4}[A-Z0-9挂学警港澳]"
```

## Whitelist Configuration

### User Whitelist

Specified users can skip sanitization processing:

```yaml
jairouter:
  security:
    sanitization:
      request:
        whitelist-users:
          - "admin-key-001"      # API Key ID
          - "system-service"     # System service
          - "data-analyst"       # Data analyst
```

### IP Whitelist

Specified IP addresses can skip sanitization processing:

```yaml
jairouter:
  security:
    sanitization:
      request:
        whitelist-ips:
          - "127.0.0.1"          # Local loopback
          - "::1"                # IPv6 local loopback
          - "192.168.0.0/16"     # Intranet IP range
          - "10.0.0.0/8"         # Intranet IP range
          - "172.16.0.0/12"      # Intranet IP range
```

### Dynamic Whitelist

Supports runtime dynamic addition of whitelists:

```bash
# Add user to whitelist
curl -X POST http://localhost:8080/admin/security/whitelist/users \
     -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"user_id": "new-user", "reason": "Temporary data analysis requirement"}'

# Add IP to whitelist
curl -X POST http://localhost:8080/admin/security/whitelist/ips \
     -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"ip": "203.0.113.1", "reason": "Partner access"}'
```

## Service Differentiation Configuration

Different AI services can use different sanitization rules:

```yaml
jairouter:
  security:
    sanitization:
      response:
        service-specific-rules:
          # Chat service
          chat:
            enabled: true
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
              - "\\b(?:支付宝|Alipay)[:：]?\\s*[\\w\\d]+\\b"
            strategies:
              social-media: "keep-prefix-suffix"
          
          # Vector service (usually no sanitization needed)
          embedding:
            enabled: false
          
          # Rerank service
          rerank:
            enabled: true
            preserve-ranking-scores: true
            additional-patterns:
              - "\\b(?:评分|得分|分数)[:：]?\\s*\\d+(\\.\\d+)?\\b"
          
          # Text-to-speech service
          tts:
            enabled: true
            additional-patterns:
              - "\\b(?:声音|语音|音色)[:：]?\\s*[\\w\\d]+\\b"
          
          # Speech-to-text service
          stt:
            enabled: true
            preserve-timestamps: true
          
          # Image generation service
          image-generation:
            enabled: true
            additional-patterns:
              - "\\b(?:风格|style)[:：]?\\s*[\\w\\d]+\\b"
```

## Performance Optimization

### Parallel Processing

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # Enable parallel processing
        parallel-enabled: true
        # Thread pool size
        thread-pool-size: 8
        # Streaming threshold
        streaming-threshold: 2097152  # 2MB
```

### Regular Expression Cache

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # Regex cache size
        regex-cache-size: 500
        # Cache expiration time (seconds)
        regex-cache-expiration: 3600
```

### Batch Processing

```yaml
jairouter:
  security:
    performance:
      sanitization:
        # Batch size
        batch-size: 100
        # Batch timeout (milliseconds)
        batch-timeout: 1000
```

## Monitoring and Auditing

### Sanitization Metrics

```yaml
jairouter:
  security:
    monitoring:
      metrics:
        sanitization:
          enabled: true
          histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
```

### Audit Logs

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

### Monitoring Metrics

- `jairouter_security_sanitization_operations_total`: Total sanitization operations
- `jairouter_security_sanitization_duration_seconds`: Sanitization operation duration
- `jairouter_security_sanitization_patterns_matched_total`: Total matched patterns
- `jairouter_security_sanitization_bytes_processed_total`: Total processed bytes

## Best Practices

### 1. Rule Design

- **Precise Matching**: Use precise regular expressions to avoid false matches
- **Performance Considerations**: Avoid overly complex regular expressions
- **Priority Setting**: Set rule priorities appropriately
- **Testing and Validation**: Thoroughly test sanitization rule effectiveness

### 2. Whitelist Management

- **Least Privilege Principle**: Only set whitelists for necessary users and IPs
- **Regular Review**: Regularly review whitelist necessity
- **Temporary Whitelists**: Set time-limited whitelists for temporary needs
- **Audit Records**: Record whitelist usage

### 3. Performance Optimization

- **Reasonable Thread Pool Configuration**: Configure thread pool size based on system resources
- **Enable Caching**: Enable regex caching to improve performance
- **Streaming Processing**: Enable streaming processing for large files
- **Performance Monitoring**: Continuously monitor sanitization performance impact

### 4. Security Considerations

- **Log Security**: Ensure sanitization logs themselves don't contain sensitive information
- **Rule Confidentiality**: Protect sanitization rule configuration security
- **Regular Updates**: Update sanitization rules based on new data types
- **Compliance Checks**: Ensure sanitization rules comply with relevant regulations

## Troubleshooting

### Common Issues

#### 1. Sanitization Not Working

**Possible Causes**:
- Sanitization feature not enabled
- Regular expression not matching
- User in whitelist
- Rule priority issues

**Solutions**:
1. Check if sanitization feature is enabled
2. Test if regular expression is correct
3. Check whitelist configuration
4. Adjust rule priorities

#### 2. Performance Issues

**Possible Causes**:
- Regular expression too complex
- Thread pool configuration inappropriate
- Cache not enabled
- Processing too much data

**Solutions**:
1. Optimize regular expressions
2. Adjust thread pool size
3. Enable cache functionality
4. Enable streaming processing

#### 3. False Sanitization

**Possible Causes**:
- Regular expression too broad
- Rule conflicts
- Strategy configuration errors

**Solutions**:
1. Precise regular expressions
2. Adjust rule priorities
3. Correct sanitization strategies

### Debugging Tips

#### 1. Enable Detailed Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security.sanitization: DEBUG
```

#### 2. Test Regular Expressions

Use online tools to test regular expressions:
- https://regex101.com/
- https://regexr.com/

#### 3. Monitor Sanitization Effect

```bash
# View sanitization logs
tail -f logs/security-audit.log | grep sanitization

# View sanitization metrics
curl http://localhost:8080/actuator/prometheus | grep sanitization
```

## Example Configurations

### Basic Configuration

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

### Advanced Configuration

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

## Related Documents

- [API Key Management Guide](api-key-management.md)
- [JWT Authentication Configuration](jwt-authentication.md)
- [Security Feature Troubleshooting Guide](troubleshooting.md)
- [Security Monitoring and Alerting](monitoring.md)
