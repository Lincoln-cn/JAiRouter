/**
 * 安全管理：API Key / 脱敏 / JWT / 黑名单 / 审计
 * （配额抽屉与 PII 深测见 12-deep-security-quota.mjs，这里做模块级骨架）
 */
import { createAuthedContext, gotoApp, api, apiThrottled, waitTableSettled } from '../harness.mjs'

const PAGES = [
  // api-keys 列表接口有 AdminApiRateLimiter 30/min；UI 冒烟 + deep 负责 API，这里只验页面
  { path: '/security/api-keys', tag: 'api-keys', expect: /API|Key|密钥|配额/i },
  { path: '/security/sanitization', tag: 'sanitization', expect: /脱敏|PII|敏感|Sanitiz/i, api: '/api/config/sanitization' },
  { path: '/security/jwt-tokens', tag: 'jwt-tokens', expect: /JWT|Token|令牌|会话/i },
  { path: '/security/blacklist', tag: 'blacklist', expect: /黑名单|Blacklist|IP|封禁/i },
  { path: '/security/audit-logs', tag: 'audit-logs', expect: /审计|Audit|日志|Log/i }
]

export default {
  id: 'security-modules',
  name: '安全管理模块骨架',
  suite: 'security',
  order: 110,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of PAGES) {
      await gotoApp(page, p.path, { waitMs: 700 })
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`security:${p.tag}:content`, p.expect.test(body), body.slice(0, 100).replace(/\n/g, ' '))

      if ((await page.locator('.el-table').count()) > 0) {
        let ok = true
        try {
          await waitTableSettled(page, 12000)
        } catch {
          ok = false
        }
        check(`security:${p.tag}:table-settled`, ok)
      }

      if (p.api) {
        const res = await apiThrottled(page, p.api)
        check(`security:${p.tag}:api`, res.status === 200, `HTTP ${res.status}`)
      }
    }

    // JWT 账户 / 权限页（系统组）
    await gotoApp(page, '/system/accounts', { waitMs: 700 })
    const accBody = await page.locator('.layout-main').innerText().catch(() => '')
    check('security:accounts:content', /账户|账号|用户|Account|User/i.test(accBody), accBody.slice(0, 80).replace(/\n/g, ' '))

    await gotoApp(page, '/system/permissions', { waitMs: 700 })
    const permBody = await page.locator('.layout-main').innerText().catch(() => '')
    check(
      'security:permissions:content',
      /权限|Permission|角色|Role/i.test(permBody),
      permBody.slice(0, 80).replace(/\n/g, ' ')
    )

    await page.close()
  }
}
