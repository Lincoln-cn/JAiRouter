"""把 @ConditionalOnProperty(prefix+name) / @Value / getProperty 还原成完整键路径，
与「严格绑定不上的键」求差集 —— 目的是防住 prefix+name 拼接写法被按全键名 grep 漏掉。

用法（在仓库根执行）：
  python scripts/audit/reconstruct-consumer-paths.py            # 全量差集
  python scripts/audit/reconstruct-consumer-paths.py --prefix jairouter.security.monitoring
"""
import argparse
import io
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "src" / "main" / "java"
SWEEP = ROOT / "target" / "config-binding-sweep.txt"

RE_COND = re.compile(r"@ConditionalOnProperty\s*\(([^)]*)\)", re.S)
RE_VALUE = re.compile(r'@Value\s*\(\s*"[^"]*?\$\{([^":}]+)')
RE_GETPROP = re.compile(r'getProperty\s*\(\s*"([^"]+)"')
RE_PREFIX = re.compile(r'prefix\s*=\s*"([^"]+)"')
RE_NAME = re.compile(r'(?:name|value)\s*=\s*(?:"([^"]+)"|\{([^}]*)\})')
RE_STRING = re.compile(r'"([^"]+)"')


def cond_paths(argtext):
    """@ConditionalOnProperty 的参数 → 完整键路径列表（含 prefix+name 拼接）"""
    prefix = RE_PREFIX.search(argtext)
    prefix = prefix.group(1) if prefix else None
    paths = []
    for m in RE_NAME.finditer(argtext):
        if m.group(1):
            names = [m.group(1)]
        else:
            names = RE_STRING.findall(m.group(2) or "")
        for name in names:
            paths.append(f"{prefix}.{name}" if prefix else name)
    return paths


def collect(text):
    out = set()
    for m in RE_COND.finditer(text):
        out.update(cond_paths(m.group(1)))
    for m in RE_VALUE.finditer(text):
        out.add(m.group(1).strip())
    for m in RE_GETPROP.finditer(text):
        out.add(m.group(1).strip())
    return out


def sweep_unbound():
    keys, in_sec = [], False
    with io.open(SWEEP, encoding="utf-8") as f:
        for line in f:
            if line.startswith("---- 严格绑定不上的键"):
                in_sec = True
                continue
            if not in_sec:
                continue
            m = re.match(r"^    ([a-zA-Z][\w.\-]*)\s*$", line)
            if m:
                keys.append(m.group(1))
    return sorted(set(keys))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefix", default="")
    ap.add_argument("--json-out", default=str(ROOT / ".tmp-e2e" / "consumer-paths.json"))
    args = ap.parse_args()

    referenced = set()
    for p in JAVA_ROOT.rglob("*.java"):
        referenced.update(collect(p.read_text(encoding="utf-8", errors="ignore")))

    unbound = sweep_unbound()
    if args.prefix:
        unbound = [k for k in unbound if k.startswith(args.prefix)]

    really_dead = [k for k in unbound if k not in referenced]
    still_used = [k for k in unbound if k in referenced]

    print(f"注解/代码引用到的完整键路径 : {len(referenced)}")
    print(f"扫描器判为未绑定的键        : {len(unbound)}")
    print(f"  其中**被代码引用**（不可删）: {len(still_used)}")
    print(f"  其中确实无引用（可删）      : {len(really_dead)}")

    if still_used:
        print("\n!! 这些键被 prefix+name 拼接引用，按全键名 grep 会漏，绝不能删：")
        for k in still_used:
            print("   " + k)
    if really_dead:
        print("\n可删清单：")
        for k in really_dead:
            print("   " + k)

    io.open(args.json_out, "w", encoding="utf-8").write(
        json.dumps({"referenced": sorted(referenced), "unbound": unbound,
                    "still_used": still_used, "really_dead": really_dead},
                   ensure_ascii=False, indent=1))
    print(f"\nJSON → {args.json_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
