import { describe, expect, it, vi } from 'vitest'

/**
 * 回归：API 密钥管理页的权限标签曾把 `imggen` / `imgedit` 原样显示出来
 * （后端早期把小写值入库，前端按驼峰 i18n key 查不到，于是回退成原始串）。
 */
const messages: Record<string, string> = {
  'apiKeys.permissions.chat': '聊天模型',
  'apiKeys.permissions.embedding': '嵌入模型',
  'apiKeys.permissions.rerank': '重排序模型',
  'apiKeys.permissions.tts': '语音合成',
  'apiKeys.permissions.stt': '语音识别',
  'apiKeys.permissions.imgGen': '图像生成',
  'apiKeys.permissions.imgEdit': '图像编辑'
}

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, fallback?: string) => messages[key] ?? fallback ?? key
  })
}))

const { useApiKeyDisplay } = await import('../useApiKeyDisplay')

describe('useApiKeyDisplay.formatPermission', () => {
  const { formatPermission } = useApiKeyDisplay()

  it('驼峰服务类型正常翻译', () => {
    expect(formatPermission('imgGen')).toBe('图像生成')
    expect(formatPermission('imgEdit')).toBe('图像编辑')
    expect(formatPermission('chat')).toBe('聊天模型')
  })

  it('库里的小写 imgGen/imgEdit 也能翻译（本轮修复点）', () => {
    expect(formatPermission('imggen')).toBe('图像生成')
    expect(formatPermission('imgedit')).toBe('图像编辑')
  })

  it('前后空白不影响翻译', () => {
    expect(formatPermission(' imggen ')).toBe('图像生成')
  })

  it('未知权限值原样返回，不抛异常', () => {
    expect(formatPermission('legacy-write')).toBe('legacy-write')
  })
})

describe('useApiKeyDisplay.getPermissionTagType', () => {
  const { getPermissionTagType } = useApiKeyDisplay()

  it('图像相关的两种写法都拿到 danger，而不是回退 info', () => {
    expect(getPermissionTagType('imgGen')).toBe('danger')
    expect(getPermissionTagType('imggen')).toBe('danger')
    expect(getPermissionTagType('imgEdit')).toBe('danger')
    expect(getPermissionTagType('imgedit')).toBe('danger')
  })

  it('其它服务类型保持既有配色', () => {
    expect(getPermissionTagType('chat')).toBe('primary')
    expect(getPermissionTagType('embedding')).toBe('success')
    expect(getPermissionTagType('rerank')).toBe('warning')
    expect(getPermissionTagType('tts')).toBe('info')
  })
})
