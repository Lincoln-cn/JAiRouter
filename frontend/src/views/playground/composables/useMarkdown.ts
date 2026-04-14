import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'

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

      return html
    } catch {
      return text
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
   * 高亮代码
   */
  const highlightCode = (code: string, language?: string): string => {
    if (language && hljs.getLanguage(language)) {
      try {
        return hljs.highlight(code, { language }).value
      } catch {
        // ignore
      }
    }
    return hljs.highlightAuto(code).value
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