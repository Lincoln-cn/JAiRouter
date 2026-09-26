# Trusted Proxies and Client IP (trusted-proxies)

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-09-26
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

JAiRouter uses the client IP as the rate-limit key for management APIs. When
requests pass through a reverse proxy (Nginx, HAProxy, cloud SLB, Kubernetes
Ingress, and so on), you must tell JAiRouter **which peers are your own
proxies**; otherwise JAiRouter cannot safely derive the real client IP from
forwarding headers such as `X-Forwarded-For`.

## Configuration

| Item | Value |
|------|-------|
| Key | `jairouter.security.rate-limit.trusted-proxies` |
| Type | String, comma-separated list |
| Default | Empty string (`""`) |
| Semantics | `X-Forwarded-For` / `X-Real-IP` are honored only when the direct TCP peer address is in this list; otherwise the connection `remoteAddress` is always used |
| Scope | Rate-limit key for management APIs `/api/auth/api-keys/**` (current release) |
| Hot reload | No; restart required after changes |

```yaml
jairouter:
  security:
    rate-limit:
      # Comma-separated; list your own reverse-proxy addresses
      trusted-proxies: "10.0.0.1, 10.0.0.2"
```

Parsing behavior (current implementation):

- Each entry is trimmed and matched **case-insensitively and exactly** against
  the peer's host address.
- Not configured, or empty = **trust no proxy**; forwarding headers are ignored.
- When XFF holds a multi-hop list, the **last hop** (the one appended by your
  proxy) is used as the client IP. This is correct for a **single** proxy hop;
  for multi-proxy chains see "Multi-Proxy Chains" below.

## Required When Behind a Reverse Proxy

> **⚠️ When deployed behind a reverse proxy, you MUST configure
> `trusted-proxies`.**
>
> Leaving it empty does **not** introduce spoofing risk (the default is
> secure), but it does break availability: every management-API request will
> share one rate-limit counter keyed by the **proxy IP**.

### Consequences of Not Configuring

| Symptom | Cause |
|---------|-------|
| All management-API traffic shares one rate-limit bucket | Rate-limit key = proxy IP, not the real client IP |
| Admins hit 429 during normal use | Default quotas (120 reads/min, 30 writes/min, 10 creates/hour) are shared by everyone |
| Abusive client cannot be identified | Logs show the proxy address |
| Loss of per-client isolation | One noisy client exhausts the quota for all |

### Multi-Proxy Chains

With client → proxy A → proxy B → JAiRouter, `X-Forwarded-For` looks like
`client, proxyA`, and JAiRouter sees `proxyB` as the direct peer.

- The current implementation takes the **last hop**, which yields `proxyA`
  rather than `client`. A **single** proxy hop (client → proxy → JAiRouter) is
  the exact case the current implementation handles correctly.
- For multi-hop chains, list **every proxy you own** in `trusted-proxies`, and
  note the last-hop limitation above. A full "walk right-to-left past trusted
  proxies" algorithm is planned (see the design document linked under
  [Related Documentation](#related-docs)).

## How to Determine the Proxy IP

1. **Inspect JAiRouter's connection peer**: trigger a rate-limit event (or turn
   on DEBUG logging) and read the IP in
   `API Key management write rate limit triggered, IP: ...`
   (Chinese log line: `API Key管理接口写操作速率限制触发, IP: ...`). If that IP
   is the proxy rather than an end user, `trusted-proxies` is not in effect.
2. **Inspect the proxy's outbound interface**:
   - Nginx: the local address the `proxy_pass` upstream uses; usually a private
     address such as `10.0.0.1`, or the `hostname -I` address on JAiRouter's
     subnet.
   - Docker / Kubernetes: usually an address in the bridge / Pod network
     (such as `172.17.0.0/16`, `10.244.0.0/16`). With many replicas, listing
     individual IPs is impractical — this is exactly why CIDR support is
     needed (see below).
3. **Never** put public client addresses in this list — see the security
   warning below.

## Security Warning: Only Trust Your Own Proxies

> **⚠️ `trusted-proxies` is a trust boundary: `X-Forwarded-For` sent by any
> listed address is treated as the real client IP. Only list your own proxy
> addresses or private network ranges.**

### Counter-Example: Handing the Bypass Back to Attackers

```yaml
# ❌ Disaster configuration — equivalent to disabling the protection
jairouter:
  security:
    rate-limit:
      # An address the attacker can originate from (their VPS / rented host)
      trusted-proxies: "203.0.113.50"
```

With this configuration, the attacker connects from `203.0.113.50` (now a
"trusted proxy") and sends `X-Forwarded-For: 1.2.3.4`. Rotating that value
yields fresh rate-limit buckets forever, so **management-API rate limiting is
fully bypassed**.

