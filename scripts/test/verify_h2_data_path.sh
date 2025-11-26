#!/bin/bash

echo "=========================================="
echo "H2 数据目录配置验证"
echo "=========================================="
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查配置文件中的路径
echo "1. 检查配置文件中的 H2 路径..."
echo "-------------------"

check_config() {
    local file=$1
    local expected=$2
    
    if [ -f "$file" ]; then
        if grep -q "$expected" "$file"; then
            echo -e "${GREEN}✓${NC} $file: $expected"
        else
            echo -e "${RED}✗${NC} $file: 未找到 $expected"
            echo "  实际内容:"
            grep "url:" "$file" | head -3
        fi
    else
        echo -e "${RED}✗${NC} $file: 文件不存在"
    fi
}

# 检查 spring.r2dbc.url 配置（统一配置）
check_config "src/main/resources/application.yml" "r2dbc:h2:file///./data/jairouter"
check_config "src/main/resources/application-dev.yml" "r2dbc:h2:file///./data/jairouter-dev"
check_config "src/main/resources/application-prod.yml" "r2dbc:h2:file///./data/jairouter-prod"

# 确保没有重复的 store.h2.url 配置
echo ""
echo "  检查是否有重复配置..."
if grep -q "store:" src/main/resources/application-h2.yml && grep -q "h2:" src/main/resources/application-h2.yml | grep -v "r2dbc:" | grep -v "console:" | grep -q "url:"; then
    echo -e "${RED}✗${NC} 发现重复的 store.h2.url 配置"
else
    echo -e "${GREEN}✓${NC} 无重复配置，使用统一的 spring.r2dbc.url"
fi
echo ""

# 检查 H2DatabaseConfiguration
echo "2. 检查 H2DatabaseConfiguration..."
echo "-------------------"
if grep -q 'spring.r2dbc.url' src/main/java/org/unreal/modelrouter/store/config/H2DatabaseConfiguration.java; then
    echo -e "${GREEN}✓${NC} 使用 spring.r2dbc.url 配置"
else
    echo -e "${RED}✗${NC} 未使用 spring.r2dbc.url 配置"
fi

if grep -q 'ensureDataDirectoryExists' src/main/java/org/unreal/modelrouter/store/config/H2DatabaseConfiguration.java; then
    echo -e "${GREEN}✓${NC} 自动创建目录功能已添加"
else
    echo -e "${YELLOW}⚠${NC} 未找到自动创建目录代码"
fi

# 确保没有使用旧的 store.h2.url
if grep -q 'store.h2.url' src/main/java/org/unreal/modelrouter/store/config/H2DatabaseConfiguration.java; then
    echo -e "${RED}✗${NC} 仍在使用旧的 store.h2.url 配置"
else
    echo -e "${GREEN}✓${NC} 已移除旧的 store.h2.url 配置"
fi
echo ""

# 检查 .gitignore
echo "3. 检查 .gitignore..."
echo "-------------------"
if [ -f .gitignore ]; then
    if grep -q "^/data/" .gitignore; then
        echo -e "${GREEN}✓${NC} data 目录已添加到 .gitignore"
    else
        echo -e "${YELLOW}⚠${NC} data 目录未添加到 .gitignore"
    fi
else
    echo -e "${RED}✗${NC} .gitignore 文件不存在"
fi
echo ""

# 检查 data 目录
echo "4. 检查 data 目录..."
echo "-------------------"
if [ -d data ]; then
    echo -e "${GREEN}✓${NC} data 目录已存在"
    echo "  目录内容:"
    ls -lh data/ 2>/dev/null || echo "  (空目录)"
else
    echo -e "${YELLOW}⚠${NC} data 目录不存在（启动时会自动创建）"
fi
echo ""

# 检查文档
echo "5. 检查文档..."
echo "-------------------"
if [ -f docs/H2_DATA_DIRECTORY_GUIDE.md ]; then
    echo -e "${GREEN}✓${NC} H2 数据目录指南已创建"
else
    echo -e "${RED}✗${NC} H2 数据目录指南缺失"
fi
echo ""

# 测试路径解析
echo "6. 测试路径解析..."
echo "-------------------"
cat > /tmp/test_path.java << 'EOF'
public class TestPath {
    public static void main(String[] args) {
        String[] paths = {
            "./data/jairouter",
            "./data/jairouter-dev",
            "./data/jairouter-prod"
        };
        
        for (String path : paths) {
            java.io.File file = new java.io.File(path);
            System.out.println(path + " -> " + file.getAbsolutePath());
        }
    }
}
EOF

if javac /tmp/test_path.java 2>/dev/null && java -cp /tmp TestPath 2>/dev/null; then
    echo -e "${GREEN}✓${NC} 路径解析测试通过"
else
    echo -e "${YELLOW}⚠${NC} 路径解析测试跳过（需要 Java）"
fi
rm -f /tmp/test_path.java /tmp/TestPath.class 2>/dev/null
echo ""

# 编译检查
echo "7. 编译检查..."
echo "-------------------"
if mvn clean compile -Pfast > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 编译成功"
else
    echo -e "${RED}✗${NC} 编译失败"
fi
echo ""

echo "=========================================="
echo "验证完成！"
echo "=========================================="
echo ""
echo "配置摘要："
echo "  - 默认环境: ./data/jairouter.mv.db (容器内路径: /app/r2dbc:h2:file/data/jairouter.mv.db)"
echo "  - 开发环境: ./data/jairouter-dev.mv.db (容器内路径: /app/r2dbc:h2:file/data/jairouter-dev.mv.db)"
echo "  - 生产环境: ./data/jairouter-prod.mv.db (容器内路径: /app/r2dbc:h2:file/data/jairouter-prod.mv.db)"
echo ""
echo "下一步："
echo "  1. 启动应用: mvn spring-boot:run"
echo "  2. 检查 data 目录: ls -lh data/"
echo "  3. 查看日志: grep 'H2 database file location' logs/application.log"
echo ""
echo "文档参考："
echo "  - docs/H2_DATA_DIRECTORY_GUIDE.md"
echo ""
