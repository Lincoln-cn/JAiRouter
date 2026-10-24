# Data Sanitization Rule Configuration Document

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: 
<!-- /版本信息 -->



## Overview

JAiRouter's data sanitization feature can automatically identify and process sensitive information in responses and call records, including Personally Identifiable Information (PII) and sensitive words.

### Since v3.2.0: Record-path primary + console management

The **primary** PII surface is chat call-history / logs / tracing records, handled by
`SanitizationService.sanitizeForStorage`. This path is **not** gated by the gateway
`response.enabled` flag (configured PII/sensitive words are always loaded into the rule set).

| Capability | Description |
|------------|-------------|
| Console | Security → **PII Sanitization** (`/security/sanitization`) |
| Permission | `security:sanitization:manage` (ADMIN) |
| Admin API | `GET/PUT /api/config/sanitization`, `GET .../rules`, `POST .../test` |
| Gateway response filter | Off by default; admin `/api/**` and ops endpoints excluded; **non-streaming JSON** on `/v1/**` is sanitized since #560; SSE (`text/event-stream`) is sanitized **frame by frame** since #560 phase 2 (`response.sanitize-sse`, on by default) |
| Dry-run | Non-JSON samples mask as `text/plain` for chat-log pastes |

**Call history**: with `record-level=SUMMARY`, the adapter sanitizes via `sanitizeForStorage` before persist.

See [management API](../api-reference/management-api.md) and [web-console.md](../getting-started/web-console.md).

## Features

- **Response and Record Sanitization**: Gateway outbound responses (JSON / SSE) and call history, logs, and traces are sanitized before persistence
- **Multiple Sanitization Strategies**: Supports masking, replacement, deletion, hashing, and other strategies
- **Regular Expression Support**: Supports complex pattern matching
- **Performance Optimization**: Supports parallel processing and cache optimization
- **Service Differentiation**: Different AI services can use different sanitization rules

## Quick Start

### 1. Enable Data Sanitization

```yaml
jairouter:
  security:
    enabled: true
    sanitization:
      response:
        enabled: true
```

### 2. Basic Configuration

```yaml
jairouter:
  security:
    sanitization:
      response:
        enabled: true
        sensitive-words:
          - "password"
          - "secret"
        pii-patterns:
          - "(?<!\\d)1[3-9]\\d{9}(?!\\d)"                       # Phone number (digit boundaries, never cuts a longer number)
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

This version does **not** rewrite the request body sent to the AI model — the request above reaches the upstream unchanged. Response-side rules apply to **the response body** and to **records / logs before they are stored**. With response sanitization enabled, the phone number and email in the returned content are masked, for example:
```
My phone number is ***********, email is *******************
```

## Detailed Configuration

> ⚠️ **Not every key in the examples below is supported.** The implementation only binds these keys under `response`: `enabled`, `sensitive-words`, `pii-patterns`, `masking-char`, `log-sanitization`, `fail-on-error`, `preserve-json-structure`, `scope`, `max-buffered-bytes` and `sanitize-sse`.
> Keys such as `strategies` (`keep-prefix-suffix` / `keep-domain`), `rule-priorities` and `service-specific-rules` are **not implemented**: adding them to YAML does not fail, but has no effect.
> The strategy actually applied comes from each rule's `strategy` field (`MASK` / `REPLACE` / `REMOVE` / `HASH`); the shipped rules all use `MASK` (same-length replacement, e.g. an 11-digit phone number becomes `***********`).

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
        
        # Response PII patterns (list order = execution order, longest first)
        pii-patterns:
          - "(?<!\\d)\\d{17}[\\dXx](?!\\d)"
          - "(?<!\\d)(?:\\d{16}|\\d{19})(?!\\d)"
          - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
          - "(?<!\\d)1[3-9]\\d{9}(?!\\d)"
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        
        # Preserve JSON structure
        preserve-json-structure: true
        
        # Effective scope (only affects the HTTP response sanitization filter)
        # mode=exclude (default): patterns are the paths to SKIP; empty means the
        # built-in exclusion set is used. mode=include: only matching paths are
        # sanitized, and an empty list sanitizes nothing (a WARN is logged).
        scope:
          mode: exclude
          patterns:
            - "/actuator"
            - "/swagger-ui"
```

### Response Sanitization Scope (`scope`)

Response sanitization filters by **path** first, then splits by **content type**: `application/json` is sanitized as a whole body; `text/event-stream` (SSE) is sanitized frame by frame (see below); anything else passes through untouched. The built-in exclusion set covers the admin console, Actuator, Swagger and static assets; both non-streaming JSON and SSE on `/v1` are in scope.

