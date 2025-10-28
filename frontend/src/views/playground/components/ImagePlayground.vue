<template>
  <div class="image-playground">
    <el-tabs v-model="activeImageTab" type="border-card" @tab-change="onImageTabChange">
      <!-- 图像生成选项卡 -->
      <el-tab-pane label="图像生成 (Generate)" name="generate">
        <div class="service-content">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card header="图像生成配置" class="config-card">
                <el-form 
                  ref="generateFormRef"
                  :model="generateFormData" 
                  :rules="generateRules"
                  label-width="120px"
                  @submit.prevent="sendGenerateRequest"
                >
                  <!-- 实例选择 -->
                  <el-form-item label="选择实例" prop="selectedInstanceId" required>
                    <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                      <el-select 
                        v-model="selectedGenerateInstanceId" 
                        placeholder="请选择图像生成服务实例"
                        @change="onGenerateInstanceChange"
                        style="flex: 1"
                        :loading="generateInstancesLoading"
                        clearable
                      >
                        <el-option
                          v-for="instance in availableGenerateInstances"
                          :key="instance.instanceId"
                          :label="instance.name"
                          :value="instance.instanceId"
                        >
                          <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>{{ instance.name }}</span>
                            <el-tag 
                              v-if="instance.headers && Object.keys(instance.headers).length > 0"
                              type="success" 
                              size="small"
                            >
                              {{ Object.keys(instance.headers).length }} 个请求头
                            </el-tag>
                          </div>
                        </el-option>
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ generateInstancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加图像生成服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button 
                        type="info" 
                        size="small" 
                        @click="fetchGenerateInstances"
                        :loading="generateInstancesLoading"
                        title="刷新实例列表"
                      >
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </div>
                    <div v-if="!selectedGenerateInstanceId" class="instance-hint">
                      <el-text type="danger" size="small">请选择一个图像生成服务实例</el-text>
                    </div>
                    <div v-else-if="selectedGenerateInstanceInfo" class="instance-selected">
                      <el-text type="success" size="small">
                        <el-icon><Check /></el-icon>
                        已选择实例: {{ selectedGenerateInstanceInfo.name }}
                      </el-text>
                    </div>
                  </el-form-item>

                  <!-- 请求头配置 -->
                  <el-form-item v-if="selectedGenerateInstanceInfo" label="请求头配置">
                    <div class="headers-config">
                      <div class="headers-list">
                        <div 
                          v-for="(header, index) in generateHeadersList" 
                          :key="index"
                          class="header-item"
                        >
                          <el-input
                            v-model="header.key"
                            placeholder="请求头名称"
                            size="small"
                            @input="onGenerateHeaderChange"
                            class="header-key"
                            :disabled="header.fromInstance"
                          />
                          <el-input
                            v-model="header.value"
                            placeholder="请求头值"
                            size="small"
                            @input="onGenerateHeaderChange"
                            class="header-value"
                            :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                            :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')"
                          />
                          <el-tag 
                            v-if="header.fromInstance" 
                            type="success" 
                            size="small"
                            class="instance-tag"
                          >
                            实例
                          </el-tag>
                          <el-button 
                            v-else
                            type="danger" 
                            size="small" 
                            @click="removeGenerateHeader(index)"
                            class="remove-btn"
                          >
                            <el-icon><Close /></el-icon>
                          </el-button>
                        </div>
                      </div>
                      <el-button 
                        type="primary" 
                        size="small" 
                        @click="addGenerateHeader"
                        class="add-header-btn"
                      >
                        <el-icon><Plus /></el-icon>
                        添加请求头
                      </el-button>
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="提示词" prop="prompt" required>
                    <el-input 
                      v-model="generateConfig.prompt" 
                      type="textarea" 
                      :rows="4"
                      placeholder="请输入图像生成的提示词描述"
                      maxlength="4000"
                      show-word-limit
                      clearable
                    />
                  </el-form-item>
                  
                  <el-form-item label="图像数量">
                    <el-input-number 
                      v-model="generateConfig.n" 
                      :min="1" 
                      :max="10"
                      placeholder="生成图像数量"
                    />
                  </el-form-item>
                  
                  <el-form-item label="图像尺寸">
                    <el-select v-model="generateConfig.size" placeholder="选择图像尺寸">
                      <el-option label="256x256" value="256x256" />
                      <el-option label="512x512" value="512x512" />
                      <el-option label="1024x1024" value="1024x1024" />
                      <el-option label="1792x1024" value="1792x1024" />
                      <el-option label="1024x1792" value="1024x1792" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="图像质量">
                    <el-select v-model="generateConfig.quality" placeholder="选择图像质量">
                      <el-option label="标准" value="standard" />
                      <el-option label="高清" value="hd" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="图像风格">
                    <el-select v-model="generateConfig.style" placeholder="选择图像风格">
                      <el-option label="生动" value="vivid" />
                      <el-option label="自然" value="natural" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="响应格式">
                    <el-select v-model="generateConfig.responseFormat" placeholder="选择响应格式">
                      <el-option label="URL" value="url" />
                      <el-option label="Base64" value="b64_json" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="用户ID">
                    <el-input 
                      v-model="generateConfig.user" 
                      placeholder="可选的用户标识符"
                      clearable
                    />
                  </el-form-item>
                  
                  <el-form-item>
                    <el-button 
                      type="primary" 
                      @click="sendGenerateRequest"
                      :loading="generateLoading"
                      :disabled="!canSendGenerateRequest"
                    >
                      <el-icon><Picture /></el-icon>
                      生成图像
                    </el-button>
                    <el-button @click="resetGenerateForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>
            
            <el-col :span="12">
              <el-card header="生成结果" class="result-card">
                <div class="image-result">
                  <div v-if="generateLoading" class="loading-state">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <p>正在生成图像...</p>
                  </div>
                  
                  <div v-else-if="generateError" class="error-state">
                    <el-alert 
                      :title="generateError" 
                      type="error" 
                      show-icon 
                      :closable="false"
                    />
                  </div>
                  
                  <div v-else-if="generateImages.length > 0" class="image-gallery">
                    <div class="result-info">
                      <el-tag type="success">
                        <el-icon><Check /></el-icon>
                        图像生成成功
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ generateDuration }}ms
                      </span>
                    </div>
                    
                    <div class="images-grid">
                      <div 
                        v-for="(image, index) in generateImages" 
                        :key="index" 
                        class="image-item"
                      >
                        <div class="image-container">
                          <img 
                            :src="image.url || `data:image/png;base64,${image.b64_json}`" 
                            :alt="`Generated image ${index + 1}`"
                            class="generated-image"
                            @click="previewImage(image, index)"
                          />
                          <div class="image-overlay">
                            <el-button 
                              type="primary" 
                              size="small"
                              @click="downloadImage(image, index)"
                            >
                              <el-icon><Download /></el-icon>
                            </el-button>
                            <el-button 
                              type="info" 
                              size="small"
                              @click="previewImage(image, index)"
                            >
                              <el-icon><ZoomIn /></el-icon>
                            </el-button>
                          </div>
                        </div>
                        <div class="image-info">
                          <span class="image-index">图像 {{ index + 1 }}</span>
                          <span v-if="image.revised_prompt" class="revised-prompt">
                            修订提示词: {{ image.revised_prompt }}
                          </span>
                        </div>
                      </div>
                    </div>
                    
                    <div class="result-actions">
                      <el-button 
                        type="primary" 
                        @click="downloadAllImages"
                      >
                        <el-icon><Download /></el-icon>
                        下载全部
                      </el-button>
                      <el-button 
                        @click="clearGenerateResult"
                      >
                        <el-icon><Delete /></el-icon>
                        清除结果
                      </el-button>
                    </div>
                  </div>
                  
                  <div v-else class="empty-state">
                    <el-empty description="请配置参数并生成图像" />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
      
      <!-- 图像编辑选项卡 -->
      <el-tab-pane label="图像编辑 (Edit)" name="edit">
        <div class="service-content">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card header="图像编辑配置" class="config-card">
                <el-form 
                  ref="editFormRef"
                  :model="editFormData" 
                  :rules="editRules"
                  label-width="120px"
                  @submit.prevent="sendEditRequest"
                >
                  <!-- 实例选择 -->
                  <el-form-item label="选择实例" prop="selectedInstanceId" required>
                    <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                      <el-select 
                        v-model="selectedEditInstanceId" 
                        placeholder="请选择图像编辑服务实例"
                        @change="onEditInstanceChange"
                        style="flex: 1"
                        :loading="editInstancesLoading"
                        clearable
                      >
                        <el-option
                          v-for="instance in availableEditInstances"
                          :key="instance.instanceId"
                          :label="instance.name"
                          :value="instance.instanceId"
                        >
                          <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>{{ instance.name }}</span>
                            <el-tag 
                              v-if="instance.headers && Object.keys(instance.headers).length > 0"
                              type="success" 
                              size="small"
                            >
                              {{ Object.keys(instance.headers).length }} 个请求头
                            </el-tag>
                          </div>
                        </el-option>
                        <template #empty>
                          <div style="padding: 10px; text-align: center; color: #999;">
                            {{ editInstancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加图像编辑服务实例' }}
                          </div>
                        </template>
                      </el-select>
                      <el-button 
                        type="info" 
                        size="small" 
                        @click="fetchEditInstances"
                        :loading="editInstancesLoading"
                        title="刷新实例列表"
                      >
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </div>
                    <div v-if="!selectedEditInstanceId" class="instance-hint">
                      <el-text type="danger" size="small">请选择一个图像编辑服务实例</el-text>
                    </div>
                    <div v-else-if="selectedEditInstanceInfo" class="instance-selected">
                      <el-text type="success" size="small">
                        <el-icon><Check /></el-icon>
                        已选择实例: {{ selectedEditInstanceInfo.name }}
                      </el-text>
                    </div>
                  </el-form-item>

                  <!-- 请求头配置 -->
                  <el-form-item v-if="selectedEditInstanceInfo" label="请求头配置">
                    <div class="headers-config">
                      <div class="headers-list">
                        <div 
                          v-for="(header, index) in editHeadersList" 
                          :key="index"
                          class="header-item"
                        >
                          <el-input
                            v-model="header.key"
                            placeholder="请求头名称"
                            size="small"
                            @input="onEditHeaderChange"
                            class="header-key"
                            :disabled="header.fromInstance"
                          />
                          <el-input
                            v-model="header.value"
                            placeholder="请求头值"
                            size="small"
                            @input="onEditHeaderChange"
                            class="header-value"
                            :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                            :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')"
                          />
                          <el-tag 
                            v-if="header.fromInstance" 
                            type="success" 
                            size="small"
                            class="instance-tag"
                          >
                            实例
                          </el-tag>
                          <el-button 
                            v-else
                            type="danger" 
                            size="small" 
                            @click="removeEditHeader(index)"
                            class="remove-btn"
                          >
                            <el-icon><Close /></el-icon>
                          </el-button>
                        </div>
                      </div>
                      <el-button 
                        type="primary" 
                        size="small" 
                        @click="addEditHeader"
                        class="add-header-btn"
                      >
                        <el-icon><Plus /></el-icon>
                        添加请求头
                      </el-button>
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="图像文件" prop="images" required>
                    <el-upload
                      ref="editUploadRef"
                      class="upload-demo"
                      drag
                      :auto-upload="false"
                      :limit="10"
                      accept="image/*,.png,.jpg,.jpeg,.webp"
                      :on-change="onEditFileChange"
                      :on-remove="onEditFileRemove"
                      :file-list="editFileList"
                      multiple
                    >
                      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                      <div class="el-upload__text">
                        将图像文件拖到此处，或<em>点击上传</em>
                      </div>
                      <template #tip>
                        <div class="el-upload__tip">
                          支持 PNG, JPG, JPEG, WebP 格式，单个文件不超过 4MB，最多10个文件
                        </div>
                      </template>
                    </el-upload>
                  </el-form-item>
                  
                  <el-form-item label="编辑提示词" prop="prompt" required>
                    <el-input 
                      v-model="editConfig.prompt" 
                      type="textarea" 
                      :rows="3"
                      placeholder="请输入图像编辑的提示词描述"
                      maxlength="1000"
                      show-word-limit
                      clearable
                    />
                  </el-form-item>
                  
                  <el-form-item label="图像数量">
                    <el-input-number 
                      v-model="editConfig.n" 
                      :min="1" 
                      :max="10"
                      placeholder="生成图像数量"
                    />
                  </el-form-item>
                  
                  <el-form-item label="图像尺寸">
                    <el-select v-model="editConfig.size" placeholder="选择图像尺寸">
                      <el-option label="256x256" value="256x256" />
                      <el-option label="512x512" value="512x512" />
                      <el-option label="1024x1024" value="1024x1024" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="响应格式">
                    <el-select v-model="editConfig.responseFormat" placeholder="选择响应格式">
                      <el-option label="URL" value="url" />
                      <el-option label="Base64" value="b64_json" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="用户ID">
                    <el-input 
                      v-model="editConfig.user" 
                      placeholder="可选的用户标识符"
                      clearable
                    />
                  </el-form-item>
                  
                  <el-form-item>
                    <el-button 
                      type="primary" 
                      @click="sendEditRequest"
                      :loading="editLoading"
                      :disabled="!canSendEditRequest"
                    >
                      <el-icon><Edit /></el-icon>
                      编辑图像
                    </el-button>
                    <el-button @click="resetEditForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>
            
            <el-col :span="12">
              <el-card header="编辑结果" class="result-card">
                <div class="image-result">
                  <div v-if="editLoading" class="loading-state">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <p>正在编辑图像...</p>
                  </div>
                  
                  <div v-else-if="editError" class="error-state">
                    <el-alert 
                      :title="editError" 
                      type="error" 
                      show-icon 
                      :closable="false"
                    />
                  </div>
                  
                  <div v-else-if="editImages.length > 0" class="image-gallery">
                    <div class="result-info">
                      <el-tag type="success">
                        <el-icon><Check /></el-icon>
                        图像编辑成功
                      </el-tag>
                      <span class="duration-info">
                        耗时: {{ editDuration }}ms
                      </span>
                    </div>
                    
                    <div class="images-grid">
                      <div 
                        v-for="(image, index) in editImages" 
                        :key="index" 
                        class="image-item"
                      >
                        <div class="image-container">
                          <img 
                            :src="image.url || `data:image/png;base64,${image.b64_json}`" 
                            :alt="`Edited image ${index + 1}`"
                            class="generated-image"
                            @click="previewImage(image, index)"
                          />
                          <div class="image-overlay">
                            <el-button 
                              type="primary" 
                              size="small"
                              @click="downloadImage(image, index)"
                            >
                              <el-icon><Download /></el-icon>
                            </el-button>
                            <el-button 
                              type="info" 
                              size="small"
                              @click="previewImage(image, index)"
                            >
                              <el-icon><ZoomIn /></el-icon>
                            </el-button>
                          </div>
                        </div>
                        <div class="image-info">
                          <span class="image-index">图像 {{ index + 1 }}</span>
                        </div>
                      </div>
                    </div>
                    
                    <div class="result-actions">
                      <el-button 
                        type="primary" 
                        @click="downloadAllEditImages"
                      >
                        <el-icon><Download /></el-icon>
                        下载全部
                      </el-button>
                      <el-button 
                        @click="clearEditResult"
                      >
                        <el-icon><Delete /></el-icon>
                        清除结果
                      </el-button>
                    </div>
                  </div>
                  
                  <div v-else class="empty-state">
                    <el-empty description="请上传图像文件并开始编辑" />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 图像预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      title="图像预览"
      width="80%"
      center
    >
      <div class="preview-container">
        <img 
          v-if="previewImageData"
          :src="previewImageData.url || `data:image/png;base64,${previewImageData.b64_json}`"
          :alt="previewImageData.alt || 'Preview image'"
          class="preview-image"
        />
        <div v-if="previewImageData?.revised_prompt" class="preview-info">
          <h4>修订提示词:</h4>
          <p>{{ previewImageData.revised_prompt }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
        <el-button 
          type="primary" 
          @click="downloadPreviewImage"
        >
          <el-icon><Download /></el-icon>
          下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, inject, nextTick, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile, type UploadFiles } from 'element-plus'
import { 
  UploadFilled, 
  Picture, 
  Edit,
  Loading, 
  Check, 
  Download, 
  Delete, 
  ZoomIn,
  Refresh,
  Plus,
  Close
} from '@element-plus/icons-vue'
import { sendUniversalRequest } from '@/api/universal'
import { getModelsByServiceType, getInstanceServiceType } from '@/api/models'
import { getServiceInstances } from '@/api/dashboard'
import type { 
  ImageGenerateRequestConfig, 
  ImageEditRequestConfig, 
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
const activeImageTab = ref<'generate' | 'edit'>('generate')

// 图像生成相关状态
const generateFormRef = ref<FormInstance>()
const generateConfig = ref<ImageGenerateRequestConfig>({
  model: '',
  prompt: '',
  n: 1,
  quality: 'standard',
  size: '1024x1024',
  style: 'vivid',
  responseFormat: 'url',
  user: ''
})

// 表单数据（包含所有需要验证的字段）
const generateFormData = reactive({
  selectedInstanceId: '',
  model: generateConfig.value.model,
  prompt: generateConfig.value.prompt,
  n: generateConfig.value.n,
  quality: generateConfig.value.quality,
  size: generateConfig.value.size,
  style: generateConfig.value.style,
  responseFormat: generateConfig.value.responseFormat,
  user: generateConfig.value.user
})
const generateLoading = ref(false)
const generateError = ref<string | null>(null)
const generateImages = ref<any[]>([])

// 图像生成实例选择相关状态
const availableGenerateInstances = ref<any[]>([])
const generateInstancesLoading = ref(false)
const selectedGenerateInstanceId = ref('')
const selectedGenerateInstanceInfo = ref<any>(null)
const generateHeadersList = ref<Array<{key: string, value: string, fromInstance: boolean}>>([])
const currentGenerateHeaders = ref<Record<string, string>>({})
const generateDuration = ref(0)

// 图像编辑相关状态
const editFormRef = ref<FormInstance>()
const editConfig = ref<Omit<ImageEditRequestConfig, 'images'> & { images?: File[] }>({
  model: '',
  prompt: '',
  n: 1,
  size: '1024x1024',
  responseFormat: 'url',
  user: '',
  images: []
})

// 图像编辑表单数据（包含所有需要验证的字段）
const editFormData = reactive({
  selectedInstanceId: '',
  model: editConfig.value.model,
  prompt: editConfig.value.prompt,
  n: editConfig.value.n,
  size: editConfig.value.size,
  responseFormat: editConfig.value.responseFormat,
  user: editConfig.value.user,
  images: editConfig.value.images
})
const editLoading = ref(false)
const editError = ref<string | null>(null)
const editImages = ref<any[]>([])

// 图像编辑实例选择相关状态
const availableEditInstances = ref<any[]>([])
const editInstancesLoading = ref(false)
const selectedEditInstanceId = ref('')
const selectedEditInstanceInfo = ref<any>(null)
const editHeadersList = ref<Array<{key: string, value: string, fromInstance: boolean}>>([])
const currentEditHeaders = ref<Record<string, string>>({})
const editDuration = ref(0)
const editUploadRef = ref()
const editFileList = ref<UploadFile[]>([])

// 预览对话框
const previewDialogVisible = ref(false)
const previewImageData = ref<any>(null)
const previewImageIndex = ref(0)

// 表单验证规则
const generateRules: ValidationRules = {
  selectedInstanceId: [
    { 
      required: true, 
      message: '请选择一个图像生成服务实例', 
      trigger: 'change',
      validator: (_rule: any, value: string, callback: Function) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个图像生成服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  prompt: [
    { required: true, message: '请输入图像生成提示词', trigger: 'blur' },
    { min: 1, max: 4000, message: '提示词长度应在 1-4000 字符之间', trigger: 'blur' }
  ]
}

const editRules: ValidationRules = {
  selectedInstanceId: [
    { 
      required: true, 
      message: '请选择一个图像编辑服务实例', 
      trigger: 'change',
      validator: (_rule: any, value: string, callback: Function) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个图像编辑服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  prompt: [
    { required: true, message: '请输入图像编辑提示词', trigger: 'blur' },
    { min: 1, max: 1000, message: '提示词长度应在 1-1000 字符之间', trigger: 'blur' }
  ],
  images: [
    { required: true, message: '请上传图像文件', trigger: 'change' }
  ]
}

// 计算属性
const canSendGenerateRequest = computed(() => {
  return generateConfig.value.model && 
         generateConfig.value.prompt && 
         !generateLoading.value
})

const canSendEditRequest = computed(() => {
  return editConfig.value.model && 
         editConfig.value.prompt && 
         editConfig.value.images && 
         editConfig.value.images.length > 0 &&
         !editLoading.value
})

// 选项卡切换处理
const onImageTabChange = (tabName: string) => {
  activeImageTab.value = tabName as 'generate' | 'edit'
  if (tabState) {
    tabState.activeImageTab = activeImageTab.value
  }
  
  // 清除之前的错误和结果
  clearGenerateResult()
  clearEditResult()
}

// 获取图像生成实例列表
const fetchGenerateInstances = async () => {
  generateInstancesLoading.value = true
  try {
    const response = await getServiceInstances('imgGen')
    if (response.data?.success) {
      availableGenerateInstances.value = response.data.data || []
      ElMessage.success(`已刷新实例列表，找到 ${availableGenerateInstances.value.length} 个可用实例`)
    } else {
      availableGenerateInstances.value = []
      ElMessage.warning('获取实例列表失败')
    }
  } catch (error) {
    console.error('获取实例列表失败:', error)
    availableGenerateInstances.value = []
    ElMessage.error('获取实例列表失败')
  } finally {
    generateInstancesLoading.value = false
  }
}

// 图像生成实例选择变化处理
const onGenerateInstanceChange = (instanceId: string) => {
  // 同步到formData
  generateFormData.selectedInstanceId = instanceId
  
  if (!instanceId) {
    selectedGenerateInstanceInfo.value = null
    generateConfig.value.model = ''
    generateFormData.model = ''
    generateHeadersList.value = []
    currentGenerateHeaders.value = {}
    return
  }
  
  const instance = availableGenerateInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    selectedGenerateInstanceInfo.value = instance
    
    // 使用实例名称作为默认模型
    generateConfig.value.model = instance.name || 'default-model'
    generateFormData.model = instance.name || 'default-model'
    
    // 初始化请求头列表
    initializeGenerateHeaders(instance.headers || {})
    
    ElMessage.success(`已选择实例 "${instance.name}"`)
  }
}

// 初始化图像生成请求头列表
const initializeGenerateHeaders = (instanceHeaders: Record<string, string>) => {
  generateHeadersList.value = []
  currentGenerateHeaders.value = {}
  
  // 添加实例的请求头（标记为来自实例，不可删除）
  Object.entries(instanceHeaders).forEach(([key, value]) => {
    generateHeadersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentGenerateHeaders.value[key] = value
  })
  
  // 添加全局配置中的自定义请求头
  Object.entries(props.globalConfig.customHeaders || {}).forEach(([key, value]) => {
    if (!currentGenerateHeaders.value[key]) {
      generateHeadersList.value.push({
        key,
        value,
        fromInstance: false
      })
      currentGenerateHeaders.value[key] = value
    }
  })
}

// 添加图像生成请求头
const addGenerateHeader = () => {
  generateHeadersList.value.push({
    key: '',
    value: '',
    fromInstance: false
  })
}

// 删除图像生成请求头
const removeGenerateHeader = (index: number) => {
  const header = generateHeadersList.value[index]
  if (!header.fromInstance) {
    generateHeadersList.value.splice(index, 1)
    onGenerateHeaderChange()
  }
}

// 图像生成请求头变化处理
const onGenerateHeaderChange = () => {
  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}
  
  generateHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      newHeaders[header.key.trim()] = header.value.trim()
    }
  })
  
  currentGenerateHeaders.value = newHeaders
}

// 获取图像编辑实例列表
const fetchEditInstances = async () => {
  editInstancesLoading.value = true
  try {
    const response = await getServiceInstances('imgEdit')
    if (response.data?.success) {
      availableEditInstances.value = response.data.data || []
      ElMessage.success(`已刷新实例列表，找到 ${availableEditInstances.value.length} 个可用实例`)
    } else {
      availableEditInstances.value = []
      ElMessage.warning('获取实例列表失败')
    }
  } catch (error) {
    console.error('获取实例列表失败:', error)
    availableEditInstances.value = []
    ElMessage.error('获取实例列表失败')
  } finally {
    editInstancesLoading.value = false
  }
}

// 图像编辑实例选择变化处理
const onEditInstanceChange = (instanceId: string) => {
  // 同步到formData
  editFormData.selectedInstanceId = instanceId
  
  if (!instanceId) {
    selectedEditInstanceInfo.value = null
    editConfig.value.model = ''
    editFormData.model = ''
    editHeadersList.value = []
    currentEditHeaders.value = {}
    return
  }
  
  const instance = availableEditInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    selectedEditInstanceInfo.value = instance
    
    // 使用实例名称作为默认模型
    editConfig.value.model = instance.name || 'default-model'
    editFormData.model = instance.name || 'default-model'
    
    // 初始化请求头列表
    initializeEditHeaders(instance.headers || {})
    
    ElMessage.success(`已选择实例 "${instance.name}"`)
  }
}

// 初始化图像编辑请求头列表
const initializeEditHeaders = (instanceHeaders: Record<string, string>) => {
  editHeadersList.value = []
  currentEditHeaders.value = {}
  
  // 添加实例的请求头（标记为来自实例，不可删除）
  Object.entries(instanceHeaders).forEach(([key, value]) => {
    editHeadersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentEditHeaders.value[key] = value
  })
  
  // 添加全局配置中的自定义请求头
  Object.entries(props.globalConfig.customHeaders || {}).forEach(([key, value]) => {
    if (!currentEditHeaders.value[key]) {
      editHeadersList.value.push({
        key,
        value,
        fromInstance: false
      })
      currentEditHeaders.value[key] = value
    }
  })
}

// 添加图像编辑请求头
const addEditHeader = () => {
  editHeadersList.value.push({
    key: '',
    value: '',
    fromInstance: false
  })
}

// 删除图像编辑请求头
const removeEditHeader = (index: number) => {
  const header = editHeadersList.value[index]
  if (!header.fromInstance) {
    editHeadersList.value.splice(index, 1)
    onEditHeaderChange()
  }
}

// 图像编辑请求头变化处理
const onEditHeaderChange = () => {
  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}
  
  editHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      newHeaders[header.key.trim()] = header.value.trim()
    }
  })
  
  currentEditHeaders.value = newHeaders
}

