#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前后端「契约形状」对账器（JAiRouter）。

## 这类缺陷是什么

控制台（`frontend/src`）为接口载荷声明了 TypeScript 类型，后端返回 Java DTO。
两者漂移时，UI 会读一个**永远不会到达**的字段并渲染 `undefined` —— 这是本仓
真实发生过的缺陷类（例：`Dashboard.vue` 曾读 `monitoringOverview.value.systemStatus`
/ `requestStats` / `resourceUsage`，而后端 `/api/monitoring/overview` 从不返回这些键）。

本脚本静态抽取两侧的「形状」（JSON 可见字段名集合），做四类对账：

1. **表 1 字段缺失（backend → frontend）**：前端类型声称某字段，对应后端 DTO 不产出
   ⇒ UI 读到 `undefined`（高危）。
2. **表 2 字段多余（frontend 少字段）**：后端产出而前端类型未声明（低危，不影响读取）。
3. **表 3 类型名对不上**：`request<T>` 的 `T` 没有任何同名后端 DTO —— 单独列出，
   并附「端点实际返回什么」以区分「名字漂移」与「前端自造形状」。
4. **表 4 命名空间嵌套错位**：与 locale 文件相关，**按任务要求本工具不解析**
   `frontend/src/locales/**`，只报告被排除的文件数（见报告第 6 节）。

## 用法

    # 报告模式（默认写 scripts/audit/out/contract-shapes.{md,json}）
    python scripts/audit/reconcile-contract-shapes.py

    # 强制校准：合成夹具 → 断言判据真的会响 / 不该响的不响 → 清理并断言已删净
    python scripts/audit/reconcile-contract-shapes.py --self-test

    # 换根目录（自我校准内部用；也可对别的检出跑）
    python scripts/audit/reconcile-contract-shapes.py --root D:\\some\\checkout

    # 只出 JSON / 只出 Markdown
    python scripts/audit/reconcile-contract-shapes.py --no-md
    python scripts/audit/reconcile-contract-shapes.py --md-out out/x.md --json-out out/x.json

**不启动应用、不跑 maven/npm/vue-tsc、不修改 `src/main/java` 与 `frontend/src`。**

## 判据（改代码必须同步改报告口径）

### 后端形状

* 候选 DTO = `class` / `record`，且满足任一：带 `@Data` / `@Getter` / `@Value` /
  `@Builder` / `@Schema`，**或被控制器方法返回类型引用**。
* JSON 可见字段 =
  * `record` 组件（`@JsonProperty` 改名、`@JsonIgnore` 排除）；
  * 类的非 static 字段：`public` 字段、有同名 getter 的字段、或类带 Lombok
    `@Data`/`@Getter`/`@Value`（生成 getter）；
  * 类的 `public` 无参 `getX()` / `isX()` getter（Jackson 默认可见性）；
  * `@JsonProperty("...")` 改写 JSON 名（`@JsonProperty` 无值 = 不改名）；
  * `@JsonIgnore`（字段或 getter）排除整个属性；`@JsonIgnoreProperties({"a"})` 排除点名属性；
  * boolean 字段 `isXxx` 归一为 `xxx`（`is` getter 语义）。
* 返回类型逐层剥掉透明包装：`ResponseEntity` / `Mono` / `Flux` / `RouterResponse` /
  `Optional` / `List` / `Set` / `Page` / `Slice` / `ServerSentEvent` 等，落到 `X`。
  `X` 是 `Map` / `Object` / 标量 / `void` / `interface` 时**没有形状可抽**，计入盲区。

### 前端形状

* `frontend/src/**/*.ts`（含 `.vue` 的 `<script>` 区）里的 `interface` 与 `type`；
  处理可选 `?`、数组、联合、交叉、`extends`、对象别名、索引签名。
* `request.<method><T>(path)`：括号内泛型即载荷类型；透明包装
  `RouterResponse` / `ApiResponse` / `PageResponse` / `PagedResult` 等会被剥掉。
* **不解析** `frontend/src/locales/**`（表 4 显式排除）；`__tests__` / `*.spec.ts`
  的声明仍抽取，但其 request 调用不计入对账（不是运行时读取点）。

### 匹配

1. **同名优先**：前端类型名 == 后端 DTO 简单名；
2. 同名找不到时，用 `request<T>` → 端点 → 控制器返回类型链；
3. **端点权威**：若同名 DTO 与端点实际返回的 DTO 不是同一个，以端点实际返回者做
   形状对账（否则会拿错 DTO 报假缺陷 —— 实例：前端 `RateLimitConfig` 同名命中
   `common/dto/RateLimitConfig`，但端点 `/config/instance/{}/rate-limit` 实际返回
   `InstanceRateLimitDTO`），并把「名字漂移」写入表 3；
4. 同名候选多于一个且无法用端点消歧 ⇒ 不猜，计入盲区（`ambiguous-name`）。

### 嵌套

字段两侧都能解析到具体对象时递归比较（数组 / 分页包装会剥掉），路径记在 `位置`
列（如 `PoolDefinition.members[].PoolMember`）。深度上限 4，带环检测。

> 说明：本工具只做**形状（字段名）**对账，不做类型（`string` vs `number`）对账。
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set, Tuple

# ======================================================================================
# 通用工具
# ======================================================================================

EXCLUDED_DIRS = {"node_modules", "dist", "target", ".git"}

JAVA_MODIFIERS = {
    "public", "private", "protected", "static", "final", "abstract", "sealed",
    "non-sealed", "strictfp", "default", "synchronized", "native", "transient", "volatile",
}

DATA_ANNOTATIONS = {"Data", "Getter", "Value", "Builder", "Schema"}
LOMBOK_GETTER_ANNOTATIONS = {"Data", "Getter", "Value"}

HTTP_MAPPING = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}

# Java 返回类型里可以一层层剥掉、不改变载荷对象的包装
JAVA_TRANSPARENT_WRAPPERS = {
    "Mono", "Flux", "ResponseEntity", "RouterResponse", "Optional",
    "List", "Set", "Collection", "Iterable", "Page", "Slice", "Stream",
    "Publisher", "ServerSentEvent", "CompletionStage", "CompletableFuture",
    "ArrayList", "LinkedList", "HashSet", "LinkedHashSet", "TreeSet",
    "DeferredResult", "Callable", "Array",
}

# Java 侧「没有字段形状」的返回/字段类型
JAVA_NO_SHAPE = {
    "Map", "HashMap", "LinkedHashMap", "ConcurrentHashMap", "TreeMap", "SortedMap",
    "Object", "String", "CharSequence", "JsonNode", "JsonObject", "ArrayNode", "ObjectNode",
    "Integer", "int", "Long", "long", "Double", "double", "Float", "float",
    "Boolean", "boolean", "Short", "short", "Byte", "byte", "Character", "char",
    "BigDecimal", "BigInteger", "Number",
    "LocalDateTime", "LocalDate", "LocalTime", "Instant", "ZonedDateTime", "OffsetDateTime", "Date",
    "UUID", "URI", "URL", "Duration", "Period",
    "Void", "void", "MultipartFile", "File", "Path", "Class", "Throwable", "Exception",
}

JAVA_MAP_TYPES = {"Map", "HashMap", "LinkedHashMap", "ConcurrentHashMap", "TreeMap", "SortedMap"}

PRIMITIVE_TS = {
    "string", "number", "boolean", "bigint", "symbol", "null", "undefined",
    "any", "unknown", "never", "object", "void", "Object", "String", "Number", "Boolean",
}

# 前端响应/分页包装：剥掉后取载荷对象
FE_ENVELOPES = {
    "RouterResponse", "ApiResponse", "AxiosResponse",
    "PageResponse", "PagedResult", "PaginationResponse", "Page", "Readonly",
}

FE_MAP_TYPES = {"Record", "Map", "Partial", "Required", "Pick", "Omit", "ReadonlyMap"}
FE_TYPE_DECL_RE = re.compile(
    r"(?m)^[ \t]*(?:export\s+)?(?:declare\s+)?(interface|type)\s+([A-Za-z_$][\w$]*)"
)
TS_MEMBER_NEW_RE = re.compile(
    r"""^(?:readonly\s+)?(?:[A-Za-z_$][\w$]*|"[^"]*"|'[^']*')\s*\??\s*:"""
)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, max(0, index)) + 1


def rel_posix(path: Path, root: Path) -> str:
    # 先用纯字符串相对路径（files 都来自 root.rglob，必然是 root 的子路径），
    # 避免 Windows 上每次 resolve() 都走 _getfinalpathname 的系统调用。
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        pass
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except (ValueError, OSError):
        return path.as_posix()


def iter_source_files(root: Path, subdir: str, suffixes: Tuple[str, ...]) -> List[Path]:
    base = root / subdir
    if not base.is_dir():
        return []
    out: List[Path] = []
    for p in base.rglob("*"):
        if not p.is_file():
            continue
        parts = set(p.relative_to(base).parts[:-1])
        if parts & EXCLUDED_DIRS:
            continue
        if p.suffix not in suffixes:
            continue
        out.append(p)
    return sorted(out)


# --------------------------------------------------------------------------------------
# 注释剥离 / 字符串遮罩（保持字符偏移与行号不变）
# --------------------------------------------------------------------------------------

def strip_java_comments(src: str) -> str:
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if ch in "\"'":
            q = ch
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def mask_java_strings(src: str) -> str:
    """把字符串/字符量的内容替换为空格（保留引号），偏移与行号不变。"""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if ch in "\"'":
            q = ch
            i += 1
            while i < n:
                c = src[i]
                if c == "\\":
                    for k in (i, i + 1):
                        if k < n and out[k] != "\n":
                            out[k] = " "
                    i += 2
                    continue
                if c == q:
                    i += 1
                    break
                if c != "\n":
                    out[i] = " "
                i += 1
            continue
        i += 1
    return "".join(out)


def strip_ts_comments(src: str) -> str:
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if ch in "\"'`":
            q = ch
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if ch == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def mask_ts_strings(src: str) -> str:
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if ch in "\"'`":
            q = ch
            i += 1
            while i < n:
                c = src[i]
                if c == "\\":
                    for k in (i, i + 1):
                        if k < n and out[k] != "\n":
                            out[k] = " "
                    i += 2
                    continue
                if c == q:
                    i += 1
                    break
                if c != "\n":
                    out[i] = " "
                i += 1
            continue
        i += 1
    return "".join(out)


def match_delim(s: str, i: int) -> int:
    """s[i] 是开括号，返回匹配的闭括号下标；失败 -1。忽略字符串内容。"""
    pairs = {"(": ")", "[": "]", "{": "}", "<": ">"}
    if i >= len(s) or s[i] not in pairs:
        return -1
    open_ch = s[i]
    close_ch = pairs[open_ch]
    depth = 0
    j = i
    n = len(s)
    while j < n:
        c = s[j]
        if c in "\"'`":
            q = c
            j += 1
            while j < n:
                if s[j] == "\\":
                    j += 2
                    continue
                if s[j] == q:
                    j += 1
                    break
                j += 1
            continue
        if c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return -1


def split_top_level(s: str, seps: Sequence[str] = (",",)) -> List[str]:
    """按顶层分隔符切分；忽略 ()[]{}<> 与字符串内部。`=>` 先中性化。"""
    s = s.replace("=>", "\u21d2")
    out: List[str] = []
    buf: List[str] = []
    depth = 0
    angle = 0
    i, n = 0, len(s)
    quote: Optional[str] = None
    while i < n:
        c = s[i]
        if quote:
            buf.append(c)
            if c == "\\" and i + 1 < n:
                buf.append(s[i + 1])
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in "\"'`":
            quote = c
            buf.append(c)
            i += 1
            continue
        if c in "([{":
            depth += 1
            buf.append(c)
            i += 1
            continue
        if c in ")]}":
            depth = max(0, depth - 1)
            buf.append(c)
            i += 1
            continue
        if c == "<":
            angle += 1
            buf.append(c)
            i += 1
            continue
        if c == ">" and angle > 0:
            angle -= 1
            buf.append(c)
            i += 1
            continue
        if c in seps and depth == 0 and angle == 0:
            out.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    if buf or out:
        out.append("".join(buf))
    return [x.replace("\u21d2", "=>") for x in out]


def cut_top_level(s: str, ch: str) -> int:
    """返回第一个顶层 ch 的下标（忽略括号与字符串），无则 -1。"""
    s2 = s.replace("=>", "\u21d2")
    depth = 0
    angle = 0
    i, n = 0, len(s2)
    quote: Optional[str] = None
    while i < n:
        c = s2[i]
        if quote:
            if c == "\\":
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in "\"'`":
            quote = c
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth = max(0, depth - 1)
        elif c == "<":
            angle += 1
        elif c == ">" and angle > 0:
            angle -= 1
        elif c == ch and depth == 0 and angle == 0:
            return i
        i += 1
    return -1


def split_generic(t: str) -> Optional[Tuple[str, str]]:
    """`Name<Arg>` → (Name, Arg)；要求第一个 '<' 与最后一个 '>' 配对。"""
    t = t.strip()
    lt = -1
    depth = 0
    quote: Optional[str] = None
    for i, c in enumerate(t):
        if quote:
            if c == quote:
                quote = None
            continue
        if c in "\"'":
            quote = c
            continue
        if c == "<":
            if depth == 0:
                lt = i
            depth += 1
        elif c == ">" and depth > 0:
            depth -= 1
    if lt <= 0 or depth != 0 or not t.endswith(">"):
        return None
    name = t[:lt].strip()
    if not re.fullmatch(r"[A-Za-z_$][\w$.]*", name):
        return None
    # 确认第一个 '<' 就闭合在末尾
    if match_delim(t, lt) != len(t) - 1:
        return None
    return name, t[lt + 1:-1].strip()


def simple_name(qualified: str) -> str:
    return qualified.strip().split(".")[-1]


