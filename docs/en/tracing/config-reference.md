# Configuration Reference

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-06-02
> **Git Commit**: b305e6de
> **Author**: Lincoln
<!-- /版本信息 -->

This document provides complete configuration reference for JAiRouter's distributed tracing feature.

## Configuration File Structure

JAiRouter uses a modular configuration management approach, with tracing configuration located in a separate configuration file:

- **Main Configuration File**: `src/main/resources/application.yml`
- **Tracing Configuration File**: `src/main/resources/config/tracing/tracing-base.yml`
- **Environment Configuration Files**: `src/main/resources/application-{profile}.yml`

## Modular Configuration Explanation

Tracing configuration has been separated from the main configuration file and is imported through the `spring.config.import` mechanism:

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/tracing/tracing-base.yml
```

> **Authoritative source**: key names on this page follow
> `src/main/resources/config/tracing/tracing-base.yml` — that file is guarded by
> `TracingConfigBindingGuardTest` (every key must bind to a field). This page was checked line by line
> against the code in issue #231 for key names/paths and for whether a capability exists at all, but two
> kinds of information were not re-verified (see "Known gaps on this page" at the end).
>
> ⚠️ **"The key exists" is not the same as "tuning it does something"**: a number of fields under tracing
> **bind but have no consumer** (issues #224 / #231); this page flags them where relevant.

## Basic Configuration

### Enable Tracing

```yaml
jairouter:
  tracing:
    enabled: true                    # Whether to enable tracing, default: true
    service-name: "jairouter"       # Service name, default: "jairouter"
    service-version: "1.0.0"        # Service version, default: "1.0.0"
```

### Basic Configuration Items

| Configuration Item | Type | Default Value | Description |
|--------|------|---------|------|
| `enabled` | boolean | `true` | Whether to enable tracing |
| `service-name` | string | `"jairouter"` | Service name for identifying the tracing source |
| `service-version` | string | `"1.0.0"` | Service version number |
| `service-namespace` | string | `"production"` | Service namespace (**the `environment` field this table used to show does not exist**, see issue #231) |

## Sampling Configuration

### Ratio Sampling

```yaml
jairouter:
  tracing:
    sampling:
      # Sampling strategy (real since issue #234): ratio (default) / rule / adaptive
      strategy: ratio
      ratio: 0.1                     # Sampling rate 0.0-1.0 (field default 1.0; tracing-base.yml sets 0.1)
```

### Rule Sampling

```yaml
jairouter:
  tracing:
    sampling:
      # Rule sampling requires strategy: rule (issue #234); a rule has exactly two fields —
      # condition and ratio.
      strategy: rule
      rules:
        - condition: "http.status_code >= 500"   # Attribute expression: >= / <= / == / !=
          ratio: 1.0                             # Sampling ratio when the rule matches
        - condition: "http.route == /api/v1/chat/completions"
          ratio: 0.5
```

**Matching semantics** (`RuleBasedSamplingStrategy.matchesRule` /
`SamplingConfigurationValidator.validateRuleCondition`): the comparison operators in `condition`
(`>=`, `<=`, `==`, `!=`) are evaluated against span attributes, and **the first matching rule in
declaration order wins**. A `condition` without any of those operators is reported as probably invalid.

> Wiring (issue #234): `SamplingStrategyManager` builds the sampler according to `strategy`, and only
> when `rule` is selected does it put `RuleBasedSamplingStrategy` into the sampling chain — i.e. **`rules`
> take effect only when `strategy` is set to `rule`**. Under the default `ratio` they are deliberately
> ignored, so that merely listing rules cannot silently change the global sampling rate. The same strategy
> also activates `always-sample` / `never-sample` / `service-ratios`.
>
> The previous table (`service` / `operation` / `path-pattern` / `method` / `header-name` /
> `header-value` / `error-only` / `status-code`) listed eight conditions that do not exist in the code;
> it was removed.


### Adaptive Sampling

```yaml
jairouter:
  tracing:
    sampling:
      # Adaptive sampling needs strategy: adaptive AND enabled: true (issue #234)
      strategy: adaptive
      adaptive:
        enabled: false                # Whether to enable it, default: false
        target-spans-per-second: 1000 # Target spans per second, default: 1000
        min-ratio: 0.1                # Lower bound of the sampling ratio, default: 0.1
        max-ratio: 1.0                # Upper bound of the sampling ratio, default: 1.0
        adjustment-interval: 30       # Adjustment interval in **whole seconds**, default: 30
