﻿# Kubernetes Deployment

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter supports deployment in Kubernetes clusters, providing enterprise-grade features such as high availability, auto-scaling, and rolling updates. This document details how to deploy JAiRouter in a K8s environment.

## Kubernetes Deployment Overview

### Features

- **High Availability**: Multi-instance deployment with automatic failover
- **Auto-scaling**: Automatic scaling based on CPU/memory/custom metrics
- **Rolling Updates**: Zero-downtime updates
- **Service Discovery**: Built-in service discovery and load balancing
- **Configuration Management**: Configuration management using ConfigMap and Secret
- **Persistent Storage**: Support for PVC to persist configurations and logs

### Architecture Diagram

``mermaid
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

## Prerequisites

### 1. Kubernetes Cluster Requirements

| Component | Minimum Requirement | Recommended Configuration | Description |
|-----------|---------------------|----------------------------|-------------|
| **Kubernetes Version** | 1.20+ | 1.24+ | Support for latest features |
| **Node Count** | 3 nodes | 5+ nodes | Including master and worker |
| **Node Configuration** | 4C8G | 8C16G | Per worker node |
| **Storage** | 100GB | 500GB SSD | Persistent storage |
| **Network** | CNI Plugin | Calico/Flannel | Network communication |

### 2. Required Components

| Component | Version Requirement | Installation Method | Purpose |
|-----------|---------------------|---------------------|---------|
| **kubectl** | 1.20+ | Official Installation | Cluster management tool |
| **Helm** | 3.0+ | Official Installation | Package manager |
| **Ingress Controller** | Nginx/Traefik | Helm Chart | External access |
| **Cert-Manager** | 1.0+ | Helm Chart | SSL certificate management |
| **Prometheus Operator** | 0.50+ | Helm Chart | Monitoring system |
| **Grafana** | 8.0+ | Helm Chart | Monitoring visualization |

### 3. Storage Class

```bash
# Check available storage classes
kubectl get storageclass

# If there's no default storage class, create one
kubectl patch storageclass <storage-class-name> -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
```

## Basic Deployment

### 1. Create Namespace

```yaml
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

### 2. Create ConfigMap

```yaml
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

### 3. Create Secret

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-secret
  namespace: jairouter
type: Opaque
data:
  # Base64 encoded keys
  api-key: eW91ci1hcGkta2V5LWhlcmU=  # your-api-key-here
  database-password: cGFzc3dvcmQ=     # password
```

```bash
kubectl apply -f secret.yaml
```

### 4. Create PVC

```yaml
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
  storageClassName: nfs-client  # Adjust according to actual situation

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
  storageClassName: nfs-client  # Adjust according to actual situation
```

```bash
kubectl apply -f pvc.yaml
```

### 5. Create Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jairouter
  namespace: jairouter
  labels:
    app: jairouter
spec:
  replicas: 3
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
        - name: API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-secret
              key: api-key
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
        - name: config-store-volume
          mountPath: /app/config-store
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
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
      restartPolicy: Always
```

```bash
kubectl apply -f deployment.yaml
```

### 6. Create Service

```yaml
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
# If NodePort access is needed
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

### 7. Create Ingress

```yaml
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
  # If TLS certificate is available
  # tls:
  # - hosts:
  #   - jairouter.example.com
  #   secretName: jairouter-tls
```

```bash
kubectl apply -f ingress.yaml
```

## Auto-scaling

### 1. Horizontal Pod Autoscaler (HPA)

```yaml
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
  # Custom metrics (requires Prometheus Adapter)
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

### 2. Vertical Pod Autoscaler (VPA)

```yaml
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
    updateMode: "Auto"  # Or "Off", "Initial", "Recreation"
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

## Monitoring Integration

### 1. ServiceMonitor

```yaml
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

```yaml
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

## Helm Chart Deployment

### 1. Create Helm Chart

```bash
# Create Chart directory structure
mkdir -p jairouter-chart/{templates,charts}
cd jairouter-chart
```

### 2. Chart.yaml

```yaml
# Chart.yaml
apiVersion: v2
name: jairouter
description: A Helm chart for JAiRouter AI model routing gateway
type: application
version: 1.0.0
appVersion: "1.0.0"
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