def normalize_path(p: str) -> str:
    p = (p or "").strip()
    if not p:
        return ""
    p = p.split("?")[0].split("#")[0]
    p = re.sub(r"\{[^}]*\}", "{}", p)
    if not p.startswith("/"):
        p = "/" + p
    p = re.sub(r"/+", "/", p)
    if len(p) > 1:
        p = p.rstrip("/")
    return p


# ======================================================================================
# Java 侧：注解 / 类型 / 字段
# ======================================================================================

@dataclass
class Ann:
    name: str
    args: Optional[str]
    start: int
    end: int


def scan_annotations(masked: str, original: str, lo: int = 0, hi: Optional[int] = None) -> List[Ann]:
    hi = len(masked) if hi is None else hi
    out: List[Ann] = []
    i = lo
    while i < hi:
        if masked[i] != "@":
            i += 1
            continue
        j = i + 1
        while j < hi and (masked[j].isalnum() or masked[j] in "_$."):
            j += 1
        raw = masked[i + 1:j]
        name = raw.split(".")[-1] if raw else ""
        if not name:
            i += 1
            continue
        k = j
        while k < hi and masked[k].isspace():
            k += 1
        args: Optional[str] = None
        end = j
        if k < hi and masked[k] == "(":
            close = match_delim(masked, k)
            if close < 0:
                close = hi - 1
            args = original[k + 1:close]
            end = close + 1
        out.append(Ann(name=name, args=args, start=i, end=end))
        i = end
    return out


def trailing_annotations(masked: str, original: str, decl_start: int) -> List[Ann]:
    """取紧邻声明之前的注解（允许中间夹修饰符），从后往前。"""
    window_start = max(0, decl_start - 4000)
    anns = scan_annotations(masked, original, window_start, decl_start)
    by_end: Dict[int, Ann] = {a.end: a for a in anns}
    accepted: List[Ann] = []
    pos = decl_start
    guard = 0
    while pos > window_start and guard < 64:
        guard += 1
        while pos > window_start and masked[pos - 1].isspace():
            pos -= 1
        if pos in by_end:
            a = by_end[pos]
            accepted.append(a)
            pos = a.start
            continue
        m = re.search(r"([A-Za-z_$][\w$]*)\s*$", masked[max(window_start, pos - 64):pos])
        if m and m.group(1) in JAVA_MODIFIERS:
            pos = max(window_start, pos - 64) + m.start(1)
            continue
        break
    return list(reversed(accepted))


def ann_map(anns: Sequence[Ann]) -> Dict[str, Optional[str]]:
    d: Dict[str, Optional[str]] = {}
    for a in anns:
        d.setdefault(a.name, a.args)
    return d


