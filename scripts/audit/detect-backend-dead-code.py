#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
detect-backend-dead-code.py
===========================

A reusable, self-calibrating *candidate* detector for dead code in the JAiRouter
Spring Boot (WebFlux) backend.

Python 3 standard library only.  It never invokes Maven/Gradle, never writes
anything under ``src/main/java`` or ``src/test/java`` and never modifies the
repository: it only reads source files and writes its report.

Reported candidate classes
--------------------------
1. Zero-reference top-level types
   Classes / interfaces / enums / records declared in ``src/main/java`` whose
   name does not appear outside their own declaring file (java corpus +
   resource files are both consulted; see the report for exact scopes).
2. Interfaces with no implementor
   No ``implements <Name>`` / ``extends <Name>`` anywhere in ``src/main``.
3. Unused private methods
   ``private`` methods with no other occurrence of their name in the same file.
4. Unused private / package-private fields
   Field name never appears again in the same file.  Fields annotated
   ``@Value`` / ``@Autowired`` / ``@Resource`` / ``@Inject`` /
   ``@PersistenceContext`` and fields declared ``static final`` are excluded
   (and counted in the report).
5. Public methods with no call site (candidates, not verdicts)
   5a: public methods of Spring stereotypes
       (``@Service`` ``@Component`` ``@Repository`` ``@RestController``
       ``@Controller`` ``@Configuration``) whose name appears nowhere else in
       the scanned corpus.
   5b: the same check for public methods of non-stereotype classes
       (this is where pure utility classes such as
       ``SecurityUtils.generateSecureRandomString`` land).
   5c: collision-tolerant view for Spring stereotypes: methods with no
       ``.name(`` / ``::name`` / static-import occurrence anywhere, i.e. no
       call-site-shaped reference even though the *name* is declared elsewhere.

Calibration
-----------
``--self-test`` builds a synthetic tree in a temporary directory (created and
removed by the script itself, never inside the repository), runs the very same
analysis code against it, asserts every category fires, asserts negative
controls stay silent, prints ``PASS``/``FAIL`` per assertion and exits
non-zero if any assertion fails.

Usage
-----
    python scripts/audit/detect-backend-dead-code.py                 # write report
    python scripts/audit/detect-backend-dead-code.py --self-test     # calibrate
    python scripts/audit/detect-backend-dead-code.py --report out.md --json out.json
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import shutil
import sys
import tempfile
from collections import Counter
from pathlib import Path

# --------------------------------------------------------------------------
# constants
# --------------------------------------------------------------------------

EXCLUDED_PATH_PARTS = {"target", "node_modules", ".git"}

TYPE_KEYWORDS = ("class", "interface", "enum", "record")

MODIFIER_KEYWORDS = {
    "public", "protected", "private", "static", "final", "abstract",
    "native", "synchronized", "transient", "volatile", "default",
    "strictfp", "sealed", "non-sealed",
}

# statements starting with one of these can never be a type/method declaration
# (a stray `)` / `]` may precede a declaration when a multi-line annotation
# array was merged back into the statement, so those are not listed here)
CONTROL_STARTERS = {
    "if", "for", "while", "switch", "catch", "synchronized", "return", "new",
    "do", "else", "try", "assert", "throw", "case", "default", "break",
    "continue", "yield", "}", ";", "extends", "implements",
}

NOT_A_METHOD_NAME = {
    "if", "for", "while", "switch", "catch", "return", "new", "do", "else",
    "try", "assert", "throw", "case", "default", "break", "continue", "yield",
    "this", "super", "instanceof", "class", "interface", "enum", "record",
    "void", "int", "long", "double", "float", "boolean", "char", "byte",
    "short", "null", "true", "false", "synchronized",
}

BEAN_ANNOTATIONS = {
    "Service", "Component", "Repository", "RestController", "Controller",
    "Configuration",
}

EXCLUDED_FIELD_ANNOTATIONS = {
    "Value", "Autowired", "Resource", "Inject", "PersistenceContext",
}

# annotations that make a member framework-invoked rather than source-called
FRAMEWORK_ANNOTATIONS = {
    "Override", "Bean", "EventListener", "Scheduled", "PostConstruct",
    "PreDestroy", "Lookup", "InitBinder", "ModelAttribute", "ExceptionHandler",
    "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
    "PatchMapping", "MessageMapping", "StreamingResponseBody", "Async",
    "Transactional", "Cacheable", "CacheEvict", "CachePut", "Query", "SqlUpdate",
    "Recover", "Retryable", "KafkaListener", "RabbitListener", "JmsListener",
    "ConditionalOnProperty", "ConditionalOnMissingBean", "ConditionalOnBean",
    "PrePersist", "PostPersist", "PreUpdate", "PostUpdate", "PreRemove",
    "PostRemove", "PostLoad", "Converter", "JsonCreator", "JsonValue",
    "JsonAnyGetter", "JsonAnySetter", "JsonGetter", "JsonSetter",
    "JsonPropertyOrder", "ToString", "EqualsAndHashCode",
}

# annotations that generate accessors / builders referencing field names
LOMBOK_ACCESSOR_ANNOTATIONS = {"Data", "Getter", "Setter", "Builder", "Value"}

# interfaces whose implementation is generated at runtime by Spring Data
SPRING_DATA_REPOSITORY_BASES = {
    "Repository", "CrudRepository", "PagingAndSortingRepository",
    "JpaRepository", "JpaSpecificationExecutor", "ReactiveCrudRepository",
    "ReactiveSortingRepository", "ReactiveQueryByExampleExecutor",
    "R2dbcRepository", "MongoRepository", "ReactiveMongoRepository",
    "ElasticsearchRepository", "ReactiveElasticsearchRepository",
    "CassandraRepository", "ReactiveCassandraRepository",
}

RESOURCE_SUFFIXES = {
    ".yml", ".yaml", ".properties", ".json", ".xml", ".factories", ".txt",
    ".sql", ".md", ".conf", ".imports",
}

IDENT_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")
NUM_RE = re.compile(r"[0-9][0-9A-Za-z_.]*")

BEAN_SHAPED_RE = re.compile(r"^(get|set|is)[A-Z0-9_].*$")
BEAN_SHAPED_EXTRA = {
    "toString", "hashCode", "equals", "main", "run", "call", "apply", "accept",
    "test", "iterator", "compareTo", "afterPropertiesSet", "close", "init",
}


# --------------------------------------------------------------------------
# lexer
# --------------------------------------------------------------------------

class Tok:
    __slots__ = ("v", "kind", "line")

    def __init__(self, v: str, kind: str, line: int):
        self.v = v
        self.kind = kind
        self.line = line

    def __repr__(self):  # pragma: no cover - debugging aid
        return f"Tok({self.v!r},{self.kind},{self.line})"


def is_word(v: str) -> bool:
    return bool(v) and (v[0].isalpha() or v[0] in "_$")


def lex(text: str):
    """Tokenize java source, skipping comments and string/char/text literals.

    Literal contents are dropped (not turned into tokens), which is why a
    method name that only occurs inside a string (reflection, SpEL, SQL) is
    *not* counted as a reference - a documented blind spot of this tool.
    """
    toks = []
    i = 0
    n = len(text)
    line = 1
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
        elif c in " \t\r\f\v":
            i += 1
        elif c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j < 0:
                break
            line += text.count("\n", i, j + 2)
            i = j + 2
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            line += text.count("\n", i, j)
            i = j
        elif c == '"' or c == "'":
            q = c
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == q:
                    j += 1
                    break
                if text[j] == "\n":
                    break
                j += 1
            line += text.count("\n", i, j)
            i = j
        else:
            m = IDENT_RE.match(text, i)
            if m:
                toks.append(Tok(sys.intern(m.group(0)), "word", line))
                i = m.end()
                continue
            if text.startswith("::", i):
                toks.append(Tok("::", "sym", line))
                i += 2
                continue
            if text.startswith("->", i):
                toks.append(Tok("->", "sym", line))
                i += 2
                continue
            if text.startswith("...", i):
                toks.append(Tok("...", "sym", line))
                i += 3
                continue
            if c.isdigit():
                m2 = NUM_RE.match(text, i)
                toks.append(Tok(m2.group(0), "num", line))
                i = m2.end()
                continue
            toks.append(Tok(c, "sym", line))
            i += 1
    return toks


# --------------------------------------------------------------------------
# declaration model
# --------------------------------------------------------------------------

class TypeDecl:
    __slots__ = ("name", "tkind", "line", "path", "rel", "top_level",
                 "modifiers", "annotations", "owner", "supertypes")

    def __init__(self, **kw):
        for k in self.__slots__:
            setattr(self, k, kw.get(k))


class MethodDecl:
    __slots__ = ("name", "line", "path", "rel", "owner", "owner_kind",
                 "modifiers", "annotations", "has_body", "is_constructor",
                 "abstract")

    def __init__(self, **kw):
        for k in self.__slots__:
            setattr(self, k, kw.get(k))


class FieldDecl:
    __slots__ = ("name", "line", "path", "rel", "owner", "owner_kind",
                 "modifiers", "annotations")

    def __init__(self, **kw):
        for k in self.__slots__:
            setattr(self, k, kw.get(k))


class FileInfo:
    __slots__ = ("path", "rel", "pkg", "types", "methods", "fields",
                 "ident", "ident_all", "prose", "prose_all", "dot_calls",
                 "supertypes", "anonymous", "decl_method_counts",
                 "decl_field_counts", "decl_type_counts", "decl_name_counts",
                 "is_main", "lines")

    def __init__(self):
        self.path = None
        self.rel = None
        self.pkg = ""
        self.types = []
        self.methods = []
        self.fields = []
        self.ident = {}
        self.ident_all = None
        self.prose = {}
        self.prose_all = None
        self.dot_calls = Counter()
        self.supertypes = set()
        self.anonymous = set()
        self.decl_method_counts = Counter()
        self.decl_field_counts = Counter()
        self.decl_type_counts = Counter()
        self.decl_name_counts = Counter()
        self.is_main = True
        self.lines = 0


# --------------------------------------------------------------------------
# parser
# --------------------------------------------------------------------------

def strip_leading_annotations(toks):
    """Return (annotation dotted names, remaining tokens, unbalanced).

    ``unbalanced`` is True when the statement ends in the middle of an
    annotation's parentheses, which happens for multi-line annotation arrays
    such as ``@Table(name = "x", indexes = { @Index(...), ... })``.  The
    caller then keeps the statement open instead of guessing a declaration.
    """
    anns = []
    unbalanced = False
    i = 0
    n = len(toks)
    while i < n and toks[i].v == "@":
        if i + 1 < n and toks[i + 1].v == "interface":
            break  # `@interface` is an annotation *type* declaration
        j = i + 1
        parts = []
        if j < n and is_word(toks[j].v):
            parts.append(toks[j].v)
            j += 1
            while j + 1 < n and toks[j].v == "." and is_word(toks[j + 1].v):
                parts.append(toks[j + 1].v)
                j += 2
        if j < n and toks[j].v == "(":
            depth = 0
            while j < n:
                if toks[j].v == "(":
                    depth += 1
                elif toks[j].v == ")":
                    depth -= 1
                    if depth == 0:
                        j += 1
                        break
                j += 1
            if depth != 0:
                unbalanced = True
        if parts:
            anns.append(".".join(parts))
        i = j
    return anns, list(toks[i:]), unbalanced


def _scan_depth(vals):
    """Yield (index, value, at_top_level) for a token-value list."""
    paren = brack = brace = angle = 0
    for idx, v in enumerate(vals):
        yield idx, v, (paren == 0 and brack == 0 and brace == 0 and angle == 0)
        if v == "(":
            paren += 1
        elif v == ")":
            paren = max(0, paren - 1)
        elif v == "[":
            brack += 1
        elif v == "]":
            brack = max(0, brack - 1)
        elif v == "{":
            brace += 1
        elif v == "}":
            brace = max(0, brace - 1)
        elif v == "<":
            angle += 1
        elif v == ">":
            angle = max(0, angle - 1)


def _has_top_level(vals, target):
    for _, v, at_top in _scan_depth(vals):
        if at_top and v == target:
            return True
    return False


def collect_supertypes(toks):
    """Names appearing in `extends` / `implements` clauses of one declaration."""
    out = []
    vals = [t.v for t in toks]
    for i, v in enumerate(vals):
        if v not in ("implements", "extends"):
            continue
        j = i + 1
        depth = 0
        expect = True
        while j < len(vals):
            w = vals[j]
            if w in ("{", ";", "("):
                break
            if w == "implements":
                expect = True
                j += 1
                continue
            if w == "<":
                depth += 1
            elif w == ">":
                depth = max(0, depth - 1)
            elif w == "," and depth == 0:
                expect = True
            elif w != "." and is_word(w) and depth == 0:
                if expect:
                    out.append(w)
                    expect = False
                elif out and out[-1] != w:
                    out.append(w)  # last segment of `implements Outer.Inner`
            j += 1
    return out


def accessor_variants(name):
    cap = name[:1].upper() + name[1:]
    return ["get" + cap, "set" + cap, "is" + cap]


def find_type_keyword(rest):
    """Index of a type keyword that starts a type declaration, or None."""
    for idx, t in enumerate(rest):
        if t.v not in TYPE_KEYWORDS:
            continue
        if idx > 0 and rest[idx - 1].v == ".":
            continue  # `Foo.class`
        if idx + 1 >= len(rest) or not is_word(rest[idx + 1].v):
            continue  # `Foo.class` fallback / malformed
        return idx
    return None


def looks_like_method(rest):
    """Return (name, name_index, modifiers, is_constructor_unknown) or None."""
    vals = [t.v for t in rest]
    if not vals:
        return None
    if vals[0] in CONTROL_STARTERS:
        return None
    # `=` / `->` only disqualify a *top level* occurrence: parameter
    # annotations such as `@RequestParam(defaultValue = "1")` live inside the
    # parameter list and must not turn a method declaration into an initializer.
    if _has_top_level(vals, "=") or _has_top_level(vals, "->") or ";" in vals:
        return None
    # last closing paren of the parameter list
    k = len(vals) - 1
    while k >= 0 and vals[k] != ")":
        k -= 1
    if k < 0:
        return None
    tail = vals[k + 1:]
    for t in tail:
        if not (is_word(t) or t in ".,<>[]?"):
            return None
    depth = 0
    m = -1
    for i in range(k, -1, -1):
        if vals[i] == ")":
            depth += 1
        elif vals[i] == "(":
            depth -= 1
            if depth == 0:
                m = i
                break
    if m <= 0:
        return None
    name_tok = rest[m - 1]
    if not is_word(name_tok.v) or name_tok.v in NOT_A_METHOD_NAME:
        return None
    prefix = vals[:m - 1]
    if _has_top_level(prefix, "="):
        return None
    if prefix and prefix[-1] in ("new", "return", "throw"):
        return None
    mods = {v for v in prefix if v in MODIFIER_KEYWORDS}
    return name_tok.v, m - 1, mods


def split_top_level_commas(toks):
    parts = []
    cur = []
    paren = brack = brace = angle = 0
    for t in toks:
        v = t.v
        if v == "(":
            paren += 1
        elif v == ")":
            paren = max(0, paren - 1)
        elif v == "[":
            brack += 1
        elif v == "]":
            brack = max(0, brack - 1)
        elif v == "{":
            brace += 1
        elif v == "}":
            brace = max(0, brace - 1)
        elif v == "<":
            angle += 1
        elif v == ">":
            angle = max(0, angle - 1)
        if v == "," and paren == 0 and brack == 0 and brace == 0 and angle == 0:
            parts.append(cur)
            cur = []
        else:
            cur.append(t)
    parts.append(cur)
    return parts


def cut_at_top_level_equals(toks):
    paren = brack = brace = angle = 0
    for i, t in enumerate(toks):
        v = t.v
        if v == "(":
            paren += 1
        elif v == ")":
            paren = max(0, paren - 1)
        elif v == "[":
            brack += 1
        elif v == "]":
            brack = max(0, brack - 1)
        elif v == "{":
            brace += 1
        elif v == "}":
            brace = max(0, brace - 1)
        elif v == "<":
            angle += 1
        elif v == ">":
            angle = max(0, angle - 1)
        elif v == "=" and paren == 0 and brack == 0 and brace == 0 and angle == 0:
            return toks[:i], True
    return toks, False


def extract_fields(rest):
    """Return list of (name, line, modifiers) for a class-body statement."""
    out = []
    for part in split_top_level_commas(rest):
        if not part:
            continue
        head, _ = cut_at_top_level_equals(part)
        name_tok = None
        for t in reversed(head):
            if t.kind == "word" and t.v not in MODIFIER_KEYWORDS:
                name_tok = t
                break
        if name_tok is None:
            continue
        if name_tok.v in NOT_A_METHOD_NAME:
            continue
        mods = {t.v for t in head if t.v in MODIFIER_KEYWORDS}
        out.append((name_tok.v, name_tok.line, mods))
    return out


def parse_java(path: Path, rel: str, is_main: bool):
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:  # pragma: no cover
        raise RuntimeError(f"read error: {exc}")
    toks = lex(text)
    info = FileInfo()
    info.path = str(path)
    info.rel = rel
    info.is_main = is_main
    info.lines = text.count("\n") + (0 if text.endswith("\n") else 1)

    ident = Counter()
    dot_calls = Counter()
    for i, t in enumerate(toks):
        if t.kind == "word":
            ident[t.v] += 1
            if i > 0 and toks[i - 1].v in (".", "::"):
                dot_calls[t.v] += 1
            if t.v in ("implements", "extends"):
                j = i + 1
                depth = 0
                expect = True
                chain = []
                while j < len(toks):
                    v = toks[j].v
                    if v in ("{", ";"):
                        break
                    if v == "implements":
                        expect = True
                        chain = []
                        j += 1
                        continue
                    if v == "<":
                        depth += 1
                    elif v == ">":
                        depth = max(0, depth - 1)
                    elif v == "," and depth == 0:
                        expect = True
                        chain = []
                    elif v != "." and is_word(v) and depth == 0:
                        if expect:
                            chain = [v]
                            info.supertypes.add(v)
                            expect = False
                        elif chain:
                            # last segment of a dotted chain: `implements Outer.Inner`
                            chain.append(v)
                            info.supertypes.add(v)
                    j += 1
        elif t.v == "new" and i + 1 < len(toks) and is_word(toks[i + 1].v):
            j = i + 1
            while j < len(toks) and toks[j].v not in ("(", "{", ";", "="):
                j += 1
            if j < len(toks) and toks[j].v == "(":
                depth = 0
                while j < len(toks):
                    if toks[j].v == "(":
                        depth += 1
                    elif toks[j].v == ")":
                        depth -= 1
                        if depth == 0:
                            j += 1
                            break
                    j += 1
                if j < len(toks) and toks[j].v == "{":
                    info.anonymous.add(toks[i + 1].v)

    # ---- structural walk -------------------------------------------------
    stack = [{"kind": "root", "name": None, "tkind": None, "annotations": [],
              "carry": None}]
    stmt_start = 0
    i = 0
    n = len(toks)
    owners = []  # stack of type context dicts

    def nearest_owner():
        for ctx in reversed(stack):
            if ctx["kind"] == "type":
                return ctx
        return None

    def type_level():
        for ctx in reversed(stack):
            if ctx["kind"] == "arrayinit":
                continue
            return ctx["kind"] == "type"
        return False

    while i < n:
        v = toks[i].v
        if v == "{":
            stmt = toks[stmt_start:i]
            anns, rest, unbalanced = strip_leading_annotations(stmt)
            vals = [t.v for t in rest]
            parent = stack[-1]
            ctx = None
            top_assign = _has_top_level(vals, "=") or _has_top_level(vals, "->")
            if unbalanced:
                # still inside a multi-line annotation array: keep the current
                # statement open so the declaration that follows is seen whole
                ctx = {"kind": "arrayinit", "carry": stmt_start}
            elif (parent["kind"] == "type" and parent.get("tkind") == "enum"
                    and parent.get("enum_pending")):
                ctx = {"kind": "block", "carry": None}
            else:
                ti = find_type_keyword(rest)
                if ti is not None and vals and vals[0] not in CONTROL_STARTERS:
                    kw = rest[ti].v
                    name = rest[ti + 1].v
                    tkind = kw
                    if kw == "interface" and ti > 0 and rest[ti - 1].v == "@":
                        tkind = "annotation"
                    mods = {t.v for t in rest[:ti] if t.v in MODIFIER_KEYWORDS}
                    owner = nearest_owner()
                    td = TypeDecl(
                        name=name, tkind=tkind, line=rest[ti + 1].line,
                        path=str(path), rel=rel,
                        top_level=(owner is None),
                        modifiers=mods, annotations=anns,
                        owner=(owner["name"] if owner else None),
                        supertypes=collect_supertypes(rest[ti + 1:]),
                    )
                    info.types.append(td)
                    ctx = {"kind": "type", "name": name, "tkind": tkind,
                           "annotations": anns, "carry": None,
                           "enum_pending": (tkind == "enum")}
                else:
                    sig = looks_like_method(rest)
                    if sig is not None:
                        mname, _, mmods = sig
                        owner = nearest_owner()
                        decl = MethodDecl(
                            name=mname, line=rest[0].line, path=str(path),
                            rel=rel,
                            owner=(owner["name"] if owner else None),
                            owner_kind=(owner["tkind"] if owner else None),
                            modifiers=mmods, annotations=anns, has_body=True,
                            is_constructor=(owner is not None
                                            and mname == owner["name"]),
                            abstract=("abstract" in mmods),
                        )
                        info.methods.append(decl)
                        ctx = {"kind": "method", "carry": None}
                    elif vals == ["static"]:
                        ctx = {"kind": "block", "carry": None}
                    else:
                        transparent = type_level() and top_assign
                        if transparent:
                            ctx = {"kind": "arrayinit", "carry": stmt_start}
                        elif top_assign:
                            ctx = {"kind": "arrayinit", "carry": None}
                        else:
                            ctx = {"kind": "block", "carry": None}
            stack.append(ctx)
            stmt_start = i + 1
            i += 1
            continue

        if v == "}":
            if len(stack) > 1:
                ctx = stack.pop()
                if ctx.get("carry") is not None:
                    stmt_start = ctx["carry"]
                else:
                    stmt_start = i + 1
            i += 1
            continue

        if v == ";":
            cur = stack[-1]
            anns, rest, _unbalanced = strip_leading_annotations(toks[stmt_start:i])
            if (cur["kind"] == "type" and cur.get("tkind") == "enum"
                    and cur.get("enum_pending")):
                cur["enum_pending"] = False
            elif type_level() and rest:
                sig = looks_like_method(rest)
                owner = nearest_owner()
                if sig is not None:
                    mname, _, mmods = sig
                    info.methods.append(MethodDecl(
                        name=mname, line=rest[0].line, path=str(path), rel=rel,
                        owner=(owner["name"] if owner else None),
                        owner_kind=(owner["tkind"] if owner else None),
                        modifiers=mmods, annotations=anns, has_body=False,
                        is_constructor=(owner is not None
                                        and mname == owner["name"]),
                        abstract=True,
                    ))
                else:
                    for fname, fline, fmods in extract_fields(rest):
                        info.fields.append(FieldDecl(
                            name=fname, line=fline, path=str(path), rel=rel,
                            owner=(owner["name"] if owner else None),
                            owner_kind=(owner["tkind"] if owner else None),
                            modifiers=fmods, annotations=anns,
                        ))
            stmt_start = i + 1
            i += 1
            continue

        i += 1

    # ---- per-file counters limited to declared names ----------------------
    interest = set()
    for c in (info.types, info.methods, info.fields):
        for d in c:
            interest.add(d.name)
    info.ident_all = ident
    # identifiers that occur *only* inside comments / string literals:
    # javadoc `@link`, reflection names, SpEL, SQL, JSON keys ...
    raw = Counter(IDENT_RE.findall(text))
    prose_all = Counter({k: v for k, v in raw.items()
                         if v > ident.get(k, 0)})
    info.prose_all = prose_all
    info.prose = {k: prose_all[k] for k in interest if k in prose_all}
    info.ident = {k: ident[k] for k in interest if k in ident}
    info.dot_calls = dot_calls
    for d in info.types:
        info.decl_type_counts[d.name] += 1
    for d in info.methods:
        info.decl_method_counts[d.name] += 1
    for d in info.fields:
        info.decl_field_counts[d.name] += 1
    for c in (info.decl_type_counts, info.decl_method_counts,
              info.decl_field_counts):
        for k, val in c.items():
            info.decl_name_counts[k] += val
    return info


# --------------------------------------------------------------------------
# corpus scan
# --------------------------------------------------------------------------

def is_excluded(path: Path) -> bool:
    return any(part in EXCLUDED_PATH_PARTS for part in path.parts)


def java_files(root: Path):
    if not root.exists():
        return []
    return sorted(p for p in root.rglob("*.java")
                  if p.is_file() and not is_excluded(p))


def resource_files(dirs):
    out = []
    for d in dirs:
        if not d.exists():
            continue
        for p in sorted(d.rglob("*")):
            if p.is_file() and not is_excluded(p) and p.suffix.lower() in RESOURCE_SUFFIXES:
                out.append(p)
    return out


class Analysis:
    def __init__(self):
        self.main_files = []
        self.test_files = []
        self.resource_counts = Counter()
        self.resource_files = 0
        self.global_java = Counter()
        self.global_prose = Counter()
        self.global_dot_calls = Counter()
        self.global_method_decls = Counter()
        self.global_name_decls = Counter()
        self.parse_errors = []
        self.scope = {}

    @property
    def ref_files(self):
        return self.main_files + self.test_files


def scan(root: Path, main_src: str, test_src: str, resource_dirs):
    ana = Analysis()
    main_dir = root / main_src
    test_dir = root / test_src
    res_dirs = [root / r for r in resource_dirs]

    for p in java_files(main_dir):
        rel = p.relative_to(root).as_posix()
        try:
            fi = parse_java(p, rel, True)
        except Exception as exc:  # pragma: no cover
            ana.parse_errors.append((rel, repr(exc)))
            continue
        ana.main_files.append(fi)
    for p in java_files(test_dir):
        rel = p.relative_to(root).as_posix()
        try:
            fi = parse_java(p, rel, False)
        except Exception as exc:  # pragma: no cover
            ana.parse_errors.append((rel, repr(exc)))
            continue
        ana.test_files.append(fi)

    for fi in ana.ref_files:
        for k, v in fi.ident_all.items():
            ana.global_java[k] += v
        for k, v in fi.prose_all.items():
            ana.global_prose[k] += v
        for k, v in fi.dot_calls.items():
            ana.global_dot_calls[k] += v
        for k, v in fi.decl_method_counts.items():
            ana.global_method_decls[k] += v
        for k, v in fi.decl_name_counts.items():
            ana.global_name_decls[k] += v
        fi.ident_all = None      # release the full per-file index
        fi.prose_all = None
        fi.dot_calls = Counter()  # only the corpus-wide aggregate is needed

    rf = resource_files(res_dirs)
    ana.resource_files = len(rf)
    for p in rf:
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in IDENT_RE.finditer(text):
            ana.resource_counts[m.group(0)] += 1

    ana.scope = {
        "root": str(root),
        "main_dir": main_src,
        "test_dir": test_src,
        "resource_dirs": list(resource_dirs),
        "main_files": len(ana.main_files),
        "test_files": len(ana.test_files),
        "main_lines": sum(f.lines for f in ana.main_files),
        "test_lines": sum(f.lines for f in ana.test_files),
        "resource_files": ana.resource_files,
        "java_files": len(ana.ref_files),
        "identifiers": len(ana.global_java),
        "types": sum(len(f.types) for f in ana.main_files),
        "types_top": sum(1 for f in ana.main_files for t in f.types if t.top_level),
        "methods": sum(len(f.methods) for f in ana.main_files),
        "fields": sum(len(f.fields) for f in ana.main_files),
        "parse_errors": len(ana.parse_errors),
    }
    return ana


# --------------------------------------------------------------------------
# findings
# --------------------------------------------------------------------------

def _rel_line(d):
    return f"{d.rel}:{d.line}"


def _ann_note(anns):
    keep = [a.split(".")[-1] for a in anns]
    if not keep:
        return ""
    return "@" + " @".join(keep)


def analyze(ana: Analysis):
    f = {
        "cat1_plain": [],
        "cat1_annotated": [],
        "cat2_plain": [],
        "cat2_framework": [],
        "cat3_private_methods": [],
        "cat4_fields": [],
        "cat4b_lombok_fields": [],
        "cat5a_core": [],
        "cat5a_framework": [],
        "cat5b_core": [],
        "cat5b_framework": [],
        "cat5c_core": [],
        "cat5c_framework": [],
        "suppressed": Counter(),
    }

    # ---- 1. zero-reference top-level types -------------------------------
    for fi in ana.main_files:
        for t in fi.types:
            if not t.top_level:
                continue
            own = fi.ident.get(t.name, 0)
            java_refs = ana.global_java.get(t.name, 0) - own
            res_refs = ana.resource_counts.get(t.name, 0)
            if java_refs == 0 and res_refs == 0:
                row = {
                    "name": t.name, "kind": t.tkind, "loc": _rel_line(t),
                    "java_refs_outside_file": java_refs,
                    "resource_refs": res_refs,
                    "prose_refs": (ana.global_prose.get(t.name, 0)
                                   - fi.prose.get(t.name, 0)),
                    "annotations": _ann_note(t.annotations),
                }
                (f["cat1_annotated"] if t.annotations
                 else f["cat1_plain"]).append(row)
            elif java_refs == 0:
                f["suppressed"]["cat1_resource_only_refs"] += 1

    # ---- 2. interfaces with no implementor -------------------------------
    main_super = Counter()
    test_super = Counter()
    anon_all = Counter()
    for fi in ana.ref_files:
        for s in fi.supertypes:
            (main_super if fi.is_main else test_super)[s] += 1
        for s in fi.anonymous:
            anon_all[s] += 1
    for fi in ana.main_files:
        for t in fi.types:
            if t.tkind != "interface":
                continue
            name = t.name
            main_hits = main_super.get(name, 0)
            test_hits = test_super.get(name, 0)
            if main_hits > 0:
                continue
            anns = {a.split(".")[-1] for a in t.annotations}
            sd = sorted(set(t.supertypes) & SPRING_DATA_REPOSITORY_BASES)
            flags = []
            if sd:
                flags.append("spring-data(" + "|".join(sd) + ")")
            if "FunctionalInterface" in anns:
                flags.append("functional-interface")
            if anon_all.get(name, 0):
                flags.append("anonymous-impl")
            row = {
                "name": name, "loc": _rel_line(t),
                "top_level": t.top_level,
                "impl_hits_main": main_hits,
                "impl_hits_test": test_hits,
                "anonymous_new": anon_all.get(name, 0),
                "total_name_refs": ana.global_java.get(name, 0),
                "flags": " ".join(flags),
                "annotations": _ann_note(t.annotations),
            }
            (f["cat2_framework"] if flags else f["cat2_plain"]).append(row)

    # ---- 3. unused private methods ---------------------------------------
    for fi in ana.main_files:
        for m in fi.methods:
            if "private" not in m.modifiers or m.is_constructor:
                continue
            total = fi.ident.get(m.name, 0)
            decls = fi.decl_method_counts.get(m.name, 0)
            if total == decls:
                f["cat3_private_methods"].append({
                    "name": m.name, "owner": m.owner, "loc": _rel_line(m),
                    "refs_in_file": total, "decls_in_file": decls,
                    "repo_refs": ana.global_java.get(m.name, 0),
                    "prose_refs": fi.prose.get(m.name, 0),
                    "note": _ann_note(m.annotations),
                })

    # ---- 4. unused private / package-private fields -----------------------
    owner_ann_index = {}
    for fi in ana.main_files:
        for t in fi.types:
            owner_ann_index[(fi.rel, t.name)] = {
                a.split(".")[-1] for a in t.annotations}
    for fi in ana.main_files:
        for fl in fi.fields:
            anns = {a.split(".")[-1] for a in fl.annotations}
            if "static" in fl.modifiers and "final" in fl.modifiers:
                f["suppressed"]["static_final"] += 1
                continue
            if anns & EXCLUDED_FIELD_ANNOTATIONS:
                f["suppressed"]["annotated_field"] += 1
                continue
            if not ("private" in fl.modifiers
                    or not ({"public", "protected"} & fl.modifiers)):
                continue
            total = fi.ident.get(fl.name, 0)
            decls = fi.decl_field_counts.get(fl.name, 0)
            if total != decls:
                continue
            owner_anns = owner_ann_index.get((fi.rel, fl.owner), set())
            repo_refs = ana.global_java.get(fl.name, 0)
            row = {
                "name": fl.name, "owner": fl.owner, "loc": _rel_line(fl),
                "refs_in_file": total, "decls_in_file": decls,
                "repo_refs": repo_refs,
                "prose_refs": fi.prose.get(fl.name, 0),
                "modifiers": " ".join(sorted(fl.modifiers)) or "(package-private)",
                "note": _ann_note(fl.annotations),
            }
            if owner_anns & LOMBOK_ACCESSOR_ANNOTATIONS:
                # Lombok generates the accessors, so the field name itself is
                # never repeated at a call site.  Look for the generated
                # accessor names instead.
                acc_refs = sum(ana.global_java.get(v, 0)
                               for v in accessor_variants(fl.name))
                acc_res = sum(ana.resource_counts.get(v, 0)
                              for v in accessor_variants(fl.name))
                row["generated_accessor_refs"] = acc_refs
                row["note"] = (row["note"] + " lombok:" + ",".join(
                    sorted(owner_anns & LOMBOK_ACCESSOR_ANNOTATIONS))).strip()
                if repo_refs <= 1 and acc_refs == 0 and acc_res == 0:
                    f["cat4b_lombok_fields"].append(row)
                else:
                    f["suppressed"]["lombok_accessor_used"] += 1
                continue
            f["cat4_fields"].append(row)

    # ---- 5. public methods with no call site ------------------------------
    bean_classes = {}
    for fi in ana.main_files:
        for t in fi.types:
            anns = {a.split(".")[-1] for a in t.annotations}
            if anns & BEAN_ANNOTATIONS:
                bean_classes[(fi.rel, t.name)] = (t, fi, sorted(anns & BEAN_ANNOTATIONS))
    owner_type_index = {}
    methods_by_owner = {}
    for fi in ana.main_files:
        for t in fi.types:
            owner_type_index[(fi.rel, t.name)] = t
        for m in fi.methods:
            methods_by_owner.setdefault(m.owner, set()).add(m.name)
    for fi in ana.main_files:
        for m in fi.methods:
            if "public" not in m.modifiers or m.is_constructor:
                continue
            if m.owner_kind in ("interface", "annotation"):
                continue
            key = (fi.rel, m.owner)
            is_bean = key in bean_classes
            total = ana.global_java.get(m.name, 0)
            decls = ana.global_method_decls.get(m.name, 0)
            file_total = fi.ident.get(m.name, 0)
            file_decls = fi.decl_name_counts.get(m.name, 0)
            dot = ana.global_dot_calls.get(m.name, 0)
            anns = {a.split(".")[-1] for a in m.annotations}
            fw = bool(anns & FRAMEWORK_ANNOTATIONS)
            bs = bool(BEAN_SHAPED_RE.match(m.name)) or m.name in BEAN_SHAPED_EXTRA
            owner_type = owner_type_index.get((fi.rel, m.owner))
            contract = bool(owner_type is not None and any(
                m.name in methods_by_owner.get(s, ())
                for s in (owner_type.supertypes or [])))
            base = {
                "name": m.name,
                "owner": m.owner,
                "loc": _rel_line(m),
                "repo_refs": total,
                "repo_decls": decls,
                "dot_calls": dot,
                "refs_in_own_file": file_total - file_decls,
                "prose_refs": ana.global_prose.get(m.name, 0),
                "annotations": ",".join(sorted(anns)),
                "framework_anchored": fw,
                "bean_shaped": bs,
                "interface_contract": contract,
                "static": "static" in m.modifiers,
                "abstract": bool(m.abstract),
                "bean_annotations": ",".join(bean_classes[key][2]) if is_bean else "",
            }
            strict = (total == decls)
            no_call_shape = (dot == 0 and (file_total - file_decls) == 0)
            if strict and is_bean:
                f["cat5a_core" if not (fw or bs) else "cat5a_framework"].append(base)
            elif strict and not is_bean:
                f["cat5b_core" if not (fw or bs) else "cat5b_framework"].append(base)
            elif is_bean and no_call_shape:
                f["cat5c_core" if not (fw or bs)
                  else "cat5c_framework"].append(base)

    for k in f:
        if isinstance(f[k], list):
            f[k].sort(key=lambda r: (r.get("owner") or "", r["name"], r["loc"]))
    return f


# --------------------------------------------------------------------------
# report
# --------------------------------------------------------------------------

def _table(rows, cols, max_rows):
    if not rows:
        return "_(none)_\n"
    out = ["| " + " | ".join(cols) + " |",
           "|" + "|".join(["---"] * len(cols)) + "|"]
    shown = rows if max_rows <= 0 else rows[:max_rows]
    for r in shown:
        out.append("| " + " | ".join(str(r.get(c, "")) for c in cols) + " |")
    if max_rows > 0 and len(rows) > max_rows:
        out.append(f"| _… {len(rows) - max_rows} further rows omitted "
                   f"(raise --max-rows)_ |" + " |" * (len(cols) - 1))
    return "\n".join(out) + "\n"


def build_report(ana: Analysis, f, args, known_positive, negative_controls,
                 self_test=None):
    ts = _dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    s = ana.scope
    L = []
    A = L.append
    A("# JAiRouter backend dead-code report (candidate list)\n")
    A(f"- Generated: {ts} (local time)")
    A("- Script: `scripts/audit/detect-backend-dead-code.py`")
    A(f"- Command: `python scripts/audit/detect-backend-dead-code.py "
      f"--root {args.root} --report {args.report}`")
    A("- Runtime: Python " + sys.version.split()[0] + ", standard library only")
    A("- Build tools: **not invoked** (no maven / gradle) — analysis is purely "
      "source-based\n")
    A("> Every row below is a **candidate**, not a verdict. This tool matches "
      "*names* in source text; it cannot see reflection, Spring component "
      "scanning, bytecode, generated code, or calls made from outside the "
      "scanned corpus. See *Known blind spots* before deleting anything.\n")

    A("## 1. Scope and counts\n")
    A("| Item | Value |")
    A("|---|---|")
    A(f"| repository root | `{s['root']}` |")
    A(f"| declaration scope | `{s['main_dir']}` |")
    A(f"| reference scope | `{s['main_dir']}` + `{s['test_dir']}` "
      f"({s['java_files']} java files, {s['main_lines'] + s['test_lines']} lines) |")
    A(f"| resource reference scope | {', '.join('`'+d+'`' for d in s['resource_dirs'])} "
      f"({s['resource_files']} text files) |")
    A(f"| main java files | {s['main_files']} ({s['main_lines']} lines) |")
    A(f"| test java files | {s['test_files']} ({s['test_lines']} lines, reference only) |")
    A(f"| distinct identifiers indexed | {s['identifiers']} |")
    A(f"| type declarations in scope | {s['types']} (top-level {s['types_top']}) |")
    A(f"| method declarations in scope | {s['methods']} |")
    A(f"| field declarations in scope | {s['fields']} |")
    A(f"| files that failed to parse | {s['parse_errors']} |")
    A("")
    A("Excluded paths everywhere: any path containing `target`, `node_modules` "
      "or `.git`.\n")

    A("## 2. Method (what each category actually tests)\n")
    A("| # | Category | Rule applied | Search scope |")
    A("|---|---|---|---|")
    A("| 1 | Zero-reference top-level types | top-level `class`/`interface`/"
      "`enum`/`record` in `src/main/java` whose name has **zero** occurrences "
      "outside its own declaring file | java corpus (main+test) **and** "
      "resource text files |")
    A("| 2 | Interfaces with no implementor | interface with no "
      "`implements <Name>` / `extends <Name>` occurrence (dotted supertype "
      "chains such as `implements Outer.Inner` are recognised) | "
      "`src/main/java` only (per specification); test-scope hits reported as "
      "a separate column |")
    A("| 3 | Unused private methods | `private` method (constructors excluded) "
      "whose name occurs in its own file exactly as many times as it is "
      "declared there | declaring file |")
    A("| 4 | Unused private/package-private fields | as #3, for fields; "
      "`static final` and framework-injected/annotated fields excluded | "
      "declaring file (+ repository-wide count shown for triage) |")
    A("| 5a | Public methods of Spring stereotypes with no reference | "
      "`public` non-constructor method in a class annotated `@Service` "
      "`@Component` `@Repository` `@RestController` `@Controller` "
      "`@Configuration` whose name occurs in the corpus exactly as often as "
      "it is declared (split into rows with and without a framework anchor) | "
      "java corpus (main+test) |")
    A("| 5b | Public methods of non-stereotype classes with no reference | "
      "same rule, classes without a Spring stereotype annotation (split the "
      "same way) | java corpus (main+test) |")
    A("| 5c | Stereotype methods with no call-shaped reference | the name is "
      "declared elsewhere (so 5a cannot see it) but there is **no** `.name(` / "
      "`::name` occurrence anywhere and no extra occurrence in its own file | "
      "java corpus (main+test) |")
    A("")
    A("Counting note: the declaration itself is one occurrence, and method "
      "declaration counts across the whole reference corpus are subtracted, so "
      "\"zero references\" means \"every occurrence of this name is a method "
      "declaration\". Comments, javadoc and string literals are **not** counted "
      "as references (the lexer drops them).\n")
    A("`prose_refs` is the number of occurrences that exist **only** inside "
      "comments, javadoc or string literals (raw-text count minus token count). "
      "It is triage evidence, not usage: a row whose only other mention is "
      "javadoc (`{@link #name}`) or a reflective string "
      "(`getDeclaredMethod(\"name\")`) still counts as unreferenced here, but a "
      "non-zero `prose_refs` tells you where to look before deleting. Two real "
      "examples from this run: `StreamingRequestProcessor.estimateTokens` "
      "(`prose_refs=2`, both javadoc) and `CacheConfiguration` "
      "(`prose_refs=1`, a javadoc mention inside a test class).\n")

    A("## 3. Calibration\n")
    if self_test:
        st_failed = sum(1 for _, ok, _ in self_test if not ok)
        A(f"The self-test ran **in-process while this report was generated**: "
          f"it built {len(FIXTURES)} synthetic fixture files in a temporary "
          f"directory outside the repository, pointed the same analysis code at "
          f"that tree, and checked {len(self_test)} assertions — "
          f"**{len(self_test) - st_failed} passed, {st_failed} failed**. "
          f"The scratch tree was removed afterwards. Reproduce with "
          f"`python scripts/audit/detect-backend-dead-code.py --self-test`.\n")
        A("| # | Assertion | Result | Evidence |")
        A("|---|---|---|---|")
        for i, (label, ok, detail) in enumerate(self_test, 1):
            A(f"| {i} | {label} | {'PASS' if ok else 'FAIL'} | {detail} |")
        A("")
    A("Calibration against this repository (computed by this run):\n")
    A("| Check | Expectation | Result | Evidence |")
    A("|---|---|---|---|")
    A(f"| known positive `SecurityUtils.generateSecureRandomString` | reported | "
      f"{'PASS' if known_positive['hit'] else 'FAIL'} | "
      f"{known_positive['detail']} |")
    for nc in negative_controls:
        A(f"| negative control `{nc['name']}` ({nc['owner']}) | not reported | "
          f"{'PASS' if nc['hit'] else 'FAIL'} | {nc['detail']} |")
    A("")

    A("## 4. Findings\n")
    counts = [
        ("1a  zero-reference top-level types (no annotations)", len(f["cat1_plain"])),
        ("1b  zero-reference top-level types (annotated)", len(f["cat1_annotated"])),
        ("2a  interfaces with no implementor (plain)", len(f["cat2_plain"])),
        ("2b  interfaces with no implementor (framework-shaped)", len(f["cat2_framework"])),
        ("3   unused private methods", len(f["cat3_private_methods"])),
        ("4   unused private/package-private fields", len(f["cat4_fields"])),
        ("4b  Lombok-type fields with no accessor call site", len(f["cat4b_lombok_fields"])),
        ("5a-i  bean methods, no framework anchor", len(f["cat5a_core"])),
        ("5a-ii bean methods, framework-anchored/bean-shaped", len(f["cat5a_framework"])),
        ("5b-i  non-bean public methods, no framework anchor", len(f["cat5b_core"])),
        ("5b-ii non-bean public methods, framework-anchored/bean-shaped", len(f["cat5b_framework"])),
        ("5c-i  bean methods with no call-shaped reference", len(f["cat5c_core"])),
        ("5c-ii bean methods with no call-shaped reference, framework-anchored",
         len(f["cat5c_framework"])),
    ]
    A("| Category | Candidates |")
    A("|---|---|")
    for label, n in counts:
        A(f"| {label} | {n} |")
    A("")

    A(f"### 4.1a Zero-reference top-level types, no annotations — "
      f"{len(f['cat1_plain'])} candidate(s)\n")
    A("Strongest rows of category 1: nothing is written on the type itself "
      "that a framework could key on.\n")
    A(_table(f["cat1_plain"],
             ["name", "kind", "loc", "java_refs_outside_file", "resource_refs",
              "prose_refs"],
             args.max_rows))

    A(f"### 4.1b Zero-reference top-level types carrying annotations — "
      f"{len(f['cat1_annotated'])} candidate(s)\n")
    A("The name is referenced nowhere, but the annotation list shows why that "
      "may be fine: `@Service`/`@Component`/`@RestController`/`@Configuration`/"
      "`@Repository` types are instantiated by component scanning, `@Entity`/"
      "`@Table`/`@Data`/`@Builder` types are used by JPA/Jackson/Lombok without "
      "the class name appearing in source.\n")
    A(_table(f["cat1_annotated"],
             ["name", "kind", "loc", "java_refs_outside_file", "resource_refs",
              "prose_refs", "annotations"],
             args.max_rows))

    A(f"### 4.2a Interfaces with no implementor — {len(f['cat2_plain'])} "
      f"candidate(s)\n")
    A("Implementing search scope = `src/main/java` (whole tree); "
      "`impl_hits_test` additionally counts test-scope `implements`/`extends` "
      "hits and `anonymous_new` counts `new Name(...) { ... }` anonymous "
      "implementations. An interface with 0 implementors but a non-zero "
      "`anonymous_new` / `impl_hits_test` is most likely live.\n")
    A(_table(f["cat2_plain"],
             ["name", "loc", "top_level", "impl_hits_main", "impl_hits_test",
              "anonymous_new", "total_name_refs", "annotations"],
             args.max_rows))

    A(f"### 4.2b Interfaces with no implementor but framework-shaped — "
      f"{len(f['cat2_framework'])} candidate(s)\n")
    A("`flags` explains the mechanism that hides the implementation: "
      "`spring-data(...)` = the interface extends a Spring Data repository base "
      "type, so the implementation is a runtime proxy; `functional-interface` = "
      "a lambda target, so no `implements` clause exists anywhere; "
      "`anonymous-impl` = an anonymous class implements it but the name does not "
      "appear in an `implements` clause this tool indexes.\n")
    A(_table(f["cat2_framework"],
             ["name", "loc", "top_level", "impl_hits_main", "impl_hits_test",
              "anonymous_new", "total_name_refs", "flags", "annotations"],
             args.max_rows))

    A(f"### 4.3 Unused private methods — {len(f['cat3_private_methods'])} candidate(s)\n")
    A("Excluded: constructors (a private constructor is a legitimate "
      "utility-class idiom). **Not** excluded: methods invoked reflectively or "
      "by a framework (`@Scheduled`, `@EventListener`, …); the `note` column "
      "lists any annotation on the method, and a reflective caller such as "
      "`getDeclaredMethod(\"name\")` is invisible to this analysis because "
      "string literals are stripped.\n")
    A(_table(f["cat3_private_methods"],
             ["name", "owner", "loc", "refs_in_file", "decls_in_file",
              "repo_refs", "prose_refs", "note"],
             args.max_rows))

    A(f"### 4.4 Unused private / package-private fields — "
      f"{len(f['cat4_fields'])} candidate(s)\n")
    A("Exclusions applied (per specification): fields declared `static final` "
      f"({f['suppressed']['static_final']} suppressed), fields annotated "
      "`@Value` / `@Autowired` / `@Resource` / `@Inject` / `@PersistenceContext` "
      f"({f['suppressed']['annotated_field']} suppressed). Public and protected "
      "fields are out of scope. Fields of types carrying accessor-generating "
      "Lombok annotations (`@Data`, `@Getter`, `@Setter`, `@Builder`, "
      f"`@Value`) are reported separately in 4.4b. Note that a `private final` "
      "field in a `@RequiredArgsConstructor` class is assigned by the generated "
      "constructor: it is never read here, but deleting it also changes the "
      "constructor signature and the injection graph.\n")
    A("`refs_in_file` is the raw occurrence count of the field name in its own "
      "file; `repo_refs` is the occurrence count over the whole corpus and is "
      "triage information only (a non-zero value means some other symbol shares "
      "the name).\n")
    A(_table(f["cat4_fields"],
             ["name", "owner", "loc", "modifiers", "refs_in_file",
              "decls_in_file", "repo_refs", "prose_refs", "note"],
             args.max_rows))

    A(f"### 4.4b Lombok-type fields with no accessor call site anywhere — "
      f"{len(f['cat4b_lombok_fields'])} candidate(s)\n")
    A("The same-file rule of 4.4 cannot judge these fields: Lombok generates "
      "the accessors, so the field name never repeats in source. A stricter "
      "check was applied instead — the field name occurs (at most) in its own "
      "declaration **and** none of `get`/`set`/`is` + Capitalised-name occurs "
      "anywhere in the java corpus or in resource files. "
      f"{f['suppressed']['lombok_accessor_used']} further Lombok fields were "
      "suppressed because an accessor name is referenced somewhere.\n")
    A("Caveat: a field can still be live through Jackson/Spring binding without "
      "any accessor call site (see blind spot 1), and a "
      "`@ConfigurationProperties` field whose accessor is never called is "
      "exactly the \"configurable but never read\" case — those rows are "
      "valuable, but confirm against the code that binds the property.\n")
    A(_table(f["cat4b_lombok_fields"],
             ["name", "owner", "loc", "modifiers", "refs_in_file", "repo_refs",
              "generated_accessor_refs", "prose_refs", "note"],
             args.max_rows))

    A(f"### 4.5a-i Public methods of Spring stereotypes with no reference "
      f"(no framework anchor) — {len(f['cat5a_core'])} candidate(s)\n")
    A("**Candidates, not verdicts.** These rows carry no HTTP/scheduling/event "
      "annotation and are not JavaBean-accessor-shaped, so the ordinary "
      "explanations (framework entrypoint, Jackson serialization) are less "
      "likely. A method can still be reached through an interface (the call "
      "site carries the method name, so that case *is* caught) or by "
      "reflection/AOP.\n")
    A(_table(f["cat5a_core"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "static",
              "abstract", "bean_annotations", "prose_refs", "annotations",
              "interface_contract"],
             args.max_rows))

    A(f"### 4.5a-ii Public methods of Spring stereotypes with no reference "
      f"(framework-anchored or bean-shaped) — {len(f['cat5a_framework'])} "
      f"candidate(s)\n")
    A("`framework_anchored=1` means the method carries an annotation that "
      "commonly makes the framework call it (`@Bean`, `@Scheduled`, "
      "`@EventListener`, `@GetMapping`, `@Override`, …); `bean_shaped=1` means "
      "the name looks like an accessor/serialization callback; "
      "`interface_contract=1` means the declaring class lists a supertype that "
      "declares a method of the same name, so dispatch may happen through that "
      "interface (possibly from a framework). Verify those mechanisms before "
      "treating the row as dead.\n")
    A(_table(f["cat5a_framework"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "static",
              "abstract", "bean_annotations", "prose_refs", "annotations",
              "framework_anchored", "bean_shaped", "interface_contract"],
             args.max_rows))

    A(f"### 4.5b-i Public methods of non-stereotype classes with no reference "
      f"(no framework anchor) — {len(f['cat5b_core'])} candidate(s)\n")
    A("Same rule as 4.5a but for classes without a Spring stereotype "
      "annotation (utility classes, DTOs, entities, enums, "
      "configuration-properties holders, …). This is where pure static helpers "
      "such as `SecurityUtils.generateSecureRandomString` are found.\n")
    A(_table(f["cat5b_core"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "static",
              "abstract", "prose_refs", "annotations", "interface_contract"],
             args.max_rows))

    A(f"### 4.5b-ii Public methods of non-stereotype classes with no reference "
      f"(framework-anchored or bean-shaped) — {len(f['cat5b_framework'])} "
      f"candidate(s)\n")
    A("Accessor-shaped names on DTO/entity classes are usually live through "
      "Jackson, JPA or Spring binding even though no source call site exists; "
      "`@PrePersist`/`@JsonCreator`-style callbacks appear in the `annotations` "
      "column.\n")
    A(_table(f["cat5b_framework"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "static",
              "prose_refs", "annotations", "framework_anchored", "bean_shaped",
              "interface_contract"],
             args.max_rows))

    A(f"### 4.5c-i Stereotype methods with no call-shaped reference — "
      f"{len(f['cat5c_core'])} candidate(s)\n")
    A("Stricter, collision-tolerant view: the method name *is* declared "
      "elsewhere in the corpus (so the name-count rule of 4.5a is silent), yet "
      "no `.name(` / `::name` / static-import occurrence exists anywhere and the "
      "name is not used again in its own file. `repo_refs` exceeds `repo_decls` "
      "only because unrelated declarations share the name.\n")
    A(_table(f["cat5c_core"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "dot_calls",
              "prose_refs", "annotations", "framework_anchored", "bean_shaped",
              "interface_contract"],
             args.max_rows))

    A(f"### 4.5c-ii Stereotype methods with no call-shaped reference "
      f"(framework-anchored or bean-shaped) — "
      f"{len(f['cat5c_framework'])} candidate(s)\n")
    A("Same rule as 4.5c-i, but the method carries a framework annotation or a "
      "JavaBean-shaped name. A `@Bean` factory method, for instance, is "
      "registered by Spring and is normally referenced only through the bean it "
      "produces, never through the method name.\n")
    A(_table(f["cat5c_framework"],
             ["owner", "name", "loc", "repo_refs", "repo_decls", "dot_calls",
              "prose_refs", "annotations", "framework_anchored", "bean_shaped",
              "interface_contract"],
             args.max_rows))

    A("## 5. Applied exclusions (explicit)\n")
    A("- Paths: anything containing `target`, `node_modules`, `.git`.")
    A("- Category 1: nested types are not reported (only top-level types); "
      "types whose name appears in any resource text file are not reported; "
      "annotated and unannotated rows are split into 4.1b / 4.1a, not excluded.")
    A("- Category 2: interfaces with at least one `implements`/`extends` in "
      "`src/main/java` are not reported (test-scope hits are reported as a "
      "column, not as an exclusion); framework-shaped rows (Spring Data "
      "repository proxies, `@FunctionalInterface` lambda targets) are split "
      "into 4.2b, not excluded.")
    A("- Category 3: constructors excluded.")
    A("- Category 4: `static final` fields, fields annotated `@Value`, "
      "`@Autowired`, `@Resource`, `@Inject`, `@PersistenceContext`, public and "
      "protected fields; Lombok-annotated types moved to 4.4b, where fields "
      "whose generated accessors are referenced anywhere are suppressed.")
    A("- Category 5: interface/annotation-type method declarations excluded; "
      "non-`public` methods excluded (package-private bean methods are not "
      "covered).")
    A("- Everywhere: text inside comments, javadoc, string literals, char "
      "literals and text blocks is ignored.")
    A("")

    A("## 6. Known blind spots (what this method cannot see)\n")
    A("1. **Reflection and dynamic invocation** — `getDeclaredMethod(\"...\")`, "
      "`Class.forName`, Spring DI resolution, Jackson/JSON serialization "
      "(`getX`/`isX`), JPA/MyBatis is not visible: names appear only as string "
      "literals, which the lexer strips.")
    A("2. **Spring component scanning / configuration** — a class that Spring "
      "instantiates by annotation, by `@ConfigurationProperties` binding, by "
      "`spring.factories`, or by profile-conditional wiring has no source "
      "reference; such a class will be reported by category 1. Resource files "
      "are consulted for class names, which covers only names written out "
      "literally.")
    A("3. **Interface-mediated calls** — a method reached only through an "
      "interface still has a call site carrying its name, so it is normally "
      "not reported; but a *public* method that is invoked generically is not "
      "visible: `MethodHandle`, lambdas produced by name, AOP point cuts, and "
      "framework dispatch by *type* rather than by name (Spring publishes an "
      "event and the container calls the listener method; nothing in the "
      "source writes `listener.onEvent(...)`). Rows whose class lists a "
      "matching supertype carry `interface_contract=1` as a warning.")
    A("4. **Name collisions and overloads** — counting is by identifier name "
      "only. If two unrelated members share a name, a dead one is hidden "
      "(false negative). Overloads are counted together: if any overload is "
      "called, all of them are treated as referenced; if none is called, every "
      "overload is reported. Category 5c partially compensates for methods by "
      "requiring a call-shaped reference.")
    A("5. **Anonymous classes, lambdas, local classes** — fields declared "
      "inside anonymous-class bodies are not collected (their statements are "
      "not at class-body level); methods are collected but attributed to the "
      "outer type.")
    A("6. **Records** — record components are constructor parameters in this "
      "parser, not fields, so record components are never reported by "
      "category 4/4b.")
    A("7. **Javadoc / `@link`** — a member referenced only in javadoc counts "
      "as unreferenced.")
    A("8. **Generated and non-java sources** — Lombok-generated accessors, "
      "MapStruct builders, annotation-processed classes, Kotlin/Groovy, and "
      "anything outside the scanned directories are invisible. Frontend code "
      "is not part of the reference corpus.")
    A("9. **Gradle/Maven build configuration** — plugins, profiles or "
      "generated sources are not read; a class used only from a build plugin "
      "would look dead.")
    A("10. **Parser approximation** — Java is parsed with a lexer plus "
      "heuristics, not a full grammar. Files that fail to parse are reported "
      "in section 1 and skipped; exotic syntax (annotation processors' "
      "injected members, deeply unusual formatting) may be misread. Every "
      "candidate must be verified before deletion.")
    A("")
    A("This tool does **not** claim that the remainder of the codebase is "
      "live. It reports exactly the candidates its rules can see, inside the "
      "scope stated in section 1.")
    return "\n".join(L) + "\n"


# --------------------------------------------------------------------------
# self test
# --------------------------------------------------------------------------

FIXTURES = {
    "src/main/java/demo/pkg/OrphanTypes.java": '''package demo.pkg;

public class SyntheticOrphan {
    public String describe() { return "orphan"; }
}

interface SyntheticPackagePrivateOrphan {
    void ping();
}
''',
    "src/main/java/demo/pkg/LiveTypes.java": '''package demo.pkg;

public class SyntheticLiveType {
    public String liveValue() { return "live"; }
}
''',
    "src/main/java/demo/pkg/OrphanInterfaces.java": '''package demo.pkg;

public interface SyntheticOrphanInterface {
    void neverImplemented();
}

interface SyntheticImplementedInterface {
    void work();
}

class SyntheticImplementor implements SyntheticImplementedInterface {
    @Override
    public void work() { }
}
''',
    "src/main/java/demo/pkg/Holder.java": '''package demo.pkg;

import org.springframework.beans.factory.annotation.Value;

public class SyntheticHolder {
    private int syntheticOrphanField;

    private String syntheticUsedField;

    private static final String SYNTHETIC_STATIC_FINAL_CONST = "const";

    @Value("${demo.value:default}")
    private String syntheticValueAnnotatedField;

    public String useFields() {
        return syntheticUsedField;
    }

    private int syntheticUnusedPrivate() {
        return 1;
    }

    private int syntheticUsedPrivate() {
        return 2;
    }

    public int callPrivate() {
        return syntheticUsedPrivate();
    }
}
''',
    "src/main/java/demo/pkg/BeanFixture.java": '''package demo.pkg;

import org.springframework.stereotype.Service;

@Service
public class SyntheticBeanFixture {
    public String syntheticBeanOrphanMethod() {
        return "orphan";
    }

    public String syntheticCalledBeanMethod() {
        return "called";
    }
}
''',
    "src/main/java/demo/pkg/UtilityFixture.java": '''package demo.pkg;

public final class SyntheticUtilityFixture {
    private SyntheticUtilityFixture() { }

    public static String syntheticUtilityOrphan() {
        return "orphan";
    }

    public static String syntheticUtilityUsed() {
        return "used";
    }
}
''',
    "src/main/java/demo/pkg/LiveController.java": '''package demo.pkg;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyntheticLiveController {
    @GetMapping("/synthetic")
    public String syntheticLiveControllerMethod() {
        return "ok";
    }
}
''',
    # guards two real parser regressions: a multi-line annotation array before a
    # type declaration, and `=` inside a parameter annotation
    "src/main/java/demo/pkg/AnnotationTorture.java": '''package demo.pkg;

import org.springframework.web.bind.annotation.RequestParam;

@SuppressWarnings({
        "unchecked",
        "rawtypes"
})
public class SyntheticAnnotatedArrayClass {

    private String syntheticFieldAfterAnnotationArray;

    public String syntheticParamAnnotatedMethod(
            @RequestParam(defaultValue = "1") int page) {
        String syntheticLocalNotAField = "local";
        return syntheticLocalNotAField + page;
    }
}
''',
    # guards the Lombok accessor rule (field name never repeats in source)
    "src/main/java/demo/pkg/LombokFixture.java": '''package demo.pkg;

import lombok.Data;

@Data
public class SyntheticLombokHolder {
    private String syntheticLombokDeadField;

    private String syntheticLombokLiveField;
}
''',
    "src/test/java/demo/pkg/SyntheticCaller.java": '''package demo.pkg;

public class SyntheticCaller {
    public String callEverything() {
        SyntheticLiveType live = new SyntheticLiveType();
        SyntheticBeanFixture bean = new SyntheticBeanFixture();
        SyntheticLiveController controller = new SyntheticLiveController();
        SyntheticLombokHolder lombok = new SyntheticLombokHolder();
        return live.liveValue()
                + bean.syntheticCalledBeanMethod()
                + SyntheticUtilityFixture.syntheticUtilityUsed()
                + controller.syntheticLiveControllerMethod()
                + lombok.getSyntheticLombokLiveField();
    }
}
''',
}


def run_selftest(verbose=True):
    """Create synthetic fixtures in a temp dir, run the real analysis, verify
    every category fires and every negative control stays silent.

    Returns (failed_assertions, results) where results is a list of
    (label, ok, detail).  The scratch tree is always removed.
    """
    if verbose:
        print("=== dead-code detector self-test ===")
    scratch = Path(tempfile.mkdtemp(prefix="deadcode-selftest-"))
    results = []
    try:
        repo_root = Path(__file__).resolve()
        for rel, content in FIXTURES.items():
            p = scratch / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")
        scratch_root = scratch.resolve()
        repo_resolved = repo_root.parents[2].resolve()
        inside_repo = (repo_resolved == scratch_root
                       or repo_resolved in scratch_root.parents)
        results.append(("scratch tree is outside the repository",
                        not inside_repo, f"repo={repo_resolved}"))
        if verbose:
            print(f"scratch tree: {scratch}")
            print(f"fixture files: {len(FIXTURES)}")

        ana = scan(scratch, "src/main/java", "src/test/java",
                   ["src/main/resources", "src/test/resources"])
        f = analyze(ana)
        if verbose:
            print(f"parsed: {ana.scope['main_files']} main + "
                  f"{ana.scope['test_files']} test file(s), "
                  f"{ana.scope['types']} types, {ana.scope['methods']} methods, "
                  f"{ana.scope['fields']} fields")

        cat1 = {r["name"]: r["loc"] for r in f["cat1_plain"] + f["cat1_annotated"]}
        cat2 = {r["name"]: r["loc"] for r in f["cat2_plain"] + f["cat2_framework"]}
        cat3 = {r["name"]: r["loc"] for r in f["cat3_private_methods"]}
        cat4 = {r["name"]: r["loc"] for r in f["cat4_fields"]}
        cat4b = {r["name"]: r["loc"] for r in f["cat4b_lombok_fields"]}
        cat5a = {r["name"]: r["loc"] for r in f["cat5a_core"] + f["cat5a_framework"]}
        cat5b = {r["name"]: r["loc"] for r in f["cat5b_core"] + f["cat5b_framework"]}

        def pos(label, mapping, name):
            if name in mapping:
                results.append((label, True, f"reported at {mapping[name]}"))
            else:
                results.append((label, False,
                                f"MISSING (category produced {sorted(mapping)})"))

        def neg(label, mapping, name):
            if name not in mapping:
                results.append((label, True,
                                f"correctly not reported (category has "
                                f"{len(mapping)} row(s))"))
            else:
                results.append((label, False,
                                f"FALSE POSITIVE at {mapping[name]}"))

        pos("cat1 fires: SyntheticOrphan reported", cat1, "SyntheticOrphan")
        pos("cat1 fires: SyntheticPackagePrivateOrphan reported", cat1,
            "SyntheticPackagePrivateOrphan")
        neg("cat1 negative control: live SyntheticLiveType not reported", cat1,
            "SyntheticLiveType")
        pos("cat1 also reports never-named implementation classes "
            "(expected, name-based rule)", cat1, "SyntheticImplementor")

        pos("cat2 fires: SyntheticOrphanInterface reported", cat2,
            "SyntheticOrphanInterface")
        neg("cat2 negative control: implemented interface not reported", cat2,
            "SyntheticImplementedInterface")

        pos("cat3 fires: syntheticUnusedPrivate reported", cat3,
            "syntheticUnusedPrivate")
        neg("cat3 negative control: syntheticUsedPrivate not reported", cat3,
            "syntheticUsedPrivate")
        neg("cat3 negative control: private constructor not reported", cat3,
            "SyntheticUtilityFixture")

        pos("cat4 fires: syntheticOrphanField reported", cat4,
            "syntheticOrphanField")
        neg("cat4 negative control: syntheticUsedField not reported", cat4,
            "syntheticUsedField")
        neg("cat4 exclusion: static final constant not reported", cat4,
            "SYNTHETIC_STATIC_FINAL_CONST")
        neg("cat4 exclusion: @Value field not reported", cat4,
            "syntheticValueAnnotatedField")
        pos("cat4 regression guard: field after a multi-line annotation array "
            "reported", cat4, "syntheticFieldAfterAnnotationArray")
        neg("cat4 regression guard: local variable in a method with an "
            "annotated parameter not reported", cat4, "syntheticLocalNotAField")
        pos("cat4b fires: Lombok field with no accessor call site reported",
            cat4b, "syntheticLombokDeadField")
        neg("cat4b control: Lombok field whose generated getter is called not "
            "reported", cat4b, "syntheticLombokLiveField")

        pos("cat5a fires: syntheticBeanOrphanMethod reported", cat5a,
            "syntheticBeanOrphanMethod")
        neg("cat5a negative control: called bean method not reported", cat5a,
            "syntheticCalledBeanMethod")
        neg("cat5a negative control: live controller method not reported",
            cat5a, "syntheticLiveControllerMethod")
        pos("cat5b fires: syntheticUtilityOrphan reported", cat5b,
            "syntheticUtilityOrphan")
        neg("cat5b negative control: used utility method not reported", cat5b,
            "syntheticUtilityUsed")
    finally:
        existed = scratch.exists()
        shutil.rmtree(scratch, ignore_errors=True)
        gone = not scratch.exists()
        results.append(("scratch tree removed (nothing left behind)",
                        (not existed) or gone, str(scratch)))

    failed = 0
    for label, ok, detail in results:
        if verbose:
            print(f"[{'PASS' if ok else 'FAIL'}] {label} — {detail}")
        if not ok:
            failed += 1
    if verbose:
        print(f"=== self-test: {len(results) - failed}/{len(results)} assertions "
              f"passed ===")
        print("SELF-TEST FAILED" if failed else "SELF-TEST PASSED")
    return failed, results


def _run_selftest() -> int:
    failed, _ = run_selftest(verbose=True)
    return 1 if failed else 0


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------

KNOWN_POSITIVE = ("SecurityUtils", "generateSecureRandomString")


def real_run(args) -> int:
    root = Path(args.root).resolve()
    if not (root / args.main_src).exists():
        print(f"error: {root / args.main_src} does not exist "
              f"(use --root / --main-src)", file=sys.stderr)
        return 2
    print(f"scanning {root / args.main_src} ...")
    ana = scan(root, args.main_src, args.test_src,
               [args.main_res, args.test_res])
    f = analyze(ana)
    s = ana.scope
    print(f"scanned {s['main_files']} main + {s['test_files']} test java "
          f"files ({s['main_lines']} + {s['test_lines']} lines), "
          f"{s['resource_files']} resource files, {s['identifiers']} identifiers")

    # calibration evidence on the real corpus
    hit = [r for r in f["cat5b_core"] + f["cat5b_framework"]
           if r["name"] == KNOWN_POSITIVE[1] and r["owner"] == KNOWN_POSITIVE[0]]
    known_positive = {
        "hit": bool(hit),
        "detail": (f"reported at `{hit[0]['loc']}` (owner {hit[0]['owner']})"
                   if hit else
                   "NOT REPORTED — calibration failure"),
    }
    # negative controls: the most call-heavy public methods in the corpus
    calls = ana.global_dot_calls
    reported_names = set()
    for key in ("cat5a_core", "cat5a_framework", "cat5b_core",
                "cat5b_framework", "cat5c_core", "cat5c_framework"):
        for r in f[key]:
            reported_names.add((r["owner"], r["name"]))
    neg_controls = []
    pool = []
    for fi in ana.main_files:
        for m in fi.methods:
            if "public" in m.modifiers and not m.is_constructor:
                pool.append((calls.get(m.name, 0), m))
    pool.sort(key=lambda x: (-x[0], x[1].name))
    seen_names = set()
    for count, m in pool:
        if count <= 0 or len(neg_controls) >= 5:
            break
        if m.name in seen_names:
            continue
        seen_names.add(m.name)
        hit = (m.owner, m.name) in reported_names
        neg_controls.append({
            "name": m.name, "owner": m.owner,
            "hit": not hit,
            "detail": f"{count} `.name(` / `::name` occurrence(s) in corpus, "
                      + ("correctly absent from every category"
                         if not hit else f"WRONGLY reported at {m.rel}:{m.line}"),
        })

    st_failed, st_results = run_selftest(verbose=not args.quiet)

    report = build_report(ana, f, args, known_positive, neg_controls,
                          st_results)
    out = Path(args.report)
    if not out.is_absolute():
        out = root / out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"report written: {out}")

    if args.json:
        jout = Path(args.json)
        if not jout.is_absolute():
            jout = root / jout
        jout.parent.mkdir(parents=True, exist_ok=True)
        jout.write_text(json.dumps({
            "scope": s,
            "parse_errors": ana.parse_errors,
            "findings": {k: v for k, v in f.items() if isinstance(v, list)},
            "suppressed": dict(f["suppressed"]),
            "calibration": {
                "known_positive": known_positive,
                "negative_controls": neg_controls,
                "self_test": {
                    "failed": st_failed,
                    "assertions": [
                        {"label": l, "ok": ok, "detail": d}
                        for l, ok, d in st_results
                    ],
                },
            },
        }, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"json written: {jout}")

    print("--- summary ---")
    print(f"1a. zero-ref top-level types, plain : {len(f['cat1_plain'])}")
    print(f"1b. zero-ref top-level types, annot : {len(f['cat1_annotated'])}")
    print(f"2a. interfaces w/o implementor      : {len(f['cat2_plain'])}")
    print(f"2b.   ... framework-shaped          : {len(f['cat2_framework'])}")
    print(f"3.  unused private methods          : {len(f['cat3_private_methods'])}")
    print(f"4.  unused private/pkg fields       : {len(f['cat4_fields'])} "
          f"(static-final suppressed {f['suppressed']['static_final']}, "
          f"annotated suppressed {f['suppressed']['annotated_field']})")
    print(f"4b. lombok fields, no accessor ref  : {len(f['cat4b_lombok_fields'])} "
          f"(suppressed {f['suppressed']['lombok_accessor_used']})")
    print(f"5a. bean public methods no refs     : {len(f['cat5a_core'])} core "
          f"+ {len(f['cat5a_framework'])} framework-shaped")
    print(f"5b. other public methods no refs    : {len(f['cat5b_core'])} core "
          f"+ {len(f['cat5b_framework'])} framework-shaped")
    print(f"5c. bean methods w/o call shape     : {len(f['cat5c_core'])} core "
          f"+ {len(f['cat5c_framework'])} framework-shaped")
    if ana.parse_errors:
        print(f"parse errors: {len(ana.parse_errors)}")
    print(f"known positive {KNOWN_POSITIVE[0]}.{KNOWN_POSITIVE[1]}: "
          f"{'REPORTED' if known_positive['hit'] else 'NOT REPORTED'}")
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="Self-calibrating candidate dead-code detector for the "
                    "JAiRouter backend (python 3 stdlib only).")
    ap.add_argument("--root", default=None,
                    help="repository root (default: two levels above this script)")
    ap.add_argument("--main-src", default="src/main/java")
    ap.add_argument("--test-src", default="src/test/java")
    ap.add_argument("--main-res", default="src/main/resources")
    ap.add_argument("--test-res", default="src/test/resources")
    ap.add_argument("--report", default="scripts/audit/out/backend-dead-code.md",
                    help="report path (default: %(default)s)")
    ap.add_argument("--json", default=None, help="optional machine-readable dump")
    ap.add_argument("--max-rows", type=int, default=0,
                    help="max rows per table, 0 = unlimited (default: %(default)s)")
    ap.add_argument("--quiet", action="store_true",
                    help="do not echo the in-process self-test to stdout")
    ap.add_argument("--self-test", action="store_true",
                    help="build synthetic fixtures, verify every category fires "
                         "and negative controls stay silent, exit non-zero on "
                         "failure")
    args = ap.parse_args(argv)

    if args.root is None:
        args.root = str(Path(__file__).resolve().parents[2])
    if args.self_test:
        return _run_selftest()
    return real_run(args)


if __name__ == "__main__":
    sys.exit(main())
