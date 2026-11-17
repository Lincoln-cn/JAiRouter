<template>
  <PageSkeleton :title="t('slaAccess.pageTitle')">
    <template #actions>
      <el-button
        v-permission="'system:sla:manage'"
        type="primary"
        size="default"
        @click="openCreate"
      >
        <el-icon><Plus /></el-icon>
        {{ t('slaAccess.create') }}
      </el-button>
    </template>

    <template #stats>
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <template #toolbar>
      <!-- 工具栏顺序：筛选 → 刷新（导出类次要动作放这里） -->
      <el-input
        v-model="keyword"
        :placeholder="t('slaAccess.searchPlaceholder')"
        clearable
        style="max-width: 260px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button @click="loadAccounts" :loading="loading">
        <el-icon><Refresh /></el-icon>
        {{ t('slaAccess.reload') }}
      </el-button>
    </template>

    <el-alert
      :title="t('slaAccess.boundaryTitle')"
      :description="t('slaAccess.boundaryDesc')"
      type="info"
      :closable="false"
      show-icon
      class="boundary-alert"
    />

    <el-card shadow="never" class="collection-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('slaAccess.accountsTitle') }}</span>
          <span class="card-hint">{{ t('slaAccess.accountsHint') }}</span>
        </div>
      </template>

      <el-table :data="filteredAccounts" v-loading="loading" size="small">
        <template #empty>
          <EmptyState
            :title="keyword ? t('slaAccess.noMatchTitle') : t('slaAccess.empty')"
            :description="keyword ? t('slaAccess.noMatchHint') : t('slaAccess.emptyHint')"
          >
            <template v-if="!keyword" #actions>
              <el-button
                v-permission="'system:sla:manage'"
                type="primary"
                size="small"
                @click="openCreate"
              >
                {{ t('slaAccess.create') }}
              </el-button>
            </template>
          </EmptyState>
        </template>
        <el-table-column prop="name" :label="t('slaAccess.colName')" min-width="150" />
        <el-table-column :label="t('slaAccess.colTemplate')" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleTemplate }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('slaAccess.colRefreshTokens')" width="110" align="center">
          <template #default="{ row }">{{ row.activeRefreshTokens }}</template>
        </el-table-column>
        <el-table-column :label="t('slaAccess.colBootstrapTokens')" width="110" align="center">
          <template #default="{ row }">{{ row.unusedBootstrapTokens }}</template>
        </el-table-column>
        <el-table-column :label="t('slaAccess.colLastSeen')" min-width="170">
          <template #default="{ row }">
            <TimeText v-if="row.lastSeenAt" :value="row.lastSeenAt" />
            <span v-else class="muted">{{ t('slaAccess.neverUsed') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('slaAccess.colCreator')" min-width="110">
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('slaAccess.colActions')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:sla:manage'"
              link
              type="primary"
              size="small"
              @click="openIssue(row)"
            >
              {{ t('slaAccess.issue') }}
            </el-button>
            <ConfirmAction
              v-permission="'system:sla:manage'"
              :title="t('slaAccess.revokeConfirm')"
              :width="280"
              :confirm-text="t('slaAccess.revoke')"
              type="warning"
              @confirm="handleRevoke(row)"
            >
              <el-button link type="warning" size="small">{{ t('slaAccess.revoke') }}</el-button>
            </ConfirmAction>
            <ConfirmAction
              v-permission="'system:sla:manage'"
              :title="t('slaAccess.deleteConfirm')"
              :width="260"
              :confirm-text="t('slaAccess.remove')"
              type="danger"
              @confirm="handleDelete(row)"
            >
              <el-button link type="danger" size="small">{{ t('slaAccess.remove') }}</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建账号：集合页统一用右侧抽屉（与账户管理/黑名单一致） -->
    <el-drawer
      v-model="createVisible"
      :title="t('slaAccess.createTitle')"
      :size="drawerSize"
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-form-item :label="t('slaAccess.createName')">
          <el-input
            v-model="createForm.name"
            :placeholder="t('slaAccess.createNamePlaceholder')"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('slaAccess.createTemplate')">
          <el-select v-model="createForm.roleTemplate" style="width: 100%">
            <el-option value="SLA_READONLY" :label="t('slaAccess.templateReadonly')" />
            <el-option value="SLA_OPERATOR" :label="t('slaAccess.templateOperator')" />
          </el-select>
        </el-form-item>
        <p class="field-hint">{{ t('slaAccess.createRoleHint') }}</p>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('slaAccess.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          {{ t('slaAccess.createConfirm') }}
        </el-button>
      </template>
    </el-drawer>

    <!-- 签发一次性令牌：同样是抽屉；结果（明文只显示一次）留在抽屉内 -->
    <el-drawer
      v-model="issueVisible"
      :title="t('slaAccess.issueTitle')"
      :size="drawerSize"
      :close-on-click-modal="false"
      @closed="closeIssue"
    >
      <template v-if="!issued">
        <p class="dialog-desc">{{ t('slaAccess.issueDesc') }}</p>
        <el-form label-width="130px">
          <el-form-item :label="t('slaAccess.issueTtl')">
            <el-input-number v-model="issueForm.ttlMinutes" :min="1" :max="1440" />
            <span class="field-hint">{{ t('slaAccess.issueTtlHint') }}</span>
          </el-form-item>
          <el-form-item :label="t('slaAccess.issueIp')">
            <el-input
              v-model="issueForm.allowedIp"
              :placeholder="t('slaAccess.issueIpPlaceholder')"
              maxlength="64"
            />
          </el-form-item>
        </el-form>
      </template>

      <template v-else>
        <el-alert
          :title="t('slaAccess.issueResultTitle')"
          :description="t('slaAccess.issueResultDesc')"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <div class="token-box">
          <code class="token-value">{{ issued.bootstrapToken }}</code>
        </div>
        <p class="field-hint">
          {{ t('slaAccess.expiresAt') }}：{{ issued.expiresAt }}
        </p>
        <el-button type="primary" plain size="small" @click="copyToken">
          <el-icon><CopyDocument /></el-icon>
          {{ t('slaAccess.copyToken') }}
        </el-button>
      </template>

      <template #footer>
        <el-button @click="issueVisible = false">
          {{ issued ? t('slaAccess.close') : t('slaAccess.cancel') }}
        </el-button>
        <el-button v-if="!issued" type="primary" :loading="issuing" @click="handleIssue">
          {{ t('slaAccess.issueConfirm') }}
        </el-button>
      </template>
    </el-drawer>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import TimeText from '@/components/TimeText.vue'
import {
  createSlaAccount,
  deleteSlaAccount,
  getSlaAccounts,
  issueSlaBootstrap,
  revokeSlaCredentials,
  type SlaAccount,
  type SlaBootstrapIssue
} from '@/api/sla'

/**
 * 系统 → SLA 接入（#1174）
 *
 * 页面只做三件事：看有哪些接入账号、签发一次性令牌（明文只显示一次）、吊销/删除。
 * 真正的换凭据动作发生在 SLA 部署侧（`POST /api/auth/sla/bootstrap` 与 `/refresh`），
 * 那些端点匿名，不需要管理员在场。
 *
 * 版式（2026-11-18 统一列表页口径）：PageSkeleton + MetricStrip 统计条 + 工具栏（筛选 →
 * 刷新）+ 表格空态 EmptyState + 右侧抽屉编辑；危险操作一律 ConfirmAction。
 */
const { t } = useI18n()

const drawerSize = '640px'

const accounts = ref<SlaAccount[]>([])
const loading = ref(false)
const keyword = ref('')

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', roleTemplate: 'SLA_READONLY' })

const issueVisible = ref(false)
const issuing = ref(false)
const issueTarget = ref<SlaAccount | null>(null)
const issueForm = reactive({ ttlMinutes: 15, allowedIp: '' })
const issued = ref<SlaBootstrapIssue | null>(null)

const filteredAccounts = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return accounts.value
  }
  return accounts.value.filter((item) => item.name.toLowerCase().includes(kw))
})

