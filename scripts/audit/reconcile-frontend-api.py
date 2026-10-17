#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前端调用 ↔ 后端映射 双向对账器（JAiRouter）。

## 为什么要它

管理控制台（`frontend/src`）通过 `@/utils/request`（axios，`baseURL = VITE_API_BASE_URL`）
调用 `src/main/java` 下的 Spring WebFlux 控制器。两侧任一侧漂移都会产生**静默的产品缺陷**：

* 前端调了后端没有的 (method, path) ⇒ 线上 404；
* 前端用错方法（后端只有 PUT 而前端发 POST）⇒ 线上 405；
* 后端有映射但没人调 ⇒ 未必是死端点（`/api/**` 还有直连 API 客户端），需按
  「文档里有没有」分诊，**不能直接判死**。

本仓已有的 `scripts/docs/check-api-endpoints.py` 只覆盖 **GET + 手写清单** 的一面；
本脚本做的是**全方法、双向、可自校准**的对账。

## 用法

    # 报告模式（默认写 scripts/audit/out/frontend-api-reconcile.{md,json}）
    python scripts/audit/reconcile-frontend-api.py

    # 校准（强制前置）：造合成夹具 → 断言判据真的会响 / 不该响的不响 → 清理夹具
    python scripts/audit/reconcile-frontend-api.py --self-test

    # 只输出 JSON，或换输出目录
    python scripts/audit/reconcile-frontend-api.py --md-out "" --json-out out/x.json

**不启动应用、不跑 maven/npm、不修改 `src/main/java` 与 `frontend/src`。**

## 判据（与报告正文同一套，改代码必须同步改报告口径）

* 前端路径 = 字面量（含模板串，`${...}` 归一为 `{}`）**拼接 `VITE_API_BASE_URL`**（取自
  `frontend/.env*`，默认 `/api`）；
* 后端路径 = 类级 `@RequestMapping` 前缀 + 方法级映射（缺失路径 = 前缀自身），
  `{var}` / `{var:regex}` 归一为 `{}`，无前导斜杠的类级前缀补 `/`；
* **命中** = 方法相容（后端 `ANY` 相容一切）且路径用 `{}` → `[^/]+` 的通配正则互配；
* 前端→后端失配分两种 **原因**：`no-mapping-for-path`（整条路径不存在）与
  `method-not-declared-on-path`（路径在、方法不在 = 405 类）。
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

# --------------------------------------------------------------------------------------
# 通用工具
# --------------------------------------------------------------------------------------

ALL_METHODS = ("GET", "POST", "PUT", "DELETE", "PATCH")
ANY_METHOD = "*"


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def rel(path: Path, base: Path) -> str:
    try:
        return path.resolve().relative_to(base.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


# --------------------------------------------------------------------------------------
# 后端抽取
# --------------------------------------------------------------------------------------

_METHOD_ANNOTATION_TO_METHOD = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}
_MAPPING_ANNOTATIONS = set(_METHOD_ANNOTATION_TO_METHOD) | {"RequestMapping"}
_ANNOTATION_RE = re.compile(r"@([A-Za-z_$][\w$]*)")
_CLASS_DECL_RE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)")


def strip_java_comments(src: str) -> str:
    """把注释内容替换成空格（保留换行 ⇒ 行号不变）。字符串/字符量原样保留。"""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if ch in "\"'":
            quote = ch
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            j = n if j == -1 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def _skip_ws(text: str, i: int) -> int:
    while i < len(text) and text[i] in " \t\r\n":
        i += 1
    return i


def _balanced(text: str, i: int, opener: str = "(", closer: str = ")") -> tuple[str, int]:
    """从 `text[i] == opener` 起读到配对的 closer（跳过字符串/字符量/注释）。

    返回 (内部内容, closer 之后的下标)。不配平时返回 (剩余内容, len(text))。
    """
    assert text[i] == opener
    depth = 0
    j = i
    n = len(text)
    start = i + 1
    while j < n:
        ch = text[j]
        if ch in "\"'":
            quote = ch
            j += 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == quote:
                    break
                j += 1
            j += 1
            continue
        if ch == opener:
            depth += 1
        elif ch == closer:
            depth -= 1
            if depth == 0:
                return text[start:j], j + 1
        j += 1
    return text[start:], n


