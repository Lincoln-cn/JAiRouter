<template>
  <div class="exception-detail">
    <el-card class="detail-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="detail-title">
            <el-icon><Warning /></el-icon>
            {{ t('exception.detail.title') }}
          </span>
          <el-button icon="ArrowLeft" @click="goBack">{{ t('exception.detail.back') }}</el-button>
        </div>
      </template>

      <el-loading v-model="loading" :text="t('exception.detail.loading')" />

      <div v-if="eventData" class="detail-content">
        <!-- 基本信息 -->
        <el-divider content-position="left">{{ t('exception.detail.basicInfo') }}</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('exception.detail.eventId')">
            <el-tag effect="plain" type="info">{{ eventData.eventId }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.exceptionType')">
            <span class="mono-font">{{ eventData.exceptionType }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.errorCode')">
            <el-tag :type="getErrorTagType(eventData.errorCode)" size="small">
              {{ eventData.errorCode }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.errorCategory')">
            <el-tag :type="getCategoryTagType(eventData.errorCategory)" size="small">
              {{ formatCategory(eventData.errorCategory) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.httpStatus')">
            <el-tag :type="getHttpStatusTagType(eventData.httpStatus)" size="small">
              {{ eventData.httpStatus || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.operation')">{{ eventData.operation || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.clientIp')">
            <el-tag effect="plain" type="info">{{ eventData.clientIp || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.serviceName')">{{ eventData.serviceName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.traceId')" :span="2">
            <el-tag v-if="eventData.traceId" effect="plain" type="info" class="mono-font">
              {{ eventData.traceId }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 异常信息 -->
        <el-divider content-position="left">{{ t('exception.detail.exceptionInfo') }}</el-divider>
        <el-card shadow="never" class="info-card">
          <template #header>
            <span>{{ t('exception.detail.rawMessage') }}</span>
          </template>
          <pre class="message-box">{{ eventData.exceptionMessage || t('exception.detail.none') }}</pre>
        </el-card>

        <el-card v-if="eventData.sanitizedMessage" shadow="never" class="info-card">
          <template #header>
            <span>{{ t('exception.detail.sanitizedMessage') }}</span>
          </template>
          <pre class="message-box">{{ eventData.sanitizedMessage }}</pre>
        </el-card>

        <!-- 统计信息 -->
        <el-divider content-position="left">{{ t('exception.detail.statisticsInfo') }}</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('exception.detail.occurrenceCount')">{{ eventData.occurrenceCount || 1 }}</el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.isAggregated')">
            <el-tag :type="eventData.isAggregated ? 'success' : 'info'" size="small">
              {{ eventData.isAggregated ? t('exception.detail.yes') : t('exception.detail.no') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.firstOccurredAt')">
            {{ formatTime(eventData.firstOccurrence) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.lastOccurredAt')">
            {{ formatTime(eventData.lastOccurrence) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('exception.detail.occurredAt')">
            {{ formatTime(eventData.occurredAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 时间线 -->
        <el-divider content-position="left">{{ t('exception.detail.timeline') }}</el-divider>
        <el-timeline>
          <el-timeline-item
            :timestamp="formatTime(eventData.firstOccurrence)"
            placement="top"
          >
            <el-card shadow="hover">
              <p>{{ t('exception.detail.timelineFirst') }}</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item
            v-if="eventData.lastOccurrence !== eventData.firstOccurrence"
            :timestamp="formatTime(eventData.lastOccurrence)"
            placement="top"
          >
            <el-card shadow="hover">
              <p>{{ t('exception.detail.timelineLast') }}</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item
            v-if="eventData.isAggregated"
            :timestamp="formatTime(eventData.occurredAt)"
            placement="top"
            type="success"
          >
            <el-card shadow="hover">
              <p>{{ t('exception.detail.timelineAggregated') }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>

      <el-empty v-else :description="t('exception.detail.notFound')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Warning, ArrowLeft } from '@element-plus/icons-vue'
import { getExceptionEventById } from '@/api/exception'
import type { ExceptionEvent } from '@/types/exception'

const router = useRouter()
const route = useRoute()

const { t } = useI18n()

const loading = ref(true)
const eventData = ref<ExceptionEvent | null>(null)

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 获取错误标签类型
const getErrorTagType = (code?: string) => {
  if (!code) return 'info'
  const num = parseInt(code)
  if (num >= 500) return 'danger'
  if (num >= 400) return 'warning'
  return 'info'
}

// 获取分类标签类型
const getCategoryTagType = (category?: string) => {
  switch (category) {
    case 'SERVER_ERROR':
      return 'danger'
    case 'CLIENT_ERROR':
      return 'warning'
    case 'SECURITY_ERROR':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取 HTTP 状态标签类型
const getHttpStatusTagType = (status?: string) => {
  if (!status) return 'info'
  const num = parseInt(status)
  if (num >= 500) return 'danger'
  if (num >= 400) return 'warning'
  return 'success'
}

// 格式化分类
const formatCategory = (category?: string) => {
  if (!category) return '-'
  return category.replace(/_/g, '')
}

// 加载数据
const loadData = async () => {
  const eventId = route.params.id as string
  if (!eventId) {
    ElMessage.error(t('exception.detail.eventIdRequired'))
    loading.value = false
    return
  }

  loading.value = true
  try {
    const event = await getExceptionEventById(eventId)
    if (event) {
      eventData.value = event
    } else {
      ElMessage.warning(t('exception.detail.notFoundMessage'))
    }
  } catch (error: any) {
    console.error('加载异常事件详情失败:', error)
    ElMessage.error(t('exception.detail.loadFailed', { message: error.message || t('exception.detail.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 返回
const goBack = () => {
  router.back()
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.exception-detail {
  padding: 20px;
}

.detail-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-card .detail-title {
  font-size: 18px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-card .detail-content .mono-font {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.detail-card .detail-content .info-card {
  margin-bottom: 15px;
}

.detail-card .detail-content .info-card .message-box {
  background: var(--el-fill-color-light);
  padding: 15px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  margin: 0;
}
</style>
