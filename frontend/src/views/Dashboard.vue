<template>
  <div class="dashboard">
    <el-card>
      <template #header>
        <h1>{{ t('dashboard.title') }}</h1>
      </template>
      <p>{{ t('dashboard.welcome') }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped>
.dashboard {
  padding: 20px;
}
</style>