// 图像生成相关方法
const sendGenerateRequest = async () => {
  if (!generateFormRef.value) return
  
  // 检查是否选择了实例
  if (!generateFormData.selectedInstanceId) {
    ElMessage.error('请选择一个图像生成服务实例')
    return
  }
  
  try {
    const valid = await generateFormRef.value.validate()
    if (!valid) return
  } catch (error) {
    console.log('表单验证失败:', error)
    return
  }
  
  generateLoading.value = true
  generateError.value = null
  emit('response', null, true)
  
  try {
    const requestBody = {
      model: generateConfig.value.model,
      prompt: generateConfig.value.prompt,
      n: generateConfig.value.n,
      quality: generateConfig.value.quality,
      size: generateConfig.value.size,
      style: generateConfig.value.style,
      response_format: generateConfig.value.responseFormat,
      user: generateConfig.value.user || undefined
    }
    
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...currentGenerateHeaders.value
    }
    
    const generateRequest = {
      endpoint: '/v1/images/generations',
      method: 'POST',
      headers,
      body: requestBody
    }
    
    // 发送request事件
    const requestSize = JSON.stringify(requestBody).length
    emit('request', generateRequest, requestSize)
    
    const response = await sendUniversalRequest(generateRequest)
    
    generateDuration.value = response.duration
    const actualData = getActualData(response.data)
    generateImages.value = actualData?.data || []
    
    emit('response', response)
    ElMessage.success('图像生成成功')
    
  } catch (error: any) {
    console.error('Image generation request failed:', error)
    generateError.value = error.data?.error?.message || error.message || '图像生成失败'
    emit('response', null, false, generateError.value)
    ElMessage.error(generateError.value || '图像生成失败')
  } finally {
    generateLoading.value = false
  }
}

