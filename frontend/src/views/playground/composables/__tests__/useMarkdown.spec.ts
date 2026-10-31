// @vitest-environment jsdom
// happy-dom 的 DOM 实现会让 DOMPurify 误动作（实测 sanitize 会保留 <script>、
// 却删掉 <h1>），所以这一组用例固定在 jsdom 下跑。
import { describe, it, expect } from 'vitest'
import { useMarkdown } from '../useMarkdown'

// #899：模型输出是不可信内容，渲染结果会经 v-html 进入控制台 DOM，
// 而 admin_token 就存在 localStorage 里 —— 所以这些用例守住「消毒不退化」。
describe('useMarkdown 渲染消毒（#899）', () => {
  const { render, highlightCode } = useMarkdown()

  it('剥离 onerror 等事件处理器', () => {
    const html = render('<img src=x onerror="alert(1)">')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('alert(1)')
  })

  it('剥离 script 标签', () => {
    const html = render('hello\n\n<script>alert(document.cookie)</script>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('document.cookie')
  })

  it('剥离 javascript: 链接协议', () => {
    const html = render('[click](javascript:alert(1))')
    expect(html).not.toContain('javascript:')
  })

  it('iframe / svg onload 同样被剥离', () => {
    const html = render('<iframe src="https://evil.example"></iframe><svg onload="alert(1)"></svg>')
    expect(html).not.toContain('onload')
  })

  it('正常 markdown 仍然渲染（防消毒过度）', () => {
    const html = render('# 标题\n\n**加粗** 与 `inline`')
    expect(html).toContain('<h1')
    expect(html).toContain('<strong>')
    expect(html).toContain('<code>')
  })

  it('代码块高亮仍保留 hljs 类名', () => {
    const html = render('```java\nint x = 1;\n```')
    expect(html).toContain('hljs')
    expect(html).toContain('<span class=')
  })

  it('代码块里的尖括号被转义而不是当标签执行', () => {
    const html = render('```html\n<script>alert(1)</script>\n```')
    expect(html).not.toContain('<script>')
  })

  it('highlightCode 的产物也经过消毒', () => {
    const html = highlightCode('<img src=x onerror="alert(1)">', 'html')
    // hljs 会把属性名当文本着色，所以 'onerror' 作为纯文本出现是安全的；
    // 危险的是它作为真实属性被解析 —— 断言没有未转义的标签开头。
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;')
  })
})
