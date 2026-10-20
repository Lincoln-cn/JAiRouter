import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusPill from '@/components/StatusPill.vue'

const mountPill = (status: string, tone?: string) =>
  mount(StatusPill, { props: tone === undefined ? { status } : { status, tone } })

const toneClass = (status: string, tone?: string): string => {
  const classes = mountPill(status, tone).classes()
  const matched = classes.find(name => name.startsWith('status-pill--'))
  return matched ?? ''
}

describe('StatusPill', () => {
  it('渲染语义化 status 元素并展示状态文本', () => {
    const wrapper = mountPill('RUNNING')

    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.attributes('role')).toBe('status')
    expect(wrapper.attributes('aria-label')).toBe('RUNNING')
    expect(wrapper.attributes('title')).toBe('RUNNING')
    expect(wrapper.text()).toContain('RUNNING')
  })

  it.each([
    ['RUNNING', 'success'],
    ['healthy', 'success'],
    ['OK', 'success'],
    ['DEGRADED', 'warning'],
    ['slow', 'warning'],
    ['FAILED', 'danger'],
    ['down', 'danger'],
    ['BOOTING', 'neutral']
  ])('按关键词/精确映射推断 %s → %s', (status, expected) => {
    expect(toneClass(status)).toBe(`status-pill--${expected}`)
  })

  it('大写精确映射优先于关键词：INACTIVE 判为 danger 而非 success', () => {
    expect(toneClass('INACTIVE')).toBe('status-pill--danger')
    expect(toneClass('UNHEALTHY')).toBe('status-pill--danger')
    expect(toneClass('HALF_OPEN')).toBe('status-pill--warning')
  })

  it('显式 tone 覆盖关键词推断', () => {
    expect(toneClass('error', 'success')).toBe('status-pill--success')
    expect(toneClass('RUNNING', 'brand')).toBe('status-pill--brand')
    expect(toneClass('queued', 'info')).toBe('status-pill--info')
    expect(toneClass('whatever', 'neutral')).toBe('status-pill--neutral')
  })

  it('未知状态兜底为 neutral', () => {
    expect(toneClass('PENDING_REVIEW')).toBe('status-pill--neutral')
  })
})
