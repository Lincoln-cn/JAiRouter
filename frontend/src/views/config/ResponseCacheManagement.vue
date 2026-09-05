<template>
  <PageSkeleton title="响应缓存管理">
    <template #actions>
      <el-button @click="loadStatus" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        刷新状态
      </el-button>
    </template>

    <!-- 顶部状态卡片 -->
    <template #stats>
      <el-row :gutter="16">
        <el-col :span="6">
          <StatCard
            :icon="status?.enabled ? 'CircleCheck' : 'CircleClose'"
            label="缓存状态"
            :value="status?.enabled ? '已启用' : '已禁用'"
            :tone="status?.enabled ? 'success' : 'info'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Timer"
            label="TTL"
            :value="status?.ttlSeconds ?? '—'"
            unit="秒"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Box"
            label="最大条目数"
            :value="status?.maxSize ?? '—'"
            tone="warning"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="DataLine"
            label="当前条目数"
            :value="status?.size ?? '—'"
            tone="info"
          />
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="8">
          <StatCard
            icon="SuccessFilled"
            label="命中次数"
            :value="status?.hits ?? '—'"
            tone="success"
          />
        </el-col>
        <el-col :span="8">
          <StatCard
            icon="CircleCloseFilled"
            label="未命中"
            :value="status?.misses ?? '—'"
            tone="danger"
          />
        </el-col>
        <el-col :span="8">
          <StatCard
            icon="TrendCharts"
            label="命中率"
            :value="hitRatioDisplay"
            tone="primary"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 状态未就绪提示 -->
    <el-alert
      v-if="!status"
      title="缓存状态暂未就绪"
      description="后端缓存状态端点未返回数据（可能 404），请确认服务已启动且版本 ≥ v2.10.2"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 运行时配置面板 -->
    <el-card v-if="status" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">运行时配置</span>
      </template>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">启用缓存</span>
          <el-switch v-model="runtimeForm.enabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">跳过流式请求</span>
          <el-switch v-model="runtimeForm.skipStreaming" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">仅缓存确定性请求</span>
          <el-switch v-model="runtimeForm.onlyDeterministic" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">TTL（秒）</span>
          <el-input-number
            v-model="runtimeForm.ttlSeconds"
            :min="1"
            :max="604800"
            :step="60"
            controls-position="right"
            placeholder="留空不修改"
            style="width: 180px"
          />
        </div>
      </div>

      <div class="config-actions">
        <el-button
          type="primary"
          :loading="savingConfig"
          :disabled="!configChanged"
          @click="handleSaveConfig"
        >
          <el-icon><Check /></el-icon>
          保存运行时配置
        </el-button>
        <el-button
          :disabled="savingConfig"
          @click="resetRuntimeForm"
        >
          重置
        </el-button>
      </div>

      <div class="config-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>运行时即时生效但重启/配置刷新后还原为 yaml <code>jairouter.response-cache.*</code></span>
      </div>
      <div class="config-hint">
        <el-icon><InfoFilled /></el-icon>
        <span><code>maxSize</code> 只读，调整需改 yaml 后重启</span>
      </div>
    </el-card>

    <!-- 操作区：缓存失效 -->
    <el-card shadow="hover">
      <template #header>
        <span class="card-title">缓存失效操作</span>
      </template>

      <!-- 清空全部 -->
      <div class="action-section">
        <div class="action-label">清空全部缓存</div>
        <el-popconfirm
          title="确认清空全部响应缓存？"
          confirm-button-text="确认"
          cancel-button-text="取消"
          @confirm="handleInvalidateAll"
        >
          <template #reference>
            <el-button type="danger" :loading="invalidating" :disabled="!status?.enabled">
              <el-icon><Delete /></el-icon>
              清空全部缓存
            </el-button>
          </template>
        </el-popconfirm>
      </div>

      <el-divider />

      <!-- 按服务类型失效 -->
      <div class="action-section">
        <div class="action-label">按服务类型失效</div>
        <div class="action-row">
          <el-select
            v-model="selectedServiceType"
            placeholder="选择服务类型"
            style="width: 200px"
            clearable
          >
            <el-option
              v-for="st in serviceTypes"
              :key="st"
              :label="st"
              :value="st"
            />
          </el-select>
          <el-button
            type="warning"
            :loading="invalidating"
            :disabled="!selectedServiceType || !status?.enabled"
            @click="handleInvalidateByServiceType"
          >
            <el-icon><Delete /></el-icon>
            按服务失效
          </el-button>
        </div>
      </div>

      <el-divider />

      <!-- 按模型失效 -->
      <div class="action-section">
        <div class="action-label">按模型失效</div>
        <div class="action-row">
          <el-input
            v-model="modelName"
            placeholder="输入模型名称"
            style="width: 300px"
            clearable
          />
          <el-button
            type="warning"
            :loading="invalidating"
            :disabled="!modelName.trim() || !status?.enabled"
            @click="handleInvalidateByModel"
          >
            <el-icon><Delete /></el-icon>
            按模型失效
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 提示 -->
    <el-alert
      title="提示"
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    >
      <template #default>
        <p style="margin: 0">缓存命中不写调用历史。配置项参考 YAML：<code>jairouter.response-cache.*</code>。</p>
      </template>
    </el-alert>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  Delete,
  InfoFilled,
  CircleCheck,
  CircleClose,
  Timer,
  Box,
  DataLine,
  Check,
  SuccessFilled,
  CircleCloseFilled,
  TrendCharts,
} from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getCacheStatus,
  invalidateCache,
  updateCacheConfig,
  type CacheStatus,
  type CacheConfigPayload,
} from '@/api/responseCache'

