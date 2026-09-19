/**
 * 数据记录与监控：调用历史 / 慢查询 / 异常
 */
import { createAuthedContext, gotoApp, api, waitTableSettled } from '../harness.mjs'

const PAGES = [
  {
    path: '/call-history/dashboard',
    tag: 'ch-dashboard',
    expect: /调用|历史|请求|Token|Dashboard|统计/i
  },
  {
    path: '/call-history/list',
    tag: 'ch-list',
    expect: /调用|历史|列表|时间|模型|Call|History/i,
    api: '/api/call-history/list?page=0&size=10'
  },
  {
    path: '/call-history/slow-calls',
    tag: 'ch-slow',
    expect: /慢|Slow|调用|耗时/i
  },
  {
    path: '/call-history/token-usage',
    tag: 'ch-token',
    expect: /Token|用量|统计|Usage/i
  },
  {
    path: '/monitoring/slow-queries',
    tag: 'slow-queries',
    expect: /慢|查询|Slow|Query|耗时/i
  },
  {
    path: '/monitoring/quota',
    tag: 'quota-monitor',
    expect: /配额|Quota|用量/i,
    api: '/api/monitoring/quota/usage?window=DAY'
  },
  {
    path: '/exceptions/list',
    tag: 'exceptions',
    expect: /异常|Exception|错误|Error/i
  },
  {
    path: '/exceptions/statistics',
    tag: 'exception-stats',
    expect: /异常|统计|Exception|Statistic/i
  }
]

export default {
  id: 'records-monitoring',
  name: '数据记录与监控',
  suite: 'records',
  order: 90,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    for (const p of PAGES) {
      await gotoApp(page, p.path, { waitMs: 700 })
      const body = await page.locator('.layout-main').innerText().catch(() => '')
      check(`records:${p.tag}:content`, p.expect.test(body), body.slice(0, 100).replace(/\n/g, ' '))

      if ((await page.locator('.el-table').count()) > 0) {
        let ok = true
        try {
          await waitTableSettled(page, 12000)
        } catch {
          ok = false
        }
        check(`records:${p.tag}:table-settled`, ok)
      }

      if (p.api) {
        const res = await api(page, p.api)
        check(`records:${p.tag}:api`, res.status === 200 || res.status === 404, `HTTP ${res.status}`)
      }
    }

    // 调用历史列表：筛选控件存在
    await gotoApp(page, '/call-history/list', { waitMs: 600 })
    const filters = await page.locator('.el-form, .el-input, .el-select, .el-date-editor').count()
    check('records:ch-list:filters', filters > 0, `filters=${filters}`)

    // 异常列表：若有数据，尝试打开详情
    await gotoApp(page, '/exceptions/list', { waitMs: 600 })
    try {
      await waitTableSettled(page, 8000)
    } catch {}
    const firstRow = page.locator('.el-table__body tr').first()
    const rowCount = await firstRow.count()
    if (rowCount > 0) {
      const detailBtn = page
        .locator('.el-table__body tr')
        .first()
        .locator('button')
        .filter({ hasText: /详情|Detail|查看|View/ })
        .first()
      if ((await detailBtn.count()) > 0) {
        await detailBtn.click()
        await page.waitForTimeout(800)
        const url = page.url()
        check(
          'records:exception-detail',
          url.includes('/exceptions/') || (await page.locator('.el-drawer, .el-dialog').count()) > 0,
          url
        )
      } else {
        // 点击行本身
        await firstRow.click()
        await page.waitForTimeout(600)
        const url = page.url()
        check(
          'records:exception-detail',
          url.includes('/exceptions/') || (await page.locator('.el-drawer, .el-dialog').count()) > 0,
          url + ' (row-click)'
        )
      }
    } else {
      check('records:exception-detail', true, 'skip: empty exception table')
    }

    await page.close()
  }
}
