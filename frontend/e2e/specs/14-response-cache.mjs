/**
 * 响应缓存管理页（/config/cache）
 *
 * 为什么单独建这一条：审计 #466 查出该页**此前没有任何 E2E 覆盖**（specs/ 下 grep
 * `response-cache` 零命中），而它上面有两个批量失效操作 —— 也正因为没覆盖，
 * 「这两处缺二次确认」（#460）一直没被发现。
 *
 * ⚠️ 前置条件：页面上两个失效按钮都带 `:disabled="… || !status?.enabled"`，所以
 * **响应缓存必须是开启态**才点得到。启动方式：
 *
 *   java -jar target/model-router-*.jar --spring.profiles.active=dev \
 *        --jairouter.response-cache.enabled=true
 *
 * 缓存关闭时本 spec 会明确报 `skip`（而不是静默通过），因为"没测到"≠"没问题"。
 */
import { createAuthedContext, gotoApp, api } from '../harness.mjs'

export default {
  id: 'response-cache',
  name: '响应缓存管理',
  suite: 'config',
  order: 145,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    await gotoApp(page, '/config/cache', { waitMs: 700 })
    await page
      .locator('.page-skeleton')
      .first()
      .waitFor({ state: 'hidden', timeout: 10000 })
      .catch(() => {})

    const body = await page.locator('.layout-main').innerText().catch(() => '')
    check('cache:page:content', /响应缓存|缓存|Cache/i.test(body), body.slice(0, 100).replace(/\n/g, ' '))

    // 后端状态：enabled 决定两个失效按钮是否可点
    const status = await api(page, '/api/config/cache/response')
    const enabled = !!(status.body?.data?.enabled ?? status.body?.enabled)
    check('cache:status:reachable', status.status === 200, `HTTP ${status.status} enabled=${enabled}`)

    if (!enabled) {
      // 不静默通过：明确说明为什么没测
      check(
        'cache:invalidate:confirm-skip',
        true,
        'skip: response cache disabled —— 需以 --jairouter.response-cache.enabled=true 起服务才能验证'
      )
      await page.close()
      return
    }

    // 记录写请求，用于断言「取消后不得发出 DELETE」
    const sent = []
    const onReq = (req) => {
      if (req.method() === 'DELETE' && req.url().includes('/config/cache/response')) sent.push(req.url())
    }
    page.on('request', onReq)

    // ── 按服务类型失效 ────────────────────────────────────────────────
    const select = page.locator('.layout-main .el-select').first()
    if ((await select.count()) > 0) {
      await select.click()
      await page.waitForTimeout(400)
      const firstOpt = page.locator('.el-select-dropdown__item').first()
      if ((await firstOpt.count()) > 0) {
        await firstOpt.click()
        await page.waitForTimeout(300)
      }
    }

    const byServiceBtn = page.locator('.layout-main button').filter({ hasText: /按服务失效|Invalidate.*Service/i }).first()
    if ((await byServiceBtn.count()) === 0) {
      check('cache:invalidate-service:confirm-dialog', false, 'skip: button not found')
    } else if (!(await byServiceBtn.isEnabled().catch(() => false))) {
      check('cache:invalidate-service:confirm-dialog', false, 'skip: button disabled (no service type selected)')
    } else {
      const before = sent.length
      await byServiceBtn.scrollIntoViewIfNeeded().catch(() => {})
      await byServiceBtn.click({ timeout: 10000 })
      const box = page.locator('.el-message-box').first()
      const appeared = await box
        .waitFor({ state: 'visible', timeout: 5000 })
        .then(() => true)
        .catch(() => false)
      check('cache:invalidate-service:confirm-dialog', appeared, appeared ? '.el-message-box visible' : 'no dialog appeared')

      if (appeared) {
        const cancel = box.locator('button').filter({ hasText: /取消|Cancel/i }).first()
        if ((await cancel.count()) > 0) {
          await cancel.click()
        } else {
          await box.locator('button').first().click()
        }
        await page.waitForTimeout(600)
      }
      // 无条件断言：没弹层时这条会红，而不是被跳过
      check('cache:invalidate-service:cancel-sends-nothing', sent.length === before, `DELETE requests=${sent.length - before}`)
    }

    // ── 按模型失效 ───────────────────────────────────────────────────
    const modelInput = page.getByPlaceholder(/输入模型名称|Enter a model name/i).first()
    if ((await modelInput.count()) > 0) {
      await modelInput.fill('audit-e2e-model').catch(() => {})
      await page.waitForTimeout(200)
    }

    const byModelBtn = page.locator('.layout-main button').filter({ hasText: /按模型失效|Invalidate.*Model/i }).first()
    if ((await byModelBtn.count()) === 0) {
      check('cache:invalidate-model:confirm-dialog', false, 'skip: button not found')
    } else if (!(await byModelBtn.isEnabled().catch(() => false))) {
      check('cache:invalidate-model:confirm-dialog', false, 'skip: button disabled (no model name)')
    } else {
      const before = sent.length
      await byModelBtn.scrollIntoViewIfNeeded().catch(() => {})
      await byModelBtn.click({ timeout: 10000 })
      const box = page.locator('.el-message-box').first()
      const appeared = await box
        .waitFor({ state: 'visible', timeout: 5000 })
        .then(() => true)
        .catch(() => false)
      check('cache:invalidate-model:confirm-dialog', appeared, appeared ? '.el-message-box visible' : 'no dialog appeared')

      if (appeared) {
        const cancel = box.locator('button').filter({ hasText: /取消|Cancel/i }).first()
        if ((await cancel.count()) > 0) {
          await cancel.click()
        } else {
          await box.locator('button').first().click()
        }
        await page.waitForTimeout(600)
      }
      check('cache:invalidate-model:cancel-sends-nothing', sent.length === before, `DELETE requests=${sent.length - before}`)
    }

    page.off('request', onReq)
    await page.close()
  }
}
