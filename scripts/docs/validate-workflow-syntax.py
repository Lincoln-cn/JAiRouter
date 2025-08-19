#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GitHub工作流语法验证脚本

验证GitHub Actions工作流中嵌入的Python代码语法是否正确。
"""

import os
import sys
import yaml
import re
import ast
from pathlib import Path


def extract_python_code_from_workflow(content):
    """从工作流文件中提取Python代码"""
    python_codes = []
    
    # 查找 python -c "..." 模式
    pattern = r'python\s+-c\s+"([^"]+)"'
    matches = re.findall(pattern, content)
    for match in matches:
        python_codes.append(('python -c', match))
    
    # 查找 python3 -c "..." 模式  
    pattern = r'python3\s+-c\s+"([^"]+)"'
    matches = re.findall(pattern, content)
    for match in matches:
        python_codes.append(('python3 -c', match))
    
    # 查找多行Python代码块
    pattern = r'python\s+-c\s+\|\s*\n((?:\s+.*\n)*)'
    matches = re.findall(pattern, content, re.MULTILINE)
    for match in matches:
        python_codes.append(('python multiline', match.strip()))
    
    return python_codes


def validate_python_syntax(code, context=""):
    """验证Python代码语法"""
    try:
        # 尝试解析Python代码
        ast.parse(code)
        return True, None
    except SyntaxError as e:
        return False, f"语法错误: {e.msg} (行 {e.lineno}, 列 {e.offset})"
    except Exception as e:
        return False, f"解析错误: {str(e)}"


def check_workflow_python_syntax():
    """检查工作流中的Python语法"""
    project_root = Path(".")
    workflows_dir = project_root / ".github" / "workflows"
    
    if not workflows_dir.exists():
        print("❌ .github/workflows 目录不存在")
        return False
    
    issues = []
    total_checks = 0
    
    # 检查所有工作流文件
    for workflow_file in workflows_dir.glob("*.yml"):
        print(f"🔍 检查工作流文件: {workflow_file.name}")
        
        try:
            with open(workflow_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 提取Python代码
            python_codes = extract_python_code_from_workflow(content)
            
            if not python_codes:
                print(f"  ℹ️  未找到嵌入的Python代码")
                continue
            
            for context, code in python_codes:
                total_checks += 1
                print(f"  🔍 检查Python代码 ({context}):")
                print(f"     {code[:50]}{'...' if len(code) > 50 else ''}")
                
                is_valid, error = validate_python_syntax(code)
                if is_valid:
                    print(f"     ✅ 语法正确")
                else:
                    issues.append(f"❌ {workflow_file.name}: {error}")
                    print(f"     ❌ {error}")
        
        except Exception as e:
            issues.append(f"❌ {workflow_file.name}: 读取失败 - {e}")
    
    print(f"\n📊 总计检查了 {total_checks} 段Python代码")
    
    # 输出结果
    if issues:
        print("\n❌ 发现语法问题:")
        for issue in issues:
            print(f"  {issue}")
        return False
    else:
        print("\n✅ 所有Python代码语法检查通过!")
        return True


def check_common_issues():
    """检查常见问题"""
    print("\n🔍 检查常见问题:")
    
    workflows_dir = Path(".github/workflows")
    issues = []
    
    for workflow_file in workflows_dir.glob("*.yml"):
        with open(workflow_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查文件路径问题
        if 'docs/docs-versions.json' in content:
            issues.append(f"❌ {workflow_file.name}: 错误的文件路径 'docs/docs-versions.json'，应该是 'docs/docs-versions.json'")
        
        # 检查Python版本一致性
        python_versions = re.findall(r"python-version:\s*['\"]([^'\"]+)['\"]", content)
        if python_versions:
            unique_versions = set(python_versions)
            if len(unique_versions) > 1:
                issues.append(f"⚠️  {workflow_file.name}: 发现多个Python版本: {unique_versions}")
        
        # 检查脚本路径
        script_paths = re.findall(r'scripts/([^/\s]+\.py)', content)
        for path in script_paths:
            if not Path(f"scripts/{path}").exists() and not Path(f"scripts/docs/{path}").exists():
                issues.append(f"❌ {workflow_file.name}: 脚本路径可能错误: scripts/{path}")
    
    if issues:
        for issue in issues:
            print(f"  {issue}")
        return False
    else:
        print("  ✅ 未发现常见问题")
        return True


def main():
    """主函数"""
    print("🚀 GitHub工作流语法验证")
    print("=" * 50)
    
    success = True
    
    # 检查Python语法
    if not check_workflow_python_syntax():
        success = False
    
    # 检查常见问题
    if not check_common_issues():
        success = False
    
    print("\n" + "=" * 50)
    
    if success:
        print("✅ 所有语法检查通过! GitHub Actions应该能正常执行。")
        sys.exit(0)
    else:
        print("❌ 发现语法问题，请修复后重试。")
        sys.exit(1)


if __name__ == "__main__":
    main()