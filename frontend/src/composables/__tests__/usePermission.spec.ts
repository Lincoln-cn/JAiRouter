import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermission } from '@/composables/usePermission'
import { menuGroups } from '@/config/menu'
import { useUserStore } from '@/stores/user'

/**
 * 菜单权限过滤（P0-3：`permission?: string | string[]`，数组 = 或）
 *
 * 用真实 Pinia + user store 控制 permissions，而不是 mock store：
 * 判定入口仍是 `userStore.hasPermission`（ADMIN 超集 / 权限为空 fail-open 都保持原语义）。
 */
function setup(permissions: string[], roles: string[] = ['USER']) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.permissions = permissions
  store.roles = roles
  return store
}

function findGroup(index: string) {
  const { filterMenuByPermission } = usePermission()
  return filterMenuByPermission(menuGroups).find(group => group.index === index)
}

describe('filterMenuByPermission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('数组权限为「或」：命中任一码即保留菜单项', () => {
    setup(['monitoring:quota:read'])
    const observability = findGroup('observability')
    const usage = observability?.children.find(item => item.path === '/call-history/token-usage')
    expect(usage).toBeTruthy()
    // 同一组内没有命中的项仍被移除
    expect(observability?.children.some(item => item.path === '/call-history/list')).toBe(false)
  })

  it('单码不命中即隐藏；组内全部隐藏时整组消失', () => {
    setup(['overview:dashboard:read'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    // 只剩「概览」与「开发工具」（后者含无权限声明的客户端接入，对所有登录用户可见）
    expect(groups.map(group => group.index)).toEqual(['dashboard', 'dev-tools'])
  })

  it('ADMIN 拥有全部权限 ⇒ 7 组 29 项全在', () => {
    setup([], ['ADMIN'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups).toHaveLength(7)
    expect(groups.reduce((total, group) => total + group.children.length, 0)).toBe(29)
  })

  it('无 permission 声明的菜单项（客户端接入）对所有已登录用户可见', () => {
    setup(['overview:dashboard:read'])
    const devTools = findGroup('dev-tools')
    expect(devTools?.children.map(item => item.path)).toEqual(['/tools/client-access'])
  })
})

describe('resolveLandingPath / getAccessibleTabs', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('落地页 = 第一个当前用户有权限的 tab（跳过无权限的 tab[0]）', () => {
    setup(['monitoring:quota:read'])
    const item = menuGroups
      .find(group => group.index === 'observability')!
      .children.find(child => child.path === '/call-history/token-usage')!
    const { resolveLandingPath, getAccessibleTabs } = usePermission()
    expect(getAccessibleTabs(item).map(tab => tab.key)).toEqual(['quota'])
    expect(resolveLandingPath(item)).toBe('/monitoring/quota')
  })

  it('全部 tab 无权限时回退第一个 tab（不留空导航）', () => {
    setup(['overview:dashboard:read'])
    const item = menuGroups
      .find(group => group.index === 'observability')!
      .children.find(child => child.path === '/call-history/list')!
    const { resolveLandingPath, getAccessibleTabs } = usePermission()
    expect(getAccessibleTabs(item)).toHaveLength(0)
    expect(resolveLandingPath(item)).toBe('/call-history/list')
  })

  it('tab 未声明权限时沿用菜单项权限', () => {
    setup(['ai:playground:use'])
    const item = menuGroups
      .find(group => group.index === 'dev-tools')!
      .children.find(child => child.path === '/playground/chat')!
    const { getAccessibleTabs } = usePermission()
    expect(getAccessibleTabs(item)).toHaveLength(5)
  })
})