def split_top_level(args: str) -> list[str]:
    """按顶层逗号切分注解参数（忽略括号/花括号/方括号与字符串内的逗号）。"""
    parts: list[str] = []
    depth = 0
    buf: list[str] = []
    i, n = 0, len(args)
    while i < n:
        ch = args[i]
        if ch in "\"'":
            quote = ch
            buf.append(ch)
            i += 1
            while i < n:
                buf.append(args[i])
                if args[i] == "\\":
                    i += 1
                    if i < n:
                        buf.append(args[i])
                    i += 1
                    continue
                if args[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    if buf:
        parts.append("".join(buf))
    return [p for p in (part.strip() for part in parts) if p]


_STRING_LITERAL_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')


def _strings_in(expr: str) -> list[str]:
    return [m.group(1).replace('\\"', '"').replace("\\\\", "\\") for m in _STRING_LITERAL_RE.finditer(expr)]


def _split_assignment(part: str) -> tuple[str | None, str]:
    """`key = value` → (key, value)；无顶层 `=` 时返回 (None, part)。"""
    depth = 0
    i, n = 0, len(part)
    while i < n:
        ch = part[i]
        if ch in "\"'":
            quote = ch
            i += 1
            while i < n:
                if part[i] == "\\":
                    i += 2
                    continue
                if part[i] == quote:
                    break
                i += 1
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "=" and depth == 0:
            return part[:i].strip(), part[i + 1 :].strip()
        i += 1
    return None, part


def parse_mapping_annotation(name: str, args: str) -> tuple[list[str], list[str]]:
    """解析映射注解参数 → (路径候选列表, HTTP 方法列表)。

    支持：位置参数、`value =`、`path =`、字符串数组、`method = RequestMethod.X`。
    `produces` / `consumes` / `headers` / `params` 一律忽略。
    """
    paths: list[str] = []
    methods: list[str] = []
    for part in split_top_level(args):
        key, value = _split_assignment(part)
        if key is None:
            for literal in _strings_in(value):
                if "${" not in literal:
                    paths.append(literal)
            continue
        lowered = key.lower()
        if lowered in ("value", "path"):
            for literal in _strings_in(value):
                if "${" not in literal:
                    paths.append(literal)
        elif lowered == "method":
            for token in re.findall(r"RequestMethod\.([A-Za-z]+)", value):
                methods.append(token.upper())
    if name in _METHOD_ANNOTATION_TO_METHOD:
        methods = [_METHOD_ANNOTATION_TO_METHOD[name]]
    elif not methods:
        methods = [ANY_METHOD]
    return paths, methods


def scan_annotations(src: str) -> list[tuple[str, str, int, int]]:
    """(注解名, 参数字符串, 起始下标, 结束下标)。无括号的注解参数为空串。"""
    found: list[tuple[str, str, int, int]] = []
    for match in _ANNOTATION_RE.finditer(src):
        name = match.group(1)
        i = _skip_ws(src, match.end())
        if i < len(src) and src[i] == "(":
            args, end = _balanced(src, i)
            found.append((name, args, match.start(), end))
        else:
            found.append((name, "", match.start(), match.end()))
    return found


def normalize_path(raw: str) -> str:
    """`{var}` / `{var:regex}` / 嵌套花括号 → `{}`；压制重复斜杠与尾斜杠。"""
    out: list[str] = []
    depth = 0
    for ch in raw.strip():
        if ch == "{":
            if depth == 0:
                out.append("{}")
            depth += 1
            continue
        if ch == "}":
            depth = max(0, depth - 1)
            continue
        if depth == 0:
            out.append(ch)
    path = "".join(out)
    path = re.sub(r"/{2,}", "/", path)
    if not path.startswith("/"):
        path = "/" + path
    if len(path) > 1:
        path = path.rstrip("/")
    return path or "/"


def merge_paths(prefix: str, path: str) -> str:
    if not path:
        merged = prefix or ""
    elif path.startswith("/"):
        merged = prefix.rstrip("/") + path
    else:
        merged = prefix.rstrip("/") + "/" + path
    return normalize_path(merged)


def extract_backend_mappings(src: str, file_label: str) -> tuple[list[dict], list[dict]]:
    """返回 (mapping 列表, 可疑项列表)。"""
    stripped = strip_java_comments(src)
    annotations = scan_annotations(stripped)

    class_decl = _CLASS_DECL_RE.search(stripped)
    class_pos = class_decl.start() if class_decl else 0

    class_prefix = ""
    for name, args, start, _end in annotations:
        if name == "RequestMapping" and start < class_pos:
            paths, _methods = parse_mapping_annotation(name, args)
            if paths:
                class_prefix = paths[0]

    mappings: list[dict] = []
    unresolved: list[dict] = []
    for name, args, start, _end in annotations:
        if name not in _MAPPING_ANNOTATIONS:
            continue
        if name == "RequestMapping" and start < class_pos:
            continue  # 类级前缀
        paths, methods = parse_mapping_annotation(name, args)
        if not paths:
            paths = [""]
        for path in paths:
            full = merge_paths(class_prefix, path)
            for method in methods:
                mappings.append(
                    {
                        "method": method,
                        "path": full,
                        "file": file_label,
                        "line": line_of(stripped, start),
                        "classPrefix": normalize_path(class_prefix) if class_prefix else "",
                        "rawPath": path,
                        "annotation": name,
                    }
                )
        # 类级前缀带 ${...} 的情况：记录下来，不静默丢
        if name == "RequestMapping" and "${" in args and start < class_pos:
            unresolved.append({"file": file_label, "line": line_of(stripped, start), "reason": "class-prefix-placeholder", "raw": args.strip()})
    return mappings, unresolved


# --------------------------------------------------------------------------------------
# 前端抽取
# --------------------------------------------------------------------------------------

_DEFAULT_IMPORT_RE = re.compile(
    r"import\s+([A-Za-z_$][\w$]*)\s*(?:,\s*\{[^}]*\}\s*)?from\s*['\"]([^'\"]*utils/request)['\"]"
)
_AXIOS_CREATE_RE = re.compile(r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::\s*[^=]+)?=\s*axios\s*\.\s*create\s*\(")
_TEMPLATE_EXPR_RE = re.compile(r"\$\{([^{}]*)\}")

# 其它 HTTP 出口（不在 request.get/post/... 面上）：仅作覆盖面声明，不参与对账
_OTHER_CALL_RE = re.compile(r"\b(?:fetch|axios\s*\.\s*(?:get|post|put|delete|patch|request))\s*[<(]")
_CLIENT_FN_RE = re.compile(r"\b(playgroundRequest|axiosRequest|httpRequest)\s*[<(]")

_TS_EXCLUDE_DIRS = {"node_modules", "dist", "__tests__", "__mocks__"}
_TS_EXCLUDE_SUFFIX = (".spec.ts", ".test.ts", ".d.ts")


def iter_ts_files(root: Path) -> list[Path]:
    out: list[Path] = []
    if not root.exists():
        return out
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix not in (".ts", ".vue"):
            continue
        if any(part in _TS_EXCLUDE_DIRS for part in path.parts):
            continue
        if path.name.endswith(_TS_EXCLUDE_SUFFIX):
            continue
        out.append(path)
    return out


def _read_ts_string(src: str, i: int) -> tuple[str | None, int, str]:
    """读取 TS 字符串/模板串字面量。返回 (内容, 结束下标, 种类)；非字面量返回 (None, i, '')。"""
    ch = src[i]
    if ch == "'" or ch == '"':
        j = i + 1
        n = len(src)
        while j < n:
            if src[j] == "\\":
                j += 2
                continue
            if src[j] == ch:
                return src[i + 1 : j], j + 1, "string"
            j += 1
        return src[i + 1 :], n, "string"
    if ch == "`":
        j = i + 1
        n = len(src)
        while j < n:
            c = src[j]
            if c == "\\":
                j += 2
                continue
            if c == "$" and j + 1 < n and src[j + 1] == "{":
                depth = 0
                while j < n:
                    if src[j] == "{":
                        depth += 1
                    elif src[j] == "}":
                        depth -= 1
                        if depth == 0:
                            j += 1
                            break
                    j += 1
                continue
            if c == "`":
                return src[i + 1 : j], j + 1, "template"
            j += 1
        return src[i + 1 :], n, "template"
    return None, i, ""


def _skip_generics(src: str, i: int) -> int:
    """若 src[i] == '<' 且能找到「后面紧跟 `(`」的配对 `>`，返回 `>` 之后的下标；否则原样返回。"""
    if i >= len(src) or src[i] != "<":
        return i
    depth = 0
    j = i
    n = len(src)
    while j < n and j - i < 400:
        ch = src[j]
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
            if depth == 0:
                k = _skip_ws(src, j + 1)
                if k < n and src[k] == "(":
                    return k
                return i
        elif ch == "\n" and depth == 0:
            return i
        j += 1
    return i


def _first_argument(src: str, i: int) -> tuple[str | None, str, int, int, str]:
    """从 `(` 之后读第一个实参。

    返回 (字面量内容或 None, 类型, 结束下标, 逗号/右括号下标, 实参原文)。
    """
    n = len(src)
    i = _skip_ws(src, i)
    # 跳过行内注释
    while i < n and src.startswith("//", i):
        i = src.find("\n", i)
        i = n if i == -1 else _skip_ws(src, i)
    if i < n and src[i] in "'\"`":
        literal, end, kind = _read_ts_string(src, i)
        return literal, kind, end, end, src[i:end]
    # 计算型实参：读到顶层逗号或右括号
    depth = 0
    j = i
    while j < n:
        ch = src[j]
        if ch in "'\"`":
            _lit, j, _k = _read_ts_string(src, j)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0 and ch == ")":
                return None, "computed", j, j, src[i:j]
            depth -= 1
        elif ch == "," and depth == 0:
            return None, "computed", j, j, src[i:j]
        j += 1
    return None, "computed", n, n, src[i:n]


def identifier_url_candidates(src: str, name: str, scope: dict | None = None) -> list[str]:
    """`request.post(endpoint)` 这类「实参是本地变量」的**有界**候选值解析。

    只在**同一个函数作用域内**（拿不到作用域时退化为同一文件）找对 `name` 的赋值，
    且值必须是**纯字符串字面量或纯模板串**；任何一处赋值不是纯字面量
    （`endpoint = someRuntimeValue`）就整体放弃 —— 宁可报成盲区，也不给一个不完整的候选集合。
    """
    start = scope["bodyStart"] if scope else 0
    end = scope["bodyEnd"] if scope else len(src)
    region = src[start:end]
    assignments = list(re.finditer(r"(?<![\w$.])" + re.escape(name) + r"\s*=\s*([^\n;]*)", region))
    if not assignments:
        return []
    candidates: list[str] = []
    for match in assignments:
        text = match.group(1).strip()
        if not text:
            continue
        if text[0] in "'\"":
            literal, end_at, kind = _read_ts_string(text, 0)
            if kind == "string" and text[end_at:].strip() in ("", "as const"):
                candidates.append(literal)
                continue
            return []
        if text[0] == "`":
            literal, end_at, kind = _read_ts_string(text, 0)
            if kind == "template" and text[end_at:].strip() in ("", "as const"):
                candidates.append(literal)
                continue
            return []
        return []
    return [c for c in candidates if c]


_TS_STRING_RE = re.compile(r"""(['"])((?:\\.|(?!\1)[^\\\n])*)\1""")
_TS_FUNC_RE = re.compile(
    r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::[^=;\n]+)?=\s*(?:async\s+)?(?:function\s*)?\(([^)]*)\)\s*(?::[^=>{;\n]+)?=>"
    r"|\bfunction\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)"
)
_DROPDOWN_EVENT_RE = re.compile(r"@command\s*=\s*[\"']([A-Za-z_$][\w$]*)[\"']")
# 只取**字面量** command（`:command="expr"` 是绑定，值只在运行时可知，不算解析）
_DROPDOWN_ITEM_RE = re.compile(r"<el-dropdown-item[^>]*?(?<!:)\bcommand\s*=\s*[\"']([^\"']+)[\"']")
_MAX_EXPANSIONS = 16

# 常量声明：`const NAME[: type] = <整个初始化表达式>`（表达式截到第一个 `;` 或换行）
_TS_ASSIGN_RE = re.compile(r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::[^=;\n]+)?=\s*([^;\n]*)")
# 整表达式就是**一个**字符串字面量（可带 `as const`）
# ⚠️ 分支必须互斥：`[^\\]*(?:\\.[^\\]*)*` 而不是 `(?:\\.|(?!\1).)*`。
# 后者的两个分支在反斜杠处重叠，CodeQL 判为可指数回溯（ReDoS）；本脚本要入库，故换成
# 经典的不重叠形态（语义等价：仍允许转义字符）。
_PURE_LITERAL_RE = re.compile(r"""^(['"])([^\\]*(?:\\.[^\\]*)*)\1\s*(?:as\s+const)?$""")
# 整表达式就是「<无引号部分> ? '<a>' : '<b>' <无引号部分>」
_PURE_TERNARY_RE = re.compile(
    r"""^[^'"]*\?[^'"]*(['"])([^\\]*(?:\\.[^\\]*)*)\1[^'"]*:[^'"]*(['"])([^\\]*(?:\\.[^\\]*)*)\3[^'"]*$"""
)


def _ts_string_literals(text: str) -> list[str]:
    return [m.group(2).replace('\\"', '"').replace("\\'", "'") for m in _TS_STRING_RE.finditer(text)]


def _ts_params(raw: str) -> list[str]:
    out: list[str] = []
    for part in raw.split(","):
        name = re.split(r"[:=?]", part.strip())[0].strip()
        if re.fullmatch(r"[A-Za-z_$][\w$]*", name or ""):
            out.append(name)
    return out


def _matching_brace(src: str, open_index: int) -> int:
    depth = 0
    i = open_index
    n = len(src)
    while i < n:
        ch = src[i]
        if ch in "'\"`":
            _lit, i, _k = _read_ts_string(src, i)
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n


def ts_function_scopes(src: str) -> list[dict]:
    """粗略但够用的 TS/Vue 函数作用域（`<script setup>` 里基本是 `const f = (…) => {}`）。"""
    scopes: list[dict] = []
    for match in _TS_FUNC_RE.finditer(src):
        name = match.group(1) or match.group(3)
        params = _ts_params(match.group(2) if match.group(1) else match.group(4))
        i = _skip_ws(src, match.end())
        if i < len(src) and src[i] == "{":
            body_end = _matching_brace(src, i)
        else:
            body_end = src.find("\n", i)
            body_end = len(src) if body_end == -1 else body_end
        scopes.append(
            {"name": name, "params": params, "declStart": match.start(), "bodyStart": i, "bodyEnd": body_end}
        )
    return scopes


def enclosing_scope(scopes: list[dict], index: int) -> dict | None:
    best = None
    for scope in scopes:
        if scope["bodyStart"] <= index <= scope["bodyEnd"]:
            if best is None or scope["bodyStart"] > best["bodyStart"]:
                best = scope
    return best


def _top_level_args(src: str, open_index: int) -> tuple[list[str], int]:
    depth = 0
    i = open_index + 1
    n = len(src)
    start = i
    args: list[str] = []
    while i < n:
        ch = src[i]
        if ch in "'\"`":
            _lit, i, _k = _read_ts_string(src, i)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0 and ch == ")":
                args.append(src[start:i])
                return args, i
            depth -= 1
        elif ch == "," and depth == 0:
            args.append(src[start:i])
            start = i + 1
        i += 1
    args.append(src[start:])
    return args, n


def file_placeholder_value_sets(src: str) -> tuple[dict[str, list[str]], dict[str, list[str]], list[dict]]:
    """返回 (变量名→取值集合, "函数名#形参名"→取值集合, 解析记录)。"""
    scopes = ts_function_scopes(src)
    name_values: dict[str, list[str]] = {}
    log: list[dict] = []

    # 来源 1/2：常量声明。
    #
    # **必须匹配「整个初始化表达式」**，不能只从表达式里捞字符串字面量 —— 否则
    # `const response = await request.get('/auth/api-keys')` 会被解析成
    # 「常量 response = '/auth/api-keys'」，一旦某处写 `${response}` 就会拿这个 URL 去匹配。
    # 这个形态在本仓 frontend 里极其普遍（每个 API 函数都是 `const response = await request.get(...)`），
    # 所以「整表达式匹配」是硬要求，不是洁癖。
    for match in _TS_ASSIGN_RE.finditer(src):
        name = match.group(1)
        expr = match.group(2).strip()
        pure_literal = _PURE_LITERAL_RE.match(expr)
        if pure_literal and pure_literal.group(2).startswith("/"):
            # 来源 1：只有「值本身以 / 开头」的常量才当 base path 解析。
            # 理由：中段/尾段的 `${x}` 绝大多数是运行时路径变量；若一个同名模块级常量
            # （如 `const id = 'x'`）恰好存在，替换会把 `/items/${id}` 变成 `/items/x`，
            # 破坏路径形状归一（表 3 分组）并掩盖「同名不同作用域」的事实。
            value = pure_literal.group(2)
            name_values.setdefault(name, [value])
            log.append({"kind": "const", "name": name, "values": [value]})
            continue
        pure_ternary = _PURE_TERNARY_RE.match(expr)
        if pure_ternary:
            # 来源 2：整个初始化表达式就是「两个字面量的三元」（分派关键字）
            values = [pure_ternary.group(2), pure_ternary.group(4)]
            if all(values):  # 空串不算解析结果（如 `const x = a ? '' : ''`）
                name_values.setdefault(name, values)
                log.append({"kind": "ternary", "name": name, "values": values, "raw": expr[:120]})

    # 来源 3：函数调用点的字面量实参
    param_values: dict[str, list[str]] = {}
    for scope in scopes:
        name = scope["name"]
        # 声明自身要排除，避免把 `const f = (a) => ...` 的 `f(` 当成调用
        call_sites = [
            m for m in re.finditer(r"(?<![\w$.])" + re.escape(name) + r"\s*\(", src) if m.start() != scope["declStart"]
        ]
        if not call_sites:
            continue
        for pos, param in enumerate(scope["params"]):
            collected: list[str] = []
            ok = True
            for call in call_sites:
                args, _end = _top_level_args(src, call.end() - 1)
                if pos >= len(args):
                    ok = False
                    break
                literals = _ts_string_literals(args[pos].strip())
                if len(literals) != 1 or not args[pos].strip().startswith(("'", '"')):
                    ok = False
                    break
                collected.append(literals[0])
            if ok and collected:
                param_values[f"{name}#{param}"] = sorted(set(collected))
                log.append(
                    {"kind": "call-site-literals", "function": name, "param": param, "values": sorted(set(collected))}
                )

    # 来源 4：Element Plus dropdown 的 @command 实参（只在**同一个 `<el-dropdown>` 块内**收集字面量项）
    for event in _DROPDOWN_EVENT_RE.finditer(src):
        fn = event.group(1)
        block_start = src.rfind("<el-dropdown", 0, event.start())
        if block_start == -1:
            continue
        block_end = src.find("</el-dropdown>", event.end())
        block = src[block_start : block_end if block_end != -1 else min(len(src), event.end() + 4000)]
        commands = [c for c in _DROPDOWN_ITEM_RE.findall(block) if c]
        if not commands:
            continue
        scope = next((s for s in scopes if s["name"] == fn and s["params"]), None)
        if scope is None:
            continue
        key = f"{fn}#{scope['params'][0]}"
        param_values[key] = sorted(set(commands))
        log.append(
            {"kind": "element-plus-@command", "function": fn, "param": scope["params"][0], "values": sorted(set(commands))}
        )

    return name_values, param_values, log


def analyze_template(
    literal: str,
    const_values: dict[str, list[str]],
    param_values: dict[str, list[str]],
    scope: dict | None,
) -> tuple[str, list[str], list[str], list[str]]:
    """把模板串拆成 (形状串, 匹配串列表, 已解析的占位符, 未解析的占位符)。

    * 形状串：**所有**占位符一律 `{}`（用于报告与表 3 分组，保持稳定）；
    * 匹配串：能解析的占位符展开成各自的字面量，解析不出的仍是 `{}`（用于真正判命中）。
    """
    placeholders: list[tuple[str, list[str] | None]] = []
    resolved: list[str] = []
    unresolved: list[str] = []

    def _collect(match: re.Match) -> str:
        expr = match.group(1).strip()
        values: list[str] | None = None
        if re.fullmatch(r"[A-Za-z_$][\w$]*", expr):
            if expr in const_values:
                values = const_values[expr]
            elif scope and expr in scope["params"]:
                values = param_values.get(f"{scope['name']}#{expr}")
        if values:
            resolved.append(expr)
        else:
            unresolved.append(expr)
        placeholders.append((expr, values))
        # 形状串：只有一个取值时直接用该值（保持 `baseURL` 这类前缀可见），否则 `{}`
        return values[0] if values and len(values) == 1 else "{}"

    shape = _TEMPLATE_EXPR_RE.sub(_collect, literal)

    # 匹配串：对已解析的占位符做笛卡尔积（上限 _MAX_EXPANSIONS，超限即放弃展开）
    options = [values if values else ["{}"] for _expr, values in placeholders]
    total = 1
    for option in options:
        total *= len(option)
    if total > _MAX_EXPANSIONS:
        match_texts = [shape]
    else:
        texts = [literal]
        for option in options:
            nxt: list[str] = []
            for text in texts:
                for value in option:
                    nxt.append(_TEMPLATE_EXPR_RE.sub(lambda _m, v=value: v, text, count=1))
            texts = nxt
        match_texts = texts
    return shape, match_texts, resolved, unresolved


def normalize_template(
    literal: str,
    base_path: str,
    const_values: dict[str, list[str]],
    param_values: dict[str, list[str]],
    scope: dict | None,
) -> dict:
    """完整归一（含动态取值解析）。返回 shapePath / matchPaths / status。"""
    raw = literal
    shape_text, match_texts, resolved, unresolved = analyze_template(
        literal, const_values, param_values, scope
    )

    def _clean(text: str) -> str:
        for sep in ("?", "#"):
            idx = text.find(sep)
            if idx != -1:
                text = text[:idx]
        return text

    shape_clean = _clean(shape_text)
    if not shape_clean.startswith("/"):
        return {
            "path": None,
            "matchPaths": [],
            "status": "unresolved-prefix",
            "reason": "URL 前缀由运行时变量拼出，无法静态还原完整路径",
            "raw": raw,
            "resolvedPlaceholders": resolved,
            "unresolvedPlaceholders": unresolved,
            "partial": None,
        }

    shape_path = normalize_path(shape_clean)
    if base_path:
        shape_path = normalize_path(base_path.rstrip("/") + shape_path)

    match_paths: list[str] = []
    for text in match_texts:
        cleaned = _clean(text)
        if not cleaned.startswith("/"):
            continue
        path = normalize_path(cleaned)
        if base_path:
            path = normalize_path(base_path.rstrip("/") + path)
        match_paths.append(path)
    match_paths = sorted(set(match_paths)) or [shape_path]

    return {
        "path": shape_path,
        "matchPaths": match_paths,
        "status": "ok",
        "reason": "",
        "raw": raw,
        "resolvedPlaceholders": resolved,
        "unresolvedPlaceholders": unresolved,
        "partial": None,
    }


def extract_frontend_calls(src: str, file_label: str) -> tuple[list[dict], list[dict], list[dict]]:
    """返回 (call 列表, 不可归一列表, 其它 HTTP 出口列表)。URL 归一留给调用方（需要 basePath）。"""
    receivers = {"request"}
    for match in _DEFAULT_IMPORT_RE.finditer(src):
        receivers.add(match.group(1))
    for match in _AXIOS_CREATE_RE.finditer(src):
        receivers.add(match.group(1))

    calls: list[dict] = []
    unparseable: list[dict] = []
    scopes = ts_function_scopes(src)
    pattern = re.compile(r"(?<![\w$.])([A-Za-z_$][\w$]*)\s*\.\s*(get|post|put|delete|patch)\b")
    for match in pattern.finditer(src):
        if match.group(1) not in receivers:
            continue
        i = _skip_generics(src, match.end())
        if i >= len(src) or src[i] != "(":
            unparseable.append(
                {
                    "file": file_label,
                    "line": line_of(src, match.start()),
                    "method": match.group(2).upper(),
                    "reason": "no-argument-list",
                    "snippet": src[match.start() : match.start() + 60].replace("\n", " "),
                }
            )
            continue
        literal, kind, _end, _stop, arg_text = _first_argument(src, i + 1)
        line = line_of(src, match.start())
        if literal is None or kind == "computed":
            candidates: list[str] = []
            expr = arg_text.strip()
            if re.fullmatch(r"[A-Za-z_$][\w$]*", expr):
                candidates = identifier_url_candidates(src, expr, enclosing_scope(scopes, match.start()))
            call = {
                "file": file_label,
                "line": line,
                "index": match.start(),
                "method": match.group(2).upper(),
                "rawUrl": None,
                "urlKind": "computed",
                "receiver": match.group(1),
                "rawUrlCandidates": candidates,
                "computedArgument": expr[:120],
                "path": None,
                "status": "computed-argument",
                "reason": "第一个实参是变量/表达式，URL 只在运行时可知",
                "resolvedConsts": [],
            }
            calls.append(call)
            if not candidates:
                unparseable.append(
                    {
                        "file": file_label,
                        "line": line,
                        "method": match.group(2).upper(),
                        "reason": "computed-argument",
                        "snippet": src[match.start() : match.start() + 80].replace("\n", " "),
                    }
                )
            continue
        calls.append(
            {
                "file": file_label,
                "line": line,
                "index": match.start(),
                "method": match.group(2).upper(),
                "rawUrl": literal,
                "urlKind": kind,
                "receiver": match.group(1),
            }
        )

    others: list[dict] = []
    for regex, tag in ((_OTHER_CALL_RE, "fetch/axios"), (_CLIENT_FN_RE, "custom-axios-client")):
        for match in regex.finditer(src):
            others.append(
                {
                    "file": file_label,
                    "line": line_of(src, match.start()),
                    "kind": tag,
                    "snippet": src[match.start() : match.start() + 90].replace("\n", " "),
                }
            )
    return calls, unparseable, others


# --------------------------------------------------------------------------------------
# baseURL
# --------------------------------------------------------------------------------------

def resolve_base_path(frontend_root: Path, project_root: Path | None = None) -> tuple[str, str, list[dict]]:
    """从 `frontend/.env*` 取 `VITE_API_BASE_URL`。返回 (路径前缀, 来源, 全部观测值)。

    从 `frontend/src` 逐级向上找含 `.env` / `.env.production` 的目录（也认目录名 `frontend`），
    避免把 `frontend/src` 的父目录硬拼成 `frontend/frontend`。
    """
    env_dir: Path | None = None
    for directory in (frontend_root, *frontend_root.parents):
        if any((directory / name).exists() for name in (".env.production", ".env")):
            env_dir = directory
            break
        if project_root is not None and directory == project_root:
            break
    env_dirs = [env_dir] if env_dir else [frontend_root]

    observed: list[dict] = []
    chosen: tuple[str, str] | None = None
    for env_dir in env_dirs:
        for name in (".env.production", ".env"):
            path = env_dir / name
            if not path.exists():
                continue
            for line in read_text(path).splitlines():
                stripped = line.strip()
                if stripped.startswith("VITE_API_BASE_URL"):
                    _, _, value = stripped.partition("=")
                    value = value.strip().strip('"').strip("'")
                    observed.append({"file": f"{env_dir.name}/{name}", "value": value})
                    if chosen is None:
                        chosen = (value, f"{env_dir.name}/{name}")
    if chosen is None:
        return "/api", "default (no VITE_API_BASE_URL found)", observed
    value, source = chosen
    if value.startswith("http://") or value.startswith("https://"):
        # http(s)://host[:port]/api → 取 path 部分
        rest = value.split("//", 1)[1]
        slash = rest.find("/")
        value = rest[slash:] if slash != -1 else "/"
    if not value.startswith("/"):
        value = "/" + value
    return (value.rstrip("/") or "/api"), source, observed


# --------------------------------------------------------------------------------------
# 对账
# --------------------------------------------------------------------------------------

def path_pattern(path: str) -> re.Pattern:
    return re.compile("^" + re.escape(path).replace(r"\{\}", "[^/]+") + "$")


def paths_match(backend_path: str, frontend_path: str) -> bool:
    return bool(path_pattern(backend_path).match(frontend_path))


def methods_compatible(backend_method: str, frontend_method: str) -> bool:
    return backend_method == ANY_METHOD or backend_method == frontend_method


def expand_methods(method: str) -> list[str]:
    return list(ALL_METHODS) if method == ANY_METHOD else [method]


def relaxed_segment_match(backend_path: str, frontend_path: str) -> bool:
    """放宽匹配：把前端形状串里的 `{}` 也当成通配符（可为任意字面量段）。

    只用于**诊断**（告诉读者「这条 finding 在放宽规则下会不会落到某条字面量路由上」），
    不用于判定命中 —— 否则 `${instanceId}` 这类真实 ID 会把真实缺陷盖掉。
    """
    b = backend_path.strip("/").split("/")
    f = frontend_path.strip("/").split("/")
    if len(b) != len(f):
        return False
    return all(bs == fs or bs == "{}" or fs == "{}" for bs, fs in zip(b, f))


def build_docs_index(docs_root: Path) -> dict:
    """docs/**/*.md 语料：原文 + `{...}` 归一后的文本。"""
    docs: list[dict] = []
    if docs_root.exists():
        for path in sorted(docs_root.rglob("*.md")):
            text = read_text(path)
            if not text:
                continue
            docs.append(
                {
                    "path": path,
                    "label": path.as_posix(),
                    "raw": text,
                    "normalized": re.sub(r"\{[^{}\n]*\}", "{}", text),
                }
            )
    return {"files": docs, "count": len(docs)}


def docs_lookup(index: dict, path: str) -> dict:
    """在 docs 语料里找该路径。返回 {hit, mode, files}。

    两种形态都算命中：
      ① 原文里出现 `{var}` 形式（把路径当正则匹配），或出现**具体值**；
      ② 文档里的 `{var}` 归一后出现同形的 `{}` 路径。
    """
    wildcard = re.compile(r"(?<![A-Za-z0-9_/])" + re.escape(path).replace(r"\{\}", r"[^/\s`'\"|,)\]*]+") + r"(?![A-Za-z0-9_/-])")
    literal = path
    literal_re = re.compile(r"(?<![A-Za-z0-9_/])" + re.escape(literal) + r"(?![A-Za-z0-9_/-])")
    hits: list[str] = []
    modes: set[str] = set()
    for doc in index["files"]:
        if literal_re.search(doc["normalized"]):
            hits.append(doc["path"].as_posix())
            modes.add("normalised-{}-literal")
        elif wildcard.search(doc["raw"]):
            hits.append(doc["path"].as_posix())
            modes.add("wildcard-or-concrete")
    return {
        "hit": bool(hits),
        "mode": ",".join(sorted(modes)),
        "files": hits[:8],
        "fileCount": len(hits),
    }


MANAGEMENT_SURFACE_PREFIXES = ("/api",)


def classify_backend_only(path: str, docs: dict) -> tuple[str, str]:
    """返回 (分类, 依据说明)。

    分类口径（**没有 OpenAPI 产物时**，见报告「方法与局限」）：
      * `documented` —— 路径出现在 `docs/**` 里（`{}` 具体化或原形均可）；
      * `undocumented-and-unused` —— 不在 docs、也没有前端调用，且路径位于
        `/api/**` 管理面（该面在 `docs/{zh,en}/api-reference/` 有系统性覆盖）；
      * `unknown` —— 不在 docs，但路径**不在** `/api/**`（`/v1/**`、`/actuator/**` 等协议面），
        文档覆盖不是系统性的 ⇒ 「docs 里没有」不构成证据。
    """
    if docs["hit"]:
        return "documented", f"docs 命中 {docs['fileCount']} 个文件（{docs['mode']}）"
    if any(path == prefix or path.startswith(prefix + "/") for prefix in MANAGEMENT_SURFACE_PREFIXES):
        return "undocumented-and-unused", "docs 未提及 + 无前端调用 + 属 /api/** 管理面（docs 对该面有系统性覆盖）"
    return "unknown", "docs 未提及 + 无前端调用 + 不属 /api/** 管理面（协议面文档覆盖非系统性）"


def analyze(project_root: Path, backend_root: Path, frontend_root: Path, docs_root: Path) -> dict:
    base_path, base_source, base_observed = resolve_base_path(frontend_root, project_root)

    backend_files: list[Path] = []
    if backend_root.exists():
        backend_files = sorted(backend_root.rglob("*.java"))

    controller_files = 0
    stereotype = re.compile(r"@(?:RestController|Controller)\b")
    mappings: list[dict] = []
    backend_unresolved: list[dict] = []
    for path in backend_files:
        label = rel(path, project_root)
        src = read_text(path)
        if not src:
            continue
        if stereotype.search(src):
            controller_files += 1
        file_mappings, file_unresolved = extract_backend_mappings(src, label)
        mappings.extend(file_mappings)
        backend_unresolved.extend(file_unresolved)

    # 去重（同 method+path 的多处声明全部保留，但记录坐标）
    mapping_index: dict[tuple[str, str], list[dict]] = {}
    for mapping in mappings:
        mapping_index.setdefault((mapping["method"], mapping["path"]), []).append(mapping)

    ts_files = iter_ts_files(frontend_root)
    frontend_calls: list[dict] = []
    frontend_unparseable: list[dict] = []
    other_call_sites: list[dict] = []
    value_resolution_log: list[dict] = []
    for path in ts_files:
        label = rel(path, project_root)
        src = read_text(path)
        if not src:
            continue
        const_values, param_values, resolution_log = file_placeholder_value_sets(src)
        for entry in resolution_log:
            entry["file"] = label
        value_resolution_log.extend(resolution_log)
        scopes = ts_function_scopes(src)
        raw_calls, unparseable, others = extract_frontend_calls(src, label)
        for item in unparseable:
            item["consts"] = sorted(const_values)
        frontend_unparseable.extend(unparseable)
        other_call_sites.extend(others)
        for call in raw_calls:
            if call.get("rawUrl") is not None:
                scope = enclosing_scope(scopes, call["index"])
                resolved = normalize_template(call["rawUrl"], base_path, const_values, param_values, scope)
                call.update(resolved)
                call["enclosingFunction"] = scope["name"] if scope else None
                if call["status"] != "ok":
                    frontend_unparseable.append(
                        {
                            "file": call["file"],
                            "line": call["line"],
                            "method": call["method"],
                            "reason": call["status"],
                            "snippet": call["rawUrl"],
                            "resolvedConsts": call.get("resolvedPlaceholders", []),
                        }
                    )
            elif call.get("rawUrlCandidates"):
                # `request.post(endpoint)`：实参是本地变量，但所有赋值点都是纯字面量/模板串
                # ⇒ 每个候选值**独立**判命中（某个候选命中不能替另一个候选开脱），并把解析记进日志。
                scope = enclosing_scope(scopes, call["index"])
                units: list[dict] = []
                for candidate in call["rawUrlCandidates"]:
                    resolved = normalize_template(candidate, base_path, const_values, param_values, scope)
                    if resolved["status"] == "ok":
                        units.append(
                            {
                                "raw": candidate,
                                "shape": resolved["path"],
                                "matchPaths": resolved["matchPaths"],
                                "resolvedPlaceholders": resolved["resolvedPlaceholders"],
                                "unresolvedPlaceholders": resolved["unresolvedPlaceholders"],
                            }
                        )
                call["enclosingFunction"] = scope["name"] if scope else None
                if not units:
                    frontend_unparseable.append(
                        {
                            "file": call["file"],
                            "line": call["line"],
                            "method": call["method"],
                            "reason": "unresolved-prefix",
                            "snippet": " | ".join(call["rawUrlCandidates"])[:160],
                            "resolvedConsts": [],
                        }
                    )
                    frontend_calls.append(call)
                    continue
                call.update(
                    {
                        "path": units[0]["shape"],
                        "matchPaths": sorted({p for u in units for p in u["matchPaths"]}),
                        "status": "ok",
                        "reason": "",
                        "candidates": units,
                        "candidateShapes": sorted({u["shape"] for u in units}),
                        "resolvedPlaceholders": sorted({p for u in units for p in u["resolvedPlaceholders"]}),
                        "unresolvedPlaceholders": sorted({p for u in units for p in u["unresolvedPlaceholders"]}),
                    }
                )
                value_resolution_log.append(
                    {
                        "kind": "identifier-assignments",
                        "file": label,
                        "name": call.get("computedArgument"),
                        "values": [u["shape"] for u in units],
                        "rawValues": call["rawUrlCandidates"],
                    }
                )
            else:
                call["enclosingFunction"] = None
            frontend_calls.append(call)

    normalized_calls = [c for c in frontend_calls if c["path"]]
    docs_index = build_docs_index(docs_root)

    # 前端标识符跨文件引用计数（用于给 finding 标注「包裹它的函数有没有人调用」；best-effort）
    identifier_counts: dict[str, int] = {}
    identifier_counts_per_file: dict[str, dict[str, int]] = {}
    for path in ts_files:
        src = read_text(path)
        counts: dict[str, int] = {}
        for token in re.findall(r"[A-Za-z_$][\w$]*", src):
            counts[token] = counts.get(token, 0) + 1
        identifier_counts_per_file[rel(path, project_root)] = counts
        for token, value in counts.items():
            identifier_counts[token] = identifier_counts.get(token, 0) + value

    def other_file_references(call: dict) -> int | None:
        name = call.get("enclosingFunction")
        if not name:
            return None
        return identifier_counts.get(name, 0) - identifier_counts_per_file.get(call["file"], {}).get(name, 0)

    # ---- 表 1：前端 → 后端 ----
    #
    # 每个 `request.*` 调用可能展开成多个「判定单元」：正常调用是 1 个（其模板串的形状），
    # `request.post(endpoint)` 这类是 N 个（每个静态可见的赋值候选各算一个）。
    # **每个单元独立判命中** —— 一个候选命中不能替另一个候选开脱。
    table1: list[dict] = []
    for call in sorted(normalized_calls, key=lambda c: (c["file"], c["line"])):
        units = call.get("candidates") or [
            {
                "raw": call["rawUrl"],
                "shape": call["path"],
                "matchPaths": call["matchPaths"],
                "resolvedPlaceholders": call.get("resolvedPlaceholders", []),
                "unresolvedPlaceholders": call.get("unresolvedPlaceholders", []),
            }
        ]
        for unit in units:
            path_hits = [m for m in mappings if any(paths_match(m["path"], mp) for mp in unit["matchPaths"])]
            method_ok = [m for m in path_hits if methods_compatible(m["method"], call["method"])]
            if method_ok:
                continue
            declared = sorted({m["method"] for m in path_hits})
            # 诊断：放宽规则下同方法是否存在字面量路由（说明这段是「分派关键字」而非「真实 ID」）
            ambiguous = sorted(
                {
                    (m["method"], m["path"], m["file"], m["line"])
                    for m in mappings
                    if m["method"] == call["method"] and relaxed_segment_match(m["path"], unit["shape"])
                }
            )
            table1.append(
                {
                    "file": call["file"],
                    "line": call["line"],
                    "method": call["method"],
                    "path": unit["shape"],
                    "matchPaths": unit["matchPaths"],
                    "rawUrl": unit["raw"],
                    "rawUrlCandidates": call.get("rawUrlCandidates") or [],
                    "candidateShapes": call.get("candidateShapes") or [],
                    "candidateOf": call.get("candidates") and len(call["candidates"]) > 1,
                    "reason": "method-not-declared-on-path" if path_hits else "no-mapping-for-path",
                    "backendMethodsOnPath": declared,
                    "unresolvedPlaceholders": unit.get("unresolvedPlaceholders", []),
                    "resolvedPlaceholders": unit.get("resolvedPlaceholders", []),
                    "enclosingFunction": call.get("enclosingFunction"),
                    "enclosingFunctionOtherFileReferences": other_file_references(call),
                    "ambiguousLiteralRoutes": [
                        {"method": m, "path": p, "file": f, "line": ln} for m, p, f, ln in ambiguous
                    ],
                }
            )

    # ---- 表 2：后端 → 前端 ----
    table2: list[dict] = []
    for (method, path), items in sorted(mapping_index.items()):
        reached_by = sorted(
            {
                c["method"]
                for c in normalized_calls
                if methods_compatible(method, c["method"])
                and any(paths_match(path, mp) for mp in c["matchPaths"])
            }
        )
        if reached_by:
            continue
        other_methods = sorted(
            {
                c["method"]
                for c in normalized_calls
                if any(paths_match(path, mp) for mp in c["matchPaths"]) and c["method"] not in reached_by
            }
        )
        docs = docs_lookup(docs_index, path)
        classification, basis = classify_backend_only(path, docs)
        first = sorted(items, key=lambda m: (m["file"], m["line"]))[0]
        table2.append(
            {
                "method": method,
                "path": path,
                "file": first["file"],
                "line": first["line"],
                "additionalDeclarationSites": [
                    {"file": m["file"], "line": m["line"]} for m in sorted(items, key=lambda m: (m["file"], m["line"]))[1:]
                ],
                "docsHit": docs["hit"],
                "docsFiles": docs["files"],
                "classification": classification,
                "basis": basis,
                "pathUsedByOtherMethods": other_methods,
            }
        )

    # ---- 表 3：方法级矩阵 ----
    shape_counts: dict[str, dict] = {}
    for call in normalized_calls:
        entry = shape_counts.setdefault(
            call["path"],
            {"path": call["path"], "frontendMethods": set(), "calls": [], "backendHits": {}},
        )
        entry["frontendMethods"].add(call["method"])
        entry["calls"].append(call)
        for (method, bpath), items in mapping_index.items():
            if any(paths_match(bpath, mp) for mp in call["matchPaths"]):
                for m in items:
                    entry["backendHits"][(m["method"], m["path"], m["file"], m["line"])] = m

    table3: list[dict] = []
    for path, entry in sorted(shape_counts.items()):
        matching = list(entry["backendHits"].values())
        backend_methods = sorted({m for mapping in matching for m in expand_methods(mapping["method"])})
        exact = [m for m in matching if m["path"] == path]
        via_wildcard = [m for m in matching if m["path"] != path]
        exact_methods = sorted({m for mapping in exact for m in expand_methods(mapping["method"])})
        wildcard_methods = sorted({m for mapping in via_wildcard for m in expand_methods(mapping["method"])})
        frontend_methods = sorted(entry["frontendMethods"])
        # 405 判据用**并集**：Spring 会按 `{var}` 路由到通配处理器，所以「只在通配路由上声明」
        # 依然是可命中的；用精确形状去判 405 会造出假缺陷。
        frontend_only = sorted(set(frontend_methods) - set(backend_methods))
        backend_only = sorted(set(backend_methods) - set(frontend_methods))
        table3.append(
            {
                "path": path,
                "frontendMethods": frontend_methods,
                "backendMethods": backend_methods,
                "backendMethodsExactShape": exact_methods,
                "backendMethodsViaWildcardRoute": wildcard_methods,
                "backendPaths": sorted({m["path"] for m in matching}),
                "backendPathsExactShape": sorted({m["path"] for m in exact}),
                "frontendOnlyMethods": frontend_only,
                "backendOnlyMethods": backend_only,
                "frontendCallCount": len(entry["calls"]),
                "calls": [{"file": c["file"], "line": c["line"], "method": c["method"]} for c in entry["calls"]],
            }
        )

    distinct_missing = sorted({(item["method"], item["path"]) for item in table1})

    # 前端调用的三种「不可归一」形态（分别计数，不静默丢）
    unresolved_prefix_sites = [u for u in frontend_unparseable if u["reason"] == "unresolved-prefix"]
    no_arglist_sites = [u for u in frontend_unparseable if u["reason"] == "no-argument-list"]
    computed_sites = [u for u in frontend_unparseable if u["reason"] == "computed-argument"]
    multi_expansion_calls = sorted(
        {c["file"] + ":" + str(c["line"]) for c in normalized_calls if len(c.get("matchPaths", [])) > 1}
    )

    return {
        "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
        "projectRoot": project_root.as_posix(),
        "inputs": {
            "backendRoot": rel(backend_root, project_root) if backend_root.exists() else str(backend_root),
            "frontendRoot": rel(frontend_root, project_root) if frontend_root.exists() else str(frontend_root),
            "docsRoot": rel(docs_root, project_root) if docs_root.exists() else str(docs_root),
            "frontendBasePath": base_path,
            "frontendBasePathSource": base_source,
            "frontendBasePathObserved": base_observed,
            "frontendTestFilesExcluded": ["*.spec.ts", "*.test.ts", "__tests__/**", "*.d.ts"],
            "openapiArtifact": None,
        },
        "counts": {
            "backendJavaFiles": len(backend_files),
            "controllerFiles": controller_files,
            "backendMappings": len(mappings),
            "backendDistinctMethodPath": len(mapping_index),
            "frontendSourceFiles": len(ts_files),
            "frontendCallSites": len(frontend_calls),
            "frontendCallSitesNormalised": len(normalized_calls),
            "frontendCallSitesUnparseable": len(computed_sites) + len(unresolved_prefix_sites) + len(no_arglist_sites),
            "frontendCallSitesComputedArgument": len(computed_sites),
            "frontendCallSitesUnresolvedPrefix": len(unresolved_prefix_sites),
            "frontendCallSitesNoArgumentList": len(no_arglist_sites),
            "frontendMissingCallSites": len(table1),
            "frontendMissingDistinct": len(distinct_missing),
            "backendUnreachedMappingSites": len(table2),
            "docsFilesScanned": docs_index["count"],
        },
        "table1_frontendToBackend": table1,
        "table1_distinct": [{"method": m, "path": p} for m, p in distinct_missing],
        "table2_backendToFrontend": table2,
        "table3_methodMatrix": table3,
        "blindSpots": {
            "unparseable": sorted(frontend_unparseable, key=lambda d: (d["file"], d["line"])),
            "backendUnresolved": backend_unresolved,
            "otherHttpCallSites": sorted(other_call_sites, key=lambda d: (d["file"], d["line"])),
            "templateValueResolutions": value_resolution_log,
            "multiExpansionCalls": multi_expansion_calls,
            "normalisationVariants": normalisation_variant_summary(normalized_calls),
        },
    }


def normalisation_variant_summary(calls: list[dict]) -> dict:
    variants = {
        "plain-literal": 0,
        "template-with-unresolved-placeholder": 0,
        "template-with-resolved-dispatch-values": 0,
        "template-resolved-prefix-only": 0,
    }
    for call in calls:
        if call.get("urlKind") == "string":
            variants["plain-literal"] += 1
            continue
        if call.get("resolvedPlaceholders") and call.get("unresolvedPlaceholders"):
            variants["template-with-resolved-dispatch-values"] += 1
        elif call.get("resolvedPlaceholders"):
            variants["template-resolved-prefix-only"] += 1
        else:
            variants["template-with-unresolved-placeholder"] += 1
    return variants


# --------------------------------------------------------------------------------------
# 报告
# --------------------------------------------------------------------------------------

def _esc(value) -> str:
    return str(value).replace("|", "\\|")


def _code(value) -> str:
    """行内代码。内部的 ` 用单引号替换 —— 源码片段里出现反引号会打断 markdown 表格单元。"""
    return "`" + _esc(value).replace("`", "'") + "`"


def render_markdown(result: dict, script_label: str) -> str:
    counts = result["counts"]
    inputs = result["inputs"]
    table1 = result["table1_frontendToBackend"]
    table2 = result["table2_backendToFrontend"]
    table3 = result["table3_methodMatrix"]
    blind = result["blindSpots"]

    lines: list[str] = []
    add = lines.append

    add("# Frontend ↔ Backend API reconciliation")
    add("")
    add(f"* Generated: `{result['generatedAt']}`")
    add(f"* Project root: `{result['projectRoot']}`")
    add(f"* Tool: `{script_label}` (report mode; `--self-test` is the mandatory calibration)")
    add("* Application was **not** started; no maven/npm was run; `src/main/java` and `frontend/src` were not modified.")
    add("")

    add("## 1. Methodology")
    add("")
    add("### 1.1 Backend extraction")
    add("")
    add(f"* Scope: every `*.java` under `{inputs['backendRoot']}` ({counts['backendJavaFiles']} files; "
        f"{counts['controllerFiles']} carry `@RestController`/`@Controller`).")
    add("* Comments (line + block) are blanked out before scanning, so commented-out mappings and "
        "javadoc examples cannot become findings.")
    add("* Class-level prefix = the `@RequestMapping` annotation that closes before the first "
        "`class|interface|enum|record` declaration; method-level = every mapping annotation after it.")
    add("* Supported method-level forms: `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping` "
        "(with `(`, `(\"…\")`, `(value = \"…\")`, `(path = \"…\")`, string arrays) **and** "
        "`@RequestMapping(method = RequestMethod.X)` — including `method = {RequestMethod.A, RequestMethod.B}`. "
        "A method-level `@RequestMapping` with no `method=` is recorded as `ANY` and matches every HTTP method.")
    add("* An absent path means “the class prefix itself”.")
    add("* `produces` / `consumes` / `headers` / `params` values are ignored (only `value` / `path` / positional "
        "strings are treated as paths).")
    add("")

    add("### 1.2 Frontend extraction")
    add("")
    add(f"* Scope: every `*.ts` and `*.vue` under `{inputs['frontendRoot']}` "
        f"({counts['frontendSourceFiles']} files) — this includes `frontend/src/views/**`.")
    add(f"* Excluded: {', '.join('`' + p + '`' for p in inputs['frontendTestFilesExcluded'])} "
        "(vitest specs mock `request` and never perform a real call).")
    add("* Receivers: `request` plus every default import of `@/utils/request` and every identifier "
        "bound to `axios.create(...)` in the same file (so wrappers around the shared client are captured).")
    add("* First argument forms captured: `'…'`, `\"…\"` and template literals with `${…}`.")
    add("")

    add("### 1.3 Normalisation")
    add("")
    add("| Side | Rule |")
    add("| --- | --- |")
    add("| Backend | `{var}` and `{var:regex}` (any brace nesting) → `{}`; missing leading `/` on a class "
        "prefix is added (`@RequestMapping(\"api/auth/api-keys\")` → `/api/auth/api-keys`); repeated slashes collapsed; "
        "trailing slash removed |")
    add("| Frontend | query string / `#fragment` stripped; `${…}` placeholders resolved where statically possible "
        "(see below); every remaining `${…}` → `{}`; then prefixed with the client `baseURL` "
        f"`{inputs['frontendBasePath']}` (source: {inputs['frontendBasePathSource']}) |")
    add("| Match | method must be equal (backend `ANY` matches all) **and** the backend path, with `{}` → `[^/]+`, "
        "must match at least one of the call's expanded paths exactly (no prefix matching — a prefix match would "
        "hide real 404s) |")
    add("")
    add("#### Bounded resolution of `${…}` placeholders")
    add("")
    add("A `${…}` in a frontend URL is one of two very different things: a **real identifier** (the value of a path "
        "variable such as `${keyId}` or `${instanceId}`, which must stay `{}`), or a **dispatch keyword** "
        "(`${action}` ∈ {`pause`,`resume`}, `${command}` ∈ {`json`,`csv`}) that actually names concrete backend "
        "routes. Treating a dispatch keyword as `{}` produces false 404/405 findings, so the tool resolves "
        "placeholders from five bounded, fully logged sources and **never guesses**:")
    add("")
    add("| # | Source | Example |")
    add("| ---: | --- | --- |")
    add("| 1 | module-level string constant whose *whole* initializer is a literal starting with `/` | `const apiBaseUrl = '/v1/routing-monitor'` |")
    add("| 2 | constant whose whole initializer is a ternary of exactly two string literals | `const action = paused ? 'resume' : 'pause'` |")
    add("| 3 | function parameter where **every** call site passes a string literal | `fn('json')` for `fn(command: string)` |")
    add("| 4 | Element Plus dropdown (`@command=\"fn\"` + literal `<el-dropdown-item command=\"X\">` in the same block) | `<el-dropdown-item command=\"csv\">` |")
    add("| 5 | first argument is a local variable and **every** assignment in the enclosing function is a literal/template | `let endpoint = …; request.post(endpoint)` |")
    add("")
    add("Source 1 deliberately requires the *whole* initializer to be the literal: `const response = await "
        "request.get('/auth/api-keys')` is the dominant shape in this codebase, and a pattern that merely harvests "
        "string literals from the initializer would invent a constant `response = '/auth/api-keys'`. For the same "
        "reason, a constant whose value does not start with `/` (e.g. `const id = 'x'`) is never substituted into a "
        "middle segment — middle/trailing `${…}` are treated as runtime identifiers.")
    add("")
    add("Every resolution is recorded in the JSON under `blindSpots.templateValueResolutions` and listed in §6.4, "
        "so a wrong resolution is visible rather than silent. A placeholder that resolves produces one *expanded* "
        "path per value (Cartesian product, capped at 16); a call is only reported as a mismatch when **no** "
        "expansion matches. The reported `path` is always the stable shape form (all placeholders → `{}`) so that "
        "table 3 groups consistently.")
    add("")
    add("Normalisation variants across all captured call sites: "
        + ", ".join(f"`{k}` = {v}" for k, v in sorted(result["blindSpots"]["normalisationVariants"].items()))
        + f". Calls expanded into multiple concrete paths: {len(result['blindSpots']['multiExpansionCalls'])}.")
    add("")

    add("### 1.4 Backend→Frontend classification (no endpoint is called “dead”)")
    add("")
    add("`/api/**` is also consumed by direct API clients, so an unreached mapping is **never** labelled dead. "
        "Each unreached `(method, path)` is classified against the published docs site:")
    add("")
    add("| Classification | Rule |")
    add("| --- | --- |")
    add("| `documented` | the path appears under `docs/**` — either written out with `{var}` / concrete values, "
        "or matching after normalising the docs' `{...}` to `{}` |")
    add("| `undocumented-and-unused` | not in `docs/**`, no frontend call reaches it, **and** the path is on the "
        "`/api/**` management surface (which `docs/{zh,en}/api-reference/` covers systematically) |")
    add("| `unknown` | not in `docs/**` and **not** on `/api/**` (`/v1/**`, `/actuator/**` …), where docs coverage "
        "is not systematic ⇒ absence from docs is not evidence |")
    add("")
    add("**OpenAPI axis: unknown.** `/v3/api-docs` is served by the running application; starting the app is out of "
        "scope for this tool and no generated specification is checked into the repository "
        "(`docs/{zh,en}/api-reference/openapi-spec.md` is prose, not a machine-readable spec). Therefore an endpoint "
        "classified `undocumented-and-unused` may still be a live client-facing contract — the OpenAPI claim could "
        "not be tested, and this report says so rather than guessing.")
    add("")
    add("Two limits of the docs check, stated rather than papered over:")
    add("")
    add("* A **wildcard** mention does *not* count as documentation of a concrete endpoint. "
        "`docs/**` contains `/api/health-status/**`; that is not treated as documenting "
        "`/api/health-status/stream`. The rule is deliberately strict: a wildcard describes a policy (auth, rate "
        "limiting), not an endpoint. So a path can be `undocumented-and-unused` while some *prefix* of it is in docs.")
    add(f"* A path with `{{}}` is matched against docs both as the literal `{{var}}` form and against **concrete "
        f"values** (e.g. `/api/call-history/{{}}/detail` matches `GET /api/call-history/{{id}}/detail` in "
        f"`changelog.md`). Both count as `documented`, which is the permissive direction for this axis.")
    add("")

    add("### 1.5 Known blind spots (stated, counted, listed — never silently dropped)")
    add("")
    add("| Blind spot | Why it cannot be normalised | Where it is listed |")
    add("| --- | --- | --- |")
    add(f"| Computed URL (first argument is a variable/expression) | the URL is only known at runtime | §6.1 "
        f"({len([u for u in blind['unparseable'] if u['reason'] == 'computed-argument'])} sites) |")
    add(f"| URL prefix assembled from an unknown variable | `${{unknown}}/tail` — the leading path is unknowable, so "
        f"the whole path is too | §6.1 ({len([u for u in blind['unparseable'] if u['reason'] == 'unresolved-prefix'])} sites) |")
    add(f"| `baseURL` join | `VITE_API_BASE_URL` is a build-time value; a build with a different value invalidates "
        f"every frontend path in this report | §1.3 ({inputs['frontendBasePath']} from {inputs['frontendBasePathSource']}) |")
    add(f"| Other HTTP exits (`fetch(...)`, `axios.<m>(...)`, a custom axios client invoked as a function) | outside the "
        f"`request.get/post/…` surface by construction | §6.3 ({len(blind['otherHttpCallSites'])} sites) |")
    add(f"| Backend class prefix carrying a `${{…}}` placeholder | the prefix is not statically known | §6.2 "
        f"({len(blind['backendUnresolved'])} sites) |")
    add(f"| Ambiguity of an unresolved `${{…}}` segment | a runtime value that is neither a bounded dispatch keyword "
        f"nor a path variable cannot be enumerated; such calls are matched as `{{}}` (path-variable semantics) | §6.4 "
        f"({len(blind['templateValueResolutions'])} resolutions logged) |")
    add("")
    add("Every `${…}` resolution the tool performed is listed in §6.4 — that list is the audit surface for the "
        "resolution rules.")
    add("")

    add("## 2. Extraction counts")
    add("")
    add("| Metric | Value |")
    add("| --- | ---: |")
    add(f"| Backend `.java` files scanned | {counts['backendJavaFiles']} |")
    add(f"| Backend files with `@RestController`/`@Controller` | {counts['controllerFiles']} |")
    add(f"| Backend mapping declarations (method × path) | {counts['backendMappings']} |")
    add(f"| Backend distinct `(method, path)` pairs | {counts['backendDistinctMethodPath']} |")
    add(f"| Frontend `.ts`/`.vue` files scanned | {counts['frontendSourceFiles']} |")
    add(f"| Frontend `request.*` call sites found | {counts['frontendCallSites']} |")
    add(f"| … normalised to `(method, path)` | {counts['frontendCallSitesNormalised']} |")
    add(f"| … **not** normalisable (blind spots) | {counts['frontendCallSitesUnparseable']} |")
    add(f"| `docs/**/*.md` files used for classification | {counts['docsFilesScanned']} |")
    add("")

    add("## 3. Table 1 — Frontend → Backend (frontend calls with no matching backend mapping)")
    add("")
    if not table1:
        add("_No findings._ A frontend call must have the same method and a path-equal backend mapping; "
            "the empty result is a statement about the coverage in §2, and the calibration in `--self-test` proves "
            "the check does fire.")
    else:
        add(f"{len(table1)} call site(s), {counts['frontendMissingDistinct']} distinct `(method, path)` pair(s). "
            "`method-not-declared-on-path` is the 405 class: the path exists, that method does not.")
        add("")
        add("| # | file:line | frontend method | normalised path | reason | backend methods on that path | raw URL |")
        add("| ---: | --- | --- | --- | --- | --- | --- |")
        for i, item in enumerate(table1, 1):
            backend_methods = ", ".join(item["backendMethodsOnPath"]) or "—"
            raw = item.get("rawUrl") or " | ".join(item.get("rawUrlCandidates") or []) or "—"
            add(f"| {i} | `{_esc(item['file'])}:{item['line']}` | `{item['method']}` | `{_esc(item['path'])}` | "
                f"`{item['reason']}` | {backend_methods} | `{_esc(raw)}` |")
        add("")
        add("Detail for each finding (expanded paths actually tested, unresolved placeholders, and the "
            "same-method literal routes that a *different* runtime value would have hit — i.e. the residual "
            "ambiguity that no static tool can remove):")
        add("")
        for i, item in enumerate(table1, 1):
            add(f"**{i}. `{item['method']} {_esc(item['path'])}` — `{_esc(item['file'])}:{item['line']}`**")
            add("")
            add(f"* expanded paths tested: {', '.join('`'+_esc(p)+'`' for p in item['matchPaths'])}")
            add(f"* unresolved placeholders → `{{}}`: "
                f"{', '.join('`'+_esc(p)+'`' for p in item['unresolvedPlaceholders']) or '—'}")
            add(f"* resolved placeholders: "
                f"{', '.join('`'+_esc(p)+'`' for p in item['resolvedPlaceholders']) or '—'}")
            refs = item.get("enclosingFunctionOtherFileReferences")
            add(f"* wrapping function: `{_esc(item.get('enclosingFunction') or '—')}` "
                f"(references from other frontend files: {'unknown' if refs is None else refs})")
            if item["ambiguousLiteralRoutes"]:
                add("* residual ambiguity — same-method literal routes that a different runtime value would reach: "
                    + "; ".join(
                        f"`{r['method']} {_esc(r['path'])}` (`{_esc(r['file'])}:{r['line']}`)"
                        for r in item["ambiguousLiteralRoutes"]
                    ))
            else:
                add("* residual ambiguity: none — no same-method literal route can absorb this call either.")
            add("")
        add("Distinct pairs:")
        add("")
        add("| method | path |")
        add("| --- | --- |")
        for item in result["table1_distinct"]:
            add(f"| `{item['method']}` | `{_esc(item['path'])}` |")
    add("")

    add("## 4. Table 2 — Backend → Frontend (mappings no frontend call reaches)")
    add("")
    if not table2:
        add("_No findings._")
    else:
        by_class: dict[str, int] = {}
        for item in table2:
            by_class[item["classification"]] = by_class.get(item["classification"], 0) + 1
        add(f"{len(table2)} unreached mapping(s) — **not dead**: {', '.join(f'`{k}` = {v}' for k, v in sorted(by_class.items()))}.")
        add("")
        add("| # | method | path | declared at | classification | docs hit | also used by |")
        add("| ---: | --- | --- | --- | --- | --- | --- |")
        for i, item in enumerate(table2, 1):
            others = ", ".join(item["pathUsedByOtherMethods"]) or "—"
            add(f"| {i} | `{item['method']}` | `{_esc(item['path'])}` | `{_esc(item['file'])}:{item['line']}` | "
                f"`{item['classification']}` | {'yes' if item['docsHit'] else 'no'} | {others} |")
    add("")

    add("## 5. Table 3 — Method-level matrix (frontend methods vs backend methods per path)")
    add("")
    mismatched = [row for row in table3 if row["frontendOnlyMethods"] or row["backendOnlyMethods"]]
    add(f"{len(table3)} path(s) called by the frontend; {len(mismatched)} with a method-set difference.")
    add("")
    add("“backend methods” is counted on the **union** of the exact shape and any `{var}` route that also "
        "matches the URI (Spring routes a literal like `list` to a `/{name}` handler too, so ignoring those routes "
        "would invent 405s). Both parts are shown separately, because a method that exists only on a `{var}` route "
        "is a different thing from one declared at the exact path.")
    add("")
    add("### 5.1 Frontend-only methods (the 405 class)")
    add("")
    frontend_only_rows = [row for row in table3 if row["frontendOnlyMethods"]]
    if not frontend_only_rows:
        add("_None._ No frontend method is absent from the backend's method set on the same path.")
    else:
        add("| path | frontend methods | backend methods (all matching routes) | frontend-only |")
        add("| --- | --- | --- | --- |")
        for row in frontend_only_rows:
            add(f"| `{_esc(row['path'])}` | {', '.join('`'+m+'`' for m in row['frontendMethods'])} | "
                f"{', '.join('`'+m+'`' for m in row['backendMethods']) or '—'} | "
                f"{', '.join('`'+m+'`' for m in row['frontendOnlyMethods'])} |")
        add("")
        add("These rows are exactly the Table 1 findings with reason `method-not-declared-on-path` — two views of "
            "the same defect.")
    add("")
    add("### 5.2 Backend-only methods (declared but never called by the console)")
    add("")
    backend_only_rows = [row for row in table3 if row["backendOnlyMethods"]]
    if not backend_only_rows:
        add("_None._")
    else:
        add(f"{len(backend_only_rows)} path(s). This is informational only — a method can be legitimate for direct "
            "API clients.")
        add("")
        add("| path | frontend methods | backend-only methods | backend paths matched |")
        add("| --- | --- | --- | --- |")
        for row in backend_only_rows:
            shapes = ", ".join(f"`{_esc(p)}`" for p in row["backendPaths"]) or "—"
            add(f"| `{_esc(row['path'])}` | {', '.join('`'+m+'`' for m in row['frontendMethods'])} | "
                f"{', '.join('`'+m+'`' for m in row['backendOnlyMethods'])} | {shapes} |")
        add("")
    add("### 5.3 Full matrix")
    add("")
    add("| path | frontend methods | backend (exact shape) | backend (via `{var}` route) | calls |")
    add("| --- | --- | --- | --- | ---: |")
    for row in table3:
        add(f"| `{_esc(row['path'])}` | {', '.join('`'+m+'`' for m in row['frontendMethods']) or '—'} | "
            f"{', '.join('`'+m+'`' for m in row['backendMethodsExactShape']) or '—'} | "
            f"{', '.join('`'+m+'`' for m in row['backendMethodsViaWildcardRoute']) or '—'} | "
            f"{row['frontendCallCount']} |")
    add("")

    add("## 6. Blind spots — calls that could not be normalised")
    add("")
    add("### 6.1 Frontend call sites not normalisable")
    add("")
    if not blind["unparseable"]:
        add("_None._")
    else:
        add("| # | file:line | method | reason | source snippet |")
        add("| ---: | --- | --- | --- | --- |")
        for i, item in enumerate(blind["unparseable"], 1):
            add(f"| {i} | {_code(item['file'] + ':' + str(item['line']))} | `{item['method']}` | "
                f"`{_esc(item['reason'])}` | {_code(item.get('snippet', ''))} |")
    add("")
    add(f"Result: **{counts['frontendCallSitesNormalised']} of {counts['frontendCallSites']} call sites were "
        f"normalised**, {counts['frontendCallSitesUnparseable']} could not be. A count of 0 is a statement about "
        "*this* repository and *these* resolution rules only — a different codebase (or a frontend that builds URLs "
        "from API responses, Redux/router state, or a config service) would land here instead.")
    add("")
    add("### 6.2 Backend mappings whose class prefix carries a placeholder")
    add("")
    if not blind["backendUnresolved"]:
        add("_None._")
    else:
        for item in blind["backendUnresolved"]:
            add(f"* `{_esc(item['file'])}:{item['line']}` — {_esc(item['reason'])}: `{_esc(item['raw'])}`")
    add("")
    add("### 6.3 Other HTTP exits (informational — outside the `request.*` surface, not reconciled)")
    add("")
    if not blind["otherHttpCallSites"]:
        add("_None._")
    else:
        add("| # | file:line | kind | snippet |")
        add("| ---: | --- | --- | --- |")
        for i, item in enumerate(blind["otherHttpCallSites"], 1):
            add(f"| {i} | {_code(item['file'] + ':' + str(item['line']))} | `{item['kind']}` | "
                f"{_code(item['snippet'])} |")
    add("")
    add("### 6.4 `${…}` value resolutions performed (full audit surface for the §1.3 rules)")
    add("")
    if not blind["templateValueResolutions"]:
        add("_None._")
    else:
        add("| # | file | source | resolved to | detail |")
        add("| ---: | --- | --- | --- | --- |")
        for i, item in enumerate(
            sorted(blind["templateValueResolutions"], key=lambda d: (d["file"], d["kind"], d.get("name", ""))), 1
        ):
            values = ", ".join(f"`{_esc(v)}`" for v in item["values"])
            if item["kind"] == "const":
                detail = f"constant `{item['name']}`"
            elif item["kind"] == "ternary":
                detail = f"constant `{item['name']}` = `{_esc(item.get('raw', ''))}`"
            elif item["kind"] == "call-site-literals":
                detail = f"`{item['function']}(…)` parameter `{item['param']}`"
            elif item["kind"] == "identifier-assignments":
                detail = (
                    f"`{item['name']}` assigned in the enclosing function: "
                    + ", ".join(f"`{_esc(v)}`" for v in item.get("rawValues", []))
                )
            else:
                detail = f"`@command=\"{item['function']}\"` parameter `{item['param']}`"
            add(f"| {i} | `{_esc(item['file'])}` | `{item['kind']}` | {values} | {detail} |")
    add("")
    if blind["multiExpansionCalls"]:
        add("Call sites expanded into more than one concrete path (each expansion was tested independently):")
        add("")
        for site in blind["multiExpansionCalls"]:
            add(f"* `{_esc(site)}`")
        add("")
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------------------
# 自校准
# --------------------------------------------------------------------------------------

SELF_TEST_CONTROLLER = """package com.example.selftest;

import org.springframework.web.bind.annotation.*;

/**
 * Commented-out mapping must NOT become a finding:
 * @GetMapping("/ghost")
 */
@RestController
@RequestMapping("api/self-test")
public class SelfTestController {

    @GetMapping
    public String list() { return "ok"; }

    @GetMapping("/items/{itemId}")
    public String getItem(@PathVariable String itemId) { return "ok"; }

    @PostMapping("/items")
    public String create() { return "ok"; }

    @PutMapping("/items/{itemId}")
    public String update(@PathVariable String itemId) { return "ok"; }

    @RequestMapping(value = "/legacy", method = RequestMethod.DELETE)
    public String legacy() { return "ok"; }

    @GetMapping(path = "/documented")
    public String documented() { return "ok"; }

    @GetMapping(value = "/never-called", produces = MediaType.APPLICATION_JSON_VALUE)
    public String neverCalled() { return "ok"; }

    @GetMapping("/typed/{id:[0-9]+}")
    public String typed(@PathVariable String id) { return "ok"; }

    @PostMapping("/pause")
    public String pause() { return "ok"; }

    @PostMapping("/resume")
    public String resume() { return "ok"; }

    @GetMapping("/export/json")
    public String exportJson() { return "ok"; }

    @GetMapping("/export/csv")
    public String exportCsv() { return "ok"; }

    @GetMapping("/literal-only/states")
    public String literalOnly() { return "ok"; }
}
"""

SELF_TEST_FRONTEND = """import request from '@/utils/request'

const apiBaseUrl = '/self-test'

const id = 'x'

export const list = () => request.get('/self-test')
export const create = () => request.post('/self-test/items', {})
export const getItem = () => request.get<RouterResponse<Item>>(`/self-test/items/${id}`)
export const wrongMethod = () => request.post(`/self-test/items/${id}`)
export const baseJoin = () => request.get(`${apiBaseUrl}/status`)
export const missing = () => request.delete('/self-test/does-not-exist')
export const computed = () => {
  let endpoint = ''
  return request.post(endpoint)
}
export const typedOk = () => request.get('/self-test/typed/42')
export const toggle = async () => {
  const action = paused.value ? 'resume' : 'pause'
  return request.post(`/self-test/${action}`)
}
export const literalTrap = () => request.get(`/self-test/literal-only/${instanceId}`)
export const viaVariable = async (row) => {
  let endpoint = ''
  if (row.kind === 'a') {
    endpoint = `${apiBaseUrl}/pause`
  } else {
    endpoint = `${apiBaseUrl}/does-not-exist-either`
  }
  return request.post(endpoint)
}
export const opaque = async (url: string) => request.get(url)
"""

SELF_TEST_VUE = """<template>
  <el-dropdown @command="handleExport">
    <el-dropdown-item command="json">JSON</el-dropdown-item>
    <el-dropdown-item command="csv">CSV</el-dropdown-item>
  </el-dropdown>
</template>

<script setup lang="ts">
import request from '@/utils/request'

const apiBaseUrl = '/self-test'

const handleExport = async (command: string) => {
  return request.get(`${apiBaseUrl}/export/${command}`)
}
</script>
"""

SELF_TEST_DOCS = """# API reference

## Self test

* `GET /api/self-test/documented` — documented endpoint.
"""


def run_self_test(project_root: Path, verbose: bool = True) -> int:
    scratch = Path(tempfile.mkdtemp(prefix="reconcile-self-test-"))
    results: list[tuple[str, bool, str]] = []

    def check(name: str, condition: bool, detail: str = "") -> None:
        results.append((name, bool(condition), detail))
        if verbose:
            print(f"  [{'PASS' if condition else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))

    try:
        backend_root = scratch / "src" / "main" / "java" / "com" / "example" / "selftest"
        frontend_root = scratch / "frontend" / "src" / "api"
        docs_root = scratch / "docs"
        backend_root.mkdir(parents=True, exist_ok=True)
        frontend_root.mkdir(parents=True, exist_ok=True)
        docs_root.mkdir(parents=True, exist_ok=True)

        (backend_root / "SelfTestController.java").write_text(SELF_TEST_CONTROLLER, encoding="utf-8")
        (frontend_root / "selfTest.ts").write_text(SELF_TEST_FRONTEND, encoding="utf-8")
        (frontend_root / "SelfTestPanel.vue").write_text(SELF_TEST_VUE, encoding="utf-8")
        (docs_root / "api-reference.md").write_text(SELF_TEST_DOCS, encoding="utf-8")
        (scratch / "frontend" / ".env").write_text("VITE_API_BASE_URL=/api\n", encoding="utf-8")

        print(f"self-test scratch tree: {scratch}")
        print(f"  backend  : {backend_root}")
        print(f"  frontend : {frontend_root}")
        print(f"  docs     : {docs_root}")
        print()
        print("Assertions:")

        result = analyze(
            project_root=scratch,
            backend_root=scratch / "src" / "main" / "java",
            frontend_root=scratch / "frontend" / "src",
            docs_root=docs_root,
        )
        # result 里没有原始 mapping 集合，这里重新对夹具跑一次后端抽取以精确断言。
        mapping_set: set[tuple[str, str]] = set()
        for path in sorted((scratch / "src" / "main" / "java").rglob("*.java")):
            file_mappings, _ = extract_backend_mappings(read_text(path), "x")
            for mapping in file_mappings:
                mapping_set.add((mapping["method"], mapping["path"]))
        call_set: set[tuple[str, str]] = set()
        for row in result["table3_methodMatrix"]:
            for method in row["frontendMethods"]:
                call_set.add((method, row["path"]))

        # --- 后端抽取 ---
        check(
            "backend: no-leading-slash class prefix normalised to /api/self-test",
            ("GET", "/api/self-test") in mapping_set,
            f'"api/self-test" + @GetMapping -> {sorted(p for m, p in mapping_set if p == "/api/self-test")}',
        )
        check(
            "backend: @RequestMapping(method = RequestMethod.DELETE) yields DELETE /api/self-test/legacy",
            ("DELETE", "/api/self-test/legacy") in mapping_set,
            str(sorted(m for m in mapping_set if "legacy" in m[1])),
        )
        check(
            "backend: @PutMapping('/items/{itemId}') path variable normalised to {}",
            ("PUT", "/api/self-test/items/{}") in mapping_set,
            str(sorted(m for m in mapping_set if "items" in m[1])),
        )
        check(
            "backend: @GetMapping(path = '/documented') (path= form) parsed",
            ("GET", "/api/self-test/documented") in mapping_set,
        )
        check(
            "backend: @GetMapping(value = '/never-called', produces = ...) parsed and produces ignored",
            ("GET", "/api/self-test/never-called") in mapping_set,
        )
        check(
            "backend: regex path variable {id:[0-9]+} normalised to {}",
            ("GET", "/api/self-test/typed/{}") in mapping_set,
            str(sorted(m for m in mapping_set if "typed" in m[1])),
        )
        check(
            "backend: commented-out @GetMapping('/ghost') is NOT extracted",
            not any("ghost" in m[1] for m in mapping_set),
        )

        # --- 前端抽取 ---
        check(
            "frontend: 13 request.* call sites found",
            result["counts"]["frontendCallSites"] == 13,
            f"got {result['counts']['frontendCallSites']}",
        )
        check(
            "frontend: exactly 2 call sites could not be normalised (1 computed variable + 1 opaque param)",
            len(result["blindSpots"]["unparseable"]) == 2
            and {u["reason"] for u in result["blindSpots"]["unparseable"]} == {"computed-argument"},
            str(result["blindSpots"]["unparseable"]),
        )
        check(
            "frontend: unnormalisable calls are counted in the report, not silently dropped",
            result["counts"]["frontendCallSitesUnparseable"] == 2,
            f"got {result['counts']['frontendCallSitesUnparseable']}",
        )
        check(
            "frontend: `let endpoint` resolved from its two static assignments (logged as a resolution)",
            any(
                item["kind"] == "identifier-assignments" and item["name"] == "endpoint"
                for item in result["blindSpots"]["templateValueResolutions"]
            ),
            str([i["kind"] for i in result["blindSpots"]["templateValueResolutions"]]),
        )
        check(
            "frontend: the good assignment candidate is NOT reported, the bad one IS",
            any(
                item["path"] == "/api/self-test/does-not-exist-either"
                for item in result["table1_frontendToBackend"]
            )
            and not any(item["path"] == "/api/self-test/pause" for item in result["table1_frontendToBackend"]),
            str([(i["method"], i["path"]) for i in result["table1_frontendToBackend"]]),
        )
        check(
            "frontend: local const `${apiBaseUrl}` resolved (no unresolved-prefix blind spot)",
            not any(u["reason"] == "unresolved-prefix" for u in result["blindSpots"]["unparseable"]),
        )
        check(
            "frontend: template ${id} normalised to {} and baseURL /api prepended",
            ("GET", "/api/self-test/items/{}") in call_set,
            str(sorted(p for m, p in call_set if "items" in p)),
        )
        check(
            "frontend: concrete literal matches backend {} path",
            ("GET", "/api/self-test/typed/42") in call_set,
        )

        # --- 表 1 ---
        table1 = result["table1_frontendToBackend"]
        t1_pairs = {(item["method"], item["path"]): item for item in table1}

        check(
            "table1 FIRES: DELETE /api/self-test/does-not-exist is reported",
            ("DELETE", "/api/self-test/does-not-exist") in t1_pairs,
            str(sorted(t1_pairs)),
        )
        check(
            "table1 FIRES: POST /api/self-test/items/{} (backend only declares PUT) is reported",
            ("POST", "/api/self-test/items/{}") in t1_pairs,
        )
        check(
            "table1 FIRES: GET /api/self-test/status from the resolved `${apiBaseUrl}` const is reported "
            "(proves const resolution ran; without it this would be an unresolved-prefix blind spot)",
            ("GET", "/api/self-test/status") in t1_pairs,
            str(sorted(t1_pairs)),
        )
        check(
            "table1 reason split: method-not-declared-on-path vs no-mapping-for-path",
            t1_pairs.get(("POST", "/api/self-test/items/{}"), {}).get("reason") == "method-not-declared-on-path"
            and t1_pairs.get(("DELETE", "/api/self-test/does-not-exist"), {}).get("reason") == "no-mapping-for-path",
        )
        check(
            "table1 reports file:line for every finding",
            all(item["file"].endswith("selfTest.ts") and item["line"] > 0 for item in table1) and bool(table1),
        )
        check(
            "table1 does NOT fire for matching pairs",
            not any(
                pair in t1_pairs
                for pair in (
                    ("GET", "/api/self-test"),
                    ("POST", "/api/self-test/items"),
                    ("GET", "/api/self-test/items/{}"),
                    ("GET", "/api/self-test/typed/42"),
                )
            ),
            str(sorted(t1_pairs)),
        )
        check(
            "table1 does NOT fire for the ternary dispatch `${action}` (resolved to pause/resume)",
            ("POST", "/api/self-test/{}") not in t1_pairs,
            str(sorted(t1_pairs)),
        )
        check(
            "table1 does NOT fire for the Element Plus `@command` dispatch `${command}` "
            "(resolved to export/json + export/csv)",
            ("GET", "/api/self-test/export/{}") not in t1_pairs,
            str(sorted(t1_pairs)),
        )
        check(
            "resolution log records both dispatch resolutions (auditable, not silent)",
            {item["kind"] for item in result["blindSpots"]["templateValueResolutions"]}
            >= {"ternary", "element-plus-@command", "const"},
            str(sorted({item["kind"] for item in result["blindSpots"]["templateValueResolutions"]})),
        )
        check(
            "table1 FIRES for a genuine path variable that only a literal backend route could absorb",
            ("GET", "/api/self-test/literal-only/{}") in t1_pairs,
            str(sorted(t1_pairs)),
        )
        check(
            "that finding carries the residual-ambiguity annotation (the literal route it could theoretically hit)",
            any(
                r["path"] == "/api/self-test/literal-only/states"
                for r in t1_pairs.get(("GET", "/api/self-test/literal-only/{}"), {}).get("ambiguousLiteralRoutes", [])
            ),
            str(t1_pairs.get(("GET", "/api/self-test/literal-only/{}"), {}).get("ambiguousLiteralRoutes")),
        )
        check(
            "table1 contains exactly the 5 expected findings",
            len(table1) == 5,
            f"got {len(table1)}: {sorted(t1_pairs)}",
        )

        # --- 表 2 ---
        table2 = {(item["method"], item["path"]): item for item in result["table2_backendToFrontend"]}
        check(
            "table2 FIRES: GET /api/self-test/never-called is unreached",
            ("GET", "/api/self-test/never-called") in table2,
        )
        check(
            "table2: docs-documented endpoint classified `documented`",
            table2.get(("GET", "/api/self-test/documented"), {}).get("classification") == "documented",
            str(table2.get(("GET", "/api/self-test/documented"))),
        )
        check(
            "table2: undocumented management endpoint classified `undocumented-and-unused`",
            table2.get(("GET", "/api/self-test/never-called"), {}).get("classification")
            == "undocumented-and-unused",
        )
        check(
            "table2: non-/api mapping would be classified `unknown` (rule check)",
            classify_backend_only("/v1/self-test/x", {"hit": False, "mode": "", "files": [], "fileCount": 0})[0]
            == "unknown",
        )
        check(
            "table2: matched endpoints do NOT appear",
            ("GET", "/api/self-test") not in table2 and ("POST", "/api/self-test/items") not in table2,
        )

        # --- 表 3 ---
        matrix = {row["path"]: row for row in result["table3_methodMatrix"]}
        check(
            "table3: /api/self-test/items/{} shows frontend {GET,POST} vs backend {GET,PUT}",
            matrix.get("/api/self-test/items/{}", {}).get("frontendMethods") == ["GET", "POST"]
            and matrix.get("/api/self-test/items/{}", {}).get("backendMethods") == ["GET", "PUT"]
            and matrix.get("/api/self-test/items/{}", {}).get("frontendOnlyMethods") == ["POST"],
            str(matrix.get("/api/self-test/items/{}")),
        )

        # --- JSON 可序列化 ---
        try:
            json.dumps(result, ensure_ascii=False)
            serialisable = True
        except (TypeError, ValueError):
            serialisable = False
        check("result is JSON-serialisable", serialisable)
    finally:
        shutil.rmtree(scratch, ignore_errors=True)

    removed = not scratch.exists()
    results.append(("scratch tree removed after the run", removed, str(scratch)))
    if verbose:
        print(f"  [{'PASS' if removed else 'FAIL'}] scratch tree removed after the run — {scratch}")

    failed = [name for name, ok, _detail in results if not ok]
    print()
    print(f"self-test: {len(results) - len(failed)}/{len(results)} assertions passed")
    if failed:
        print("FAILED assertions:")
        for name in failed:
            print(f"  - {name}")
        return 1
    print("PASS — calibration ok (mismatch fires, matching pair does not, fixtures removed)")
    return 0


# --------------------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Frontend <-> backend API reconciliation (Spring WebFlux mappings vs axios request.* calls)"
    )
    parser.add_argument("--project-root", default=".", help="repository root (default: .)")
    parser.add_argument("--self-test", action="store_true", help="run calibration fixtures and exit")
    parser.add_argument(
        "--json-out",
        default="scripts/audit/out/frontend-api-reconcile.json",
        help="JSON output path (relative to project root; empty string disables)",
    )
    parser.add_argument(
        "--md-out",
        default="scripts/audit/out/frontend-api-reconcile.md",
        help="Markdown output path (relative to project root; empty string disables)",
    )
    parser.add_argument("--backend-root", default=None, help="override backend java root")
    parser.add_argument("--frontend-root", default=None, help="override frontend src root")
    parser.add_argument("--docs-root", default=None, help="override docs root")
    parser.add_argument(
        "--fail-on-mismatch",
        action="store_true",
        help="exit 1 when Table 1 is non-empty (default: exit 0, report mode is informational)",
    )
    args = parser.parse_args(argv)

    project_root = Path(args.project_root).resolve()

    if args.self_test:
        return run_self_test(project_root)

    backend_root = Path(args.backend_root).resolve() if args.backend_root else project_root / "src" / "main" / "java"
    frontend_root = Path(args.frontend_root).resolve() if args.frontend_root else project_root / "frontend" / "src"
    docs_root = Path(args.docs_root).resolve() if args.docs_root else project_root / "docs"

    for label, path in (("backend", backend_root), ("frontend", frontend_root), ("docs", docs_root)):
        if not path.exists():
            print(f"!! {label} root not found: {path}", file=sys.stderr)
            return 2

    result = analyze(project_root, backend_root, frontend_root, docs_root)

    counts = result["counts"]
    print(f"backend : {counts['backendJavaFiles']} java files, {counts['controllerFiles']} controllers, "
          f"{counts['backendMappings']} mapping declarations ({counts['backendDistinctMethodPath']} distinct method+path)")
    print(f"frontend: {counts['frontendSourceFiles']} files, {counts['frontendCallSites']} request.* call sites "
          f"({counts['frontendCallSitesNormalised']} normalised, {counts['frontendCallSitesUnparseable']} not normalisable)")
    print(f"docs    : {counts['docsFilesScanned']} markdown files used for classification")
    print(f"table 1 : {counts['frontendMissingCallSites']} frontend->backend mismatches "
          f"({counts['frontendMissingDistinct']} distinct)")
    print(f"table 2 : {counts['backendUnreachedMappingSites']} backend mappings no frontend call reaches")
    print(f"table 3 : {len(result['table3_methodMatrix'])} frontend paths compared method-by-method")

    if args.json_out:
        json_path = project_root / args.json_out
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {rel(json_path, project_root)}")
    if args.md_out:
        md_path = project_root / args.md_out
        md_path.parent.mkdir(parents=True, exist_ok=True)
        md_path.write_text(
            render_markdown(result, "scripts/audit/reconcile-frontend-api.py"), encoding="utf-8"
        )
        print(f"wrote {rel(md_path, project_root)}")

    if args.fail_on_mismatch and result["table1_frontendToBackend"]:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
