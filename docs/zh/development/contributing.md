# 贡献指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
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

### 发布检查清单

- [ ] 所有测试通过
- [ ] 代码质量检查通过
- [ ] 文档已更新
- [ ] CHANGELOG 已更新
- [ ] 版本号已更新

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