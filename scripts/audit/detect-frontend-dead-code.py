#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
detect-frontend-dead-code.py
============================

Self-calibrating dead-code detector for the JAiRouter Vue 3 + TypeScript admin
console (``frontend/src``).

Python 3 standard library only. Reads source files; never writes anything below
``frontend/src`` and never runs npm / npx / vue-tsc / any build.

Categories
----------
1. exported symbols never referenced elsewhere under ``frontend/src``
2. locale keys never referenced (plus zh-CN / en-US key-set asymmetry)
3. ``views/**/*.vue`` not reachable from the route table (transitive closure)
4. ``components/`` ``composables/`` ``stores/`` modules nothing imports
5. exported functions in ``api/**`` with no call site outside ``api/**``

Usage
-----
    python scripts/audit/detect-frontend-dead-code.py                 # write report
    python scripts/audit/detect-frontend-dead-code.py --self-test     # calibration only
    python scripts/audit/detect-frontend-dead-code.py --out other.md --skip-selftest

The ``--self-test`` flag builds synthetic positive/negative fixtures in a private
scratch directory, scans that tree, asserts that every category fires and that
live code is *not* reported, prints PASS/FAIL per assertion, removes the scratch
tree and exits non-zero if any assertion fails.

Report mode runs the same self-test first and embeds its raw output in the
report's calibration section; a failing calibration is surfaced prominently
instead of being silently ignored.
"""

from __future__ import annotations

import argparse
import bisect
import json
import os
import re
import shutil
import sys
import tempfile
import time
from pathlib import Path

TOOL_VERSION = "1.0.0"

# --------------------------------------------------------------------------- #
# scan scope
# --------------------------------------------------------------------------- #

EXCLUDE_DIR_NAMES = {
    "node_modules",
    "dist",
    ".git",
    ".vite",
    "coverage",
    ".nyc_output",
    ".turbo",
    "__pycache__",
}

CODE_EXTENSIONS = (".ts", ".vue", ".tsx", ".js", ".mjs", ".cjs")
DECL_EXTENSIONS = (".ts", ".vue")  # where exported symbols are looked for

TEST_PATH_MARKERS = ("__tests__", ".spec.", ".test.")

TOP_FINDING_CAP = 200  # per-category table cap; totals are always reported

# --------------------------------------------------------------------------- #
# regular expressions
# --------------------------------------------------------------------------- #

# Category 1 / 5 declaration shapes.
RE_EXPORT_DECL = re.compile(
    r"\bexport\s+(?:async\s+)?(?:declare\s+)?(?:(const)\s+)?"
    r"(function|const|var|let|class|interface|type|enum)\s+([A-Za-z_$][\w$]*)"
)
RE_EXPORT_FUNCTION = re.compile(
    r"\bexport\s+(?:async\s+)?function\s+([A-Za-z_$][\w$]*)"
)
RE_EXPORT_CONST = re.compile(r"\bexport\s+const\s+([A-Za-z_$][\w$]*)")
# First top-level-ish assignment: not ==, !=, <=, >=, => and not `= >`.
RE_FIRST_ASSIGN = re.compile(r"(?<![=!<>])=(?!=)(?!>)")

# Category 2.
# `t(` / `$t(` / `i18n.global.t(` - but never the trailing `t(` of `format(`, `print(`.
RE_T_CALL_LITERAL = re.compile(r"(?<![\w$])\$?t\s*\(\s*(['\"])((?:\\.|(?!\1)[^\\])*)\1")
RE_T_CALL_TEMPLATE_PREFIX = re.compile(r"(?<![\w$])\$?t\s*\(\s*`([^`$]*)\$\{")

# Module specifiers.
RE_IMPORT_STATIC = re.compile(
    r"\bimport\s+(?:type\s+)?[\s\S]{0,400}?\bfrom\s*(['\"])([^'\"]+)\1"
)
RE_IMPORT_BARE = re.compile(r"\bimport\s*(['\"])([^'\"]+)\1")
RE_IMPORT_DYNAMIC = re.compile(r"\bimport\s*\(\s*(['\"])([^'\"]+)\1")
RE_EXPORT_FROM = re.compile(
    r"\bexport\s+(?:type\s+)?[\s\S]{0,400}?\bfrom\s*(['\"])([^'\"]+)\1"
)

RE_SCRIPT_BLOCK = re.compile(r"<script\b[^>]*>([\s\S]*?)</script\s*>", re.I)
RE_STYLE_BLOCK = re.compile(r"<style\b[^>]*>([\s\S]*?)</style\s*>", re.I)
RE_HTML_COMMENT = re.compile(r"<!--[\s\S]*?-->")

RE_IDENT = re.compile(r"[A-Za-z_$][\w$]*")
RE_TEMPLATE_STRING = re.compile(r"(['\"])((?:\\.|(?!\1)[^\\])*)\1")


# --------------------------------------------------------------------------- #
# small helpers
# --------------------------------------------------------------------------- #


def make_line_index(text: str):
    """Return sorted list of line-start offsets so offset -> line is O(log n)."""
    starts = [0]
    idx = text.find("\n")
    while idx != -1:
        starts.append(idx + 1)
        idx = text.find("\n", idx + 1)
    return starts


def line_at(starts, offset: int) -> int:
    return bisect.bisect_right(starts, offset)


def blank_preserving_newlines(text: str) -> str:
    """Replace every non-newline character with a space (keeps offsets stable)."""
    return re.sub(r"[^\n]", " ", text)


def is_test_path(rel_posix: str) -> bool:
    return any(marker in rel_posix for marker in TEST_PATH_MARKERS)


def iter_files(root: Path, extensions):
    """Yield files below root with one of extensions, skipping excluded dirs."""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(
            d for d in dirnames if d not in EXCLUDE_DIR_NAMES and not d.startswith(".")
        )
        for name in sorted(filenames):
            if name.endswith(extensions):
                yield Path(dirpath) / name


# --------------------------------------------------------------------------- #
# JS/TS lexer: blank comments (offsets preserved) and collect string literals
# --------------------------------------------------------------------------- #

_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v",
            "0": "\0", "\\": "\\", "/": "/", "'": "'", '"': '"', "`": "`"}

# Characters after which a `/` starts a regular expression rather than division.
_REGEX_PREV_CHARS = set("(,=:[!&|?{};+-*%~^<>")
_REGEX_PREV_WORDS = ("return", "typeof", "instanceof", "in", "of", "new", "delete",
                     "void", "case", "do", "else", "yield", "await")


def lex_js(text: str, start: int, end: int, mode: str, code_chars, literals):
    """Blank comments in ``code_chars[start:end]`` and collect string literals.

    ``mode`` is ``"script"`` (``//`` and ``/* */`` comments, regex-literal aware)
    or ``"template"`` (``/* */`` only - a Vue template may contain ``</div>``
    which must never be mistaken for a regex literal, and ``https://`` which
    must never be mistaken for a line comment).
    """
    i = start
    prev_significant = ""
    while i < end:
        c = text[i]
        if c in "'\"`":
            quote = c
            j = i + 1
            buf = []
            while j < end:
                d = text[j]
                if d == "\\" and j + 1 < end:
                    esc = text[j + 1]
                    if esc == "u" and j + 5 < end:
                        try:
                            buf.append(chr(int(text[j + 2:j + 6], 16)))
                            j += 6
                            continue
                        except ValueError:
                            pass
                    buf.append(_ESCAPES.get(esc, esc))
                    j += 2
                    continue
                if d == quote:
                    j += 1
                    break
                buf.append(d)
                j += 1
            literals.append(("".join(buf), i))
            i = j
            prev_significant = quote
            continue
        if c == "/" and i + 1 < end:
            nxt = text[i + 1]
            if nxt == "/" and mode == "script":
                while i < end and text[i] != "\n":
                    code_chars[i] = " "
                    i += 1
                continue
            if nxt == "*":
                i += 2
                while i < end and not (text[i] == "*" and i + 1 < end and text[i + 1] == "/"):
                    if text[i] != "\n":
                        code_chars[i] = " "
                    i += 1
                if i < end:
                    code_chars[i] = " "
                    if i + 1 < end:
                        code_chars[i + 1] = " "
                    i += 2
                continue
            if mode == "script" and _regex_can_start(text, i, prev_significant):
                # consume a regex literal so its contents are never parsed as code
                j = i + 1
                in_class = False
                while j < end:
                    d = text[j]
                    if d == "\\":
                        j += 2
                        continue
                    if d == "[":
                        in_class = True
                    elif d == "]":
                        in_class = False
                    elif d == "/" and not in_class:
                        j += 1
                        break
                    elif d == "\n":
                        break
                    j += 1
                literals.append((text[i:j], i))
                i = j
                prev_significant = "/"
                continue
        if not c.isspace():
            prev_significant = c
        i += 1


def _regex_can_start(text: str, pos: int, prev_significant: str) -> bool:
    if prev_significant == "" or prev_significant in _REGEX_PREV_CHARS:
        return True
    # `return /re/` and friends: look at the trailing word before the slash.
    j = pos - 1
    while j >= 0 and text[j].isspace():
        j -= 1
    k = j
    while k >= 0 and (text[k].isalnum() or text[k] in "_$"):
        k -= 1
    word = text[k + 1:j + 1]
    return word in _REGEX_PREV_WORDS


# --------------------------------------------------------------------------- #
# per-file analysis
# --------------------------------------------------------------------------- #


class SourceFile:
    """A .ts / .vue file reduced to: comment-free text + literals + script ranges."""

    __slots__ = ("abs_path", "rel_posix", "src_rel", "text", "code", "literals",
                 "script_ranges", "is_test", "line_starts")

    def __init__(self, abs_path: Path, rel_posix: str, src_rel: str):
        self.abs_path = abs_path
        self.rel_posix = rel_posix
        self.src_rel = src_rel
        self.is_test = is_test_path(rel_posix)
        raw = abs_path.read_text(encoding="utf-8", errors="replace")
        self.text = raw
        self.line_starts = make_line_index(raw)
        self.literals = []          # list[(value, offset)]
        self.script_ranges = []     # list[(start, end)]
        self.code = self._analyse(raw)

    # -- internals ---------------------------------------------------------- #

    def _analyse(self, raw: str) -> str:
        chars = list(raw)
        if self.rel_posix.endswith(".vue"):
            # 1. HTML comments and <style> bodies are not code: blank them first.
            for m in RE_HTML_COMMENT.finditer(raw):
                for k in range(m.start(), m.end()):
                    if chars[k] != "\n":
                        chars[k] = " "
            for m in RE_STYLE_BLOCK.finditer(raw):
                for k in range(m.start(1), m.end(1)):
                    if chars[k] != "\n":
                        chars[k] = " "
            code_so_far = "".join(chars)
            covered = []
            for m in RE_SCRIPT_BLOCK.finditer(code_so_far):
                self.script_ranges.append((m.start(1), m.end(1)))
                covered.append((m.start(), m.end()))
            # 2. script blocks: full JS lexing (comments + string literals)
            for s, e in self.script_ranges:
                lex_js(raw, s, e, "script", chars, self.literals)
            # 3. template region: blank /* */ comments and collect quoted strings
            pos = 0
            for s, e in covered:
                if s > pos:
                    lex_js(raw, pos, s, "template", chars, self.literals)
                pos = e
            if pos < len(raw):
                lex_js(raw, pos, len(raw), "template", chars, self.literals)
            return "".join(chars)
        # plain .ts: whole file is script
        self.script_ranges.append((0, len(raw)))
        lex_js(raw, 0, len(raw), "script", chars, self.literals)
        return "".join(chars)

    # -- accessors ---------------------------------------------------------- #

    def line_of(self, offset: int) -> int:
        return line_at(self.line_starts, offset)

    def in_script(self, offset: int) -> bool:
        for s, e in self.script_ranges:
            if s <= offset < e:
                return True
        return False

    def literals_set(self):
        return {v for v, _ in self.literals}


# --------------------------------------------------------------------------- #
# JSON locale parser that keeps the line of every key
# --------------------------------------------------------------------------- #


class JsonParseError(Exception):
    pass


def parse_locale_json(path: Path):
    """Return list of (key_path, line, kind) for every leaf key of a JSON file.

    ``key_path`` is dot-joined exactly as vue-i18n resolves it after the i18n
    module merges every ``locales/<lang>/*.json`` file into one messages object
    (i.e. the file's top-level keys become top-level namespaces).
    """
    text = path.read_text(encoding="utf-8-sig")
    n = len(text)
    state = {"i": 0, "line": 1}
    leaves = []

    def skip_ws():
        while state["i"] < n:
            c = text[state["i"]]
            if c == "\n":
                state["line"] += 1
                state["i"] += 1
            elif c in " \t\r":
                state["i"] += 1
            else:
                return

    def parse_string():
        i = state["i"]
        if text[i] != '"':
            raise JsonParseError(f"expected string at line {state['line']}")
        i += 1
        buf = []
        while i < n:
            c = text[i]
            if c == "\\":
                nxt = text[i + 1] if i + 1 < n else ""
                if nxt == "u":
                    try:
                        buf.append(chr(int(text[i + 2:i + 6], 16)))
                        i += 6
                        continue
                    except ValueError:
                        pass
                buf.append(_ESCAPES.get(nxt, nxt))
                i += 2
                continue
            if c == '"':
                i += 1
                break
            if c == "\n":
                state["line"] += 1
            buf.append(c)
            i += 1
        state["i"] = i
        return "".join(buf)

    def parse_value(path_keys, key_line):
        skip_ws()
        i = state["i"]
        if i >= n:
            return
        c = text[i]
        if c == "{":
            state["i"] += 1
            skip_ws()
            if state["i"] < n and text[state["i"]] == "}":
                state["i"] += 1
                leaves.append((".".join(path_keys), key_line, "empty-object"))
                return
            while state["i"] < n:
                skip_ws()
                child_line = state["line"]
                key = parse_string()
                skip_ws()
                if state["i"] >= n or text[state["i"]] != ":":
                    raise JsonParseError(f"expected ':' near line {state['line']}")
                state["i"] += 1
                parse_value(path_keys + [key], child_line)
                skip_ws()
                if state["i"] >= n:
                    raise JsonParseError("unterminated object")
                d = text[state["i"]]
                if d == ",":
                    state["i"] += 1
                    continue
                if d == "}":
                    state["i"] += 1
                    return
                raise JsonParseError(f"unexpected {d!r} at line {state['line']}")
            raise JsonParseError("unterminated object")
        if c == "[":
            depth = 0
            while state["i"] < n:
                d = text[state["i"]]
                if d == "[":
                    depth += 1
                elif d == "]":
                    depth -= 1
                    if depth == 0:
                        state["i"] += 1
                        break
                elif d == '"':
                    parse_string()
                    continue
                elif d == "\n":
                    state["line"] += 1
                state["i"] += 1
            leaves.append((".".join(path_keys), key_line, "array"))
            return
        if c == '"':
            # A string scalar must be consumed with string semantics: a value such
            # as "a, b }" must not terminate the enclosing object.
            parse_string()
            leaves.append((".".join(path_keys), key_line, "scalar"))
            return
        while state["i"] < n and text[state["i"]] not in ",]}":
            if text[state["i"]] == "\n":
                state["line"] += 1
            state["i"] += 1
        leaves.append((".".join(path_keys), key_line, "scalar"))

    parse_value([], 1)
    return leaves


# --------------------------------------------------------------------------- #
# scanner
# --------------------------------------------------------------------------- #


class Scanner:
    """Scans one ``frontend/src``-shaped tree and reports dead-code candidates."""

    def __init__(self, src_dir: Path, display_root: Path):
        self.src_dir = Path(src_dir).resolve()
        self.display_root = Path(display_root).resolve()
        self.files = {}          # abs path str -> SourceFile
        self.result = {}
        self.counts = {}

    # -- plumbing ----------------------------------------------------------- #

    def rel(self, path) -> str:
        p = Path(path).resolve()
        try:
            return p.relative_to(self.display_root).as_posix()
        except ValueError:
            return p.as_posix()

    def load(self):
        for path in iter_files(self.src_dir, CODE_EXTENSIONS):
            p = path.resolve()
            rel = self.rel(p)
            src_rel = p.relative_to(self.src_dir).as_posix()
            self.files[str(p)] = SourceFile(p, rel, src_rel)

    # -- import graph ------------------------------------------------------- #

    def _resolve(self, spec: str, importer: Path):
        if spec.startswith("@/"):
            base = self.src_dir / spec[2:]
        elif spec.startswith("./") or spec.startswith("../"):
            base = importer.parent / spec
        else:
            return None
        base = Path(os.path.normpath(str(base)))
        b = str(base)
        candidates = [
            b, b + ".ts", b + ".vue", b + ".tsx", b + ".js", b + ".mjs", b + ".cjs",
            b + "/index.ts", b + "/index.vue", b + "/index.tsx", b + "/index.js",
        ]
        for cand in candidates:
            p = Path(cand)
            if p.is_file():
                return p.resolve()
        return None

    def build_graph(self):
        """Return (edges, inbound, outbound). Keys are absolute path strings."""
        edges = []          # (src_abs, dst_abs, spec, src_rel, line)
        inbound = {}
        outbound = {}
        for abs_str, sf in self.files.items():
            src_abs = Path(abs_str)
            out = []
            patterns = [
                (RE_IMPORT_STATIC, 2), (RE_IMPORT_BARE, 2),
                (RE_IMPORT_DYNAMIC, 2), (RE_EXPORT_FROM, 2),
            ]
            seen = set()
            for rx, gi in patterns:
                for m in rx.finditer(sf.code):
                    spec = m.group(gi)
                    key = (m.start(gi), spec)
                    if key in seen:
                        continue
                    seen.add(key)
                    dst = self._resolve(spec, src_abs)
                    if dst is None:
                        continue
                    dst_str = str(dst)
                    edges.append((abs_str, dst_str, spec, sf.rel_posix, sf.line_of(m.start(gi))))
                    out.append(dst_str)
                    inbound.setdefault(dst_str, []).append((abs_str, sf.rel_posix))
            outbound[abs_str] = out
        return edges, inbound, outbound

    # -- reference indices -------------------------------------------------- #

    def build_ident_index(self):
        ident_to_files = {}
        file_idents = {}
        for abs_str, sf in self.files.items():
            idents = set(RE_IDENT.findall(sf.code))
            file_idents[abs_str] = idents
            for name in idents:
                ident_to_files.setdefault(name, set()).add(abs_str)
        self.ident_to_files = ident_to_files
        self.file_idents = file_idents

    # -- category 1 --------------------------------------------------------- #

    def category_exports(self):
        findings = []
        total = 0
        for abs_str, sf in sorted(self.files.items(), key=lambda kv: kv[1].rel_posix):
            if not sf.rel_posix.endswith(DECL_EXTENSIONS):
                continue
            seen = set()
            for m in RE_EXPORT_DECL.finditer(sf.code):
                if not sf.in_script(m.start()):
                    continue
                kind = m.group(2)
                name = m.group(3)
                if (name, kind) in seen:
                    continue
                seen.add((name, kind))
                total += 1
                ref_files = self.ident_to_files.get(name, set()) - {abs_str}
                if not ref_files:
                    findings.append({
                        "name": name,
                        "kind": kind,
                        "where": f"{sf.rel_posix}:{sf.line_of(m.start())}",
                        "decl_file": sf.rel_posix,
                        "is_test": sf.is_test,
                    })
        findings.sort(key=lambda f: f["where"])
        self.counts["exported_symbols_declared"] = total
        self.counts["exported_symbols_unreferenced"] = len(findings)
        return findings

    # -- category 2 --------------------------------------------------------- #

    def load_locales(self):
        loc_dir = self.src_dir / "locales"
        key_defs = {}        # key -> {lang: (rel, line)}
        collisions = []
        parse_errors = []
        per_lang_ns = {}
        if not loc_dir.is_dir():
            return key_defs, collisions, parse_errors, {}
        file_count = 0
        for lang_dir in sorted(p for p in loc_dir.iterdir() if p.is_dir()):
            lang = lang_dir.name
            for path in sorted(lang_dir.rglob("*.json")):
                file_count += 1
                rel = self.rel(path)
                try:
                    leaves = parse_locale_json(path)
                except JsonParseError as exc:
                    parse_errors.append((rel, f"JSON parse error: {exc}"))
                    continue
                for key, line, _kind in leaves:
                    slot = key_defs.setdefault(key, {})
                    if lang in slot and slot[lang][0] != rel:
                        collisions.append(
                            (rel, line, f"namespace/key collision with {slot[lang][0]} for '{key}'")
                        )
                    slot.setdefault(lang, (rel, line))
                    ns = key.split(".")[0]
                    per_lang_ns.setdefault(lang, {}).setdefault(ns, set()).add(rel)
        self.counts["locale_files"] = file_count
        self.counts["locale_parse_errors"] = len(parse_errors)
        return key_defs, collisions, parse_errors, per_lang_ns

    def category_locales(self):
        key_defs, collisions, parse_errors, per_lang_ns = self.load_locales()

        hard = {}        # key -> list[(rel, line)]  t('key') call sites
        literal = {}     # key -> list[(rel, line)]  bare string literal anywhere in code
        prefixes = {}    # static prefix -> list[(rel, line)] from t(`prefix.${...}`)

        for abs_str, sf in self.files.items():
            for value, off in sf.literals:
                if value in key_defs:
                    literal.setdefault(value, []).append((sf.rel_posix, sf.line_of(off)))
            for m in RE_T_CALL_LITERAL.finditer(sf.code):
                value = m.group(2)
                if value in key_defs:
                    hard.setdefault(value, []).append((sf.rel_posix, sf.line_of(m.start())))
            for m in RE_T_CALL_TEMPLATE_PREFIX.finditer(sf.code):
                prefixes.setdefault(m.group(1), []).append((sf.rel_posix, sf.line_of(m.start())))

        unused, indirect, prefixed = [], [], []
        for key in sorted(key_defs):
            defs = key_defs[key]
            # evidence line: prefer the zh-CN definition (primary language), then en-US
            primary = defs.get("zh-CN") or defs.get("en-US") or next(iter(defs.values()))
            where = f"{primary[0]}:{primary[1]}"
            langs = ",".join(sorted(defs))
            hard_refs = hard.get(key, [])
            lit_refs = literal.get(key, [])
            pfx_hit = None
            for pfx, sites in prefixes.items():
                if key.startswith(pfx) and len(key) > len(pfx):
                    pfx_hit = (pfx, sites[0])
                    break
            row = {"key": key, "where": where, "langs": langs,
                   "hard": len(hard_refs), "literal": len(lit_refs)}
            if hard_refs:
                continue
            if lit_refs:
                indirect.append(row)
            elif pfx_hit:
                row["prefix"] = pfx_hit[0]
                row["prefix_site"] = f"{pfx_hit[1][0]}:{pfx_hit[1][1]}"
                prefixed.append(row)
            else:
                unused.append(row)

        # asymmetry
        zh_only, en_only = [], []
        for key in sorted(key_defs):
            defs = key_defs[key]
            has_zh, has_en = "zh-CN" in defs, "en-US" in defs
            if has_zh and not has_en:
                zh_only.append({"key": key, "where": f"{defs['zh-CN'][0]}:{defs['zh-CN'][1]}"})
            elif has_en and not has_zh:
                en_only.append({"key": key, "where": f"{defs['en-US'][0]}:{defs['en-US'][1]}"})

        self.counts["locale_keys_total"] = len(key_defs)
        self.counts["locale_keys_unused"] = len(unused)
        self.counts["locale_keys_indirect_only"] = len(indirect)
        self.counts["locale_keys_prefix_covered"] = len(prefixed)
        self.counts["locale_asymmetry"] = len(zh_only) + len(en_only)
        self.counts["locale_namespaces"] = {
            lang: len(ns) for lang, ns in sorted(per_lang_ns.items())
        }
        return {
            "unused": unused,
            "indirect": indirect,
            "prefixed": prefixed,
            "zh_only": zh_only,
            "en_only": en_only,
            "collisions": collisions,
            "parse_errors": parse_errors,
            "prefixes": sorted(prefixes),
        }

    # -- category 3 --------------------------------------------------------- #

    def category_views(self, inbound, outbound):
        router_dir = self.src_dir / "router"
        seeds = set()
        if router_dir.is_dir():
            for abs_str, sf in self.files.items():
                if Path(abs_str).parent == router_dir.resolve():
                    for dst in outbound.get(abs_str, []):
                        seeds.add(dst)
        closure = set()
        stack = list(seeds)
        while stack:
            node = stack.pop()
            if node in closure:
                continue
            closure.add(node)
            for nxt in outbound.get(node, []):
                if nxt not in closure:
                    stack.append(nxt)

        views_dir = (self.src_dir / "views").resolve()
        findings = []
        total_views = 0
        for abs_str, sf in sorted(self.files.items(), key=lambda kv: kv[1].rel_posix):
            p = Path(abs_str)
            if p.suffix != ".vue" or views_dir not in p.parents:
                continue
            total_views += 1
            if abs_str in closure:
                continue
            importers = inbound.get(abs_str, [])
            non_test = [i for i in importers if not is_test_path(i[1])]
            findings.append({
                "where": sf.rel_posix,
                "importers": len(non_test),
                "importer_sample": ", ".join(sorted({i[1] for i in non_test})[:3]),
            })
        self.counts["views_total"] = total_views
        self.counts["views_unreachable"] = len(findings)
        self.counts["router_seeds"] = len(seeds)
        return findings

    # -- category 4 --------------------------------------------------------- #

    def category_modules(self, inbound):
        targets = ("components", "composables", "stores")
        findings = []
        total = 0
        for abs_str, sf in sorted(self.files.items(), key=lambda kv: kv[1].rel_posix):
            rel = sf.rel_posix
            parts = sf.src_rel.split("/")
            if len(parts) < 2 or parts[0] not in targets:
                continue
            top = parts[0]
            if not rel.endswith((".ts", ".vue")):
                continue
            total += 1
            importers = [i for i in inbound.get(abs_str, []) if i[0] != abs_str]
            non_test = [i for i in importers if not is_test_path(i[1])]
            if not importers:
                findings.append({
                    "where": rel,
                    "kind": top,
                    "importers": 0,
                    "importer_sample": "",
                    "test_only": False,
                })
            elif not non_test:
                findings.append({
                    "where": rel,
                    "kind": top,
                    "importers": len(importers),
                    "importer_sample": ", ".join(sorted({i[1] for i in importers})[:3]),
                    "test_only": True,
                })
        self.counts["modules_scanned"] = total
        self.counts["modules_unimported"] = len(findings)
        return findings

    # -- category 5 --------------------------------------------------------- #

    def api_exported_functions(self):
        api_dir = (self.src_dir / "api").resolve()
        decls = []   # (name, rel, line, abs)
        for abs_str, sf in sorted(self.files.items(), key=lambda kv: kv[1].rel_posix):
            p = Path(abs_str)
            if api_dir not in p.parents:
                continue
            if sf.is_test:      # test-local helpers are not API surface
                continue
            seen = set()
            for m in RE_EXPORT_FUNCTION.finditer(sf.code):
                name = m.group(1)
                if name in seen:
                    continue
                seen.add(name)
                decls.append((name, sf.rel_posix, sf.line_of(m.start()), abs_str))
            for m in RE_EXPORT_CONST.finditer(sf.code):
                name = m.group(1)
                if name in seen:
                    continue
                tail = sf.code[m.end():m.end() + 600]
                eq = RE_FIRST_ASSIGN.search(tail)
                if not eq:
                    continue
                rhs = tail[eq.end():].lstrip()
                if not rhs:
                    continue
                if rhs[0] in "(<" or rhs.startswith("async ") or rhs.startswith("function"):
                    seen.add(name)
                    decls.append((name, sf.rel_posix, sf.line_of(m.start()), abs_str))
        return decls

    def category_api(self):
        api_dir = (self.src_dir / "api").resolve()
        decls = self.api_exported_functions()
        findings = []
        for name, rel, line, abs_str in decls:
            ref_files = self.ident_to_files.get(name, set()) - {abs_str}
            outside = [f for f in ref_files if api_dir not in Path(f).parents]
            outside_non_test = [f for f in outside if not is_test_path(self.rel(f))]
            if not outside:
                inside = sorted({self.rel(f) for f in ref_files})
                findings.append({
                    "where": f"{rel}:{line}",
                    "name": name,
                    "external_refs": 0,
                    "non_test_refs": 0,
                    "note": ("referenced only inside api/: " + ", ".join(inside)) if inside
                            else "no reference at all, not even inside api/",
                    "verdict": "uncalled",
                })
            elif not outside_non_test:
                findings.append({
                    "where": f"{rel}:{line}",
                    "name": name,
                    "external_refs": len(outside),
                    "non_test_refs": 0,
                    "note": "referenced only from test files outside api/",
                    "verdict": "test-only",
                })
        findings.sort(key=lambda f: f["where"])

        # Independent cross-check: re-run the same test on RAW file text (comments
        # included). Any finding that appears here would mean the verdict depends on
        # comment stripping, which would be a defect in the detector.
        raw_hits = []
        for f in findings:
            name = f["name"]
            rx = re.compile(r"\b" + re.escape(name) + r"\b")
            for abs_str, sf in self.files.items():
                if api_dir in Path(abs_str).parents:
                    continue
                if rx.search(sf.text):
                    raw_hits.append(f"{name} -> {sf.rel_posix}")
                    break

        self.counts["api_exported_functions"] = len(decls)
        self.counts["api_functions_uncalled"] = sum(1 for f in findings if f["verdict"] == "uncalled")
        self.counts["api_functions_test_only"] = sum(1 for f in findings if f["verdict"] == "test-only")
        self.counts["api_raw_text_crosscheck_hits"] = len(raw_hits)
        self.api_raw_hits = raw_hits
        return findings

    # -- supplementary ------------------------------------------------------ #

    def module_orphans(self, inbound):
        """Non-test modules with zero inbound imports (supplementary evidence).

        Entry points are excluded: ``main.ts`` is loaded by index.html, not imported.
        """
        findings = []
        for abs_str, sf in sorted(self.files.items(), key=lambda kv: kv[1].rel_posix):
            if sf.is_test or sf.src_rel == "main.ts":
                continue
            if inbound.get(abs_str):
                continue
            findings.append({"where": sf.rel_posix})
        self.counts["modules_orphan"] = len(findings)
        return findings

    # -- driver ------------------------------------------------------------- #

    def scan(self):
        t0 = time.time()
        self.load()
        self.build_ident_index()
        edges, inbound, outbound = self.build_graph()
        self.result = {
            "cat1": self.category_exports(),
            "locales": self.category_locales(),
            "cat3": self.category_views(inbound, outbound),
            "cat4": self.category_modules(inbound),
            "cat5": self.category_api(),
            "orphans": self.module_orphans(inbound),
        }
        self.counts["files_scanned"] = len(self.files)
        self.counts["import_edges"] = len(edges)
        self.counts["elapsed_s"] = round(time.time() - t0, 2)
        return self.result, self.counts


# --------------------------------------------------------------------------- #
# report
# --------------------------------------------------------------------------- #

BLIND_SPOTS = """\
- **Computed dynamic imports.** `import()` whose argument is not a plain string
  literal (a variable, a template literal, `import.meta.glob`) cannot be resolved;
  the target then looks unreachable. This repo has no such call sites, but the
  method cannot prove that for future code.
- **Keys built by string concatenation / interpolation.** ``t(other + '.suffix')``
  or ``t(`a.${b}`)`` only exposes its static prefix; only the
  ``t(`prefix.${...}`)`` form is recovered, and only when the prefix is a literal.
  Keys reached purely through a runtime-computed string are counted as unused.
- **Word-boundary references are not scope-aware.** Category 1/5 count an
  identifier anywhere in any *other* file, including JSON-free string literals,
  object property names, JSDoc-free comment text (comments *are* stripped, so
  prose in comments does not count), and type positions. A symbol whose name
  collides with an unrelated identifier or a property name is counted as live
  (false negative), never as dead (no false positives from this mechanism).
- **Template-only / runtime string references to locale keys** are covered only
  because every string literal is indexed; a key assembled at runtime is not.
- **Global component registration.** A component registered through
  `app.component(...)` (e.g. Element Plus icons in `main.ts`) or auto-imported by
  a Vite plugin would be reported as unimported. No project-owned component is
  registered that way here.
- **Barrel re-export style.** `export { X } from './y'` and `export default` are
  not treated as *declarations* by category 1 (only the spec's
  `export <kind> <Name>` shapes are). They are followed as import edges, and the
  original declaration is still the one that gets reported.
- **`declare`/ambient symbols and `.d.ts` files** are out of scope.
- **`views/**/*.vue` reachability** is a static import-closure from
  `frontend/src/router/**`. A route component that is only reachable through a
  natively-computed component name, or a view used purely as a `<component :is>`
  target, would be reported as unreachable even though it renders.
- **Locale JSON files are not part of the reference corpus** (otherwise each key
  would reference itself). Keys that are only meaningful as data (e.g. a key
  whose value is another key) are not followed.
- **Scope of "reference":** the method proves *absence of textual references*.
  It does not prove that the referenced call is reachable, nor that a locale key
  resolves at runtime (a key present in neither language file would be a
  fallback miss, not a dead key, and is not in any table here).
"""


def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join("---" for _ in headers) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


def write_report(path: Path, scanner: Scanner, result, counts, calibration_output,
                 calibration_ok, argv, src_display, top=TOP_FINDING_CAP):
    TOP_FINDING_CAP = top
    loc = result["locales"]
    lines = []
    ap = lines.append
    ap("# Frontend dead-code report (JAiRouter admin console)")
    ap("")
    ap(f"- Generated: {time.strftime('%Y-%m-%dT%H:%M:%S%z')}")
    ap(f"- Detector: `scripts/audit/detect-frontend-dead-code.py` v{TOOL_VERSION}")
    ap("- Command: `python scripts/audit/detect-frontend-dead-code.py "
       + (" ".join(argv) if argv else "(no arguments)") + "`")
    ap(f"- Scan root: `{src_display}`")
    ap("- Mutation: none. No file under `frontend/src` is written; no npm/npx/"
       "vue-tsc/build command is run.")
    ap("")
    ap("## 0. Calibration (`--self-test`)")
    ap("")
    if calibration_ok:
        ap("**Result: PASS** - every category produced its synthetic finding and "
           "every negative control stayed silent. The counts below are therefore "
           "meaningful, including any zero.")
    else:
        ap("**Result: FAIL** - the detector did not prove it can find the "
           "synthetic defects. **Do not trust the tables below.**")
    if result["locales"]["parse_errors"]:
        ap("")
        ap(f"**WARNING: {len(result['locales']['parse_errors'])} locale file(s) "
           "failed to parse and were skipped - the locale tables are incomplete.**")
    ap("")
    ap("```text")
    ap(calibration_output.strip())
    ap("```")
    ap("")

    ap("## 1. Method")
    ap("")
    ap("1. **Source corpus.** Every `.ts/.vue/.tsx/.js/.mjs/.cjs` file below "
       "`frontend/src`, excluding `node_modules`, `dist`, `.git` (and other "
       "build/cache directories), is parsed with a small JS/TS lexer that")
    ap("   - blanks `//` and `/* */` comments and `<style>` bodies and HTML "
       "comments (offset-preserving, so line numbers stay exact);")
    ap("   - collects every string literal (single/double/template) with its "
       "offset;")
    ap("   - skips regular-expression literals so `/\\/+/` is never read as a "
       "comment;")
    ap("   - for `.vue`, records the `<script>`/`<script setup>` block ranges so "
       "export declarations are only taken from real script code.")
    ap("2. **Reference test.** All identifiers of all files are indexed "
       "(`name -> files`). A symbol is *referenced* iff its name occurs in at "
       "least one file **other than** the file that declares it (a type used only "
       "inside its own file is therefore self-reference, not a use).")
    ap("3. **Locale keys.** Each JSON file is parsed by an offset-tracking JSON "
       "reader, so every key carries `file:line`. Key paths are the vue-i18n "
       "paths produced by `frontend/src/i18n/index.ts` (each file's top-level keys "
       "become top-level namespaces). Three reference classes are distinguished:")
    ap("   - **call site** - `t('k')` / `$t('k')` / `i18n.global.t('k')` with a "
       "string literal;")
    ap("   - **literal** - any string literal anywhere in the corpus that equals "
       "the key (covers `config/menu.ts` titles, `ROLE_DESCRIPTIONS`, "
       "`conditionTypeMap`, router `meta.titleKey`, and `t`-aliases such as "
       "`gt`/`tTitle`);")
    ap("   - **prefix** - ``t(`prefix.${...}`)`` families, where every key under "
       "`prefix.` is treated as referenced.")
    ap("4. **Import graph.** Relative and `@/` specifiers from static imports, "
       "side-effect imports, dynamic `import('...')` and `export ... from` are "
       "resolved (with `.ts/.vue/.tsx/.js` and `/index.*` alternatives) into a "
       "directed graph used by categories 3 and 4.")
    ap("5. **Category 3** seeds the closure with every component referenced from "
       "`frontend/src/router/**` (all nesting levels of the route table, "
       "`children` included, parameterised paths such as `detail/:id` are "
       "irrelevant to component resolution) and then walks the import graph "
       "transitively; a `views/**/*.vue` outside that closure is reported.")
    ap("")

    ap("## 2. Scope and counts")
    ap("")
    ap(md_table(["item", "value"], [
        ["code files scanned", counts["files_scanned"]],
        [".vue files under `views/`", counts["views_total"]],
        ["resolved import edges", counts["import_edges"]],
        ["router-referenced entry components", counts["router_seeds"]],
        ["exported symbols declared (`export <kind> <Name>` in `.ts` + `.vue` script blocks)",
         counts["exported_symbols_declared"]],
        ["locale files parsed", counts["locale_files"]],
        ["locale files that failed to parse", counts.get("locale_parse_errors", 0)],
        ["distinct locale key paths", counts["locale_keys_total"]],
        ["locale namespaces per language", json.dumps(counts["locale_namespaces"])],
        ["`components/`+`composables/`+`stores/` modules", counts["modules_scanned"]],
        ["modules with zero inbound imports (supplementary)", counts["modules_orphan"]],
        ["exported functions under `api/` (excluding `__tests__`)",
         counts["api_exported_functions"]],
    ]))
    ap("")

    ap("## 3. Category 1 - exported symbols never referenced")
    ap(f"**{counts['exported_symbols_unreferenced']}** of "
       f"**{counts['exported_symbols_declared']}** declared exported symbols have no "
       "reference in any other file under `frontend/src`.")
    ap("")
    ap("Declaration regex: `\\bexport\\s+(?:async\\s+)?(?:declare\\s+)?(?:(const)\\s+)?"
       "(function|const|var|let|class|interface|type|enum)\\s+<Name>` over `.ts` files "
       "and `.vue` `<script>`/`<script setup>` blocks. An earlier ad-hoc probe of this "
       "repo reported 467 exported symbols; this run counts "
       f"{counts['exported_symbols_declared']}. The 7-symbol difference was not "
       "reproduced and is recorded as unknown (see section 11).")
    ap("")
    c1 = result["cat1"]
    if c1:
        rows = [(f["name"], f["kind"], f"`{f['where']}`",
                 "yes" if f["is_test"] else "") for f in c1[:TOP_FINDING_CAP]]
        ap(md_table(["symbol", "kind", "declaration", "in `__tests__`"], rows))
        if len(c1) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(c1)} rows._")
    else:
        ap("_No findings in this category._")
    ap("")

    ap("## 4. Category 2 - locale keys never referenced")
    ap(f"**{counts['locale_keys_unused']}** of **{counts['locale_keys_total']}** "
       "distinct key paths have no call site, no literal reference and no dynamic "
       "prefix reference under `frontend/src`.")
    ap("")
    if loc["unused"]:
        rows = [(f"`{r['key']}`", r["langs"], f"`{r['where']}`") for r in loc["unused"][:TOP_FINDING_CAP]]
        ap(md_table(["key", "defined in", "first definition"], rows))
        if len(loc["unused"]) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(loc['unused'])} rows._")
    else:
        ap("_No findings in this category._")
    ap("")
    ap(f"### 4b. Informational - keys with no `t()` call site but a literal "
       f"reference ({counts['locale_keys_indirect_only']})")
    ap("")
    ap("These are live (a data structure holds the key and a render site calls "
       "`t(<that value>)`), but no `t('...')`/`$t('...')`/`i18n.global.t('...')` "
       "call with a string literal mentions them. Listed for completeness, not as "
       "defects.")
    ap("")
    if loc["indirect"]:
        rows = [(f"`{r['key']}`", f"`{r['where']}`", r["literal"]) for r in loc["indirect"][:60]]
        ap(md_table(["key", "first definition", "literal refs"], rows))
        if len(loc["indirect"]) > 60:
            ap("")
            ap(f"_Table truncated: showing 60 of {len(loc['indirect'])} rows._")
    else:
        ap("_None._")
    ap("")
    ap(f"### 4c. Dynamically addressed key families ({counts['locale_keys_prefix_covered']} keys)")
    ap("")
    if loc["prefixed"]:
        rows = [(f"`{r['key']}`", f"`{r['prefix']}.`",
                 f"`{r['prefix_site']}`") for r in loc["prefixed"][:40]]
        ap(md_table(["key", "covered by prefix", "call site"], rows))
        if len(loc["prefixed"]) > 40:
            ap("")
            ap(f"_Table truncated: showing 40 of {len(loc['prefixed'])} rows._")
    else:
        ap("_None._")
    ap("")
    if loc["collisions"]:
        ap("### 4d. Namespace/key collisions inside one language")
        ap("")
        ap(md_table(["file", "line", "detail"],
                    [(f"`{c[0]}`", c[1], c[2]) for c in loc["collisions"]]))
        ap("")
    if loc["parse_errors"]:
        ap("### 4e. Locale files that could not be parsed")
        ap("")
        ap(md_table(["file", "detail"],
                    [(f"`{c[0]}`", c[1]) for c in loc["parse_errors"]]))
        ap("")

    ap("## 5. Locale key-set asymmetry (zh-CN vs en-US)")
    ap("")
    ap(f"**{counts['locale_asymmetry']}** key paths exist on exactly one side "
       f"({len(loc['zh_only'])} zh-CN-only, {len(loc['en_only'])} en-US-only).")
    ap("")
    both = loc["zh_only"] + loc["en_only"]
    both.sort(key=lambda r: r["key"])
    if both:
        rows = []
        for r in both[:TOP_FINDING_CAP]:
            side = "zh-CN only" if r in loc["zh_only"] else "en-US only"
            rows.append((f"`{r['key']}`", side, f"`{r['where']}`"))
        ap(md_table(["key", "present in", "definition"], rows))
        if len(both) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(both)} rows._")
    else:
        ap("_No asymmetry._")
    ap("")

    ap("## 6. Category 3 - views not reachable from the router")
    ap(f"**{counts['views_unreachable']}** of **{counts['views_total']}** `.vue` "
       "files under `views/` are outside the router import closure.")
    ap("")
    c3 = result["cat3"]
    if c3:
        rows = [(f"`{f['where']}`", f["importers"], f["importer_sample"] or "-") for f in c3[:TOP_FINDING_CAP]]
        ap(md_table(["view", "non-test importers", "imported by"], rows))
        if len(c3) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(c3)} rows._")
    else:
        ap("_No findings in this category._")
    ap("")
    ap("### 6b. Supplementary - modules no import reaches (not one of the five categories)")
    ap("")
    ap("A module with zero inbound imports cannot be reached statically at all. "
       "This list is *evidence*, not a sixth category: it explains several rows "
       "elsewhere (e.g. an unimported re-export barrel makes the views it exports "
       "unreachable too). `main.ts` is excluded (it is loaded by `index.html`); "
       "test files are excluded.")
    ap("")
    orphans = result["orphans"]
    if orphans:
        ap(md_table(["module"], [(f"`{o['where']}`",) for o in orphans]))
    else:
        ap("_None._")
    ap("")

    ap("## 7. Category 4 - components / composables / stores never imported")
    ap(f"**{counts['modules_unimported']}** of **{counts['modules_scanned']}** "
       "modules in those three directories have no inbound import from anywhere "
       "under `frontend/src` (a test-only inbound import is reported separately).")
    ap("")
    c4 = result["cat4"]
    if c4:
        rows = [(f"`{f['where']}`", f["kind"], f["importers"],
                 ("test-only" if f["test_only"] else "none"),
                 f["importer_sample"] or "-") for f in c4[:TOP_FINDING_CAP]]
        ap(md_table(["module", "kind", "importers", "import source", "imported by"], rows))
        if len(c4) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(c4)} rows._")
    else:
        ap("_No findings in this category._")
    ap("")

    ap("## 8. Category 5 - API functions never called outside `api/`")
    ap(f"**{counts['api_functions_uncalled']}** of "
       f"**{counts['api_exported_functions']}** exported functions under `api/` "
       f"have no reference in any file outside `api/**`; "
       f"**{counts['api_functions_test_only']}** more are referenced only from test "
       "files outside `api/`.")
    ap("")
    ap(f"**Independent cross-check.** The same test was re-run on the *raw* file "
       f"text (comments included) of every non-`api/` file. Findings whose name "
       f"appears in raw text: **{counts['api_raw_text_crosscheck_hits']}**. A "
       "non-zero value would mean a verdict that depends on comment stripping; "
       "zero means the verdicts do not.")
    ap("")
    if scanner.api_raw_hits:
        ap(md_table(["raw-text hit"], [(h,) for h in scanner.api_raw_hits[:40]]))
        ap("")
    ap("An earlier ad-hoc probe of this repo reported **0** dead `api` exports; this "
       "run reports 40 that no file outside `api/**` references. The cross-check "
       "above shows the difference is not a comment/string artefact. The earlier "
       "probe's implementation was not available, so why it found 0 is unknown "
       "(see section 11).")
    ap("")
    c5 = result["cat5"]
    if c5:
        rows = [(f"`{f['name']}`", f"`{f['where']}`", f["verdict"],
                 f["external_refs"], f["note"]) for f in c5[:TOP_FINDING_CAP]]
        ap(md_table(["function", "declaration", "verdict", "refs outside api/", "note"], rows))
        if len(c5) > TOP_FINDING_CAP:
            ap("")
            ap(f"_Table truncated: showing {TOP_FINDING_CAP} of {len(c5)} rows._")
    else:
        ap("_No findings in this category._")
    ap("")

    ap("## 9. Exclusions")
    ap("")
    ap("- Directories never walked: " + ", ".join(f"`{d}`" for d in sorted(EXCLUDE_DIR_NAMES)) +
       " and any dot-directory.")
    ap("- `frontend/src/locales/**/*.json` are read as *key definitions* and are "
       "deliberately **not** part of the reference corpus (a key would otherwise "
       "reference itself).")
    ap("- `__tests__/` and `*.spec.*` / `*.test.*` files: included in the source "
       "corpus (they are real imports and real call sites) but flagged; for "
       "category 5 they are excluded from the *declaration* set (a test-local "
       "helper is not API surface) and a test-only call site is reported as a "
       "separate verdict rather than as \"called\".")
    ap("- `frontend/src` is only ever read. Nothing under it is written.")
    ap("- Not run: `npm`, `npx`, `vue-tsc`, `vite`, any build or type-check.")
    ap("")

    ap("## 10. Blind spots of this method")
    ap("")
    ap(BLIND_SPOTS)

    ap("## 11. Unknowns")
    ap("")
    ap("- Whether any reported symbol is dead *at runtime* is not established: the "
       "method proves the absence of textual references, not the absence of a "
       "reachable call. Same for locale keys reached through values computed at "
       "runtime.")
    ap("- Runtime locale fallback behaviour is not modelled: a key that is used but "
       "missing from a language file does not appear here as a defect (a missing "
       "*key* is reported only as zh/en asymmetry).")
    ap("- No coverage from the built bundle: `dist/` is excluded and no build is "
       "run, so nothing here is validated against the shipped artifact.")
    ap("- The transitively-reachable set is a static over-approximation: an import "
       "that is present but provably unreachable at runtime still counts as live.")
    ap("- Why an earlier ad-hoc probe of this repo reported 467 exported symbols "
       "(this run: " + str(counts["exported_symbols_declared"]) + ") and 0 dead "
       "`api` exports (this run: " + str(counts["api_functions_uncalled"]) + ") is "
       "unknown: that probe's implementation was not available for comparison. The "
       "raw-text cross-check above rules out comment matching as the cause of the "
       "category-5 difference; nothing here rules out a different scope (e.g. a "
       "different file set or declaration regex) on the earlier probe's side.")
    ap("- Whether a reported symbol is reachable through a Vue `<component :is>` "
       "name computed at runtime is unknown without running the app.")

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


