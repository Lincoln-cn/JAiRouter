# 生命周期脚本：安装 / 停机 / 卸载

`deploy/lifecycle/` 提供 JAiRouter 的**装、停、卸**三件事的脚本化实现。它补齐的是
`sla/` 工具链原本只覆盖「巡」与「诊」的缺口（issue #1108）。

> `sla/` 目录在 `.gitignore` 里、随商业版分发，不进开源仓库；这份脚本放在 `deploy/` 下，
> 是开源侧可直接使用的等价资产。

## 能力边界

| 动作 | Linux | Windows | 说明 |
| --- | --- | --- | --- |
| 安装 | `linux/install.sh` | `windows/install.ps1` | 环境检测 + 托管形态降级 + 装完自检 |
| 停机 | `systemctl stop` / 脚本内 | `uninstall.ps1` / WinSW | Linux 与 WinSW 服务都能优雅停机 |
| 卸载 | `linux/uninstall.sh` | `windows/uninstall.ps1` | 默认只删程序，`--purge`/`-Purge` 才删数据 |
| 升级 | 重跑 install 脚本 | 重跑 install 脚本 | 幂等：不覆盖已有密钥与数据 |

**不做的事**（刻意）：

- 不 DROP 数据库表、不清 Redis 键、不删镜像。这些是共享资源，可能同时被别的实例或别的系统用着，
  卸载脚本只把该看的命令打印出来。
- 不接管 `docker compose` 之外的编排。K8s 请用 `deploy/k8s/apply.sh`（见 [部署顺序](../../k8s/apply.sh)）。

## 安装：检测与降级

两条平台都是同一个思路：**先在内存里把所有检测跑完，任何一项不满足就退出，磁盘保持原样**。

Linux 的降级链（`--mode auto` 默认）：

1. `systemd` + 系统 Java 17+ → 注册 unit，开机自启、崩溃自动重启
2. `docker` + `compose` + systemd → 生成 compose，`restart: unless-stopped`。**有 docker 不等于有 compose**：只装了引擎、没装 compose 插件的机器（Ubuntu 的 `docker.io` 包就是这样）判为容器模式不可用，而不是装完才发现 `docker compose up` 不存在
3. `foreground` → `setsid nohup` 后台跑，**不会开机自启**，脚本会明确打印这一点
4. 都不满足 → 装前失败，提示用离线 bundle 自带的运行时

Windows 的降级链（`-Mode auto` 默认）：

1. 管理员 + WinSW → 注册服务（`startmode=Automatic` + `delayedAutoStart`）
2. 管理员但没给 WinSW → 计划任务（`/sc onstart /ru SYSTEM`），**停机只能是强制终止**
3. 非管理员 → 前台运行，提示「安装时请以管理员身份运行」
4. 都没有 → 前台运行 + 明确警告不会自启

每次降级都会打印**为什么**降级，而不是静默选一条路。

### 目标目录不是空的怎么办

`--prefix` 指向的目录已经有内容、但里面没有本脚本写的 `jairouter.json` 时，安装会**直接拒绝**并
退出（写盘之前），因为那个目录很可能是别的部署的数据卷 —— 接手时 `chown` 一遍就足以把别人的
挂载目录改坏。确认要覆盖再加 `--force`。

`--dry-run` 同样会先做这项归属判断，所以可以拿它去探一个目录「能不能装」而不动磁盘。

### docker 模式的镜像 tag

默认 `latest` 只为快速起步；生产请用 `--image-tag v3.8.5` 固定版本，否则某天重新 `up` 会把版本
一起换掉。compose 文件里同时留了 `JAIRouter_IMAGE` 环境变量覆盖口。

### 装完自检

两个脚本最后都真的去请求 `http://127.0.0.1:<port>/actuator/health`，超时（默认 60s / 90s）
则回滚：停实例、删服务/任务定义，**保留数据目录与密钥**，然后非零退出。看到进程在不算成功。

### 生成的密钥

首次安装会生成 `JWT_SECRET` 与 `INITIAL_ADMIN_PASSWORD`：

- Linux：`/etc/jairouter/<prefix 目录名>.env`（默认前缀下就是 `/etc/jairouter/jairouter.env`），`chmod 600`
- Windows：`<Prefix>\config\jairouter.env`，`icacls` 收紧到 SYSTEM + Administrators

重复执行**不会覆盖**已有密钥 —— 换了密钥所有已签发的 token 立刻失效。

