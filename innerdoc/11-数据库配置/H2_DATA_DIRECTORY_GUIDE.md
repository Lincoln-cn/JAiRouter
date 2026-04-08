# H2 数据目录配置指南

## 概述

本项目的 H2 数据库文件统一存储在项目根目录下的 `data` 目录中，使用相对路径配置。

## 目录结构

```
jairouter/
├── data/                          # H2 数据库存储目录（不提交到 Git）
│   ├── jairouter.mv.db           # H2 配置文件（默认/测试环境）
│   ├── jairouter-dev.mv.db       # 开发环境数据库
│   ├── jairouter-prod.mv.db      # 生产环境数据库
│   └── jairouter.trace.db        # H2 跟踪日志（可选）
├── config/                        # JSON 配置文件目录
├── src/
└── pom.xml
```

## 配置说明

### 默认配置 (application-h2.yml)

```yaml
store:
  type: h2
  h2:
    url: file:./data/jairouter  # 相对路径，存储在项目 data 目录
```

**数据库文件**: `./data/jairouter.mv.db`

### 开发环境 (application-dev.yml)

```yaml
store:
  type: h2
  h2:
    url: file:./data/jairouter-dev  # 开发环境独立数据库
```

**数据库文件**: `./data/jairouter-dev.mv.db`

### 生产环境 (application-prod.yml)

```yaml
store:
  type: h2
  h2:
    url: file:./data/jairouter-prod  # 生产环境独立数据库
```

**数据库文件**: `./data/jairouter-prod.mv.db`

## 路径特性

### 1. 相对路径

所有配置使用相对路径 `./data/`，相对于应用启动目录（通常是项目根目录）。

### 2. 自动创建

如果 `data` 目录不存在，应用启动时会自动创建：

```java
// H2DatabaseConfiguration.java
java.io.File dataDir = dbFile.getParentFile();
if (dataDir != null && !dataDir.exists()) {
    dataDir.mkdirs();
    log.info("Created data directory: {}", dataDir.getAbsolutePath());
}
```

### 3. 环境隔离

不同环境使用不同的数据库文件，避免数据混淆：
- 测试环境: `jairouter.mv.db`
- 开发环境: `jairouter-dev.mv.db`
- 生产环境: `jairouter-prod.mv.db`

## 数据库文件说明

### 主文件

- **jairouter.mv.db**: 主数据库文件，包含所有表和数据
- 文件格式: H2 MVStore 格式（多版本存储）

### 辅助文件（可能生成）

- **jairouter.trace.db**: 跟踪日志文件（调试时生成）
- **jairouter.lock.db**: 锁文件（运行时生成，停止后自动删除）

## 使用示例

### 1. 启动应用

```bash
# 使用默认配置
mvn spring-boot:run

# 使用开发环境配置
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 使用生产环境配置
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 2. 查看数据库文件

```bash
# 列出所有数据库文件
ls -lh data/