```

> Wiring (issue #234): with `strategy: adaptive` **and** `adaptive.enabled: true`,
> `SamplingStrategyManager` builds an `AdaptiveSamplingStrategy` from the fields above and puts it into
> the sampling chain; if either is missing it falls back to `ratio` (with a warning in the startup log and
> from the validator).

## Exporter Configuration

### Log Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "logging"
      logging:
        # Checked in issue #231: LoggingExporterConfig only has enabled / level; the former
        # format / include-resource keys do not exist and were removed.
        enabled: false               # Whether to enable this exporter, default: false
        level: "INFO"                # Log level, default: INFO
```

### Jaeger Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://localhost:14268/api/traces"  # Jaeger collector endpoint
        timeout: 10s                 # Connection timeout, default: 10s
        # Checked in issue #231: JaegerConfig only has endpoint / timeout / headers — no compression
        headers:                     # Custom request headers
          "Authorization": "Bearer token"
```

### Zipkin Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "zipkin"
      zipkin:
        endpoint: "http://localhost:9411/api/v2/spans"
        timeout: 10s
        # Checked in issue #231: ZipkinConfig only has endpoint / timeout — no compression
```

### OTLP Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"  # OTLP endpoint (gRPC)
        timeout: 10s                 # Timeout, default: 10s
        compression: "gzip"          # Compression
        headers:                     # Custom headers
          "api-key": "your-api-key"
        # Checked in issue #231: OtlpConfig only has endpoint / timeout / compression / headers —
        # neither protocol nor the whole tls.* block exists; both were removed.
```

### Batch Processing Configuration

```yaml
jairouter:
  tracing:
    # Checked in issue #231: the batch processor parameters live **under performance**, not under
    # open-telemetry. The open-telemetry.sdk.trace.processors.batch.* keys this page used to show do
    # bind, but no production code reads them — they do not match the real wiring, and the fields were
    # removed in #224. See OpenTelemetryAutoConfiguration#tracerProvider for the real assembly.
    performance:
      batch:
        size: 2048                   # Max spans per export batch (setMaxExportBatchSize), default: 100
        timeout: 30s                 # Export timeout, also used as schedule delay, default: 5s
      buffer:
        size: 8192                   # Queue capacity (that is what setMaxQueueSize reads), default: 1024
```

## Memory Management Configuration

```yaml
jairouter:
  tracing:
    performance:
      # Checked in issue #231: the real path is performance.memory, and it has exactly these three
      # fields. The former max-spans / cleanup-interval / span-ttl / memory-threshold /
      # gc-pressure-threshold keys and the whole cache.* block do not exist and were removed.
      memory:
        max-spans-in-memory: 10000   # Max spans held in memory, default: 10000
        memory-limit-mb: 100         # Memory limit in MB; exceeding it triggers eviction, default: 100
        gc-interval: 60s             # Memory-pressure check interval, default: 60s
