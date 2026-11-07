<template>
  <el-card v-if="!loading" class="gs-card" :class="{ 'gs-done': allDone }" shadow="never">
    <div class="gs-head">
      <div>
        <div class="gs-title">{{ allDone ? t('dashboard.gsTitleDone') : t('dashboard.gsTitle') }}</div>
        <div class="gs-subtitle">{{ allDone ? t('dashboard.gsAllDone') : t('dashboard.gsSubtitle') }}</div>
      </div>
      <el-tag v-if="allDone" type="success" effect="light" size="small">OK</el-tag>
    </div>

    <div class="gs-steps">
      <div v-for="step in steps" :key="step.key" class="gs-step" :class="step.state">
        <span class="gs-dot" />
        <div class="gs-body">
          <div class="gs-step-title">{{ step.title }}</div>
          <div class="gs-step-desc">{{ step.desc }}</div>
        </div>
        <el-button v-if="step.actionText" size="small" text type="primary" @click="step.action">
          {{ step.actionText }} →
        </el-button>
      </div>
    </div>

    <!-- 完成前：给出「下一步去哪」，但不做成第四步（它不是配置动作，是使用动作） -->
    <div v-if="!allDone" class="gs-foot">
      <span class="gs-foot-text">{{ t('dashboard.gsStepCallDesc') }}</span>
      <el-button size="small" text type="primary" @click="router.push('/playground/chat')">
        {{ t('dashboard.gsActionPlayground') }} →
      </el-button>
    </div>

    <div v-else class="gs-curl">
      <div class="gs-curl-hint">{{ t('dashboard.gsCurlHint') }}</div>
      <div class="gs-curl-box">
        <code>{{ curlText }}</code>
        <el-button size="small" text @click="copyCurl">{{ copied ? '已复制' : '复制' }}</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getApiKeys } from '@/api/apiKey'
import { getServiceStats } from '@/api/dashboard'

const { t } = useI18n()
const router = useRouter()

const loading = ref(true)
const instanceCount = ref(0)
const apiKeyCount = ref(0)
const copied = ref(false)

// 「服务在线」不做真实探测：页面能加载出这张卡片，本身就说明后端在响应。
// 真正的链路检查由运维侧的诊断工具负责（不随本仓分发），这里只做「配置是否齐备」的判断。
const serviceOk = ref(true)

const instanceOk = computed(() => instanceCount.value > 0)
const keyOk = computed(() => apiKeyCount.value > 0)
const allDone = computed(() => instanceOk.value && keyOk.value)

const steps = computed(() => [
  {
    key: 'service',
    state: serviceOk.value ? 'ok' : 'bad',
    title: t('dashboard.gsStepService'),
    desc: serviceOk.value ? t('dashboard.gsStepServiceOk') : t('dashboard.gsStepServiceFail'),
    actionText: '',
    action: () => {},
  },
  {
    key: 'instance',
    state: instanceOk.value ? 'ok' : 'bad',
    title: t('dashboard.gsStepInstance'),
    desc: instanceOk.value
      ? t('dashboard.gsStepInstanceOk', { count: instanceCount.value })
      : t('dashboard.gsStepInstanceFail'),
    actionText: instanceOk.value ? '' : t('dashboard.gsActionConfig'),
    action: () => router.push('/config/instances'),
  },
  {
    key: 'key',
    state: keyOk.value ? 'ok' : 'bad',
    title: t('dashboard.gsStepKey'),
    desc: keyOk.value ? t('dashboard.gsStepKeyOk') : t('dashboard.gsStepKeyFail'),
    actionText: keyOk.value ? '' : t('dashboard.gsActionApiKey'),
    action: () => router.push('/security/api-keys'),
  },
])

const curlText = computed(
  () =>
    `curl ${window.location.origin.replace(/:\d+$/, ':8080')}/v1/chat/completions \\\n` +
    `  -H "Authorization: Bearer <你的 API Key>" \\\n` +
    `  -d '{"model":"<实例名>","messages":[{"role":"user","content":"你好"}]}'`,
)

async function copyCurl() {
  try {
    await navigator.clipboard.writeText(curlText.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}

onMounted(async () => {
  const [stats, keys] = await Promise.all([
    getServiceStats().catch(() => null),
    getApiKeys().catch(() => null),
  ])
  instanceCount.value = stats?.data?.data?.totalInstances ?? 0
  apiKeyCount.value = keys?.items?.length ?? 0
  loading.value = false
})
</script>

<style scoped>
.gs-card {
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-lighter);
}
.gs-card.gs-done {
  border-color: var(--el-color-success-light-5);
}
.gs-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.gs-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.gs-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.gs-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.gs-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.gs-step.ok {
  background: var(--el-color-success-light-9);
}
.gs-step.bad {
  background: var(--el-color-warning-light-9);
}
.gs-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--el-color-info);
}
.gs-step.ok .gs-dot {
  background: var(--el-color-success);
}
.gs-step.bad .gs-dot {
  background: var(--el-color-warning);
}
.gs-body {
  flex: 1;
  min-width: 0;
}
.gs-step-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.gs-step-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.gs-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.gs-foot-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.gs-curl {
  margin-top: 14px;
}
.gs-curl-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.gs-curl-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-darker);
}
.gs-curl-box code {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
