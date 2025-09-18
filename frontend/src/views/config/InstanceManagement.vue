<template>
  <div class="instance-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>实例管理</span>
          <el-button type="primary" @click="handleAddInstance">添加实例</el-button>
        </div>
      </template>
      
      <el-tabs v-model="activeServiceType">
        <el-tab-pane
          v-for="serviceType in serviceTypes"
          :key="serviceType"
          :label="serviceType"
          :name="serviceType"
        >
          <el-table :data="instances[serviceType]" style="width: 100%">
            <el-table-column prop="name" label="实例名称" width="180" />
            <el-table-column prop="baseUrl" label="基础URL" width="250" />
            <el-table-column prop="weight" label="权重" width="80" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">
                  {{ scope.row.status === 'active' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    
    <!-- 添加/编辑实例对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="服务类型">
          <el-select v-model="form.serviceType" placeholder="请选择服务类型">
            <el-option
              v-for="serviceType in serviceTypes"
              :key="serviceType"
              :label="serviceType"
              :value="serviceType"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实例名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="基础URL">
          <el-input v-model="form.baseUrl" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getServiceInstances, getServiceTypes, addServiceInstance, updateServiceInstance, deleteServiceInstance } from '@/api/dashboard'

// 服务类型
const serviceTypes = ref<string[]>([])

// 当前激活的服务类型
const activeServiceType = ref('chat')

// 定义实例类型
interface ServiceInstance {
  id: number
  serviceType: string
  name: string
  baseUrl: string
  weight: number
  status: 'active' | 'inactive'
}

// 实例数据缓存
const instancesCache = ref<Record<string, any[]>>({})
// 实例数据
const instances = ref<Record<string, any[]>>({})

// 请求防抖定时器
let fetchTimer: ReturnType<typeof setTimeout> | null = null

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const form = ref<ServiceInstance>({
  id: 0,
  serviceType: 'chat',
  name: '',
  baseUrl: '',
  weight: 1,
  status: 'active'
})

// 监听服务类型变化，获取对应实例数据
watch(activeServiceType, (newServiceType) => {
  if (newServiceType && !instancesCache.value[newServiceType]) {
    fetchServiceInstances(newServiceType)
  } else if (newServiceType && instancesCache.value[newServiceType]) {
    // 从缓存中获取数据
    instances.value[newServiceType] = [...instancesCache.value[newServiceType]]
  }
})

// 获取服务类型列表
const fetchServiceTypes = async () => {
  try {
    const response = await getServiceTypes()
    if (response.data?.success) {
      serviceTypes.value = response.data.data || []
      if (serviceTypes.value.length > 0) {
        activeServiceType.value = serviceTypes.value[0]
        // 获取第一个服务类型的实例数据
        fetchServiceInstances(serviceTypes.value[0])
      }
    }
  } catch (error) {
    console.error('获取服务类型失败:', error)
    ElMessage.error('获取服务类型失败')
  }
}

// 获取指定服务类型的实例列表（带防抖和缓存）
const fetchServiceInstances = (serviceType: string) => {
  // 清除之前的定时器
  if (fetchTimer) {
    clearTimeout(fetchTimer)
  }
  
  // 设置新的定时器（防抖）
  fetchTimer = setTimeout(async () => {
    try {
      // 如果缓存中有数据，直接使用
      if (instancesCache.value[serviceType]) {
        instances.value[serviceType] = [...instancesCache.value[serviceType]]
        return
      }
      
      const response = await getServiceInstances(serviceType)
      if (response.data?.success) {
        const data = response.data.data || []
        // 缓存数据
        instancesCache.value[serviceType] = data
        instances.value[serviceType] = [...data]
      } else {
        instances.value[serviceType] = []
      }
    } catch (error) {
      console.error(`获取${serviceType}服务实例失败:`, error)
      instances.value[serviceType] = []
      ElMessage.error(`获取${serviceType}服务实例失败`)
    }
  }, 300) // 300ms防抖延迟
}

// 添加实例
const handleAddInstance = () => {
  dialogTitle.value = '添加实例'
  isEdit.value = false
  form.value = {
    id: 0,
    serviceType: activeServiceType.value,
    name: '',
    baseUrl: '',
    weight: 1,
    status: 'active'
  }
  dialogVisible.value = true
}

// 编辑实例
const handleEdit = (row: any) => {
  dialogTitle.value = '编辑实例'
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

// 删除实例
const handleDelete = (row: any) => {
  ElMessageBox.confirm('确定要删除该实例吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 调用后端API删除实例，不创建新版本
      const response = await deleteServiceInstance(row.serviceType, row.name, row.baseUrl, false)
      if (response.data?.success) {
        instances.value[row.serviceType] = instances.value[row.serviceType].filter((instance: any) => instance.id !== row.id)
        // 更新缓存
        instancesCache.value[row.serviceType] = [...instances.value[row.serviceType]]
        ElMessage.success('删除成功')
      } else {
        ElMessage.error(response.data?.message || '删除失败')
      }
    } catch (error) {
      console.error('删除实例失败:', error)
      ElMessage.error('删除实例失败')
    }
  })
}

// 保存实例
const handleSave = async () => {
  try {
    if (isEdit.value) {
      // 编辑
      // 构造更新数据
      const updateData = {
        instanceId: `${form.value.name}@${form.value.baseUrl}`,
        instance: {
          name: form.value.name,
          baseUrl: form.value.baseUrl,
          weight: form.value.weight,
          status: form.value.status
        }
      }
      
      // 调用后端API更新实例，不创建新版本
      const response = await updateServiceInstance(form.value.serviceType, updateData, false)
      if (response.data?.success) {
        const index = instances.value[form.value.serviceType].findIndex((instance: any) => instance.id === form.value.id)
        if (index !== -1) {
          instances.value[form.value.serviceType][index] = { ...form.value }
          // 更新缓存
          instancesCache.value[form.value.serviceType] = [...instances.value[form.value.serviceType]]
        }
        ElMessage.success('编辑成功')
      } else {
        ElMessage.error(response.data?.message || '编辑失败')
        return
      }
    } else {
      // 新增
      // 构造实例数据
      const instanceData = {
        name: form.value.name,
        baseUrl: form.value.baseUrl,
        weight: form.value.weight,
        status: form.value.status
      }
      
      // 调用后端API添加实例，不创建新版本
      const response = await addServiceInstance(form.value.serviceType, instanceData, false)
      if (response.data?.success) {
        const newInstance: any = {
          ...form.value,
          id: Date.now()
        }
        if (!instances.value[form.value.serviceType]) {
          instances.value[form.value.serviceType] = []
        }
        instances.value[form.value.serviceType].push(newInstance)
        // 更新缓存
        instancesCache.value[form.value.serviceType] = [...instances.value[form.value.serviceType]]
        ElMessage.success('添加成功')
      } else {
        ElMessage.error(response.data?.message || '添加失败')
        return
      }
    }
    
    dialogVisible.value = false
  } catch (error) {
    console.error('保存实例失败:', error)
    ElMessage.error('保存实例失败')
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchServiceTypes()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.instance-management {
  padding: 20px;
}
</style>