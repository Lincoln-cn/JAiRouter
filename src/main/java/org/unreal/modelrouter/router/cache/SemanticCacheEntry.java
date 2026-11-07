package org.unreal.modelrouter.router.cache;

/**
 * 语义缓存条目（issue #1002：从 {@link SemanticCacheService} 的内部 record 提升为公共类型，
 * 供 {@link SemanticCacheStore} 的内存 / Redis 两种实现共用）。
 *
 * @param fingerprint 请求文本的 SimHash 指纹
 * @param tokens      归一化文本的 token 数（相似度判定的第二维）
 * @param response    待复用的响应值（String 或 {@link CachedStreamingResponse}）
 * @param expireAt    过期时间戳（epoch ms）
 * @since v3.8.0
 */
public record SemanticCacheEntry(long fingerprint, int tokens, Object response, long expireAt) {
}
