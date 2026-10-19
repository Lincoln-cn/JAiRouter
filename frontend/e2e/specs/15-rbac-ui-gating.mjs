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

    // ── 例 2：`/config/rules` 的**拖拽排序**（不是按钮，`v-permission` 摘元素不适用）──
    // 拖拽改 priority 并立即 PUT /api/config/rules/priority ⇒ 是写操作（config:rules:write）。
    // 修前：手柄对所有角色渲染，只读用户拖一下必然 403（issue #468 残项①）。
    // 默认 rules.yml 是空列表，所以先由 ADMIN 建一条非 YAML 规则，否则页面上根本没有手柄
    // ——那样"低权限看不到手柄"会因为它本来就没有而假绿。
    const ruleName = uniq('e2e-drag-rule')
    const ruleBody = {
      name: ruleName,
      description: 'e2e rbac ui gating',
      enabled: true,
      priority: 10,
      conditions: [{ type: 'MODEL_NAME', operator: 'EQUALS', value: 'e2e-model' }],
      action: { type: 'TARGET_MODEL', modelName: 'e2e-target' }
    }
    const ruleCreated = await api(adminPage, '/api/config/rules', { method: 'POST', body: ruleBody })
    check('rbac-ui:rules:create-fixture-rule', ruleCreated.status === 200 || ruleCreated.status === 201,
      `status=${ruleCreated.status}`)
    const createdRuleId = ruleCreated.body?.data?.id ?? null

    await gotoApp(lowPage, '/config/rules', { waitMs: 900 })
    const lowDragCount = await lowPage.locator('.layout-main .drag-handle').count()
    check('rbac-ui:rules:drag-handle-hidden-for-limited', lowDragCount === 0, `count=${lowDragCount}`)

    await gotoApp(adminPage, '/config/rules', { waitMs: 900 })
    const adminDragCount = await adminPage.locator('.layout-main .drag-handle').count()
    check('rbac-ui:rules:drag-handle-visible-for-admin', adminDragCount > 0, `count=${adminDragCount}`)

    // ── 例 3：`/monitoring/slow-queries` 的「重置全部统计」──
    // 路由只要 monitoring:slowquery:read，但重置是 DELETE（自 #468 起需 monitoring:slowquery:write）。
    // ⚠️ 选择器必须精确到「重置统计」：本页还有一个**筛选区的「重置」**按钮
    // （`slowQuery.resetFilter`），用 /重置|Reset/ 会把它算进来 —— 第一版就这么假绿了。
    const resetStatsText = /重置统计|Reset Stats/
    const lowResetCount = await (async () => {
      await gotoApp(lowPage, '/monitoring/slow-queries', { waitMs: 900 })
      return lowPage.locator('.layout-main button').filter({ hasText: resetStatsText }).count()
    })()
    check('rbac-ui:slowquery:reset-hidden-for-limited', lowResetCount === 0, `count=${lowResetCount}`)

    const adminResetCount = await (async () => {
      await gotoApp(adminPage, '/monitoring/slow-queries', { waitMs: 900 })
      return adminPage.locator('.layout-main button').filter({ hasText: resetStatsText }).count()
    })()
    check('rbac-ui:slowquery:reset-visible-for-admin', adminResetCount > 0, `count=${adminResetCount}`)

    // 后端裁决：低权限 DELETE 必须 403（证明前端只是显隐，不是安全边界）
    const lowResetApi = await api(lowPage, '/api/monitoring/slow-queries/stats', { method: 'DELETE' })
    check('rbac-ui:slowquery:reset-api-403-for-limited', lowResetApi.status === 403,
      `status=${lowResetApi.status}`)

    // 对照：ADMIN 必须仍然能重置（新增 URL 规则不得把原本可用的操作关掉）
    const adminResetApi = await api(adminPage, '/api/monitoring/slow-queries/stats', { method: 'DELETE' })
    check('rbac-ui:slowquery:reset-api-200-for-admin', adminResetApi.status === 200,
      `status=${adminResetApi.status}`)

    // ── 例 5：`/circuit-breakers/monitoring` 的「重置」与「批量重置」──
    // 路由只要 `cb:monitoring:read`（USER 角色就有），而重置走
    // `POST /api/v1/circuit-breaker-monitor/instances/**` ⇒ 自 #557 起需
    // `config:circuitbreaker:write`。修前 USER 能看到按钮、点了必然 403。
    // ⚠️ 实例表由后端按「运行时熔断器 ⋈ 实例库」生成，开发环境可能为空表 ——
    // 空表时按钮根本不存在，断言会假绿。这里用固定夹具保证「表里至少一行」。
    const cbFixture = [
      {
        instanceId: '11111111-1111-4111-8111-111111111111',
        instanceName: 'rbac-fixture-open',
        serviceType: 'chat',
        baseUrl: 'https://open.example.com',
        state: 'OPEN',
        failureCount: 5,
        successCount: 0,
        failureThreshold: 5,
        successThreshold: 2,
        timeoutMillis: 60000,
        lastFailureTime: new Date().toISOString()
      }
    ]
    const mockInstances = async (target) => {
      await target.route('**/api/v1/circuit-breaker-monitor/instances**', (route) => {
        if (route.request().method() !== 'GET') return route.continue()
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(cbFixture)
        })
      })
    }
    await mockInstances(lowPage)
    await mockInstances(adminPage)

    await gotoApp(lowPage, '/circuit-breakers/monitoring', { waitMs: 1200 })
    const lowCbBody = await lowPage.locator('.layout-main').innerText().catch(() => '')
    check('rbac-ui:cb:page-opens-for-limited', /熔断器实例|Circuit Breaker Instances/.test(lowCbBody),
      lowCbBody.slice(0, 80).replace(/\n/g, ' '))
    const lowCbRows = await lowPage.locator('.instances-table .el-table__body tr').count()
    const lowCbReset = await lowPage
      .locator('.instances-table button')
      .filter({ hasText: /^重置$|^Reset$/ })
      .count()
    check('rbac-ui:cb:rows-rendered-for-limited', lowCbRows === 1, `rows=${lowCbRows}`)
    check('rbac-ui:cb:reset-hidden-for-limited', lowCbReset === 0, `count=${lowCbReset}`)
    const lowCbBatch = await lowPage
      .locator('.layout-main button')
      .filter({ hasText: /重置筛选结果|Reset Filtered/ })
      .count()
    check('rbac-ui:cb:batch-reset-hidden-for-limited', lowCbBatch === 0, `count=${lowCbBatch}`)

    await gotoApp(adminPage, '/circuit-breakers/monitoring', { waitMs: 1200 })
    const adminCbReset = await adminPage
      .locator('.instances-table button')
      .filter({ hasText: /^重置$|^Reset$/ })
      .count()
    check('rbac-ui:cb:reset-visible-for-admin', adminCbReset > 0, `count=${adminCbReset}`)

    // 后端裁决：USER 的 POST 必须 403（前端只是显隐，不是安全边界）
    const lowCbApi = await api(lowPage, '/api/v1/circuit-breaker-monitor/instances/uuid-none/reset', {
      method: 'POST'
    })
    check('rbac-ui:cb:reset-api-403-for-limited', lowCbApi.status === 403, `status=${lowCbApi.status}`)

    // ── 清理夹具规则 ──
    if (createdRuleId) {
      const delRule = await api(adminPage, `/api/config/rules/${createdRuleId}`, { method: 'DELETE' })
      check('rbac-ui:rules:cleanup-fixture', delRule.status === 200, `status=${delRule.status}`)
    } else {
      check('rbac-ui:rules:cleanup-fixture', false, 'rule id not found')
    }

    // ── 例 4：`/security/**` 的**角色门**（不是权限码门）──
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
