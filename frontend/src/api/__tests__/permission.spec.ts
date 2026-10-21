import { describe, expect, it } from 'vitest'
import { ALL_PERMISSION_CODES, PERMISSION_GROUPS } from '../permission'
import zh from '@/locales/zh-CN/permissions.json'
import en from '@/locales/en-US/permissions.json'

/**
 * 权限码展示口径守卫。
 *
 * 背景：权限树过去直接渲染权限码（`config:services:read`），且分组表漏了 TA4 新增的
 * read 码（49/63）——用户看不到、也勾不到那些权限。这里把两条都钉住：
 * 1) 分组表必须覆盖后端全量 63 码（数目 + 不重复 + 分组无重复）；
 * 2) 每个码拆出的模块/资源/动作，在中英词表里都必须有词条（否则界面会回退成裸码）。
 */

const ACTIONS = ['read', 'view', 'write', 'manage', 'use']
const EXPECTED_TOTAL = 63

interface SplitCode {
  module: string
  resource: string
  action: string
}

function splitCode(code: string): SplitCode {
  const segments = code.split(':')
  const last = segments[segments.length - 1]
  const action = ACTIONS.includes(last) ? last : ''
  const module = segments[0]
  const resource = action
    ? segments.slice(1, -1).join(':') || module
    : segments.slice(1).join(':') || module
  return { module, resource, action }
}

const localeWordMaps = [
  ['zh-CN', zh.permissions],
  ['en-US', en.permissions]
] as const

describe('permission codes wiring', () => {
  it('covers all 63 backend permission codes exactly once', () => {
    expect(ALL_PERMISSION_CODES).toHaveLength(EXPECTED_TOTAL)
    expect(new Set(ALL_PERMISSION_CODES).size).toBe(EXPECTED_TOTAL)
  })

  it('has no duplicate code across groups', () => {
    const seen = new Set<string>()
    for (const group of PERMISSION_GROUPS) {
      for (const code of group.codes) {
        expect(seen.has(code), `duplicate code ${code}`).toBe(false)
        seen.add(code)
      }
    }
    expect(seen.size).toBe(EXPECTED_TOTAL)
  })

  it('exposes every module, resource and action used by a code', () => {
    for (const [locale, words] of localeWordMaps) {
      for (const code of ALL_PERMISSION_CODES) {
        const { module } = splitCode(code)
        expect(words.modules[module as keyof typeof words.modules], `${locale} module ${module}`).toBeTruthy()

        const { resource, action } = splitCode(code)
        expect(
          words.resources[resource as keyof typeof words.resources],
          `${locale} resource ${resource} (${code})`
        ).toBeTruthy()
        if (action) {
          expect(words.actions[action as keyof typeof words.actions], `${locale} action ${action}`).toBeTruthy()
        }
      }
    }
  })

  it('keeps zh and en word maps in sync', () => {
    for (const section of ['modules', 'resources', 'actions'] as const) {
      const zhKeys = Object.keys(zh.permissions[section]).sort()
      const enKeys = Object.keys(en.permissions[section]).sort()
      expect(zhKeys, `${section} key parity`).toEqual(enKeys)
    }
  })
})
