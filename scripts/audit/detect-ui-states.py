#!/usr/bin/env python3
"""Mechanical UI-state detector for the JAiRouter Vue 3 + Element Plus console.

Five (six sub-)categories, each decidable from source text:

  cat 1  缺少加载态        HARD-ADVISORY  el-table page fed by an async fetch with no
                                        loading mechanism at all (v-loading / :loading /
                                        el-skeleton :loading).
  cat 2  缺少空态          ADVISORY       el-table without empty-text, el-empty or #empty.
                                        Element Plus renders a default empty block anyway.
  cat 3  危险操作无二次确认  HARD          a destructive call reachable from the template and
                                        no ElMessageBox.confirm / ElMessageBox( / confirm( /
                                        ElMessageBox.prompt / el-popconfirm anywhere in the file.
  cat 4  权限态缺失        HEURISTIC      a mutation control (request.post/put/delete/... or an
                                        add/edit/delete/save button) with no permission token.
                                        Route-level meta.permissions is NOT modelled.
  cat 5a 仅图标按钮          ADVISORY      el-button with only an icon and no aria-label/title.
  cat 5b 无标签表单控件      ADVISORY      el-input/el-select/el-textarea outside a labelled
                                        el-form-item and without aria-label/placeholder.

Usage:
    python scripts/audit/detect-ui-states.py            # write scripts/audit/out/ui-states.md
    python scripts/audit/detect-ui-states.py --self-test  # synthetic fixtures, PASS/FAIL, exit 1 on failure

The scanner never imports or executes the Vue files; it is a text analyser.
It never writes under frontend/src.
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = REPO_ROOT / "frontend" / "src" / "views"
DEFAULT_OUT = REPO_ROOT / "scripts" / "audit" / "out" / "ui-states.md"

CATEGORY_TITLES = {
    "1": "1. 缺少加载态（advisory）",
    "2": "2. 缺少空态（advisory）",
    "3": "3. 危险操作无二次确认（hard）",
    "4": "4. 权限态缺失（heuristic / advisory）",
    "5a": "5a. 可访问性：仅图标按钮无可访问名称（advisory）",
    "5b": "5b. 可访问性：表单控件无标签（advisory）",
}

DESTRUCTIVE_NAME_RE = re.compile(r"(?i)(delete|remove|clear|revoke|reset|cleanup)")

REQUEST_MUT_RE = re.compile(r"\brequest\s*\.\s*(delete|post|put|patch)\s*(?:<[^()]*>)?\s*\(")
URL_DESTRUCTIVE_RE = re.compile(
    r"request\s*\.\s*(?:post|put|patch|delete)\s*(?:<[^()]*>)?\s*\(\s*[`'\"][^`'\"]*?"
    r"(?:delete|reset|cleanup|clear|revoke|remove)",
    re.I,
)

CONFIRM_PATTERNS = {
    "ElMessageBox.confirm": re.compile(r"ElMessageBox\s*\.\s*confirm\s*\("),
    "ElMessageBox.prompt": re.compile(r"ElMessageBox\s*\.\s*prompt\s*\("),
    "ElMessageBox(": re.compile(r"\bElMessageBox\s*\("),
    "confirm(": re.compile(r"(?<![.\w$])confirm\s*\("),
    "*.confirm(": re.compile(r"\.\s*confirm\s*\("),
    "el-popconfirm": re.compile(r"<el-popconfirm\b"),
}

PERMISSION_RE = re.compile(
    r"\bv-permission\b"
    r"|\bhasPermission\s*\("
    r"|\busePermission\s*\("
    r"|\bhasAnyRole\s*\("
    r"|\bisAdmin\b"
    r"|\b(?:userStore|userInfo|authStore)\s*\.\s*(?:permissions|roles)\b"
    r"|\buserInfo\s*\.\s*roles\s*\.\s*includes\s*\("
    r"|v-if\s*=\s*\"[^\"]*(?:hasPermission|isAdmin|userStore\.permissions)"
    r"|:disabled\s*=\s*\"[^\"]*(?:hasPermission|isAdmin|userStore\.permissions)"
)

LOADING_V_RE = re.compile(r"\bv-loading\b")
TABLE_LOADING_RE = re.compile(r"<el-table(?=[\s>])[^>]*?:loading\s*=")
SKELETON_LOADING_RE = re.compile(r"<el-skeleton(?=[\s>])[^>]*?:loading\s*=")
EL_EMPTY_RE = re.compile(r"<el-empty\b")
TABLE_EMPTY_SLOT_RE = re.compile(r"#empty\b|v-slot:empty\b")

IMPORT_NAMED_RE = re.compile(r"import\s*(?:type\s+)?\{([^}]*)\}\s*from\s*['\"]([^'\"]+)['\"]")

DEF_RE = re.compile(
    r"(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{"
    r"|(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::[^=\n]+)?=\s*(?:async\s*)?"
    r"(?:\([^)]*\)\s*(?::[^=\n]+)?|[A-Za-z_$][\w$]*)\s*=>\s*\{"
    r"|(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?function\s*\([^)]*\)\s*\{",
    re.M,
)

TEMPLATE_EVENT_RE = re.compile(r"@[A-Za-z][\w:.\-]*\s*=\s*\"([^\"]*)\"")
IDENT_RE = re.compile(r"[A-Za-z_$][\w$]*")


# --------------------------------------------------------------------------- #
# text helpers
# --------------------------------------------------------------------------- #
def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig", errors="replace")


def mask_js(text: str) -> str:
    """Blank out comments and string/template-literal contents, preserving offsets."""
    out = list(text)
    i, n = 0, len(text)
    state = None  # None | '//' | '/*' | "'" | '"' | '`'
    while i < n:
        c = text[i]
        if state is None:
            if c == "/" and i + 1 < n and text[i + 1] == "/":
                out[i] = out[i + 1] = " "
                state = "//"
                i += 2
                continue
            if c == "/" and i + 1 < n and text[i + 1] == "*":
                out[i] = out[i + 1] = " "
                state = "/*"
                i += 2
                continue
            if c in "'\"`":
                out[i] = " "
                state = c
                i += 1
                continue
            i += 1
        elif state == "//":
            if c == "\n":
                state = None
            else:
                out[i] = " "
            i += 1
        elif state == "/*":
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                out[i] = out[i + 1] = " "
                state = None
                i += 2
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
        else:
            if c == "\\":
                out[i] = " "
                if i + 1 < n and text[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
            elif c == state:
                out[i] = " "
                state = None
                i += 1
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def split_sfc(text: str) -> tuple[str, int, str, int]:
    t_start = text.find("<template")
    if t_start < 0:
        return "", 0, "", 0
    s_start = text.find("<script", t_start)
    t_end = s_start if s_start >= 0 else len(text)
    template, t_off = text[t_start:t_end], t_start
    script, s_off = "", 0
    if s_start >= 0:
        gt = text.find(">", s_start)
        close = text.find("</script>", gt if gt >= 0 else s_start)
        s_off = (gt + 1) if gt >= 0 else s_start
        script = text[s_off:close if close >= 0 else len(text)]
    return template, t_off, script, s_off


class LineIndex:
    def __init__(self, text: str):
        self.starts = [0]
        for m in re.finditer("\n", text):
            self.starts.append(m.end())

    def line(self, offset: int) -> int:
        lo, hi = 0, len(self.starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self.starts[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1


def iter_tags(text: str, name: str, start: int = 0, end: int | None = None):
    """Yield (open_start, open_end, raw_open_tag). Quote-aware scan for '>'."""
    if end is None:
        end = len(text)
    pat = re.compile(r"<" + re.escape(name) + r"(?=[\s/>])")
    pos = start
    while True:
        m = pat.search(text, pos, end)
        if not m:
            return
        i = m.start()
        j = m.end()
        quote = None
        while j < end:
            c = text[j]
            if quote:
                if c == quote:
                    quote = None
            elif c in "\"'":
                quote = c
            elif c == ">":
                break
            j += 1
        yield i, j + 1, text[i:j + 1]
        pos = j + 1


def close_tag(text: str, name: str, start: int) -> tuple[int, int, str]:
    m = re.compile(r"</" + re.escape(name) + r"\s*>").search(text, start)
    if not m:
        return len(text), len(text), ""
    return m.start(), m.end(), m.group(0)


def attrs_nonempty(raw: str, name: str) -> bool:
    """True when attr is present with a non-empty value (works for :attr too)."""
    pat = re.compile(
        r"(?:^|\s)(?::|v-bind:)?" + re.escape(name) + r"\s*=\s*(\"[^\"]*\"|'[^']*')"
    )
    m = pat.search(raw)
    if not m:
        return False
    value = m.group(1)[1:-1].strip()
    return bool(value) and value not in ("''", '""')


def template_ref_positions(template: str, name: str) -> list[int]:
    """Offsets of open tags whose @event expression references `name`."""
    out: list[int] = []
    word = re.compile(r"\b" + re.escape(name) + r"\b")
    for m in TEMPLATE_EVENT_RE.finditer(template):
        if word.search(m.group(1)):
            lt = template.rfind("<", 0, m.start())
            if lt >= 0:
                out.append(lt)
    return out


def bracket_match(masked: str, open_index: int) -> int:
    depth = 0
    for i in range(open_index, len(masked)):
        c = masked[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
    return len(masked) - 1


@dataclass
class FunctionInfo:
    name: str
    start: int          # offset of body `{` (global)
    end: int            # offset of closing `}` (global)
    body: str           # masked body


_API_FACTS: tuple[set[str], set[str]] | None = None


def load_api_facts() -> tuple[set[str], set[str]]:
    """Map @/api/*.ts exported helpers to (destructive, mutating) names.

    Views call helpers whose names do not contain a destructive verb but whose
    implementation hits a destructive URL (e.g. refreshTracingData ->
    POST /tracing/actuator/clear-cache).  This is the only cross-file step.
    """
    global _API_FACTS
    if _API_FACTS is not None:
        return _API_FACTS
    destructive: set[str] = set()
    mutating: set[str] = set()
    api_dir = REPO_ROOT / "frontend" / "src" / "api"
    if api_dir.exists():
        for p in sorted(api_dir.rglob("*.ts")):
            text = read_text(p)
            masked = mask_js(text)
            for dm in DEF_RE.finditer(masked):
                name = dm.group(1) or dm.group(2) or dm.group(3)
                brace = masked.find("{", dm.end() - 1)
                if not name or brace < 0:
                    continue
                close = bracket_match(masked, brace)
                body = masked[brace:close + 1]
                orig = text[brace:close + 1]
                if REQUEST_MUT_RE.search(body):
                    mutating.add(name)
                if (re.search(r"\brequest\s*\.\s*delete\s*(?:<[^()]*>)?\s*\(", body)
                        or URL_DESTRUCTIVE_RE.search(orig)):
                    destructive.add(name)
    _API_FACTS = (destructive, mutating)
    return _API_FACTS


# --------------------------------------------------------------------------- #
# findings
# --------------------------------------------------------------------------- #
@dataclass
class Finding:
    category: str
    file: str
    line: int
    evidence: str
    note: str = ""


@dataclass
class FileFacts:
    rel: str
    table_lines: list[int] = field(default_factory=list)
    loading_kind: str | None = None
    loading_line: int = 0
    async_line: int = 0
    confirm_line: int = 0
    confirm_kind: str = ""
    destructive_sites: list[dict] = field(default_factory=list)
    suppressed_sites: list[dict] = field(default_factory=list)
    local_only: list[dict] = field(default_factory=list)
    unwired: list[dict] = field(default_factory=list)
    has_permission: bool = False
    mutation_line: int = 0
    mutation_why: str = ""


@dataclass
class Result:
    root: Path
    files: list[str]
    facts: dict[str, FileFacts] = field(default_factory=dict)
    findings: dict[str, list[Finding]] = field(default_factory=dict)

    def add(self, finding: Finding) -> None:
        self.findings.setdefault(finding.category, []).append(finding)

    def files_hit(self, category: str) -> list[str]:
        seen: list[str] = []
        for f in self.findings.get(category, []):
            if f.file not in seen:
                seen.append(f.file)
        return seen

    def count(self, category: str) -> int:
        return len(self.findings.get(category, []))


def snippet(text: str, line: int, limit: int = 150) -> str:
    lines = text.splitlines()
    if 1 <= line <= len(lines):
        s = lines[line - 1].strip()
    else:
        s = ""
    s = s.replace("`", "'")
    if len(s) > limit:
        s = s[: limit - 1] + "…"
    return s


def analyze_file(path: Path, rel: str, result: Result) -> None:
    text = read_text(path)
    lines = LineIndex(text)
    template, t_off, script, s_off = split_sfc(text)
    masked = mask_js(script)
    facts = FileFacts(rel=rel)
    result.facts[rel] = facts

    # ---------------- table inventory ----------------
    tables = list(iter_tags(template, "el-table"))
    if "el-table-v2" in template:
        tables += list(iter_tags(template, "el-table-v2"))
    facts.table_lines = [lines.line(t_off + t[0]) for t in tables]

    # ---------------- loading mechanism ----------------
    m = LOADING_V_RE.search(template)
    if m:
        facts.loading_kind, facts.loading_line = "v-loading", lines.line(t_off + m.start())
    else:
        m = TABLE_LOADING_RE.search(template)
        if m:
            facts.loading_kind, facts.loading_line = "el-table :loading", lines.line(t_off + m.start())
        else:
            m = SKELETON_LOADING_RE.search(template)
            if m:
                facts.loading_kind, facts.loading_line = "el-skeleton :loading", lines.line(t_off + m.start())

    # ---------------- async fetch evidence ----------------
    aw = re.search(r"\bawait\b", masked)
    if aw:
        network = re.search(
            r"request\s*\.\s*(?:get|post|put|delete|patch)\s*\("
            r"|\bonMounted\s*\("
            r"|\b(?:load|fetch)[A-Z]\w*\s*\("
            r"|\bget[A-Z]\w*\s*\(",
            masked,
        )
        if network:
            facts.async_line = lines.line(t_off + aw.start())

    # ---------------- confirm mechanism (file level) ----------------
    for label, pat in CONFIRM_PATTERNS.items():
        cm = pat.search(text)
        if cm:
            facts.confirm_kind = label
            facts.confirm_line = lines.line(cm.start())
            break

    # ---------------- mutation / destructive call inventory ----------------
    api_destructive, _api_mutating = load_api_facts()
    destructive_imports: set[str] = set()
    mutating_imports: set[str] = set()
    for m in IMPORT_NAMED_RE.finditer(script):
        if "/api" not in m.group(2):
            continue
        for raw in m.group(1).split(","):
            name = raw.strip().split(" as ")[-1].strip()
            if not name:
                continue
            if DESTRUCTIVE_NAME_RE.search(name) or name in api_destructive:
                destructive_imports.add(name)
            if (re.match(r"(?i)^(add|create|update|save|import|delete|remove|submit|reset|revoke|cleanup|batch)",
                         name)):
                mutating_imports.add(name)
    mutating_imports |= destructive_imports

    # ---------------- functions ----------------
    functions: list[FunctionInfo] = []
    for dm in DEF_RE.finditer(masked):
        name = dm.group(1) or dm.group(2) or dm.group(3)
        brace = masked.find("{", dm.end() - 1)
        if not name or brace < 0:
            continue
        close = bracket_match(masked, brace)
        functions.append(
            FunctionInfo(name=name, start=brace, end=close,
                         body=masked[brace:close + 1])
        )
    by_name: dict[str, FunctionInfo] = {}
    for fn in functions:
        by_name.setdefault(fn.name, fn)

    # template-reachable handler names
    template_handlers: set[str] = set()
    for tm in TEMPLATE_EVENT_RE.finditer(template):
        for ident in IDENT_RE.findall(tm.group(1)):
            if ident in by_name:
                template_handlers.add(ident)

    call_graph: dict[str, set[str]] = {}
    for fn in functions:
        call_graph[fn.name] = {c for c in IDENT_RE.findall(fn.body) if c in by_name}

    reachable: set[str] = set(template_handlers)
    queue = list(template_handlers)
    while queue:
        name = queue.pop()
        for callee in call_graph.get(name, set()):
            if callee not in reachable:
                reachable.add(callee)
                queue.append(callee)

    # per-function call inventory
    calls_by_fn: dict[str, dict[str, list[tuple[int, str, str]]]] = {}
    for fn in functions:
        body = fn.body
        orig = script[fn.start:fn.end + 1]
        inv: dict[str, list[tuple[int, str, str]]] = {
            "req": [], "destr_api": [], "mut_api": [], "url_destr": [],
        }
        for m in REQUEST_MUT_RE.finditer(body):
            inv["req"].append((fn.start + m.start(), m.group(1), m.group(0)))
        for name in destructive_imports:
            for m in re.finditer(r"\b" + re.escape(name) + r"\s*\(", body):
                inv["destr_api"].append((fn.start + m.start(), name, m.group(0)))
        for name in mutating_imports:
            for m in re.finditer(r"\b" + re.escape(name) + r"\s*\(", body):
                inv["mut_api"].append((fn.start + m.start(), name, m.group(0)))
        for m in re.finditer(r"\b(api|http|service|client)\s*\.\s*delete\s*\(", body):
            inv["destr_api"].append((fn.start + m.start(), "api.delete", m.group(0)))
        for m in URL_DESTRUCTIVE_RE.finditer(orig):
            inv["url_destr"].append((fn.start + m.start(), "url", m.group(0)))
        calls_by_fn[fn.name] = inv

    confirmed_handlers: set[str] = set()
    for tm in re.finditer(r"@confirm\s*=\s*\"([^\"]*)\"", template):
        confirmed_handlers.update(IDENT_RE.findall(tm.group(1)))

    # ---------------- category 3 sites ----------------
    for fn in functions:
        inv = calls_by_fn[fn.name]
        sites: list[tuple[int, str, str]] = []
        for offset, verb, call in inv["req"]:
            if verb == "delete":
                sites.append((offset, "request.delete", call))
        for offset, name, call in inv["destr_api"]:
            sites.append((offset, "api:" + name, call))
        for offset, name, call in inv["url_destr"]:
            sites.append((offset, "destructive-url", call))

        name_destructive = bool(DESTRUCTIVE_NAME_RE.search(fn.name))
        if not sites and name_destructive and (inv["req"] or inv["mut_api"] or inv["destr_api"]):
            if inv["req"]:
                offset, verb, call = inv["req"][0]
                sites.append((offset, "name+" + verb + ".call", call))
            elif inv["mut_api"]:
                offset, name, call = inv["mut_api"][0]
                sites.append((offset, "name+" + name, call))
            elif inv["url_destr"]:
                offset, _, call = inv["url_destr"][0]
                sites.append((offset, "name+url", call))

        if not sites:
            if name_destructive and fn.name in reachable:
                facts.local_only.append(
                    {"line": lines.line(s_off + fn.start), "name": fn.name}
                )
            continue

        for offset, kind, call in sites:
            site = {
                "line": lines.line(s_off + offset),
                "name": fn.name,
                "kind": kind,
                "call": call,
                "reachable": fn.name in reachable,
                "confirm_in_fn": bool(
                    any(p.search(script[fn.start:fn.end + 1]) for p in CONFIRM_PATTERNS.values())
                ) or fn.name in confirmed_handlers,
            }
            if not site["reachable"]:
                facts.unwired.append(site)
            elif facts.confirm_kind:
                facts.suppressed_sites.append(site)
            else:
                facts.destructive_sites.append(site)

    # inline request.delete in template (not inside a function)
    for tm in re.finditer(r"request\s*\.\s*delete\s*\(", template):
        facts.destructive_sites.append({
            "line": lines.line(t_off + tm.start()),
            "name": "<template>",
            "kind": "request.delete",
            "call": "request.delete(",
            "reachable": True,
            "confirm_in_fn": False,
        })

    # dedupe by (line, name); a request.delete('.../cleanup') matches both the
    # request.delete rule and the destructive-URL rule, which is one call site.
    def dedupe(sites: list[dict]) -> list[dict]:
        out, seen = [], set()
        for s in sites:
            key = (s["line"], s["name"])
            if key in seen:
                continue
            seen.add(key)
            out.append(s)
        return sorted(out, key=lambda x: (x["line"], x["name"]))

    facts.destructive_sites = dedupe(facts.destructive_sites)
    facts.suppressed_sites = dedupe(facts.suppressed_sites)
    facts.unwired = dedupe(facts.unwired)
    facts.local_only = sorted(facts.local_only, key=lambda x: x["line"])

    # ---------------- cat 1 / 2 ----------------
    if tables and facts.async_line and not facts.loading_kind:
        first = facts.table_lines[0] if facts.table_lines else 1
        result.add(Finding(
            "1", rel, first, snippet(text, first),
            "el-table + await-based fetch; no v-loading / :loading / el-skeleton binding anywhere",
        ))

    for idx, (open_start, open_end, raw) in enumerate(tables):
        tline = lines.line(t_off + open_start)
        has_empty_attr = attrs_nonempty(raw, "empty-text")
        span_end = close_tag(template, "el-table", open_end)[0]
        has_slot = bool(TABLE_EMPTY_SLOT_RE.search(template[open_end:span_end])) if span_end > open_end else False
        has_el_empty = bool(EL_EMPTY_RE.search(template))
        if not (has_empty_attr or has_slot or has_el_empty):
            result.add(Finding(
                "2", rel, tline, snippet(text, tline),
                "no empty-text / #empty slot / <el-empty>; Element Plus still renders its default empty block",
            ))

    # ---------------- cat 3 ----------------
    for site in facts.destructive_sites:
        result.add(Finding(
            "3", rel, site["line"], snippet(text, site["line"]),
            "%s() → %s; no confirm anywhere in file" % (site["name"], site["kind"]),
        ))

    # ---------------- cat 4 ----------------
    # A control counts only when it can reach a server mutation (or is an import/export
    # control). Purely local add/remove-row buttons are deliberately not counted.
    mutation_fns = {
        fn.name for fn in functions
        if fn.name in reachable and any(calls_by_fn[fn.name][k] for k in ("req", "destr_api", "mut_api", "url_destr"))
    }

    def entry_handler(target: str) -> str | None:
        if target in template_handlers:
            return target
        for h in sorted(template_handlers):
            seen = {h}
            queue2 = [h]
            while queue2:
                cur = queue2.pop()
                for callee in call_graph.get(cur, set()):
                    if callee == target:
                        return h
                    if callee not in seen:
                        seen.add(callee)
                        queue2.append(callee)
        return None

    control_lines: list[tuple[int, str]] = []
    for name in sorted(mutation_fns):
        entry = entry_handler(name)
        if not entry:
            continue
        for pos in template_ref_positions(template, entry):
            control_lines.append((lines.line(t_off + pos), "handler " + entry))

    # import/export controls are permission-relevant even when they only read data
    for open_start, open_end, raw in iter_tags(template, "el-button"):
        content_end = close_tag(template, "el-button", open_end)[0]
        content = template[open_end:content_end]
        keys = " ".join(re.findall(r"\bt\(\s*['\"]([^'\"]+)['\"]", content + raw))
        if re.search(r"导出|导入", content) or re.search(
                r"(?i)(?:^|[._-])(?:export|import)(?=[A-Z._-]|$)", keys):
            control_lines.append((lines.line(t_off + open_start), "import/export control"))

    mutation_line, mutation_why = 0, ""
    if control_lines:
        mutation_line, mutation_why = min(control_lines, key=lambda x: (x[0], x[1]))
    facts.mutation_line = mutation_line
    facts.mutation_why = mutation_why
    facts.has_permission = bool(PERMISSION_RE.search(template + script))
    if mutation_line and not facts.has_permission:
        result.add(Finding(
            "4", rel, mutation_line, snippet(text, mutation_line),
            "mutation control (%s) but no v-permission / hasPermission / role / permission-bound disabled" % mutation_why,
        ))

    # ---------------- cat 5a / 5b ----------------
    for open_start, open_end, raw in iter_tags(template, "el-button"):
        content_end, _, _ = close_tag(template, "el-button", open_end)
        content = template[open_end:content_end]
        icon = ("icon=" in raw) or ("<el-icon" in content) or ("<el-icon" in raw)
        if not icon:
            continue
        if (attrs_nonempty(raw, "aria-label") or attrs_nonempty(raw, "aria-labelledby")
                or attrs_nonempty(raw, "title")):
            continue
        cleaned = re.sub(r"<el-icon\b.*?</el-icon>", "", content, flags=re.S)
        cleaned = re.sub(r"<!--.*?-->", "", cleaned, flags=re.S)
        cleaned = re.sub(r"<[^>]+>", "", cleaned)
        cleaned = cleaned.replace("&nbsp;", " ").strip()
        if cleaned:
            continue
        line = lines.line(t_off + open_start)
        result.add(Finding(
            "5a", rel, line, snippet(text, line),
            "icon-only el-button without aria-label/title",
        ))

    form_items: list[tuple[int, int, str]] = []
    stack: list[tuple[int, int, str]] = []
    events: list[tuple[int, str, tuple[int, int, str] | None]] = []
    for os_, oe_, raw in iter_tags(template, "el-form-item"):
        events.append((os_, "open", (os_, oe_, raw)))
    for m in re.finditer(r"</el-form-item\s*>", template):
        events.append((m.start(), "close", None))
    events.sort(key=lambda x: x[0])
    for pos, kind, payload in events:
        if kind == "open":
            stack.append(payload)  # type: ignore[arg-type]
        elif stack:
            os_, oe_, raw = stack.pop()
            form_items.append((os_, pos, raw))
    for os_, raw in stack:
        form_items.append((os_, len(template), raw))

    labelled_spans = [
        (a, b) for a, b, raw in form_items if attrs_nonempty(raw, "label")
    ]

    for tag_name in ("el-input", "el-select", "el-textarea"):
        for open_start, open_end, raw in iter_tags(template, tag_name):
            if (attrs_nonempty(raw, "aria-label") or attrs_nonempty(raw, "aria-labelledby")
                    or attrs_nonempty(raw, "placeholder")):
                continue
            inside = any(a <= open_start < b for a, b in labelled_spans)
            if inside:
                continue
            line = lines.line(t_off + open_start)
            result.add(Finding(
                "5b", rel, line, snippet(text, line),
                "<%s> outside a labelled el-form-item, no aria-label/placeholder" % tag_name,
            ))


def analyze_tree(root: Path) -> Result:
    files = sorted(
        p for p in root.rglob("*.vue")
        if "node_modules" not in p.parts
        and "dist" not in p.parts
        and ".git" not in p.parts
        and "__tests__" not in p.parts
        and not p.name.endswith(".spec.ts")
    )
    result = Result(root=root, files=[p.relative_to(root).as_posix() for p in files])
    for p in files:
        analyze_file(p, p.relative_to(root).as_posix(), result)
    for cat in result.findings:
        result.findings[cat].sort(key=lambda f: (f.file, f.line, f.note))
    return result


def parse_route_permissions() -> dict[str, list[str]]:
    """Best-effort map of view SFC -> route meta.permissions (static import paths only)."""
    router_path = REPO_ROOT / "frontend" / "src" / "router" / "index.ts"
    if not router_path.exists():
        return {}
    text = read_text(router_path)
    masked = mask_js(text)
    out: dict[str, list[str]] = {}
    for i, ch in enumerate(masked):
        if ch != "{":
            continue
        j = bracket_match(masked, i)
        block = text[i:j + 1]
        imp = re.search(r"component:\s*\(\)\s*=>\s*import\(['\"]([^'\"]+)['\"]\)", block)
        if not imp:
            continue
        view = imp.group(1).replace("../views/", "").replace("./", "")
        perms: list[str] = []
        pm = re.search(r"permissions:\s*\[([^\]]*)\]", block)
        if pm:
            perms = re.findall(r"['\"]([^'\"]+)['\"]", pm.group(1))
        out[view] = sorted(set(out.get(view, [])) | set(perms))
    return out


# --------------------------------------------------------------------------- #
# report
# --------------------------------------------------------------------------- #
def md(s: str) -> str:
    return s.replace("|", "\\|")


def render_report(result: Result, out_path: Path) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    files = result.files
    src_root = REPO_ROOT / "frontend" / "src"
    src_files = [p for p in src_root.rglob("*") if p.is_file() and "node_modules" not in p.parts]
    src_code_count = sum(1 for p in src_files if p.suffix in (".vue", ".ts"))
    src_vue_count = sum(1 for p in src_files if p.suffix == ".vue")
    table_files = [f for f in files if result.facts[f].table_lines]
    table_instances = sum(len(result.facts[f].table_lines) for f in files)
    loading_counts: dict[str, int] = {}
    for f in table_files:
        kind = result.facts[f].loading_kind
        if kind:
            loading_counts[kind] = loading_counts.get(kind, 0) + 1

    cat_labels = [
        ("1", "缺少加载态", "advisory"),
        ("2", "缺少空态", "advisory"),
        ("3", "危险操作无二次确认", "HARD"),
        ("4", "权限态缺失", "heuristic"),
        ("5a", "仅图标按钮", "advisory"),
        ("5b", "无标签表单控件", "advisory"),
    ]

    def cat_files(cat: str) -> int:
        return len(result.files_hit(cat))

    route_perms = parse_route_permissions()

    out: list[str] = []
    out.append("# UI 状态审计报告 — JAiRouter 管理控制台（Vue 3 + Element Plus）\n")
    out.append("> 自动生成，请勿手改。生成器：`scripts/audit/detect-ui-states.py`；")
    out.append("> 复现：`python scripts/audit/detect-ui-states.py`；校准：`python scripts/audit/detect-ui-states.py --self-test`。\n")
    out.append("- 生成时间：%s" % now)
    out.append("- 扫描根：`frontend/src/views`（只扫 SFC，不扫 `.ts` 辅助模块）")
    out.append("- 扫描文件：**%d** 个 `.vue`（排除 `__tests__`）" % len(files))
    out.append("- 含 `<el-table>` 的页面：**%d**；表实例：**%d**" % (len(table_files), table_instances))
    out.append("- 路由可达的视图组件（`router/index.ts` 静态 import 去重）：**%d** 个；其余为对话框/子组件。"
               % len(route_perms))
    out.append("- 任务书称 79 views / 146 files；本检出 `frontend/src` 下 `.vue`+`.ts` 共 **%d** 个"
               "（其中 `.vue` **%d** 个），`frontend/src/views` 下的视图 SFC 为 **%d** 个。"
               "“79 views”与本检出不符（63 个视图 SFC），差异原因未知，本报告一律以实际扫描数为分母。\n"
               % (src_code_count, src_vue_count, len(files)))

    out.append("## 0. 先读这个：hard 与 advisory 的边界\n")
    out.append("| 类别 | 定性 | 说明 |")
    out.append("| --- | --- | --- |")
    out.append("| 1 缺少加载态 | advisory（粗规则） | 只看是否出现 `v-loading`/`:loading`/`el-skeleton :loading`；任一出现即视为有机制，不校验它是否绑在真正取数的那张表上。 |")
    out.append("| 2 缺少空态 | advisory | 任务书自身明示：Element Plus 的 `el-table` 即使不写 `empty-text` 也会渲染默认空态；本类只说明没有自定义空态。 |")
    out.append("| 3 危险操作无二次确认 | **HARD** | 调用点与确认语句都是源码事实；但确认是**文件级**判定（见下）。 |")
    out.append("| 4 权限态缺失 | heuristic | 前端是否 disable/hide 与后端是否 403 是两件事；路由级 `meta.permissions` 未建模。 |")
    out.append("| 5a/5b 可访问性 | advisory | 文本规则；动态绑定、全局组件、透传 props 均看不到。 |\n")
    out.append("> 任务书内部有一处矛盾：正文写 hard = “(2, 3)”，但类别 2 的定义又明示其为 advisory。")
    out.append("> 本报告按类别 2 自身的明示定性处理：**只有类别 3 是 hard**，类别 2 归入 advisory。\n")
    out.append("**读法**：hard = 类别 3（源码里能直接定位的未确认破坏性调用）；advisory = 类别 1、2、4、5a/5b"
               "（依赖粗规则或人判断，不应直接当缺陷清单派活）。\n")

    out.append("## 1. 汇总计数\n")
    out.append("| 类别 | 定性 | 命中文件 | 命中条目 | 分母 |")
    out.append("| --- | --- | ---: | ---: | --- |")
    out.append("| 1 缺少加载态 | advisory | %d | %d | %d 个表页面 / %d views |"
               % (cat_files("1"), result.count("1"), len(table_files), len(files)))
    out.append("| 2 缺少空态 | advisory | %d | %d | %d 个表页面 / %d views |"
               % (cat_files("2"), result.count("2"), len(table_files), len(files)))
    out.append("| 3 危险操作无二次确认 | **HARD** | %d | %d | %d views |"
               % (cat_files("3"), result.count("3"), len(files)))
    out.append("| 4 权限态缺失 | heuristic | %d | %d | %d views |"
               % (cat_files("4"), result.count("4"), len(files)))
    out.append("| 5a 仅图标按钮 | advisory | %d | %d | %d views |"
               % (cat_files("5a"), result.count("5a"), len(files)))
    out.append("| 5b 无标签表单控件 | advisory | %d | %d | %d views |"
               % (cat_files("5b"), result.count("5b"), len(files)))
    out.append("")

    out.append("### 加载机制分布（表页面，用于解释类别 1 的“通过”）\n")
    if loading_counts:
        for kind, n in sorted(loading_counts.items(), key=lambda kv: -kv[1]):
            out.append("- `%s`：%d 个文件" % (kind, n))
    else:
        out.append("- 未发现任何加载机制")
    no_loading_but_not_reported = [
        f for f in table_files
        if not result.facts[f].loading_kind and not result.facts[f].async_line
    ]
    out.append("- 有表但**无 await 取数证据**（未纳入类别 1，见附录 C）：%d 个\n"
               % len(no_loading_but_not_reported))

    out.append("## 2. 逐类方法与结果\n")

    sections = [
        ("1", "缺少加载态（advisory）",
         "文件含 `<el-table>` 或 `<el-table-v2>`；脚本区出现 `await` 且伴随 `request.*` / `onMounted(` / "
         "`load*()` / `fetch*()` / `get[A-Z]*()` 之一；模板中**任何位置**都没有 `v-loading`、`el-table` 的 "
         "`:loading`、`el-skeleton` 的 `:loading`。按钮上的 `:loading` 不算表的加载态。",
         "全局 axios 拦截器 spinner、父组件传入的 loading、`v-if=\"loading\"` 自绘骨架、"
         "`.then()` 取数、store 计算属性间接取数；`v-loading` 出现在同一文件的另一张表上也视为通过（粗规则）。"
         "`PageSkeleton` 只是页面布局组件（无加载逻辑），不计为加载态。"),
        ("2", "缺少空态（advisory）",
         "每个 `<el-table>` 开标签上无 `empty-text`，表内无 `#empty` 插槽，且整个文件无 `<el-empty>`。",
         "Element Plus 自带默认空态，因此本类不代表“空白页”；也不识别 `v-if=\"rows.length===0\"` 自绘空态。"),
        ("3", "危险操作无二次确认（HARD）",
         "存在**从模板可达**的破坏性调用，且文件内没有任何 `ElMessageBox.confirm` / `ElMessageBox(` / "
         "`confirm(` / `ElMessageBox.prompt` / `el-popconfirm`。破坏性调用 = `request.delete(...)`、"
         "URL 含 delete/reset/cleanup/clear/revoke/remove 的变更请求、从 `@/api/*` 导入且名字含上述动词的"
         "函数调用、或名字含上述动词且函数体内有 HTTP 变更调用的处理函数。",
         "**确认是文件级**：只要文件任一处置有确认，整文件的所有调用点都不报告（附录 A 列出这些文件，"
         "附录 A2 另列“文件有确认、但该调用点所在函数体内没有确认”的站点）。"),
        ("4", "权限态缺失（heuristic）",
         "文件里存在**从模板可达**、会发起服务端变更的控件（`request.post/put/delete/patch`、`@/api/*` 中"
         "以 add/create/update/save/delete/remove/import/submit/reset/revoke/cleanup 开头的函数、"
         "URL 含破坏性动词的请求），或按钮是导入/导出；但全文没有 `v-permission` / `hasPermission(` / "
         "`usePermission(` / `userStore.permissions` / 角色判断 / 绑定到权限的 `disabled`。",
         "后端仍执行 RBAC；本类只问“UI 有没有先拦”。路由 `meta.permissions` 已在下方单独列出，"
         "但它只决定能否进入页面，不决定页内按钮是否可见（实测 `frontend/src/views` 内页内权限判断为 0 处）。"
         "只改本地表单的 add/remove 行按钮不计入（它们不需要权限）；变更入口若在另一个子组件文件里，"
         "本文件不计入（那个子组件文件会单独被评估）。"),
        ("5a", "仅图标按钮无可访问名称（advisory）",
         "`<el-button>` 内只有 `<el-icon>` 或仅有 `icon=` 属性、去除标签后无文本，且开标签无 "
         "`aria-label` / `aria-labelledby` / `title` / `:title`。",
         "不把 `<el-tooltip content=...>` 当作可访问名称；也不识别组件内动态拼接的 aria 属性。"),
        ("5b", "表单控件无标签（advisory）",
         "`<el-input>` / `<el-select>` / `<el-textarea>` 不在带非空 `label` 的 `<el-form-item>` 内，"
         "自身也无 `aria-label` / `aria-labelledby` / 非空 `placeholder`。",
         "不把外层自定义组件、`el-form` 的 `label-width`、或父级透传的 label 当作标签。"),
    ]

    for cat, title, rule, blind in sections:
        out.append("### %s — %s\n" % (cat, title))
        out.append("- **判定规则**：%s" % rule)
        out.append("- **覆盖不到**：%s\n" % blind)
        findings = result.findings.get(cat, [])
        if cat == "4":
            write_gate, read_gate, no_gate = [], [], []
            for rel in result.files_hit("4"):
                perms = route_perms.get(rel, [])
                if any(p.endswith(("write", "manage")) for p in perms):
                    write_gate.append(rel)
                elif perms:
                    read_gate.append(rel)
                else:
                    no_gate.append(rel)
            out.append("路由门禁（静态解析 `router/index.ts` 的 `meta.permissions`，best-effort）："
                       "**写/管理级** %d 个；**只读/使用级** %d 个（只读用户仍能进入页面、按钮仍可见）；"
                       "**无路由 permissions / 非路由组件** %d 个。\n"
                       % (len(write_gate), len(read_gate), len(no_gate)))
            out.append("> 注意：`config/menu.ts` 另有一套菜单可见性 `permission`，它只隐藏入口，不拦 URL；"
                       "本表只按路由 `meta.permissions` 分类。\n")
            out.append("| 路由门禁 | 文件 |")
            out.append("| --- | --- |")
            for label, group in (("写/管理级", write_gate), ("只读/使用级", read_gate),
                                 ("无（仅登录/非路由组件）", no_gate)):
                for rel in sorted(group):
                    out.append("| %s | `%s`（%s） |"
                               % (label, rel, ", ".join(route_perms.get(rel, [])) or "—"))
            out.append("")
        if not findings:
            out.append("**结果：0 条**\n")
            continue
        out.append("**结果：%d 条，涉及 %d 个文件**\n" % (len(findings), cat_files(cat)))
        out.append("| # | file:line | 证据 | 说明 |")
        out.append("| ---: | --- | --- | --- |")
        for i, f in enumerate(findings, 1):
            note = f.note
            if cat == "4":
                perms = route_perms.get(f.file, [])
                note += "；route: " + (", ".join(perms) if perms else "none")
            out.append("| %d | `%s:%d` | `%s` | %s |"
                       % (i, f.file, f.line, md(f.evidence), md(note)))
        out.append("")
        if cat == "3":
            site_gaps = [s for rel, facts in result.facts.items()
                         for s in facts.suppressed_sites if not s["confirm_in_fn"]]
            out.append("> 另有 **%d** 个调用点位于“文件别处有确认”的文件中、但该调用点所在函数体内没有确认"
                       "（附录 A2）。它们按任务书的**文件级**规则不报告；按调用点看仍是未确认的破坏性操作。\n"
                       % len(site_gaps))

    # per-file matrix
    out.append("## 3. 每文件命中矩阵（按类别数降序）\n")
    rows = []
    for rel in files:
        cats = []
        for cat in ("1", "2", "3", "4", "5a", "5b"):
            if any(f.file == rel for f in result.findings.get(cat, [])):
                cats.append(cat)
        if not cats:
            continue
        top = sorted({c[0] for c in cats})
        total = sum(1 for c in cats for f in result.findings.get(c, []) if f.file == rel)
        rows.append((len(top), total, rel, cats))
    rows.sort(key=lambda r: (-r[0], -r[1], r[2]))
    out.append("“类别数”按顶层类别去重（5a 与 5b 合计为类别 5）；条目数按实际发现计。\n")
    out.append("| # | 文件 | 类别数 | 命中类别 | 条目数 |")
    out.append("| ---: | --- | ---: | --- | ---: |")
    for i, (ncat, total, rel, cats) in enumerate(rows, 1):
        out.append("| %d | `%s` | %d | %s | %d |"
                   % (i, rel, ncat, ", ".join(cats), total))
    out.append("")
    if not rows:
        out.append("_没有任何文件命中。_\n")

    # appendix A / A2
    out.append("## 附录 A：类别 3 已确认受保护的文件（有 confirm，未报告）\n")
    guarded = [
        (rel, facts) for rel, facts in result.facts.items() if facts.suppressed_sites
    ]
    out.append("共 **%d** 个文件带破坏性调用且有确认机制；按文件级规则它们**不在**类别 3 结果里。\n"
               % len(guarded))
    if guarded:
        out.append("| 文件 | 确认机制（行） | 被抑制的调用点 |")
        out.append("| --- | --- | --- |")
        for rel, facts in sorted(guarded, key=lambda x: x[0]):
            sites = ", ".join("`%d:%s`" % (s["line"], s["name"]) for s in facts.suppressed_sites)
            out.append("| `%s` | %s（%d） | %s |"
                       % (rel, facts.confirm_kind, facts.confirm_line, sites))
        out.append("")

    out.append("### 附录 A2：文件有确认、但调用点所在函数体内没有确认（文件级规则放过了它们）\n")
    a2 = [s for rel, facts in result.facts.items() for s in facts.suppressed_sites if not s["confirm_in_fn"]]
    if not a2:
        out.append("_无。所有被抑制的调用点，其所在函数体内都出现了确认语句。_\n")
    else:
        out.append("| file:line | 函数 | 调用 | 文件确认机制 |")
        out.append("| --- | --- | --- | --- |")
        for rel, facts in sorted(result.facts.items()):
            for s in facts.suppressed_sites:
                if not s["confirm_in_fn"]:
                    out.append("| `%s:%d` | %s | `%s` | %s@%d |"
                               % (rel, s["line"], s["name"], md(s["call"]),
                                  facts.confirm_kind, facts.confirm_line))
        out.append("")

    out.append("## 附录 B：名字含危险动词、但函数体内没有任何 API 调用的处理函数（未计入类别 3）\n")
    local = [(rel, s) for rel, facts in result.facts.items() for s in facts.local_only]
    out.append("共 **%d** 个。任务书的字面规则（“handler 名字含 delete/remove/clear/revoke/reset/cleanup”）"
               "会把它们算作破坏性操作；其中多数只是重置表单/清空本地数组，少数是“打开清理对话框”。"
               "这里要求函数体内存在 API 调用才算，故排除（真正的破坏性调用若存在，会在它自己的函数里被计数）。\n"
               % len(local))
    if local:
        out.append("| file:line | 函数 |")
        out.append("| --- | --- |")
        for rel, s in sorted(local, key=lambda x: (x[0], x[1]["line"])):
            out.append("| `%s:%d` | `%s` |" % (rel, s["line"], s["name"]))
        out.append("")

    out.append("## 附录 C：有 `<el-table>` 但无 `await` 取数证据（未纳入类别 1）\n")
    static = [f for f in table_files if not result.facts[f].async_line]
    out.append("共 **%d** 个。可能是静态表 / 纯本地数据 / `.then()` 取数（正则看不到）。\n" % len(static))
    if static:
        for f in static:
            out.append("- `%s`（首表行 %d）" % (f, result.facts[f].table_lines[0]))
        out.append("")

    out.append("## 附录 D：破坏性调用存在但模板不可达（未计入类别 3）\n")
    unwired = [(rel, s) for rel, facts in result.facts.items() for s in facts.unwired]
    if not unwired:
        out.append("_无。_\n")
    else:
        out.append("| file:line | 函数 | 调用 |")
        out.append("| --- | --- | --- |")
        for rel, s in sorted(unwired, key=lambda x: (x[0], x[1]["line"])):
            out.append("| `%s:%d` | `%s` | `%s` |" % (rel, s["line"], s["name"], md(s["call"])))
        out.append("")

    # blind spots
    out.append("## 4. 盲区（这些规则看不到的东西）\n")
    out.append("1. **动态权限判断**：权限码若由运行期变量拼出（`hasPermission(module + ':' + action)`），"
               "或由后端返回的按钮白名单驱动，本扫描器只认字面 token；反过来，出现 `hasPermission(` "
               "也不等于判断正确。")
    out.append("2. **全局注册的组件/指令**：`v-permission` 若是插件注册、页面里拼成 `v-if` 或自定义指令别名，"
               "类别 4 看不到；`el-button` 等若是二次封装组件（`<AppButton>`），类别 5 完全扫不到。")
    out.append("3. **继承/透传 props**：`v-bind=\"$attrs\"`、`inheritAttrs`、`<component :is>`、"
               "slot 由父组件传入的 `aria-label`/`placeholder`/`title` 都不在文本里。")
    out.append("4. **运行期计算条件**：`v-if=\"canEdit\"`、`disabled=\"!isEditable(row)\"` 这类条件名"
               "不含 permission 字样时，类别 4 视为“没有权限机制”；反之含 permission 字样也不证明逻辑正确。")
    out.append("5. **确认的语义**：文件里出现 `confirm(` 只证明有一次阻断式交互，不证明文案说清了后果；"
               "`ElMessageBox.prompt` 与 `el-popconfirm` 被计为确认手段，任务书原文只列了 `confirm` 系。")
    out.append("6. **危险动词的边界**：类别 3 只认 delete/remove/clear/revoke/reset/cleanup 与 URL 中的同名动词；"
               "`disable` / `toggle` / `invalidate` / `purge` 这类敏感但不在名单内的操作未计入。")
    out.append("7. **加载/空态的真实效果**：不渲染、不截图、不跑浏览器；`v-loading` 绑错变量、"
               "空态被 `v-show` 藏住之类都发现不了。")
    out.append("8. **路由/菜单级权限**：`router/index.ts` 的 `meta.permissions` 与 `menu.ts` 的菜单过滤构成"
               "页面级门禁；报告只对前者做了 best-effort 静态映射，`menu.ts` 的入口隐藏未建模。"
               "因此类别 4 的逐文件清单**不能**直接读成“任何人点得到这些按钮”：写/管理级路由会先挡住无权用户，"
               "但只读级路由与无路由 permissions 的页面里，只读用户确实能看到并点击变更按钮。")
    out.append("9. **跨文件组合**：破坏性操作可能封装在 `@/api` 里由通用 `handleSubmit` 调用；"
               "类别 3 只在本文件的函数调用图内做可达性分析，不跨文件追踪 `@/api/*.ts` 的实现。")
    out.append("10. **注释与字符串**：脚本区注释/字符串已被屏蔽，但**模板注释**（`<!-- -->`）没有剥离；"
               "注释掉的 `v-loading`/`empty-text` 会被当作存在，可能造成类别 1/2 的假阴性。\n")

    out.append("## 5. 复现与校准\n")
    out.append("```")
    out.append("python scripts/audit/detect-ui-states.py            # 生成本报告")
    out.append("python scripts/audit/detect-ui-states.py --self-test  # 12 个合成 fixture，任一 FAIL 退出码非 0")
    out.append("```\n")
    out.append("校准 fixture 覆盖：类别 1/2/3/4/5a/5b 各一正一负；类别 3 的正例是无确认的 `request.delete`，"
               "负例是同一删除流外加 `ElMessageBox.confirm`。`--self-test` 在临时目录生成 fixture 树，"
               "跑完后删除并断言目录不存在。\n")

    report = "\n".join(out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(report, encoding="utf-8", newline="\n")
    return report


# --------------------------------------------------------------------------- #
# self-test
# --------------------------------------------------------------------------- #
BASE_FIXTURE = """<template>
  <div>
    {body}
  </div>
</template>

<script setup lang="ts">
import {{ ref, onMounted }} from 'vue'
import request from '@/api/request'
{script}
</script>
"""

FIXTURES = {
    "pos-loading.vue": BASE_FIXTURE.format(
        body='<el-table :data="rows"><el-table-column prop="a" /></el-table>',
        script="""
const rows = ref<any[]>([])
onMounted(async () => {
  const res = await request.get('/api/rows')
  rows.value = res.data.data
})
""",
    ),
    "neg-loading.vue": BASE_FIXTURE.format(
        body='<el-table v-loading="loading" :data="rows"><el-table-column prop="a" /></el-table>',
        script="""
const rows = ref<any[]>([])
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  try {
    const res = await request.get('/api/rows')
    rows.value = res.data.data
  } finally {
    loading.value = false
  }
})
""",
    ),
    "pos-empty.vue": BASE_FIXTURE.format(
        body='<el-table v-loading="loading" :data="rows"><el-table-column prop="a" /></el-table>',
        script="""
const rows = ref<any[]>([])
const loading = ref(false)
onMounted(async () => {
  const res = await request.get('/api/rows')
  rows.value = res.data.data
})
""",
    ),
    "neg-empty.vue": BASE_FIXTURE.format(
        body='<el-table v-loading="loading" :data="rows" empty-text="No data"><el-table-column prop="a" /></el-table>',
        script="""
const rows = ref<any[]>([])
const loading = ref(false)
onMounted(async () => {
  const res = await request.get('/api/rows')
  rows.value = res.data.data
})
""",
    ),
    "pos-confirm.vue": BASE_FIXTURE.format(
        body='<el-button type="danger" @click="handleDelete">Delete</el-button>',
        script="""
const handleDelete = async (row: any) => {
  await request.delete(`/api/rows/${row.id}`)
}
""",
    ),
    "neg-confirm.vue": BASE_FIXTURE.format(
        body='<el-button type="danger" @click="handleDelete">Delete</el-button>',
        script="""
import { ElMessageBox } from 'element-plus'
const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('Delete this row?', 'Confirm', { type: 'warning' })
  await request.delete(`/api/rows/${row.id}`)
}
""",
    ),
    "pos-perm.vue": BASE_FIXTURE.format(
        body='<el-button type="primary" @click="handleCreate">Create</el-button>',
        script="""
const handleCreate = async () => {
  await request.post('/api/rows', { name: 'x' })
}
""",
    ),
    "neg-perm.vue": BASE_FIXTURE.format(
        body='<el-button v-if="hasPermission(\'config:rows:write\')" type="primary" @click="handleCreate">Create</el-button>',
        script="""
import { usePermission } from '@/composables/usePermission'
const { hasPermission } = usePermission()
const handleCreate = async () => {
  await request.post('/api/rows', { name: 'x' })
}
""",
    ),
    "pos-icon-label.vue": BASE_FIXTURE.format(
        body='<el-button circle @click="handleAdd"><el-icon><Plus /></el-icon></el-button>',
        script="""
const handleAdd = () => { /* noop */ }
""",
    ),
    "neg-icon-label.vue": BASE_FIXTURE.format(
        body='<el-button circle aria-label="Add row" @click="handleAdd"><el-icon><Plus /></el-icon></el-button>',
        script="""
