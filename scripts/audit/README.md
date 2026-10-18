# 静态面审计（2026-10-17）

本目录是这次审计的**可执行资产**：每个轴一个自校准探测器 + 一份它的报告。
审计结论落在 GitHub issue 台账（下方索引），本文件只写**方法与覆盖边界**。

## 为什么会有这次审计

目标：对「死代码 / 配置项不匹配或没实现 / 未实现功能代码 / 前端界面与代码 / 接口测试」做一次静态面审计，
要求**每轴先写可自证的检测器并校准**，跑出命中清单，逐条落 issue。

范围与边界（用户裁定）：

* **不含 `innerdoc/`**（内部设计笔记与会话记忆不在审计目标内）；
* **接口测试纳入审计**，含静态对账与动态实跑；
* 只审静态面，**不改运行时行为**；修复另走 PR。

## 一句话结论

| 轴 | 结论 |
|---|---|
| 死代码 | 后端 **12 个零引用类型**（无注解、且有并行活实现）、**3 个无实现接口**、**12 个未使用私有方法**、**15 个注入但从不读取的 `private final` 字段**；前端 **154/474** 零引用导出、**40/189** 无调用方 api 函数、**4/63** 路由不可达视图 |
| 配置项 | **116 个键既无绑定类也无读取方**（去重后），分 9 个家族；孤儿子树 14 个但**全部**有非绑定消费方（0 缺陷）；反向扫描 44 个「代码读了 yml 没有」的键，**0 缺陷** |
| 未实现功能 | **整族安全指标从不记录**（`SecurityMetrics` 19/19 方法无调用方，动态实测计数器恒 0）、**1 个端点文档说能用实为占位**（`custom-tags`）、1 个同步实现是占位（不可达） |
| 前端界面 | **8 处用户可见中文硬编码**（语言键已存在却未用）；zh/en 语言包**键级完全对称（0 不对称）**；**8 处不可逆操作缺二次确认**（2 处整文件无确认）；页内**权限门控 0 处**（机制只用在菜单与路由层） |
| 契约形状 | 前端声明了 **10 个后端从不发送的字段**（15 条候选里 5 条是假阳性）⇒ UI 读 `undefined`；含 `SecurityAlert.eventType` ↔ 后端 `alertType` 的改名漂移 |
| 接口测试 | 前端↔后端**只 1 处不匹配**（405）；**27 个管理面端点既无前端入口也无文档**；**`/v3/api-docs` 是空文档（paths=0）** |

## 方法与「校准」纪律

**每个探测器都必须先用已知正例证明它能报出东西，再用负控证明它不乱报。** 这不是形式要求 ——
本轮**最初几个探测器全是错的**：

| 探测器 | 我修掉的假阳性/假阴性 | 说明 |
|---|---|---|
| `ConfigBindingSweepTest`（Java） | 4 类 | ① 属性名存常量的读取（`getProperty(MODE_KEY)`）→ 误报 3 键；② **宽前缀冒领**（`jairouter.security` 无 `monitoring` 字段却"认领"整棵子树）→ 孤儿扫描完全看不见那批键；③ **列表元素盲区**（`NoUnboundElementsBindHandler` 不认 `x[0].y`）→ 61/273 是假阳性；④ 重叠前缀 + `${...}` 占位符 |
| `detect-frontend-dead-code.py` | 我的手写粗探针 | 曾得出「0 个死 api 导出」，原因是把声明所在文件也算作"被引用"⇒ 判据恒真。校准后 **40 个** |
| `detect-hardcoded-i18n.py` | 推翻了我自己的假设 | 我按"中文出现次数"判断「大范围硬编码」，实测那些出现全在注释与 `console.error` 里 ⇒ 真实范围只有 **8 处** |
| `reconcile-frontend-api.py` | 首轮 6→1 | 5 条假阳性来自 `${action}`/`${command}` 派发占位符；加了有界解析后手验 |
| `detect-backend-dead-code.py` | 它自己抓到 2 个解析 bug | 多行注解数组、参数注解里的 `=` |

⇒ **"没查到"只能写成「在 X 手法下未命中」**，不能写成"不存在"。本文件每节都标了判据与盲区。

## 探测器清单与运行方式

