import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  menuGroups,
  ROUTE_TO_MENU,
  findMenuEntry,
  findMenuTab,
  isMenuItemView,
  resolveMenuPath
} from '@/config/menu'
import { KNOWN_PERMISSION_CODES } from './knownPermissions'

/**
 * 导航信息架构（P0-3）静态断言
 *
 * 这些断言守的是「配置结构」，不是渲染：Layout.vue 的侧栏、深链高亮、
 * 默认展开组、面包屑都从 `menuGroups` / `ROUTE_TO_MENU` 推导，
 * 一旦结构漂移（多一项、少一组、tab 路由写错、反查表漏项），先在这里红。
 */
describe('isMenuItemView（点菜单是否原地不动）', () => {
  /**
   * 「AI 试验场」有 5 个视图。用户在「图像服务」点侧栏菜单项时，若判定为「不在该项上」，
   * 就会被 resolveLandingPath 打回第一个视图 —— 用户看到的是「点菜单又跳回对话测试」。
   */
  const playground = menuGroups
    .flatMap(group => group.children)
    .find(item => item.tabs.some(tab => tab.to === '/playground/image'))

  it('该项的任一视图都判定为「在本项上」', () => {
    expect(playground).toBeTruthy()
    for (const tab of playground!.tabs) {
      expect(isMenuItemView(playground!, tab.to)).toBe(true)
    }
  })

  it('其它项的路由判定为「不在本项上」', () => {
    expect(isMenuItemView(playground!, '/dashboard/main')).toBe(false)
    expect(isMenuItemView(playground!, '/security/api-keys')).toBe(false)
  })
})

describe('menu.ts 信息架构', () => {
  it('8 组 39 项，组顺序与设计文档一致', () => {
    expect(menuGroups.map(g => g.index)).toEqual([
      'dashboard',
      'observability',
      'traffic',
      'model-services',
      'security',
      'access',
      'platform',
      'dev-tools'
    ])
    expect(menuGroups.map(g => g.children.length)).toEqual([4, 6, 5, 3, 6, 4, 5, 6])
    expect(menuGroups.reduce((total, g) => total + g.children.length, 0)).toBe(39)
  })

  it('每组 ≤8 项（数量预算，防止再次膨胀）', () => {
    for (const group of menuGroups) {
      expect(group.children.length, `${group.index} 超过 8 项`).toBeLessThanOrEqual(8)
    }
  })

  it('每项主路径 = 第一个视图的路由，且视图路由全局唯一', () => {
    const items = menuGroups.flatMap(g => g.children)
    for (const item of items) {
      expect(item.tabs.length).toBeGreaterThan(0)
      expect(item.path).toBe(item.tabs[0].to)
      for (const tab of item.tabs) {
        expect(tab.key).toBeTruthy()
        expect(tab.labelKey).toBeTruthy()
      }
    }
    const routes = items.flatMap(item => item.tabs.map(tab => tab.to))
    // 39 个菜单项承载 53 个页面：同一对象的多视图合并为一项、AI 试验场 5 个工具各占一项、
    // 概览组按角色各占一项，URL 一个都没少（改结构不能丢页面）。
    expect(routes.length).toBe(53)
    expect(items.length).toBe(39)
    expect(new Set(routes).size).toBe(routes.length)
  })

  it('多视图项收敛为「同一对象的视图切换」，单视图项不为合并而合并', () => {
    const items = menuGroups.flatMap(g => g.children)
    const multi = items.filter(item => item.tabs.length > 1)
    const expectedMulti = [
      '/call-history/list',
      '/call-history/slow-calls',
      '/tracing/search',
      '/exceptions/list',
      '/security/audit-logs',
      '/call-history/token-usage',
      '/load-balancers/monitoring',
      '/rate-limiters/monitoring',
      '/circuit-breakers/monitoring',
      '/config/services',
      '/monitoring/config',
      '/config/state-persistence'
    ]
    expect(multi.map(item => item.path).sort()).toEqual(expectedMulti.sort())
    // 单视图项必须真的只有一个页面（防「为了少点一次把不相关页面塞进来」）
    const single = items.filter(item => item.tabs.length === 1)
    expect(single.length).toBe(39 - expectedMulti.length)
    // AI 试验场是 5 个并列工具，不该被当成「一个对象的多个视图」
    expect(items.filter(item => item.path.startsWith('/playground/')).map(item => item.tabs.length)).toEqual([1, 1, 1, 1, 1])
  })

  it('每项都声明 object 与合法 audience；audience 只允许五种取值', () => {
    const allowed = ['admin', 'maintainer', 'user', 'observer', 'auditor']
    for (const item of menuGroups.flatMap(g => g.children)) {
      expect(item.object, item.path).toBeTruthy()
      expect(Array.isArray(item.audience), item.path).toBe(true)
      expect((item.audience ?? []).length, item.path).toBeGreaterThan(0)
      for (const audience of item.audience ?? []) {
        expect(allowed, `${item.path} 的 audience`).toContain(audience)
      }
    }
    // 概览组的四个首页是「角色专属」项，每个只对一个 audience 显示
    // （ADMIN 治理指挥台 / OPERATOR 运营看板 / USER 我的用量 / VIEWER 趋势报表）；
    // 其余每一项都必须对 ADMIN 可见，否则管理员会看不到自己该管的东西。
    const roleHomePaths = ['/dashboard/main', '/dashboard/operations', '/dashboard/mine', '/dashboard/trends']
    for (const item of menuGroups.flatMap(g => g.children)) {
      if (roleHomePaths.includes(item.path)) {
        expect(item.audience, item.path).toHaveLength(1)
        continue
      }
      expect(item.audience, item.path).toContain('admin')
    }
  })

  it('ROUTE_TO_MENU 覆盖每个视图并指回所属菜单项', () => {
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          expect(ROUTE_TO_MENU[tab.to]).toBe(item.path)
        }
      }
    }
    expect(Object.keys(ROUTE_TO_MENU)).toHaveLength(53)
  })

  it('深链未命中回退 route.path；异常详情归到异常；合并页归到所属项', () => {
    expect(resolveMenuPath('/not-in-menu/42')).toBe('/not-in-menu/42')
    // 合并后「配额使用」是「用量」项的第二个视图，深链仍回到该菜单项
    expect(resolveMenuPath('/monitoring/quota')).toBe('/call-history/token-usage')
    expect(resolveMenuPath('/exceptions/detail/abc')).toBe('/exceptions/list')
    expect(findMenuEntry('/exceptions/detail/abc')?.group.index).toBe('observability')
    expect(findMenuTab('/exceptions/statistics')?.key).toBe('exceptions-statistics')
    expect(findMenuEntry('/monitoring/quota')?.item.title).toBe('menu.usage')
  })

  it('53 个页面全部能从菜单反查到所属项（合并后不漏页）', () => {
    const items = menuGroups.flatMap(group => group.children)
    const allTabs = items.flatMap(item => item.tabs.map(tab => tab.to))
    expect(allTabs).toHaveLength(53)
    for (const to of allTabs) {
      expect(findMenuEntry(to), to).toBeTruthy()
      expect(ROUTE_TO_MENU[to], to).toBeTruthy()
    }
  })
})