# 查看文件大小
du -sh data/*.mv.db
```

### 3. 备份数据库

```bash
# 备份当前数据库
cp data/jairouter.mv.db data/jairouter-backup-$(date +%Y%m%d).mv.db

# 备份所有数据库
tar -czf data-backup-$(date +%Y%m%d).tar.gz data/
```

### 4. 恢复数据库

```bash
# 停止应用
# 恢复备份
cp data/jairouter-backup-20241124.mv.db data/jairouter.mv.db
# 重启应用
```

### 5. 清空数据库

```bash
# 停止应用
# 删除数据库文件
rm data/jairouter.mv.db
# 重启应用（会自动创建新的空数据库）
```

## H2 控制台访问

### 开发环境

H2 控制台在开发环境默认启用：

1. 启动应用: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
2. 访问: http://localhost:8080/h2-console
3. 连接信息:
   - **JDBC URL**: `jdbc:h2:file:./data/jairouter-dev`
   - **用户名**: `sa`
   - **密码**: (空)

### 生产环境

生产环境默认关闭 H2 控制台，如需启用：

```yaml
spring:
  h2:
    console:
      enabled: true
```

⚠️ **安全警告**: 生产环境不建议启用 H2 控制台

## 数据迁移

### 从旧路径迁移

如果之前使用了不同的路径，需要迁移数据：

```bash
# 停止应用

# 创建 data 目录
mkdir -p data

# 移动旧数据库文件
mv ./config.mv.db ./data/jairouter.mv.db

# 或从其他位置复制
cp /var/lib/jairouter/data/config.mv.db ./data/jairouter.mv.db

# 重启应用
```

### 环境间迁移

```bash
# 从开发环境复制到生产环境
cp data/jairouter-dev.mv.db data/jairouter-prod.mv.db

# 或反向
cp data/jairouter-prod.mv.db data/jairouter-dev.mv.db
```

## 自定义路径

如果需要使用自定义路径，可以通过环境变量或命令行参数覆盖：

### 方法 1: 环境变量

```bash
export STORE_H2_URL=file:/custom/path/mydb
mvn spring-boot:run
```

### 方法 2: 命令行参数

```bash
mvn spring-boot:run -Dstore.h2.url=file:/custom/path/mydb
```

### 方法 3: 修改配置文件

```yaml
store:
  h2:
    url: file:/custom/path/mydb
```

## 容器化部署

### Docker

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/data

# 复制应用
COPY target/modelrouter.jar app.jar

# 挂载数据卷
VOLUME /app/data

# 启动应用
CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

### Docker Compose

```yaml
version: '3.8'
services:
  jairouter:
    image: jairouter:latest
    volumes:
      - ./data:/app/data  # 挂载本地 data 目录
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

### Kubernetes

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jairouter
spec:
  template:
    spec:
      containers:
      - name: jairouter
        image: jairouter:latest
        volumeMounts:
        - name: data
          mountPath: /app/data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: jairouter-data
```

## 监控和维护

### 1. 监控数据库大小

```bash
# 创建监控脚本
cat > monitor-db-size.sh << 'EOF'
#!/bin/bash
while true; do
    echo "$(date): $(du -sh data/*.mv.db)"
    sleep 3600  # 每小时检查一次
done
EOF

chmod +x monitor-db-size.sh
./monitor-db-size.sh
```

### 2. 自动备份

```bash
# 创建备份脚本
cat > backup-db.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="./backups"
mkdir -p $BACKUP_DIR

DATE=$(date +%Y%m%d-%H%M%S)
tar -czf $BACKUP_DIR/data-backup-$DATE.tar.gz data/

# 保留最近7天的备份
find $BACKUP_DIR -name "data-backup-*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_DIR/data-backup-$DATE.tar.gz"
EOF

chmod +x backup-db.sh

# 添加到 crontab（每天凌晨3点备份）
# 0 3 * * * /path/to/backup-db.sh
```

### 3. 数据库压缩

H2 数据库会自动压缩，但可以手动触发：

```sql
-- 连接到 H2 控制台执行
SHUTDOWN COMPACT;
```

## 故障排查

### 问题 1: 数据库文件未创建

**症状**: 启动后 data 目录为空

**原因**: 
- 配置路径错误
- 权限不足

**解决**:
```bash
# 检查配置
grep "store.h2.url" src/main/resources/application*.yml

# 检查权限
ls -ld data/
chmod 755 data/

# 查看日志
grep "H2 database" logs/application.log
```

### 问题 2: 数据库文件损坏

**症状**: 启动时报错 "Database may be already in use"

**解决**:
```bash
# 删除锁文件
rm data/*.lock.db

# 如果还是失败，尝试恢复
# 1. 备份当前文件
cp data/jairouter.mv.db data/jairouter-corrupted.mv.db

# 2. 使用 H2 工具恢复
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db jairouter

# 3. 重启应用
```

### 问题 3: 磁盘空间不足

**症状**: 数据库写入失败

**解决**:
```bash
# 检查磁盘空间
df -h

# 清理旧备份
rm data/*-backup-*.mv.db

# 压缩数据库（通过 H2 控制台）
# SHUTDOWN COMPACT;
```

## 最佳实践

### 1. 定期备份

- 每天自动备份
- 保留至少7天的备份
- 重要操作前手动备份

### 2. 监控大小

- 设置大小告警（如超过1GB）
- 定期清理过期数据
- 考虑数据归档

### 3. 权限控制

- data 目录权限: 755
- 数据库文件权限: 644
- 仅应用用户可写

### 4. 环境隔离

- 不同环境使用不同数据库文件
- 不要在生产环境使用开发数据
- 测试前备份生产数据

### 5. 版本控制

- data 目录添加到 .gitignore
- 不要提交数据库文件到 Git
- 使用迁移脚本管理表结构变更

## 总结

- ✅ 统一使用 `./data/` 相对路径
- ✅ 自动创建目录
- ✅ 环境隔离（dev/prod）
- ✅ 支持自定义路径
- ✅ 容器化友好
- ✅ 易于备份和恢复

---

**文档版本**: 1.0  
**更新时间**: 2024-11-24  
**适用版本**: ModelRouter 1.0+
