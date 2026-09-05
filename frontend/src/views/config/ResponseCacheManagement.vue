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

    <!-- 配置信息展示 -->
    <el-card v-if="status" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">缓存配置</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="跳过流式请求">
          <el-tag :type="status.skipStreaming ? 'success' : 'info'" size="small">
            {{ status.skipStreaming ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="仅缓存确定性请求">
          <el-tag :type="status.onlyDeterministic ? 'success' : 'info'" size="small">
            {{ status.onlyDeterministic ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="配置项路径">
          <code>jairouter.response-cache.*</code>
        </el-descriptions-item>
      </el-descriptions>
      <div class="config-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>UI 仅展示与失效，配置持久化请修改 YAML 文件。</span>
      </div>
    </el-card>

    <!-- 操作区 -->
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
        <p style="margin: 0">缓存命中不写调用历史。配置项参考 YAML：<code>jairouter.response-cache.*</code>（UI 仅展示与失效，不提供配置持久化）。</p>
      </template>
    </el-alert>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Delete, InfoFilled, CircleCheck, CircleClose, Timer, Box, DataLine } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import { getCacheStatus, invalidateCache, type CacheStatus } from '@/api/responseCache'

const loading = ref(false)
const invalidating = ref(false)
const status = ref<CacheStatus | null>(null)
const selectedServiceType = ref('')
const modelName = ref('')

/** 后端 ServiceType 枚举值 */
const serviceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

const loadStatus = async () => {
  loading.value = true
  try {
    status.value = await getCacheStatus()
  } catch {
    status.value = null
  } finally {
    loading.value = false
  }
}

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
  } catch (e) {
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
  } catch (e) {
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
  } catch (e) {
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
</style>
