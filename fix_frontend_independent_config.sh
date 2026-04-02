#!/bin/bash
# V1.4.6 前端独立配置修复脚本

cd /home/ubuntu/jairouter/modelrouter/frontend

echo "=== 开始修复前端独立配置功能 ==="

# 1. 备份
cp src/views/config/InstanceManagement.vue src/views/config/InstanceManagement.vue.bak.$(date +%Y%m%d_%H%M%S)

# 2. 使用 sed 添加组件导入
echo "添加组件导入..."
sed -i "/import type { ServiceInstance } from '@\/types\/config'/a import RateLimitConfig from '@/components/RateLimitConfig.vue'\nimport CircuitBreakerConfig from '@/components/CircuitBreakerConfig.vue'" src/views/config/InstanceManagement.vue

# 3. 添加图标导入
echo "添加图标导入..."
sed -i "s/import { Plus, Edit, Delete, Close, Key } from '@element-plus\/icons-vue'/import { Plus, Edit, Delete, Close, Key, Timer, WarningFilled } from '@element-plus\/icons-vue'/" src/views/config/InstanceManagement.vue

# 4. 添加变量
echo "添加变量..."
sed -i '/const dialogVisible = ref(false)/a\
// 限流器独立配置\
const rateLimitDialogVisible = ref(false)\
const rateLimitDialogTitle = ref('\''限流器配置'\'')\
const rateLimitFormData = ref<any>({})\
const rateLimitEditInstance = ref<any>(null)\
// 熔断器独立配置\
const circuitBreakerDialogVisible = ref(false)\
const circuitBreakerDialogTitle = ref('\''熔断器配置'\'')\
const circuitBreakerFormData = ref<any>({})\
const circuitBreakerEditInstance = ref<any>(null)' src/views/config/InstanceManagement.vue

# 5. 添加按钮到操作列
echo "添加操作按钮..."
sed -i 's/<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle>/<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑实例">/' src/views/config/InstanceManagement.vue
sed -i 's/<el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle>/<el-button size="small" @click="openRateLimitConfig(scope.row)" type="warning" plain circle title="限流器配置">\
                      <el-icon><Timer \/><\/el-icon>\
                    <\/el-button>\
                    <el-button size="small" @click="openCircuitBreakerConfig(scope.row)" type="danger" plain circle title="熔断器配置">\
                      <el-icon><WarningFilled \/><\/el-icon>\
                    <\/el-button>\
                    <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle/' src/views/config/InstanceManagement.vue

# 6. 调整操作列宽度
sed -i 's/label="操作" width="120"/label="操作" width="220"/' src/views/config/InstanceManagement.vue

echo "=== 前端修复完成 ==="
echo "请手动添加方法和弹窗组件到 script 部分"
