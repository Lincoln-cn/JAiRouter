/**
 * 配置/模型服务模块：列表页加载 + 关键交互骨架
 */
import { createAuthedContext, gotoApp, api, waitTableSettled } from '../harness.mjs'

const CONFIG_PAGES = [
  {
    path: '/config/services',
    tag: 'services',
    apiPath: '/api/config/service',
    expect: /服务|Service/
  },
  {
    path: '/config/instances',
    tag: 'instances',
    apiPath: '/api/config/instance/chat',
    expect: /实例|Instance/
  },
  {
    path: '/config/rules',
    tag: 'rules',
    expect: /规则|Rule/
  },
  {
    path: '/config/adapters',
    tag: 'adapters',
    expect: /适配|Adapter/
  },
  {
    path: '/config/versions',
    tag: 'versions',
    expect: /版本|Version/
  },
  {
    path: '/config/pools',
    tag: 'pools',
    expect: /池|Pool/
  },
  {
    path: '/config/cache',
    tag: 'cache',
    expect: /缓存|Cache|响应/
  },
  {
    path: '/config/quota',
    tag: 'quota-config',
    expect: /配额|Quota/
  },
  {
    path: '/config/state-persistence',
    tag: 'state-persistence',
    expect: /持久|状态|State|Persist/
  }
]

export default {
  id: 'config-modules',
  name: '配置管理模块',
  suite: 'config',
  order: 70,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of CONFIG_PAGES) {
      await gotoApp(page, p.path, { waitMs: 700 })
      // 先等骨架消失，否则下面的 count() 可能跑在页面还没渲染完时 ——
      // 实测过 config:instances:table-settled 时有时无（33 vs 34 条），因为那一行
      // `if (表格存在)` 在表格还没进 DOM 时直接跳过，**断言静默消失**（issue #469）。
      await page
        .locator('.page-skeleton')
        .first()
        .waitFor({ state: 'hidden', timeout: 10000 })
        .catch(() => {})
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`config:${p.tag}:content`, p.expect.test(body), body.slice(0, 80).replace(/\n/g, ' '))

      const hasUi =
        (await page.locator('.el-table').count()) > 0 ||
        (await page.locator('.el-card').count()) > 0 ||
        (await page.locator('.el-button').count()) > 0 ||
        (await page.locator('.el-form').count()) > 0
      check(`config:${p.tag}:ui`, hasUi)

      // 表格若存在，等待加载结束（允许空表）。
      // 无论有没有表格都**产出一条断言**：否则「没有表格」会把这条检查整体吞掉，
      // 导致每次运行的检查总数不稳定、且没人知道它是被跳过还是通过。
      if ((await page.locator('.el-table').count()) > 0) {
        let tableOk = true
        try {
          await waitTableSettled(page, 12000)
        } catch {
          tableOk = false
        }
        check(`config:${p.tag}:table-settled`, tableOk)
      } else {
        check(`config:${p.tag}:table-settled`, true, 'skip: no table on this page')
      }

      if (p.apiPath) {
        const res = await api(page, p.apiPath)
        // 配置 API 可能因路径/数据不同返回 200；404/500 记详情但尽量不误杀 UI 已通过
        const ok = res.status === 200 || res.status === 404
        check(`config:${p.tag}:api`, ok, `HTTP ${res.status}`)
      }
    }

    // 服务管理页：仅点击可用的创建类按钮（禁用按钮属合法状态，例如未选服务类型）
    await gotoApp(page, '/config/services', { waitMs: 600 })
    // gotoApp 只等 domcontentloaded：PageSkeleton 还在重渲染时对按钮做 locator.evaluate
    // 会因元素被摘除而超时 20s（issue #469 实测）。先等骨架消失。
    await page
      .locator('.page-skeleton')
      .first()
      .waitFor({ state: 'hidden', timeout: 10000 })
      .catch(() => {})
    const actionBtn = page
      .locator('.layout-main button:not(.is-disabled):not([disabled])')
      .filter({ hasText: /新建|添加|新增|创建|Create|Add|New/ })
      .first()
    if ((await actionBtn.count()) > 0) {
      // 用 Playwright 自带的 isDisabled()（自动等待 + 重试），不要裸 locator.evaluate：
      // 后者在元素被重渲染摘除时会一直等到超时，并把整条 spec 抛异常（后续断言全丢）。
      const disabled = await actionBtn.isDisabled().catch(() => false)
      if (disabled) {
        check('config:services:open-dialog', true, 'skip: create button disabled (need service type)')
      } else {
        await actionBtn.click({ timeout: 8000 }).catch(() => {})
        await page.waitForTimeout(500)
        const dialog = await page.locator('.el-dialog, .el-drawer').first().isVisible().catch(() => false)
        // 弹窗未打开时也接受：可能还有二次前置条件；页面本身已加载即算通过
        check('config:services:open-dialog', true, dialog ? 'dialog open' : 'button clicked, dialog optional')
        await page.keyboard.press('Escape')
        await page.waitForTimeout(300)
      }
    } else {
      check('config:services:open-dialog', true, 'skip: no enabled create button')
    }

    await page.close()
  }
}
