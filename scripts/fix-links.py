#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
链接修复助手脚本
基于链接检查报告，提供修复建议和自动修复功能
"""

import os
import re
import json
import sys
from pathlib import Path
from typing import Dict, List, Optional
import argparse

class LinkFixer:
    def __init__(self, report_file: str = "link-check-report.json"):
        self.report_file = report_file
        self.report_data = None
        self.fix_suggestions = []
        
    def load_report(self) -> bool:
        """加载链接检查报告"""
        try:
            with open(self.report_file, 'r', encoding='utf-8') as f:
                self.report_data = json.load(f)
            return True
        except FileNotFoundError:
            print(f"❌ 报告文件不存在: {self.report_file}")
            print("请先运行链接检查脚本生成报告")
            return False
        except json.JSONDecodeError as e:
            print(f"❌ 报告文件格式错误: {e}")
            return False
    
    def suggest_internal_link_fix(self, invalid_url: str, file_path: str) -> Optional[str]:
        """为内部链接提供修复建议"""
        # 移除锚点和查询参数
        clean_url = invalid_url.split('?')[0].split('#')[0]
        
        # 如果是相对路径，尝试找到可能的目标文件
        if not clean_url.startswith(('http://', 'https://', '/')):
            current_dir = Path(file_path).parent
            
            # 尝试不同的可能路径
            possible_paths = [
                current_dir / clean_url,
                current_dir / f"{clean_url}.md",
                Path("docs") / clean_url,
                Path("docs") / f"{clean_url}.md",
            ]
            
            # 如果原路径没有扩展名，尝试添加 .md
            if not Path(clean_url).suffix:
                possible_paths.extend([
                    current_dir / f"{clean_url}/index.md",
                    Path("docs") / f"{clean_url}/index.md",
                ])
            
            for possible_path in possible_paths:
                if possible_path.exists():
                    # 计算相对路径
                    try:
                        relative_path = os.path.relpath(possible_path, current_dir)
                        return relative_path.replace('\\', '/')  # 统一使用正斜杠
                    except ValueError:
                        continue
        
        # 如果是绝对路径，检查是否存在对应文件
        elif clean_url.startswith('/'):
            abs_path = Path("docs") / clean_url.lstrip('/')
            if abs_path.exists():
                return clean_url
            
            # 尝试添加 .md 扩展名
            if not abs_path.suffix:
                md_path = abs_path.with_suffix('.md')
                if md_path.exists():
                    return f"{clean_url}.md"
                
                # 尝试 index.md
                index_path = abs_path / "index.md"
                if index_path.exists():
                    return f"{clean_url}/index.md"
        
        return None
    
    def suggest_external_link_fix(self, invalid_url: str) -> Optional[str]:
        """为外部链接提供修复建议"""
        # 常见的 URL 修复模式
        fixes = [
            # HTTP -> HTTPS
            (r'^http://', 'https://'),
            # 移除多余的斜杠
            (r'([^:])//+', r'\1/'),
            # 修复常见的域名错误
            (r'github\.com/([^/]+)/([^/]+)\.git', r'github.com/\1/\2'),
        ]
        
        fixed_url = invalid_url
        for pattern, replacement in fixes:
            fixed_url = re.sub(pattern, replacement, fixed_url)
        
        return fixed_url if fixed_url != invalid_url else None
    
    def analyze_invalid_links(self):
        """分析无效链接并生成修复建议"""
        if not self.report_data:
            return
        
        invalid_links = self.report_data.get('invalid_links', [])
        
        if not invalid_links:
            print("✅ 没有发现无效链接")
            return
        
        print(f"🔍 分析 {len(invalid_links)} 个无效链接...")
        
        for link_info in invalid_links:
            file_path = link_info['file']
            url = link_info['url']
            line = link_info['line']
            message = link_info['message']
            
            suggestion = {
                'file': file_path,
                'line': line,
                'original_url': url,
                'error': message,
                'suggested_fix': None,
                'fix_type': None
            }
            
            # 根据错误类型提供不同的修复建议
            if '文件不存在' in message:
                # 内部链接问题
                fix = self.suggest_internal_link_fix(url, file_path)
                if fix:
                    suggestion['suggested_fix'] = fix
                    suggestion['fix_type'] = 'internal_link'
            elif 'HTTP' in message and url.startswith(('http://', 'https://')):
                # 外部链接问题
                fix = self.suggest_external_link_fix(url)
                if fix:
                    suggestion['suggested_fix'] = fix
                    suggestion['fix_type'] = 'external_link'
            
            self.fix_suggestions.append(suggestion)
    
    def print_suggestions(self):
        """打印修复建议"""
        if not self.fix_suggestions:
            print("✅ 没有修复建议")
            return
        
        print(f"\n🔧 修复建议:")
        
        for i, suggestion in enumerate(self.fix_suggestions, 1):
            print(f"\n{i}. 📄 {suggestion['file']}:{suggestion['line']}")
            print(f"   ❌ 原链接: {suggestion['original_url']}")
            print(f"   🔍 错误: {suggestion['error']}")
            
            if suggestion['suggested_fix']:
                print(f"   ✅ 建议修复: {suggestion['suggested_fix']}")
                print(f"   🔧 修复类型: {suggestion['fix_type']}")
            else:
                print(f"   ⚠️  需要手动检查和修复")
    
    def apply_fixes(self, auto_fix: bool = False):
        """应用修复建议"""
        if not self.fix_suggestions:
            print("没有可应用的修复建议")
            return
        
        fixes_applied = 0
        
        for suggestion in self.fix_suggestions:
            if not suggestion['suggested_fix']:
                continue
            
            file_path = suggestion['file']
            original_url = suggestion['original_url']
            suggested_fix = suggestion['suggested_fix']
            
            if not auto_fix:
                # 交互式确认
                print(f"\n📄 {file_path}:{suggestion['line']}")
                print(f"将 '{original_url}' 替换为 '{suggested_fix}'?")
                response = input("确认修复? (y/N): ").strip().lower()
                if response not in ['y', 'yes']:
                    continue
            
            try:
                # 读取文件内容
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # 替换链接
                # 需要小心处理，避免误替换
                patterns = [
                    f'\\[([^\\]]*)\\]\\({re.escape(original_url)}\\)',  # Markdown 链接
                    f'href=["\\']{re.escape(original_url)}["\\'']',     # HTML 链接
                    re.escape(original_url)  # 直接 URL
                ]
                
                replaced = False
                for pattern in patterns:
                    if re.search(pattern, content):
                        if '\\[' in pattern:  # Markdown 链接
                            content = re.sub(pattern, f'[\\1]({suggested_fix})', content)
                        elif 'href=' in pattern:  # HTML 链接
                            content = re.sub(pattern, f'href="{suggested_fix}"', content)
                        else:  # 直接 URL
                            content = content.replace(original_url, suggested_fix)
                        replaced = True
                        break
                
                if replaced:
                    # 写回文件
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(content)
                    
                    print(f"✅ 已修复: {file_path}")
                    fixes_applied += 1
                else:
                    print(f"⚠️  未找到匹配的链接模式: {file_path}")
                    
            except Exception as e:
                print(f"❌ 修复失败 {file_path}: {e}")
        
        print(f"\n🎉 共应用了 {fixes_applied} 个修复")
    
    def run(self, auto_fix: bool = False, apply_fixes: bool = False):
        """运行链接修复分析"""
        if not self.load_report():
            return False
        
        self.analyze_invalid_links()
        self.print_suggestions()
        
        if apply_fixes and self.fix_suggestions:
            print(f"\n🔧 开始应用修复...")
            self.apply_fixes(auto_fix)
        
        return True

def main():
    parser = argparse.ArgumentParser(description='分析和修复文档链接问题')
    parser.add_argument('--report', default='link-check-report.json', help='链接检查报告文件')
    parser.add_argument('--apply', action='store_true', help='应用修复建议')
    parser.add_argument('--auto', action='store_true', help='自动应用所有修复（不询问确认）')
    
    args = parser.parse_args()
    
    fixer = LinkFixer(args.report)
    success = fixer.run(auto_fix=args.auto, apply_fixes=args.apply)
    
    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()