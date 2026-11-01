/**
 * 概览仪表板：统计卡片、治理链路、跳转入口
 */
import { createAuthedContext, gotoApp, currentPathname } from '../harness.mjs'

export default {
  id: 'overview',
  name: '仪表板概览',
  suite: 'overview',
  order: 60,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)
    await gotoApp(page, '/dashboard/main', { waitMs: 800 })

    check('overview-cards', (await page.locator('.el-card, .card-panel, .stats-wrap').count()) > 0)

    const body = await page.locator('.layout-main').innerText().catch(() => '')
    const hasStats = /服务|实例|请求|健康|Service|Instance|Request|Health|治理|规则/i.test(body)
    check('overview-stats-text', hasStats, body.slice(0, 120).replace(/\n/g, ' '))

    // 治理链路区块
    const hasGovernance = (await page.locator('.governance-card, .gov-section').count()) > 0
    check('overview-governance', hasGovernance)

    // 治理链路跳转：按钮文案是「查看/监控」，需按区块标签定位
    // 治理链路的四个跳转都用 waitForURL 等路由真正换掉：
    // 目标路由是懒加载 chunk，全量跑批时可能 >700ms 才落地，
    // 固定 sleep 会把「慢」误判成「跳转失败」（本轮全量实测 469/470 的唯一红项）。
    const jumpTo = async (pathFragment) => {
      await page
        .waitForURL((u) => u.pathname.includes(pathFragment), { timeout: 5000 })
        .catch(() => {})
      await page.waitForTimeout(150)
    }

    const ruleJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /规则|Rule/i }) })
      .locator('.gov-jump')
      .first()
    if ((await ruleJump.count()) > 0) {
      await ruleJump.click()
      await jumpTo('/config/rules')
      const path = currentPathname(page)
      check('overview-jump-rules', path.includes('/config/rules'), path)
      await gotoApp(page, '/dashboard/main', { waitMs: 400 })
    } else {
      check('overview-jump-rules', false, 'rule jump not found')
    }

    const rlJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /限流|Rate/i }) })
      .locator('.gov-jump')
      .first()
    if ((await rlJump.count()) > 0) {
      await rlJump.click()
      await jumpTo('/rate-limiters')
      check('overview-jump-rl', currentPathname(page).includes('/rate-limiters'), page.url())
    } else {
      check('overview-jump-rl', false, 'rl jump not found')
    }
    await gotoApp(page, '/dashboard/main', { waitMs: 400 })

    const cbJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /熔断|Circuit/i }) })
      .locator('.gov-jump')
      .first()
    if ((await cbJump.count()) > 0) {
      await cbJump.click()
      await jumpTo('/circuit-breakers')
      check('overview-jump-cb', currentPathname(page).includes('/circuit-breakers'), page.url())
    } else {
      check('overview-jump-cb', false, 'cb jump not found')
    }
    await gotoApp(page, '/dashboard/main', { waitMs: 800 })

    const lbJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /负载|Load/i }) })
      .locator('.gov-jump')
      .first()
    if ((await lbJump.count()) > 0) {
      await lbJump.click()
      await jumpTo('/load-balancers')
      check('overview-jump-lb', currentPathname(page).includes('/load-balancers'), page.url())
    } else {
      // 页面可能仍在 hydrate，再等一次
      await page.waitForTimeout(800)
      const retry = page
        .locator('.gov-section')
        .filter({ has: page.locator('.gov-label', { hasText: /负载|Load/i }) })
        .locator('.gov-jump')
        .first()
      if ((await retry.count()) > 0) {
        await retry.click()
        await jumpTo('/load-balancers')
        check('overview-jump-lb', currentPathname(page).includes('/load-balancers'), page.url())
      } else {
        check('overview-jump-lb', false, 'lb jump not found')
      }
    }

    // 服务配置速览：页签必须能切换（页签曾做成 router-link，点一下整页跳走、表格不换 —— 用户 2026-10-21 反馈）
    await gotoApp(page, '/dashboard/main', { waitMs: 800 })
    const svcCard = page.locator('.config-card').first()
    const svcTabs = svcCard.locator('.el-tabs__item')
    // 服务类型页签来自 /api/monitoring/overview 的异步数据：等它渲染出来（最多 8s），否则只有「全局配置」一个页签
    for (let i = 0; i < 16; i += 1) {
      if ((await svcTabs.count()) > 2) break
      await page.waitForTimeout(500)
    }
    const svcTabCount = await svcTabs.count()
    if (svcTabCount > 2) {
      const beforePath = currentPathname(page)
      await svcTabs.nth(2).click()
      await page.waitForTimeout(400)
      const activeLabel = (await svcCard.locator('.el-tabs__item.is-active').first().innerText().catch(() => '')).trim()
      const afterPath = currentPathname(page)
      check(
        'overview-service-tab-switch',
        activeLabel.length > 0 && afterPath === beforePath,
        `active=${activeLabel} path=${afterPath} tabs=${svcTabCount}`
      )
    } else {
      check('overview-service-tab-switch', false, `tabs=${svcTabCount}`)
    }

    await page.close()
  }
}
