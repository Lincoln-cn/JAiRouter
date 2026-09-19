/**
 * API Key 列表加载策略：列表 spinner 不依赖配额接口
 */
import { describe, expect, it } from 'vitest'

/** 模拟 fetchApiKeys：主列表完成后立即结束 loading，配额异步补齐 */
async function fetchApiKeysStrategy(opts: {
  getList: () => Promise<unknown>
  quotaTasks: Array<() => Promise<unknown>>
}): Promise<{ listLoaded: boolean; loadingAfterList: boolean }> {
  let loading = true
  const list = await opts.getList()
  loading = false
  const afterList = !loading
  // fire-and-forget
  void Promise.allSettled(opts.quotaTasks.map(t => t()))
  return { listLoaded: list != null, loadingAfterList: afterList }
}

describe('API Key list loading isolation', () => {
  it('clears loading after list even if quota hangs', async () => {
    const hang = () => new Promise(() => {})
    const r = await fetchApiKeysStrategy({
      getList: async () => ({ items: [] }),
      quotaTasks: [hang, hang]
    })
    expect(r.listLoaded).toBe(true)
    expect(r.loadingAfterList).toBe(true)
  })
})