同一台机器上装第二个实例时，密钥文件、容器名、systemd 之外的一切都按 `--prefix` 的目录名区分：
`/opt/jairouter` → `/etc/jairouter/jairouter.env` + 容器 `jairouter`；
`/opt/jairouter-test` → `/etc/jairouter/jairouter-test.env` + 容器 `jairouter-test`。
这样两个实例不会共用密钥，也不会互相顶掉容器。

## 停机语义（这是本目录存在的主要原因）

| 方式 | 是否执行 shutdown hook | 实测 |
| --- | --- | --- |
| Linux `systemctl stop`（SIGTERM） | ✅ | `KillSignal=SIGTERM` + `TimeoutStopSec=40`（> Spring 的 30s） |
| **WinSW 服务 `sc stop`** | ✅ | **1.43s** 内 `GRACEFUL_COMMENCING` / `GRACEFUL_COMPLETE`，16–28 行 hook 日志 |
| 计划任务 `taskkill /F` | ❌ | 无控制台宿主，只能硬杀 —— 脚本会打印「在途请求会中断」 |
| 前台 `Ctrl+C` | ✅ | 与 Linux 的 SIGTERM 等价 |
| `taskkill /PID`（不带 `/F`） | ❌ | **返回 SUCCESS 但 shutdown hook 一行都不跑**，等价硬杀 |
| 直接关闭控制台窗口 | ❌ | 等价 `taskkill /F` |

`TimeoutStopSec` 与 `<stoptimeout>` 必须大于 `spring.lifecycle.timeout-per-shutdown-phase`
（仓库默认 30s），否则托管进程会在 Spring 还没排空请求时补一刀。`LifecycleAssetsTest` 对这两个值有断言。

## 卸载

```bash
sudo ./linux/uninstall.sh                  # 只删程序
sudo ./linux/uninstall.sh --purge          # 连数据、日志、密钥一起删（二次确认）
```

```powershell
.\windows\uninstall.ps1                    # 只删程序
.\windows\uninstall.ps1 -Purge             # 连数据、日志、密钥一起删（二次确认）
```

默认删的程序资产：`lib/model-router.jar`、`docker/docker-compose.yml`（compose 定义）、
`jairouter.json`（安装标记），以及服务/计划任务注册与容器网络。
默认保留：`data/`（H2 文件库或外部库的本地缓存）、`logs/`、`config/`（覆盖层）、密钥。
`--purge` / `-Purge` 才删数据，且一定会先打印将删哪些路径再确认。

卸载时如果 `docker compose` 不可用，脚本会明确打印「容器可能仍在运行」并给出人工处理命令 ——
不会在 `down` 失败的情况下照样宣布「已停止并移除」。

## 安装记录

`install.ps1` 会写 `<Prefix>\jairouter.json`，`doctor` 类工具可以据此知道实际安装形态：

| 字段 | 含义 |
| --- | --- |
| `mode` | `service` / `task` / `foreground` |
| `prefix` / `port` / `profile` | 安装目录、端口、Spring profile |
| `javaBin` / `jar` | 实际使用的 JVM 与 jar 路径 |
| `workingDirectory` / `logDirectory` | **日志落点**：`logback-spring.xml` 用相对路径 `logs`，所以日志在 `<workingDirectory>\logs` |
| `service.kind` | `winsw` / `schtasks` / `none` |

Linux 侧同样写 `$PREFIX/jairouter.json`（字段：`mode` / `prefix` / `port` / `profile` / `envFile` /
`containerName` / `dataDirectory` / `logDirectory`），另外两个约定是：前缀默认 `/opt/jairouter`，
unit 名固定 `jairouter.service`（同机多实例只用 `--prefix` 区分数据与密钥，不注册第二个 unit）。

## 离线安装

服务器不联网时，把三样东西放进同一个目录即可：

1. `model-router.jar`
2. 一份 17+ 的 JRE（`jlink` 裁剪版约 45.6MB，模块清单见 [`docker/jre-modules.txt`](../../../docker/jre-modules.txt)）
3. 本目录的脚本（Windows 另加 `WinSW.NET4.exe` / `WinSW.NET6.exe`）

然后把 `JAVA_HOME`/`PATH` 指向 bundle 里的运行时，再执行安装脚本。Linux 的 `install.sh`
在检测不到系统 java 时会明确提示走这条路，而不是报一个含糊的「找不到 java」。
