#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库归档移动器

将标记为归档的文件从 innerdoc/ 移动到 innerdoc/archive/ 目录下。
支持从 archive-list.txt 读取或命令行指定路径。
"""

import argparse
import json
import os
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path


# 受保护的目录（绝不归档）
PROTECTED_DIRS = ["knowledge-base", "16-版本发布"]


def is_protected(rel_path: str) -> bool:
    """检查文件是否在受保护目录下"""
    for protected in PROTECTED_DIRS:
        if rel_path.startswith(protected + "/") or rel_path.startswith(protected + "\\"):
            return True
    return False


def read_archive_list(innerdoc_root: Path) -> list:
    """读取 archive-list.txt 中列出的文件路径"""
    archive_list_path = innerdoc_root / "archive-list.txt"
    if not archive_list_path.exists():
        return []
    paths = []
    with open(archive_list_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                paths.append(line)
    return paths


# 一次性文件启发式模式（与 scanner 一致）
ONE_OFF_PATTERNS = [
    r'fix.*summary', r'fix.*verification', r'fix.*completion',
    r'DEBUG', r'DIAGNOSTIC', r'temp', r'LOADING_FIX',
    r'test-execution-report',
]


def is_one_off(filename: str) -> bool:
    """判断文件名是否匹配一次性文档模式"""
    lower = filename.lower()
    for pat in ONE_OFF_PATTERNS:
        if re.search(pat, lower, re.IGNORECASE):
            return True
    return False


def generate_plan(project_root: Path, paths: list = None) -> dict:
    """生成归档计划"""
    innerdoc_root = project_root / "innerdoc"
    if not innerdoc_root.exists():
        print(f"错误: innerdoc 目录不存在: {innerdoc_root}", file=sys.stderr)
        sys.exit(1)

    archive_list = read_archive_list(innerdoc_root)

    # 确定要归档的文件列表
    if paths:
        # 使用命令行指定的路径
        target_files = paths
    elif archive_list:
        # 使用 archive-list.txt 中的路径
        target_files = archive_list
    else:
        # 使用启发式分类：扫描所有文件，找出匹配 one-off 的
        target_files = []
        for dirpath, dirnames, filenames in os.walk(innerdoc_root):
            rel_dir = os.path.relpath(dirpath, innerdoc_root)
            if rel_dir.startswith("archive") and rel_dir != ".":
                continue
            for fname in filenames:
                if not fname.endswith('.md'):
                    continue
                filepath = Path(dirpath) / fname
                rel_path = str(filepath.relative_to(innerdoc_root)).replace('\\', '/')
                if rel_path.startswith("archive/"):
                    continue
                if is_one_off(fname):
                    target_files.append(rel_path)

    # 构建归档计划
    moves = []
    protected_files = []

    for rel_path in target_files:
        # 标准化路径分隔符
        rel_path = rel_path.replace('\\', '/')

        if is_protected(rel_path):
            protected_files.append(rel_path)
            continue

        src = innerdoc_root / rel_path
        if not src.exists():
            print(f"警告: 文件不存在，跳过: {rel_path}", file=sys.stderr)
            continue

        # 计算目标路径: innerdoc/archive/<原分类目录>/<文件名>
        parts = rel_path.split('/')
        if len(parts) >= 2:
            cat_dir = parts[0]
            basename = parts[-1]
        else:
            cat_dir = "(root)"
            basename = parts[0]

        dst_rel = f"archive/{cat_dir}/{basename}"
        dst = innerdoc_root / dst_rel

        moves.append({
            "src": rel_path,
            "dst": dst_rel,
            "reason": "在 archive-list.txt 中列出" if rel_path in archive_list
                      else "文件名匹配一次性文档模式"
        })

    plan = {
        "moves": moves,
        "total": len(moves),
        "protected_skipped": protected_files,
    }

    return plan


def execute_moves(project_root: Path, plan: dict, dry_run: bool = False):
    """执行归档移动"""
    innerdoc_root = project_root / "innerdoc"

    if plan['protected_skipped']:
        print(f"\n受保护文件（跳过）:")
        for f in plan['protected_skipped']:
            print(f"  [受保护] {f}")

    if not plan['moves']:
        print("\n没有需要移动的文件。")
        return

    if dry_run:
        print(f"\n[DRY-RUN] 归档计划（共 {plan['total']} 个文件）:")
        for move in plan['moves']:
            print(f"  {move['src']} -> {move['dst']}")
            print(f"    原因: {move['reason']}")
        print(f"\n[DRY-RUN] 使用 --execute 执行实际移动")
    else:
        print(f"\n执行归档（共 {plan['total']} 个文件）:")
        for move in plan['moves']:
            src = innerdoc_root / move['src']
            dst = innerdoc_root / move['dst']

            # 创建目标目录
            dst.parent.mkdir(parents=True, exist_ok=True)

            if dst.exists():
                print(f"  [跳过] 目标已存在: {move['dst']}")
                continue

            shutil.move(str(src), str(dst))
            print(f"  [移动] {move['src']} -> {move['dst']}")

        print(f"\n归档完成: {plan['total']} 个文件")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="知识库归档移动器")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--plan", help="输出归档计划 JSON 文件路径")
    parser.add_argument("--execute", action="store_true", help="执行归档移动")
    parser.add_argument("--dry-run", action="store_true", help="仅显示计划，不执行")
    parser.add_argument("--paths", help="指定要归档的文件路径（逗号分隔）")

    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()

    # 解析路径参数
    paths = None
    if args.paths:
        paths = [p.strip() for p in args.paths.split(',')]

    # 生成计划
    plan = generate_plan(project_root, paths)

    print(f"归档计划: {plan['total']} 个文件待归档")

    # 受保护文件检查
    if plan['protected_skipped']:
        protected_errors = []
        for f in plan['protected_skipped']:
            protected_errors.append(f)
        if protected_errors and args.execute:
            print(f"错误: 以下受保护目录中的文件不能归档:")
            for f in protected_errors:
                print(f"  {f}")
            # 注意：只阻止受保护文件，其他文件仍然可以归档
            print("受保护文件已跳过，其他文件继续处理。")

    # 保存计划
    if args.plan:
        with open(args.plan, 'w', encoding='utf-8') as f:
            json.dump(plan, f, ensure_ascii=False, indent=2)
        print(f"归档计划已保存: {args.plan}")

    # 执行
    if args.execute:
        execute_moves(project_root, plan, dry_run=False)
    elif args.dry_run or not args.execute:
        execute_moves(project_root, plan, dry_run=True)


if __name__ == "__main__":
    main()