```yaml
jairouter:
  security:
    sanitization:
      response:
        scope:
          mode: exclude       # exclude (default) | include
          patterns: []        # empty = use the built-in exclusion set
```

- `exclude`: paths in `patterns` are skipped, everything else is sanitized; an empty list means the built-in set (identical to not configuring `scope` at all).
- `include`: only paths matching `patterns` are sanitized; **an empty list sanitizes nothing** and logs a WARN at startup.
- Matching: entries without wildcards match by prefix (`/actuator` covers `/actuator/health`); entries with `*` / `**` use Ant-style matching, and `/v1/**` also matches `/v1` itself.
- The scope is **read-only for now**: the console page (Security > PII sanitization > Response sanitization scope) shows the effective mode, its source (built-in defaults / configuration) and the pattern list, and offers a path preview (`POST /api/config/sanitization/scope/preview`). Changing it requires editing the configuration file and restarting.
- Record-side sanitization (call history, logs, tracing) is not affected by this scope.

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

## Performance Impact and Capacity Planning

Sanitization is **not a zero-cost bypass**: every match runs regex matching and text rewriting over the whole body. The response side covers **outbound** traffic (`ResponseSanitizationFilter`), and the recording side (`sanitizeForStorage`, run before call history and traces are persisted) executes the same matching.

### Current coverage (size with this in mind, not with "everything is sanitized")