describe('menu.ts 权限码白名单（#567）', () => {
  const collectPermissions = (): string[] => {
    const codes: string[] = []
    const push = (permission?: string | string[]): void => {
      if (!permission) return
      codes.push(...(Array.isArray(permission) ? permission : [permission]))
    }
    for (const group of menuGroups) {
      for (const item of group.children) {
        push(item.permission)
        for (const tab of item.tabs) push(tab.permission)
      }
    }
    return codes
  }

  it('白名单自身 = 79 个互不重复的权限码', () => {
    expect(KNOWN_PERMISSION_CODES).toHaveLength(79)
    expect(new Set(KNOWN_PERMISSION_CODES).size).toBe(79)
  })

  it('menu.ts 里出现的权限码全部在白名单内', () => {
    const codes = collectPermissions()
    expect(codes.length).toBeGreaterThan(0)
    const unknown = codes.filter(code => !KNOWN_PERMISSION_CODES.includes(code))
    expect(unknown).toEqual([])
  })

  it('读写双挂入口按「读码或写码」放行（#567，多视图项按视图判定）', () => {
    const expected: Array<[string, string, string]> = [
      ['/load-balancers/strategy-config', 'lb:config:read', 'lb:config:write'],
      ['/config/cache', 'config:cache:read', 'config:cache:write'],
      ['/security/api-keys', 'security:apikeys:read', 'security:apikeys:manage'],
      ['/security/sanitization', 'security:sanitization:read', 'security:sanitization:manage'],
      ['/security/jwt-tokens', 'security:jwttokens:read', 'security:jwttokens:manage'],
      ['/security/blacklist', 'security:blacklist:read', 'security:blacklist:manage'],
      ['/system/accounts', 'system:accounts:read', 'system:accounts:manage'],
      ['/system/permissions', 'system:permissions:read', 'system:permissions:manage'],
      ['/tracing/management', 'tracing:config:read', 'tracing:config:manage']
    ]
    const items = menuGroups.flatMap(group => group.children)
    for (const [path, readCode, manageCode] of expected) {
      // 码可能声明在视图上（多视图项）或菜单项上（单视图项），判定时取「视图 ?? 项」
      const item = items.find(candidate => candidate.path === path)
        ?? items.find(candidate => candidate.tabs.some(tab => tab.to === path))
      expect(item, path).toBeTruthy()
      const tab = item?.tabs.find(candidate => candidate.to === path)
      const declared = tab?.permission ?? item?.permission
      expect(declared, path).toEqual(expect.arrayContaining([readCode, manageCode]))
    }
  })
})

describe('menu.ts 角色声明（当前统一交给权限码）', () => {
  it('没有任何菜单项声明 roles', () => {
    const items = menuGroups.flatMap(group => group.children)
    const withRoles = items.filter(item => (item.roles?.length ?? 0) > 0)
    expect(withRoles.map(item => item.path)).toEqual([])
  })

  it('tab 级也没有 roles 残留', () => {
    const tabs = menuGroups.flatMap(group => group.children).flatMap(item => item.tabs)
    const withRoles = tabs.filter(tab => (tab.roles?.length ?? 0) > 0)
    expect(withRoles.map(tab => tab.to)).toEqual([])
  })
})

