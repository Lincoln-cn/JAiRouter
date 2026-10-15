import { describe, expect, it } from 'vitest';

/**
 * A/B 探针（**临时文件，验证后立即删除**）：issue #392 的新 workflow 必须能因单测失败而变红。
 * 这里断言一个必然不成立的值 ⇒ 若 job 仍然绿，说明它没有真的跑测试。
 */
describe('CI 探针：新 workflow 的检测力', () => {
  it('必然失败的断言', () => {
    expect(1).toBe(2);
  });
});
