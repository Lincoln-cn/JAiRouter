# Troubleshooting

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This section provides solutions and troubleshooting guides for common JAiRouter issues, helping users quickly identify and resolve various problems encountered during system operation.

## Troubleshooting Overview

When JAiRouter encounters issues, it is recommended to follow this systematic approach for troubleshooting:

### Troubleshooting Process
1. **Quick Diagnosis** - Check basic service status and connectivity
2. **Log Analysis** - Review application logs and error messages
3. **Performance Monitoring** - Analyze system resource usage and performance metrics
4. **Configuration Validation** - Verify configuration files and parameter settings
5. **In-depth Debugging** - Use professional tools for detailed analysis

### Diagnostic Tools
- **Health Check Endpoint**: `/actuator/health`
- **Monitoring Metrics Endpoint**: `/actuator/metrics`
- **Configuration Information Endpoint**: `/actuator/configprops`
- **Log Files**: `logs/jairouter-debug.log`

## Issue Classification

### By Severity
- **Critical Issues** - Service completely unavailable, affecting all users
- **Major Issues** - Partial functionality abnormal, affecting some users
- **Minor Issues** - Performance degradation or occasional exceptions
- **Trivial Issues** - Log warnings or configuration suggestions

### By Issue Type
- **Startup Issues** - Application fails to start or exits abnormally
- **Connection Issues** - Backend service connection failures or timeouts
- **Performance Issues** - Slow response, high resource usage, or low throughput
- **Configuration Issues** - Configuration errors, ineffectiveness, or conflicts
- **Functional Issues** - Load balancing, rate limiting, circuit breaker function abnormalities

## Troubleshooting Guides

### [Common Issues](common-issues.md)
Collects the most frequently encountered problems and their solutions during usage, including:
- Startup failures and configuration errors
- Connection timeouts and network issues
- Memory leaks and performance degradation
- Load balancing and rate limiting configuration problems

### [Performance Troubleshooting](performance.md)
Dedicated diagnostic and optimization guide for performance-related issues:
- Analysis and optimization of long response times
- Causes and solutions for insufficient throughput
- Handling of high memory and CPU usage
- JVM tuning and system optimization strategies

### [Debugging Guide](debugging.md)
Provides detailed debugging techniques and tool usage methods:
- Debugging configurations for development and production environments
- Log analysis and network debugging techniques
- JVM memory and thread debugging methods
- Reactive programming debugging strategies

## Quick Diagnosis Checklist

### Basic Checks
- [ ] Service started normally (`curl http://localhost:8080/actuator/health`)
- [ ] Port listening normally (`netstat -tlnp | grep :8080`)
- [ ] Configuration file format is correct
- [ ] Java version meets requirements (Java 17+)

### Connection Checks
- [ ] Backend services are reachable
- [ ] Network firewall is not blocking connections
- [ ] DNS resolution is normal
- [ ] SSL certificates are valid

### Performance Checks
- [ ] CPU usage is normal (< 80%)
- [ ] Memory usage is normal (< 85%)
- [ ] Response time is within expected range
- [ ] Error rate is within acceptable range (< 1%)

### Configuration Checks
- [ ] Service instance configuration is correct
- [ ] Load balancing strategy is appropriate
- [ ] Rate limiting parameters are reasonable
- [ ] Circuit breaker thresholds are appropriate

## Monitoring and Alerting

### Key Monitoring Metrics
```bash
# Service health status
curl http://localhost:8080/actuator/health

# Request statistics
curl http://localhost:8080/actuator/metrics/jairouter.requests.total

# Response time
curl http://localhost:8080/actuator/metrics/jairouter.request.duration

# JVM memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# System CPU usage
curl http://localhost:8080/actuator/metrics/system.cpu.usage
```

### Alert Threshold Recommendations
- **Response Time**: P95 > 5s alert, P95 > 10s critical alert
- **Error Rate**: > 1% alert, > 5% critical alert
- **CPU Usage**: > 80% alert, > 90% critical alert
- **Memory Usage**: > 85% alert, > 95% critical alert

## Incident Handling Process

### 1. Issue Reporting
- Collect detailed error information and environment description
- Record the time and frequency of issue occurrence
- Save relevant logs and configuration files

### 2. Initial Diagnosis
- Perform basic checks using the quick diagnosis checklist
- Review monitoring metrics to identify abnormal patterns
- Analyze log files to locate error causes

### 3. In-depth Analysis
- Select appropriate debugging tools based on issue type
- Conduct detailed performance analysis or network diagnosis
- Enable detailed logging when necessary

### 4. Solution Implementation
- Develop solutions based on analysis results
- Validate fix effectiveness in test environment
- Carefully implement fixes in production environment

### 5. Verification and Summary
- Verify that the issue is completely resolved
- Update monitoring and alerting strategies
- Document issues and solutions for future reference

## Preventive Measures

### Configuration Management
- Use version control to manage configuration files
- Establish configuration change review processes
- Regularly back up important configuration data

### Monitoring System
- Establish a comprehensive monitoring metrics system
- Set reasonable alert thresholds
- Regularly check monitoring system effectiveness

### Capacity Planning
- Regularly assess system capacity requirements
- Conduct performance stress testing
- Develop expansion and optimization plans

### Operations Procedures
- Establish standardized operations procedures
- Regularly conduct failure drills
- Continuously improve issue handling efficiency

## Getting Help

If you still cannot resolve the issue following this guide, you can get help through the following methods:

### Community Support
- Check [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues) for known issues
- Search related discussions and solutions
- Participate in community discussions for advice

### Issue Reporting
- Submit new Issues using the issue report template
- Provide detailed environment information and error logs
- Include reproduction steps and expected behavior description

### Documentation Resources
- View [API Reference Documentation](../api-reference/index.md)
- Read [Configuration Guide](../configuration/index.md)
- Refer to [FAQ](../reference/faq.md)

### Professional Support
- Contact the project maintenance team
- Seek professional technical support services
- Attend related training and workshops

Remember, most issues have solutions - the key is adopting a systematic approach for diagnosis and handling.
