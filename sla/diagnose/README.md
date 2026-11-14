# jairouter-doctor

只读诊断工具。回答一个问题：**此刻这条链路能不能通，不能通是哪一层的问题。**

## 为什么需要它

健康检查说「好」、请求说「坏」、日志不说为什么 —— 这是网关最难查的一类故障。本项目已经踩过三次：

| 缺陷 | 现象 | 为什么难查 |
| --- | --- | --- |
| `adapter` 没传到实例（#1004） | 实例 HEALTHY，请求 502 | 健康检查只测端口 |
| Netty 解析器与 glibc 不一致（#1014） | 实例 HEALTHY，请求 502 | `getent`/`wget` 都正常 |
| 熔断器打开（#1010） | 503「all in circuit breaker state」 | 把真实原因完全盖住 |

三者的共同点是**探测手段与实际调用路径不是同一条**。所以 doctor 全部用与真实请求相同的路径去测。

## 用法

```bash
# 最常见：对着本机网关跑
node scripts/doctor/doctor.mjs --base-url http://127.0.0.1:8080 --token "$JWT"

# 只要结构化输出给自动化用
node scripts/doctor/doctor.mjs --base-url ... --token ... --format json

# 不跑真实调用（只想看配置与连通性）
node scripts/doctor/doctor.mjs --base-url ... --token ... --no-chat

# 指定服务类型（默认只查 chat）
node scripts/doctor/doctor.mjs --base-url ... --token ... --services chat,embedding,rerank
```

token 也可以走环境变量：`JAIR_TOKEN=... node scripts/doctor/doctor.mjs`。

取 token：

```bash
curl -s -X POST http://127.0.0.1:8080/api/auth/jwt/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<你的密码>"}' | jq -r .data.token
```

## 检查项

| 分组 | 检查什么 | 失败意味着 |
| --- | --- | --- |
| 服务面 | `/actuator/health` 是否 UP、版本 | 进程没起来或端口不对 |
| 实例配置 | `name`/`baseUrl`/`path` 非空、`adapter` 已注册、`status` active | 网关会对这类实例直接报错 |
| 出站连通性 | 分三层：DNS 解析 → TCP 建连 → HTTP 探活 | 能定位到具体哪一层断 |
| 熔断器 | 是否有 OPEN 的实例 | OPEN 会遮蔽真实失败原因 |
| 真实调用 | 一次最小 `chat/completions` | 这一条才是「能不能用」的答案 |

**为什么连通性要分三层**：本轮三个缺陷恰好各自落在不同层 —— adapter 是配置层、DNS 是解析层、熔断是状态层。只测一种，另外两种就查不出来。

## 退出码

| 码 | 含义 |
| --- | --- |
| 0 | 全通过 |
| 1 | 有警告（例如 doctor 与网关不在同一网络，DNS 探不到） |
| 2 | 有失败 |

## 已知局限

**doctor 的网络位置决定了它能看到什么。** 跑在宿主机、网关跑在容器里时，DNS 那一项探不到容器内部的名字 —— 这不是网关的问题，工具会给出 WARN 而不是 FAIL，并提示看下面「真实调用」的结果。

要判断网关自己的解析行为，把 doctor 跑进同一个网络里：

```bash
docker compose -f deploy/demo/docker-compose.yml exec jairouter \
  node /app/doctor/doctor.mjs --base-url http://127.0.0.1:8080 --token "$JWT"
```

（需要镜像里带上这个脚本，当前镜像没有。）

## 不做的事

- **不自动修复**。擅自改配置会把「可观测性」变成「另一个故障源」。
- **不做压测**。那是 `scripts/perf/` 的事，doctor 只测单次。
- **不替代指标**。Prometheus 看趋势，doctor 看「此刻这一条链路通不通」。
