<template>
  <div class="audio-playground">
    <el-tabs v-model="activeAudioTab" type="border-card" @tab-change="onAudioTabChange">
      <!-- TTS 选项卡 -->
      <el-tab-pane label="文本转语音 (TTS)" name="tts">
        <div class="service-content">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card header="TTS 配置" class="config-card">
                <el-form ref="ttsFormRef" :model="ttsFormData" :rules="ttsRules" label-width="120px"
                  @submit.prevent="sendTtsRequest">
                  <!-- 实例选择 -->
                  <el-form-item label="选择实例" prop="selectedInstanceId" required>
                    <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                      <el-select v-model="selectedTtsInstanceId" placeholder="请选择TTS服务实例" @change="onTtsInstanceChange"
                        style="flex: 1" :loading="ttsInstancesLoading" clearable>
                        <el-option v-for="instance in availableTtsInstances" :key="instance.instanceId"
                          :label="instance.name" :value="instance.instanceId">
                          <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>{{ instance.name }}</span>
                            <el-tag v-if="instance.headers && Object.keys(instance.headers).length > 0" type="success"
                              size="small">
                              {{ Object.keys(instance.headers).length }} 个请求头
                            </el-tag>
                          </div>
                        </el-option>
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ ttsInstancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加TTS服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button type="info" size="small" @click="fetchTtsInstances" :loading="ttsInstancesLoading"
                        title="刷新实例列表">
                        <el-icon>
                          <Refresh />
                        </el-icon>
                      </el-button>
                    </div>
                    <div v-if="!selectedTtsInstanceId" class="instance-hint">
                      <el-text type="danger" size="small">请选择一个TTS服务实例</el-text>
                    </div>
                    <div v-else-if="selectedTtsInstanceInfo" class="instance-selected">
                      <el-text type="success" size="small">
                        <el-icon>
                          <Check />
                        </el-icon>
                        已选择实例: {{ selectedTtsInstanceInfo.name }}
                      </el-text>
                    </div>
                  </el-form-item>

                  <!-- 请求头配置 -->
                  <el-form-item v-if="selectedTtsInstanceInfo" label="请求头配置">
                    <div class="headers-config">
                      <div class="headers-list">
                        <div v-for="(header, index) in ttsHeadersList" :key="index" class="header-item">
                          <el-input v-model="header.key" placeholder="请求头名称" size="small" @input="onTtsHeaderChange"
                            class="header-key" :disabled="header.fromInstance" />
                          <el-input v-model="header.value" placeholder="请求头值" size="small" @input="onTtsHeaderChange"
                            class="header-value"
                            :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                            :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')" />
                          <el-tag v-if="header.fromInstance" type="success" size="small" class="instance-tag">
                            实例
                          </el-tag>
                          <el-button v-else type="danger" size="small" @click="removeTtsHeader(index)"
                            class="remove-btn">
                            <el-icon>
                              <Close />
                            </el-icon>
                          </el-button>
                        </div>
                      </div>
                      <el-button type="primary" size="small" @click="addTtsHeader" class="add-header-btn">
                        <el-icon>
                          <Plus />
                        </el-icon>
                        添加请求头
                      </el-button>
                    </div>
                  </el-form-item>

                  <el-form-item label="输入文本" prop="input" required>
                    <el-input v-model="ttsConfig.input" type="textarea" :rows="4" placeholder="请输入要转换为语音的文本"
                      maxlength="4096" show-word-limit clearable />
                  </el-form-item>

                  <el-form-item label="语音">
                    <el-select v-model="ttsConfig.voice" placeholder="选择语音">
                      <el-option label="Alloy" value="alloy" />
                      <el-option label="Echo" value="echo" />
                      <el-option label="Fable" value="fable" />
                      <el-option label="Onyx" value="onyx" />
                      <el-option label="Nova" value="nova" />
                      <el-option label="Shimmer" value="shimmer" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="音频格式">
                    <el-select v-model="ttsConfig.responseFormat" placeholder="选择音频格式">
                      <el-option label="MP3" value="mp3" />
                      <el-option label="OPUS" value="opus" />
                      <el-option label="AAC" value="aac" />
                      <el-option label="FLAC" value="flac" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="语速">
                    <el-slider v-model="ttsConfig.speed" :min="0.25" :max="4.0" :step="0.25" show-input
                      :show-input-controls="false" />
                    <div class="speed-hint">范围: 0.25 - 4.0，默认: 1.0</div>
                  </el-form-item>

                  <el-form-item>
                    <el-button type="primary" @click="sendTtsRequest" :loading="ttsLoading"
                      :disabled="!canSendTtsRequest">
                      <el-icon>
                        <Microphone />
                      </el-icon>
                      生成语音
                    </el-button>
                    <el-button @click="resetTtsForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>

            <el-col :span="12">
              <el-card header="音频结果" class="result-card">
                <div class="audio-result">
                  <div v-if="ttsLoading" class="loading-state">
                    <el-icon class="is-loading">
                      <Loading />
                    </el-icon>
                    <p>正在生成语音...</p>
                  </div>

                  <div v-else-if="ttsError" class="error-state">
                    <el-alert :title="ttsError" type="error" show-icon :closable="false" />
                  </div>

                  <div v-else-if="ttsAudioUrl" class="audio-player">
                    <div class="audio-info">
                      <el-tag type="success">
                        <el-icon>
                          <Check />
                        </el-icon>
                        语音生成成功
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ ttsDuration }}ms
                      </span>
                    </div>

                    <audio ref="ttsAudioRef" :src="ttsAudioUrl" controls preload="metadata" class="audio-control">
                      您的浏览器不支持音频播放
                    </audio>

                    <div class="audio-actions">
                      <el-button type="primary" size="small" @click="downloadTtsAudio">
                        <el-icon>
                          <Download />
                        </el-icon>
                        下载音频
                      </el-button>
                      <el-button size="small" @click="clearTtsResult">
                        <el-icon>
                          <Delete />
                        </el-icon>
                        清除结果
                      </el-button>
                    </div>
                  </div>

                  <div v-else class="empty-state">
                    <el-empty description="请配置参数并生成语音" />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- STT 选项卡 -->
      <el-tab-pane label="语音转文本 (STT)" name="stt">
        <div class="service-content">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card header="STT 配置" class="config-card">
                <el-form ref="sttFormRef" :model="sttFormData" :rules="sttRules" label-width="120px"
                  @submit.prevent="sendSttRequest">
                  <!-- 实例选择 -->
                  <el-form-item label="选择实例" prop="selectedInstanceId" required>
                    <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                      <el-select v-model="selectedSttInstanceId" placeholder="请选择STT服务实例" @change="onSttInstanceChange"
                        style="flex: 1" :loading="sttInstancesLoading" clearable>
                        <el-option v-for="instance in availableSttInstances" :key="instance.instanceId"
                          :label="instance.name" :value="instance.instanceId">
                          <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>{{ instance.name }}</span>
                            <el-tag v-if="instance.headers && Object.keys(instance.headers).length > 0" type="success"
                              size="small">
                              {{ Object.keys(instance.headers).length }} 个请求头
                            </el-tag>
                          </div>
                        </el-option>
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ sttInstancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加STT服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button type="info" size="small" @click="fetchSttInstances" :loading="sttInstancesLoading"
                        title="刷新实例列表">
                        <el-icon>
                          <Refresh />
                        </el-icon>
                      </el-button>
                    </div>
                    <div v-if="!selectedSttInstanceId" class="instance-hint">
                      <el-text type="danger" size="small">请选择一个STT服务实例</el-text>
                    </div>
                    <div v-else-if="selectedSttInstanceInfo" class="instance-selected">
                      <el-text type="success" size="small">
                        <el-icon>
                          <Check />
                        </el-icon>
                        已选择实例: {{ selectedSttInstanceInfo.name }}
                      </el-text>
                    </div>
                  </el-form-item>

                  <!-- 请求头配置 -->
                  <el-form-item v-if="selectedSttInstanceInfo" label="请求头配置">
                    <div class="headers-config">
                      <div class="headers-list">
                        <div v-for="(header, index) in sttHeadersList" :key="index" class="header-item">
                          <el-input v-model="header.key" placeholder="请求头名称" size="small" @input="onSttHeaderChange"
                            class="header-key" :disabled="header.fromInstance" />
                          <el-input v-model="header.value" placeholder="请求头值" size="small" @input="onSttHeaderChange"
                            class="header-value"
                            :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                            :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')" />
                          <el-tag v-if="header.fromInstance" type="success" size="small" class="instance-tag">
                            实例
                          </el-tag>
                          <el-button v-else type="danger" size="small" @click="removeSttHeader(index)"
                            class="remove-btn">
                            <el-icon>
                              <Close />
                            </el-icon>
                          </el-button>
                        </div>
                      </div>
                      <el-button type="primary" size="small" @click="addSttHeader" class="add-header-btn">
                        <el-icon>
                          <Plus />
                        </el-icon>
                        添加请求头
                      </el-button>
                    </div>
                  </el-form-item>

                  <el-form-item label="音频文件" prop="file" required>
                    <el-upload ref="sttUploadRef" class="upload-demo" drag :auto-upload="false" :limit="1"
                      accept="audio/*,.mp3,.wav,.m4a,.flac,.opus" :on-change="onSttFileChange"
                      :on-remove="onSttFileRemove" :file-list="sttFileList">
                      <el-icon class="el-icon--upload">
                        <UploadFilled />
                      </el-icon>
                      <div class="el-upload__text">
                        将音频文件拖到此处，或<em>点击上传</em>
                      </div>
                      <template #tip>
                        <div class="el-upload__tip">
                          支持 mp3, wav, m4a, flac, opus 格式，文件大小不超过 25MB
                        </div>
                      </template>
                    </el-upload>
                  </el-form-item>

                  <el-form-item label="语言">
                    <el-select v-model="sttConfig.language" placeholder="选择语言（可选）" clearable>
                      <el-option label="自动检测" value="" />
                      <el-option label="中文" value="zh" />
                      <el-option label="英文" value="en" />
                      <el-option label="日文" value="ja" />
                      <el-option label="韩文" value="ko" />
                      <el-option label="法文" value="fr" />
                      <el-option label="德文" value="de" />
                      <el-option label="西班牙文" value="es" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="提示词">
                    <el-input v-model="sttConfig.prompt" type="textarea" :rows="2" placeholder="可选的提示词，用于指导转录风格"
                      maxlength="244" show-word-limit clearable />
                  </el-form-item>

                  <el-form-item label="响应格式">
                    <el-select v-model="sttConfig.responseFormat" placeholder="选择响应格式">
                      <el-option label="JSON" value="json" />
                      <el-option label="文本" value="text" />
                      <el-option label="SRT" value="srt" />
                      <el-option label="VTT" value="vtt" />
                      <el-option label="详细JSON" value="verbose_json" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="温度">
                    <el-slider v-model="sttConfig.temperature" :min="0" :max="1" :step="0.1" show-input
                      :show-input-controls="false" />
                    <div class="temperature-hint">范围: 0 - 1，默认: 0</div>
                  </el-form-item>

                  <el-form-item>
                    <el-button type="primary" @click="sendSttRequest" :loading="sttLoading"
                      :disabled="!canSendSttRequest">
                      <el-icon>
                        <VideoPlay />
                      </el-icon>
                      转录音频
                    </el-button>
                    <el-button @click="resetSttForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>

            <el-col :span="12">
              <el-card header="转录结果" class="result-card">
                <div class="stt-result">
                  <div v-if="sttLoading" class="loading-state">
                    <el-icon class="is-loading">
                      <Loading />
                    </el-icon>
                    <p>正在转录音频...</p>
                  </div>

                  <div v-else-if="sttError" class="error-state">
                    <el-alert :title="sttError" type="error" show-icon :closable="false" />
                  </div>

                  <div v-else-if="sttResult" class="transcription-result">
                    <div class="result-info">
                      <el-tag type="success">
                        <el-icon>
                          <Check />
                        </el-icon>
                        转录完成
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ sttDuration }}ms
                      </span>
                    </div>

                    <div class="transcription-text">
                      <el-input v-model="sttResult.text" type="textarea" :rows="6" readonly class="result-textarea" />
                    </div>

                    <div class="result-actions">
                      <el-button type="primary" size="small" @click="copySttResult">
                        <el-icon>
                          <CopyDocument />
                        </el-icon>
                        复制文本
                      </el-button>
                      <el-button size="small" @click="clearSttResult">
                        <el-icon>
                          <Delete />
                        </el-icon>
                        清除结果
                      </el-button>
                    </div>

                    <!-- 详细信息（如果有） -->
                    <div v-if="sttResult.segments || sttResult.language" class="result-details">
                      <el-collapse>
                        <el-collapse-item title="详细信息" name="details">
                          <div v-if="sttResult.language" class="detail-item">
                            <strong>检测语言:</strong> {{ sttResult.language }}
                          </div>
                          <div v-if="sttResult.duration" class="detail-item">
                            <strong>音频时长:</strong> {{ sttResult.duration }}秒
                          </div>
                          <div v-if="sttResult.segments" class="segments">
                            <strong>分段信息:</strong>
                            <div v-for="(segment, index) in sttResult.segments" :key="index" class="segment">
                              <span class="segment-time">[{{ segment.start }}s - {{ segment.end }}s]</span>
                              <span class="segment-text">{{ segment.text }}</span>
                            </div>
                          </div>
                        </el-collapse-item>
                      </el-collapse>
                    </div>
                  </div>

                  <div v-else class="empty-state">
                    <el-empty description="请上传音频文件并开始转录" />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, inject, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile, type UploadFiles } from 'element-plus'
