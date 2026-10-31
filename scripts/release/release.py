#!/usr/bin/env python3
"""JAiRouter 一键发布助手.

本仓的发布流程有七处版本号 + 两个发布面（changelog / roadmap），纯手工改很容易漏。
这个脚本把「检查 → 改版本号 → 打 tag 发 Release」串起来，默认 **dry-run**，任何写操作都要显式
加 ``--write`` / ``--yes``。

用法::

    # 1) 发布前自检（只读；CI 里的 ReleaseVersionConsistencyTest 是同一套判据的测试版）
    python scripts/release/release.py check

    # 2) 预演把版本号改到 3.5.9（只打印将要改什么）
    python scripts/release/release.py bump --version 3.5.9 --date 2026-11-02

    # 3) 真改
    python scripts/release/release.py bump --version 3.5.9 --date 2026-11-02 --write

    # 4) 提交并发 Release（tag 打在 HEAD 上；先确认 PR 已合并、本地 master 与远端一致）
    python scripts/release/release.py tag --version 3.5.9 --notes release-notes.md --yes

判据来源：``docs/zh/development/contributing.md`` 的「发布检查清单」（七处版本号必须一致）。
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

POM = "pom.xml"
PACKAGE_JSON = "frontend/package.json"
README = "README.md"
README_ZH = "README-ZH.md"
CHANGELOG_ZH = "docs/zh/reference/changelog.md"
CHANGELOG_EN = "docs/en/reference/changelog.md"
ROADMAP_ZH = "docs/zh/reference/roadmap.md"
ROADMAP_EN = "docs/en/reference/roadmap.md"


# --------------------------------------------------------------------------- 读写（保留 CRLF）

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"缺少文件: {rel}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        return handle.read()


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    with path.open("w", encoding="utf-8", newline="") as handle:
        handle.write(text)


def newline_of(text: str) -> str:
    return "\r\n" if "\r\n" in text else "\n"


def sub_once(text: str, pattern: str, replacement: str, where: str, flags: int = 0) -> str:
    """替换且断言「恰好替换成功一次」，避免脚本静默什么都没改。"""
    new_text, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{where}: 没匹配到模式 {pattern!r} —— 文件结构可能变了，请先人工核对")
    return new_text


# --------------------------------------------------------------------------- 版本号读取

def project_version() -> str:
    pom = read(POM)
    anchor = re.search(r"<artifactId>model-router</artifactId>", pom)
    if not anchor:
        raise SystemExit("pom.xml 里找不到 <artifactId>model-router</artifactId>")
    match = re.compile(r"<version>([^<]+)</version>").search(pom, anchor.end())
    if not match:
        raise SystemExit("model-router 之后找不到 <version>（pom 结构变了）")
    return match.group(1).strip()


def expected_places(version: str) -> dict[str, str]:
    """readme/changelog/roadmap 里应当等于 version 的位置 → 用的正则。"""
    return {
        f"{README}: project_version": r"^project_version:\s*(\S+)",
        f"{README_ZH}: project_version": r"^project_version:\s*(\S+)",
        f"{PACKAGE_JSON}: version": r'"version"\s*:\s*"([^"]+)"',
        f"{CHANGELOG_ZH}: 文档版本": r"\*\*文档版本\*\*:\s*(\S+)",
        f"{CHANGELOG_EN}: Document Version": r"\*\*Document Version\*\*:\s*(\S+)",
        f"{ROADMAP_ZH}: Git 标签": r"\*\*Git 标签\*\*:\s*v(\S+)",
        f"{ROADMAP_EN}: Git Tag": r"\*\*Git Tag\*\*:\s*v(\S+)",
    }


def latest_release_versions() -> list[tuple[str, str]]:
    out: list[tuple[str, str]] = []
    for rel in (README, README_ZH):
        text = read(rel)
        match = re.search(r"^latest_release:\s*v(\S+?)\s*\(", text, re.MULTILINE)
        out.append((f"{rel}: latest_release", match.group(1).strip() if match else "<未找到>"))
    return out


# --------------------------------------------------------------------------- check

def cmd_check(args: argparse.Namespace) -> int:
    version = project_version()
    print(f"[release] pom 项目版本: {version}")
    problems: list[str] = []

    for where, pattern in expected_places(version).items():
        rel = where.split(":")[0]
        match = re.search(pattern, read(rel), re.MULTILINE)
        actual = match.group(1).strip() if match else "<未找到>"
        if actual != version:
            problems.append(f"{where} = {actual}（应为 {version}）")

    for where, actual in latest_release_versions():
        if actual != version:
            problems.append(f"{where} = {actual}（应为 {version}）")

    for rel in (CHANGELOG_ZH, CHANGELOG_EN):
        if f"### [{version}]" not in read(rel):
            problems.append(f"{rel} 缺少 ### [{version}] 条目")

    if args.version and args.version != version:
        problems.append(f"pom.xml 项目版本是 {version}，与 --version {args.version} 不一致")

    tag = f"v{version}"
    tag_exists = subprocess.run(["git", "rev-parse", "--verify", f"refs/tags/{tag}"],
                                cwd=ROOT, capture_output=True).returncode == 0
    if tag_exists:
        message = f"本地已存在 tag {tag}（这一版已经发过了）"
        if args.expect_unreleased:
            problems.append(message + "；若要在同一版本号上再发一次，请先删 tag 或换版本号")
        else:
            print(f"[release] ℹ️ {message}；只想校验一致性可忽略，准备发新版请先 bump")

    if problems:
        print("[release] ❌ 不一致：")
        for item in problems:
            print(f"  - {item}")
        print("[release] 修复: python scripts/release/release.py bump --version "
              f"{version} --write")
        return 1

    print("[release] ✅ 七处版本号一致、changelog 有当前版本条目")
    return 0


# --------------------------------------------------------------------------- bump

def cmd_bump(args: argparse.Namespace) -> int:
    version = args.version
    date = args.date
    current = project_version()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit(f"--version 必须是 X.Y.Z，收到 {version!r}")
    if version == current:
        print(f"[release] 版本号已经是 {version}，只做一致性检查")
        return cmd_check(argparse.Namespace(version=version, expect_unreleased=False))

    plan: list[tuple[str, str, str, int]] = []

    pom = read(POM)
    anchor = re.search(r"<artifactId>model-router</artifactId>", pom)
    match = re.compile(r"<version>([^<]+)</version>").search(pom, anchor.end())
    start, end = match.start(1), match.end(1)
    plan.append((POM, pom[:start] + version + pom[end:], f"项目版本 {current} → {version}", 0))

    package = read(PACKAGE_JSON)
    plan.append((PACKAGE_JSON,
                 sub_once(package, r'"version"\s*:\s*"[^"]+"', f'"version": "{version}"', PACKAGE_JSON),
                 f"version → {version}", 0))

    for rel in (README, README_ZH):
        text = read(rel)
        text = sub_once(text, r"^project_version:\s*\S+", f"project_version: {version}", rel, re.MULTILINE)
        text = sub_once(text, r"^latest_release:\s*v\S+?(\s*\()",
                        f"latest_release:  v{version} ( {date})", rel, re.MULTILINE)
        plan.append((rel, text, f"project_version / latest_release → {version} ({date})", 0))

    zh = read(CHANGELOG_ZH)
    zh = sub_once(zh, r"\*\*文档版本\*\*:\s*\S+", f"**文档版本**: {version}", CHANGELOG_ZH)
    zh = sub_once(zh, r"\*\*最后更新\*\*:\s*\S+", f"**最后更新**: {date}", CHANGELOG_ZH)
    plan.append((CHANGELOG_ZH, zh, f"文档版本 → {version} / 最后更新 → {date}", 0))

    en = read(CHANGELOG_EN)
    en = sub_once(en, r"\*\*Document Version\*\*:\s*\S+", f"**Document Version**: {version}", CHANGELOG_EN)
    en = sub_once(en, r"\*\*Last Updated\*\*:\s*\S+", f"**Last Updated**: {date}", CHANGELOG_EN)
    plan.append((CHANGELOG_EN, en, f"Document Version → {version} / Last Updated → {date}", 0))

    for rel, label in ((ROADMAP_ZH, "Git 标签"), (ROADMAP_EN, "Git Tag")):
        text = read(rel)
        text = sub_once(text, rf"\*\*{label}\*\*:\s*v\S+", f"**{label}**: v{version}", rel)
        plan.append((rel, text, f"{label} → v{version}", 0))

    for rel, _, summary, _ in plan:
        print(f"[release] 将修改 {rel}: {summary}")

    missing = [rel for rel in (CHANGELOG_ZH, CHANGELOG_EN) if f"### [{version}]" not in read(rel)]
    for rel in missing:
        print(f"[release] ⚠️ {rel} 还没有 ### [{version}] 条目 —— 脚本不会替你写发布说明，"
              f"请先按 contributing.md 的「发布说明只写本区间变化」自行补上")

    if not args.write:
        print("[release] 这是 dry-run。加 --write 才会真正写入。")
        return 0

    for rel, text, _, _ in plan:
        write(rel, text)
    print(f"[release] ✅ 已写入 {len(plan)} 个文件；接下来: git diff 自查 → 提交 → PR → 合并后 tag")
    return 0


# --------------------------------------------------------------------------- tag

def git(*argv: str) -> str:
    result = subprocess.run(["git", *argv], cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"git {' '.join(argv)} 失败:\n{result.stderr.strip()}")
    return result.stdout.strip()


def cmd_tag(args: argparse.Namespace) -> int:
    version = args.version
    tag = f"v{version}"
    current = project_version()
    if version != current:
        raise SystemExit(f"pom 项目版本是 {current}，与 --version {version} 不一致；先 bump 再发")

    dirty = git("status", "--porcelain")
    if dirty:
        raise SystemExit("工作区不干净，先提交或 stash：\n" + dirty)
    if git("rev-parse", "--abbrev-ref", "HEAD") != "master":
        raise SystemExit("请在 master 上打 tag（本仓口径：tag 打在已合并的 squash 提交上）")

    head = git("rev-parse", "HEAD")
    notes = Path(args.notes)
    if not notes.is_file():
        raise SystemExit(f"缺少发布说明文件: {notes}")

    print(f"[release] 将在 {head[:8]} 上打 {tag}，并创建 GitHub Release")
    if not args.yes:
        print("[release] 这是 dry-run。加 --yes 才会执行 tag / push / gh release create。")
        return 0

    git("tag", "-a", tag, head, "-m", args.message or f"{tag}: release")
    git("push", "github", tag)
    subprocess.run(["gh", "release", "create", tag, "--title", args.title or tag,
                    "--notes-file", str(notes)], cwd=ROOT, check=True)
    print(f"[release] ✅ 已发布 {tag}（Release 创建后才触发 Docker 镜像 workflow）")
    return 0


# --------------------------------------------------------------------------- main

def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="JAiRouter 一键发布助手（默认 dry-run）")
    subs = parser.add_subparsers(dest="command", required=True)

    check = subs.add_parser("check", help="只读校验：七处版本号 + changelog 条目 + tag 未占用")
    check.add_argument("--version")
    check.add_argument("--expect-unreleased", action="store_true",
                       help="把「tag 已存在」当作失败（发布前的自检用）")
    check.set_defaults(func=cmd_check)

    bump = subs.add_parser("bump", help="改版本号（pom/package.json/README×2/changelog×2/roadmap×2）")
    bump.add_argument("--version", required=True)
    bump.add_argument("--date", default=dt.date.today().isoformat())
    bump.add_argument("--write", action="store_true", help="真正写入（默认 dry-run）")
    bump.set_defaults(func=cmd_bump)

    tag = subs.add_parser("tag", help="打 tag + 推 tag + 创建 GitHub Release")
    tag.add_argument("--version", required=True)
    tag.add_argument("--notes", required=True)
    tag.add_argument("--title")
    tag.add_argument("--message")
    tag.add_argument("--yes", action="store_true", help="真正执行（默认 dry-run）")
    tag.set_defaults(func=cmd_tag)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
