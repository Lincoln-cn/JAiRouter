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

/* ===== Highlight.js 代码高亮主题适配 =====
 * 容器/行内 code 底色沿用上方 var(--el-*) 令牌（亮/暗自动切换）。
 * token 色以“语义分组 + CSS 变量”实现：亮色为 GitHub Light 色调，
 * 暗色(html.dark)切换为 GitHub Dark 近似色调，两组 hue 语义一一对应。
 * 亮色红/紫/绿为在应用灰底(#e6e8eb)上满足对比度≥4.5 而作的加深色
 * （注释类豁免，≥3 即可）。
 */
.markdown-renderer {
  /* 亮色 token 色（GitHub Light 近似） */
  --md-token-keyword: #c31f2b;   /* 关键字/标签/类型 */
  --md-token-string: #0a3069;    /* 字符串/属性值 */
  --md-token-number: #0550ae;    /* 数字/字面量/运算符 */
  --md-token-comment: #6e7781;   /* 注释 */
  --md-token-function: #7a3fd1;  /* 函数/标题/类名 */
  --md-token-class: #953800;     /* 内建类型/遗留 class/符号 */
  --md-token-name: #16713a;      /* 标签名/伪类选择器 */
}

html.dark .markdown-renderer {
  /* 暗色 token 色（GitHub Dark 近似） */
  --md-token-keyword: #ff7b72;
  --md-token-string: #a5d6ff;
  --md-token-number: #79c0ff;
  --md-token-comment: #8b949e;
  --md-token-function: #d2a8ff;
  --md-token-class: #ffa657;
  --md-token-name: #7ee787;
}

.markdown-renderer .hljs {
  background: transparent;
}

/* ---- 通用分组：一组选择器 + 随主题切换的 --md-token-* ---- */

/* 关键字 / 标签 / 类型（红） */
.markdown-renderer .hljs-keyword,
.markdown-renderer .hljs-selector-tag,
.markdown-renderer .hljs-doctag,
.markdown-renderer .hljs-template-tag,
.markdown-renderer .hljs-template-variable,
.markdown-renderer .hljs-type,
.markdown-renderer .hljs-variable.language_,
.markdown-renderer .hljs-meta .hljs-keyword {
  color: var(--md-token-keyword);
}

/* 字符串 / 属性值（藏蓝 -> 浅蓝） */
.markdown-renderer .hljs-string,
.markdown-renderer .hljs-attr,
.markdown-renderer .hljs-regexp,
.markdown-renderer .hljs-meta .hljs-string {
  color: var(--md-token-string);
}

/* 数字 / 字面量 / 变量 / 运算符 / 属性名 / 元信息（蓝） */
.markdown-renderer .hljs-number,
.markdown-renderer .hljs-literal,
.markdown-renderer .hljs-variable,
.markdown-renderer .hljs-operator,
.markdown-renderer .hljs-attribute,
.markdown-renderer .hljs-meta,
.markdown-renderer .hljs-selector-attr,
.markdown-renderer .hljs-selector-class,
.markdown-renderer .hljs-selector-id {
  color: var(--md-token-number);
}

/* 注释 / 代码 / 引用（灰） */
.markdown-renderer .hljs-comment,
.markdown-renderer .hljs-code,
.markdown-renderer .hljs-formula,
.markdown-renderer .hljs-quote {
  color: var(--md-token-comment);
}

/* 函数 / 标题 / 类名（紫） */
.markdown-renderer .hljs-function,
.markdown-renderer .hljs-title,
.markdown-renderer .hljs-title.function_,
.markdown-renderer .hljs-title.class_,
.markdown-renderer .hljs-title.class_.inherited__ {
  color: var(--md-token-function);
}

/* 内建类型 / 遗留 class / 符号（橙褐） */
.markdown-renderer .hljs-class,
.markdown-renderer .hljs-built_in,
.markdown-renderer .hljs-symbol {
  color: var(--md-token-class);
}

/* 标签名 / 伪类选择器（绿） */
.markdown-renderer .hljs-name,
.markdown-renderer .hljs-selector-pseudo {
  color: var(--md-token-name);
}

/* ===== 暗色容器底色（亮色沿用上方 var(--el-*) 令牌原值） ===== */
html.dark .markdown-renderer code {
  background-color: var(--el-fill-color); /* 行内 code：#303030，略高于暗色消息体 #262727 */
}

html.dark .markdown-renderer pre {
  background-color: var(--ja-bg-overlay); /* 代码容器：#1d1e1f，GitHub Dark 画布近似 */
}

html.dark .markdown-renderer pre code {
  background: none;
}
</style>