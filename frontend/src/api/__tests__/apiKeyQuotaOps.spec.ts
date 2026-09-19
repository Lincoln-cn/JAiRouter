import { beforeEach, describe, expect, it, vi } from 'vitest'

const put = vi.fn()
const post = vi.fn()

vi.mock('@/utils/request', () => ({
  default: {
    put: (...args: unknown[]) => put(...(args as [])),
    post: (...args: unknown[]) => post(...(args as []))
  }
}))

import { batchResetApiKeyQuota, resetAllApiKeyQuota, updateApiKeyQuota } from '@/api/apiKeyQuotaOps'

describe('apiKeyQuotaOps API', () => {
  beforeEach(() => {
    put.mockReset()
    post.mockReset()
  })

  it('PUT /auth/api-keys/{id}/quota partial update', async () => {
    put.mockResolvedValue({
      data: { data: { keyId: 'k1', dailyRequestLimit: 500, remainingRequests: 470 } }
    })
    const detail = await updateApiKeyQuota('k1', { dailyRequestLimit: 500 })
    expect(put).toHaveBeenCalledWith('/auth/api-keys/k1/quota', { dailyRequestLimit: 500 }, { timeout: 8000 })
    expect(detail.dailyRequestLimit).toBe(500)
  })

  it('POST batch-reset returns counts', async () => {
    post.mockResolvedValue({ data: { data: { requested: 2, reset: 2 } } })
    const r = await batchResetApiKeyQuota(['a', 'b'])
    expect(post).toHaveBeenCalledWith('/auth/api-keys/quota/batch-reset', { keyIds: ['a', 'b'] })
    expect(r.reset).toBe(2)
  })

  it('POST reset-all', async () => {
    post.mockResolvedValue({ data: { data: null } })
    await resetAllApiKeyQuota()
    expect(post).toHaveBeenCalledWith('/auth/api-keys/quota/reset-all')
  })
})
