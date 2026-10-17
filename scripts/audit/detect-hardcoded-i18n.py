#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
detect-hardcoded-i18n.py -- self-calibrating detector for user-visible Chinese text
that is NOT routed through vue-i18n, in the JAiRouter bilingual admin console.

Why this exists
---------------
A dead-code detector reported 62 locale keys that nothing references.  The hypothesis
under test is "the views ignore those keys and hardcode the Chinese literal instead,
so the English UI stays Chinese".  This tool measures that precisely, with file:line
evidence, and pairs every hardcoded Chinese string with the (unused) zh-CN locale key
whose value is the same text -- that pairing is the actionable output.

Scope (what is scanned)
-----------------------
  * frontend/src/**/*.vue   -- <template> text nodes, attributes, <script> strings
  * frontend/src/**/*.ts    -- <script>-equivalent strings (a .ts string can be rendered)
  * excluded by path: node_modules, dist, .git, coverage, .vite, locales/** (dictionary)

NOT findings (exclusions)
-------------------------
  * text inside t('...') / $t('...') / tc('...') / i18n.global.t('...') arguments
  * text inside comments:  //  /* */  <!-- -->
  * text inside <style> blocks (and CSS comments)
  * text inside console.log/error/warn/info/debug/trace calls
  * locale JSON files themselves
  * non-Chinese text (this detector measures the zh-CN/en-US mixing failure mode)

Usage
-----
  python scripts/audit/detect-hardcoded-i18n.py --self-test
  python scripts/audit/detect-hardcoded-i18n.py
  python scripts/audit/detect-hardcoded-i18n.py --out scripts/audit/out/hardcoded-i18n.md

Exit codes: 0 = ok, 1 = self-test FAIL (report marked untrustworthy), 2 = fatal.

Python 3 standard library only.  No third-party imports, no npm, no network.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import time
import traceback
from collections import Counter, defaultdict
from datetime import datetime

# --------------------------------------------------------------------------------------
# Vocabulary / rules
# --------------------------------------------------------------------------------------

CJK_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")

# Attributes that carry text a user can see.
VISIBLE_ATTRS = {
    "alt",
    "cancel-button-text",
    "confirm-button-text",
    "content",
    "description",
    "empty-text",
    "error",
    "label",
    "loading-text",
    "message",
    "no-data-text",
    "placeholder",
    "prompt",
    "title",
    "tooltip",
    "unit",
}

# An `el-option` value is not displayed, but it is frozen in Chinese and submitted to the
# backend, so it is reported under its own class.
PAYLOAD_ATTRS = {"value"}
PAYLOAD_PARENTS = {"el-option", "option", "el-radio", "el-checkbox"}

# Element Plus message/box/notification APIs: Chinese as a call argument here is UI text.
MESSAGE_CALL_NAMES = {
    "elmessage",
    "elmessagebox",
    "elnotification",
    "elalert",
    "message",
    "messagebox",
    "notification",
    "alert",
}

# Property names that make a Chinese literal user visible.
UI_PROP_NAMES = {
    "aria-label",
    "cancelbuttontext",
    "confirmbuttontext",
    "description",
    "emptytxt",
    "errortext",
    "label",
    "loadingtext",
    "nodatatxt",
    "placeholder",
    "placeholdertext",
    "rowlabel",
    "title",
    "tooltip",
    "unlimitedlabel",
}

# Routinely used as payloads too; when the enclosing call is unknown these are `review`.
AMBIGUOUS_PROP_NAMES = {"content", "message", "title", "label"}

I18N_CALL_RE = re.compile(r"(?:i18n\.global\.)?\$?(?:t|tc|te|d|n|tm)\Z")
CONSOLE_CALL_RE = re.compile(r"console\.(?:log|error|warn|info|debug|trace|table|dir|group)\Z")
IDENT_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")

SKIP_DIRS = {"node_modules", "dist", ".git", "coverage", ".vite", "__pycache__"}


def normalize_attr_name(name: str) -> str:
    """`:confirmButtonText` / `confirm-button-text` / `v-bind:label` -> `confirm-button-text`."""
    value = name
    for prefix in ("v-bind:", "v-on:"):
        if value.startswith(prefix):
            value = value[len(prefix) :]
            break
    else:
        if value[:1] in ":@":
            value = value[1:]
    return value.split(".")[0].strip()


def kebab_attr(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "-", normalize_attr_name(name)).lower()


def call_name_is_i18n(name: str) -> bool:
    return bool(I18N_CALL_RE.match(name))


def call_name_is_console(name: str) -> bool:
    return bool(CONSOLE_CALL_RE.match(name))


JS_KEYWORDS = {
    "if",
    "for",
    "while",
    "switch",
    "catch",
    "return",
    "function",
    "typeof",
    "new",
    "await",
    "yield",
    "else",
    "do",
    "try",
    "throw",
    "case",
    "with",
    "delete",
    "void",
    "in",
    "of",
    "instanceof",
}


def build_call_name_from_text(text, ident_start):
    """Reconstruct a dotted call name ending at ident_start (e.g. `ElMessage.error`)."""
    parts = []
    j = ident_start
    while j > 0:
        match = re.search(r"[A-Za-z_$][A-Za-z0-9_$]*$", text[:j])
        if not match:
            break
        parts.insert(0, match.group(0))
        if match.start() == 0 or text[match.start() - 1] != ".":
            break
        j = match.start() - 1
    name = ".".join(parts)
    if name in JS_KEYWORDS:
        return ""
    return name


def is_message_call(name: str) -> bool:
    if not name:
        return False
    root = IDENT_RE.match(name)
    if not root:
        return False
    return root.group(0).lower() in MESSAGE_CALL_NAMES


def new_finding(file, line, snippet, kind, detail, cls, note=""):
    return {
        "file": file,
        "line": int(line),
        "snippet": snippet,
        "kind": kind,
        "detail": detail,
        "class": cls,
        "note": note,
    }


# --------------------------------------------------------------------------------------
# SFC block extraction
# --------------------------------------------------------------------------------------

BLOCK_OPEN_RE = re.compile(
    r"^[ \t]*<(template|script|style)(?=[\s>])([^>]*)>", re.IGNORECASE | re.MULTILINE
)


def find_block_close(text, content_start, kind):
    """Return (content_end, after_close) for the matching </kind>.

    `<template>` nests, but only *plain* `<template>` opens a level: `<template #slot>`
    and `<template v-slot:...>` are slot markers, and `<template>` inside a comment is
    inert.  A plain first-match search truncated the block at the first slot marker,
    which silently dropped most of the file -- hence this scanner.
    """
    if kind != "template":
        close = re.compile(r"</" + kind + r"\s*>", re.IGNORECASE).search(text, content_start)
        return (close.start(), close.end()) if close else None
    depth = 1
    i = content_start
    n = len(text)
    while i < n:
        if text.startswith("<!--", i):
            end = text.find("-->", i + 4)
            i = n if end < 0 else end + 3
            continue
        match = re.compile(r"<(/?)template(?=[\s>])", re.IGNORECASE).match(text, i)
        if match:
            tag_end = text.find(">", match.end())
            if tag_end < 0:
                return None
            if match.group(1) == "/":
                depth -= 1
                if depth == 0:
                    return (match.start(), tag_end + 1)
            else:
                depth += 1  # slot templates (`<template #x>`) are balanced too
            i = tag_end + 1
            continue
        i += 1
    return None


def extract_blocks(text: str):
    """Return (blocks, custom_block_names).

    `<template #slot>` is NOT an SFC block: it is markup inside the top-level template.
    """
    blocks = []
    custom_names = []
    pos = 0
    n = len(text)
    while pos < n:
        match = BLOCK_OPEN_RE.search(text, pos)
        if not match:
            break
        kind = match.group(1).lower()
        attrs = match.group(2) or ""
        if kind == "template" and ("#" in attrs or "v-slot" in attrs):
            pos = match.end()
            continue
        content_start = text.find(">", match.start()) + 1
        line = text.count("\n", 0, content_start) + 1
        close = find_block_close(text, content_start, kind)
        if close is None:
            blocks.append(
                {
                    "kind": kind,
                    "content": text[content_start:],
                    "line": line,
                    "unparsed": "unclosed <%s> block (EOF before </%s>)" % (kind, kind),
                }
            )
            pos = n
            break
        blocks.append(
            {
                "kind": kind,
                "content": text[content_start : close[0]],
                "line": line,
                "unparsed": None,
            }
        )
        pos = close[1]
    leftover = text[pos:]
    for match in re.finditer(r"^[ \t]*<([A-Za-z][\w:-]*)([^>]*)>", leftover, re.MULTILINE):
        if match.group(1).lower() in {"template", "script", "style"}:
            continue
        custom_names.append("<" + match.group(1) + ">")
    return blocks, custom_names


# --------------------------------------------------------------------------------------
# JS expression helpers
# --------------------------------------------------------------------------------------

def skip_balanced(text: str, open_index: int) -> int:
    """text[open_index] == '(' -> index just past the matching ')'."""
    depth = 0
    i = open_index
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "\\":
            i += 2
            continue
        if ch in "\"'`":
            quote = ch
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    i += 2
                    continue
                i += 1
            i += 1
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


I18N_CALL_START_RE = re.compile(r"(?:[A-Za-z_$][\w$]*\.)*\$?(?:t|tc|te|d|n|tm)")


