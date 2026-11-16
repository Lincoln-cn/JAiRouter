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
  // 角色判定与路由守卫同源（userInfo.roles），fixture 必须一并写入；
  // 只设 store.roles 是无效的 —— store 并未暴露 roles 字段。
  store.userInfo = { username: 'tester', roles, permissions }
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

  it('ADMIN 拥有全部权限 ⇒ 7 组 32 项全在', () => {
    setup([], ['ADMIN'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups).toHaveLength(7)
    expect(groups.reduce((total, group) => total + group.children.length, 0)).toBe(32)
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

/**
 * 菜单可见性必须与路由守卫同口径（死入口回归，2026-11-16）
 *
 * `/security`、`/system` 两个父路由声明了 `meta.roles = ['ADMIN']`，而菜单此前只按权限码
 * 过滤 ⇒ OPERATOR / USER / VIEWER 会看到「点了被重定向回仪表板」的菜单项。
 * 现在菜单项用 `roles` 把同一条约束显式写下来，本组断言守的正是它。
 */
describe('roles 声明：菜单与路由守卫同口径', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('OPERATOR 持有 security:audit:read / config:cluster:read 也看不到「安全」组与 /system 下 5 项', () => {
    setup(
      ['security:audit:read', 'config:cluster:read', 'config:health:read', 'monitoring:console:read', 'config:callhistory:read', 'config:persistence:read'],
      ['OPERATOR']
    )
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    // 安全组 6 项全部落在 /security 父级（roles: ['ADMIN']）下 ⇒ 整个组消失
    expect(groups.find(group => group.index === 'security')).toBeUndefined()
    // 系统组：accounts/permissions/cluster/health/console-operations 被角色拦掉；
    // 追踪配置需要 tracing:config:*（OPERATOR 不持有）；只剩不在 /system 父级下的状态持久化
    const system = groups.find(group => group.index === 'system')
    expect(system?.children.map(item => item.path)).toEqual(['/config/state-persistence'])
  })

  it('USER 持有 config:cluster:read 也看不到「集群设置」（父级要求 ADMIN）', () => {
    setup(['config:cluster:read', 'config:persistence:read'], ['USER'])
    const { filterMenuByPermission } = usePermission()
    const system = filterMenuByPermission(menuGroups).find(group => group.index === 'system')
    expect(system?.children.map(item => item.path)).toEqual(['/config/state-persistence'])
  })

  it('ADMIN 不受 roles 声明影响（安全组 6 项、系统组 7 项都在）', () => {
    setup([], ['ADMIN'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups.find(group => group.index === 'security')?.children).toHaveLength(6)
    expect(groups.find(group => group.index === 'system')?.children).toHaveLength(7)
  })

  it('调用历史（callhistory:view）当前只授 ADMIN：其它角色看不到调用记录页面', () => {
    // 现状口径（后端 RolePermissionSeederTest 已逐角色断言）：OPERATOR 只收 :read/:write、
    // USER 的硬编码清单一律不含、VIEWER 只收 :read ⇒ 三角色都没有 callhistory:view。
    // 若将来把该码发给其它角色，这条断言会红 —— 那时应当同步文档与可见性评审，
    // 而不是直接改断言（调用记录含请求/响应正文）。
    for (const role of ['OPERATOR', 'USER', 'VIEWER']) {
      setup(['overview:dashboard:read', 'config:callhistory:read'], [role])
      const { filterMenuByPermission } = usePermission()
      const tabs = filterMenuByPermission(menuGroups).flatMap(group =>
        group.children.flatMap(item => item.tabs.map(tab => tab.to))
      )
      expect(tabs, role).not.toContain('/call-history/list')
      expect(tabs, role).not.toContain('/call-history/dashboard')
    }
  })
})
