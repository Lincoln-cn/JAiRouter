<template>
  <div class="loading-indicator">
    <div
      v-if="type === 'dots'"
      class="loading-dots"
    >
      <span></span>
      <span></span>
      <span></span>
    </div>
    <div
      v-else-if="type === 'spinner'"
      class="loading-spinner"
    >
      <el-icon
        class="is-loading"
        :size="size"
      >
        <Loading />
      </el-icon>
    </div>
    <div
      v-else-if="type === 'progress'"
      class="loading-progress"
    >
      <el-progress
        :percentage="progress"
        :status="progressStatus"
        :stroke-width="4"
      />
      <span
        v-if="text"
        class="loading-text"
      >{{ text }}</span>
    </div>
    <div
      v-else-if="type === 'skeleton'"
      class="loading-skeleton"
    >
      <el-skeleton
        :rows="rows"
        animated
      />
    </div>
    <div
      v-if="text && type !== 'progress'"
      class="loading-text"
    >
      {{ text }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'

interface Props {
  type?: 'dots' | 'spinner' | 'progress' | 'skeleton'
  text?: string
  size?: number
  progress?: number
  rows?: number
}

const props = withDefaults(defineProps<Props>(), {
  type: 'dots',
  text: '',
  size: 24,
  progress: 0,
  rows: 3
})

const progressStatus = computed(() => {
  if (props.progress >= 100) return 'success'
  return undefined
})
</script>

<style scoped>
.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  gap: 12px;
}

.loading-dots {
  display: flex;
  gap: 6px;
}

.loading-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.loading-spinner {
  color: var(--el-color-primary);
}

.loading-progress {
  width: 100%;
  max-width: 300px;
}

.loading-skeleton {
  width: 100%;
}

.loading-text {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>