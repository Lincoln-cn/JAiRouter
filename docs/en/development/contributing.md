# Contribution Guide

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



## Welcome Contributions

Thank you for your interest in the JAiRouter project! We welcome all forms of contributions, including but not limited to:

- 🐛 Bug reports and fixes
- ✨ New feature development
- 📚 Documentation improvements
- 🧪 Test case additions
- 💡 Feature suggestions and discussions

## Development Environment Setup

### System Requirements

- **Java**: 17 or higher
- **Maven**: 3.8+ (recommended to use the built-in Maven Wrapper)
- **Git**: 2.20+
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

### Environment Configuration

1. **Clone the Project**
   ```bash
   git clone https://github.com/Lincoln-cn/JAiRouter.git
   cd jairouter
   ```

2. **Verify Java Version**
   ```bash
   java -version
   # Ensure output shows Java 17 or higher
   ```

3. **Build the Project**
   ```bash
   # For Chinese users (recommended)
   ./mvnw clean package -Pchina
   
   # For international users
   ./mvnw clean package
   ```

4. **Run Tests**
   ```bash
   # Skip code quality checks and run tests directly
   ./mvnw compiler:compile compiler:testCompile surefire:test
   ```

5. **Start the Application**
   ```bash
   java -jar target/model-router-*.jar
   ```

### IDE Configuration

#### IntelliJ IDEA

1. **Import Project**
   - File → Open → Select project root directory
   - Choose "Import as Maven project"

2. **Configure Code Style**
   - File → Settings → Editor → Code Style
   - Import `checkstyle.xml` configuration from project root

3. **Configure Maven**
   - File → Settings → Build → Build Tools → Maven
   - Set Maven home directory to `.mvn/wrapper` within the project

4. **Install Recommended Plugins**
   - CheckStyle-IDEA
   - SpotBugs
   - SonarLint

## Development Workflow

### 1. Create Branch

```bash
# Create feature branch from main
git checkout main
git pull origin main
git checkout -b feature/your-feature-name

# Or create fix branch
git checkout -b fix/issue-number-description
```

### 2. Development Standards

#### Code Style

- Follow the project's Checkstyle configuration
- Use 4 spaces for indentation, not tabs
- Line length should not exceed 120 characters
- Classes and methods require complete Javadoc comments

#### Naming Conventions

- **Class names**: PascalCase (e.g., `LoadBalancerFactory`)
- **Method names**: camelCase (e.g., `selectInstance`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_TIMEOUT`)
- **Package names**: lowercase, dot-separated (e.g., `org.unreal.modelrouter.adapter`)

#### Comment Standards

```java
/**
 * Load balancer factory class
 * 
 * <p>Responsible for creating and managing different types of load balancer instances,
 * supporting dynamic switching of load balancing strategies.
 * 
 * @author Author Name
 * @since 1.0.0
 */
public class LoadBalancerFactory {
    
    /**
     * Create load balancer based on configuration
     * 
     * @param config Load balancing configuration
     * @return Load balancer instance
     * @throws IllegalArgumentException when configuration is invalid
     */
    public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
        // Implementation logic
    }
}
```

### 3. Testing Requirements

#### Unit Tests

- New features must include corresponding unit tests
- Test coverage should be no less than 80%
- Test class naming format: `{ClassName}Test`

```java
@ExtendWith(MockitoExtension.class)
class LoadBalancerFactoryTest {
    
    @Mock
    private LoadBalanceConfig config;
    
    @InjectMocks
    private LoadBalancerFactory factory;
    
    @Test
    @DisplayName("Should create correct load balancer based on configuration")
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

#### Integration Tests

- Key features require integration tests
- Use `@SpringBootTest` for complete application context testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UniversalControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldHandleChatRequest() {
        // Integration test logic
    }
}
```

### 4. Commit Standards

#### Commit Message Format

Use [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types (type):**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation update
- `style`: Code formatting adjustments
- `refactor`: Code refactoring
- `test`: Test-related changes
- `chore`: Build process or auxiliary tool changes

**Example:**
```bash
feat(loadbalancer): Add consistent hash load balancing algorithm