| Location | Hook | Status |
|----------|------|--------|
| Response | Outbound response body filter | `application/json` is sanitized as a **whole body**; `text/event-stream` (SSE) is sanitized **frame by frame** (since issue #560 phase 2, `response.sanitize-sse`, on by default) |
| Response paths | Path filter | **Non-streaming JSON** on `/v1/**` is **in scope** since issue #560; `/api/**`, `/admin/**`, `/actuator/**` and static assets are still **excluded by default** in `ExcludedPathsConfig` |
| Recording | Before persistence (`sanitizeForStorage`) | Controlled by `record-level` and call-history settings, independent of `response.enabled` |

So with the response switch on, plan capacity in **three tiers**: (1) every `application/json` response — including the **non-streaming** return of the main inference entry (`/v1/chat/completions`) — is sanitized as a whole body (issue #560 phase 1 opened up `/v1/**` JSON); (2) SSE (`text/event-stream`) is sanitized **per frame**, costing in proportion to frame count and frame size but **without buffering the whole stream** (issue #560 phase 2: line by line, `data:` payloads are rewritten, other lines and the `[DONE]` sentinel pass through); (3) management endpoints (`/api/**`), ops endpoints and static assets are still not rewritten. Do not base a capacity plan on "every response is sanitized", nor on "/v1 or SSE is never sanitized".

In JSON mode the values of `traceId` / `spanId` / `requestId` / `traceparent` / `tracestate` are kept as-is (key match is case-insensitive): masking troubleshooting identifiers breaks the correlation between responses and logs, which is more harm than protection (issue #561). Values under other keys are still sanitized, and plain text has no key to infer, so its behavior is unchanged.

### What drives the cost

- **Number of rules**: every enabled rule attempts at least one match against every text to inspect, so cost scales roughly with rules multiplied by text length.
- **Regex complexity**: expressions with nested quantifiers or many backtracking branches can degrade into catastrophic backtracking on long text, where a single match burns tens of milliseconds.
- **Body size**: the filter reads the whole response body into memory and rewrites it, so cost is close to linear in bytes; MB-scale bodies also hold an extra copy on the heap.
- **Request rate**: per-request cost grows into CPU usage and higher average and tail latency under concurrency; with `fail-on-error=true`, retries amplify it once more.

### Rough numbers (a starting point only, not a conclusion)

- A small response (**< 10KB**) plus 3-5 PII patterns: usually **sub-millisecond** (< 1 ms), negligible against inference time.
- **MB-scale** bodies, or **dozens** of complex patterns: **milliseconds to tens of milliseconds**; decide whether to enable it from measured load-test results.

### Recommendations

1. **Load-test in staging with real traffic**: compare average and tail latency and CPU with the switch on and off using production samples (long sessions, large bodies) before you roll out.
2. **Start from a minimal rule set**: enable only the PII patterns you need, verify the effect, then add rules one at a time instead of importing every sample from the rule library.
3. **Avoid catastrophic backtracking**: `SafeRegexValidator` screens dangerous patterns on the engine side, but it is **not a performance guarantee** and does not cover every degraded shape; validate custom patterns yourself against long worst-case text.
4. **Separate gateway from recording**: `response.enabled` controls only the HTTP response filter; the recording side (call history / logs / traces) always sanitizes with all enabled rules and is not turned off by that switch.
5. **The console warns you**: the "PII Sanitization" page asks for confirmation before enabling the switch (naming the performance cost) and keeps a warning alert on top of the response configuration card while it is on.

### Zero overhead when disabled

When `response.enabled` is `false`, the response filter passes the exchange through at its first check (`ResponseSanitizationFilter.filter()`): it reads no body and runs no regex matching or rewriting.

That zero-overhead guarantee covers the gateway response path only: the recording side (`sanitizeForStorage`) **does not check this switch**. Rules are always loaded, so any applicable rule still runs before content is persisted. Turning the switch off removes the outbound response cost, but not the recording cost: with heavy call history or trace volume, the number of rules still affects write-side latency.

## Performance Optimization

### Parallel Processing

```yaml
jairouter:
  security:
```

### Regular Expression Cache

```yaml
jairouter:
  security:
```

### Batch Processing

```yaml
jairouter:
  security:
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

### 2. Performance Optimization

- **Reasonable Thread Pool Configuration**: Configure thread pool size based on system resources
- **Enable Caching**: Enable regex caching to improve performance
- **Streaming Processing**: Enable streaming processing for large files
- **Performance Monitoring**: Continuously monitor sanitization performance impact

### 3. Response Buffer Limit

Response-side sanitization has to read the entire response body into memory for regex replacement, so there is a hard limit: `jairouter.security.sanitization.response.max-buffered-bytes` (default 1 MiB, floor 1 KiB).

- **`Content-Length` present and above the limit**: sanitization is skipped and the response is passed through unchanged, with a WARN log and an audit event — better to miss masking than to buffer a huge response.
- **Chunked response (no `Content-Length`) above the limit**: the original bytes are already consumed and cannot be replayed, so the request fails as a sanitization error and the global exception handler renders the error body. A truncated body is never emitted.

Raising the limit increases per-request memory linearly — budget the heap as "concurrency × limit". Lowering it pushes more large responses into the "skip sanitization" branch. The limit applies only to the HTTP response filter, not to `sanitizeForStorage`.

### 4. SSE Streaming Frame-by-Frame Sanitization

Streaming responses (`text/event-stream`) cannot be buffered as a whole — a long-lived connection may keep emitting for minutes, and `DataBufferUtils.join` would read the entire stream into memory until it ends. SSE therefore takes a **per-frame** path (issue #560 phase 2):

```yaml
jairouter:
  security:
    sanitization:
      response:
        sanitize-sse: true      # enabled by default
```

Rules:

- Lines are split on newlines; only the payload after `data:` on `data:` lines is rewritten. `event:` / `id:` / `retry:` / comment lines (`:`) and the `data: [DONE]` sentinel pass through unchanged;
- Framing semantics are preserved: line terminators (`\n` / `\r\n`), blank-line separators and field order are not rewritten;
- A frame may span network chunks: partial lines are carried into the next chunk, so a JSON frame is never cut in half;
- **A failing frame never aborts the stream**: if sanitizing one frame throws, that frame is emitted as-is with a warning and later frames keep being processed;
- The **per-line limit** reuses `max-buffered-bytes` (minimum 1 KiB): a line larger than the limit switches to pass-through for that line with a single warning — no bytes are dropped and later lines are unaffected.

Cost and boundaries: the cost moves from "once per response" to "once per frame"; with many frames CPU and latency rise slightly (a frame is usually tens to a few hundred bytes). With `sanitize-sse=false` SSE is not sanitized at all (the pre-#560 behavior) and is not recommended when sanitization is required. The switch is independent of `scope`: paths excluded by `scope` are not sanitized even for SSE.

### 5. Security Considerations

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
- Rule priority issues

**Solutions**:
1. Check if sanitization feature is enabled
2. Test if regular expression is correct
3. Adjust rule priorities

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
    org.unreal.modelrouter.auth.security.sanitization: DEBUG
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
      response:
        enabled: true
        service-specific-rules:
          chat:
            additional-patterns:
              - "\\b(?:QQ|微信|WeChat)[:：]?\\s*\\d+\\b"
          embedding:
            enabled: false
```

## Related Documents

- [API Key Management Guide](api-key-management.md)
- [JWT Authentication Configuration](jwt-authentication.md)
- [Security Feature Troubleshooting Guide](troubleshooting.md)
- [Security Monitoring and Alerting](../monitoring/alerts.md)