const handleAdd = () => { /* noop */ }
""",
    ),
    "pos-input-label.vue": BASE_FIXTURE.format(
        body='<el-input v-model="name" />',
        script="""
const name = ref('')
""",
    ),
    "neg-input-label.vue": BASE_FIXTURE.format(
        body='<el-form-item label="Name"><el-input v-model="name" /></el-form-item>',
        script="""
const name = ref('')
""",
    ),
}

# (case-id, category, fixture, expected_reported)
SELF_TEST_CASES = [
    ("cat1 positive: table + await, no loading", "1", "pos-loading.vue", True),
    ("cat1 negative: table + v-loading", "1", "neg-loading.vue", False),
    ("cat2 positive: table without empty-text/el-empty", "2", "pos-empty.vue", True),
    ("cat2 negative: table with empty-text", "2", "neg-empty.vue", False),
    ("cat3 positive: delete call, no confirm", "3", "pos-confirm.vue", True),
    ("cat3 negative: delete call with ElMessageBox.confirm", "3", "neg-confirm.vue", False),
    ("cat4 positive: create button, no permission token", "4", "pos-perm.vue", True),
    ("cat4 negative: create button guarded by hasPermission", "4", "neg-perm.vue", False),
    ("cat5a positive: icon-only button, no aria-label", "5a", "pos-icon-label.vue", True),
    ("cat5a negative: icon-only button with aria-label", "5a", "neg-icon-label.vue", False),
    ("cat5b positive: input without label/placeholder", "5b", "pos-input-label.vue", True),
    ("cat5b negative: input inside labelled el-form-item", "5b", "neg-input-label.vue", False),
]


def run_self_test() -> int:
    print("=" * 78)
    print("SELF-TEST: synthetic .vue fixtures, one positive + one negative per category")
    print("=" * 78)
    scratch = Path(tempfile.mkdtemp(prefix="jairouter-ui-states-selftest-"))
    print("scratch tree: %s" % scratch)
    failures = 0
    try:
        for name, content in FIXTURES.items():
            (scratch / name).write_text(content, encoding="utf-8", newline="\n")
        result = analyze_tree(scratch)
        for label, category, fixture, expected in SELF_TEST_CASES:
            hits = [f for f in result.findings.get(category, []) if f.file == fixture]
            passed = bool(hits) == expected
            status = "PASS" if passed else "FAIL"
            if not passed:
                failures += 1
            where = ("line %d" % hits[0].line) if hits else "no finding"
            print("[%s] %-58s category %-3s expected=%-8s actual=%-8s (%s)"
                  % (status, label, category, "REPORT" if expected else "quiet",
                     "REPORT" if hits else "quiet", where))
        # extra invariant: a guarded delete flow must never be reported under cat 3
        for fixture in ("neg-confirm.vue", "neg-loading.vue", "neg-empty.vue",
                        "neg-perm.vue", "neg-icon-label.vue", "neg-input-label.vue"):
            label = {c[2]: c[1] for c in SELF_TEST_CASES}[fixture]
            if any(f.file == fixture for f in result.findings.get(label, [])):
                print("[FAIL] invariant: negative fixture %s was reported under category %s"
                      % (fixture, label))
                failures += 1
    finally:
        try:
            shutil.rmtree(scratch)
        except OSError as exc:
            print("[FAIL] could not remove scratch tree: %s" % exc)
            failures += 1
        gone = not scratch.exists()
        print("-" * 78)
        print("scratch tree removed: %s (exists=%s)" % (scratch, scratch.exists()))
        if not gone:
            failures += 1
            print("[FAIL] scratch tree still exists")
    print("-" * 78)
    if failures:
        print("SELF-TEST RESULT: FAIL (%d failed case/assertion(s))" % failures)
        return 1
    print("SELF-TEST RESULT: PASS (12/12 cases)")
    return 0


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def print_known_positives(result: Result) -> None:
    guarded = sorted(
        (rel, facts) for rel, facts in result.facts.items() if facts.suppressed_sites
    )
    unguarded = sorted(
        (rel, facts) for rel, facts in result.facts.items() if facts.destructive_sites
    )
    print("=" * 78)
    print("CATEGORY-3 KNOWN-POSITIVE CHECK (real tree)")
    print("=" * 78)
    print("guarded destructive files (confirm present, must NOT be reported): %d" % len(guarded))
    for rel, facts in guarded:
        print("  - %s  confirm=%s@%d  sites=%s"
              % (rel, facts.confirm_kind, facts.confirm_line,
                 ",".join("%d:%s" % (s["line"], s["name"]) for s in facts.suppressed_sites)))
    print("unguarded destructive files (reported): %d" % len(unguarded))
    for rel, facts in unguarded:
        print("  - %s  %s" % (rel, ",".join("%d:%s/%s" % (s["line"], s["name"], s["kind"])
                                          for s in facts.destructive_sites)))
    overlap = {rel for rel, _ in guarded} & {rel for rel, _ in unguarded}
    if overlap:
        print("INVARIANT VIOLATION: guarded file also reported: %s" % sorted(overlap))
    else:
        print("invariant OK: no file with a file-level confirm appears in category 3")
    if not unguarded:
        print("0 unguarded destructive files found — category 3 is empty")
    print()


def main(argv: list[str] | None = None) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true",
                        help="run synthetic fixture calibration and exit")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT,
                        help="directory to scan (default: frontend/src/views)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT,
                        help="report path (default: scripts/audit/out/ui-states.md)")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()

    root = args.root.resolve()
    if not root.exists():
        print("scan root does not exist: %s" % root, file=sys.stderr)
        return 2

    result = analyze_tree(root)
    report = render_report(result, args.out)

    print("=" * 78)
    print("UI STATE AUDIT — %s" % root)
    print("=" * 78)
    print("scanned: %d .vue files | table pages: %d"
          % (len(result.files), sum(1 for f in result.files if result.facts[f].table_lines)))
    for cat, title in CATEGORY_TITLES.items():
        print("cat %-2s %-42s %3d files / %3d items"
              % (cat, title, len(result.files_hit(cat)), result.count(cat)))
    print()
    print_known_positives(result)

    print("report written: %s (%d chars)" % (args.out, len(report)))
    print()
    print("PER-FILE TOP 15 (by distinct categories hit)")
    rows = []
    for rel in result.files:
        cats = [c for c in ("1", "2", "3", "4", "5a", "5b")
                if any(f.file == rel for f in result.findings.get(c, []))]
        if cats:
            total = sum(1 for c in cats for f in result.findings.get(c, []) if f.file == rel)
            rows.append((len({c[0] for c in cats}), total, rel, cats))
    rows.sort(key=lambda r: (-r[0], -r[1], r[2]))
    for n, total, rel, cats in rows[:15]:
        print("  %d  %-58s %s (%d)" % (n, rel, ",".join(cats), total))
    if not rows:
        print("  (none)")
    print()
    print("category-3 unguarded files (complete):")
    for rel in result.files_hit("3"):
        facts = result.facts[rel]
        print("  - %s: %s" % (rel, ",".join(str(s["line"]) for s in facts.destructive_sites)))
    if not result.files_hit("3"):
        print("  (none)")

    overlap = {rel for rel, f in result.facts.items() if f.suppressed_sites} & set(
        result.files_hit("3"))
    return 3 if overlap else 0


if __name__ == "__main__":
    raise SystemExit(main())
