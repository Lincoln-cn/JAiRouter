/**
 * RBAC 页内门控（审计 #468）
 *
 * 背景：权限码层面读写是分离的（14 组 :read/:write），但**页内按钮**原先不随写权限变化
 * ——路由只要 :read，只读角色就能进页，然后看到"点了必然 403"的按钮。
 *
 * 本 spec 守第一条已验证的实例：`/config/quota`
 *   路由要求 `config:quota:read`，保存动作是 `PUT /api/config/quota`（需 `config:quota:write`）。
 *   修复后：低权限角色**看不到**保存按钮；ADMIN 看得到。
 *
 * ⚠️ 两个断言必须成对：只断言"低权限看不到按钮"是**空转**的
 * （选择器写错、页面没加载完、按钮改名都会让它通过）。所以还要断言
 * "ADMIN 看得到同一个按钮"，用同一个选择器做对照。
 */
import { createAuthedContext, createAuthedContextAs, gotoApp, api, uniq } from '../harness.mjs'

export default {
  id: 'rbac-ui-gating',
  name: 'RBAC 页内门控（#468）',
  suite: 'config',
  order: 150,
  async run({ browser, check }) {
    const adminPage = await createAuthedContext(browser)

    const uname = uniq('e2e-lowpriv')
    const pass = 'E2eLowPriv!2345'

    const created = await api(adminPage, '/api/security/jwt/accounts', {
      method: 'POST',
      body: { username: uname, password: pass, roles: ['USER'], enabled: true }
    })
    check('rbac-ui:create-limited-user', created.status === 200, `status=${created.status}`)

    const list = await api(adminPage, '/api/security/jwt/accounts')
    const items = list.body?.data?.items ?? list.body?.data ?? []
    const accId = (Array.isArray(items) ? items : []).find((a) => a.username === uname)?.id ?? null

    const lowPage = await createAuthedContextAs(browser, uname, pass)
    const saveBtnText = /保存配置|Save Configuration/

    // ── 低权限：页面能进，但保存按钮必须不存在 ──
    await gotoApp(lowPage, '/config/quota', { waitMs: 700 })
    await lowPage
      .locator('.page-skeleton')
      .first()
      .waitFor({ state: 'hidden', timeout: 10000 })
      .catch(() => {})
    const lowBody = await lowPage.locator('.layout-main').innerText().catch(() => '')
    check('rbac-ui:quota:page-opens-for-limited', /配额|Quota/i.test(lowBody), lowBody.slice(0, 80).replace(/\n/g, ' '))

    const lowSaveCount = await lowPage
      .locator('.layout-main button')
      .filter({ hasText: saveBtnText })
      .count()
    check('rbac-ui:quota:save-hidden-for-limited', lowSaveCount === 0, `count=${lowSaveCount}`)

    // ── 对照（反空转）：ADMIN 用同一个选择器必须看得到 ──
    await gotoApp(adminPage, '/config/quota', { waitMs: 700 })
    await adminPage
      .locator('.page-skeleton')
      .first()
      .waitFor({ state: 'hidden', timeout: 10000 })
      .catch(() => {})
    const adminSaveCount = await adminPage
      .locator('.layout-main button')
      .filter({ hasText: saveBtnText })
      .count()
    check('rbac-ui:quota:save-visible-for-admin', adminSaveCount > 0, `count=${adminSaveCount}`)

    // ── 第二例：`/security/**` 的**角色门**（不是权限码门）──
    // 我原以为这里要测"OPERATOR 能进审计页但没有黑名单权限"。实测否掉了这个前提：
    // `/security` **父路由**带 `meta.roles: ['ADMIN']`（router/index.ts:176），
    // 所以非 ADMIN 根本进不去任何 `/security/**` 子页——子页自己的 `meta.permissions`
    // 被这个角色门完全遮住（OPERATOR 的 JWT 里**确实**有 security:audit:read，仍被重定向）。
    // 因此这里断言的真实属性是：**OPERATOR 访问 /security/audit-logs 必须被重定向到仪表盘**。
    // ⚠️ 这条不能用「页面正文里有没有『审计』二字」来判：仪表盘的「审计事件」字样会让它**假绿**
    //    （我第一版就这么错了）。判据必须是 URL。
    const opName = uniq('e2e-operator')
    const opCreated = await api(adminPage, '/api/security/jwt/accounts', {
      method: 'POST',
      body: { username: opName, password: pass, roles: ['OPERATOR'], enabled: true }
    })
    check('rbac-ui:create-operator', opCreated.status === 200, `status=${opCreated.status}`)

    const list2 = await api(adminPage, '/api/security/jwt/accounts')
    const items2 = list2.body?.data?.items ?? list2.body?.data ?? []
    const opId = (Array.isArray(items2) ? items2 : []).find((a) => a.username === opName)?.id ?? null

    const opPage = await createAuthedContextAs(browser, opName, pass)

    await gotoApp(opPage, '/security/audit-logs', { waitMs: 1500 })
    const opPath = new URL(opPage.url()).pathname
    check(
      'rbac-ui:security-pages-are-admin-only',
      !opPath.includes('/security/'),
      `operator 落在 ${opPath}（期望被重定向出 /security/）`
    )

    // 对照：ADMIN 必须**留在**该页（证明重定向确实来自角色门，而不是页面本身坏了）
    await gotoApp(adminPage, '/security/audit-logs', { waitMs: 1200 })
    const adminPath = new URL(adminPage.url()).pathname
    check(
      'rbac-ui:security-pages-open-for-admin',
      adminPath.includes('/security/audit-logs'),
      `admin 落在 ${adminPath}`
    )

    if (opId) {
      const del = await api(adminPage, `/api/security/jwt/accounts/${opId}`, { method: 'DELETE' })
      check('rbac-ui:cleanup-operator', del.status === 200, `status=${del.status}`)
    } else {
      check('rbac-ui:cleanup-operator', false, 'account id not found')
    }

    // ── 清理 ──
    if (accId) {
      const del = await api(adminPage, `/api/security/jwt/accounts/${accId}`, { method: 'DELETE' })
      check('rbac-ui:cleanup-limited-user', del.status === 200, `status=${del.status}`)
    } else {
      check('rbac-ui:cleanup-limited-user', false, 'account id not found')
    }

    await opPage.close()
    await lowPage.close()
    await adminPage.close()
  }
}
