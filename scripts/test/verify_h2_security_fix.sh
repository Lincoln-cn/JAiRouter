#!/bin/bash

echo "=========================================="
echo "H2 安全持久化修复验证脚本"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查文件是否存在
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $1"
        return 0
    else
        echo -e "${RED}✗${NC} $1 (缺失)"
        return 1
    fi
}

echo "1. 检查新增文件..."
echo "-------------------"
check_file "src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditServiceImpl.java"
check_file "src/main/java/org/unreal/modelrouter/store/entity/JwtBlacklistEntity.java"
check_file "src/main/java/org/unreal/modelrouter/store/repository/JwtBlacklistRepository.java"
check_file "src/main/java/org/unreal/modelrouter/security/service/H2JwtBlacklistService.java"
check_file "src/main/java/org/unreal/modelrouter/security/config/JwtBlacklistInitializer.java"
check_file "src/main/java/org/unreal/modelrouter/security/scheduler/SecurityDataCleanupScheduler.java"
check_file "src/test/java/org/unreal/modelrouter/security/H2SecurityPersistenceTest.java"
echo ""

echo "2. 检查数据库表定义..."
echo "-------------------"
if grep -q "jwt_blacklist" src/main/resources/schema.sql; then
    echo -e "${GREEN}✓${NC} jwt_blacklist 表已添加到 schema.sql"
else
    echo -e "${RED}✗${NC} jwt_blacklist 表未找到"
fi
echo ""

echo "3. 检查配置文件..."
echo "-------------------"
if grep -q "cleanup:" src/main/resources/application-h2.yml; then
    echo -e "${GREEN}✓${NC} 清理任务配置已添加"
else
    echo -e "${YELLOW}⚠${NC} 清理任务配置未找到"
fi
echo ""

echo "4. 检查代码注解..."
echo "-------------------"
if grep -q '@Primary' src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditServiceImpl.java; then
    echo -e "${GREEN}✓${NC} H2SecurityAuditServiceImpl 标记为 @Primary"
else
    echo -e "${RED}✗${NC} H2SecurityAuditServiceImpl 未标记为 @Primary"
fi

if grep -q '@Primary' src/main/java/org/unreal/modelrouter/security/service/H2JwtBlacklistService.java; then
    echo -e "${GREEN}✓${NC} H2JwtBlacklistService 标记为 @Primary"
else
    echo -e "${RED}✗${NC} H2JwtBlacklistService 未标记为 @Primary"
fi
echo ""

echo "5. 统计代码行数..."
echo "-------------------"
total_lines=0
for file in \
    "src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditServiceImpl.java" \
    "src/main/java/org/unreal/modelrouter/store/entity/JwtBlacklistEntity.java" \
    "src/main/java/org/unreal/modelrouter/store/repository/JwtBlacklistRepository.java" \
    "src/main/java/org/unreal/modelrouter/security/service/H2JwtBlacklistService.java" \
    "src/main/java/org/unreal/modelrouter/security/config/JwtBlacklistInitializer.java" \
    "src/main/java/org/unreal/modelrouter/security/scheduler/SecurityDataCleanupScheduler.java"
do
    if [ -f "$file" ]; then
        lines=$(wc -l < "$file")
        total_lines=$((total_lines + lines))
        echo "  $(basename $file): $lines 行"
    fi
done
echo -e "${GREEN}总计: $total_lines 行代码${NC}"
echo ""

echo "6. 编译检查..."
echo "-------------------"
echo "运行 Maven 编译..."
if mvn clean compile -DskipTests > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 编译成功"
else
    echo -e "${RED}✗${NC} 编译失败，请检查错误信息"
    echo "运行以下命令查看详细错误："
    echo "  mvn clean compile"
fi
echo ""

echo "7. 运行测试..."
echo "-------------------"
echo "运行 H2 安全持久化测试..."
if mvn test -Dtest=H2SecurityPersistenceTest > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 测试通过"
else
    echo -e "${YELLOW}⚠${NC} 测试失败或跳过（可能需要配置）"
    echo "运行以下命令查看详细信息："
    echo "  mvn test -Dtest=H2SecurityPersistenceTest"
fi
echo ""

echo "=========================================="
echo "验证完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 启动应用: mvn spring-boot:run -Dspring-boot.run.profiles=h2"
echo "2. 查看日志确认 H2 审计服务和黑名单服务已启动"
echo "3. 测试审计功能和黑名单功能"
echo "4. 检查 H2 数据库文件: ./data/config.mv.db"
echo ""
echo "文档参考："
echo "- H2_SECURITY_PERSISTENCE_FIX_SUMMARY.md"
echo "- H2_PERSISTENCE_COVERAGE_REPORT.md"
echo ""
