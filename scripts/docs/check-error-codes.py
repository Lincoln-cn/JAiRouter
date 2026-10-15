#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档错误码 ↔ **错误码注册表**双向对账检查器（issue #342 引入，issue #370 阶段 2 换成注册表权威）

## 为什么要它

`docs/{zh,en}/api-reference/error-codes.md` 是客户端**照着写错误处理分支**的表。
#342 记录过：这张表里曾有 **17 个全仓不存在的码**（`INSTANCE_NOT_FOUND` / `CACHE_ERROR` /
`CIRCUIT_BREAKER_OPEN` …），客户端会为永不会出现的码写死分支，而真正会拿到的码反而没被覆盖。
#348 已按代码重写这两张表；本检查器把它变成**可执行判据**。

## 判据（#370 阶段 2 起：权威来源 = 注册表，双向失败）

* **权威清单** = `common/constants/ErrorCode.java`（注册表里登记的就是「会被真正发出的码」）；
* **表格单元格里的反引号码**（`` `CODE` ``）= 文档清单 ⇒ 必须**恰好等于**注册表：
  * 文档有、注册表没有 ⇒ ❌ **幽灵码**（客户端会照它写死分支）；
  * 注册表有、文档没列 ⇒ ❌ **漏列**（客户端会漏判，这是 #370 正文记录的原始缺口）；
* **散文 / 引用块（blockquote）里提到的码** = 允许（它们常用于说明「上一版列过但不存在」
  「只有常量声明」），一律**不参与**对账；
* 以 `_` 结尾的**前缀模式**（如 `` `INVALID_*` ``）与含通配符的写法不算码。

> 为什么不再以「源码字面量」为权威：字面量只能证明「某个字符串出现过」，无法区分
> 「声明了但从不发出的码」（本仓实测有 7 个：`MISSING_API_KEY` / `INVALID_JWT_TOKEN` /
> `BLACKLISTED_TOKEN` / `INSUFFICIENT_PERMISSIONS` / `ACCESS_DENIED` / `FORBIDDEN_RESOURCE` /
> `RULE_COMPILATION_FAILED`）。注册表把「发出点取证」这件事固化成了一次性结论。

用法:
    python scripts/docs/check-error-codes.py --project-root . --fail-on-error
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, Iterable, Set, Tuple

# 码的形状：全大写 + 下划线/数字，至少 3 个字符
_CODE = r"[A-Z][A-Z0-9_]{2,}"

# 文档：**表格行**里的反引号码（首列或任意列都算）
_DOC_TABLE_ROW = re.compile(r"^\s*\|")
_DOC_BACKTICK_CODE = re.compile(r"`(" + _CODE + r")`")

# 形如 INVALID_ 的前缀模式（作者用它表达「任何 INVALID_* 都归此类」）
_PREFIX_LIKE = re.compile(r"_+$")

# 注册表：枚举项 = NAME("CODE", HttpStatus.X | null, RenderPath.Y, "说明")
_REGISTRY_ENTRY = re.compile(
    r'^\s*[A-Z][A-Z0-9_]*\(\s*"([A-Z][A-Z0-9_]+)"\s*,\s*'
    r'(null|HttpStatus\.[A-Z_]+)\s*,\s*RenderPath\.([A-Z_]+)\s*,',
    re.M,
)

DEFAULT_DOCS = (
    "docs/zh/api-reference/error-codes.md",
    "docs/en/api-reference/error-codes.md",
)
DEFAULT_REGISTRY = "src/main/java/org/unreal/modelrouter/common/constants/ErrorCode.java"


def extract_doc_codes(markdown: str) -> Set[str]:
    """从 markdown 里取**表格行**中出现的错误码（散文/引用块不计）。"""
    codes: Set[str] = set()
    for line in markdown.splitlines():
        if not _DOC_TABLE_ROW.match(line):
            continue
        for match in _DOC_BACKTICK_CODE.finditer(line):
            code = match.group(1)
            if _PREFIX_LIKE.search(code):
                continue  # INVALID_ 这类前缀模式不是码
            codes.add(code)
    return codes


def extract_registry_codes(java_text: str) -> Dict[str, str]:
    """从注册表源码里取 {码: 渲染路径}。找不到任何项时返回空字典（由调用方判为致命错误）。"""
    return {m.group(1): m.group(3) for m in _REGISTRY_ENTRY.finditer(java_text)}


def reconcile(doc_codes: Iterable[str], registry_codes: Set[str]) -> Tuple[Set[str], Set[str]]:
    """返回 (文档里有但注册表没有的码, 注册表里有但文档没列的码)。"""
    doc = set(doc_codes)
    return doc - registry_codes, registry_codes - doc


def main() -> int:
    parser = argparse.ArgumentParser(description="文档错误码与错误码注册表对账")
    parser.add_argument("--project-root", default=".", help="项目根目录 (默认: .)")
    parser.add_argument(
        "--fail-on-error", action="store_true", help="发现任何一侧不一致时退出码为 1"
    )
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    registry_path = root / DEFAULT_REGISTRY
    if not registry_path.exists():
        print(f"❌ 找不到错误码注册表: {DEFAULT_REGISTRY}")
        return 1
    registry = extract_registry_codes(registry_path.read_text(encoding="utf-8", errors="ignore"))
    if not registry:
        # 反空转：解析不到任何注册项时必须立刻报错，否则「两侧都空」会静默通过
        print(f"❌ 注册表里解析出 0 个码（解析规则或文件结构已变）: {DEFAULT_REGISTRY}")
        return 1

    by_path: Dict[str, int] = {}
    for path in registry.values():
        by_path[path] = by_path.get(path, 0) + 1
    print(f"🔍 注册表登记的错误码: {len(registry)} 个"
          f"（{' · '.join(f'{k}={v}' for k, v in sorted(by_path.items()))}）")

    problems = 0
    for doc_rel in DEFAULT_DOCS:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue

        doc_codes = extract_doc_codes(doc_path.read_text(encoding="utf-8", errors="ignore"))
        ghost, missing = reconcile(doc_codes, set(registry))

        print(f"\n📄 {doc_rel}")
        print(f"   表中错误码: {len(doc_codes)} / 注册表: {len(registry)}")
        if ghost:
            print(f"   ❌ 文档列了但注册表里没有（发不出的幽灵码）{len(ghost)}:")
            for code in sorted(ghost):
                print(f"      - {code}")
            problems += len(ghost)
        if missing:
            print(f"   ❌ 会被发出但文档没列（客户端会漏判）{len(missing)}:")
            for code in sorted(missing):
                print(f"      - {code}")
            problems += len(missing)
        if not ghost and not missing:
            print("   ✅ 与注册表双向一致")

    print()
    if problems:
        print(f"❌ 双向对账共发现 {problems} 处不一致"
              f"（改码名/改状态请先改注册表，再同步文档）")
        return 1 if args.fail_on_error else 0
    print("✅ 文档错误码与注册表双向一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
