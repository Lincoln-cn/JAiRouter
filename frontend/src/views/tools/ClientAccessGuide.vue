<template>
  <PageSkeleton :title="t('clientAccess.pageTitle')">
    <!-- OpenAI 兼容接口 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('clientAccess.openaiSection') }}</span>
      </template>

      <div class="info-section">
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.openaiBaseUrlLabel') }}：</span>
          <code class="info-value">{{ openaiBaseUrl }}</code>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.openaiAuthLabel') }}：</span>
          <span class="info-value">{{ t('clientAccess.openaiAuthDesc') }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.openaiModelsEndpoint') }}：</span>
          <code class="info-value">GET /v1/models</code>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.openaiChatEndpoint') }}：</span>
          <code class="info-value">POST /v1/chat/completions</code>
          <span class="stream-note">（{{ t('clientAccess.streamNote') }}）</span>
        </div>
      </div>

      <!-- curl 示例 -->
      <div class="code-section">
        <div class="code-header">
          <span class="code-title">{{ t('clientAccess.openaiCurlTitle') }}</span>
          <el-button size="small" @click="copyCode(openaiCurlCode)">
            <el-icon><DocumentCopy /></el-icon>
            {{ t('clientAccess.copyCode') }}
          </el-button>
        </div>
        <pre class="code-block"><code>{{ openaiCurlCode }}</code></pre>
      </div>

      <!-- SDK 示例 -->
      <div class="code-section">
        <div class="code-header">
          <span class="code-title">{{ t('clientAccess.openaiSdkTitle') }}</span>
          <el-button size="small" @click="copyCode(openaiSdkCode)">
            <el-icon><DocumentCopy /></el-icon>
            {{ t('clientAccess.copyCode') }}
          </el-button>
        </div>
        <pre class="code-block"><code>{{ openaiSdkCode }}</code></pre>
      </div>
    </el-card>

    <!-- Anthropic 兼容接口 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('clientAccess.anthropicSection') }}</span>
      </template>

      <div class="info-section">
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.anthropicBaseUrlLabel') }}：</span>
          <code class="info-value">{{ origin }}</code>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.anthropicAuthLabel') }}：</span>
          <span class="info-value">{{ t('clientAccess.anthropicAuthDesc') }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.anthropicMessagesEndpoint') }}：</span>
          <code class="info-value">POST /v1/messages</code>
          <span class="stream-note">（{{ t('clientAccess.streamNote') }}）</span>
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('clientAccess.anthropicCountTokensEndpoint') }}：</span>
          <code class="info-value">POST /v1/messages/count_tokens</code>
        </div>
      </div>

      <!-- 环境变量 -->
      <div class="code-section">
        <div class="code-header">
          <span class="code-title">{{ t('clientAccess.anthropicEnvTitle') }}</span>
          <el-button size="small" @click="copyCode(anthropicEnvCode)">
            <el-icon><DocumentCopy /></el-icon>
            {{ t('clientAccess.copyCode') }}
          </el-button>
        </div>
        <pre class="code-block"><code>{{ anthropicEnvCode }}</code></pre>
      </div>

      <!-- Claude Code CLI -->
      <div class="code-section">
        <div class="code-header">
          <span class="code-title">{{ t('clientAccess.anthropicCliTitle') }}</span>
          <el-button size="small" @click="copyCode(anthropicCliCode)">
            <el-icon><DocumentCopy /></el-icon>
            {{ t('clientAccess.copyCode') }}
          </el-button>
        </div>
        <pre class="code-block"><code>{{ anthropicCliCode }}</code></pre>
      </div>
    </el-card>

    <!-- 可用模型清单 -->
    <el-card shadow="hover">
      <template #header>
        <span class="card-title">{{ t('clientAccess.modelListSection') }}</span>
      </template>
      <el-table
        :data="modelList"
        v-loading="loadingModels"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" :label="t('clientAccess.modelIdColumn')" min-width="200" />
        <el-table-column prop="owned_by" :label="t('clientAccess.ownedByColumn')" min-width="150" />
      </el-table>
      <el-alert
        v-if="modelLoadFailed"
        :title="t('clientAccess.modelListLoadFailed', { origin })"
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />
      <el-empty
        v-if="!loadingModels && !modelLoadFailed && modelList.length === 0"
        :description="t('clientAccess.modelListNotAvailable')"
      />
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'

const { t } = useI18n()

const origin = window.location.origin

interface ModelItem {
  id: string
  owned_by: string
}

const modelList = ref<ModelItem[]>([])
const loadingModels = ref(false)
const modelLoadFailed = ref(false)

// ── OpenAI 兼容面 ──

const openaiBaseUrl = computed(() => `${origin}/v1`)

const openaiCurlCode = computed(() => `curl ${origin}/v1/chat/completions \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: <YOUR_API_KEY>" \\
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'`)

const openaiSdkCode = computed(() => `from openai import OpenAI

client = OpenAI(
    base_url="${origin}/v1",
    default_headers={"X-API-Key": "<YOUR_API_KEY>"},
)

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Hello!"}],
    stream=True,
)
for chunk in response:
    print(chunk.choices[0].delta.content, end="")`)

// ── Anthropic 兼容面 ──

const anthropicEnvCode = computed(() => `export ANTHROPIC_BASE_URL="${origin}"
export ANTHROPIC_API_KEY="<YOUR_API_KEY>"
export ANTHROPIC_MODEL="claude-sonnet-4-20250514"`)

const anthropicCliCode = computed(() => `npx -y @anthropic-ai/claude-code`)

// ── 复制功能 ──

const copyCode = async (code: string) => {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success(t('clientAccess.copied'))
  } catch {
    ElMessage.warning(t('clientAccess.copyFailed'))
  }
}

// ── 模型列表加载 ──

const loadModels = async () => {
  loadingModels.value = true
  modelLoadFailed.value = false
  try {
    const token = localStorage.getItem('admin_token')
    if (!token) {
      modelList.value = []
      modelLoadFailed.value = true
      return
    }
    const resp = await fetch(`${origin}/v1/models`, {
      headers: { 'Jairouter_Token': token },
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    const data = json.data ?? json
    if (Array.isArray(data)) {
      modelList.value = data.map((m: { id: string; owned_by?: string }) => ({
        id: m.id,
        owned_by: m.owned_by ?? '—',
      }))
    } else {
      modelList.value = []
      modelLoadFailed.value = true
    }
  } catch {
    modelList.value = []
    modelLoadFailed.value = true
  } finally {
    loadingModels.value = false
  }
}

onMounted(loadModels)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
}

.info-section {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 14px;
}

.info-label {
  font-weight: 500;
  color: var(--ja-text-primary);
  white-space: nowrap;
}

.info-value {
  color: var(--el-text-color-regular);
}

.stream-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.code-section {
  margin-bottom: 16px;
}

.code-section:last-child {
  margin-bottom: 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.code-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.code-block {
  background: var(--el-fill-color-dark, #1e1e1e);
  color: var(--el-text-color-primary, #d4d4d4);
  padding: 16px;
  border-radius: var(--ja-radius-md, 8px);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  font-family: var(--el-font-family-monospace, 'Consolas', 'Monaco', monospace);
  margin: 0;
}

.code-block code {
  white-space: pre;
}
</style>
