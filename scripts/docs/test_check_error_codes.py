#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-error-codes.py 的离线自测（issue #342 引入；#370 阶段 2 换成注册表权威后重写）

覆盖对账判据的四条要害：
  ① **表格行**里的反引号码 = 文档清单，**散文 / 引用块**里提到的码不参与对账；
  ② 权威清单来自**错误码注册表**（`ErrorCode.java`），解析出 0 项必须致命（防「两侧都空」静默通过）；
  ③ 对账是**双向**的：文档列了注册表没有的码 ⇒ 幽灵码；注册表有而文档没列 ⇒ 漏列；
  ④ 两条方向都要有**反证**（把码补上 / 抹掉后结论必须翻转），证明断言不是恒真。

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

REGISTRY_SAMPLE = """
public enum ErrorCode {
    INVALID_API_KEY("INVALID_API_KEY", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "无效的 API Key"),
    JWT_EXPIRED("JWT_EXPIRED", HttpStatus.UNAUTHORIZED, RenderPath.AUTHENTICATION, "JWT 令牌已过期"),
    BUILTIN_READONLY("BUILTIN_READONLY", HttpStatus.BAD_REQUEST, RenderPath.RESPONSE_BODY, "内置项只读"),
    DOWNSTREAM_SERVICE_ERROR("DOWNSTREAM_SERVICE_ERROR", null, RenderPath.ERROR_BODY, "下游服务异常"),
    ;
    // 注释里的示例不算：FAKE_CODE("FAKE_CODE", HttpStatus.OK, RenderPath.API_EXCEPTION, "x"),
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

    registry = module.extract_registry_codes(REGISTRY_SAMPLE)
    check("注册表里的码与渲染路径被提取",
          registry == {"INVALID_API_KEY": "AUTHENTICATION", "JWT_EXPIRED": "AUTHENTICATION",
                       "BUILTIN_READONLY": "RESPONSE_BODY", "DOWNSTREAM_SERVICE_ERROR": "ERROR_BODY"},
          str(registry))
    check("解析不到注册项时返回空（调用方据此判致命，防「两侧都空」静默通过）",
          module.extract_registry_codes("public enum ErrorCode { }") == {})
    check("源码注释里的示例码不被当成注册项", "FAKE_CODE" not in registry, str(sorted(registry)))

    ghost, missing = module.reconcile(doc_codes, set(registry))
    # 文档有 JWT_EXPIRED ✓、INVALID_API_KEY ✓；注册表里的 BUILTIN_READONLY / DOWNSTREAM_SERVICE_ERROR 文档没列
    check("对账：文档列了注册表没有的码 ⇒ 幽灵码（CACHE_ERROR 只在引用块里，故这里为空）",
          ghost == set(), str(sorted(ghost)))
    check("对账：注册表有而文档没列 ⇒ 漏列",
          missing == {"BUILTIN_READONLY", "DOWNSTREAM_SERVICE_ERROR"}, str(sorted(missing)))

    # 反证 ①：把一个真幽灵码放进文档表格 ⇒ ghost 必须非空
    ghost_doc = module.extract_doc_codes(DOC_SAMPLE + "\n| `CACHE_ERROR` | 只在表里 |")
    ghost2, _ = module.reconcile(ghost_doc, set(registry))
    check("反证：文档表格里出现假码 ⇒ 幽灵码被揪出（证明上一条不是恒真）",
          ghost2 == {"CACHE_ERROR"}, str(sorted(ghost2)))

    # 反证 ②：把漏列的码补进文档 ⇒ missing 必须清空
    filled_doc = doc_codes | {"BUILTIN_READONLY", "DOWNSTREAM_SERVICE_ERROR"}
    _, missing2 = module.reconcile(filled_doc, set(registry))
    check("反证：把漏列码补进文档 ⇒ 漏列清空（证明该断言有检测力）",
          missing2 == set(), str(sorted(missing2)))

    # 反证 ③：把注册表里的码删掉、文档保留 ⇒ 立刻变成幽灵码
    ghost3, _ = module.reconcile(doc_codes, {"INVALID_API_KEY"})
    check("反证：注册表少了 JWT_EXPIRED ⇒ 文档侧报幽灵码（双向都咬得住）",
          ghost3 == {"JWT_EXPIRED"}, str(sorted(ghost3)))

    print()
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        return 1
    print("✅ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
