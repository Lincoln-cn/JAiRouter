import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 配置页一致性守卫。
 *
 * <p>背景：配置页原先各自实现保存按钮，出现了「一页 3 个保存按钮共用同一个 loading」
 * （监控配置）、「两个一模一样的保存按钮」（集群设置）、「保存按钮单独占一张卡片」
 * （数据脱敏）这些形态。改造后的口径是：配置页必须用 `ConfigPageShell`，保存入口
 * 只由外壳的动作条提供，页面自身不得再出现保存按钮。</p>
 *
 * <p>这是**源码级**断言（不 mount 组件）：它能拦住「又加了一个保存按钮」这类回退，
 * 但不校验像素。交互行为由 `useConfigDraft.spec.ts` 覆盖。</p>
 */

const frontendRoot = ((): string => {
  const cwd = process.cwd()
  if (existsSync(join(cwd, 'src', 'views'))) {
    return cwd
  }
  return join(cwd, 'frontend')
})()

const viewsRoot = join(frontendRoot, 'src', 'views')

const walk = (dir: string, out: string[] = []): string[] => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      if (name === '__tests__') {
        continue
      }
      walk(full, out)
      continue
    }
    if (name.endsWith('.vue')) {
      out.push(full)
    }
  }
  return out
}

const relative = (full: string): string => full.slice(viewsRoot.length + 1).split('\\').join('/')

const sources = walk(viewsRoot).map((full) => ({ path: relative(full), source: readFileSync(full, 'utf8') }))

const shellPages = sources.filter((page) => /from '@\/components\/config\/ConfigPageShell\.vue'/.test(page.source))

/** 只统计模板部分：`<script>` 里的 `save` 函数名不算保存按钮。 */
const templateOf = (source: string): string => {
  const start = source.indexOf('<template>')
  const end = source.indexOf('</template>')
  if (start < 0 || end < 0) {
    return ''
  }
  return source.slice(start, end)
}

const saveHandlersIn = (source: string): string[] =>
  [...templateOf(source).matchAll(/@click="([^"]+)"@?/g)]
    .map((match) => match[1])
    .filter((handler) => /[Ss]ave/.test(handler))

describe('配置页一致性（ConfigPageShell 口径）', () => {
  it('扫描到的配置页不少于 3 个（防止解析失效后静默通过）', () => {
    expect(sources.length).toBeGreaterThan(40)
    expect(shellPages.length).toBeGreaterThanOrEqual(3)
  })

  it('P0 三页样板都使用统一外壳', () => {
    const paths = shellPages.map((page) => page.path)
    expect(paths).toContain('monitoring/MonitoringConfig.vue')
    expect(paths).toContain('system/ClusterConfig.vue')
    expect(paths).toContain('system/HealthProbeConfig.vue')
  })

  it('使用统一外壳的页面不得自己渲染保存按钮', () => {
    const offenders = shellPages
      .map((page) => ({ path: page.path, handlers: saveHandlersIn(page.source) }))
      .filter((entry) => entry.handlers.length > 0)
    expect(offenders).toEqual([])
  })

  it('使用统一外壳的页面不再直接用 PageSkeleton / el-card（应走 ConfigSectionCard）', () => {
    const offenders = shellPages
      .filter((page) => /<PageSkeleton\b/.test(page.source) || /<el-card\b/.test(page.source))
      .map((page) => page.path)
    expect(offenders).toEqual([])
  })
})
