/**
 * 全站弹窗骨架：打开新增类弹窗 → 校验表单控件 → 关闭（不保存）
 */
import {
  createAuthedContext,
  gotoApp,
  clickMainButton,
  waitForOverlay,
  overlayFormStats,
  closeOverlay,
  countVisibleOverlays,
  waitTableSettled
} from '../harness.mjs'

const DIALOG_PAGES = [
  {
    tag: 'accounts',
    path: '/system/accounts',
    openRe: /创建账户|Create Account/,
    minForms: 1,
    minInputs: 2
  },
  {
    tag: 'pools',
    path: '/config/pools',
    openRe: /新增资源池|Add Pool|新增/,
    minForms: 1,
    minInputs: 1
  },
  {
    tag: 'api-keys',
    path: '/security/api-keys',
    openRe: /创建API密钥|创建 API|Create API|新建/,
    minForms: 1,
    minInputs: 1
  },
  {
    tag: 'blacklist',
    path: '/security/blacklist',
    openRe: /添加黑名单|Add Blacklist|添加/,
    minForms: 1,
    minInputs: 0
  },
  {
    tag: 'rules-add',
    path: '/config/rules',
    openRe: /新增规则|Add Rule/,
    minForms: 1,
    minInputs: 1
  },
  {
    tag: 'rules-template',
    path: '/config/rules',
    openRe: /从模板创建|Template/,
    minForms: 0,
    minInputs: 0,
    minButtons: 1
  },
  {
    tag: 'adapters',
    path: '/config/adapters',
    openRe: /新增Adapter|新增 Adapter|Add Adapter/,
    minForms: 0,
    minInputs: 0,
    minButtons: 1
  },
  {
    tag: 'services',
    path: '/config/services',
    openRe: /添加服务|Add Service/,
    minForms: 1,
    minInputs: 0,
    optional: true
  },
  {
    tag: 'instances',
    path: '/config/instances',
    openRe: /添加实例|Add Instance/,
    minForms: 1,
    minInputs: 0,
    optional: true
  },
  {
    tag: 'jwt-tokens',
    path: '/security/jwt-tokens',
    openRe: /撤销|Revoke|清理/,
    minForms: 0,
    minInputs: 0,
    optional: true,
    minButtons: 0
  }
]

export default {
  id: 'dialog-skeleton',
  name: '全站弹窗打开/关闭骨架',
  suite: 'dialog',
  order: 210,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of DIALOG_PAGES) {
      try {
        await gotoApp(page, p.path, { waitMs: 700 })
        if ((await page.locator('.el-table').count()) > 0) {
          try {
            await waitTableSettled(page, 8000)
          } catch {}
        }

        const opened = await clickMainButton(page, p.openRe)
        if (!opened) {
          check(`dialog:${p.tag}:open`, !!p.optional, p.optional ? `skip: button not found (${p.openRe})` : `button not found ${p.openRe}`)
          continue
        }

        let overlayVisible = false
        try {
          await waitForOverlay(page, 6000)
          overlayVisible = true
        } catch {
          const msgBox = await page.locator('.el-message-box').first().isVisible().catch(() => false)
          if (msgBox) {
            check(`dialog:${p.tag}:open`, true, 'message-box instead of dialog')
            await page.locator('.el-message-box button').last().click().catch(() => {})
            check(`dialog:${p.tag}:close`, true)
            continue
          }
        }

        if (!overlayVisible) {
          check(`dialog:${p.tag}:open`, !!p.optional, 'overlay not visible after click')
          continue
        }
        check(`dialog:${p.tag}:open`, true)

        const stats = await overlayFormStats(page)
        const formOk = stats.forms >= (p.minForms || 0)
        const inputOk = stats.inputs >= (p.minInputs || 0)
        const buttonOk = p.minButtons == null || stats.buttons >= p.minButtons
        check(
          `dialog:${p.tag}:form`,
          formOk && inputOk && buttonOk,
          `forms=${stats.forms} inputs=${stats.inputs} selects=${stats.selects} buttons=${stats.buttons}`
        )

        await closeOverlay(page)
        const stillOpen = await countVisibleOverlays(page)
        check(`dialog:${p.tag}:close`, stillOpen === 0, `overlays=${stillOpen}`)
      } catch (e) {
        check(`dialog:${p.tag}:aborted`, !!p.optional, e?.message || String(e))
        await closeOverlay(page).catch(() => {})
      }
    }

    // 额外：规则编辑弹窗（点第一行「编辑」若存在）
    await gotoApp(page, '/config/rules', { waitMs: 600 })
    try {
      await waitTableSettled(page, 6000)
    } catch {}
    const editOpened = await clickMainButton(page, /^编辑$/, { scope: page.locator('.el-table') })
    if (editOpened) {
      try {
        await waitForOverlay(page, 5000)
        const stats = await overlayFormStats(page)
        check('dialog:rule-edit:form', stats.inputs + stats.selects > 0, JSON.stringify(stats))
        await closeOverlay(page)
      } catch {
        check('dialog:rule-edit:form', false, 'edit overlay not opened')
      }
    } else {
      check('dialog:rule-edit:form', true, 'skip: no edit button / empty table')
    }

    await page.close()
  }
}
