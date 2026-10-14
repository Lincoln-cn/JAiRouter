#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-error-codes.py 的离线自测（issue #342）

覆盖对账判据的三条要害：
  ① **表格行**里的反引号码 = 权威清单，必须能在源码中找到；
  ② **散文 / 引用块**里提到的码（用于说明「上一版列过但不存在」「只有常量声明」）不参与对账；
  ③ 源码侧同时看「是否存在」（任意码形状字面量）与「会发出」（调用点），
     而**枚举常量名**（未加引号，如 `SecurityEventType.TOKEN_EXPIRED`）不算错误码字面量
     —— 这正是 #342 实测抓到的那类误列。

用法: python scripts/docs/test_check_error_codes.py
"""

import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parent / "check-error-codes.py"


def load_module():
    spec = importlib.util.spec_from_file_location("check_error_codes", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


DOC_SAMPLE = """
## 认证错误码

| 错误码 | 描述 |
|--------|------|
| `INVALID_API_KEY` | 无效的 API Key |
| `JWT_EXPIRED` | 令牌过期 |

> 上一版还列过 `CACHE_ERROR`（**不存在**），以及 `` `INVALID_*` `` 这类前缀写法。
"""

SOURCE_SAMPLE = """
public class Demo {
    public static final String EXPIRED_API_KEY = "EXPIRED_API_KEY";
    SecurityEventType type = SecurityEventType.TOKEN_EXPIRED;
    Mono<?> a() { return Mono.error(ApiException.of("INVALID_REQUEST", "bad")); }
    Mono<?> b() { return Mono.just(RouterResponse.error("x", "TEMPLATE_NOT_FOUND")); }
    String notACode = "JWT_EXPIRED";
}
"""


def main():
    module = load_module()
    failures = []

    def check(name, condition, detail=""):
        print(("✅ " if condition else "❌ ") + name + ("" if condition else f"  → {detail}"))
        if not condition:
            failures.append(name)

    doc_codes = module.extract_doc_codes(DOC_SAMPLE)
    check("表格行里的码被提取", {"INVALID_API_KEY", "JWT_EXPIRED"} <= doc_codes, str(sorted(doc_codes)))
    check("引用块里的码**不**被提取（CACHE_ERROR）", "CACHE_ERROR" not in doc_codes, str(sorted(doc_codes)))
    check("以 _ 结尾的前缀写法不被当成码", not any(c.endswith("_") for c in doc_codes), str(sorted(doc_codes)))

    literals = module.extract_source_literals(SOURCE_SAMPLE)
    check("源码里带引号的码字面量被提取", {"EXPIRED_API_KEY", "INVALID_REQUEST",
                                          "TEMPLATE_NOT_FOUND", "JWT_EXPIRED"} <= literals,
          str(sorted(literals)))
    check("枚举常量名（未加引号）不算码字面量", "TOKEN_EXPIRED" not in literals, str(sorted(literals)))

    emitted = module.extract_source_codes(SOURCE_SAMPLE)
    check("调用点发出的码被提取（含 RouterResponse 第二参）",
          {"INVALID_REQUEST", "TEMPLATE_NOT_FOUND"} <= emitted, str(sorted(emitted)))

    missing, source_only = module.reconcile(doc_codes, literals)
    check("对账：文档列了源码里没有的码 ⇒ 必须被揪出来（INVALID_API_KEY 即样本里的假码）",
          missing == {"INVALID_API_KEY"}, str(sorted(missing)))
    check("对账：源码独有的码被归为 INFO（不判红）",
          source_only == {"EXPIRED_API_KEY", "INVALID_REQUEST", "TEMPLATE_NOT_FOUND"},
          str(sorted(source_only)))

    # 反向对照：把那个假码补进源码后，missing 必须清空（证明上一条不是恒真）
    fixed_missing, _ = module.reconcile(doc_codes, literals | {"INVALID_API_KEY"})
    check("对账：假码在源码里出现后不再报缺（证明断言有检测力）",
          fixed_missing == set(), str(sorted(fixed_missing)))

    print()
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        return 1
    print("✅ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
