#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库重复文件检测器

检测 innerdoc/ 下的完全重复（SHA-256 相同）和近似重复（文件名相似度高）文件。
"""

import argparse
import hashlib
import json
import os
import re
import sys
from collections import defaultdict
from datetime import datetime
from difflib import SequenceMatcher
from pathlib import Path


def compute_sha256(filepath: Path) -> str:
    """计算文件的 SHA-256 哈希"""
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        for chunk in iter(lambda: f.read(8192), b''):
            h.update(chunk)
    return h.hexdigest()


def count_lines(filepath: Path) -> int:
    """计算文件行数"""
    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
        return sum(1 for _ in f)


def normalize_filename(filename: str) -> str:
    """
    规范化文件名用于近似重复检测：
    去除数字、横线、下划线、点、空格，转小写，
    去除常见后缀如 guide/zh/en 等。
    """
    name = filename.lower()
    # 去除扩展名
    name = re.sub(r'\.md$', '', name)
    # 去除数字、横线、下划线、点、空格
    name = re.sub(r'[0-9_\-\. ]+', '', name)
    # 去除常见后缀词
    for suffix in ['guide', 'guideen', 'guidezh', 'summary', 'report',
                   'readme', 'index', 'quickreference', 'quickref']:
        name = name.replace(suffix, '')
    return name


def scan_files(project_root: Path) -> list:
    """扫描 innerdoc 下所有 .md 文件（排除 archive）"""
    innerdoc_root = project_root / "innerdoc"
    files = []

    if not innerdoc_root.exists():
        print(f"错误: innerdoc 目录不存在: {innerdoc_root}", file=sys.stderr)
        sys.exit(1)

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

            files.append({
                "path": rel_path,
                "abs_path": filepath,
                "lines": count_lines(filepath),
                "sha256": compute_sha256(filepath),
                "filename": fname,
                "norm_name": normalize_filename(fname),
            })

    return files


def find_exact_duplicates(files: list) -> list:
    """查找完全重复的文件（SHA-256 相同）"""
    hash_groups = defaultdict(list)
    for f in files:
        hash_groups[f['sha256']].append(f['path'])

    duplicates = []
    for sha, paths in hash_groups.items():
        if len(paths) > 1:
            duplicates.append({"files": sorted(paths)})

    return duplicates


def find_near_duplicates(files: list, threshold: float = 0.8) -> list:
    """查找近似重复的文件（文件名规范化后相似度 > threshold）"""
    near_dupes = []
    n = len(files)

    # 仅对规范化名称非空的文件进行比较
    valid_files = [f for f in files if f['norm_name']]

    for i in range(len(valid_files)):
        group = []
        for j in range(i + 1, len(valid_files)):
            ratio = SequenceMatcher(
                None,
                valid_files[i]['norm_name'],
                valid_files[j]['norm_name']
            ).ratio()

            if ratio > threshold:
                if not group:
                    group.append({
                        "path": valid_files[i]['path'],
                        "lines": valid_files[i]['lines']
                    })
                group.append({
                    "path": valid_files[j]['path'],
                    "lines": valid_files[j]['lines']
                })

        if group:
            near_dupes.append({"members": group})

    return near_dupes


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="知识库重复文件检测器")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--scan", action="store_true", help="执行扫描")
    parser.add_argument("--output", help="输出 JSON 报告文件路径")

    args = parser.parse_args()

    if not args.scan:
        parser.print_help()
        sys.exit(0)

    project_root = Path(args.project_root).resolve()
    files = scan_files(project_root)

    print(f"扫描完成: {len(files)} 个文件")

    # 完全重复检测
    exact_dupes = find_exact_duplicates(files)
    print(f"完全重复组: {len(exact_dupes)}")

    # 近似重复检测
    near_dupes = find_near_duplicates(files, threshold=0.8)
    print(f"近似重复组: {len(near_dupes)}")

    result = {
        "scan_time": datetime.now().isoformat(),
        "exact_duplicates": exact_dupes,
        "near_duplicates": near_dupes,
    }

    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"报告已保存: {args.output}")


if __name__ == "__main__":
    main()