import {
  UploadFilled,
  Microphone,
  VideoPlay,
  Loading,
  Check,
  Download,
  Delete,
  CopyDocument,
  Refresh,
  Plus,
  Close
} from '@element-plus/icons-vue'
import { sendUniversalRequest } from '@/api/universal'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import type {
  TtsRequestConfig,
  SttRequestConfig,
  GlobalConfig,
  PlaygroundResponse,
  TabState,
  ValidationRules,
  SERVICE_ENDPOINTS
} from '../types/playground'
import { DEFAULT_CONFIGS } from '../types/playground'

// Props
interface Props {
  globalConfig?: GlobalConfig
}

const props = withDefaults(defineProps<Props>(), {
  globalConfig: () => ({ authorization: '', customHeaders: {} })
})

// Emits
const emit = defineEmits<{
  response: [response: PlaygroundResponse | null, loading?: boolean, error?: string | null]
  request: [request: any | null, size?: number]
}>()

// 注入全局状态
const tabState = inject<TabState>('tabState')

// 响应式数据
const activeAudioTab = ref<'tts' | 'stt'>('tts')

// TTS 相关状态
const ttsFormRef = ref<FormInstance>()
const ttsConfig = ref<TtsRequestConfig>({
  model: '',
  input: '',
  voice: 'alloy',
  responseFormat: 'mp3',
  speed: 1.0
})

