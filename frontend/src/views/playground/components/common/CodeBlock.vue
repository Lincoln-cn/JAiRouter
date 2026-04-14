<template>
  <div class="code-block">
    <div class="code-header">
      <span class="code-language">{{ displayLanguage }}</span>
      <el-button
        text
        size="small"
        class="copy-btn"
        @click="copyCode"
      >
        <el-icon><DocumentCopy /></el-icon>
        {{ copied ? '已复制' : '复制' }}
      </el-button>
    </div>
    <pre class="code-content"><code :class="codeClass" v-html="highlightedCode"></code></pre>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { DocumentCopy } from '@element-plus/icons-vue'
import { useMarkdown } from '../../composables/useMarkdown'
import { ElMessage } from 'element-plus'

interface Props {
  code: string
  language?: string
}

const props = withDefaults(defineProps<Props>(), {
  language: ''
})

const { highlightCode, detectLanguage } = useMarkdown()
const copied = ref(false)

const detectedLanguage = computed(() => {
  if (props.language) return props.language
  return detectLanguage(props.code)
})

const displayLanguage = computed(() => {
  return detectedLanguage.value || 'plaintext'
})

const codeClass = computed(() => {
  return `language-${displayLanguage.value}`
})

const highlightedCode = computed(() => {
  return highlightCode(props.code, detectedLanguage.value)
})

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.code)
    copied.value = true
    ElMessage.success('代码已复制到剪贴板')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.code-block {
  border-radius: 8px;
  overflow: hidden;
  background-color: #1e1e1e;
  margin: 1em 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background-color: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.code-language {
  font-size: 12px;
  color: #888;
  text-transform: uppercase;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.copy-btn {
  color: #888;
}

.copy-btn:hover {
  color: #fff;
}

.code-content {
  margin: 0;
  padding: 16px;
  overflow-x: auto;
  font-size: 14px;
  line-height: 1.5;
}

.code-content code {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  color: #d4d4d4;
  background: transparent;
}
</style>