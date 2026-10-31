import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

// 配置 marked
marked.setOptions({
  gfm: true,
  breaks: true
})

export function useMarkdown() {
  /**
   * 将 Markdown 文本转换为 HTML（带代码高亮）
   */
  const render = (text: string): string => {
    if (!text) return ''

    try {
      // 先用 marked 解析
      let html = marked.parse(text) as string

      // 后处理：对代码块进行高亮
      html = html.replace(
        /<pre><code class="language-(\w+)?">([\s\S]*?)<\/code><\/pre>/g,
        (_match, lang, code) => {
          const decodedCode = decodeHtmlEntities(code)
          if (lang && hljs.getLanguage(lang)) {
            const highlighted = hljs.highlight(decodedCode, { language: lang }).value
            return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`
          }
          const autoHighlighted = hljs.highlightAuto(decodedCode).value
          return `<pre><code class="hljs">${autoHighlighted}</code></pre>`
        }
      )

      // 处理没有语言标记的代码块
      html = html.replace(
        /<pre><code>([\s\S]*?)<\/code><\/pre>/g,
        (_match, code) => {
          const decodedCode = decodeHtmlEntities(code)
          const highlighted = hljs.highlightAuto(decodedCode).value
          return `<pre><code class="hljs">${highlighted}</code></pre>`
        }
      )

      // #899：模型输出是不可信内容。marked 从 v5 起不再消毒、原样透传 HTML，
      // 而这里的结果会经 v-html 注入，等于把上游模型（或提示注入）给出的
      // <img onerror> / <script> 放进控制台源执行 —— 而 admin_token 就在
      // localStorage 里，所以必须在返回前统一消毒。hljs 产物是转义过的
      // <span class="hljs-*">，默认允许类名与 span，不受影响。
      return DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        ADD_ATTR: ['target', 'rel']
      })
    } catch {
      // 解析失败时同样不能把原文交给 v-html
      return DOMPurify.sanitize(text, { USE_PROFILES: { html: true } })
    }
  }

  /**
   * 解码 HTML 实体
   */
  const decodeHtmlEntities = (html: string): string => {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    return doc.documentElement.textContent || html
  }

  /**
   * 检测文本中的代码块语言
   */
  const detectLanguage = (code: string): string => {
    const result = hljs.highlightAuto(code)
    return result.language || 'plaintext'
  }

  /**
   * 消毒着色器产物（#899）：只保留 highlight.js 需要的 span/类名。
   */
  const sanitizeHighlighted = (html: string): string =>
    DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['span'],
      ALLOWED_ATTR: ['class']
    })

  /**
   * 高亮代码
   */
  const highlightCode = (code: string, language?: string): string => {
    // hljs 的 value 已对 < > & 做转义，这里再消毒一次是为了防止
    // 将来换成别的着色器（或 hljs 行为变化）时把未转义内容带进 v-html。
    if (language && hljs.getLanguage(language)) {
      try {
        return sanitizeHighlighted(hljs.highlight(code, { language }).value)
      } catch {
        // ignore
      }
    }
    return sanitizeHighlighted(hljs.highlightAuto(code).value)
  }

  return {
    render,
    detectLanguage,
    highlightCode
  }
}

// 代码块样式主题（可导入）
export const codeTheme = {
  light: 'github',
  dark: 'github-dark'
}