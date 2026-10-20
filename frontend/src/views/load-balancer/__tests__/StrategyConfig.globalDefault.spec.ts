/**
 * issue #575：没有服务级 LB 配置的服务类型（后端返回 404）必须仍然出现在策略表里。
 *
 * 症状：策略配置页只剩 5 行，图像生成 / 图像编辑两类服务整行消失。
 * 根因：`loadServiceConfigs()` 对每个服务类型单独拉配置，任何失败都 `return null` 后被
 * `filter` 丢掉 —— 而「没有专属配置」在后端的语义是「按全局默认」，不是错误。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const requestMock = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({ default: requestMock }))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

interface StrategyConfigVm {
  loadServiceConfigs: () => Promise<void>
  serviceConfigs: Array<Record<string, unknown>>
}

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': {}, 'en-US': {} } })

const elStubs = [
  'el-alert', 'el-button', 'el-card', 'el-col', 'el-descriptions', 'el-descriptions-item',
  'el-dialog', 'el-divider', 'el-empty', 'el-form', 'el-form-item', 'el-icon', 'el-input',
  'el-input-number', 'el-option', 'el-popconfirm', 'el-row', 'el-select', 'el-switch',
  'el-table', 'el-table-column', 'el-tag', 'el-tooltip'
]
const stubs = Object.fromEntries([...elStubs.map(c => [c, true]), ['PageSkeleton', true]])
// v-loading 是 Element Plus 的全局指令，单测未装 EP 插件时需要空实现
const directives = { loading: { mounted() {}, updated() {}, unmounted() {} } }

const mountOptions = { global: { plugins: [i18n], stubs, directives } }

/** 逐 URL 返回不同响应；未列出的 URL 回 { success: false }，避免 onMounted 抛未处理拒绝 */
const routeResponses = (table: Record<string, () => Promise<unknown>>) => {
  requestMock.get.mockImplementation((url: string) => {
    const handler = table[url]
    return handler ? handler() : Promise.resolve({ data: { success: false } })
  })
}

const notFound = () =>
  Promise.reject(Object.assign(new Error('服务配置不存在'), { response: { status: 404 } }))
const serverError = () =>
  Promise.reject(Object.assign(new Error('服务器错误'), { response: { status: 500 } }))

const statusOk = () =>
  Promise.resolve({
    data: { success: true, data: [{ serviceType: 'chat' }, { serviceType: 'imggen' }] }
  })

const globalOk = () =>
  Promise.resolve({
    data: { success: true, data: { type: 'round-robin', hashAlgorithm: 'md5', virtualNodes: 150 } }
  })

const chatOk = () =>
  Promise.resolve({
    data: {
      success: true,
      data: {
        serviceType: 'chat',
        type: 'least-conn',
        hashAlgorithm: 'md5',
        virtualNodes: 150,
        isGlobal: false
      }
    }
  })

const loadPage = async (table: Record<string, () => Promise<unknown>>) => {
  // 先装响应再挂载：onMounted 会并发拉 status / 全局配置 / 服务级配置，
  // 全局配置必须在这轮里成功加载，用例才能验证「兜底行沿用服务端全局值」。
  routeResponses(table)
  const { default: StrategyConfig } = await import('../StrategyConfig.vue')
  const wrapper = mount(StrategyConfig, mountOptions)
  await flushPromises()
  requestMock.get.mockClear()
  return wrapper
}

describe('StrategyConfig 全局默认兜底（issue #575）', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
  })

  it('404 时保留该行并标记为按全局默认', async () => {
    const wrapper = await loadPage({
      '/loadbalancer/status': statusOk,
      '/loadbalancer/config/global': globalOk,
      '/loadbalancer/config/chat': chatOk,
      '/loadbalancer/config/imggen': notFound
    })

    const vm = wrapper.vm as unknown as StrategyConfigVm
    await vm.loadServiceConfigs()

    expect(vm.serviceConfigs).toHaveLength(2)
    const imggen = vm.serviceConfigs.find(c => c.serviceType === 'imggen')
    expect(imggen).toBeDefined()
    expect(imggen?.isGlobal).toBe(true)
    // 兜底值来自服务端全局配置，而不是组件里写死的本地默认值
    expect(imggen?.type).toBe('round-robin')
    expect(imggen?.hashAlgorithm).toBe('md5')
    expect(imggen?.virtualNodes).toBe(150)
    // 有专属配置的服务类型不能被兜底行替换
    expect(vm.serviceConfigs.find(c => c.serviceType === 'chat')?.isGlobal).toBe(false)
  })

  it('500 时仍按原来的语义丢弃该行（只有 404 改道）', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = await loadPage({
      '/loadbalancer/status': statusOk,
      '/loadbalancer/config/global': globalOk,
      '/loadbalancer/config/chat': chatOk,
      '/loadbalancer/config/imggen': serverError
    })

    const vm = wrapper.vm as unknown as StrategyConfigVm
    await vm.loadServiceConfigs()

    expect(vm.serviceConfigs).toHaveLength(1)
    expect(vm.serviceConfigs[0].serviceType).toBe('chat')
    expect(errorSpy).toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})