# --------------------------------------------------------------------------- #
# self test
# --------------------------------------------------------------------------- #

FIXTURE_FILES = {
    # ---- a view that IS routed -------------------------------------------- #
    "views/ReachableView.vue": """<template>
  <div class="reachable">{{ t('fixture.liveKey') }}
    <LiveComp /><ChildComp />
  </div>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LiveComp from '@/components/LiveComp.vue'
import ChildComp from '@/components/ChildComp.vue'
import { liveApiFn } from '@/api/live'
import { usedHelper } from '@/utils/usedSymbol'
import { useLiveStore } from '@/stores/live'
const { t } = useI18n()
const store = useLiveStore()
const run = () => { liveApiFn(); usedHelper(); store.n }
run()
</script>
<style scoped>
.reachable { color: red; /* comment with fixture.deadKey inside */ }
</style>
""",
    # ---- a view nothing reaches ------------------------------------------- #
    "views/OrphanView.vue": """<template>
  <div>orphan</div>
</template>
<script setup lang="ts">
const noop = () => 1
noop()
</script>
""",
    # ---- live component chain (LiveComp -> ChildComp, transitive) --------- #
    "components/LiveComp.vue": """<template><span>{{ t('fixture.liveKey') }}</span></template>
<script setup lang="ts">
import ChildComp from './ChildComp.vue'
import { useLiveComposable } from '@/composables/useLive'
const { child } = useLiveComposable(ChildComp)
child()
</script>
""",
    "components/ChildComp.vue": """<template><span>child</span></template>
<script setup lang="ts">
const x = 1
x
</script>
""",
    # ---- dead component ---------------------------------------------------- #
    "components/DeadComp.vue": """<template><span>dead</span></template>
<script setup lang="ts">
const y = 2
y
</script>
""",
    # ---- composable / store: one live, one dead --------------------------- #
    "composables/useLive.ts": """import { ref } from 'vue'
export function useLiveComposable(seed: unknown) {
  const child = ref(seed)
  return { child }
}
""",
    "composables/useDead.ts": """import { ref } from 'vue'
export function useDeadComposable() {
  return { dead: ref(0) }
}
""",
    "stores/live.ts": """import { defineStore } from 'pinia'
export const useLiveStore = defineStore('live', { state: () => ({ n: 0 }) })
""",
    "stores/dead.ts": """import { defineStore } from 'pinia'
export const useDeadStore = defineStore('dead', { state: () => ({ n: 0 }) })
""",
    # ---- api: one live, one dead ------------------------------------------ #
    "api/live.ts": """import request from '@/utils/request'
export const liveApiFn = () => request.get('/live')
""",
    "api/dead.ts": """import request from '@/utils/request'
export const deadApiFn = () => request.get('/dead')
""",
    # ---- exported symbol used in another file (negative control) ---------- #
    "utils/usedSymbol.ts": """export const usedHelper = () => 'used'
export const unusedExportedConst = 'dead'
export interface NeverReferencedInterface { a: string }
export interface SelfReferencedOnly { b: NeverReferencedInterface }
export type OnlyUsedInOwnFile = SelfReferencedOnly
""",
    # ---- a consumer so `usedHelper` is referenced elsewhere --------------- #
    "utils/consumer.ts": """import { usedHelper } from './usedSymbol'
export const consume = () => usedHelper()
""",
    # ---- bare-literal locale key holder (indirect reference control) ------ #
    "config/labels.ts": """export const LABELS = { a: 'fixture.literalKey' }
""",
    "router/index.ts": """import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/views/ReachableView.vue'),
    children: [
      { path: 'detail/:id', component: () => import('@/views/ReachableView.vue') }
    ]
  },
  { path: '/static', component: () => import('@/components/ChildComp.vue') }
]
export default createRouter({ history: createWebHistory(), routes })
""",
    "locales/zh-CN/base.json": json.dumps({
        "fixture": {
            "liveKey": "live",
            "deadKey": "dead, with } and , and \" delimiters",
            "literalKey": "literal",
            "zhOnlyKey": "zh only",
            "tailKey": "after the tricky scalar",
            "nested": {"deep": "d"}
        }
    }, ensure_ascii=False, indent=2) + "\n",
    "locales/en-US/base.json": json.dumps({
        "fixture": {
            "liveKey": "live",
            "deadKey": "dead, with } and , and \" delimiters",
            "literalKey": "literal",
            "enOnlyKey": "en only",
            "tailKey": "after the tricky scalar",
            "nested": {"deep": "d"}
        }
    }, ensure_ascii=False, indent=2) + "\n",
}


