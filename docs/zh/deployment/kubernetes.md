# Kubernetes 部署

<!-- 版本信息 -->
> **文档版本**: 1.0.2  
> **最后更新**: 2026-05-21  
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 支持在 Kubernetes 集群中部署，提供自动故障转移准备、滚动更新等企业级特性。本文档详细介绍如何在 K8s 环境中部署 JAiRouter.

> **⚠️ 多副本不受支持**：当前版本唯一支持 H2 嵌入式单文件数据库，`replicas` **必须为 1**。详见下方「多副本支持现状（已知约束）」。

## 多副本支持现状（已知约束）

**当前版本多副本不受支持，`replicas` 必须为 1；唯一支持 H2 嵌入式文件数据库。** 共享同一数据卷时第 2、3 个 Pod 无法打开 H2 文件库并进入 crash-loop；各自独立卷时每 Pod 一份数据，请求轮询会导致状态随机不一致。已实测两个 JVM 进程并发打开 `jdbc:h2:file:./data/jairouter` 时，第二个进程报 `Database may be already in use`（H2 错误码 90020）。外部共享数据库适配见 issue #160。

### 每 Pod 语义清单

| 能力 | 现状 | 多副本后果 |
|------|------|------------|
| 限流 | 全部状态在 JVM 内，仓库无分布式限流实现 | 放行量约等于配置值乘以副本数；窗口重置各算各的 |
| 配额账本 | 默认 `jairouter.quota.enabled=false`、`jairouter.quota.distributed.enabled=false`、`fail-open=true`，本地计数 | 超支约乘以 N |
| JWT 黑名单 | 默认回落 H2/StoreManager | 撤销只在签发 Pod 生效 |
| API-Key 缓存 | 默认内存实现，无 TTL，只在本进程内修改 | 吊销/新建不跨 Pod |
| 角色权限缓存 | Caffeine `expireAfterWrite=5min`，`invalidateCache()` 只清本进程 | 权限变更最多延迟 5 分钟且不跨 Pod |
| SSE / WebSocket | 事件源是进程内 `Sinks.Many`，全库无 Redis pub/sub | 客户端只看得到所连 Pod 的事件 |
| 调度任务 | 36+ 个 `@Scheduled` 全部无分布式锁 | 同一定时任务在每个 Pod 各跑一遍 |
| 优雅停机 | `server.shutdown`/`graceful`/`timeout-per-shutdown-phase` 全仓零命中 | 滚动升级会截断在途 AI 流式响应 |

### 横向扩展前置条件（按顺序）

1. 换外部共享数据库（issue #160）
2. 开启共享态 Redis 开关并加启动门禁（issue #162）
3. 限流跨实例化，需新写（issue #161）
4. 调度任务加分布式锁（issue #163）
5. 实时事件广播（issue #164）
6. 部署制品：优雅停机、`PodDisruptionBudget`、迁移作业（issue #165）

### 已有基础：Redis 共享态实现

仓库已有 Redis 共享态实现（配额 `RedisCounterBackend` + Lua、JWT 黑名单、令牌持久化、`RedisApiKeyCache`、状态持久化 Tier1），只是**默认全关**。`config/persistence/state-persistence-base.yml` 注释原文已写着「集群部署时开启」。相关配置键：

| 配置键 | 作用 | 默认 |
|--------|------|------|
| `jairouter.persistence.redis.enabled` | 状态持久化 Tier1（熔断/负载均衡状态跨节点共享） | `false` |
| `jairouter.quota.distributed.enabled` | 配额 Redis 原子计数 | `false` |
| `jairouter.security.jwt.persistence.redis.enabled` | JWT 令牌持久化 | `false` |
| `jairouter.security.jwt.blacklist.redis.enabled` | JWT 黑名单跨节点 | `false` |
| `jairouter.security.cache.redis.enabled` | API-Key 分布式缓存 | `false` |

## Kubernetes 部署概述

### 特性