| 轴 | 探测器 | 自检 | 报告 |
|---|---|---|---|
| 配置（正向） | `src/test/java/org/unreal/modelrouter/config/binding/ConfigBindingSweepTest.java` | 6 例（1 反空转 + 3 反证 + 报告 + 最长前缀） | `target/config-binding-sweep.txt` |
| 配置（正向+反向） | `scripts/audit/analyze-config-keys.py` | `--self-test` 6 例 | `scripts/audit/out/config-keys.md` |
| 死代码（后端） | `scripts/audit/detect-backend-dead-code.py` | `--self-test` **26/26**（含新加的「4b 可疑桶 / 4c 序列化面桶按 owner 互斥」不变量） | `scripts/audit/out/backend-dead-code.md` |
| 死代码（前端） | `scripts/audit/detect-frontend-dead-code.py` | `--self-test` **30/30** | `scripts/audit/out/frontend-dead-code.md` |
| 前端硬编码 | `scripts/audit/detect-hardcoded-i18n.py` | `--self-test` **30/30** | `scripts/audit/out/hardcoded-i18n.md` |
| 接口对账 | `scripts/audit/reconcile-frontend-api.py` | `--self-test` **35/35** | `scripts/audit/out/frontend-api-reconcile.md` |
| 契约形状 | `scripts/audit/reconcile-contract-shapes.py` | 见下 | `scripts/audit/out/contract-shapes.md` |
| 界面可判定项 | `scripts/audit/detect-ui-states.py` | 见下 | `scripts/audit/out/ui-states.md` |
| 接口冒烟（资产） | `scripts/test/api-smoke.mjs` | 需要运行实例 | — |

## issue 台账（本轮产出）

| issue | 轴 | 标题 |
|---|---|---|
| #450 | 配置 | `jairouter.security.monitoring` 下两个子树没有绑定类且无读取方（约 58 个键） |
| #451 | 台账 | 技术债基线未入台账：`catch (Exception` 857 处、>500 行文件 17 个 |
| #452 | 配置 | 其余 61 个配置键既无绑定类也无读取方（9 个家族） |
| #453 | 死代码 | 死代码清单（母 issue）：后端 12 类型 / 前端 154 导出 + 40 api 函数 + 4 视图 |
| #454 | 接口 | 接口对账：1 处前端调用后端无该方法（405）+ 27 个管理面端点无前端入口且无文档 |
| #455 | 前端 | 8 处用户可见中文硬编码（语言键已存在但未使用） |
| #456 | 未实现 | `SecurityMetrics` 19/19 指标方法无调用方（动态实测计数器恒 0）+ `custom-tags` 谎报成功 + store 同步为占位 |
| #457 | 接口 | `/v3/api-docs` 是空文档（paths=0）：springdoc 扫描的包不存在 —— **已提 PR #458** |
| #459 | 契约 | 前端类型对账：10 个字段后端从不发送（含 `SecurityAlert.eventType` ↔ `alertType` 改名漂移） |
| #460 | 前端 | 界面可判定项：8 处不可逆操作缺二次确认 + 页内权限门控 0 处 |

## 动态复核（把静态结论拿到运行态）

起真实实例（dev profile、独立 H2）逐项实测：

```
GET  /api/config/instance/chat/1        -> 405 METHOD_NOT_ALLOWED        （#454 静态结论被现场证实）
PUT  /api/monitoring/config/custom-tags -> 200 success=true + "此功能尚未实现"
GET  /api/monitoring/config/snapshot    -> 刚提交的标签没有落库          （证明在骗人）
GET  /api/auth/api-keys  (user 账号)    -> 403 text/plain "Access Denied"
PUT  /api/config/adapter/gemini          -> 403 BUILTIN_READONLY
真实成功登录 + 真实失败登录              -> authentication_attempts/failures/successes 三个计数器仍全为 0
GET  /v3/api-docs       (ADMIN)          -> 200，180 字节，paths=0
```

## 覆盖与漏检率（诚实申报）

* **机械扫描覆盖 100% 目标文件**：后端 828 个 `.java`、前端 146 个源文件、34 个 yml（加载 803 个叶子属性）、58 个 controller / 359 个映射、215 个前端调用点。
* **命中项人工仲裁 100%**：每个 issue 的关键行都由我亲自回代码/文档复核（例如 12 个零引用类型逐名 grep 确认外部 0 命中；`jwt.secret`、`keys[0].*`、`api-key.cache-enabled` 逐个判定）。
* **分层抽样估漏检率**：**未做**（诚实申报）。本轮各探测器的假阳性率都有实测（配置 4 类、契约 5/15、接口 5/6、硬编码假设被推翻），但**没有**做「人工精读 30 个文件反推漏检率」这一步。⇒ 本报告的每条结论都带「探测器 + 人工仲裁」，但**不能**据此声称「目标范围内不存在更多同类问题」。

