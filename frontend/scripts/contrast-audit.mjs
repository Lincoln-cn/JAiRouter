#!/usr/bin/env node
/**
 * JAiRouter 暗色 DOM 对比度审计脚本（前端对比度门禁 / contrast gate）
 *
 * 作用：以真实浏览器登录管理台 → 切换到指定主题（默认暗色）→ 逐个访问路由清单页面
 *       → 枚举页面可见文本节点，沿祖先解析有效前景色/背景色（含 alpha 合成），
 *       按 WCAG 相对亮度计算对比度 → 输出 ratio < 阈值（默认 4.5）的内容文字违规。
 *
 * 依赖：全局安装 @playwright/test（本脚本用 createRequire 从全局解析，无需在项目里装）
 *   npm i -g @playwright/test
 *   npx playwright install chromium
 *
 * 用法（Windows PowerShell）：
 *   $env:NODE_PATH = npm root -g
 *   node scripts/contrast-audit.mjs                          # 默认 http://127.0.0.1:3000，暗色，审计内置路由清单
 *   node scripts/contrast-audit.mjs http://localhost:3000    # 自定义 baseUrl
 *   node scripts/contrast-audit.mjs --theme=light --only=config/services,config/instances   # 亮色抽查
 *   node scripts/contrast-audit.mjs --routes=foo/bar --json=contrast-report.json            # 追加路由 + 输出 JSON
 *   $env:ADMIN_PASSWORD = 'x'; node scripts/contrast-audit.mjs                              # 密码覆盖
 *
 * 退出码：0 = 通过（内容文字全部 ≥ 阈值）；1 = 存在内容文字违规；2 = 运行/登录/环境失败。
 * 例外处理：代码块语法色（pre/code/.hljs，归 T41）、canvas 绘制文字不计入违规；
 *           元素 disabled 态（WCAG 1.4.3 豁免 inactive 控件）与自着色控件
 *           （EP 主色按钮/步骤圈/彩色 tag 上的白字，非暗色灰字问题）会单列 exceptions 供人工复核。
 */

import { createRequire } from 'node:module'
import { writeFileSync } from 'node:fs'

const require = createRequire(import.meta.url)
const { chromium } = require('@playwright/test')

/* ========== 配置 ========== */

// 内置主要页面清单（相对于 /admin 前缀；对应 frontend/src/router/index.ts 全部主页面）
const DEFAULT_ROUTES = [
  'dashboard/main',
  'config/services',
  'config/instances',
  'config/versions',
  'config/state-persistence',
  'config/adapters',
  'config/rules',
  'config/pools',
  'config/cache',
  'load-balancers/monitoring',
  'load-balancers/strategy-config',
  'circuit-breakers/monitoring',
  'circuit-breakers/history',
  'circuit-breakers/global-config',
  'rate-limiters/monitoring',
  'call-history/dashboard',
  'call-history/list',
  'call-history/slow-calls',
  'call-history/token-usage',
  'exceptions/list',
  'exceptions/statistics',
  'tracing/dashboard',
  'tracing/search',
  'tracing/management',
  'security/api-keys',
  'security/jwt-tokens',
  'security/blacklist',
  'security/audit-logs',
  'system/accounts',
  'system/permissions',
  'monitoring/slow-queries',
  'playground/chat',
  'playground/embedding',
  'playground/rerank',
  'playground/audio',
  'playground/image'
]

const DEFAULT_BASE = 'http://127.0.0.1:3000'
const APP_PREFIX = '/admin' // vite base（createWebHistory(import.meta.env.BASE_URL)，dev/prod 均为 /admin/）
const DEFAULT_USERNAME = 'admin'
const DEFAULT_PASSWORD = 'ChangeMeOnFirstStartup123456'
const DEFAULT_THRESHOLD = 4.5

