#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GitHub 工作流语法验证脚本

验证 GitHub Actions 工作流中**内嵌的 Python 代码**语法是否正确，以及几条 workflow 侧常见问题。

## 为什么重写（issue #429）

旧实现有两处**误报**，导致本脚本在 master 上直接失败、因而没有任何 workflow 引用它：

1. **把 YAML 缩进当成 Python 代码**：旧实现用正则
   `python\\s+-c\\s+\\|\\s*\\n((?:\\s+.*\\n)*)` 抓「`python -c |` 形式的多行块」。
   - 该形状在 bash 里根本不存在（`python -c` 后面直接跟代码，没有 `|` 管道写法），
     所以这条正则唯一能匹配到的是 `python -c "..."` 的**下一行开始的 YAML 缩进块**；
   - 于是它把 `docs-quality.yml` 里 `continue-on-error: true` / `- name: ...` 之类的 YAML 行
     当成 Python 送去 `ast.parse` ⇒ 报 `unexpected indent`（实测 2 处）。
2. **自相矛盾的路径检查**：旧实现判「`docs/docs-versions.json` 这个词出现 ⇒ 报错，应该是
   `docs/docs-versions.json`」—— 期望值与检出值字面相同，**恒真**。
   而 `docs/docs-versions.json` 恰恰是本仓**正确**的路径（`docs-version-manager.py:112`、
   `docs-version-management.yml:90/189`）⇒ 每跑必报假错。

## 依赖：只用标准库（刻意不引 PyYAML）

`gates-fast.yml` 的既定口径是「**秒级、纯标准库、不联网**」（该文件 `setup-python` 一步的注释
明确写着「刻意不装任何 pip 依赖」）⇒ 本脚本**不 import yaml**，改用下面这个块标量缩进提取器。
（实测教训：接进快闸的第一版用了 `yaml.safe_load`，CI 上因该 job 不装 PyYAML 直接 exit 2 ⇒ 报红。）

## 现在的判据

- **结构化提取**：按 YAML 块标量（`run: |` / `- run: |`，含 `|-` `|+` 与折行标量 `>-`）的缩进规则
  取出 `run` 的文本，**只在 `run` 文本里**找 `python -c` / `python3 -c`，再按**字符串字面量语义**
  取出 `-c` 的实参（正确处理跨行与 `\\"` 转义、对齐 YAML 的缩进），然后只对**第一条语句**做
  `ast.parse`（`python -c` 本来就只执行第一条语句）。
- **脚本内的 `python <path>.py` 不做语法解析**：那些 .py 由 `py_compile` 与各自自测覆盖，
  本脚本的职能是「工作流里内嵌的那段代码写得对不对」。
- **常见问题**：Python 版本是否一致、`scripts/<name>.py` 引用是否存在。**不做恒真的字符串自比较**。

## 自测

`python scripts/docs/test_validate_workflow_syntax.py` —— 含反证（故意写坏一段 ⇒ 必须判红）。