def ann_strings(args: Optional[str]) -> List[str]:
    if not args:
        return []
    return [m.group(1) for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', args)]


def ann_first_string(args: Optional[str]) -> Optional[str]:
    vals = ann_strings(args)
    return vals[0] if vals else None


def ann_is_true(args: Optional[str]) -> bool:
    if not args:
        return True
    return not re.search(r"\bfalse\b", args)


def ann_rename(anns: Dict[str, Optional[str]]) -> Optional[str]:
    if "JsonProperty" not in anns:
        return None
    return ann_first_string(anns.get("JsonProperty"))


def decap(name: str) -> str:
    if not name:
        return name
    if len(name) > 1 and name[1].isupper():
        return name
    return name[0].lower() + name[1:]


@dataclass
class JavaField:
    name: str
    json_name: str
    type_text: str
    line: int
    static: bool
    anns: Dict[str, Optional[str]]
    public: bool = False


@dataclass
class JavaMember:
    header_start: int
    header_end: int
    body_start: Optional[int] = None
    body_end: Optional[int] = None


@dataclass
class JavaType:
    name: str
    kind: str                      # class | record | interface | enum
    file: str
    line: int
    decl_start: int
    body_open: int
    body_close: int
    annotations: Dict[str, Optional[str]]
    outer: str = ""
    fields: List[JavaField] = field(default_factory=list)
    getters: List[JavaField] = field(default_factory=list)
    shape: Dict[str, JavaField] = field(default_factory=dict)
    ignore_props: Set[str] = field(default_factory=set)
    is_return_type: bool = False

    @property
    def annotated(self) -> bool:
        return bool(set(self.annotations) & DATA_ANNOTATIONS)


def split_members(src_m: str, body_open: int, body_close: int) -> List[JavaMember]:
    members: List[JavaMember] = []
    i = body_open + 1
    start = i
    paren = 0
    bracket = 0
    while i < body_close:
        c = src_m[i]
        if c == "(":
            paren += 1
        elif c == ")":
            paren = max(0, paren - 1)
        elif c == "[":
            bracket += 1
        elif c == "]":
            bracket = max(0, bracket - 1)
        elif paren == 0 and bracket == 0:
            if c == ";":
                members.append(JavaMember(start, i))
                start = i + 1
            elif c == "{":
                header = src_m[start:i]
                j = match_delim(src_m, i)
                if j < 0:
                    j = body_close
                if "(" in header:
                    members.append(JavaMember(start, i, i, j))
                    start = j + 1
                    i = j
                elif re.search(r"\b(class|interface|enum|record|@interface)\b", header):
                    start = j + 1
                    i = j
                else:
                    # 初始化块 / 数组初始化：保留 start，跳过块
                    i = j + 1
                    continue
            elif c == "}":
                pass
        i += 1
    return members


def strip_anns_from_header(header: str, anns: Sequence[Ann]) -> str:
    chars = list(header)
    for a in anns:
        lo = max(0, a.start)
        hi = min(len(chars), a.end)
        for k in range(lo, hi):
            if chars[k] != "\n":
                chars[k] = " "
    return "".join(chars)


def strip_modifiers(text: str) -> str:
    t = text.strip()
    while True:
        m = re.match(r"^(public|private|protected|final|transient|volatile|synchronized|native)\b\s*", t)
        if not m:
            return t
        t = t[m.end():].lstrip()


def parse_field_header(header: str, anns: Sequence[Ann], base_line: int) -> List[JavaField]:
    raw = header
    if re.search(r"\bstatic\b", raw):
        return []
    text = strip_anns_from_header(header, anns)
    text = re.sub(r"\s+", " ", text).strip()
    if not text:
        return []
    is_public = bool(re.search(r"\bpublic\b", text))
    text = strip_modifiers(text)
    if not text:
        return []
    eq = cut_top_level(text, "=")
    if eq >= 0:
        text = text[:eq]
    parts = split_top_level(text, (",",))
    if not parts:
        return []
    first = parts[0].strip()
    m = re.search(r"([A-Za-z_$][\w$]*)\s*$", first)
    if not m:
        return []
    name = m.group(1)
    type_text = first[:m.start()].strip()
    if not type_text or name in JAVA_MODIFIERS or name in {"class", "interface", "enum", "record"}:
        return []
    out = [JavaField(
        name=name,
        json_name=name,
        type_text=type_text,
        line=base_line,
        static=False,
        anns=ann_map(anns),
        public=is_public,
    )]
    for extra in parts[1:]:
        nm = extra.strip()
        if re.fullmatch(r"[A-Za-z_$][\w$]*", nm or ""):
            out.append(JavaField(nm, nm, type_text, base_line, False, ann_map(anns), is_public))
    return out


def parse_method_header(header: str, anns: Sequence[Ann], base_line: int) -> Optional[Tuple[str, str, str]]:
    """返回 (方法名, 返回类型文本, 参数文本)。"""
    text = strip_anns_from_header(header, anns)
    text = re.sub(r"\s+", " ", text).strip()
    p = text.find("(")
    if p < 0:
        return None
    sig = text[:p].strip()
    close = match_delim(text, p)
    params = text[p + 1:close] if close > p else ""
    m = re.search(r"([A-Za-z_$][\w$]*)\s*$", sig)
    if not m:
        return None
    name = m.group(1)
    ret = sig[:m.start()].strip()
    ret = strip_modifiers(ret)
    if not ret or name in JAVA_MODIFIERS:
        return None
    return name, ret, params.strip()


def parse_components(text: str, base_line: int) -> List[JavaField]:
    out: List[JavaField] = []
    for raw in split_top_level(text, (",",)):
        if not raw.strip():
            continue
        anns = scan_annotations(raw, raw)
        cleaned = strip_anns_from_header(raw, anns)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        m = re.search(r"([A-Za-z_$][\w$]*)\s*$", cleaned)
        if not m:
            continue
        name = m.group(1)
        type_text = cleaned[:m.start()].strip()
        am = ann_map(anns)
        out.append(JavaField(name, name, type_text, base_line, False, am, True))
    return out


def compute_shape(jt: JavaType, src_m: str, src_c: str) -> None:
    if jt.kind in ("interface", "enum"):
        jt.shape = {}
        return
    ignore_from_type: Set[str] = set()
    if "JsonIgnoreProperties" in jt.annotations:
        ignore_from_type |= set(ann_strings(jt.annotations.get("JsonIgnoreProperties")))
    jt.ignore_props = ignore_from_type

    lombok = bool(set(jt.annotations) & LOMBOK_GETTER_ANNOTATIONS)
    getter_props = {g.json_name for g in jt.getters if ann_rename(g.anns) is None}
    ignored: Set[str] = set()
    visible: Dict[str, JavaField] = {}

    for f in jt.fields:
        if f.static:
            continue
        if "JsonIgnore" in f.anns and ann_is_true(f.anns.get("JsonIgnore")):
            ignored.add(f.name)
            ignored.add(ann_rename(f.anns) or f.name)
            continue
        prop = f.name
        if prop.startswith("is") and len(prop) > 2 and prop[2].isupper():
            # 只有 primitive boolean 的 Lombok getter 才是 isX()（Jackson 属性名 = x）；
            # Boolean 包装类型生成 getIsX()，属性名仍是 isX。
            if re.search(r"(^|[\s<,])boolean([\s>,]|$)", f.type_text):
                prop = decap(prop[2:])
        json = ann_rename(f.anns) or prop
        f.json_name = json
        if json in ignore_from_type or f.name in ignore_from_type:
            continue
        getter_ok = prop in getter_props or f.name in getter_props
        if f.public or lombok or getter_ok:
            visible.setdefault(json, f)

    for g in jt.getters:
        if "JsonIgnore" in g.anns and ann_is_true(g.anns.get("JsonIgnore")):
            ignored.add(g.json_name)
            continue
        json = ann_rename(g.anns) or g.json_name
        g.json_name = json
        if json in ignore_from_type:
            continue
        visible.setdefault(json, g)

    for name in ignored:
        visible.pop(name, None)
    jt.shape = visible


def parse_java_file(path: Path, root: Path) -> List[JavaType]:
    raw = read_text(path)
    if not raw:
        return []
    src_c = strip_java_comments(raw)
    src_m = mask_java_strings(src_c)
    rel = rel_posix(path, root)
    types: List[JavaType] = []

    for m in re.finditer(r"(?<![\w$.@])(class|record|interface|enum)\s+([A-Za-z_$][\w$]*)", src_m):
        kind, name = m.group(1), m.group(2)
        decl_start = m.start(1)
        anns = trailing_annotations(src_m, src_c, decl_start)
        k = m.end(2)
        comps: List[JavaField] = []
        if kind == "record":
            while k < len(src_m) and src_m[k].isspace():
                k += 1
            if k < len(src_m) and src_m[k] == "<":
                close = match_delim(src_m, k)
                k = close + 1 if close > 0 else k
            while k < len(src_m) and src_m[k].isspace():
                k += 1
            if k < len(src_m) and src_m[k] == "(":
                close = match_delim(src_m, k)
                if close > 0:
                    comps = parse_components(src_c[k + 1:close], line_of(src_c, k + 1))
                    k = close + 1
        brace = src_m.find("{", k)
        if brace < 0:
            continue
        close_brace = match_delim(src_m, brace)
        if close_brace < 0:
            continue
        types.append(JavaType(
            name=name,
            kind=kind,
            file=rel,
            line=line_of(src_m, decl_start),
            decl_start=decl_start,
            body_open=brace,
            body_close=close_brace,
            annotations=ann_map(anns),
        ))

    # 填 outer（最近的外层类型）
    for t in types:
        best: Optional[JavaType] = None
        for o in types:
            if o is t:
                continue
            if o.body_open < t.decl_start < o.body_close:
                if best is None or (o.body_close - o.body_open) < (best.body_close - best.body_open):
                    best = o
        t.outer = best.name if best else ""

    for t in types:
        if t.kind in ("interface", "enum"):
            continue
        members = split_members(src_m, t.body_open, t.body_close)
        for mem in members:
            header = src_c[mem.header_start:mem.header_end]
            if not header.strip():
                continue
            mem_anns = scan_annotations(header, header)
            base_line = line_of(src_c, mem.header_start)
            clean_header = strip_anns_from_header(header, mem_anns)
            eq = cut_top_level(clean_header, "=")
            par = clean_header.find("(")
            looks_method = mem.body_start is not None or (par >= 0 and (eq < 0 or par < eq))
            if looks_method:
                parsed = parse_method_header(header, mem_anns, base_line)
                if not parsed:
                    continue
                mname, ret, params = parsed
                if params:
                    continue
                if "static" in header.split("(")[0]:
                    continue
                if not re.search(r"\bpublic\b", header):
                    continue
                if ret in ("void", "Void"):
                    continue
                prop: Optional[str] = None
                if mname.startswith("get") and len(mname) > 3:
                    prop = decap(mname[3:])
                elif mname.startswith("is") and len(mname) > 2 and mname[2].isupper():
                    prop = decap(mname[2:])
                if not prop:
                    continue
                t.getters.append(JavaField(
                    name=mname,
                    json_name=prop,
                    type_text=ret,
                    line=base_line,
                    static=False,
                    anns=ann_map(mem_anns),
                    public=True,
                ))
            else:
                t.fields.extend(parse_field_header(header, mem_anns, base_line))

        if t.kind == "record":
            t.fields.extend(comps)

        compute_shape(t, src_m, src_c)

    return types


# ======================================================================================
# 后端：返回类型解析 / 端点
# ======================================================================================

@dataclass
class ShapeRef:
    name: Optional[str]
    reason: str
    chain: Tuple[str, ...] = ()
    depth: int = 0
    args: Tuple[str, ...] = ()


def resolve_java_shape(type_text: str) -> ShapeRef:
    t = (type_text or "").strip()
    t = re.sub(r"^@[\w.]+(\([^)]*\))?\s*", "", t)
    chain: List[str] = []
    depth = 0
    while True:
        t = t.strip()
        while t.endswith("[]"):
            t = t[:-2].strip()
            depth += 1
        if not t:
            break
        g = split_generic(t)
        if g:
            raw_name, arg = g
            sname = simple_name(raw_name)
            if sname in JAVA_TRANSPARENT_WRAPPERS:
                chain.append(sname)
                t = arg
                continue
            # 非透明泛型容器（Map / 自定义泛型 DTO）
            if sname in JAVA_MAP_TYPES:
                return ShapeRef(None, "map", tuple(chain), depth)
            # 具体泛型 DTO（如 PagedResult<T>）：落到该 DTO 本身，而不是丢掉形状。
            # 同时记录泛型实参名，供「前端 T == 实参」时优先使用元素 DTO。
            arg_names: List[str] = []
            for part in split_top_level(arg, (",",)):
                r = resolve_java_shape(part)
                if r.name:
                    arg_names.append(r.name)
            return ShapeRef(sname, "ok", tuple(chain) + (sname,), depth, tuple(arg_names))
        break
    t = t.strip()
    if not t or t in {"?", "Object"}:
        return ShapeRef(None, "object", tuple(chain), depth)
    if t.startswith("?"):
        return ShapeRef(None, "wildcard", tuple(chain), depth)
    if t in JAVA_NO_SHAPE:
        reason = "map" if simple_name(t) in JAVA_MAP_TYPES else (
            "void" if t in ("void", "Void") else "scalar"
        )
        return ShapeRef(None, reason, tuple(chain), depth)
    if re.fullmatch(r"[A-Z]", t):
        return ShapeRef(None, "type-variable", tuple(chain), depth)
    if not re.fullmatch(r"[A-Za-z_$][\w$.]*", t):
        return ShapeRef(None, "unparsed-type", tuple(chain), depth)
    sname = simple_name(t)
    if sname in JAVA_NO_SHAPE:
        return ShapeRef(None, "scalar", tuple(chain), depth)
    return ShapeRef(sname, "ok", tuple(chain), depth)


@dataclass
class Endpoint:
    http: str
    path: str
    controller: str
    file: str
    line: int
    method_name: str
    return_text: str
    dto_name: Optional[str] = None
    dto_reason: str = ""
    dto_args: Tuple[str, ...] = ()
    map_keys: Tuple[str, ...] = ()
    body_text: str = ""


def extract_map_keys(body: str) -> Tuple[str, ...]:
    keys: List[str] = []
    for m in re.finditer(r'\.put\(\s*"([^"]+)"\s*,', body):
        keys.append(m.group(1))
    for m in re.finditer(r"Map\.of\(\s*([^;]*?)\)", body, re.S):
        args = m.group(1)
        parts = split_top_level(args, (",",))
        for idx in range(0, len(parts) - 1, 2):
            sm = re.fullmatch(r'\s*"([^"]*)"\s*', parts[idx])
            if sm:
                keys.append(sm.group(1))
    for m in re.finditer(r'entry\(\s*"([^"]+)"\s*,', body):
        keys.append(m.group(1))
    seen: Set[str] = set()
    out: List[str] = []
    for k in keys:
        if k and k not in seen:
            seen.add(k)
            out.append(k)
    return tuple(out)


def join_path(prefix: str, sub: str) -> str:
    p = "/" + "/".join(x for x in (prefix.strip("/"), sub.strip("/")) if x)
    return normalize_path(p)


def parse_endpoints(jt: JavaType, src_m: str, src_c: str, root: Path) -> List[Endpoint]:
    class_anns = jt.annotations
    if "RestController" not in class_anns and "Controller" not in class_anns:
        return []
    prefix = ""
    if "RequestMapping" in class_anns:
        vals = ann_strings(class_anns.get("RequestMapping"))
        if vals:
            prefix = vals[0]

    out: List[Endpoint] = []
    members = split_members(src_m, jt.body_open, jt.body_close)
    for mem in members:
        if mem.body_start is None:
            continue
        header = src_c[mem.header_start:mem.header_end]
        mem_anns = scan_annotations(header, header)
        if not mem_anns:
            continue
        mapping = next((a for a in mem_anns if a.name in HTTP_MAPPING or a.name == "RequestMapping"), None)
        if mapping is None:
            continue
        parsed = parse_method_header(header, mem_anns, 1)
        if not parsed:
            continue
        mname, ret, _params = parsed
        if mapping.name in HTTP_MAPPING:
            http = HTTP_MAPPING[mapping.name]
            method_override = ""
        else:
            http = "ANY"
            method_override = ""
            if mapping.args:
                mm = re.search(r"method\s*=\s*\{?([^,})]*)", mapping.args)
                if mm:
                    methods = re.findall(r"RequestMethod\.(\w+)", mm.group(1))
                    if len(methods) == 1:
                        http = methods[0]
        paths = ann_strings(mapping.args)
        if not paths:
            paths = [""]
        body = src_c[mem.body_start:mem.body_end]
        keys = extract_map_keys(body)
        for p in paths:
            full = join_path(prefix, p)
            ep = Endpoint(
                http=http,
                path=full,
                controller=jt.name,
                file=jt.file,
                line=line_of(src_c, mem.header_start),
                method_name=mname,
                return_text=ret,
                map_keys=keys,
                body_text=body,
            )
            ep.dto_name = resolve_java_shape(ret).name
            ep.dto_reason = resolve_java_shape(ret).reason
            ep.dto_args = resolve_java_shape(ret).args
            out.append(ep)
    return out


# ======================================================================================
# 前端：类型声明
# ======================================================================================

@dataclass
class TsField:
    name: str
    type_text: str
    optional: bool
    line: int
    kind: str = "prop"       # prop | index | method


@dataclass
class TsType:
    name: str
    kind: str                # interface | alias
    file: str
    line: int
    fields: Dict[str, TsField] = field(default_factory=dict)
    bases: List[str] = field(default_factory=list)
    arms: List[str] = field(default_factory=list)
    alias_target: Optional[str] = None
    object_body: bool = False
    index_signature: Optional[str] = None
    unparsed_members: int = 0


@dataclass
class FeRef:
    kind: str                # named | inline | opaque
    name: str = ""
    fields: Dict[str, TsField] = field(default_factory=dict)
    file: str = ""
    line: int = 0
    reason: str = ""
    optional: bool = False


def split_ts_members(body: str) -> List[Tuple[str, int]]:
    s = body.replace("=>", "\u21d2")
    out: List[Tuple[str, int]] = []
    buf: List[str] = []
    start = 0
    i, n = 0, len(s)
    depth = 0
    quote: Optional[str] = None

    def emit(end_idx: int) -> None:
        nonlocal buf, start
        text = "".join(buf)
        if text.strip():
            out.append((text, start))
        buf = []
        start = end_idx + 1

    while i < n:
        c = s[i]
        if quote:
            buf.append(c)
            if c == "\\" and i + 1 < n:
                buf.append(s[i + 1])
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in "\"'`":
            quote = c
            buf.append(c)
            i += 1
            continue
        if c in "([{<":
            depth += 1
            buf.append(c)
            i += 1
            continue
        if c in ")]}>":
            depth = max(0, depth - 1)
            buf.append(c)
            i += 1
            continue
        if c in ";," and depth == 0:
            emit(i)
            i += 1
            continue
        if c == "\n" and depth == 0:
            text = "".join(buf).strip()
            if text:
                j = i + 1
                while j < n and s[j] in " \t\r\n":
                    j += 1
                ne = s.find("\n", j)
                next_line = s[j: (n if ne < 0 else ne)].strip()
                last_line = text.splitlines()[-1].strip() if text else ""
                new_member = bool(TS_MEMBER_NEW_RE.match(next_line)) or next_line.startswith("}")
                cont = text.endswith(("|", "&", ":", "<", "(", ",", "=")) or (
                    last_line.startswith(("|", "&")) and not new_member
                )
                if not cont and (new_member or not next_line):
                    emit(i)
            buf.append(c)
            i += 1
            continue
        buf.append(c)
        i += 1
    if "".join(buf).strip():
        out.append(("".join(buf), start))
    return out


def parse_ts_body(body: str, base_index: int, src: str) -> Tuple[Dict[str, TsField], Optional[str], int]:
    fields: Dict[str, TsField] = {}
    index_sig: Optional[str] = None
    unparsed = 0
    for text, off in split_ts_members(body):
        t = re.sub(r"\s+", " ", text).strip()
        if not t:
            continue
        line = line_of(src, base_index + off + (len(text) - len(text.lstrip())))
        if t.startswith("["):
            m = re.match(r"^\[([^\]]+)\]\s*\??\s*:\s*(.+)$", t)
            index_sig = m.group(2).strip() if m else t
            continue
        m = re.match(
            r"""^(?:readonly\s+)?([A-Za-z_$][\w$]*|"[^"]*"|'[^']*')\s*(\?)?\s*:\s*(.+)$""",
            t,
        )
        if m:
            name = m.group(1).strip("\"'")
            fields[name] = TsField(
                name=name,
                type_text=m.group(3).strip(),
                optional=bool(m.group(2)),
                line=line,
                kind="prop",
            )
            continue
        m = re.match(r"^(?:readonly\s+)?([A-Za-z_$][\w$]*)\s*(\?)?\s*\(", t)
        if m:
            fields[m.group(1)] = TsField(
                name=m.group(1),
                type_text="",
                optional=bool(m.group(2)),
                line=line,
                kind="method",
            )
            continue
        unparsed += 1
    return fields, index_sig, unparsed


def read_ts_type_expression(src_m: str, i: int) -> Tuple[str, int]:
    """从 i 起读取一个类型表达式，返回 (文本, 结束下标)。"""
    n = len(src_m)
    while i < n and src_m[i].isspace():
        i += 1
    start = i
    depth = 0
    angle = 0
    quote: Optional[str] = None
    while i < n:
        c = src_m[i]
        if quote:
            if c == "\\":
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c in "\"'`":
            quote = c
            i += 1
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth = max(0, depth - 1)
        elif c == "<":
            angle += 1
        elif c == ">" and angle > 0:
            angle -= 1
        elif c == "\n" and depth == 0 and angle == 0:
            text = src_m[start:i].rstrip()
            if text and not text.endswith(("|", "&", "=", "(", "<", ",")):
                j = i + 1
                while j < n and src_m[j] in " \t\r\n":
                    j += 1
                ne = src_m.find("\n", j)
                nxt = src_m[j:(n if ne < 0 else ne)].strip()
                if not (nxt.startswith(("|", "&"))):
                    return text, i
        i += 1
    return src_m[start:i].rstrip(), n


def parse_ts_file(path: Path, root: Path) -> Tuple[List[TsType], List["RequestSite"], List["RequestSite"]]:
    raw = read_text(path)
    if not raw:
        return [], [], []
    src_c = strip_ts_comments(raw)
    src_m = mask_ts_strings(src_c)
    rel = rel_posix(path, root)
    types: List[TsType] = []

    for m in FE_TYPE_DECL_RE.finditer(src_m):
        kind, name = m.group(1), m.group(2)
        name_pos = m.start(2)
        # 排除 `import type X,` 之类：名字后必须是 = / { / < / extends
        j = m.end(2)
        while j < len(src_m) and src_m[j] in " \t":
            j += 1
        if j >= len(src_m) or src_m[j] not in "= {<" and not src_m.startswith("extends", j):
            continue
        line = line_of(src_m, name_pos)
        if kind == "interface":
            k = j
            if k < len(src_m) and src_m[k] == "<":
                close = match_delim(src_m, k)
                k = close + 1 if close > 0 else k
            brace = src_m.find("{", k)
            if brace < 0:
                continue
            header = src_c[k:brace]
            bases: List[str] = []
            em = re.search(r"\bextends\b(.*)$", header, re.S)
            if em:
                for b in split_top_level(em.group(1), (",",)):
                    b = b.strip()
                    if re.fullmatch(r"[A-Za-z_$][\w$.]*", b):
                        bases.append(simple_name(b))
            close_brace = match_delim(src_m, brace)
            if close_brace < 0:
                continue
            fields, idx, unparsed = parse_ts_body(src_c[brace + 1:close_brace], brace + 1, src_c)
            types.append(TsType(
                name=name, kind="interface", file=rel, line=line,
                fields=fields, bases=bases, object_body=True,
                index_signature=idx, unparsed_members=unparsed,
            ))
        else:
            eq = src_m.find("=", j)
            if eq < 0:
                continue
            rhs, _end = read_ts_type_expression(src_m, eq + 1)
            # 用原文取字符串字面量内容（masked 里被抹掉了），此处类型文本无字符串需求
            rhs = src_c[eq + 1:eq + 1 + len(rhs)].strip()
            t = TsType(name=name, kind="alias", file=rel, line=line)
            if rhs.startswith("{") and match_delim(rhs, 0) == len(rhs) - 1:
                fields, idx, unparsed = parse_ts_body(rhs[1:-1], 1, src_c)
                t.fields = fields
                t.index_signature = idx
                t.unparsed_members = unparsed
                t.object_body = True
            elif rhs.startswith("(") and match_delim(rhs, 0) == len(rhs) - 1:
                inner = rhs[1:-1].strip()
                if inner.startswith("{") and match_delim(inner, 0) == len(inner) - 1:
                    fields, idx, unparsed = parse_ts_body(inner[1:-1], 1, src_c)
                    t.fields = fields
                    t.index_signature = idx
                    t.unparsed_members = unparsed
                    t.object_body = True
                else:
                    t.alias_target = inner
            else:
                arms = split_top_level(rhs, ("|",))
                if len(arms) > 1:
                    t.arms = [a.strip() for a in arms if a.strip()]
                else:
                    inter = split_top_level(rhs, ("&",))
                    if len(inter) > 1:
                        obj_fields: Dict[str, TsField] = {}
                        all_obj = True
                        for part in inter:
                            part = part.strip()
                            if part.startswith("{") and match_delim(part, 0) == len(part) - 1:
                                f2, _, _ = parse_ts_body(part[1:-1], 1, src_c)
                                obj_fields.update(f2)
                            elif re.fullmatch(r"[A-Za-z_$][\w$.]*", part):
                                t.bases.append(simple_name(part))
                            else:
                                all_obj = False
                        if all_obj and obj_fields:
                            t.fields = obj_fields
                            t.object_body = True
                    elif re.fullmatch(r"[A-Za-z_$][\w$.]*", rhs):
                        t.alias_target = simple_name(rhs)
            types.append(t)

    requests, plain = scan_request_sites(src_c, src_m, rel)
    return types, requests, plain


# ======================================================================================
# 前端：request 调用
# ======================================================================================

@dataclass
class RequestSite:
    file: str
    line: int
    http: str
    path: str
    path_raw: str
    dynamic: bool
    generic: Optional[str] = None
    is_test: bool = False


def is_test_file(rel: str) -> bool:
    low = rel.lower()
    return "/__tests__/" in low or low.endswith(".spec.ts") or low.endswith(".test.ts")


def read_ts_string_at(s: str, i: int) -> Tuple[Optional[str], int]:
    if i >= len(s) or s[i] not in "\"'`":
        return None, i
    q = s[i]
    i += 1
    buf: List[str] = []
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            buf.append(s[i + 1])
            i += 2
            continue
        if c == q:
            return "".join(buf), i + 1
        buf.append(c)
        i += 1
    return None, i


def scan_request_sites(src_c: str, src_m: str, rel: str) -> Tuple[List[RequestSite], List[RequestSite]]:
    requests: List[RequestSite] = []
    plain: List[RequestSite] = []
    test = is_test_file(rel)

    generic_re = re.compile(r"\brequest\s*\.\s*(get|post|put|patch|delete)\s*<")
    for m in generic_re.finditer(src_m):
        http = m.group(1).upper()
        lt = src_m.rfind("<", m.start(), m.end())
        close = match_delim(src_m, lt) if lt >= 0 else -1
        if close < 0:
            continue
        generic = src_c[lt + 1:close].strip()
        k = close + 1
        while k < len(src_m) and src_m[k].isspace():
            k += 1
        if k >= len(src_m) or src_m[k] != "(":
            continue
        a = k + 1
        while a < len(src_m) and src_m[a].isspace():
            a += 1
        if a < len(src_c) and src_c[a] in "\"'`":
            val, _ = read_ts_string_at(src_c, a)
            raw = val or ""
            path = re.sub(r"\$\{[^}]*\}", "{}", raw) if src_c[a] == "`" else raw
            requests.append(RequestSite(
                file=rel, line=line_of(src_m, m.start()), http=http,
                path=normalize_path(path), path_raw=raw, dynamic=False,
                generic=generic, is_test=test,
            ))
        else:
            # 动态 URL：取第一个实参文本
            end = a
            depth = 0
            while end < len(src_m):
                c = src_m[end]
                if c in "([{":
                    depth += 1
                elif c in ")]}":
                    if depth == 0:
                        break
                    depth -= 1
                elif c == "," and depth == 0:
                    break
                end += 1
            expr = src_c[a:end].strip()
            requests.append(RequestSite(
                file=rel, line=line_of(src_m, m.start()), http=http,
                path="", path_raw=expr, dynamic=True,
                generic=generic, is_test=test,
            ))

    plain_re = re.compile(r"\brequest\s*\.\s*(get|post|put|patch|delete)\s*\(")
    for m in plain_re.finditer(src_m):
        http = m.group(1).upper()
        a = m.end()
        while a < len(src_m) and src_m[a].isspace():
            a += 1
        if a < len(src_c) and src_c[a] in "\"'`":
            val, _ = read_ts_string_at(src_c, a)
            raw = val or ""
            path = re.sub(r"\$\{[^}]*\}", "{}", raw) if src_c[a] == "`" else raw
            plain.append(RequestSite(
                file=rel, line=line_of(src_m, m.start()), http=http,
                path=normalize_path(path), path_raw=raw, dynamic=False,
                generic=None, is_test=test,
            ))
        else:
            plain.append(RequestSite(
                file=rel, line=line_of(src_m, m.start()), http=http,
                path="", path_raw="", dynamic=True, generic=None, is_test=test,
            ))
    return requests, plain


# ======================================================================================
# 形状解析（前端）
# ======================================================================================

def ts_lookup(fe_types: Dict[str, List[TsType]], name: str, prefer_file: str = "") -> List[TsType]:
    cands = fe_types.get(name, [])
    same = [c for c in cands if c.file == prefer_file]
    if same:
        return same
    return cands


def ts_fields_of(
    decl: TsType,
    fe_types: Dict[str, List[TsType]],
    stack: Optional[Set[str]] = None,
) -> Dict[str, TsField]:
    stack = set() if stack is None else stack
    if decl.name in stack:
        return dict(decl.fields)
    stack.add(decl.name)
    out: Dict[str, TsField] = {}
    for base in decl.bases:
        for bd in ts_lookup(fe_types, base, decl.file):
            out.update(ts_fields_of(bd, fe_types, stack))
    out.update(decl.fields)
    return out


def fe_ref_from_type(
    text: str,
    fe_types: Dict[str, List[TsType]],
    file: str,
    line: int,
    array_hint: bool = False,
) -> List[FeRef]:
    t = re.sub(r"\s+", " ", (text or "").strip())
    if not t:
        return [FeRef("opaque", reason="empty", file=file, line=line)]
    # 去括号
    while t.startswith("(") and match_delim(t, 0) == len(t) - 1:
        t = t[1:-1].strip()
    while t.endswith("[]"):
        t = t[:-2].strip()
        array_hint = True
    # 顶层联合
    arms = split_top_level(t, ("|",))
    if len(arms) > 1:
        out: List[FeRef] = []
        for arm in arms:
            out.extend(fe_ref_from_type(arm, fe_types, file, line, array_hint))
        return out
    g = split_generic(t)
    if g:
        name, arg = g
        sname = simple_name(name)
        if sname in ("Array", "ReadonlyArray", "Page", "PageResponse", "PagedResult", "PaginationResponse"):
            return fe_ref_from_type(arg, fe_types, file, line, True)
        if sname in FE_MAP_TYPES:
            return [FeRef("opaque", reason="record/map", file=file, line=line)]
        decls = ts_lookup(fe_types, sname, file)
        if decls:
            return [FeRef("named", name=sname, file=file, line=line)]
        return [FeRef("opaque", reason=f"unknown-generic:{sname}", file=file, line=line)]
    if t.startswith("{"):
        if match_delim(t, 0) == len(t) - 1:
            fields, _, _ = parse_ts_body(t[1:-1], 0, t)
            return [FeRef("inline", fields=fields, file=file, line=line)]
        return [FeRef("opaque", reason="inline-object", file=file, line=line)]
    if t in PRIMITIVE_TS:
        return [FeRef("opaque", reason=f"primitive:{t}", file=file, line=line)]
    if "'" in t or '"' in t or "[" in t or "=>" in t:
        return [FeRef("opaque", reason="literal/function/indexed", file=file, line=line)]
    if re.fullmatch(r"[A-Za-z_$][\w$.]*", t):
        name = simple_name(t)
        decls = ts_lookup(fe_types, name, file)
        if decls:
            return [FeRef("named", name=name, file=file, line=line)]
        return [FeRef("opaque", reason=f"external-type:{name}", file=file, line=line)]
    return [FeRef("opaque", reason=f"unparsed:{t[:60]}", file=file, line=line)]


def unwrap_fe_response(text: str) -> str:
    t = re.sub(r"\s+", " ", (text or "").strip())
    guard = 0
    while guard < 8:
        guard += 1
        if t.endswith("[]"):
            t = t[:-2].strip()
            continue
        g = split_generic(t)
        if g:
            name, arg = g
            sname = simple_name(name)
            if sname in FE_ENVELOPES:
                t = arg.strip()
                continue
        break
    return t


# ======================================================================================
# 对账
# ======================================================================================

@dataclass
class Row1:
    fe_type: str
    field: str
    optional: bool
    fe_file: str
    fe_line: int
    be_type: str
    be_file: str
    be_line: int
    via: str
    location: str
    endpoint: str
    note: str = ""


@dataclass
class Row2:
    be_type: str
    field: str
    be_file: str
    be_line: int
    fe_type: str
    fe_file: str
    fe_line: int
    via: str
    location: str
    endpoint: str


@dataclass
class Row3:
    fe_type: str
    fe_file: str
    fe_line: int
    req_file: str
    req_line: int
    http: str
    path: str
    endpoint_dto: str
    category: str
    note: str = ""


@dataclass
class MatchPair:
    fe_type: str
    be_type: str
    via: str
    endpoint: str
    location: str


@dataclass
class Analysis:
    root: Path
    java_files: int
    ts_files: int
    java_types: List[JavaType]
    endpoints: List[Endpoint]
    fe_types: Dict[str, List[TsType]]
    requests: List[RequestSite]
    plain_requests: List[RequestSite]
    t1: Dict[Tuple[Any, ...], Row1]
    t2: Dict[Tuple[Any, ...], Row2]
    t3: List[Row3]
    pairs: List[MatchPair]
    blind: Dict[str, List[str]]
    stats: Dict[str, Any]
    locale_files: List[str]
    test_request_sites: List[RequestSite]
    map_heuristic: List[Tuple[str, str, str, List[str], List[str]]]


def paths_compatible(ep_path: str, fe_path: str) -> bool:
    a = [s for s in ep_path.strip("/").split("/") if s != ""]
    b = [s for s in fe_path.strip("/").split("/") if s != ""]
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if x == "{}" or y == "{}":
            continue
        if x != y:
            return False
    return True


def path_match_score(ep_path: str, fe_path: str) -> float:
    """端点与前端路径的匹配分；-1 = 不匹配。

    优先级：字面量相等 > 双方都是 `{}` > 端点 `{}` 吃字面量 > 端点字面量吃前端 `{}`。
    最后一档分低，避免把 `/config/adapter/list` 这种具体路由当成
    `/config/adapter/{name}` 的载荷来源（实例：AdapterDetail 的形状对账）。
    """
    a = [s for s in ep_path.strip("/").split("/") if s != ""]
    b = [s for s in fe_path.strip("/").split("/") if s != ""]
    if len(a) != len(b):
        return -1.0
    score = 0.0
    for x, y in zip(a, b):
        if x == y:
            score += 3.0
        elif x == "{}" and y == "{}":
            score += 2.0
        elif x == "{}":
            score += 1.0
        elif y == "{}":
            score += 0.0
        else:
            return -1.0
    # 同等匹配下，少一些通配段的端点更具体
    score -= 0.01 * sum(1 for x in a if x == "{}")
    return score


def endpoint_matches(ep: Endpoint, http: str, fe_path: str) -> bool:
    if ep.http not in (http, "ANY"):
        return False
    return path_match_score(ep.path, fe_path) >= 0


def select_endpoints(
    endpoints: Sequence[Endpoint], http: str, fe_path: str, base_path: str
) -> List[Endpoint]:
    scored: List[Tuple[float, int, Endpoint]] = []
    for idx, ep in enumerate(endpoints):
        if ep.http not in (http, "ANY"):
            continue
        s = max(path_match_score(ep.path, base_path), path_match_score(ep.path, fe_path))
        if s >= 0:
            scored.append((s, idx, ep))
    if not scored:
        return []
    best = max(s for s, _, _ in scored)
    return [ep for s, _, ep in scored if s == best]



def backend_lookup(types: List[JavaType]) -> Dict[str, List[JavaType]]:
    d: Dict[str, List[JavaType]] = {}
    for t in types:
        d.setdefault(t.name, []).append(t)
    return d


def shape_candidates(
    by_name: Dict[str, List[JavaType]], name: str, dto_only: bool = False
) -> List[JavaType]:
    """同名且能解析出形状的 class/record。

    `dto_only=True` 时只认「DTO 候选」＝ 带 @Data/@Getter/@Value/@Builder/@Schema，
    或被控制器返回类型引用。用于**根类型同名匹配**，避免把服务实现里的同名嵌套类
    当成接口载荷（实例：`EnhancedJwtBlacklistService.BlacklistStats` vs 端点返回 Map）。
    嵌套字段解析不受此限制（字段类型就是载荷的一部分）。
    """
    out: List[JavaType] = []
    for t in by_name.get(name, []):
        if t.kind not in ("class", "record") or not t.shape:
            continue
        if dto_only and not (t.annotated or t.is_return_type):
            continue
        out.append(t)
    return out


def run_analysis(root: Path) -> Analysis:
    java_files = iter_source_files(root, "src/main/java", (".java",))
    ts_files = iter_source_files(root, "frontend/src", (".ts", ".vue"))
    locale_files: List[str] = []
    locale_dir = root / "frontend" / "src" / "locales"
    if locale_dir.is_dir():
        locale_files = [
            rel_posix(p, root) for p in sorted(locale_dir.rglob("*")) if p.is_file()
        ]
    ts_files_real: List[Path] = []
    for p in ts_files:
        rel = rel_posix(p, root)
        if "/locales/" in f"/{rel}":
            continue
        ts_files_real.append(p)

    java_types: List[JavaType] = []
    for p in java_files:
        java_types.extend(parse_java_file(p, root))
    by_name = backend_lookup(java_types)

    endpoints: List[Endpoint] = []
    for t in java_types:
        if t.kind != "class":
            continue
        if not ({"RestController", "Controller"} & set(t.annotations)):
            continue
        raw = read_text(root / t.file)
        src_c = strip_java_comments(raw)
        src_m = mask_java_strings(src_c)
        endpoints.extend(parse_endpoints(t, src_m, src_c, root))

    # 标记「被返回类型引用」的 DTO
    return_names: Set[str] = set()
    for ep in endpoints:
        if ep.dto_name:
            return_names.add(ep.dto_name)
    for t in java_types:
        if t.name in return_names:
            t.is_return_type = True

    ts_types: List[TsType] = []
    requests: List[RequestSite] = []
    plain_requests: List[RequestSite] = []
    for p in ts_files_real:
        types, reqs, plains = parse_ts_file(p, root)
        ts_types.extend(types)
        requests.extend(reqs)
        plain_requests.extend(plains)
    fe_types: Dict[str, List[TsType]] = {}
    for t in ts_types:
        fe_types.setdefault(t.name, []).append(t)

    t1: Dict[Tuple[Any, ...], Row1] = {}
    t2: Dict[Tuple[Any, ...], Row2] = {}
    t3: List[Row3] = []
    pairs: List[MatchPair] = []
    blind: Dict[str, List[str]] = {}

    def add_blind(kind: str, detail: str) -> None:
        blind.setdefault(kind, []).append(detail)

    def add_t1(fe_type: str, f: TsField, be: JavaType, via: str, location: str, endpoint: str, note: str = "") -> None:
        key = (fe_type, f.name, be.name)
        if key in t1:
            if endpoint and endpoint not in t1[key].endpoint:
                t1[key].endpoint += ("; " if t1[key].endpoint else "") + endpoint
            return
        t1[key] = Row1(
            fe_type=fe_type,
            field=f.name,
            optional=f.optional,
            fe_file="",
            fe_line=f.line,
            be_type=be.name,
            be_file=be.file,
            be_line=be.line,
            via=via,
            location=location,
            endpoint=endpoint,
            note=note,
        )

    def add_t2(be: JavaType, bf: JavaField, fe_type: str, fe_decl: TsType, via: str, location: str, endpoint: str) -> None:
        key = (be.name, bf.json_name, fe_type, fe_decl.file)
        if key in t2:
            return
        t2[key] = Row2(
            be_type=be.name,
            field=bf.json_name,
            be_file=be.file,
            be_line=bf.line,
            fe_type=fe_type,
            fe_file=fe_decl.file,
            fe_line=fe_decl.line,
            via=via,
            location=location,
            endpoint=endpoint,
        )

    def diff_fields(
        fe_type_name: str,
        fe_file: str,
        fe_line: int,
        fe_fields: Dict[str, TsField],
        be: JavaType,
        via: str,
        location: str,
        endpoint: str,
        stack: Set[Tuple[str, str, str]],
        depth: int,
    ) -> None:
        if depth > 4:
            add_blind("depth-limit", f"{fe_type_name} ↔ {be.name} @ {location}")
            return
        for name, f in fe_fields.items():
            if f.kind == "method":
                continue
            if name not in be.shape:
                row = Row1(
                    fe_type=fe_type_name, field=name, optional=f.optional,
                    fe_file=fe_file, fe_line=f.line, be_type=be.name,
                    be_file=be.file, be_line=be.line, via=via,
                    location=location, endpoint=endpoint,
                )
                key = (fe_type_name, name, be.name, be.file)
                if key in t1:
                    if endpoint and endpoint not in t1[key].endpoint:
                        t1[key].endpoint += ("; " if t1[key].endpoint else "") + endpoint
                else:
                    t1[key] = row
                continue
            bf = be.shape[name]
            sig = (fe_type_name, be.name, name)
            if sig in stack:
                continue
            child_ref = resolve_java_shape(bf.type_text)
            if child_ref.reason == "ok" and child_ref.name:
                candidates = shape_candidates(by_name, child_ref.name)
                if len(candidates) > 1:
                    add_blind("ambiguous-nested", f"{be.name}.{name} → {child_ref.name}（{len(candidates)} 个同名）")
                    continue
                if not candidates:
                    continue
                child_be = candidates[0]
                child_location = f"{location}.{name}" + ("[]" if child_ref.depth else "")
                fe_children = fe_ref_from_type(f.type_text, fe_types, fe_file, f.line)
                for ref in fe_children:
                    if ref.kind == "named":
                        decls = ts_lookup(fe_types, ref.name, fe_file)
                        for decl in decls:
                            child_fields = ts_fields_of(decl, fe_types)
                            diff_fields(
                                ref.name, decl.file, decl.line, child_fields,
                                child_be, "nested", child_location,
                                endpoint, stack | {sig}, depth + 1,
                            )
                    elif ref.kind == "inline":
                        diff_fields(
                            f"{fe_type_name}.{name}", fe_file, f.line, ref.fields,
                            child_be, "nested", child_location,
                            endpoint, stack | {sig}, depth + 1,
                        )
                    else:
                        if ref.reason.startswith(("record", "external", "unparsed")):
                            add_blind(
                                "nested-frontend-unresolved",
                                f"{fe_type_name}.{name}: {ref.reason}（后端 {child_be.name}）",
                            )
            else:
                if child_ref.reason in ("map", "object", "generic-container", "type-variable", "wildcard"):
                    fe_child_refs = fe_ref_from_type(f.type_text, fe_types, fe_file, f.line)
                    if any(r.kind != "opaque" for r in fe_child_refs):
                        add_blind(
                            "nested-backend-unresolved",
                            f"{be.name}.{name}: {child_ref.reason}（前端 {f.type_text}）",
                        )
                    else:
                        add_blind(
                            "map-typed-field",
                            f"{be.name}.{name}: 后端 {bf.type_text} / 前端 {f.type_text}",
                        )
        # 表 2：后端产出、前端未声明（低危）
        for name, bf in be.shape.items():
            if name in fe_fields:
                continue
            key = (be.name, name, fe_type_name, fe_file)
            if key in t2:
                continue
            t2[key] = Row2(
                be_type=be.name, field=name, be_file=be.file, be_line=bf.line,
                fe_type=fe_type_name, fe_file=fe_file, fe_line=fe_line,
                via=via, location=location, endpoint=endpoint,
            )

    def do_root(
        ref: FeRef,
        req: RequestSite,
        endpoint_desc: str,
        endpoint_dtos: List[JavaType],
        name_candidates: List[JavaType],
    ) -> None:
        if ref.kind == "inline":
            if not endpoint_dtos:
                return
            be = endpoint_dtos[0]
            decl_stub = TsType(name=f"<inline>{req.file}:{req.line}", kind="alias", file=req.file, line=req.line)
            decl_stub.fields = ref.fields
            # 直接比较
            for fname, f in ref.fields.items():
                if fname not in be.shape:
                    key = (decl_stub.name, fname, be.name)
                    t1.setdefault(key, Row1(
                        fe_type=decl_stub.name, field=fname, optional=f.optional,
                        fe_file=req.file, fe_line=f.line, be_type=be.name,
                        be_file=be.file, be_line=be.line, via="endpoint",
                        location="<inline>", endpoint=endpoint_desc,
                    ))
            for bname, bf in be.shape.items():
                if bname not in ref.fields:
                    t2.setdefault((be.name, bname, decl_stub.name, req.file), Row2(
                        be_type=be.name, field=bname, be_file=be.file, be_line=bf.line,
                        fe_type=decl_stub.name, fe_file=req.file, fe_line=req.line,
                        via="endpoint", location="<inline>", endpoint=endpoint_desc,
                    ))
            return

        decls = ts_lookup(fe_types, ref.name, req.file)
        if not decls:
            return
        for decl in decls:
            fe_fields = ts_fields_of(decl, fe_types)
            # 选择后端形状：端点权威 > 同名唯一
            be: Optional[JavaType] = None
            via = ""
            if endpoint_dtos:
                if len(endpoint_dtos) == 1:
                    be = endpoint_dtos[0]
                    via = "endpoint" if not any(t is be for t in name_candidates) else "name+endpoint"
                else:
                    continue
            elif len(name_candidates) == 1:
                be = name_candidates[0]
                via = "name"
            elif len(name_candidates) > 1:
                add_blind("ambiguous-name", f"{ref.name}：{len(name_candidates)} 个同名后端 DTO（{req.file}:{req.line}）")
                continue
            if be is None:
                continue
            pairs.append(MatchPair(
                fe_type=ref.name, be_type=be.name, via=via,
                endpoint=endpoint_desc, location=decl.file,
            ))
            diff_fields(
                ref.name, decl.file, decl.line, fe_fields, be, via,
                decl.name, endpoint_desc, set(), 0,
            )

    # 基准 URL
    base_url = "/api"
    env_file = root / "frontend" / ".env"
    if env_file.is_file():
        for line in read_text(env_file).splitlines():
            m = re.match(r"\s*VITE_API_BASE_URL\s*=\s*(\S+)", line)
            if m:
                base_url = m.group(1).rstrip("/")
                break

    test_request_sites: List[RequestSite] = []
    resolvable = 0
    unresolved_sites = 0
    unresolvable: Dict[str, int] = {}

    for req in requests:
        if req.is_test:
            test_request_sites.append(req)
            continue
        if not req.generic:
            continue
        if req.dynamic or not req.path:
            add_blind("dynamic-url", f"{req.file}:{req.line} {req.http} {req.path_raw or '<empty>'}")
            # 仍然可以按同名匹配（无端点证据）
            fe_path = ""
        else:
            fe_path = req.path
        base_path = fe_path
        if fe_path and not (fe_path == base_url or fe_path.startswith(base_url + "/")):
            base_path = normalize_path(base_url + fe_path)

        eps: List[Endpoint] = []
        if fe_path:
            eps = select_endpoints(endpoints, req.http, fe_path, base_path)
        endpoint_desc = ""
        if eps:
            ep = eps[0]
            endpoint_desc = f"{ep.http} {ep.path} → {ep.controller}#{ep.method_name}"
            if len(eps) > 1:
                add_blind("multiple-endpoints", f"{req.file}:{req.line} {req.http} {fe_path} 命中 {len(eps)} 个端点，取第一个")

        endpoint_dtos: List[JavaType] = []
        endpoint_dto_names: List[str] = []
        endpoint_arg_names: Set[str] = set()
        if eps:
            for ep in eps:
                endpoint_arg_names.update(ep.dto_args)
                if ep.dto_name:
                    sc = shape_candidates(by_name, ep.dto_name, dto_only=True)
                    if len(sc) == 1:
                        if sc[0] not in endpoint_dtos:
                            endpoint_dtos.append(sc[0])
                            endpoint_dto_names.append(sc[0].name)
                    elif len(sc) > 1:
                        add_blind("ambiguous-endpoint-dto", f"{ep.path} → {ep.dto_name}（{len(sc)} 个同名）")

        core = unwrap_fe_response(req.generic)
        arms = split_top_level(core, ("|",)) if core else []
        if not arms:
            arms = [core]
        any_root = False
        for arm in arms:
            arm = arm.strip()
            if not arm:
                continue
            refs = fe_ref_from_type(arm, fe_types, req.file, req.line)
            for ref in refs:
                if ref.kind == "opaque":
                    unresolvable[ref.reason] = unresolvable.get(ref.reason, 0) + 1
                    add_blind(
                        "generic-unresolvable",
                        f"{req.file}:{req.line} {req.http} {req.path or req.path_raw} T={req.generic!r} → {ref.reason}",
                    )
                    continue
                any_root = True
                name_cands = shape_candidates(by_name, ref.name, dto_only=True) if ref.kind == "named" else []
                # 端点泛型实参与前端根类型同名（例：端点 PagedResult<JwtTokenInfo>，
                # 前端也写 RouterResponse<PagedResult<JwtTokenInfo>>，剥掉包装后根是 JwtTokenInfo）
                # ⇒ 用同名 DTO，而不是拿包装器 PagedResult 的字段去比元素类型。
                eff_endpoint_dtos = endpoint_dtos
                if name_cands and any(t.name in endpoint_arg_names for t in name_cands):
                    eff_endpoint_dtos = []
                if ref.kind == "named" and not name_cands:
                    # 表 3：没有同名后端 DTO
                    if endpoint_dto_names:
                        category = "name-drift"
                        note = "端点实际返回 " + ",".join(sorted(set(endpoint_dto_names)))
                    elif eps:
                        reasons = sorted({e.dto_reason for e in eps})
                        declared = sorted({e.dto_name for e in eps if e.dto_name})
                        if declared:
                            category = "endpoint-dto-no-shape"
                            note = "端点声明 " + ",".join(declared) + " 但未解析出字段形状（enum/interface？）"
                        elif any(r in ("map", "object") for r in reasons):
                            category = "untyped-map"
                            note = "端点返回无形状载荷（" + ",".join(reasons) + "）"
                        elif any(r in ("void", "scalar") for r in reasons):
                            category = "scalar-void"
                            note = "端点返回标量/void（" + ",".join(reasons) + "）"
                        else:
                            category = "endpoint-unresolved"
                            note = "端点返回类型无法解析（" + ",".join(reasons) + "）"
                    else:
                        category = "no-endpoint"
                        note = "前端与后端都没有匹配端点" if not eps else ""
                    decl_for_row = (ts_lookup(fe_types, ref.name, req.file) or [None])[0]
                    t3.append(Row3(
                        fe_type=ref.name,
                        fe_file=decl_for_row.file if decl_for_row else ref.file,
                        fe_line=decl_for_row.line if decl_for_row else ref.line,
                        req_file=req.file, req_line=req.line, http=req.http,
                        path=req.path or req.path_raw, endpoint_dto=",".join(sorted(set(endpoint_dto_names))),
                        category=category, note=note,
                    ))
                    # 名字不匹配但端点给出具体 DTO 时，仍然按端点形状对账
                    if eff_endpoint_dtos:
                        do_root(ref, req, endpoint_desc, eff_endpoint_dtos, name_cands)
                    continue
                if ref.kind == "named" and name_cands:
                    # 同名命中 ≠ 端点一致 → 名字漂移提示（表 3 不收录，单列漂移）
                    if eff_endpoint_dtos and not any(t.name == ref.name for t in eff_endpoint_dtos):
                        add_blind(
                            "name-drift-with-endpoint",
                            f"{ref.name} @ {req.file}:{req.line} 同名命中 {ref.name}，但 {endpoint_desc} 返回 "
                            + ",".join(t.name for t in eff_endpoint_dtos),
                        )
                do_root(ref, req, endpoint_desc, eff_endpoint_dtos, name_cands)
        if any_root:
            resolvable += 1
        else:
            unresolved_sites += 1

    # map 启发式（不计入表 1，仅附录）
    # 只保留「前端字段与字面量键集有交集、但不全覆盖」的行：交集为 0 时多半是
    # 控制器把业务载荷包在 {"success":..., "data":...} 里，字段级比较没有意义。
    map_heuristic: List[Tuple[str, str, str, List[str], List[str]]] = []
    map_key_endpoints = 0
    for req in requests:
        if req.is_test or not req.generic or not req.path:
            continue
        core = unwrap_fe_response(req.generic)
        refs = fe_ref_from_type(core, fe_types, req.file, req.line)
        named = [r for r in refs if r.kind == "named"]
        if not named:
            continue
        cands = select_endpoints(
            endpoints, req.http, req.path, normalize_path(base_url + req.path)
        )
        if not cands:
            continue
        ep = cands[0]
        if ep.dto_name or not ep.map_keys:
            continue
        map_key_endpoints += 1
        # 控制器自建 {"success":…, "data":…} 包装：键是信封字段，不是载荷字段
        if "data" in ep.map_keys and ({"success", "message"} & set(ep.map_keys)):
            continue
        decls = ts_lookup(fe_types, named[0].name, req.file)
        if not decls:
            continue
        fields = list(ts_fields_of(decls[0], fe_types).keys())
        overlap = [f for f in fields if f in ep.map_keys]
        missing = [f for f in fields if f not in ep.map_keys]
        extra = [k for k in ep.map_keys if k not in fields]
        if missing and overlap:
            map_heuristic.append((
                named[0].name, f"{req.file}:{req.line}", f"{ep.http} {ep.path}",
                missing, extra,
            ))

    # 统计
    for k in list(blind):
        blind[k] = list(dict.fromkeys(blind[k]))
    shape_types = [t for t in java_types if t.kind in ("class", "record") and t.shape]
    stats: Dict[str, Any] = {
        "java_files": len(java_files),
        "ts_files": len(ts_files_real),
        "java_types_parsed": len(java_types),
        "backend_shape_dtos": len(shape_types),
        "backend_shape_from_annotation": sum(1 for t in shape_types if t.annotated),
        "backend_shape_from_return_only": sum(1 for t in shape_types if not t.annotated and t.is_return_type),
        "backend_duplicate_names": {
            k: [t.file for t in v] for k, v in by_name.items() if len(v) > 1
        },
        "endpoints": len(endpoints),
        "endpoints_with_dto": sum(1 for e in endpoints if e.dto_name),
        "endpoints_untyped": sum(1 for e in endpoints if not e.dto_name),
        "endpoints_untyped_reasons": {},
        "frontend_type_decls": len(ts_types),
        "frontend_interfaces": sum(1 for t in ts_types if t.kind == "interface"),
        "frontend_aliases": sum(1 for t in ts_types if t.kind == "alias"),
        "frontend_fields": sum(len(t.fields) for t in ts_types),
        "frontend_index_signatures": sum(1 for t in ts_types if t.index_signature),
        "frontend_unparsed_members": sum(t.unparsed_members for t in ts_types),
        "frontend_duplicate_names": {
            k: [t.file for t in v] for k, v in fe_types.items() if len(v) > 1
        },
        "request_sites_total": len(requests) + len(plain_requests),
        "request_sites_with_generic": len(requests),
        "request_sites_without_generic": len(plain_requests),
        "request_sites_resolvable": resolvable,
        "request_sites_unresolvable": unresolved_sites,
        "request_unresolvable_reasons": dict(sorted(unresolvable.items(), key=lambda kv: -kv[1])),
        "test_request_sites": len(test_request_sites),
        "matched_pairs": len(pairs),
        "table1": 0,
        "table2": 0,
        "table3": len(t3),
        "locale_files_excluded": len(locale_files),
        "map_key_endpoints": map_key_endpoints,
        "map_heuristic_rows": len(map_heuristic),
    }
    reasons: Dict[str, int] = {}
    for e in endpoints:
        if not e.dto_name:
            reasons[e.dto_reason] = reasons.get(e.dto_reason, 0) + 1
    stats["endpoints_untyped_reasons"] = dict(sorted(reasons.items(), key=lambda kv: -kv[1]))
    stats["table1"] = len(t1)
    stats["table2"] = len(t2)
    stats["table3"] = len(t3)

    return Analysis(
        root=root,
        java_files=len(java_files),
        ts_files=len(ts_files_real),
        java_types=java_types,
        endpoints=endpoints,
        fe_types=fe_types,
        requests=requests,
        plain_requests=plain_requests,
        t1=t1,
        t2=t2,
        t3=t3,
        pairs=pairs,
        blind=blind,
        stats=stats,
        locale_files=locale_files,
        test_request_sites=test_request_sites,
        map_heuristic=map_heuristic,
    )


# ======================================================================================
# 报告
# ======================================================================================

def md_escape(s: Any) -> str:
    return str(s).replace("|", "\\|").replace("\n", " ")


def short_endpoints(s: str, limit: int = 2) -> str:
    """端点列只展示前 N 个（完整列表在 JSON），避免表格被撑爆。"""
    if not s:
        return "—"
    parts = [p for p in s.split("; ") if p]
    if len(parts) <= limit:
        return s
    return "; ".join(parts[:limit]) + f"（+{len(parts) - limit}）"


def md_table(headers: Sequence[str], rows: Sequence[Sequence[Any]]) -> str:
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join("---" for _ in headers) + "|"]
    for r in rows:
        out.append("| " + " | ".join(md_escape(c) for c in r) + " |")
    return "\n".join(out)


