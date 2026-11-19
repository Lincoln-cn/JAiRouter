import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'

/**
 * `--ja-primary` 别名退役守卫（P4）。
 *
 * 背景：`--ja-primary` 曾是页面与组件直接消费的品牌色，unslop 收口后它的唯一合法用途是
 * 供 Element Plus 镜像（`--el-color-primary*`）读取。页面继续用它会让「换强调色要改两处」
 * 的老问题回来 —— 30 处调用已迁到 `--ja-accent`，本用例防止它们再爬回来。
 *
 * 只扫描 `src/` 下的 vue/ts/css，且放行 `styles/tokens.css`（别名定义处）。
 */
const SRC = join(process.cwd(), 'src')
const ALLOWED = new Set([join('styles', 'tokens.css')])
const EXTENSIONS = ['.vue', '.ts', '.css']

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') {
        continue
      }
      walk(full, out)
    } else if (EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      out.push(full)
    }
  }
  return out
}

describe('--ja-primary 别名退役', () => {
  it('页面与组件不得再直接引用 var(--ja-primary)', () => {
    const offenders: string[] = []
    for (const file of walk(SRC)) {
      const rel = relative(SRC, file).split(sep).join('/')
      if (ALLOWED.has(rel.split('/').join(sep))) {
        continue
      }
      const source = readFileSync(file, 'utf-8')
      if (source.includes('var(--ja-primary)')) {
        offenders.push(rel)
      }
    }
    expect(offenders, `请改用 var(--ja-accent)：${offenders.join(', ')}`).toEqual([])
  })

  it('tokens.css 仍保留别名定义（Element 镜像依赖它）', () => {
    const tokens = readFileSync(join(SRC, 'styles', 'tokens.css'), 'utf-8')
    expect(tokens).toContain('--ja-primary: var(--ja-accent)')
    expect(tokens).toContain('--ja-primary-light-3')
  })
})