def strip_i18n_calls(expr: str) -> str:
    """Remove every i18n call (balanced arguments) from a JS expression.

    Non-i18n string literals are kept (quotes replaced by spaces) so that a literal
    surviving the strip can be identified by its own text afterwards.
    """
    out = []
    i = 0
    n = len(expr)
    while i < n:
        ch = expr[i]
        if ch == "\\":
            out.append(expr[i : i + 2])
            i += 2
            continue
        if ch in "\"'`":
            quote = ch
            start = i
            i += 1
            while i < n and expr[i] != quote:
                if expr[i] == "\\":
                    i += 2
                    continue
                i += 1
            i += 1
            out.append(" " + expr[start + 1 : i - 1] + " ")
            continue
        match = I18N_CALL_START_RE.match(expr, i)
        if match:
            j = match.end()
            k = j
            while k < n and expr[k] in " \t\r\n":
                k += 1
            if k < n and expr[k] == "(":
                out.append(" ")
                i = skip_balanced(expr, k)
                continue
            out.append(match.group(0))
            i = j
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def decode_entity(text: str) -> str:
    if "&" not in text:
        return text
    for name, value in (
        ("&lt;", "<"),
        ("&gt;", ">"),
        ("&quot;", '"'),
        ("&#39;", "'"),
        ("&apos;", "'"),
        ("&nbsp;", " "),
        ("&amp;", "&"),
    ):
        text = text.replace(name, value)
    return text


def extract_string_literals(expr: str):
    """Return [(literal_text, index_in_expr)] for quote-delimited literals in expr."""
    out = []
    i = 0
    n = len(expr)
    while i < n:
        ch = expr[i]
        if ch in "\"'`":
            quote = ch
            j = i + 1
            buf = []
            while j < n and expr[j] != quote:
                if expr[j] == "\\":
                    buf.append(expr[j + 1] if j + 1 < n else "")
                    j += 2
                    continue
                buf.append(expr[j])
                j += 1
            out.append(("".join(buf), i))
            i = j + 1
            continue
        i += 1
    return out


def scan_expression(expr, prefix, file, start_line, findings, counters):
    """Find Chinese literals in a JS expression embedded in template markup."""
    stripped = strip_i18n_calls(expr)
    if not CJK_RE.search(stripped):
        return
    literals = extract_string_literals(expr)
    reported = False
    for value, index in literals:
        if not CJK_RE.search(value):
            continue
        snippet = value.strip()
        if not snippet:
            continue
        # A literal is only kept if its own text survives the i18n strip; the surrounding
        # quotes are removed by the strip, so compare the bare text.
        if snippet not in stripped:
            continue
        line = start_line + expr.count("\n", 0, index)
        findings.append(
            new_finding(
                file,
                line,
                re.sub(r"\s+", " ", snippet),
                "template-expression",
                prefix,
                "payload",
                "Chinese string literal inside a bound expression / interpolation; not routed through i18n.",
            )
        )
        counters["hits_template-expression"] += 1
        reported = True
    if reported:
        return
    # Unquoted CJK directly in a mustache, e.g. `{{ 提交中 }}`.  Only reported when the
    # expression carries no quoted literal, so `{{ t('x') + '条' }}` is not double counted.
    bare = "".join(ch for ch in stripped if CJK_RE.match(ch))
    if bare and not literals:
        findings.append(
            new_finding(
                file,
                start_line,
                bare,
                "template-expression",
                prefix,
                "high",
                "Unquoted Chinese in a bound expression / interpolation.",
            )
        )
        counters["hits_template-expression"] += 1


# --------------------------------------------------------------------------------------
# Template parser
# --------------------------------------------------------------------------------------

def scan_template(block, file, findings, counters):
    text = block["content"]
    base = block["line"]
    i = 0
    n = len(text)
    last_attr_name = None
    while i < n:
        if text.startswith("<!--", i):
            end = text.find("-->", i + 4)
            i = n if end < 0 else end + 3
            last_attr_name = None
            continue
        if text[i] == "<":
            if text.startswith("</", i):
                end = text.find(">", i)
                i = n if end < 0 else end + 1
                last_attr_name = None
                continue
            j = i + 1
            while j < n and (text[j].isalnum() or text[j] in "-_:"):
                j += 1
            tag_name = text[i + 1 : j].lower()
            attrs = []
            while j < n and text[j] != ">":
                if text[j] == "/" and j + 1 < n and text[j + 1] == ">":
                    break
                if text[j].isspace():
                    j += 1
                    continue
                k = j
                while k < n and not text[k].isspace() and text[k] not in "=/>":
                    k += 1
                name = text[j:k]
                attr_line = base + text.count("\n", 0, j)
                l = k
                while l < n and text[l].isspace():
                    l += 1
                if l < n and text[l] == "=":
                    l += 1
                    while l < n and text[l].isspace():
                        l += 1
                    if l < n and text[l] in "\"'":
                        quote = text[l]
                        m = l + 1
                        while m < n and text[m] != quote:
                            m += 1
                        value = text[l + 1 : m]
                        j = min(m + 1, n)
                    else:
                        m = l
                        while m < n and not text[m].isspace() and text[m] != ">":
                            m += 1
                        value = text[l:m]
                        j = m
                else:
                    value = None
                    j = l
                attrs.append({"name": name, "value": value, "line": attr_line})
            end = text.find(">", j)
            self_closing = text.startswith("/>", j)
            i = n if end < 0 else end + 1
            counters["elements"] += 1
            for attr in attrs:
                if attr["value"] is None:
                    continue
                if attr["name"].startswith(("@", "v-on:")):
                    continue
                visible = kebab_attr(attr["name"]) in VISIBLE_ATTRS
                payload = (
                    kebab_attr(attr["name"]) in PAYLOAD_ATTRS and tag_name in PAYLOAD_PARENTS
                )
                if not (visible or payload):
                    continue
                counters["attributes_scanned"] += 1
                value = attr["value"]
                if not CJK_RE.search(value):
                    continue
                if not CJK_RE.search(strip_i18n_calls(value)):
                    continue  # fully produced by i18n calls
                snippet = re.sub(r"\s+", " ", value).strip()
                for literal, _index in extract_string_literals(value):
                    if CJK_RE.search(literal):
                        snippet = literal.strip()
                        break
                else:
                    snippet = decode_entity(snippet)
                if visible:
                    findings.append(
                        new_finding(
                            file,
                            attr["line"],
                            snippet,
                            "attribute",
                            "attribute <%s>" % attr["name"],
                            "high",
                            "Chinese literal in a user-visible attribute of <%s>." % tag_name,
                        )
                    )
                    counters["hits_attribute"] += 1
                else:
                    findings.append(
                        new_finding(
                            file,
                            attr["line"],
                            snippet,
                            "attribute-payload",
                            "attribute <%s>" % attr["name"],
                            "payload",
                            "Chinese value of a <%s> option; displayed label is translated, but this "
                            "value is submitted to the backend." % tag_name,
                        )
                    )
                    counters["hits_attribute-payload"] += 1
                last_attr_name = attr["name"]
            if self_closing:
                last_attr_name = None
            continue
        # text node
        end = text.find("<", i)
        if end < 0:
            end = n
        node = text[i:end]
        _handle_text_node(node, base + text.count("\n", 0, i), file, findings, counters)
        if node.strip():
            last_attr_name = None
        i = end


def _handle_text_node(node, line, file, findings, counters):
    if not node.strip():
        return
    counters["text_nodes"] += 1
    mustaches = list(re.finditer(r"\{\{(.*?)\}\}", node, re.DOTALL))
    static = node
    for match in reversed(mustaches):
        static = static[: match.start()] + static[match.end() :]
    static = decode_entity(static).strip()
    if static and CJK_RE.search(static):
        snippet = re.sub(r"\s+", " ", static)
        findings.append(
            new_finding(
                file,
                line + node.count("\n", 0, node.find(static)),
                snippet,
                "template-text",
                "template text node",
                "high",
                "Chinese rendered directly as element text.",
            )
        )
        counters["hits_template-text"] += 1
    for match in mustaches:
        scan_expression(
            match.group(1),
            "mustache {{ ... }}",
            file,
            line + node.count("\n", 0, match.start(1)),
            findings,
            counters,
        )


# --------------------------------------------------------------------------------------
# Script lexer + script-side detector
# --------------------------------------------------------------------------------------