// TTS表单数据（包含所有需要验证的字段）
const ttsFormData = reactive({
  selectedInstanceId: '',
  model: ttsConfig.value.model,
  input: ttsConfig.value.input,
  voice: ttsConfig.value.voice,
  responseFormat: ttsConfig.value.responseFormat,
  speed: ttsConfig.value.speed
})
const ttsLoading = ref(false)
const ttsError = ref<string | null>(null)
const ttsAudioUrl = ref<string | null>(null)

// 使用优化后的数据管理 - TTS
const {
  availableInstances: availableTtsInstances,
  instancesLoading: ttsInstancesLoading,
  selectedInstanceId: selectedTtsInstanceId,
  selectedInstanceInfo: selectedTtsInstanceInfo,
  hasInstances: hasTtsInstances,
  onInstanceChange: handleTtsInstanceChange,
  initializeData: initializeTtsData,
  refreshData: refreshTtsData,
  fetchInstances: fetchTtsInstances
} = usePlaygroundData('tts')
const ttsHeadersList = ref<Array<{ key: string, value: string, fromInstance: boolean }>>([])
const currentTtsHeaders = ref<Record<string, string>>({})
const ttsAudioBlob = ref<Blob | null>(null)
const ttsDuration = ref(0)
const ttsAudioRef = ref<HTMLAudioElement>()