用法: python scripts/docs/validate-workflow-syntax.py [--project-root .]
"""

import argparse
import ast
import re
import sys
from pathlib import Path


# 每个 `run` 里可能有多个 python 调用
_C_INVOCATION = re.compile(r"\bpython3?\s+-c\b")
_SCRIPT_INVOCATION = re.compile(r"\bpython3?\s+([^\s'\"]+\.py)")
_PY_VERSION_KEY = re.compile(r"python-version:\s*['\"]?([0-9][0-9.]*)['\"]?")
_SCRIPTS_PY_REF = re.compile(r"scripts/([^/\s'\"]+\.py)")

# `run: |` / `- run: |` / `run: >-`（块标量，可带缩进指示符 `|2`）
_BLOCK_INDICATOR = re.compile(r"^(?P<indent>[ \t]*)(?:-[ \t]+)?(?P<key>[A-Za-z_][\w.\-]*):[ \t]*"
                              r"(?P<style>[|>])(?P<chomp>[+-]?)(?P<indent_ind>[0-9]*)[ \t]*$")
# `run: <单行标量>`（如 `run: python scripts/tools/generate_sitemap.py`）
_INLINE_RUN = re.compile(r"^(?P<indent>[ \t]*)(?:-[ \t]+)?run:[ \t]*(?P<value>\S.*)$")


def iter_run_blocks(text):
    """产出文本里所有 `run` 的内容（纯标准库，按 YAML 缩进切片）。

    覆盖两种写法：**块标量**（`run: |` 等，按缩进切片）与**单行标量**（`run: <命令>`）。

    ⚠️ 为什么不用 PyYAML：`gates-fast.yml` 的既定口径是「**秒级、纯标准库、不联网**」
    （该文件 `setup-python` 一步的注释明确写着「刻意不装任何 pip 依赖」）⇒ 本校验器必须
    零第三方依赖，否则接进快闸就等于要求 CI 多装一个包。
    """
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        m = _BLOCK_INDICATOR.match(lines[i])
        if not m or m.group("key") != "run":
            inline = _INLINE_RUN.match(lines[i])
            if inline:
                yield inline.group("value")
            i += 1
            continue
        key_indent = len(m.group("indent"))
        buf = []
        j = i + 1
        while j < len(lines):
            raw = lines[j]
            if not raw.strip():
                buf.append("")
                j += 1
                continue
            if (len(raw) - len(raw.lstrip(" \t"))) <= key_indent:
                break
            buf.append(raw)
            j += 1
        while buf and not buf[-1].strip():  # 去掉挂尾空行
            buf.pop()
        content = _dedent_block(buf)
        if m.group("style") == ">":  # 折行标量：换行折叠成空格
            content = " ".join(part.strip() for part in content.split("\n"))
        yield content
        i = j


def _dedent_block(lines):
    """按 YAML 块标量规则去掉块的公共缩进。"""
    non_empty = [ln for ln in lines if ln.strip()]
    if not non_empty:
        return ""
    indent = min(len(ln) - len(ln.lstrip(" \t")) for ln in non_empty)
    return "\n".join(ln[indent:] if ln.strip() else "" for ln in lines)


def _parse_string_literal(text, start):
    """从 text[start]（必须是引号）起，按 Python 字符串字面量语义取一段字符串。

    返回 (值, 结束引号之后的下标)；取不出合法字面量时返回 (None, len(text))。
    """
    quote = text[start]
    i = start + 1
    out = []
    closed = False
    while i < len(text):
        ch = text[i]
        if ch == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            out.append({  # 只覆盖内嵌代码里会出现的转义；其余原样保留
                "n": "\n", "r": "\r", "t": "\t",
                "\\": "\\", '"': '"', "'": "'",
            }.get(nxt, "\\" + nxt))
            i += 2
            continue
        if ch == quote:
            closed = True
            i += 1
            break
        out.append(ch)
        i += 1
    if not closed:
        return None, len(text)
    return "".join(out), i


def extract_inline_python(run_text):
    """从一段 `run` 脚本里取出所有 `python -c` 的**实参源码**。

    返回 [(命令形式, 源码, 被忽略的后续内容长度), ...]。
    """
    found = []
    pos = 0
    while True:
        m = _C_INVOCATION.search(run_text, pos)
        if not m:
            break
        j = m.end()
        while j < len(run_text) and run_text[j] in " \t":
            j += 1
        # 允许 `python -c "..."` 与 `python -c '...'`；取不到引号则跳过本次匹配
        if j >= len(run_text) or run_text[j] not in "\"'":
            pos = m.end()
            continue
        code, after = _parse_string_literal(run_text, j)
        if code is None:
            # 引号没闭合：这本身是工作流写坏了，用剩余内容当源码以便报出语法错误
            code, after = run_text[j + 1:], len(run_text)
        first_stmt, skipped = _first_statement(code)
        found.append((m.group(0).strip(), first_stmt, skipped))
        pos = after
    return found


def _first_statement(code):
    """`python -c` 只执行第一条语句 ⇒ 截到第一条语句为止。

    返回 (第一条语句源码, 被忽略的剩余字符数)。截不出来时原样返回。
    """
    try:
        tree = ast.parse(code)
    except SyntaxError:
        return code, 0  # 真语法错，原样交给调用方报红
    if not tree.body:
        return code, 0
    last = tree.body[0]
    lines = code.splitlines(keepends=True)
    end = getattr(last, "end_lineno", None)
    if not end:
        return code, 0
    kept = "".join(lines[:end])
    return kept, len(code) - len(kept)


def collect_python_syntax_issues(project_root):
    """返回 (问题列表, 检查统计 dict)。问题元素 = (相对路径, 说明)。"""
    workflows_dir = Path(project_root) / ".github" / "workflows"
    issues = []
    stats = {"workflows": 0, "inline": 0, "scripts": 0}

    if not workflows_dir.exists():
        return [(".github/workflows", "目录不存在")], stats

    for workflow_file in sorted(workflows_dir.glob("*.y*ml")):
        rel = workflow_file.relative_to(project_root).as_posix()
        stats["workflows"] += 1
        text = workflow_file.read_text(encoding="utf-8")
        for run_text in iter_run_blocks(text):
            for command, code, _skipped in extract_inline_python(run_text):
                stats["inline"] += 1
                try:
                    ast.parse(code)
                except SyntaxError as exc:
                    issues.append(
                        (rel, f"内嵌 Python 语法错误（{command}）: {exc.msg} "
                              f"(行 {exc.lineno}, 列 {exc.offset})")
                    )
            stats["scripts"] += len(_SCRIPT_INVOCATION.findall(run_text))

    return issues, stats


def collect_common_issues(project_root):
    """workflow 侧常见问题（Python 版本一致、scripts 引用存在）。"""
    workflows_dir = Path(project_root) / ".github" / "workflows"
    issues = []
    if not workflows_dir.exists():
        return issues

    for workflow_file in sorted(workflows_dir.glob("*.y*ml")):
        rel = workflow_file.relative_to(project_root).as_posix()
        content = workflow_file.read_text(encoding="utf-8")

        versions = set(_PY_VERSION_KEY.findall(content))
        if len(versions) > 1:
            issues.append((rel, f"发现多个 Python 版本: {sorted(versions)}"))

        for name in _SCRIPTS_PY_REF.findall(content):
            if not (Path(project_root) / "scripts" / name).exists() and \
               not (Path(project_root) / "scripts" / "docs" / name).exists():
                issues.append((rel, f"脚本路径可能错误: scripts/{name}"))

    return issues


def main():
    parser = argparse.ArgumentParser(description="GitHub 工作流语法验证")
    parser.add_argument("--project-root", default=".", help="仓库根目录")
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    print("🚀 GitHub 工作流语法验证")
    print("=" * 50)

    syntax_issues, stats = collect_python_syntax_issues(root)
    print(f"📊 检查了 {stats['workflows']} 个 workflow 文件 · "
          f"{stats['inline']} 段内嵌 Python · {stats['scripts']} 处脚本调用")

    common_issues = collect_common_issues(root)
    all_issues = syntax_issues + common_issues

    if all_issues:
        print("\n❌ 发现语法问题:")
        for rel, message in all_issues:
            print(f"  ❌ {rel}: {message}")
        print("\n" + "=" * 50)
        print("❌ 发现语法问题，请修复后重试。")
        return 1

    print("\n✅ 所有内嵌 Python 代码语法检查通过!")
    print("✅ workflow 常见问题检查通过!")
    print("\n" + "=" * 50)
    print("✅ 所有语法检查通过! GitHub Actions 应该能正常执行。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
