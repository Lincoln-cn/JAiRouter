#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档错误码 ↔ **错误码注册表**双向对账检查器（issue #342 引入，issue #370 阶段 2 换成注册表权威，
issue #419 把对账面从「两张主表」扩到全部参考文档）

## 为什么要它

`docs/{zh,en}/api-reference/error-codes.md` 是客户端**照着写错误处理分支**的表。
#342 记录过：这张表里曾有 **17 个全仓不存在的码**（`INSTANCE_NOT_FOUND` / `CACHE_ERROR` /
`CIRCUIT_BREAKER_OPEN` …），客户端会为永不会出现的码写死分支，而真正会拿到的码反而没被覆盖。
#348 已按代码重写这两张表；本检查器把它变成**可执行判据**。

#419 补的是**覆盖面**：原先只对账两张主表 ⇒ 其它参考文档里的码表与 JSON 示例长期没人管
（`management-api` 的 4 个幽灵码活了很久，`api-reference/index.md` 的小写 `rate_limit_exceeded`
一直没被发现）。现在把它们一并纳入判红，但**「文档漏列注册表码」这条只对两张主表生效**
（否则会要求每篇参考文档都列全 63 个码）。

## 判据（#370 阶段 2 起：权威来源 = 注册表，双向失败）

* **权威清单** = `common/constants/ErrorCode.java`（注册表里登记的就是「会被真正发出的码」）；
* **主表（两张 `error-codes.md`）里表格单元格的反引号码**（`` `CODE` ``）= 文档清单
  ⇒ 必须**恰好等于**注册表：
  * 文档有、注册表没有 ⇒ ❌ **幽灵码**（客户端会照它写死分支）；
  * 注册表有、文档没列 ⇒ ❌ **漏列**（客户端会漏判，这是 #370 正文记录的原始缺口）；
* **其它文档（`docs/{zh,en}/**/*.md`）**，只判「幽灵码」这一侧，取两类位置：
  1. **表头含错误码语义的表格**：表头单元格含 `错误码` / `错误代码` / `Error Code(s)`，
     或**恰好等于** `Code`（`management-api.md` 的 en 侧就是这么写的）；
     表格数据行里的反引号码必须都在注册表里；
  2. **代码围栏内的 JSON/YAML 示例**：`"code": "XXX"` 与 `"errorCode": "XXX"`（含小写写法，
     这正是 #419 点名的那处漂移）的取值必须都在注册表里；
* **非码 token 白名单**（`NON_CODE_TOKENS`）：**按实测**登记的形状占位符 —— 它们出现在「码」的位置上
  但本来就不是码（如响应体模板里的 `"errorCode": "ERROR_CODE"`）。加白名单前必须先跑一遍看真实误报，
  不许预先臆造；
* **散文 / 引用块（blockquote）里提到的码** = 允许（它们常用于说明「上一版列过但不存在」
  「只有常量声明」），一律**不参与**对账；
* 以 `_` 结尾的**前缀模式**（如 `` `INVALID_*` ``）与含通配符的写法不算码。

> 为什么不再以「源码字面量」为权威：字面量只能证明「某个字符串出现过」，无法区分
> 「声明了但从不发出的码」（本仓实测有 7 个：`MISSING_API_KEY` / `INVALID_JWT_TOKEN` /
> `BLACKLISTED_TOKEN` / `INSUFFICIENT_PERMISSIONS` / `ACCESS_DENIED` / `FORBIDDEN_RESOURCE` /
> `RULE_COMPILATION_FAILED`）。注册表把「发出点取证」这件事固化成了一次性结论。

用法:
    python scripts/docs/check-error-codes.py --project-root . --fail-on-error

退出码：0 = 无问题；1 = 有判红项（配合 `--fail-on-error`）或脚本自身出错。
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Set, Tuple

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

# ---------------- 覆盖面扩展（#419） ----------------

