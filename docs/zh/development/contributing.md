# 贡献指南

<!-- 版本信息 -->
> **文档版本**: 1.0.3  
> **最后更新**: 2026-09-15  
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->



## 欢迎贡献

感谢您对 JAiRouter 项目的关注！我们欢迎各种形式的贡献，包括但不限于：

- 🐛 Bug 报告和修复
- ✨ 新功能开发
- 📚 文档改进
- 🧪 测试用例补充
- 💡 功能建议和讨论

## 开发环境准备

### 系统要求

- **Java**: 17 或更高版本
- **Maven**: 3.8+ (推荐使用项目内置的 Maven Wrapper)
- **Git**: 2.20+
- **IDE**: IntelliJ IDEA (推荐) 或 Eclipse

### 环境配置

1. **克隆项目**
   ```bash
   git clone https://github.com/Lincoln-cn/JAiRouter.git
   cd jairouter
   ```

2. **验证Java版本**
   ```bash
   java -version
   # 确保输出显示 Java 17 或更高版本
   ```

3. **构建项目**
   ```bash
   # 中国用户（推荐）
   ./mvnw clean package -Pchina
   
   # 国际用户
   ./mvnw clean package
   ```

4. **运行测试**
   ```bash
   # 跳过代码质量检查，直接运行测试
   ./mvnw compiler:compile compiler:testCompile surefire:test
   ```

5. **启动应用**
   ```bash
   java -jar target/model-router-*.jar
   ```

### IDE 配置

#### IntelliJ IDEA

1. **导入项目**
   - File → Open → 选择项目根目录
   - 选择 "Import as Maven project"

2. **配置代码风格**
   - File → Settings → Editor → Code Style
   - 导入项目根目录的 `checkstyle.xml` 配置

3. **配置 Maven**
   - File → Settings → Build → Build Tools → Maven
   - 设置 Maven home directory 为项目内的 `.mvn/wrapper`

4. **安装推荐插件**
   - CheckStyle-IDEA
   - SpotBugs
   - SonarLint

## 开发流程

### 1. 创建分支

```bash
# 从 main 分支创建功能分支
git checkout main
git pull origin main
git checkout -b feature/your-feature-name

# 或创建修复分支
git checkout -b fix/issue-number-description
```

### 2. 开发规范

#### 代码风格

- 遵循项目的 Checkstyle 配置
- 使用 4 个空格缩进，不使用 Tab
- 行长度不超过 120 字符
- 类和方法需要有完整的 Javadoc 注释

#### 命名规范

- **类名**: PascalCase (如 `LoadBalancerFactory`)
- **方法名**: camelCase (如 `selectInstance`)
- **常量**: UPPER_SNAKE_CASE (如 `DEFAULT_TIMEOUT`)
- **包名**: 小写，使用点分隔 (如 `org.unreal.modelrouter.adapter`)

#### 注释规范

```java
/**
 * 负载均衡器工厂类
 * 
 * <p>负责创建和管理不同类型的负载均衡器实例，支持动态切换负载均衡策略。
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class LoadBalancerFactory {
    
    /**
     * 根据配置创建负载均衡器
     * 
     * @param config 负载均衡配置
     * @return 负载均衡器实例
     * @throws IllegalArgumentException 当配置无效时抛出
     */
    public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
        // 实现逻辑
    }
}
```

### 3. 测试要求

#### 单元测试

- 新功能必须包含对应的单元测试
- 测试覆盖率不低于 80%
- 测试类命名格式：`{ClassName}Test`

```java
@ExtendWith(MockitoExtension.class)
class LoadBalancerFactoryTest {
    
    @Mock
    private LoadBalanceConfig config;
    
    @InjectMocks
    private LoadBalancerFactory factory;
    
    @Test
    @DisplayName("应该根据配置创建正确的负载均衡器")
    void shouldCreateCorrectLoadBalancer() {
        // Given
        when(config.getType()).thenReturn("random");
        
        // When
        LoadBalancer balancer = factory.createLoadBalancer(config);
        
        // Then
        assertThat(balancer).isInstanceOf(RandomLoadBalancer.class);
    }
}
```

#### 集成测试

- 关键功能需要集成测试
- 使用 `@SpringBootTest` 进行完整的应用上下文测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UniversalControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldHandleChatRequest() {
        // 集成测试逻辑
    }
}
```

#### 控制台 E2E

`frontend/e2e/` 下有一套自研 E2E（16 个 spec + harness + runner），CI 里只跑稳定子集（`smoke` + `overview`），job 名 `e2e-smoke`，定义在 `.github/workflows/e2e-smoke.yml`。它需要真实后端与浏览器，比单测慢得多，所以不放进 `前端单元测试`。

本地复现同一套：

```bash
cd frontend
npx playwright-core install chromium        # 只需一次
E2E_BASE_URL=http://127.0.0.1:8080/admin \
  E2E_PASS=<dev 环境 admin 口令> node e2e/runner.mjs --only smoke,overview