def render_report(an: Analysis) -> str:
    st = an.stats
    now = datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S %z")
    lines: List[str] = []
    ap = lines.append

    ap("# 前后端契约形状对账报告（declared type vs actual payload）")
    ap("")
    ap(f"- 生成时间：{now}")
    ap(f"- 根目录：`{an.root}`")
    ap("- 脚本：`scripts/audit/reconcile-contract-shapes.py`")
    ap("- 约束：**不启动应用、不跑 maven/npm/vue-tsc、不修改 `src/main/java` 与 `frontend/src`**")
    ap("")
    ap("## 1. 方法与判据")
    ap("")
    ap("### 1.1 后端形状（`src/main/java/**/*.java`）")
    ap("")
    ap("- 候选 DTO：`class` / `record`，且（a）带 `@Data` / `@Getter` / `@Value` / `@Builder` / `@Schema`，")
    ap("  或（b）被控制器方法返回类型引用。")
    ap("- JSON 可见字段：`record` 组件、`public` 字段、有 getter 的字段、Lombok 生成 getter 的字段、")
    ap("  `public getX()` / `isX()`；`@JsonProperty(\"...\")` 改名生效；`@JsonIgnore` 与")
    ap("  `@JsonIgnoreProperties({\"...\"})` 排除；`static` 字段跳过；boolean `isXxx` 归一为 `xxx`。")
    ap("- 返回类型逐层剥掉透明包装（`ResponseEntity` / `Mono` / `Flux` / `RouterResponse` /")
    ap("  `List` / `Page` / `Set` / `ServerSentEvent` …）落到载荷 DTO。")
    ap("")
    ap("### 1.2 前端形状（`frontend/src/**/*.ts`，含 `.vue` 的 script 区）")
    ap("")
    ap("- `interface` / `type` 的字段集合（可选 `?`、数组、联合、交叉、`extends`、对象别名、索引签名）。")
    ap("- `request.<method><T>(path)` 的泛型 `T`；透明包装 `RouterResponse` / `ApiResponse` /")
    ap("  `PageResponse` / `PagedResult` 会被剥掉后取载荷对象。")
    ap("- `frontend/src/locales/**` **按任务要求不解析**（见第 6 节）；`__tests__` / `*.spec.ts` 的")
    ap("  request 调用不计入对账（不是运行时读取点），其声明仍抽取。")
    ap("")
    ap("### 1.3 匹配判据")
    ap("")
    ap("1. **同名优先**：前端类型名 == 后端 DTO 简单名；")
    ap("2. 同名找不到时，用 `request<T>` → 端点 → 控制器返回类型链；")
    ap("3. **端点权威**：同名 DTO 与端点实际返回 DTO 不同时，以端点实际返回者做形状对账，")
    ap("   并把「名字漂移」记入表 3 —— 否则会拿错 DTO 报假缺陷。实例：前端 `RateLimitConfig`")
    ap("   同名命中 `common/dto/RateLimitConfig`，而 `/config/instance/{}/rate-limit` 实际返回")
    ap("   `InstanceRateLimitDTO`（9 字段，含 `id`/`instanceId`）。");
    ap("4. 同名候选多于一个且端点无法消歧 ⇒ 不猜，计入盲区（`ambiguous-name`）。")
    ap("- 嵌套：字段两侧都能解析到对象时递归比较（数组/分页包装剥掉），路径见「位置」列；深度上限 4。")
    ap("")
    ap("> 本工具只对账**字段名集合（形状）**，不对账字段类型（`string` vs `number`）。")
    ap("")
    ap("## 2. 抽取计数")
    ap("")
    rows = [
        ("后端 Java 文件", st["java_files"]),
        ("后端类型声明（class/record/interface/enum）", st["java_types_parsed"]),
        ("**解析出形状的 class/record（含配置类/实体；根匹配只认 DTO 候选）**", st["backend_shape_dtos"]),
        ("  ├ 由注解识别（@Data/@Getter/@Value/@Builder/@Schema）", st["backend_shape_from_annotation"]),
        ("  ├ 无注解、由控制器返回类型识别", st["backend_shape_from_return_only"]),
        ("  └ 无注解、仅有 getter（普通 DTO/实体/配置类）",
         st["backend_shape_dtos"] - st["backend_shape_from_annotation"] - st["backend_shape_from_return_only"]),
        ("控制器端点总数", st["endpoints"]),
        ("  ├ 返回类型可解析到具体 DTO", st["endpoints_with_dto"]),
        ("  └ 返回类型无形状（Map/Object/标量/void/接口…）", st["endpoints_untyped"]),
        ("前端文件（.ts/.vue，已排除 locales）", st["ts_files"]),
        ("前端类型声明", st["frontend_type_decls"]),
        ("  ├ interface", st["frontend_interfaces"]),
        ("  └ type alias", st["frontend_aliases"]),
        ("前端声明字段总数", st["frontend_fields"]),
        ("前端索引签名类型（无固定键集）", st["frontend_index_signatures"]),
        ("**request 调用点总数**", st["request_sites_total"]),
        ("  ├ 带泛型 `request.x<T>(...)`", st["request_sites_with_generic"]),
        ("  ├ 不带泛型（`as` 手写断言，无法对账）", st["request_sites_without_generic"]),
        ("  └ 测试文件中的 request 调用（不计入）", st["test_request_sites"]),
        ("**泛型 T 可解析到具体形状的调用点**", st["request_sites_resolvable"]),
        ("**泛型 T 无法解析的调用点**", st["request_sites_unresolvable"]),
        ("成功配对的（前端类型, 后端 DTO）", st["matched_pairs"]),
        ("表 1 字段缺失（高危）", st["table1"]),
        ("表 2 字段多余（低危）", st["table2"]),
        ("表 3 类型名对不上", st["table3"]),
        ("被排除的 locale 文件（表 4 范围）", st["locale_files_excluded"]),
    ]
    ap(md_table(["项", "数量"], rows))
    ap("")
    if st["request_unresolvable_reasons"]:
        ap("泛型 `T` 无法解析的原因分布：")
        ap("")
        ap(md_table(["原因", "次数"], [(k, v) for k, v in st["request_unresolvable_reasons"].items()]))
        ap("")
    if st["endpoints_untyped_reasons"]:
        ap("端点返回类型无形状的原因分布：")
        ap("")
        ap(md_table(["原因", "端点数"], [(k, v) for k, v in st["endpoints_untyped_reasons"].items()]))
        ap("")

    ap("### 2.1 覆盖结论（先读这段再看表 1）")
    ap("")
    ap(f"- 表 1/2 的实际覆盖面 = **{st['matched_pairs']}** 个成功配对的（前端类型, 后端 DTO），")
    ap(f"  由 **{st['request_sites_resolvable']}/{st['request_sites_with_generic']}** 个「T 可解析」的带泛型 request 点驱动，")
    ap("  并对可解析字段做了嵌套递归。")
    ap(f"- **{st['request_sites_unresolvable']}** 个带泛型 request 点的 `T` 不是对象形状")
    ap("  （`any` / `void` / `string` / `Record<…>` / 外部类型…），本工具不对账其字段（逐条见 7.1）。")
    ap(f"- **{st['request_sites_without_generic']}** 个不带泛型的 request 点用 `as` 手写断言，工具看不到声明类型（7.2）。")
    ap(f"- **{st['table3']}** 个表 3 请求点的端点没有可静态解析的 DTO（多为 `Map<String,Object>`，7.3）。")
    ap("")
    ap("> 因此：表 1 **非空**说明确实存在类型层谎言；若某轮表 1 为空，也只能说明「已配对类型无缺失」，")
    ap("> 不能说明「全部契约正确」——必须同时看上述未覆盖计数。")
    ap("")

    # ---------------- 表 1 ----------------
    ap("## 3. 表 1：字段缺失（前端声称，后端从未产出）——高危")
    ap("")
    ap("> 判据：对应后端 DTO 的 JSON 可见字段集合里没有该名字 ⇒ UI 读到的必然是 `undefined`。")
    ap("> `可选` 列 `?` 表示前端声明为可选；可选不等于安全（它仍承诺该键存在）。")
    ap("> **后端位置 = DTO 类声明行**（该字段不存在，因此没有字段行可指）。")
    ap("> 本表是**类型层**证据：字段是否真的被某个视图读取未逐点验证（那是另一层）。")
    ap("")
    rows1 = sorted(an.t1.values(), key=lambda r: (r.fe_type, r.fe_file, r.fe_line, r.field))
    if not rows1:
        ap("**本轮为空。** 覆盖范围见第 2 节计数与第 7 节盲区 —— 空结果只代表「所有被成功配对的前端类型」")
        ap("在其后端 DTO 上都有对应字段，不代表全部契约都被覆盖。")
        ap("")
    else:
        table_rows = []
        for i, r in enumerate(rows1, 1):
            table_rows.append([
                i, r.fe_type, r.field + ("?" if r.optional else ""),
                f"{r.fe_file}:{r.fe_line}",
                r.be_type, f"{r.be_file}:{r.be_line}",
                r.via, r.location, short_endpoints(r.endpoint),
            ])
        ap(md_table(
            ["#", "前端类型", "前端声称字段", "前端位置", "后端 DTO", "后端位置", "匹配方式", "位置路径", "端点"],
            table_rows,
        ))
        ap("")
        ap(f"合计 **{len(rows1)}** 条。")
        ap("")

    # ---------------- 表 2 ----------------
    ap("## 4. 表 2：字段多余（后端产出，前端类型未声明）——低危")
    ap("")
    ap("> 不影响读取（前端不读就不会 `undefined`），但会让类型对真实载荷失约束。")
    ap("")
    rows2 = sorted(an.t2.values(), key=lambda r: (r.be_type, r.be_file, r.be_line, r.field))
    if not rows2:
        ap("本轮为空。")
        ap("")
    else:
        ap(md_table(
            ["#", "后端 DTO", "后端字段", "后端位置", "前端类型", "前端声明位置", "匹配方式", "位置路径", "端点"],
            [[i, r.be_type, r.field, f"{r.be_file}:{r.be_line}", r.fe_type,
              f"{r.fe_file}:{r.fe_line}", r.via, r.location, r.endpoint or "—"]
             for i, r in enumerate(rows2, 1)],
        ))
        ap("")
        ap(f"合计 **{len(rows2)}** 条；按出现次数 Top 10：")
        ap("")
        from collections import Counter
        cnt = Counter((r.be_type, r.field) for r in rows2)
        ap(md_table(["后端 DTO", "字段", "出现次数"], [[k[0], k[1], v] for k, v in cnt.most_common(10)]))
        ap("")

    # ---------------- 表 3 ----------------
    ap("## 5. 表 3：类型名对不上（`request<T>` 的 T 没有同名后端 DTO）")
    ap("")
    ap("> 分类说明：`name-drift` = 端点实际返回另一个 DTO（名字漂移）；`untyped-map` = 端点返回")
    ap("> `Map<String,Object>`（运行时才有键，静态无形状）；`scalar-void` = 标量/void；")
    ap("> `no-endpoint` = 前后端都没匹配到端点（可能是 `/v1/**` 协议面或路径漂移）；")
    ap("> `endpoint-unresolved` = 端点在但返回类型无法解析。")
    ap("")
    rows3 = sorted(an.t3, key=lambda r: (r.category, r.fe_type, r.req_file, r.req_line))
    if not rows3:
        ap("本轮为空。")
        ap("")
    else:
        ap(md_table(
            ["#", "前端类型", "前端声明位置", "request 位置", "方法/路径", "端点实际返回", "分类", "说明"],
            [[i, r.fe_type, f"{r.fe_file}:{r.fe_line}", f"{r.req_file}:{r.req_line}",
              f"{r.http} {r.path}", r.endpoint_dto or "—", r.category, r.note]
             for i, r in enumerate(rows3, 1)],
        ))
        ap("")
        from collections import Counter
        cnt3 = Counter(r.category for r in rows3)
        ap(f"合计 **{len(rows3)}** 条；分类分布：")
        ap("")
        ap(md_table(["分类", "条数"], list(cnt3.most_common())))
        ap("")
        ap("按类型聚合（Top 20）：")
        ap("")
        cnt3b = Counter(r.fe_type for r in rows3)
        ap(md_table(["前端类型", "请求点次数"], [[k, v] for k, v in cnt3b.most_common(20)]))
        ap("")

    # ---------------- 表 4 ----------------
    ap("## 6. 表 4：命名空间嵌套错位（locale）——按任务要求不在本工具内")
    ap("")
    ap(f"`frontend/src/locales/**` 下共 **{len(an.locale_files)}** 个文件被**显式排除**（不解析、不报告）。")
    ap("")
    ap("原因：locale 文件的 `<file>.<keyPath>` 命名空间与视图 `t('a.b.c')` 的错位是**另一类缺陷**，")
    ap("与本工具的「API 字段形状」判据无关；把两类混在一张表里会让两侧证据无法对齐。")
    ap("")
    if an.locale_files:
        ap("被排除的文件：")
        ap("")
        for f in an.locale_files[:50]:
            ap(f"- `{f}`")
        if len(an.locale_files) > 50:
            ap(f"- …（其余 {len(an.locale_files) - 50} 个）")
        ap("")

    # ---------------- 盲区 ----------------
    ap("## 7. 盲区与无法解析项（显式计数，不静默丢弃）")
    ap("")
    ap("### 7.1 request 泛型无法解析")
    ap("")
    if st["request_unresolvable_reasons"]:
        ap(md_table(["原因", "次数"], [(k, v) for k, v in st["request_unresolvable_reasons"].items()]))
        ap("")
    for kind in ("generic-unresolvable",):
        vals = an.blind.get(kind, [])
        if vals:
            ap(f"`{kind}`（{len(vals)} 条）：")
            ap("")
            for v in vals:
                ap(f"- {v}")
            ap("")
    if not an.blind.get("generic-unresolvable") and not st["request_unresolvable_reasons"]:
        ap("无。")
        ap("")

    ap("### 7.2 不带泛型的 request 调用（`as` 手写断言，工具看不到声明类型）")
    ap("")
    if an.plain_requests:
        ap(md_table(
            ["#", "位置", "方法", "路径"],
            [[i, f"{r.file}:{r.line}", r.http, r.path or r.path_raw or "—"]
             for i, r in enumerate(sorted(an.plain_requests, key=lambda x: (x.file, x.line)), 1)],
        ))
        ap("")
    else:
        ap("无。")
        ap("")

    ap("### 7.3 端点为 `Map<String,Object>` / `Object` 等无形状载荷")
    ap("")
    untyped = [e for e in an.endpoints if not e.dto_name]
    if untyped:
        from collections import Counter
        cnt = Counter(e.dto_reason for e in untyped)
        ap(md_table(["原因", "端点数"], list(cnt.most_common())))
        ap("")
        ap("受影响端点（最多列 80 个）：")
        ap("")
        for e in sorted(untyped, key=lambda x: (x.file, x.line))[:80]:
            ap(f"- `{e.http} {e.path}` → `{e.return_text}`（{e.dto_reason}）— `{e.file}:{e.line}`")
        if len(untyped) > 80:
            ap(f"- …（其余 {len(untyped) - 80} 个）")
        ap("")
    else:
        ap("无。")
        ap("")

    ap("### 7.4 嵌套字段无法解析")
    ap("")
    any_nested = False
    for kind in ("nested-backend-unresolved", "nested-frontend-unresolved", "map-typed-field",
                 "ambiguous-nested", "ambiguous-name", "ambiguous-endpoint-dto",
                 "multiple-endpoints", "dynamic-url", "depth-limit",
                 "name-drift-with-endpoint"):
        vals = an.blind.get(kind, [])
        if not vals:
            continue
        any_nested = True
        ap(f"**`{kind}`**（{len(vals)} 条）：")
        ap("")
        for v in vals[:60]:
            ap(f"- {v}")
        if len(vals) > 60:
            ap(f"- …（其余 {len(vals) - 60} 条）")
        ap("")
    if not any_nested:
        ap("无。")
        ap("")

    ap("### 7.5 同名冲突（无法唯一确定对应 DTO）")
    ap("")
    dup_be = st["backend_duplicate_names"]
    dup_fe = st["frontend_duplicate_names"]
    ap(f"- 后端同名类型：**{len(dup_be)}** 组" + ("：" if dup_be else "。"))
    if dup_be:
        for name, files in sorted(dup_be.items()):
            ap(f"  - `{name}` → {', '.join(files)}")
    ap(f"- 前端同名类型：**{len(dup_fe)}** 组" + ("：" if dup_fe else "。"))
    if dup_fe:
        for name, files in sorted(dup_fe.items()):
            ap(f"  - `{name}` → {', '.join(files)}")
    ap("")

    ap("### 7.6 其他已知盲区（结构性，不在本工具射程）")
    ap("")
    ap("- **字段类型**不做对账：`string` vs `number`、可空性、枚举取值集合都不检查。")
    ap("- **运行时分支泛型**：`Mono<X>` / `Flux<X>` 里若按分支返回不同实现，静态只取声明类型。")
    ap("- **`@JsonInclude(NON_NULL)`**：字段「声明存在」但运行时可缺省；表 1/2 按声明判定，")
    ap("  不按运行时值判定。")
    ap("- **`@JsonNaming` / 自定义 `Serializer` / `@JsonAnyGetter`**：本仓 `src/main/java` 全仓无命中")
    ap("  （已 grep 确认），故未实现；若将来引入，字段名集合会失真。")
    ap("- **枚举/接口返回类型**：接口无法静态确定实现类形状，计为无形状。")
    ap("- **响应/分页包装器自身字段不对账**：`RouterResponse` / `ApiResponse` / `PageResponse` /")
    ap("  `PagedResult` 被剥掉后只对账元素类型；包装器字段（`content`/`page`/…）本身不比较。")
    ap("- **Java 父类字段不合并**：只解析类体字段与 getter，`extends` 的父类字段不计入形状。")
    ap("  （本轮表 1/2 命中的 15 个 DTO 均无 `extends`；全仓 42 处 `extends` 多为框架/异常/内部装饰器类。）")
    ap("- **`Map<String,Object>` 的键**：静态无形状；本报告 7.7 给出**启发式**键提取，仅供参考。")
    ap("")
    if st["frontend_index_signatures"]:
        ap(f"- 前端索引签名类型 **{st['frontend_index_signatures']}** 个（`[key: string]: any`），")
        ap("  这些类型没有固定键集，无法逐字段对账。")
        ap("")
    if st["frontend_unparsed_members"]:
        ap(f"- 前端未能解析的成员 **{st['frontend_unparsed_members']}** 个（方法签名/计算属性等）。")
        ap("")

    ap("### 7.7 附：`Map<String,Object>` 端点的键启发式（不计入表 1）")
    ap("")
    ap("> 从控制器方法体提取 `.put(\"...\")` / `Map.of(\"...\")` / `entry(\"...\")` 的键。")
    ap("> 这是**启发式**：可能漏掉服务层构造的键、或把嵌套 map 的键混入。只用于给")
    ap("> `untyped-map` 类前端类型提供线索，**不作为缺陷判定**。")
    ap("")
    ap(f"- 无形状端点中，方法体可提取到字面量键的 request 点：**{st.get('map_key_endpoints', 0)}** 个。")
    ap("- 键集完整/为空、或与前端字段零交集（控制器把载荷包在 `{\"success\":…,\"data\":…}` 里）的行不列出。")
    ap("")
    if an.map_heuristic:
        ap(f"前端字段与键集**有交集但不全覆盖**的 request 点：**{len(an.map_heuristic)}** 个（仅线索，需人工核对）：")
        ap("")
        ap(md_table(
            ["前端类型", "request 位置", "端点", "前端声明但键集里没有", "键集里多出"],
            [[a, b, c, ", ".join(d) or "—", ", ".join(e)[:160] or "—"]
             for a, b, c, d, e in an.map_heuristic],
        ))
        ap("")
    else:
        ap("无。")
        ap("")

    ap("## 8. 复现")
    ap("")
    ap("```bash")
    ap("python scripts/audit/reconcile-contract-shapes.py --self-test   # 先校准判据")
    ap("python scripts/audit/reconcile-contract-shapes.py               # 再出报告")
    ap("```")
    ap("")
    ap("JSON 明细：`scripts/audit/out/contract-shapes.json`")
    ap("")
    return "\n".join(lines)


