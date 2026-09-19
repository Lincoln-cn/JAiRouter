import { chromium } from 'playwright-core'
const BASE = process.env.E2E_BASE_URL || 'http://127.0.0.1:8080/admin'
const EXE = process.env.CHROMIUM_PATH ||
  'C:\\Users\\Administrator\\AppData\\Local\\ms-playwright\\chromium_headless_shell-1208\\chrome-headless-shell-win64\\chrome-headless-shell.exe'
const browser = await chromium.launch({ executablePath: EXE, headless: true, args: ['--no-sandbox', '--disable-gpu'] })
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.login-form input')
await page.locator('.login-form input').nth(0).fill('admin')
await page.locator('.login-form input').nth(1).fill('ChangeMeOnFirstStartup123456')
await page.locator('button.login-button').first().click()
await page.waitForFunction(() => !location.pathname.endsWith('/login'))
await page.goto(`${BASE}/security/api-keys`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2000)
const info = await page.evaluate(() => {
  const buttons = [...document.querySelectorAll('.el-table button')].map(b => b.textContent?.trim())
  const lang = document.documentElement.lang
  const token = !!localStorage.getItem('admin_token')
  return { buttons, lang, token, url: location.href }
})
console.log(JSON.stringify(info, null, 2))
await browser.close()
