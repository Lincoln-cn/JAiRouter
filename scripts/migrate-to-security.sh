#!/bin/bash

# JAiRouter 安全功能迁移脚本
# 用于帮助用户从传统模式迁移到安全增强模式

set -e

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_ROOT/config-backup-$(date +%Y%m%d-%H%M%S)"
MIGRATION_LOG="$PROJECT_ROOT/migration-$(date +%Y%m%d-%H%M%S).log"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    local message="$1"
    echo -e "${BLUE}[INFO]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $message" >> "$MIGRATION_LOG"
}

log_success() {
    local message="$1"
    echo -e "${GREEN}[SUCCESS]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [SUCCESS] $message" >> "$MIGRATION_LOG"
}

log_warning() {
    local message="$1"
    echo -e "${YELLOW}[WARNING]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARNING] $message" >> "$MIGRATION_LOG"
}

log_error() {
    local message="$1"
    echo -e "${RED}[ERROR]${NC} $message"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $message" >> "$MIGRATION_LOG"
}

# 显示帮助信息
show_help() {
    cat << EOF
JAiRouter 安全功能迁移脚本

用法: $0 [选项]

选项:
    -h, --help              显示此帮助信息
    -c, --check             检查当前配置和迁移准备情况
    -b, --backup            仅备份当前配置
    -m, --migrate           执行完整迁移
    -r, --rollback          回滚到迁移前状态
    -s, --step-by-step      分步骤迁移（交互式）
    -f, --force             强制执行（跳过确认）
    --dry-run               模拟运行（不实际修改文件）

迁移阶段:
    1. 检查和备份当前配置
    2. 生成安全配置模板
    3. 更新应用配置文件
    4. 配置环境变量
    5. 验证迁移结果

示例:
    $0 -c                   # 检查迁移准备情况
    $0 -b                   # 备份当前配置
    $0 -m                   # 执行完整迁移
    $0 -s                   # 分步骤迁移
    $0 -r                   # 回滚迁移

EOF
}

# 检查迁移准备情况
check_migration_readiness() {
    log_info "检查迁移准备情况..."
    
    local issues=0
    
    # 检查Java版本
    if command -v java >/dev/null 2>&1; then
        local java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$java_version" -ge 17 ]]; then
            log_success "Java版本检查通过: $java_version"
        else
            log_error "Java版本过低: $java_version (需要17+)"
            issues=$((issues + 1))
        fi
    else
        log_error "未找到Java运行环境"
        issues=$((issues + 1))
    fi
    
    # 检查Spring Boot版本
    if [[ -f "$PROJECT_ROOT/pom.xml" ]]; then
        local spring_boot_version=$(grep -o '<spring-boot.version>[^<]*' "$PROJECT_ROOT/pom.xml" | cut -d'>' -f2)
        if [[ -n "$spring_boot_version" ]]; then
            log_success "Spring Boot版本: $spring_boot_version"
        else
            log_warning "无法确定Spring Boot版本"
        fi
    else
        log_error "未找到pom.xml文件"
        issues=$((issues + 1))
    fi
    
    # 检查当前配置文件
    local config_files=(
        "$PROJECT_ROOT/src/main/resources/application.yml"
        "$PROJECT_ROOT/config/application.yml"
        "$PROJECT_ROOT/application.yml"
    )
    
    local config_found=false
    for config_file in "${config_files[@]}"; do
        if [[ -f "$config_file" ]]; then
            log_success "找到配置文件: $config_file"
            config_found=true
            
            # 检查是否已经包含安全配置
            if grep -q "jairouter.security" "$config_file"; then
                log_warning "配置文件已包含安全配置，可能已经迁移过"
            fi
            break
        fi
    done
    
    if [[ "$config_found" != "true" ]]; then
        log_error "未找到应用配置文件"
        issues=$((issues + 1))
    fi
    
    # 检查磁盘空间
    local available_space=$(df "$PROJECT_ROOT" | tail -1 | awk '{print $4}')
    if [[ "$available_space" -gt 1048576 ]]; then  # 1GB
        log_success "磁盘空间充足"
    else
        log_warning "磁盘空间可能不足，建议清理后再迁移"
    fi
    
    # 检查是否有运行中的JAiRouter实例
    if pgrep -f "model-router" >/dev/null; then
        log_warning "检测到运行中的JAiRouter实例，建议停止后再迁移"
    else
        log_success "没有运行中的JAiRouter实例"
    fi
    
    # 总结检查结果
    if [[ $issues -eq 0 ]]; then
        log_success "迁移准备检查通过，可以开始迁移"
        return 0
    else
        log_error "发现 $issues 个问题，请解决后再进行迁移"
        return 1
    fi
}

