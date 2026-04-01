#!/bin/bash

echo "=== 数据库迁移：添加限流器和熔断器字段 ==="
echo ""

# H2 数据库文件路径
DB_PATH="/home/ubuntu/jairouter/modelrouter/data/jairouter-dev.mv.db"

# 检查数据库文件是否存在
if [ ! -f "$DB_PATH" ]; then
    echo "✗ 数据库文件不存在：$DB_PATH"
    echo "  请确保应用程序至少运行过一次以创建数据库"
    exit 1
fi

echo "✓ 找到数据库文件：$DB_PATH"
echo ""

# 创建 SQL 迁移脚本
MIGRATION_SQL="/tmp/migrate_database.sql"
cat > "$MIGRATION_SQL" << 'EOF'
-- 添加限流器相关字段（如果不存在）
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS rate_limit_client_ip_enable BOOLEAN DEFAULT FALSE;

-- 添加熔断器相关字段（如果不存在）
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_failure_threshold INT DEFAULT 5;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_timeout INT DEFAULT 60000;
ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_success_threshold INT DEFAULT 2;
EOF

echo "创建的迁移 SQL:"
cat "$MIGRATION_SQL"
echo ""

# 使用 H2 命令行工具执行迁移（如果可用）
if command -v h2sh &> /dev/null; then
    echo "使用 H2 Shell 执行迁移..."
    h2sh -url "jdbc:h2:$DB_PATH" -script "$MIGRATION_SQL"
    echo "✓ 迁移完成"
else
    echo "⚠ H2 Shell 不可用，将使用 Java 执行迁移"
    
    # 创建 Java 迁移程序
    JAVA_MIGRATE="/tmp/H2Migrate.java"
    cat > "$JAVA_MIGRATE" << 'JAVAEOF'
import java.sql.*;

public class H2Migrate {
    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "/home/ubuntu/jairouter/modelrouter/data/jairouter-dev";
        String url = "jdbc:h2:" + dbPath;
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            System.out.println("✓ 数据库连接成功");
            
            String[] statements = {
                "ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS rate_limit_client_ip_enable BOOLEAN DEFAULT FALSE",
                "ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_enabled BOOLEAN DEFAULT FALSE",
                "ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_failure_threshold INT DEFAULT 5",
                "ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_timeout INT DEFAULT 60000",
                "ALTER TABLE service_instance ADD COLUMN IF NOT EXISTS circuit_breaker_success_threshold INT DEFAULT 2"
            };
            
            for (String sql : statements) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    System.out.println("✓ 执行：" + sql);
                } catch (SQLException e) {
                    if (e.getSQLState().equals("42S21")) {
                        System.out.println("⚠ 列已存在：" + sql);
                    } else {
                        System.out.println("✗ 执行失败：" + e.getMessage());
                    }
                }
            }
            
            // 验证列是否存在
            System.out.println("\n验证列是否存在:");
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, "SERVICE_INSTANCE", null)) {
                boolean hasCircuitBreaker = false;
                boolean hasRateLimitClientIp = false;
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (columnName.equals("CIRCUIT_BREAKER_ENABLED")) hasCircuitBreaker = true;
                    if (columnName.equals("RATE_LIMIT_CLIENT_IP_ENABLE")) hasRateLimitClientIp = true;
                }
                System.out.println("  CIRCUIT_BREAKER_ENABLED: " + (hasCircuitBreaker ? "✓ 存在" : "✗ 不存在"));
                System.out.println("  RATE_LIMIT_CLIENT_IP_ENABLE: " + (hasRateLimitClientIp ? "✓ 存在" : "✗ 不存在"));
            }
        }
    }
}
JAVAEOF
    
    # 编译并运行 Java 迁移程序
    cd /tmp
    javac H2Migrate.java 2>/dev/null
    if [ $? -eq 0 ]; then
        java -cp /tmp H2Migrate "$DB_PATH"
    else
        echo "✗ Java 迁移程序编译失败"
        exit 1
    fi
fi

echo ""
echo "=== 数据库迁移完成 ==="