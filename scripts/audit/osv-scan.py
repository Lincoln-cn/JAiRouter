#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用 OSV.dev 对 Maven 运行时依赖做漏洞扫描（issue #931/#933 的落地脚本）。

为什么需要它：`mvn dependency:list` 只给版本，不给「这个版本有没有已知漏洞」。项目的
依赖由 Spring Boot BOM 统一管理，补丁版本长期落后一两个版本时没有任何信号 —— 2026-10-31
的一次扫描里，27 个 artifact 命中 102 条 advisory（4 条 CRITICAL），而 CI 完全没有察觉。

用法：

    python scripts/audit/osv-scan.py --deps deps.txt --fail-on high

`--deps` 指向 `mvn dependency:list` 的输出；不给时会自己调 maven 生成到临时文件。

退出码：0 = 通过（或只剩 baseline 里已接受的风险）；1 = 命中未接受的风险；2 = 运行错误。

baseline（`scripts/audit/osv-baseline.json`）只允许登记**经评估确认不可达或上游无回补**的条目，
每条必须写 `reason`。它存在的意义是「让门闩保持牙齿」，不是用来消音。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from typing import Dict, List, Tuple

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/"

ANSI = re.compile(r"\x1b\[[0-9;]*m")
# org.springframework.boot:spring-boot:jar:3.5.5:compile
GAV = re.compile(r"([\w.\-]+):([\w.\-]+):(jar|pom):([\w.\-]+):(compile|runtime|provided)")

SEVERITY_ORDER = {"CRITICAL": 4, "HIGH": 3, "MODERATE": 2, "MEDIUM": 2, "LOW": 1, "UNKNOWN": 0}
THRESHOLD = {"critical": 4, "high": 3, "moderate": 2, "low": 1, "none": 99}


def strip_ansi(text: str) -> str:
    return ANSI.sub("", text)


def generate_deps() -> str:
    out = os.path.join(tempfile.mkdtemp(prefix="osv-deps-"), "deps.txt")
    cmd = [
        "mvn", "-B", "-q", "dependency:list",
        "-DincludeScope=runtime",
        f"-DoutputFile={out}",
        "-DappendOutput=false",
    ]
    print(f"[deps] {' '.join(cmd)}")
    subprocess.run(cmd, check=True)
    return out


def parse_deps(path: str) -> List[Tuple[str, str]]:
    seen: Dict[str, str] = {}
    with open(path, encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            m = GAV.search(strip_ansi(line))
            if not m:
                continue
            group, artifact, _, version = m.group(1), m.group(2), m.group(3), m.group(4)
            seen.setdefault(f"{group}:{artifact}", version)
    return sorted(seen.items())


def post_json(url: str, payload: dict) -> dict:
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.load(resp)


def query_osv(packages: List[Tuple[str, str]]) -> List[dict]:
    hits: List[dict] = []
    chunk = 200
    for start in range(0, len(packages), chunk):
        part = packages[start:start + chunk]
        queries = [
            {"package": {"name": name, "ecosystem": "Maven"}, "version": version}
            for name, version in part
        ]
        data = post_json(OSV_BATCH, {"queries": queries})
        for (name, version), result in zip(part, data.get("results", [])):
            vulns = result.get("vulns") or []
            if vulns:
                hits.append({
                    "package": name,
                    "version": version,
                    "vulns": [v.get("id") for v in vulns],
                })
    return hits


def fetch_details(ids: List[str]) -> Dict[str, dict]:
    details: Dict[str, dict] = {}
    for vid in ids:
        try:
            with urllib.request.urlopen(OSV_VULN + vid, timeout=60) as resp:
                rec = json.load(resp)
        except (urllib.error.URLError, urllib.error.HTTPError) as exc:  # pragma: no cover - 网络
            print(f"[warn] 取 {vid} 详情失败: {exc}", file=sys.stderr)
            continue
        severity = rec.get("database_specific", {}).get("severity")
        if not severity:
            scores = [s.get("score") for s in rec.get("severity", []) or []]
            severity = scores[0] if scores else "UNKNOWN"
        fixed = sorted({
            event["fixed"]
            for affected in rec.get("affected", [])
            for rng in affected.get("ranges", [])
            for event in rng.get("events", [])
            if "fixed" in event
        })
        details[vid] = {
            "severity": str(severity).upper(),
            "summary": rec.get("summary") or "",
            "fixed": fixed,
            "aliases": rec.get("aliases", []),
        }
    return details


def load_baseline(path: str) -> Dict[str, str]:
    if not path or not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    accepted = data.get("accepted", [])
    return {item["id"]: item.get("reason", "") for item in accepted}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Maven 依赖 OSV 漏洞扫描")
    parser.add_argument("--deps", help="mvn dependency:list 的输出文件；省略则自动生成")
    parser.add_argument("--baseline", default="scripts/audit/osv-baseline.json",
                        help="已接受风险的登记文件")
    parser.add_argument("--fail-on", default="high",
                        choices=["critical", "high", "moderate", "low", "none"],
                        help="达到该级别即判红（默认 high）")
    parser.add_argument("--json", help="把完整结果写到该文件")
    args = parser.parse_args(argv)

    deps_path = args.deps or generate_deps()
    packages = parse_deps(deps_path)
    print(f"[deps] 运行时依赖 {len(packages)} 个（去重后按 group:artifact）")
    if not packages:
        print("[error] 没解析到任何依赖，检查 --deps 指向的文件格式", file=sys.stderr)
        return 2

    hits = query_osv(packages)
    all_ids = sorted({vid for hit in hits for vid in hit["vulns"]})
    details = fetch_details(all_ids) if all_ids else {}
    baseline = load_baseline(args.baseline)

    threshold = THRESHOLD[args.fail_on]
    blocking: List[dict] = []
    accepted: List[dict] = []

    for hit in hits:
        for vid in hit["vulns"]:
            info = details.get(vid, {})
            severity = info.get("severity", "UNKNOWN")
            entry = {
                "package": hit["package"],
                "version": hit["version"],
                "id": vid,
                "severity": severity,
                "summary": info.get("summary", ""),
                "fixed": info.get("fixed", []),
            }
            if vid in baseline:
                entry["baseline_reason"] = baseline[vid]
                accepted.append(entry)
            elif SEVERITY_ORDER.get(severity, 0) >= threshold:
                blocking.append(entry)

    print()
    print(f"{'package':52} {'version':16} {'id':24} {'severity':9} fixed")
    for entry in sorted(blocking + accepted, key=lambda e: -SEVERITY_ORDER.get(e["severity"], 0)):
        marker = "（baseline）" if "baseline_reason" in entry else ""
        fixed = ",".join(entry["fixed"][:3])
        print(f"{entry['package']:52} {entry['version']:16} {entry['id']:24} "
              f"{entry['severity']:9} {fixed} {marker}")

    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump({"blocking": blocking, "accepted": accepted}, fh,
                      ensure_ascii=False, indent=2)

    print()
    print(f"[result] 命中未接受风险 {len(blocking)} 条（阈值 {args.fail_on} 及以上）；"
          f"已登记接受 {len(accepted)} 条")
    if blocking:
        print("[result] 判红：请在 PR 里升级依赖，或在 osv-baseline.json 里登记并写明理由")
        return 1
    print("[result] 通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
