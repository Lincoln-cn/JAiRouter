# 可信代理与客户端 IP（trusted-proxies）

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-09-26
> **Git 提交**: -
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 会用客户端 IP 作为管理接口限流键。当请求经过反向代理（Nginx、
HAProxy、云 SLB、Kubernetes Ingress 等）时，必须告诉 JAiRouter **哪些对端是
你自己的代理**，否则 JAiRouter 无法安全地从 `X-Forwarded-For` 等转发头中
解析出真实客户端 IP。

## 配置项

| 项 | 值 |
|----|----|
| 键 | `jairouter.security.rate-limit.trusted-proxies` |
| 类型 | 字符串，逗号分隔列表 |
| 默认值 | 空字符串（`""`） |
| 语义 | 只有当 TCP 直连对端地址在该列表中时，才采信 `X-Forwarded-For` / `X-Real-IP`；否则一律使用连接的 `remoteAddress` |
| 生效范围 | 管理接口 `/api/auth/api-keys/**` 的限流键（当前版本） |
| 热更新 | 否，修改后需重启 |

```yaml
jairouter:
  security:
    rate-limit:
      # 逗号分隔；写你自己的反向代理地址
      trusted-proxies: "10.0.0.1, 10.0.0.2"
```

解析行为（当前实现）：

- 每一项去除首尾空白，与连接对端的主机地址做**不区分大小写的精确匹配**。
- 未配置或配置为空 = **不信任任何代理**，转发头一律忽略。
- XFF 为多跳列表时，取**最后一跳**（由你的代理追加的那一跳）作为客户端 IP。
  这在**一跳**代理拓扑下是正确的；多跳代理链路请参阅下文「多代理链路」。

## 反向代理部署必须配置

> **⚠️ 部署在反向代理之后时，必须配置 `trusted-proxies`。**
>
> 不配置虽然**不会引入伪造风险**（默认安全），但会带来可用性问题：
> 所有管理接口流量的限流键都会变成**代理的 IP**，共享同一个计数器。

### 不配置的后果

| 现象 | 原因 |
|------|------|
| 全部管理 API 请求共享同一个限流桶 | 限流键 = 代理 IP，而不是真实客户端 IP |
| 管理员正常使用即触发 429 | 默认限额（读 120 次/分、写 30 次/分、创建 10 次/时）被全员共享 |
| 无法定位滥用者 | 日志里出现的 IP 是代理地址 |
| 失去按客户端隔离 | 一个人刷接口会把所有人一起限流掉 |

### 多代理链路

客户端 → 代理 A → 代理 B → JAiRouter 时，`X-Forwarded-For` 形如
`client, proxyA`，JAiRouter 看到的对端是 `proxyB`。

- 当前实现取**最后一跳**，会得到 `proxyA` 而不是 `client`。
  因此**一跳代理**（客户端 → 代理 → JAiRouter）才是当前实现的精确适用场景。
