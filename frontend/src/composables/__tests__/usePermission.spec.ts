import { describe, expect, it } from 'vitest'
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
  // 必须在 setActivePinia 之后调用 composable（内部 useUserStore() 立即求值）
  const permission = usePermission()
  return { store, ...permission }
}

describe('usePermission 菜单过滤', () => {
  it('数组权限为「或」：命中任一码即保留菜单项（多视图项按视图聚合）', () => {
    // 负载均衡项的视图是「监控（lb:monitoring:read）」与「策略（lb:config:read|write）」
    const { filterMenuByPermission, getAccessibleTabs } = setup(['lb:config:write'])
    const groups = filterMenuByPermission(menuGroups)
    const traffic = groups.find(group => group.index === 'traffic')
    const item = traffic?.children.find(candidate => candidate.path === '/load-balancers/monitoring')
    expect(item).toBeTruthy()
    expect(getAccessibleTabs(item!).map(tab => tab.to)).toEqual(['/load-balancers/strategy-config'])
  })

  it('单码不命中即隐藏；组内全部隐藏时整组消失', () => {
    const { filterMenuByPermission } = setup(['overview:dashboard:read'])
    const groups = filterMenuByPermission(menuGroups)
    expect(groups.map(group => group.index)).toEqual(['dashboard', 'dev-tools'])
  })

  it('ADMIN 拥有全部权限且命中全部 audience ⇒ 8 组 31 项全在', () => {
    const { filterMenuByPermission } = setup([], ['ADMIN'])
    const groups = filterMenuByPermission(menuGroups)
    expect(groups).toHaveLength(8)
    expect(groups.reduce((total, group) => total + group.children.length, 0)).toBe(31)
  })

  it('无 permission 声明的菜单项（客户端接入）对所有已登录用户可见', () => {
    const { filterMenuByPermission } = setup(['overview:dashboard:read'])
    const groups = filterMenuByPermission(menuGroups)
    const devTools = groups.find(group => group.index === 'dev-tools')
    expect(devTools?.children.map(item => item.path)).toEqual(['/tools/client-access'])
  })

  it('单视图菜单项的落地页就是它自己', () => {
    const { filterMenuByPermission, resolveLandingPath } = setup(
      ['config:cache:read', 'config:cache:write'],
      ['OPERATOR']
    )
    const groups = filterMenuByPermission(menuGroups)
    const item = groups
      .flatMap(group => group.children)
      .find(candidate => candidate.path === '/config/cache')
    expect(item).toBeTruthy()
    expect(resolveLandingPath(item!)).toBe('/config/cache')
  })

  it('无权限的菜单项被整项过滤；resolveLandingPath 仍回退第一个视图（不留空导航）', () => {
    const { getAccessibleTabs, resolveLandingPath } = setup(['overview:dashboard:read'])
    const callItem = menuGroups
      .flatMap(group => group.children)
      .find(item => item.path === '/call-history/list')
    expect(callItem).toBeTruthy()
    expect(getAccessibleTabs(callItem!)).toHaveLength(0)
    expect(resolveLandingPath(callItem!)).toBe('/call-history/list')
  })

  it('视图未声明权限时沿用菜单项权限；声明了不同码时按视图各自判定', () => {
    const { filterMenuByPermission, getAccessibleTabs } = setup(
      ['monitoring:config:read', 'config:services:read'],
      ['OPERATOR']
    )
    const items = filterMenuByPermission(menuGroups).flatMap(group => group.children)

    // 监控：两个视图都没单独声明码 ⇒ 都继承 item 的 monitoring:config:read
    const monitoring = items.find(candidate => candidate.path === '/monitoring/config')
    expect(monitoring).toBeTruthy()
    expect(getAccessibleTabs(monitoring!).map(tab => tab.to)).toEqual([
      '/monitoring/config',
      '/monitoring/config/runtime'
    ])

    // 服务：版本视图单独声明了 config:versions:read ⇒ 只有服务视图可见
    const services = items.find(candidate => candidate.path === '/config/services')
    expect(services).toBeTruthy()
    expect(getAccessibleTabs(services!).map(tab => tab.to)).toEqual(['/config/services'])
  })

  it('熔断三项合并后：只持 cb:history:read 时整项可见，落地到历史视图', () => {
    const { filterMenuByPermission, getAccessibleTabs, resolveLandingPath } = setup([
      'cb:history:read'
    ])
    const item = filterMenuByPermission(menuGroups)
      .flatMap(group => group.children)
      .find(candidate => candidate.path === '/circuit-breakers/monitoring')
    expect(item).toBeTruthy()
    expect(getAccessibleTabs(item!).map(tab => tab.to)).toEqual(['/circuit-breakers/history'])
    expect(resolveLandingPath(item!)).toBe('/circuit-breakers/history')
  })

  it('一个视图都进不去时菜单项整体隐藏（避免「菜单可点、点进去被守卫弹回」）', () => {
    const { filterMenuByPermission } = setup(['rl:monitoring:read'])
    const item = filterMenuByPermission(menuGroups)
      .flatMap(group => group.children)
      .find(candidate => candidate.path === '/circuit-breakers/monitoring')
    expect(item).toBeUndefined()
  })
})

