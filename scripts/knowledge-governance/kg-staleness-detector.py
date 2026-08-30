#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库过时性检测器

检测 innerdoc/ 中索引漂移、状态冲突和版本漂移。
"""

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path


def read_pom_version(project_root: Path) -> str:
    """从 pom.xml 读取版本号"""
    pom_path = project_root / "pom.xml"
    if not pom_path.exists():
        return "unknown"
    try:
        tree = ET.parse(pom_path)
        root = tree.getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        ver_elem = root.find('./m:version', ns)
        if ver_elem is not None and ver_elem.text:
            return ver_elem.text.strip()
        with open(pom_path, 'r', encoding='utf-8') as f:
            text = f.read()
        match = re.search(r'<version>([^<]+)</version>', text)
        if match:
            return match.group(1).strip()
    except Exception:
        pass
    return "unknown"


def count_actual_files(innerdoc_root: Path) -> int:
    """统计实际文件数（排除 archive）"""
    count = 0
    for dirpath, dirnames, filenames in os.walk(innerdoc_root):
        rel_dir = os.path.relpath(dirpath, innerdoc_root)
        if rel_dir.startswith("archive") and rel_dir != ".":
            continue
        for fname in filenames:
            if not fname.endswith('.md'):
                continue
            filepath = str(os.path.relpath(os.path.join(dirpath, fname), innerdoc_root))
            if filepath.startswith("archive"):
                continue
            count += 1
    return count


def get_actual_categories(innerdoc_root: Path) -> list:
    """获取实际的分类目录列表"""
    cats = []
    for entry in sorted(innerdoc_root.iterdir()):
        if entry.is_dir() and entry.name != "archive":
            cats.append(entry.name)
    return cats


def check_index_drift(project_root: Path) -> list:
    """检查索引漂移"""
    innerdoc_root = project_root / "innerdoc"
    drifts = []

    if not innerdoc_root.exists():
        return drifts

    actual_count = count_actual_files(innerdoc_root)

    # 检查 README-INNERDOC.md
    readme_path = innerdoc_root / "README-INNERDOC.md"
    if readme_path.exists():
        with open(readme_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # 搜索 '个文件' 模式
        file_count_match = re.search(r'(\d+)\s*个文件', content)
        if file_count_match:
            claimed = int(file_count_match.group(1))
            if claimed != actual_count:
                drifts.append({
                    "artifact": "README-INNERDOC.md",
                    "claimed": claimed,
                    "actual": actual_count,
                    "note": f"文件计数不匹配: 声称 {claimed}, 实际 {actual_count}"
                })

        # 搜索 '总计' 模式
        total_match = re.search(r'\*\*总计\*\*\s*\|\s*\*\*(\d+)\*\*', content)
        if total_match:
            claimed = int(total_match.group(1))
            if claimed != actual_count:
                drifts.append({
                    "artifact": "README-INNERDOC.md (总计行)",
                    "claimed": claimed,
                    "actual": actual_count,
                    "note": f"总计行不匹配: 声称 {claimed}, 实际 {actual_count}"
                })

    # 检查 INDEX.json
    index_path = innerdoc_root / "INDEX.json"
    if index_path.exists():
        try:
            with open(index_path, 'r', encoding='utf-8') as f:
                index_data = json.load(f)

            index_count = index_data.get("stats", {}).get("total_files", 0)
            if index_count != actual_count:
                drifts.append({
                    "artifact": "INDEX.json",
                    "claimed": index_count,
                    "actual": actual_count,
                    "note": f"total_files 不匹配: 声称 {index_count}, 实际 {actual_count}"
                })

        except (json.JSONDecodeError, KeyError) as e:
            drifts.append({
                "artifact": "INDEX.json",
                "claimed": "N/A",
                "actual": actual_count,
                "note": f"读取 INDEX.json 失败: {e}"
            })

    # 检查 00-索引/README.md 分类表完整性
    index_readme = innerdoc_root / "00-索引" / "README.md"
    if index_readme.exists():
        with open(index_readme, 'r', encoding='utf-8') as f:
            content = f.read()

        # 提取分类表中列出的目录
        listed_dirs = set()
        for match in re.finditer(r'`(\d{2}-[^`]+)/`', content):
            listed_dirs.add(match.group(1))

        actual_cats = set(get_actual_categories(innerdoc_root))
        # 排除非数字前缀的目录
        numbered_actual = {d for d in actual_cats if re.match(r'\d{2}-', d)}

        missing = numbered_actual - listed_dirs
        extra = listed_dirs - actual_cats

        if missing or extra:
            note_parts = []
            if missing:
                note_parts.append(f"未列出的目录: {', '.join(sorted(missing))}")
            if extra:
                note_parts.append(f"列出但不存在的目录: {', '.join(sorted(extra))}")
            drifts.append({
                "artifact": "00-索引/README.md",
                "claimed": len(listed_dirs),
                "actual": len(numbered_actual),
                "note": "; ".join(note_parts)
            })

    return drifts


def check_status_conflicts(project_root: Path) -> list:
    """检查状态冲突"""
    innerdoc_root = project_root / "innerdoc"
    conflicts = []

    # 扫描开发计划2026.md 中的 "开发中/规划中" 任务
    plan_path = innerdoc_root / "01-项目概述" / "开发计划2026.md"
    track_path = innerdoc_root / "01-项目概述" / "任务跟踪表.md"

    if not plan_path.exists() or not track_path.exists():
        return conflicts

    with open(plan_path, 'r', encoding='utf-8') as f:
        plan_content = f.read()

    with open(track_path, 'r', encoding='utf-8') as f:
        track_content = f.read()

    # 查找计划中标记为"开发中"或"规划中"的任务
    plan_tasks = []
    for line in plan_content.splitlines():
        if re.search(r'开发中|规划中', line):
            # 提取任务关键词
            task_match = re.search(r'[|│]?\s*(.+?)\s*[|│]', line)
            if task_match:
                plan_tasks.append({
                    "text": line.strip(),
                    "status": "开发中" if "开发中" in line else "规划中"
                })

    # 检查跟踪表中是否标记为已完成
    for task in plan_tasks:
        # 从任务文本中提取关键词
        keywords = re.findall(r'[\u4e00-\u9fff]+|[a-zA-Z]+', task['text'])
        for kw in keywords:
            if len(kw) < 3:
                continue
            if kw in track_content:
                # 检查跟踪表中对应行是否标记为已完成
                for tline in track_content.splitlines():
                    if kw in tline and re.search(r'✅已完成|已完成|✅', tline):
                        # 如果计划中为"开发中"但跟踪表标记"已完成"
                        if task['status'] == "开发中":
                            conflicts.append({
                                "source1": "01-项目概述/开发计划2026.md",
                                "source2": "01-项目概述/任务跟踪表.md",
                                "note": f"任务 '{kw}' 在开发计划中仍标记为开发中，但跟踪表已标记为已完成"
                            })
                        break
                break  # 找到一个关键词匹配即够

    return conflicts


def check_version_drift(project_root: Path) -> list:
    """检查版本漂移"""
    innerdoc_root = project_root / "innerdoc"
    drifts = []

    pom_version = read_pom_version(project_root)
    if pom_version == "unknown":
        return drifts

    # 从版本号中提取 v 前缀格式
    current_v = f"v{pom_version}"

    version_pattern = re.compile(r'v\d+\.\d+\.\d+')

    # 检查索引相关文件中的版本号
    check_files = [
        innerdoc_root / "INDEX.json",
        innerdoc_root / "README-INNERDOC.md",
        innerdoc_root / "00-索引" / "README.md",
    ]

    for filepath in check_files:
        if not filepath.exists():
            continue
        with open(filepath, 'r', encoding='utf-8') as f:
            for line_no, line in enumerate(f, 1):
                for match in version_pattern.finditer(line):
                    found_ver = match.group(0)
                    # 比较版本号
                    if is_older_version(found_ver, current_v):
                        drifts.append({
                            "file": str(filepath.relative_to(project_root)).replace('\\', '/'),
                            "line": line_no,
                            "found": found_ver,
                            "current": current_v,
                        })

    return drifts


def is_older_version(ver1: str, ver2: str) -> bool:
    """比较两个版本号 v1 是否比 v2 旧"""
    def parse(v):
        nums = re.findall(r'\d+', v)
        return tuple(int(n) for n in nums[:3])

    try:
        return parse(ver1) < parse(ver2)
    except (ValueError, IndexError):
        return False


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="知识库过时性检测器")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--scan", action="store_true", help="执行扫描")
    parser.add_argument("--output", help="输出 JSON 报告文件路径")

    args = parser.parse_args()

    if not args.scan:
        parser.print_help()
        sys.exit(0)

    project_root = Path(args.project_root).resolve()

    index_drift = check_index_drift(project_root)
    status_conflicts = check_status_conflicts(project_root)
    version_drift = check_version_drift(project_root)

    print(f"索引漂移: {len(index_drift)} 项")
    for d in index_drift:
        print(f"  - {d['artifact']}: {d['note']}")

    print(f"状态冲突: {len(status_conflicts)} 项")
    for c in status_conflicts:
        print(f"  - {c['note']}")

    print(f"版本漂移: {len(version_drift)} 项")
    for v in version_drift:
        print(f"  - {v['file']}:{v['line']} - {v['found']} (当前 {v['current']})")

    result = {
        "scan_time": datetime.now().isoformat(),
        "index_drift": index_drift,
        "status_conflicts": status_conflicts,
        "version_drift": version_drift,
    }

    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"报告已保存: {args.output}")


if __name__ == "__main__":
    main()
