#!/bin/bash

# 配置版本管理集成测试
# 使用 Spring Boot Test 方式验证业务逻辑

echo "========================================"
echo "  配置版本管理集成测试"
echo "========================================"

cd /home/ubuntu/jairouter/modelrouter

# 运行特定测试类
echo -e "\n运行版本管理相关测试...\n"

./mvnw test \
    -Dtest=org.unreal.modelrouter.version.** \
    -DfailIfNoTests=false \
    -q 2>&1 | tail -50

echo -e "\n========================================"
echo "测试完成"
echo "========================================"
