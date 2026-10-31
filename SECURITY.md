# Security Policy

JAiRouter is a self-hosted gateway that holds upstream model credentials and sits in front of
every model call, so security reports are taken seriously.

## Supported versions

Security fixes land on the latest release line (currently **3.5.x**) and on `master`.
Older minor versions are not patched — upgrade to the latest release to receive fixes.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub private vulnerability reporting:

<https://github.com/Lincoln-cn/JAiRouter/security/advisories/new>

A useful report contains:

- the affected version (`/actuator/info` or the Docker image tag) and, if possible, the commit;
- how the gateway is deployed (standalone, behind a reverse proxy, Kubernetes, number of replicas);
- reproduction steps or a proof of concept, and the impact you observed;
- any configuration that differs from the defaults.

Reports are handled on a best-effort basis: there is no contractual SLA, but valid reports are
acknowledged, reproduced, fixed on `master`, and shipped in the next release. Credit is given in
the release notes unless you ask to stay anonymous.

## Scope

In scope:

- authentication and authorization — API keys, JWT, RBAC rules, admin endpoints;
- SSRF and request forgery on outbound model calls;
- response sanitization and audit logging;
- configuration handling, secret storage and the container images published from this repository.

Out of scope:

- vulnerabilities in the upstream model servers (Ollama, vLLM, GPUStack, …) that the gateway
  proxies to — report those upstream;
- deployments that deliberately weaken the built-in protections, for example by setting
  `JAIRouter_SKIP_AUTH_WARNING=true`, exposing `/actuator/**` to the internet, or leaving the
  default administrator password in place.

## Known and accepted findings

Two CodeQL alerts are closed on the Security tab with an explanation rather than a code change:

- `java/spring-disabled-csrf-protection` — the gateway is a stateless API (API key or JWT header,
  `NoOpServerSecurityContextRepository`, no cookies, no session), so there is no credential a
  browser would attach automatically. If cookie-based sessions are ever introduced, CSRF must be
  re-evaluated.
- `java/ssrf` on the outbound HTTP client — every outbound client is bound to
  `SsrfGuardedAddressResolverGroup`, which rejects loopback, link-local, metadata and private
  ranges at DNS resolution time (issue #266), and configuration writes go through
  `UrlSecurityValidator`. Forwarding to internal model servers is the product's purpose.

## Hardening checklist

Before exposing a deployment to untrusted clients:

1. Set `JWT_SECRET` and `INITIAL_ADMIN_PASSWORD` to strong random values — production profiles
   refuse to start otherwise.
2. Change the administrator password after the first start; the initial password is printed once.
3. Put the gateway behind TLS and keep `/actuator/**` restricted to the operations network.
4. Configure `trusted-proxies` when running behind a reverse proxy, so client IP based rate
   limiting cannot be influenced by forged forwarding headers.
5. Review the [security documentation](https://jairouter.com) for RBAC, sanitization and audit
   settings.
