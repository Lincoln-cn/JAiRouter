# JPA 迁移指南 (v1.5.1)

## 当前状态

✅ **已完成**:
- 移除 R2DBC 依赖，添加 JPA 依赖
- 配置 JPA 数据源 (`application.yml`)
- 创建 JPA 基础包结构和配置类
- 创建 JPA Entity (`jpa.entity.ConfigEntity`)
- 创建 JPA Repository (`jpa.repository.ConfigRepository`)
- 创建 `JpaStoreManager` 替代 `H2StoreManager`
- 批量迁移所有 Entity 到 JPA 注解
- 批量迁移所有 Repository 到 JPA
- 删除 R2DBC 核心文件
- 修改 `StoreManagerConfiguration` 使用 JPA
- 代码已提交到分支 `feature/v1.5.1-jpa-infrastructure`

🔄 **待完成** (约 400+ 编译错误):
- Service/Controller 层的响应式代码迁移
- 移除 Mono/Flux 的 `.block()`, `.subscribeOn()` 调用
- 修复 Repository 查询方法的返回类型
- 修复事务注解
- 测试验证

---

## 分支信息

```bash
# 当前分支
feature/v1.5.1-jpa-infrastructure

# 提交记录
85661ca wip(v1.5.1): JPA 迁移基础设施
```

---

## 主要变更

### 1. 依赖变更 (pom.xml)

**移除**:
- `spring-boot-starter-data-r2dbc`
- `r2dbc-h2`

**添加**:
- `spring-boot-starter-data-jpa`
- `h2` (JDBC driver)

### 2. 配置变更 (application.yml)

**移除**:
```yaml
spring:
  r2dbc:
    url: r2dbc:h2:file:///...
```

**添加**:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/jairouter...
  jpa:
    hibernate:
      ddl-auto: update
```

### 3. Entity 迁移规则

| R2DBC | JPA |
|-------|-----|
| `@Table("xxx")` | `@Entity` + `@Table(name = "xxx")` |
| `@Column("xxx")` | `@Column(name = "xxx")` |
| `@Id` | `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `org.springframework.data.annotation.Id` | `jakarta.persistence.*` |

### 4. Repository 迁移规则

| R2DBC | JPA |
|-------|-----|
| `extends R2dbcRepository` | `extends JpaRepository` |
| `@Query` (R2DBC) | `@Query` (JPA) |
| `Mono<T>` | `Optional<T>` |
| `Flux<T>` | `List<T>` |

### 5. Service 层迁移规则

| R2DBC | JPA |
|-------|-----|
| `.block()` | 直接返回 |
| `.subscribeOn(Schedulers...)` | 删除 |
| `Mono.fromCallable(...)` | 直接调用 |

---

## 后续迁移步骤

### 步骤 1: 修复 Service 层 (估计 200 错误)

需要修改的文件:
- `ConfigInitializer.java` - 移除响应式调用
- `DatabaseConfigService.java` - 同步化方法
- `ApiKeyService.java` - 同步化方法
- `JwtAccountService.java` - 同步化方法
- ...

**示例**:
```java
// R2DBC 旧代码
configRepository.findById(id)
    .subscribeOn(Schedulers.boundedElastic())
    .block();

// JPA 新代码
configRepository.findById(id).orElse(null);
```

### 步骤 2: 修复 Controller 层 (估计 100 错误)

如果 Controller 返回 `Mono<>`，需要修改为同步返回或保持 WebFlux。

### 步骤 3: 修复 Repository 查询 (估计 50 错误)

将返回 `Mono/Flux` 的方法修改为返回 `Optional/List`。

### 步骤 4: 添加事务注解

```java
// R2DBC
@Transactional
public Mono<Void> save(...) { ... }

// JPA
@Transactional
public void save(...) { ... }
```

### 步骤 5: 测试验证

```bash
./mvnw test
./test_version_with_auth.sh
```

---

## 常见问题

### Q1: 编译错误太多，如何快速修复？

**A**: 可以按模块逐步修复:
1. 先修复 `store` 包
2. 再修复 `config` 包
3. 最后修复其他包

### Q2: WebFlux 和 JPA 可以共存吗？

**A**: 可以。WebFlux 是 Web 层，JPA 是数据层，两者不冲突。

### Q3: 需要修改数据库表结构吗？

**A**: 不需要。JPA `ddl-auto: update` 会自动适配。

### Q4: 如何回滚？

**A**: 
```bash
git checkout master
# 或者
git checkout feature/v1.5.1-jpa-infrastructure~1
```

---

## 建议的迁移策略

### 方案 A: 激进方案 (当前)
- 一次性移除所有 R2DBC
- 工作量大但彻底
- 适合重构窗口期

### 方案 B: 保守方案 (建议)
- 保留 R2DBC，新增 JPA
- 逐个模块迁移
- 风险低，可回滚

---

## 相关文件

### 新增文件
- `jpa/config/JpaConfig.java`
- `jpa/JpaStoreManager.java`
- `jpa/entity/ConfigEntity.java`
- `jpa/repository/ConfigRepository.java`

### 修改文件
- `pom.xml` - 依赖变更
- `application.yml` - 配置变更
- `store/entity/*` - 注解迁移
- `store/repository/*` - 接口迁移
- `store/StoreManagerConfiguration.java` - Bean 配置

### 删除文件
- `store/ReactiveH2StoreManager.java`
- `store/H2StoreManager.java`
- `store/config/H2DatabaseConfiguration.java`

---

## 完成标准

- [ ] `mvnw compile` 0 错误
- [ ] `mvnw test` 全部通过
- [ ] `./test_version_with_auth.sh` 通过
- [ ] 应用可正常启动
- [ ] 配置版本管理功能正常

---

## 下一步

当前分支已准备好基础设施，可以继续修复剩余编译错误，或采用保守方案重新实施。