Once CIDR support ships (planned), the same disaster becomes one line:

```yaml
trusted-proxies: "0.0.0.0/0"   # trust the whole Internet — never do this
```

### Correct Configuration

```yaml
# ✅ Trust only your own reverse proxy
jairouter:
  security:
    rate-limit:
      trusted-proxies: "10.0.0.1"
```

### If the Proxy Itself Is Compromised

The proxy is part of the trusted computing base (TCB). A compromised proxy can
append an arbitrary last XFF hop, and the trust model collapses with it. Keep
the proxy and JAiRouter on a private network / NetworkPolicy-isolated subnet,
and forbid third parties from connecting directly to the JAiRouter port.

## Verifying Whether XFF Is Honored

1. **Read the IP in the 429 log line** (most direct):

   ```
   API Key管理接口写操作速率限制触发, IP: 203.0.113.7, 路径: /api/auth/api-keys
   ```

   - IP is the end-user address → `trusted-proxies` is in effect.
   - IP is the proxy address → not in effect (missing, wrong, or the request
     does not pass through that proxy).

2. **Probe directly** (bypass the proxy, hit JAiRouter):

   ```bash
   # Forged XFF must have no effect: the logged IP should be the curl client
   curl -H "X-Forwarded-For: 1.2.3.4" http://jairouter:8080/api/auth/api-keys
   ```

   If the log shows `1.2.3.4`, a header that should not be trusted was
   honored — re-check the configuration immediately.

3. **Probe through the proxy**:

   ```bash
   curl -H "X-Forwarded-For: 1.2.3.4" https://gateway.example.com/api/auth/api-keys
   ```

   Through a trusted proxy the key should be the **last hop appended by the
   proxy** (the real client), not your forged `1.2.3.4` (`1.2.3.4` only appears
   at the head of the chain, never as the last hop).

## IP Spellings and CIDR

Current release (exact matching):

| Spelling | Matches? | Notes |
|----------|----------|-------|
| `10.0.0.1` | Yes | Recommended; matches `getHostAddress()` output |
| ` 10.0.0.1 ` (surrounding spaces) | Yes | Trimmed automatically |
| `10.0.0.1` (letter case, IPv4) | Yes | Case-insensitive |
| `10.0.0.1:8080` | **No** | Do not write ports; the connection host address has no port |
| `10.0.0.0/8` and other CIDR | **No** (current release) | See below |
| IPv6: `::1` | Depends on JVM output | `getHostAddress()` may return `0:0:0:0:0:0:0:1`; use the exact string shown in logs/diagnostics |
| `[::1]` | **No** | Do not write brackets |
| `0.0.0.0/0` | No (and **never configure it**) | See security warning |

### CIDR Support (Planned)

When proxies run inside a subnet or a replica set, exact IPs are not
operable (rolling updates and scaling change them). CIDR support (IPv4, such
as `10.0.0.0/8`, `172.17.0.0/16`) is planned in #151; IPv6 will support exact
matching first, and IPv6 CIDR is out of scope for the first release. Until CIDR
ships, list **concrete proxy IPs** — network ranges will not match.

## Troubleshooting: Keep RBAC 403 Separate

Behind a reverse proxy, 403 and 429 often appear together but have completely
different causes:

| Symptom | Cause | Action |
|---------|-------|--------|
| **429 Too Many Requests** | Management-API rate limiting. Usually `trusted-proxies` not configured → shared bucket | Configure per this document; confirm with the "Verifying" section |
| **403 Forbidden** | RBAC permission. Unmatched permission rules are denied by default for non-ADMIN (fail-closed) | Check JWT `roles` / `permissions` and `jairouter.security.rbac.unmatched-policy` (default `DENY_ALL`); **this is unrelated to IP** |
| 403 and "I do have permission" | The token lacks the required permission code, or the path is not registered in the permission rules | See [RBAC Permission Management](rbac-permissions.md) and the RBAC coverage self-check |
| Suspecting "my IP is blocked" | The system has **no** per-IP denial logic; 403 is not an IP problem | Separate 403 from 429 first |

Tip: temporarily set `jairouter.security.rbac.unmatched-policy=AUTHENTICATED`
to fall back to the legacy permissive behavior while isolating the RBAC
variable, and **switch it back to `DENY_ALL` afterwards**.

## Related Documentation

- <a id="related-docs"></a>Client IP trust policy design (internal):
  `innerdoc/02-架构与设计/trusted-proxies-client-ip-trust-policy.md`
- [Production Deployment](../deployment/production.md) (Nginx reverse-proxy
  example)
- [Rate Limiting](../configuration/rate-limiting.md)
- [RBAC Permission Management](rbac-permissions.md)
- [Security Features](index.md)