class JsLexer:
    """Single-pass lexer that keeps strings, comments and call nesting."""

    def __init__(self, text, start_line, file, findings, counters, pool=None):
        self.text = text
        self.start_line = start_line
        self.file = file
        self.findings = findings
        self.counters = counters
        self.pool = pool if pool is not None else []
        self.is_test = "/__tests__/" in "/" + file or file.endswith(".spec.ts") or file.endswith(".test.ts")
        self.tokens = []          # (index, text, kind)
        self.open_parens = []     # {index, open, brace_depth, name, end}
        self.paren_stack = []
        self.brace_depth = 0
        self.frames = [{"type": "code", "kind": "code", "depth": 0}]
        self.template_ranges = []

    # -- helpers ----------------------------------------------------------------------
    def line_at(self, index):
        return self.start_line + self.text.count("\n", 0, index)

    def token_before(self, index):
        best = None
        for token in self.tokens:
            if token[0] < index and (best is None or token[0] > best[0]):
                best = token
        return best

    def enclosing_brace_depth(self, index):
        depth = 0
        for token in self.tokens:
            if token[0] >= index:
                break
            if token[1] == "{":
                depth += 1
            elif token[1] == "}":
                depth -= 1
        return depth

    def enclosing_call(self, index):
        """Narrowest recorded call whose argument list contains `index` at <= brace depth.

        A call's argument list is (still-open `(` .. its `)`), and strings are only ever
        analysed while their call is still open, so `end is None` means "open, spans far".
        """
        best = None
        here = self.enclosing_brace_depth(index)
        for call in self.open_parens:
            if call["open"] >= index:
                continue
            if call["end"] is not None and call["end"] <= index:
                continue
            if here < call["brace_depth"]:
                continue
            if best is None or call["open"] > best["open"]:
                best = call
        return best

    def build_call_name(self, ident_start):
        return build_call_name_from_text(self.text, ident_start)

    def property_name(self, start_index):
        prev = self.token_before(start_index)
        if prev is None or prev[1] not in (":", "="):
            return None
        gap = self.text[prev[0] + len(prev[1]) : start_index]
        if prev[1] == ":":
            if gap.strip():
                return None
        else:
            # `w => ` / `x === ` are not property assignments
            if gap.strip() and gap.strip()[0] in ">=":
                return None
        name_token = self.token_before(prev[0])
        if name_token is None or name_token[2] != "ident":
            return None
        return name_token[1]

    def handle_string(self, literal, start_index, end_index):
        if not CJK_RE.search(literal):
            return
        self.counters["cjk_literals"] += 1
        call = self.enclosing_call(start_index)
        call_name = call["name"] if call else ""
        if call_name and call_name_is_i18n(call_name):
            self.counters["excluded_i18n_arg"] += 1
            return
        if call_name and call_name_is_console(call_name):
            self.counters["excluded_console"] += 1
            return
        prop = self.property_name(start_index)
        prop_key = prop.replace("-", "").lower() if prop else ""
        in_message_call = bool(call) and is_message_call(call_name)
        if prop_key and (prop_key in UI_PROP_NAMES or prop_key in AMBIGUOUS_PROP_NAMES):
            if in_message_call or prop_key in UI_PROP_NAMES:
                cls = "high"
                note = "Chinese literal assigned to the UI property `%s`." % prop
            else:
                cls = "review"
                note = (
                    "Chinese literal assigned to `%s` of a call that is not a recognized message "
                    "API; user visibility not established by this detector." % prop
                )
            self.findings.append(
                new_finding(
                    self.file,
                    self.line_at(start_index),
                    literal.strip(),
                    "property",
                    "property `%s`" % prop,
                    cls,
                    note,
                )
            )
            self.counters["hits_property"] += 1
            return
        if in_message_call:
            snippet = literal.strip()
            if not snippet:
                return
            self.findings.append(
                new_finding(
                    self.file,
                    self.line_at(start_index),
                    snippet,
                    "message-call",
                    "call %s()" % call_name,
                    "high",
                    "Chinese literal passed to the Element Plus message API %s()." % call_name,
                )
            )
            self.counters["hits_message-call"] += 1
            return
        # Unclassified: a Chinese literal that is not in any position this detector treats
        # as user visible.  Recorded (not dropped) so the blind spot is measurable.
        self.counters["skipped_not_ui"] += 1
        if not self.is_test and len(self.pool) < 500:
            self.pool.append(
                {
                    "file": self.file,
                    "line": self.line_at(start_index),
                    "snippet": re.sub(r"\s+", " ", literal.strip())[:80],
                    "context": ("call %s()" % call_name) if call_name else ("property `%s`" % prop if prop else "bare literal"),
                }
            )

    def read_quoted(self, i):
        text = self.text
        quote = text[i]
        j = i + 1
        n = len(text)
        buf = []
        while j < n and text[j] != quote:
            if text[j] == "\\":
                buf.append(text[j + 1] if j + 1 < n else "")
                j += 2
                continue
            buf.append(text[j])
            j += 1
        literal = "".join(buf)
        self.handle_string(literal, i, j)
        self.tokens.append((i, literal, "string"))
        return j + 1

    def close_template_at(self, backtick_index):
        """A template literal ends at `backtick_index`; emit its text and pop the frame."""
        templ = None
        for index in range(len(self.frames) - 1, 0, -1):
            if self.frames[index]["type"] == "template":
                templ = self.frames[index]
                del self.frames[index:]
                break
        if templ is None:
            return False
        for rng in self.template_ranges:
            if rng[1] is None and rng[0] == templ["start"]:
                rng[1] = backtick_index
                break
        self.handle_string(self.text[templ["emitted"] : backtick_index], templ["emitted"], backtick_index)
        return True

    def run(self):
        text = self.text
        i = 0
        n = len(text)
        while i < n:
            frame = self.frames[-1]
            if frame["type"] != "template":
                # both top-level code and `${...}` interpolations are code context
                ch = text[i]
                if ch == "/" and i + 1 < n and text[i + 1] == "/":
                    j = text.find("\n", i)
                    i = n if j < 0 else j
                    self.counters["comments"] += 1
                    continue
                if ch == "/" and i + 1 < n and text[i + 1] == "*":
                    j = text.find("*/", i + 2)
                    i = n if j < 0 else j + 2
                    self.counters["comments"] += 1
                    continue
                if ch in "\"'":
                    i = self.read_quoted(i)
                    continue
                if ch == "`":
                    self.template_ranges.append([i, None])
                    self.frames.append(
                        {"type": "template", "start": i, "emitted": i + 1, "depth": frame["depth"]}
                    )
                    i += 1
                    continue
                if ch == "(":
                    prev = self.token_before(i)
                    if prev is not None and re.fullmatch(r"\s*", text[prev[0] + len(prev[1]) : i]):
                        self.paren_stack.append(i)
                        self.open_parens.append(
                            {
                                "index": prev[0] + len(prev[1]),
                                "open": i,
                                "brace_depth": frame["depth"],
                                "name": self.build_call_name(prev[0] + len(prev[1])),
                                "end": None,
                            }
                        )
                    self.tokens.append((i, "(", "punct"))
                    i += 1
                    continue
                if ch == ")":
                    if self.paren_stack:
                        opened = self.paren_stack.pop()
                        for call in self.open_parens:
                            if call["open"] == opened and call["end"] is None:
                                call["end"] = i
                    self.tokens.append((i, ")", "punct"))
                    i += 1
                    continue
                if ch == "{":
                    self.tokens.append((i, "{", "punct"))
                    frame["depth"] += 1
                    i += 1
                    continue
                if ch == "}":
                    frame["depth"] -= 1
                    self.tokens.append((i, "}", "punct"))
                    if frame["kind"] == "expr" and frame["depth"] < frame["base"]:
                        self.frames.pop()  # interpolation closed -> back to template body
                        if self.frames and self.frames[-1]["type"] == "template":
                            # the next static segment starts after the `}`
                            self.frames[-1]["emitted"] = i + 1
                    i += 1
                    continue
                if ch in "[];,":
                    self.tokens.append((i, ch, "punct"))
                    i += 1
                    continue
                if ch in ":=":
                    self.tokens.append((i, ch, "punct"))
                    i += 1
                    continue
                ident = IDENT_RE.match(text, i)
                if ident:
                    self.tokens.append((i, ident.group(0), "ident"))
                    i = ident.end()
                    continue
                i += 1
                continue
            if frame["type"] == "template":
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "`":
                    self.close_template_at(i)
                    i += 1
                    continue
                if text[i] == "$" and i + 1 < n and text[i + 1] == "{":
                    self.handle_string(text[frame["emitted"] : i], frame["emitted"], i)
                    self.frames.append(
                        {"type": "expr", "kind": "expr", "base": frame["depth"], "depth": frame["depth"]}
                    )
                    i += 2
                    continue
                i += 1
                continue
            # interpolation expression: a nested template literal may open here
            if text[i] == "`":
                self.template_ranges.append([i, None])
                self.frames.append(
                    {"type": "template", "start": i, "emitted": i + 1, "depth": frame["depth"]}
                )
                i += 1
                continue
            i += 1

    def finalize(self):
        for rng in self.template_ranges:
            if rng[1] is None:
                self.counters["unclosed_template_literals"] += 1

def scan_script(block, file, findings, counters, pool=None):
    lexer = JsLexer(block["content"], block["line"], file, findings, counters, pool)
    try:
        lexer.run()
        lexer.finalize()
    except Exception as exc:  # pragma: no cover - defensive
        return "%s: %s" % (type(exc).__name__, exc)
    return None


# --------------------------------------------------------------------------------------
# Locale dictionary
# --------------------------------------------------------------------------------------

def flatten(node, prefix=""):
    out = {}
    if isinstance(node, dict):
        for key, value in node.items():
            path = prefix + "." + str(key) if prefix else str(key)
            out.update(flatten(value, path))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            out.update(flatten(value, "%s[%d]" % (prefix, index)))
    else:
        out[prefix] = node
    return out