/* ========== 命令行参数 ========== */
function parseArgs(argv) {
  const args = { baseUrl: null, theme: 'dark', threshold: DEFAULT_THRESHOLD, only: null, routes: [], json: null, headless: true }
  const positionals = []
  for (const a of argv) {
    if (a.startsWith('--')) {
      const eq = a.indexOf('=')
      const key = eq >= 0 ? a.slice(2, eq) : a.slice(2)
      const val = eq >= 0 ? a.slice(eq + 1) : 'true'
      if (key === 'theme') args.theme = val
      else if (key === 'threshold') args.threshold = parseFloat(val) || DEFAULT_THRESHOLD
      else if (key === 'only') args.only = val.split(',').map(s => s.trim()).filter(Boolean)
      else if (key === 'routes') args.routes = val.split(',').map(s => s.trim()).filter(Boolean)
      else if (key === 'json') args.json = val
      else if (key === 'headed') args.headless = val !== 'true'
    } else {
      positionals.push(a)
    }
  }
  if (positionals.length) args.baseUrl = positionals[0]
  args.baseUrl = args.baseUrl || process.env.JA_BASE_URL || DEFAULT_BASE
  args.theme = args.theme === 'light' ? 'light' : 'dark'
  return args
}

/* ========== 页面扫描函数（在浏览器内执行；自包含，无外部引用） ========== */
function scanVisibleText(opts) {
  const THRESHOLD = opts.threshold || 4.5
  const isDark = document.documentElement.classList.contains('dark')

  // ---- 颜色解析（兼容 hex / rgb(a) / hsl(a)；getComputedStyle 输出 rgb/rgba） ----
  function parseColor(str) {
    if (!str) return null
    str = String(str).trim().toLowerCase()
    if (!str || str === 'none' || str === 'transparent') return null
    let m
    if ((m = str.match(/^#([0-9a-f]{3,8})$/))) {
      let h = m[1]
      if (h.length === 3 || h.length === 4) h = h.split('').map(c => c + c).join('')
      const a = h.length === 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1
      return { r: parseInt(h.slice(0, 2), 16), g: parseInt(h.slice(2, 4), 16), b: parseInt(h.slice(4, 6), 16), a }
    }
    if ((m = str.match(/^rgba?\(([^)]+)\)$/))) {
      const parts = m[1].split(/[,\s/]+/).filter(Boolean)
      const nums = parts.slice(0, 3).map(v => (v.endsWith('%') ? (parseFloat(v) / 100) * 255 : parseFloat(v)))
      let alpha = 1
      if (parts[3] !== undefined) alpha = parts[3].endsWith('%') ? parseFloat(parts[3]) / 100 : parseFloat(parts[3])
      if (nums.some(n => Number.isNaN(n))) return null
      return { r: nums[0], g: nums[1], b: nums[2], a: alpha }
    }
    if ((m = str.match(/^hsla?\(([^)]+)\)$/))) {
      const parts = m[1].split(/[,\s/]+/).filter(Boolean)
      const h = parseFloat(parts[0]) / 360
      const s = parseFloat(parts[1].replace('%', '')) / 100
      const l = parseFloat(parts[2].replace('%', '')) / 100
      let alpha = 1
      if (parts[3] !== undefined) alpha = parts[3].endsWith('%') ? parseFloat(parts[3]) / 100 : parseFloat(parts[3])
      const hue2rgb = (p, q, t) => {
        if (t < 0) t += 1
        if (t > 1) t -= 1
        if (t < 1 / 6) return p + (q - p) * 6 * t
        if (t < 1 / 2) return q
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6
        return p
      }
      const q = l < 0.5 ? l * (1 + s) : l + s - l * s
      const p2 = 2 * l - q
      return {
        r: Math.round(hue2rgb(p2, q, h + 1 / 3) * 255),
        g: Math.round(hue2rgb(p2, q, h) * 255),
        b: Math.round(hue2rgb(p2, q, h - 1 / 3) * 255),
        a: alpha
      }
    }
    return null
  }

  // 近似取 backgroundImage 第一层线性渐变的平均色（模拟 gradient 上文字的背景）
  function gradientAvg(bgImage) {
    if (!bgImage || bgImage === 'none' || !bgImage.includes('gradient')) return null
    const colors = []
    const re = /#(?:[0-9a-f]{3,8})\b|rgba?\([^)]*\)|hsla?\([^)]*\)/gi
    let mm
    while ((mm = re.exec(bgImage)) && colors.length < 2) {
      const c = parseColor(mm[0])
      if (c) colors.push(c)
    }
    if (!colors.length) return null
    let r = 0, g = 0, b = 0
    for (const c of colors) { r += c.r; g += c.g; b += c.b }
    const n = colors.length
    return { r: r / n, g: g / n, b: b / n, a: 1 }
  }

  function over(fg, bg) {
    const a = fg.a
    return { r: fg.r * a + bg.r * (1 - a), g: fg.g * a + bg.g * (1 - a), b: fg.b * a + bg.b * (1 - a), a: 1 }
  }

  function lin(ch) {
    const c = ch / 255
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  function luminance(c) {
    return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b)
  }
  function contrast(fg, bg) {
    const l1 = luminance(fg)
    const l2 = luminance(bg)
    const hi = Math.max(l1, l2)
    const lo = Math.min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)
  }
  function toHex(c) {
    const h = v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')
    return '#' + h(c.r) + h(c.g) + h(c.b)
  }

  // 求文本实际背景色：沿祖先链收集背景层，从远到近合成（透明层继续向上取）
  function effectiveBg(el) {
    const layers = []
    let node = el
    while (node && node !== document.documentElement) {
      const cs = getComputedStyle(node)
      const c = parseColor(cs.backgroundColor)
      if (c) layers.push(c)
      const g = gradientAvg(cs.backgroundImage)
      if (g) layers.push(g)
      if ((c && c.a >= 1) || g) break
      node = node.parentElement
    }
    if (!layers.length) {
      const c = parseColor(getComputedStyle(document.documentElement).backgroundColor)
      if (c) layers.push(c)
      const b = parseColor(getComputedStyle(document.body).backgroundColor)
      if (b) layers.push(b)
    }
    if (!layers.length) {
      const raw = getComputedStyle(document.documentElement).getPropertyValue('--ja-bg-page').trim()
      const c = parseColor(raw)
      if (c) return c
      return isDark ? { r: 20, g: 20, b: 20, a: 1 } : { r: 255, g: 255, b: 255, a: 1 }
    }
    // layers 近→远；从最远开始逐层叠近
    let bg = { ...layers[layers.length - 1], a: 1 }
    for (let i = layers.length - 2; i >= 0; i--) {
      const l = layers[i]
      bg = l.a >= 1 ? { r: l.r, g: l.g, b: l.b, a: 1 } : over(l, bg)
    }
    if (bg.a < 1) bg = over(bg, isDark ? { r: 20, g: 20, b: 20, a: 1 } : { r: 255, g: 255, b: 255, a: 1 })
    return bg
  }

  const EXCLUDE_SEL =
    'script,style,noscript,template,canvas,svg,math,textarea,input,select,option,iframe,embed,object,code,pre,kbd,samp,.hljs,.el-loading-mask,.el-loading-spinner'
  const DISABLED_SEL = '.is-disabled,[disabled],[aria-disabled="true"],fieldset[disabled]'
  function hasAncestor(el, sel) {
    let n = el
    while (n && n !== document.body) {
      if (n.matches && n.matches(sel)) return true
      n = n.parentElement
    }
    return false
  }

  // 判定文本是否落在「自着色控件」（按钮/步骤圈/彩色 tag/头像等）上：
  // 元素自身或 3 层祖先内有不透明且饱和度明显的背景 → 属控件着色而非暗色灰字，按例外处理。
  function isOnColoredChip(el) {
    let n = el
    let depth = 0
    while (n && n !== document.body && depth <= 3) {
      const cs = getComputedStyle(n)
      const c = parseColor(cs.backgroundColor)
      if (c && c.a >= 1) {
        const diff = Math.max(c.r, c.g, c.b) - Math.min(c.r, c.g, c.b)
        if (diff >= 48) return true
      }
      const g = gradientAvg(cs.backgroundImage)
      if (g) {
        const diff = Math.max(g.r, g.g, g.b) - Math.min(g.r, g.g, g.b)
        if (diff >= 48) return true
      }
      n = n.parentElement
      depth++
    }
    return false
  }

  const findings = []
  const seen = new Set()
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
    acceptNode(n) {
      const p = n.parentElement
      if (!p) return NodeFilter.FILTER_REJECT
      if (p.closest(EXCLUDE_SEL)) return NodeFilter.FILTER_REJECT
      const t = (n.textContent || '').trim()
      if (!t || t.length > 200) return NodeFilter.FILTER_REJECT
      if (!/[A-Za-z0-9\u4e00-\u9fa5]/.test(t)) return NodeFilter.FILTER_REJECT
      return NodeFilter.FILTER_ACCEPT
    }
  })

  while (walker.nextNode()) {
    const node = walker.currentNode
    const el = node.parentElement
    if (hasAncestor(el, '.el-loading-mask, .el-loading-spinner')) continue
    // 可见性：元素自身或任一祖先隐藏 / 透明 / 0 字号
    let visible = true
    let n = el
    while (n && n !== document.body) {
      const s = getComputedStyle(n)
      if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0' || s.fontSize === '0px') { visible = false; break }
      n = n.parentElement
    }
    if (!visible) continue
    if (el.closest('[aria-hidden="true"]')) continue
    const rect = el.getBoundingClientRect()
    if (!rect || rect.width < 1 || rect.height < 1) continue

    let fg = parseColor(getComputedStyle(el).color)
    if (!fg) continue
    const bg = effectiveBg(el)
    if (fg.a < 1) fg = over({ ...fg }, bg)
    const ratio = contrast(fg, bg)
    const cls = (el.className && typeof el.className === 'string' ? el.className : '').trim() || el.tagName.toLowerCase()
    const text = (node.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 80)

    if (ratio < THRESHOLD) {
      const key = cls + '|' + text + '|' + ratio.toFixed(2)
      if (seen.has(key)) continue
      seen.add(key)
      const disabled = hasAncestor(el, DISABLED_SEL)
      const chip = !disabled && isOnColoredChip(el)
      findings.push({
        text,
        tag: el.tagName.toLowerCase(),
        cls: cls.slice(0, 160),
        ratio: Math.round(ratio * 100) / 100,
        fg: toHex(fg),
        bg: toHex(bg),
        disabled,
        chip,
        reason: disabled ? 'disabled' : chip ? 'colored-control' : 'content',
        fontSize: getComputedStyle(el).fontSize || ''
      })
    }
  }
  return findings
}

