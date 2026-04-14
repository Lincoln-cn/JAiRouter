<template>
  <div class="markdown-renderer" v-html="renderedContent"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMarkdown } from '../../composables/useMarkdown'

interface Props {
  content: string
}

const props = defineProps<Props>()

const { render } = useMarkdown()

const renderedContent = computed(() => {
  return render(props.content || '')
})
</script>

<style>
.markdown-renderer {
  line-height: 1.6;
  word-wrap: break-word;
}

.markdown-renderer h1,
.markdown-renderer h2,
.markdown-renderer h3,
.markdown-renderer h4,
.markdown-renderer h5,
.markdown-renderer h6 {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.3;
}

.markdown-renderer h1 {
  font-size: 1.5em;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 0.3em;
}

.markdown-renderer h2 {
  font-size: 1.3em;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 0.3em;
}

.markdown-renderer h3 {
  font-size: 1.15em;
}

.markdown-renderer p {
  margin: 0.8em 0;
}

.markdown-renderer ul,
.markdown-renderer ol {
  padding-left: 2em;
  margin: 0.8em 0;
}

.markdown-renderer li {
  margin: 0.25em 0;
}

.markdown-renderer code {
  background-color: var(--el-fill-color-light);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 0.9em;
}

.markdown-renderer pre {
  background-color: var(--el-fill-color-darker);
  padding: 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-renderer pre code {
  background: none;
  padding: 0;
  font-size: 0.875em;
  line-height: 1.5;
}

.markdown-renderer blockquote {
  border-left: 4px solid var(--el-color-primary);
  padding-left: 1em;
  margin: 1em 0;
  color: var(--el-text-color-secondary);
}

.markdown-renderer table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.markdown-renderer th,
.markdown-renderer td {
  border: 1px solid var(--el-border-color-lighter);
  padding: 0.5em 1em;
  text-align: left;
}

.markdown-renderer th {
  background-color: var(--el-fill-color-light);
  font-weight: 600;
}

.markdown-renderer hr {
  border: none;
  border-top: 1px solid var(--el-border-color-lighter);
  margin: 1.5em 0;
}

.markdown-renderer a {
  color: var(--el-color-primary);
  text-decoration: none;
}

.markdown-renderer a:hover {
  text-decoration: underline;
}

.markdown-renderer img {
  max-width: 100%;
  height: auto;
}

/* Highlight.js 代码高亮主题适配 */
.markdown-renderer .hljs {
  background: transparent;
}

.markdown-renderer .hljs-keyword,
.markdown-renderer .hljs-selector-tag {
  color: #cf222e;
}

.markdown-renderer .hljs-string,
.markdown-renderer .hljs-attr {
  color: #0a3069;
}

.markdown-renderer .hljs-number {
  color: #0550ae;
}

.markdown-renderer .hljs-comment {
  color: #6e7781;
}

.markdown-renderer .hljs-function {
  color: #8250df;
}

.markdown-renderer .hljs-class {
  color: #953800;
}
</style>