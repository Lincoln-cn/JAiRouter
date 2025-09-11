#!/usr/bin/env python3
"""
验证 MkDocs 配置文件的语法正确性
"""

import yaml
import sys
import os
from pathlib import Path

def validate_mkdocs_config():
    """验证 mkdocs.yml 配置文件"""
    config_file = Path("mkdocs.yml")
    
    if not config_file.exists():
        print("❌ 错误: 未找到 mkdocs.yml 文件")
        return False
    
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
        
        print("✅ mkdocs.yml 语法正确")
        
        # 检查必要的配置项
        required_fields = ['site_name', 'nav', 'theme']
        for field in required_fields:
            if field not in config:
                print(f"⚠️  警告: 缺少必要配置项 '{field}'")
            else:
                print(f"✅ 配置项 '{field}' 存在")
        
        return True
        
    except yaml.YAMLError as e:
        print(f"❌ YAML 语法错误: {e}")
        return False
    except Exception as e:
        print(f"❌ 配置文件读取错误: {e}")
        return False

def validate_nav_structure():
    """验证导航结构中的文件是否存在"""
    config_file = Path("mkdocs.yml")
    
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
        
        nav = config.get('nav', [])
        missing_files = []
        
        def check_nav_item(item, prefix=""):
            if isinstance(item, dict):
                for key, value in item.items():
                    if isinstance(value, str):
                        # 这是一个文件路径
                        file_path = Path("docs") / value
                        if not file_path.exists():
                            missing_files.append(f"{prefix}{key}: {value}")
                    elif isinstance(value, list):
                        # 这是一个子菜单
                        for sub_item in value:
                            check_nav_item(sub_item, f"{prefix}{key} -> ")
            elif isinstance(item, str):
                # 直接的文件路径
                file_path = Path("docs") / item
                if not file_path.exists():
                    missing_files.append(f"{prefix}{item}")
        
        for nav_item in nav:
            check_nav_item(nav_item)
        
        if missing_files:
            print("⚠️  以下导航文件不存在:")
            for file in missing_files:
                print(f"   - {file}")
        else:
            print("✅ 所有导航文件都存在")
        
        return len(missing_files) == 0
        
    except Exception as e:
        print(f"❌ 导航结构验证错误: {e}")
        return False

def main():
    """主函数"""
    print("🔍 验证 MkDocs 配置...")
    print("-" * 40)
    
    config_valid = validate_mkdocs_config()
    nav_valid = validate_nav_structure()
    
    print("-" * 40)
    
    if config_valid and nav_valid:
        print("🎉 所有验证通过！")
        return 0
    else:
        print("❌ 验证失败，请检查上述问题")
        return 1

if __name__ == "__main__":
    sys.exit(main())