### 各探测器的实测假阳性率（本轮最有参考价值的数字）

| 探测器 | 候选数 | 人工仲裁后为真 | 假阳性率 | 假阳性类型 |
|---|---:|---:|---:|---|
| 配置绑定（严格层） | 273 原始未绑定 | 116 去重真发现 | 61 个列表盲区 + 25 个合法消费方 | 索引元素、重叠前缀、常量读取、`${...}` 占位符 |
| 契约形状 | 15 | **10** | 33% | 前端表单本地字段、`RouterResponse` 信封字段、端点消歧错 |
| 接口对账 | 6（首轮） | **1** | 83% | `${action}` / `${command}` 派发占位符 |
| 硬编码中文 | 我上轮的粗判「大范围」 | **8 处** | — | 注释与 `console.error` 被当成 UI 文案 |
| 界面可判定项 | 12 类规则 | 2 hard + 6 调用点级 | — | 文件级确认规则掩盖了 6 处（已单列） |

**结论：这类探测器的候选清单天生带噪声（本轮 33%–83%），"逐条人工仲裁"不是可选项而是必需项。**

## 未完成/待裁决

* **批 7（收紧 Qodana / 静态分析门闩）需要用户拍板**：`qodana.yaml` 用 `qodana.starter` 且 `failureConditions` 整段注释 ⇒ 该 job **不可能失败**；SpotBugs 的 include 只有 `category="SECURITY"` ⇒ 不查死代码。这两处一旦收紧会让后续 PR 变红，属"改门闩"，需授权。
* ~~探测器与报告目前未提交~~ —— **已过时**：探测器脚本已随 `#461` 入库；`out/` 下的报告（约 500KB）仍按 `.gitignore` 不入库，需要时用各脚本重新生成（全部纯标准库、离线可跑）。

---

## 审计收口状态（2026-10-18 终态）

> 全部数字为**终态实跑**；7 个探测器的 `--self-test` 全绿，其中两个（后端死代码、前端死代码）已接进 `快闸`。

### 各轴终态

| 轴 | 探测器 | 自检 | 终态命中 | 处置 |
|---|---|---|---|---|
| 后端死代码 | `detect-backend-dead-code.py` | **26/26** | `4`=0 · `4b`=0 · `3`=1；`1a`=3 · `1b`=82 · `2b`=25 · `5a-i`=133 | **轴清零**：删 ~2900 行（含整个安全配置服务族 1416 行、99 行死 DTO）；`1b/2b/5a` 是"注解/框架形状"，删不动 |
| 前端死代码 | `detect-frontend-dead-code.py` | **30/30** | `cat1_safe`=**0**；`cat1`=68/379（仅本文件使用）；`cat2`=64/2553 | **轴清零**：删 94 个零引用导出 + 2 个空壳文件（不动点迭代 43→42→9→0） |
| 配置项 | `ConfigBindingSweepTest` + `analyze-config-keys.py` | 6 例 | yml 叶子 **687** 个，**真发现 0** | **轴清零**：惰性键 **116 → 0**；文档示例改到真实绑定路径 |
| 契约形状 | `reconcile-contract-shapes.py` | **19/19** | 8 条（原 15） | 10 个字段已修（后端映射/真算/删字段），8 条待产品口径 |
| 接口对账 | `reconcile-frontend-api.py` | **35/35** | 前端→后端 **0** 不匹配；后端→前端 177 未触达（113 有文档、37 已补文档、其余在无需系统文档的面） | 405 已修；37 个管理端点已文档化 |
| 前端硬编码 i18n | `detect-hardcoded-i18n.py` | **30/30** | 12 处 / 9 片段 / 1 文件 | 属"数据值层"i18n（黑名单原因、会话默认标题），非漏调 `t()`；待口径 |
| 界面可判定项 | `detect-ui-states.py` | **12/12** | 缺加载态 5、缺空态 26（advisory） | 页内权限门控已修 2 页；其余为建议项 |
| 接口动态实跑 | `scripts/test/api-smoke.mjs` | 59 检查 | **59/59 PASS**（2026-10-18 复跑，含低权限 RBAC 矩阵） | 批 2D 已完成 |

