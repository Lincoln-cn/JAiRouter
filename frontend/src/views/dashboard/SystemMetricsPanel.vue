<template>
  <div class="monitoring-box">
    <!-- 白底描述列表：标签/数值只用中性阶，语义色只落在失败/告警计数上 -->
    <el-descriptions v-if="metrics" :column="1" border size="small">
      <el-descriptions-item :label="t('dashboard.metrics.jvmMemory')">
        <div class="metric-line">
          <el-progress
            class="metric-progress"
            :percentage="metrics.jvm?.heapUsagePercent || 0"
            :status="(metrics.jvm?.heapUsagePercent || 0) > 80 ? 'exception' : ''"
            :stroke-width="10"
            :show-text="false"
          />
          <span class="metric-value">
            {{ metrics.jvm?.heapUsedMB || 0 }} / {{ metrics.jvm?.heapMaxMB || 0 }} MB
          </span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.threads')">
        <div class="metric-line">
          <span class="metric-value">{{ t('dashboard.metrics.countUnit', { count: metrics.jvm?.threadCount || 0 }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.peakPrefix', { count: metrics.jvm?.peakThreadCount || 0 }) }}</span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.httpRequests')">
        <div class="metric-line">
          <span class="metric-value">{{ t('dashboard.metrics.timesUnit', { count: metrics.http?.totalRequests || 0 }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.avgResponse', { value: (metrics.http?.avgResponseTimeMs || 0).toFixed(1) }) }}</span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.authentication')">
        <div class="metric-line">
          <span class="metric-value">{{ t('dashboard.metrics.successUnit', { count: metrics.security?.authSuccesses || 0 }) }}</span>
          <span :class="['metric-muted', { 'metric-muted--danger': (metrics.security?.authFailures || 0) > 0 }]">
            {{ t('dashboard.metrics.failureUnit', { count: metrics.security?.authFailures || 0 }) }}
          </span>
          <span class="metric-muted">{{ t('dashboard.metrics.activePrefix', { count: Math.round(metrics.security?.activeUsers || 0) }) }}</span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.securityCache')">
        <div class="metric-line">
          <span class="metric-value">{{ t('dashboard.metrics.hitsPrefix', { count: Math.round(metrics.security?.cacheHits || 0) }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.missesPrefix', { count: Math.round(metrics.security?.cacheMisses || 0) }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.sizePrefix', { count: Math.round(metrics.security?.cacheSize || 0) }) }}</span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.auditEvents')">
        <div class="metric-line">
          <span class="metric-value">{{ t('dashboard.metrics.totalUnit', { count: Math.round(metrics.audit?.totalEvents || 0) }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.successUnit', { count: Math.round(metrics.audit?.successEvents || 0) }) }}</span>
          <span :class="['metric-muted', { 'metric-muted--danger': (metrics.audit?.failureEvents || 0) > 0 }]">
            {{ t('dashboard.metrics.failureUnit', { count: Math.round(metrics.audit?.failureEvents || 0) }) }}
          </span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.systemLoad')">
        <div class="metric-line">
          <span class="metric-value">{{ (metrics.system?.systemLoadAverage || 0).toFixed(2) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.cpuPrefix', { value: ((metrics.system?.processCpuUsage || 0) * 100).toFixed(1) }) }}</span>
          <span class="metric-muted">{{ t('dashboard.metrics.coresPrefix', { count: metrics.system?.availableProcessors || 0 }) }}</span>
        </div>
      </el-descriptions-item>

      <el-descriptions-item :label="t('dashboard.metrics.uptime')">
        <div class="metric-line">
          <span class="metric-value">{{ formatUptime(metrics.system?.uptimeSeconds) }}</span>
        </div>
      </el-descriptions-item>
    </el-descriptions>

    <div v-else class="metrics-empty">
      <el-icon><Loading /></el-icon>
      <span>{{ t('dashboard.loadingMetrics') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 「系统指标」描述列表（Dashboard 私有子组件）。
 *
 * 只负责呈现：数据、轮询与错误处理都留在 Dashboard.vue。
 * 视觉规则：白底 + 中性阶（ink 阶梯 / surface / border），
 * 语义色只允许出现在失败计数与进度条的 exception 态上。
 */
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import type { DashboardMetrics } from '@/types'

defineProps<{ metrics: DashboardMetrics | null }>()

const { t } = useI18n()

/** 运行时长：与 Dashboard 原实现一致（天/小时/分钟三档） */
const formatUptime = (seconds: number | undefined) => {
  if (seconds === undefined || seconds === null) return 'N/A'
  const s = Math.round(seconds)
  const days = Math.floor(s / 86400)
  const hours = Math.floor((s % 86400) / 3600)
  const mins = Math.floor((s % 3600) / 60)
  if (days > 0) return t('dashboard.metrics.uptimeDaysHours', { days, hours })
  if (hours > 0) return t('dashboard.metrics.uptimeHoursMinutes', { hours, minutes: mins })
  return t('dashboard.metrics.uptimeMinutes', { minutes: mins })
}
</script>

<style scoped>
.monitoring-box { padding: 0; }

:deep(.el-descriptions__label) { color: var(--ja-ink-3); font-weight: 500; }
:deep(.el-descriptions__content) { color: var(--ja-ink); }

.metric-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ja-space-2);
  min-width: 0;
}

.metric-progress { flex: 1 1 96px; min-width: 64px; }
:deep(.el-progress-bar__outer) { background: var(--ja-surface-2); }

.metric-value { color: var(--ja-ink); font-size: 13px; font-variant-numeric: tabular-nums; }
.metric-muted { color: var(--ja-ink-3); font-size: 12px; font-variant-numeric: tabular-nums; }
.metric-muted--danger { color: var(--ja-danger); }

.metrics-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--ja-space-2);
  padding: var(--ja-space-6) var(--ja-space-3);
  color: var(--ja-ink-3);
  font-size: 13px;
}
</style>
