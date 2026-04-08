# H2 配置整合总结

## 变更概述

将 H2 数据库配置整合到默认配置和环境特定配置中，消除重复配置，统一使用 `spring.r2dbc` 配置。

## 变更时间
2024-11-24

## 主要变更

### 1. 删除独立的 H2 配置文件

**删除**: `src/main/resources/application-h2.yml`

**原因**: 
- 避免配置文件过多
- H2 作为默认存储，应该在主配置中
- 减少配置维护成本

### 2. 整合到默认配置

**文件**: `src/main/resources/application.yml`

**新增配置**:
```yaml
spring:
  # H2 数据库配置（默认配置）
  r2dbc:
    url: r2dbc:h2:file:///./data/jairouter?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
    username: sa
    password:
  
  # H2 控制台（默认关闭）
  h2:
    console:
      enabled: false

# 存储配置（默认使用 H2）
store:
  type: h2
  migration:
    enabled: true
  security-migration:
    enabled: true
```

### 3. 简化环境配置

#### 开发环境 (application-dev.yml)

**变更前**:
```yaml
store:
  type: h2
  h2:
    url: file:./data/jairouter-dev  # 重复配置
  migration:
    enabled: true
  security-migration:
    enabled: true

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/jairouter-dev?...  # 重复配置
```

**变更后**:
```yaml
spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/jairouter-dev?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
  h2:
    console:
      enabled: true  # 仅覆盖需要不同的配置
```

#### 生产环境 (application-prod.yml)

**变更前**:
```yaml
store:
  type: h2
  h2:
    url: file:./data/jairouter-prod  # 重复配置
  migration:
    enabled: false
  security-migration:
    enabled: false

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/jairouter-prod?...  # 重复配置
```

**变更后**:
```yaml
store:
  migration:
    enabled: false  # 仅覆盖需要不同的配置
  security-migration:
    enabled: false

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/jairouter-prod?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
    pool:
      initial-size: 20
      max-size: 50
  h2:
    console:
      enabled: false
```

### 4. 更新 H2DatabaseConfiguration

**变更前**:
```java
@Value("${store.h2.url:file:./data/jairouter}")
private String h2Url;

// 需要手动解析和转换路径
```

**变更后**:
```java
@Value("${spring.r2dbc.url}")
private String r2dbcUrl;

// 直接使用 Spring 的 R2DBC URL，无需转换
```

## 配置层次结构

```
application.yml (默认配置)
├── H2 数据库: ./data/jairouter
├── H2 控制台: 关闭
├── 迁移: 启用
└── 安全迁移: 启用

application-dev.yml (开发环境覆盖)
├── H2 数据库: ./data/jairouter-dev
└── H2 控制台: 启用

application-prod.yml (生产环境覆盖)
├── H2 数据库: ./data/jairouter-prod
├── 迁移: 关闭
└── 安全迁移: 关闭
```

## 数据库文件位置

| 环境 | 数据库文件 | 说明 |
|------|-----------|------|
| 默认 | `./data/jairouter.mv.db` | 测试或未指定环境时使用 |
| 开发 | `./data/jairouter-dev.mv.db` | 开发环境独立数据 |
| 生产 | `./data/jairouter-prod.mv.db` | 生产环境独立数据 |

## 优势

### 1. 消除重复配置

**之前**: 
- `store.h2.url` 和 `spring.r2dbc.url` 重复配置
- 需要在两个地方维护相同的路径

**现在**:
- 只有 `spring.r2dbc.url` 一个配置
- 单一数据源，避免不一致

### 2. 简化配置文件

**之前**: 
- 需要单独的 `application-h2.yml`
- 环境配置文件包含大量重复内容

**现在**:
- H2 配置在默认配置中
- 环境配置只覆盖差异部分

### 3. 更符合 Spring Boot 规范

**之前**: 
- 自定义 `store.h2.url` 配置
- 需要额外的代码转换

**现在**:
- 使用标准的 `spring.r2dbc.url`
- Spring Boot 自动处理

