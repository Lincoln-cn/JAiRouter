/**
 * 链路追踪模块
 */
import { createAuthedContext, gotoApp, api } from '../harness.mjs'

export default {
  id: 'tracing',
  name: '链路追踪',
  suite: 'tracing',
  order: 100,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    const pages = [
      { path: '/tracing/dashboard', tag: 'dashboard', expect: /追踪|链路|Trace|Span|调用/i },
      { path: '/tracing/search', tag: 'search', expect: /搜索|追踪|Search|Trace|条件/i },
      { path: '/tracing/management', tag: 'management', expect: /管理|采样|配置|Management|Sample/i }
    ]

    for (const p of pages) {
      await gotoApp(page, p.path, { waitMs: 700 })
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`tracing:${p.tag}:content`, p.expect.test(body), body.slice(0, 100).replace(/\n/g, ' '))
      const ui = (await page.locator('.el-card,.el-table,.el-form,.el-button').count()) > 0
      check(`tracing:${p.tag}:ui`, ui)
    }

    // 搜索页：搜索表单/按钮
    await gotoApp(page, '/tracing/search', { waitMs: 600 })
    const hasSearchUi =
      (await page.locator('.el-input').count()) > 0 ||
      (await page.locator('button').filter({ hasText: /搜索|Search|查询|Query/ }).count()) > 0
    check('tracing:search:controls', hasSearchUi)

    // 管理页 API（常见采样配置）
    await gotoApp(page, '/tracing/management', { waitMs: 400 })
    const apiPaths = [
      '/api/tracing/config',
      '/api/tracing/management/config',
      '/api/tracing/settings'
    ]
    let anyOk = false
    const details = []
    for (const p of apiPaths) {
      const res = await api(page, p)
      details.push(`${p}=${res.status}`)
      if (res.status === 200) anyOk = true
    }
    check('tracing:management:api-any', anyOk || details.every(d => d.includes('=404')), details.join(','))

    // ── 清理缓存的二次确认（审计 #460）──────────────────────────────────────
    // 这条尤其需要守：按钮文案是「清理缓存」，但底层 refreshTracingData() 叫「刷新」、
    // 实际是 POST /tracing/actuator/clear-cache —— 名字和效果相反，误点代价不小。
    await gotoApp(page, '/tracing/management', { waitMs: 600 })
    // 「清理缓存」在 el-tab-pane name="advanced" 里 —— 不切到「高级配置」这个 tab，
    // 元素虽在 DOM 但不可见，locator.click 会一直等到超时（实测踩到）。
    await page
      .locator('.page-skeleton')
      .first()
      .waitFor({ state: 'hidden', timeout: 10000 })
      .catch(() => {})
    const advancedTab = page
      .locator('.el-tabs__item')
      .filter({ hasText: /高级配置|Advanced Configuration|Advanced/i })
      .first()
    if ((await advancedTab.count()) > 0) {
      await advancedTab.click({ timeout: 10000 }).catch(() => {})
      await page.waitForTimeout(500)
    } else {
      check('tracing:clear-cache:advanced-tab', false, 'skip: advanced tab not found')
    }
    const clearBtn = page.locator('.layout-main button').filter({ hasText: /清理缓存|Clear Cache/i }).first()
    if ((await clearBtn.count()) === 0) {
      check('tracing:clear-cache:confirm-dialog', false, 'skip: button not found')
    } else {
      const sent = []
      const onReq = (req) => {
        if (req.method() === 'POST' && req.url().includes('clear-cache')) sent.push(req.url())
      }
      page.on('request', onReq)

      await clearBtn.scrollIntoViewIfNeeded().catch(() => {})
      await clearBtn.click({ timeout: 10000 })
      const box = page.locator('.el-message-box').first()
      const appeared = await box
        .waitFor({ state: 'visible', timeout: 5000 })
        .then(() => true)
        .catch(() => false)
      check('tracing:clear-cache:confirm-dialog', appeared, appeared ? '.el-message-box visible' : 'no dialog appeared')

      if (appeared) {
        const cancel = box.locator('button').filter({ hasText: /取消|Cancel/i }).first()
        if ((await cancel.count()) > 0) {
          await cancel.click()
        } else {
          await box.locator('button').first().click()
        }
        await page.waitForTimeout(600)
      }
      // 无条件断言：弹层没出现时这条会红，而不是被跳过
      check('tracing:clear-cache:cancel-sends-nothing', sent.length === 0, `requests=${sent.length}`)

      page.off('request', onReq)
    }

    await page.close()
  }
}