```yaml
# values.yaml
replicaCount: 3

image:
  repository: jairouter/model-router
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
  fsGroup: 1001

securityContext:
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: false
  runAsNonRoot: true
  runAsUser: 1001

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
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

nodeSelector: {}

tolerations: []

affinity: {}

persistence:
  enabled: true
  storageClass: ""
  accessMode: ReadWriteMany
  size: 10Gi

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

### 4. Template Files

Create `templates/deployment.yaml`:

```yaml
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
            {{- if .Values.persistence.enabled }}
            - name: logs
              mountPath: /app/logs
            {{- end }}
      volumes:
        - name: config
          configMap:
            name: {{ include "jairouter.fullname" . }}-config
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

### 5. Deploy Helm Chart

```bash
# Install Chart
helm install jairouter ./jairouter-chart -n jairouter --create-namespace

# Upgrade Chart
helm upgrade jairouter ./jairouter-chart -n jairouter

# View status
helm status jairouter -n jairouter

# Uninstall Chart
helm uninstall jairouter -n jairouter
```

## Advanced Configuration

### 1. Pod Anti-affinity

```yaml
# Add to deployment.yaml
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

### 2. Pod Disruption Budget

```yaml
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

### 3. Network Policy

```yaml
# networkpolicy.yaml
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
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to: []
    ports:
    - protocol: TCP
      port: 53
    - protocol: UDP
      port: 53
  - to: []
    ports:
    - protocol: TCP
      port: 80
    - protocol: TCP
      port: 443
```

## Operations Management

### 1. View Deployment Status

```bash
# View Pod status
kubectl get pods -n jairouter

# View Deployment status
kubectl get deployment -n jairouter

# View Service status
kubectl get service -n jairouter

# View Ingress status
kubectl get ingress -n jairouter

# View HPA status
kubectl get hpa -n jairouter
```

### 2. View Logs

```bash
# View Pod logs
kubectl logs -f deployment/jairouter -n jairouter

# View specific Pod logs
kubectl logs -f jairouter-xxx-yyy -n jairouter

# View all Pod logs
kubectl logs -f -l app=jairouter -n jairouter --max-log-requests=10
```

### 3. Rolling Updates

```bash
# Update image
kubectl set image deployment/jairouter jairouter=jairouter/model-router:v1.1.0 -n jairouter

# View update status
kubectl rollout status deployment/jairouter -n jairouter

# View update history
kubectl rollout history deployment/jairouter -n jairouter

# Rollback to previous version
kubectl rollout undo deployment/jairouter -n jairouter

# Rollback to specific version
kubectl rollout undo deployment/jairouter --to-revision=2 -n jairouter
```

### 4. Scaling

```bash
# Manual scaling
kubectl scale deployment jairouter --replicas=5 -n jairouter

# View HPA status
kubectl describe hpa jairouter-hpa -n jairouter

# Temporarily disable HPA
kubectl patch hpa jairouter-hpa -n jairouter -p '{"spec":{"minReplicas":0,"maxReplicas":0}}'
```

## Troubleshooting

### 1. Pod Troubleshooting

```bash
# View Pod details
kubectl describe pod jairouter-xxx-yyy -n jairouter

# View Pod events
kubectl get events --sort-by=.metadata.creationTimestamp -n jairouter

# Debug into Pod
kubectl exec -it jairouter-xxx-yyy -n jairouter -- sh

# View Pod resource usage
kubectl top pod -n jairouter
```

### 2. Network Troubleshooting

```bash
# Test Service connectivity
kubectl run test-pod --image=busybox -it --rm -- sh
# Execute inside Pod
nslookup jairouter-service.jairouter.svc.cluster.local
wget -qO- http://jairouter-service.jairouter.svc.cluster.local/actuator/health

# View Endpoints
kubectl get endpoints -n jairouter

# View Ingress status
kubectl describe ingress jairouter-ingress -n jairouter
```

### 3. Storage Troubleshooting

```bash
# View PVC status
kubectl get pvc -n jairouter

# View PV status
kubectl get pv

# View storage classes
kubectl get storageclass

# View Pod mount details
kubectl describe pod jairouter-xxx-yyy -n jairouter | grep -A 10 Volumes
```

