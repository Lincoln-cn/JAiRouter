# JWT Persistence Deployment Checklist

<!-- Version Information -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2025-01-15  
> **Git Commit**: -  
> **Author**: System
<!-- /Version Information -->

This checklist ensures proper deployment of JWT token persistence features in JAiRouter.

## Pre-Deployment Checklist

### Environment Setup

- [ ] **Java Environment**
  - [ ] Java 17 or higher installed
  - [ ] JAVA_HOME environment variable set
  - [ ] Sufficient heap memory allocated (minimum 2GB for production)

- [ ] **Docker Environment**
  - [ ] Docker Engine 20.10+ installed
  - [ ] Docker Compose 2.0+ installed
  - [ ] Docker daemon running
  - [ ] Sufficient disk space for containers and volumes

- [ ] **Network Configuration**
  - [ ] Required ports available (8080, 6379, 9090, 3000)
  - [ ] Firewall rules configured
  - [ ] DNS resolution working

### Configuration Validation

- [ ] **Environment Variables**
  - [ ] `JWT_SECRET` set (minimum 32 characters)
  - [ ] `REDIS_PASSWORD` set (strong password)
  - [ ] `JWT_EXPIRATION_MINUTES` configured (recommended: 15 for production)
  - [ ] `JWT_REFRESH_EXPIRATION_DAYS` configured (recommended: 30 for production)

- [ ] **Configuration Files**
  - [ ] `config/redis.conf` exists and is valid
  - [ ] `src/main/resources/config/security/persistence-base.yml` configured
  - [ ] Environment-specific configuration files updated
  - [ ] YAML syntax validated

- [ ] **Docker Configuration**
  - [ ] `docker-compose.yml` updated with Redis service
  - [ ] `docker-compose.prod.yml` configured for production
  - [ ] Volume mounts configured correctly
  - [ ] Network configuration validated

### Security Configuration

- [ ] **JWT Security**
  - [ ] Strong JWT secret key generated
  - [ ] Appropriate token expiration times set
  - [ ] Blacklist persistence enabled
  - [ ] Audit logging configured

- [ ] **Redis Security**
  - [ ] Redis password authentication enabled
  - [ ] Redis configuration file secured
  - [ ] Dangerous Redis commands disabled
  - [ ] Network access restricted

- [ ] **Application Security**
  - [ ] Security audit logging enabled
  - [ ] API access controls configured
  - [ ] HTTPS/TLS configured (production)
  - [ ] Security headers configured

## Deployment Steps

### Step 1: Configuration Validation

```bash
# Run configuration validation script
./scripts/validate-jwt-persistence-config.sh

# Check Docker Compose configuration
docker-compose config
docker-compose -f docker-compose.prod.yml config
```

### Step 2: Start Infrastructure Services

```bash
# Start Redis first
docker-compose up -d redis

# Wait for Redis to be healthy
docker-compose ps redis
docker-compose logs redis

# Verify Redis connectivity
docker-compose exec redis redis-cli ping
```

### Step 3: Start Application

```bash
# Start JAiRouter application
docker-compose up -d jairouter

# Monitor startup logs
docker-compose logs -f jairouter

# Wait for application to be ready
curl -f http://localhost:8080/actuator/health
```

### Step 4: Verify JWT Persistence

```bash
# Check JWT persistence health
curl http://localhost:8080/actuator/health/jwt-persistence

# Test JWT token creation
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'

# Test token management API
curl -H "Authorization: Bearer <token>" \
     http://localhost:8080/api/auth/jwt/tokens
```

### Step 5: Start Monitoring (Optional)

```bash
# Start monitoring stack
docker-compose -f docker-compose-monitoring.yml up -d

# Verify Prometheus
curl http://localhost:9090/api/v1/targets

# Verify Grafana
curl http://localhost:3000/api/health
```

## Post-Deployment Verification

### Functional Testing

- [ ] **JWT Token Operations**
  - [ ] Token creation works
  - [ ] Token validation works
  - [ ] Token refresh works
  - [ ] Token revocation works
  - [ ] Blacklist functionality works

- [ ] **Persistence Verification**
  - [ ] Tokens are stored in Redis
  - [ ] Blacklist entries are persisted
  - [ ] Fallback to memory storage works
  - [ ] Cleanup operations work

- [ ] **API Endpoints**
  - [ ] `/api/auth/jwt/tokens` returns token list
  - [ ] `/api/auth/jwt/tokens/{id}` returns token details
  - [ ] `/api/auth/jwt/tokens/{id}/revoke` revokes tokens
  - [ ] `/api/auth/jwt/cleanup` triggers cleanup
  - [ ] `/api/auth/jwt/blacklist/stats` returns statistics

### Performance Testing

- [ ] **Load Testing**
  - [ ] Token creation under load
  - [ ] Token validation performance
  - [ ] Redis connection pool performance
  - [ ] Memory usage under load

- [ ] **Stress Testing**
  - [ ] High concurrent token operations
  - [ ] Large blacklist performance
  - [ ] Memory cleanup effectiveness
  - [ ] Redis failover behavior

### Security Testing

- [ ] **Authentication Testing**
  - [ ] Invalid token rejection
  - [ ] Expired token handling
  - [ ] Revoked token blocking
  - [ ] Blacklist enforcement

- [ ] **Audit Testing**
  - [ ] Security events logged
  - [ ] Audit log rotation works
  - [ ] Suspicious activity detection
  - [ ] Log integrity maintained

