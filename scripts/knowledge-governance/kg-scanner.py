#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库扫描器

扫描 innerdoc/ 下所有 .md 文件，记录元信息并按目录分类。
输出 JSON 和可选 Markdown 报告。
"""

import argparse
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime
from pathlib import Path


# --- 一次性文件启发式模式 ---
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


def extract_status(content: str) -> str:
    """从文件内容中提取状态标记"""
    for line in content.splitlines():
        # 匹配 "状态:" 或状态 emoji 标记
        if re.search(r'状态[:：]', line):
            return line.strip()
        if re.search(r'✅已完成|🔶进行中|❌已废弃', line):
            return line.strip()
    return ""


def extract_date(content: str) -> str:
    """从文件内容中提取日期标记 (YYYY-MM-DD)"""
    for line in content.splitlines():
        match = re.search(r'(\d{4}-\d{2}-\d{2})', line)
        if match:
            return match.group(1)
    return ""


def read_pom_version(project_root: Path) -> str:
    """从 pom.xml 读取版本号"""
    pom_path = project_root / "pom.xml"
    if not pom_path.exists():
        return "unknown"
    try:
        tree = ET.parse(pom_path)
        root = tree.getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        # 先查 project/version
        ver_elem = root.find('./m:version', ns)
        if ver_elem is not None and ver_elem.text:
            return ver_elem.text.strip()
        # 回退：正则搜索
        with open(pom_path, 'r', encoding='utf-8') as f:
            text = f.read()
        match = re.search(r'<version>([^<]+)</version>', text)
        if match:
            return match.group(1).strip()
    except Exception:
        pass
    return "unknown"


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


def scan_innerdoc(project_root: Path, classify: bool = False) -> dict:
    """扫描 innerdoc 目录，返回结构化数据"""
    innerdoc_root = project_root / "innerdoc"
    if not innerdoc_root.exists():
        print(f"错误: innerdoc 目录不存在: {innerdoc_root}", file=sys.stderr)
        sys.exit(1)

    pom_version = read_pom_version(project_root)
    archive_paths = read_archive_list(innerdoc_root)

    files_data = []
    categories = defaultdict(lambda: {"file_count": 0, "files": []})
    total_lines = 0

    for dirpath, dirnames, filenames in os.walk(innerdoc_root):
        # 跳过 archive 子目录
        rel_dir = os.path.relpath(dirpath, innerdoc_root)
        if rel_dir.startswith("archive") and rel_dir != ".":
            continue

        for fname in filenames:
            if not fname.endswith('.md'):
                continue

            filepath = Path(dirpath) / fname
            rel_path = str(filepath.relative_to(innerdoc_root)).replace('\\', '/')

            # 跳过 archive 子目录下的文件
            if rel_path.startswith("archive/"):
                continue

            lines = count_lines(filepath)
            sha256 = compute_sha256(filepath)
            total_lines += lines

            with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()

            status_marker = extract_status(content)
            date_marker = extract_date(content)

            # 分类
            classification = "durable"
            archive_reason = ""

            if classify:
                if is_one_off(fname):
                    classification = "one-off"
                    archive_reason = "文件名匹配一次性文档模式"
                elif rel_path in archive_paths:
                    classification = "archive-candidate"
                    archive_reason = "在 archive-list.txt 中列出"

            file_entry = {
                "path": rel_path,
                "lines": lines,
                "sha256": sha256,
                "status_marker": status_marker,
                "date_marker": date_marker,
                "classification": classification,
                "archive_reason": archive_reason,
            }
            files_data.append(file_entry)

            # 分类目录统计（取第一级子目录）
            if '/' in rel_path:
                cat_dir = rel_path.split('/')[0]
            else:
                cat_dir = "(root)"
            categories[cat_dir]["file_count"] += 1
            categories[cat_dir]["files"].append(rel_path)

    result = {
        "scan_time": datetime.now().isoformat(),
        "pom_version": pom_version,
        "total_files": len(files_data),
        "total_lines": total_lines,
        "categories": dict(categories),
        "files": files_data,
    }
    return result


def write_markdown_report(data: dict, output_path: Path):
    """生成 Markdown 格式的扫描报告"""
    lines = []
    lines.append("# Innerdoc 扫描报告\n")
    lines.append(f"> 扫描时间: {data['scan_time']}")
    lines.append(f"> pom.xml 版本: {data['pom_version']}")
    lines.append(f"> 总文件数: {data['total_files']}")
    lines.append(f"> 总行数: {data['total_lines']}\n")
    lines.append("---\n")
    lines.append("## 分类统计\n")
    lines.append("| 分类 | 文件数 |")
    lines.append("|------|--------|")
    for cat, info in sorted(data['categories'].items()):
        lines.append(f"| {cat} | {info['file_count']} |")
    lines.append("")

    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="知识库扫描器 - 扫描 innerdoc 目录")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--scan", action="store_true", help="执行扫描")
    parser.add_argument("--classify", action="store_true", help="启用文件分类（一次性/归档候选）")
    parser.add_argument("--output", help="输出 JSON 报告文件路径")
    parser.add_argument("--output-md", help="输出 Markdown 报告文件路径")

    args = parser.parse_args()

    if not args.scan:
        parser.print_help()
        sys.exit(0)

    project_root = Path(args.project_root).resolve()
    data = scan_innerdoc(project_root, classify=args.classify)

    # 打印摘要
    print(f"扫描完成: {data['total_files']} 个文件, {data['total_lines']} 行")
    print(f"pom.xml 版本: {data['pom_version']}")
    print(f"分类数: {len(data['categories'])}")

    if args.classify:
        one_off = sum(1 for f in data['files'] if f['classification'] == 'one-off')
        archive_cand = sum(1 for f in data['files'] if f['classification'] == 'archive-candidate')
        print(f"一次性文档: {one_off}, 归档候选: {archive_cand}")

    # 输出 JSON
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"JSON 报告已保存: {args.output}")

    # 输出 Markdown
    if args.output_md:
        write_markdown_report(data, Path(args.output_md))
        print(f"Markdown 报告已保存: {args.output_md}")


if __name__ == "__main__":
    main()
