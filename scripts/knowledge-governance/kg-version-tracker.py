#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库版本追踪器

管理 innerdoc/ 的 docs-versions.json，重新生成 INDEX.json，
检查过时文件。
"""

import argparse
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path


def compute_sha256(filepath: Path) -> str:
    """计算文件的 SHA-256 哈希"""
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        for chunk in iter(lambda: f.read(8192), b''):
            h.update(chunk)
    return h.hexdigest()


def content_hash_short(filepath: Path) -> str:
    """计算内容短哈希（16位十六进制），与 docs-versions.json 格式一致"""
    full = compute_sha256(filepath)
    return full[:16]


def get_mtime_iso(filepath: Path) -> str:
    """获取文件修改时间的 ISO 格式"""
    mtime = os.path.getmtime(filepath)
    dt = datetime.fromtimestamp(mtime)
    return dt.isoformat()


def count_lines(filepath: Path) -> int:
    """计算文件行数"""
    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
        return sum(1 for _ in f)


def scan_innerdoc_files(project_root: Path) -> list:
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
            })

    return files


def read_docs_versions_template() -> dict:
    """读取 docs/docs-versions.json 以了解字段结构"""
    return {
        "FilePath": "",
        "Version": "1.0.0",
        "LastModified": "",
        "ContentHash": "",
        "GitCommit": "",
        "Author": "team",
        "ChangeSummary": "",
        "Dependencies": [],
    }


def generate_docs_versions(project_root: Path, files: list) -> dict:
    """生成 innerdoc 的 docs-versions.json"""
    versions = {}

    for f in files:
        key = f"innerdoc/{f['path']}"
        versions[key] = {
            "FilePath": key,
            "Version": "1.0.0",
            "LastModified": get_mtime_iso(f['abs_path']),
            "ContentHash": content_hash_short(f['abs_path']),
            "GitCommit": "",
            "Author": "team",
            "ChangeSummary": "",
            "Dependencies": [],
        }

    return {
        "versions": versions,
        "changes": [],
    }


def read_pom_version(project_root: Path) -> str:
    """从 pom.xml 读取版本号"""
    pom_path = project_root / "pom.xml"
    if not pom_path.exists():
        return "unknown"
    try:
        import xml.etree.ElementTree as ET
        tree = ET.parse(pom_path)
        root = tree.getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        ver_elem = root.find('./m:version', ns)
        if ver_elem is not None and ver_elem.text:
            return ver_elem.text.strip()
    except Exception:
        pass
    return "unknown"


def generate_index_json(project_root: Path, files: list) -> dict:
    """重新生成 INDEX.json"""
    innerdoc_root = project_root / "innerdoc"

    # 按分类目录组织
    category_map = {}
    total_lines = 0

    for f in files:
        rel_path = f['path']
        total_lines += f['lines']

        parts = rel_path.split('/')
        if len(parts) >= 2:
            cat_dir = parts[0]
            file_name = parts[-1]
        else:
            cat_dir = "(root)"
            file_name = rel_path

        if cat_dir not in category_map:
            category_map[cat_dir] = []
        category_map[cat_dir].append({
            "file": file_name,
            "path": rel_path,
            "lines": f['lines'],
        })

    # 构建 categories 结构
    categories = {}
    for cat_dir, cat_files in sorted(category_map.items()):
        # 生成一个简短的 key
        cat_key = re.sub(r'^\d+-', '', cat_dir).replace(' ', '_')
        if cat_key == "(root)":
            cat_key = "root"

        categories[cat_key] = {
            "name": re.sub(r'^\d+-', '', cat_dir),
            "path": cat_dir,
            "file_count": len(cat_files),
            "docs": [{"file": cf["file"], "path": cf["path"]} for cf in cat_files],
        }

    pom_version = read_pom_version(project_root)

    return {
        "version": pom_version,
        "updated": datetime.now().strftime("%Y-%m-%d"),
        "stats": {
            "total_files": len(files),
            "total_lines": total_lines,
            "categories": len(categories),
        },
        "categories": categories,
        "tags": {},
    }


def update_readme_innerdoc(project_root: Path, total_files: int, pom_version: str):
    """更新 README-INNERDOC.md 的头部统计信息"""
    readme_path = project_root / "innerdoc" / "README-INNERDOC.md"
    if not readme_path.exists():
        print("README-INNERDOC.md 不存在，跳过更新")
        return

    with open(readme_path, 'r', encoding='utf-8') as f:
        content = f.read()

    updated = content

    # 更新 "当前版本" 行
    updated = re.sub(
        r'> 当前版本：.*',
        f'> 当前版本：v{pom_version}',
        updated
    )

    # 更新 "文档数量" 行
    updated = re.sub(
        r'> 文档数量：.*',
        f'> 文档数量：{total_files}个文件',
        updated
    )

    # 更新 "最后更新" 行
    updated = re.sub(
        r'> 最后更新：.*',
        f'> 最后更新：{datetime.now().strftime("%Y-%m-%d")}',
        updated
    )

    if updated != content:
        with open(readme_path, 'w', encoding='utf-8') as f:
            f.write(updated)
        print("README-INNERDOC.md 头部信息已更新")


def check_outdated(files: list, days: int) -> list:
    """检查超过指定天数未修改的文件（排除 archive 和 16-版本发布）"""
    cutoff = datetime.now() - timedelta(days=days)
    outdated = []

    for f in files:
        rel_path = f['path']

        # 排除 archive 和 16-版本发布
        if rel_path.startswith("archive/"):
            continue
        if rel_path.startswith("16-版本发布/"):
            continue

        mtime = os.path.getmtime(f['abs_path'])
        mod_time = datetime.fromtimestamp(mtime)

        if mod_time < cutoff:
            outdated.append({
                "path": rel_path,
                "last_modified": mod_time.isoformat(),
                "days_old": (datetime.now() - mod_time).days,
            })

    return sorted(outdated, key=lambda x: x['days_old'], reverse=True)


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="知识库版本追踪器")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--scan", action="store_true", help="扫描并生成 docs-versions.json")
    parser.add_argument("--regen-index", action="store_true", help="重新生成 INDEX.json")
    parser.add_argument("--check-outdated", nargs='?', const=30, type=int,
                        help="检查超过 N 天未修改的文件（默认 30 天）")

    args = parser.parse_args()

    if not args.scan and not args.regen_index and args.check_outdated is None:
        parser.print_help()
        sys.exit(0)

    project_root = Path(args.project_root).resolve()
    files = scan_innerdoc_files(project_root)

    print(f"扫描完成: {len(files)} 个文件")

    # --scan: 生成 docs-versions.json
    if args.scan:
        versions_data = generate_docs_versions(project_root, files)
        output_path = project_root / "innerdoc" / "docs-versions.json"
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(versions_data, f, ensure_ascii=False, indent=2)
        print(f"docs-versions.json 已生成: {output_path}")

    # --regen-index: 重新生成 INDEX.json
    if args.regen_index:
        index_data = generate_index_json(project_root, files)
        pom_version = read_pom_version(project_root)

        index_path = project_root / "innerdoc" / "INDEX.json"
        with open(index_path, 'w', encoding='utf-8') as f:
            json.dump(index_data, f, ensure_ascii=False, indent=2)
        print(f"INDEX.json 已重新生成: {index_path}")

        # 同步更新 README-INNERDOC.md
        update_readme_innerdoc(project_root, len(files), pom_version)

    # --check-outdated
    if args.check_outdated is not None:
        days = args.check_outdated if args.check_outdated > 0 else 30
        outdated = check_outdated(files, days)
        print(f"\n超过 {days} 天未修改的文件: {len(outdated)} 个")
        for item in outdated:
            print(f"  {item['path']} ({item['days_old']} 天前)")


if __name__ == "__main__":
    main()