describe('usePermission audience 收窄（角色决定可见面）', () => {
  const pathsOf = (roles: string[], permissions: string[]) => {
    const { filterMenuByPermission } = setup(permissions, roles)
    return filterMenuByPermission(menuGroups).flatMap(group =>
      group.children.map(item => item.path)
    )
  }

  it('USER 的可见面收敛到「自己的数据 + 试验场」，看不到配置与平台设置', () => {
    const paths = pathsOf(['USER'], [
      'overview:dashboard:read',
      'callhistory:read',
      'monitoring:exceptions:read',
      'monitoring:tokenusage:read',
      'monitoring:quota:read',
      'ai:playground:use',
      // 即使持有配置码，也不该因 audience 而看到管理面
      'config:cluster:read',
      'config:cache:read'
    ])
    expect(paths).toContain('/dashboard/main')
    expect(paths).toContain('/call-history/list')
    expect(paths).toContain('/playground/chat')
    expect(paths).not.toContain('/system/cluster')
    expect(paths).not.toContain('/config/cache')
    expect(paths).not.toContain('/config/services')
    expect(paths.length).toBeLessThanOrEqual(8)
  })

  it('VIEWER 看到趋势与审计，但看不到配置与模型管理', () => {
    const paths = pathsOf(['VIEWER'], [
      'overview:dashboard:read',
      'callhistory:read',
      'monitoring:slowquery:read',
      'tracing:dashboard:read',
      'tracing:search:read',
      'monitoring:exceptions:read',
      'security:audit:read',
      'monitoring:console:read',
      'monitoring:tokenusage:read',
      'monitoring:quota:read',
      'config:services:read'
    ])
    expect(paths).toContain('/security/audit-logs')
    expect(paths).toContain('/tracing/search')
    expect(paths).not.toContain('/config/services')
    expect(paths).not.toContain('/config/state-persistence')
    expect(paths.length).toBeLessThanOrEqual(8)
  })

  it('AUDITOR 只看到仪表板、审计与接入指南三项', () => {
    const paths = pathsOf(['AUDITOR'], [
      'overview:dashboard:read',
      'security:audit:read',
      'monitoring:console:read'
    ])
    expect(paths.sort()).toEqual(
      ['/dashboard/main', '/security/audit-logs', '/tools/client-access'].sort()
    )
  })

  it('OPERATOR 看得到运维面，但看不到「访问与身份」组', () => {
    const paths = pathsOf(['OPERATOR'], [
      'overview:dashboard:read',
      'config:services:read',
      'config:rules:read',
      'monitoring:metrics:read',
      'security:audit:read',
      'monitoring:console:read',
      'tracing:config:read',
      'config:cache:read'
    ])
    expect(paths).toContain('/config/services')
    expect(paths).toContain('/security/audit-logs')
    expect(paths).not.toContain('/system/accounts')
    expect(paths).not.toContain('/system/permissions')
    expect(paths).not.toContain('/system/sla')
  })

  it('未知角色不映射任何 audience ⇒ 菜单为空（宁少给：新角色上线需先登记映射）', () => {
    // 这是刻意的安全默认：宁可让新角色看不到任何菜单，也不要默认授予可见面。
    const paths = pathsOf(['SOME_NEW_ROLE'], ['overview:dashboard:read'])
    expect(paths).toEqual([])
  })
})
