#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GitHub工作流路径验证脚本

验证GitHub Actions工作流中引用的所有脚本路径是否正确。
"""

import os
import sys
import yaml
import re
from pathlib import Path


def check_workflow_paths():
    """检查工作流中的脚本路径"""
    project_root = Path(".")
    workflows_dir = project_root / ".github" / "workflows"
    
    if not workflows_dir.exists():
        print("❌ .github/workflows 目录不存在")
        return False
    
    issues = []
    
    # 检查所有工作流文件
    for workflow_file in workflows_dir.glob("*.yml"):
        print(f"🔍 检查工作流文件: {workflow_file.name}")
        
        try:
            with open(workflow_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 查找脚本路径引用
            script_patterns = [
                r'python\s+([^\\s]+\.py)',
                r'python3\s+([^\\s]+\.py)',
                r'\\\.\\([^\\s]+\.ps1)',
                r'bash\s+([^\\s]+\.sh)',
            ]
            
            for pattern in script_patterns:
                matches = re.findall(pattern, content)
                for match in matches:
                    script_path = project_root / match.replace('\\', '/')
                    if not script_path.exists():
                        issues.append(f"❌ {workflow_file.name}: 脚本不存在 - {match}")
                    else:
                        print(f"  ✅ 脚本存在: {match}")
        
        except Exception as e:
            issues.append(f"❌ {workflow_file.name}: 读取失败 - {e}")
    
    # 检查必需的脚本文件
    required_scripts = [
        "scripts/docs/check-docs-sync.py",
        "scripts/docs/check-docs-sync.ps1", 
        "scripts/docs/check-links.py",
        "scripts/docs/docs-version-manager.py",
        "scripts/docs/fix-links.py",
        "scripts/docs/validate-docs-config.py",
        "scripts/docs/validate-docs-sync.py"
    ]
    
    print("\\n🔍 检查必需的脚本文件:")
    for script in required_scripts:
        script_path = project_root / script
        if script_path.exists():
            print(f"  ✅ {script}")
        else:
            issues.append(f"❌ 必需脚本不存在: {script}")
    
    # 输出结果
    if issues:
        print("\\n❌ 发现问题:")
        for issue in issues:
            print(f"  {issue}")
        return False
    else:
        print("\\n✅ 所有脚本路径检查通过!")
        return True


def check_script_permissions():
    """检查脚本执行权限 (Linux/macOS)"""
    if os.name == 'nt':  # Windows
        print("🔍 Windows系统，跳过权限检查")
        return True
    
    scripts_dir = Path("scripts/docs")
    if not scripts_dir.exists():
        print("❌ scripts/docs 目录不存在")
        return False
    
    issues = []
    
    for script_file in scripts_dir.glob("*.py"):
        if not os.access(script_file, os.X_OK):
            issues.append(f"❌ 脚本缺少执行权限: {script_file}")
        else:
            print(f"✅ 权限正确: {script_file}")
    
    if issues:
        print("\\n❌ 权限问题:")
        for issue in issues:
            print(f"  {issue}")
        print("\\n🔧 修复建议:")
        print("  chmod +x scripts/docs/*.py")
        return False
    
    return True


def main():
    """主函数"""
    print("🚀 GitHub工作流路径验证")
    print("=" * 50)
    
    success = True
    
    # 检查脚本路径
    if not check_workflow_paths():
        success = False
    
    print("\\n" + "=" * 50)
    
    # 检查脚本权限
    if not check_script_permissions():
        success = False
    
    print("\\n" + "=" * 50)
    
    if success:
        print("✅ 所有检查通过! GitHub Actions应该能正常执行。")
        sys.exit(0)
    else:
        print("❌ 发现问题，请修复后重试。")
        sys.exit(1)


if __name__ == "__main__":
    main()