# 备份当前配置
backup_current_config() {
    log_info "备份当前配置到: $BACKUP_DIR"
    
    mkdir -p "$BACKUP_DIR"
    
    # 备份配置文件
    local config_files=(
        "$PROJECT_ROOT/src/main/resources/application.yml"
        "$PROJECT_ROOT/src/main/resources/application.properties"
        "$PROJECT_ROOT/config"
        "$PROJECT_ROOT/.env"
        "$PROJECT_ROOT/docker-compose.yml"
        "$PROJECT_ROOT/docker-compose.dev.yml"
    )
    
    for item in "${config_files[@]}"; do
        if [[ -e "$item" ]]; then
            cp -r "$item" "$BACKUP_DIR/" 2>/dev/null || true
            log_success "已备份: $(basename "$item")"
        fi
    done
    
    # 创建备份信息文件
    cat > "$BACKUP_DIR/backup-info.txt" << EOF
JAiRouter 配置备份信息
备份时间: $(date)
备份目录: $BACKUP_DIR
原始目录: $PROJECT_ROOT
迁移日志: $MIGRATION_LOG

备份内容:
$(ls -la "$BACKUP_DIR")

恢复方法:
1. 停止JAiRouter服务
2. 将备份文件复制回原位置
3. 重启服务

注意: 此备份不包含运行时数据，仅包含配置文件
EOF
    
    log_success "配置备份完成"
}

