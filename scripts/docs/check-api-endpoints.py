#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档端点 ↔ 控制器映射对账检查器（issue #250 批次 8；形态照 check-error-codes.py）

## 为什么要它

`docs/{zh,en}/api-reference/*.md` 用**逐条路径**描述管理面端点（`management-api.md` 一篇就有 166 个）。
这类文档最容易出的问题是「文档列了控制器里不存在的路径」——读者照着调会 404，而本仓历史上
（`#339` / `#350` / `#374`）反复出现过同类漂移。人工核 166 条不可持续 ⇒ 做成门闩。

## 判据

* **文档侧**：从 `docs/{zh,en}/api-reference/*.md` 里抽「反引号包裹的、以 `/api/` 开头的路径」；
* **代码侧**：从全部 `*Controller.java` 重建完整路径 = **类级 `@RequestMapping` 前缀 + 方法级映射**，
  并把 `{xxx}` 路径变量统一归一为 `{}`；
* **判据**：文档里每条路径都必须在代码侧找到（允许「文档路径是代码路径的**前缀**」这一形态 ——
  文档常把同一前缀下的多个方法合并成一行描述）；
* 通配写法（`/api/**`）、`/actuator/**`、纯说明性路径不参与对账。

用法:
    python scripts/docs/check-api-endpoints.py --fail-on-error
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Iterable, Set, Tuple

# ---------------- 代码侧 ----------------

