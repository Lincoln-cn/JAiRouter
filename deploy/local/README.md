# 本机部署（长期运行）

这个目录给的是「在自己机器上长期跑一个」的最小方案，不是演示环境。

和 `deploy/demo/` 的区别：

| | demo | local |
|---|---|---|
| 上游 | mock 服务（容器） | 你自己的模型服务 |
| 数据库 | 内存库，每次启动重建 | H2 文件库，重启保留 |
| 面向 | 「看一眼效果」 | 「一直开着用」 |
| 启动 | `docker compose up` | `.\start.ps1` |

## 前置

- JDK 17+
- 一个可访问的模型服务（Ollama / vLLM / 云 API 都行）

## 启动

```powershell
# 1. 改配置：把上游地址指到你的模型服务
notepad deploy\local\application-local.yml

# 2. 启动
.\deploy\local\start.ps1
```

启动后：

- 控制台：http://127.0.0.1:8080/admin/
- 健康检查：http://127.0.0.1:8080/actuator/health
- 指标：http://127.0.0.1:8080/actuator/prometheus

默认账号 `admin`，密码见 `application-local.yml` 里的 `INITIAL_ADMIN_PASSWORD`。

## 停止

```powershell
.\deploy\local\stop.ps1
```

## 数据在哪

| 内容 | 位置 |
|---|---|
| 数据库 | `data/jairouter.mv.db` |
| 日志 | `logs/` |
| 上传/缓存文件 | `data/` |

**备份** = 停服务后拷 `data/jairouter.mv.db`。这是个单文件 H2 库，不要在线拷贝。

## 常见问题

**改了上游地址但没生效** —— 用文件库时实例配置会被落库、之后以库为准。要么在控制台里改，要么删掉 `data/jairouter.mv.db` 重新初始化。

**启动报「实例配置不完整」** —— 环境变量 `MODEL_SERVICES_<TYPE>_INSTANCES_0_*` 这类写法会替换整个实例列表，不要用它改单个实例。改 `application-local.yml`。

**只想试一下不想留数据** —— 用 `deploy/demo/`，那个用内存库。

## 为什么不用 Docker

`deploy/demo/docker-compose.yml` 里有全容器的版本，但**在本机（Windows + Docker Desktop）上实测网关容器连不上同网络的 mock 服务**：TCP 端口可达（健康检查 `socket_connect` 通过）但 WebClient 应用层连接失败，mock 侧收不到请求。同一份 jar 在宿主机上跑完全正常。

所以本机长期运行推荐直接跑 jar；容器形态留给 Linux 主机或 K8s 环境（`docs/*/deployment/kubernetes.md`）。