- **高可用性**（规划中）：多实例部署与自动故障转移需先完成共享数据库与共享态接线，当前 `replicas` 必须为 1
- **自动扩缩容**（规划中）：HPA 会把副本数拉高，当前与 H2 单文件库不兼容，勿启用
- **滚动更新**：零停机时间更新（注意：当前无优雅停机配置，在途流式响应可能被截断）
- **服务发现**：内置服务发现和负载均衡
- **配置管理**：使用 ConfigMap 和 Secret 管理配置
- **持久化存储**：支持 PVC 持久化配置、日志和数据

### 架构图

```
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Ingress Layer"
            A[Ingress Controller]
        end
        
        subgraph "Service Layer"
            B[JAiRouter Service]
        end
        
        subgraph "Application Layer"
            C[JAiRouter Pod 1]
            D[JAiRouter Pod 2]
            E[JAiRouter Pod N]
        end
        
        subgraph "Storage Layer"
            F[ConfigMap]
            G[Secret]
            H[PVC - Config]
            I[PVC - Logs]
        end
        
        subgraph "Monitoring Layer"
            J[Prometheus]
            K[Grafana]
            L[ServiceMonitor]
        end
    end
    
    A --> B
    B --> C
    B --> D
    B --> E
    
    C --> F
    C --> G
    C --> H
    D --> F
    D --> G
    D --> I
    
    L --> C
    L --> D
    L --> E
    L --> J
    J --> K
```

## 前置要求

### 1. Kubernetes 集群要求

| 组件 | 最低要求 | 推荐配置 | 说明 |
|------|----------|----------|------|
| **Kubernetes 版本** | 1.20+ | 1.24+ | 支持最新特性 |
| **节点数量** | 3 个节点 | 5+ 个节点 | 包含 master 和 worker |
| **节点配置** | 4C8G | 8C16G | 每个 worker 节点 |
| **存储** | 100GB | 500GB SSD | 持久化存储 |
| **网络** | CNI 插件 | Calico/Flannel | 网络通信 |

### 2. 必需组件

| 组件 | 版本要求 | 安装方式 | 用途 |
|------|----------|----------|------|
| **kubectl** | 1.20+ | 官方安装 | 集群管理工具 |
| **Helm** | 3.0+ | 官方安装 | 包管理器 |
| **Ingress Controller** | Nginx/Traefik | Helm Chart | 外部访问 |
| **Cert-Manager** | 1.0+ | Helm Chart | SSL 证书管理 |
| **Prometheus Operator** | 0.50+ | Helm Chart | 监控系统 |
| **Grafana** | 8.0+ | Helm Chart | 监控可视化 |

### 3. 存储类

```
# 检查可用的存储类
kubectl get storageclass

# 如果没有默认存储类，创建一个
kubectl patch storageclass <storage-class-name> -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
```

## 基础部署

### 1. 创建命名空间

```
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: jairouter
  labels:
    name: jairouter
```

```bash
kubectl apply -f namespace.yaml
```

### 2. 创建 ConfigMap

```
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-config
  namespace: jairouter
data:
  application.yml: |
    server:
      port: 8080
    
    model:
      load-balance:
        type: round-robin
      rate-limit:
        enabled: true
        algorithm: token-bucket
        capacity: 1000
        rate: 100
      services:
        chat:
          instances:
            - name: "llama3.2:3b"
              base-url: "http://ollama-service:11434"
              path: "/v1/chat/completions"
              weight: 1
    
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      metrics:
        export:
          prometheus:
            enabled: true
    
    logging:
      level:
        org.unreal.modelrouter: INFO
      file:
        name: /app/logs/jairouter.log
```

```bash
kubectl apply -f configmap.yaml
```

### 3. 创建 Secret

```
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-secret
  namespace: jairouter
type: Opaque
data:
  # Base64 编码的密钥
  api-key: eW91ci1hcGkta2V5LWhlcmU=  # your-api-key-here
  jwt-secret: eW91ci1qd3Qtc2VjcmV0LWtleQ==  # your-jwt-secret-key
  database-password: cGFzc3dvcmQ=     # password

# 生产环境推荐使用环境变量配置
---
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-prod-secret
  namespace: jairouter
type: Opaque
stringData:
  PROD_ADMIN_API_KEY: "your-production-admin-api-key-here"
  PROD_SERVICE_API_KEY: "your-production-service-api-key-here"
  PROD_READONLY_API_KEY: "your-production-readonly-api-key-here"
  PROD_JWT_SECRET: "your-production-jwt-secret-here"
  REDIS_PASSWORD: "your-redis-password"
  SECURITY_ALERT_EMAIL: "security-alerts@your-company.com"

---
# TLS Secret 配置
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-tls
  namespace: jairouter
type: kubernetes.io/tls
data:
  tls.crt: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCiMKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
  tls.key: LS0tLS1CRUdJTiBSU0EgUFJJV
```