### 复验记录（终态证据）

| 日期 | 复验项 | 结果 |
|---|---|---|
| 2026-10-18 | 接口冒烟（含批 2D 低权限 RBAC） | **59/59 PASS**（dev 实例 8080 就绪 15.2s） |
| 2026-10-18 | 后端全量 | `Tests run: 4150, Failures: 0, Errors: 0, Skipped: 26` |
| 2026-10-18 | 前端 lint | **0 error / 300 warning**（棘轮 `--max-warnings 300`） |
| 2026-10-18 | 前端类型 | `vue-tsc --noEmit` **exit 0** |
| 2026-10-18 | 7 个探测器自检 | 26/26 · 30/30 · 19/19 · 12/12 · 35/35 · PASS · PASS |
| 2026-10-18 | 配置绑定审计 | 真发现 **0** |

### 技术债基线（对比审计开始时）

| 指标 | 开始时 | 终态 |
|---|---:|---:|
| `catch (Exception` 处数 | 857 | **835** |
| `src/main/java` 下 >500 行文件 | 29 | **15** |
| 最大文件 | `QuotaLedgerService` 1211 行 | **1150 行** |
| 后端测试数 | 4117 | **4150** |
| 前端 lint 警告 | 不可用（eslint 10 读不了 eslintrc） | **300**（0 error，棘轮） |
| 惰性配置键 | 116 | **0** |

### 门闩现状（审计即守卫的落地）

| 门闩 | 覆盖 |
|---|---|
| `快闸` | 错误码对账 · 配置清单对账 · API 端点对账 · 工作流内嵌 Python 语法 · **后端死代码探测器自测 26/26**（路径面已含 `scripts/audit/**`） |
| `frontend-tests.yml` → `eslint` | flat config + `--max-warnings 300` 棘轮（新增即红） |
| `frontend-tests.yml` → `vitest` | 14 文件 / 45 例 |
| `java-tests.yml` → `test` | 全量 4150 例 + checkstyle |
| `api-smoke.mjs` | 59 项接口冒烟（含 RBAC 读写矩阵） |

**仍未收紧**（需授权）：`qodana.yaml` 用 `qodana.starter` 且 `failureConditions` 整段注释 ⇒ 该 job 不可能失败；
SpotBugs include 只有 `category="SECURITY"`。

### 仍有 10 条开放 issue（全部为"待产品口径"或 bot 提醒）

`#454`（端点契约口径）· `#514`（call-history 权限分离）· `#456`（2 个指标）· `#480` · `#483` · `#487` ·
`#468` · `#455` · `#494`（300 条 `any`）· `#442`（bot 文档提醒）

**本批审计期间已关闭 19 个 issue**（按关闭时间）：`#429` `#432` `#436` `#446` `#447` `#448` `#450`
`#451` `#452` `#453` `#457` `#459` `#460` `#462` `#464` `#466` `#469` `#473` `#501`；
配套合并 **45 个 PR**（`#458`…`#516`，含 1 个发布文档 PR）。

### 这轮审计沉淀的判据（每条都踩过）

1. **"零引用"要问清"在哪零引用"** —— 只统计跨文件引用会把"同文件内使用"当死代码（前端曾误删 80 个声明，`vue-tsc` 20+ 处报错拦下）。
2. **"访问器零调用"≠"没被写"** —— 必须连 `setX`/`getX` 一起搜；`currentQps` 因此被我误判过一次并公开更正。
3. **"审计器说是死配置"≠"真的没人读"** —— `@ConditionalOnProperty(prefix,name)` 一度未被识别，`alerts.enabled` 差点被误删（会静默关掉安全告警）。
4. **序列化面 ≠ 死代码** —— DTO / `@ConfigurationProperties` / JPA 实体的字段由 Jackson/Binder 观测。
5. **字段级"可删"可能是"整个类已死"** —— 删类而不是删字段。
6. **删死代码要做不动点迭代** —— 42 → 9 → 0。
7. **删代码的兜底是编译器/类型检查，不是探测器计数**。
8. **"pr checks 全绿"可能是"门闩压根没跑"** —— bot 提交会让 run 停在 `action_required` 且 `gh pr checks` 不显示。
9. **门闩自己的路径面也会漏** —— 快闸曾缺 `scripts/audit/**`，改探测器时它的自检反而不触发。