def analysis_to_json(an: Analysis) -> Dict[str, Any]:
    return {
        "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(),
        "root": str(an.root),
        "stats": an.stats,
        "table1_missing_fields": [r.__dict__ for r in sorted(
            an.t1.values(), key=lambda r: (r.fe_type, r.fe_file, r.fe_line, r.field))],
        "table2_extra_fields": [r.__dict__ for r in sorted(
            an.t2.values(), key=lambda r: (r.be_type, r.be_file, r.be_line, r.field))],
        "table3_type_name_mismatch": [r.__dict__ for r in sorted(
            an.t3, key=lambda r: (r.category, r.fe_type, r.req_file, r.req_line))],
        "table4_locale_excluded": {
            "count": len(an.locale_files),
            "files": an.locale_files,
            "note": "按任务要求不解析 locales",
        },
        "matches": [p.__dict__ for p in an.pairs],
        "endpoints": [
            {
                "http": e.http,
                "path": e.path,
                "controller": e.controller,
                "method": e.method_name,
                "file": e.file,
                "line": e.line,
                "returnType": e.return_text,
                "dto": e.dto_name,
                "reason": e.dto_reason,
                "genericArgs": list(e.dto_args),
            }
            for e in sorted(an.endpoints, key=lambda x: (x.file, x.line))
        ],
        "blind_spots": an.blind,
        "map_heuristic": [
            {"frontendType": a, "request": b, "endpoint": c,
             "declaredButNotInMapKeys": d, "mapKeysNotDeclared": e}
            for a, b, c, d, e in an.map_heuristic
        ],
    }