```bash
kubectl apply -f secret.yaml
```

### 4. 创建 PVC

```
# pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-config-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 1Gi
  storageClassName: nfs-client  # 根据实际情况调整

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-logs-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
  storageClassName: nfs-client  # 根据实际情况调整

---
# 主 H2 数据库卷（必须挂载到 /app/data）
# ReadWriteOnce 本身就是 replicas: 1 的硬约束：
# H2 嵌入式单文件库不支持多进程并发打开，多 Pod 共享会 crash-loop。
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-data-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard  # 根据实际情况调整
```

```bash
kubectl apply -f pvc.yaml
```

### 5. 创建 Deployment

> **⚠️ 警告：`replicas` 必须为 1**
>
> 当前唯一支持 H2 嵌入式单文件数据库。已实测两个 JVM 进程并发打开同一文件库时，第二个进程直接失败（`Database may be already in use`，错误码 90020）。共享同一 PVC 时第 2、3 个 Pod 会 crash-loop；各自独立卷时每 Pod 一份数据，请求轮询随机 401/状态错乱。`replicas > 1` 需先完成外部共享数据库适配（issue #160）。
>
> `/app/data` 必须挂载持久卷（`ReadWriteOnce`）。若 `readOnlyRootFilesystem: true` 而未挂载 `/app/data`，主 H2 库无法创建，**即使 `replicas: 1` 也起不来**。

```
# Deployment 安全配置
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jairouter
  namespace: jairouter
  labels:
    app: jairouter
spec:
  # 必须为 1：H2 嵌入式单文件库不支持多副本（见上方警告与 issue #160）
  replicas: 1
  selector:
    matchLabels:
      app: jairouter
  template:
    metadata:
      labels:
        app: jairouter
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      # 安全上下文配置
      securityContext:
        runAsNonRoot: true
        runAsUser: 10010
        runAsGroup: 10010
        fsGroup: 10010
      containers:
      - name: jairouter
        image: sodlinken/jairouter:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
        # 密钥引用必须与上方创建的 Secret 名/键一致（jairouter-prod-secret）
        - name: PROD_ADMIN_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_ADMIN_API_KEY
        - name: PROD_SERVICE_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_SERVICE_API_KEY
        - name: PROD_READONLY_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_READONLY_API_KEY
        - name: PROD_JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_JWT_SECRET
        # Redis 环境变量：单副本下非必需，多副本下必需（共享态开关见上文）
        - name: REDIS_HOST
          value: "your-redis-host"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: REDIS_PASSWORD
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
        - name: config-store-volume
          mountPath: /app/config-store
        # 主 H2 数据库目录，必须挂载（否则 readOnlyRootFilesystem 下无法创建库）
        - name: data-volume
          mountPath: /app/data
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        # 容器安全上下文
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
      volumes:
      - name: config-volume
        configMap:
          name: jairouter-config
      - name: logs-volume
        persistentVolumeClaim:
          claimName: jairouter-logs-pvc
      - name: config-store-volume
        persistentVolumeClaim:
          claimName: jairouter-config-pvc
      - name: data-volume
        persistentVolumeClaim:
          claimName: jairouter-data-pvc
      restartPolicy: Always
```

```bash
kubectl apply -f deployment.yaml
```

### 6. 创建 Service