- Implement virtual node-based consistent hash algorithm
- Support dynamic node addition and removal
- Add corresponding unit tests

Closes #123
```

### 5. Code Review

#### Submit Pull Request

1. **Push Branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create Pull Request**
   - Create PR on GitHub
   - Fill in detailed PR description
   - Link related Issues

3. **PR Description Template**
   ```markdown
   ## Change Description
   
   Briefly describe the content and purpose of this change.
   
   ## Change Type
   
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Documentation update
   - [ ] Code refactoring
   - [ ] Performance optimization
   
   ## Testing
   
   - [ ] Unit tests added
   - [ ] Integration tests added
   - [ ] Manual testing passed
   
   ## Checklist
   
   - [ ] Code follows project standards
   - [ ] Related documentation updated
   - [ ] Test coverage requirements met
   - [ ] No Checkstyle and SpotBugs warnings
   
   ## Related Issues
   
   Closes #issue-number
   ```

#### Code Review Focus Points

**Reviewer Focus Areas:**
- Code logic correctness
- Performance impact
- Security considerations
- Test completeness
- Documentation updates

**Common Issues:**
- Unhandled exceptions
- Resource leaks
- Concurrency safety issues
- Unnecessary complexity

#### Before You Merge: Read the CI Gates Correctly

Merge only when **every** row of `gh pr checks <n>` is `pass` or `skipping` — `pending` is not a pass.

**Which rows appear depends on what you changed.** Never read a missing row as green:

| Changed paths | Gates that run | Gates that do **not** appear |
|---|---|---|
| `docs/**`, `*.md`, `.cspell.json` | `拼写检查`, `文档质量检查`, `文档代码同步检查`, … | `test` (java-tests.yml filters on code paths) |
| `src/**` (Java), `pom.xml`, resources | `test`, `qodana`, `smoke`, … | `拼写检查`, `文档质量检查` (docs-quality.yml filters on docs paths) |
| `pom.xml`, `frontend/package*.json`, `scripts/audit/**` | `OSV 漏洞扫描 + SBOM` (dependency-scan.yml) | everything else (it filters on dependency paths) |
| `.gitignore` and other unmatched paths | CodeQL / Analyze only | both of the above |

So a docs-only PR with no `test` row is expected, and a code-only PR with no `拼写检查` row is expected.

**Dependency gate semantics**: `OSV 漏洞扫描 + SBOM` looks up the runtime dependencies exported by `mvn dependency:list` against OSV and **fails at high and above**; only entries listed in `scripts/audit/osv-baseline.json` **with a written reason** are downgraded to a record (for example "the fix only lands in the next major line and this code path is unreachable"). If you want to add a baseline entry, explain first why upgrading cannot solve it. The same job also runs
`npm audit --omit=dev --audit-level=high` on the frontend (production dependencies gate the merge;
dev-chain advisories such as eslint/vitest are only recorded).

**Trap: bot commits can hide gates.** When a bot pushes to your branch (for example
`Auto-update sitemap.xml`), the runs it triggers can stop at `action_required` — 0s, no jobs —
and those runs **do not show up in `gh pr checks` at all**. Find them by listing runs per branch:

```bash
gh run list --branch <your-branch> --limit 10
# the same workflow appears twice, e.g.
#   completed  success          Java 测试   38143230269  4m34s
#   completed  action_required  Java 测试   38143237970  0s
```

Release each one, then re-check:

```bash
gh api -X POST repos/<owner>/<repo>/actions/runs/<run_id>/approve
```

Related trap: `gh pr view <n> --json mergeStateStatus` may report `UNSTABLE` while `mergeable`
is `MERGEABLE`, and the merge still succeeds — **mergeable does not mean the gates ran**.

**Known flake.** The external link check inside `文档质量检查` occasionally gets `HTTP 503` for
`https://github.com/<owner>/<repo>/blob/master/LICENSE`. Re-run that job, and say so in the PR
description instead of pretending it never happened.

#### When CI Is Unavailable: Run the Same Gates Locally

GitHub Actions **minutes** can run out (it happened once in this repo, in Oct 2026 — see issue #334).
`gh pr checks` then has nothing to report — but the criterion must **not** loosen, or an episode like
`#294` (merged with a red spell check) repeats. The repo ships a local replica:

```bash
# Windows (the wrapper only bypasses the execution policy that blocks unsigned .ps1 files)
scripts\test\local-gates.cmd

# docs / spelling / frontend half only (fast)
scripts\test\local-gates.cmd -SkipMaven
```

It reproduces the table above item by item: starts `redis:7-alpine` + `postgres:16-alpine` (same
`health-cmd` as CI) → `mvnw test` → asserts the three gated tests were **not skipped**; then runs
`mkdocs build --strict`, `markdownlint-cli2`, `check-links.py --fail-on-error`,
`check-docs-sync.py --fail-on-error`, `cspell` and the frontend `type-check`.

**Rules for using it** (same spirit as "merge only when the gates are green"):

- **SKIP is not PASS**: the script lists SKIPs separately. You must state in the PR description which item
  did not run and why. A typical case: a `frontend/**`-only PR has no changed `*.md`, so the spell check
  SKIPs — that is not a pass.
- **Declare the local deltas**: this machine is Windows, so there is no CI Linux behavior and no
  cross-platform matrix; and the local `cspell@8` dictionary is **not identical** to CI's
  `cspell-action@v5` ⇒ **a clean local spell check does not imply a green CI one**.
- **Re-run after minutes are restored**: once CI is back, re-run `test` + `文档质量检查` + `smoke`
  for everything merged during the gap.
- Troubleshooting and known local defects (this machine's intermittent AMSI crash, and the ignored local
  artifacts under `docs/` that pollute the docs checks) are documented in the header of
  `scripts/test/local-gates.ps1`.
- ⚠️ Only the PowerShell version exists so far (the maintainer's machine is Windows). On Linux / macOS,
  follow the mapping table in that script header and run the same commands by hand — **never claim in a PR
  that you ran something you did not**.

## Development Guidance

### Adding New Adapters

1. **Create Adapter Class**
   ```java
   @Component
   public class NewServiceAdapter extends BaseAdapter {
       @Override
       public Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
           // Implement adapter logic
       }
   }
   ```

2. **Register Adapter**
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

3. **Add Tests**
   ```java
   @ExtendWith(MockitoExtension.class)
   class NewServiceAdapterTest {
       // Test logic
   }
   ```

### Adding New Load Balancing Strategies

1. **Implement Interface**
   ```java
   @Component
   public class CustomLoadBalancer implements LoadBalancer {
       @Override
       public ServiceInstance selectInstance(List<ServiceInstance> instances, String clientInfo) {
           // Implement load balancing logic
       }
   }
   ```

2. **Register Strategy**
   ```java
   @Component
   public class LoadBalancerFactory {
       public LoadBalancer createLoadBalancer(String type) {
           switch (type) {
               case "custom":
                   return new CustomLoadBalancer();
               // Other strategies
           }
       }
   }
   ```

### Adding New Rate Limiting Algorithms

1. **Implement Interface**
   ```java
   @Component
   public class CustomRateLimiter implements RateLimiter {
       @Override
       public boolean tryAcquire(String key, int permits) {
           // Implement rate limiting logic
       }
   }
   ```

2. **Configuration Support**
   ```yaml
   model:
     services:
       chat:
         rate-limit:
           type: custom
           # Custom parameters
   ```

## Release Process

### Version Number Standards

Use [Semantic Versioning](https://semver.org/) specification:

- **MAJOR**: Incompatible API changes
- **MINOR**: Backward-compatible feature additions
- **PATCH**: Backward-compatible bug fixes

### Release Cadence

**Rule: once a themed batch is merged, cut a PATCH release within 48 hours; in any case, never let more than 7 days pass.**

This is not a formality — it comes from a measured lesson in this repository. In the 7 days after
`v3.3.0` (2026-10-07) the project accumulated **82 commits (58 substantive) with no release at all**.
The consequences were concrete: the behavior-changes list had to carry 9 entries at once, the release
notes became hard to write, the blast radius of any unknown regression covered the whole batch, and the
empty-stream regression on `/v1` introduced by `#305` (`#356`) survived an extra day.

Four supporting rules:

1. **Release per batch — never stockpile**: once a themed batch (an EPIC or a group of related issues)
   is merged, ship it;
2. **Ship a patch within 72 hours of merging a P1 fix** — do not wait for the next feature window;
3. **Decide the version with Semantic Versioning**: a batch containing only `fix` / `docs` / `chore` /
   `test` / `refactor` (zero `feat`) is a **PATCH** — do not inflate it to MINOR to look important;
4. **Release notes cover that interval only**: do not repeat the previous release's behavior changes
   (repeating them reads as if the change happened twice); list behavior changes in the
   "Behavior changes to be aware of" table.

### Release Checklist

- [ ] **All tests pass**: `scripts/test/local-gates.cmd` is green locally (or SKIP with the reason stated
      in the PR / release notes), and the CI `test` job passes — the three gated integration tests
      **must not be skipped**
- [ ] **Code quality checks pass**: both Qodana and CodeQL pass
- [ ] **Documentation updated**: zh/en in sync, and the documentation gates that *should* appear for the
      changed paths all pass (see "Before merging: read the CI gates correctly" in this file)
- [ ] **CHANGELOG updated**: add the entry to `docs/{zh,en}/reference/changelog.md` and update the
      "current version status" in `roadmap.md`
- [ ] **Version number updated**: `pom.xml`, the facts block in `README.md` / `README-ZH.md`, and the
      document version block in `docs/{zh,en}/reference/changelog.md` — **all four must agree**

### One-click release script

Seven places carry the version (the `pom.xml` project version, `frontend/package.json`, the facts block of both READMEs,
the document-version block of both changelogs, and the tag line of both roadmap files). Editing them by hand drifts easily,
so use the script — it defaults to dry-run:

```bash
python scripts/release/release.py check                 # read-only, same judgment as the ReleaseVersionConsistencyTest guard
python scripts/release/release.py bump --version 3.5.9  # preview: prints which line of which file changes
python scripts/release/release.py bump --version 3.5.9 --write
python scripts/release/release.py tag --version 3.5.9 --notes release-notes.md --yes
```

The script **does not write release notes**: the `### [X.Y.Z]` section in the changelog is still written by hand
(only the changes of this range), and `check` fails while that section is missing. `tag` only runs on master,
with a clean working tree and a version matching `pom.xml`, and reminds you that the image build is triggered by the Release.

## Community Participation

### Reporting Issues

When reporting issues via GitHub Issues, please provide:

- Detailed problem description
- Reproduction steps
- Expected behavior
- Actual behavior
- Environment information (Java version, operating system, etc.)
- Related logs

### Feature Suggestions

When submitting feature suggestions, please explain:

- Use case for the feature
- Expected implementation approach
- Impact on existing features
- Willingness to participate in development

### Participating in Discussions

- Join project discussions
- Answer other users' questions
- Share usage experiences
- Provide improvement suggestions

## Getting Help

If you encounter problems during development, you can get help through:

- Reviewing project documentation
- Searching existing Issues
- Creating new Issues
- Participating in community discussions

Thank you for your contributions! Every contribution makes JAiRouter better.
