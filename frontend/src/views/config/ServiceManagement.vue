<template>
  <div class="service-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>服务管理</span>
          <el-button type="primary" @click="handleAddService">添加服务</el-button>
        </div>
      </template>
      
      <el-table :data="services" style="width: 100%">
        <el-table-column prop="type" label="服务类型" width="180" />
        <el-table-column prop="name" label="服务名称" width="180" />
        <el-table-column prop="description" label="描述" />
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
    </el-card>
    
    <!-- 添加/编辑服务对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="服务类型">
          <el-input v-model="form.type" />
        </el-form-item>
        <el-form-item label="服务名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 定义服务类型
interface Service {
  id: number
  type: string
  name: string
  description: string
  status: string
}

// 模拟数据
const services = ref<Service[]>([
  { id: 1, type: 'chat', name: '聊天服务', description: '提供聊天模型服务', status: 'active' },
  { id: 2, type: 'embedding', name: '嵌入服务', description: '提供文本嵌入服务', status: 'active' },
  { id: 3, type: 'rerank', name: '重排序服务', description: '提供结果重排序服务', status: 'inactive' }
])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const form = ref<Omit<Service, 'id'> & { id: number }>({
  id: 0,
  type: '',
  name: '',
  description: '',
  status: ''
})

// 添加服务
const handleAddService = () => {
  dialogTitle.value = '添加服务'
  isEdit.value = false
  form.value = {
    id: 0,
    type: '',
    name: '',
    description: '',
    status: 'active'
  }
  dialogVisible.value = true
}

// 编辑服务
const handleEdit = (row: Service) => {
  dialogTitle.value = '编辑服务'
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

// 删除服务
const handleDelete = (row: Service) => {
  ElMessageBox.confirm('确定要删除该服务吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    services.value = services.value.filter(service => service.id !== row.id)
    ElMessage.success('删除成功')
  })
}

// 保存服务
const handleSave = () => {
  if (isEdit.value) {
    // 编辑
    const index = services.value.findIndex(service => service.id === form.value.id)
    if (index !== -1) {
      services.value[index] = { ...form.value }
    }
  } else {
    // 新增
    const newService: Service = {
      ...form.value,
      id: Date.now()
    }
    services.value.push(newService)
  }
  
  dialogVisible.value = false
  ElMessage.success(isEdit.value ? '编辑成功' : '添加成功')
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取服务管理数据')
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.service-management {
  padding: 20px;
}
</style>