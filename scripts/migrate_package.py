#!/usr/bin/env python3
"""
Package迁移辅助脚本
用于v2.7.x版本package结构重组

功能：
1. 批量移动文件到目标目录
2. 更新package声明
3. 更新import语句
4. 验证迁移结果

使用方法：
    python migrate_package.py --source security --target auth/security --dry-run
    python migrate_package.py --source security --target auth/security --execute
"""

import os
import re
import argparse
import shutil
from pathlib import Path
from typing import List, Dict, Tuple

# 项目根目录
PROJECT_ROOT = Path("/home/ubuntu/jairouter/modelrouter")
SOURCE_ROOT = PROJECT_ROOT / "src/main/java/org/unreal/modelrouter"
TEST_ROOT = PROJECT_ROOT / "src/test/java/org/unreal/modelrouter"

# 迁移映射表
MIGRATION_MAP = {
    # auth 模块
    "security": "auth/security",
    "audit": "auth/audit",
    
    # config 模块
    "version": "config/version",
    
    # router 模块
    "adapter": "router/adapter",
    "loadbalancer": "router/loadbalancer",
    "circuitbreaker": "router/circuitbreaker",
    "ratelimit": "router/ratelimit",
    "fallback": "router/fallback",
    "checker": "router/checker",
    
    # monitor 模块
    "tracing": "monitor/tracing",
    "monitoring": "monitor/metrics",
    
    # persistence 模块
    "store": "persistence/store",
    "jpa": "persistence/jpa",
    "repository": "persistence/repository",
    
    # common 模块
    "constants": "common/constants",
    "dto": "common/dto",
    "vo": "common/vo",
    "entity": "common/entity",
    "exception": "common/exception",
    "exceptionhandler": "common/handler",
    "util": "common/util",
    "factory": "common/factory",
    "filter": "common/filter",
    "sanitization": "common/sanitization",
    "cli": "common/cli",
    "model": "common/model",
}


def get_java_files(directory: Path) -> List[Path]:
    """获取目录下所有Java文件"""
    if not directory.exists():
        return []
    return list(directory.rglob("*.java"))


def update_package_declaration(file_path: Path, old_package: str, new_package: str) -> str:
    """更新文件中的package声明"""
    content = file_path.read_text(encoding='utf-8')
    
    # 更新package声明
    pattern = rf'package\s+{re.escape(old_package)}'
    replacement = f'package {new_package}'
    content = re.sub(pattern, replacement, content)
    
    return content


def update_imports(content: str, old_package: str, new_package: str) -> str:
    """更新文件中的import语句"""
    # 更新import语句
    pattern = rf'import\s+org\.unreal\.modelrouter\.{re.escape(old_package)}'
    replacement = f'import org.unreal.modelrouter.{new_package}'
    content = re.sub(pattern, replacement, content)
    
    return content


