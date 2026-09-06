<template>
  <el-config-provider :locale="epLocale">
    <div id="app">
      <router-view />
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useUserStore } from '@/stores/user'
import { useLocale } from '@/composables/useLocale'

const route = useRoute()
const userStore = useUserStore()

// v2.10.3: Element Plus 组件语言随应用语言切换（分页/表格/日期等内置文案）
const { locale } = useLocale()
const epLocale = computed(() => (locale.value === 'en-US' ? en : zhCn))

// 在应用启动时检查是否需要启动令牌刷新
onMounted(() => {
  if (userStore.isAuthenticated()) {
    // 启动定时刷新令牌
    userStore.startTokenRefresh()
  }
})
</script>

<style>
#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100vh;
  margin: 0;
  padding: 0;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
}
</style>