// STT 相关状态
const sttFormRef = ref<FormInstance>()
const sttConfig = ref<Omit<SttRequestConfig, 'file'> & { file?: File }>({
  model: '',
  language: '',
  prompt: '',
  responseFormat: 'json',
  temperature: 0
})

// STT表单数据（包含所有需要验证的字段）
const sttFormData = reactive({
  selectedInstanceId: '',
  model: sttConfig.value.model,
  file: sttConfig.value.file,
  language: sttConfig.value.language,
  prompt: sttConfig.value.prompt,
  responseFormat: sttConfig.value.responseFormat,
  temperature: sttConfig.value.temperature
})
const sttLoading = ref(false)
const sttError = ref<string | null>(null)
const sttResult = ref<any>(null)

// 使用优化后的数据管理 - STT
const {
  availableInstances: availableSttInstances,
  instancesLoading: sttInstancesLoading,
  selectedInstanceId: selectedSttInstanceId,
  selectedInstanceInfo: selectedSttInstanceInfo,
  hasInstances: hasSttInstances,
  onInstanceChange: handleSttInstanceChange,
  initializeData: initializeSttData,
  refreshData: refreshSttData,
  fetchInstances: fetchSttInstances
} = usePlaygroundData('stt')
const sttHeadersList = ref<Array<{ key: string, value: string, fromInstance: boolean }>>([])
const currentSttHeaders = ref<Record<string, string>>({})
const sttDuration = ref(0)
const sttUploadRef = ref()
const sttFileList = ref<UploadFile[]>([])