# 表格分隔行：`|--------|------|`（用它区分「表头行」与普通数据行）
_TABLE_SEPARATOR = re.compile(r"^\s*\|[\s:\-|]+\|\s*$")
# 表头里的错误码语义（中/英）；en 侧 `management-api.md` 的表头就写 `Code`
_HEADER_ZH = re.compile(r"错误码|错误代码")
_HEADER_EN = re.compile(r"error\s*codes?\b", re.I)
# 代码围栏内的示例字段：`"code": "XXX"` / `"errorCode": "XXX"`（大小写写法都取，见 #419）
_EXAMPLE_CODE_FIELD = re.compile(r'"(?:code|errorCode)"\s*:\s*"([^"]*)"')
_FENCE = "```"
_DOC_ROOTS = ("docs/zh", "docs/en")

# **实测**得到的非码 token 白名单（不是猜的：先按下面的判据跑一遍全仓，再把真误报登记进来）。
# 每一项都写明「是什么、为什么不是码」。
NON_CODE_TOKENS: Dict[str, str] = {
    "ERROR_CODE": "响应体模板里的占位符（`{\"success\": false, \"errorCode\": \"ERROR_CODE\"}`，"
                  "`error-codes.md` 与 `management-api.md` 的中英侧各一处），是形状示意，不是任何路径发出的码",
    # `error_code`（小写写法）曾在 en 侧 `api-reference/index.md` 出现，那条漂移已在 #419 修掉；
    # 2026-10-16 全仓复查（`grep -ri "error_code" docs/**/*.md`）零命中 ⇒ 按「不留中间态」移除白名单项。
}

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


def _table_cells(line: str) -> List[str]:
    """把一行表格拆成单元格文本（去掉加粗与反引号包裹）。"""
    return [c.strip().strip("*").strip().strip("`").strip()
            for c in line.strip().strip("|").split("|")]


def is_error_code_table_header(line: str) -> bool:
    """判断这一行是不是「错误码语义」的表头行。

    两条判据（都要按实测校准，见 #419「按实测补白名单，不要预先臆造」）：
      * 单元格里含 `错误码` / `错误代码` / `Error Code(s)`；
      * 有单元格**恰好等于** `Code`（en 侧 `management-api.md` 的写法）。
    刻意**不**接受 `Permission code` / `Source Code Build` 这类表头 ——
    它们不是错误码表，收进来只会制造误报。
    """
    if not line.strip().startswith("|"):
        return False
    if _HEADER_ZH.search(line) or _HEADER_EN.search(line):
        return True
    return any(cell.lower() == "code" for cell in _table_cells(line))


def extract_table_codes(markdown: str) -> Dict[str, List[int]]:
    """取「错误码语义表格」数据行里的反引号码 ⇒ {码: [行号, …]}。

    表格边界 = 表头行 + 紧随其后的分隔行 + 连续的数据行。
    """
    found: Dict[str, List[int]] = {}
    lines = markdown.splitlines()
    index = 0
    while index < len(lines) - 1:
        line = lines[index]
        if (line.strip().startswith("|")
                and _TABLE_SEPARATOR.match(lines[index + 1] or "")
                and is_error_code_table_header(line)):
            cursor = index + 2
            while cursor < len(lines) and lines[cursor].strip().startswith("|"):
                for match in _DOC_BACKTICK_CODE.finditer(lines[cursor]):
                    code = match.group(1)
                    if _PREFIX_LIKE.search(code):
                        continue
                    found.setdefault(code, []).append(cursor + 1)
                cursor += 1
            index = cursor
            continue
        index += 1
    return found


def extract_example_codes(markdown: str) -> Dict[str, List[int]]:
    """取**代码围栏内**的 `"code"` / `"errorCode"` 取值 ⇒ {取值: [行号, …]}。

    取值不做「形状」过滤：`api-reference/index.md` 那处漂移就是**小写**的
    `"code": "rate_limit_exceeded"`，按形状过滤反而会漏掉它（#419）。
    """
    found: Dict[str, List[int]] = {}
    in_fence = False
    for number, line in enumerate(markdown.splitlines(), start=1):
        if line.strip().startswith(_FENCE):
            in_fence = not in_fence
            continue
        if not in_fence:
            continue
        for match in _EXAMPLE_CODE_FIELD.finditer(line):
            found.setdefault(match.group(1), []).append(number)
    return found


