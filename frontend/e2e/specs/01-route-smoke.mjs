/**
 * 全路由冒烟：覆盖 router 中全部业务路由
 */
import { createAuthedContext, gotoApp, assertAppPageHealthy } from '../harness.mjs'
import { ALL_ROUTES, PUBLIC_ROUTES } from '../routes.mjs'

export default {
  id: 'route-smoke',
  name: '全路由冒烟',
  suite: 'smoke',
  order: 40,
  async run({ browser, check }) {
    // 公开路由
    {
      const { newPage } = await import('../harness.mjs')
      const anon = await newPage(browser)
      for (const r of PUBLIC_ROUTES) {
        await gotoApp(anon, r.path)
        await anon.waitForTimeout(400)
        const hasForm = await anon.locator('.login-form').count().catch(() => 0)
        check(`smoke:public:${r.name}`, hasForm > 0, anon.url())
      }
      await anon.close()
    }

    const page = await createAuthedContext(browser)
    check('smoke-session-ready', true, page.url())

    // 按路由逐个冒烟；单页失败不中断后续
    for (const route of ALL_ROUTES) {
      try {
        await gotoApp(page, route.path, { waitMs: 600 })
        // 给异步请求/懒加载一点时间
        await page.waitForTimeout(400)
        await assertAppPageHealthy(page, route, check)
      } catch (e) {
        check(`smoke:${route.name}:aborted`, false, e?.message || String(e))
      }
    }

    await page.close()
  }
}