# ======================================================================================
# 校准（--self-test）
# ======================================================================================

JAVA_TYPES_FIXTURE = """\
package demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

public final class DemoNamespace {

    @Data
    public static class Widget {
        private String id;
        private String name;
        @JsonProperty("display_name")
        private String javaDisplayName;
        @JsonIgnore
        private String internalOnly;
        private List<PartDto> parts;
    }

    @Data
    public static class PartDto {
        private String partId;
        private String partName;
    }

    @Data
    public static class PerfectDto {
        private String id;
        private String name;
    }

    @Data
    public static class RenamedDto {
        @JsonProperty("json_name")
        private String javaName;
    }

    @Data
    public static class IgnoredDto {
        private String visible;
        @JsonIgnore
        private String secretIgnored;
    }

    @Data
    public static class GetterIgnoreDto {
        private String keep;
        private String secret;

        @JsonIgnore
        public String getSecret() {
            return secret;
        }
    }

    @Data
    public static class EndpointOnlyDto {
        private String alpha;
        private String beta;
    }

    /** 包装类型 Boolean 的 isX 字段：Lombok 生成 getIsX()，Jackson 属性名仍是 isX */
    @Data
    public static class BooleanFlagDto {
        private Boolean isActive;
    }

    /** primitive boolean 的 isX 字段：Lombok 生成 isX()，Jackson 属性名是 x */
    @Data
    public static class PrimitiveFlagDto {
        private boolean isActive;
    }

    public record RecordRenamedDto(
            String plain,
            @JsonProperty("renamed_field") String javaField
    ) {
    }
}
"""