const loading = ref(false)
const invalidating = ref(false)
const savingConfig = ref(false)
const status = ref<CacheStatus | null>(null)
const selectedServiceType = ref('')
const modelName = ref('')

/** 后端 ServiceType 枚举值 */
const serviceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

// ── 运行时配置表单 ──

interface RuntimeForm {
  enabled: boolean
  skipStreaming: boolean
  onlyDeterministic: boolean
  /** undefined = 未修改（不传），number = 用户修改后的值 */
  ttlSeconds: number | undefined
}

const runtimeForm = ref<RuntimeForm>({
  enabled: false,
  skipStreaming: false,
  onlyDeterministic: false,
  ttlSeconds: undefined,
})

/** 用当前 snapshot 填充运行时表单（同步基准值，不重新赋 ttlSeconds = undefined） */
const syncFormFromStatus = (s: CacheStatus) => {
  runtimeForm.value.enabled = s.enabled
  runtimeForm.value.skipStreaming = s.skipStreaming
  runtimeForm.value.onlyDeterministic = s.onlyDeterministic
  runtimeForm.value.ttlSeconds = undefined
}

/** 重置表单到 snapshot 原值 */
const resetRuntimeForm = () => {
  if (status.value) syncFormFromStatus(status.value)
}

/** 命中率显示 */
const hitRatioDisplay = computed(() => {
  if (!status.value || status.value.hitRatio === null) return '暂无数据'
  return `${(status.value.hitRatio * 100).toFixed(1)}%`
})

/**
 * 判断表单是否有变更，决定「保存」按钮是否可点。
 * 仅发送与 snapshot 不同的字段；未动字段不传。
 */
const configChanged = computed(() => {
  if (!status.value) return false
  const s = status.value
  if (runtimeForm.value.enabled !== s.enabled) return true
  if (runtimeForm.value.skipStreaming !== s.skipStreaming) return true
  if (runtimeForm.value.onlyDeterministic !== s.onlyDeterministic) return true
  if (runtimeForm.value.ttlSeconds !== undefined && runtimeForm.value.ttlSeconds !== s.ttlSeconds) return true
  return false
})

// ── 数据加载 ──

const loadStatus = async () => {
  loading.value = true
  try {
    const s = await getCacheStatus()
    status.value = s
    if (s) syncFormFromStatus(s)
  } catch {
    status.value = null
  } finally {
    loading.value = false
  }
}

// ── 保存运行时配置（diff → payload） ──

const handleSaveConfig = async () => {
  if (!status.value) return
  const s = status.value
  const payload: CacheConfigPayload = {}

  if (runtimeForm.value.enabled !== s.enabled) {
    payload.enabled = runtimeForm.value.enabled
  }
  if (runtimeForm.value.skipStreaming !== s.skipStreaming) {
    payload.skipStreaming = runtimeForm.value.skipStreaming
  }
  if (runtimeForm.value.onlyDeterministic !== s.onlyDeterministic) {
    payload.onlyDeterministic = runtimeForm.value.onlyDeterministic
  }
  if (runtimeForm.value.ttlSeconds !== undefined && runtimeForm.value.ttlSeconds !== s.ttlSeconds) {
    payload.ttlSeconds = runtimeForm.value.ttlSeconds
  }

  // 后端：全空 payload → 400
  if (Object.keys(payload).length === 0) {
    ElMessage.warning('未修改任何配置项')
    return
  }

  savingConfig.value = true
  try {
    await updateCacheConfig(payload)
    ElMessage.success('运行时配置已更新')
    await loadStatus()
  } catch {
    ElMessage.error('保存失败，请检查参数')
  } finally {
    savingConfig.value = false
  }
}

// ── 缓存失效操作 ──

const handleInvalidateAll = async () => {
  invalidating.value = true
  try {
    const result = await invalidateCache()
    if (result.executed) {
      ElMessage.success('缓存已清空')
    } else {
      ElMessage.warning('缓存未启用，操作未执行')
    }
    await loadStatus()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByServiceType = async () => {
  if (!selectedServiceType.value) return
  invalidating.value = true
  try {
    const result = await invalidateCache({ serviceType: selectedServiceType.value })
    if (result.executed) {
      ElMessage.success(`已按服务类型 [${selectedServiceType.value}] 失效缓存`)
    } else {
      ElMessage.warning('缓存未启用，操作未执行')
    }
    await loadStatus()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByModel = async () => {
  const model = modelName.value.trim()
  if (!model) return
  invalidating.value = true
  try {
    const result = await invalidateCache({ model })
    if (result.executed) {
      ElMessage.success(`已按模型 [${model}] 失效缓存`)
    } else {
      ElMessage.warning('缓存未启用，操作未执行')
    }
    await loadStatus()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    invalidating.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
}

.action-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--ja-text-primary);
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--ja-text-secondary, var(--el-text-color-secondary));
}

.runtime-config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 32px;
  margin-bottom: 16px;
}

.config-switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--ja-radius-md, 8px);
  background: var(--ja-bg-hover, var(--el-fill-color-light));
}

.config-switch-label {
  font-size: 14px;
  color: var(--ja-text-primary);
  font-weight: 500;
}

.config-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}
</style>
