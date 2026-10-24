#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""``check-fence-parity.py`` 的自测（issue #607）。

覆盖三类判定，且每类都做**双向**验证（该红的红、该绿的绿）：

1. 配平的三反引号围栏 ⇒ 通过；
2. 围栏总数是奇数 ⇒ 红（``odd fence count``）；
3. 两个反引号的开栏 / 闭栏 ⇒ 红（``two-backtick opener`` / ``two-backtick closer``），
   其中闭栏那类**不影响奇偶**，只有专门规则能抓到。

夹具建在仓库内（``target/fence-parity-selftest-*``）：Windows 上系统临时目录常在另一个盘符，
而 git 的忽略查询需要仓库内的相对路径。

用法：``python3 scripts/docs/test_check_fence_parity.py``；退出码 0 = 自测通过。
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "check-fence-parity.py")
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
FENCE = "`" * 3


def run_case(name, content, expect_ok, expect_text=None):
    parent = os.path.join(REPO_ROOT, "target")
    os.makedirs(parent, exist_ok=True)
    workdir = tempfile.mkdtemp(prefix="fence-parity-selftest-", dir=parent)
    try:
        path = os.path.join(workdir, name + ".md")
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(content)
        proc = subprocess.run(
            [sys.executable, SCRIPT, "--dir", workdir, "--fail-on-error", "--include-ignored"],
            capture_output=True,
            text=True,
            cwd=REPO_ROOT,
        )
        output = proc.stdout + proc.stderr
        ok = (proc.returncode == 0) if expect_ok else (proc.returncode != 0)
        if not ok:
            print("❌ %s：退出码 %d（预期 %s）\n%s"
                  % (name, proc.returncode, "0" if expect_ok else "非 0", output))
            return False
        if expect_text and expect_text not in output:
            print("❌ %s：输出里没有 %r\n%s" % (name, expect_text, output))
            return False
        print("✅ %s" % name)
        return True
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def main():
    cases = []

    balanced = ("# 标题\n\n" + FENCE + "yaml\nkey: value\n" + FENCE + "\n\n正文。\n")
    cases.append(run_case("balanced", balanced, True))

    odd = ("# 标题\n\n" + FENCE + "yaml\nkey: value\n\n正文被吞进代码块。\n")
    cases.append(run_case("odd", odd, False, "odd fence count"))

    two_tick_opener = ("# 标题\n\n" + "``" + "mermaid\ngraph TD;\n" + FENCE + "\n\n正文。\n")
    cases.append(run_case("two_tick_opener", two_tick_opener, False, "two-backtick opener"))

    # 开栏、闭栏都只有两个反引号 ⇒ 分隔符总数仍是偶数，只有专门规则能发现
    two_tick_closer = ("# 标题\n\n" + FENCE + "yaml\nkey: value\n" + "``" + "\n\n正文。\n")
    cases.append(run_case("two_tick_closer", two_tick_closer, False, "two-backtick closer"))

    ok = all(cases)
    print("\n自测结果：%d/%d 通过" % (sum(1 for c in cases if c), len(cases)))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
