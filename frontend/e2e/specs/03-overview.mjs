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
    const ruleJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /规则/ }) })
      .locator('.gov-jump')
      .first()
    if ((await ruleJump.count()) > 0) {
      await ruleJump.click()
      await page.waitForTimeout(700)
      const path = currentPathname(page)
      check('overview-jump-rules', path.includes('/config/rules'), path)
      await gotoApp(page, '/dashboard/main', { waitMs: 400 })
    } else {
      check('overview-jump-rules', false, 'rule jump not found')
    }

    const rlJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /限流/ }) })
      .locator('.gov-jump')
      .first()
    if ((await rlJump.count()) > 0) {
      await rlJump.click()
      await page.waitForTimeout(700)
      check('overview-jump-rl', currentPathname(page).includes('/rate-limiters'), page.url())
    } else {
      check('overview-jump-rl', false, 'rl jump not found')
    }
    await gotoApp(page, '/dashboard/main', { waitMs: 400 })

    const cbJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /熔断/ }) })
      .locator('.gov-jump')
      .first()
    if ((await cbJump.count()) > 0) {
      await cbJump.click()
      await page.waitForTimeout(700)
      check('overview-jump-cb', currentPathname(page).includes('/circuit-breakers'), page.url())
    } else {
      check('overview-jump-cb', false, 'cb jump not found')
    }
    await gotoApp(page, '/dashboard/main', { waitMs: 800 })

    const lbJump = page
      .locator('.gov-section')
      .filter({ has: page.locator('.gov-label', { hasText: /负载/ }) })
      .locator('.gov-jump')
      .first()
    if ((await lbJump.count()) > 0) {
      await lbJump.click()
      await page.waitForTimeout(800)
      check('overview-jump-lb', currentPathname(page).includes('/load-balancers'), page.url())
    } else {
      // 页面可能仍在 hydrate，再等一次
      await page.waitForTimeout(800)
      const retry = page
        .locator('.gov-section')
        .filter({ has: page.locator('.gov-label', { hasText: /负载/ }) })
        .locator('.gov-jump')
        .first()
      if ((await retry.count()) > 0) {
        await retry.click()
        await page.waitForTimeout(800)
        check('overview-jump-lb', currentPathname(page).includes('/load-balancers'), page.url())
      } else {
        check('overview-jump-lb', false, 'lb jump not found')
      }
    }

    await page.close()
  }
}
