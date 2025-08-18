#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文档版本管理脚本 (Python 版本)
实现文档版本标识和更新提醒，追踪文档变更
"""

import os
import sys
import json
import hashlib
import argparse
import subprocess
import re
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import yaml


class DocumentVersion:
    """文档版本信息类"""
    
    def __init__(self, file_path: str, version: str, last_modified: str, content_hash: str):
        self.file_path = file_path
        self.version = version
        self.last_modified = last_modified
        self.content_hash = content_hash
        self.git_commit = ""
        self.author = ""
        self.change_summary = ""
        self.dependencies = []
    
    def to_dict(self) -> dict:
        return {
            'FilePath': self.file_path,
            'Version': self.version,
            'LastModified': self.last_modified,
            'ContentHash': self.content_hash,
            'GitCommit': self.git_commit,
            'Author': self.author,
            'ChangeSummary': self.change_summary,
            'Dependencies': self.dependencies
        }
    
    @classmethod
    def from_dict(cls, data: dict) -> 'DocumentVersion':
        version = cls(
            data.get('FilePath', ''),
            data.get('Version', ''),
            data.get('LastModified', ''),
            data.get('ContentHash', '')
        )
        version.git_commit = data.get('GitCommit', '')
        version.author = data.get('Author', '')
        version.change_summary = data.get('ChangeSummary', '')
        version.dependencies = data.get('Dependencies', [])
        return version


class VersionChange:
    """版本变更信息类"""
    
    def __init__(self, file_path: str, old_version: str, new_version: str, 
                 change_type: str, timestamp: str):
        self.file_path = file_path
        self.old_version = old_version
        self.new_version = new_version
        self.change_type = change_type
        self.timestamp = timestamp
        self.description = ""
    
    def to_dict(self) -> dict:
        return {
            'FilePath': self.file_path,
            'OldVersion': self.old_version,
            'NewVersion': self.new_version,
            'ChangeType': self.change_type,
            'Timestamp': self.timestamp,
            'Description': self.description
        }
    
    @classmethod
    def from_dict(cls, data: dict) -> 'VersionChange':
        change = cls(
            data.get('FilePath', ''),
            data.get('OldVersion', ''),
            data.get('NewVersion', ''),
            data.get('ChangeType', ''),
            data.get('Timestamp', '')
        )
        change.description = data.get('Description', '')
        return change


class DocumentVersionManager:
    """文档版本管理器类"""
    
    def __init__(self, project_root: str):
        self.project_root = Path(project_root).resolve()
        self.version_file = self.project_root / '.kiro' / 'docs-versions.json'
        self.config_file = self.project_root / '.kiro' / 'docs-version-config.yml'
        self.versions: Dict[str, DocumentVersion] = {}
        self.changes: List[VersionChange] = []
        
        # 确保版本文件目录存在
        self.version_file.parent.mkdir(parents=True, exist_ok=True)
        
        # 加载现有版本信息
        self.load_versions()
        self.load_config()
    
    def load_config(self):
        """加载配置文件"""
        self.config = {
            'version_management': {
                'version_format': 'semantic',
                'auto_increment': {
                    'major': ['breaking_change', 'api_change', 'major_restructure'],
                    'minor': ['new_section', 'new_feature_doc', 'significant_update'],
                    'patch': ['content_update', 'typo_fix', 'format_change', 'link_update']
                }
            },
            'document_scanning': {
                'include_patterns': ['docs/**/*.md', 'README*.md', '*.md'],
                'exclude_patterns': ['node_modules/**', '.git/**', 'target/**', 'build/**']
            },
            'version_headers': {
                'enabled': True,
                'template': '''<!-- 版本信息 -->
> **文档版本**: {version}  
> **最后更新**: {last_modified}  
> **Git 提交**: {git_commit}  
> **作者**: {author}
<!-- /版本信息 -->''',
                'position': 'after_title'
            },
            'outdated_detection': {
                'default_threshold_days': 30
            }
        }
        
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    loaded_config = yaml.safe_load(f)
                    if loaded_config:
                        self._merge_config(self.config, loaded_config)
            except Exception as e:
                print(f"⚠️ 配置文件加载失败: {e}")
    
    def _merge_config(self, base: dict, update: dict):
        """递归合并配置"""
        for key, value in update.items():
            if key in base and isinstance(base[key], dict) and isinstance(value, dict):
                self._merge_config(base[key], value)
            else:
                base[key] = value
    
    def load_versions(self):
        """加载版本信息"""
        if not self.version_file.exists():
            return
        
        try:
            with open(self.version_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            self.versions = {}
            if 'versions' in data:
                for file_path, version_data in data['versions'].items():
                    self.versions[file_path] = DocumentVersion.from_dict(version_data)
            
            self.changes = []
            if 'changes' in data:
                for change_data in data['changes']:
                    self.changes.append(VersionChange.from_dict(change_data))
        
        except Exception as e:
            print(f"⚠️ 加载版本信息失败: {e}")
            self.versions = {}
            self.changes = []
    
    def save_versions(self):
        """保存版本信息"""
        try:
            data = {
                'versions': {path: version.to_dict() for path, version in self.versions.items()},
                'changes': [change.to_dict() for change in self.changes],
                'last_updated': datetime.now().isoformat()
            }
            
            with open(self.version_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        
        except Exception as e:
            print(f"❌ 保存版本信息失败: {e}")
    
    def calculate_content_hash(self, file_path: str) -> str:
        """计算文件内容哈希"""
        try:
            full_path = self.project_root / file_path
            if full_path.exists():
                with open(full_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                return hashlib.sha256(content.encode('utf-8')).hexdigest()[:16]
        except Exception:
            pass
        return ""
    
    def get_git_info(self, file_path: str) -> Tuple[str, str]:
        """获取 Git 信息"""
        try:
            full_path = self.project_root / file_path
            result = subprocess.run(
                ['git', 'log', '-1', '--format=%H|%an', '--', str(full_path)],
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=10
            )
            
            if result.returncode == 0 and result.stdout.strip():
                parts = result.stdout.strip().split('|', 1)
                commit = parts[0][:8] if parts else ""
                author = parts[1] if len(parts) > 1 else ""
                return commit, author
        except Exception:
            pass
        return "", ""
    
    def generate_version_number(self, file_path: str, content_hash: str) -> str:
        """生成版本号"""
        existing_version = self.versions.get(file_path)
        
        if not existing_version:
            return "1.0.0"
        
        if existing_version.content_hash == content_hash:
            return existing_version.version
        
        # 内容有变化，递增版本号
        try:
            parts = existing_version.version.split('.')
            if len(parts) == 3:
                major, minor, patch = map(int, parts)
                patch += 1  # 简单的版本递增策略
                return f"{major}.{minor}.{patch}"
        except ValueError:
            pass
        
        return "1.0.0"
    
    def detect_document_dependencies(self, file_path: str) -> List[str]:
        """检测文档依赖关系"""
        dependencies = []
        
        try:
            full_path = self.project_root / file_path
            if not full_path.exists():
                return dependencies
            
            with open(full_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 检测 Markdown 链接
            md_links = re.findall(r'\[.*?\]\(([^)]+)\)', content)
            for link in md_links:
                if not link.startswith(('http://', 'https://', 'mailto:')) and link.endswith('.md'):
                    dep_path = (full_path.parent / link).resolve()
                    if dep_path.exists():
                        rel_path = dep_path.relative_to(self.project_root)
                        dependencies.append(str(rel_path).replace('\\', '/'))
            
            # 检测文件引用 #[[file:...]]
            file_refs = re.findall(r'#\[\[file:([^\]]+)\]\]', content)
            for ref in file_refs:
                ref_path = self.project_root / ref
                if ref_path.exists():
                    dependencies.append(ref)
        
        except Exception as e:
            print(f"⚠️ 检测依赖关系失败 {file_path}: {e}")
        
        return sorted(set(dependencies))
    
    def scan_documents(self) -> List[str]:
        """扫描文档文件"""
        documents = []
        
        include_patterns = self.config['document_scanning']['include_patterns']
        exclude_patterns = self.config['document_scanning']['exclude_patterns']
        
        for pattern in include_patterns:
            try:
                if '**' in pattern:
                    # 递归搜索
                    base_path = pattern.split('**')[0].rstrip('/')
                    full_base_path = self.project_root / base_path if base_path else self.project_root
                    
                    if full_base_path.exists():
                        for md_file in full_base_path.rglob('*.md'):
                            rel_path = md_file.relative_to(self.project_root)
                            rel_path_str = str(rel_path).replace('\\', '/')
                            
                            # 检查排除模式
                            excluded = False
                            for exclude_pattern in exclude_patterns:
                                if self._match_pattern(rel_path_str, exclude_pattern):
                                    excluded = True
                                    break
                            
                            if not excluded:
                                documents.append(rel_path_str)
                else:
                    # 简单模式匹配
                    for md_file in self.project_root.glob(pattern):
                        if md_file.is_file():
                            rel_path = md_file.relative_to(self.project_root)
                            rel_path_str = str(rel_path).replace('\\', '/')
                            
                            # 检查排除模式
                            excluded = False
                            for exclude_pattern in exclude_patterns:
                                if self._match_pattern(rel_path_str, exclude_pattern):
                                    excluded = True
                                    break
                            
                            if not excluded:
                                documents.append(rel_path_str)
            
            except Exception as e:
                print(f"⚠️ 扫描模式失败 {pattern}: {e}")
        
        return sorted(set(documents))
    
    def _match_pattern(self, path: str, pattern: str) -> bool:
        """简单的模式匹配"""
        if '**' in pattern:
            parts = pattern.split('**')
            if len(parts) == 2:
                prefix, suffix = parts
                return path.startswith(prefix.rstrip('/')) and path.endswith(suffix.lstrip('/'))
        elif '*' in pattern:
            # 简单的通配符匹配
            import fnmatch
            return fnmatch.fnmatch(path, pattern)
        else:
            return path == pattern
    
    def update_document_version(self, file_path: str) -> Optional[VersionChange]:
        """更新文档版本"""
        full_path = self.project_root / file_path
        
        if not full_path.exists():
            # 文档被删除
            if file_path in self.versions:
                old_version = self.versions[file_path].version
                del self.versions[file_path]
                
                change = VersionChange(
                    file_path, old_version, "", "DELETED",
                    datetime.now().isoformat()
                )
                change.description = "文档已删除"
                self.changes.append(change)
                return change
            return None
        
        # 计算当前文档信息
        content_hash = self.calculate_content_hash(file_path)
        git_commit, author = self.get_git_info(file_path)
        dependencies = self.detect_document_dependencies(file_path)
        
        # 生成版本号
        new_version = self.generate_version_number(file_path, content_hash)
        
        # 检查是否有变化
        existing_version = self.versions.get(file_path)
        
        if existing_version:
            if existing_version.content_hash == content_hash:
                # 内容未变化，但可能需要更新其他信息
                existing_version.git_commit = git_commit
                existing_version.author = author
                existing_version.dependencies = dependencies
                return None
            
            # 内容有变化
            change_type = "MODIFIED"
            old_version = existing_version.version
        else:
            # 新文档
            change_type = "CREATED"
            old_version = ""
        
        # 更新版本信息
        version = DocumentVersion(
            file_path, new_version, datetime.now().isoformat(), content_hash
        )
        version.git_commit = git_commit
        version.author = author
        version.dependencies = dependencies
        
        self.versions[file_path] = version
        
        # 记录变更
        change = VersionChange(
            file_path, old_version, new_version, change_type,
            datetime.now().isoformat()
        )
        change.description = f"文档{change_type.lower()}"
        self.changes.append(change)
        
        return change
    
    def update_all_versions(self) -> List[VersionChange]:
        """更新所有版本"""
        print("🔍 扫描文档文件...")
        documents = self.scan_documents()
        
        print(f"📄 发现 {len(documents)} 个文档文件")
        
        all_changes = []
        
        for doc_path in documents:
            change = self.update_document_version(doc_path)
            if change:
                all_changes.append(change)
                print(f"  📝 {change.change_type}: {change.file_path} ({change.old_version} → {change.new_version})")
        
        # 检查已删除的文档
        existing_paths = set(documents)
        to_remove = [path for path in self.versions.keys() if path not in existing_paths]
        
        for file_path in to_remove:
            change = self.update_document_version(file_path)
            if change:
                all_changes.append(change)
                print(f"  🗑️ {change.change_type}: {change.file_path}")
        
        return all_changes
    
    def check_outdated_documents(self, days_threshold: int) -> List[str]:
        """检查过期文档"""
        outdated = []
        threshold_date = datetime.now() - timedelta(days=days_threshold)
        
        for file_path, version in self.versions.items():
            try:
                last_modified = datetime.fromisoformat(version.last_modified.replace('Z', '+00:00'))
                if last_modified < threshold_date:
                    outdated.append(file_path)
            except ValueError:
                # 日期格式错误，认为是过期的
                outdated.append(file_path)
        
        return outdated
    
    def add_version_headers(self) -> int:
        """添加版本头信息"""
        if not self.config['version_headers']['enabled']:
            return 0
        
        added_count = 0
        template = self.config['version_headers']['template']
        position = self.config['version_headers']['position']
        
        for file_path, version_info in self.versions.items():
            full_path = self.project_root / file_path
            
            if not full_path.exists():
                continue
            
            try:
                with open(full_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # 生成版本头
                version_header = template.format(
                    version=version_info.version,
                    last_modified=version_info.last_modified[:10],
                    git_commit=version_info.git_commit,
                    author=version_info.author
                )
                
                # 检查是否已有版本头
                if '<!-- 版本信息 -->' in content:
                    # 更新现有版本头
                    new_content = re.sub(
                        r'<!-- 版本信息 -->.*?<!-- /版本信息 -->',
                        version_header,
                        content,
                        flags=re.DOTALL
                    )
                    
                    if new_content != content:
                        with open(full_path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                        added_count += 1
                else:
                    # 添加新版本头
                    if position == 'after_title':
                        # 在第一个标题后插入
                        lines = content.split('\n')
                        title_index = -1
                        for i, line in enumerate(lines):
                            if line.startswith('# '):
                                title_index = i
                                break
                        
                        if title_index >= 0:
                            new_lines = (
                                lines[:title_index + 1] +
                                ['', version_header, ''] +
                                lines[title_index + 1:]
                            )
                            new_content = '\n'.join(new_lines)
                            
                            with open(full_path, 'w', encoding='utf-8') as f:
                                f.write(new_content)
                            added_count += 1
                    elif position == 'top':
                        # 在文档顶部插入
                        new_content = version_header + '\n\n' + content
                        with open(full_path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                        added_count += 1
            
            except Exception as e:
                print(f"⚠️ 添加版本头失败 {file_path}: {e}")
        
        return added_count
    
    def export_version_data(self, export_path: str):
        """导出版本数据"""
        try:
            export_data = {
                'metadata': {
                    'export_time': datetime.now().isoformat(),
                    'project_root': str(self.project_root),
                    'total_documents': len(self.versions),
                    'total_changes': len(self.changes)
                },
                'versions': {path: version.to_dict() for path, version in self.versions.items()},
                'changes': [change.to_dict() for change in self.changes],
                'statistics': {
                    'by_type': {},
                    'by_month': {},
                    'outdated_count': len(self.check_outdated_documents(30))
                }
            }
            
            # 生成统计信息
            from collections import Counter
            
            change_types = Counter(change.change_type for change in self.changes)
            export_data['statistics']['by_type'] = dict(change_types)
            
            change_months = Counter()
            for change in self.changes:
                try:
                    month = datetime.fromisoformat(change.timestamp.replace('Z', '+00:00')).strftime('%Y-%m')
                    change_months[month] += 1
                except ValueError:
                    change_months['unknown'] += 1
            export_data['statistics']['by_month'] = dict(change_months)
            
            # 根据文件扩展名确定导出格式
            export_path_obj = Path(export_path)
            
            if export_path_obj.suffix.lower() == '.json':
                with open(export_path, 'w', encoding='utf-8') as f:
                    json.dump(export_data, f, ensure_ascii=False, indent=2)
            elif export_path_obj.suffix.lower() == '.csv':
                import csv
                with open(export_path, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.writer(f)
                    writer.writerow(['FilePath', 'Version', 'LastModified', 'GitCommit', 'Author'])
                    for path, version in self.versions.items():
                        writer.writerow([
                            path, version.version, version.last_modified,
                            version.git_commit, version.author
                        ])
            else:
                # 默认导出为 JSON
                with open(export_path, 'w', encoding='utf-8') as f:
                    json.dump(export_data, f, ensure_ascii=False, indent=2)
        
        except Exception as e:
            print(f"❌ 导出版本数据失败: {e}")
    
    def generate_version_report(self) -> str:
        """生成版本报告"""
        report = ["# 文档版本管理报告\n"]
        
        # 统计信息
        total_docs = len(self.versions)
        recent_changes = [
            change for change in self.changes
            if self._is_recent_change(change.timestamp, 7)
        ]
        
        report.extend([
            "## 版本统计\n",
            f"- 总文档数: {total_docs}",
            f"- 近7天变更: {len(recent_changes)}",
            f"- 版本文件: {self.version_file}",
            f"- 最后扫描: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        ])
        
        # 最近变更
        if recent_changes:
            report.extend(["## 最近变更\n"])
            for change in sorted(recent_changes, key=lambda x: x.timestamp, reverse=True)[:10]:
                timestamp = change.timestamp[:10]
                report.append(
                    f"- **{change.change_type}**: {change.file_path} "
                    f"({change.old_version} → {change.new_version}) - {timestamp}"
                )
            report.append("")
        
        # 过期文档检查
        outdated_docs = self.check_outdated_documents(30)
        if outdated_docs:
            report.extend(["## 过期文档 (30天未更新)\n"])
            for doc_path in outdated_docs:
                version_info = self.versions[doc_path]
                last_modified = version_info.last_modified[:10]
                report.append(
                    f"- {doc_path} (版本: {version_info.version}, 最后更新: {last_modified})"
                )
            report.append("")
        
        # 依赖关系分析
        report.extend(["## 依赖关系分析\n"])
        dependency_count = 0
        for path, version in self.versions.items():
            if version.dependencies:
                dependency_count += len(version.dependencies)
                report.append(f"- **{path}**: 依赖 {len(version.dependencies)} 个文档")
                for dep in version.dependencies:
                    report.append(f"  - {dep}")
        
        if dependency_count == 0:
            report.append("- 未发现文档依赖关系")
        report.append("")
        
        # 所有文档版本
        report.extend(["## 所有文档版本\n"])
        sorted_versions = sorted(
            self.versions.items(),
            key=lambda x: x[1].last_modified,
            reverse=True
        )
        
        for file_path, version_info in sorted_versions:
            last_modified = version_info.last_modified[:10]
            git_info = f" ({version_info.git_commit})" if version_info.git_commit else ""
            report.append(f"- **{file_path}**: v{version_info.version} - {last_modified}{git_info}")
        
        return "\n".join(report)
    
    def _is_recent_change(self, timestamp: str, days: int) -> bool:
        """检查是否为最近的变更"""
        try:
            change_date = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            return change_date > datetime.now() - timedelta(days=days)
        except ValueError:
            return False


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='文档版本管理工具')
    parser.add_argument('--project-root', default='.', help='项目根目录')
    parser.add_argument('--scan', action='store_true', help='扫描并更新版本信息')
    parser.add_argument('--report', help='生成版本报告到指定文件')
    parser.add_argument('--add-headers', action='store_true', help='添加版本头信息')
    parser.add_argument('--cleanup', type=int, help='清理指定天数前的变更记录')
    parser.add_argument('--export', help='导出版本数据到指定文件')
    parser.add_argument('--check-outdated', type=int, default=30, help='检查过期文档的天数阈值')
    
    args = parser.parse_args()
    
    try:
        # 创建版本管理器
        manager = DocumentVersionManager(args.project_root)
        
        if args.scan:
            print("🔄 更新文档版本信息...")
            changes = manager.update_all_versions()
            
            if changes:
                print(f"📝 发现 {len(changes)} 个变更")
            else:
                print("✅ 没有发现变更")
            
            manager.save_versions()
        
        if args.add_headers:
            print("📋 添加版本头信息...")
            added_count = manager.add_version_headers()
            print(f"✅ 为 {added_count} 个文档添加了版本头信息")
            manager.save_versions()
        
        if args.cleanup:
            print(f"🧹 清理 {args.cleanup} 天前的变更记录...")
            old_count = len(manager.changes)
            
            threshold_date = datetime.now() - timedelta(days=args.cleanup)
            manager.changes = [
                change for change in manager.changes
                if manager._is_recent_change(change.timestamp, args.cleanup)
            ]
            
            new_count = len(manager.changes)
            print(f"✅ 清理了 {old_count - new_count} 条旧记录")
            manager.save_versions()
        
        if args.report:
            print("📊 生成版本报告...")
            report_content = manager.generate_version_report()
            
            with open(args.report, 'w', encoding='utf-8') as f:
                f.write(report_content)
            print(f"📄 报告已保存到: {args.report}")
        
        if args.export:
            print("📤 导出版本数据...")
            manager.export_version_data(args.export)
            print(f"📄 数据已导出到: {args.export}")
        
        # 检查过期文档
        outdated_docs = manager.check_outdated_documents(args.check_outdated)
        if outdated_docs:
            print(f"\n⚠️ 发现 {len(outdated_docs)} 个过期文档 (超过 {args.check_outdated} 天未更新):")
            for doc in outdated_docs:
                version_info = manager.versions[doc]
                last_modified = version_info.last_modified[:10]
                print(f"  - {doc} (版本: {version_info.version}, 最后更新: {last_modified})")
        
        print("✅ 文档版本管理完成")
    
    except Exception as e:
        print(f"❌ 文档版本管理失败: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()