def migrate_directory(source: str, target: str, dry_run: bool = True) -> Dict:
    """
    迁移目录
    
    Args:
        source: 源目录名（如 security）
        target: 目标目录名（如 auth/security）
        dry_run: 是否仅模拟运行
    
    Returns:
        迁移统计信息
    """
    source_dir = SOURCE_ROOT / source
    target_dir = SOURCE_ROOT / target
    
    if not source_dir.exists():
        return {"error": f"源目录不存在: {source_dir}"}
    
    # 获取所有Java文件
    java_files = get_java_files(source_dir)
    
    stats = {
        "source": source,
        "target": target,
        "files_count": len(java_files),
        "files_migrated": [],
        "imports_updated": 0,
    }
    
    if dry_run:
        print(f"[DRY-RUN] 将迁移 {len(java_files)} 个文件从 {source} 到 {target}")
        for f in java_files[:10]:  # 只显示前10个
            rel_path = f.relative_to(source_dir)
            print(f"  - {source}/{rel_path} -> {target}/{rel_path}")
        if len(java_files) > 10:
            print(f"  ... 还有 {len(java_files) - 10} 个文件")
        return stats
    
    # 执行迁移
    print(f"[EXECUTE] 迁移 {len(java_files)} 个文件从 {source} 到 {target}")
    
    # 创建目标目录
    target_dir.mkdir(parents=True, exist_ok=True)
    
    for java_file in java_files:
        # 计算相对路径
        rel_path = java_file.relative_to(source_dir)
        target_file = target_dir / rel_path
        
        # 创建子目录
        target_file.parent.mkdir(parents=True, exist_ok=True)
        
        # 计算新旧package名
        old_package_parts = list(java_file.relative_to(SOURCE_ROOT).parts[:-1])
        new_package_parts = list(target_file.relative_to(SOURCE_ROOT).parts[:-1])
        
        old_package = "org.unreal.modelrouter." + ".".join(old_package_parts)
        new_package = "org.unreal.modelrouter." + ".".join(new_package_parts)
        
        # 读取并更新文件内容
        content = java_file.read_text(encoding='utf-8')
        content = update_package_declaration(java_file, old_package, new_package)
        
        # 写入目标文件
        target_file.write_text(content, encoding='utf-8')
        
        stats["files_migrated"].append(str(rel_path))
        print(f"  ✓ {rel_path}")
    
    # 更新所有import语句
    print("\n更新import语句...")
    all_java_files = list(SOURCE_ROOT.rglob("*.java")) + list(TEST_ROOT.rglob("*.java"))
    
    old_import = f"org.unreal.modelrouter.{source}"
    new_import = f"org.unreal.modelrouter.{target}"
    
    for java_file in all_java_files:
        content = java_file.read_text(encoding='utf-8')
        if old_import in content:
            content = update_imports(content, source, target)
            java_file.write_text(content, encoding='utf-8')
            stats["imports_updated"] += 1
    
    print(f"  ✓ 更新了 {stats['imports_updated']} 个文件的import语句")
    
    return stats


def batch_migrate(dry_run: bool = True) -> List[Dict]:
    """批量迁移所有模块"""
    results = []
    
    # 按顺序迁移
    order = [
        # 先迁移common模块（被其他模块依赖）
        ("constants", "common/constants"),
        ("dto", "common/dto"),
        ("vo", "common/vo"),
        ("entity", "common/entity"),
        ("exception", "common/exception"),
        ("exceptionhandler", "common/handler"),
        ("util", "common/util"),
        ("factory", "common/factory"),
        ("filter", "common/filter"),
        ("sanitization", "common/sanitization"),
        ("cli", "common/cli"),
        
        # 然后迁移服务模块
        ("security", "auth/security"),
        ("audit", "auth/audit"),
        ("version", "config/version"),
        ("adapter", "router/adapter"),
        ("loadbalancer", "router/loadbalancer"),
        ("circuitbreaker", "router/circuitbreaker"),
        ("ratelimit", "router/ratelimit"),
        ("fallback", "router/fallback"),
        ("checker", "router/checker"),
        ("tracing", "monitor/tracing"),
        ("monitoring", "monitor/metrics"),
        ("store", "persistence/store"),
        ("jpa", "persistence/jpa"),
        ("repository", "persistence/repository"),
    ]
    
    for source, target in order:
        result = migrate_directory(source, target, dry_run)
        results.append(result)
    
    return results


def main():
    parser = argparse.ArgumentParser(description="Package迁移辅助脚本")
    parser.add_argument("--source", help="源目录名（如 security）")
    parser.add_argument("--target", help="目标目录名（如 auth/security）")
    parser.add_argument("--dry-run", action="store_true", help="仅模拟运行，不实际迁移")
    parser.add_argument("--execute", action="store_true", help="执行实际迁移")
    parser.add_argument("--batch", action="store_true", help="批量迁移所有模块")
    
    args = parser.parse_args()
    
    dry_run = not args.execute
    
    if args.batch:
        print("=" * 60)
        print("批量迁移所有模块")
        print("=" * 60)
        results = batch_migrate(dry_run)
        
        total_files = sum(r.get("files_count", 0) for r in results)
        print(f"\n总计: {len(results)} 个模块, {total_files} 个文件")
        
        if dry_run:
            print("\n提示: 使用 --execute 参数执行实际迁移")
    
    elif args.source and args.target:
        result = migrate_directory(args.source, args.target, dry_run)
        
        if dry_run:
            print("\n提示: 使用 --execute 参数执行实际迁移")
    
    else:
        parser.print_help()


if __name__ == "__main__":
    main()