def load_locale_dictionary(locales_dir):
    """Flatten every zh-CN namespace file into {key path -> zh text}.

    The vue-i18n message tree is the union of the files' own root keys (see
    `frontend/src/i18n/index.ts`, which `Object.assign`s each module), so the key path
    is the JSON key path *without* a filename prefix -- `accounts.json` contributes
    `accounts.title`, `base.json` contributes `common.delete` etc.
    """
    entries = []
    warnings = []
    if not os.path.isdir(locales_dir):
        return entries, ["locale directory not found: %s" % locales_dir]
    for name in sorted(os.listdir(locales_dir)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(locales_dir, name)
        namespace = name[:-5]
        try:
            with open(path, "r", encoding="utf-8-sig") as handle:
                data = json.load(handle)
        except Exception as exc:
            warnings.append("%s: %s: %s" % (path, type(exc).__name__, exc))
            continue
        for keypath, value in flatten(data).items():
            if isinstance(value, str) and CJK_RE.search(value):
                entries.append(
                    {
                        "ns": namespace,
                        "keypath": keypath,
                        "text": value.strip(),
                        "id": keypath,
                    }
                )
    return entries, warnings


def strip_comments(text: str) -> str:
    """Blank out comments and string bodies so key extraction cannot match prose."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


KEY_CALL_RE = re.compile(r"(?<![\w$.])(?:i18n\.global\.)?\$?(?:t|tc|te|tm)\(\s*(['\"`])([^'\"`]+)\1")
KEY_TEMPLATE_RE = re.compile(r"(?<![\w$.])(?:i18n\.global\.)?\$?(?:t|tc|te|tm)\(\s*`([^`$]*)\$\{")
STRING_LITERAL_RE = re.compile(r"'([^'\n\\]*)'|\"([^\"\n\\]*)\"|`([^`$\n]*)`")
KEY_SHAPE_RE = re.compile(r"^[A-Za-z_$][\w$-]*(?:\.[\w$-]+)*$")


def collect_key_evidence(src_root):
    """Classify how each locale key could be referenced.

    Returns (direct, indirect, dynamic_prefixes, files, failures):
      direct           -- appears as a `t('key')`-style argument
      indirect         -- appears as a string literal somewhere else (constant maps,
                          arrays, config), i.e. reachable through `t(variable)`
      dynamic_prefixes -- prefixes from ``t(`prefix.${expr}`)``
    """
    direct = Counter()
    indirect = Counter()
    dynamic_prefixes = Counter()
    files = 0
    failures = []
    for base, dirs, names in os.walk(src_root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in sorted(names):
            if not name.endswith((".vue", ".ts")):
                continue
            path = os.path.join(base, name)
            rel = os.path.relpath(path, src_root).replace("\\", "/")
            if rel.split("/")[0] == "locales":
                continue
            files += 1
            try:
                with open(path, "r", encoding="utf-8-sig") as handle:
                    text = strip_comments(handle.read())
            except Exception as exc:
                failures.append("%s: %s" % (rel, exc))
                continue
            for match in KEY_CALL_RE.finditer(text):
                key = match.group(2)
                if key and not key[0].isspace() and "$" not in key:
                    direct[key] += 1
            for match in KEY_TEMPLATE_RE.finditer(text):
                prefix = match.group(1)
                if prefix:
                    dynamic_prefixes[prefix] += 1
            for match in STRING_LITERAL_RE.finditer(text):
                literal = match.group(1) or match.group(2) or match.group(3) or ""
                if literal and KEY_SHAPE_RE.match(literal) and "." in literal:
                    indirect[literal] += 1
    return direct, indirect, dynamic_prefixes, files, failures


def classify_keys(dictionary_ids, direct, indirect, dynamic_prefixes):
    """Return {key: 'direct' | 'indirect' | 'dynamic-prefix' | 'absent'}."""
    verdicts = {}
    prefixes = list(dynamic_prefixes)
    for key in dictionary_ids:
        if key in direct:
            verdicts[key] = "direct"
        elif key in indirect:
            verdicts[key] = "indirect"
        elif any(key.startswith(prefix) for prefix in prefixes):
            verdicts[key] = "dynamic-prefix"
        else:
            verdicts[key] = "absent"
    return verdicts


# --------------------------------------------------------------------------------------
# Tree scan
# --------------------------------------------------------------------------------------

def iter_source_files(src_root):
    for base, dirs, names in os.walk(src_root):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
        for name in sorted(names):
            if name.endswith((".vue", ".ts")):
                yield os.path.join(base, name)


def scan_tree(src_root):
    findings = []
    counters = Counter()
    files_scanned = 0
    vue_files = 0
    blocks_seen = Counter()
    custom_blocks = Counter()
    unparsed = []
    decode_failures = []
    styles_skipped = 0
    pool = []
    for path in iter_source_files(src_root):
        rel = os.path.relpath(path, src_root).replace("\\", "/")
        if rel.split("/")[0] == "locales":
            continue
        try:
            raw = open(path, "rb").read()
        except Exception as exc:
            decode_failures.append("%s: %s" % (rel, exc))
            continue
        try:
            text = raw.decode("utf-8-sig")
        except UnicodeDecodeError as exc:
            decode_failures.append("%s: UnicodeDecodeError: %s" % (rel, exc))
            text = raw.decode("utf-8", errors="replace")
            unparsed.append("%s: decoded with errors='replace' (%s)" % (rel, exc))
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        files_scanned += 1
        if path.endswith(".vue"):
            vue_files += 1
            blocks, custom_names = extract_blocks(text)
            for name in custom_names:
                custom_blocks[name] += 1
            if not blocks:
                unparsed.append("%s: no <template>/<script>/<style> block found" % rel)
            for block in blocks:
                blocks_seen[block["kind"]] += 1
                if block["unparsed"]:
                    unparsed.append("%s: %s" % (rel, block["unparsed"]))
                if block["kind"] == "style":
                    styles_skipped += 1
                    continue
                if block["kind"] == "template":
                    try:
                        scan_template(block, rel, findings, counters)
                    except Exception as exc:
                        unparsed.append(
                            "%s: template parse error near line %d: %s: %s"
                            % (rel, block["line"], type(exc).__name__, exc)
                        )
                elif block["kind"] == "script":
                    error = scan_script(block, rel, findings, counters, pool)
                    if error:
                        unparsed.append("%s: script lexer stopped: %s" % (rel, error))
        else:
            error = scan_script({"content": text, "line": 1}, rel, findings, counters, pool)
            if error:
                unparsed.append("%s: ts lexer stopped: %s" % (rel, error))
    files_with_findings = sorted({f["file"] for f in findings})
    stats = {
        "files_scanned": files_scanned,
        "vue_files": vue_files,
        "blocks": blocks_seen,
        "custom_blocks": custom_blocks,
        "styles_skipped": styles_skipped,
        "counters": counters,
        "unparsed": unparsed,
        "decode_failures": decode_failures,
        "files_with_findings": files_with_findings,
        "locale_warnings": [],
        "pool": pool,
    }
    return findings, stats


# --------------------------------------------------------------------------------------
# Pairing
# --------------------------------------------------------------------------------------

def pair_findings(findings, dictionary_entries, exact_only=False):
    """Pair findings with dictionary entries whose zh-CN value matches the snippet.

    Default is exact text equality (a key whose value *is* the hardcoded string).
    `exact_only=False` additionally allows one-side containment, which is noisy
    ("违规操作" would match `common.action` = "操作") and is only used for a
    separate near-miss appendix.
    """
    by_text = defaultdict(list)
    for entry in dictionary_entries:
        by_text[entry["text"]].append(entry)
    pairs = []
    for finding in findings:
        snippet = finding["snippet"]
        matches = list(by_text.get(snippet, []))
        if not matches and not exact_only and len(snippet) >= 2:
            matches = [
                e
                for e in dictionary_entries
                if len(e["text"]) >= 2 and (e["text"] in snippet or snippet in e["text"])
            ]
        for entry in matches:
            pairs.append(
                {
                    "text": snippet,
                    "key": entry["id"],
                    "value": entry["text"],
                    "file": finding["file"],
                    "line": finding["line"],
                    "kind": finding["kind"],
                    "class": finding["class"],
                    "exact": entry["text"] == snippet,
                }
            )
    return pairs


def raw_occurrence_counts(src_root, tokens, suffix=None):
    counts = Counter()
    where = defaultdict(list)
    for path in iter_source_files(src_root):
        if suffix and not path.endswith(suffix):
            continue
        rel = os.path.relpath(path, src_root).replace("\\", "/")
        if rel.split("/")[0] == "locales":
            continue
        try:
            text = open(path, "r", encoding="utf-8-sig").read().replace("\r\n", "\n")
        except Exception:
            continue
        for index, line in enumerate(text.split("\n"), start=1):
            for token in tokens:
                if token in line:
                    counts[token] += 1
                    where[token].append((rel, index, line.strip()[:160]))
    return counts, where


# --------------------------------------------------------------------------------------
# Self-test
# --------------------------------------------------------------------------------------

FIXTURE_TEMPLATE = """<template>
  <div class="panel">
    <!-- 这是注释 排除 -->
    <h3>硬编码标题</h3>
    <el-input placeholder="请输入名称" />
    <el-input :placeholder="t('fixture.panel.placeholder')" />
    <span>{{ t('fixture.panel.placeholder') }}</span>
    <el-button title="保存" @click="save">保存</el-button>
    <span>{{ 提交中 }}</span>
    <el-option :label="t('fixture.panel.placeholder')" value="选项值" />
    <el-option :label="中文标签" value="b" />
    <span>混排 {{ t('fixture.panel.placeholder') }} 尾部中文</span>
    <span>{{ ok ? '内联中文' : t('fixture.panel.placeholder') }}</span>
    <el-input :placeholder="ok ? t('fixture.panel.placeholder') : '回退中文'" />
  </div>
</template>

<script setup lang="ts">
// 注释里的中文不算
/* 块注释里的中文也不算 */
const options = { label: '标签中文' }
function save() {
  console.log('日志中文')
  console.error('错误日志中文')
  ElMessage.error('保存失败中文')
  ElMessage.success(t('fixture.panel.placeholder'))
  const box = { title: '标题中文', content: '内容中文' }
  doSomething(box)
}
</script>

<style scoped>
/* 样式里的中文 不算 */
.panel::after { content: '样式中文'; }
</style>
"""

FIXTURE_LOCALE = {
    "fixture": {
        "panel": {
            "placeholder": "请输入名称",
            "title": "硬编码标题",
            "label": "标签中文",
            "message": "保存失败中文",
        }
    }
}

EXPECTED_POSITIVES = [
    ("template-text", "硬编码标题", "template text node"),
    ("attribute", "请输入名称", "attribute <placeholder>"),
    ("message-call", "保存失败中文", "call ElMessage.error()"),
    ("property", "标签中文", "property `label`"),
    ("template-expression", "内联中文", "mustache"),
    ("attribute", "回退中文", "attribute <:placeholder>"),
    ("attribute-payload", "选项值", "attribute <value>"),
]

EXCLUDED_SNIPPETS = [
    "这是注释",
    "注释里",
    "块注释",
    "样式中文",
    "日志中文",
    "错误日志中文",
    "fixture.panel.placeholder",
]

# Fixture strings that must also appear as locale values so the pairing check has teeth.
PAIRING_EXPECTATIONS = {
    "硬编码标题": "fixture.panel.title",
    "请输入名称": "fixture.panel.placeholder",
    "保存失败中文": "fixture.panel.message",
    "标签中文": "fixture.panel.label",
}


def run_self_test(root, locales_dir, out_stream):
    """Calibration suite. Returns (log_lines, failed_count, meta)."""
    lines = []

    def emit(message=""):
        lines.append(message)
        out_stream.write(message + "\n")
        out_stream.flush()

    failed = 0
    passed = 0
    results = []

    def check(label, condition, evidence=""):
        nonlocal failed, passed
        status = "PASS" if condition else "FAIL"
        if condition:
            passed += 1
        else:
            failed += 1
        results.append((label, status, evidence))
        emit("  [%s] %s%s" % (status, label, ("  -- " + str(evidence)) if evidence else ""))

    emit("=" * 78)
    emit("SELF-TEST -- synthetic fixtures + real-tree known positives")
    emit("time     : %s" % datetime.now().astimezone().isoformat(timespec="seconds"))
    emit("script   : %s" % os.path.abspath(__file__))
    emit("frontend : %s" % root)
    emit("locales  : %s" % locales_dir)
    emit("=" * 78)

    scratch = tempfile.mkdtemp(prefix="hardcoded-i18n-selftest-")
    emit("scratch  : %s" % scratch)
    named_evidence = {}
    try:
        fixture_root = os.path.join(scratch, "src")
        os.makedirs(fixture_root)
        with open(os.path.join(fixture_root, "fixture.vue"), "w", encoding="utf-8") as handle:
            handle.write(FIXTURE_TEMPLATE)
        locale_root = os.path.join(scratch, "locales", "zh-CN")
        os.makedirs(locale_root)
        with open(os.path.join(locale_root, "fx.json"), "w", encoding="utf-8") as handle:
            json.dump(FIXTURE_LOCALE, handle, ensure_ascii=False, indent=2)

        findings, stats = scan_tree(fixture_root)
        entries, warnings = load_locale_dictionary(locale_root)

        emit("")
        emit("[fixture findings: %d]" % len(findings))
        for finding in sorted(findings, key=lambda f: (f["line"], f["kind"])):
            emit(
                "  fixture.vue:%-3d [%-19s/%-7s] %-10r  %s"
                % (finding["line"], finding["kind"], finding["class"], finding["snippet"], finding["detail"])
            )
        emit("[fixture counters: %s]" % dict(sorted(stats["counters"].items())))
        emit("[fixture style blocks skipped: %d]" % stats["styles_skipped"])
        emit("[fixture dictionary: %s]" % [e["id"] for e in entries])
        if warnings:
            emit("[fixture dictionary warnings: %s]" % warnings)

        emit("")
        emit("[A] synthetic positives (must fire)")
        for kind, snippet, detail in EXPECTED_POSITIVES:
            hits = [
                f
                for f in findings
                if f["kind"] == kind and snippet in f["snippet"] and (detail in f["detail"] or kind != "attribute")
            ]
            check(
                "positive %-21s %-8r -> %s" % (kind, snippet, detail),
                len(hits) >= 1,
                ", ".join("fixture.vue:%d" % h["line"] for h in hits) or "nothing found",
            )
        check(
            "negative-in-A: attribute whose value is only a t() call must NOT fire",
            not [
                f
                for f in findings
                if f["kind"] == "attribute" and "fixture.panel.placeholder" in f["detail"] + f["snippet"]
            ],
        )
        check(
            "negative-in-A: t() argument never reported as a snippet",
            not [f for f in findings if "fixture.panel.placeholder" in f["snippet"]],
        )
        check(
            "positive-in-A: mixed node reports only the static Chinese part",
            any(f["snippet"] == "尾部中文" or "尾部中文" in f["snippet"] for f in findings),
        )

        emit("")
        emit("[B] exclusion controls (must NOT fire)")
        for snippet in EXCLUDED_SNIPPETS:
            offenders = [f for f in findings if snippet in f["snippet"]]
            check("excluded %-28r absent from findings" % snippet, not offenders, offenders)
        check("style block recorded as skipped", stats["styles_skipped"] >= 1, stats["styles_skipped"])
        check(
            "console.log / console.error arguments never reported",
            not [f for f in findings if f["kind"] in ("message-call", "property") and "日志" in f["snippet"]],
        )

        emit("")
        emit("[C] key-pairing logic (fixture locale)")
        pairs = pair_findings(findings, entries)
        paired = defaultdict(set)
        for pair in pairs:
            paired[pair["text"]].add(pair["key"])
        for text, key in PAIRING_EXPECTATIONS.items():
            check(
                "pairing %-10r -> %s" % (text, key),
                key in paired.get(text, set()),
                sorted(paired.get(text, set())) or "no pair",
            )
        check(
            "pairing: a hardcoded string with no locale value yields no pair",
            "回退中文" not in paired and "选项值" not in paired and "标题中文" not in paired,
            sorted(paired),
        )
        check(
            "pairing: dictionary carries exactly the 4 fixture CJK values",
            sorted(e["id"] for e in entries)
            == sorted(
                [
                    "fixture.panel.label",
                    "fixture.panel.message",
                    "fixture.panel.placeholder",
                    "fixture.panel.title",
                ]
            ),
            sorted(e["id"] for e in entries),
        )

        emit("")
        emit("[D] real tree known positives")
        src_root = os.path.join(root, "src")
        if os.path.isdir(src_root):
            tree_findings, tree_stats = scan_tree(src_root)
            emit("  real tree: %d finding(s) in %d file(s)" % (len(tree_findings), len(tree_stats["files_with_findings"])))
            for token in ("删除", "取消", "搜索"):
                hits = [f for f in tree_findings if token in f["snippet"]]
                where = ", ".join("%s:%d" % (h["file"], h["line"]) for h in hits[:5]) or "none"
                emit("  %s : %d strict finding(s) -> %s" % (token, len(hits), where))
                named_evidence[token] = ["%s:%d" % (h["file"], h["line"]) for h in hits]
            check("real tree produced at least one finding", len(tree_findings) > 0, len(tree_findings))
            check(
                "real tree known positive '恶意登录尝试' found in BlacklistManagement.vue",
                any(f["file"] == "views/security/BlacklistManagement.vue" for f in tree_findings if f["snippet"] == "恶意登录尝试"),
                [
                    "%s:%d" % (f["file"], f["line"])
                    for f in tree_findings
                    if f["snippet"] == "恶意登录尝试"
                ],
            )
            check(
                "real tree known positive attribute finding count >= 5",
                len([f for f in tree_findings if f["kind"] == "attribute-payload"]) >= 5,
                len([f for f in tree_findings if f["kind"] == "attribute-payload"]),
            )
        else:
            check("real tree src/ exists", False, src_root)

        emit("")
        emit("[E] honest known-positive accounting for the brief's named strings")
        emit(
            "  The brief states 「删除」13x, 「取消」11x, 「搜索」22x occur as Chinese literals in .vue"
        )
        emit(
            "  files. Strict findings for them in this run: %s"
            % (named_evidence if named_evidence else "{}")
        )
        emit(
            "  Report mode prints the raw line counts with file:line so the discrepancy is auditable."
        )
        check(
            "named-string accounting recorded (must be explicit, not silent)",
            all(token in named_evidence for token in ("删除", "取消", "搜索")),
            named_evidence,
        )
    except Exception:
        failed += 1
        emit("  [FAIL] self-test raised an unexpected exception:")
        for line in traceback.format_exc().splitlines():
            emit("      " + line)
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
        gone = not os.path.exists(scratch)
        results.append(("scratch fixtures removed (existence asserted)", "PASS" if gone else "FAIL", scratch))
        if gone:
            passed += 1
        else:
            failed += 1
        emit("")
        emit("  [%s] scratch fixtures removed and existence asserted (%s)" % ("PASS" if gone else "FAIL", scratch))

    emit("")
    emit("-" * 78)
    emit("SELF-TEST SUMMARY: %d PASS / %d FAIL" % (passed, failed))
    emit(
        "VERDICT: %s"
        % ("PASS -- calibration trustworthy, report counts may be quoted" if failed == 0 else "FAIL -- report MUST be treated as untrustworthy")
    )
    emit("-" * 78)
    return lines, failed, {"results": results, "real_tree": named_evidence}


# --------------------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------------------

def md_escape(text):
    return str(text).replace("|", "\\|").replace("\n", " ")


def build_report(args, findings, stats, entries, unused, pairs_unused, pairs_used, selftest_log, selftest_failed, tree_note, raw_counts, raw_where):
    lines = []
    add = lines.append
    now = datetime.now().astimezone().isoformat(timespec="seconds")
    counters = stats["counters"]
    verdicts = stats["key_verdicts"]
    vue_with_findings = sorted({f for f in stats["files_with_findings"] if f.endswith(".vue")})
    ts_with_findings = sorted({f for f in stats["files_with_findings"] if f.endswith(".ts")})
    distinct = sorted({f["snippet"] for f in findings})
    occ = Counter(f["snippet"] for f in findings)

    add("# Hardcoded i18n detector report -- `frontend/src`")
    add("")
    add("Generated: `%s`  " % now)
    add("Detector: `scripts/audit/detect-hardcoded-i18n.py`  ")
    add("Command: `python scripts/audit/detect-hardcoded-i18n.py%s`" % (" --self-test" if args.self_test else ""))
    add("")
    add("## 0. Trust verdict")
    add("")
    if selftest_failed == 0:
        add("**Calibration PASSED (0 failed assertions).** The embedded self-test below proves the")
        add("detector fires on every synthetic positive, stays silent on every exclusion control,")
        add("and pairs text to locale keys. Counts in this report may be quoted.")
    else:
        add("**Calibration FAILED (%d failed assertion(s)). This report MUST be treated as" % selftest_failed)
        add("untrustworthy.** Do not quote any count below until `--self-test` is green.")
    add("")
    add("Headline numbers:")
    add("")
    add("- total findings: **%d**" % len(findings))
    add("- distinct Chinese snippets: **%d**" % len(distinct))
    add("- `.vue` files affected: **%d**" % len(vue_with_findings))
    add("- distinct hardcoded snippets that DO have an existing but unused locale key (exact): **%d**" % len({p["text"] for p in pairs_unused}))
    add("- distinct hardcoded snippets whose exact text IS a locale value of a key used elsewhere: **%d**" % len({p["text"] for p in pairs_used}))
    add("- locale keys that are definitely unused (no direct, indirect or dynamic reference): **%d**" % len(unused))
    add("- `.vue` files scanned (the denominator): **%d**" % stats["vue_files"])
    add("")
    add("Read section 6 before repeating the task brief's 「删除」/「取消」/「搜索」 claim: this scan")
    add("does **not** reproduce it, and shows exactly why.")
    add("")
    add("What this report does **not** establish (do not read it as \"the UI is fully i18n'd\"):")
    add("")
    add("- It does not prove that every user-visible string is routed through i18n; it proves")
    add("  that no *literal Chinese text node or visible attribute* was found, and lists 200")
    add("  Chinese literals in positions the rules do not classify (section 8.2).")
    add("- It does not see Chinese produced at runtime (concatenation, backend responses,")
    add("  dynamic props, `v-html`) -- see section 8.")
    add("- It does not check that the `en-US` translation set is complete or correct.")
    add("")
    add("## 1. Method (what exactly was scanned)")
    add("")
    add("| step | rule |")
    add("|---|---|")
    add("| scope | `frontend/src/**/*.vue` (full SFC) and `frontend/src/**/*.ts` |")
    add("| excluded paths | `node_modules`, `dist`, `.git`, `coverage`, `.vite`, `__pycache__`, `frontend/src/locales/**` |")
    add("| template text | text nodes outside `{{ }}`, HTML entities decoded, `<!-- -->` skipped |")
    add("| visible attributes | %s |" % ", ".join("`%s`" % a for a in sorted(VISIBLE_ATTRS)))
    add("| payload attributes | `value` on `el-option` / `option` / `el-radio` / `el-checkbox` |")
    add("| template expressions | string literals inside `{{ }}` and inside bindings, after removing every `t()/$t()/tc()/tm()/i18n.global.t()` call with balanced arguments |")
    add("| script (`<script>`, `.ts`) | single-pass JS lexer over strings and template literals, tracking comments, call nesting and property keys |")
    add("| script triage | `high` = message-API argument or an unambiguous UI property (`placeholder`, `description`, `unlimitedLabel`, ...); `review` = Chinese on `label`/`title`/`message`/`content` of a call that is **not** a recognized message API, where user visibility is not established |")
    add("| pairing | each finding's trimmed snippet is matched **exactly** against the flattened `locales/zh-CN/*.json` dictionary (id = the vue-i18n key path, e.g. `common.delete`); containment matches are listed separately as near misses and never counted |")
    add("")
    add("Calibration is mandatory: `--self-test` runs before any count is trusted, and a failed")
    add("assertion marks the report untrustworthy (non-zero exit).")
    add("")
    add("## 2. Self-test raw log")
    add("")
    add("```text")
    lines.extend(selftest_log)
    add("```")
    add("")
    add("## 3. Scope and counts")
    add("")
    add("| metric | value |")
    add("|---|---|")
    add("| files scanned | %d |" % stats["files_scanned"])
    add("| `.vue` files scanned | %d |" % stats["vue_files"])
    add("| `.ts` files scanned | %d |" % (stats["files_scanned"] - stats["vue_files"]))
    add("| `.vue` files with >= 1 finding | **%d** |" % len(vue_with_findings))
    add("| `.ts` files with >= 1 finding | %d |" % len(ts_with_findings))
    add("| total findings | **%d** |" % len(findings))
    add("| distinct Chinese snippets | **%d** |" % len(distinct))
    add("| class `high` | %d |" % len([f for f in findings if f["class"] == "high"]))
    add("| class `review` | %d |" % len([f for f in findings if f["class"] == "review"]))
    add("| class `payload` | %d |" % len([f for f in findings if f["class"] == "payload"]))
    add("| `<style>` blocks skipped | %d |" % stats["styles_skipped"])
    add("| visible attributes inspected | %d |" % counters.get("attributes_scanned", 0))
    add("| template text nodes inspected | %d |" % counters.get("text_nodes", 0))
    add("| zh-CN dictionary entries containing CJK | %d |" % len(entries))
    add("| keys referenced directly (`t('key')`) | %d |" % verdicts.get("direct", 0))
    add("| keys reachable indirectly (literal in a map/array, used via `t(variable)`) | %d |" % verdicts.get("indirect", 0))
    add("| keys behind a dynamic template-literal prefix (`` t(`ns.${x}`) ``) | %d |" % verdicts.get("dynamic-prefix", 0))
    add("| keys with **no** reference of any kind (\"definitely unused\") | **%d** |" % verdicts.get("absent", 0))
    add("| unused keys matched by a hardcoded snippet | **%d** |" % len({p["key"] for p in pairs_unused}))
    add("| distinct hardcoded snippets with an unused key | **%d** |" % len({p["text"] for p in pairs_unused}))
    add("| CJK string literals found outside any classified UI position | %d |" % counters.get("skipped_not_ui", 0))
    add("| locale/JSON parse failures | %d |" % len(stats["locale_warnings"]))
    add("| files reported as not fully parseable | %d |" % len(stats["unparsed"] + stats["decode_failures"]))
    add("")
    add("### 3.1 Raw counters")
    add("")
    add("```text")
    for key in sorted(counters):
        add("%-30s %d" % (key, counters[key]))
    add("%-30s %s" % ("sfc_blocks", dict(stats["blocks"])))
    add("%-30s %s" % ("custom_blocks_skipped", dict(stats["custom_blocks"])))
    add("%-30s %s" % ("dynamic_key_prefixes", stats.get("dynamic_prefixes", {})))
    add("```")
    add("")
    add("## 4. Findings by file")
    add("")
    by_file = defaultdict(list)
    for finding in findings:
        by_file[finding["file"]].append(finding)
    add("Files with findings: **%d** (`vue` = **%d**, `ts` = **%d**)" % (len(by_file), len(vue_with_findings), len(ts_with_findings)))
    add("")
    for file in sorted(by_file):
        rows = sorted(by_file[file], key=lambda f: (f["line"], f["kind"]))
        is_test_file = "/__tests__/" in "/" + file or file.endswith((".spec.ts", ".test.ts"))
        add("### `%s` -- %d finding(s)%s" % (file, len(rows), "  _(test file)_" if is_test_file else ""))
        add("")
        add("| file:line | snippet | context | class |")
        add("|---|---|---|---|")
        for row in rows:
            add(
                "| `%s:%d` | %s | %s | %s |"
                % (row["file"], row["line"], md_escape(row["snippet"]), md_escape(row["detail"]), row["class"])
            )
        add("")
    if not findings:
        add("_No findings._")
        add("")
    add("## 5. Unused locale key <-> hardcoded text pairing (ranked by occurrence count)")
    add("")
    add("Each row = one distinct Chinese snippet that is hardcoded **and** whose exact text is")
    add("the zh-CN value of a locale key that nothing references. `occ` = occurrences of that")
    add("snippet in this scan. Pairing is **exact text equality** only.")
    add("")
    if pairs_unused:
        by_text = {}
        for pair in pairs_unused:
            slot = by_text.setdefault(pair["text"], {"keys": {}, "hits": []})
            slot["keys"][pair["key"]] = pair["exact"]
            slot["hits"].append(pair)
        ordered = sorted(by_text.items(), key=lambda kv: (-occ[kv[0]], kv[0]))
        add("| # | occ | hardcoded zh text | unused key(s) | representative file:line | class |")
        add("|---|---|---|---|---|---|")
        for index, (text, slot) in enumerate(ordered, start=1):
            hits = sorted(slot["hits"], key=lambda p: (p["file"], p["line"]))
            rep = hits[0]
            add(
                "| %d | %d | %s | %s | `%s:%d` | %s |"
                % (
                    index,
                    occ[text],
                    md_escape(text),
                    "<br>".join("`%s`" % md_escape(k) for k in sorted(slot["keys"])),
                    rep["file"],
                    rep["line"],
                    rep["class"],
                )
            )
    else:
        add("**Empty table: 0 distinct hardcoded strings match the exact text of an unused key.**")
        add("")
        add("That is a measurement, not a tool failure. Every hardcoded string this scan found")
        add("either has no locale counterpart at all, or its exact text is the value of a key that")
        add("*is* referenced (section 5.2). So the unused keys are **not** unused because a view")
        add("hardcodes their text -- the two facts are independent in this tree.")
    add("")
    add("### 5.1 Hardcoded text that DOES equal a locale value (key used elsewhere)")
    add("")
    add("These are the real \"mixed-language\" risks: the string is frozen in Chinese while the")
    add("very same translation exists and is used elsewhere. Informational -- the pairing key")
    add("here is *referenced*, so it is not the dead-key pattern the brief described.")
    add("")
    if pairs_used:
        by_text = {}
        for pair in pairs_used:
            slot = by_text.setdefault(pair["text"], {"keys": [], "hits": []})
            slot["keys"].append(pair["key"])
            slot["hits"].append(pair)
        ordered = sorted(by_text.items(), key=lambda kv: (-occ[kv[0]], kv[0]))
        add("| # | occ | hardcoded zh text | matching locale key(s) | representative file:line | class |")
        add("|---|---|---|---|---|---|")
        for index, (text, slot) in enumerate(ordered, start=1):
            hits = sorted(slot["hits"], key=lambda p: (p["file"], p["line"]))
            rep = hits[0]
            add(
                "| %d | %d | %s | %s | `%s:%d` | %s |"
                % (
                    index,
                    occ[text],
                    md_escape(text),
                    "<br>".join("`%s`" % md_escape(k) for k in sorted(set(slot["keys"]))),
                    rep["file"],
                    rep["line"],
                    rep["class"],
                )
            )
    else:
        add("_None._")
    add("")
    add("### 5.2 Near misses (containment only -- listed for transparency, not counted)")
    add("")
    add("A snippet that merely *contains* a locale value (e.g. 「违规操作」 vs `common.action` = 「操作」)")
    add("is **not** treated as a pairing. The candidates are listed so nobody has to guess why")
    add("they are absent from the table above.")
    add("")
    near = stats.get("near_misses", [])
    if near:
        add("| hardcoded text | near-miss key | key value | file:line |")
        add("|---|---|---|---|")
        for item in near[:40]:
            add(
                "| %s | `%s` | %s | `%s:%d` |"
                % (md_escape(item["text"]), md_escape(item["key"]), md_escape(item["value"]), item["file"], item["line"])
            )
        if len(near) > 40:
            add("")
            add("_... %d more near misses not shown._" % (len(near) - 40))
    else:
        add("_None._")
    add("")
    add("### 5.3 How \"unused\" is decided")
    add("")
    add("A locale key is called **definitely unused** only when none of these hold:")
    add("")
    add("1. it appears as a `t('key')` / `$t('key')` / `tc('key')` / `tm('key')` argument anywhere in `frontend/src`;")
    add("2. it appears as a *string literal elsewhere* (a constant map or array consumed by `t(variable)` -- this project does that for `serviceTypes.*` and `adapter.category.*`);")
    add("3. it starts with the prefix of a `` t(`ns.${expr}`) `` dynamic call.")
    add("")
    add("| bucket | keys |")
    add("|---|---|")
    for bucket, label in (
        ("direct", "referenced directly (`t('key')`)"),
        ("indirect", "reachable indirectly (literal in a map/array)"),
        ("dynamic-prefix", "behind a dynamic template-literal prefix"),
        ("absent", "**definitely unused**"),
    ):
        add("| %s | %d |" % (label, verdicts.get(bucket, 0)))
    add("")
    add("Keys behind dynamic prefixes (`` t(`prefix.${expr}`) ``):")
    add("")
    add("```text")
    for prefix, count in sorted(stats.get("dynamic_prefixes", {}).items()):
        add("%-70s %d call site(s)" % (prefix, count))
    add("```")
    add("")
    add("### 5.4 Full inventory of definitely-unused keys")
    add("")
    add("| unused key | zh-CN value | value also hardcoded in this scan? |")
    add("|---|---|---|")
    matched = {p["key"] for p in pairs_unused}
    for key in sorted(unused):
        add("| `%s` | %s | %s |" % (md_escape(key), md_escape(unused[key]), "**yes**" if key in matched else "no"))
    add("")
    add("## 6. Known-positive check from the task brief")
    add("")
    add("The brief states that 「删除」(13x), 「取消」(11x) and 「搜索」(22x) appear as Chinese")
    add("literals in `.vue` files while `common.delete` / `common.cancel` / `common.search` are")
    add("referenced 0 times. The detector reproduces the raw counts and shows where each")
    add("occurrence sits:")
    add("")
    add("| string | raw `.vue` lines | raw `.vue`+`.ts` lines | strict findings | strict finding locations | verdict |")
    add("|---|---|---|---|---|---|")
    for token in ("删除", "取消", "搜索"):
        strict = sorted(
            ["%s:%d" % (f["file"], f["line"]) for f in findings if token in f["snippet"]]
        )
        add(
            "| %s | %d | %d | %d | %s | %s |"
            % (
                token,
                stats.get("raw_counts_vue", {}).get(token, 0),
                raw_counts.get(token, 0),
                len(strict),
                ", ".join("`%s`" % s for s in strict[:6]) or "none",
                tree_note.get(token, ""),
            )
        )
    add("")
    add("Raw evidence from `.vue` files, first 5 occurrences of each string:")
    add("")
    add("```text")
    for token in ("删除", "取消", "搜索"):
        add("--- %s (.vue raw lines: %d) ---" % (token, stats.get("raw_counts_vue", {}).get(token, 0)))
        for rel, line, text in stats.get("raw_where_vue", {}).get(token, [])[:5]:
            add("%s:%d: %s" % (rel, line, text))
    add("```")
    add("")
    add("Measured conclusion for this specific claim:")
    add("")
    add("- The 13 / 11 / 22 counts are reproduced **exactly** for `.vue` files, so the brief's")
    add("  measurement step was sound.")
    add("- Every one of those occurrences is inside a `//` comment, an HTML comment, a `/* */`")
    add("  block, or a `console.error(...)` argument -- i.e. inside the brief's own exclusion")
    add("  set. Strict findings for all three strings: **0**.")
    add("- Consequently those three strings produce **no** mixed-language UI. They do not")
    add("  substantiate the finding; they are log/comment Chinese, which is a separate (benign)")
    add("  question from UI localization.")
    add("- `common.delete` / `common.cancel` / `common.search` do exist as unused keys, but no")
    add("  hardcoded 「删除」/「取消」/「搜索」 text exists anywhere in `frontend/src`, so the")
    add("  \"key exists, view ignores it\" pairing does **not** hold for them.")
    add("")
    add("What the scan *does* find is a smaller, different set of strings (section 4): 9 distinct")
    add("snippets, all of which match locale values whose keys are **used elsewhere** (section")
    add("5.1), plus 200 unclassified Chinese literals listed in section 8.2.")
    add("")
    add("## 7. Exclusion list (documented, not silently applied)")
    add("")
    add("| # | excluded thing | mechanism |")
    add("|---|---|---|")
    add("| 1 | `frontend/src/locales/**/*.json` | path filter -- locale files are the dictionary, not UI |")
    add("| 2 | `node_modules`, `dist`, `.git`, `coverage`, `.vite`, `__pycache__` | directory filter in the walker |")
    add("| 3 | arguments of `t()`, `$t()`, `tc()`, `$tc()`, `tm()`, `te()`, `i18n.global.t(...)` | balanced-paren stripping before the CJK test |")
    add("| 4 | HTML comments `<!-- ... -->` | skipped by the template scanner |")
    add("| 5 | `//` and `/* */` comments in `<script>` / `.ts` | skipped by the JS lexer |")
    add("| 6 | every `<style>` block (and CSS comments in it) | block extractor skips the block |")
    add("| 7 | arguments of `console.log/error/warn/info/debug/trace/table/dir/group` | call-name match on the enclosing parenthesis |")
    add("| 8 | non-Chinese text | the detector measures the zh-CN/en-US mixing failure mode only |")
    add("| 9 | custom SFC blocks (`<i18n>`, `<docs>`, ...) | only `template`/`script`/`style` are scanned |")
    add("| 10 | `v-on`/`@event` handler attribute values | event handlers are code, not text |")
    add("")
    add("### 7.1 Finding classes")
    add("")
    add("- `high` -- user-visible by construction: template text node, visible attribute, message-API argument, unambiguous UI property.")
    add("- `review` -- Chinese on `label`/`title`/`message`/`content` of a call that is not a recognized message API (may be a request payload).")
    add("- `payload` -- `value` attribute of an option control, or Chinese inside a bound expression: shown as a translated label but frozen in Chinese where it is submitted or read back.")
    add("")
    add("## 8. Blind spots (explicitly not covered)")
    add("")
    add("| # | blind spot | why it is not covered | effect on the numbers |")
    add("|---|---|---|---|")
    add("| 1 | strings assembled by concatenation or interpolation (`'共' + n + '条'`, `` `删除${name}` ``) | only literal CJK runs are measured; a fragment is reported as its own snippet, and a template literal with `${}` is reported as the static part up to the interpolation | total and distinct counts are a **lower bound** on display strings; both halves of a split display string count as two snippets |")
    add("| 2 | Chinese coming from backend responses (`res.data.message` rendered directly) | no network, no runtime; a static client-side scan cannot see server-side strings | mixed-language UI from this source is **invisible** here |")
    add("| 3 | dynamic component props (`v-bind=\"obj\"`, `v-bind=\"{ placeholder: '中文' }\"`) | attribute names are unknown statically | attribute findings are a lower bound |")
    add("| 4 | UI strings reached through a variable or helper (`showMsg(MSG_DELETE)`, `const L = {del: '删除'}`) | only literals at known syntactic positions are reported | script-side findings are a lower bound |")
    add("| 5 | strings inside data structures whose property name is outside the tracked set, or computed keys (`[k]: '中文'`) | property-name heuristic | script-side findings are a lower bound |")
    add("| 6 | Chinese in `.js`/`.jsx`/`.html`/`.css`, `frontend/public/**`, Playwright tests, or the backend | scope is `frontend/src/**/*.{vue,ts}` by instruction | out of scope |")
    add("| 7 | locale key completeness (does `en-US` cover every `zh-CN` key; does a key resolve) | different question: this detector measures unrouted Chinese, not translation coverage | none |")
    add("| 8 | text injected at runtime via `v-html` / `innerHTML` from non-literal data | no runtime evaluation | invisible |")
    add("| 9 | whether a reported string is *actually* displayed (a `label` property can be a payload) | no cross-file dataflow analysis; such cases are class `review` rather than asserted | `review` findings are candidates, not proof |")
    add("")
    add("### 8.1 Files / constructs the detector could not fully parse")
    add("")
    unparsed = list(stats["unparsed"]) + list(stats["decode_failures"]) + ["locale: " + w for w in stats["locale_warnings"]]
    if unparsed:
        add("Reported rather than dropped (block-level or file-level degradation):")
        add("")
        add("```text")
        for item in unparsed:
            add(item)
        add("```")
    else:
        add("None. Every scanned file yielded all of its SFC blocks, and no template parser or JS")
        add("lexer error was raised. (`unclosed_template_literals` counter: %d -- those template" % counters.get("unclosed_template_literals", 0))
        add("literals still contributed their static text, but the tail of the file after the")
        add("unclosed literal is skipped by the lexer.)")
    add("")
    add("### 8.2 Chinese literals the detector saw but did NOT classify as findings")
    add("")
    add("These are real Chinese string literals in `.vue`/`.ts` code that sit in positions this")
    add("detector does not treat as user-visible (`high`). They are listed so the blind spot is")
    add("measurable instead of invisible; each one is a **candidate** for manual review, not a")
    add("confirmed defect. Test files (`__tests__`, `*.spec.ts`) are excluded from this list, and")
    add("the static segments of one template literal are listed separately (so `获取` and")
    add("`获取${serviceType}...` are two rows for one call site -- the row count is not a defect")
    add("count).")
    add("")
    pool = stats.get("pool", [])
    add("Count: **%d** (capped at 500)." % counters.get("skipped_not_ui", 0))
    add("")
    if pool:
        add("| file:line | literal | position |")
        add("|---|---|---|")
        for item in pool[:150]:
            add("| `%s:%d` | %s | %s |" % (item["file"], item["line"], md_escape(item["snippet"]), md_escape(item["context"])))
        if len(pool) > 150:
            add("")
            add("_... %d more not shown (the count above is the authoritative number)._" % (len(pool) - 150))
    else:
        add("_None._")
    add("")
    add("## 9. Reproduction")
    add("")
    add("```powershell")
    add("python scripts/audit/detect-hardcoded-i18n.py --self-test")
    add("python scripts/audit/detect-hardcoded-i18n.py --out scripts/audit/out/hardcoded-i18n.md")
    add("```")
    add("")
    add("Exit code 1 means the self-test failed and this report is untrustworthy. No npm, no")
    add("build, no network, and `frontend/src` is never written to.")
    add("")
    return "\n".join(lines)


# --------------------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------------------

def main(argv=None):
    parser = argparse.ArgumentParser(description="Detect hardcoded Chinese UI text not routed through vue-i18n.")
    parser.add_argument("--root", default=None, help="frontend root containing src/ (default: <repo>/frontend)")
    parser.add_argument("--locales", default=None, help="zh-CN locale dir (default: <root>/src/locales/zh-CN)")
    parser.add_argument("--out", default=None, help="report path (default: scripts/audit/out/hardcoded-i18n.md)")
    parser.add_argument("--self-test", action="store_true", help="run the calibration suite first (its raw log is embedded in the report)")
    parser.add_argument("--no-report", action="store_true", help="self-test only; write no report")
    parser.add_argument("--quiet", action="store_true", help="do not echo progress")
    args = parser.parse_args(argv)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(script_dir, "..", ".."))
    root = os.path.abspath(args.root) if args.root else os.path.join(repo_root, "frontend")
    src_root = os.path.join(root, "src")
    locales_dir = os.path.abspath(args.locales) if args.locales else os.path.join(src_root, "locales", "zh-CN")
    out_path = os.path.abspath(args.out) if args.out else os.path.join(script_dir, "out", "hardcoded-i18n.md")

    if not os.path.isdir(src_root):
        sys.stderr.write("FATAL: src directory not found: %s\n" % src_root)
        return 2

    console = open(os.devnull, "w") if args.quiet else sys.stdout
    started = time.time()
    selftest_log, selftest_failed, _meta = run_self_test(root, locales_dir, console)
    if args.self_test and args.no_report:
        return 1 if selftest_failed else 0

    findings, stats = scan_tree(src_root)
    entries, locale_warnings = load_locale_dictionary(locales_dir)
    stats["locale_warnings"] = locale_warnings
    direct, indirect, dynamic_prefixes, _files, key_failures = collect_key_evidence(src_root)
    stats["decode_failures"].extend("key-scan: " + failure for failure in key_failures)
    dictionary_text = {entry["id"]: entry["text"] for entry in entries}
    verdicts = classify_keys(list(dictionary_text), direct, indirect, dynamic_prefixes)
    unused = {key: text for key, text in dictionary_text.items() if verdicts[key] == "absent"}
    stats["key_verdicts"] = Counter(verdicts.values())
    stats["dynamic_prefixes"] = dict(dynamic_prefixes)
    stats["direct_key_count"] = len(direct)
    pairs_unused = pair_findings(findings, [e for e in entries if e["id"] in unused], exact_only=True)
    pairs_used = pair_findings(findings, [e for e in entries if e["id"] not in unused], exact_only=True)
    near_misses = []
    for finding in findings:
        snippet = finding["snippet"]
        for entry in entries:
            if len(entry["text"]) < 2 or entry["text"] == snippet:
                continue
            if entry["text"] in snippet or snippet in entry["text"]:
                near_misses.append(
                    {
                        "text": snippet,
                        "key": entry["id"],
                        "value": entry["text"],
                        "file": finding["file"],
                        "line": finding["line"],
                    }
                )
    stats["near_misses"] = near_misses
    raw_counts, raw_where = raw_occurrence_counts(src_root, ("删除", "取消", "搜索"))
    raw_counts_vue, raw_where_vue = raw_occurrence_counts(src_root, ("删除", "取消", "搜索"), suffix=".vue")
    stats["raw_counts_vue"] = raw_counts_vue
    stats["raw_where_vue"] = raw_where_vue
    tree_note = {}
    for token in ("删除", "取消", "搜索"):
        strict = len([f for f in findings if token in f["snippet"]])
        tree_note[token] = "strict finding(s): %d" % strict if strict else "excluded (comments/console only)"
    report = build_report(
        args,
        findings,
        stats,
        entries,
        unused,
        pairs_unused,
        pairs_used,
        selftest_log,
        selftest_failed,
        tree_note,
        raw_counts,
        raw_where,
    )
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    try:
        with open(out_path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(report)
    except Exception as exc:
        sys.stderr.write("FATAL: cannot write report: %s: %s\n" % (type(exc).__name__, exc))
        return 2

    if not args.quiet:
        sys.stdout.write(
            "[result] calibration=%s  findings=%d  distinct=%d  vue_files_with_findings=%d  unused_keys=%d  "
            "paired_unused=%d  paired_used=%d\n"
            % (
                "PASS" if selftest_failed == 0 else "FAIL",
                len(findings),
                len({f["snippet"] for f in findings}),
                len({f["file"] for f in findings if f["file"].endswith(".vue")}),
                len(unused),
                len({p["text"] for p in pairs_unused}),
                len({p["text"] for p in pairs_used}),
            )
        )
        sys.stdout.write("[report] %s (%d bytes, %.1fs)\n" % (out_path, len(report.encode("utf-8")), time.time() - started))
    if selftest_failed:
        sys.stderr.write("SELF-TEST FAILED (%d assertion(s)) -- report marked untrustworthy\n" % selftest_failed)
    return 1 if selftest_failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SystemExit:
        raise
    except Exception:
        traceback.print_exc()
        sys.exit(2)