const downloadImage = async (image: any, index: number) => {
  try {
    let imageUrl = image.url
    let filename = `generated_image_${index + 1}_${Date.now()}.png`
    
    if (image.b64_json) {
      // 处理 base64 图像
      const byteCharacters = atob(image.b64_json)
      const byteNumbers = new Array(byteCharacters.length)
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i)
      }
      const byteArray = new Uint8Array(byteNumbers)
      const blob = new Blob([byteArray], { type: 'image/png' })
      imageUrl = URL.createObjectURL(blob)
    }
    
    const link = document.createElement('a')
    link.href = imageUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    if (image.b64_json) {
      URL.revokeObjectURL(imageUrl)
    }
    
    ElMessage.success('图像下载成功')
  } catch (error) {
    console.error('Download failed:', error)
    ElMessage.error('图像下载失败')
  }
}

const downloadAllImages = async () => {
  for (let i = 0; i < generateImages.value.length; i++) {
    await downloadImage(generateImages.value[i], i)
    // 添加小延迟避免浏览器阻止多个下载
    await new Promise(resolve => setTimeout(resolve, 100))
  }
}

const previewImage = (image: any, index: number) => {
  previewImageData.value = {
    ...image,
    alt: `Generated image ${index + 1}`
  }
  previewImageIndex.value = index
  previewDialogVisible.value = true
}