# 生成安全配置模板
generate_security_config() {
    log_info "生成安全配置模板..."
    
    local security_config_file="$PROJECT_ROOT/security-config-template.yml"
    
    cat > "$security_config_file" << 'EOF'
# JAiRouter 安全功能配置模板
# 此文件包含启用安全功能所需的基本配置
# 请根据实际需求修改相关参数

jairouter:
  security:
    # 安全功能总开关
    enabled: true
    
    # API Key 认证配置
    api-key:
      enabled: true
      header-name: "X-API-Key"
      keys:
        # 管理员API Key - 请修改为强密钥
        - key-id: "admin-key-001"
          key-value: "${ADMIN_API_KEY:请设置强密钥}"
          description: "管理员API密钥"
          permissions: ["admin", "read", "write"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
        
        # 用户API Key - 请修改为强密钥
        - key-id: "user-key-001"
          key-value: "${USER_API_KEY:请设置强密钥}"
          description: "用户API密钥"
          permissions: ["read"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
    
    # 数据脱敏配置
    sanitization:
      request:
        enabled: true
        sensitive-words:
          - "password"
          - "secret"
          - "token"
        pii-patterns:
          - "\\d{11}"  # 手机号
          - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"  # 邮箱
      
      response:
        enabled: true
        sensitive-words:
          - "internal"
          - "debug"
    
    # 安全审计配置
    audit:
      enabled: true
      log-level: "INFO"
      retention-days: 90

# 环境变量配置示例
# 请在 .env 文件或系统环境变量中设置以下值:
#
# ADMIN_API_KEY=your-strong-admin-key-here
# USER_API_KEY=your-strong-user-key-here
# JWT_SECRET=your-jwt-secret-key-here (如果启用JWT)
# REDIS_PASSWORD=your-redis-password (如果使用Redis缓存)
EOF
    
    log_success "安全配置模板已生成: $security_config_file"
    
    # 生成环境变量模板
    local env_template_file="$PROJECT_ROOT/.env.security.template"
    
    cat > "$env_template_file" << 'EOF'
# JAiRouter 安全功能环境变量模板
# 复制此文件为 .env 并设置实际的密钥值

# 安全功能开关
JAIROUTER_SECURITY_ENABLED=true

# API Key 配置
ADMIN_API_KEY=your-admin-api-key-here-please-change-this
USER_API_KEY=your-user-api-key-here-please-change-this

# JWT 配置 (可选)
JAIROUTER_SECURITY_JWT_ENABLED=false
JWT_SECRET=your-jwt-secret-key-here-please-change-this

# Redis 配置 (可选)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password-here

# 重要提醒:
# 1. 请将所有 "please-change-this" 替换为强密钥
# 2. API Key 建议至少16位随机字符
# 3. JWT Secret 建议至少32位随机字符
# 4. 生产环境中请使用更复杂的密钥
EOF
    
    log_success "环境变量模板已生成: $env_template_file"
}

# 更新应用配置文件
update_application_config() {
    log_info "更新应用配置文件..."
    
    local main_config="$PROJECT_ROOT/src/main/resources/application.yml"
    
    if [[ ! -f "$main_config" ]]; then
        log_error "未找到主配置文件: $main_config"
        return 1
    fi
    
    # 检查是否已经包含安全配置
    if grep -q "jairouter.security" "$main_config"; then
        log_warning "配置文件已包含安全配置，跳过更新"
        return 0
    fi
    
    # 在配置文件末尾添加安全配置引用
    cat >> "$main_config" << 'EOF'

# ========================================
# 安全功能配置引用
# ========================================
# 安全配置已移至独立的配置文件中
# 如需启用安全功能，请参考 security-config-template.yml
# 并将相关配置合并到此文件中

# 向后兼容模式:
# 如需保持传统模式运行，请使用以下配置:
# spring.profiles.active: legacy
EOF
    
    log_success "应用配置文件已更新"
}

# 分步骤迁移
step_by_step_migration() {
    log_info "开始分步骤迁移..."
    
    echo ""
    echo "=== JAiRouter 安全功能迁移向导 ==="
    echo ""
    
    # 步骤1: 确认迁移
    read -p "是否要开始迁移到安全增强模式? (y/N): " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
        log_info "迁移已取消"
        return 0
    fi
    
    # 步骤2: 检查准备情况
    echo ""
    log_info "步骤 1/5: 检查迁移准备情况"
    if ! check_migration_readiness; then
        read -p "发现问题，是否继续? (y/N): " continue_anyway
        if [[ "$continue_anyway" != "y" && "$continue_anyway" != "Y" ]]; then
            log_info "迁移已终止"
            return 1
        fi
    fi
    
    # 步骤3: 备份配置
    echo ""
    log_info "步骤 2/5: 备份当前配置"
    backup_current_config
    
    # 步骤4: 生成配置模板
    echo ""
    log_info "步骤 3/5: 生成安全配置模板"
    generate_security_config
    
    # 步骤5: 配置API Key
    echo ""
    log_info "步骤 4/5: 配置API Key"
    echo "请设置API Key (留空将生成随机密钥):"
    
    read -p "管理员API Key: " admin_key
    if [[ -z "$admin_key" ]]; then
        admin_key=$(openssl rand -hex 16)
        log_info "已生成管理员API Key: $admin_key"
    fi
    
    read -p "用户API Key: " user_key
    if [[ -z "$user_key" ]]; then
        user_key=$(openssl rand -hex 16)
        log_info "已生成用户API Key: $user_key"
    fi
    
    # 创建环境变量文件
    local env_file="$PROJECT_ROOT/.env"
    cat > "$env_file" << EOF
# JAiRouter 安全功能环境变量
# 由迁移脚本自动生成于 $(date)

JAIROUTER_SECURITY_ENABLED=true
ADMIN_API_KEY=$admin_key
USER_API_KEY=$user_key
EOF
    
    log_success "环境变量文件已创建: $env_file"
    
    # 步骤6: 完成迁移
    echo ""
    log_info "步骤 5/5: 完成迁移配置"
    update_application_config
    
    echo ""
    log_success "迁移完成！"
    echo ""
    echo "下一步操作:"
    echo "1. 检查生成的配置文件并根据需要调整"
    echo "2. 重启JAiRouter服务"
    echo "3. 使用API Key测试访问: curl -H 'X-API-Key: $admin_key' http://localhost:8080/v1/models"
    echo "4. 查看迁移日志: $MIGRATION_LOG"
    echo ""
    echo "如需回滚，请运行: $0 -r"
}

# 回滚迁移
rollback_migration() {
    log_info "开始回滚迁移..."
    
    # 查找最新的备份目录
    local latest_backup=$(find "$PROJECT_ROOT" -maxdepth 1 -name "config-backup-*" -type d | sort | tail -1)
    
    if [[ -z "$latest_backup" ]]; then
        log_error "未找到备份目录，无法回滚"
        return 1
    fi
    
    log_info "使用备份目录: $latest_backup"
    
    read -p "确认要回滚到备份状态吗? 这将覆盖当前配置 (y/N): " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
        log_info "回滚已取消"
        return 0
    fi
    
    # 恢复配置文件
    if [[ -f "$latest_backup/application.yml" ]]; then
        cp "$latest_backup/application.yml" "$PROJECT_ROOT/src/main/resources/"
        log_success "已恢复 application.yml"
    fi
    
    if [[ -d "$latest_backup/config" ]]; then
        cp -r "$latest_backup/config" "$PROJECT_ROOT/"
        log_success "已恢复 config 目录"
    fi
    
    if [[ -f "$latest_backup/.env" ]]; then
        cp "$latest_backup/.env" "$PROJECT_ROOT/"
        log_success "已恢复 .env 文件"
    fi
    
    # 删除迁移生成的文件
    local migration_files=(
        "$PROJECT_ROOT/security-config-template.yml"
        "$PROJECT_ROOT/.env.security.template"
    )
    
    for file in "${migration_files[@]}"; do
        if [[ -f "$file" ]]; then
            rm "$file"
            log_success "已删除迁移文件: $(basename "$file")"
        fi
    done
    
    log_success "回滚完成！"
    log_info "请重启JAiRouter服务以应用回滚的配置"
}

# 主函数
main() {
    local check_only=false
    local backup_only=false
    local migrate=false
    local rollback=false
    local step_by_step=false
    local force=false
    local dry_run=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -c|--check)
                check_only=true
                shift
                ;;
            -b|--backup)
                backup_only=true
                shift
                ;;
            -m|--migrate)
                migrate=true
                shift
                ;;
            -r|--rollback)
                rollback=true
                shift
                ;;
            -s|--step-by-step)
                step_by_step=true
                shift
                ;;
            -f|--force)
                force=true
                shift
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 创建迁移日志
    echo "JAiRouter 安全功能迁移日志 - $(date)" > "$MIGRATION_LOG"
    log_info "迁移日志: $MIGRATION_LOG"
    
    # 执行相应操作
    if [[ "$check_only" == "true" ]]; then
        check_migration_readiness
    elif [[ "$backup_only" == "true" ]]; then
        backup_current_config
    elif [[ "$rollback" == "true" ]]; then
        rollback_migration
    elif [[ "$step_by_step" == "true" ]]; then
        step_by_step_migration
    elif [[ "$migrate" == "true" ]]; then
        # 完整迁移
        check_migration_readiness
        backup_current_config
        generate_security_config
        update_application_config
        log_success "迁移完成！请查看生成的配置文件并重启服务"
    else
        # 默认显示帮助
        show_help
    fi
}

# 执行主函数
main "$@"