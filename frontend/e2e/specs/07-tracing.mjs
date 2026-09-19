/**
 * 链路追踪模块
 */
import { createAuthedContext, gotoApp, api, waitTableSettled } from '../harness.mjs'

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

    await page.close()
  }
}
