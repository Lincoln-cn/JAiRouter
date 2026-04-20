# JAiRouter Makefile
# 提供便捷的构建和部署命令

# 变量定义
PROJECT_NAME := jairouter
ARTIFACT_ID := model-router
VERSION := $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "1.0-SNAPSHOT")
DOCKER_IMAGE := $(PROJECT_NAME)/$(ARTIFACT_ID)

# 默认目标
.DEFAULT_GOAL := help

# 帮助信息
.PHONY: help
help: ## 显示帮助信息
	@echo "JAiRouter 构建和部署工具"
	@echo ""
	@echo "可用命令:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# 清理
.PHONY: clean
clean: ## 清理构建产物
	@echo "清理构建产物..."
	mvn clean
	docker system prune -f

# 编译
.PHONY: compile
compile: ## 编译项目
	@echo "编译项目..."
	mvn compile

# 测试
.PHONY: test
test: ## 运行测试
	@echo "运行测试..."
	mvn test

# 打包
.PHONY: package
package: ## 打包应用
	@echo "打包应用..."
	mvn clean package

# 打包（跳过测试）
.PHONY: package-skip-tests
package-skip-tests: ## 打包应用（跳过测试）
	@echo "打包应用（跳过测试）..."
	mvn clean package -DskipTests

# 快速打包（跳过所有检查）
.PHONY: package-fast
package-fast: ## 快速打包应用（跳过所有检查）
	@echo "快速打包应用（跳过所有检查）..."
	mvn clean package -Pfast

# 代码质量检查
.PHONY: quality-check
quality-check: ## 运行代码质量检查
	@echo "运行代码质量检查..."
	mvn checkstyle:check spotbugs:check

# 生成覆盖率报告
.PHONY: coverage
coverage: ## 生成测试覆盖率报告
	@echo "生成测试覆盖率报告..."
	mvn clean test jacoco:report

# Docker 构建 - 生产环境
.PHONY: docker-build
docker-build: package-fast ## 构建生产环境 Docker 镜像
	@echo "构建生产环境 Docker 镜像..."
	docker build -t $(DOCKER_IMAGE):$(VERSION) .
	docker tag $(DOCKER_IMAGE):$(VERSION) $(DOCKER_IMAGE):latest

# Docker 构建 - 开发环境
.PHONY: docker-build-dev
docker-build-dev: package ## 构建开发环境 Docker 镜像
	@echo "构建开发环境 Docker 镜像..."
	docker build -f Dockerfile.dev -t $(DOCKER_IMAGE):$(VERSION)-dev .

# Docker 构建 - 使用 Jib
.PHONY: docker-build-jib
docker-build-jib: ## 使用 Jib 构建 Docker 镜像
	@echo "使用 Jib 构建 Docker 镜像..."
	mvn clean package jib:dockerBuild -Pjib

# Docker 运行 - 生产环境
.PHONY: docker-run
docker-run: ## 运行生产环境 Docker 容器
	@echo "运行生产环境 Docker 容器..."
	@mkdir -p logs config config-store data
	docker run -d \
		--name $(PROJECT_NAME)-prod \
		-p 8080:8080 \
		-e SPRING_PROFILES_ACTIVE=prod \
		-v $$(pwd)/config:/app/config:ro \
		-v $$(pwd)/logs:/app/logs \
		-v $$(pwd)/config-store:/app/config-store \
		-v $$(pwd)/data:/app/r2dbc:h2:file/data \
		--restart unless-stopped \
		$(DOCKER_IMAGE):latest

# Docker 运行 - 开发环境
.PHONY: docker-run-dev
docker-run-dev: ## 运行开发环境 Docker 容器
	@echo "运行开发环境 Docker 容器..."
	@mkdir -p logs config config-store data
	docker run -d \
		--name $(PROJECT_NAME)-dev \
		-p 8080:8080 \
		-p 5005:5005 \
		-e SPRING_PROFILES_ACTIVE=dev \
		-v $$(pwd)/config:/app/config \
		-v $$(pwd)/logs:/app/logs \
		-v $$(pwd)/config-store:/app/config-store \
		-v $$(pwd)/data:/app/r2dbc:h2:file/data \
		$(DOCKER_IMAGE):$(VERSION)-dev

# Docker Compose 启动
.PHONY: compose-up
compose-up: ## 使用 Docker Compose 启动服务
	@echo "使用 Docker Compose 启动服务..."
	docker-compose up -d

# Docker Compose 启动（包含监控）
.PHONY: compose-up-monitoring
compose-up-monitoring: ## 使用 Docker Compose 启动服务（包含监控）
	@echo "使用 Docker Compose 启动服务（包含监控）..."
	docker-compose --profile monitoring up -d

# Docker Compose 停止
.PHONY: compose-down
compose-down: ## 停止 Docker Compose 服务
	@echo "停止 Docker Compose 服务..."
	docker-compose down

# Docker 停止容器
.PHONY: docker-stop
docker-stop: ## 停止 Docker 容器
	@echo "停止 Docker 容器..."
	-docker stop $(PROJECT_NAME)-prod $(PROJECT_NAME)-dev
	-docker rm $(PROJECT_NAME)-prod $(PROJECT_NAME)-dev

