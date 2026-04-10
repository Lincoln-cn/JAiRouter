# Project Summary

## Overall Goal
增强JAiRouter的安全黑名单功能，支持Token/IP/Device三种类型的黑名单，实现持久化存储和前端管理界面。

## Key Knowledge

### 技术栈
- **后端**: Java 17, Spring Boot 3.5.5 (WebFlux), JPA with Hibernate (ddl-auto: update)
- **前端**: Vue 3 + TypeScript + Element Plus + Vite
- **数据库**: H2 (embedded), JPA访问
- **JWT库**: JJWT 0.12.3 (注意：API与旧版本不兼容)

### JJWT 0.12.x API变更
```java
// 旧API (0.11.x)
Jwts.builder().setSubject(username).setIssuer(issuer).signWith(key, SignatureAlgorithm.HS256)

// 新API (0.12.x)
Jwts.builder().subject(username).issuer(issuer).signWith(key)  // 无需指定算法
Keys.hmacShaKeyFor(bytes)  // 新的密钥生成方式
```

### 配置关键点
- JWT accounts配置路径: `jairouter.security.jwt.accounts`
- 密码格式: `{noop}password` 使用DelegatingPasswordEncoder
- 登录端点: `POST /api/auth/jwt/login`
- Token头: `Jairouter_Token`

### 常见问题
1. **旧进程问题**: 多次遇到旧进程运行导致新代码不生效，必须先`pkill -f model-router`确认进程已停止
2. **数据库表结构**: JPA的`ddl-auto: update`不会修改已存在表的结构，需要使用数据库迁移服务
3. **浏览器缓存**: 测试前端时需要清除浏览器缓存

## Recent Actions

### ✅ 已完成任务 (2026-04-10)

1. **创建数据库迁移服务 `DatabaseMigrationService`**
   - 使用 `ApplicationRunner` 在应用启动后执行迁移
   - 尝试多种 SQL 语法确保兼容 H2 和 MySQL 模式
   - 成功语法: `ALTER TABLE security_blacklist ALTER COLUMN expires_at DROP NOT NULL`

2. **修复永久黑名单功能**
   - 问题: `expires_at` 列定义为 `NOT NULL`，导致永久黑名单（expires_at=null）添加失败
   - 解决: 通过迁移服务修改列定义，允许 NULL 值
   - 验证: API 测试成功添加永久黑名单

3. **API 测试结果**
   ```json
   {
     "success": true,
     "message": "黑名单添加成功",
     "data": {
       "id": 4,
       "blacklistType": "IP",
       "targetValue": "10.0.0.100",
       "expiresAt": null,
       "permanent": true,
       "status": "ACTIVE"
     }
   }
   ```

### 黑名单功能文件列表

| 文件 | 描述 |
|------|------|
| `DatabaseMigrationService.java` | 数据库迁移服务（新建） |
| `SecurityBlacklistEntity.java` | 实体类，支持TOKEN/IP/DEVICE三种类型 |
| `SecurityBlacklistRepository.java` | JPA Repository |
| `SecurityBlacklistService.java` | 服务接口 |
| `SecurityBlacklistServiceImpl.java` | 服务实现 |
| `SecurityBlacklistController.java` | REST API控制器 |
| `BlacklistEntryDTO.java` | 数据传输对象 |
| `AddBlacklistRequest.java` | 添加请求DTO |
| `BlacklistStatsDTO.java` | 统计DTO |
| `frontend/src/api/blacklist.ts` | 前端API |
| `frontend/src/views/security/BlacklistManagement.vue` | 前端管理页面 |
| `frontend/src/router/index.ts` | 路由配置（/security/blacklist） |

## 黑名单API端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/security/blacklist/list` | GET | 分页查询 |
| `/api/security/blacklist/stats` | GET | 统计信息 |
| `/api/security/blacklist/add` | POST | 添加黑名单 |
| `/api/security/blacklist/{id}` | DELETE | 移除条目 |
| `/api/security/blacklist/check` | GET | 检查是否在黑名单 |
| `/api/security/blacklist/cleanup` | POST | 清理过期条目 |

## 启动命令
```bash
# 编译（包含前端）
mvn clean package -Pprod -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true -q

# 启动（确保先杀掉旧进程）
pkill -f model-router
java -jar target/model-router-1.6.1.jar --spring.profiles.active=dev
```

## 前端访问
- 管理界面: http://localhost:8080/admin/index.html
- 黑名单管理: http://localhost:8080/admin/index.html#/security/blacklist

---

## Summary Metadata
**Update time**: 2026-04-10T10:30:00.000Z
**Status**: 所有任务已完成 ✅