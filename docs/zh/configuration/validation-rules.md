# 配置校验规则

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-07-17
> **Git 提交**: ca98a2ee
> **作者**: Lincoln
<!-- /版本信息 -->

---

## 概述

JAiRouter 的配置校验分**两类**，时机与后果都不同：

| 时机 | 触发点 | 校验器 | 出错的后果 |
|---|---|---|---|
| **启动期** | `ApplicationReadyEvent` | `ConfigIntegrityValidator` · `SensitiveConfigValidator` | 前者：**应用启动失败**；后者：**仅严格模式下**才失败 |
| **运行时** | 保存/应用配置时 | `ConfigurationValidator`（服务与实例）· `SecurityConfigurationValidator` · `MonitoringConfigurationValidator` · `SamplingConfigurationValidator`（追踪采样） | 由各调用点决定（见下节清单） |

> ⚠️ **本页已按代码重写（issue #374）**。此前的版本列了 7 条具名规则（`rate-limit-capacity`、
> `circuit-breaker-threshold`、`jwt-expiration`、`thread-pool-size` 等）、对应的 `export RATE_LIMIT_*`
> 示例，以及一个 `ConfigurationValidationRule` 扩展接口 —— **这三类内容在本仓都不存在**。
> 现在本页的每一条都能指到具体 `file:line`。

---

## 一、启动期校验

启动期校验由两个 `ApplicationListener<ApplicationReadyEvent>` 在**应用就绪后**各跑一次
（即校验失败时容器已经起来了，失败表现为**进程退出**）。

### 总开关

| 键 | 默认值 | 作用范围 |
|---|---|---|
| `jairouter.config.validation.enabled` | `true` | 两个校验器都会读它；**读法略有差异**（见下） |
| `jairouter.config.validation.strict-mode` | `false` | **只影响** `SensitiveConfigValidator` |

```yaml
jairouter:
  config:
    validation:
      enabled: true       # false ⇒ 两个启动期校验器都不工作
      strict-mode: false  # true ⇒ 敏感值体检的「错误」升级为启动失败
```

### 1. `ConfigIntegrityValidator` —— 发现错误即启动失败

`config/core/validator/ConfigIntegrityValidator.java`（`:45` 是类声明，`:79-87` 读开关）。
它把问题分成两个桶，然后（`:107-112`）：

- **有任何 error** ⇒ 逐条 `log.error` 后 **抛 `IllegalStateException("配置校验失败，存在 N 个错误")`**
  ⇒ 应用**启动失败**；
- **只有 warning** ⇒ 只打印日志，**不影响启动**。

规则表（取值集合与边界**逐条取自代码**，见 `:129-206`）：

| 配置键 | 判定 | 级别 |
|---|---|---|
| `server.port` | 不在 `1`–`65535` | **错误** |
| `server.port` | `< 1024`（特权端口，可能需要 root） | 警告 |
| `model.load-balance.type` | 不在 `round-robin` · `weighted` · `least-connections` · `ip-hash` · `random` | **错误** |
| `model.rate-limit.algorithm`（仅当 `model.rate-limit.enabled=true`） | 不在 `token-bucket` · `sliding-window` · `fixed-window` | **错误** |
| `model.circuit-breaker.failureThreshold`（仅当 `model.circuit-breaker.enabled=true`） | `≤ 0` | **错误** |
| 同上 | `> 100`（熔断器可能难以触发） | 警告 |
| `model.circuit-breaker.timeout`（仅当 `model.circuit-breaker.enabled=true`） | `≤ 0` | **错误** |
| 同上 | `> 300000`（毫秒，恢复可能缓慢） | 警告 |
| `store.type` | 不在 `jpa` · `redis` · `memory` · `h2` | **错误** |
| `monitoring.enabled` | `false`（建议生产启用监控） | 警告 |
| `jairouter.tracing.enabled` | `false`（建议生产启用追踪） | 警告 |

开关读法：它用 `@Value("${jairouter.config.validation.enabled:true}")` 注入（`:79`），
`false` 时打印一行「配置校验已禁用」后直接返回（`:84-87`）。

> 📌 该类的 javadoc 里也有一份规则清单，但**取值集合比代码少**（写的是 4 种负载均衡类型 / 2 种限流算法 /
> 2 种存储类型）。本页按**代码**写；javadoc 已在 issue #374 的同一批里对齐。

### 2. `SensitiveConfigValidator` —— 敏感值体检（**默认只告警**）

`config/core/validator/SensitiveConfigValidator.java`。它是
`@ConditionalOnProperty(name = "jairouter.config.validation.enabled", havingValue = "true", matchIfMissing = true)`
（`:28`）—— 注意与上一个校验器的差异：**这一个在开关缺失时默认启用**。

