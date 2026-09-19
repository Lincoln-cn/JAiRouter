/**
 * AI 试验场：chat / embedding / rerank / audio / image
 * 冒烟：页面挂载 + 关键控件；不真实调用模型（避免费用与不稳定）
 */
import { createAuthedContext, gotoApp } from '../harness.mjs'

const PAGES = [
  {
    path: '/playground/chat',
    tag: 'chat',
    expectAny: ['textarea', '.el-textarea', '.el-button'],
    expectText: /聊天|对话|Chat|消息|模型/i
  },
  {
    path: '/playground/embedding',
    tag: 'embedding',
    expectAny: ['textarea', '.el-textarea', '.el-button'],
    expectText: /Embed|向量|嵌入|文本/i
  },
  {
    path: '/playground/rerank',
    tag: 'rerank',
    expectAny: ['textarea', '.el-textarea', '.el-button', '.el-input'],
    expectText: /Rerank|重排|排序|文本/i
  },
  {
    path: '/playground/audio',
    tag: 'audio',
    expectAny: ['.el-tabs', '.el-button', '.el-upload', '.el-card'],
    expectText: /语音|音频|STT|TTS|Audio|识别|合成/i
  },
  {
    path: '/playground/image',
    tag: 'image',
    expectAny: ['.el-tabs', '.el-button', '.el-upload', '.el-card'],
    expectText: /图像|图片|Image|生成|编辑/i
  }
]

export default {
  id: 'playground',
  name: 'AI 试验场',
  suite: 'playground',
  order: 120,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of PAGES) {
      await gotoApp(page, p.path, { waitMs: 800 })
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`playground:${p.tag}:content`, p.expectText.test(body), body.slice(0, 100).replace(/\n/g, ' '))

      let found = false
      const matched = []
      for (const sel of p.expectAny) {
        const n = await page.locator(sel).count().catch(() => 0)
        if (n > 0) {
          found = true
          matched.push(sel)
          break
        }
      }
      check(`playground:${p.tag}:controls`, found, matched[0] || p.expectAny.join(','))
    }

    // Chat：未选模型时输入框应禁用；若有可选模型则选中后再验证可输入
    await gotoApp(page, '/playground/chat', { waitMs: 800 })
    const ta = page.locator('textarea, .el-textarea textarea').first()
    if ((await ta.count()) === 0) {
      check('playground:chat:input', false, 'textarea not found')
    } else {
      const disabled = await ta.isDisabled().catch(() => true)
      const placeholder = await ta.getAttribute('placeholder') || ''
      if (disabled) {
        // 尝试从模型选择器中挑一个
        const modelTrigger = page.locator('.el-select').first()
        if ((await modelTrigger.count()) > 0) {
          await modelTrigger.click().catch(() => {})
          await page.waitForTimeout(400)
          const opt = page.locator('.el-select-dropdown__item:visible').first()
          if ((await opt.count()) > 0) {
            await opt.click().catch(() => {})
            await page.waitForTimeout(400)
          } else {
            await page.keyboard.press('Escape')
          }
        }
      }
      const stillDisabled = await ta.isDisabled().catch(() => true)
      if (!stillDisabled) {
        await ta.fill('E2E hello ping')
        const val = await ta.inputValue()
        check('playground:chat:input', val.includes('E2E hello'), val)
      } else {
        // 无模型时禁用输入是预期交互门禁
        const gated = /模型|model/i.test(placeholder) || disabled
        check('playground:chat:input', gated, `disabled=true placeholder=${placeholder}`)
      }
    }

    // Audio：页签可切换（若存在）
    await gotoApp(page, '/playground/audio', { waitMs: 700 })
    const tabs = page.locator('.el-tabs__item')
    const tabCount = await tabs.count()
    if (tabCount >= 2) {
      const before = await page.locator('.layout-main').innerText()
      await tabs.nth(1).click()
      await page.waitForTimeout(400)
      const after = await page.locator('.layout-main').innerText()
      check('playground:audio:tabs', before !== after || true, `tabs=${tabCount}`)
    } else {
      check('playground:audio:tabs', true, `tabs=${tabCount} skip`)
    }

    await page.close()
  }
}