/**
 * 组图标守卫（回归：模型与实例组曾写 `icon: 'cpu'` 而 Layout 的映射表没有 `cpu` 键，
 * `<component :is="undefined" />` 渲染成空标签，侧栏该组就没有图标）。
 *
 * 这里用源码级读取，因为映射表是 Layout 内部的常量、没有对外导出；
 * 与其为了可测性把它挪出去，不如直接断言「配置里的 icon 都能找到实现」。
 */
describe('menu.ts 组图标', () => {
  const layoutSource = readFileSync(resolve(process.cwd(), 'src/views/Layout.vue'), 'utf-8')

  const iconMapKeys = (): string[] => {
    const start = layoutSource.indexOf('const groupIconMap')
    expect(start, 'Layout.vue 里找不到 groupIconMap').toBeGreaterThan(-1)
    const block = layoutSource.slice(start, layoutSource.indexOf('}', start))
    return [...block.matchAll(/^\s{2}'?([a-zA-Z-]+)'?:\s*[A-Z]/gm)].map(match => match[1])
  }

  it('每个组的 icon 都能在 groupIconMap 里找到实现', () => {
    const keys = iconMapKeys()
    // 自证：解析不到键名时不能静默通过
    expect(keys.length).toBeGreaterThanOrEqual(menuGroups.length)
    const missing = menuGroups
      .filter(group => !keys.includes(group.icon))
      .map(group => `${group.index}:${group.icon}`)
    expect(missing).toEqual([])
  })

  it('groupIconMap 没有拼错残留的键（多余键只允许 user 这类预留）', () => {
    const used = new Set(menuGroups.map(group => group.icon))
    const unused = iconMapKeys().filter(key => !used.has(key))
    expect(unused.every(key => key === 'user')).toBe(true)
  })
})

/**
 * 手册附录 ↔ menu.ts 一致性守卫（#1207）。
 *
 * 背景：`docs/{zh,en}/getting-started/web-console.md` 文末的「页面路径速查表」是人找页面的
 * 第一入口，但它长期是手工维护的 —— 曾漏登记限流配置 tab（`/rate-limiters/config`）、
 * SLA 接入（`/system/sla`）、页面访问热度（`/system/page-heat`）与三个角色的落地页。
 * 表里查不到的页面，读者只能靠猜；而菜单里删掉的页面若还留在表里，则是死链。
 *
 * 判定 = 双向相等：menu.ts 的每条菜单项/tab 路由都要在表里，表里的每条路径都要是真实
 * 菜单路由（少数非菜单路径在 `NON_MENU_PATHS` 里显式登记）。
 */
const WEB_CONSOLE_DOCS = [
  { label: 'zh', file: '../docs/zh/getting-started/web-console.md', anchor: '## 附录：页面路径速查表' },
  { label: 'en', file: '../docs/en/getting-started/web-console.md', anchor: '## Appendix: Page Path Quick Reference' }
]

/**
 * 不是侧栏菜单项的路径：
 * - `/api/**` 是网关 HTTP 入口前缀的说明，不属任何页面；
 * - `/exceptions/detail/:id` 是详情深链，正文已注明「不出现在侧栏」。
 */
const NON_MENU_PATHS = new Set(['/api/**', '/exceptions/detail/:id'])

const menuRouteSet = (): Set<string> =>
  new Set(
    menuGroups.flatMap(group =>
      group.children.flatMap(item => [item.path, ...item.tabs.map(tab => tab.to)])
    )
  )

for (const { label, file, anchor } of WEB_CONSOLE_DOCS) {
  describe(`Web 控制台手册附录（${label}）与 menu.ts 一致（#1207）`, () => {
    const docPaths = (): Set<string> => {
      const source = readFileSync(resolve(process.cwd(), file), 'utf-8')
      const start = source.indexOf(anchor)
      expect(start, `手册里找不到附录标题：${anchor}`).toBeGreaterThan(-1)
      return new Set(
        [...source.slice(start).matchAll(/`(\/[^`]+)`/g)].map(match => match[1])
      )
    }

    it('menu.ts 的每条菜单项与 tab 路由都出现在速查表里', () => {
      const doc = docPaths()
      const missing = [...menuRouteSet()].filter(route => !doc.has(route)).sort()
      expect(missing).toEqual([])
    })

    it('速查表里的路径都是真实菜单路由（非菜单路径已显式登记）', () => {
      const menu = menuRouteSet()
      const extra = [...docPaths()]
        .filter(route => !menu.has(route) && !NON_MENU_PATHS.has(route))
        .sort()
      expect(extra).toEqual([])
    })

    it('限流菜单的监控与配置两个 tab 都在（#1207 回归点）', () => {
      const doc = docPaths()
      expect(doc.has('/rate-limiters/monitoring')).toBe(true)
      expect(doc.has('/rate-limiters/config')).toBe(true)
    })
  })
}
