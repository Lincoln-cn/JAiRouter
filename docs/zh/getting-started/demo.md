# 5 分钟演示

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-11-08
> **适用版本**: v3.8.0+
> **作者**: Lincoln
<!-- /版本信息 -->

不需要任何模型服务，也不需要装 JDK：仓库里带了一套演示环境，用两个容器加一个脚本就能把网关的主链路走一遍。

适合第一次接触这个项目、想先看看「它到底做什么」的人。读完并跑完这一页，你会看到网关怎么把请求转给后端、怎么换模型、怎么复用缓存、怎么在发给上游之前脱敏。

## 演示由什么组成

| 资产 | 作用 |
|------|------|
| `deploy/demo/docker-compose.yml` | 起两个容器：网关（`jairouter-demo`，端口 8080）和 mock 上游（`jairouter-demo-upstream`，端口 18080） |
| `deploy/demo/mock-upstream.mjs` | 一个零依赖的 OpenAI 兼容上游，固定回 `Hello from mock.`；它代替真实模型服务，所以演示不需要 GPU、不需要 API Key |
| `scripts/demo/run-demo.mjs` | 对网关跑四步，每步打印「做了什么 / 预期 / 实际」，失败会指出是哪一步 |

三者都在仓库里，没有任何外部依赖。

## 三步跑起来

前置条件：Docker 20.10+ 与 Docker Compose v2；Node.js 18+（脚本只用标准库，不需要 `npm install`）。

```bash
# 1. 拉镜像（保证拿到 v3.8.0 及之后的镜像，见文末常见问题）
docker compose -f deploy/demo/docker-compose.yml pull

# 2. 起两个容器
docker compose -f deploy/demo/docker-compose.yml up -d

# 3. 等两个服务都 healthy（通常 20~60 秒）
docker compose -f deploy/demo/docker-compose.yml ps

# 4. 跑演示
node scripts/demo/run-demo.mjs
```

`ps` 里两行都显示 `healthy` 时再跑脚本；只有一个 healthy 就跑，第 1 步会直接失败。

## 脚本会做哪四步

| 步骤 | 做什么 | 预期 |
|------|--------|------|
| 1 基础调用 | 登录拿 token，向 `qwen3:4b` 发一次对话请求 | HTTP 200，响应体是 OpenAI 兼容形状；顺带打印网关暴露的模型名（默认 7 个） |
| 2 同一问题打两个模型 | 用两个对话模型问同一句话，对比两边输出 | 默认只有 1 个对话实例，脚本会打印一行提示并跳过 —— 这是预期的，不算失败 |
| 3 重复请求走缓存 | 把同一句话再问一次，比较两次耗时 | 第二次明显更快时判「缓存生效」；没提速会提示可能是条目过期（默认 10 分钟） |
| 4 可逆脱敏 | 发一段带身份证号和手机号的文本 | 客户端拿回的仍是原文；判定要看上游收到了什么（见下一节） |

成功时最后打印「通过 N 项，失败 N 项」以及控制台地址 `http://127.0.0.1:8080/admin/`，账号 `admin`。

> 💡 脚本里的用户名/密码/网关地址都能覆盖：`DEMO_BASE`（默认 `http://127.0.0.1:8080`）、`DEMO_USER`（默认 `admin`）、`DEMO_PASSWORD`（默认 `DemoOnlyPassw0rd`）。把网关换到别的端口时，只改 `DEMO_BASE` 即可。

## 可逆脱敏：开与不开差在哪

这一步的判定在上游侧，不在客户端 —— 可逆脱敏的用途就是「上游看不到明文、客户端拿回原文」，所以客户端看到的响应永远一样。

看 mock 上游收到的最后一条用户消息：

```bash
docker logs jairouter-demo-upstream | grep lastUser
```

```powershell
docker logs jairouter-demo-upstream | Select-String lastUser
```

两种形状，含义相反：

| 日志形状 | 含义 |
|----------|------|
| `lastUser placeholders=[PII_1],[PII_2]` | 上游只收到占位符，脱敏生效 |
| `lastUser plaintext(61B)` | 上游收到了明文，脱敏没开（默认就是关的） |

日志只打形状和字节数，不打明文 —— 判定不需要明文，把它写进容器日志反而是新的泄漏面。

默认关闭（`jairouter.pii.reversible.enabled: false`）。开启方式：在 `deploy/demo/docker-compose.yml` 的 `jairouter` 服务的 `environment` 下加一行，再重启网关：

```yaml
    environment:
      JAIRouter_PII_REVERSIBLE_ENABLED: "true"
```