const downloadPreviewImage = () => {
  if (previewImageData.value) {
    downloadImage(previewImageData.value, previewImageIndex.value)
  }
}

const clearGenerateResult = () => {
  generateImages.value = []
  generateError.value = null
  generateDuration.value = 0
}

const resetGenerateForm = () => {
  clearGenerateResult()
  Object.assign(generateConfig.value, {
    ...DEFAULT_CONFIGS.imageGenerate,
    model: '',
    prompt: ''
  })
  generateFormRef.value?.clearValidate()
}

// 图像编辑相关方法
const onEditFileChange = (file: UploadFile, fileList: UploadFiles) => {
  if (file.raw) {
    // 检查文件大小（4MB限制）
    const maxSize = 4 * 1024 * 1024
    if (file.raw.size > maxSize) {
      ElMessage.error('文件大小不能超过 4MB')
      return
    }
    
    // 检查文件类型
    const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp']
    if (!allowedTypes.includes(file.raw.type)) {
      ElMessage.error('只支持 PNG, JPG, JPEG, WebP 格式的图像文件')
      return
    }
    
    editConfig.value.images = fileList.map(f => f.raw!).filter(Boolean)
    editFileList.value = fileList
  }
}

const onEditFileRemove = (file: UploadFile, fileList: UploadFiles) => {
  editConfig.value.images = fileList.map(f => f.raw!).filter(Boolean)
  editFileList.value = fileList
}