def collect_ghost_problems(path_label: str, markdown: str,
                           registry_codes: Set[str]) -> List[str]:
    """对**单篇**非主表文档判「幽灵码」（只判这一侧，见 #419 的判据设计）。"""
    problems: List[str] = []
    for code, lines in sorted(extract_table_codes(markdown).items()):
        if code in registry_codes or code in NON_CODE_TOKENS:
            continue
        problems.append(f"{path_label}:{lines[0]} 码表里的 `{code}` 不在注册表"
                        f"（同表命中行 {lines}）")
    for value, lines in sorted(extract_example_codes(markdown).items()):
        if value in registry_codes or value in NON_CODE_TOKENS:
            continue
        problems.append(f"{path_label}:{lines[0]} 示例里的 \"code\": \"{value}\" 不在注册表"
                        f"（同取值命中行 {lines}）")
    return problems


def iter_doc_files(root: Path) -> List[Path]:
    """`docs/{zh,en}/**/*.md`（跳过构建产物目录）。"""
    files: List[Path] = []
    for docs_root in _DOC_ROOTS:
        base = root / docs_root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.md")):
            if any(part in {"site", "node_modules", ".git"} for part in path.parts):
                continue
            files.append(path)
    return files


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

    # ---- 判据 ①：两张主表，双向严格对账（这部分判据不许退化） ----
    for doc_rel in DEFAULT_DOCS:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue

        doc_codes = extract_doc_codes(doc_path.read_text(encoding="utf-8", errors="ignore"))
        ghost, missing = reconcile(doc_codes, set(registry))

        print(f"\n📄 {doc_rel}（主表：双向严格对账）")
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

    # ---- 判据 ②：其它文档，只判幽灵码（码表 + 代码围栏里的示例） ----
    docs = iter_doc_files(root)
    if not docs:
        print("❌ docs/{zh,en} 下没有找到任何 markdown（路径或仓库结构已变）")
        return 1
    table_hits = example_hits = 0
    ghost_problems: List[str] = []
    for doc_path in docs:
        rel = doc_path.relative_to(root).as_posix()
        text = doc_path.read_text(encoding="utf-8", errors="ignore")
        table_hits += len(extract_table_codes(text))
        example_hits += len(extract_example_codes(text))
        ghost_problems.extend(collect_ghost_problems(rel, text, set(registry)))

    print(f"\n📚 覆盖面扩展（#419）：扫描 {len(docs)} 篇文档，"
          f"命中码表 token {table_hits} 个、示例码字段 {example_hits} 处")
    print(f"   非码 token 白名单 {len(NON_CODE_TOKENS)} 项（{', '.join(sorted(NON_CODE_TOKENS))}）")
    if ghost_problems:
        print(f"   ❌ 覆盖面上的幽灵码 {len(ghost_problems)} 处:")
        for line in ghost_problems:
            print(f"      - {line}")
        problems += len(ghost_problems)
    else:
        print("   ✅ 覆盖面上没有幽灵码")

    print()
    if problems:
        print(f"❌ 共发现 {problems} 处不一致"
              f"（改码名/改状态请先改注册表，再同步文档）")
        return 1 if args.fail_on_error else 0
    print("✅ 文档错误码与注册表双向一致（含 #419 的覆盖面）")
    return 0


def _entrypoint() -> int:
    try:
        return main()
    except Exception as exc:  # noqa: BLE001 - 脚本自身异常必须非 0 且打印清楚
        print(f"❌ 检查器自身异常: {type(exc).__name__}: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(_entrypoint())