```

## Performance Configuration

```yaml
jairouter:
  tracing:
    performance:
      async-processing: true       # Asynchronous processing, default: true
      # Async processing thread pool (issue #215: batch-size / buffer-size /
      # max-queue-size are not fields under performance, so configuring them had no effect)
      # Caps for the memory/performance monitor schedulers (read via constructor @Value; no fields)
      scheduler:
        thread-cap: 2              # Thread cap, default: 2
        queue-capacity: 100        # Queue capacity, default: 100

      thread-pool:
        core-size: 8               # boundedElastic threadCap (i.e. the cap), default: 2
        queue-capacity: 8192       # Queue capacity, default: 1000
        keep-alive: 60s            # Idle-thread TTL, default: 60s (only wired up in #220)
        thread-name-prefix: "tracing-"  # Thread name prefix, default: "tracing-"
        # Note: the class also has max-size, but boundedElastic has a single cap — it is only
        # echoed by /actuator/info
      batch:
        size: 2048                 # Batch size, default: 2048
        timeout: 30s               # Batch timeout, default: 30s
        max-concurrent-batches: 3  # Max concurrent batches, default: 3
      buffer:
        size: 8192                 # Buffer size, default: 8192
        flush-interval: 5s         # Flush interval, default: 5s
        max-wait-time: 30s         # Max extra wait for a partially filled batch, default: 30s
```

## Component Configuration

### HTTP Configuration

```yaml
jairouter:
  tracing:
    components:
      http:
        enabled: true
        # The four fields below are currently only echoed by /actuator/info; they do not change
        # capture behavior (issue #224)
        capture-headers: true
        capture-body: false
        excluded-paths: []
```

> Checked in issues #231 / #224: the **WebFlux / WebClient / Database / Redis sections this page used
> to show do not exist** in `TracingComponentConfig` (that class only has http / load-balancer /
> rate-limiter / circuit-breaker; the database / cache / messaging sections had no consumer and were
> deleted in #224). They were all removed.

### Rate Limiter Configuration

```yaml
jairouter:
  tracing:
    components:
      rate-limiter:
        # enabled is the real behavior switch (TracingWrapperFactory reads it); the capture-* flags
        # are only echoed by /actuator/info, so tuning them changes nothing (issue #224).
        # capture-quota / capture-decision were not even echoed and were deleted.
        enabled: true
        capture-algorithm: true
        capture-statistics: true
```

### Circuit Breaker Configuration

```yaml
jairouter:
  tracing:
    components:
      circuit-breaker:
        # Same as above; capture-state-changes / capture-failure-rate were not even echoed and were deleted.
        enabled: true
        capture-state: true
        capture-statistics: true
```

### Load Balancer Configuration

```yaml
jairouter:
  tracing:
    components:
      load-balancer:
        # Same as above; capture-selection and capture-candidates (which had no yml key at all) were deleted.
        enabled: true
        capture-strategy: true
        capture-statistics: true
```

## Security Configuration

```yaml
jairouter:
  tracing:
    security:
      # Whole-section switch (no field; driven by @ConditionalOnProperty, absent means on)
      enabled: true
      sanitization:
        enabled: true
        inherit-global-rules: true
        additional-patterns: []
        sensitive-attributes: []      # Attribute names to sanitize (real field, checked in #224)
      access-control:
        # There is also access-control.enabled (a condition-only key); the two below are echoed by
        # /actuator/info
        restrict-trace-access: true
        allowed-roles: []
      # Checked in issues #231 / #224 batch B: the whole audit.* section (including storage), plus
      # encryption's encrypt-sensitive-spans / encrypt-sensitive-logs /
      # key-management.{rotation-interval,key-store-path,use-hardware-security-module} /
      # data-retention.* **had no consumer and were deleted**. Encryption now keeps only
      # enabled / algorithm / key-size / key-management.auto-rotation, and those are echoed by
      # /actuator/info only, so they are not listed here.
```

## Monitoring Configuration

```yaml
jairouter:
  tracing:
    monitoring:
      metrics:
        traces:
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]   # Processing-latency histogram buckets, in seconds
      health:
        enabled: true
        check-interval: 30s          # Exporter health check period (@Scheduled reads it), default: 30s
        failure-threshold: 3         # Consecutive failures before unhealthy, default: 3
        recovery-threshold: 2        # Consecutive successes before healthy again, default: 2
      alerts:
        thresholds:
          memory-usage: 0.8          # Heap usage above this ratio is reported as a memory bottleneck, default: 0.8
          export-latency-p99: 5000   # P99 critical threshold for the trace.export operation, in ms, default: 5000
