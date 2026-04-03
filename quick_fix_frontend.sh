#!/bin/bash
# V1.4.6 前端独立配置快速修复脚本

cd /home/ubuntu/jairouter/modelrouter/frontend

echo "=== 开始快速修复前端独立配置 ==="

# 1. 备份
BACKUP_FILE="src/views/config/InstanceManagement.vue.quickfix.$(date +%Y%m%d_%H%M%S)"
cp src/views/config/InstanceManagement.vue "$BACKUP_FILE"
echo "✓ 已备份到 $BACKUP_FILE"

# 2. 使用 sed 添加组件导入
echo "添加组件导入..."
sed -i '/import type { ServiceInstance/a import RateLimitConfig from '\''@/components/RateLimitConfig.vue'\''\nimport CircuitBreakerConfig from '\''@/components/CircuitBreakerConfig.vue\'' src/views/config/InstanceManagement.vue

# 3. 添加图标导入
echo "添加图标导入..."
sed -i 's/import { Plus, Edit, Delete, Close, Key }/import { Plus, Edit, Delete, Close, Key, Timer, WarningFilled }/' src/views/config/InstanceManagement.vue

# 4. 添加变量
echo "添加变量..."
sed -i '/const dialogVisible = ref(false)/a\
const rateLimitDialogVisible = ref(false)\
const rateLimitDialogTitle = ref('\''限流器配置'\'')\
const rateLimitFormData = ref<any>({})\
const rateLimitEditInstance = ref<any>(null)\
const circuitBreakerDialogVisible = ref(false)\
const circuitBreakerDialogTitle = ref('\''熔断器配置'\'')\
const circuitBreakerFormData = ref<any>({})\
const circuitBreakerEditInstance = ref<any>(null)' src/views/config/InstanceManagement.vue

# 5. 添加按钮（在编辑按钮后，删除按钮前）
echo "添加操作按钮..."
sed -i 's/<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle>/<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑实例">/' src/views/config/InstanceManagement.vue

sed -i '/<el-button size="small" @click="handleEdit/i\
                    <el-button size="small" @click="openRateLimitConfig(scope.row)" type="warning" plain circle title="限流器配置">\
                      <el-icon><Timer \/><\/el-icon>\
                    <\/el-button>\
                    <el-button size="small" @click="openCircuitBreakerConfig(scope.row)" type="danger" plain circle title="熔断器配置">\
                      <el-icon><WarningFilled \/><\/el-icon>\
                    <\/el-button>' src/views/config/InstanceManagement.vue

# 6. 调整列宽
sed -i 's/label="操作" width="120"/label="操作" width="220"/' src/views/config/InstanceManagement.vue

# 7. 添加弹窗组件
echo "添加弹窗组件..."
sed -i '/<!-- 添加\/编辑实例对话框 -->/i\
    <!-- 限流器配置弹窗 -->\
    <RateLimitConfig\
      v-model="rateLimitDialogVisible"\
      :title="rateLimitDialogTitle"\
      :initial-data="rateLimitFormData"\
      @save="handleRateLimitSave"\
    />\
\
    <!-- 熔断器配置弹窗 -->\
    <CircuitBreakerConfig\
      v-model="circuitBreakerDialogVisible"\
      :title="circuitBreakerDialogTitle"\
      :initial-data="circuitBreakerFormData"\
      @save="handleCircuitBreakerSave"\
    />\
' src/views/config/InstanceManagement.vue

echo "✓ 模板部分已修改"

# 8. 添加方法
echo "添加方法..."
METHODS='
// 打开限流器配置弹窗
const openRateLimitConfig = (row: any) => {
  rateLimitEditInstance.value = row
  rateLimitDialogTitle.value = `限流器配置 - ${row.name}`
  rateLimitFormData.value = { rateLimit: row.rateLimit || { enabled: false } }
  rateLimitDialogVisible.value = true
}

// 打开熔断器配置弹窗
const openCircuitBreakerConfig = (row: any) => {
  circuitBreakerEditInstance.value = row
  circuitBreakerDialogTitle.value = `熔断器配置 - ${row.name}`
  circuitBreakerFormData.value = { circuitBreaker: row.circuitBreaker || { enabled: false } }
  circuitBreakerDialogVisible.value = true
}

