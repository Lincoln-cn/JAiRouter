#!/bin/bash

# JWT Persistence Configuration Validation Script
# This script validates the JWT persistence configuration and dependencies

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CONFIG_DIR="$PROJECT_ROOT/config"
DOCKER_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
DOCKER_COMPOSE_PROD_FILE="$PROJECT_ROOT/docker-compose.prod.yml"

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Validation functions
validate_environment_variables() {
    log_info "Validating environment variables..."
    
    local required_vars=("JWT_SECRET" "REDIS_PASSWORD")
    local missing_vars=()
    
    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            missing_vars+=("$var")
        fi
    done
    
    if [ ${#missing_vars[@]} -gt 0 ]; then
        log_error "Missing required environment variables: ${missing_vars[*]}"
        log_info "Please set the following environment variables:"
        for var in "${missing_vars[@]}"; do
            case $var in
                "JWT_SECRET")
                    echo "  export JWT_SECRET=\"your-very-secure-jwt-secret-key-at-least-32-characters-long\""
                    ;;
                "REDIS_PASSWORD")
                    echo "  export REDIS_PASSWORD=\"your-secure-redis-password\""
                    ;;
            esac
        done
        return 1
    fi
    
    # Validate JWT_SECRET strength
    if [ ${#JWT_SECRET} -lt 32 ]; then
        log_warning "JWT_SECRET should be at least 32 characters long for security"
    fi
    
    log_success "Environment variables validation passed"
    return 0
}

validate_configuration_files() {
    log_info "Validating configuration files..."
    
    local config_files=(
        "$CONFIG_DIR/redis.conf"
        "$PROJECT_ROOT/src/main/resources/config/security/persistence-base.yml"
        "$PROJECT_ROOT/src/main/resources/application-prod.yml"
    )
    
    for file in "${config_files[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "Configuration file not found: $file"
            return 1
        fi
    done
    
    # Validate YAML syntax
    if command -v yq >/dev/null 2>&1; then
        log_info "Validating YAML syntax..."
        for file in "${config_files[@]}"; do
            if [[ "$file" == *.yml ]] || [[ "$file" == *.yaml ]]; then
                if ! yq eval '.' "$file" >/dev/null 2>&1; then
                    log_error "Invalid YAML syntax in: $file"
                    return 1
                fi
            fi
        done
        log_success "YAML syntax validation passed"
    else
        log_warning "yq not found, skipping YAML syntax validation"
    fi
    
    log_success "Configuration files validation passed"
    return 0
}

validate_docker_configuration() {
    log_info "Validating Docker configuration..."
    
    # Check if Docker is installed and running
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is not installed"
        return 1
    fi
    
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker is not running"
        return 1
    fi
    
    # Check if Docker Compose is installed
    if ! command -v docker-compose >/dev/null 2>&1; then
        log_error "Docker Compose is not installed"
        return 1
    fi
    
    # Validate Docker Compose files
    local compose_files=("$DOCKER_COMPOSE_FILE" "$DOCKER_COMPOSE_PROD_FILE")
    
    for file in "${compose_files[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "Docker Compose file not found: $file"
            return 1
        fi
        
        if ! docker-compose -f "$file" config >/dev/null 2>&1; then
            log_error "Invalid Docker Compose configuration: $file"
            return 1
        fi
    done
    
    log_success "Docker configuration validation passed"
    return 0
}

validate_redis_connectivity() {
    log_info "Validating Redis connectivity..."
    
    # Check if Redis is running (if Docker Compose is up)
    if docker-compose ps redis >/dev/null 2>&1; then
        local redis_container=$(docker-compose ps -q redis)
        if [ -n "$redis_container" ]; then
            if docker exec "$redis_container" redis-cli ping >/dev/null 2>&1; then
                log_success "Redis connectivity validation passed"
                return 0
            else
                log_warning "Redis container is running but not responding to ping"
                return 1
            fi
        fi
    fi
    
    log_info "Redis container not running, skipping connectivity test"
    return 0
}

validate_application_health() {
    log_info "Validating application health..."
    
    # Check if JAiRouter is running
    if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
        log_success "JAiRouter application is healthy"
        
        # Check JWT persistence specific health
        if curl -f http://localhost:8080/actuator/health/jwt-persistence >/dev/null 2>&1; then
            log_success "JWT persistence health check passed"
        else
            log_warning "JWT persistence health check endpoint not available"
        fi
        
        return 0
    else
        log_info "JAiRouter application not running, skipping health check"
        return 0
    fi
}

validate_monitoring_configuration() {
    log_info "Validating monitoring configuration..."
    
    local monitoring_files=(
        "$PROJECT_ROOT/monitoring/prometheus/prometheus.yml"
        "$PROJECT_ROOT/monitoring/prometheus/rules/jwt-persistence-alerts.yml"
    )
    
    for file in "${monitoring_files[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "Monitoring configuration file not found: $file"
            return 1
        fi
    done
    
    # Validate Prometheus configuration
    if command -v promtool >/dev/null 2>&1; then
        if ! promtool check config "$PROJECT_ROOT/monitoring/prometheus/prometheus.yml" >/dev/null 2>&1; then
            log_error "Invalid Prometheus configuration"
            return 1
        fi
        
        if ! promtool check rules "$PROJECT_ROOT/monitoring/prometheus/rules/jwt-persistence-alerts.yml" >/dev/null 2>&1; then
            log_error "Invalid Prometheus alert rules"
            return 1
        fi
        
        log_success "Prometheus configuration validation passed"
    else
        log_warning "promtool not found, skipping Prometheus configuration validation"
    fi
    
    log_success "Monitoring configuration validation passed"
    return 0
}

generate_deployment_checklist() {
    log_info "Generating deployment checklist..."
    
    cat << EOF

=== JWT Persistence Deployment Checklist ===

Pre-deployment:
□ Environment variables set (JWT_SECRET, REDIS_PASSWORD)
□ Configuration files validated
□ Docker and Docker Compose installed
□ Monitoring configuration validated

Deployment:
□ Start Redis container: docker-compose up -d redis
□ Wait for Redis to be healthy
□ Start JAiRouter: docker-compose up -d jairouter
□ Verify application health: curl http://localhost:8080/actuator/health
□ Verify JWT persistence: curl http://localhost:8080/actuator/health/jwt-persistence

Post-deployment:
□ Start monitoring stack: docker-compose -f docker-compose-monitoring.yml up -d
□ Configure Grafana dashboards
□ Test JWT token operations
□ Verify audit logging
□ Set up backup procedures

Security checklist:
□ JWT_SECRET is strong (32+ characters)
□ Redis password is set and strong
□ Audit logging is enabled
□ Monitoring alerts are configured
□ Access controls are in place

EOF
}

run_comprehensive_test() {
    log_info "Running comprehensive JWT persistence test..."
    
    # Test JWT token creation
    log_info "Testing JWT token creation..."
    local login_response=$(curl -s -X POST http://localhost:8080/api/auth/jwt/login \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}')
    
    if echo "$login_response" | grep -q "token"; then
        log_success "JWT token creation test passed"
        
        # Extract token for further tests
        local token=$(echo "$login_response" | jq -r '.data.token' 2>/dev/null)
        
        if [ "$token" != "null" ] && [ -n "$token" ]; then
            # Test token validation
            log_info "Testing JWT token validation..."
            if curl -s -H "Authorization: Bearer $token" http://localhost:8080/actuator/health >/dev/null 2>&1; then
                log_success "JWT token validation test passed"
            else
                log_warning "JWT token validation test failed"
            fi
            
            # Test token management API
            log_info "Testing JWT token management API..."
            if curl -s -H "Authorization: Bearer $token" http://localhost:8080/api/auth/jwt/tokens >/dev/null 2>&1; then
                log_success "JWT token management API test passed"
            else
                log_warning "JWT token management API test failed"
            fi
        fi
    else
        log_warning "JWT token creation test failed - application may not be running"
    fi
}

# Main execution
main() {
    echo "=== JWT Persistence Configuration Validation ==="
    echo "Project root: $PROJECT_ROOT"
    echo ""
    
    local validation_passed=true
    
    # Run all validations
    validate_environment_variables || validation_passed=false
    echo ""
    
    validate_configuration_files || validation_passed=false
    echo ""
    
    validate_docker_configuration || validation_passed=false
    echo ""
    
    validate_redis_connectivity || validation_passed=false
    echo ""
    
    validate_application_health || validation_passed=false
    echo ""
    
    validate_monitoring_configuration || validation_passed=false
    echo ""
    
    # Generate deployment checklist
    generate_deployment_checklist
    echo ""
    
    # Run comprehensive test if application is running
    if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
        run_comprehensive_test
        echo ""
    fi
    
    # Final result
    if [ "$validation_passed" = true ]; then
        log_success "All validations passed! JWT persistence configuration is ready for deployment."
        exit 0
    else
        log_error "Some validations failed. Please fix the issues before deployment."
        exit 1
    fi
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [--help|--checklist-only]"
        echo ""
        echo "Options:"
        echo "  --help, -h          Show this help message"
        echo "  --checklist-only    Only generate deployment checklist"
        exit 0
        ;;
    --checklist-only)
        generate_deployment_checklist
        exit 0
        ;;
    *)
        main
        ;;
esac