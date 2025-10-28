<template>
  <div class="login-container">
    <el-card class="login-form">
      <div class="login-header text-center mb-4">
        <div class="logo-placeholder">
          <svg class="logo-icon" viewBox="0 0 100 100">
            <circle cx="50" cy="40" r="20" fill="#409EFF" />
            <path d="M30 70 Q50 90 70 70" stroke="#409EFF" stroke-width="8" fill="none" />
          </svg>
        </div>
        <h1 class="login-title">{{ t('login.title') }}</h1>
        <p class="login-subtitle">{{ t('login.subtitle') }}</p>
      </div>
      
      <!-- 使用Element Plus组件 -->
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item :label="t('login.username')" prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="t('login.usernamePlaceholder')"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        
        <el-form-item :label="t('login.password')" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            size="large"
            show-password
            prefix-icon="Lock"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            size="large"
            style="width: 100%"
            class="login-button"
          >
            <span v-if="!loading">{{ t('login.submit') }}</span>
            <span v-else>{{ t('login.submitting') }}</span>
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const loginAttempted = ref(false)
const loginFormRef = ref<FormInstance | null>(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = reactive<FormRules<typeof loginForm>>({
  username: [
    { required: true, message: t('login.usernameRequired'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('login.passwordRequired'), trigger: 'blur' }
  ]
})

const handleLogin = async () => {
  
  // 标记已尝试登录
  loginAttempted.value = true
  
  if (!loginFormRef.value) return
  
  try {
    await loginFormRef.value.validate()
    loading.value = true
    
    // 调用用户 store 的登录方法
    const response = await userStore.login(loginForm.username, loginForm.password)
    
    // 跳转到仪表板 (将使用Layout组件)
    router.push({ name: 'dashboard-main' })
  } catch (error: any) {
    console.error('登录失败:', error)
    // 显示错误消息给用户
    ElMessage.error(error.message || t('login.failed'))
  } finally {
    loading.value = false
  }
}

// 页面加载时检查是否已经登录
onMounted(() => {
  if (userStore.isAuthenticated()) {
    // 如果已经登录，直接跳转到仪表板
    router.push({ name: 'dashboard-main' })
  }
})
</script>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 1rem;
}

.login-form {
  width: 100%;
  max-width: 400px;
  border: none;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  padding: 2rem;
}

.login-header {
  margin-bottom: 2rem;
}

.logo-placeholder {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.logo-icon {
  width: 60px;
  height: 60px;
}

.login-title {
  color: #303133;
  font-size: 2rem;
  font-weight: 600;
  margin: 0.5rem 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-subtitle {
  color: #606266;
  font-size: 1rem;
  margin: 0;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
}

.login-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  font-weight: 500;
  letter-spacing: 1px;
  transition: all 0.3s ease;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

:deep(.el-alert) {
  border-radius: 0.5rem;
}
</style>
