#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档错误码 ↔ 源码对账检查器（issue #342）

## 为什么要它

`docs/{zh,en}/api-reference/error-codes.md` 是客户端**照着写错误处理分支**的表。
#342 记录过：这张表里曾有 **17 个全仓不存在的码**（`INSTANCE_NOT_FOUND` / `CACHE_ERROR` /
`CIRCUIT_BREAKER_OPEN` …），客户端会为永不会出现的码写死分支，而真正会拿到的码反而没被覆盖。
#348 已按代码重写这两张表；本检查器把它变成**可执行判据**，防止再次漂移。

## 判据

* **表格单元格里的反引号码**（`` `CODE` ``）= 权威清单 ⇒ 必须在 `src/main/java` 里存在；
* **散文 / 引用块（blockquote）里提到的码** = 允许（它们常用于说明「上一版列过但不存在」「只有常量声明」），
  一律**不参与**对账；
* 以 `_` 结尾的**前缀模式**（如 `` `INVALID_*` ``）与含通配符的写法不算码；
* 源码里存在但文档没列的码 ⇒ 输出为 **INFO**（不判红）—— 那是「文档是否够全」的问题，
  与「文档列了假码」不是同一类错误。

用法:
    python scripts/docs/check-error-codes.py --fail-on-error
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Iterable, Set, Tuple

# 码的形状：全大写 + 下划线/数字，至少 3 个字符
_CODE = r"[A-Z][A-Z0-9_]{2,}"

# 文档：**表格行**里的反引号码（首列或任意列都算）
_DOC_TABLE_ROW = re.compile(r"^\s*\|")
_DOC_BACKTICK_CODE = re.compile(r"`(" + _CODE + r")`")

# 源码：真实发出错误码的写法（用于 INFO：「会发出但文档没列」）
_SOURCE_PATTERNS = (
    re.compile(r'ApiException\.of\(\s*"(' + _CODE + r')"'),
    re.compile(r'ApiException\.resolveStatus\(\s*"(' + _CODE + r')"'),
    re.compile(r'RouterResponse\.error\([^;]*?"(' + _CODE + r')"\s*[,)]'),
)

# 源码：**任何**形状像错误码的字符串字面量。
# 判据依据 #342 自己的取证方法：本仓的错误码就是字符串字面量（无混淆、无拼接），
# 而码可能以常量声明（如 AuthenticationException.INVALID_API_KEY = "INVALID_API_KEY"）
# 或经常量引用发出 ⇒ 只扫调用点会大量误报。
_SOURCE_LITERAL = re.compile(r'"(' + _CODE + r')"')

# 形如 INVALID_ 的前缀模式（作者用它表达「任何 INVALID_* 都归此类」）
_PREFIX_LIKE = re.compile(r"_+$")

DEFAULT_DOCS = (
    "docs/zh/api-reference/error-codes.md",
    "docs/en/api-reference/error-codes.md",
)
DEFAULT_SOURCE_ROOT = "src/main/java"


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


def extract_source_codes(source: str) -> Set[str]:
    """从 Java 源码文本里取会**真实发出**的错误码字面量（按调用点，窄口径）。"""
    codes: Set[str] = set()
    for pattern in _SOURCE_PATTERNS:
        for match in pattern.finditer(source):
            codes.add(match.group(1))
    return codes


def extract_source_literals(source: str) -> Set[str]:
    """从 Java 源码文本里取**所有**形状像错误码的字符串字面量（宽口径，用于「是否存在」判定）。"""
    return {match.group(1) for match in _SOURCE_LITERAL.finditer(source)}


def scan_source(root: Path) -> Tuple[Set[str], Set[str]]:
    """扫描目录下全部 .java 文件，返回 (全部码形状字面量, 调用点发出的码)。"""
    literals: Set[str] = set()
    emitted: Set[str] = set()
    for java_file in root.rglob("*.java"):
        try:
            text = java_file.read_text(encoding="utf-8", errors="ignore")
        except OSError as exc:  # 读不了的单个文件不应让整轮失败
            print(f"⚠️  跳过无法读取的文件 {java_file}: {exc}")
            continue
        literals |= extract_source_literals(text)
        emitted |= extract_source_codes(text)
    return literals, emitted


def reconcile(doc_codes: Iterable[str], source_codes: Set[str]) -> Tuple[Set[str], Set[str]]:
    """返回 (文档里有但源码里没有的码, 源码里有但文档没列的码)。"""
    doc = set(doc_codes)
    return doc - source_codes, source_codes - doc


def main() -> int:
    parser = argparse.ArgumentParser(description="文档错误码与源码对账")
    parser.add_argument("--project-root", default=".", help="项目根目录 (默认: .)")
    parser.add_argument(
        "--fail-on-error", action="store_true", help="发现「文档列了不存在的码」时退出码为 1"
    )
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    source_literals, source_emitted = scan_source(root / DEFAULT_SOURCE_ROOT)
    print(f"🔍 源码里形状像错误码的字面量: {len(source_literals)} 个；"
          f"其中调用点会发出的: {len(source_emitted)} 个（{DEFAULT_SOURCE_ROOT}）")

    missing_total = 0
    for doc_rel in DEFAULT_DOCS:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue

        doc_codes = extract_doc_codes(doc_path.read_text(encoding="utf-8", errors="ignore"))
        missing = doc_codes - source_literals
        undocumented = source_emitted - doc_codes

        print(f"\n📄 {doc_rel}")
        print(f"   表中错误码: {len(doc_codes)}")
        print(f"   ✅ 能在源码中找到: {len(doc_codes) - len(missing)}")
        if missing:
            print(f"   ❌ 源码里不存在的码 ({len(missing)}):")
            for code in sorted(missing):
                print(f"      - {code}")
            missing_total += len(missing)
        if undocumented:
            print(f"   ℹ️  会发出但文档未列 ({len(undocumented)}): {', '.join(sorted(undocumented))}")

    print()
    if missing_total:
        print(f"❌ 文档里共 {missing_total} 个错误码在全仓源码中不存在"
              f"（客户端会照它写死分支）：请按代码改写文档，或确认该码确实会被发出。")
        return 1 if args.fail_on_error else 0
    print("✅ 文档错误码与源码一致（表格里的每个码都能在源码中找到）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
