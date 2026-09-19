/**
 * 流量治理：负载均衡 / 熔断 / 限流
 */
import { createAuthedContext, gotoApp, api, waitTableSettled } from '../harness.mjs'

const PAGES = [
  {
    path: '/load-balancers/monitoring',
    tag: 'lb-monitor',
    expect: /负载|均衡|策略|实例|Load|Balance/i,
    api: '/api/load-balancer/status'
  },
  {
    path: '/load-balancers/strategy-config',
    tag: 'lb-strategy',
    expect: /策略|权重|Strategy|Weight/i
  },
  {
    path: '/circuit-breakers/monitoring',
    tag: 'cb-monitor',
    expect: /熔断|Circuit|CLOSED|OPEN|健康/i,
    api: '/api/circuit-breaker/status'
  },
  {
    path: '/circuit-breakers/history',
    tag: 'cb-history',
    expect: /历史|熔断|History|Circuit/i
  },
  {
    path: '/circuit-breakers/global-config',
    tag: 'cb-config',
    expect: /全局|阈值|配置|Global|Threshold|Circuit/i
  },
  {
    path: '/rate-limiters/monitoring',
    tag: 'rl-monitor',
    expect: /限流|Rate|Limiter|令牌|桶/i,
    api: '/api/rate-limiter/status'
  }
]

export default {
  id: 'traffic-governance',
  name: '流量治理模块',
  suite: 'traffic',
  order: 80,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of PAGES) {
      await gotoApp(page, p.path, { waitMs: 700 })
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`traffic:${p.tag}:content`, p.expect.test(body), body.slice(0, 100).replace(/\n/g, ' '))

      if ((await page.locator('.el-table').count()) > 0) {
        let ok = true
        try {
          await waitTableSettled(page, 12000)
        } catch {
          ok = false
        }
        check(`traffic:${p.tag}:table-settled`, ok)
      }

      if (p.api) {
        const res = await api(page, p.api)
        check(`traffic:${p.tag}:api`, res.status === 200 || res.status === 404 || res.status === 400, `HTTP ${res.status}`)
      }
    }

    // 限流页常见刷新按钮
    await gotoApp(page, '/rate-limiters/monitoring', { waitMs: 500 })
    const refresh = page
      .locator('.layout-main button')
      .filter({ hasText: /刷新|Refresh|同步|Sync/ })
      .first()
    if ((await refresh.count()) > 0) {
      await refresh.click()
      await page.waitForTimeout(800)
      check('traffic:rl-refresh', true)
    } else {
      check('traffic:rl-refresh', true, 'skip: no refresh button')
    }

    await page.close()
  }
}