const sendEditRequest = async () => {
  if (!editFormRef.value) return
  
  // 检查是否选择了实例
  if (!editFormData.selectedInstanceId) {
    ElMessage.error('请选择一个图像编辑服务实例')
    return
  }
  
  try {
    const valid = await editFormRef.value.validate()
    if (!valid) return
  } catch (error) {
    console.log('表单验证失败:', error)
    return
  }
  
  if (!editConfig.value.images || editConfig.value.images.length === 0) {
    ElMessage.error('请先上传图像文件')
    return
  }
  
  editLoading.value = true
  editError.value = null
  emit('response', null, true)
  
  try {
    const requestBody: any = {
      model: editConfig.value.model,
      prompt: editConfig.value.prompt,
      n: editConfig.value.n,
      size: editConfig.value.size,
      response_format: editConfig.value.responseFormat,
      user: editConfig.value.user || undefined
    }
    
    const headers: Record<string, string> = {
      ...currentEditHeaders.value
    }
    
    const editRequest = {
      endpoint: '/v1/images/edits',
      method: 'POST',
      headers,
      body: requestBody,
      files: editConfig.value.images
    }
    
    // 发送request事件
    const requestSize = JSON.stringify(requestBody).length + (editConfig.value.images?.reduce((sum, file) => sum + file.size, 0) || 0)
    emit('request', editRequest, requestSize)
    
    const response = await sendUniversalRequest(editRequest)
    
    editDuration.value = response.duration
    const actualData = getActualData(response.data)
    editImages.value = actualData?.data || []
    
    emit('response', response)
    ElMessage.success('图像编辑成功')
    
  } catch (error: any) {
    console.error('Image edit request failed:', error)
    editError.value = error.data?.error?.message || error.message || '图像编辑失败'
    emit('response', null, false, editError.value)
    ElMessage.error(editError.value || '图像编辑失败')
  } finally {
    editLoading.value = false
  }
}

