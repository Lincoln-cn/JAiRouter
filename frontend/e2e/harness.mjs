/**
 * E2E harness：浏览器启动、登录、断言收集、页面工具
 */
import { chromium } from 'playwright-core'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export const BASE = process.env.E2E_BASE_URL || 'http://127.0.0.1:8080/admin'
export const USER = process.env.E2E_USER || 'admin'
export const PASS = process.env.E2E_PASS || 'ChangeMeOnFirstStartup123456'

const DEFAULT_EXE =
  'C:\\Users\\Administrator\\AppData\\Local\\ms-playwright\\chromium_headless_shell-1208\\chrome-headless-shell-win64\\chrome-headless-shell.exe'
const EXE =
  process.env.CHROMIUM_PATH ||
  [DEFAULT_EXE, path.resolve(__dirname, '../../.mimocode/pw/node_modules/.bin/chrome')].find(
    p => p && existsSync(p)
  ) ||
  undefined

export function createReporter(suiteId = 'e2e') {
  const results = []
  const check = (name, ok, detail = '') => {
    results.push({ suite: suiteId, name, ok: !!ok, detail: detail == null ? '' : String(detail) })
    console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail ?? ''}`)
  }
  return {
    check,
    results,
    summary() {
      const failed = results.filter(r => !r.ok)
      const passed = results.length - failed.length
      return { total: results.length, passed, failed, results }
    }
  }
}

export async function launchBrowser() {
  const launchOpts = { headless: true, args: ['--no-sandbox', '--disable-gpu'] }
  if (EXE) launchOpts.executablePath = EXE
  const browser = await chromium.launch(launchOpts)
  return browser
}

export async function newPage(browser) {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  page.setDefaultTimeout(20000)
  return page
}

export function appUrl(pathname = '/') {
  return `${BASE}${pathname.startsWith('/') ? pathname : `/${pathname}`}`
}

/** SPA 导航：domcontentloaded，避免 networkidle 被轮询拖死 */
export async function gotoApp(page, pathname, opts = {}) {
  const url = appUrl(pathname)
  await page.goto(url, { waitUntil: opts.waitUntil || 'domcontentloaded', timeout: opts.timeout || 30000 })
  if (opts.waitMs) await page.waitForTimeout(opts.waitMs)
  return url
}

export async function isLoggedIn(page) {
  return page.evaluate(() => !!localStorage.getItem('admin_token'))
}

export async function login(page, user = USER, pass = PASS) {
  await gotoApp(page, '/login')
  await page.waitForSelector('.login-form input')
  await page.locator('.login-form input').nth(0).fill(user)
  await page.locator('.login-form input').nth(1).fill(pass)
  await page.locator('button.login-button').first().click()
  await page.waitForFunction(() => !location.pathname.endsWith('/login'), { timeout: 20000 })
  return page.url()
}

export async function logoutViaUi(page) {
  const userMenu = page.locator('.user-info').first()
  if ((await userMenu.count()) === 0) return false
  await userMenu.click()
  await page.waitForTimeout(300)
  const logout = page.locator('.el-dropdown-menu__item').filter({ hasText: /登出|退出|Logout|Sign out/i }).first()
  if ((await logout.count()) === 0) return false
  await logout.click()
  await page.waitForFunction(() => location.pathname.endsWith('/login'), { timeout: 15000 }).catch(() => {})
  return true
}

async function apiOnce(page, apiPath, method, body) {
  return page.evaluate(
    async ({ path: p, method: m, body: b }) => {
      const token = localStorage.getItem('admin_token')
      const res = await fetch(p, {
        method: m || 'GET',
        headers: {
          Jairouter_Token: token || '',
          'Content-Type': 'application/json'
        },
        body: b ? JSON.stringify(b) : undefined
      })
      return { status: res.status, body: await res.json().catch(() => null) }
    },
    { path: apiPath, method, body }
  )
}

/**
 * 管理端 API；对 /api/auth/api-keys 的 429（AdminApiRateLimiter，30/min）自动退避重试
 */
export async function api(page, apiPath, init = {}) {
  const method = init.method
  const body = init.body
  const isRateLimitedPath = apiPath.includes('/auth/api-keys')
  let res = await apiOnce(page, apiPath, method, body)
  if (res.status === 429 && isRateLimitedPath) {
    for (const delayMs of init.retryDelays || [3000, 8000, 15000]) {
      await page.waitForTimeout(delayMs)
      res = await apiOnce(page, apiPath, method, body)
      if (res.status !== 429) break
    }
  }
  return res
}

/** 对 api-keys 管理接口做节奏控制，降低触发分钟限流概率 */
export async function apiThrottled(page, apiPath, init = {}) {
  await page.waitForTimeout(init.gapMs ?? 700)
  return api(page, apiPath, init)
}

export function currentPathname(page) {
  return new URL(page.url()).pathname.replace(/\/+$/, '') || '/'
}

/**
 * 通用页面健康断言：未跳登录、布局存在、主内容非空、未崩溃
 */
export async function assertAppPageHealthy(page, route, check, tag = route.name) {
  const pathname = currentPathname(page)
  const stillOn = pathname.endsWith(route.path) || pathname.endsWith(`${route.path}/`)
  check(`smoke:${tag}:route`, stillOn, `${pathname}`)

  const hasLayout = await page
    .locator('.layout-container, .layout-main, .layout-menu')
    .first()
    .isVisible()
    .catch(() => false)
  check(`smoke:${tag}:layout`, hasLayout, '')

  const pageState = await page.evaluate(() => {
    const main = document.querySelector('.layout-main') || document.querySelector('#app') || document.body
    const text = (main.innerText || '').trim()
    const title = document.title || ''
    const bodyText = (document.body.innerText || '')
    return {
      contentLen: text.length,
      title,
      hasLoginRedirectHint: location.pathname.endsWith('/login'),
      hasModuleError:
        bodyText.includes('Failed to fetch dynamically imported module') ||
        bodyText.includes('ChunkLoadError') ||
        bodyText.includes('Cannot read properties of undefined')
    }
  })
  check(`smoke:${tag}:content`, !pageState.hasLoginRedirectHint && pageState.contentLen >= 8, `len=${pageState.contentLen} title=${pageState.title}`)
  check(`smoke:${tag}:no-module-error`, !pageState.hasModuleError, pageState.title)

  if (route.expectAny?.length) {
    let found = false
    const matched = []
    for (const sel of route.expectAny) {
      const n = await page.locator(sel).count().catch(() => 0)
      if (n > 0) {
        found = true
        matched.push(sel)
        break
      }
    }
    check(`smoke:${tag}:expect`, found, matched[0] || `none of ${route.expectAny.join(',')}`)
  }
}

export async function waitTableSettled(page, timeoutMs = 15000) {
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

/** 生成唯一测试后缀，避免并发/重复跑撞名 */
export function uniq(prefix = 'e2e') {
  return `${prefix}-${Date.now().toString(36)}-${Math.floor(Math.random() * 1000)}`
}

/** 按文案点击页面主区域按钮（跳过禁用；对 SPA 重渲染做重试） */
export async function clickMainButton(page, textRe, opts = {}) {
  const scopeSel = opts.scopeSelector || '.layout-main, body'
  for (let attempt = 0; attempt < 3; attempt++) {
    const scope = opts.scope || page.locator(scopeSel)
    const btn = scope
      .locator('button:not(.is-disabled):not([disabled])')
      .filter({ hasText: textRe })
      .first()
    const count = await btn.count().catch(() => 0)
    if (!count) return false
    try {
      await btn.click({ timeout: opts.timeout || 5000 })
      return true
    } catch {
      await page.waitForTimeout(400)
    }
  }
  return false
}

/** 可见的 dialog / drawer（忽略 DOM 中残留但隐藏的节点） */
export function visibleOverlay(page) {
  return page.locator('.el-dialog:visible, .el-drawer:visible').first()
}

export async function countVisibleOverlays(page) {
  return page.evaluate(() => {
    return [...document.querySelectorAll('.el-dialog, .el-drawer')].filter(el => {
      const s = getComputedStyle(el)
      if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0') return false
      const r = el.getBoundingClientRect()
      return r.width > 4 && r.height > 4
    }).length
  })
}

export async function waitForOverlay(page, timeout = 8000) {
  await page.waitForFunction(
    () => {
      return [...document.querySelectorAll('.el-dialog, .el-drawer')].some(el => {
        const s = getComputedStyle(el)
        if (s.display === 'none' || s.visibility === 'hidden') return false
        const r = el.getBoundingClientRect()
        return r.width > 4 && r.height > 4
      })
    },
    { timeout }
  )
  return visibleOverlay(page)
}

export async function overlayFormStats(page) {
  return page.evaluate(() => {
    const root = [...document.querySelectorAll('.el-dialog, .el-drawer')].find(el => {
      const s = getComputedStyle(el)
      if (s.display === 'none' || s.visibility === 'hidden') return false
      const r = el.getBoundingClientRect()
      return r.width > 4 && r.height > 4
    })
    const target = root || document
    return {
      inputs: target.querySelectorAll('.el-input__inner, textarea.el-textarea__inner, .el-textarea__inner').length,
      selects: target.querySelectorAll('.el-select').length,
      buttons: target.querySelectorAll('.el-button').length,
      forms: target.querySelectorAll('.el-form').length
    }
  })
}

/** 在当前可见弹窗内填第一个可见 input */
export async function fillFirstVisibleInput(page, value, index = 0) {
  const inputs = page.locator(
    '.el-dialog:visible .el-input__inner:visible, .el-drawer:visible .el-input__inner:visible, .el-dialog:visible textarea:visible, .el-drawer:visible textarea:visible'
  )
  const n = await inputs.count()
  if (n <= index) return false
  await inputs.nth(index).fill(value)
  return true
}

/** 点击可见弹窗 footer 中的按钮 */
export async function clickOverlayButton(page, textRe) {
  const btn = page
    .locator(
      '.el-dialog:visible .el-dialog__footer button, .el-drawer:visible button, .el-dialog:visible .el-dialog__body button, .el-dialog:visible button'
    )
    .filter({ hasText: textRe })
    .first()
  if ((await btn.count().catch(() => 0)) === 0) return false
  await btn.click({ timeout: 8000 })
  return true
}

export async function closeOverlay(page) {
  // 1) 取消/关闭类按钮
  const cancel = page
    .locator('.el-dialog:visible button, .el-drawer:visible button')
    .filter({ hasText: /取消|关闭|Cancel|Close|我已保存/ })
    .first()
  if ((await cancel.count().catch(() => 0)) > 0) {
    await cancel.click().catch(() => {})
    await page.waitForTimeout(350)
  }

  // 2) header 关闭图标
  if ((await countVisibleOverlays(page)) > 0) {
    const closeBtn = page
      .locator(
        '.el-dialog:visible .el-dialog__headerbtn, .el-drawer:visible .el-drawer__close-btn, .el-drawer:visible .el-drawer__close-btn--custom'
      )
      .first()
    if ((await closeBtn.count().catch(() => 0)) > 0) {
      await closeBtn.click().catch(() => {})
      await page.waitForTimeout(350)
    } else {
      await page.keyboard.press('Escape').catch(() => {})
      await page.waitForTimeout(350)
    }
  }

  // 3) 兜底：强制隐藏 overlay（E2E 专用，不改业务状态）
  if ((await countVisibleOverlays(page)) > 0) {
    await page.evaluate(() => {
      document.querySelectorAll('.el-overlay, .el-dialog, .el-drawer').forEach(el => {
        const s = getComputedStyle(el)
        const r = el.getBoundingClientRect()
        if (s.display !== 'none' && r.width > 0 && r.height > 0) {
          el.style.setProperty('display', 'none', 'important')
        }
      })
    })
    await page.waitForTimeout(200)
  }

  return (await countVisibleOverlays(page)) === 0
}

/** 处理 Element Plus MessageBox 确认框 */
export async function confirmMessageBox(page, textRe = /确定|删除|确认|移除|OK|Confirm/i) {
  const box = page.locator('.el-message-box, .el-overlay-message-box')
  const visible = await box.first().isVisible().catch(() => false)
  if (!visible) return false
  const btn = box.locator('button').filter({ hasText: textRe }).first()
  if ((await btn.count().catch(() => 0)) === 0) {
    // 兜底：最后一个主按钮
    await box.locator('button').last().click().catch(() => {})
  } else {
    await btn.click()
  }
  await page.waitForTimeout(400)
  return true
}

/** Element Plus 下拉：点击 select 并选择含指定文本的选项 */
export async function selectOption(page, selectLocator, optionRe) {
  await selectLocator.first().click()
  await page.waitForTimeout(300)
  const opt = page.locator('.el-select-dropdown__item:visible').filter({ hasText: optionRe }).first()
  if ((await opt.count().catch(() => 0)) === 0) {
    await page.keyboard.press('Escape')
    return false
  }
  await opt.click()
  await page.waitForTimeout(200)
  return true
}

/** 在表格行中点击与名称匹配的按钮（删除/编辑等） */
export async function clickRowAction(page, rowTextRe, actionRe) {
  const row = page.locator('.el-table__body tr').filter({ hasText: rowTextRe }).first()
  if ((await row.count().catch(() => 0)) === 0) return false
  const btn = row.locator('button').filter({ hasText: actionRe }).first()
  if ((await btn.count().catch(() => 0)) === 0) return false
  await btn.click()
  return true
}

/**
 * AdminApiRateLimiter 按客户端 IP 计数（X-Forwarded-For 优先）。
 * E2E 注入独立 IP，避免与人工操作/历史跑次共享 30/min、100/hour 桶。
 */
export function e2eClientIp(tag = 'suite') {
  if (process.env.E2E_CLIENT_IP) return process.env.E2E_CLIENT_IP
  const n = Math.floor(Math.random() * 200) + 10
  return `10.77.${n}.${Math.floor(Math.random() * 200) + 10}`
}

export async function attachAdminApiRateLimitIsolation(page, ip = e2eClientIp()) {
  await page.route('**/api/auth/api-keys**', route => {
    const headers = { ...route.request().headers(), 'x-forwarded-for': ip }
    return route.continue({ headers })
  })
  return ip
}

/** 创建已登录会话（每个 spec 共享 browser，page 可复用） */
export async function createAuthedContext(browser) {
  const page = await newPage(browser)
  const ip = await attachAdminApiRateLimitIsolation(page)
  page.__e2eClientIp = ip
  await login(page)
  return page
}