// 保存限流器配置
const handleRateLimitSave = async (configData: any) => {
  if (!rateLimitEditInstance.value) return
  saveLoading.value = true
  try {
    const serviceType = activeServiceType.value
    const instance = rateLimitEditInstance.value
    const updateData = {
      instanceId: instance.instanceId,
      name: instance.name,
      baseUrl: instance.baseUrl,
      path: instance.path,
      weight: instance.weight,
      status: instance.status,
      adapter: instance.adapter,
      headers: instance.headers || {},
      rateLimitEnabled: configData.rateLimit?.enabled || false,
      rateLimitAlgorithm: configData.rateLimit?.algorithm || '\''token-bucket'\'',
      rateLimitCapacity: configData.rateLimit?.capacity || 100,
      rateLimitRate: configData.rateLimit?.rate || 10,
      rateLimitScope: configData.rateLimit?.scope || '\''instance'\'',
      rateLimitKey: configData.rateLimit?.key || '\''\'',
      rateLimitClientIpEnable: configData.rateLimit?.clientIpEnable || false,
      circuitBreakerEnabled: instance.circuitBreaker?.enabled || false,
      circuitBreakerFailureThreshold: instance.circuitBreaker?.failureThreshold || 5,
      circuitBreakerTimeout: instance.circuitBreaker?.timeout || 60000,
      circuitBreakerSuccessThreshold: instance.circuitBreaker?.successThreshold || 2
    }
    const response = await updateServiceInstance(serviceType, instance.instanceId, updateData)
    if (response.data?.success) {
      ElMessage.success('\''限流器配置保存成功'\'')
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '\''保存失败'\'')
    }
  } catch (error) {
    console.error('\''保存限流器配置失败:'\'', error)
    ElMessage.error('\''保存限流器配置失败'\'')
  } finally {
    saveLoading.value = false
  }
}

// 保存熔断器配置
const handleCircuitBreakerSave = async (configData: any) => {
  if (!circuitBreakerEditInstance.value) return
  saveLoading.value = true
  try {
    const serviceType = activeServiceType.value
    const instance = circuitBreakerEditInstance.value
    const updateData = {
      instanceId: instance.instanceId,
      name: instance.name,
      baseUrl: instance.baseUrl,
      path: instance.path,
      weight: instance.weight,
      status: instance.status,
      adapter: instance.adapter,
      headers: instance.headers || {},
      rateLimitEnabled: instance.rateLimit?.enabled || false,
      rateLimitAlgorithm: instance.rateLimit?.algorithm || '\''token-bucket'\'',
      rateLimitCapacity: instance.rateLimit?.capacity || 100,
      rateLimitRate: instance.rateLimit?.rate || 10,
      rateLimitScope: instance.rateLimit?.scope || '\''instance'\'',
      rateLimitKey: instance.rateLimit?.key || '\''\'',
      rateLimitClientIpEnable: instance.rateLimit?.clientIpEnable || false,
      circuitBreakerEnabled: configData.circuitBreaker?.enabled || false,
      circuitBreakerFailureThreshold: configData.circuitBreaker?.failureThreshold || 5,
      circuitBreakerTimeout: configData.circuitBreaker?.timeout || 60000,
      circuitBreakerSuccessThreshold: configData.circuitBreaker?.successThreshold || 2
    }
    const response = await updateServiceInstance(serviceType, instance.instanceId, updateData)
    if (response.data?.success) {
      ElMessage.success('\''熔断器配置保存成功'\'')
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '\''保存失败'\'')
    }
  } catch (error) {
    console.error('\''保存熔断器配置失败:'\'', error)
    ElMessage.error('\''保存熔断器配置失败'\'')
  } finally {
    saveLoading.value = false
  }
}
'

# 在 onMounted 之前添加方法
sed -i "/\/\/ 初始化/i\\
$METHODS
" src/views/config/InstanceManagement.vue

echo "✓ 方法已添加"

echo ""
echo "=== 前端修复完成！==="
echo "请运行 npm run build 编译前端"