const downloadAllEditImages = async () => {
  for (let i = 0; i < editImages.value.length; i++) {
    await downloadImage(editImages.value[i], i)
    // 添加小延迟避免浏览器阻止多个下载
    await new Promise(resolve => setTimeout(resolve, 100))
  }
}

const clearEditResult = () => {
  editImages.value = []
  editError.value = null
  editDuration.value = 0
}

const resetEditForm = () => {
  clearEditResult()
  Object.assign(editConfig.value, {
    ...DEFAULT_CONFIGS.imageEdit,
    model: '',
    prompt: '',
    images: []
  })
  editFileList.value = []
  editUploadRef.value?.clearFiles()
  editFormRef.value?.clearValidate()
}

// 初始化
if (tabState?.activeImageTab) {
  activeImageTab.value = tabState.activeImageTab
}

// 获取实际的数据对象（处理包装格式）
const getActualData = (responseData: any) => {
  if (!responseData) return null
  
  // 检查是否是包装的响应格式 (有success, message, data字段)
  if (responseData.success !== undefined && responseData.data) {
    return responseData.data
  }
  
  return responseData
}

// 同步 generateConfig 和 generateFormData
watch(() => generateConfig.value.prompt, (newPrompt) => {
  generateFormData.prompt = newPrompt
})

