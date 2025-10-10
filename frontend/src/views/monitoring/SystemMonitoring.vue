<template>
  <div class="system-monitoring">
    <!-- 存储系统健康状态 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>存储系统健康状态</span>
              <el-button :loading="healthLoading" type="primary" @click="refreshHealthStatus">刷新状态</el-button>
            </div>
          </template>
          
          <el-row :gutter="20">
            <el-col :span="8">
              <el-card shadow="never" class="health-card">
                <el-statistic :value="healthStatus.redis.connected ? '正常' : '异常'" title="Redis连接状态">
                  <template #prefix>
                    <el-icon :style="{ color: healthStatus.redis.connected ? '#67c23a' : '#f56c6c' }">
                      <SuccessFilled v-if="healthStatus.redis.connected"/>
                      <CircleCloseFilled v-else/>
                    </el-icon>
                  </template>
                </el-statistic>
                <div class="health-details">
                  <p>响应时间: {{ healthStatus.redis.responseTime }}ms</p>
                  <p>连接池: {{ healthStatus.redis.poolSize }}/{{ healthStatus.redis.maxPoolSize }}</p>
                </div>
              </el-card>
            </el-col>
            
            <el-col :span="8">
              <el-card shadow="never" class="health-card">
                <el-statistic :value="healthStatus.memory.status" title="内存存储状态">
                  <template #prefix>
                    <el-icon :stylealth.overall === 'healthy' ? 'success' : 'danger'" size="large">
              {{ systemHealth.overall === 'healthy' ? '健康' : '异常' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 存储系统健康状态 -->
    <el-card class="monitoring-card">
      <template #header>
        <div class="card-header">
          <span>存储系统健康状态</span>
          <el-button :loading="healthLoading" @click="refreshHealthStatus">刷新状态</el-button>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="health-section">
            <h4>Redis 存储</h4>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="连接状态">
                <el-tag :type="systemHealth.redis.connected ? 'success' : 'danger'">
                  {{ systemHealth.redis.connected ? '已连接' : '断开' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="响应时间">
                {{ systemHealth.redis.responseTime }}ms
              </el-descriptions-item>
              <el-descriptions-item label="内存使用">
                {{ systemHealth.redis.memoryUsage }}MB
              </el-descriptions-item>
              <el-descriptions-item label="键数量">
                {{ systemHealth.redis.keyCount }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="health-section">
            <h4>内存存储</h4>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="状态">
                <el-tag :type="systemHealth.memory.status === 'healthy' ? 'success' : 'warning'">
                  {{ systemHealth.memory.status === 'healthy' ? '正常' : '警告' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="使用率">
                {{ systemHealth.memory.usagePercentage }}%
              </el-descriptions-item>
              <el-descriptions-item label="令牌数量">
                {{ systemHealth.memory.tokenCount }}
              </el-descriptions-item>
              <el-descriptions-item label="黑名单数量">
                {{ systemHealth.memory.blacklistCount }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 清理操作统计图表 -->
    <el-card class="monitoring-card">
      <template #header>
        <div class="card-header">
          <span>清理操作统计</span>
          <div>
            <el-button @click="refreshCleanupStats">刷新数据</el-button>
            <el-button type="primary" @click="triggerManualCleanup">手动清理</el-button>
          </div>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic :value="cleanupStats.lastCleanupTokens" title="上次清理令牌数">
            <template #suffix>个</template>
          </el-statistic>
        </el-col>
        <el-col :span="8">
          <el-statistic :value="cleanupStats.lastCleanupBlacklist" title="上次清理黑名单数">
            <template #suffix>个</template>
          </el-statistic>
        </el-col>
        <el-col :span="8">
          <el-statistic :value="formatDateTime(cleanupStats.lastCleanupTime)" title="上次清理时间" />
        </el-col>
      </el-row>

      <div class="cleanup-chart-container">
        <div ref="cleanupChartRef" class="cleanup-chart"></div>
      </div>
    </el-card>

    <!-- 审计事件查看 -->
    <el-card class="monitoring-card">
      <template #header>
        <div class="card-header">
          <span>最近审计事件</span>
          <el-button @click="viewAllAuditEvents">查看全部</el-button>
        </div>
      </template>
      
      <!-- 事件类型过滤 -->
      <div class="audit-filters">
        <el-radio-group v-model="auditFilter" @change="filterAuditEvents">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="JWT_TOKEN">JWT令牌</el-radio-button>
          <el-radio-button label="API_KEY">API密钥</el-radio-button>
          <el-radio-button label="SECURITY">安全事件</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="recentAuditEvents" style="width: 100%" max-height="400">
        <el-table-column prop="timestamp" label="时间" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="事件类型" width="120">
          <template #default="scope">
            <el-tag :type="getEventTypeColor(scope.row.type)">
              {{ getEventTypeText(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="action" label="操作" width="120" />
        <el-table-column prop="ipAddress" label="IP地址" width="140" />
        <el-table-column prop="success" label="结果" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="details" label="详情" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" @click="viewAuditEventDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 系统配置状态 -->
    <el-card class="monitoring-card">
      <template #header>
        <div class="card-header">
          <span>系统配置状态</span>
          <el-button @click="refreshConfigStatus">刷新配置</el-button>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="config-section">
            <h4>JWT 持久化配置</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="持久化启用">
                <el-tag :type="systemConfig.jwt.persistenceEnabled ? 'success' : 'info'">
                  {{ systemConfig.jwt.persistenceEnabled ? '已启用' : '未启用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="主存储">
                {{ systemConfig.jwt.primaryStorage }}
              </el-descriptions-item>
              <el-descriptions-item label="备用存储">
                {{ systemConfig.jwt.fallbackStorage }}
              </el-descriptions-item>
              <el-descriptions-item label="清理调度">
                {{ systemConfig.jwt.cleanupSchedule }}
              </el-descriptions-item>
              <el-descriptions-item label="保留天数">
                {{ systemConfig.jwt.retentionDays }}天
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="config-section">
            <h4>黑名单配置</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="黑名单启用">
                <el-tag :type="systemConfig.blacklist.enabled ? 'success' : 'info'">
                  {{ systemConfig.blacklist.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="主存储">
                {{ systemConfig.blacklist.primaryStorage }}
              </el-descriptions-item>
              <el-descriptions-item label="最大内存大小">
                {{ systemConfig.blacklist.maxMemorySize }}
              </el-descriptions-item>
              <el-descriptions-item label="清理间隔">
                {{ systemConfig.blacklist.cleanupInterval }}秒
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 审计事件详情对话框 -->
    <el-dialog v-model="auditDetailDialogVisible" title="审计事件详情" width="800px">
      <el-descriptions v-if="selectedAuditEvent" :column="2" border>
        <el-descriptions-item label="事件ID">{{ selectedAuditEvent.id }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="getEventTypeColor(selectedAuditEvent.type)">
            {{ getEventTypeText(selectedAuditEvent.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ selectedAuditEvent.userId }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ selectedAuditEvent.resourceId }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ selectedAuditEvent.action }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ selectedAuditEvent.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="用户代理" :span="2">{{ selectedAuditEvent.userAgent }}</el-descriptions-item>
        <el-descriptions-item label="操作结果">
          <el-tag :type="selectedAuditEvent.success ? 'success' : 'danger'">
            {{ selectedAuditEvent.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间戳">{{ formatDateTime(selectedAuditEvent.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="详细信息" :span="2">
          <pre class="audit-details">{{ JSON.stringify(selectedAuditEvent.metadata, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="auditDetailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Warning, Delete } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  getSystemStats,
  getSystemHealth,
  getCleanupStats,
  getSystemConfig,
  getRecentAuditEvents,
  triggerManualCleanup as apiTriggerManualCleanup,
  type SystemStats,
  type SystemHealth,
  type CleanupStats,
  type SystemConfig,
  type AuditEvent
} from '@/api/systemMonitoring'

const router = useRouter()

// 系统统计数据
const systemStats = reactive({
  activeTokens: 0,
  blacklistSize: 0,
  totalCleanedTokens: 0
})

// 系统健康状态
const systemHealth = reactive({
  overall: 'healthy',
  redis: {
    connected: true,
    responseTime: 15,
    memoryUsage: 128,
    keyCount: 1250
  },
  memory: {
    status: 'healthy',
    usagePercentage: 65,
    tokenCount: 850,
    blacklistCount: 45
  }
})

// 清理统计数据
const cleanupStats = reactive({
  lastCleanupTokens: 0,
  lastCleanupBlacklist: 0,
  lastCleanupTime: ''
})

// 系统配置状态
const systemConfig = reactive({
  jwt: {
    persistenceEnabled: true,
    primaryStorage: 'redis',
    fallbackStorage: 'memory',
    cleanupSchedule: '0 0 2 * * ?',
    retentionDays: 30
  },
  blacklist: {
    enabled: true,
    primaryStorage: 'redis',
    maxMemorySize: 10000,
    cleanupInterval: 3600
  }
})

// 审计事件数据
const recentAuditEvents = ref([])
const auditFilter = ref('all')
const selectedAuditEvent = ref(null)
const auditDetailDialogVisible = ref(false)

// 加载状态
const healthLoading = ref(false)

// 图表引用
const cleanupChartRef = ref()

// 格式化日期时间
const formatDateTime = (dateTime: string | number) => {
  if (!dateTime) return '未知'
  
  if (typeof dateTime === 'number') {
    return new Date(dateTime).toLocaleString('zh-CN')
  }
  
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 获取事件类型颜色
const getEventTypeColor = (type: string) => {
  switch (type) {
    case 'JWT_TOKEN':
      return 'primary'
    case 'API_KEY':
      return 'success'
    case 'SECURITY':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取事件类型文本
const getEventTypeText = (type: string) => {
  switch (type) {
    case 'JWT_TOKEN':
      return 'JWT令牌'
    case 'API_KEY':
      return 'API密钥'
    case 'SECURITY':
      return '安全事件'
    default:
      return '未知'
  }
}

// 刷新健康状态
const refreshHealthStatus = async () => {
  healthLoading.value = true
  try {
    const healthData = await getSystemHealth()
    Object.assign(systemHealth, healthData)
    ElMessage.success('健康状态已刷新')
  } catch (error: any) {
    ElMessage.error('刷新健康状态失败: ' + (error.message || '未知错误'))
  } finally {
    healthLoading.value = false
  }
}

// 刷新清理统计
const refreshCleanupStats = async () => {
  try {
    const cleanupData = await getCleanupStats()
    Object.assign(cleanupStats, cleanupData)
    ElMessage.success('清理统计已刷新')
  } catch (error: any) {
    ElMessage.error('刷新清理统计失败: ' + (error.message || '未知错误'))
  }
}

// 触发手动清理
const triggerManualCleanup = async () => {
  ElMessageBox.confirm('确定要执行手动清理吗？这将清理所有过期的令牌和黑名单条目。', '确认清理', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const result = await apiTriggerManualCleanup()
      
      ElMessage.success(`清理完成！清理了 ${result.cleanedTokens} 个过期令牌和 ${result.cleanedBlacklist} 个黑名单条目`)
      
      // 刷新相关数据
      await Promise.all([
        loadSystemStats(),
        refreshCleanupStats()
      ])
      
    } catch (error: any) {
      ElMessage.error('手动清理失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 过滤审计事件
const filterAuditEvents = () => {
  loadRecentAuditEvents()
}

// 查看审计事件详情
const viewAuditEventDetail = (event: any) => {
  selectedAuditEvent.value = event
  auditDetailDialogVisible.value = true
}

// 查看全部审计事件
const viewAllAuditEvents = () => {
  router.push('/security/audit-logs')
}

// 刷新配置状态
const refreshConfigStatus = async () => {
  try {
    const configData = await getSystemConfig()
    Object.assign(systemConfig, configData)
    ElMessage.success('配置状态已刷新')
  } catch (error: any) {
    ElMessage.error('刷新配置状态失败: ' + (error.message || '未知错误'))
  }
}

// 加载系统统计数据
const loadSystemStats = async () => {
  try {
    const statsData = await getSystemStats()
    Object.assign(systemStats, statsData)
  } catch (error: any) {
    ElMessage.error('加载系统统计失败: ' + (error.message || '未知错误'))
  }
}

// 加载最近审计事件
const loadRecentAuditEvents = async () => {
  try {
    const query = {
      type: auditFilter.value === 'all' ? undefined : auditFilter.value,
      size: 10
    }
    
    const events = await getRecentAuditEvents(query)
    recentAuditEvents.value = events
  } catch (error: any) {
    ElMessage.error('加载审计事件失败: ' + (error.message || '未知错误'))
  }
}

// 初始化清理图表
const initCleanupChart = async () => {
  await nextTick()
  // 这里可以使用 ECharts 或其他图表库来绘制清理统计图表
  // 由于没有引入图表库，这里只是占位
  if (cleanupChartRef.value) {
    cleanupChartRef.value.innerHTML = '<div style="text-align: center; padding: 50px; color: #999;">清理统计图表（需要集成图表库）</div>'
  }
}

// 组件挂载时加载数据
onMounted(async () => {
  await Promise.all([
    loadSystemStats(),
    refreshHealthStatus(),
    refreshCleanupStats(),
    refreshConfigStatus(),
    loadRecentAuditEvents(),
    initCleanupChart()
  ])
  console.log('系统监控页面已加载')
})
</script>

<style scoped>
.system-monitoring {
  padding: 20px;
}

.overview-cards {
  margin-bottom: 20px;
}

.overview-card {
  text-align: center;
}

.health-status {
  text-align: center;
}

.health-title {
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
}

.monitoring-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.health-section {
  margin-bottom: 20px;
}

.health-section h4 {
  margin-bottom: 15px;
  color: #303133;
}

.config-section {
  margin-bottom: 20px;
}

.config-section h4 {
  margin-bottom: 15px;
  color: #303133;
}

.cleanup-chart-container {
  margin-top: 20px;
}

.cleanup-chart {
  height: 300px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.audit-filters {
  margin-bottom: 15px;
}

.audit-details {
  max-height: 200px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
}

// 获取审-CN')leString('zhe).toLocaTimw Date(date  return ne }
  
N')
 zh-CleString('ocaateTime).toL Date(dew return n
   ber') { === 'numTime(typeof date
  if  ''
  rn retume)!dateTi
  if (number) => { string | me:Tite (dame =Ti formatDate期时间
const
// 格式化日: 20
})
  size  page: 1,
eactive({
ination = rt auditPag审计分页
cons

//  | null
})ng], stritringull as [sge: n dateRan '',
 rId:sen: '',
  uio: '',
  actentType evve({
 er = reactiiltauditFst 器
con 审计过滤
//false)
e = ref(VisibletailsDialoguditD anull)
constull>(itEvent | n = ref<AudenttedAuditEvst selec})

contrue
e,
  last:  first: truer: 0,
 umb,
  n20,
  size: : 0ges totalPa
 lements: 0,  totalE,
ontent: []>>({
  cntuditEvelt<Aesu<PagedRs = reft auditEvent
cons审计事件)

// rue
  }
}d: t
    enable {audit:,
  
  }Days: 30 retention
    * ?',0 2 *le: '0 chedurue,
    snabled: t
    eup: {,
  cleand: true
  }enable
    ist: {,
  blackl
  }y'age: 'MemorStor  fallback',
   'RedisStorage:   primarye,
 bled: tru    enace: {
wtPersisten j
 >({atus<ConfigSts = reftatufigSnst con/ 配置状态
co

/}
  ]
})'
    CCESSatus: 'SU   st,
   Time: 1350cution     exes: 15,
 EntriestedBlackliclean      kens: 52,
  cleanedTo  g(),
  trin1000).toISOS* 60 *  60  *24 - 3 * now()w Date(Date.mestamp: ne   ti    {
   },
CCESS'
    tus: 'SU
      stame: 950,executionTi   8,
   stEntries: lackli  cleanedBs: 38,
    cleanedToken     ng(),
 0).toISOStri1000 *  * 624 * 60() - 2 * (Date.now new Date timestamp:    {
 
    S'
    }, 'SUCCESstatus:00,
      11ionTime:    execut2,
    1tries:listEnckedBlaclean
      5,nedTokens: 4   cleag(),
   oISOStrin1000).t60 * 60 * ow() - 24 * e.nDat Date(tamp: newmes   ti
    [
    {ps:nuClea recent
 50,anupTime: 12  averageCleOString(),
().toISDate: new tCleanupTime80,
  lasntries: 32istEeanedBlackltotalCl20,
  kens: 154dToleaneotalC
  tupStats>({eans = ref<Cltat cleanupS 清理统计
const}
})

//'
  moryrage: 'MefallbackStoedis',
    age: 'RaryStor
    prim true,  enabled:
  stence: {siper0
  },
  nCount: 125  toke 65,
  gePercent:usa
    ,tatus: '正常'
    s memory: { },
  20
 e:xPoolSiz   ma 8,
   poolSize:5,
  e: seTim
    respontrue,ted: onnec{
    c
  redis: s>({althStatuHetatus = ref<lthShea
const 

// 健康状态e)falsding = ref(nst auditLoa(false)
coing = refnfigLoad
const colse)g = ref(fadinatsLoanupStclease)
const ref(fal = lthLoading hea据
const// 响应式数olean
}

bo last: 
 st: booleanumber
  firumber: n
  nnumber
  size: ages: numbertalPmber
  to nuts:lElemen
  tota[] content: T<T> {
 dResultce Page

interfany
}: atadata?ng
  mestristamp: ean
  timeess: boolng
  succstrigent: rAse string
  uipAddress:  s: string
detailring
  urceId: st resog
 trinrId: sring
  usetion: sting
  ac
  type: str: string {
  iditEventerface Aud数据结构
int}

// 审计事件n
  }
ooleaabled: b  endit: {
   auber
  }
 s: numayionD   retent: string
 hedule   sc: boolean
   enabled{
    cleanup: lean
  }
abled: boo
    enblacklist: {
  ring
  }orage: st  fallbackSt string
  Storage:
    primaryleaned: boo enabl
   sistence: {Per {
  jwtatusace ConfigSt
interf构态数据结
// 配置状 }>
}
ing
 stratus:     st
berTime: numxecution   eber
 ntries: numtEklisleanedBlac
    cns: numberanedToke    cletring
: s   timestampArray<{
 anups: centCleumber
  reeanupTime: nerageClstring
  avme: anupTitCle lasumber
 s: ntEntrieklisdBlactalCleane tos: number
 nedTokenlCleaotaats {
  tleanupStace C数据结构
interf}

// 清理统计tring
  }
rage: sfallbackSto
    e: stringrimaryStoragn
    p: boolea enabled {
   ersistence:er
  }
  pCount: numbr
    tokenent: numbeePercg
    usagus: strinaty: {
    stmorme  r
  }
umbePoolSize: naxr
    mlSize: numbeer
    poo numbonseTime:sp
    re boolean  connected:s: {
  redi
  s { HealthStaturface
inte康状态数据结构/ 健vue'

//icons-ust-pllemenm '@eg } froled, WarninseFil CircleClolled,ccessFiimport { Su
s'lunt-pemefrom 'eleBox } e, ElMessagagMess El
import {'vue'om  } frveref, reactinMounted,  oort {">
impg="ts lanipt setup

<scrtemplate> </div>
</dialog>
   </el->
  template>
      </pan  </s
      ton>">关闭</el-butfalseisible = gVDetailsDialock="audit@clion el-butt          <footer">
log-diaan class="sp
        <#footer> <template 
     ions>riptscde  </el-
    tions-item>l-descrip/e