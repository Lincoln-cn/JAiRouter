#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文档链接检查脚本
检查文档中的所有链接，生成详细的检查报告
"""

import os
import re
import sys
import json
import time
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Tuple, Set
from urllib.parse import urljoin, urlparse
import argparse


class LinkChecker:
    def __init__(
        self, base_dir: str = "docs", output_file: str = "link-check-report.json"
    ):
        self.base_dir = Path(base_dir)
        self.output_file = output_file
        self.checked_urls: Set[str] = set()
        self.results = {
            "timestamp": datetime.now().isoformat(),
            "summary": {
                "total_files": 0,
                "total_links": 0,
                "valid_links": 0,
                "invalid_links": 0,
                "skipped_links": 0,
            },
            "files": [],
            "invalid_links": [],
            "skipped_patterns": [],
        }

        # 忽略的链接模式
        self.ignore_patterns = [
            r"^mailto:",
            r"^tel:",
            r"^#",
            r"^javascript:",
            r"^http://localhost",
            r"^https://localhost",
            r"^http://127\.0\.0\.1",
            r"^https://127\.0\.0\.1",
            r"\.(jpg|jpeg|png|gif|svg|ico|pdf)$",
        ]

        # HTTP 请求头
        self.headers = {
            "User-Agent": "Mozilla/5.0 (compatible; DocumentationLinkChecker/1.0)",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Accept-Encoding": "gzip, deflate",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
        }

    def should_ignore_link(self, url: str) -> bool:
        """检查链接是否应该被忽略"""
        for pattern in self.ignore_patterns:
            if re.search(pattern, url, re.IGNORECASE):
                return True
        return False

    def extract_links_from_file(self, file_path: Path) -> List[Tuple[str, int]]:
        """从 Markdown 文件中提取所有链接"""
        links = []
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()

            # 匹配 Markdown 链接格式 [text](url)
            markdown_links = re.finditer(r"\[([^\]]*)\]\(([^)]+)\)", content)
            for match in markdown_links:
                url = match.group(2)
                line_num = content[: match.start()].count("\n") + 1
                links.append((url, line_num))

            # 匹配 HTML 链接格式 <a href="url">
            html_links = re.finditer(
                r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>', content, re.IGNORECASE
            )
            for match in html_links:
                url = match.group(1)
                line_num = content[: match.start()].count("\n") + 1
                links.append((url, line_num))

            # 匹配直接的 URL
            url_pattern = r'https?://[^\s<>"\'`\[\](){}]+'
            direct_urls = re.finditer(url_pattern, content)
            for match in direct_urls:
                url = match.group(0)
                line_num = content[: match.start()].count("\n") + 1
                # 避免重复添加已经在 Markdown 或 HTML 链接中的 URL
                if not any(url in link[0] for link in links):
                    links.append((url, line_num))

        except Exception as e:
            print(f"❌ 读取文件失败 {file_path}: {e}")

        return links

    def check_internal_link(self, url: str, current_file: Path) -> bool:
        """检查内部链接（相对路径）"""
        try:
            # 处理相对路径
            if (
                url.startswith("./")
                or url.startswith("../")
                or not url.startswith(("http://", "https://", "/"))
            ):
                # 相对于当前文件的路径
                target_path = (current_file.parent / url).resolve()
            elif url.startswith("/"):
                # 相对于文档根目录的路径
                target_path = (self.base_dir / url.lstrip("/")).resolve()
            else:
                return True  # 不是内部链接

            # 移除锚点
            if "#" in str(target_path):
                target_path = Path(str(target_path).split("#")[0])

            # 检查文件是否存在
            if target_path.exists():
                return True

            # 如果是目录，检查是否有 index.md
            if target_path.is_dir():
                index_file = target_path / "index.md"
                return index_file.exists()

            # 尝试添加 .md 扩展名
            if not target_path.suffix:
                md_file = target_path.with_suffix(".md")
                return md_file.exists()

            return False

        except Exception:
            return False

    def check_external_link(self, url: str) -> Tuple[bool, str]:
        """检查外部链接（HTTP/HTTPS）"""
        if url in self.checked_urls:
            return True, "已检查过"

        try:
            req = urllib.request.Request(url, headers=self.headers)
            with urllib.request.urlopen(req, timeout=30) as response:
                status_code = response.getcode()
                self.checked_urls.add(url)
                if status_code in [
                    200,
                    301,
                    302,
                    403,
                ]:  # 403 可能是防爬虫，但链接可能有效
                    return True, f"HTTP {status_code}"
                else:
                    return False, f"HTTP {status_code}"

        except urllib.error.HTTPError as e:
            self.checked_urls.add(url)
            if e.code in [403, 429]:  # 可能是防爬虫
                return True, f"HTTP {e.code} (可能被防爬虫保护)"
            return False, f"HTTP {e.code}"

        except urllib.error.URLError as e:
            self.checked_urls.add(url)
            return False, f"URL错误: {e.reason}"

        except Exception as e:
            self.checked_urls.add(url)
            return False, f"检查失败: {str(e)}"

    def check_link(self, url: str, current_file: Path) -> Tuple[bool, str]:
        """检查单个链接"""
        # 清理 URL
        url = url.strip()

        # 移除查询参数和锚点进行检查（但保留原始 URL 用于报告）
        clean_url = url.split("?")[0].split("#")[0]

        if url.startswith(("http://", "https://")):
            return self.check_external_link(url)
        else:
            is_valid = self.check_internal_link(clean_url, current_file)
            return is_valid, "内部链接" if is_valid else "文件不存在"

    def check_file(self, file_path: Path) -> Dict:
        """检查单个文件中的所有链接"""
        print(f"📄 检查文件: {file_path}")

        file_result = {
            "file": str(file_path.relative_to(Path.cwd())),
            "links": [],
            "summary": {"total": 0, "valid": 0, "invalid": 0, "skipped": 0},
        }

        links = self.extract_links_from_file(file_path)
        file_result["summary"]["total"] = len(links)

        for url, line_num in links:
            if self.should_ignore_link(url):
                file_result["summary"]["skipped"] += 1
                file_result["links"].append(
                    {
                        "url": url,
                        "line": line_num,
                        "status": "skipped",
                        "message": "匹配忽略模式",
                    }
                )
                continue

            is_valid, message = self.check_link(url, file_path)

            link_result = {
                "url": url,
                "line": line_num,
                "status": "valid" if is_valid else "invalid",
                "message": message,
            }

            file_result["links"].append(link_result)

            if is_valid:
                file_result["summary"]["valid"] += 1
                print(f"  ✅ {url}")
            else:
                file_result["summary"]["invalid"] += 1
                print(f"  ❌ {url} - {message}")

                # 添加到全局无效链接列表
                self.results["invalid_links"].append(
                    {
                        "file": str(file_path.relative_to(Path.cwd())),
                        "url": url,
                        "line": line_num,
                        "message": message,
                    }
                )

            # 添加延迟避免请求过快
            time.sleep(0.5)

        return file_result

    def run(self) -> bool:
        """运行链接检查"""
        print(f"🔍 开始检查文档链接...")
        print(f"📁 检查目录: {self.base_dir}")

        if not self.base_dir.exists():
            print(f"❌ 目录不存在: {self.base_dir}")
            return False

        # 查找所有 Markdown 文件
        md_files = list(self.base_dir.rglob("*.md"))

        # 也检查根目录的 README 文件
        for readme in ["README.md", "README-ZH.md"]:
            readme_path = Path(readme)
            if readme_path.exists():
                md_files.append(readme_path)

        if not md_files:
            print("⚠️  未找到 Markdown 文件")
            return True

        print(f"📋 找到 {len(md_files)} 个 Markdown 文件")

        self.results["summary"]["total_files"] = len(md_files)

        # 检查每个文件
        for file_path in md_files:
            file_result = self.check_file(file_path)
            self.results["files"].append(file_result)

            # 更新总计
            self.results["summary"]["total_links"] += file_result["summary"]["total"]
            self.results["summary"]["valid_links"] += file_result["summary"]["valid"]
            self.results["summary"]["invalid_links"] += file_result["summary"][
                "invalid"
            ]
            self.results["summary"]["skipped_links"] += file_result["summary"][
                "skipped"
            ]

        # 生成报告
        self.generate_report()

        # 输出总结
        summary = self.results["summary"]
        print(f"\n📊 检查完成:")
        print(f"  📁 文件数量: {summary['total_files']}")
        print(f"  🔗 链接总数: {summary['total_links']}")
        print(f"  ✅ 有效链接: {summary['valid_links']}")
        print(f"  ❌ 无效链接: {summary['invalid_links']}")
        print(f"  ⏭️  跳过链接: {summary['skipped_links']}")

        if summary["invalid_links"] > 0:
            print(f"\n❌ 发现 {summary['invalid_links']} 个无效链接:")
            for invalid_link in self.results["invalid_links"]:
                print(
                    f"  - {invalid_link['file']}:{invalid_link['line']} - {invalid_link['url']}"
                )
                print(f"    错误: {invalid_link['message']}")

        return summary["invalid_links"] == 0

    def generate_report(self):
        """生成检查报告"""
        try:
            with open(self.output_file, "w", encoding="utf-8") as f:
                json.dump(self.results, f, ensure_ascii=False, indent=2)
            print(f"📄 报告已生成: {self.output_file}")
        except Exception as e:
            print(f"❌ 生成报告失败: {e}")


def main():
    parser = argparse.ArgumentParser(description="检查文档中的链接有效性")
    parser.add_argument("--dir", default="docs", help="要检查的文档目录 (默认: docs)")
    parser.add_argument(
        "--output",
        default="link-check-report.json",
        help="输出报告文件 (默认: link-check-report.json)",
    )
    parser.add_argument(
        "--fail-on-error", action="store_true", help="发现无效链接时退出码为1"
    )

    args = parser.parse_args()

    checker = LinkChecker(args.dir, args.output)
    success = checker.run()

    if args.fail_on_error and not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