/* ========== 运行 ========== */
async function main() {
  const args = parseArgs(process.argv.slice(2))
  const password = process.env.ADMIN_PASSWORD || DEFAULT_PASSWORD
  const username = process.env.ADMIN_USERNAME || DEFAULT_USERNAME
  const base = args.baseUrl.replace(/\/$/, '')
  const routes = args.only || [...DEFAULT_ROUTES, ...args.routes]

  console.log('=== JAiRouter 对比度审计 ===')
  console.log(`baseUrl   : ${base}${APP_PREFIX}`)
  console.log(`theme     : ${args.theme}   threshold: ${args.threshold}`)
  console.log(`pages     : ${routes.length}`)
  if (!routes.length) {
    console.error('no routes given')
    return 2
  }

  const report = { base, theme: args.theme, threshold: args.threshold, ranAt: new Date().toISOString(), pages: [] }
  const browser = await chromium.launch({ headless: args.headless })

  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
    const page = await context.newPage()
    page.setDefaultTimeout(20000)
    const pageErrors = []
    page.on('pageerror', e => pageErrors.push(String(e).slice(0, 200)))

    /* ---- 1. 登录 ---- */
    try {
      await page.goto(`${base}${APP_PREFIX}/login`, { waitUntil: 'domcontentloaded', timeout: 40000 })
      await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {})
      const form = page.locator('.login-form')
      if (await form.isVisible().catch(() => false)) {
        const inputs = page.locator('.login-form input')
        const n = await inputs.count()
        if (n >= 1) await inputs.nth(0).fill(username)
        if (n >= 2) await inputs.nth(1).fill(password)
        await page.locator('.login-button, .login-form button[type="submit"]').first().click()
        await page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 25000 })
      }
      await page.waitForSelector('.el-main, .layout-main', { timeout: 20000 })
      console.log('login OK (user=' + username + ')')
    } catch (err) {
      console.error('登录失败: ' + err.message)
      return 2
    }

    /* ---- 2. 切主题（默认暗色；dark 态由 html.dark 驱动） ---- */
    const wantDark = args.theme === 'dark'
    const toggle = page.locator('.theme-toggle')
    for (let i = 0; i < 3; i++) {
      const cur = await page.evaluate(() => document.documentElement.classList.contains('dark'))
      if (cur === wantDark) break
      if ((await toggle.count().catch(() => 0)) > 0) {
        await toggle.first().click()
        await page.waitForTimeout(300)
      }
      const after = await page.evaluate(() => document.documentElement.classList.contains('dark'))
      if (after === wantDark) break
      // 兜底：直接写偏好 + 加类，整页刷新保证后续全量 reload 维持主题
      await page.evaluate(theme => {
        localStorage.setItem('ja-theme', theme)
        document.documentElement.classList.toggle('dark', theme === 'dark')
      }, args.theme)
      await page.reload({ waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.el-main, .layout-main', { timeout: 20000 })
    }
    const finalDark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
    if (finalDark !== wantDark) {
      console.error('主题切换失败: 期望 dark=' + wantDark)
      return 2
    }
    console.log(`theme OK (html.dark=${finalDark})`)

    /* ---- 3. 逐页审计（每页容错，失败不中断） ---- */
    for (const route of routes) {
      const url = `${base}${APP_PREFIX}/${route}`
      const rec = { route, url, violations: [], exceptions: [], error: null }
      report.pages.push(rec)
      try {
        pageErrors.length = 0
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 40000 })
        await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
        if (/\/login$/.test(new URL(page.url()).pathname)) {
          rec.error = 'redirected to login (auth/permission?)'
          console.log(`[SKIP] ${route} -> ${rec.error}`)
          continue
        }
        // 等待 EP loading 消失 + 数据 settle（空数据页出现 el-empty 不阻塞）
        await page
          .waitForFunction(
            () => !document.querySelector('.el-loading-mask') && !document.querySelector('.el-loading-spinner'),
            { timeout: 8000 }
          )
          .catch(() => {})
        await page.waitForTimeout(900)

        const found = await page.evaluate(scanVisibleText, { threshold: args.threshold })
        for (const f of found || []) {
          if (f.reason === 'content') rec.violations.push(f)
          else rec.exceptions.push(f)
        }
        const tag = rec.violations.length ? 'FAIL' : 'ok  '
        let extra = rec.violations.length ? ` violations=${rec.violations.length}` : ''
        extra += rec.exceptions.length ? ` exceptions=${rec.exceptions.length}` : ''
        console.log(`[${tag}] ${route}${extra}`)
        if (pageErrors.length) console.log('   pageerror: ' + pageErrors[0])
      } catch (err) {
        rec.error = String((err && err.message) || err).slice(0, 300)
        console.log(`[ERR ] ${route} -> ${rec.error}`)
      }
    }
  } finally {
    await browser.close()
  }

  /* ---- 4. 汇总输出 ---- */
  console.log('\n=== 违规明细 (ratio<' + args.threshold + ') ===')
  for (const p of report.pages) {
    if (p.error) { console.log(`\n[${p.route}] ERROR: ${p.error}`); continue }
    if (!p.violations.length) continue
    console.log(`\n[${p.route}] ${p.violations.length} 处：`)
    for (const v of p.violations) {
      console.log(`   ratio=${v.ratio.toFixed(2)} fg=${v.fg} bg=${v.bg} <${v.cls}> "${v.text}"`)
    }
  }

  console.log('\n=== 例外（disabled / 自着色控件低对比，不计违规）===')
  for (const p of report.pages) {
    if (!p.exceptions.length) continue
    console.log(`[${p.route}] ${p.exceptions.length} 处:`)
    for (const v of p.exceptions.slice(0, 8)) {
      console.log(`   [${v.reason}] ratio=${v.ratio.toFixed(2)} fg=${v.fg} bg=${v.bg} <${v.cls}> "${v.text}"`)
    }
    if (p.exceptions.length > 8) console.log(`   ... 等 ${p.exceptions.length} 处`)
  }

  const totalViolations = report.pages.reduce((s, p) => s + p.violations.length, 0)
  const errorPages = report.pages.filter(p => p.error).length
  const violPages = report.pages.filter(p => p.violations.length).length
  console.log('\n=== 统计 ===')
  console.log(`页面总数: ${routes.length}  审计成功: ${routes.length - errorPages}  失败/跳过: ${errorPages}`)
  console.log(`违规(内容文字 <${args.threshold}): ${totalViolations} 处（${violPages} 页）`)
  console.log(`例外(disabled=${report.pages.reduce((s, p) => s + p.exceptions.filter(e => e.reason === 'disabled').length, 0)} 处, 自着色控件=${report.pages.reduce((s, p) => s + p.exceptions.filter(e => e.reason === 'colored-control').length, 0)} 处)`)

  if (args.json) {
    writeFileSync(args.json, JSON.stringify(report, null, 2))
    console.log('JSON 报告已写入: ' + args.json)
  }

  const exitCode = totalViolations > 0 ? 1 : 0
  console.log('exit=' + exitCode)
  return exitCode
}

main()
  .then(code => { process.exitCode = code })
  .catch(err => {
    console.error('脚本运行异常: ' + ((err && err.stack) || err))
    process.exitCode = 2
  })
