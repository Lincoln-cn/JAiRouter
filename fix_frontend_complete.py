#!/usr/bin/env python3
# V1.4.6 前端独立配置完整修复脚本

import re

file_path = '/home/ubuntu/jairouter/modelrouter/frontend/src/views/config/InstanceManagement.vue'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

print("开始修复前端独立配置功能...")

# 1. 添加组件导入
if 'RateLimitConfig' not in content:
    old_import = "import type { ServiceInstance } from '@/types/config'"
    new_import = """import type { ServiceInstance } from '@/types/config'
import RateLimitConfig from '@/components/RateLimitConfig.vue'
import CircuitBreakerConfig from '@/components/CircuitBreakerConfig.vue'"""
    content = content.replace(old_import, new_import)
    print("✓ 已添加组件导入")

# 2. 添加图标导入
if 'Timer' not in content:
    old_icons = "import { Plus, Edit, Delete, Close, Key } from '@element-plus/icons-vue'"
    new_icons = "import { Plus, Edit, Delete, Close, Key, Timer, WarningFilled } from '@element-plus/icons-vue'"
    content = content.replace(old_icons, new_icons)
    print("✓ 已添加图标导入")

# 3. 添加变量
if 'rateLimitDialogVisible' not in content:
    old_vars = "const dialogVisible = ref(false)"
    new_vars = """const dialogVisible = ref(false)
// 限流器独立配置
const rateLimitDialogVisible = ref(false)
const rateLimitDialogTitle = ref('限流器配置')
const rateLimitFormData = ref<any>({})
const rateLimitEditInstance = ref<any>(null)
// 熔断器独立配置
const circuitBreakerDialogVisible = ref(false)
const circuitBreakerDialogTitle = ref('熔断器配置')
const circuitBreakerFormData = ref<any>({})
const circuitBreakerEditInstance = ref<any>(null)"""
    content = content.replace(old_vars, new_vars)
    print("✓ 已添加变量")

# 4. 添加按钮到操作列
if 'openRateLimitConfig' not in content:
    old_buttons = '''<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle>
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle>
                      <el-icon><Delete /></el-icon>
                    </el-button>'''
    
    new_buttons = '''<el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑实例">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button size="small" @click="openRateLimitConfig(scope.row)" type="warning" plain circle title="限流器配置">
                      <el-icon><Timer /></el-icon>
                    </el-button>
                    <el-button size="small" @click="openCircuitBreakerConfig(scope.row)" type="danger" plain circle title="熔断器配置">
                      <el-icon><WarningFilled /></el-icon>
                    </el-button>
                    <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle title="删除">
                      <el-icon><Delete /></el-icon>
                    </el-button>'''
    
    content = content.replace(old_buttons, new_buttons)
    content = content.replace('label="操作" width="120"', 'label="操作" width="220"')
    print("✓ 已添加操作按钮")

# 5. 添加弹窗组件
if '<RateLimitConfig' not in content:
    old_dialog = "<!-- 添加/编辑实例对话框 -->"
    new_dialogs = """<!-- 限流器配置弹窗 -->
    <RateLimitConfig
      v-model="rateLimitDialogVisible"
      :title="rateLimitDialogTitle"
      :initial-data="rateLimitFormData"
      @save="handleRateLimitSave"
    />

    <!-- 熔断器配置弹窗 -->
    <CircuitBreakerConfig
      v-model="circuitBreakerDialogVisible"
      :title="circuitBreakerDialogTitle"
      :initial-data="circuitBreakerFormData"
      @save="handleCircuitBreakerSave"
    />

    <!-- 添加/编辑实例对话框 -->"""
    content = content.replace(old_dialog, new_dialogs)
    print("✓ 已添加弹窗组件")

# 6. 删除编辑对话框中的限流和熔断配置
# 使用正则表达式删除
pattern = r'\s*<el-divider content-position="left">限流配置</el-divider>.*?<el-divider content-position="left">熔断器配置</el-divider>'
content = re.sub(pattern, '\n        ', content, flags=re.DOTALL)
print("✓ 已删除限流配置部分")

pattern2 = r'\s*<el-divider content-position="left">熔断器配置</el-divider>.*?</el-form>'
content = re.sub(pattern2, '\n      </el-form>', content, flags=re.DOTALL)
print("✓ 已删除熔断器配置部分")

# 7. 添加方法
if 'openRateLimitConfig' not in content:
    old_mounted = "// 初始化\nonMounted(() => {"
    new_methods = """// 打开限流器配置弹窗
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
      rateLimitAlgorithm: configData.rateLimit?.algorithm || 'token-bucket',
      rateLimitCapacity: configData.rateLimit?.capacity || 100,
      rateLimitRate: configData.rateLimit?.rate || 10,
      rateLimitScope: configData.rateLimit?.scope || 'instance',
      rateLimitKey: configData.rateLimit?.key || '',
      rateLimitClientIpEnable: configData.rateLimit?.clientIpEnable || false,
      circuitBreakerEnabled: instance.circuitBreaker?.enabled || false,
      circuitBreakerFailureThreshold: instance.circuitBreaker?.failureThreshold || 5,
      circuitBreakerTimeout: instance.circuitBreaker?.timeout || 60000,
      circuitBreakerSuccessThreshold: instance.circuitBreaker?.successThreshold || 2
    }
    const response = await updateServiceInstance(serviceType, instance.instanceId, updateData)
    if (response.data?.success) {
      ElMessage.success('限流器配置保存成功')
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '保存失败')
    }
  } catch (error) {
    console.error('保存限流器配置失败:', error)
    ElMessage.error('保存限流器配置失败')
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
      rateLimitAlgorithm: instance.rateLimit?.algorithm || 'token-bucket',
      rateLimitCapacity: instance.rateLimit?.capacity || 100,
      rateLimitRate: instance.rateLimit?.rate || 10,
      rateLimitScope: instance.rateLimit?.scope || 'instance',
      rateLimitKey: instance.rateLimit?.key || '',
      rateLimitClientIpEnable: instance.rateLimit?.clientIpEnable || false,
      circuitBreakerEnabled: configData.circuitBreaker?.enabled || false,
      circuitBreakerFailureThreshold: configData.circuitBreaker?.failureThreshold || 5,
      circuitBreakerTimeout: configData.circuitBreaker?.timeout || 60000,
      circuitBreakerSuccessThreshold: configData.circuitBreaker?.successThreshold || 2
    }
    const response = await updateServiceInstance(serviceType, instance.instanceId, updateData)
    if (response.data?.success) {
      ElMessage.success('熔断器配置保存成功')
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '保存失败')
    }
  } catch (error) {
    console.error('保存熔断器配置失败:', error)
    ElMessage.error('保存熔断器配置失败')
  } finally {
    saveLoading.value = false
  }
}

// 初始化
onMounted(() => {"""
    content = content.replace(old_mounted, new_methods)
    print("✓ 已添加方法")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ 前端独立配置功能修复完成！")
print("请运行 npm run build 编译前端")
