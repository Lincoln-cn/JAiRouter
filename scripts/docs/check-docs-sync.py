#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文档内容同步检查脚本

检查文档与代码的同步性，验证配置示例和 API 文档的准确性。
"""

import os
import re
import sys
import yaml
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Set, Tuple, Any
from dataclasses import dataclass
from enum import Enum


class CheckResult(Enum):
    """检查结果枚举"""
    PASS = "PASS"
    WARN = "WARN"
    FAIL = "FAIL"


@dataclass
class SyncIssue:
    """同步问题数据类"""
    file_path: str
    issue_type: str
    description: str
    severity: CheckResult
    line_number: int = 0
    suggestion: str = ""


class DocumentSyncChecker:
    """文档同步检查器"""
    
    def __init__(self, project_root: str = "."):
        self.project_root = Path(project_root)
        self.issues: List[SyncIssue] = []
        self.config_data = {}
        self.pom_data = {}
        
    def load_project_config(self):
        """加载项目配置数据"""
        try:
            # 加载 application.yml
            app_config_path = self.project_root / "src/main/resources/application.yml"
            if app_config_path.exists():
                with open(app_config_path, 'r', encoding='utf-8') as f:
                    self.config_data = yaml.safe_load(f)
            
            # 加载 pom.xml
            pom_path = self.project_root / "pom.xml"
            if pom_path.exists():
                tree = ET.parse(pom_path)
                root = tree.getroot()
                self.pom_data = self._parse_pom_xml(root)
                
        except Exception as e:
            self.add_issue(
                "项目配置加载",
                "CONFIG_LOAD_ERROR",
                f"无法加载项目配置: {str(e)}",
                CheckResult.FAIL
            )
    
    def _parse_pom_xml(self, root) -> Dict:
        """解析 POM XML 数据"""
        ns = {'maven': 'http://maven.apache.org/POM/4.0.0'}
        
        # 提取基本信息
        pom_data = {
            'groupId': self._get_xml_text(root, './/maven:groupId', ns),
            'artifactId': self._get_xml_text(root, './/maven:artifactId', ns),
            'version': self._get_xml_text(root, './/maven:version', ns),
            'dependencies': [],
            'plugins': [],
            'profiles': []
        }
        
        # 提取依赖
        for dep in root.findall('.//maven:dependencies/maven:dependency', ns):
            pom_data['dependencies'].append({
                'groupId': self._get_xml_text(dep, './maven:groupId', ns),
                'artifactId': self._get_xml_text(dep, './maven:artifactId', ns),
                'version': self._get_xml_text(dep, './maven:version', ns)
            })
        
        # 提取插件
        for plugin in root.findall('.//maven:plugins/maven:plugin', ns):
            pom_data['plugins'].append({
                'groupId': self._get_xml_text(plugin, './maven:groupId', ns),
                'artifactId': self._get_xml_text(plugin, './maven:artifactId', ns),
                'version': self._get_xml_text(plugin, './maven:version', ns)
            })
        
        # 提取 Profile
        for profile in root.findall('.//maven:profiles/maven:profile', ns):
            pom_data['profiles'].append({
                'id': self._get_xml_text(profile, './maven:id', ns)
            })
        
        return pom_data
    
    def _get_xml_text(self, element, xpath: str, ns: Dict) -> str:
        """安全获取 XML 元素文本"""
        found = element.find(xpath, ns)
        return found.text if found is not None else ""
    
    def check_configuration_docs(self):
        """检查配置文档的准确性"""
        config_docs_path = self.project_root / "docs/zh/configuration"
        
        if not config_docs_path.exists():
            self.add_issue(
                str(config_docs_path),
                "MISSING_CONFIG_DOCS",
                "配置文档目录不存在",
                CheckResult.FAIL
            )
            return
        
        # 检查应用配置文档
        self._check_application_config_doc()
        
        # 检查负载均衡配置文档
        self._check_load_balancing_config_doc()
        
        # 检查限流配置文档
        self._check_rate_limiting_config_doc()
        
        # 检查熔断器配置文档
        self._check_circuit_breaker_config_doc()
    
    def _check_application_config_doc(self):
        """检查应用配置文档"""
        doc_path = self.project_root / "docs/zh/configuration/application-config.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "应用配置文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查端口配置
        if 'server.port' in content:
            actual_port = self.config_data.get('server', {}).get('port', 8080)
            if str(actual_port) not in content:
                self.add_issue(
                    str(doc_path),
                    "CONFIG_MISMATCH",
                    f"文档中的端口配置与实际配置不符，实际端口: {actual_port}",
                    CheckResult.WARN
                )
        
        # 检查适配器配置
        if 'adapter:' in content:
            actual_adapter = self.config_data.get('model', {}).get('adapter', 'gpustack')
            if actual_adapter not in content:
                self.add_issue(
                    str(doc_path),
                    "CONFIG_MISMATCH",
                    f"文档中缺少实际使用的适配器: {actual_adapter}",
                    CheckResult.WARN
                )
    
    def _check_load_balancing_config_doc(self):
        """检查负载均衡配置文档"""
        doc_path = self.project_root / "docs/zh/configuration/load-balancing.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "负载均衡配置文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查负载均衡策略
        lb_config = self.config_data.get('model', {}).get('load-balance', {})
        actual_type = lb_config.get('type', 'random')
        
        # 定义支持的负载均衡类型
        supported_types = ['random', 'round-robin', 'least-connections', 'ip-hash']
        
        for lb_type in supported_types:
            if lb_type not in content:
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_DOC",
                    f"文档中缺少负载均衡类型说明: {lb_type}",
                    CheckResult.WARN
                )
        
        # 检查哈希算法配置
        if 'ip-hash' in content and 'hash-algorithm' in content:
            actual_hash = lb_config.get('hash-algorithm', 'md5')
            if actual_hash not in content:
                self.add_issue(
                    str(doc_path),
                    "CONFIG_MISMATCH",
                    f"文档中缺少实际使用的哈希算法: {actual_hash}",
                    CheckResult.WARN
                )
    
    def _check_rate_limiting_config_doc(self):
        """检查限流配置文档"""
        doc_path = self.project_root / "docs/zh/configuration/rate-limiting.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "限流配置文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查限流算法
        rate_limit_config = self.config_data.get('model', {}).get('rate-limit', {})
        actual_algorithm = rate_limit_config.get('algorithm', 'token-bucket')
        
        # 定义支持的限流算法
        supported_algorithms = ['token-bucket', 'leaky-bucket', 'sliding-window', 'warm-up']
        
        for algorithm in supported_algorithms:
            if algorithm not in content:
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_DOC",
                    f"文档中缺少限流算法说明: {algorithm}",
                    CheckResult.WARN
                )
        
        # 检查限流配置参数
        required_params = ['capacity', 'rate', 'scope']
        for param in required_params:
            if param in rate_limit_config and param not in content:
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_DOC",
                    f"文档中缺少限流参数说明: {param}",
                    CheckResult.WARN
                )
    
    def _check_circuit_breaker_config_doc(self):
        """检查熔断器配置文档"""
        doc_path = self.project_root / "docs/zh/configuration/circuit-breaker.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "熔断器配置文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查熔断器配置参数
        cb_config = self.config_data.get('model', {}).get('circuit-breaker', {})
        
        required_params = ['failureThreshold', 'timeout', 'successThreshold']
        for param in required_params:
            if param in cb_config and param not in content:
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_DOC",
                    f"文档中缺少熔断器参数说明: {param}",
                    CheckResult.WARN
                )
    
    def check_api_documentation(self):
        """检查 API 文档的准确性"""
        api_docs_path = self.project_root / "docs/zh/api-reference"
        
        if not api_docs_path.exists():
            self.add_issue(
                str(api_docs_path),
                "MISSING_API_DOCS",
                "API 文档目录不存在",
                CheckResult.FAIL
            )
            return
        
        # 检查统一 API 文档
        self._check_universal_api_doc()
        
        # 检查管理 API 文档
        self._check_management_api_doc()
    
    def _check_universal_api_doc(self):
        """检查统一 API 文档"""
        doc_path = self.project_root / "docs/zh/api-reference/universal-api.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "统一 API 文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查服务端点
        services = self.config_data.get('model', {}).get('services', {})
        
        # 定义预期的 API 端点
        expected_endpoints = [
            '/v1/chat/completions',
            '/v1/embeddings',
            '/v1/rerank',
            '/v1/audio/speech',
            '/v1/audio/transcriptions',
            '/v1/images/generations',
            '/v1/images/edits'
        ]
        
        for endpoint in expected_endpoints:
            if endpoint not in content:
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_API_DOC",
                    f"文档中缺少 API 端点说明: {endpoint}",
                    CheckResult.WARN
                )
        
        # 检查服务配置与文档的一致性
        for service_name in services.keys():
            if service_name not in content:
                self.add_issue(
                    str(doc_path),
                    "SERVICE_DOC_MISMATCH",
                    f"文档中缺少服务说明: {service_name}",
                    CheckResult.WARN
                )
    
    def _check_management_api_doc(self):
        """检查管理 API 文档"""
        doc_path = self.project_root / "docs/zh/api-reference/management-api.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "管理 API 文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查 Actuator 端点
        actuator_config = self.config_data.get('management', {})
        exposed_endpoints = actuator_config.get('endpoints', {}).get('web', {}).get('exposure', {}).get('include', '')
        
        if exposed_endpoints:
            endpoints = [ep.strip() for ep in exposed_endpoints.split(',')]
            for endpoint in endpoints:
                if endpoint not in content:
                    self.add_issue(
                        str(doc_path),
                        "INCOMPLETE_API_DOC",
                        f"文档中缺少管理端点说明: {endpoint}",
                        CheckResult.WARN
                    )
    
    def check_dependency_documentation(self):
        """检查依赖文档的准确性"""
        # 检查 Maven 依赖文档
        self._check_maven_dependencies_doc()
        
        # 检查 Spring Boot 版本文档
        self._check_spring_boot_version_doc()
    
    def _check_maven_dependencies_doc(self):
        """检查 Maven 依赖文档"""
        doc_paths = [
            self.project_root / "docs/zh/getting-started/installation.md",
            self.project_root / "docs/zh/development/index.md"
        ]
        
        for doc_path in doc_paths:
            if not doc_path.exists():
                continue
                
            with open(doc_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 检查关键依赖是否在文档中提及
            key_dependencies = [
                'spring-boot-starter-webflux',
                'micrometer-registry-prometheus',
                'springdoc-openapi-starter-webflux-ui'
            ]
            
            for dep in key_dependencies:
                if dep not in content:
                    self.add_issue(
                        str(doc_path),
                        "MISSING_DEPENDENCY_DOC",
                        f"文档中缺少关键依赖说明: {dep}",
                        CheckResult.WARN
                    )
    
    def _check_spring_boot_version_doc(self):
        """检查 Spring Boot 版本文档"""
        doc_path = self.project_root / "docs/zh/getting-started/installation.md"
        
        if not doc_path.exists():
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 从 POM 中获取 Spring Boot 版本
        parent_version = None
        pom_path = self.project_root / "pom.xml"
        
        if pom_path.exists():
            tree = ET.parse(pom_path)
            root = tree.getroot()
            ns = {'maven': 'http://maven.apache.org/POM/4.0.0'}
            
            parent = root.find('./maven:parent', ns)
            if parent is not None:
                version_elem = parent.find('./maven:version', ns)
                if version_elem is not None:
                    parent_version = version_elem.text
        
        if parent_version and parent_version not in content:
            self.add_issue(
                str(doc_path),
                "VERSION_MISMATCH",
                f"文档中的 Spring Boot 版本与 POM 中的版本不符，实际版本: {parent_version}",
                CheckResult.WARN
            )
    
    def check_docker_documentation(self):
        """检查 Docker 文档的准确性"""
        doc_path = self.project_root / "docs/zh/deployment/docker.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "Docker 部署文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查 Dockerfile 是否存在
        dockerfile_path = self.project_root / "Dockerfile"
        if dockerfile_path.exists():
            with open(dockerfile_path, 'r', encoding='utf-8') as f:
                dockerfile_content = f.read()
            
            # 检查端口配置
            if 'EXPOSE' in dockerfile_content:
                expose_match = re.search(r'EXPOSE\s+(\d+)', dockerfile_content)
                if expose_match:
                    exposed_port = expose_match.group(1)
                    if exposed_port not in content:
                        self.add_issue(
                            str(doc_path),
                            "PORT_MISMATCH",
                            f"文档中缺少 Docker 暴露端口说明: {exposed_port}",
                            CheckResult.WARN
                        )
        
        # 检查 docker-compose 文件
        compose_files = [
            "docker-compose.yml",
            "docker-compose.dev.yml",
            "docker-compose-monitoring.yml"
        ]
        
        for compose_file in compose_files:
            compose_path = self.project_root / compose_file
            if compose_path.exists() and compose_file not in content:
                self.add_issue(
                    str(doc_path),
                    "MISSING_COMPOSE_DOC",
                    f"文档中缺少 Docker Compose 文件说明: {compose_file}",
                    CheckResult.WARN
                )
    
    def check_monitoring_documentation(self):
        """检查监控文档的准确性"""
        doc_path = self.project_root / "docs/zh/monitoring/setup.md"
        
        if not doc_path.exists():
            self.add_issue(
                str(doc_path),
                "MISSING_DOC",
                "监控设置文档不存在",
                CheckResult.FAIL
            )
            return
        
        with open(doc_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查监控配置
        monitoring_config = self.config_data.get('monitoring', {})
        if monitoring_config:
            metrics_config = monitoring_config.get('metrics', {})
            
            # 检查指标前缀
            prefix = metrics_config.get('prefix', 'jairouter')
            if prefix not in content:
                self.add_issue(
                    str(doc_path),
                    "CONFIG_MISMATCH",
                    f"文档中缺少指标前缀说明: {prefix}",
                    CheckResult.WARN
                )
            
            # 检查采集间隔
            interval = metrics_config.get('collection-interval', '10s')
            if interval not in content:
                self.add_issue(
                    str(doc_path),
                    "CONFIG_MISMATCH",
                    f"文档中缺少采集间隔说明: {interval}",
                    CheckResult.WARN
                )
        
        # 检查 Prometheus 配置
        prometheus_config = self.config_data.get('management', {}).get('metrics', {}).get('export', {}).get('prometheus', {})
        if prometheus_config.get('enabled', False):
            if 'prometheus' not in content.lower():
                self.add_issue(
                    str(doc_path),
                    "INCOMPLETE_DOC",
                    "文档中缺少 Prometheus 集成说明",
                    CheckResult.WARN
                )
    
    def add_issue(self, file_path: str, issue_type: str, description: str, 
                  severity: CheckResult, line_number: int = 0, suggestion: str = ""):
        """添加同步问题"""
        issue = SyncIssue(
            file_path=file_path,
            issue_type=issue_type,
            description=description,
            severity=severity,
            line_number=line_number,
            suggestion=suggestion
        )
        self.issues.append(issue)
    
    def run_all_checks(self) -> bool:
        """运行所有检查"""
        print("🔍 开始文档内容同步检查...")
        
        # 加载项目配置
        self.load_project_config()
        
        # 运行各项检查
        self.check_configuration_docs()
        self.check_api_documentation()
        self.check_dependency_documentation()
        self.check_docker_documentation()
        self.check_monitoring_documentation()
        
        return len([issue for issue in self.issues if issue.severity == CheckResult.FAIL]) == 0
    
    def generate_report(self) -> str:
        """生成检查报告"""
        report = []
        report.append("# 文档内容同步检查报告\n")
        
        # 统计信息
        total_issues = len(self.issues)
        fail_count = len([i for i in self.issues if i.severity == CheckResult.FAIL])
        warn_count = len([i for i in self.issues if i.severity == CheckResult.WARN])
        pass_count = len([i for i in self.issues if i.severity == CheckResult.PASS])
        
        report.append(f"## 检查统计\n")
        report.append(f"- 总问题数: {total_issues}")
        report.append(f"- 严重问题: {fail_count}")
        report.append(f"- 警告问题: {warn_count}")
        report.append(f"- 通过检查: {pass_count}\n")
        
        if total_issues == 0:
            report.append("✅ 所有检查都通过了！\n")
            return "\n".join(report)
        
        # 按严重程度分组显示问题
        for severity in [CheckResult.FAIL, CheckResult.WARN]:
            severity_issues = [i for i in self.issues if i.severity == severity]
            if not severity_issues:
                continue
            
            severity_name = "严重问题" if severity == CheckResult.FAIL else "警告问题"
            report.append(f"## {severity_name}\n")
            
            for issue in severity_issues:
                report.append(f"### {issue.issue_type}")
                report.append(f"**文件**: {issue.file_path}")
                report.append(f"**描述**: {issue.description}")
                if issue.line_number > 0:
                    report.append(f"**行号**: {issue.line_number}")
                if issue.suggestion:
                    report.append(f"**建议**: {issue.suggestion}")
                report.append("")
        
        return "\n".join(report)


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description="文档内容同步检查工具")
    parser.add_argument("--project-root", default=".", help="项目根目录路径")
    parser.add_argument("--output", help="输出报告文件路径")
    parser.add_argument("--fail-on-error", action="store_true", help="发现严重问题时退出码为1")
    
    args = parser.parse_args()
    
    # 创建检查器并运行检查
    checker = DocumentSyncChecker(args.project_root)
    success = checker.run_all_checks()
    
    # 生成报告
    report = checker.generate_report()
    
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"📄 报告已保存到: {args.output}")
    else:
        print(report)
    
    # 根据检查结果设置退出码
    if args.fail_on_error and not success:
        print("❌ 发现严重问题，检查失败")
        sys.exit(1)
    else:
        print("✅ 文档同步检查完成")
        sys.exit(0)


if __name__ == "__main__":
    main()