# 类级/方法级映射：@XxxMapping / @XxxMapping("path") / @XxxMapping(value = "path")
# 注意：注解括号**可省**（`@GetMapping` 单独一行很常见），必须一并匹配。
_MAPPING_ANNOTATION = re.compile(
    r"@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b\s*"
    r"(?:\(([^)]*)\))?",
)
_QUOTED_PATH = re.compile(r'"([^"]*)"')
_METHOD_LEVEL = {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"}


def _merge_paths(prefix: str, path: str) -> str:
    if not path:
        merged = prefix or "/"
    elif path.startswith("/"):
        merged = (prefix.rstrip("/") + path) if prefix else path
    else:
        merged = (prefix.rstrip("/") + "/" + path) if prefix else "/" + path
    # 类级前缀可能写成 `@RequestMapping("api/auth/api-keys")`（无前导斜杠）⇒ 统一补上
    return merged if merged.startswith("/") else "/" + merged


def normalize(path: str) -> str:
    """把路径变量归一为 {}，并去掉尾斜杠（便于两侧比较）。"""
    path = re.sub(r"\{[^}]*\}", "{}", path.strip())
    # 文档里还可能用 $ID / :id 这类占位风格 ⇒ 一并归一
    path = re.sub(r"/\$[A-Za-z_][A-Za-z0-9_]*", "/{}", path)
    path = re.sub(r"/:[A-Za-z_][A-Za-z0-9_]*", "/{}", path)
    if len(path) > 1:
        path = path.rstrip("/")
    return path


def extract_controller_paths(source: str) -> Set[str]:
    """从单个控制器源码里抽完整路径集合（类级前缀 + 方法级映射）。"""
    class_prefix = ""
    paths: Set[str] = set()

    # 逐个匹配注解；用「类级注解出现在第一个方法级注解之前」判定类级/方法级
    seen_method_level = False
    for match in _MAPPING_ANNOTATION.finditer(source):
        annotation, args = match.group(1), match.group(2) or ""
        quoted = [q for q in _QUOTED_PATH.findall(args) if not q.startswith("${")]
        # 过滤掉 consumes/produces/mediaType 之类的值：只保留像路径的（含 / 或为空）
        candidates = [q for q in quoted if "/" in q or q == ""]
        if annotation == "RequestMapping" and not seen_method_level:
            if candidates:
                class_prefix = candidates[0]
            continue
        seen_method_level = True
        if annotation in _METHOD_LEVEL:
            paths.add(normalize(_merge_paths(class_prefix, candidates[0] if candidates else "")))
        else:  # 方法级 @RequestMapping
            for candidate in candidates or [""]:
                paths.add(normalize(_merge_paths(class_prefix, candidate)))
    return {p for p in paths if p and p != "/"}


def scan_controller_paths(root: Path) -> Set[str]:
    all_paths: Set[str] = set()
    for java_file in root.rglob("*Controller.java"):
        try:
            all_paths |= extract_controller_paths(java_file.read_text(encoding="utf-8", errors="ignore"))
        except OSError as exc:
            print(f"⚠️  跳过无法读取的文件 {java_file}: {exc}")
    return all_paths


# ---------------- 文档侧 ----------------

_DOC_INLINE_CODE = re.compile(r"`([^`]*)`")
# 行内代码里出现的、看起来是端点路径的片段（允许 `` `GET /api/x` `` 这种「方法 + 路径」写法）
_DOC_PATH_IN_CODE = re.compile(r"(/api/[A-Za-z0-9/_{}.*$-]*)")
_IGNORED_PREFIXES = ("/api/**", "/api/v1/routing-monitor", "/api/v1/circuit-breaker-monitor")

# 文档里用来记录**历史/已移除**端点的措辞：这类提及不是「当前端点」，不参与对账
_REMOVAL_MARKERS = (
    "removed", "deprecated", "deleted", "no longer", "obsolete",
    "已删除", "已移除", "已废弃", "不再提供", "历史",
)


def _is_removal_note(line: str) -> bool:
    lowered = line.lower()
    return any(marker in lowered for marker in _REMOVAL_MARKERS)


def extract_doc_paths(markdown: str) -> Set[str]:
    """抽文档里的**当前**端点路径。

    覆盖面要包含两种常见写法：`` `/api/x` `` 与 `` `GET /api/x` ``（标题里带方法名），
    因此先取**行内代码整体**再在其中找 `/api/...` 片段；
    同时跳过「已移除」类历史说明所在的行（否则会把历史端点当成当前端点而误报）。
    """
    paths: Set[str] = set()
    for line in markdown.splitlines():
        removal = _is_removal_note(line)
        for span in _DOC_INLINE_CODE.findall(line):
            for match in _DOC_PATH_IN_CODE.finditer(span):
                raw = match.group(1)
                if raw in _IGNORED_PREFIXES or raw.endswith("/**") or raw.endswith("/*") or "*" in raw:
                    continue
                if removal:
                    continue
                paths.add(normalize(raw))
    return paths


def reconcile(doc_paths: Iterable[str], code_paths: Set[str]) -> Tuple[Set[str], Set[str]]:
    """返回 (文档有但代码没有的路径, 代码有但文档没提的路径)。

    三种「算命中」的形态：
      * 完全相等；
      * **文档路径是代码路径的前缀**（文档常把同一前缀下多个方法合并成一行描述）；
      * 文档写了**具体取值**而代码是路径变量（如文档 `/api/config/instance/chat` ↔ 代码
        `/api/config/instance/{}`）⇒ 用 `{}` → `[^/]+` 的正则匹配。
    """
    code = set(code_paths)
    code_patterns = [re.compile("^" + re.escape(c).replace(r"\{\}", "[^/]+") + "$") for c in code]
    missing = set()
    for doc_path in set(doc_paths):
        if doc_path in code:
            continue
        if any(c.startswith(doc_path + "/") for c in code):
            continue  # 前缀形态：文档描述的是这一族
        if any(pattern.match(doc_path) for pattern in code_patterns):
            continue  # 路径变量具体化形态
        missing.add(doc_path)
    documented_prefixes = tuple(set(doc_paths))
    undocumented = {c for c in code if not any(c.startswith(p) for p in documented_prefixes)}
    return missing, undocumented


def main() -> int:
    parser = argparse.ArgumentParser(description="文档端点与控制器映射对账")
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--docs", nargs="*", default=None,
                        help="要核对的文档（默认 docs/{zh,en}/api-reference/management-api.md）")
    parser.add_argument("--fail-on-error", action="store_true")
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    code_paths = scan_controller_paths(root / "src/main/java")
    print(f"🔍 控制器重建出的端点路径: {len(code_paths)} 条")

    docs = args.docs or ["docs/zh/api-reference/management-api.md", "docs/en/api-reference/management-api.md"]
    missing_total = 0
    for doc_rel in docs:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue
        doc_paths = extract_doc_paths(doc_path.read_text(encoding="utf-8", errors="ignore"))
        missing, undocumented = reconcile(doc_paths, code_paths)
        print(f"\n📄 {doc_rel}")
        print(f"   文档端点: {len(doc_paths)} · ✅ 命中: {len(doc_paths) - len(missing)}")
        if missing:
            print(f"   ❌ 控制器里找不到 ({len(missing)}):")
            for path in sorted(missing):
                print(f"      - {path}")
            missing_total += len(missing)
        if undocumented:
            print(f"   ℹ️  控制器有但文档未提 ({len(undocumented)}): {', '.join(sorted(undocumented))}")

    print()
    if missing_total:
        print(f"❌ 文档里共 {missing_total} 条端点路径在控制器映射中找不到")
        return 1 if args.fail_on_error else 0
    print("✅ 文档端点全部能在控制器映射中找到")
    return 0


if __name__ == "__main__":
    sys.exit(main())