```

> `health.{failure-threshold,recovery-threshold}` are **debounce** thresholds: `ExporterHealthChecker`
> marks the exporter unhealthy only after **failure-threshold consecutive** failed checks, and recovers
> only after **recovery-threshold consecutive** successful ones. A check with the opposite result resets
> the matching consecutive counter, so a single flapping check does **not** flip the `exporterHealthChecker`
> component in `/actuator/health`. Setting either threshold to 1 degenerates to "each check decides the
> state on its own".
>
> Checked in issues #231 / #224: `alerts.trace-processing-failures` / `export-failures` /
> `buffer-pressure` were already deleted in #215 (no backing fields). `metrics.exporter.*` (4 keys),
> `metrics.enabled`, `metrics.prefix`, `metrics.traces.enabled`, `self-monitoring`, `alerts.enabled`,
> `alerts.thresholds.export-failure-rate` and `alerts.thresholds.queue-size` were re-checked in #224 and
> have **no reader anywhere in the codebase** (the `monitoring` section is not even echoed to
> `/actuator/info`) — they bind but can never take effect, so they were deleted. Implement a consumer
> first if you need them back.
>
> The five keys kept above all have real consumers: `histogram-buckets` → `TracingPerformanceMonitor`
> registers the `tracing.processing.latency` histogram with them (note the unit is **seconds** while the
> metric records milliseconds — the consumer converts); `health.*` → `ExporterHealthChecker`;
> `alerts.thresholds.memory-usage` and `export-latency-p99` → `TracingPerformanceMonitor#detectBottlenecks`
> and the `trace.export` entry of its default threshold table (previously hardcoded to 0.8 / 5000).

## Environment Configuration Overrides

Different environments can override tracing configuration through corresponding environment configuration files:

### Development Environment (application-dev.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 1.0  # 100% sampling in development

# Checked in issue #231: there is no logging.level field under tracing; use Spring's own level
logging:
  level:
    org.unreal.modelrouter.monitor.tracing: DEBUG
```

### Production Environment (application-prod.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 0.1  # 10% sampling in production environment
    exporter:
      type: "otlp"
      otlp:
        endpoint: "${OTLP_ENDPOINT:http://localhost:4317}"
```

## Best Practices

### Configuration Management

1. **Base Configuration**: Define common configurations in `tracing-base.yml`
2. **Environment Differences**: Override specific configurations in corresponding environment configuration files
3. **Sensitive Information**: Use environment variables to inject sensitive configurations such as exporter endpoints and authentication information

### Sampling Strategy

1. **Development Environment**: Recommended to use 100% sampling for debugging
2. **Production Environment**: Adjust sampling rate according to system load to avoid performance impact
3. **Critical Paths**: Use rule sampling to ensure tracing of important business

### Performance Tuning

1. **Batching**: `performance.batch.*` together with `performance.buffer.size` decides exporter batching —
   tune them as a pair
2. **Memory management**: `performance.memory.memory-limit-mb` is the eviction trigger, `gc-interval`
   the check frequency
3. **Component selection**: only `components.{load-balancer,rate-limiter,circuit-breaker}.enabled` are
   real switches

## Known gaps on this page (issue #231)

This revision fixed two classes of problem: **key names/paths** and **whether a capability exists**
(the original list is in issue #231). The following were not re-verified line by line:

- the **default values** quoted in prose: the values here are the config class field defaults, while the
  repo's `tracing-base.yml` sets different values for a few keys (e.g. `sampling.ratio` defaults to 1.0
  in the class but is 0.1 in the yml) — when they differ, trust the config file you actually load;
- alert examples (PromQL), command-line and curl snippets;
- "Best Practices" is experience-based advice with no mechanically checkable facts.

The review also turned up **two capability gaps** (not documentation problems; recorded separately):

(`sampling.rules` / `sampling.adaptive.*` were wired up in issue #234: set `sampling.strategy` to
`rule` / `adaptive` and they take effect. The zero-consumer fields under `security.audit.*` and
`security.encryption.*` were deleted in issue #224 batch B and are no longer part of the configurable
surface.)
