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

    // 概览页首屏会自己刷新一轮数据（Promise.all 拉多个统计），区块在数据回来后才稳定。
    // 全量跑批时如果不等它，四个跳转里会随机某个「找不到按钮」——
    // 这不是按钮没了，是那一刻 DOM 还没这块。
    await page
      .locator('.gov-section')
      .first()
      .waitFor({ state: 'visible', timeout: 15000 })
      .catch(() => {})

    // 治理链路跳转：按钮文案是「查看/监控」，需按区块标签定位
    // 治理链路的四个跳转都用 waitForURL 等路由真正换掉：
    // 目标路由是懒加载 chunk，全量跑批时可能 >700ms 才落地，
    // 固定 sleep 会把「慢」误判成「跳转失败」（本轮全量实测 469/470 的唯一红项）。

    // 治理链路四个跳转统一走这里：定位 → 点击 → 等 URL，失败重试一次。
    // 概览页首屏会自己刷新统计数据并重渲染这几个按钮，点击偶尔落在被替换的节点上；
    // 单次点击的偶发失败不该判成「页面坏了」，所以这里重试并把最终 URL 打进 detail。
    const clickGovJump = async (labelRe, pathFragment) => {
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const button = page
          .locator('.gov-section')
          .filter({ has: page.locator('.gov-label', { hasText: labelRe }) })
          .locator('.gov-jump')
          .first()
        if ((await button.count()) === 0) {
          await page.waitForTimeout(500)
          continue
        }
        await button.click({ timeout: 5000 }).catch(() => {})
        await page
          .waitForURL((u) => u.pathname.includes(pathFragment), { timeout: 5000 })
          .catch(() => {})
        if (currentPathname(page).includes(pathFragment)) {
          return true
        }
        await gotoApp(page, '/dashboard/main', { waitMs: 800 })
      }
      return false
    }

    const ruleOk = await clickGovJump(/规则|Rule/i, '/config/rules')
    check('overview-jump-rules', ruleOk, page.url())
    await gotoApp(page, '/dashboard/main', { waitMs: 600 })

    const rlOk = await clickGovJump(/限流|Rate/i, '/rate-limiters')
    check('overview-jump-rl', rlOk, page.url())
    await gotoApp(page, '/dashboard/main', { waitMs: 600 })

    const cbOk = await clickGovJump(/熔断|Circuit/i, '/circuit-breakers')
    check('overview-jump-cb', cbOk, page.url())
    await gotoApp(page, '/dashboard/main', { waitMs: 600 })

    const lbOk = await clickGovJump(/负载|Load/i, '/load-balancers')
    check('overview-jump-lb', lbOk, page.url())

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