JAVA_CONTROLLER_FIXTURE = """\
package demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/widgets")
    public ResponseEntity<RouterResponse<List<DemoNamespace.Widget>>> widgets() {
        return null;
    }

    @GetMapping("/perfect")
    public RouterResponse<DemoNamespace.PerfectDto> perfect() {
        return null;
    }

    @GetMapping("/endpoint-only")
    public Mono<RouterResponse<DemoNamespace.EndpointOnlyDto>> endpointOnly() {
        return Mono.empty();
    }

    @GetMapping("/renamed")
    public RouterResponse<DemoNamespace.RenamedDto> renamed() {
        return null;
    }

    @GetMapping("/ignored")
    public RouterResponse<DemoNamespace.IgnoredDto> ignored() {
        return null;
    }

    @GetMapping("/items/list")
    public RouterResponse<List<DemoNamespace.PerfectDto>> itemsList() {
        return null;
    }

    @GetMapping("/items/{id}")
    public RouterResponse<DemoNamespace.EndpointOnlyDto> item() {
        return null;
    }
}
"""

TS_TYPES_FIXTURE = """\
export interface Widget {
  id: string
  name: string
  display_name: string
  javaDisplayName?: string
  internalOnly: string
  parts: PartDto[]
  ghostField: string
}

export interface PartDto {
  partId: string
  partName: string
  extraPartField: number
}

export interface PerfectDto {
  id: string
  name: string
}

export interface RenamedDto {
  json_name: string
}

export interface IgnoredDto {
  visible: string
  secretIgnored: string
}

export interface AliasShape {
  alpha: string
  beta: number
}

export interface InventedDto {
  foo: string
}

export interface ItemShape {
  alpha: string
  beta: number
}
"""

