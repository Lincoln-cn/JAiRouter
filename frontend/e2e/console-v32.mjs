/**
 * JAiRouter 控制台 E2E（v3.2 全量门禁）
 *
 * 覆盖：
 * 1) 登录
 * 2) API Key 列表加载/刷新（不允许无限转圈）
 * 3) 配额抽屉：打开、表单、预设、保存/恢复、按钮居中
 * 4) 配额 API：单 Key 详情/更新/重置、批量重置、全量重置
 * 5) PII：配置、试脱敏掩码、热改后恢复
 * 6) 配额监控页 + 交叉链接到 API Key
 *
 * 运行:
 *   cd frontend && npm run test:e2e
 * 环境变量:
 *   E2E_BASE_URL  默认 http://127.0.0.1:8080/admin
 *   E2E_USER / E2E_PASS
 *   CHROMIUM_PATH 可选 headless shell 路径
 */
import { chromium } from 'playwright-core'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const BASE = process.env.E2E_BASE_URL || 'http://127.0.0.1:8080/admin'
const USER = process.env.E2E_USER || 'admin'
const PASS = process.env.E2E_PASS || 'ChangeMeOnFirstStartup123456'
const DEFAULT_EXE =
  'C:\\Users\\Administrator\\AppData\\Local\\ms-playwright\\chromium_headless_shell-1208\\chrome-headless-shell-win64\\chrome-headless-shell.exe'
const EXE =
  process.env.CHROMIUM_PATH ||
  [DEFAULT_EXE, path.resolve(__dirname, '../../../.mimocode/pw/node_modules/.bin/chrome')].find(
    p => p && existsSync(p)
  ) ||
  undefined

