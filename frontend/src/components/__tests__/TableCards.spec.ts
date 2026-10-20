import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import TableCards from '@/components/TableCards.vue'

interface TestRow {
  id: number
  name: string
  status: string
}

const rows: TestRow[] = [
  { id: 1, name: 'alpha', status: 'ok' },
  { id: 2, name: 'beta', status: 'failed' },
  { id: 3, name: 'gamma', status: 'ok' }
]

// EmptyState 只在传入 icon 时渲染 el-icon；本 spec 不传 icon，
// 但编译器仍会解析该组件，stub 掉可让测试输出聚焦真实断言。
const global = { stubs: { 'el-icon': true } }

function renderSlot(params: { row: TestRow }): ReturnType<typeof h> {
  return h('div', { class: 'card-row' }, [
    h('span', { class: 'card-row__name' }, params.row.name),
    h('span', { class: 'card-row__status' }, params.row.status)
  ])
}

describe('TableCards', () => {
  it('按 rows 渲染 N 张卡片，并为容器与卡片提供列表语义', () => {
    const wrapper = mount(TableCards, {
      global,
      props: { rows, rowKey: 'id' },
      slots: { default: renderSlot }
    })

    expect(wrapper.attributes('role')).toBe('list')
    expect(wrapper.findAll('[role="listitem"]')).toHaveLength(rows.length)
    expect(wrapper.findAll('.card-row__name').map((node) => node.text())).toEqual([
      'alpha',
      'beta',
      'gamma'
    ])
    expect(wrapper.find('.empty-state').exists()).toBe(false)
  })

  it('rowKey 支持函数形式，并对每一行调用', () => {
    const rowKey = vi.fn((row: TestRow) => `row-${row.id}`)

    const wrapper = mount(TableCards, {
      global,
      props: { rows, rowKey },
      slots: { default: renderSlot }
    })

    expect(rowKey).toHaveBeenCalledTimes(rows.length)
    expect(rowKey).toHaveBeenNthCalledWith(1, rows[0])
    expect(wrapper.findAll('[role="listitem"]')).toHaveLength(rows.length)
  })

  it('空数组渲染 EmptyState，而不是零张卡片的空容器', () => {
    const wrapper = mount(TableCards, {
      global,
      props: {
        rows: [],
        rowKey: 'id',
        emptyTitle: '暂无数据',
        emptyDescription: '调整筛选条件后重试'
      },
      slots: { default: renderSlot }
    })

    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).toContain('调整筛选条件后重试')
    expect(wrapper.findAll('[role="listitem"]')).toHaveLength(0)
  })

  it('empty 为 true 时即使 rows 非空也只渲染空态', () => {
    const wrapper = mount(TableCards, {
      global,
      props: { rows, rowKey: 'id', empty: true, emptyTitle: '暂无数据' },
      slots: { default: renderSlot }
    })

    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.findAll('[role="listitem"]')).toHaveLength(0)
  })

  it('作用域插槽拿到正确的 row 与 index', () => {
    const wrapper = mount(TableCards, {
      global,
      props: { rows, rowKey: 'id' },
      slots: {
        default: (params: { row: TestRow; index: number }) =>
          h('div', { class: 'slot-probe' }, `${params.index}:${params.row.id}:${params.row.name}`)
      }
    })

    expect(wrapper.findAll('.slot-probe').map((node) => node.text())).toEqual([
      '0:1:alpha',
      '1:2:beta',
      '2:3:gamma'
    ])
  })
})
