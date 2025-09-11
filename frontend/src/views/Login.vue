<template>
  <div class="login-container">
    <el-card class="login-form">
      <div class="login-header text-center mb-4">
        <h1>{{ t('login.title') }}</h1>
        <p class="text-muted">{{ t('login.subtitle') }}</p>
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
            :placeholder="t('login.username')"
            size="large"
          />
        </el-form-item>
        
        <el-form-item :label="t('login.password')" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="t('login.password')"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            size="large"
            style="width: 100%"
          >
            {{ loading ? t('login.submit') + '中...' : t('login.submit') }}
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

// 添加当前时间用于调试
const currentTime = ref(new Date().toLocaleTimeString())
const loginAttempted = ref(false)

// 每秒更新时间
setInterval(() => {
  currentTime.value = new Date().toLocaleTimeString()
}, 1000)

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
  // 添加调试信息
  console.log('登录按钮被点击')
  console.log('表单数据:', loginForm)
  
  // 标记已尝试登录
  loginAttempted.value = true
  
  if (!loginFormRef.value) return
  
  try {
    await loginFormRef.value.validate()
    loading.value = true
    
    // 调用用户 store 的登录方法
    const response = await userStore.login(loginForm.username, loginForm.password)
    
    console.log(t('login.success'), response)
    
    // 跳转到仪表板
    router.push('/dashboard')
  } catch (error: any) {
    console.error('登录失败:', error)
    // 显示错误消息给用户
    ElMessage.error(error.message || t('login.failed'))
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 1rem;
}

.login-form {
  width: 100%;
  max-width: 400px;
  border: none;
}

.login-header h1 {
  color: #303133;
}

:deep(.el-alert) {
  border-radius: 0.5rem;
}
</style>