### Monitoring Verification

- [ ] **Metrics Collection**
  - [ ] JWT operation metrics available
  - [ ] Redis metrics collected
  - [ ] Application health metrics
  - [ ] Security audit metrics

- [ ] **Alerting**
  - [ ] JWT persistence alerts configured
  - [ ] Redis connection alerts
  - [ ] Security violation alerts
  - [ ] Performance degradation alerts

## Rollback Procedures

### Emergency Rollback

If JWT persistence causes issues:

```bash
# 1. Disable JWT persistence
export JWT_PERSISTENCE_ENABLED=false
docker-compose restart jairouter

# 2. Or rollback to previous configuration
git checkout HEAD~1 -- src/main/resources/config/security/
docker-compose restart jairouter

# 3. Or stop Redis and use memory-only mode
docker-compose stop redis
# Application will automatically fallback to memory storage
```

### Gradual Rollback

For planned rollback:

```bash
# 1. Stop new token creation (maintenance mode)
# 2. Wait for existing tokens to expire
# 3. Disable persistence in configuration
# 4. Restart application
# 5. Stop Redis service
```

## Troubleshooting

### Common Issues

1. **Redis Connection Failed**
   ```bash
   # Check Redis status
   docker-compose ps redis
   docker-compose logs redis
   
   # Test connectivity
   docker-compose exec redis redis-cli ping
   ```

2. **JWT Persistence Not Working**
   ```bash
   # Check application logs
   docker-compose logs jairouter | grep -i jwt
   
   # Check health endpoint
   curl http://localhost:8080/actuator/health/jwt-persistence
   ```

3. **High Memory Usage**
   ```bash
   # Check memory metrics
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   
   # Trigger manual cleanup
   curl -X POST http://localhost:8080/api/auth/jwt/cleanup
   ```

4. **Security Audit Issues**
   ```bash
   # Check audit logs
   tail -f logs/security-audit.log
   
   # Check audit configuration
   curl http://localhost:8080/actuator/configprops | grep audit
   ```

### Performance Issues

1. **Slow Token Operations**
   - Check Redis performance metrics
   - Verify network connectivity
   - Review connection pool settings
   - Consider Redis cluster setup

2. **Memory Leaks**
   - Monitor JVM heap usage
   - Check cleanup schedule
   - Verify LRU eviction policy
   - Review token retention settings

### Security Issues

1. **Unauthorized Access**
   - Review JWT secret configuration
   - Check token validation logic
   - Verify blacklist functionality
   - Audit security logs

2. **Audit Log Issues**
   - Check log file permissions
   - Verify log rotation settings
   - Review audit configuration
   - Monitor disk space

## Maintenance Procedures

### Regular Maintenance

- [ ] **Weekly**
  - [ ] Review security audit logs
  - [ ] Check system performance metrics
  - [ ] Verify backup procedures
  - [ ] Update security configurations

- [ ] **Monthly**
  - [ ] Rotate JWT secrets (if required)
  - [ ] Review and update alert thresholds
  - [ ] Performance optimization review
  - [ ] Security vulnerability assessment

- [ ] **Quarterly**
  - [ ] Full security audit
  - [ ] Disaster recovery testing
  - [ ] Configuration review and updates
  - [ ] Documentation updates

### Backup Procedures

```bash
# Backup Redis data
docker-compose exec redis redis-cli BGSAVE
docker cp $(docker-compose ps -q redis):/data/dump.rdb ./backup/

# Backup configuration
tar -czf backup/config-$(date +%Y%m%d).tar.gz config/

# Backup audit logs
tar -czf backup/audit-logs-$(date +%Y%m%d).tar.gz logs/security-audit.log*
```

### Recovery Procedures

```bash
# Restore Redis data
docker-compose stop redis
docker cp ./backup/dump.rdb $(docker-compose ps -q redis):/data/
docker-compose start redis

# Restore configuration
tar -xzf backup/config-20250115.tar.gz

# Restart services
docker-compose restart
```

## Contact Information

For issues or questions regarding JWT persistence deployment:

- **Documentation**: [JWT Persistence Configuration Guide](../configuration/jwt-persistence.md)
- **Troubleshooting**: [Common Issues Guide](../troubleshooting/common-issues.md)
- **Security**: [Security Configuration Guide](../security/jwt-authentication.md)
- **Monitoring**: [Monitoring Setup Guide](../monitoring/index.md)

## Appendix

### Environment Variable Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `JWT_SECRET` | Yes | - | JWT signing secret (32+ chars) |
| `REDIS_PASSWORD` | Yes | - | Redis authentication password |
| `JWT_EXPIRATION_MINUTES` | No | 15 | Access token expiration |
| `JWT_REFRESH_EXPIRATION_DAYS` | No | 30 | Refresh token expiration |
| `REDIS_HOST` | No | redis | Redis server hostname |
| `REDIS_PORT` | No | 6379 | Redis server port |

### Port Reference

| Port | Service | Description |
|------|---------|-------------|
| 8080 | JAiRouter | Main application port |
| 6379 | Redis | Redis server port |
| 9090 | Prometheus | Monitoring metrics |
| 3000 | Grafana | Monitoring dashboard |
| 9093 | AlertManager | Alert management |
| 9121 | Redis Exporter | Redis metrics |

### Health Check Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall application health |
| `/actuator/health/jwt-persistence` | JWT persistence health |
| `/actuator/health/redis` | Redis connection health |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus metrics |