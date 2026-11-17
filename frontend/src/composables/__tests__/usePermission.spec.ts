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

  it('ADMIN 拥有全部权限 ⇒ 7 组 35 项全在', () => {
    setup([], ['ADMIN'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups).toHaveLength(7)
    expect(groups.reduce((total, group) => total + group.children.length, 0)).toBe(35)
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

  it('只要有一个 tab 可进，菜单项就显示，且落地页落在可进的那个 tab', () => {
    // 只有熔断历史码的用户：monitoring / global-config 两个 tab 都进不去，
    // 但菜单项必须仍然可见，且点击后要直接落到 history（不能落到首个 tab）
    setup(['cb:history:read'])
    const { filterMenuByPermission, resolveLandingPath } = usePermission()
    const item = filterMenuByPermission(menuGroups)
      .find(group => group.index === 'traffic')!
      .children.find(child => child.path === '/circuit-breakers/monitoring')!
    expect(item, '有一个可进 tab 就应当可见').toBeDefined()
    expect(resolveLandingPath(item)).toBe('/circuit-breakers/history')
  })

  it('一个 tab 都进不去时菜单项整体隐藏（避免「菜单可点、点进去被守卫弹回」）', () => {
    // 只持限流监控码：负载均衡两项都不满足（monitoring 要 lb:monitoring:read，
    // strategy 要 config:read 或 config:write），故负载均衡项整项消失；
    // 同组内真正可进的限流项仍保留，组不会因此整体隐藏
    setup(['rl:monitoring:read'])
    const { filterMenuByPermission } = usePermission()
    const traffic = filterMenuByPermission(menuGroups).find(group => group.index === 'traffic')
    const paths = traffic?.children.map(item => item.path) ?? []
    expect(paths).not.toContain('/load-balancers/monitoring')
    expect(paths).toContain('/rate-limiters/monitoring')
  })
})

/**
 * 菜单可见性只由权限码决定（2026-11-16 第五轮）
 *
 * 第四轮的做法是给 `/security`、`/system` 下的菜单项补 `roles: ['ADMIN']`，
 * 与当时的父路由 `meta.roles` 对齐。但四角色运行态冒烟发现两个问题：
 * 1. 后端本来就允许 OPERATOR / VIEWER 调 `/api/security/audit/extended/**`（它们持有
 *    `security:audit:read`），前端却把入口藏起来 ⇒ 有权限也看不到，属功能不可达；
 * 2. 同一条授权边界写两遍（父路由 roles + 菜单 roles），任一处改动都可能漂移。
 *
 * 第五轮撤掉父级 `meta.roles` 与菜单 `roles`，让权限码成为唯一门控。本组断言守的是
 * 「同一套权限码在菜单与后端得到同一种可见性」。
 */
describe('菜单可见性：权限码是唯一门控', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('OPERATOR 持审计/集群/健康/操作记录/监控配置码时，安全与系统组只显示有码的项', () => {
    setup(
      ['security:audit:read', 'config:cluster:read', 'config:health:read', 'monitoring:console:read', 'config:callhistory:read', 'config:persistence:read', 'monitoring:config:read', 'monitoring:config:write'],
      ['OPERATOR']
    )
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    // 安全组：API Key / PII 脱敏 / JWT 令牌 / 黑名单 需要各自的 read 码（OPERATOR 不持有）
    const security = groups.find(group => group.index === 'security')
    expect(security?.children.map(item => item.path)).toEqual([
      '/security/audit-logs',
      '/security/record-governance'
    ])
    // 系统组：accounts/permissions 需要 system:* 码；追踪配置需要 tracing:config:* ⇒ 都不显示；
    // 「监控配置」与「状态持久化」都是运行时配置面，OPERATOR 持相应写码/读码即可见。
    const system = groups.find(group => group.index === 'system')
    expect(system?.children.map(item => item.path)).toEqual([
      '/monitoring/config',
      '/config/state-persistence',
      '/system/cluster',
      '/system/health',
      '/system/console-operations'
    ])
  })

  it('USER 只有 config:cluster:read 时只多看到「集群设置」，安全组整体不出现', () => {
    setup(['config:cluster:read', 'config:persistence:read'], ['USER'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups.find(group => group.index === 'security')).toBeUndefined()
    const system = groups.find(group => group.index === 'system')
    expect(system?.children.map(item => item.path)).toEqual([
      '/config/state-persistence',
      '/system/cluster'
    ])
  })

  it('VIEWER 持 security:audit:read 时看得到「审计日志」（与后端接口一致）', () => {
    setup(['security:audit:read'], ['VIEWER'])
    const { filterMenuByPermission } = usePermission()
    const security = filterMenuByPermission(menuGroups).find(group => group.index === 'security')
    expect(security?.children.map(item => item.path)).toEqual(['/security/audit-logs'])
  })

  it('ADMIN 不受影响（安全组 7 项、系统组 9 项都在）', () => {
    setup([], ['ADMIN'])
    const { filterMenuByPermission } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    expect(groups.find(group => group.index === 'security')?.children).toHaveLength(7)
    expect(groups.find(group => group.index === 'system')?.children).toHaveLength(9)
  })

  it('调用历史拆码：callhistory:read 给运维（可见），USER/VIEWER 不可见', () => {
    // 后端 RolePermissionSeederTest 逐角色钉住的口径：
    // OPERATOR 由「所有 :read」自动获得 callhistory:read；VIEWER 显式排除；USER 的硬编码清单不含。
    // 前端只跟 read 走 —— callhistory:full-content 是详情页的正文闸门，不进菜单。
    const visibleTabs = () => {
      const { filterMenuByPermission } = usePermission()
      return filterMenuByPermission(menuGroups).flatMap(group =>
        group.children.flatMap(item => item.tabs.map(tab => tab.to))
      )
    }

    setup(['overview:dashboard:read', 'callhistory:read'], ['OPERATOR'])
    expect(visibleTabs()).toContain('/call-history/list')
    expect(visibleTabs()).toContain('/call-history/dashboard')

    for (const role of ['USER', 'VIEWER']) {
      setup(['overview:dashboard:read', 'config:callhistory:read'], [role])
      expect(visibleTabs(), role).not.toContain('/call-history/list')
      expect(visibleTabs(), role).not.toContain('/call-history/dashboard')
    }
  })

  it('只持 callhistory:full-content 不带 read 时看不到调用历史（前缀规则要求 read）', () => {
    // 拆码后的不变式：full-content 是「锦上添花」的正文闸门，不能替代 read。
    // 若哪天前端把菜单改成读 full-content，这条会红 —— 那是权限面收缩，需评审。
    setup(['overview:dashboard:read', 'callhistory:full-content'], ['OPERATOR'])
    const { filterMenuByPermission } = usePermission()
    const tabs = filterMenuByPermission(menuGroups).flatMap(group =>
      group.children.flatMap(item => item.tabs.map(tab => tab.to))
    )
    expect(tabs).not.toContain('/call-history/list')
    expect(tabs).not.toContain('/call-history/dashboard')
  })
})
