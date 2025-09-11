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
import { ref, onMounted } from 'vue'

// 服务类型
const serviceTypes = ref(['chat', 'embedding', 'rerank', 'tts', 'stt'])

// 当前激活的服务类型
const activeServiceType = ref('chat')

// 模拟数据
const instances = ref({
  chat: [
    { id: 1, serviceType: 'chat', name: 'Ollama Chat', baseUrl: 'http://localhost:11434', weight: 50, status: 'active' },
    { id: 2, serviceType: 'chat', name: 'VLLM Chat', baseUrl: 'http://localhost:8000', weight: 30, status: 'active' }
  ],
  embedding: [
    { id: 3, serviceType: 'embedding', name: 'Ollama Embedding', baseUrl: 'http://localhost:11434', weight: 40, status: 'active' }
  ],
  rerank: [],
  tts: [],
  stt: []
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const form = ref({
  id: 0,
  serviceType: 'chat',
  name: '',
  baseUrl: '',
  weight: 1,
  status: true
})

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
    status: true
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
  }).then(() => {
    instances.value[row.serviceType] = instances.value[row.serviceType].filter(instance => instance.id !== row.id)
    ElMessage.success('删除成功')
  })
}

// 保存实例
const handleSave = () => {
  if (isEdit.value) {
    // 编辑
    const index = instances.value[form.value.serviceType].findIndex(instance => instance.id === form.value.id)
    if (index !== -1) {
      instances.value[form.value.serviceType][index] = { ...form.value }
    }
  } else {
    // 新增
    const newInstance = {
      id: Date.now(),
      ...form.value
    }
    if (!instances.value[form.value.serviceType]) {
      instances.value[form.value.serviceType] = []
    }
    instances.value[form.value.serviceType].push(newInstance)
  }
  
  dialogVisible.value = false
  ElMessage.success(isEdit.value ? '编辑成功' : '添加成功')
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取实例管理数据')
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