import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { KNOWN_PERMISSION_CODES } from './knownPermissions'

/**
 * 前端权限码白名单 ↔ 后端 `PermissionCodes.ALL_PERMISSION_CODES` 的双向一致性
 *
 * 为什么要跨目录读 Java 源：前端的白名单（`./knownPermissions.ts`）是手工维护的，
 * 而后端才是权限码的权威来源（`PermissionCodes.java`）。两边一旦漂移，症状是静默的：
 * 后端删了某个码，前端菜单/路由仍然写着它 ⇒ 该入口永远不显示或永远被拒；后端新增了码，
 * 前端想用却不在白名单里 ⇒ 测试红（这是我们希望看到的，提示同步）。
 *
 * 因此这里直接解析 Java 源文件，断言「前端白名单 === 后端列表」的**集合相等**，
 * 而不是单向包含 —— 少了或多了都要红。
 *
 * 依赖：CI（`frontend-tests.yml`）在仓库根全量检出后以 `working-directory: frontend` 跑测试，
 * 所以仓库根的 `src/main/java/...` 可读。若未来前端被拆成独立仓库，本用例会先红，
 * 那时应当改为「从后端导出权限码清单文件」而不是放宽断言。
 *
 * 路径定位用 `process.cwd()` 而不是 `import.meta.url`：happy-dom 环境下 `import.meta.url`
 * 的 scheme 不是 `file`，`fileURLToPath` 会直接抛「The URL must be of scheme file」。
 */
const frontendRoot = [process.cwd(), resolve(process.cwd(), 'frontend')].find(candidate =>
  existsSync(join(candidate, 'src', 'views'))
)
if (!frontendRoot) {
  throw new Error('无法定位 frontend 根目录：请在 frontend/ 目录下运行测试')
}
const permissionCodesSource = join(
  dirname(frontendRoot),
  'src/main/java/org/unreal/modelrouter/auth/security/permission/PermissionCodes.java'
)

interface ParsedPermissionCodes {
  /** 常量名 → 权限码字面量（`public static final String NAME = "value";`） */
  byName: Map<string, string>
  /** `ALL_PERMISSION_CODES = List.of(...)` 里登记的常量名，按声明顺序 */
  listedNames: string[]
}

function parsePermissionCodes(source: string): ParsedPermissionCodes {
  const byName = new Map<string, string>()
  const declaration = /public static final String ([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"/g
  for (const match of source.matchAll(declaration)) {
    byName.set(match[1], match[2])
  }

  // 必须从「定义处」而不是第一次出现处开始：`ALL_PERMISSION_CODES` 在类 javadoc 里被
  // `{@link #ALL_PERMISSION_CODES}` 引用过，从那里切会把整段注释和代码都当成常量名。
  const listStart = source.indexOf('ALL_PERMISSION_CODES = List.of(')
  expect(listStart, 'PermissionCodes.java 里找不到 ALL_PERMISSION_CODES = List.of(...) 定义').toBeGreaterThan(-1)
  const listEnd = source.indexOf(');', listStart)
  expect(listEnd, 'ALL_PERMISSION_CODES 的 List.of(...) 没有正常闭合').toBeGreaterThan(listStart)
  const listedNames = Array.from(
    source.slice(listStart, listEnd).matchAll(/\b([A-Z][A-Z0-9_]+)\b/g),
    match => match[1]
  ).filter(name => name !== 'ALL_PERMISSION_CODES')

  return { byName, listedNames }
}

describe('前端权限码白名单与后端 PermissionCodes 一致', () => {
  const source = readFileSync(permissionCodesSource, 'utf-8')
  const { byName, listedNames } = parsePermissionCodes(source)

  it('解析结果自证：常量表与 List.of 都解析到了（防正则失效后空转）', () => {
    expect(byName.size).toBeGreaterThanOrEqual(76)
    expect(listedNames.length).toBeGreaterThanOrEqual(76)
    // 锚点：确认正则没有把注释或别的东西算进来
    expect(byName.get('OVERVIEW_DASHBOARD_READ')).toBe('overview:dashboard:read')
    expect(byName.get('CALLHISTORY_READ')).toBe('callhistory:read')
    expect(byName.get('CALLHISTORY_FULL_CONTENT')).toBe('callhistory:full-content')
  })

  it('ALL_PERMISSION_CODES 的每个名字都能在常量表里找到值（无悬空引用）', () => {
    const dangling = listedNames.filter(name => !byName.has(name))
    expect(dangling, 'ALL_PERMISSION_CODES 引用了未定义的常量').toEqual([])
  })

  it('后端权威全集恰好 76 个且无重复', () => {
    const values = listedNames.map(name => byName.get(name)!)
    expect(values).toHaveLength(76)
    expect(new Set(values).size).toBe(76)
  })

  it('前端白名单与后端全集集合相等（少了或多了都会红）', () => {
    const backend = new Set(listedNames.map(name => byName.get(name)!))
    const frontend = new Set(KNOWN_PERMISSION_CODES)

    const missingInFrontend = [...backend].filter(code => !frontend.has(code)).sort()
    const staleInFrontend = [...frontend].filter(code => !backend.has(code)).sort()

    expect(missingInFrontend, '后端有、前端白名单缺（需同步 knownPermissions.ts）').toEqual([])
    expect(staleInFrontend, '前端白名单有、后端已不存在（拼错或已删除）').toEqual([])
  })
})
