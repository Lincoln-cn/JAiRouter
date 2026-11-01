#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文档链接检查脚本
检查文档中的所有链接，生成详细的检查报告

issue #358：本脚本是**唯一**能让 workflow 变红的链接门闩（同 workflow 里的第三方工具
`tcort/github-action-markdown-link-check` 带 `continue-on-error: true`）。而它此前把
**目标站点侧的偶发 5xx** 也判为「无效链接」⇒ master 会随机变红（实测：
`https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE` 偶发 HTTP 503），
进而训练出「红了就重跑」。现在的口径：

* **4xx（除 403/429）** = 链接不存在 ⇒ 判红（这是门闩真正要抓的东西）；
* **5xx / 连接失败 / 超时** = 目标站点侧失败 ⇒ 先**重试**（默认 2 次，间隔递增）；
* 重试后仍失败时：URL 命中 `TOLERATED_FLAKY_URL_PREFIXES`（已登记的抖动源）⇒ 记为**警告**，不判红；
  否则仍判红（保留门闩的牙齿），并在报告里写明「目标站点侧失败、已重试 N 次」。
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

# 已登记的「站点侧偶发失败」URL 前缀（issue #358）。
# 判据 = ① 我们无法通过改文档修复它；② 历史上真的抖动过（有 run 记录与 issue 留痕）。
# 按**前缀**而不是按主机登记，避免把整个 github.com 都放过。
TOLERATED_FLAKY_URL_PREFIXES = (
    "https://jairouter.com/",
    "https://lincoln-cn.github.io/JAiRouter",
    "https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE",
    # issue #934：站点侧对机房出口 IP 直接断开（CI 上稳定复现 socket hang up，
    # 浏览器打开正常，重试 2 次也无效）。链接本身有效，属目标站点侧限制。
    "https://regexr.com/",
)

# 第 2、3 次尝试前的等待（秒）；超出长度的重试沿用最后一个值。
RETRY_BACKOFF_SECONDS = (1.0, 3.0)


