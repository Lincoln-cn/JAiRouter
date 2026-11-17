# 登录保护配置

登录接口的失败频控有三个可调维度：失败阈值、统计窗口、锁定时长。同一份配置既决定 429 何时触发，也决定锁多久。

## 配置项

`config/auth/login-guard.yml`（随 `application.yml` 一起导入）：

```yaml
jairouter:
  auth:
    login-bruteforce:
      max-failures: 5          # 同一 IP + 同一用户名的失败次数阈值
      max-failures-per-ip: 30  # 同一 IP 跨用户名的失败次数阈值
      window-minutes: 15       # 失败计数窗口
      lock-minutes: 15         # 达到阈值后的锁定时长
```

dev profile（`application-dev.yml`）单独放宽为 `20 / 100 / 5 / 5`，避免本地调试被 5 次输错卡住。

## 生效方式：首次落库，之后以数据库为准

这四个值在首次启动时被写入 `security_login_guard_config` 单行表（`config_id = 'default'`，`updated_by = 'system'`），
之后每次启动都以库里的行为准 —— 与 `jwt_accounts` 的口径一致。因此：

- **改 YAML 只对还没初始化的部署生效**；跑过一次的实例要改阈值，请用控制台「安全 → 登录保护」页，或 `PUT /api/security/login-guard`。
- 改阈值会**立即热改**运行中实例的内存阈值，并写 `LOGIN_GUARD_CONFIG_CHANGED` 审计事件；不需要重启。
- 已锁定的主体**不会**因为放宽阈值而自动解开（放宽上限不等于放行）。

## 运维接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/security/login-guard` | 当前生效阈值 + 最后修改人/时间 |
| PUT | `/api/security/login-guard` | 改四个阈值（四项必须同时给） |
| GET | `/api/security/login-guard/locked` | 当前仍被锁定的主体 |
| POST | `/api/security/login-guard/unlock` | 解除锁定（空体 = 全部） |

## 多副本注意

失败计数保存在**实例内存**里，每个副本各自计数。所以被锁定的用户在 `/locked` 里只反映当前实例，
解除也需逐个实例调用（或等待 `lock-minutes` 到期自动失效）。这是当前实现的已知边界，未做跨副本共享计数。

## 排障

`429` + `RATE_LIMITED_LOGIN` 表示命中阈值，响应文案会带剩余时间；日志里对应的 WARN 会打印
`scope`（`account` = IP+用户名，`ip` = 仅 IP）、`threshold`、`windowMinutes`、`lockMinutes`。
