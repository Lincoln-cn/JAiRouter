/**
 * 认证与路由守卫
 */
import { newPage, login, gotoApp, currentPathname, isLoggedIn } from '../harness.mjs'

export default {
  id: 'auth',
  name: '登录/登出/路由守卫',
  suite: 'auth',
  order: 10,
  async run({ browser, check }) {
    // 1. 登录页渲染
    const page = await newPage(browser)
    await gotoApp(page, '/login')
    await page.waitForSelector('.login-form input')
    check('auth-login-page', true, page.url())

    const inputs = await page.locator('.login-form input').count()
    const submit = await page.locator('button.login-button').count()
    check('auth-login-form', inputs >= 2 && submit >= 1, `inputs=${inputs} submit=${submit}`)

    // 2. 未登录访问受保护路由 → 跳登录
    await gotoApp(page, '/dashboard/main')
    await page.waitForTimeout(800)
    const redirected = currentPathname(page).endsWith('/login')
    check('auth-guard-redirect', redirected, page.url())

    // 3. 错误密码 → 留在登录页
    await gotoApp(page, '/login')
    await page.locator('.login-form input').nth(0).fill('admin')
    await page.locator('.login-form input').nth(1).fill('WrongPass_ThisShouldFail!')
    await page.locator('button.login-button').first().click()
    await page.waitForTimeout(1500)
    const stillLogin = currentPathname(page).endsWith('/login')
    const tokenOnFail = await isLoggedIn(page)
    check('auth-login-fail', stillLogin && !tokenOnFail, `${page.url()} token=${tokenOnFail}`)

    // 4. 正确登录
    const url = await login(page)
    check('auth-login-success', !url.endsWith('/login'), url)
    check('auth-token-present', await isLoggedIn(page))

    // 5. 已登录访问登录页 → 重定向仪表板
    await page.waitForTimeout(500)
    await gotoApp(page, '/login')
    // 不要用固定 sleep 判跳转：SPA 启动 + 守卫重定向在机器繁忙时会超过 1.2s，
    // 造成偶发假红（审计 #469 同族；实测全量 445/446 就红在这一条，单跑即绿）。
    // 改为等条件成立，超时才算失败。
    const bounced = await page
      .waitForFunction(() => !location.pathname.endsWith('/login'), null, { timeout: 8000 })
      .then(() => true)
      .catch(() => false)
    check('auth-login-page-bounce', bounced, page.url())

    // 6. UI 登出
    const userMenu = page.locator('.user-info').first()
    if ((await userMenu.count()) > 0) {
      await userMenu.click()
      await page.waitForTimeout(300)
      const logoutItem = page
        .locator('.el-dropdown-menu__item')
        .filter({ hasText: /登出|退出|Logout|退出登录/i })
        .first()
      if ((await logoutItem.count()) > 0) {
        await logoutItem.click()
        await page.waitForFunction(() => location.pathname.endsWith('/login'), { timeout: 15000 }).catch(() => {})
        const tokenAfter = await isLoggedIn(page)
        check('auth-logout', currentPathname(page).endsWith('/login') && !tokenAfter, page.url())
      } else {
        check('auth-logout', false, 'logout menu item not found')
      }
    } else {
      check('auth-logout', false, 'user menu not found')
    }

    await page.close()
  }
}