```

两点约定：

- `E2E_MIN_CHECKS` 是反空转下限（CI 设 200，实测 251 项）。实际执行的检查项低于它即判失败 —— 后端没起来、登录失败、spec 全线中途退出时结果集为空，那种情况必须报红，不能当成绿。
- `02-layout-nav.mjs` 里硬编码了菜单组数与菜单项总数（当前 7 组 29 项）。增删菜单时这条断言会红，改断言的同时记得更新注释里的数字。

### 4. 提交规范

#### Commit Message 格式

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**类型 (type):**
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

**示例:**
```bash
feat(loadbalancer): 添加一致性哈希负载均衡算法

- 实现基于虚拟节点的一致性哈希算法
- 支持节点动态添加和删除
- 添加相应的单元测试

Closes #123
```

### 5. 代码审查

#### 提交 Pull Request

1. **推送分支**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **创建 Pull Request**
   - 在 GitHub 上创建 PR
   - 填写详细的 PR 描述
   - 关联相关的 Issue

3. **PR 描述模板**
   ```markdown
   ## 变更说明
   
   简要描述本次变更的内容和目的。
   
   ## 变更类型
   
   - [ ] Bug 修复
   - [ ] 新功能
   - [ ] 文档更新
   - [ ] 代码重构
   - [ ] 性能优化
   
   ## 测试
   
   - [ ] 已添加单元测试
   - [ ] 已添加集成测试
   - [ ] 手动测试通过
   
   ## 检查清单
   
   - [ ] 代码遵循项目规范
   - [ ] 已更新相关文档
   - [ ] 测试覆盖率满足要求
   - [ ] 无 Checkstyle 和 SpotBugs 警告
   
   ## 相关 Issue
   
   Closes #issue-number
   ```

#### 代码审查要点

**审查者关注点:**
- 代码逻辑正确性
- 性能影响
- 安全性考虑
- 测试完整性
- 文档更新

**常见问题:**
- 未处理的异常
- 资源泄漏
- 并发安全问题
- 不必要的复杂度

#### 合并前：把 CI 门闩读对

只有当 `gh pr checks <n>` 的**每一行**都是 `pass` 或 `skipping` 时才合并 —— `pending` 不算通过。

**哪些行会出现，取决于你改了什么。绝不能把「某行没出现」当成绿：**

| 改动的路径 | 会跑的门闩 | **不会**出现的门闩 |
|---|---|---|
| `docs/**`、`*.md`、`.cspell.json` | `拼写检查`、`文档质量检查`、`文档代码同步检查` 等 | `test`（java-tests.yml 按代码路径过滤） |
| `src/**`（Java）、`pom.xml`、资源文件 | `test`、`qodana`、`smoke` 等 | `拼写检查`、`文档质量检查`（docs-quality.yml 按文档路径过滤） |
| `pom.xml`、`frontend/package*.json`、`scripts/audit/**` | `OSV 漏洞扫描 + SBOM`（dependency-scan.yml） | 其余（它只按依赖相关路径过滤） |
| `.gitignore` 等两边都不匹配的路径 | 只有 CodeQL / Analyze | 上述两者 |

也就是说：**只改 docs 的 PR 没有 `test` 行是正常的**，**只改代码的 PR 没有 `拼写检查` 行也是正常的**。

**依赖门闩的口径**：`OSV 漏洞扫描 + SBOM` 会对 `mvn dependency:list` 导出的运行时依赖查 OSV，达到 **high 及以上判红**；只有 `scripts/audit/osv-baseline.json` 里**写明了理由**的条目才降级为记录（例如「该修复只在下一代大版本，本仓代码路径不可达」）。要往 baseline 里加条目，先说清为什么不能靠升级解决。
前端侧同一环节跑 `npm audit --omit=dev --audit-level=high`（只看进产物的依赖；eslint/vitest 这类开发链的 advisory 只记录不判红）。

**陷阱：机器人提交会把门闩藏起来。** 当机器人往你的分支追加提交（例如 `Auto-update sitemap.xml`）时，
由它触发的 run 可能停在 `action_required`（耗时 0s、没有任何 job），而**这些 run 完全不出现在
`gh pr checks` 里**。用「按分支列 run」把它们找出来：

```bash
gh run list --branch <你的分支> --limit 10
# 同一个 workflow 会出现两条，例如
#   completed  success          Java 测试   38143230269  4m34s
#   completed  action_required  Java 测试   38143237970  0s
```

逐条放行后再复查：

```bash
gh api -X POST repos/<owner>/<repo>/actions/runs/<run_id>/approve
```

同族陷阱：`gh pr view <n> --json mergeStateStatus` 可能报 `UNSTABLE`，而 `mergeable` 是
`MERGEABLE`，且**合并仍会成功** —— **能合 ≠ 门闩跑过**。

**已知抖动。** `文档质量检查` 里的外链检查偶尔会对
`https://github.com/<owner>/<repo>/blob/master/LICENSE` 返回 `HTTP 503`。重跑该 job 即可，
并在 PR 正文里如实说明，不要当成没发生。

#### CI 不可用时的本地替代门闩

Actions 的**额度**可能被耗尽（本仓 2026-10 出现过一次，见 issue #334）。那时 `gh pr checks` 什么都跑不出来 ——
但**判据不能因此放宽**，否则 `#294` 那类「带着红合并」会重演。本仓为此提供一条**本地复刻**：

```bash
# Windows（包装器只是为了绕开「未签名脚本」的执行策略拦截）
scripts\test\local-gates.cmd

# 只要文档/拼写/前端半边（快）
scripts\test\local-gates.cmd -SkipMaven
```

它把上一节的对照表**逐条复刻**：起 `redis:7-alpine` + `postgres:16-alpine`（CI 同款 health-cmd）
→ `mvnw test` → 校验三个门控测试**未被跳过**；随后 `mkdocs build --strict` + `markdownlint-cli2` +
`check-links.py --fail-on-error` + `check-docs-sync.py --fail-on-error` + `cspell` + 前端 `type-check`。

**用它的纪律**（与「门闩全绿才合并」同源）：

- **SKIP 不等于 PASS**：脚本会把 SKIP 单列出来；你必须在本 PR 正文写明「哪一项没跑、为什么」。
  典型例子：只改 `frontend/**` 的 PR 没有改动的 `*.md` ⇒ 拼写检查会 SKIP，这不是「通过」。
- **本地差异必须申报**：本机是 Windows，没有 CI 的 Linux 行为与跨平台矩阵；本地 `cspell@8` 与 CI 的
  `cspell-action@v5` **词典不完全一致** ⇒ **本地拼写全绿不蕴含 CI 绿**。
- **恢复额度后补跑**：CI 恢复后，要对这段期间合并的批次补跑 `test` + `文档质量检查` + `smoke`。
- 排障与已知本地缺陷（本机 AMSI 偶发崩溃、`docs/` 下被忽略的本地产物会污染文档检查）见
  `scripts/test/local-gates.ps1` 的头部注释。
- ⚠️ 目前只有 PowerShell 版（本机是 Windows）。Linux / macOS 贡献者请照脚本头部的映射表手工跑同样的命令 ——
  **不要在 PR 里声称跑了没跑过的项**。

## 开发指导

### 添加新的适配器

1. **创建适配器类**
   ```java
   @Component
   public class NewServiceAdapter extends BaseAdapter {
       @Override
       public Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
           // 实现适配逻辑
       }
   }
   ```

2. **注册适配器**
   ```java
   @Configuration
   public class AdapterConfiguration {
       @Bean
       public AdapterRegistry adapterRegistry(NewServiceAdapter newServiceAdapter) {
           AdapterRegistry registry = new AdapterRegistry();
           registry.register("newservice", newServiceAdapter);
           return registry;
       }
   }
   ```

3. **添加测试**
   ```java
   @ExtendWith(MockitoExtension.class)
   class NewServiceAdapterTest {
       // 测试逻辑
   }
   ```

### 适配器开发最佳实践

1. **支持最新的 API 特性**
   - 实现完整的 OpenAI 兼容参数支持
   - 支持扩展参数（通过 `extra_body`）
   - 实现标准的响应格式转换

2. **流式响应处理**
   - 正确处理 Server-Sent Events (SSE) 格式
   - 实现标准的 chunk 格式转换
   - 处善的错误处理

3. **错误处理和日志记录**
   - 实现统一的错误处理机制
   - 添加详细的追踪信息
   - 记录关键操作日志

4. **性能优化**
   - 避免不必要的 JSON 转换
   - 使用高效的 JSON 处理库
   - 优化内存使用

### 添加新的负载均衡策略

1. **实现接口**
   ```java
   @Component
   public class CustomLoadBalancer implements LoadBalancer {
       @Override
       public ServiceInstance selectInstance(List<ServiceInstance> instances, String clientInfo) {
           // 实现负载均衡逻辑
       }
   }
   ```

2. **注册策略**
   ```java
   @Component
   public class LoadBalancerFactory {
       public LoadBalancer createLoadBalancer(String type) {
           switch (type) {
               case "custom":
                   return new CustomLoadBalancer();
               // 其他策略
           }
       }
   }
   ```

### 添加新的限流算法

1. **实现接口**
   ```java
   @Component
   public class CustomRateLimiter implements RateLimiter {
       @Override
       public boolean tryAcquire(String key, int permits) {
           // 实现限流逻辑
       }
   }
   ```

2. **配置支持**
   ```yaml
   model:
     services:
       chat:
         rate-limit:
           type: custom
           # 自定义参数
   ```

## 发布流程

### 版本号规范

使用 [Semantic Versioning](https://semver.org/) 规范：

- **MAJOR**: 不兼容的 API 变更
- **MINOR**: 向后兼容的功能新增
- **PATCH**: 向后兼容的问题修正

### 发布节奏

**判据：任一主题批次合并完成 → 48 小时内切一个 PATCH 发布；无论如何，最少每 7 天切一次。**

这不是形式要求，而是本仓一次实测教训：`v3.3.0`（2026-10-07）之后 7 天里累计了 **82 次提交 / 58 项实质改动却一次未发**。
后果很具体 —— 「行为变化」只能一次列 9 条、发布说明难写；任何未知回归的爆炸半径覆盖整批；
`#305` 引入的 `/v1` 流式空体回归（`#356`）也因此多活了一天。

配套四条判据：

1. **按批次发，不攒批**：一个主题批次（一个 EPIC / 一组关联 issue）合并完成即可发版；
2. **P1 缺陷修复合并后 72 小时内必须发出补丁**，不得等下一个功能窗口；
3. **版本号按语义化版本判定**：一个批次里只有 `fix` / `docs` / `chore` / `test` / `refactor`（零 `feat`）时**只够 PATCH**，
   不要为了「显得重要」升 MINOR；
4. **发布说明只写本区间的变化**：不复述上一版本的行为变化（重复叙述会让读者以为变化发生了两次）；
   行为变化用「⚠️ 需要留意的行为变化」表逐条列出（先例 `v3.3.0`）。

### 发布检查清单

- [ ] **所有测试通过**：本地 `scripts/test/local-gates.cmd` 全绿（或 SKIP，且已在 PR / 发布说明里写明原因），
      且 CI `test` job pass —— 三个门控集成测试**必须没有被跳过**
- [ ] **代码质量检查通过**：Qodana 与 CodeQL 均 pass
- [ ] **文档已更新**：中英对称，并按改动路径确认该出现的文档门闩都 pass
      （判据见本文「合并前：把 CI 门闩读对」一节）
- [ ] **CHANGELOG 已更新**：`docs/{zh,en}/reference/changelog.md` 新增本版本条目，并同步 `roadmap.md` 的「当前版本状态」
- [ ] **版本号已更新**：`pom.xml`、`README.md` / `README-ZH.md` 的事实块、
      `docs/{zh,en}/reference/changelog.md` 的文档版本块 —— **四处必须一致**

### 一键发布脚本

七处版本号（`pom.xml` 项目版本、`frontend/package.json`、两个 README 的事实块、两份 changelog 的文档版本块、两份 roadmap 的标签行）
手工改容易漏。用脚本串起来，默认 dry-run：

```bash
python scripts/release/release.py check                 # 只读校验（与 CI 的 ReleaseVersionConsistencyTest 同一套判据）
python scripts/release/release.py bump --version 3.5.9  # 预演：打印将改哪些文件的哪一行
python scripts/release/release.py bump --version 3.5.9 --write
python scripts/release/release.py tag --version 3.5.9 --notes release-notes.md --yes
```

脚本**不替你写发布说明**：changelog 的 `### [X.Y.Z]` 段落仍要按「只写本区间变化」手工补，`check` 会因为缺条目报红。
`tag` 子命令只在 master、工作区干净、版本号与 `pom.xml` 一致时才执行，并提示「Release 创建后才触发镜像构建」。


## 社区参与

### 报告问题

使用 GitHub Issues 报告问题时，请提供：

- 详细的问题描述
- 复现步骤
- 期望行为
- 实际行为
- 环境信息（Java版本、操作系统等）
- 相关日志

### 功能建议

提交功能建议时，请说明：

- 功能的使用场景
- 预期的实现方式
- 对现有功能的影响
- 是否愿意参与开发

### 参与讨论

- 加入项目讨论
- 回答其他用户的问题
- 分享使用经验
- 提供改进建议

## 获得帮助

如果在开发过程中遇到问题，可以通过以下方式获得帮助：

- 查看项目文档
- 搜索已有的 Issues
- 创建新的 Issue
- 参与社区讨论

感谢您的贡献！每一个贡献都让 JAiRouter 变得更好。