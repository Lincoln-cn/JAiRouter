<template>
  <div class="jwt-token-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>JWT令牌管理</span>
          <el-button type="primary" @click="handleRefreshTokens">刷新令牌列表</el-button>
        </div>
      </template>
      
      <el-table :data="tokens" style="width: 100%">
        <el-table-column prop="userId" label="用户ID" width="180" />
        <el-table-column prop="token" label="令牌" show-overflow-tooltip />
        <el-table-column prop="issuedAt" label="签发时间" width="200" />
        <el-table-column prop="expiresAt" label="过期时间" width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">
              {{ scope.row.status === 'active' ? '有效' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button 
              size="small" 
              type="danger" 
              :disabled="scope.row.status !== 'active'"
              @click="handleRevoke(scope.row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 批量撤销令牌对话框 -->
    <el-dialog v-model="batchRevokeDialogVisible" title="批量撤销令牌" width="500px">
      <el-form :model="batchRevokeForm" label-width="100px">
        <el-form-item label="用户ID">
          <el-input v-model="batchRevokeForm.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="batchRevokeForm.expiresBefore"
            type="datetime"
            placeholder="选择在此时间之前过期的令牌"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="batchRevokeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleBatchRevoke">撤销</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 模拟数据
const tokens = ref([
  { 
    userId: 'user-1', 
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...', 
    issuedAt: '2023-10-01 10:00:00', 
    expiresAt: '2023-10-01 11:00:00', 
    status: 'active'
  },
  { 
    userId: 'user-2', 
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...', 
    issuedAt: '2023-10-01 09:30:00', 
    expiresAt: '2023-10-01 10:30:00', 
    status: 'revoked'
  },
  { 
    userId: 'user-1', 
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...', 
    issuedAt: '2023-10-01 08:00:00', 
    expiresAt: '2023-10-01 09:00:00', 
    status: 'revoked'
  }
])

const batchRevokeDialogVisible = ref(false)

const batchRevokeForm = ref({
  userId: '',
  expiresBefore: ''
})

// 刷新令牌列表
const handleRefreshTokens = () => {
  // 这里可以调用API获取最新的令牌列表
  ElMessage.success('令牌列表已刷新')
}

// 撤销令牌
const handleRevoke = (row: any) => {
  ElMessageBox.confirm(`确定要撤销用户 ${row.userId} 的令牌吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    row.status = 'revoked'
    ElMessage.success('令牌已撤销')
  })
}

// 打开批量撤销对话框
const handleOpenBatchRevoke = () => {
  batchRevokeForm.value = {
    userId: '',
    expiresBefore: ''
  }
  batchRevokeDialogVisible.value = true
}

// 批量撤销令牌
const handleBatchRevoke = () => {
  // 这里可以调用API进行批量撤销操作
  ElMessage.success('批量撤销操作已提交')
  batchRevokeDialogVisible.value = false
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取JWT令牌管理数据')
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.jwt-token-management {
  padding: 20px;
}
</style>