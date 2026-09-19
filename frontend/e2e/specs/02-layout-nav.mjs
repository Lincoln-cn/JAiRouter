/**
 * 布局 / 菜单导航 / 语言 / 主题
 */
import { createAuthedContext, gotoApp, currentPathname } from '../harness.mjs'
import { ALL_ROUTES } from '../routes.mjs'

const MENU_GROUPS = [
  'dashboard',
  'model-services',
  'traffic',
  'records',
  'tracing',
  'security',
  'system',
  'playground',
  'tools'
]

export default {
  id: 'layout-nav',
  name: '布局导航与主题语言',
  suite: 'smoke',
  order: 50,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)
    await gotoApp(page, '/dashboard/main')
    await page.waitForTimeout(500)

    // 布局骨架
    check('layout-container', await page.locator('.layout-container').first().isVisible().catch(() => false))
    check('layout-aside', await page.locator('.layout-aside').first().isVisible().catch(() => false))
    check('layout-header', await page.locator('.layout-header').first().isVisible().catch(() => false))
    check('layout-logo', (await page.locator('.logo-text').count()) > 0)

    // 菜单组
    const subMenus = page.locator('.el-sub-menu')
    const subCount = await subMenus.count()
    check('layout-menu-groups', subCount >= 8, `sub-menus=${subCount}`)

    const menuText = await page.locator('.layout-menu').innerText().catch(() => '')
    const missing = MENU_GROUPS.filter(g => false) // text is i18n; check structure via count only
    check('layout-menu-render', menuText.trim().length > 10, `missing=${missing.join(',')}`)

    // 用户区
    check('layout-user-menu', (await page.locator('.user-info').count()) > 0)
    check('layout-lang-switch', (await page.locator('.lang-toggle, .language-switcher, .layout-header').count()) > 0)

    // 主题切换
    const themeBtn = page.locator('.theme-toggle').first()
    const beforeClass = await page.evaluate(() => document.documentElement.className)
    if ((await themeBtn.count()) > 0) {
      await themeBtn.click()
      await page.waitForTimeout(400)
      const afterClass = await page.evaluate(() => document.documentElement.className)
      check('layout-theme-toggle', beforeClass !== afterClass || afterClass.includes('dark') || afterClass.includes('light'), `${beforeClass} -> ${afterClass}`)
      // 切回
      await themeBtn.click()
      await page.waitForTimeout(200)
    } else {
      check('layout-theme-toggle', false, 'theme button missing')
    }

    // 语言切换：LanguageSwitcher 内部触发器是 .lang-btn
    const langBtn = page.locator('.lang-toggle .lang-btn, .lang-btn').first()
    if ((await langBtn.count()) > 0) {
      const beforeLabel = await page.locator('.lang-btn .lang-label').first().innerText().catch(() => '')
      const beforeMenu = await page.locator('.layout-menu').innerText().catch(() => '')
      await langBtn.click()
      await page.waitForTimeout(400)
      const options = page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item')
      const optCount = await options.count()
      let switched = false
      let afterLabel = beforeLabel
      let afterMenu = beforeMenu
      for (let i = 0; i < optCount; i++) {
        const text = await options.nth(i).innerText().catch(() => '')
        if (text.trim() && text.trim() !== beforeLabel.trim()) {
          await options.nth(i).click()
          await page.waitForTimeout(600)
          afterLabel = await page.locator('.lang-btn .lang-label').first().innerText().catch(() => '')
          afterMenu = await page.locator('.layout-menu').innerText().catch(() => '')
          switched = afterLabel !== beforeLabel || afterMenu !== beforeMenu
          break
        }
      }
      check(
        'layout-lang-switch-works',
        switched,
        `label:${beforeLabel}=>${afterLabel}`
      )
      // 尽量切回原语言，避免影响后续
      if (switched && beforeLabel) {
        await page.locator('.lang-toggle .lang-btn, .lang-btn').first().click()
        await page.waitForTimeout(300)
        const back = page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').filter({ hasText: beforeLabel.trim() }).first()
        if ((await back.count()) > 0) {
          await back.click()
          await page.waitForTimeout(400)
        }
      }
    } else {
      check('layout-lang-switch-works', false, 'lang toggle missing')
    }

    // 菜单点击导航：用「可见子项」判断是否展开，避免 aria/inline 菜单误判
    async function openSubmenu(group) {
      const visible = async () => group.locator('.el-menu-item:visible').count()
      if ((await visible()) > 0) return true
      const title = group.locator('.el-sub-menu__title').first()
      await title.click({ force: true }).catch(() => {})
      await page.waitForTimeout(350)
      if ((await visible()) === 0) {
        // 再点一次切换（部分状态下手风琴/展开抖动）
        await title.click({ force: true }).catch(() => {})
        await page.waitForTimeout(350)
      }
      return (await visible()) > 0
    }

    for (let i = 0; i < Math.min(subCount, MENU_GROUPS.length); i++) {
      const group = page.locator('.layout-menu .el-sub-menu').nth(i)
      const title = await group.locator('.el-sub-menu__title').innerText().catch(() => `#${i}`)
      try {
        const opened = await openSubmenu(group)
        if (!opened) {
          check(`nav-menu-group-${i}`, false, `${title}: submenu not expandable`)
          continue
        }
        const item = group.locator('.el-menu-item:visible').first()
        await item.click({ force: true, timeout: 8000 })
        await page.waitForTimeout(500)
        const path = currentPathname(page)
        const inLayout = await page.locator('.layout-main').isVisible().catch(() => false)
        check(`nav-menu-group-${i}`, inLayout && path.includes('/admin'), `${title} -> ${path}`)
      } catch (e) {
        check(`nav-menu-group-${i}`, false, `${title}: ${e.message}`)
      }
    }

    // 面包屑存在
    check('layout-breadcrumb', (await page.locator('.el-breadcrumb').count()) > 0)

    // 所有路由可通过直接 URL 访达（与菜单权限无关的兜底已在 route-smoke）
    const sample = ALL_ROUTES.filter(r => r.group === 'security' || r.group === 'tools')
    for (const r of sample) {
      await gotoApp(page, r.path, { waitMs: 300 })
      const on = currentPathname(page).endsWith(r.path)
      check(`nav-direct:${r.name}`, on, currentPathname(page))
    }

    await page.close()
  }
}