检查三个**环境变量**（通过 Spring 占位符读，`${JWT_SECRET:}` 这种形态，`:33-40`）：

| 变量 | 未设置 | 长度不足 | 看起来是硬编码值 |
|---|---|---|---|
| `JWT_SECRET` | 严格模式：**错误**；否则：警告 | 建议 ≥32 字符，不足 ⇒ **错误**（严格模式）/ 警告 | **错误** |
| `INITIAL_ADMIN_PASSWORD` | 严格模式：**错误**；否则：警告（文案写明会回落到出厂默认密码，生产 profile 会启动失败） | 建议 ≥8 字符，不足 ⇒ 警告 | 警告 |
| `GPUSTACK_API_TOKEN` | 警告（模型服务将无法认证） | — | 警告 |

「看起来是硬编码值」的判据（`:153-169`）：包含 `your-` / `placeholder` / `example` / `test` / `demo` /
`changeme`，或整值等于 `secret` / `password`（大小写不敏感）。

**失败语义（容易误解，按代码写清）**：`errors` 桶**一律打印**，但**只有 `strict-mode=true` 才阻断启动**
（打印与阻断解耦，issue #381 起）。也就是说：

- `strict-mode=false`（默认）：`warnings` 与 `errors` **都会打印**（错误级发现例如 `JWT_SECRET` 看起来是硬编码值
  现在**可见**），但**不阻止启动**；
- `strict-mode=true`：`errors` 非空 ⇒ 逐条 `log.error` + **启动失败**。

> 📌 **与生产启动门闩的关系**：`prod` / `production` profile 下，`JWT_SECRET` 与
> `INITIAL_ADMIN_PASSWORD` 的更严格判定发生得**更早**（`auth/security/config/StartupSecurityGate.java`
> 在 `CommandLineRunner` 阶段就 fail-fast，缺失、出厂默认值或强度不足都会让进程起不来）
> ⇒ 生产环境通常**跑不到**这个校验器。开发/测试环境才主要靠它给提示。

### 3. `AdapterReferenceValidator` —— adapter 引用体检（默认只报错、不拦启动）

`router/adapter/AdapterReferenceValidator.java`（`#638` 起）。在 `ApplicationReadyEvent` 阶段扫描
`model.adapter`（全局）、`model.services.*.adapter`（服务级）、`model.services.*.instances[].adapter`
（实例级），把每个名字与**已注册**的 adapter 清单比对一次。

**为什么需要它**：旧实现只在请求路径上解析 adapter（`AdapterRegistry.getAdapter`），名字写错时启动、
健康检查、`/v1/models` 全部正常，**只有真实流量命中才 500**；一个服务下多个实例只有个别写错时，
表现为「按权重随机一部分请求 500」，很容易被误判成上游抖动。

| 开关 | 默认 | 效果 |
|---|---|---|
| `jairouter.config.validation.enabled` | `true` | 关掉则本校验器整体跳过 |
| `jairouter.config.validation.strict-mode` | `false` | `true`：存在未注册引用 ⇒ **启动失败**；`false`：只逐条打 `ERROR` 日志并继续启动 |

日志/异常里的定位信息形如
`scope=instance service=chat instance=qwen3:4b adapter=openai-compat`，而请求期的异常消息也补齐了上下文
（`Unsupported adapter: xxx (service=chat, instance=qwen3:4b; registered=[...])`），可以直接看到可用取值。

> 默认不拦启动是刻意的取舍：升级瞬间因为一个拼写错误把存量部署拦在门口，代价高于「先起、再看日志」。
> 生产环境建议在预发就把 `strict-mode` 打开，或把 `adapter 引用校验通过` 这条 `INFO` 日志纳入启动检查。

---

## 二、运行时校验（配置 API 路径）

这些校验器不在启动期跑，而是挂在**配置变更**的调用点上：

| 校验器 | 调用点 | 说明 |
|---|---|---|
| `config/core/ConfigurationValidator.java`（`:31`） | `config/controller/ServiceTypeController.java`（`:54` 注入）· `router/factory/ComponentFactory.java`（`:32` 注入） | 服务/实例/负载均衡/限流/基础配置的校验（`:258` `validateLoadBalanceConfig`、`:281` `validateRateLimitConfig`、`:376` `validateInstanceConfig`、`:434` `validateSingleServiceConfig`、`:482` `validateBasicConfiguration`） |
| `auth/security/config/SecurityConfigurationValidator.java`（`:24`） | `SecurityConfigurationManager`（`:220`）· `ImprovedSecurityConfigurationService`（`:137`）· `SecurityConfigurationBackupService`（`:64`） | 安全配置与备份的校验 |
| `monitor/monitoring/config/MonitoringConfigurationValidator.java`（`:17`） | 监控配置保存路径 | 监控配置校验 |
| `monitor/tracing/config/SamplingConfigurationValidator.java`（`:21`） | `TracingConfigManager`（`:182`）· `config/core/ConfigurationService.java`（`:122`） | 追踪采样规则校验 |

