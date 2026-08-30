#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导航文件完整性校验脚本

从 mkdocs.yml 中提取 nav 树的所有文件引用，检查每个文件是否在 docs/ 目录下存在。
支持 i18n nav_translations（忽略翻译 key，只收集文件路径）。
输出每个缺失文件的警告及最终摘要。

用法：
    python scripts/docs/validate-nav-files.py                         # 默认 mkdocs.yml
    python scripts/docs/validate-nav-files.py --mkdocs-yml path/to/mkdocs.yml
    python scripts/docs/validate-nav-files.py --strict                # 缺失文件时退出码 1
"""

import argparse
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="校验 mkdocs.yml nav 中引用的所有文件是否存在"
    )
    parser.add_argument(
        "--mkdocs-yml",
        default="mkdocs.yml",
        help="mkdocs.yml 文件路径（默认: mkdocs.yml）",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="严格模式：有任何缺失文件时退出码为 1",
    )
    return parser.parse_args()


def collect_nav_files(nav_item, files: set) -> None:
    """
    递归遍历 nav 树，收集所有文件引用。

    nav 树的节点类型：
    - dict: { "标题": "file.md" } 或 { "标题": [子节点列表] }
    - str:  直接的文件路径（如 "zh/index.md"）
    - list: 子节点列表
    """
    if isinstance(nav_item, dict):
        for _key, value in nav_item.items():
            if isinstance(value, str):
                # 直接文件路径
                files.add(value)
            elif isinstance(value, list):
                # 子菜单列表
                for sub_item in value:
                    collect_nav_files(sub_item, files)
            # 其他类型（如数字、布尔值）忽略
    elif isinstance(nav_item, str):
        files.add(nav_item)
    elif isinstance(nav_item, list):
        for sub_item in nav_item:
            collect_nav_files(sub_item, files)


def extract_nav_files(config: dict) -> set:
    """
    从 mkdocs.yml 解析结果中提取 nav 树下所有文件路径。

    会忽略 i18n 插件中的 nav_translations（翻译映射表），
    只处理顶层 nav 和每个语言的 nav（如果存在）。
    """
    files = set()

    # 处理顶层 nav
    nav = config.get("nav", [])
    if nav:
        collect_nav_files(nav, files)

    # 处理 i18n 插件中可能存在的每语言 nav
    plugins = config.get("plugins", [])
    for plugin in plugins:
        if isinstance(plugin, dict) and "i18n" in plugin:
            i18n_config = plugin["i18n"]
            languages = i18n_config.get("languages", [])
            for lang in languages:
                if isinstance(lang, dict):
                    lang_nav = lang.get("nav")
                    if lang_nav:
                        collect_nav_files(lang_nav, files)

    return files


def check_files_exist(files: set, docs_dir: Path) -> list:
    """
    检查每个文件引用是否在 docs/ 目录下存在。

    返回缺失文件的排序列表。
    """
    missing = []
    for file_ref in sorted(files):
        file_path = docs_dir / file_ref
        if not file_path.exists():
            missing.append(file_ref)
    return missing


def main() -> int:
    """主函数"""
    args = parse_args()

    # 定位 mkdocs.yml
    mkdocs_path = Path(args.mkdocs_yml)
    if not mkdocs_path.exists():
        print(f"错误: 未找到 mkdocs.yml 文件: {mkdocs_path}")
        return 1

    # 解析 YAML（使用 PyYAML，与项目其他脚本一致）
    # mkdocs.yml 中包含 !!python/name: 等自定义标签，需要构造一个宽容的 Loader
    try:
        import yaml
    except ImportError:
        print("错误: 需要安装 PyYAML（pip install pyyaml）")
        return 1

    # 定义宽容 Loader：将无法识别的标签作为普通字符串处理
    class ForgivingLoader(yaml.SafeLoader):
        """忽略 !!python/name: 等自定义标签的 YAML Loader"""
        pass

    def _ignore_unknown_tag(loader, tag_suffix, node):
        """将未知标签的值作为普通字符串或空值返回"""
        if isinstance(node, yaml.ScalarNode):
            return loader.construct_scalar(node)
        elif isinstance(node, yaml.SequenceNode):
            return loader.construct_sequence(node)
        elif isinstance(node, yaml.MappingNode):
            return loader.construct_mapping(node)
        return None

    ForgivingLoader.add_multi_constructor('tag:yaml.org,2002:python/', _ignore_unknown_tag)
    ForgivingLoader.add_multi_constructor('!', _ignore_unknown_tag)

    try:
        with open(mkdocs_path, "r", encoding="utf-8") as f:
            config = yaml.load(f, Loader=ForgivingLoader)
    except yaml.YAMLError as e:
        print(f"错误: YAML 解析失败: {e}")
        return 1
    except Exception as e:
        print(f"错误: 读取文件失败: {e}")
        return 1

    if config is None:
        print("错误: mkdocs.yml 内容为空")
        return 1

    # 确定 docs/ 目录（相对 mkdocs.yml 位置）
    docs_dir = mkdocs_path.parent / "docs"
    if not docs_dir.exists():
        print(f"警告: docs/ 目录不存在: {docs_dir}")

    # 提取 nav 文件引用
    nav_files = extract_nav_files(config)
    print(f"从 nav 中提取到 {len(nav_files)} 个文件引用")

    # 检查文件是否存在
    missing = check_files_exist(nav_files, docs_dir)

    # 输出结果
    print()
    if missing:
        print(f"发现 {len(missing)} 个缺失文件:")
        for file_ref in missing:
            print(f"  MISSING: {file_ref}")
        print()
        print(f"摘要: {len(missing)}/{len(nav_files)} 个文件缺失")
    else:
        print("所有导航文件均存在")
        print(f"摘要: 0/{len(nav_files)} 个文件缺失")

    # 退出码：strict 模式下有缺失则返回 1，否则始终返回 0（警告角色）
    if args.strict and missing:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
