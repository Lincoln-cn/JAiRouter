<template>
  <div class="onboarding-steps">
    <div
      v-for="step in steps"
      :key="step.index"
      class="step-item"
      :class="{
        'step--active': step.index === currentStep,
        'step--completed': step.index < currentStep,
        'step--pending': step.index > currentStep
      }"
      @click="navigate(step)"
    >
      <span class="step-index">
        <el-icon v-if="step.index < currentStep" class="step-check"><Check /></el-icon>
        <template v-else>{{ step.index }}</template>
      </span>
      <span class="step-label">{{ step.label }}</span>
      <el-icon v-if="step.index < steps.length" class="step-arrow"><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Check, ArrowRight } from '@element-plus/icons-vue'
import { playgroundTargetRoute } from '@/composables/useRoutePreselect'

const props = defineProps<{
  /** 当前步骤 1-based */
  currentStep: number
  /** 当前页面的服务类型，用于 steps 3/4 的 query 传递 */
  serviceType?: string
}>()

const router = useRouter()
const { t } = useI18n()

interface StepItem {
  index: number
  label: string
  route: string
}

const steps = computed<StepItem[]>(() => [
  { index: 1, label: t('onboarding.configureAdapter'), route: '/config/adapters' },
  { index: 2, label: t('onboarding.createService'), route: '/config/services' },
  { index: 3, label: t('onboarding.addInstance'), route: '/config/instances' },
  { index: 4, label: t('onboarding.tryPlayground'), route: '/playground/chat' }
])

function navigate(step: StepItem) {
  if (step.index === 4 && props.serviceType) {
    const target = playgroundTargetRoute(props.serviceType) ?? { name: 'playground-chat' }
    router.push({ name: target.name, query: { serviceType: props.serviceType } })
    return
  }
  const target: { path: string; query?: Record<string, string> } = { path: step.route }
  if (step.index === 3 && props.serviceType) {
    target.query = { serviceType: props.serviceType }
  }
  router.push(target)
}
</script>

<style scoped>
.onboarding-steps {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--ja-space-2, 8px);
  padding: var(--ja-space-3, 12px) var(--ja-space-4, 16px);
  background: var(--ja-bg-card, #fff);
  border: 1px solid var(--ja-border-lighter, #f2f6fc);
  border-radius: var(--ja-radius, 8px);
  box-shadow: var(--ja-shadow-sm);
}

.step-item {
  display: inline-flex;
  align-items: center;
  gap: var(--ja-space-1, 4px);
  cursor: pointer;
  padding: var(--ja-space-1, 4px) var(--ja-space-2, 8px);
  border-radius: var(--ja-radius-sm, 4px);
  transition: background 0.2s, color 0.2s;
  user-select: none;
  white-space: nowrap;
}

.step-item:hover {
  background: var(--ja-primary-light-9, #ecf5ff);
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.step-label {
  font-size: 13px;
  font-weight: 500;
}

.step-arrow {
  font-size: 12px;
  color: var(--ja-text-placeholder, #a8abb2);
  margin-left: var(--ja-space-1, 4px);
}

/* --- Pending --- */
.step--pending .step-index {
  background: var(--ja-border-lighter, #f2f6fc);
  color: var(--ja-text-secondary, #909399);
}

.step--pending .step-label {
  color: var(--ja-text-secondary, #909399);
}

/* --- Active (current) --- */
.step--active .step-index {
  background: var(--ja-primary, #409eff);
  color: #fff;
}

.step--active .step-label {
  color: var(--ja-primary, #409eff);
  font-weight: 700;
}

.step--active:hover {
  background: var(--ja-primary-light-9, #ecf5ff);
}

/* --- Completed --- */
.step--completed .step-index {
  background: var(--ja-success, #67c23a);
  color: #fff;
}

.step--completed .step-label {
  color: var(--ja-success, #67c23a);
}

.step--completed:hover {
  background: rgba(103, 194, 58, 0.08);
}

.step-check {
  font-size: 14px;
}
</style>