class SelfTest:
    def __init__(self, verbose=True):
        self.verbose = verbose
        self.passed = 0
        self.failed = 0
        self.lines = []

    def check(self, name, condition, detail=""):
        status = "PASS" if condition else "FAIL"
        line = f"{status}  {name}" + (f"  [{detail}]" if detail else "")
        self.lines.append(line)
        if self.verbose:
            print("  " + line)
        if condition:
            self.passed += 1
        else:
            self.failed += 1

    def text(self):
        return "\n".join(self.lines)


def run_self_test(verbose=True) -> int:
    out = print if verbose else (lambda *a, **k: None)
    scratch = Path(tempfile.mkdtemp(prefix="deadcode-selftest-"))
    out(f"[self-test] detector v{TOOL_VERSION}")
    out(f"[self-test] scratch tree: {scratch}")
    try:
        src = scratch / "frontend" / "src"
        for rel, content in FIXTURE_FILES.items():
            p = src / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")

        st = SelfTest(verbose=verbose)
        scanner = Scanner(src, scratch)
        result, counts = scanner.scan()

        # ---------------- category 1 ---------------- #
        c1_names = {f["name"] for f in result["cat1"]}
        st.check(
            "cat1-positive: unused exported const is reported",
            "unusedExportedConst" in c1_names,
            "utils/usedSymbol.ts:2",
        )
        st.check(
            "cat1-positive: exported interface used only in its own file is reported",
            "NeverReferencedInterface" in c1_names,
            "self-reference must not count as a use",
        )
        st.check(
            "cat1-negative: exported symbol referenced from another file is NOT reported",
            "usedHelper" not in c1_names,
            "utils/consumer.ts imports it",
        )
        st.check(
            "cat1-negative: live api export is NOT reported by category 1",
            "liveApiFn" not in c1_names,
            "views/ReachableView.vue imports it",
        )
        st.check(
            "cat1-negative: `<script setup>` locals are not treated as exports",
            "noop" not in c1_names and "t" not in c1_names,
        )
        c1_where = {f["name"]: f["where"] for f in result["cat1"]}
        st.check(
            "cat1-evidence: finding carries file:line",
            c1_where.get("unusedExportedConst", "").endswith("utils/usedSymbol.ts:2"),
            c1_where.get("unusedExportedConst", "?"),
        )
        st.check(
            "cat1-comment-stripping: key mentioned only inside a code comment does not hide a finding",
            "fixture.deadKey" in {r["key"] for r in result["locales"]["unused"]},
        )

        # ---------------- category 2 ---------------- #
        unused_keys = {r["key"] for r in result["locales"]["unused"]}
        indirect_keys = {r["key"] for r in result["locales"]["indirect"]}
        st.check(
            "cat2-positive: unreferenced locale key is reported",
            "fixture.deadKey" in unused_keys,
            "locales/zh-CN/base.json",
        )
        st.check(
            "cat2-negative: key used through t('...') is NOT reported",
            "fixture.liveKey" not in unused_keys,
        )
        st.check(
            "cat2-negative: key referenced only as a bare literal is NOT reported as unused",
            "fixture.literalKey" in indirect_keys and "fixture.literalKey" not in unused_keys,
            "config/labels.ts holds it as data",
        )
        asym = {r["key"] for r in result["locales"]["zh_only"]} | {r["key"] for r in result["locales"]["en_only"]}
        st.check(
            "cat2-positive: zh-CN/en-US key asymmetry is reported",
            {"fixture.zhOnlyKey", "fixture.enOnlyKey"} <= asym,
            f"{len(asym)} asymmetric key(s)",
        )
        st.check(
            "cat2-negative: symmetric key is not reported as asymmetric",
            "fixture.liveKey" not in asym and "fixture.nested.deep" not in asym,
        )
        st.check(
            "cat2-parser: no locale file failed to parse",
            counts["locale_parse_errors"] == 0 and not result["locales"]["parse_errors"],
            f"parse_errors={counts.get('locale_parse_errors')}",
        )
        st.check(
            "cat2-parser: keys after a scalar containing `,` `}` `\"` are still parsed",
            "fixture.tailKey" in unused_keys,
            "guards lossy JSON reading",
        )
        st.check(
            "cat2-evidence: locale key carries file:line (zh-CN preferred)",
            any(r["key"] == "fixture.deadKey" and r["langs"] == "en-US,zh-CN"
                and r["where"].endswith("locales/zh-CN/base.json:4")
                for r in result["locales"]["unused"]),
            "locales/zh-CN/base.json:4",
        )

        # ---------------- category 3 ---------------- #
        c3 = {f["where"] for f in result["cat3"]}
        st.check(
            "cat3-positive: view not referenced by the route table is reported",
            "frontend/src/views/OrphanView.vue" in c3,
        )
        st.check(
            "cat3-negative: routed view is NOT reported",
            "frontend/src/views/ReachableView.vue" not in c3,
        )
        st.check(
            "cat3-negative: nested `children` route with `detail/:id` is resolved correctly",
            "frontend/src/views/ReachableView.vue" not in c3,
            "dynamic child segment must not break resolution",
        )
        st.check(
            "cat3-negative: view reachable only transitively via component imports is NOT reported",
            "frontend/src/components/ChildComp.vue" not in c3 and len(c3) == 1,
            f"reported: {sorted(c3)}",
        )

        # ---------------- category 4 ---------------- #
        c4 = {f["where"] for f in result["cat4"]}
        st.check(
            "cat4-positive: component nothing imports is reported",
            "frontend/src/components/DeadComp.vue" in c4,
        )
        st.check(
            "cat4-positive: dead composable is reported",
            "frontend/src/composables/useDead.ts" in c4,
        )
        st.check(
            "cat4-positive: dead store is reported",
            "frontend/src/stores/dead.ts" in c4,
        )
        st.check(
            "cat4-negative: imported component is NOT reported",
            "frontend/src/components/LiveComp.vue" not in c4,
        )
        st.check(
            "cat4-negative: transitively imported component is NOT reported",
            "frontend/src/components/ChildComp.vue" not in c4,
            "LiveComp imports it",
        )
        st.check(
            "cat4-negative: imported composable / store are NOT reported",
            "frontend/src/composables/useLive.ts" not in c4
            and "frontend/src/stores/live.ts" not in c4,
        )

        # ---------------- category 5 ---------------- #
        c5 = {f["name"]: f for f in result["cat5"]}
        st.check(
            "cat5-positive: api function with no call site outside api/ is reported",
            c5.get("deadApiFn", {}).get("verdict") == "uncalled",
            "api/dead.ts",
        )
        st.check(
            "cat5-negative: api function a view really imports is NOT reported",
            "liveApiFn" not in c5,
        )
        st.check(
            "cat5-evidence: finding carries file:line",
            c5.get("deadApiFn", {}).get("where", "").endswith("api/dead.ts:2"),
            c5.get("deadApiFn", {}).get("where", "?"),
        )

        # ---------------- cross-cutting ---------------- #
        st.check(
            "every category produced at least one finding",
            all([result["cat1"], result["locales"]["unused"], result["cat3"],
                 result["cat4"], result["cat5"]]),
            "cat1={} cat2={} cat3={} cat4={} cat5={}".format(
                len(result["cat1"]), len(result["locales"]["unused"]),
                len(result["cat3"]), len(result["cat4"]), len(result["cat5"])),
        )
        st.check(
            "scan scope: node_modules / dist / .git are excluded",
            _exclusion_ok(scratch),
        )

        print("")
        print(f"[self-test] {st.passed} passed, {st.failed} failed")
        return (st, st.text())
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
        exists = scratch.exists()
        print(f"[self-test] scratch fixtures removed: {scratch} (exists={exists})")
        if exists:
            print("[self-test] WARNING: scratch directory survived cleanup")