```
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: jairouter-service
  namespace: jairouter
  labels:
    app: jairouter
spec:
  selector:
    app: jairouter
  ports:
  - name: http
    port: 80
    targetPort: 8080
    protocol: TCP
  type: ClusterIP

---
# 如果需要 NodePort 访问
apiVersion: v1
kind: Service
metadata:
  name: jairouter-nodeport
  namespace: jairouter
  labels:
    app: jairouter
spec:
  selector:
    app: jairouter
  ports:
  - name: http
    port: 80
    targetPort: 8080
    nodePort: 30080
    protocol: TCP
  type: NodePort
```

```bash
kubectl apply -f service.yaml
```

### 7. 创建 Ingress

```
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: jairouter-ingress
  namespace: jairouter
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "false"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
spec:
  rules:
  - host: jairouter.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: jairouter-service
            port:
              number: 80
  # 如果有 TLS 证书
  # tls:
  # - hosts:
  #   - jairouter.example.com
  #   secretName: jairouter-tls
```

```bash
kubectl apply -f ingress.yaml
```

## 自动扩缩容

### 1. 水平 Pod 自动扩缩容 (HPA)

```
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: jairouter-hpa
  namespace: jairouter
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: jairouter
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  # 自定义指标（需要 Prometheus Adapter）
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
```

```bash
kubectl apply -f hpa.yaml
```

### 2. 垂直 Pod 自动扩缩容 (VPA)

```
# vpa.yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: jairouter-vpa
  namespace: jairouter
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: jairouter
  updatePolicy:
    updateMode: "Auto"  # 或 "Off", "Initial", "Recreation"
  resourcePolicy:
    containerPolicies:
    - containerName: jairouter
      minAllowed:
        cpu: 100m
        memory: 128Mi
      maxAllowed:
        cpu: 2
        memory: 2Gi
      controlledResources: ["cpu", "memory"]
```

```bash
kubectl apply -f vpa.yaml
```

## 监控集成

### 1. ServiceMonitor

```
# servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: jairouter-monitor
  namespace: jairouter
  labels:
    app: jairouter
spec:
  selector:
    matchLabels:
      app: jairouter
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
    scrapeTimeout: 10s
```

```bash
kubectl apply -f servicemonitor.yaml
```

### 2. PrometheusRule

```
# prometheusrule.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: jairouter-rules
  namespace: jairouter
  labels:
    app: jairouter
spec:
  groups:
  - name: jairouter.rules
    rules:
    - alert: JAiRouterDown
      expr: up{job="jairouter-service"} == 0
      for: 1m
      labels:
        severity: critical
      annotations:
        summary: "JAiRouter instance is down"
        description: "JAiRouter instance {{ $labels.instance }} has been down for more than 1 minute."
    
    - alert: JAiRouterHighErrorRate
      expr: rate(http_server_requests_total{status=~"5.."}[5m]) / rate(http_server_requests_total[5m]) > 0.1
      for: 2m
      labels:
        severity: warning
      annotations:
        summary: "JAiRouter high error rate"
        description: "JAiRouter error rate is above 10% for more than 2 minutes."
    
    - alert: JAiRouterHighMemoryUsage
      expr: container_memory_usage_bytes{pod=~"jairouter-.*"} / container_spec_memory_limit_bytes > 0.9
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "JAiRouter high memory usage"
        description: "JAiRouter memory usage is above 90% for more than 5 minutes."
```

```bash
kubectl apply -f prometheusrule.yaml
```

## Helm Chart 部署

### 1. 创建 Helm Chart

```
# 创建 Chart 目录结构
mkdir -p jairouter-chart/{templates,charts}
cd jairouter-chart
```

### 2. Chart.yaml

```
# Chart.yaml
apiVersion: v2
name: jairouter
description: A Helm chart for JAiRouter AI model routing gateway
type: application
version: 3.1.1
appVersion: "3.1.1"
keywords:
  - ai
  - router
  - gateway
  - load-balancer
home: https://github.com/Lincoln-cn/JAiRouter
sources:
  - https://github.com/Lincoln-cn/JAiRouter
maintainers:
  - name: JAiRouter Team
    email: team@jairouter.com
```

### 3. values.yaml

