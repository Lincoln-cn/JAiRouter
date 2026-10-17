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

    // ── 不可逆操作的二次确认（审计 #460） ────────────────────────────────────
    // 这两处原先点一下就直接发写请求，且整文件没有任何确认语句。本块是这条修复的守卫：
    // ① 点击后必须弹出 .el-message-box；② 点「取消」不得发出任何写请求；③ 取消后弹层关闭。
    // 「不发请求」是这组断言的关键——只断言"弹了框"无法区分"确认框是个摆设"。
    const CONFIRM_CASES = [
      {
        path: '/load-balancers/monitoring',
        tag: 'lb-clear-history',
        button: /清空历史|Clear History/i,
        method: 'DELETE',
        urlPart: '/history'
      },
      {
        path: '/circuit-breakers/global-config',
        tag: 'cb-reset-global',
        button: /重置为默认值|Reset to Defaults/i,
        method: 'POST',
        urlPart: '/global-config/reset'
      }
    ]

    for (const c of CONFIRM_CASES) {
      await gotoApp(page, c.path, { waitMs: 700 })
      const btn = page.locator('.layout-main button').filter({ hasText: c.button }).first()
      if ((await btn.count()) === 0) {
        check(`traffic:${c.tag}:confirm-dialog`, false, 'skip: button not found')
        continue
      }

      const sent = []
      const onReq = (req) => {
        if (req.method() === c.method && req.url().includes(c.urlPart)) sent.push(req.url())
      }
      page.on('request', onReq)

      await btn.click()
      const box = page.locator('.el-message-box').first()
      const appeared = await box
        .waitFor({ state: 'visible', timeout: 5000 })
        .then(() => true)
        .catch(() => false)
      check(`traffic:${c.tag}:confirm-dialog`, appeared, appeared ? '.el-message-box visible' : 'no dialog appeared')

      if (appeared) {
        const cancel = box.locator('button').filter({ hasText: /取消|Cancel/i }).first()
        if ((await cancel.count()) > 0) {
          await cancel.click()
        } else {
          // 兜底：没有文案可辨时按「非主按钮」点（避免误点确认反而触发写请求）
          await box.locator('button').first().click()
        }
        await page.waitForTimeout(700)
      }

      // 这条**无条件**断言：无论弹层有没有出现，点这个按钮都不许立刻发出写请求。
      // 放在 if 里面就等于「弹层没出现时跳过」，会把最关键的失败静默掉（A/B 实测过）。
      check(`traffic:${c.tag}:cancel-sends-nothing`, sent.length === 0, `write requests=${sent.length}`)

      if (appeared) {
        const closed = (await page.locator('.el-message-box').count()) === 0
        check(`traffic:${c.tag}:dialog-closed`, closed)
      }

      page.off('request', onReq)
    }

    await page.close()
  }
}
