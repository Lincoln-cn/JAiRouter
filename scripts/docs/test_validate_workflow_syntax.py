#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""validate-workflow-syntax.py 的自测（含反证）。

用法: python scripts/docs/test_validate_workflow_syntax.py

判据分三组：
  ① 真实仓库：13 个 workflow / 2 段内嵌 Python ⇒ 0 问题（含 `#429` 的两处旧误报不得再出现）
  ② 反证（有检测力）：把内嵌代码写坏 ⇒ 必须判红；坏脚本引用 ⇒ 必须判红
  ③ 回归守卫：旧实现的两种误报模式（YAML 缩进当 Python、恒真的路径自比较）不得复活
"""

import importlib.util
import sys
import tempfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "validate-workflow-syntax.py"
REPO_ROOT = SCRIPT.parent.parent.parent

_spec = importlib.util.spec_from_file_location("validate_workflow_syntax", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

FAILURES = []


def check(name, condition, detail=""):
    if condition:
        print(f"✅ {name}")
    else:
        print(f"❌ {name}" + (f" —— {detail}" if detail else ""))
        FAILURES.append(name)


def write_workflow(root, name, body):
    wf_dir = Path(root) / ".github" / "workflows"
    wf_dir.mkdir(parents=True, exist_ok=True)
    (wf_dir / name).write_text(body, encoding="utf-8")


# ---------------------------------------------------------------- ① 真实仓库

print("=== ① 真实仓库 ===")
real_issues, real_stats = mod.collect_python_syntax_issues(REPO_ROOT)
check("真实仓库内嵌 Python 语法 0 问题", not real_issues, str(real_issues))
check("真实仓库 workflow 数 ≥ 13", real_stats["workflows"] >= 13, str(real_stats))
check("真实仓库确实提取到 2 段内嵌 Python（不是空转）",
      real_stats["inline"] == 2, str(real_stats))
check("真实仓库脚本调用被计数（不是空转）",
      real_stats["scripts"] >= 20, str(real_stats))
check("真实仓库常见问题 0 条",
      not mod.collect_common_issues(REPO_ROOT),
      str(mod.collect_common_issues(REPO_ROOT)))


# ---------------------------------------------------------------- ③ 回归守卫

print("\n=== ③ 回归守卫（旧误报复活就必须红） ===")

# 旧实现：紧跟 `python -c "..."` 的 YAML 缩进行被当成 Python ⇒ unexpected indent
STRAY_YAML = """\
name: t
on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: ok
        run: |
          python3 -c "
          import sys
          print('hi')
          "
          continue-on-error: true
      - name: next
        run: echo done
"""

with tempfile.TemporaryDirectory() as tmp:
    write_workflow(tmp, "stray.yml", STRAY_YAML)
    issues, stats = mod.collect_python_syntax_issues(tmp)
    check("回归①：`python -c` 之后的 YAML 缩进行不再被当成 Python",
          not any("unexpected indent" in msg for _, msg in issues), str(issues))
    check("回归①：该文件确实提取到 1 段内嵌 Python（不是漏检）",
          stats["inline"] == 1, str(stats))

# 旧实现：`docs/docs-versions.json` 出现即报错，且期望值与检出值字面相同（恒真）
TAUTOLOGY_YAML = """\
name: t
on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: version mgmt
        run: |
          echo "docs/docs-versions.json"
          python3 -c "print(1)"
"""

with tempfile.TemporaryDirectory() as tmp:
    write_workflow(tmp, "tautology.yml", TAUTOLOGY_YAML)
    issues = mod.collect_common_issues(tmp)
    check("回归②：正确的 `docs/docs-versions.json` 引用不再被报成错误",
          not issues, str(issues))


# ---------------------------------------------------------------- ② 反证

print("\n=== ② 反证（假代码必须被咬住） ===")

BROKEN_PY = """\
name: t
on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: broken
        run: |
          python3 -c "
          def f(:
              pass
          "
"""

with tempfile.TemporaryDirectory() as tmp:
    write_workflow(tmp, "broken.yml", BROKEN_PY)
    issues, stats = mod.collect_python_syntax_issues(tmp)
    check("反证①：写坏的内嵌 Python 必须判红",
          any("内嵌 Python 语法错误" in msg for _, msg in issues), str(issues))

BAD_SCRIPT_YAML = """\
name: t
on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: bad path
        run: python3 scripts/no-such-script-xyz.py
"""

with tempfile.TemporaryDirectory() as tmp:
    write_workflow(tmp, "badscript.yml", BAD_SCRIPT_YAML)
    issues = mod.collect_common_issues(tmp)
    check("反证②：不存在的 scripts 引用必须判红",
          any("脚本路径可能错误" in msg for _, msg in issues), str(issues))


print()
if FAILURES:
    print(f"❌ {len(FAILURES)} 项未通过: {FAILURES}")
    sys.exit(1)
print("✅ 全部通过")
sys.exit(0)