// 表单验证规则
const ttsRules: ValidationRules = {
  selectedInstanceId: [
    {
      required: true,
      message: '请选择一个TTS服务实例',
      trigger: 'change',
      validator: (_rule: any, value: string, callback: Function) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个TTS服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  input: [
    { required: true, message: '请输入要转换的文本', trigger: 'blur' },
    { min: 1, max: 4096, message: '文本长度应在 1-4096 字符之间', trigger: 'blur' }
  ]
}

const sttRules: ValidationRules = {
  selectedInstanceId: [
    {
      required: true,
      message: '请选择一个STT服务实例',
      trigger: 'change',
      validator: (_rule: any, value: string, callback: Function) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个STT服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  file: [
    { required: true, message: '请上传音频文件', trigger: 'change' }
  ]
}

// 计算属性
const canSendTtsRequest = computed(() => {
  return ttsFormData.selectedInstanceId &&
    ttsFormData.input &&
    ttsFormData.input.trim() !== '' &&
    !ttsLoading.value
})

const canSendSttRequest = computed(() => {
  return sttFormData.selectedInstanceId &&
    sttFormData.file &&
    !sttLoading.value
})

// 选项卡切换处理
const onAudioTabChange = (tabName: string) => {
  activeAudioTab.value = tabName as 'tts' | 'stt'
  if (tabState) {
    tabState.activeAudioTab = activeAudioTab.value
  }

  // 清除之前的错误和结果
  clearTtsResult()
  clearSttResult()
}

// TTS实例选择变化处理（扩展 composable 的功能）
const onTtsInstanceChange = (instanceId: string) => {
  // 调用 composable 的处理函数
  handleTtsInstanceChange(instanceId)
  
  // 同步到formData
  ttsFormData.selectedInstanceId = instanceId

  if (!instanceId) {
    ttsConfig.value.model = ''
    ttsFormData.model = ''
    ttsHeadersList.value = []
    currentTtsHeaders.value = {}
    return
  }

  const instance = availableTtsInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    // 使用实例名称作为默认模型
    ttsConfig.value.model = instance.name || 'default-model'
    ttsFormData.model = instance.name || 'default-model'

    // 初始化请求头列表
    initializeTtsHeaders(instance.headers || {})

    ElMessage.success(`已选择实例 "${instance.name}"`)
  }
}

// 初始化TTS请求头列表
const initializeTtsHeaders = (instanceHeaders: Record<string, string>) => {
  ttsHeadersList.value = []
  currentTtsHeaders.value = {}

  // 添加实例的请求头（标记为来自实例，不可删除）
  Object.entries(instanceHeaders).forEach(([key, value]) => {
    ttsHeadersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentTtsHeaders.value[key] = value
  })

  // 添加全局配置中的自定义请求头
  Object.entries(props.globalConfig.customHeaders || {}).forEach(([key, value]) => {
    if (!currentTtsHeaders.value[key]) {
      ttsHeadersList.value.push({
        key,
        value,
        fromInstance: false
      })
      currentTtsHeaders.value[key] = value
    }
  })
}

// 添加TTS请求头
const addTtsHeader = () => {
  ttsHeadersList.value.push({
    key: '',
    value: '',
    fromInstance: false
  })
}

// 删除TTS请求头
const removeTtsHeader = (index: number) => {
  const header = ttsHeadersList.value[index]
  if (!header.fromInstance) {
    ttsHeadersList.value.splice(index, 1)
    onTtsHeaderChange()
  }
}

// TTS请求头变化处理
const onTtsHeaderChange = () => {
  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}

  ttsHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      newHeaders[header.key.trim()] = header.value.trim()
    }
  })

  currentTtsHeaders.value = newHeaders
}

// STT实例选择变化处理（扩展 composable 的功能）
const onSttInstanceChange = (instanceId: string) => {
  // 调用 composable 的处理函数
  handleSttInstanceChange(instanceId)
  
  // 同步到formData
  sttFormData.selectedInstanceId = instanceId

  if (!instanceId) {
    sttConfig.value.model = ''
    sttFormData.model = ''
    sttHeadersList.value = []
    currentSttHeaders.value = {}
    return
  }

  const instance = availableSttInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    // 使用实例名称作为默认模型
    sttConfig.value.model = instance.name || 'default-model'
    sttFormData.model = instance.name || 'default-model'

    // 初始化请求头列表
    initializeSttHeaders(instance.headers || {})

    ElMessage.success(`已选择实例 "${instance.name}"`)
  }
}