watch(() => generateConfig.value.n, (newN) => {
  generateFormData.n = newN
})

watch(() => generateConfig.value.quality, (newQuality) => {
  generateFormData.quality = newQuality
})

watch(() => generateConfig.value.size, (newSize) => {
  generateFormData.size = newSize
})

watch(() => generateConfig.value.style, (newStyle) => {
  generateFormData.style = newStyle
})

watch(() => generateConfig.value.responseFormat, (newResponseFormat) => {
  generateFormData.responseFormat = newResponseFormat
})

watch(() => generateConfig.value.user, (newUser) => {
  generateFormData.user = newUser
})

// 同步 editConfig 和 editFormData
watch(() => editConfig.value.prompt, (newPrompt) => {
  editFormData.prompt = newPrompt
})

watch(() => editConfig.value.n, (newN) => {
  editFormData.n = newN
})

watch(() => editConfig.value.size, (newSize) => {
  editFormData.size = newSize
})

watch(() => editConfig.value.responseFormat, (newResponseFormat) => {
  editFormData.responseFormat = newResponseFormat
})

watch(() => editConfig.value.user, (newUser) => {
  editFormData.user = newUser
})

watch(() => editConfig.value.images, (newImages) => {
  editFormData.images = newImages
}, { deep: true })

// 监听全局刷新模型事件
const handleRefreshModels = () => {
  fetchGenerateInstances()
  fetchEditInstances()
}