各调用点如何处置校验结果（拒绝保存 / 返回错误 / 仅告警）由调用点自己决定，本页只做**清单**；
逐条后果以各自 `javadoc` 与调用点代码为准。

---

## 三、如何禁用 / 如何开启严格模式

```yaml
# 关闭两个启动期校验器（不推荐：等于放弃启动期体检）
jairouter:
  config:
    validation:
      enabled: false

# 只开启严格模式（让敏感值体检的「错误」真正阻止启动）
jairouter:
  config:
    validation:
      strict-mode: true
```

环境变量形态（Spring Boot 的宽松绑定，等价于上面的 YAML）：

```bash
export JAIRouter_CONFIG_VALIDATION_ENABLED=true
export JAIRouter_CONFIG_VALIDATION_STRICT_MODE=true
```

> ⚠️ 注意：`strict-mode` 影响两个校验器 —— `SensitiveConfigValidator`（敏感值的「错误」是否阻断启动）
> 与 `AdapterReferenceValidator`（adapter 引用错误是否阻断启动）。`ConfigIntegrityValidator` 的错误
> 一直是「启动失败」，没有严格模式开关。

---

## 四、如何扩展（按**真实**形态）

本仓**没有** `ConfigurationValidationRule` 之类的接口。要加一个启动期校验，照两个真实校验器做：

```java
@Component
public class MyConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(MyConfigValidator.class);

    @Value("${jairouter.config.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!validationEnabled) {
            return;
        }
        // 自行收集 errors / warnings；要在错误时阻止启动，就抛异常（模板见 ConfigIntegrityValidator:107-112）
        log.info("✓ 自定义校验完成");
    }
}
```

**要点**：

1. 用 `@Component` + `ApplicationListener<ApplicationReadyEvent>` 注册（本仓两个真实校验器都是这个形态）；
2. 需要「错误即启动失败」时抛 `IllegalStateException`（`ConfigIntegrityValidator` 的做法）；
   只提示不阻断就只 `log.warn`；
3. 请**顺手读一下 `jairouter.config.validation.enabled`**，让总开关对自定义校验同样有效。

---

## 五、排查

| 现象 | 原因与处置 |
|---|---|
| 启动失败，日志里 `配置错误` 段列出条目 | `ConfigIntegrityValidator` 的 error 桶非空 —— 按条目修配置（`:108-111` 会给出条数） |
| 启动失败，日志里 `敏感配置校验失败` | `strict-mode=true` 且 `SensitiveConfigValidator` 的 error 桶非空（缺 `JWT_SECRET` / 长度不足 / 硬编码值） |
| 日志有 `⚠️` 但没有启动失败 | warning 桶只提示不阻断（两个校验器都是） |
| 设置了 `JWT_SECRET` 却仍报「未设置」 | 该值通过 `${JWT_SECRET:}` 读**环境变量**；只在 YAML 里写等价属性不会命中它（生产门闩同理，见「与生产启动门闩的关系」） |
| 改了环境变量但校验结果没变 | 两个校验器都只在 `ApplicationReadyEvent` 跑一次 ⇒ 需重启进程 |
| 想把校验降级为提示 | 去掉 `strict-mode`（敏感值体检默认就只告警）；`ConfigIntegrityValidator` 的错误无法降级，只能改对配置或用 `enabled=false` 整体关闭 |

---

## 相关代码

| 文件 | 作用 |
|---|---|
| `config/core/validator/ConfigIntegrityValidator.java` | 启动期完整性校验；错误 ⇒ 启动失败 |
| `config/core/validator/SensitiveConfigValidator.java` | 启动期敏感值体检；默认只告警，严格模式下失败 |
| `config/core/ConfigurationValidator.java` | 运行时配置校验（服务/实例/负载均衡/限流/基础） |
| `auth/security/config/SecurityConfigurationValidator.java` | 安全配置与备份校验 |
| `monitor/monitoring/config/MonitoringConfigurationValidator.java` | 监控配置校验 |
| `monitor/tracing/config/SamplingConfigurationValidator.java` | 追踪采样规则校验 |
| `auth/security/config/StartupSecurityGate.java` · `StartupSecretKeyChecker.java` | 生产 profile 的启动门闩（比上述体检更早、更严） |

## 相关文档

- [配置参考](index.md)
- [部署 · 生产环境](../deployment/production.md)
- [故障排查 · 常见问题](../troubleshooting/common-issues.md)