TS_API_FIXTURE = """\
import request from '@/utils/request'
import type { RouterResponse } from '@/types'

export const widgets = () => request.get<RouterResponse<Widget[]>>('/demo/widgets')
export const perfect = () => request.get<RouterResponse<PerfectDto>>('/demo/perfect')
export const endpointOnly = () => request.get<RouterResponse<AliasShape>>('/demo/endpoint-only')
export const renamed = () => request.get<RouterResponse<RenamedDto>>('/demo/renamed')
export const ignored = () => request.get<RouterResponse<IgnoredDto>>('/demo/ignored')
export const invented = () => request.get<RouterResponse<InventedDto>>('/demo/nope')
export const item = (id: string) => request.get<RouterResponse<ItemShape>>(`/demo/items/${id}`)
"""


def write_fixture(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def run_self_test() -> int:
    print("[self-test] 契约形状对账器校准开始")
    scratch = Path(tempfile.mkdtemp(prefix="jairouter-contract-shapes-"))
    print(f"[self-test] scratch tree = {scratch}")
    failures = 0
    checks = 0

    def check(name: str, cond: bool, detail: str = "") -> bool:
        nonlocal failures, checks
        checks += 1
        tag = "PASS" if cond else "FAIL"
        line = f"  {tag}  {name}"
        if detail:
            line += f"  [{detail}]"
        print(line)
        if not cond:
            failures += 1
        return cond

    try:
        write_fixture(scratch / "src/main/java/demo/DemoNamespace.java", JAVA_TYPES_FIXTURE)
        write_fixture(scratch / "src/main/java/demo/DemoController.java", JAVA_CONTROLLER_FIXTURE)
        write_fixture(scratch / "frontend/src/types/demo.ts", TS_TYPES_FIXTURE)
        write_fixture(scratch / "frontend/src/api/demo.ts", TS_API_FIXTURE)
        write_fixture(scratch / "frontend/src/locales/zh-CN.json", '{"a": {"b": "c"}}')

        an = run_analysis(scratch)
        t1_keys = {(r.fe_type, r.field) for r in an.t1.values()}
        t1_all = sorted(t1_keys)
        be = {t.name: t for t in an.java_types}

        # (a) 前端声称、DTO 没有 → 必须报
        check(
            "(a) 字段缺失：Widget.ghostField 被表 1 报出",
            ("Widget", "ghostField") in t1_keys,
            f"table1={t1_all}",
        )
        # (b) 完全匹配 → 不得报
        perfect_hit = [k for k in t1_keys if k[0] == "PerfectDto"]
        perfect_t2 = [r for r in an.t2.values() if r.be_type == "PerfectDto"]
        check(
            "(b) 完全匹配：PerfectDto 不在表 1、也不在表 2",
            not perfect_hit and not perfect_t2,
            f"t1={perfect_hit} t2={len(perfect_t2)}",
        )
        # (c) @JsonProperty 改名 → 用 JSON 名
        renamed_shape = set(be["RenamedDto"].shape)
        check(
            "(c) @JsonProperty：RenamedDto 形状 = {json_name}，不含 javaName/java_name",
            renamed_shape == {"json_name"},
            f"shape={sorted(renamed_shape)}",
        )
        check(
            "(c2) @JsonProperty：前端写 Java 字段名 javaDisplayName 会被报缺失（证明用的是 JSON 名）",
            ("Widget", "javaDisplayName") in t1_keys and ("Widget", "display_name") not in t1_keys,
            f"t1={t1_all}",
        )
        check(
            "(c3) record 组件上的 @JsonProperty 同样生效：RecordRenamedDto 形状含 renamed_field",
            "renamed_field" in be["RecordRenamedDto"].shape
            and "javaField" not in be["RecordRenamedDto"].shape,
            f"shape={sorted(be['RecordRenamedDto'].shape)}",
        )
        # (d) @JsonIgnore → 不产出
        ignored_shape = set(be["IgnoredDto"].shape)
        check(
            "(d) @JsonIgnore：IgnoredDto 形状不含 secretIgnored",
            "secretIgnored" not in ignored_shape and "visible" in ignored_shape,
            f"shape={sorted(ignored_shape)}",
        )
        check(
            "(d2) @JsonIgnore：前端仍声明 secretIgnored → 被表 1 报出",
            ("IgnoredDto", "secretIgnored") in t1_keys,
            f"t1={t1_all}",
        )
        check(
            "(d3) @JsonIgnore 在 getter 上：GetterIgnoreDto 形状不含 secret",
            "secret" not in be["GetterIgnoreDto"].shape and "keep" in be["GetterIgnoreDto"].shape,
            f"shape={sorted(be['GetterIgnoreDto'].shape)}",
        )
        # 嵌套
        check(
            "嵌套：PartDto.extraPartField 被表 1 报出（PartDto 由 Widget.parts 递归到达）",
            ("PartDto", "extraPartField") in t1_keys,
            f"t1={t1_all}",
        )
        check(
            "嵌套：PartDto.partId/partName 未被误报",
            ("PartDto", "partId") not in t1_keys and ("PartDto", "partName") not in t1_keys,
        )
        # 端点链
        alias_t1 = [k for k in t1_keys if k[0] == "AliasShape"]
        alias_pair = [p for p in an.pairs if p.fe_type == "AliasShape"]
        check(
            "端点链：无同名 DTO 的 AliasShape 由端点形状 EndpointOnlyDto 对账，表 1 无行",
            not alias_t1 and any(p.be_type == "EndpointOnlyDto" and p.via == "endpoint" for p in alias_pair),
            f"pair={[(p.fe_type, p.be_type, p.via) for p in alias_pair]}",
        )
        # 表 3
        t3 = {(r.fe_type, r.category) for r in an.t3}
        check(
            "表 3：InventedDto 无端点 → 记入 no-endpoint",
            ("InventedDto", "no-endpoint") in t3,
            f"t3={sorted(t3)}",
        )
        check(
            "表 3：AliasShape 名字不匹配但端点有 DTO → 记入 name-drift（仍按端点对账）",
            ("AliasShape", "name-drift") in t3,
            f"t3={sorted(t3)}",
        )
        # locale 排除
        check(
            "表 4 边界：locales 文件被显式排除且计数为 1",
            len(an.locale_files) == 1 and an.locale_files[0].endswith("zh-CN.json"),
            f"locale={an.locale_files}",
        )
        check(
            "布尔字段命名：Boolean isActive → 属性名 isActive（包装类型 Lombok 生成 getIsActive）",
            set(be["BooleanFlagDto"].shape) == {"isActive"},
            f"shape={sorted(be['BooleanFlagDto'].shape)}",
        )
        check(
            "布尔字段命名：primitive boolean isActive → 属性名 active（is 前缀 getter 语义）",
            set(be["PrimitiveFlagDto"].shape) == {"active"},
            f"shape={sorted(be['PrimitiveFlagDto'].shape)}",
        )
        item_pair = [p for p in an.pairs if p.fe_type == "ItemShape"]
        item_t1 = [k for k in t1_keys if k[0] == "ItemShape"]
        check(
            "端点打分：/demo/items/${id} 命中 /items/{} 而非 /items/list（否则 ItemShape 会假报缺字段）",
            not item_t1 and any(p.be_type == "EndpointOnlyDto" for p in item_pair),
            f"pair={[(p.fe_type, p.be_type) for p in item_pair]} t1={item_t1}",
        )
        # 计数自检
        check(
            "抽取计数：后端解析出形状的 DTO ≥ 10，前端类型 = 8，request = 7",
            an.stats["backend_shape_dtos"] >= 10
            and an.stats["frontend_type_decls"] == 8
            and an.stats["request_sites_with_generic"] == 7,
            f"dto={an.stats['backend_shape_dtos']} fe={an.stats['frontend_type_decls']} req={an.stats['request_sites_with_generic']}",
        )
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
        gone = not scratch.exists()
        check("清理：scratch tree 已删除且确实不存在", gone, str(scratch))

    print(f"[self-test] {checks - failures}/{checks} PASS")
    if failures:
        print(f"[self-test] FAIL：{failures} 条断言未通过")
        return 1
    print("[self-test] PASS：判据校准通过")
    return 0


# ======================================================================================
# 入口
# ======================================================================================

def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="前后端契约形状对账器（前端 TS 类型 vs 后端 Java DTO）",
    )
    parser.add_argument("--self-test", action="store_true", help="校准判据（合成夹具 + 断言 + 清理）")
    parser.add_argument("--root", default="", help="仓库根目录（默认：脚本上两级）")
    parser.add_argument("--md-out", nargs="?", const="", default=None,
                        help="Markdown 输出路径；只写 --md-out 或 --no-md 则不写")
    parser.add_argument("--json-out", nargs="?", const="", default=None,
                        help="JSON 输出路径；只写 --json-out 或 --no-json 则不写")
    parser.add_argument("--no-md", action="store_true", help="不写 Markdown")
    parser.add_argument("--no-json", action="store_true", help="不写 JSON")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()
    if args.no_md:
        args.md_out = ""
    if args.no_json:
        args.json_out = ""

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[2]
    if not (root / "src/main/java").is_dir():
        print(f"[error] 找不到 {root / 'src/main/java'}", file=sys.stderr)
        return 2
    if not (root / "frontend/src").is_dir():
        print(f"[error] 找不到 {root / 'frontend/src'}", file=sys.stderr)
        return 2

    an = run_analysis(root)
    report = render_report(an)
    payload = analysis_to_json(an)

    out_dir = root / "scripts" / "audit" / "out"
    md_out = Path(args.md_out) if args.md_out is not None else out_dir / "contract-shapes.md"
    json_out = Path(args.json_out) if args.json_out is not None else out_dir / "contract-shapes.json"
    if args.md_out == "":
        md_out = None
    if args.json_out == "":
        json_out = None

    if md_out:
        md_out.parent.mkdir(parents=True, exist_ok=True)
        md_out.write_text(report, encoding="utf-8")
    if json_out:
        json_out.parent.mkdir(parents=True, exist_ok=True)
        json_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    st = an.stats
    print(f"[contract-shapes] root = {root}")
    print(f"[contract-shapes] 后端 DTO(有形状) = {st['backend_shape_dtos']}；"
          f"端点 = {st['endpoints']}（有 DTO {st['endpoints_with_dto']}）")
    print(f"[contract-shapes] 前端类型 = {st['frontend_type_decls']}；"
          f"request 调用 = {st['request_sites_total']}（带泛型 {st['request_sites_with_generic']}，"
          f"T 可解析 {st['request_sites_resolvable']}）")
    print(f"[contract-shapes] 表1 = {st['table1']}；表2 = {st['table2']}；表3 = {st['table3']}")
    if md_out:
        print(f"[contract-shapes] MD  → {md_out}")
    if json_out:
        print(f"[contract-shapes] JSON→ {json_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
