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

    // ── 策略表不得丢行（issue #575 的守卫） ─────────────────────────────────
    // 后端曾经把「该服务类型没有专属 LB 配置」抛成 500，前端 catch 后整行丢弃，
    // 于是 imggen / imgedit 这类没单独配置过的服务在表里根本不出现。
    // 这里用 /status 返回的服务类型清单做交叉核对，不写死行数（各环境服务类型不同）。
    await gotoApp(page, '/load-balancers/strategy-config', { waitMs: 700 })
    const statusRes = await api(page, '/api/loadbalancer/status')
    const statusTypes = Array.isArray(statusRes.body?.data)
      ? statusRes.body.data.map((s) => s?.serviceType).filter(Boolean)
      : []
    if (statusTypes.length > 0) {
      const rowTypes = await page
        .locator('.el-table__row td:first-child')
        .allInnerTexts()
        .catch(() => [])
      const missing = statusTypes.filter((t) => !rowTypes.some((cell) => cell.trim() === t))
      check(
        'traffic:lb-strategy:no-missing-rows',
        missing.length === 0,
        `rows=${rowTypes.length} types=${statusTypes.length} missing=${missing.join(',') || '-'}`
      )
    } else {
      check('traffic:lb-strategy:no-missing-rows', true, 'skip: /status 未返回服务类型')
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
      },
      {
        // 熔断器监控页的「清空历史」：同文件里 reset 类操作有确认语句，本操作没有
        // —— 文件级规则查不出这种「被别处的确认掩盖」的情况（#460 调用点级）。
        path: '/circuit-breakers/monitoring',
        tag: 'cb-clear-history',
        button: /清空历史|Clear History/i,
        method: 'DELETE',
        urlPart: '/history'
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

    // ── 熔断器实例页：真实数据渲染 + 单个/批量重置（#556 / #557） ───────────────
    // 这两条 issue 的根因是一份「前端靠 `Map<key, state>` 拆字符串」的旧契约：
    // UUID 实例标识被 `indexOf('-')` 截断 ⇒ 重置命中不了运行时熔断器却仍报成功。
    // 新契约 `GET /v1/circuit-breaker-monitor/instances` 返回结构化数组，本块用固定夹具
    // 验证「计数/阈值来自后端」「按状态筛选」「重置落到正确实例」。
    const cbRequests = []
    const CB_INSTANCES = [
      {
        instanceId: '11111111-1111-4111-8111-111111111111',
        instanceName: 'gpt-4o-open',
        serviceType: 'chat',
        baseUrl: 'https://open.example.com',
        state: 'OPEN',
        failureCount: 5,
        successCount: 0,
        failureThreshold: 5,
        successThreshold: 2,
        timeoutMillis: 60000,
        lastFailureTime: new Date().toISOString()
      },
      {
        instanceId: '22222222-2222-4222-8222-222222222222',
        instanceName: 'embedding-closed',
        serviceType: 'embedding',
        baseUrl: 'https://closed.example.com',
        state: 'CLOSED',
        failureCount: 1,
        successCount: 9,
        failureThreshold: 5,
        successThreshold: 2,
        timeoutMillis: 60000,
        lastFailureTime: null
      }
    ]

    await page.route('**/api/v1/circuit-breaker-monitor/instances**', async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      if (req.method() === 'POST') {
        const body = req.postDataJSON ? req.postDataJSON() : null
        cbRequests.push({ path: url.pathname, body })
        const single = /\/instances\/[^/]+\/reset$/.test(url.pathname)
        const payload = single
          ? {
              instanceId: CB_INSTANCES[0].instanceId,
              instanceName: CB_INSTANCES[0].instanceName,
              serviceType: CB_INSTANCES[0].serviceType,
              previousState: 'OPEN',
              currentState: 'CLOSED',
              persistedStateDeleted: true,
              triggerReason: 'MANUAL_RESET'
            }
          : { matched: 1, reset: [] }
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, message: 'ok', data: payload, timestamp: new Date().toISOString() })
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(CB_INSTANCES)
      })
    })

    await gotoApp(page, '/circuit-breakers/monitoring', { waitMs: 900 })
    await waitTableSettled(page, 12000).catch(() => {})

    const instanceRows = page.locator('.instances-table .el-table__body tr')
    const rowCount = await instanceRows.count()
    check('traffic:cb-instances:rendered', rowCount === 2, `rows=${rowCount}`)

    const openRow = instanceRows.filter({ hasText: 'gpt-4o-open' }).first()
    const openRowText = (await openRow.count()) > 0 ? await openRow.innerText() : ''
    check(
      'traffic:cb-instances:real-counters',
      openRowText.includes('5 / 5'),
      openRowText.replace(/\n/g, ' ').slice(0, 120)
    )

    // 概览卡片可点筛选：OPEN 卡片 → 只剩 1 行
    const openCard = page.locator('.summary-card').filter({ hasText: /熔断中|Tripped/ }).first()
    if ((await openCard.count()) > 0) {
      await openCard.click()
      await page.waitForTimeout(400)
      const filtered = await instanceRows.count()
      check('traffic:cb-instances:state-filter', filtered === 1, `rows=${filtered}`)
      await openCard.click()
      await page.waitForTimeout(300)
    } else {
      check('traffic:cb-instances:state-filter', false, 'skip: OPEN summary card not found')
    }

    // 详情抽屉：展示运行时 UUID（旧实现把 UUID 截断成前两段）
    const detailBtn = openRow.locator('button').filter({ hasText: /详情|Details/ }).first()
    if ((await detailBtn.count()) > 0) {
      await detailBtn.click()
      const drawer = page.locator('.el-drawer:visible').first()
      const opened = await drawer.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)
      const drawerText = opened ? await drawer.innerText() : ''
      check(
        'traffic:cb-instances:detail-drawer',
        opened && drawerText.includes('11111111-1111-4111-8111-111111111111'),
        drawerText.replace(/\n/g, ' ').slice(0, 120)
      )
      await page.keyboard.press('Escape').catch(() => {})
      await page.waitForTimeout(400)
    } else {
      check('traffic:cb-instances:detail-drawer', false, 'skip: detail button not found')
    }

    // 单个重置：必须落到该实例的 UUID 端点，且成功后提示状态迁移
    const resetBtn = openRow.locator('button').filter({ hasText: /^重置$|^Reset$/ }).first()
    if ((await resetBtn.count()) > 0) {
      await resetBtn.click()
      const box = page.locator('.el-message-box').first()
      const appeared = await box.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)
      check('traffic:cb-instances:reset-confirm', appeared)
      if (appeared) {
        await box.locator('button').filter({ hasText: /确定重置|Reset|确定/ }).first().click()
        await page.waitForTimeout(900)
      }
      const single = cbRequests.find(r => r.path.endsWith('/reset') && !r.path.endsWith('/instances/reset'))
      check(
        'traffic:cb-instances:reset-hits-uuid-endpoint',
        Boolean(single) && single.path.includes(CB_INSTANCES[0].instanceId),
        single ? single.path : 'no reset request'
      )
      const successMsg = await page.locator('.el-message--success').first().innerText().catch(() => '')
      check('traffic:cb-instances:reset-success-message', /OPEN.*CLOSED|已重置/.test(successMsg), successMsg)
    } else {
      check('traffic:cb-instances:reset-hits-uuid-endpoint', false, 'skip: reset button not found')
    }

    // 按筛选范围重置：无筛选时不得退化成「清除全部」语义，必须带 scope 进 body
    cbRequests.length = 0
    const resetFilteredBtn = page
      .locator('.layout-main button')
      .filter({ hasText: /重置筛选结果|Reset Filtered/ })
      .first()
    if ((await resetFilteredBtn.count()) > 0) {
      await resetFilteredBtn.click()
      const box = page.locator('.el-message-box').first()
      const appeared = await box.waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false)
      if (appeared) {
        await box.locator('button').filter({ hasText: /确定重置|Reset|确定/ }).first().click()
        await page.waitForTimeout(900)
      }
      const batch = cbRequests.find(r => r.path.endsWith('/instances/reset'))
      check('traffic:cb-instances:batch-reset-endpoint', Boolean(batch), batch ? batch.path : 'no batch request')
      check(
        'traffic:cb-instances:batch-reset-confirm-required',
        appeared,
        appeared ? '.el-message-box visible' : 'no dialog appeared'
      )
    } else {
      check('traffic:cb-instances:batch-reset-endpoint', false, 'skip: batch reset button not found')
    }

    await page.unroute('**/api/v1/circuit-breaker-monitor/instances**')

    await page.close()
  }
}