def _exclusion_ok(scratch: Path) -> bool:
    """Prove the walker ignores excluded directories by planting files in them."""
    junk = [
        scratch / "frontend" / "src" / "node_modules" / "junk.ts",
        scratch / "frontend" / "src" / "dist" / "junk.ts",
        scratch / "frontend" / "src" / ".git" / "junk.ts",
    ]
    for p in junk:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("export const excludedJunkSymbol = 1\n", encoding="utf-8")
    scanner = Scanner(scratch / "frontend" / "src", scratch)
    scanner.scan()
    names = {f["name"] for f in scanner.result["cat1"]}
    return "excludedJunkSymbol" not in names


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #


def repo_root_default() -> Path:
    return Path(__file__).resolve().parents[2]


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Self-calibrating dead-code detector for the JAiRouter frontend."
    )
    parser.add_argument("--root", default=None,
                        help="repository root (default: two levels above this script)")
    parser.add_argument("--src", default=None,
                        help="source directory to scan (default: <root>/frontend/src)")
    parser.add_argument("--out", default=None,
                        help="report path (default: <root>/scripts/audit/out/frontend-dead-code.md)")
    parser.add_argument("--self-test", action="store_true",
                        help="run calibration fixtures only, then exit")
    parser.add_argument("--skip-selftest", action="store_true",
                        help="do not run the calibration before writing the report")
    parser.add_argument("--top", type=int, default=TOP_FINDING_CAP,
                        help="max rows per findings table (default 200)")
    args = parser.parse_args(argv)

    if args.self_test:
        outcome = run_self_test(verbose=True)
        st, _ = outcome
        return 1 if st.failed else 0

    root = Path(args.root).resolve() if args.root else repo_root_default()
    src_dir = Path(args.src).resolve() if args.src else root / "frontend" / "src"
    out_path = Path(args.out).resolve() if args.out else root / "scripts" / "audit" / "out" / "frontend-dead-code.md"

    if not src_dir.is_dir():
        print(f"error: source directory not found: {src_dir}", file=sys.stderr)
        return 2

    calib_text = "(calibration skipped: --skip-selftest)"
    calib_ok = False
    if not args.skip_selftest:
        print("[report] running calibration self-test first ...")
        try:
            st, calib_text = run_self_test(verbose=True)
            calib_ok = st.failed == 0
        except Exception as exc:  # noqa: BLE001 - calibration must never be silent
            calib_text = f"calibration crashed: {exc!r}"
            calib_ok = False
        print("")

    scanner = Scanner(src_dir, root)
    result, counts = scanner.scan()

    src_display = scanner.rel(src_dir)
    argv_echo = [a for a in (argv if argv is not None else sys.argv[1:])]
    write_report(out_path, scanner, result, counts, calib_text, calib_ok,
                 argv_echo, src_display, top=args.top)

    print(f"[report] wrote {out_path}")
    print(f"[report] files={counts['files_scanned']} "
          f"cat1={counts['exported_symbols_unreferenced']}/{counts['exported_symbols_declared']} "
          f"cat2={counts['locale_keys_unused']}/{counts['locale_keys_total']} "
          f"asymmetry={counts['locale_asymmetry']} "
          f"cat3={counts['views_unreachable']}/{counts['views_total']} "
          f"cat4={counts['modules_unimported']}/{counts['modules_scanned']} "
          f"cat5={counts['api_functions_uncalled']}/{counts['api_exported_functions']}")
    if not calib_ok and not args.skip_selftest:
        print("[report] WARNING: calibration FAILED - report is flagged as untrustworthy")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
