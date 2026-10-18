import { describe, expect, it } from 'vitest'
import { createApp, defineComponent, h, nextTick, withDirectives } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { isPermissionGranted, permission } from '@/directives/permission'
import { useUserStore } from '@/stores/user'

/**
 * `v-permission` 指令测试（`#468` 方案 A）
 *
 * 两类断言：
 * 1. **纯判定函数** `isPermissionGranted`：空值不限制 / 单码 / 数组 OR / 空数组；
 * 2. **指令行为**：无权限时元素真的从 DOM 里消失，有权限时保留。
 *    用真实 Pinia + user store 控制 `permissions`，而不是 mock 掉 store（否则测不到接线）。
 */
describe('isPermissionGranted', () => {
  const has = (granted: string[]) => (code: string) => granted.includes(code)

  it('空值不限制（与 usePermission().hasPermission() 一致）', () => {
    expect(isPermissionGranted(undefined, has([]))).toBe(true)
    expect(isPermissionGranted(null, has([]))).toBe(true)
    expect(isPermissionGranted('', has([]))).toBe(true)
    expect(isPermissionGranted([], has([]))).toBe(true)
    expect(isPermissionGranted([''], has([]))).toBe(true)
  })

  it('单个权限码：有则 true，无则 false', () => {
    expect(isPermissionGranted('config:adapters:write', has(['config:adapters:write']))).toBe(true)
    expect(isPermissionGranted('config:adapters:write', has(['config:adapters:read']))).toBe(false)
  })

  it('数组是 OR 语义', () => {
    expect(isPermissionGranted(['a:b:c', 'd:e:f'], has(['d:e:f']))).toBe(true)
    expect(isPermissionGranted(['a:b:c', 'd:e:f'], has(['x:y:z']))).toBe(false)
  })
})

describe('v-permission 指令', () => {
  function mountWith(permissions: string[], codes: string | string[] | undefined) {
    // 关键：**同一个 pinia 实例**既要作为活动实例（给 store 赋值），又要装到 app 上，
    // 否则指令里的 useUserStore() 会解析到 app 那份全新 store（permissions 为空 ⇒ 不限制）。
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useUserStore()
    store.permissions = permissions
    store.roles = ['USER']

    const host = document.createElement('div')
    document.body.appendChild(host)

    const Comp = defineComponent({
      render() {
        const btn = h('button', { id: 'guarded' }, '新建')
        const child = codes === undefined ? btn : withDirectives(btn, [[permission, codes]])
        return h('div', [child])
      }
    })

    const app = createApp(Comp)
    app.use(pinia)
    app.mount(host)
    return { host, app, store }
  }

  it('无权限 ⇒ 按钮不渲染', async () => {
    const { host, app } = mountWith(['config:adapters:read'], 'config:adapters:write')
    await nextTick()
    expect(host.querySelector('#guarded')).toBeNull()
    app.unmount()
    host.remove()
  })

  it('有权限 ⇒ 按钮渲染', async () => {
    const { host, app } = mountWith(['config:adapters:write'], 'config:adapters:write')
    await nextTick()
    expect(host.querySelector('#guarded')).not.toBeNull()
    app.unmount()
    host.remove()
  })

  it('不传值 ⇒ 不限制，按钮渲染', async () => {
    const { host, app } = mountWith([], undefined)
    await nextTick()
    expect(host.querySelector('#guarded')).not.toBeNull()
    app.unmount()
    host.remove()
  })
})