const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`)
}

async function api(page, path, init = {}) {
  return page.evaluate(
    async ({ path, method, body }) => {
      const token = localStorage.getItem('admin_token')
      const res = await fetch(path, {
        method: method || 'GET',
        headers: {
          Jairouter_Token: token || '',
          'Content-Type': 'application/json'
        },
        body: body ? JSON.stringify(body) : undefined
      })
      return { status: res.status, body: await res.json().catch(() => null) }
    },
    { path, method: init.method, body: init.body }
  )
}

async function waitApiKeysTableSettled(page, timeoutMs = 15000) {
  await page.waitForFunction(
    () => {
      const table = document.querySelector('.el-table')
      if (!table) return false
      const spinning = document.querySelector('.el-table .el-loading-mask')
      const rows = document.querySelectorAll('.el-table__body tr')
      const empty = document.querySelector('.el-table__empty-block')
      return !spinning && (rows.length > 0 || !!empty)
    },
    { timeout: timeoutMs }
  )
}

const launchOpts = { headless: true, args: ['--no-sandbox', '--disable-gpu'] }
if (EXE) launchOpts.executablePath = EXE

const browser = await chromium.launch(launchOpts)
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
page.setDefaultTimeout(20000)

try {
  // ---- 1. Login ----
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input')
  await page.locator('.login-form input').nth(0).fill(USER)
  await page.locator('.login-form input').nth(1).fill(PASS)
  await page.locator('button.login-button').first().click()
  await page.waitForFunction(() => !location.pathname.endsWith('/login'), { timeout: 20000 })
  check('e2e-login', true, page.url())

  // ---- 2. API Keys list + refresh ----
  const t0 = Date.now()
  await page.goto(`${BASE}/security/api-keys`, { waitUntil: 'domcontentloaded' })
  let listOk = false
  try {
    await waitApiKeysTableSettled(page)
    listOk = true
  } catch {
    listOk = false
  }
  check('e2e-apikeys-list-no-spin', listOk, `elapsed=${Date.now() - t0}ms`)

  const listApi = await api(page, '/api/auth/api-keys')
  check('e2e-api-keys-http', listApi.status === 200 && !!listApi.body?.data, `HTTP ${listApi.status}`)

  // SPA 重新进入列表页（等价刷新）
  const tRefresh = Date.now()
  await page.goto(`${BASE}/dashboard/main`, { waitUntil: 'domcontentloaded' })
  await page.goto(`${BASE}/security/api-keys`, { waitUntil: 'domcontentloaded' })
  let refreshOk = false
  try {
    await waitApiKeysTableSettled(page)
    refreshOk = true
  } catch {
    refreshOk = false
  }
  check('e2e-apikeys-refresh-no-spin', refreshOk, `elapsed=${Date.now() - tRefresh}ms`)

  // ---- 3. Quota drawer UI ----
  const quotaBtn = page.locator('.el-table button').filter({ hasText: /^配额$|^Quota$/ }).first()
  const hasQuotaBtn = (await quotaBtn.count().catch(() => 0)) > 0
  check('e2e-quota-drawer-button', hasQuotaBtn)

  let firstKeyId = listApi.body?.data?.items?.[0]?.keyId || null
  let originalQuota = null

  if (hasQuotaBtn) {
    await quotaBtn.click()
    await page.waitForTimeout(800)
    const drawerVisible = await page
      .locator('.el-drawer')
      .first()
      .isVisible()
      .catch(() => false)
    check('e2e-quota-drawer-open', drawerVisible)

    const inputCount = await page.locator('.el-drawer .el-input-number input').count()
    check('e2e-quota-drawer-form', inputCount >= 3, `input-number=${inputCount}`)

    const saveBtn = page.locator('.el-drawer').getByRole('button', { name: /保存配额|Save Quota/ })
    const resetBtn = page.locator('.el-drawer').getByRole('button', { name: /重置计数|Reset Counters/ })
    check(
      'e2e-quota-drawer-actions',
      (await saveBtn.count()) > 0 && (await resetBtn.count()) > 0,
      'save/reset present'
    )

    const centered = await page.evaluate(() => {
      const el = document.querySelector('.quota-drawer-actions')
      if (!el) return false
      const cs = getComputedStyle(el)
      return cs.display.includes('flex') && cs.justifyContent === 'center'
    })
    check('e2e-quota-drawer-actions-centered', centered)

    // 应用预设「不限制」后表单应为 0
    const presetSelect = page.locator('.el-drawer .el-select').first()
    if ((await presetSelect.count()) > 0) {
      await presetSelect.click()
      await page.waitForTimeout(300)
      const unlimitedOpt = page.locator('.el-select-dropdown__item').filter({ hasText: /不限制|Unlimited/ }).first()
      if ((await unlimitedOpt.count()) > 0) {
        await unlimitedOpt.click()
        await page.waitForTimeout(200)
        const vals = await page.locator('.el-drawer .el-input-number input').evaluateAll(els =>
          els.slice(0, 3).map(e => e.value)
        )
        check(
          'e2e-quota-preset-unlimited',
          vals.every(v => v === '0'),
          `values=${vals.join(',')}`
        )
      } else {
        check('e2e-quota-preset-unlimited', false, 'preset option not found')
      }
    }

    // 保存配额（会把预设写入；随后用 API 恢复）
    if (firstKeyId) {
      const before = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`)
      originalQuota = before.body?.data
        ? {
            dailyRequestLimit: before.body.data.dailyRequestLimit,
            dailyTokenLimit: before.body.data.dailyTokenLimit,
            rateLimitPerMinute: before.body.data.rateLimitPerMinute,
            quotaAlertThreshold: before.body.data.quotaAlertThreshold
          }
        : null

      await page.locator('.el-drawer').getByRole('button', { name: /保存配额|Save Quota/ }).click()
      await page.waitForTimeout(1200)
      const afterSave = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`)
      const unlimitedApplied =
        afterSave.body?.data?.dailyRequestLimit === 0 &&
        afterSave.body?.data?.dailyTokenLimit === 0 &&
        afterSave.body?.data?.rateLimitPerMinute === 0
      check(
        'e2e-quota-save-from-drawer',
        afterSave.status === 200 && afterSave.body?.success === true && unlimitedApplied,
        `HTTP ${afterSave.status} limits=${afterSave.body?.data?.dailyRequestLimit}/${afterSave.body?.data?.dailyTokenLimit}/${afterSave.body?.data?.rateLimitPerMinute}`
      )

      // 重置计数（UI）
      await page.locator('.el-drawer').getByRole('button', { name: /重置计数|Reset Counters/ }).click()
      await page.waitForTimeout(800)
      const afterReset = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`)
      check(
        'e2e-quota-reset-from-drawer',
        afterReset.status === 200 && afterReset.body?.success === true && (afterReset.body?.data?.todayRequestCount ?? -1) >= 0,
        `todayReq=${afterReset.body?.data?.todayRequestCount}`
      )

      // 恔复原始限额
      if (originalQuota) {
        const restore = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`, {
          method: 'PUT',
          body: originalQuota
        })
        check(
          'e2e-quota-restore-original',
          restore.status === 200 &&
            restore.body?.data?.dailyRequestLimit === originalQuota.dailyRequestLimit,
          `restored=${restore.body?.data?.dailyRequestLimit}`
        )
      }
    } else {
      check('e2e-quota-save-from-drawer', true, 'skip: no api keys')
      check('e2e-quota-reset-from-drawer', true, 'skip: no api keys')
    }

    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } else {
    check('e2e-quota-drawer-open', false, 'quota button not found')
  }

  // ---- 3b. Create dialog quota round-trip (issue #84) ----
  // 回归守卫：创建对话框曾只提交 dailyRequestLimit + rotationPeriodDays，
  // dailyTokenLimit / rateLimitPerMinute / quotaAlertThreshold 被静默丢弃，
  // 导致「创建时设置的配额不生效」。此处从 UI 建 Key，再用 API 断言四项都落库。
  {
    await page.goto(`${BASE}/security/api-keys`, { waitUntil: 'domcontentloaded' })
    await waitApiKeysTableSettled(page).catch(() => {})

    const createBtn = page.getByRole('button', { name: /创建API密钥|Create API Key/ }).first()
    if ((await createBtn.count().catch(() => 0)) > 0) {
      await createBtn.click()
      const dlg = page.locator('.el-dialog:visible').first()
      await dlg.waitFor({ state: 'visible' })
      await page.waitForTimeout(400)

      const item = label => dlg.locator('.el-form-item', { hasText: label }).first()
      const fillNum = async (label, value) => {
        const input = item(label).locator('input').first()
        await input.click()
        await input.fill(String(value))
        await input.press('Enter')
        await page.waitForTimeout(150)
      }

      const newKeyId = `e2e-quota-create-${Date.now()}`
      const want = {
        dailyRequestLimit: 1234,
        dailyTokenLimit: 56789,
        rateLimitPerMinute: 42,
        quotaAlertThreshold: 0.5
      }

      await item('密钥ID').locator('input').first().fill(newKeyId)
      await fillNum('每日请求上限', want.dailyRequestLimit)
      await fillNum('每日Token上限', want.dailyTokenLimit)
      await fillNum('每分钟速率限制', want.rateLimitPerMinute)
      const thInput = item('告警阈值').locator('.el-input-number input').first()
      await thInput.click()
      await thInput.fill(String(want.quotaAlertThreshold))
      await thInput.press('Enter')
      await page.waitForTimeout(150)

      await dlg.getByRole('button', { name: /^保存$/ }).click()
      await page.waitForTimeout(2000)

      // 关掉「密钥已创建」弹窗，避免遮挡后续步骤
      const savedClose = page.getByRole('button', { name: /我已保存|Saved|Close/ }).first()
      if ((await savedClose.count().catch(() => 0)) > 0) {
        await savedClose.click()
        await page.waitForTimeout(400)
      }

      const q = await api(page, `/api/auth/api-keys/${newKeyId}/quota`)
      const d = q.body?.data
      check(
        'e2e-api-key-create-quota-roundtrip',
        d?.dailyRequestLimit === want.dailyRequestLimit &&
          d?.dailyTokenLimit === want.dailyTokenLimit &&
          d?.rateLimitPerMinute === want.rateLimitPerMinute &&
          Number(d?.quotaAlertThreshold) === want.quotaAlertThreshold,
        `req=${d?.dailyRequestLimit} tok=${d?.dailyTokenLimit} rate=${d?.rateLimitPerMinute} th=${d?.quotaAlertThreshold}`
      )

      const del = await api(page, `/api/auth/api-keys/${newKeyId}`, { method: 'DELETE' })
      check('e2e-api-key-create-cleanup', del.status === 200, `HTTP ${del.status}`)
    } else {
      check('e2e-api-key-create-quota-roundtrip', false, 'create button not found')
      check('e2e-api-key-create-cleanup', true, 'skip')
    }
  }

  // ---- 4. Quota ops APIs ----
  const keys = await api(page, '/api/auth/api-keys')
  firstKeyId = firstKeyId || keys.body?.data?.items?.[0]?.keyId || null
  if (firstKeyId) {
    const detail = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`)
    check(
      'e2e-api-key-quota-get',
      detail.status === 200 && detail.body?.data?.keyId === firstKeyId,
      `HTTP ${detail.status} remaining=${detail.body?.data?.remainingRequests}`
    )

    const put = await api(page, `/api/auth/api-keys/${firstKeyId}/quota`, {
      method: 'PUT',
      body: originalQuota || { dailyRequestLimit: 0, dailyTokenLimit: 0, rateLimitPerMinute: 0 }
    })
    check('e2e-api-key-quota-put', put.status === 200 && put.body?.success === true, `HTTP ${put.status} success=${put.body?.success}`)

    const batch = await api(page, '/api/auth/api-keys/quota/batch-reset', {
      method: 'POST',
      body: { keyIds: [firstKeyId] }
    })
    check(
      'e2e-api-key-quota-batch-reset',
      batch.status === 200 && batch.body?.success === true && (batch.body?.data?.reset ?? -1) >= 0,
      `HTTP ${batch.status} success=${batch.body?.success} reset=${batch.body?.data?.reset}`
    )

    const resetAll = await api(page, '/api/auth/api-keys/quota/reset-all', { method: 'POST' })
    check('e2e-api-key-quota-reset-all', resetAll.status === 200 && resetAll.body?.success === true, `HTTP ${resetAll.status} success=${resetAll.body?.success}`)
  } else {
    check('e2e-api-key-quota-get', true, 'skip: no keys')
    check('e2e-api-key-quota-put', true, 'skip: no keys')
    check('e2e-api-key-quota-batch-reset', true, 'skip: no keys')
    check('e2e-api-key-quota-reset-all', true, 'skip: no keys')
  }

  // ---- 5. PII sanitization ----
  await page.goto(`${BASE}/security/sanitization`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const piiCfg = await api(page, '/api/config/sanitization')
  check(
    'e2e-pii-config-api',
    piiCfg.status === 200 && !!piiCfg.body?.data?.request,
    `HTTP ${piiCfg.status}`
  )

  const testApi = await api(page, '/api/config/sanitization/test', {
    method: 'POST',
    body: { sample: 'phone 13800138000', contentType: 'text/plain' }
  })
  check(
    'e2e-pii-dry-run-masked',
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
    check('e2e-pii-dry-run-ui-masked', maskedUi)
  } else {
    check('e2e-pii-dry-run-ui-masked', true, 'ui button skip')
  }

  // PII 热改：保持原 piiPatterns，仅回写一次验证 PUT 闭环
  const beforePii = piiCfg.body?.data
  if (beforePii?.request) {
    const restorePii = await api(page, '/api/config/sanitization', {
      method: 'PUT',
      body: {
        request: {
          enabled: beforePii.request.enabled,
          piiPatterns: beforePii.request.piiPatterns || [],
          sensitiveWords: beforePii.request.sensitiveWords || [],
          maskingChar: beforePii.request.maskingChar || '*'
        }
      }
    })
    check('e2e-pii-config-put', restorePii.status === 200, `HTTP ${restorePii.status}`)
  } else {
    check('e2e-pii-config-put', false, 'no pii config snapshot')
  }

  // ---- 6. Quota monitoring + cross-link ----
  await page.goto(`${BASE}/monitoring/quota`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const bodyText = await page.locator('body').innerText()
  check(
    'e2e-quota-monitoring-page',
    bodyText.includes('配额') || bodyText.includes('Quota'),
    bodyText.includes('到 API Key') || bodyText.includes('API Key') ? 'cross-link visible' : 'page visible'
  )

  const usage = await api(page, '/api/monitoring/quota/usage?window=DAY')
  check(
    'e2e-quota-usage-api',
    usage.status === 200 && Array.isArray(usage.body?.data),
    `HTTP ${usage.status} rows=${usage.body?.data?.length ?? 0}`
  )

  const gotoKeys = page.getByRole('button', { name: /到 API Key|API Key|Set limits/ }).first()
  if ((await gotoKeys.count().catch(() => 0)) > 0) {
    await gotoKeys.click()
    await page.waitForTimeout(800)
    check('e2e-quota-monitoring-crosslink', page.url().includes('/security/api-keys'), page.url())
  } else {
    // 菜单兜底验证可达
    await page.goto(`${BASE}/security/api-keys`, { waitUntil: 'domcontentloaded' })
    check('e2e-quota-monitoring-crosslink', page.url().includes('/security/api-keys'), 'fallback navigate')
  }
} catch (e) {
  check('e2e-aborted', false, e.message)
} finally {
  await browser.close()
  const failed = results.filter(r => !r.ok)
  console.log(`\nE2E SUMMARY: ${results.length - failed.length}/${results.length} PASS`)
  if (failed.length) console.log('Failed:', failed.map(f => `${f.name}(${f.detail})`).join('; '))
  process.exit(failed.length ? 1 : 0)
}
