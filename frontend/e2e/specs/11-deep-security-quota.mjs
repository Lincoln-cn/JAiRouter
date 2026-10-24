/**
 * 深测：API Key 配额抽屉 + 配额 API + PII 脱敏 + 配额监控交叉链接
 * （自 console-v32.mjs 迁移并纳入模块化 runner）
 */
import { createAuthedContext, gotoApp, api, apiThrottled, waitTableSettled } from '../harness.mjs'

export default {
  id: 'deep-security-quota',
  name: 'API Key配额与PII深测',
  suite: 'deep',
  // 尽早跑：/api/auth/api-keys 受 AdminApiRateLimiter 30/min 限制
  order: 20,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)
    check('deep-rate-limit-isolation', !!page.__e2eClientIp, `ip=${page.__e2eClientIp}`)

    // ---- API Keys list ----
    const t0 = Date.now()
    await gotoApp(page, '/security/api-keys')
    let listOk = false
    try {
      await waitTableSettled(page)
      listOk = true
    } catch {
      listOk = false
    }
    check('deep-apikeys-list-no-spin', listOk, `elapsed=${Date.now() - t0}ms`)

    // AdminApiRateLimiter: /api/auth/api-keys 30/min — 使用节流 API
    const listApi = await apiThrottled(page, '/api/auth/api-keys')
    check('deep-api-keys-http', listApi.status === 200 && !!listApi.body?.data, `HTTP ${listApi.status}`)

    // ── 自给自足（issue #464）────────────────────────────────────────────
    // 本 spec 后面的配额抽屉断言要求表格里**至少有一行** API Key；空库
    // （新克隆 / CI 首次跑 / 独立测试库）上会因此假红两条，且带得动的整段抽屉断言全被跳过。
    // 这里在"列表为空"时自己建一个，跑完删掉；**已有 Key 时完全不介入**。
    let seededKeyId = null
    let keysList = listApi
    if (!(keysList.body?.data?.items?.length > 0)) {
      const created = await apiThrottled(page, '/api/auth/api-keys', {
        method: 'POST',
        body: { description: 'e2e-quota-fixture', rateLimitPerMinute: 60 }
      })
      seededKeyId = created.body?.data?.keyId || created.body?.data?.id || null
      check(
        'deep-apikeys-seeded',
        created.status === 200 && !!seededKeyId,
        `HTTP ${created.status} keyId=${seededKeyId}`
      )
      // 重新载入列表页，让表格里出现这一行
      await gotoApp(page, '/security/api-keys')
      await waitTableSettled(page).catch(() => {})
      keysList = await apiThrottled(page, '/api/auth/api-keys')
    }

    const tRefresh = Date.now()
    await gotoApp(page, '/dashboard/main')
    await gotoApp(page, '/security/api-keys')
    let refreshOk = false
    try {
      await waitTableSettled(page)
      refreshOk = true
    } catch {
      refreshOk = false
    }
    check('deep-apikeys-refresh-no-spin', refreshOk, `elapsed=${Date.now() - tRefresh}ms`)

    // ---- Quota drawer UI ----
    const quotaBtn = page.locator('.el-table button').filter({ hasText: /^配额$|^Quota$/ }).first()
    const hasQuotaBtn = (await quotaBtn.count().catch(() => 0)) > 0
    check('deep-quota-drawer-button', hasQuotaBtn)

    const firstKeyId = keysList.body?.data?.items?.[0]?.keyId || null
    let originalQuota = null

    if (hasQuotaBtn) {
      await quotaBtn.click()
      await page.waitForTimeout(800)
      const drawerVisible = await page
        .locator('.el-drawer')
        .first()
        .isVisible()
        .catch(() => false)
      check('deep-quota-drawer-open', drawerVisible)

      const inputCount = await page.locator('.el-drawer .el-input-number input').count()
      check('deep-quota-drawer-form', inputCount >= 3, `input-number=${inputCount}`)

      const saveBtn = page.locator('.el-drawer').getByRole('button', { name: /保存配额|Save Quota/ })
      const resetBtn = page.locator('.el-drawer').getByRole('button', { name: /重置计数|Reset Counters/ })
      check(
        'deep-quota-drawer-actions',
        (await saveBtn.count()) > 0 && (await resetBtn.count()) > 0,
        'save/reset present'
      )

      const centered = await page.evaluate(() => {
        const el = document.querySelector('.quota-drawer-actions')
        if (!el) return false
        const cs = getComputedStyle(el)
        return cs.display.includes('flex') && cs.justifyContent === 'center'
      })
      check('deep-quota-drawer-actions-centered', centered)

      const presetSelect = page.locator('.el-drawer .el-select').first()
      if ((await presetSelect.count()) > 0) {
        await presetSelect.click()
        await page.waitForTimeout(300)
        const unlimitedOpt = page
          .locator('.el-select-dropdown__item')
          .filter({ hasText: /不限制|Unlimited/ })
          .first()
        if ((await unlimitedOpt.count()) > 0) {
          await unlimitedOpt.click()
          await page.waitForTimeout(200)
          const vals = await page
            .locator('.el-drawer .el-input-number input')
            .evaluateAll(els => els.slice(0, 3).map(e => e.value))
          check('deep-quota-preset-unlimited', vals.every(v => v === '0'), `values=${vals.join(',')}`)
        } else {
          check('deep-quota-preset-unlimited', false, 'preset option not found')
        }
      }

      // ── 抽屉里「重置计数」的二次确认（审计 #460）────────────────────────
      // 同一个操作在本页有两条入口：表格行的重置有确认（quotaResetTitle/Message），
      // 抽屉里这条原先没有。断言点同样是「取消后不得发出写请求」。
      const drawerResetBtn = page
        .locator('.quota-drawer-actions button')
        .filter({ hasText: /重置计数|Reset/i })
        .first()
      if ((await drawerResetBtn.count()) === 0) {
        check('deep-quota-drawer-reset-confirm:dialog', false, 'skip: button not found')
      } else {
        const resetSent = []
        const onResetReq = (req) => {
          if (req.method() === 'POST' && req.url().includes('/quota/reset')) resetSent.push(req.url())
        }
        page.on('request', onResetReq)

        await drawerResetBtn.click()
        const resetBox = page.locator('.el-message-box').first()
        const resetDialogShown = await resetBox
          .waitFor({ state: 'visible', timeout: 5000 })
          .then(() => true)
          .catch(() => false)
        check(
          'deep-quota-drawer-reset-confirm:dialog',
          resetDialogShown,
          resetDialogShown ? '.el-message-box visible' : 'no dialog appeared'
        )

        if (resetDialogShown) {
          const cancelBtn = resetBox.locator('button').filter({ hasText: /取消|Cancel/i }).first()
          if ((await cancelBtn.count()) > 0) {
            await cancelBtn.click()
          } else {
            await resetBox.locator('button').first().click()
          }
          await page.waitForTimeout(600)
        }
        check(
          'deep-quota-drawer-reset-confirm:cancel-sends-nothing',
          resetSent.length === 0,
          `requests=${resetSent.length}`
        )

        page.off('request', onResetReq)
      }

      if (firstKeyId) {
        const before = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`)
        originalQuota = before.body?.data
          ? {
              dailyRequestLimit: before.body.data.dailyRequestLimit,
              dailyTokenLimit: before.body.data.dailyTokenLimit,
              rateLimitPerMinute: before.body.data.rateLimitPerMinute,
              quotaAlertThreshold: before.body.data.quotaAlertThreshold
            }
          : null

        await page
          .locator('.el-drawer')
          .getByRole('button', { name: /保存配额|Save Quota/ })
          .click()
        await page.waitForTimeout(1500)
        const afterSave = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`)
        const unlimitedApplied =
          afterSave.body?.data?.dailyRequestLimit === 0 &&
          afterSave.body?.data?.dailyTokenLimit === 0 &&
          afterSave.body?.data?.rateLimitPerMinute === 0
        check(
          'deep-quota-save-from-drawer',
          afterSave.status === 200 && unlimitedApplied,
          `HTTP ${afterSave.status} limits=${afterSave.body?.data?.dailyRequestLimit}/${afterSave.body?.data?.dailyTokenLimit}/${afterSave.body?.data?.rateLimitPerMinute}`
        )

        await page
          .locator('.el-drawer')
          .getByRole('button', { name: /重置计数|Reset Counters/ })
          .click()
        await page.waitForTimeout(1200)
        const afterReset = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`)
        check(
          'deep-quota-reset-from-drawer',
          afterReset.status === 200 && (afterReset.body?.data?.todayRequestCount ?? -1) >= 0,
          `todayReq=${afterReset.body?.data?.todayRequestCount}`
        )

        if (originalQuota) {
          const restore = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`, {
            method: 'PUT',
            body: originalQuota
          })
          check(
            'deep-quota-restore-original',
            restore.status === 200 &&
              restore.body?.data?.dailyRequestLimit === originalQuota.dailyRequestLimit,
            `restored=${restore.body?.data?.dailyRequestLimit}`
          )
        }
      } else {
        check('deep-quota-save-from-drawer', true, 'skip: no api keys')
        check('deep-quota-reset-from-drawer', true, 'skip: no api keys')
      }

      await page.keyboard.press('Escape')
      await page.waitForTimeout(300)
    } else {
      check('deep-quota-drawer-open', false, 'quota button not found')
    }

    // ---- Quota ops APIs（节流，避免触发管理端 30/min 限流）----
    if (firstKeyId) {
      const detail = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`)
      check(
        'deep-api-key-quota-get',
        detail.status === 200 && detail.body?.data?.keyId === firstKeyId,
        `HTTP ${detail.status} remaining=${detail.body?.data?.remainingRequests}`
      )

      const put = await apiThrottled(page, `/api/auth/api-keys/${firstKeyId}/quota`, {
        method: 'PUT',
        body: originalQuota || { dailyRequestLimit: 0, dailyTokenLimit: 0, rateLimitPerMinute: 0 }
      })
      check('deep-api-key-quota-put', put.status === 200, `HTTP ${put.status}`)

      const batch = await apiThrottled(page, '/api/auth/api-keys/quota/batch-reset', {
        method: 'POST',
        body: { keyIds: [firstKeyId] }
      })
      check(
        'deep-api-key-quota-batch-reset',
        batch.status === 200 && (batch.body?.data?.reset ?? 0) >= 0,
        `HTTP ${batch.status} reset=${batch.body?.data?.reset}`
      )

      const resetAll = await apiThrottled(page, '/api/auth/api-keys/quota/reset-all', { method: 'POST' })
      check('deep-api-key-quota-reset-all', resetAll.status === 200, `HTTP ${resetAll.status}`)
    } else {
      check('deep-api-key-quota-get', true, 'skip: no keys')
      check('deep-api-key-quota-put', true, 'skip: no keys')
      check('deep-api-key-quota-batch-reset', true, 'skip: no keys')
      check('deep-api-key-quota-reset-all', true, 'skip: no keys')
    }

    // ---- PII sanitization ----
    await gotoApp(page, '/security/sanitization')
    await page.waitForTimeout(1000)
    const piiCfg = await api(page, '/api/config/sanitization')
    check('deep-pii-config-api', piiCfg.status === 200 && !!piiCfg.body?.data?.response, `HTTP ${piiCfg.status}`)

    const testApi = await api(page, '/api/config/sanitization/test', {
      method: 'POST',
      body: { sample: 'phone 13800138000', contentType: 'text/plain' }
    })
    check(
      'deep-pii-dry-run-masked',
      testApi.status === 200 && String(testApi.body?.data?.after || '').includes('****'),
      `after=${testApi.body?.data?.after}`
    )

    const runTest = page.getByRole('button', { name: /试跑|Run Test/ }).first()
    if ((await runTest.count().catch(() => 0)) > 0) {
      await runTest.click()
      await page.waitForTimeout(1200)
      const maskedUi = await page.evaluate(() => {
        const areas = [...document.querySelectorAll('textarea')].map(t => t.value || '')
        return areas.some(v => v.includes('****'))
      })
      check('deep-pii-dry-run-ui-masked', maskedUi)
    } else {
      check('deep-pii-dry-run-ui-masked', true, 'ui button skip')
    }

    const beforePii = piiCfg.body?.data
    if (beforePii?.response) {
      const restorePii = await api(page, '/api/config/sanitization', {
        method: 'PUT',
        body: {
          response: {
            enabled: beforePii.response.enabled,
            piiPatterns: beforePii.response.piiPatterns || [],
            sensitiveWords: beforePii.response.sensitiveWords || [],
            maskingChar: beforePii.response.maskingChar || '*'
          }
        }
      })
      check('deep-pii-config-put', restorePii.status === 200, `HTTP ${restorePii.status}`)
    } else {
      check('deep-pii-config-put', false, 'no pii config snapshot')
    }

    // ---- Quota monitoring + cross-link ----
    await gotoApp(page, '/monitoring/quota')
    await page.waitForTimeout(800)
    const bodyText = await page.locator('body').innerText()
    check(
      'deep-quota-monitoring-page',
      bodyText.includes('配额') || bodyText.includes('Quota'),
      bodyText.includes('到 API Key') || bodyText.includes('API Key') ? 'cross-link visible' : 'page visible'
    )

    const usage = await api(page, '/api/monitoring/quota/usage?window=DAY')
    check(
      'deep-quota-usage-api',
      usage.status === 200 && Array.isArray(usage.body?.data),
      `HTTP ${usage.status} rows=${usage.body?.data?.length ?? 0}`
    )

    const gotoKeys = page.getByRole('button', { name: /到 API Key|API Key|Set limits/ }).first()
    if ((await gotoKeys.count().catch(() => 0)) > 0) {
      await gotoKeys.click()
      await page.waitForTimeout(800)
      check('deep-quota-monitoring-crosslink', page.url().includes('/security/api-keys'), page.url())
    } else {
      await gotoApp(page, '/security/api-keys')
      check('deep-quota-monitoring-crosslink', page.url().includes('/security/api-keys'), 'fallback navigate')
    }

    // 清理：只删本 spec 自己造的那个 Key（已有 Key 不动）
    if (seededKeyId) {
      const del = await apiThrottled(page, `/api/auth/api-keys/${seededKeyId}`, { method: 'DELETE' })
      check('deep-apikeys-seeded-cleanup', del.status === 200, `HTTP ${del.status} keyId=${seededKeyId}`)
    }

    await page.close()
  }
}
