/**
 * 生成客户端唯一 ID。
 *
 * 用 crypto.getRandomValues 而不是 crypto.randomUUID：后者要求安全上下文
 * （https 或 localhost），内网 http 部署下不可用，而 getRandomValues 在非安全上下文同样可用。
 *
 * 刻意不提供 Math.random 兜底：那只会让可预测 ID 以另一种形式回流。
 */
export function uid(prefix = ''): string {
  const bytes = new Uint8Array(8)
  globalThis.crypto.getRandomValues(bytes)
  return prefix + Date.now().toString(36) + Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
}