const metricItems = computed(() => [
  { key: 'accounts', label: t('slaAccess.metricAccounts'), value: accounts.value.length },
  {
    key: 'enabled',
    label: t('slaAccess.metricEnabled'),
    value: accounts.value.filter((item) => item.enabled).length
  },
  {
    key: 'refreshTokens',
    label: t('slaAccess.metricRefreshTokens'),
    value: accounts.value.reduce((sum, item) => sum + item.activeRefreshTokens, 0)
  },
  {
    key: 'pendingTokens',
    label: t('slaAccess.metricPendingTokens'),
    value: accounts.value.reduce((sum, item) => sum + item.unusedBootstrapTokens, 0)
  }
])

/** 从 unknown 里取后端提示（axios 拦截器已把 RouterResponse.message 提到 error.message） */
function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

async function loadAccounts(): Promise<void> {
  loading.value = true
  try {
    accounts.value = await getSlaAccounts()
  } catch (error) {
    ElMessage.error(t('slaAccess.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  createForm.name = ''
  createForm.roleTemplate = 'SLA_READONLY'
  createVisible.value = true
}

async function handleCreate(): Promise<void> {
  const name = createForm.name.trim()
  if (!name) {
    ElMessage.warning(t('slaAccess.nameRequired'))
    return
  }
  creating.value = true
  try {
    await createSlaAccount({ name, roleTemplate: createForm.roleTemplate })
    ElMessage.success(t('slaAccess.createSuccess'))
    createVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error(errorMessage(error, t('slaAccess.createFailed')))
  } finally {
    creating.value = false
  }
}

function openIssue(account: SlaAccount): void {
  issueTarget.value = account
  issueForm.ttlMinutes = 15
  issueForm.allowedIp = ''
  issued.value = null
  issueVisible.value = true
}

function closeIssue(): void {
  issued.value = null
  issueTarget.value = null
  void loadAccounts()
}

async function handleIssue(): Promise<void> {
  if (!issueTarget.value) return
  issuing.value = true
  try {
    const payload: { ttlMinutes?: number; allowedIp?: string } = {}
    if (issueForm.ttlMinutes) {
      payload.ttlMinutes = issueForm.ttlMinutes
    }
    const ip = issueForm.allowedIp.trim()
    if (ip) {
      payload.allowedIp = ip
    }
    issued.value = await issueSlaBootstrap(issueTarget.value.accountId, payload)
    ElMessage.success(t('slaAccess.issueSuccess'))
  } catch (error) {
    ElMessage.error(errorMessage(error, t('slaAccess.issueFailed')))
  } finally {
    issuing.value = false
  }
}

async function copyToken(): Promise<void> {
  if (!issued.value) return
  try {
    await navigator.clipboard.writeText(issued.value.bootstrapToken)
    ElMessage.success(t('slaAccess.copySuccess'))
  } catch {
    ElMessage.error(t('slaAccess.copyFailed'))
  }
}

async function handleRevoke(account: SlaAccount): Promise<void> {
  try {
    const revoked = await revokeSlaCredentials(account.accountId)
    ElMessage.success(t('slaAccess.revokeSuccess', { n: revoked }))
    await loadAccounts()
  } catch (error) {
    ElMessage.error(errorMessage(error, t('slaAccess.revokeFailed')))
  }
}

async function handleDelete(account: SlaAccount): Promise<void> {
  try {
    await deleteSlaAccount(account.accountId)
    ElMessage.success(t('slaAccess.deleteSuccess'))
    await loadAccounts()
  } catch (error) {
    ElMessage.error(errorMessage(error, t('slaAccess.deleteFailed')))
  }
}

onMounted(loadAccounts)
</script>

<style scoped>
.boundary-alert {
  margin-bottom: var(--ja-space-4, 16px);
}

.collection-card {
  border-radius: var(--ja-radius, 8px);
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ja-ink);
}

.card-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.muted {
  color: var(--el-text-color-placeholder);
}

.dialog-desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.field-hint {
  display: block;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.token-box {
  padding: 10px 12px;
  margin-bottom: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  word-break: break-all;
}

.token-value {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 13px;
}
</style>
