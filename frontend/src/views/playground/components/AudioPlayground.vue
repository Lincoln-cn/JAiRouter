<template>
  <div class="audio-playground">
    <el-tabs v-model="activeAudioTab" type="border-card" @tab-change="onAudioTabChange">
      <!-- TTS 选项卡 -->
      <el-tab-pane label="文本转语音 (TTS)" name="tts">
        <div class="service-content">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card header="TTS 配置" class="config-card">
                <el-form 
                  ref="ttsFormRef"
                  :model="ttsConfig" 
                  :rules="ttsRules"
                  label-width="120px"
                  @submit.prevent="sendTtsRequest"
                >
                  <el-form-item label="模型" prop="model" required>
                    <div class="model-select-container">
                      <el-select 
                        v-model="ttsConfig.model" 
                        placeholder="请选择TTS模型"
                        filterable
                        allow-create
                        style="width: 100%"
                        :loading="ttsModelsLoading"
                      >
                        <el-option
                          v-for="model in ttsAvailableModels"
                          :key="model"
                          :label="model"
                          :value="model"
                        />
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ ttsModelsLoading ? '加载中...' : '暂无可用模型，请先在实例管理中添加TTS服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button 
                        type="info" 
                        size="small" 
                        @click="fetchTtsModels"
                        class="refresh-btn"
                        :loading="ttsModelsLoading"
                        title="刷新TTS模型列表"
                      >
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="输入文本" prop="input" required>
                    <el-input 
                      v-model="ttsConfig.input" 
                      type="textarea" 
                      :rows="4"
                      placeholder="请输入要转换为语音的文本"
                      maxlength="4096"
                      show-word-limit
                      clearable
                    />
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
                    <el-slider 
                      v-model="ttsConfig.speed" 
                      :min="0.25" 
                      :max="4.0" 
                      :step="0.25"
                      show-input
                      :show-input-controls="false"
                    />
                    <div class="speed-hint">范围: 0.25 - 4.0，默认: 1.0</div>
                  </el-form-item>
                  
                  <el-form-item>
                    <el-button 
                      type="primary" 
                      @click="sendTtsRequest"
                      :loading="ttsLoading"
                      :disabled="!canSendTtsRequest"
                    >
                      <el-icon><Microphone /></el-icon>
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
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <p>正在生成语音...</p>
                  </div>
                  
                  <div v-else-if="ttsError" class="error-state">
                    <el-alert 
                      :title="ttsError" 
                      type="error" 
                      show-icon 
                      :closable="false"
                    />
                  </div>
                  
                  <div v-else-if="ttsAudioUrl" class="audio-player">
                    <div class="audio-info">
                      <el-tag type="success">
                        <el-icon><Check /></el-icon>
                        语音生成成功
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ ttsDuration }}ms
                      </span>
                    </div>
                    
                    <audio 
                      ref="ttsAudioRef"
                      :src="ttsAudioUrl" 
                      controls 
                      preload="metadata"
                      class="audio-control"
                    >
                      您的浏览器不支持音频播放
                    </audio>
                    
                    <div class="audio-actions">
                      <el-button 
                        type="primary" 
                        size="small"
                        @click="downloadTtsAudio"
                      >
                        <el-icon><Download /></el-icon>
                        下载音频
                      </el-button>
                      <el-button 
                        size="small"
                        @click="clearTtsResult"
                      >
                        <el-icon><Delete /></el-icon>
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
                <el-form 
                  ref="sttFormRef"
                  :model="sttConfig" 
                  :rules="sttRules"
                  label-width="120px"
                  @submit.prevent="sendSttRequest"
                >
                  <el-form-item label="模型" prop="model" required>
                    <div class="model-select-container">
                      <el-select 
                        v-model="sttConfig.model" 
                        placeholder="请选择STT模型"
                        filterable
                        allow-create
                        style="width: 100%"
                        :loading="sttModelsLoading"
                      >
                        <el-option
                          v-for="model in sttAvailableModels"
                          :key="model"
                          :label="model"
                          :value="model"
                        />
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ sttModelsLoading ? '加载中...' : '暂无可用模型，请先在实例管理中添加STT服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button 
                        type="info" 
                        size="small" 
                        @click="fetchSttModels"
                        class="refresh-btn"
                        :loading="sttModelsLoading"
                        title="刷新STT模型列表"
                      >
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="音频文件" prop="file" required>
                    <el-upload
                      ref="sttUploadRef"
                      class="upload-demo"
                      drag
                      :auto-upload="false"
                      :limit="1"
                      accept="audio/*,.mp3,.wav,.m4a,.flac,.opus"
                      :on-change="onSttFileChange"
                      :on-remove="onSttFileRemove"
                      :file-list="sttFileList"
                    >
                      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
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
                    <el-select 
                      v-model="sttConfig.language" 
                      placeholder="选择语言（可选）"
                      clearable
                    >
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
                    <el-input 
                      v-model="sttConfig.prompt" 
                      type="textarea" 
                      :rows="2"
                      placeholder="可选的提示词，用于指导转录风格"
                      maxlength="244"
                      show-word-limit
                      clearable
                    />
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
                    <el-slider 
                      v-model="sttConfig.temperature" 
                      :min="0" 
                      :max="1" 
                      :step="0.1"
                      show-input
                      :show-input-controls="false"
                    />
                    <div class="temperature-hint">范围: 0 - 1，默认: 0</div>
                  </el-form-item>
                  
                  <el-form-item>
                    <el-button 
                      type="primary" 
                      @click="sendSttRequest"
                      :loading="sttLoading"
                      :disabled="!canSendSttRequest"
                    >
                      <el-icon><VideoPlay /></el-icon>
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
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <p>正在转录音频...</p>
                  </div>
                  
                  <div v-else-if="sttError" class="error-state">
                    <el-alert 
                      :title="sttError" 
                      type="error" 
                      show-icon 
                      :closable="false"
                    />
                  </div>
                  
                  <div v-else-if="sttResult" class="transcription-result">
                    <div class="result-info">
                      <el-tag type="success">
                        <el-icon><Check /></el-icon>
                        转录完成
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ sttDuration }}ms
                      </span>
                    </div>
                    
                    <div class="transcription-text">
                      <el-input 
                        v-model="sttResult.text" 
                        type="textarea" 
                        :rows="6"
                        readonly
                        class="result-textarea"
                      />
                    </div>
                    
                    <div class="result-actions">
                      <el-button 
                        type="primary" 
                        size="small"
                        @click="copySttResult"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        复制文本
                      </el-button>
                      <el-button 
                        size="small"
                        @click="clearSttResult"
                      >
                        <el-icon><Delete /></el-icon>
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
import { ref, reactive, computed, inject, nextTick, onMounted } from 'vue'
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
  Refresh 
} from '@element-plus/icons-vue'
import { sendUniversalRequest } from '@/api/universal'
import { getModelsByServiceType, getInstanceServiceType } from '@/api/models'
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
const ttsLoading = ref(false)
const ttsError = ref<string | null>(null)
const ttsAudioUrl = ref<string | null>(null)