```
# values.yaml
# 必须为 1：H2 嵌入式单文件库不支持多副本（issue #160）
replicaCount: 1

image:
  repository: sodlinken/jairouter
  pullPolicy: IfNotPresent
  tag: "latest"

nameOverride: ""
fullnameOverride: ""

serviceAccount:
  create: true
  annotations: {}
  name: ""

podAnnotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/actuator/prometheus"

podSecurityContext:
  fsGroup: 10010

securityContext:
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: false
  runAsNonRoot: true
  runAsUser: 10010

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: true
  className: "nginx"
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "false"
  hosts:
    - host: jairouter.local
      paths:
        - path: /
          pathType: Prefix
  tls: []

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

autoscaling:
  # 当前不可用：HPA 会拉高副本数，与 H2 单文件库不兼容（issue #160）
  enabled: false
  minReplicas: 1
  maxReplicas: 1
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

nodeSelector: {}

tolerations: []

affinity: {}

persistence:
  enabled: true
  storageClass: ""
  # 数据卷必须 ReadWriteOnce：H2 单文件库 + replicas=1（issue #160）
  accessMode: ReadWriteOnce
  size: 10Gi

# 环境变量配置
env:
  # Spring 配置
  SPRING_PROFILES_ACTIVE: "prod"
  
  # 生产环境 API Key 配置
  PROD_ADMIN_API_KEY: "your-production-admin-api-key-here"
  PROD_SERVICE_API_KEY: "your-production-service-api-key-here"
  PROD_READONLY_API_KEY: "your-production-readonly-api-key-here"
  
  # 生产环境 JWT 配置
  PROD_JWT_SECRET: "your-production-jwt-secret-here"
  
  # Redis 配置
  REDIS_HOST: "your-redis-host"
  REDIS_PORT: "6379"
  REDIS_PASSWORD: "your-redis-password"
  
  # 安全告警配置
  SECURITY_ALERT_EMAIL: "security-alerts@your-company.com"
  SECURITY_ALERT_WEBHOOK: "https://your-webhook-url.com/security-alerts"

config:
  application.yml: |
    server:
      port: 8080
    model:
      load-balance:
        type: round-robin
      rate-limit:
        enabled: true
        algorithm: token-bucket
        capacity: 1000
        rate: 100

monitoring:
  serviceMonitor:
    enabled: true
    interval: 30s
  prometheusRule:
    enabled: true
```

### 4. 模板文件

