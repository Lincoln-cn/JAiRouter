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
        {{ t(copied ? 'playgroundCommon.action.copied' : 'playgroundCommon.action.copy') }}
      </el-button>
    </div>
    <pre class="code-content"><code :class="codeClass" v-html="highlightedCode"></code></pre>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
    ElMessage.success(t('playgroundCommon.message.codeCopiedToClipboard'))
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error(t('playgroundCommon.message.copyFailed'))
  }
}
</script>

<style scoped>
.code-block {
  border-radius: 8px;
  overflow: hidden;
  background-color: #1e1e1e;
  /* 令牌化边框：暗色容器(#1d1e1f/#262727)下块边界近乎不可见(#1e1e1e vs 容器 ~1.1:1)，
     用随主题切换的边框把深色块在亮/暗容器中都框出来 */
  border: 1px solid var(--el-border-color);
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
  color: #a8a8a8; /* 原 #888 在 #2d2d2d 上仅 ~3.9:1 -> 提亮至 ~5.9:1 */
  text-transform: uppercase;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.copy-btn {
  color: #a8a8a8;
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
  color: #d4d4d4; /* 在 #1e1e1e 上 ~11.3:1 */
  background: transparent;
}

/* 恒深色块 -> 固定用 GitHub Dark 近似 token 色（与 MarkdownRenderer 暗色同表，
   保证两组件代码观感一致）。hljs span 经 v-html 注入不带 scope 属性，需 :deep 命中。
   对比度按 #1e1e1e 底校验：正文类 ≥4.5，注释 ≥3。 */
.code-content :deep(.hljs-keyword),
.code-content :deep(.hljs-selector-tag),
.code-content :deep(.hljs-doctag),
.code-content :deep(.hljs-template-tag),
.code-content :deep(.hljs-template-variable),
.code-content :deep(.hljs-type),
.code-content :deep(.hljs-variable.language_),
.code-content :deep(.hljs-meta .hljs-keyword) {
  color: #ff7b72;
}

.code-content :deep(.hljs-string),
.code-content :deep(.hljs-attr),
.code-content :deep(.hljs-regexp),
.code-content :deep(.hljs-meta .hljs-string) {
  color: #a5d6ff;
}

.code-content :deep(.hljs-number),
.code-content :deep(.hljs-literal),
.code-content :deep(.hljs-variable),
.code-content :deep(.hljs-operator),
.code-content :deep(.hljs-attribute),
.code-content :deep(.hljs-meta),
.code-content :deep(.hljs-selector-attr),
.code-content :deep(.hljs-selector-class),
.code-content :deep(.hljs-selector-id) {
  color: #79c0ff;
}

.code-content :deep(.hljs-comment),
.code-content :deep(.hljs-code),
.code-content :deep(.hljs-formula),
.code-content :deep(.hljs-quote) {
  color: #8b949e;
}

.code-content :deep(.hljs-function),
.code-content :deep(.hljs-title),
.code-content :deep(.hljs-title.function_),
.code-content :deep(.hljs-title.class_),
.code-content :deep(.hljs-title.class_.inherited__) {
  color: #d2a8ff;
}

.code-content :deep(.hljs-class),
.code-content :deep(.hljs-built_in),
.code-content :deep(.hljs-symbol) {
  color: #ffa657;
}

.code-content :deep(.hljs-name),
.code-content :deep(.hljs-selector-pseudo) {
  color: #7ee787;
}
</style>