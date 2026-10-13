#!/usr/bin/env python3
"""检查文档的 markdown 围栏是否配平（issue #324）。

三类缺陷（前两类会破坏渲染，第三类只可能掩盖第二类）：

  1. **围栏总数是奇数** ⇒ 自错位点起，该点之后的整篇内容都会被当作代码块渲染
     （标题/表格/链接全部失效）。
  2. **两个反引号的开栏**（例如把 ```mermaid 写成 ``mermaid）⇒ 少一个反引号。
  3. **两个反引号的闭栏**（整行就是 ``）⇒ 同上，但它**连围栏总数都不影响**
     （两个反引号不被计入分隔符），所以**只看奇偶永远发现不了它**：实测 `routing-rules.md`
     的某个 YAML 示例开栏与闭栏都写成两个反引号 ⇒ 该块从未真正闭合、后续散文被吞进代码块，
     而该文件的分隔符总数仍是偶数。

  判定「这不是误报」的边界：若你确实要在代码块里**展示**一条围栏，标准做法是用**更长的外层围栏**
  （外侧 4 个反引号、内侧 3 个），本检查因此不会误伤 —— 出现整行两个反引号时，基本都是手误。

用法：
    python scripts/docs/check-fence-parity.py [--dir docs] [--fail-on-error] [--include-ignored]

退出码：0 = 无问题；1 = 有问题（配合 --fail-on-error）。
"""
import argparse
import os
import re
import subprocess
import sys

FENCE = chr(96) * 3
TWO_TICKS_OPENER = re.compile(r"^\s*" + chr(96) * 2 + r"(?!" + chr(96) + r")(\w+)\s*$")
TWO_TICKS_BARE = re.compile(r"^\s*" + chr(96) * 2 + r"\s*$")


def is_ignored(paths, repo_root):
    """用 git 过滤掉被 .gitignore 的本地产物（CI 里它们不存在，本地会误报）。"""
    if not paths:
        return set()
    try:
        proc = subprocess.run(
            ["git", "-C", repo_root, "check-ignore", "--stdin"],
            input="\n".join(paths), capture_output=True, text=True,
        )
    except OSError:
        return set()
    # git 在 Windows 上的输出可能带 \r，且路径含特殊字符时会被引号包起来 ⇒ 两种都要剥掉，
    # 否则「被忽略的路径」会匹配不上，本地产物会被误报成文档缺陷（本脚本存在的主因之一）。
    return {line.strip().strip('"') for line in proc.stdout.splitlines() if line.strip()}


def scan(path):
    problems = []
    with open(path, encoding="utf-8", newline="") as fh:
        lines = fh.read().split("\n")
    fences = 0
    for i, line in enumerate(lines):
        if line.strip().startswith(FENCE):
            fences += 1
        m = TWO_TICKS_OPENER.match(line)
        if m:
            problems.append((i + 1, "two-backtick opener",
                             "%r 只用了两个反引号（应为三个）—— 建议写成 %s%s"
                             % (line.strip(), FENCE, m.group(1))))
            continue
        if TWO_TICKS_BARE.match(line):
            # 这一类最阴：它连「围栏总数」都不影响（不被计入），所以只看奇偶永远发现不了。
            # 实测例子：routing-rules.md 的某个 YAML 示例开栏与闭栏都写成两个反引号 ⇒ 该块
            # 从未真正闭合，后面的散文被吞进代码块，而总数仍是偶数。
            problems.append((i + 1, "two-backtick closer",
                             "%r 只用了两个反引号（应为三个）—— 闭栏同样会失效"
                             % line.strip()))
    if fences % 2:
        problems.append((0, "odd fence count",
                         "围栏分隔符共 %d 个（奇数）⇒ 自错位点起整篇会被当作代码渲染" % fences))
    return problems, fences, len(lines)


def main():
    ap = argparse.ArgumentParser(description="检查 markdown 围栏配平")
    ap.add_argument("--dir", default="docs", help="要扫描的目录（默认 docs）")
    ap.add_argument("--fail-on-error", action="store_true", help="发现问题时返回非零退出码")
    ap.add_argument("--include-ignored", action="store_true",
                    help="不排除被 .gitignore 的文件（默认排除：CI 里这些文件不存在）")
    args = ap.parse_args()

    # 仓库根优先取 git 的答案（脚本可能在任何 cwd 下被调用），退化时才用 __file__
    try:
        top = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                             capture_output=True, text=True, check=True).stdout.strip()
        repo_root = top or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    except (OSError, subprocess.CalledProcessError):
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    targets, seen = [], []
    for dirpath, dirnames, filenames in os.walk(args.dir):
        dirnames[:] = [d for d in dirnames if d not in {".git", "site", "node_modules"}]
        for name in sorted(filenames):
            if name.endswith(".md"):
                targets.append(os.path.join(dirpath, name))

    if not args.include_ignored:
        ignored = is_ignored([os.path.relpath(t, repo_root).replace("\\", "/") for t in targets], repo_root)
        kept = []
        for t in targets:
            rel = os.path.relpath(t, repo_root).replace("\\", "/")
            if rel not in ignored:
                kept.append(t)
        seen = sorted(ignored)
        targets = kept

    bad_files = 0
    total_problems = 0
    for path in targets:
        problems, fences, nlines = scan(path)
        if not problems:
            continue
        bad_files += 1
        rel = os.path.relpath(path, repo_root).replace("\\", "/")
        print("❌ %s（%d 行，围栏 %d 个）" % (rel, nlines, fences))
        for lineno, kind, msg in problems:
            total_problems += 1
            loc = ("%d:" % lineno) if lineno else "-"
            print("     %-8s %s %s" % (loc, kind, msg))

    if seen:
        print("ℹ️  已跳过 %d 个被 .gitignore 的路径（用 --include-ignored 可一并检查）：%s"
              % (len(seen), ", ".join(seen[:5]) + (" …" if len(seen) > 5 else "")))
    print("总计：检查 %d 个 markdown 文件，%d 个文件有问题，%d 处问题"
          % (len(targets), bad_files, total_problems))
    if bad_files and args.fail_on_error:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
