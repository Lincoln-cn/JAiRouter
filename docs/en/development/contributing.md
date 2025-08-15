# Contribution Guide

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
   - Import [checkstyle.xml](file://D:\IdeaProjects\model-router\checkstyle.xml) configuration from project root

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
- **Method names**: camelCase (e.g., [selectInstance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\loadbalancer\LoadBalancer.java#L21-L21))
- **Constants**: UPPER_SNAKE_CASE (e.g., [DEFAULT_TIMEOUT](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\circuitbreaker\CircuitBreakerManager.java#L20-L20))
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

### Release Checklist

- [ ] All tests pass
- [ ] Code quality checks pass
- [ ] Documentation updated
- [ ] CHANGELOG updated
- [ ] Version number updated

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