# Docker 查看日志
.PHONY: docker-logs
docker-logs: ## 查看 Docker 容器日志
	@echo "查看生产环境容器日志..."
	docker logs -f $(PROJECT_NAME)-prod

# Docker 查看开发环境日志
.PHONY: docker-logs-dev
docker-logs-dev: ## 查看开发环境 Docker 容器日志
	@echo "查看开发环境容器日志..."
	docker logs -f $(PROJECT_NAME)-dev

# 健康检查
.PHONY: health-check
health-check: ## 执行应用健康检查
	@echo "执行应用健康检查..."
	@curl -f http://localhost:8080/actuator/health || echo "健康检查失败"

# 显示应用信息
.PHONY: info
info: ## 显示应用信息
	@echo "项目信息:"
	@echo "  项目名称: $(PROJECT_NAME)"
	@echo "  构件ID: $(ARTIFACT_ID)"
	@echo "  版本: $(VERSION)"
	@echo "  Docker镜像: $(DOCKER_IMAGE):$(VERSION)"
	@echo ""
	@echo "访问地址:"
	@echo "  应用主页: http://localhost:8080"
	@echo "  API文档: http://localhost:8080/swagger-ui/index.html"
	@echo "  健康检查: http://localhost:8080/actuator/health"
	@echo "  监控指标: http://localhost:8080/actuator/prometheus"

# 完整构建流程
.PHONY: build-all
build-all: clean quality-check test package docker-build ## 完整构建流程（清理、质量检查、测试、打包、Docker构建）

# 快速启动（开发环境）
.PHONY: dev
dev: docker-build-dev docker-stop docker-run-dev ## 快速启动开发环境

# 快速启动（生产环境）
.PHONY: prod
prod: docker-build docker-stop docker-run ## 快速启动生产环境

# 清理 Docker 资源
.PHONY: docker-clean
docker-clean: ## 清理 Docker 资源
	@echo "清理 Docker 资源..."
	-docker stop $(PROJECT_NAME)-prod $(PROJECT_NAME)-dev
	-docker rm $(PROJECT_NAME)-prod $(PROJECT_NAME)-dev
	-docker rmi $(DOCKER_IMAGE):$(VERSION) $(DOCKER_IMAGE):latest $(DOCKER_IMAGE):$(VERSION)-dev
	docker system prune -f

# ========================================
# 监控栈相关命令 (v1.9.5)
# ========================================

# 启动监控栈
.PHONY: monitoring-start
monitoring-start: ## 启动完整监控栈（Prometheus + Grafana + Loki + Tempo）
	@echo "启动 JAiRouter 监控栈..."
	@bash scripts/monitoring-stack.sh start

# 停止监控栈
.PHONY: monitoring-stop
monitoring-stop: ## 停止监控栈
	@echo "停止 JAiRouter 监控栈..."
	@bash scripts/monitoring-stack.sh stop

# 重启监控栈
.PHONY: monitoring-restart
monitoring-restart: ## 重启监控栈
	@echo "重启 JAiRouter 监控栈..."
	@bash scripts/monitoring-stack.sh restart

# 查看监控栈日志
.PHONY: monitoring-logs
monitoring-logs: ## 查看监控栈日志
	@bash scripts/monitoring-stack.sh logs

# 查看监控栈指定服务日志
.PHONY: monitoring-logs-service
monitoring-logs-service: ## 查看监控栈指定服务日志 (make monitoring-logs-service SERVICE=prometheus)
	@bash scripts/monitoring-stack.sh logs $(SERVICE)

# 监控栈状态
.PHONY: monitoring-status
monitoring-status: ## 查看监控栈服务状态
	@bash scripts/monitoring-stack.sh status

# 清理监控数据
.PHONY: monitoring-clean
monitoring-clean: ## 清理监控数据（危险操作）
	@echo "清理监控数据..."
	@bash scripts/monitoring-stack.sh clean

# 仅启动 Prometheus 和 Grafana
.PHONY: monitoring-lite
monitoring-lite: ## 仅启动基础监控（Prometheus + Grafana）
	@echo "启动基础监控..."
	docker-compose -f docker-compose-monitoring.yml up -d prometheus grafana

# 显示监控访问信息
.PHONY: monitoring-info
monitoring-info: ## 显示监控访问信息
	@echo ""
	@echo "=========================================="
	@echo "   JAiRouter 监控栈访问信息"
	@echo "=========================================="
	@echo ""
	@echo "  Prometheus:     http://localhost:9090"
	@echo "  Grafana:        http://localhost:3000 (admin/admin)"
	@echo "  AlertManager:   http://localhost:9093"
	@echo "  Loki:           http://localhost:3100"
	@echo "  Tempo:          http://localhost:3200"
	@echo "  Node Exporter:  http://localhost:9100"
	@echo "  cAdvisor:       http://localhost:8080"
	@echo ""
	@echo "Grafana 仪表盘:"
	@echo "  - 系统概览：system-overview.json"
	@echo "  - 基础设施：infrastructure.json"
	@echo "  - 性能分析：performance-analysis.json"
	@echo "  - 业务指标：business-metrics.json"
	@echo "  - 告警概览：alerts-overview.json"
	@echo "  - 异常管理：exception-management.json (v1.9.4)"
	@echo ""