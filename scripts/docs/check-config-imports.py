#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档配置清单 ↔ 真实配置树/导入清单 对账检查器（issue #250 批次 11）

## 为什么要它

本会话连续两次抓到**同一类**缺陷（批次 7 的 `application-config.md`、批次 10 的 `modular-config.md`）：
文档里的「配置模块清单 / 主配置导入清单」落后于代码 —— 4 个后来新增的模块
（`common/cluster.yml`、`common/scheduling.yml`、`router/pools.yml`、`router/rules.yml`）在两页里都缺，
其中 `modular-config.md` 还缺 4 条「向后兼容配置」。照抄清单的部署会「能启动但少了资源池 / 路由规则 /
集群自检 / 定时任务」⇒ **某些能力静默不存在**。人工发现一次不够，要变成门闩。

## 判据（三组双向比对）

1. **导入清单**：`src/main/resources/application.yml` 的 `spring.config.import` 条目
   ↔ `docs/{zh,en}/configuration/modular-config.md` 的 `- classpath:`/`- optional:file:` 清单
   （**缺**与**多**都判红）；
2. **真实配置树**：`src/main/resources/config/**/*.yml` 的每个文件都必须被
   `docs/{zh,en}/configuration/application-config.md` 提到（**没提到就判红**）；
3. **诚实性**：该页提到的 `config/**.yml` 必须真实存在（**幽灵文件判红**）。

用法:
    python scripts/docs/check-config-imports.py --fail-on-error
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Iterable, Set, Tuple

IMPORT_DOCS = (
    "docs/zh/configuration/modular-config.md",
    "docs/en/configuration/modular-config.md",
)
TREE_DOCS = (
    "docs/zh/configuration/application-config.md",
    "docs/en/configuration/application-config.md",
)

# application.yml —— 只取 spring.config.import 段落
_IMPORT_ENTRY = re.compile(r"^\s*-\s*((?:optional:file:|classpath:)\S+)\s*$", re.M)
# 文档里的导入清单条目（同样只认列表项，避免把正文其它提及算进来）
_DOC_IMPORT_ENTRY = re.compile(r"^\s*-\s*((?:optional:file:|classpath:)\S+)\s*$", re.M)
# 文档里提到的 config/**/*.yml（排除 /app/config/... 这类外部挂载路径）
_CONFIG_FILE_MENTION = re.compile(r"(?<![/\w])config/[A-Za-z0-9_./-]+\.ya?ml")
# 真实配置树里的文件
_CONFIG_FILE_REAL = re.compile(r"^config/[A-Za-z0-9_./-]+\.ya?ml$")


def parse_imports(text: str) -> Set[str]:
    return set(_IMPORT_ENTRY.findall(text))


def parse_doc_imports(text: str) -> Set[str]:
    return set(_DOC_IMPORT_ENTRY.findall(text))


def parse_config_tree(root: Path) -> Set[str]:
    base = root / "src/main/resources"
    return {
        str(p.relative_to(base)).replace("\\", "/")
        for p in (base / "config").rglob("*.yml")
        if _CONFIG_FILE_REAL.match(str(p.relative_to(base)).replace("\\", "/"))
    }


def parse_doc_config_mentions(text: str) -> Set[str]:
    """文档「提到」的配置文件的完整路径集合 —— 两种形态都要认：

    ① **完整路径**写法（如 `` `config/common/server.yml` ``）；
    ② **框线树**写法（如 `│   ├── server.yml`，其完整路径靠父目录行 `├── common/` 表达）——
       本会话批次 7 补进去的 4 个模块就是这种形态，若不做树解析会误判成「未收录」。
    """
    mentions = {m for m in _CONFIG_FILE_MENTION.findall(text)}
    mentions |= _parse_tree_mentions(text)
    return mentions


_TREE_LINE = re.compile(r"^((?:│   |    )*)[├└]──\s+([^\s#]+)\s*(?:#.*)?$")


def _parse_tree_mentions(text: str) -> Set[str]:
    """把框线树解析成完整路径集合。

    树的根是单独一行 `config/`；其后每条 `├──`/`└──` 行的**深度由前缀缩进推断**，
    目录名（以 `/` 结尾）入栈，文件名与前缀栈拼接成完整路径。
    只在 `config/` 树内解析（遇到代码围栏或标题即退出），避免把 `/app/config/...` 那棵树误算进来。
    """
    found: Set[str] = set()
    in_tree = False
    stack: dict = {0: "config/"}
    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        stripped = line.strip()
        if stripped == "config/":
            in_tree, stack = True, {0: "config/"}
            continue
        if in_tree and (stripped.startswith("```") or stripped.startswith("#")):
            in_tree = False
            continue
        if not in_tree:
            continue
        match = _TREE_LINE.match(line)
        if not match:
            continue
        prefix, name = match.group(1), match.group(2)
        depth = len(prefix) // 4 + 1  # 根 config/ 下的直接条目为第 1 层
        if name.endswith("/"):
            stack[depth] = name
            continue
        if not name.endswith((".yml", ".yaml")):
            continue
        parent = "".join(stack.get(level, "") for level in range(depth))
        if parent.startswith("config/"):
            found.add(parent + name)
    return found


def reconcile(doc: Iterable[str], real: Set[str]) -> Tuple[Set[str], Set[str]]:
    """返回 (文档缺, 文档多)。"""
    doc_set = set(doc)
    return real - doc_set, doc_set - real


def main() -> int:
    parser = argparse.ArgumentParser(description="文档配置清单与真实配置对账")
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--fail-on-error", action="store_true")
    args = parser.parse_args()

    root = Path(args.project_root).resolve()
    app_yml = root / "src/main/resources/application.yml"
    real_imports = parse_imports(app_yml.read_text(encoding="utf-8"))
    real_tree = parse_config_tree(root)
    print(f"🔍 真实：import 条目 {len(real_imports)} 条 · config 树 {len(real_tree)} 个 yml")

    problems = 0

    print("\n—— ① 导入清单（modular-config）——")
    for doc_rel in IMPORT_DOCS:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue
        missing, extra = reconcile(parse_doc_imports(doc_path.read_text(encoding="utf-8")), real_imports)
        print(f"📄 {doc_rel}: 缺 {len(missing)} · 多 {len(extra)}")
        for item in sorted(missing):
            print(f"   ❌ 文档缺: {item}")
        for item in sorted(extra):
            print(f"   ❌ 文档多（不存在）: {item}")
        problems += len(missing) + len(extra)

    print("\n—— ②③ 配置模块清单（application-config）——")
    for doc_rel in TREE_DOCS:
        doc_path = root / doc_rel
        if not doc_path.exists():
            print(f"⚠️  文档不存在，跳过: {doc_rel}")
            continue
        missing, extra = reconcile(parse_doc_config_mentions(doc_path.read_text(encoding="utf-8")), real_tree)
        print(f"📄 {doc_rel}: 未收录 {len(missing)} · 幽灵 {len(extra)}")
        for item in sorted(missing):
            print(f"   ❌ 真实文件未被收录: {item}")
        for item in sorted(extra):
            print(f"   ❌ 文档提到但不存在: {item}")
        problems += len(missing) + len(extra)

    print()
    if problems:
        print(f"❌ 共 {problems} 处配置清单不一致")
        return 1 if args.fail_on_error else 0
    print("✅ 文档配置清单与真实配置树/导入清单一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