// TTS模型列表
const ttsAvailableModels = ref<string[]>([])
const ttsModelsLoading = ref(false)
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
const sttLoading = ref(false)
const sttError = ref<string | null>(null)
const sttResult = ref<any>(null)

// STT模型列表
const sttAvailableModels = ref<string[]>([])
const sttModelsLoading = ref(false)
const sttDuration = ref(0)
const sttUploadRef = ref()
const sttFileList = ref<UploadFile[]>([])

// 表单验证规则
const ttsRules: ValidationRules = {
  model: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  input: [
    { required: true, message: '请输入要转换的文本', trigger: 'blur' },
    { min: 1, max: 4096, message: '文本长度应在 1-4096 字符之间', trigger: 'blur' }
  ]
}

const sttRules: ValidationRules = {
  model: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  file: [
    { required: true, message: '请上传音频文件', trigger: 'change' }
  ]
}

// 计算属性
const canSendTtsRequest = computed(() => {
  return ttsConfig.value.model && 
         ttsConfig.value.input && 
         !ttsLoading.value
})

const canSendSttRequest = computed(() => {
  return sttConfig.value.model && 
         sttConfig.value.file && 
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

// 获取TTS模型列表
const fetchTtsModels = async () => {
  ttsModelsLoading.value = true
  try {
    const serviceType = getInstanceServiceType('tts')
    const models = await getModelsByServiceType(serviceType)
    ttsAvailableModels.value = models
  } catch (error) {
    console.error('获取TTS模型列表失败:', error)
    ElMessage.error('获取TTS模型列表失败')
  } finally {
    ttsModelsLoading.value = false
  }
}

// 获取STT模型列表
const fetchSttModels = async () => {
  sttModelsLoading.value = true
  try {
    const serviceType = getInstanceServiceType('stt')
    const models = await getModelsByServiceType(serviceType)
    sttAvailableModels.value = models
  } catch (error) {
    console.error('获取STT模型列表失败:', error)
    ElMessage.error('获取STT模型列表失败')
  } finally {
    sttModelsLoading.value = false
  }
}

// TTS 相关方法
const sendTtsRequest = async () => {
  if (!ttsFormRef.value) return
  
  try {
    const valid = await ttsFormRef.value.validate()
    if (!valid) return
  } catch {
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
      ...props.globalConfig.customHeaders
    }
    
    if (props.globalConfig.authorization) {
      headers['Authorization'] = props.globalConfig.authorization
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
  
  try {
    const valid = await sttFormRef.value.validate()
    if (!valid) return
  } catch {
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
      ...props.globalConfig.customHeaders
    }
    
    if (props.globalConfig.authorization) {
      headers['Authorization'] = props.globalConfig.authorization
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

// 监听全局刷新模型事件
const handleRefreshModels = () => {
  fetchTtsModels()
  fetchSttModels()
}

// 组件挂载时获取模型列表
onMounted(() => {
  fetchTtsModels()
  fetchSttModels()
  
  // 监听全局刷新事件
  document.addEventListener('playground-refresh-models', handleRefreshModels)
})

// 组件卸载时清理资源
import { onUnmounted } from 'vue'
onUnmounted(() => {
  clearTtsResult()
  document.removeEventListener('playground-refresh-models', handleRefreshModels)
})
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
.el-form-item .el-button + .el-button {
  margin-left: 10px;
}
</style>