创建 `templates/deployment.yaml`：

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "jairouter.fullname" . }}
  labels:
    {{- include "jairouter.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "jairouter.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      labels:
        {{- include "jairouter.selectorLabels" . | nindent 8 }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      serviceAccountName: {{ include "jairouter.serviceAccountName" . }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      containers:
        - name: {{ .Chart.Name }}
          securityContext:
            {{- toYaml .Values.securityContext | nindent 12 }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
            - name: data
              mountPath: /app/data
            {{- if .Values.persistence.enabled }}
            - name: logs
              mountPath: /app/logs
            {{- end }}
      volumes:
        - name: config
          configMap:
            name: {{ include "jairouter.fullname" . }}-config
        - name: data
          persistentVolumeClaim:
            claimName: {{ include "jairouter.fullname" . }}-data
        {{- if .Values.persistence.enabled }}
        - name: logs
          persistentVolumeClaim:
            claimName: {{ include "jairouter.fullname" . }}-logs
        {{- end }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

### 5. 部署 Helm Chart

```
# 安装 Chart
helm install jairouter ./jairouter-chart -n jairouter --create-namespace

# 升级 Chart
helm upgrade jairouter ./jairouter-chart -n jairouter

# 查看状态
helm status jairouter -n jairouter

# 卸载 Chart
helm uninstall jairouter -n jairouter
```

## 高级配置

### 1. Pod 反亲和性

```
# 在 deployment.yaml 中添加
spec:
  template:
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - jairouter
              topologyKey: kubernetes.io/hostname
```

### 2. Pod 中断预算

```
# pdb.yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: jairouter-pdb
  namespace: jairouter
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: jairouter
```

### 3. 网络策略

```
# 网络策略配置
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: jairouter-netpol
  namespace: jairouter
spec:
  podSelector:
    matchLabels:
      app: jairouter
  policyTypes:
  - Ingress
  - Egress
  ingress:
  # 允许来自 Ingress Controller 的流量
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  # 允许来自监控组件的流量
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
    ports:
    - protocol: TCP
      port: 8080
  egress:
  # 允许 DNS 查询
  - to:
    - namespaceSelector:
        matchLabels:
          name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
  # 允许访问外部 AI 服务
  - to: []
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 80
```

## 安全配置

### 1. 网络安全策略

创建 `networkpolicy.yaml`：

```
# 网络策略配置
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: jairouter-netpol
  namespace: jairouter
spec:
  podSelector:
    matchLabels:
      app: jairouter
  policyTypes:
  - Ingress
  - Egress
  ingress:
  # 允许来自 Ingress Controller 的流量
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  # 允许来自监控组件的流量
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
    ports:
    - protocol: TCP
      port: 8080
  egress:
  # 允许 DNS 查询
  - to:
    - namespaceSelector:
        matchLabels:
          name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
  # 允许访问外部 AI 服务
  - to: []
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 80
```

### 2. Pod 安全策略

更新 `deployment.yaml`：

```
# Deployment 安全配置
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jairouter
  namespace: jairouter
  labels:
    app: jairouter
spec:
  # 必须为 1：H2 嵌入式单文件库不支持多副本（issue #160）
  replicas: 1
  selector:
    matchLabels:
      app: jairouter
  template:
    metadata:
      labels:
        app: jairouter
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      # 安全上下文配置
      securityContext:
        runAsNonRoot: true
        runAsUser: 10010
        runAsGroup: 10010
        fsGroup: 10010
      containers:
      - name: jairouter
        image: sodlinken/jairouter:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
        - name: PROD_ADMIN_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_ADMIN_API_KEY
        - name: PROD_SERVICE_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_SERVICE_API_KEY
        - name: PROD_READONLY_API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_READONLY_API_KEY
        - name: PROD_JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: PROD_JWT_SECRET
        - name: REDIS_HOST
          value: "your-redis-host"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: jairouter-prod-secret
              key: REDIS_PASSWORD
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
        - name: config-store-volume
          mountPath: /app/config-store
        - name: data-volume
          mountPath: /app/data
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        # 容器安全上下文
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
      volumes:
      - name: config-volume
        configMap:
          name: jairouter-config
      - name: logs-volume
        persistentVolumeClaim:
          claimName: jairouter-logs-pvc
      - name: config-store-volume
        persistentVolumeClaim:
          claimName: jairouter-config-pvc
      - name: data-volume
        persistentVolumeClaim:
          claimName: jairouter-data-pvc
      restartPolicy: Always
```

### 3. Secret 管理

创建 `secret.yaml`：

```
# Secret 配置
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-secret
  namespace: jairouter
type: Opaque
data:
  # Base64 编码的密钥
  api-key: eW91ci1hcGkta2V5LWhlcmU=  # your-api-key-here
  jwt-secret: eW91ci1qd3Qtc2VjcmV0LWtleQ==  # your-jwt-secret-key
  database-password: cGFzc3dvcmQ=     # password

---
# TLS Secret 配置
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-tls
  namespace: jairouter
type: kubernetes.io/tls
data:
  tls.crt: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCiMKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
  tls.key: LS0tLS1CRUdJTiBSU0EgUFJJV
```

```
kubectl apply -f secret.yaml
```

### 4. 应用安全配置

创建 `configmap-security.yaml`：

```
# 安全配置 ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-security-config
  namespace: jairouter
data:
  application-security.yml: |
    # 安全配置
    security:
      # API Key 配置
      api-key:
        enabled: true
        header: X-API-Key
        # 生产环境使用环境变量配置
        keys:
          - key-id: "prod-admin"
            key-value: "${PROD_ADMIN_API_KEY:}"
            permissions: ["admin", "read", "write", "delete"]
            enabled: true
          - key-id: "prod-service"
            key-value: "${PROD_SERVICE_API_KEY:}"
            permissions: ["read", "write"]
            enabled: true
          - key-id: "prod-readonly"
            key-value: "${PROD_READONLY_API_KEY:}"
            permissions: ["read"]
            enabled: true
      
      # JWT 配置
      jwt:
        enabled: true
        secret: "${PROD_JWT_SECRET:}"
        algorithm: HS256
        expiration-minutes: 15
        issuer: jairouter-prod
        accounts:
          - username: admin
            password: "{bcrypt}your-bcrypt-hashed-password"
            roles: [ADMIN, USER]
            enabled: true
          - username: user
            password: "{bcrypt}your-bcrypt-hashed-password"
            roles: [USER]
            enabled: true

      # CORS 配置
      cors:
        allowed-origins: "*"
        allowed-methods: "*"
        allowed-headers: "*"
        allow-credentials: false

    # HTTPS 配置
    server:
      port: 8443
      ssl:
        enabled: true
        key-store: /app/config/tls/keystore.p12
        key-store-password: ${SSL_KEYSTORE_PASSWORD}
        key-store-type: PKCS12
        key-alias: jairouter
```


```

```

## 日志配置

### 1. 日志收集配置

创建 `fluentd-config.yaml`：

```yaml
# Fluentd 配置
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-fluentd-config
  namespace: jairouter
data:
  fluent.conf: |
    <source>
      @type tail
      path /var/log/containers/jairouter-*_jairouter_*.log
      pos_file /var/log/jairouter.log.pos
      tag jairouter.*
      read_from_head true
      <parse>
        @type json
        time_format %Y-%m-%dT%H:%M:%S.%NZ
      </parse>
    </source>
    
    <filter jairouter.**>
      @type kubernetes_metadata
    </filter>
    
    <match jairouter.**>
      @type elasticsearch
      host elasticsearch
      port 9200
      logstash_format true
      logstash_prefix jairouter
      flush_interval 10s
    </match>
```

### 2. 结构化日志配置

更新 `configmap.yaml`：

```
# 更新后的 ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-config
  namespace: jairouter
data:
  application.yml: |
    server:
      port: 8080
    
    model:
      load-balance:
        type: round-robin
      rate-limit:
        enabled: true
        algorithm: token-bucket
        capacity: 1000
        rate: 100
      services:
        chat:
          instances:
            - name: "llama3.2:3b"
              base-url: "http://ollama-service:11434"
              path: "/v1/chat/completions"
              weight: 1
    
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      metrics:
        export:
          prometheus:
            enabled: true
    
    logging:
      level:
        org.unreal.modelrouter: INFO
        org.unreal.modelrouter.auth: DEBUG
      file:
        name: /app/logs/jairouter.log
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
        file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
      
      structured:
        enabled: true
        format: json
        fields:
          timestamp: "@timestamp"
          level: "level"
          logger: "logger"
          message: "message"
          thread: "thread"
          traceId: "traceId"
          spanId: "spanId"
          pod: "pod"
          namespace: "namespace"
```

### 3. 日志持久化存储

更新 `pvc.yaml`：

```
# 更新后的 PVC 配置
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-config-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 1Gi
  storageClassName: nfs-client

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-logs-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
  storageClassName: nfs-client

---
# 审计日志 PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jairouter-audit-logs-pvc
  namespace: jairouter
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 5Gi
  storageClassName: nfs-client
```

### 4. 日志轮转和清理

创建 `log-cleanup-cronjob.yaml`：

```
# 日志清理 CronJob
apiVersion: batch/v1
kind: CronJob
metadata:
  name: jairouter-log-cleanup
  namespace: jairouter
spec:
  schedule: "0 2 * * *"  # 每天凌晨 2 点执行
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: log-cleanup
            image: busybox
            args:
            - /bin/sh
            - -c
            - |
              find /logs -name "*.log" -mtime +30 -delete
              find /logs -name "*.log.*" -mtime +30 -delete
            volumeMounts:
            - name: logs-volume
              mountPath: /logs
          restartPolicy: OnFailure
          volumes:
          - name: logs-volume
            persistentVolumeClaim:
              claimName: jairouter-logs-pvc
```

## 运维管理

### 1. 查看部署状态

```
# 查看 Pod 状态
kubectl get pods -n jairouter

# 查看 Deployment 状态
kubectl get deployment -n jairouter

# 查看 Service 状态
kubectl get service -n jairouter

# 查看 Ingress 状态
kubectl get ingress -n jairouter

# 查看 HPA 状态
kubectl get hpa -n jairouter
```

### 2. 查看日志

```
# 查看 Pod 日志
kubectl logs -f deployment/jairouter -n jairouter

# 查看特定 Pod 日志
kubectl logs -f jairouter-xxx-yyy -n jairouter

# 查看所有 Pod 日志
kubectl logs -f -l app=jairouter -n jairouter --max-log-requests=10
```

### 3. 滚动更新

```
# 更新镜像
kubectl set image deployment/jairouter jairouter=sodlinken/jairouter:v3.1.1 -n jairouter

# 查看更新状态
kubectl rollout status deployment/jairouter -n jairouter

# 查看更新历史
kubectl rollout history deployment/jairouter -n jairouter

# 回滚到上一个版本
kubectl rollout undo deployment/jairouter -n jairouter

# 回滚到指定版本
kubectl rollout undo deployment/jairouter --to-revision=2 -n jairouter
```

### 4. 扩缩容

> **⚠️ 当前不可用**：`kubectl scale --replicas > 1` 与 H2 嵌入式单文件库不兼容，需先完成共享数据库适配（issue #160）。多副本下限流/配额/JWT 黑名单/API-Key 缓存等均为每 Pod 语义，详见「多副本支持现状（已知约束）」。

```
# 手动扩容（当前不可用，见上方警告）
# kubectl scale deployment jairouter --replicas=5 -n jairouter

# 查看 HPA 状态
kubectl describe hpa jairouter-hpa -n jairouter

# 临时禁用 HPA
kubectl patch hpa jairouter-hpa -n jairouter -p '{"spec":{"minReplicas":0,"maxReplicas":0}}'
```

## 故障排查

### 1. Pod 故障排查

```
# 查看 Pod 详细信息
kubectl describe pod jairouter-xxx-yyy -n jairouter

# 查看 Pod 事件
kubectl get events --sort-by=.metadata.creationTimestamp -n jairouter

# 进入 Pod 调试
kubectl exec -it jairouter-xxx-yyy -n jairouter -- sh

# 查看 Pod 资源使用
kubectl top pod -n jairouter
```

### 2. 网络故障排查

```
# 测试 Service 连通性
kubectl run test-pod --image=busybox -it --rm -- sh
# 在 Pod 内执行
nslookup jairouter-service.jairouter.svc.cluster.local
wget -qO- http://jairouter-service.jairouter.svc.cluster.local/actuator/health

# 查看 Endpoint
kubectl get endpoints -n jairouter

# 查看 Ingress 状态
kubectl describe ingress jairouter-ingress -n jairouter
```

### 3. 存储故障排查

```
# 查看 PVC 状态
kubectl get pvc -n jairouter

# 查看 PV 状态
kubectl get pv

# 查看存储类
kubectl get storageclass

# 查看 Pod 挂载情况
kubectl describe pod jairouter-xxx-yyy -n jairouter | grep -A 10 Volumes
```

## 最佳实践

### 1. 资源管理

- 设置合适的资源请求和限制
- 使用 HPA 和 VPA 自动扩缩容
- 配置 Pod 中断预算
- 使用反亲和性分散 Pod

### 2. 安全配置

- 使用非 root 用户运行容器
- 配置网络策略限制流量
- 使用 Secret 管理敏感信息
- 定期更新镜像和依赖

### 3. 监控告警

- 配置 ServiceMonitor 收集指标
- 设置 PrometheusRule 告警规则
- 使用 Grafana 可视化监控
- 配置告警通知

### 4. 高可用性

- 多副本部署
- 跨可用区分布
- 配置健康检查
- 实施滚动更新策略

## 下一步

完成 Kubernetes 部署后，您可以：

- **[生产环境部署](production.md)** - 配置生产级高可用环境
- **[监控指南](../monitoring/index.md)** - 设置完整的监控体系
- **[故障排查](../troubleshooting/index.md)** - 学习 K8s 环境故障诊断
- **[性能调优](../troubleshooting/performance.md)** - 优化 K8s 部署性能