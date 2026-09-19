/**
 * 开发者工具：客户端接入指南
 */
import { createAuthedContext, gotoApp } from '../harness.mjs'

export default {
  id: 'tools',
  name: '开发者工具',
  suite: 'tools',
  order: 130,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)
    await gotoApp(page, '/tools/client-access', { waitMs: 800 })

    const body = await page.locator('.layout-main').innerText().catch(() => '')
    check(
      'tools:client-access:content',
      /接入|客户端|指南|Client|Access|Base URL|API Key|curl|OpenAI/i.test(body),
      body.slice(0, 120).replace(/\n/g, ' ')
    )

    const hasCodeOrTabs =
      (await page.locator('pre, code, .el-tabs, .el-card').count()) > 0
    check('tools:client-access:ui', hasCodeOrTabs)

    // 若有页签/语言切换，尝试点击
    const tabs = page.locator('.el-tabs__item')
    if ((await tabs.count()) >= 2) {
      await tabs.nth(1).click()
      await page.waitForTimeout(400)
      const after = await page.locator('.layout-main').innerText().catch(() => '')
      check('tools:client-access:tabs', after.length > 10)
    } else {
      check('tools:client-access:tabs', true, 'no tabs')
    }

    // 复制按钮若存在
    const copyBtn = page
      .locator('button')
      .filter({ hasText: /复制|Copy/ })
      .first()
    if ((await copyBtn.count()) > 0) {
      await copyBtn.click()
      await page.waitForTimeout(300)
      check('tools:client-access:copy', true)
    } else {
      check('tools:client-access:copy', true, 'skip: no copy button')
    }

    await page.close()
  }
}