// 组件挂载时获取实例列表
onMounted(() => {
  fetchGenerateInstances()
  fetchEditInstances()
  
  // 同步初始状态
  generateFormData.selectedInstanceId = selectedGenerateInstanceId.value
  generateFormData.prompt = generateConfig.value.prompt
  generateFormData.n = generateConfig.value.n
  generateFormData.quality = generateConfig.value.quality
  generateFormData.size = generateConfig.value.size
  generateFormData.style = generateConfig.value.style
  generateFormData.responseFormat = generateConfig.value.responseFormat
  generateFormData.user = generateConfig.value.user
  
  editFormData.selectedInstanceId = selectedEditInstanceId.value
  editFormData.prompt = editConfig.value.prompt
  editFormData.n = editConfig.value.n
  editFormData.size = editConfig.value.size
  editFormData.responseFormat = editConfig.value.responseFormat
  editFormData.user = editConfig.value.user
  editFormData.images = editConfig.value.images
  
  // 监听全局刷新事件
  document.addEventListener('playground-refresh-models', handleRefreshModels)
})

// 组件卸载时清理资源
import { onUnmounted } from 'vue'
onUnmounted(() => {
  clearGenerateResult()
  clearEditResult()
  document.removeEventListener('playground-refresh-models', handleRefreshModels)
})
</script>

<style scoped>
.image-playground {
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

/* 图像结果样式 */
.image-result {
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

.image-gallery {
  padding: 20px;
}

.result-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.duration-info {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.image-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.image-container {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
}

.generated-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.generated-image:hover {
  transform: scale(1.05);
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.image-container:hover .image-overlay {
  opacity: 1;
}

.image-info {
  padding: 10px;
}

.image-index {
  font-weight: 500;
  color: var(--el-text-color-primary);
  display: block;
  margin-bottom: 5px;
}

.revised-prompt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  display: block;
}

.result-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* 上传组件样式 */
.upload-demo {
  width: 100%;
}

.upload-demo :deep(.el-upload-dragger) {
  width: 100%;
}

.upload-demo :deep(.el-upload__tip) {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 预览对话框样式 */
.preview-container {
  text-align: center;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.preview-info {
  margin-top: 20px;
  text-align: left;
  padding: 15px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
}

.preview-info h4 {
  margin: 0 0 10px 0;
  color: var(--el-text-color-primary);
}

.preview-info p {
  margin: 0;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .image-playground {
    padding: 15px;
  }
  
  .images-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 15px;
  }
}

@media (max-width: 768px) {
  .image-playground {
    padding: 10px;
  }
  
  .service-content .el-row {
    flex-direction: column;
  }
  
  .service-content .el-col {
    width: 100% !important;
    margin-bottom: 20px;
  }
  
  .images-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 10px;
  }
  
  .result-info {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
  
  .result-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
  
  .preview-image {
    max-height: 50vh;
  }
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .image-item {
    border-color: var(--el-border-color);
    background: var(--el-bg-color-overlay);
  }
  
  .preview-info {
    background: var(--el-bg-color-overlay);
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

/* 选择器样式 */
.el-select {
  width: 100%;
}

/* 按钮组样式 */
.el-form-item .el-button + .el-button {
  margin-left: 10px;
}

/* 输入数字组件样式 */
.el-input-number {
  width: 100%;
}

/* 图像悬停效果 */
.image-item {
  transition: box-shadow 0.3s ease;
}

.image-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 对话框样式优化 */
.el-dialog {
  border-radius: 12px;
}

.el-dialog__header {
  padding: 20px 20px 10px;
}

.el-dialog__body {
  padding: 10px 20px 20px;
}

.el-dialog__footer {
  padding: 10px 20px 20px;
}
</style>