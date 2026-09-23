#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文档版本管理脚本 (Python 版本)
实现文档版本标识和更新提醒，追踪文档变更
"""

import os
import sys
import json
import fnmatch
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
    
    def __init__(self, file_path: str, version: str, last_modified: str, content_hash: str,
                 reviewed_at: str = ""):
        self.file_path = file_path
        self.version = version
        self.last_modified = last_modified
        self.content_hash = content_hash
        # 人工评审时间（ISO-8601）：维护者审阅后确认“内容仍然准确”时写入，
        # 让该文档在不改写 LastModified/ContentHash 的前提下重新获得一个完整窗口。
        self.reviewed_at = reviewed_at or ""
        self.git_commit = ""
        self.author = ""
        self.change_summary = ""
        self.dependencies = []
    
    def to_dict(self) -> dict:
        data = {
            'FilePath': self.file_path,
            'Version': self.version,
            'LastModified': self.last_modified,
            'ContentHash': self.content_hash,
            'GitCommit': self.git_commit,
            'Author': self.author,
            'ChangeSummary': self.change_summary,
            'Dependencies': self.dependencies
        }
        # 仅在已记录人工评审时落盘该字段：否则每日 --scan 会给全部记录都补上空的
        # ReviewedAt，产生 170 条无信息量的机械 diff。缺失即“未评审”，语义不变。
        if self.reviewed_at:
            data['ReviewedAt'] = self.reviewed_at
        return data
    
    @classmethod
    def from_dict(cls, data: dict) -> 'DocumentVersion':
        version = cls(
            data.get('FilePath', ''),
            data.get('Version', ''),
            data.get('LastModified', ''),
            data.get('ContentHash', '')
        )
        version.reviewed_at = data.get('ReviewedAt', '') or ''
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
        self.version_file = self.project_root / 'docs' / 'docs-versions.json'
        self.config_file = self.project_root / 'docs' / 'docs-version-config.yml'
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
    
    def _normalize_doc_path(self, path: str) -> str:
        """统一为仓库根目录相对路径（正斜杠）"""
        normalized = str(path).replace('\\', '/')
        while normalized.startswith('./'):
            normalized = normalized[2:]
        return normalized
    
    def is_ignored_outdated_document(self, file_path: str) -> bool:
        """文档是否命中 outdated_detection.ignore_patterns（不参与过期检查）"""
        ignore_patterns = self.config.get('outdated_detection', {}).get('ignore_patterns') or []
        normalized = self._normalize_doc_path(file_path)
        
        for pattern in ignore_patterns:
            if fnmatch.fnmatch(normalized, self._normalize_doc_path(pattern)):
                return True
        
        return False
    
    def get_document_type(self, file_path: str) -> Optional[str]:
        """按 document_scanning.document_types[*].patterns 匹配文档类型"""
        document_types = self.config.get('document_scanning', {}).get('document_types') or {}
        normalized = self._normalize_doc_path(file_path)
        matched_type = None
        matched_length = -1
        
        for type_name, type_config in document_types.items():
            for pattern in (type_config or {}).get('patterns') or []:
                # 多个类型命中时，取模式更具体（更长）的那个
                if self._match_pattern(normalized, pattern) and len(str(pattern)) > matched_length:
                    matched_type = type_name
                    matched_length = len(str(pattern))
        
        return matched_type
    
    def resolve_outdated_threshold(self, file_path: str, days_threshold: Optional[int] = None) -> int:
        """解析单个文档的过期阈值（天）

        days_threshold 仅覆盖“没有类型专属阈值”时的默认阈值，不覆盖 type_thresholds。
        """
        outdated_config = self.config.get('outdated_detection', {}) or {}
        document_type = self.get_document_type(file_path)
        type_thresholds = outdated_config.get('type_thresholds') or {}
        
        if document_type and document_type in type_thresholds:
            return int(type_thresholds[document_type])
        
        if days_threshold is not None:
            return int(days_threshold)
        
        return int(outdated_config.get('default_threshold_days', 30))
    
    def _parse_datetime(self, value) -> Optional[datetime]:
        """宽松解析 ISO-8601 时间戳，统一为无时区的本地时间；缺失/不可解析返回 None"""
        if value is None:
            return None
        
        text = str(value).strip()
        if not text:
            return None
        
        try:
            parsed = datetime.fromisoformat(text.replace('Z', '+00:00'))
        except (ValueError, TypeError):
            return None
        
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone().replace(tzinfo=None)
        
        return parsed
    
    def latest_freshness_date(self, version: DocumentVersion) -> Optional[datetime]:
        """文档的新鲜度依据：LastModified 与人工评审 ReviewedAt 中较新的那个

        两者都不存在或都不可解析时返回 None，由调用方按“过期”处理
        （即未引入评审机制前的行为）。
        """
        candidates = [
            parsed for parsed in (
                self._parse_datetime(getattr(version, 'last_modified', None)),
                self._parse_datetime(getattr(version, 'reviewed_at', None)),
            ) if parsed is not None
        ]
        
        return max(candidates) if candidates else None
    
    def is_outdated_document(self, file_path: str, days_threshold: Optional[int] = None) -> bool:
        """单个文档是否过期：max(LastModified, ReviewedAt) 早于 now - 该文档的类型阈值"""
        threshold_days = self.resolve_outdated_threshold(file_path, days_threshold)
        threshold_date = datetime.now() - timedelta(days=threshold_days)
        freshness_date = self.latest_freshness_date(self.versions[file_path])
        
        return freshness_date is None or freshness_date < threshold_date
    
    def review_state(self, file_path: str, days_threshold: Optional[int] = None) -> dict:
        """人工评审状态（仅用于展示，不参与过期判定）

        fresh_by_review 为 True 表示该文档当前之所以不算过期，靠的是人工评审
        （ReviewedAt 比 LastModified 新，且评审窗口还没走完），而不是内容变更。
        """
        version = self.versions[file_path]
        threshold_days = self.resolve_outdated_threshold(file_path, days_threshold)
        reviewed_at = self._parse_datetime(getattr(version, 'reviewed_at', None))
        
        if reviewed_at is None:
            return {
                'reviewed_at': None,
                'expires_at': None,
                'days_left': None,
                'fresh_by_review': False,
                'summary': '未记录人工评审',
            }
        
        expires_at = reviewed_at + timedelta(days=threshold_days)
        days_left = (expires_at - datetime.now()).days
        last_modified = self._parse_datetime(getattr(version, 'last_modified', None))
        fresh_by_review = (
            days_left >= 0
            and (last_modified is None or reviewed_at > last_modified)
            and not self.is_outdated_document(file_path, days_threshold)
        )
        
        if days_left >= 0:
            summary = (
                f'人工评审 {reviewed_at:%Y-%m-%d}，评审窗口（{threshold_days} 天）'
                f'有效至 {expires_at:%Y-%m-%d}'
            )
        else:
            summary = (
                f'人工评审已失效（评审于 {reviewed_at:%Y-%m-%d}，'
                f'窗口止于 {expires_at:%Y-%m-%d}）'
            )
        
        return {
            'reviewed_at': reviewed_at,
            'expires_at': expires_at,
            'days_left': days_left,
            'fresh_by_review': fresh_by_review,
            'summary': summary,
        }
    
    def reviewed_fresh_documents(self, days_threshold: Optional[int] = None) -> List[str]:
        """当前因人工评审（而非内容变更）而不算过期的文档"""
        return [
            file_path for file_path in self.versions
            if not self.is_ignored_outdated_document(file_path)
            and self.review_state(file_path, days_threshold)['fresh_by_review']
        ]
    
    def count_review_records(self) -> int:
        """已记录 ReviewedAt 的文档数"""
        return sum(
            1 for version in self.versions.values()
            if self._parse_datetime(getattr(version, 'reviewed_at', None)) is not None
        )
    
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
        
        # 更新版本信息（人工评审不继承：评审只针对被评审的那一版内容，
        # 内容已变时 LastModified 已被推进，新内容需要重新评审）
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
    
    def check_outdated_documents(self, days_threshold: Optional[int] = None) -> List[str]:
        """检查过期文档

        每个文档的阈值按类型解析：先按 document_scanning.document_types[*].patterns
        匹配文档类型，再取 outdated_detection.type_thresholds[type]；未匹配到类型时
        用 outdated_detection.default_threshold_days（days_threshold 显式传入时覆盖它）。
        outdated_detection.ignore_patterns 命中的文档永不参与检查。

        新鲜度取 max(LastModified, ReviewedAt)：人工评审 ReviewedAt 让文档在不改写
        LastModified/ContentHash 的前提下重新获得一个完整窗口。ReviewedAt 缺失或
        不可解析时行为与引入评审机制前完全一致。
        """
        outdated = []
        
        for file_path in self.versions:
            if self.is_ignored_outdated_document(file_path):
                continue
            
            if self.is_outdated_document(file_path, days_threshold):
                outdated.append(file_path)
        
        return outdated
    
    def set_reviewed_at(self, file_paths: List[str], reviewed_at: Optional[str] = None) -> int:
        """写入/清除 ReviewedAt，返回实际被改动的文档数

        reviewed_at 为 None 表示清除评审记录。未被 docs-versions.json 跟踪的路径
        直接抛 ValueError（两遍处理，绝不部分写入），避免操作员误以为标记成功。
        """
        normalized_paths = []
        unknown_paths = []
        
        for raw_path in file_paths:
            normalized = self._normalize_doc_path(raw_path)
            if normalized in self.versions:
                normalized_paths.append(normalized)
            else:
                unknown_paths.append(normalized)
        
        if unknown_paths:
            raise ValueError(
                f"以下路径未被 docs/docs-versions.json 跟踪，未写入任何修改: "
                f"{', '.join(unknown_paths)}"
                f"（新文档请先运行 --scan 生成版本记录）"
            )
        
        stamp = '' if reviewed_at is None else str(reviewed_at)
        changed_count = 0
        
        for file_path in dict.fromkeys(normalized_paths):
            version = self.versions[file_path]
            
            if (version.reviewed_at or '') == stamp:
                print(f"  = {file_path} 已是 {stamp or '（无评审记录）'}，幂等跳过")
                continue
            
            version.reviewed_at = stamp
            changed_count += 1
            
            if stamp:
                print(f"  ✅ {file_path} 评审时间 -> {stamp}")
            else:
                print(f"  🧹 {file_path} 已清除评审记录")
        
        return changed_count
    
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
                    'outdated_count': len(self.check_outdated_documents())
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
        
        # 过期文档检查（阈值按文档类型解析，见 outdated_detection.type_thresholds；
        # 新鲜度取 max(LastModified, ReviewedAt)，人工评审见本文件上方说明）
        # 注意：本节标题必须仍以“## 过期文档”开头，且节内不得出现 “##”（含 ###），
        # 工作流用 /## 过期文档.*?(?=##|$)/s 抽取本节正文。
        outdated_docs = self.check_outdated_documents()
        reviewed_fresh_docs = self.reviewed_fresh_documents()
        if outdated_docs or reviewed_fresh_docs:
            report.extend([f"## 过期文档 (按文档类型阈值判定，共 {len(outdated_docs)} 个)\n"])
            for doc_path in outdated_docs:
                version_info = self.versions[doc_path]
                last_modified = str(version_info.last_modified)[:10]
                threshold_days = self.resolve_outdated_threshold(doc_path)
                review_summary = self.review_state(doc_path)['summary']
                report.append(
                    f"- {doc_path} (版本: {version_info.version}, 最后更新: {last_modified}, "
                    f"阈值 {threshold_days} 天) — {review_summary}"
                )
            
            if reviewed_fresh_docs:
                report.append("")
                report.append("**未计入过期的文档：靠人工评审保持新鲜（不是内容变更，计时由人工评审重置）**")
                for doc_path in reviewed_fresh_docs:
                    state = self.review_state(doc_path)
                    report.append(
                        f"- {doc_path} (人工评审: {state['reviewed_at']:%Y-%m-%d}, "
                        f"评审窗口止于: {state['expires_at']:%Y-%m-%d}, "
                        f"剩余 {state['days_left']} 天)"
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
    parser.add_argument(
        '--check-outdated',
        type=int,
        nargs='?',
        default=None,
        help='检查过期文档；阈值默认完全由 docs/docs-version-config.yml 决定'
             '（按文档类型取 outdated_detection.type_thresholds，未匹配类型时取 default_threshold_days）；'
             '显式传入数值时，该数值仅作为“没有类型专属阈值”的文档的默认阈值，不覆盖按类型的阈值。'
             '不带数值（或不指定该参数）时等同于完全由配置决定'
    )
    parser.add_argument(
        '--mark-reviewed',
        nargs='+',
        metavar='PATH',
        help='记录人工评审：把 ReviewedAt 写入 docs/docs-versions.json 中已跟踪的文档'
             '（可一次多个路径）。人工评审让 max(LastModified, ReviewedAt) 落在阈值窗口内，'
             '从而使审查过但内容未变的文档不再出现在过期提醒里；幂等，路径未被跟踪时报错退出'
    )
    parser.add_argument(
        '--clear-reviewed',
        nargs='+',
        metavar='PATH',
        help='清除人工评审记录（撤销误标），与 --mark-reviewed 使用相同的路径校验规则'
    )
    parser.add_argument(
        '--reviewed-at',
        help='配合 --mark-reviewed 使用：评审时间，YYYY-MM-DD 或完整 ISO-8601；默认当前时间'
    )
    
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
        
        # 人工评审：在 --scan/--add-headers/--cleanup 之后处理，保证同一次调用里评审记录是最终状态
        if args.reviewed_at and not args.mark_reviewed:
            print("⚠️ --reviewed-at 仅与 --mark-reviewed 搭配使用，已忽略")
        
        if args.mark_reviewed:
            reviewed_at_value = ''
            parsed_reviewed_at = None
            
            if args.reviewed_at:
                try:
                    parsed_reviewed_at = datetime.fromisoformat(args.reviewed_at)
                except ValueError:
                    print(f"❌ --reviewed-at 无法解析为 ISO-8601 时间戳: {args.reviewed_at}"
                          f"（示例: 2026-01-05 或 2026-01-05T10:30:00）")
                    sys.exit(1)
                
                if parsed_reviewed_at.tzinfo is not None:
                    parsed_reviewed_at = parsed_reviewed_at.astimezone().replace(tzinfo=None)
                reviewed_at_value = parsed_reviewed_at.isoformat()
            else:
                parsed_reviewed_at = datetime.now()
                reviewed_at_value = parsed_reviewed_at.isoformat()
            
            print(f"🧾 记录人工评审 (ReviewedAt={reviewed_at_value})...")
            if parsed_reviewed_at > datetime.now():
                print("⚠️ 注意：评审时间在未来，该文档的过期计时会被推迟到该时间之后")
            
            try:
                changed_count = manager.set_reviewed_at(args.mark_reviewed, reviewed_at_value)
            except ValueError as exc:
                print(f"❌ {exc}")
                sys.exit(1)
            
            if changed_count:
                manager.save_versions()
            print(f"✅ 记录人工评审完成（{changed_count} 个文档被改动）")
        
        if args.clear_reviewed:
            print("🧹 清除人工评审记录...")
            
            try:
                changed_count = manager.set_reviewed_at(args.clear_reviewed, None)
            except ValueError as exc:
                print(f"❌ {exc}")
                sys.exit(1)
            
            if changed_count:
                manager.save_versions()
            print(f"✅ 清除人工评审完成（{changed_count} 个文档被改动）")
        
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
        
        # 检查过期文档：args.check_outdated 为 None 时完全由配置驱动
        outdated_docs = manager.check_outdated_documents(args.check_outdated)
        if outdated_docs:
            threshold_scope = (
                f"无类型专属阈值时按 {args.check_outdated} 天判定"
                if args.check_outdated is not None
                else "阈值按文档类型解析，见 docs/docs-version-config.yml"
            )
            print(f"\n⚠️ 发现 {len(outdated_docs)} 个过期文档 ({threshold_scope}):")
            for doc in outdated_docs:
                version_info = manager.versions[doc]
                last_modified = str(version_info.last_modified)[:10]
                threshold_days = manager.resolve_outdated_threshold(doc, args.check_outdated)
                review_summary = manager.review_state(doc, args.check_outdated)['summary']
                print(
                    f"  - {doc} (版本: {version_info.version}, 最后更新: {last_modified}, "
                    f"阈值 {threshold_days} 天) — {review_summary}"
                )
        
        # 评审可见性：单独列出“靠人工评审保持新鲜”的文档，读者才能把
        # “内容变更重置计时”与“人工评审重置计时”区分开
        reviewed_fresh_docs = manager.reviewed_fresh_documents(args.check_outdated)
        if reviewed_fresh_docs:
            print(f"\n🧾 靠人工评审保持新鲜、未计入过期: {len(reviewed_fresh_docs)} 个文档")
            for doc in reviewed_fresh_docs:
                state = manager.review_state(doc, args.check_outdated)
                print(
                    f"  - {doc} (人工评审: {state['reviewed_at']:%Y-%m-%d}, "
                    f"评审窗口止于: {state['expires_at']:%Y-%m-%d}, 剩余 {state['days_left']} 天)"
                )
        
        review_count = manager.count_review_records()
        print(f"\n🧾 已记录人工评审 (ReviewedAt): {review_count} 个文档")
        if review_count == 0:
            print("   （审查确认某文档仍然准确时，用 --mark-reviewed <path> 记录评审，"
                  "可在不改写 LastModified 的前提下重置其过期计时）")
        
        print("✅ 文档版本管理完成")
    
    except Exception as e:
        print(f"❌ 文档版本管理失败: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()