```bash
docker compose -f deploy/demo/docker-compose.yml up -d
node scripts/demo/run-demo.mjs
docker logs jairouter-demo-upstream | grep lastUser
```

2026-11-08 在本机 Docker Desktop（v3.8.0 镜像）实测：未开启时该行是 `lastUser plaintext(61B)`，加上环境变量重启后变成 `lastUser placeholders=[PII_1],[PII_2]`，脚本四步全部通过。

> ⚠️ 开启后网关会改写发给上游的内容 —— 这是改变请求语义的行为，生产上要按业务确认后再开。配置项与规则见 [可逆脱敏](../security/data-sanitization.md)。

## 演示环境用了哪些设置

`deploy/demo/docker-compose.yml` 里网关的配置，每一项都是为了让演示「一键起、不打扰本机」：

| 变量 | 值 | 为什么 |
|------|-----|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` | 走生产 profile（启动门闩会检查密钥），避免演示环境与生产行为不一致 |
| `JWT_SECRET` | 演示用固定值 | 演示环境不需要真密钥；生产必须换成自己生成的 |
| `INITIAL_ADMIN_PASSWORD` | `DemoOnlyPassw0rd` | 演示账号密码；可用 `DEMO_PASSWORD` 覆盖 |
| `MODEL_UPSTREAM_BASE_URL` | `mock-upstream:18080`（容器名与端口） | 把内置 `services.yml` 里所有实例的上游指向 mock |
| `DATABASE_URL` | `jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1` | 内存库，不落盘，重启即清空 |
| `MODEL_RATELIMIT_ENABLED` | `false` | 关掉限流，避免演示被 429 打断 |
| `MODEL_SERVICES_CHAT_RATELIMIT_ENABLED` | `false` | 同上，服务级限流 |
| `LOGGING_LEVEL_ORG_UNREAL_MODELROUTER` | `INFO` | 日志别太吵 |

## 与生产部署的区别

这套 compose 是**演示环境，不是生产部署模板**。四处刻意做得不适合生产：

1. **数据库在内存里** —— 重启容器，配置、调用记录、API Key 全部清空。
2. **限流关着** —— 生产上这是网关的主要职责之一。
3. **密钥与密码是公开的演示值** —— 生产必须自己生成 `JWT_SECRET` 与管理员密码，否则启动门闩会直接拒绝启动。
4. **上游是 mock** —— 它固定回一句话，不代表任何真实模型的质量。

生产怎么部署，见 [生产环境部署](../deployment/production.md) 与 [Docker 部署](../deployment/docker.md)。

## 停止与清理

```bash
# 停容器，保留镜像（下次 up -d 很快）
docker compose -f deploy/demo/docker-compose.yml down

# 连网络一起删干净
docker compose -f deploy/demo/docker-compose.yml down -v
```

## 常见问题

**8080 端口被占用** —— 改 compose 里 `jairouter` 服务的端口映射左值（如 `8081:8080`），然后 `DEMO_BASE=http://127.0.0.1:8081 node scripts/demo/run-demo.mjs`。

**容器一直 unhealthy 或脚本第 1 步返回 502** —— 先看 `docker compose -f deploy/demo/docker-compose.yml ps` 与 `docker logs jairouter-demo`。如果日志里是「下游服务连接失败」，多半是镜像太旧：早期镜像在容器网络里解析 `mock-upstream` 这类容器名会失败。`docker compose -f deploy/demo/docker-compose.yml pull` 取到 v3.8.0 及之后的镜像后重起即可。

**第 2 步总是跳过** —— 内置配置只有一个对话实例 `qwen3:4b`。想真跑「两个模型对比」，在控制台里再加一个对话实例（上游指向同一个 mock 即可），或挂载自己的 `services.yml`。客户端代码不需要改，这正是网关的意义。

**第 3 步没提速** —— 缓存条目默认 10 分钟过期，重启容器后第一次请求也不会命中；另外多副本部署时请求可能落到没缓存的实例上。这不是失败，脚本会如实提示。

**登录失败** —— 脚本默认用 `DemoOnlyPassw0rd`。如果你改过 compose 里的 `INITIAL_ADMIN_PASSWORD`，用 `DEMO_PASSWORD` 传新密码。

## 相关文档

- [安装指南](installation.md) —— 想用 JAR 或源码运行
- [快速开始](quick-start.md) —— 接自己的模型服务、逐步配置
- [Web 控制台](web-console.md) —— 演示里打印的控制台地址能看什么
- [可逆脱敏](../security/data-sanitization.md) —— 占位符、TTL 与规则配置