// 初始化STT请求头列表
const initializeSttHeaders = (instanceHeaders: Record<string, string>) => {
  sttHeadersList.value = []
  currentSttHeaders.value = {}

  // 添加实例的请求头（标记为来自实例，不可删除）
  Object.entries(instanceHeaders).forEach(([key, value]) => {
    sttHeadersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentSttHeaders.value[key] = value
  })

  // 添加全局配置中的自定义请求头
  Object.entries(props.globalConfig.customHeaders || {}).forEach(([key, value]) => {
    if (!currentSttHeaders.value[key]) {
      sttHeadersList.value.push({
        key,
        value,
        fromInstance: false
      })
      currentSttHeaders.value[key] = value
    }
  })
}

// 添加STT请求头
const addSttHeader = () => {
  sttHeadersList.value.push({
    key: '',
    value: '',
    fromInstance: false
  })
}

// 删除STT请求头
const removeSttHeader = (index: number) => {
  const header = sttHeadersList.value[index]
  if (!header.fromInstance) {
    sttHeadersList.value.splice(index, 1)
    onSttHeaderChange()
  }
}

// STT请求头变化处理
const onSttHeaderChange = () => {
  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}

  sttHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      newHeaders[header.key.trim()] = header.value.trim()
    }
  })

  currentSttHeaders.value = newHeaders
}

// TTS 相关方法
const sendTtsRequest = async () => {
  if (!ttsFormRef.value) return

  // 检查是否选择了实例
  if (!ttsFormData.selectedInstanceId) {
    ElMessage.error('请选择一个TTS服务实例')
    return
  }

  try {
    const valid = await ttsFormRef.value.validate()
    if (!valid) return
  } catch (error) {
    console.log('表单验证失败:', error)
    return
  }

  ttsLoading.value = true
  ttsError.value = null
  emit('response', null, true)

  try {
    const requestBody = {
      model: ttsConfig.value.model,
      input: ttsConfig.value.input,
      voice: ttsConfig.value.voice,
      response_format: ttsConfig.value.responseFormat,
      speed: ttsConfig.value.speed
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...currentTtsHeaders.value
    }

    const ttsRequest = {
      endpoint: '/v1/audio/speech',
      method: 'POST',
      headers,
      body: requestBody
    }

    // 发送request事件
    const requestSize = JSON.stringify(requestBody).length
    emit('request', ttsRequest, requestSize)

    const response = await sendUniversalRequest(ttsRequest)

    ttsDuration.value = response.duration

    // 处理音频响应
    const actualData = getActualData(response.data)
    if (actualData instanceof Blob) {
      ttsAudioBlob.value = actualData
      ttsAudioUrl.value = URL.createObjectURL(actualData)
    } else if (actualData && actualData.audio_url) {
      ttsAudioUrl.value = actualData.audio_url
    } else {
      throw new Error('未收到有效的音频数据')
    }

    emit('response', response)
    ElMessage.success('语音生成成功')

  } catch (error: any) {
    console.error('TTS request failed:', error)
    ttsError.value = error.data?.error?.message || error.message || '语音生成失败'
    emit('response', null, false, ttsError.value)
    ElMessage.error(ttsError.value || '语音生成失败')
  } finally {
    ttsLoading.value = false
  }
}