### 4. 更容易理解和维护

**之前**: 
- 配置分散在多个地方
- 不清楚哪个配置生效

**现在**:
- 配置层次清晰
- 覆盖关系明确

## 使用方式

### 启动不同环境

```bash
# 使用默认配置（./data/jairouter.mv.db）
mvn spring-boot:run

# 使用开发环境配置（./data/jairouter-dev.mv.db）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 使用生产环境配置（./data/jairouter-prod.mv.db）
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 自定义数据库路径

如果需要临时使用不同的路径：

```bash
# 通过命令行参数
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.r2dbc.url=r2dbc:h2:file:///./custom/path?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE"

# 通过环境变量
export SPRING_R2DBC_URL="r2dbc:h2:file:///./custom/path?DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE"
mvn spring-boot:run
```

## 迁移指南

如果你之前使用了 `application-h2.yml`：

### 步骤 1: 备份数据

```bash
cp -r data data-backup-$(date +%Y%m%d)
```

### 步骤 2: 删除旧配置文件

```bash
rm src/main/resources/application-h2.yml
```

### 步骤 3: 更新启动命令

**之前**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

**现在**:
```bash
# 使用默认配置
mvn spring-boot:run

# 或使用开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 步骤 4: 验证配置

```bash
# 启动应用
mvn spring-boot:run

# 检查日志
grep "H2 database file location" logs/application.log

# 检查数据库文件
ls -lh data/
```

## 配置验证

运行验证脚本：

```bash
./verify_h2_data_path.sh
```

预期输出：
```
✓ 使用 spring.r2dbc.url 配置
✓ 自动创建目录功能已添加
✓ 已移除旧的 store.h2.url 配置
✓ 无重复配置，使用统一的 spring.r2dbc.url
✓ 编译成功
```

## 常见问题

### Q1: 为什么删除 application-h2.yml？

**A**: 
- H2 是默认存储，应该在主配置中
- 避免配置文件过多，降低维护成本
- 环境特定配置通过 dev/prod 文件覆盖即可

### Q2: 如何切换到其他数据库？

**A**: 在环境配置中覆盖 `spring.r2dbc.url` 和 `store.type`：

```yaml
# application-mysql.yml
store:
  type: mysql

spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/jairouter
    username: root
    password: password
```

### Q3: 数据库文件会自动创建吗？

**A**: 是的，`H2DatabaseConfiguration` 会自动创建 `data` 目录：

```java
if (dataDir != null && !dataDir.exists()) {
    dataDir.mkdirs();
    log.info("Created data directory: {}", dataDir.getAbsolutePath());
}
```

### Q4: 如何在容器中使用？

**A**: 挂载 data 目录即可：

```yaml
# docker-compose.yml
services:
  jairouter:
    image: jairouter:latest
    volumes:
      - ./data:/app/data
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

## 文件清单

### 修改的文件

1. `src/main/resources/application.yml` - 添加默认 H2 配置
2. `src/main/resources/application-dev.yml` - 简化为只覆盖差异
3. `src/main/resources/application-prod.yml` - 简化为只覆盖差异
4. `src/main/java/org/unreal/modelrouter/store/config/H2DatabaseConfiguration.java` - 使用 spring.r2dbc.url

### 删除的文件

1. `src/main/resources/application-h2.yml` - 整合到默认配置

## 总结

### 变更前

- ❌ 配置重复（store.h2.url 和 spring.r2dbc.url）
- ❌ 配置文件过多
- ❌ 不符合 Spring Boot 规范
- ❌ 维护成本高

### 变更后

- ✅ 单一配置源（spring.r2dbc.url）
- ✅ 配置层次清晰
- ✅ 符合 Spring Boot 规范
- ✅ 易于理解和维护
- ✅ 环境隔离明确

---

**变更完成时间**: 2024-11-24  
**影响范围**: 配置文件和数据库配置类  
**向后兼容**: 是（数据库文件位置不变）  
**测试状态**: ✅ 编译通过
