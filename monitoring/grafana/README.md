# JAiRouter Grafana 仪表板配置

本目录包含了JAiRouter项目的Grafana仪表板配置文件，提供全面的监控可视化界面。

## 仪表板概览

### 1. 系统概览 (system-overview.json)
- **用途**: 提供JAiRouter系统的整体运行状态概览
- **主要指标**:
  - 请求总览（总请求率、成功请求率、错误请求率）
  - 错误率统计
  - 响应时间分布（P50、P95、P99）
  - 后端服务健康状态
  - 活跃连接数

### 2. 业务指标 (business-metrics.json)
- **用途**: 监控AI模型服务的业务相关指标
- **主要指标**:
  - 各服务请求量（chat、embedding、rerank等）
  - 模型调用成功率
  - 负载均衡策略分布
  - 后端调用响应时间
  - 请求/响应大小分布
  - 各适配器调用量

### 3. 基础设施监控 (infrastructure.json)
- **用途**: 监控系统基础设施组件的运行状态
- **主要指标**:
  - 限流器状态（可用令牌数）
  - 熔断器状态（关闭/半开/开启）
  - 后端适配器响应时间
  - 限流事件统计
  - 熔断器事件统计
  - 负载均衡选择统计

### 4. 性能分析 (performance-analysis.json)
- **用途**: 深入分析系统性能和资源使用情况
- **主要指标**:
  - CPU使用率
  - JVM内存使用情况（堆内存、非堆内存）
  - GC统计
  - 线程统计（活跃线程、守护线程）
  - 请求响应时间热力图
  - 监控系统性能（指标收集速率、错误率）
  - 监控系统资源使用（缓冲区大小、内存使用）

### 5. 告警概览 (alerts-overview.json)
- **用途**: 集中展示系统告警状态和历史
- **主要指标**:
  - 各类告警状态（高错误率、后端服务下线、熔断器开启、高响应时间）
  - 活跃告警时间线
  - 告警类型分布
  - 告警状态统计
  - 告警详情表

## 配置文件结构

```
monitoring/grafana/
├── dashboards/                    # 仪表板JSON配置文件
│   ├── system-overview.json       # 系统概览仪表板
│   ├── business-metrics.json      # 业务指标仪表板
│   ├── infrastructure.json        # 基础设施监控仪表板
│   ├── performance-analysis.json  # 性能分析仪表板
│   └── alerts-overview.json       # 告警概览仪表板
├── provisioning/                  # Grafana自动配置
│   ├── dashboards.yml            # 仪表板自动加载配置
│   └── datasources.yml           # 数据源配置
└── README.md                      # 本文档
```

## 使用方法

### 1. 自动部署（推荐）
使用Docker Compose自动部署整个监控栈：

```bash
# 启动监控栈
docker-compose -f docker-compose-monitoring.yml up -d

# 访问Grafana
# URL: http://localhost:3000
# 默认用户名/密码: admin/admin
```

### 2. 手动导入
如果需要手动导入仪表板：

1. 登录Grafana管理界面
2. 点击左侧菜单的"+"号，选择"Import"
3. 上传对应的JSON文件或复制JSON内容
4. 配置数据源为Prometheus
5. 保存仪表板

### 3. 配置数据源
确保Prometheus数据源配置正确：
- **Name**: Prometheus
- **Type**: Prometheus
- **URL**: http://prometheus:9090 (Docker环境) 或 http://localhost:9090 (本地环境)
- **Access**: Server (Default)

## 指标说明

### 核心指标前缀
所有JAiRouter相关指标都以`jairouter_`为前缀：

- `jairouter_requests_total`: 请求总数计数器
- `jairouter_request_duration_seconds`: 请求响应时间直方图
- `jairouter_backend_calls_total`: 后端调用总数计数器
- `jairouter_backend_call_duration_seconds`: 后端调用时间直方图
- `jairouter_backend_health`: 后端服务健康状态
- `jairouter_rate_limit_tokens`: 限流器可用令牌数
- `jairouter_circuit_breaker_state`: 熔断器状态
- `jairouter_loadbalancer_selections_total`: 负载均衡选择计数

### 标签说明
- `service`: 服务类型（chat、embedding、rerank等）
- `adapter`: 适配器类型（gpustack、ollama、vllm等）
- `instance`: 服务实例标识
- `status`: 状态码或结果状态
- `strategy`: 负载均衡策略
- `algorithm`: 限流算法类型

## 自定义配置

### 修改刷新间隔
在仪表板设置中可以调整数据刷新间隔：
- 默认: 5秒
- 推荐生产环境: 30秒-1分钟

### 添加告警
可以在每个面板上设置告警规则：
1. 编辑面板
2. 切换到"Alert"标签
3. 配置告警条件和通知渠道

### 时间范围
默认显示最近1小时的数据，可以根据需要调整：
- 实时监控: 最近15分钟
- 趋势分析: 最近24小时或7天

## 故障排查

### 常见问题

1. **仪表板显示"No data"**
   - 检查Prometheus数据源配置
   - 确认JAiRouter应用正在运行并暴露指标
   - 验证Prometheus能够抓取到JAiRouter的指标

2. **指标数据不准确**
   - 检查时间同步
   - 确认指标标签配置正确
   - 验证PromQL查询语句

3. **仪表板加载缓慢**
   - 调整查询时间范围
   - 优化PromQL查询
   - 增加Prometheus查询超时时间

### 日志检查
```bash
# 检查Grafana日志
docker logs grafana

# 检查Prometheus日志
docker logs prometheus

# 检查JAiRouter指标端点
curl http://localhost:8080/actuator/prometheus
```

## 扩展和定制

### 添加新的仪表板
1. 创建新的JSON配置文件
2. 放置在`dashboards/`目录下
3. 重启Grafana或等待自动重载

### 修改现有仪表板
1. 在Grafana界面中编辑仪表板
2. 导出JSON配置
3. 更新对应的配置文件

### 集成告警通知
配置AlertManager或Grafana告警通知：
- 邮件通知
- Slack集成
- 钉钉/企业微信通知
- PagerDuty集成

## 最佳实践

1. **定期备份仪表板配置**
2. **使用版本控制管理配置文件**
3. **为不同环境创建不同的仪表板**
4. **设置合理的数据保留策略**
5. **定期检查和优化查询性能**
6. **建立告警响应流程**

## 支持和维护

如需帮助或报告问题，请：
1. 检查本文档的故障排查部分
2. 查看Grafana和Prometheus官方文档
3. 联系系统管理员或开发团队