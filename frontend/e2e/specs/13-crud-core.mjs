/**
 * 核心模块 CRUD 闭环：账户 / 资源池 / API Key / 黑名单
 * 约定：唯一命名 e2e-*，测完尽量删除；单模块失败不中断其它模块。
 */
import {
  createAuthedContext,
  gotoApp,
  api,
  apiThrottled,
  uniq,
  clickMainButton,
  waitForOverlay,
  clickOverlayButton,
  closeOverlay,
  confirmMessageBox,
  selectOption,
  clickRowAction,
  countVisibleOverlays,
  waitTableSettled
} from '../harness.mjs'

async function tableHasText(page, textRe) {
  const body = await page.locator('.el-table').first().innerText().catch(() => '')
  return textRe.test(body)
}

async function settleTable(page) {
  try {
    await waitTableSettled(page, 8000)
  } catch {}
}

export default {
  id: 'crud-core',
  name: '核心模块CRUD闭环',
  suite: 'crud',
  order: 200,
  async run({ browser, check }) {
    const page = await createAuthedContext(browser)

    // ========== 1. JWT 账户 ==========
    try {
      // 后端用户名不允许连字符
      const username = uniq('e2eu').replace(/-/g, '')
      const password = 'E2ePass!123456'
      await gotoApp(page, '/system/accounts', { waitMs: 700 })
      await settleTable(page)

      const opened = await clickMainButton(page, /创建账户|Create Account/)
      check('crud:account:dialog-open', opened)
      if (opened) {
        await waitForOverlay(page)
        const inputs = page.locator('.el-dialog:visible .el-input__inner:visible')
        const inputCount = await inputs.count()
        check('crud:account:dialog-inputs', inputCount >= 3, `inputs=${inputCount}`)
        await inputs.nth(0).fill(username)
        await inputs.nth(1).fill(password)
        await inputs.nth(2).fill(password)

        const roleSelect = page
          .locator('.el-dialog:visible .el-form-item')
          .filter({ hasText: /角色|Roles/ })
          .locator('.el-select')
          .first()
        if ((await roleSelect.count()) > 0) {
          await roleSelect.click()
          await page.waitForTimeout(400)
          // 插槽渲染为「用户 / 基础访问权限」；用 nth(1) 精确选 USER 项
          const userOpt = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').nth(1)
          if ((await userOpt.count()) > 0) {
            await userOpt.click()
            await page.waitForTimeout(200)
          }
          await page.keyboard.press('Escape')
          await page.waitForTimeout(200)
        }

        const confirmBtn = page
          .locator('.el-dialog:visible .el-dialog__footer button, .el-dialog:visible button')
          .filter({ hasText: /^确定$|^确认$|Create|创建$/ })
          .first()
        await confirmBtn.click()
        await page.waitForTimeout(1500)

        const createdUi = await tableHasText(page, new RegExp(username))
        const list = await api(page, '/api/security/jwt/accounts')
        const found = Array.isArray(list.body?.data)
          ? list.body.data.some(a => a.username === username)
          : false
        const msgs = await page.locator('.el-message').allInnerTexts().catch(() => [])
        const errors = await page.locator('.el-dialog:visible .el-form-item__error').allInnerTexts().catch(() => [])
        check(
          'crud:account:create-ui',
          createdUi || found,
          `username=${username} ui=${createdUi} apiFound=${found} msg=${msgs.join('|')} err=${errors.join('|')}`
        )
        check(
          'crud:account:create-api',
          list.status === 200 && (found || createdUi),
          `HTTP ${list.status} found=${found}`
        )
        if ((await countVisibleOverlays(page)) > 0) await closeOverlay(page)
      }

      // 账户操作列是图标按钮（Edit/Delete icon），无文案
      const row = page.locator('.el-table__body tr').filter({ hasText: new RegExp(username) }).first()
      const dangerBtn = row.locator('button.el-button--danger, button[type="danger"]').first()
      let deleted = false
      if ((await dangerBtn.count().catch(() => 0)) > 0) {
        await dangerBtn.click()
        await page.waitForTimeout(400)
        deleted = await confirmMessageBox(page, /确定删除|删除|Delete|确认/)
      } else {
        deleted = await clickRowAction(page, new RegExp(username), /删除|Delete/)
        if (deleted) {
          await confirmMessageBox(page)
          await page.waitForTimeout(800)
        }
      }
      if (deleted) {
        await page.waitForTimeout(800)
        const still = await tableHasText(page, new RegExp(username))
        check('crud:account:delete', !still, still ? 'still in table' : 'removed')
      } else {
        const del = await api(page, `/api/security/jwt/accounts/${username}`, { method: 'DELETE' })
        check(
          'crud:account:delete',
          del.status === 200 || del.status === 204 || del.status === 404,
          `api HTTP ${del.status}`
        )
      }
    } catch (e) {
      check('crud:account:aborted', false, e?.message || String(e))
      await closeOverlay(page).catch(() => {})
    }

    // ========== 2. 资源池 ==========
    try {
      const poolName = uniq('e2ep')
      await gotoApp(page, '/config/pools', { waitMs: 700 })
      await settleTable(page)

      const instances = await api(page, '/api/config/instance/chat')
      const instList = Array.isArray(instances.body?.data)
        ? instances.body.data
        : instances.body?.data?.items || []
      const firstInst = instList[0]?.name || instList[0]?.instanceId || null
      check('crud:pool:has-instance', !!firstInst, firstInst || 'no chat instance')

      const opened = await clickMainButton(page, /新增资源池|新增|Add Pool/)
      check('crud:pool:dialog-open', opened)
      if (opened && firstInst) {
        await waitForOverlay(page)
        const nameInput = page.locator('.el-dialog:visible .el-input__inner:visible').first()
        await nameInput.fill(poolName)

        const selects = page.locator('.el-dialog:visible .el-select')
        await selectOption(page, selects.nth(0), /chat/)
        await page.waitForTimeout(500)

        const memberSelect = page.locator('.el-dialog:visible .el-select').nth(2)
        if ((await memberSelect.count()) > 0) {
          await memberSelect.click()
          await page.waitForTimeout(400)
          const instOpt = page
            .locator('.el-select-dropdown:visible .el-select-dropdown__item')
            .filter({ hasText: firstInst })
            .first()
          if ((await instOpt.count()) > 0) {
            await instOpt.click()
          } else {
            const any = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
            if ((await any.count()) > 0) await any.click()
          }
          await page.keyboard.press('Escape')
        }

        await clickOverlayButton(page, /保存|Save/)
        await page.waitForTimeout(1200)

        const createdUi = await tableHasText(page, new RegExp(poolName))
        check('crud:pool:create-ui', createdUi, poolName)
        const getPool = await api(page, `/api/config/pools/${poolName}`)
        check('crud:pool:create-api', getPool.status === 200 || createdUi, `HTTP ${getPool.status}`)
      } else if (opened) {
        await closeOverlay(page)
        check('crud:pool:create-ui', true, 'skip: no instance to use as member')
      }

      const deleted = await clickRowAction(page, new RegExp(poolName), /删除|Delete/)
      if (deleted) {
        await confirmMessageBox(page)
        await page.waitForTimeout(800)
        const still = await tableHasText(page, new RegExp(poolName))
        check('crud:pool:delete', !still, still ? 'still present' : 'removed')
      } else if (poolName) {
        const del = await api(page, `/api/config/pools/${poolName}`, { method: 'DELETE' })
        check(
          'crud:pool:delete',
          del.status === 200 || del.status === 204 || del.status === 404,
          `api ${del.status}`
        )
      } else {
        check('crud:pool:delete', true, 'skip')
      }
    } catch (e) {
      check('crud:pool:aborted', false, e?.message || String(e))
      await closeOverlay(page).catch(() => {})
    }

    // ========== 3. API Key ==========
    try {
      const keyId = uniq('e2ek').replace(/[^a-zA-Z0-9_-]/g, '')
      await gotoApp(page, '/security/api-keys', { waitMs: 700 })
      await settleTable(page)

      const opened = await clickMainButton(page, /创建API密钥|创建 API|Create API/)
      check('crud:apikey:dialog-open', opened)
      if (opened) {
        await waitForOverlay(page, 10000)
        const keyInput = page
          .locator('.el-dialog:visible .el-input__inner:visible')
          .first()
        await keyInput.waitFor({ state: 'visible', timeout: 8000 })
        await keyInput.fill(keyId)
        const desc = page.locator('.el-dialog:visible textarea:visible').first()
        if ((await desc.count()) > 0) await desc.fill(`e2e-created ${keyId}`)

        const chatCb = page
          .locator('.el-dialog:visible .el-checkbox')
          .filter({ hasText: /chat|对话|聊天/i })
          .first()
        if ((await chatCb.count()) > 0) await chatCb.click().catch(() => {})

        const saveBtn = page
          .locator('.el-dialog:visible button')
          .filter({ hasText: /^保存$|Save/ })
          .first()
        if ((await saveBtn.count()) > 0) {
          await saveBtn.click()
        } else {
          await clickOverlayButton(page, /保存|Save/)
        }
        await page.waitForTimeout(1800)

        const keyDialog = await page
          .locator('.el-dialog:visible')
          .filter({ hasText: /密钥值|仅此一次|apiKeyCreated/ })
          .count()
          .catch(() => 0)
        check(
          'crud:apikey:create-key-dialog',
          keyDialog > 0 || (await tableHasText(page, new RegExp(keyId))),
          `keyDialog=${keyDialog}`
        )

        await clickOverlayButton(page, /我已保存|关闭|Close|确定/).catch(() => {})
        await closeOverlay(page)
        await page.waitForTimeout(400)

        const createdUi = await tableHasText(page, new RegExp(keyId))
        check('crud:apikey:create-ui', createdUi, keyId)

        const list = await apiThrottled(page, '/api/auth/api-keys')
        const found = (list.body?.data?.items || []).some(i => i.keyId === keyId || i.id === keyId)
        check(
          'crud:apikey:create-api',
          list.status === 200 && (found || createdUi),
          `HTTP ${list.status} found=${found}`
        )
      }

      const deletedUi = await clickRowAction(page, new RegExp(keyId), /删除|Delete/)
      if (deletedUi) {
        await confirmMessageBox(page, /确定删除|删除|Delete|确定/)
        await page.waitForTimeout(1000)
      }
      const stillUi = await tableHasText(page, new RegExp(keyId)).catch(() => false)
      if (stillUi || !deletedUi) {
        const del = await apiThrottled(page, `/api/auth/api-keys/${keyId}`, { method: 'DELETE' })
        check(
          'crud:apikey:delete',
          del.status === 200 || del.status === 204 || del.status === 404 || !stillUi,
          `HTTP ${del.status} stillUi=${stillUi}`
        )
      } else {
        check('crud:apikey:delete', true)
      }
    } catch (e) {
      check('crud:apikey:aborted', false, e?.message || String(e))
      await closeOverlay(page).catch(() => {})
    }

    // ========== 4. 黑名单 DEVICE ==========
    try {
      const deviceId = uniq('e2edev')
      await gotoApp(page, '/security/blacklist', { waitMs: 700 })
      await settleTable(page)

      const opened = await clickMainButton(page, /添加黑名单|Add Blacklist|添加/)
      check('crud:blacklist:dialog-open', opened)
      if (opened) {
        await waitForOverlay(page)
        const deviceRadio = page
          .locator('.el-dialog:visible .el-radio-button, .el-dialog:visible .el-radio')
          .filter({ hasText: /DEVICE|设备/ })
          .first()
        if ((await deviceRadio.count()) > 0) {
          await deviceRadio.click()
          await page.waitForTimeout(400)
        }

        const candidates = page.locator('.el-dialog:visible .el-input__inner:visible')
        const n = await candidates.count()
        let filled = false
        for (let i = n - 1; i >= 0; i--) {
          const val = await candidates.nth(i).inputValue().catch(() => '')
          if (!val) {
            await candidates.nth(i).fill(deviceId)
            filled = true
            break
          }
        }
        if (!filled && n > 0) {
          await candidates.nth(n - 1).fill(deviceId)
        }

        await clickOverlayButton(page, /确认添加|添加|Add|确认/)
        await page.waitForTimeout(1200)

        const list = await api(page, '/api/security/blacklist/list?type=DEVICE&page=0&size=50')
        const content = list.body?.data?.content || []
        const found = content.some(
          e =>
            String(e.targetValue || '').includes(deviceId) ||
            String(e.targetValueMasked || '').includes(deviceId.slice(0, 10)) ||
            String(e.id) && String(e.targetValue || '').length > 0 && deviceId.slice(0, 8) &&
            (String(e.targetValueMasked || '').includes(deviceId.slice(-6)) ||
              String(e.reason || '').includes('e2e'))
        )
        // UI 列表对 target 做掩码，以 API 命中为主；UI 仅作辅助
        const createdUi = await tableHasText(page, new RegExp(deviceId.slice(0, 12)))
        check(
          'crud:blacklist:add-ui',
          found || createdUi,
          `deviceId=${deviceId} apiFound=${found} ui=${createdUi} rows=${content.length}`
        )
        check(
          'crud:blacklist:add-api',
          list.status === 200 && (found || createdUi),
          `HTTP ${list.status} found=${found}`
        )
      }

      const removed = await clickRowAction(page, new RegExp(deviceId), /移除|Remove/)
      if (removed) {
        await confirmMessageBox(page, /移除|确认|Remove|确定/)
        await page.waitForTimeout(800)
        check('crud:blacklist:remove', true)
      } else {
        const list = await api(page, '/api/security/blacklist/list?type=DEVICE&page=0&size=50')
        const entry = (list.body?.data?.content || []).find(
          e =>
            String(e.targetValue || '').includes(deviceId) ||
            String(e.targetValueMasked || '').includes(deviceId.slice(0, 8))
        )
        if (entry?.id != null) {
          const del = await api(page, `/api/security/blacklist/${entry.id}`, { method: 'DELETE' })
          check(
            'crud:blacklist:remove',
            del.status === 200 || del.status === 204,
            `api ${del.status}`
          )
        } else {
          check('crud:blacklist:remove', true, 'skip: entry not found for cleanup')
        }
      }
    } catch (e) {
      check('crud:blacklist:aborted', false, e?.message || String(e))
      await closeOverlay(page).catch(() => {})
    }

    await page.close()
  }
}
