import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '@/router'
import { useUserStore } from '@/stores/user'

/**
 * 路由守卫的权限码门控（从 Layout.spec 独立出来）。
 *
 * 这几个断言只关心「守卫放行/重定向」，不需要挂 Layout。放在 `views/__tests__` 里会与
 * 前面的 mount 用例共享 router 单例，懒加载组件未落定时偶发「导航被中止 ⇒ 停在旧路由」，
 * 造成与产品行为无关的假红（实际踩到过）。独立成 spec 后每次都是干净的单例状态。
 */
const asUser = (permissions: string[], roles: string[] = ['OPERATOR']): void => {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.token = 'test-token'
  store.permissions = permissions
  store.userInfo = { username: 'op', roles, permissions }
}

describe('路由守卫：权限码门控', () => {
  it('有码即可直达 /system/cluster', async () => {
    asUser(['overview:dashboard:read', 'config:cluster:read', 'config:persistence:read'])
    // 从无权限码要求的页面出发：同路径 push 会被 vue-router 当成重复导航跳过
    await router.push('/tools/client-access')
    await router.push('/system/cluster')
    expect(router.currentRoute.value.path).toBe('/system/cluster')
  }, 20000)

  it('没有 security:apikeys:read 时被挡回本角色首页（OPERATOR → 运营看板）', async () => {
    // 五个角色各有首页：概览组里 OPERATOR 只看到「运营看板」，因此被拒绝后落在这里，
    // 而不是旧行为的 /dashboard/main（那是 ADMIN 的看板）。
    asUser(['overview:dashboard:read', 'config:cluster:read'])
    await router.push('/tools/client-access')
    await router.push('/security/api-keys')
    expect(router.currentRoute.value.path).toBe('/dashboard/operations')
  }, 20000)

  it('USER 直接访问治理指挥台会被挡回「我的用量」', async () => {
    asUser(['overview:dashboard:read', 'callhistory:self:read'], ['USER'])
    await router.push('/tools/client-access')
    await router.push('/dashboard/main')
    expect(router.currentRoute.value.path).toBe('/dashboard/mine')
  }, 20000)

  it('AUDITOR 没有概览位，落到审计日志而不是仪表板', async () => {
    asUser(['overview:dashboard:read', 'security:audit:read', 'monitoring:console:read'], ['AUDITOR'])
    await router.push('/tools/client-access')
    await router.push('/dashboard/main')
    expect(router.currentRoute.value.path).toBe('/security/audit-logs')
  }, 20000)

  it('连仪表板权限都没有时放行落地页，而不是无限重定向', async () => {
    asUser(['config:cluster:read'])
    await router.push('/tools/client-access')
    await router.push('/system/cluster')
    expect(router.currentRoute.value.path).toBe('/system/cluster')
  }, 20000)
})