class LinkChecker:
    def __init__(
        self,
        base_dir: str = "docs",
        output_file: str = "link-check-report.json",
        retries: int = 2,
        timeout: int = 30,
    ):
        self.base_dir = Path(base_dir)
        self.output_file = output_file
        self.checked_urls: Set[str] = set()
        self.retries = max(0, retries)
        self.timeout = timeout
        self.tolerated_flaky_prefixes = TOLERATED_FLAKY_URL_PREFIXES
        self.results = {
            "timestamp": datetime.now().isoformat(),
            "summary": {
                "total_files": 0,
                "total_links": 0,
                "valid_links": 0,
                "invalid_links": 0,
                "transient_links": 0,
                "skipped_links": 0,
            },
            "files": [],
            "invalid_links": [],
            "transient_links": [],
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
            # Windows本地路径（包括带行号的）
            r"^file://[A-Za-z]:/",
            # 示例服务URL（内部服务名）
            r"^http://[a-z0-9-]+:(8080|8000|11434|9090|9997|5001|5005|9411|4317|4318)(/|$)",
            r"^http://[a-z0-9-]+-server:\d+",
            r"^http://[a-z0-9-]+-cluster:\d+",
            r"^http://(backend|frontend|new-server|old-server|backup-server|gpu-server|cpu-server|stable-server|fast-server|slow-server|experimental-server|reliable-server|unstable-server|high-risk-server|primary|secondary|backup|openai-proxy|jairouter|prometheus|alertmanager|jaeger|zipkin|grafana|webhook-server|your-webhook|proxy\.example|your-sms-gateway|jairouter_backend)(:|/|$|\$)",
            # 变量模板URL
            r"^\{\{",
            r"\$[a-zA-Z_]+",
            r"VERSION",  # 版本变量占位符
            # shields.io emoji badges（编码问题）
            r"badge/.*%F0%9F",
            r"badge/.*[\U0001F300-\U0001F9FF]",
            # 内部Kubernetes服务地址
            r"\.svc\.cluster\.local",
            # 示例域名
            r"example\.(com|org|net)",
            r"jairouter\.example\.(com|org)",
            r"admin\.jairouter\.example\.(com|org)",
            # 外部镜像源（经常超时或不可达）
            r"maven\.aliyun\.com",
            r"docker\.mirrors\.ustc\.edu\.cn",
            r"hub-mirror\.c\.163\.com",
            r"mirror\.baidubce\.com",
            # Docker官方站点（经常连接重置）
            r"docs\.docker\.com",
            r"www\.docker\.com",
            # Docker Hub
            r"hub\.docker\.com",
            # Maven XSD命名空间（返回404但有效）
            r"maven\.apache\.org/SETTINGS",
            r"maven\.apache\.org/xsd",
            # AI服务商端点（通常不需要文档访问）
            r"dashscope\.aliyuncs\.com",
            r"api\.openai\.com",
            r"api\.anthropic\.com",
            # 内网IP地址
            r"^http://172\.",
            r"^http://10\.",
            r"^http://192\.168\.",
            # webhook示例URL
            r"your-webhook",
            r"webhook-server",
            r"your-sms-gateway",
            # Slack webhook占位符
            r"hooks\.slack\.com/services/YOUR",
            # 示例域名组合
            r"your-webhook-url\.com",
            r"your-org",
            # jairouter.com 故障排查页面（可能尚未部署）
            r"jairouter\.com/troubleshooting/",
            # springfox内部引用（文档生成工具引用）
            r"^springfox/",
            # 单独的文档文件名（相对路径解析失败的情况）
            r"^[a-z-]+\.md$",
            # Java内部类引用
            r"[A-Z][a-zA-Z]+\.java#L\d",
            # nginx变量格式URL
            r"http://[a-z_]+;$",
            # jwt-decoder（第三方工具站点）
            r"jwt-decoder\.com",
            # api.jairouter.com（示例API端点）
            r"api\.jairouter\.com",
            # jairouter.com 中文页面（尚未部署）
            r"jairouter\.com/zh/",
            # httpbin.org（测试服务，经常不可用）
            r"httpbin\.org",
            # 占位符URL（文档示例）
            r"^http://your-",
            r"^http://pushgateway:",
            r"^http://influxdb:",
            # GitHub stargazers（URL可能不存在）
            r"stargazers$",
            # AI服务商端点（需要认证或已变更）
            r"api\.deepseek\.com",
            r"generativelanguage\.googleapis\.com",
            r"api\.hunyuan\.cloud\.tencent\.com",
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

    def _is_tolerated_flaky(self, url: str) -> bool:
        """是否属于「已登记的站点侧抖动源」（issue #358）"""
        return any(url.startswith(prefix) for prefix in self.tolerated_flaky_prefixes)

    def _probe_external_link(self, url: str) -> Tuple[str, str]:
        """单次探测，返回 (kind, message)；kind ∈ {"ok", "invalid", "transient"}"""
        try:
            req = urllib.request.Request(url, headers=self.headers)
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                status_code = response.getcode()
                if status_code in [200, 301, 302, 403]:  # 403 可能是防爬虫，但链接可能有效
                    return "ok", f"HTTP {status_code}"
                if 500 <= status_code <= 599:
                    return "transient", f"HTTP {status_code}"
                return "invalid", f"HTTP {status_code}"

        except urllib.error.HTTPError as e:
            if e.code in [403, 429]:  # 可能是防爬虫 / 限流
                return "ok", f"HTTP {e.code} (可能被防爬虫保护)"
            if 500 <= e.code <= 599:
                return "transient", f"HTTP {e.code}"
            return "invalid", f"HTTP {e.code}"

        except urllib.error.URLError as e:
            return "transient", f"URL错误: {e.reason}"

        except Exception as e:  # 超时等
            return "transient", f"检查失败: {str(e)}"

    def check_external_link(self, url: str) -> Tuple[bool, str, bool]:
        """检查外部链接（HTTP/HTTPS）。

        issue #358：5xx / 连接失败先重试 ``self.retries`` 次；重试后仍失败时，
        只有**已登记的抖动源**降级为警告（第三个返回值为 True），其余仍判红。

        :return: (is_valid, message, is_transient_warning)
        """
        if url in self.checked_urls:
            return True, "已检查过", False

        attempts = 0
        kind, message = "invalid", ""
        while attempts <= self.retries:
            attempts += 1
            kind, message = self._probe_external_link(url)
            if kind != "transient":
                break
            if attempts <= self.retries:
                backoff = RETRY_BACKOFF_SECONDS[
                    min(attempts - 1, len(RETRY_BACKOFF_SECONDS) - 1)
                ]
                time.sleep(backoff)

        self.checked_urls.add(url)
        if attempts > 1:
            message = f"{message}（已重试 {attempts - 1} 次）"

        if kind == "ok":
            return True, message, False
        if kind == "transient" and self._is_tolerated_flaky(url):
            return False, f"{message} —— 目标站点侧失败，且属已登记的抖动源（#358），记为警告", True
        if kind == "transient":
            return False, (
                f"{message} —— 目标站点侧失败（重试后仍失败）。"
                f"若确认是站点侧抖动、不是文档链接写错，请把该 URL 前缀登记到 "
                f"TOLERATED_FLAKY_URL_PREFIXES（issue #358）"
            ), False
        return False, message, False

    def check_link(self, url: str, current_file: Path) -> Tuple[bool, str, bool]:
        """检查单个链接，返回 (is_valid, message, is_transient_warning)"""
        # 清理 URL
        url = url.strip()

        # 移除查询参数和锚点进行检查（但保留原始 URL 用于报告）
        clean_url = url.split("?")[0].split("#")[0]

        if url.startswith(("http://", "https://")):
            return self.check_external_link(url)
        else:
            is_valid = self.check_internal_link(clean_url, current_file)
            return is_valid, "内部链接" if is_valid else "文件不存在", False

    def check_file(self, file_path: Path) -> Dict:
        """检查单个文件中的所有链接"""
        print(f"📄 检查文件: {file_path}")

        # 处理相对路径和绝对路径
        if file_path.is_absolute():
            relative_path = str(file_path.relative_to(Path.cwd()))
        else:
            relative_path = str(file_path)

        file_result = {
            "file": relative_path,
            "links": [],
            "summary": {"total": 0, "valid": 0, "invalid": 0, "transient": 0, "skipped": 0},
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

            is_valid, message, is_transient = self.check_link(url, file_path)

            if is_valid:
                status = "valid"
            elif is_transient:
                status = "transient"
            else:
                status = "invalid"

            link_result = {
                "url": url,
                "line": line_num,
                "status": status,
                "message": message,
            }

            file_result["links"].append(link_result)

            if is_valid:
                file_result["summary"]["valid"] += 1
                print(f"  ✅ {url}")
            elif is_transient:
                file_result["summary"]["transient"] += 1
                print(f"  ⚠️  {url} - {message}")
                self.results["transient_links"].append(
                    {
                        "file": relative_path,
                        "url": url,
                        "line": line_num,
                        "message": message,
                    }
                )
            else:
                file_result["summary"]["invalid"] += 1
                print(f"  ❌ {url} - {message}")

                # 添加到全局无效链接列表
                self.results["invalid_links"].append(
                    {
                        "file": relative_path,
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
            self.results["summary"]["transient_links"] += file_result["summary"][
                "transient"
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
        print(f"  ⚠️  抖动源警告: {summary.get('transient_links', 0)}")
        print(f"  ⏭️  跳过链接: {summary['skipped_links']}")

        if summary.get("transient_links", 0) > 0:
            print(
                f"\n⚠️  有 {summary['transient_links']} 个链接属于**已登记的站点侧抖动源**"
                f"（重试后仍失败，不计为文档缺陷，故不影响退出码；详见报告）："
            )
            for transient_link in self.results.get("transient_links", []):
                print(
                    f"  - {transient_link['file']}:{transient_link['line']} - "
                    f"{transient_link['url']}"
                )
                print(f"    说明: {transient_link['message']}")

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
    parser.add_argument(
        "--retries",
        type=int,
        default=2,
        help="5xx/连接失败的重试次数（默认 2；issue #358）",
    )
    parser.add_argument(
        "--timeout", type=int, default=30, help="单个请求的超时秒数（默认 30）"
    )

    args = parser.parse_args()

    checker = LinkChecker(
        args.dir, args.output, retries=args.retries, timeout=args.timeout
    )
    success = checker.run()

    if args.fail_on_error and not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
