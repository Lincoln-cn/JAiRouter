<template>
  <PageSkeleton :title="title">
    <template #actions>
      <slot name="actions" />
      <el-button :loading="loading" @click="handleRefresh">
        <el-icon><Refresh /></el-icon>
        <span>{{ t('configShell.refresh') }}</span>
      </el-button>
    </template>

    <template v-if="description || $slots.toolbar" #toolbar>
      <div class="config-toolbar">
        <p v-if="description" class="config-desc">{{ description }}</p>
        <slot name="toolbar" />
      </div>
    </template>

    <slot />

    <div class="config-action-bar" :class="{ 'is-dirty': dirtyCount > 0 }">
      <span class="action-state" :class="stateClass">
        <el-icon v-if="saving"><Loading /></el-icon>
        {{ stateText }}
      </span>
      <div v-if="showActions" class="action-buttons">
        <el-button :disabled="saving || dirtyCount === 0" @click="handleDiscard">
          {{ t('configShell.discardChanges') }}
        </el-button>
        <el-button
          v-if="canWrite"
          v-permission="savePermission"
          type="primary"
          :loading="saving"
          :disabled="dirtyCount === 0"
          @click="emit('save')"
        >
          <el-icon><Check /></el-icon>
          <span>{{ saveLabel || t('common.save') }}</span>
        </el-button>
        <span v-else class="readonly-hint">{{ t('configShell.readonlyHint') }}</span>
      </div>
    </div>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Check, Loading, Refresh } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  loading?: boolean
  saving?: boolean
  /** 正在保存的分区名（来自 useConfigDraft.savingLabel） */
  savingLabel?: string
  /** 未保存的分区数（来自 useConfigDraft.dirtyCount） */
  dirtyCount?: number
  canWrite?: boolean
  /** 写操作权限码，例如 'monitoring:config:write' */
  savePermission?: string
  saveLabel?: string
  /**
   * 是否渲染页脚的保存/放弃动作条。纯即时生效页面（例如健康探针，两个开关一拨就写库）
   * 没有草稿可提交，传 false 只保留状态行，避免出现永远禁用的保存按钮。
   */
  showActions?: boolean
}>(), {
  description: '',
  loading: false,
  saving: false,
  savingLabel: '',
  dirtyCount: 0,
  canWrite: false,
  savePermission: '',
  saveLabel: '',
  showActions: true
})

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'save'): void
  (e: 'discard'): void
}>()

const { t } = useI18n()

const stateText = computed(() => {
  if (props.saving) {
    return props.savingLabel
      ? t('configShell.savingSection', { label: props.savingLabel })
      : t('configShell.saving')
  }
  if (props.dirtyCount > 0) {
    return t('configShell.unsavedCount', { count: props.dirtyCount })
  }
  return t('configShell.allSaved')
})

const stateClass = computed(() => {
  if (props.saving) {
    return 'is-saving'
  }
  return props.dirtyCount > 0 ? 'is-dirty' : 'is-clean'
})

/** 有脏字段时，刷新与放弃都要确认 —— 两者都会丢修改。 */
const confirmDiscard = async (message: string): Promise<boolean> => {
  try {
    await ElMessageBox.confirm(message, t('configShell.discardChanges'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    })
    return true
  } catch {
    return false
  }
}

const handleRefresh = async (): Promise<void> => {
  if (props.dirtyCount > 0) {
    const ok = await confirmDiscard(t('configShell.refreshDirtyConfirm', { count: props.dirtyCount }))
    if (!ok) {
      return
    }
  }
  emit('refresh')
}

const handleDiscard = async (): Promise<void> => {
  const ok = await confirmDiscard(t('configShell.discardConfirm'))
  if (ok) {
    emit('discard')
  }
}

const beforeUnload = (event: BeforeUnloadEvent): void => {
  if (props.dirtyCount === 0) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
})

onBeforeRouteLeave(async () => {
  if (props.dirtyCount === 0) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      t('configShell.leaveMessage', { count: props.dirtyCount }),
      t('configShell.leaveTitle'),
      {
        type: 'warning',
        confirmButtonText: t('configShell.leaveConfirm'),
        cancelButtonText: t('configShell.leaveCancel')
      }
    )
    return true
  } catch {
    return false
  }
})
</script>

<style scoped>
.config-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.config-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

/* 动作条贴底：配置页会越滚越长，保存入口必须一直在视野内。
   必须放在正文插槽里（而不是 PageSkeleton 的 #footer）：sticky 会被父盒边界推回，
   而 .page-footer 只有自身高度，黏附范围因此只有几十像素，滚动后保存入口就看不到了。 */
.config-action-bar {
  position: sticky;
  bottom: 0;
  margin-top: 16px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ja-radius-md, 8px);
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  backdrop-filter: blur(6px);
  box-shadow: 0 -2px 12px rgb(0 0 0 / 6%);
}

.config-action-bar.is-dirty {
  border-color: var(--el-color-warning-light-5);
}

.action-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.action-state.is-dirty {
  color: var(--el-color-warning);
}

.action-state.is-saving {
  color: var(--el-color-primary);
}

.action-state.is-clean {
  color: var(--el-text-color-secondary);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.readonly-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 767px) {
  .config-action-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .action-buttons {
    justify-content: flex-end;
  }
}
</style>
