#!/usr/bin/env python3
"""
文档与代码同步检查脚本
检测文档中的 API 端点、配置项、包名等是否与实际代码一致。

用法:
    python scripts/docs/check-docs-sync.py [--project-root .] [--fail-on-error]
"""
import os
import re
import sys
import json
import glob as globmod
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Set, Tuple


class DocsSyncChecker:
    """文档与代码同步检查器"""

    def __init__(self, project_root: str, fail_on_error: bool = False):
        self.project_root = Path(project_root).resolve()
        self.fail_on_error = fail_on_error
        self.errors: List[dict] = []
        self.warnings: List[dict] = []

    def run_all_checks(self) -> bool:
        """运行所有检查，返回 True 表示全部通过"""
        print("=" * 60)
        print("文档与代码同步检查")
        print("=" * 60)

        self.check_banned_patterns()
        self.check_stale_versions()
        self.check_admin_endpoints()
        self.check_actuator_monitoring_prefix()
        self.check_security_package_refs()
        self.check_docker_uid()
        self.check_api_endpoint_paths()
        self.check_config_prefix_consistency()

        self.print_report()
        return len(self.errors) == 0

    def _scan_docs(self) -> List[str]:
        """扫描所有文档文件"""
        docs = []
        for pattern in ['docs/**/*.md', '*.md']:
            for f in globmod.glob(str(self.project_root / pattern), recursive=True):
                docs.append(f)
        return docs

    def _read_file(self, path: str) -> str:
        """读取文件内容"""
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return f.read()
        except Exception:
            return ""

    def _get_project_version(self) -> str:
        """从 pom.xml 获取项目版本"""
        pom = self.project_root / 'pom.xml'
        if not pom.exists():
            return "unknown"
        try:
            tree = ET.parse(pom)
            root = tree.getroot()
            ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
            version = root.find('.//m:version', ns)
            if version is not None:
                return version.text
        except Exception:
            pass
        return "unknown"

    def check_banned_patterns(self):
        """检查文档中不应出现的模式"""
        print("\n[1/8] 检查禁止模式...")
        docs = self._scan_docs()
        count = 0

        banned = [
            (r'/admin/security/', 'CRITICAL', '使用了不存在的 /admin/security/ 端点，应使用 /api/auth/ 或 /api/security/'),
            (r'org\.unreal\.modelrouter\.security[^.]', 'CRITICAL', '使用了不存在的包名 org.unreal.modelrouter.security，应为 org.unreal.modelrouter.auth'),
            (r'/actuator/monitoring/', 'CRITICAL', '使用了错误的端点前缀 /actuator/monitoring/，应为 /api/monitoring/'),
            (r'sodlinken/jairouter:v0\.', 'HIGH', '使用了过旧的 Docker 标签版本'),
        ]

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)
            for pattern, severity, message in banned:
                matches = list(re.finditer(pattern, content))
                if matches:
                    for m in matches:
                        line_num = content[:m.start()].count('\n') + 1
                        self.errors.append({
                            'severity': severity,
                            'file': rel_path,
                            'line': line_num,
                            'message': message,
                            'matched': m.group()
                        })
                        count += 1

        print(f"  发现 {count} 个禁止模式匹配")

    def check_stale_versions(self):
        """检查过期的版本引用"""
        print("\n[2/8] 检查过期版本引用...")
        docs = self._scan_docs()
        version = self._get_project_version()
        count = 0

        # 检查 Helm chart version 1.0.0
        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            # Helm chart version
            if 'Chart.yaml' in content and 'version: 1.0.0' in content:
                line_num = content.index('version: 1.0.0')
                line_num = content[:line_num].count('\n') + 1
                self.warnings.append({
                    'severity': 'MEDIUM',
                    'file': rel_path,
                    'line': line_num,
                    'message': f'Helm Chart version 仍为 1.0.0，项目版本已为 {version}'
                })
                count += 1

            # actuator/info version
            if '"version": "1.0.0"' in content and 'actuator/info' in content:
                line_num = content.index('"version": "1.0.0"')
                line_num = content[:line_num].count('\n') + 1
                self.warnings.append({
                    'severity': 'MEDIUM',
                    'file': rel_path,
                    'line': line_num,
                    'message': f'actuator/info 示例版本仍为 1.0.0，项目版本已为 {version}'
                })
                count += 1

        print(f"  发现 {count} 个过期版本引用")

    def check_admin_endpoints(self):
        """检查不存在的 /admin/ 端点"""
        print("\n[3/8] 检查 /admin/ 端点引用...")
        docs = self._scan_docs()
        count = 0

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            # 查找 /admin/ 路径引用（排除注释和说明性文字）
            for m in re.finditer(r'http://localhost:\d+/admin/\S+', content):
                line_num = content[:m.start()].count('\n') + 1
                self.errors.append({
                    'severity': 'HIGH',
                    'file': rel_path,
                    'line': line_num,
                    'message': f'引用了不存在的 /admin/ 端点',
                    'matched': m.group()
                })
                count += 1

        print(f"  发现 {count} 个 /admin/ 端点引用")

    def check_actuator_monitoring_prefix(self):
        """检查错误的 /actuator/monitoring/ 前缀"""
        print("\n[4/8] 检查 /actuator/monitoring/ 前缀...")
        docs = self._scan_docs()
        count = 0

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            for m in re.finditer(r'/actuator/monitoring/\S+', content):
                line_num = content[:m.start()].count('\n') + 1
                self.errors.append({
                    'severity': 'CRITICAL',
                    'file': rel_path,
                    'line': line_num,
                    'message': '使用了错误的端点前缀 /actuator/monitoring/，应为 /api/monitoring/',
                    'matched': m.group()
                })
                count += 1

        print(f"  发现 {count} 个错误前缀")

    def check_security_package_refs(self):
        """检查错误的安全包名引用"""
        print("\n[5/8] 检查安全包名引用...")
        docs = self._scan_docs()
        count = 0

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            # 查找 org.unreal.modelrouter.security 引用（排除 org.unreal.modelrouter.auth.security）
            for m in re.finditer(r'org\.unreal\.modelrouter\.security(?!\.s)', content):
                # 排除 actuator/loggers 路径中的引用
                line_start = content.rfind('\n', 0, m.start()) + 1
                line_text = content[line_start:content.find('\n', m.end())]
                if '/actuator/loggers/' in line_text:
                    continue
                line_num = content[:m.start()].count('\n') + 1
                self.errors.append({
                    'severity': 'CRITICAL',
                    'file': rel_path,
                    'line': line_num,
                    'message': '使用了不存在的包名 org.unreal.modelrouter.security，应为 org.unreal.modelrouter.auth',
                    'matched': m.group()
                })
                count += 1

        print(f"  发现 {count} 个错误包名引用")

    def check_docker_uid(self):
        """检查 Docker UID"""
        print("\n[6/8] 检查 Docker UID...")
        docs = self._scan_docs()
        count = 0

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            # 检查错误的 UID
            for pattern in [r'--user 1001:1001', r'runAsUser: 1001\b', r'fsGroup: 1001\b']:
                for m in re.finditer(pattern, content):
                    line_num = content[:m.start()].count('\n') + 1
                    self.errors.append({
                        'severity': 'HIGH',
                        'file': rel_path,
                        'line': line_num,
                        'message': f'Docker/K8s UID 使用了 1001，应为 10010',
                        'matched': m.group()
                    })
                    count += 1

        print(f"  发现 {count} 个错误 UID")

    def check_api_endpoint_paths(self):
        """检查 API 端点路径格式"""
        print("\n[7/8] 检查 API 端点路径...")
        docs = self._scan_docs()
        count = 0

        # 收集实际的 API 端点
        actual_endpoints: Set[str] = set()
        java_files = globmod.glob(str(self.project_root / 'src/**/*.java'), recursive=True)
        for jf in java_files:
            content = self._read_file(jf)
            # 匹配 @RequestMapping, @GetMapping, @PostMapping 等
            for m in re.finditer(r'@(?:Request|Get|Post|Put|Delete|Patch)Mapping\(\s*["\']([^"\']+)', content):
                actual_endpoints.add(m.group(1))

        # 检查文档中的端点
        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            for m in re.finditer(r'(?:GET|POST|PUT|DELETE|PATCH)\s+(/\S+)', content):
                endpoint = m.group(1).rstrip('`').rstrip('|')
                # 跳过明显的模板路径
                if '{' in endpoint and '}' in endpoint:
                    continue
                # 跳过 actuator 标准端点
                if endpoint.startswith('/actuator/'):
                    continue

        print(f"  扫描了 {len(actual_endpoints)} 个实际 API 端点")

    def check_config_prefix_consistency(self):
        """检查配置前缀一致性"""
        print("\n[8/8] 检查配置前缀一致性...")
        docs = self._scan_docs()
        count = 0

        # 路由配置应使用 model.* 前缀
        wrong_prefixes = [
            r'jairouter:\s*\n\s+adapter:',
            r'jairouter:\s*\n\s+loadbalancer:',
            r'jairouter:\s*\n\s+ratelimit:',
            r'jairouter:\s*\n\s+circuitbreaker:',
        ]

        for doc_path in docs:
            content = self._read_file(doc_path)
            rel_path = os.path.relpath(doc_path, self.project_root)

            for pattern in wrong_prefixes:
                for m in re.finditer(pattern, content):
                    line_num = content[:m.start()].count('\n') + 1
                    self.errors.append({
                        'severity': 'CRITICAL',
                        'file': rel_path,
                        'line': line_num,
                        'message': '路由配置使用了 jairouter.* 前缀，应为 model.*',
                        'matched': m.group().strip()
                    })
                    count += 1

        print(f"  发现 {count} 个错误配置前缀")

    def print_report(self):
        """打印检查报告"""
        print("\n" + "=" * 60)
        print("检查报告")
        print("=" * 60)

        if self.errors:
            print(f"\n❌ 发现 {len(self.errors)} 个错误:")
            for e in self.errors:
                print(f"  [{e['severity']}] {e['file']}:{e['line']} - {e['message']}")
                if 'matched' in e:
                    print(f"         匹配: {e['matched']}")
        else:
            print("\n✅ 未发现错误")

        if self.warnings:
            print(f"\n⚠️  发现 {len(self.warnings)} 个警告:")
            for w in self.warnings:
                print(f"  [{w['severity']}] {w['file']}:{w['line']} - {w['message']}")

        # 输出 JSON 报告
        report = {
            'errors': len(self.errors),
            'warnings': len(self.warnings),
            'details': self.errors + self.warnings
        }
        report_path = self.project_root / 'docs-sync-report.json'
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"\n📄 详细报告已保存到: {report_path}")

        total = len(self.errors) + len(self.warnings)
        print(f"\n总计: {len(self.errors)} 错误, {len(self.warnings)} 警告, {total} 问题")


def main():
    import argparse
    parser = argparse.ArgumentParser(description='文档与代码同步检查')
    parser.add_argument('--project-root', default='.', help='项目根目录')
    parser.add_argument('--fail-on-error', action='store_true', help='有错误时返回非零退出码')
    args = parser.parse_args()

    checker = DocsSyncChecker(args.project_root, args.fail_on_error)
    passed = checker.run_all_checks()

    if not passed and args.fail_on_error:
        sys.exit(1)
    sys.exit(0)


if __name__ == '__main__':
    main()
