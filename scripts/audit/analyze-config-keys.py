#!/usr/bin/env python3
"""配置键对账分析器（审计用，非门闩）。

判据链（本仓 #295 / #312 / #320 定下的三步取证，这里把它扩到全树）：

  ① 该键是否落在**任何** @ConfigurationProperties 前缀之下？   没有 ⇒ **孤儿区域**
     （能设、不报错、不生效；运维照 yml 调参完全没用）
  ② 落在某个前缀之下时，是否是该键的**最长前缀**归属？         用最长前缀避免
     「父前缀类没有子树的字段」这类误报（例：jairouter.security 与
     jairouter.security.jwt 并存时，后者才是 accounts.* 的归属）
  ③ 孤儿键有没有非绑定的消费方？                              @Value / @ConditionalOnProperty /
     getProperty / getenv 直接读属性是合法形态，需要登记而不是判缺陷

输出：markdown 报告 + JSON 清单（供后续落 issue 用）。

用法：
    python scripts/audit/analyze-config-keys.py [--root .] [--out scripts/audit/out]
    python scripts/audit/analyze-config-keys.py --prefix jairouter
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    print("需要 PyYAML（本仓 CI 的快闸不得引第三方依赖，故本脚本刻意不进快闸）", file=sys.stderr)
    raise SystemExit(2)

YML_GLOBS = [
    "src/main/resources/application*.yml",
    "src/main/resources/config/**/*.yml",
]
JAVA_ROOT = "src/main/java"

RE_CONFIG_PROPS = re.compile(
    r'@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?"([^"]+)"', re.MULTILINE
)
RE_CONFIG_PROPS_ANY = re.compile(r'@ConfigurationProperties')
RE_VALUE = re.compile(r'@Value\s*\(\s*"\$\{([^}:]+)')
RE_COND_NAME = re.compile(r'@ConditionalOnProperty\s*\(([^)]*)\)', re.S)
RE_GETPROP = re.compile(r'\.getProperty\s*\(\s*"([^"]+)"')
RE_GETENV = re.compile(r'(?:System\.)?getenv\s*\(\s*"([^"]+)"')
RE_NAME_IN_COND = re.compile(r'name\s*=\s*"([^"]+)"')
RE_PREFIX_IN_COND = re.compile(r'prefix\s*=\s*"([^"]+)"')
# 持有「属性名」的字符串常量：private/public static final String X = "a.b.c";
RE_PROP_CONST = re.compile(
    r'static\s+final\s+String\s+(\w+)\s*=\s*"([a-zA-Z][\w.\-]*)"'
)
# 以常量（而非字面量）取属性的写法：getProperty(X) / getProperty(X, ...) / getProperty(X, T.class, ...)
RE_GETPROP_IDENT = re.compile(r'\.getProperty\s*\(\s*(\w+)\s*[,)]')
RE_COND_NAME_IDENT = re.compile(r'name\s*=\s*(\w+)\s*[,)]')
# 任何 ${...} 占位符都算读了这个属性（含 @Scheduled(fixedRateString="${...}")）
RE_PLACEHOLDER = re.compile(r'\$\{([a-zA-Z][\w.\-]*)(?::[^}]*)?\}')


def flatten(node, prefix="", out=None):
    """把嵌套 dict/list 展平成点号路径集合；列表元素带 [i]。"""
    if out is None:
        out = {}
    if isinstance(node, dict):
        if not node:
            out[prefix] = None
        for k, v in node.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            flatten(v, key, out)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            flatten(v, f"{prefix}[{i}]", out)
    else:
        out[prefix] = node
    return out


def strip_indices(path: str) -> str:
    return re.sub(r"\[\d+\]", "", path)


def collect_prefixes(root: Path):
    """→ {prefix: [ 'file:line', ... ]}"""
    found: dict[str, list[str]] = {}
    java_files = list((root / JAVA_ROOT).rglob("*.java"))
    for f in java_files:
        text = f.read_text(encoding="utf-8", errors="replace")
        for m in RE_CONFIG_PROPS.finditer(text):
            prefix = m.group(1)
            line = text[: m.start()].count("\n") + 1
            rel = f.relative_to(root).as_posix()
            found.setdefault(prefix, []).append(f"{rel}:{line}")
    return found


def collect_property_constants(root: Path):
    """→ {常量名: 属性名字面量}

    为什么必须有这一步（实测教训）：`MultiReplicaSharedStateChecker` 把
    `jairouter.cluster.shared-state-check.mode` 等键声明成 `public static final String MODE_KEY`
    再用 `environment.getProperty(MODE_KEY)` 读取。只匹配字面量的检测器会把这 3 个键
    误报成「无人读」—— 第一版实测就是这样出了 3 个假阳性。
    """
    consts: dict[str, str] = {}
    for f in (root / JAVA_ROOT).rglob("*.java"):
        text = f.read_text(encoding="utf-8", errors="replace")
        for m in RE_PROP_CONST.finditer(text):
            consts.setdefault(m.group(1), m.group(2))
    return consts


def collect_consumers(root: Path):
    """非绑定消费方：@Value / @ConditionalOnProperty / getProperty / getenv。

    含「以常量取属性」的写法（见 collect_property_constants 的说明）。
    """
    consts = collect_property_constants(root)
    consumers: dict[str, list[str]] = {}
    for f in (root / JAVA_ROOT).rglob("*.java"):
        text = f.read_text(encoding="utf-8", errors="replace")
        rel = f.relative_to(root).as_posix()

        def add(name: str, pos: int):
            if not name:
                return
            line = text[:pos].count("\n") + 1
            consumers.setdefault(name, []).append(f"{rel}:{line}")

        for m in RE_VALUE.finditer(text):
            add(m.group(1), m.start())
        for m in RE_GETPROP.finditer(text):
            add(m.group(1), m.start())
        for m in RE_GETENV.finditer(text):
            add(m.group(1), m.start())
        for m in RE_COND_NAME.finditer(text):
            block = m.group(1)
            names = RE_NAME_IN_COND.findall(block)
            prefixes = RE_PREFIX_IN_COND.findall(block)
            for n in names:
                for p in (prefixes or [""]):
                    add(f"{p}.{n}" if p else n, m.start())
        # 常量形态：getProperty(MODE_KEY) / @ConditionalOnProperty(name = MODE_KEY)
        for m in RE_GETPROP_IDENT.finditer(text):
            add(consts.get(m.group(1), ""), m.start())
        for m in RE_COND_NAME_IDENT.finditer(text):
            add(consts.get(m.group(1), ""), m.start())
    return consumers


def load_keys(root: Path):
    """→ {key: [ 'file', ... ]}，只保留叶子键。"""
    keys: dict[str, set[str]] = {}
    files: list[Path] = []
    for g in YML_GLOBS:
        files.extend(sorted(root.glob(g)))
    for f in files:
        rel = f.relative_to(root).as_posix()
        try:
            data = yaml.safe_load(f.read_text(encoding="utf-8", errors="replace"))
        except Exception as e:  # noqa: BLE001
            print(f"  !! 无法解析 {rel}: {e}", file=sys.stderr)
            continue
        if not isinstance(data, dict):
            continue
        for k in flatten(data):
            if k == "":
                continue
            keys.setdefault(strip_indices(k), set()).add(rel)
    return keys, [f.relative_to(root).as_posix() for f in files]


def longest_prefix(key: str, prefixes) -> str | None:
    best = None
    for p in prefixes:
        if key == p or key.startswith(p + "."):
            if best is None or len(p) > len(best):
                best = p
    return best


def matches_consumer(key: str, consumers) -> list[str]:
    """键被某个消费方读取（消费方名可能是键本身或某个前缀）。"""
    hits = []
    for name, where in consumers.items():
        if key == name or key.startswith(name + "."):
            hits.extend(where)
        # @Value("${x.y}") 这种只写前缀的写法
        elif name.startswith(key + ".") or key.startswith(name):
            hits.extend(where)
    return sorted(set(hits))


def self_test() -> int:
    """自证：合成一棵最小仓库，断言三类判定都能命中，且负控不被误报。

    判据（缺一不可）：
      正例 1 键在绑定前缀下            → 不算孤儿
      正例 2 键无绑定类但有字面量消费方 → 算孤儿，且报出消费方
      正例 3 键无绑定类且无任何消费方   → 算孤儿，消费方为空（这才是缺陷）
      正例 4 键无绑定类、消费方是**常量** → 不得误报成「无人读」（第一版实测的假阳性）
      负控   已绑定的键                → 不得出现在孤儿清单里
    """
    import tempfile

    failures: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "src/main/java/demo").mkdir(parents=True)
        (root / "src/main/resources").mkdir(parents=True)
        (root / "src/main/resources/application-selftest.yml").write_text(
            "jairouter:\n"
            "  bound:\n"
            "    key: 1\n"
            "  orphan:\n"
            "    withLiteral: 2\n"
            "    withConst: 3\n"
            "    withNothing: 4\n",
            encoding="utf-8",
        )
        (root / "src/main/java/demo/Demo.java").write_text(
            "package demo;\n"
            "class Bound { }\n"
            "@ConfigurationProperties(prefix = \"jairouter.bound\")\n"
            "class BoundProps { }\n"
            "class Reader {\n"
            "  static final String WITH_CONST = \"jairouter.orphan.withConst\";\n"
            "  Object a(Environment e) { return e.getProperty(\"jairouter.orphan.withLiteral\"); }\n"
            "  Object b(Environment e) { return e.getProperty(WITH_CONST); }\n"
            "}\n",
            encoding="utf-8",
        )

        prefixes = collect_prefixes(root)
        consumers = collect_consumers(root)
        keys, _ = load_keys(root)
        scoped = {k: v for k, v in keys.items() if k.startswith("jairouter.")}
        orphans = {}
        for key, files in scoped.items():
            if longest_prefix(key, prefixes) is None:
                orphans[key] = matches_consumer(key, consumers)

        def expect(cond, label):
            (print(f"  [PASS] {label}") if cond else (failures.append(label), print(f"  [FAIL] {label}")))

        expect("jairouter.bound" in prefixes, "正例 0：绑定前缀被识别")
        expect("jairouter.bound.key" in scoped, "正例 0b：yml 叶子键被展开")
        expect("jairouter.bound.key" not in orphans, "负控：已绑定的键不进孤儿清单")
        expect(orphans.get("jairouter.orphan.withLiteral"), "正例 2：字面量消费方被识别")
        expect(orphans.get("jairouter.orphan.withConst"), "正例 4：**常量**消费方被识别（假阳性回归守卫）")
        expect("jairouter.orphan.withNothing" in orphans and not orphans["jairouter.orphan.withNothing"],
               "正例 3：无消费方的孤儿键被报为「无人读」")

    print(f"\n自检结论：{'全部通过' if not failures else f'{len(failures)} 项失败'}")
    return 1 if failures else 0


def collect_config_reads(root: Path):
    """反向扫描：代码**读取**的配置键（不含环境变量），带来源标记。

    与 collect_consumers 的区别：这里排除 getenv（环境变量本来就不该出现在 yml 里），
    并把 `@ConditionalOnProperty` 的 `matchIfMissing` 一并记下来 —— 读了一个 yml 里
    不存在的键时，`matchIfMissing=true` 是「有意的默认」，否则多半是缺陷。
    """
    reads: dict[str, list[str]] = {}
    consts = collect_property_constants(root)
    for f in (root / JAVA_ROOT).rglob("*.java"):
        text = f.read_text(encoding="utf-8", errors="replace")
        rel = f.relative_to(root).as_posix()

        def add(name: str, kind: str, pos: int):
            if not name or "." not in name:
                return
            line = text[:pos].count("\n") + 1
            reads.setdefault(name, []).append(f"{kind}@{rel}:{line}")

        for m in RE_VALUE.finditer(text):
            add(m.group(1), "Value", m.start())
        for m in RE_PLACEHOLDER.finditer(text):
            add(m.group(1), "placeholder", m.start())
        for m in RE_GETPROP.finditer(text):
            add(m.group(1), "getProperty", m.start())
        for m in RE_GETPROP_IDENT.finditer(text):
            add(consts.get(m.group(1), ""), "getProperty(const)", m.start())
        for m in RE_COND_NAME.finditer(text):
            block = m.group(1)
            missing = "true" if "matchIfMissing" in block else "false"
            names = RE_NAME_IN_COND.findall(block)
            prefixes = RE_PREFIX_IN_COND.findall(block)
            for n in names:
                for p in (prefixes or [""]):
                    add(f"{p}.{n}" if p else n, f"ConditionalOnProperty(matchIfMissing={missing})", m.start())
        for m in RE_COND_NAME_IDENT.finditer(text):
            add(consts.get(m.group(1), ""), "ConditionalOnProperty(const)", m.start())
    return reads


def reverse_orphan_keys(reads, keys) -> dict[str, list[str]]:
    """代码读了、但 yml 里没有任何键（也不作为前缀存在）的那些。"""
    out = {}
    for name, where in reads.items():
        if any(k == name or k.startswith(name + ".") or name.startswith(k + ".")
               for k in keys):
            continue
        out[name] = where
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--out", default="scripts/audit/out")
    ap.add_argument("--prefix", default="jairouter", help="只分析该前缀下的键")
    ap.add_argument("--self-test", action="store_true", help="用合成 fixture 自证检测力")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    outdir = root / args.out
    outdir.mkdir(parents=True, exist_ok=True)

    prefixes = collect_prefixes(root)
    consumers = collect_consumers(root)
    keys, yml_files = load_keys(root)

    scoped = {k: v for k, v in keys.items() if k == args.prefix or k.startswith(args.prefix + ".")}

    orphans, claimed = {}, {}
    for key, files in sorted(scoped.items()):
        p = longest_prefix(key, prefixes)
        if p is None:
            orphans[key] = {"yml": sorted(files), "consumers": matches_consumer(key, consumers)}
        else:
            claimed.setdefault(p, []).append(key)

    print(f"yml 文件            : {len(yml_files)}")
    print(f"@ConfigurationProperties 前缀 : {len(prefixes)}")
    print(f"{args.prefix}.* 叶子键        : {len(scoped)}")
    print(f"有归属前缀的键       : {len(scoped) - len(orphans)}")
    print(f"孤儿键（无任何绑定类）: {len(orphans)}")
    orphan_with_consumer = sum(1 for v in orphans.values() if v["consumers"])
    print(f"  其中被 @Value/条件注解 消费 : {orphan_with_consumer}")
    print(f"  其中**无任何消费方**       : {len(orphans) - orphan_with_consumer}")

    report = []
    report.append(f"# 配置键对账分析（前缀 `{args.prefix}`）\n")
    report.append(f"- yml 文件：{len(yml_files)}")
    report.append(f"- `@ConfigurationProperties` 前缀：{len(prefixes)}")
    report.append(f"- `{args.prefix}.*` 叶子键：{len(scoped)}")
    report.append(f"- 有归属前缀：{len(scoped) - len(orphans)}")
    report.append(f"- **孤儿键（无任何绑定类）：{len(orphans)}**（其中 {orphan_with_consumer} 个有非绑定消费方）\n")

    report.append("## 孤儿键（能设、不报错、无人读）\n")
    report.append("| 键 | yml | 非绑定消费方 |")
    report.append("|---|---|---|")
    for key, info in sorted(orphans.items()):
        report.append(f"| `{key}` | {', '.join(info['yml'])} | {', '.join(info['consumers']) or '**无**'} |")

    report.append("\n## 各前缀下的键数\n")
    report.append("| 前缀 | 键数 | 声明位置 |")
    report.append("|---|---|---|")
    for p in sorted(claimed, key=lambda x: -len(claimed[x])):
        report.append(f"| `{p}` | {len(claimed[p])} | {', '.join(prefixes.get(p, [])[:2])} |")

    report.append("\n## 声明了绑定类、但没有任何键的前缀（潜在死配置类）\n")
    report.append("| 前缀 | 声明位置 |")
    report.append("|---|---|")
    for p, where in sorted(prefixes.items()):
        if p not in claimed:
            report.append(f"| `{p}` | {', '.join(where)} |")

    # ---- 反向扫描：代码读了但 yml 里没有 ----
    reads = collect_config_reads(root)
    reverse = reverse_orphan_keys(reads, keys)
    # 只保留配置属性（含点号已由 add() 保证）；按有无 matchIfMissing=true 分组
    conditional_missing = {}
    other_missing = {}
    for name, where in reverse.items():
        if any("matchIfMissing=true" in w for w in where):
            conditional_missing[name] = where
        else:
            other_missing[name] = where

    print()
    print(f"代码读取的配置键          : {len(reads)}")
    print(f"反向孤儿（yml 里没有）    : {len(reverse)}")
    print(f"  其中 ConditionalOnProperty(matchIfMissing=true) : {len(conditional_missing)}")
    print(f"  其它（无默认 / 只有默认值兜底）                 : {len(other_missing)}")

    report.append(f"\n## 反向孤儿：代码读了但 yml 里没有任何键（共 {len(reverse)}）\n")
    report.append("`matchIfMissing=true` 的是**有意的默认开关**（合法）；其余要么靠代码默认值兜底、"
                  "要么就是缺陷。\n")
    report.append("| 键 | 来源 | matchIfMissing=true |")
    report.append("|---|---|---|")
    for name in sorted(reverse):
        where = reverse[name]
        has_default = "是" if any("matchIfMissing=true" in w for w in where) else ""
        report.append(f"| `{name}` | {', '.join(sorted(set(where))[:3])} | {has_default} |")

    (outdir / "config-keys.md").write_text("\n".join(report), encoding="utf-8")
    (outdir / "config-keys.json").write_text(
        json.dumps(
            {
                "scope": args.prefix,
                "yml_files": yml_files,
                "prefixes": prefixes,
                "orphans": orphans,
                "claimed_counts": {p: len(v) for p, v in claimed.items()},
                "prefixes_without_keys": [p for p in prefixes if p not in claimed],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\n报告：{(outdir / 'config-keys.md').relative_to(root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