const downloadTtsAudio = () => {
  if (!ttsAudioBlob.value && !ttsAudioUrl.value) return

  const link = document.createElement('a')
  link.href = ttsAudioUrl.value!
  link.download = `tts_audio_${Date.now()}.${ttsConfig.value.responseFormat}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const clearTtsResult = () => {
  if (ttsAudioUrl.value) {
    URL.revokeObjectURL(ttsAudioUrl.value)
  }
  ttsAudioUrl.value = null
  ttsAudioBlob.value = null
  ttsError.value = null
  ttsDuration.value = 0
}

const resetTtsForm = () => {
  clearTtsResult()
  Object.assign(ttsConfig.value, {
    ...DEFAULT_CONFIGS.tts,
    model: '',
    input: ''
  })
  ttsFormRef.value?.clearValidate()
}

// STT 相关方法
const onSttFileChange = (file: UploadFile, fileList: UploadFiles) => {
  if (file.raw) {
    // 检查文件大小（25MB限制）
    const maxSize = 25 * 1024 * 1024
    if (file.raw.size > maxSize) {
      ElMessage.error('文件大小不能超过 25MB')
      sttFileList.value = []
      sttConfig.value.file = undefined
      return
    }

    sttConfig.value.file = file.raw
    sttFileList.value = [file]
  }
}

const onSttFileRemove = () => {
  sttConfig.value.file = undefined
  sttFileList.value = []
}

const sendSttRequest = async () => {
  if (!sttFormRef.value) return

  // 检查是否选择了实例
  if (!sttFormData.selectedInstanceId) {
    ElMessage.error('请选择一个STT服务实例')
    return
  }

  try {
    const valid = await sttFormRef.value.validate()
    if (!valid) return
  } catch (error) {
    console.log('表单验证失败:', error)
    return
  }

  if (!sttConfig.value.file) {
    ElMessage.error('请先上传音频文件')
    return
  }

  sttLoading.value = true
  sttError.value = null
  emit('response', null, true)

  try {
    const requestBody: any = {
      model: sttConfig.value.model,
      response_format: sttConfig.value.responseFormat,
      temperature: sttConfig.value.temperature
    }

    if (sttConfig.value.language) {
      requestBody.language = sttConfig.value.language
    }

    if (sttConfig.value.prompt) {
      requestBody.prompt = sttConfig.value.prompt
    }

    const headers: Record<string, string> = {
      ...currentSttHeaders.value
    }

    const sttRequest = {
      endpoint: '/v1/audio/transcriptions',
      method: 'POST',
      headers,
      body: requestBody,
      files: [sttConfig.value.file]
    }

    // 发送request事件
    const requestSize = JSON.stringify(requestBody).length + (sttConfig.value.file?.size || 0)
    emit('request', sttRequest, requestSize)

    const response = await sendUniversalRequest(sttRequest)

    sttDuration.value = response.duration
    sttResult.value = getActualData(response.data)

    emit('response', response)
    ElMessage.success('音频转录成功')

  } catch (error: any) {
    console.error('STT request failed:', error)
    sttError.value = error.data?.error?.message || error.message || '音频转录失败'
    emit('response', null, false, sttError.value)
    ElMessage.error(sttError.value || '音频转录失败')
  } finally {
    sttLoading.value = false
  }
}

const copySttResult = async () => {
  if (!sttResult.value?.text) return

  try {
    await navigator.clipboard.writeText(sttResult.value.text)
    ElMessage.success('转录文本已复制到剪贴板')
  } catch {
    // 降级方案
    const textArea = document.createElement('textarea')
    textArea.value = sttResult.value.text
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    ElMessage.success('转录文本已复制到剪贴板')
  }
}

const clearSttResult = () => {
  sttResult.value = null
  sttError.value = null
  sttDuration.value = 0
}

const resetSttForm = () => {
  clearSttResult()
  Object.assign(sttConfig.value, {
    ...DEFAULT_CONFIGS.stt,
    model: '',
    file: undefined
  })
  sttFileList.value = []
  sttUploadRef.value?.clearFiles()
  sttFormRef.value?.clearValidate()
}

// 初始化
if (tabState?.activeAudioTab) {
  activeAudioTab.value = tabState.activeAudioTab
}

// 获取实际的数据对象（处理包装格式）
const getActualData = (responseData: any) => {
  if (!responseData) return null

  // 对于Blob数据，直接返回
  if (responseData instanceof Blob) {
    return responseData
  }

  // 检查是否是包装的响应格式 (有success, message, data字段)
  if (responseData.success !== undefined && responseData.data) {
    return responseData.data
  }

  return responseData
}

// 同步 ttsConfig 和 ttsFormData
watch(() => ttsConfig.value.input, (newInput) => {
  ttsFormData.input = newInput
})

watch(() => ttsConfig.value.voice, (newVoice) => {
  ttsFormData.voice = newVoice
})

watch(() => ttsConfig.value.responseFormat, (newResponseFormat) => {
  ttsFormData.responseFormat = newResponseFormat
})

watch(() => ttsConfig.value.speed, (newSpeed) => {
  ttsFormData.speed = newSpeed
})

// 同步 sttConfig 和 sttFormData
watch(() => sttConfig.value.file, (newFile) => {
  sttFormData.file = newFile
})

watch(() => sttConfig.value.language, (newLanguage) => {
  sttFormData.language = newLanguage
})

watch(() => sttConfig.value.prompt, (newPrompt) => {
  sttFormData.prompt = newPrompt
})

watch(() => sttConfig.value.responseFormat, (newResponseFormat) => {
  sttFormData.responseFormat = newResponseFormat
})

watch(() => sttConfig.value.temperature, (newTemperature) => {
  sttFormData.temperature = newTemperature
})

// 监听全局刷新模型事件
const handleRefreshModels = () => {
  fetchTtsInstances()
  fetchSttInstances()
}

// 组件挂载时获取实例列表
onMounted(() => {
  // 初始化数据（使用缓存，静默加载）
  initializeTtsData()
  initializeSttData()

  // 同步初始状态
  ttsFormData.selectedInstanceId = selectedTtsInstanceId.value
  ttsFormData.input = ttsConfig.value.input
  ttsFormData.voice = ttsConfig.value.voice
  ttsFormData.responseFormat = ttsConfig.value.responseFormat
  ttsFormData.speed = ttsConfig.value.speed

  sttFormData.selectedInstanceId = selectedSttInstanceId.value
  sttFormData.file = sttConfig.value.file
  sttFormData.language = sttConfig.value.language
  sttFormData.prompt = sttConfig.value.prompt
  sttFormData.responseFormat = sttConfig.value.responseFormat
  sttFormData.temperature = sttConfig.value.temperature

  // 监听全局刷新事件
  document.addEventListener('playground-refresh-models', handleGlobalRefresh)
})

onUnmounted(() => {
  // 清理事件监听器
  document.removeEventListener('playground-refresh-models', handleGlobalRefresh)
})

// 处理全局刷新事件
const handleGlobalRefresh = () => {
  refreshTtsData()
  refreshSttData()
}
</script>

<style scoped>
.audio-playground {
  padding: 20px;
  height: 100%;
  overflow: auto;
}

.model-select-container {
  display: flex;
  gap: 8px;
  align-items: center;
}

.model-select-container .el-select {
  flex: 1;
}

.refresh-btn {
  flex-shrink: 0;
}

.service-content {
  height: 100%;
}

.config-card,
.result-card {
  height: 100%;
}

.config-card :deep(.el-card__body) {
  height: calc(100% - 60px);
  overflow: auto;
}

/* 让请求头配置占满宽度 */
.headers-config {
  width: 100%;
}

.headers-list {
  width: 100%;
}

.header-item {
  width: 100%;
}

/* 让表单项占满宽度 */
.el-form-item__content {
  width: 100% !important;
}

/* 让选择器和输入框占满宽度 */
.el-select,
.el-input,
.el-textarea {
  width: 100% !important;
}

.result-card :deep(.el-card__body) {
  height: calc(100% - 60px);
  overflow: auto;
}

/* TTS 样式 */
.speed-hint,
.temperature-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 5px;
}

.audio-result {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.loading-state,
.error-state,
.empty-state {
  text-align: center;
  padding: 40px 20px;
}

.loading-state .el-icon {
  font-size: 32px;
  color: var(--el-color-primary);
  margin-bottom: 16px;
}

.loading-state p {
  color: var(--el-text-color-secondary);
  margin: 0;
}

.audio-player {
  padding: 20px;
}

.audio-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.duration-info {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.audio-control {
  width: 100%;
  margin-bottom: 20px;
}

.audio-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* STT 样式 */
.upload-demo {
  width: 100%;
}

.upload-demo :deep(.el-upload-dragger) {
  width: 100%;
}

.stt-result {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.transcription-result {
  padding: 20px;
}

.result-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.transcription-text {
  margin-bottom: 20px;
}

.result-textarea :deep(.el-textarea__inner) {
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  line-height: 1.6;
}

.result-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-bottom: 20px;
}

.result-details {
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 20px;
}

.detail-item {
  margin-bottom: 10px;
  font-size: 14px;
}

.segments {
  margin-top: 10px;
}

.segment {
  display: flex;
  margin-bottom: 8px;
  padding: 8px;
  background-color: var(--el-bg-color-page);
  border-radius: 4px;
  font-size: 13px;
}

.segment-time {
  color: var(--el-color-primary);
  font-weight: 500;
  margin-right: 10px;
  min-width: 120px;
}

.segment-text {
  flex: 1;
  line-height: 1.4;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .audio-playground {
    padding: 15px;
  }
}

@media (max-width: 768px) {
  .audio-playground {
    padding: 10px;
  }

  .service-content .el-row {
    flex-direction: column;
  }

  .service-content .el-col {
    width: 100% !important;
    margin-bottom: 20px;
  }

  .audio-info,
  .result-info {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .audio-actions,
  .result-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .result-textarea :deep(.el-textarea__inner) {
    background-color: var(--el-bg-color-overlay);
    color: var(--el-text-color-primary);
  }

  .segment {
    background-color: var(--el-bg-color-overlay);
  }
}

/* 动画效果 */
.loading-state .el-icon.is-loading {
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

/* 表单样式优化 */
.el-form-item {
  margin-bottom: 20px;
}

.el-form-item:last-child {
  margin-bottom: 0;
}

/* 上传组件样式 */
.upload-demo :deep(.el-upload__tip) {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 滑块样式 */
.el-slider {
  margin: 10px 0;
}

/* 选择器样式 */
.el-select {
  width: 100%;
}

/* 按钮组样式 */
.el-form-item .el-button+.el-button {
  margin-left: 10px;
}
</style>