- 多跳链路请把链路上**所有自家代理**都写进 `trusted-proxies`，并注意当前
  末跳语义的限制。更完整的"从右向左跳过可信代理"算法已列入规划
  （见[相关文档](#related-docs)中的设计文档）。

## 如何确定代理 IP

1. **直接看 JAiRouter 的连接对端**：触发一次限流（或开启 DEBUG 日志），
   观察 `API Key管理接口写操作速率限制触发, IP: ...` 中的 IP。该 IP 若是
   代理地址而非终端用户地址，说明还没配 `trusted-proxies`。
2. **看代理的出站接口**：
   - Nginx：`proxy_pass` 上游所在的本机地址；通常就是 `10.0.0.1` 这类
     内网地址，或 `hostname -I` 中与 JAiRouter 同网段的那个地址。
   - Docker / K8s：通常是网桥 / Pod 网段（如 `172.17.0.0/16`、
     `10.244.0.0/16`）里的某个地址；副本多时逐个 IP 不现实，这也是
     需要 CIDR 的原因（见下文）。
3. **不要**把公网客户端地址写进这个列表——见下面的安全警告。

## 安全警告：只信任自己的代理

> **⚠️ `trusted-proxies` 是信任边界：列入的地址发出的 `X-Forwarded-For`
> 会被当成真实客户端 IP。只允许填你自己的代理地址或内网网段。**

### 反例：把绕过还给攻击者

```yaml
# ❌ 灾难配置 —— 相当于关闭防护
jairouter:
  security:
    rate-limit:
      # 攻击者可以发起连接的地址（他们的 VPS / 租用主机）
      trusted-proxies: "203.0.113.50"
```

如此配置后，攻击者从 `203.0.113.50`（现已成"可信代理"）发起请求并带上
`X-Forwarded-For: 1.2.3.4`。轮换该值即可不断获得新的限流桶，
**管理接口限流被完全绕过**。

待 CIDR 支持发布后，同样的灾难只需一行：

```yaml
trusted-proxies: "0.0.0.0/0"   # 信任全网 —— 绝不要这么配
```

### 正确配置

```yaml
# ✅ 只信任自家反代
jairouter:
  security:
    rate-limit:
      trusted-proxies: "10.0.0.1"
```

### 代理自身被攻陷

代理属于信任计算基（TCB）。代理一旦被攻陷，它可以追加任意 XFF 末跳，
信任模型随之失效。请把代理与 JAiRouter 放在私网 / NetworkPolicy 隔离的
网段内，并禁止第三方直连 JAiRouter 端口。

## 验证 XFF 是否被采信

1. **看 429 日志里的 IP**（最直接）：

   ```
   API Key管理接口写操作速率限制触发, IP: 203.0.113.7, 路径: /api/auth/api-keys
   ```

   - IP 是终端用户地址 → `trusted-proxies` 已生效。
   - IP 是代理地址 → 未生效（没配、配错，或请求不经过该代理）。

2. **主动探测**（直连 JAiRouter，绕过代理）：

   ```bash
   # 伪造 XFF 必须无效：日志中的 IP 应是 curl 客户端的地址
   curl -H "X-Forwarded-For: 1.2.3.4" http://jairouter:8080/api/auth/api-keys
   ```

   若此时日志出现 `1.2.3.4`，说明有不该信任的头被采信了——立即复查配置。

3. **经代理探测**：

   ```bash
   curl -H "X-Forwarded-For: 1.2.3.4" https://gateway.example.com/api/auth/api-keys
   ```

   经可信代理时，键应是**代理追加的末跳**（即真实客户端），而不是你伪造的
   `1.2.3.4`（`1.2.3.4` 只会出现在链头，不是末跳）。

## IP 书写形式与 CIDR

当前版本（精确匹配）：

| 书写形式 | 是否匹配 | 说明 |
|----------|----------|------|
| `10.0.0.1` | 匹配 | 推荐写法，与 `getHostAddress()` 输出一致 |
| `10.0.0.1`（前后空白） | 匹配 | 自动 trim |
| `10.0.0.1`（大小写，IPv4） | 匹配 | 不区分大小写 |
| `10.0.0.1:8080` | **不匹配** | 配置里不要写端口；连接主机地址不含端口 |
| `10.0.0.0/8` 等 CIDR | **不匹配**（当前版本） | 见下方说明 |
| IPv6：`::1` | 视 JVM 输出而定 | JVM 的 `getHostAddress()` 可能返回 `0:0:0:0:0:0:0:1`；请以日志/诊断中出现的实际字符串为准 |
| `[::1]` | **不匹配** | 不要写方括号 |
| `0.0.0.0/0` | 不匹配（但也**绝不要配**） | 见安全警告 |

### CIDR 支持（规划中）

代理跑在网段 / 副本集合里时，精确 IP 不可运维（副本滚动、扩容都会变）。
CIDR 支持（IPv4，如 `10.0.0.0/8`、`172.17.0.0/16`）已列入 #151 的实现计划；
IPv6 将支持精确匹配，IPv6 CIDR 暂不在首版范围。在 CIDR 发布前，请填写
**具体代理 IP**，不要写网段（写了也不匹配）。

## 故障排除：和 RBAC 403 分开看

反向代理部署下，403 与 429 常同时出现，但原因完全不同：

| 症状 | 原因 | 处理 |
|------|------|------|
| **429 Too Many Requests** | 管理接口限流。多半是 `trusted-proxies` 未配 → 共享限流桶 | 按本文档配置；按「验证」一节确认生效 |
| **403 Forbidden** | RBAC 权限。未命中权限规则且非 ADMIN 时默认拒绝（fail-closed） | 查 JWT `roles` / `permissions`、`jairouter.security.rbac.unmatched-policy`（默认 `DENY_ALL`），**与 IP 无关** |
| 403 且"我明明有权限" | 令牌缺少所需权限码，或请求路径未登记到权限规则 | 参见 [权限管理](rbac-permissions.md) 与 RBAC 覆盖自检 |
| 怀疑"IP 被封" | 系统**没有**按 IP 拒绝的逻辑；403 不是 IP 问题 | 先分清 403 / 429 |

排障小技巧：可临时设置 `jairouter.security.rbac.unmatched-policy=AUTHENTICATED`
回退到旧的放行语义，以隔离 RBAC 变量，**排查完必须改回 `DENY_ALL`**。

## 相关文档

- <a id="related-docs"></a>客户端 IP 信任策略设计（内部）：`innerdoc/02-架构与设计/trusted-proxies-client-ip-trust-policy.md`
- [生产环境部署](../deployment/production.md)（Nginx 反代示例）
- [限流配置](../configuration/rate-limiting.md)
- [权限管理](rbac-permissions.md)
- [安全功能](index.md)