## Best Practices

### 1. Resource Management

- Set appropriate resource requests and limits
- Use HPA and VPA for auto-scaling
- Configure Pod Disruption Budget
- Use anti-affinity to distribute Pods

### 2. Security Configuration

#### 1. Network Security Policy

Create `networkpolicy.yaml`:

```yaml
# Network policy configuration
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
  # Allow traffic from Ingress Controller
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  # Allow traffic from monitoring components
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
    ports:
    - protocol: TCP
      port: 8080
  egress:
  # Allow DNS queries
  - to:
    - namespaceSelector:
        matchLabels:
          name: kube-system
    ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
  # Allow access to external AI services
  - to: []
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 80
```

#### 2. Pod Security Policy

Update `deployment.yaml`:

```yaml
# Deployment security configuration
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jairouter
  namespace: jairouter
  labels:
    app: jairouter
spec:
  replicas: 3
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
      # Security context configuration
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        fsGroup: 1001
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
        - name: API_KEY
          valueFrom:
            secretKeyRef:
              name: jairouter-secret
              key: api-key
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
        - name: config-store-volume
          mountPath: /app/config-store
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        # Container security context
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
      restartPolicy: Always
```

#### 3. Secret Management

Create `secret.yaml`:

```yaml
# Secret configuration
apiVersion: v1
kind: Secret
metadata:
  name: jairouter-secret
  namespace: jairouter
type: Opaque
data:
  # Base64 encoded keys
  api-key: eW91ci1hcGkta2V5LWhlcmU=  # your-api-key-here
  jwt-secret: eW91ci1qd3Qtc2VjcmV0LWtleQ==  # your-jwt-secret-key
  database-password: cGFzc3dvcmQ=     # password

---
# TLS Secret configuration
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

#### 4. Application Security Configuration

Create `configmap-security.yaml`:

```yaml
# Security configuration ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-security-config
  namespace: jairouter
data:
  application-security.yml: |
    # Security configuration
    security:
      # API Key configuration
      api-key:
        enabled: true
        header: X-API-Key
        file: /app/config/api-keys.yml
      
      # JWT configuration
      jwt:
        enabled: true
        secret: ${JWT_SECRET}
        algorithm: HS256
        expiration-minutes: 60
        issuer: jairouter
        accounts:
          - username: admin
            password: ${ADMIN_PASSWORD}
            roles: [ADMIN, USER]
            enabled: true
          - username: user
            password: ${USER_PASSWORD}
            roles: [USER]
            enabled: true

      # CORS configuration
      cors:
        allowed-origins: "*"
        allowed-methods: "*"
        allowed-headers: "*"
        allow-credentials: false

    # HTTPS configuration
    server:
      port: 8443
      ssl:
        enabled: true
        key-store: /app/config/tls/keystore.p12
        key-store-password: ${SSL_KEYSTORE_PASSWORD}
        key-store-type: PKCS12
        key-alias: jairouter
```

Create `api-keys-config.yaml`:

```yaml
# API key configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: jairouter-api-keys
  namespace: jairouter
data:
  api-keys.yml: |
    # API key configuration
    api-keys:
      - name: "service-a"
        key: "sk-service-a-key-here"
        permissions:
          - "chat:read"
          - "embedding:read"
        enabled: true
      
      - name: "service-b"
        key: "sk-service-b-key-here"
        permissions:
          - "chat:*"
          - "embedding:*"
        enabled: true
```

### 3. Monitoring and Alerting

- Configure ServiceMonitor to collect metrics
- Set up PrometheusRule alert rules
- Use Grafana for monitoring visualization
- Configure alert notifications

### 4. High Availability

- Deploy multiple replicas
- Distribute across availability zones
- Configure health checks
- Implement rolling update strategies

## Next Steps

After completing the Kubernetes deployment, you can:

- **[Production Deployment](production.md)** - Configure a production-grade high-availability environment
- **[Monitoring Guide](../../monitoring/index.md)** - Set up a complete monitoring system
- **[Troubleshooting](../troubleshooting/index.md)** - Learn K8s environment fault diagnosis
- **[Performance Tuning](../troubleshooting/performance.md)** - Optimize K8s deployment performance
