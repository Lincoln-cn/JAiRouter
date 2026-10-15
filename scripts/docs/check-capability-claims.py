#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""路线图「能力清单」↔ 代码的三类**可枚举**对账门闩（issue #425）

## 为什么要它

`docs/{zh,en}/reference/roadmap.md` 的「功能特性规划」是**对外承诺面**：把「已实现」写成「计划中」
会让评估者低估当前能力，把「规划」写成已支持则会让人照文档部署踩空。#425 记录的一次审计就抓到
**5 处「其实已实现、却还写在计划里」**（Gemini / Latency-Aware / API Key 级限流 / 分布式限流 /
PostgreSQL），外加两处互相矛盾的版本号。以前没有任何一条门闩看「能力声明」——
`check-docs-sync.py`（文档↔代码）、`check-error-codes.py`（码表）、`check-api-endpoints.py`（端点）、
`check-config-imports.py`（配置键）都不管这块。

## 判据（只做可枚举映射，**双向**判红；不做「自然语言 ⊆ 代码」的不可判定检查）

三类各有一张**显式映射表**（代码项 → 展示名，中英各一份）：

1. **适配器**：代码项 = `AdapterRegistry` 里 `adapters.put("xxx", …)` 的**字面量注册键**；
2. **负载均衡策略**：代码项 = `router/loadbalancer/**` 下的 `*LoadBalancer` 实现类；
3. **限流算法**：代码项 = `router/ratelimit/**` 下的 `*RateLimiter` 实现类（抽象基类/接口不计）。

对每一类、每一语言，去路线图的对应区段里取两个清单：
「**已支持 / 已实现**」（Supported / Implemented）与「**计划支持 / 计划实现**」（Planned）。
然后**双向**判红：

* **代码 → 文档**：映射表上的代码项必须在「已支持」段出现；只在「计划」段出现 ⇒
  ❌ **「已实现却写在计划里」**（#425 的 A/B 反证就是这条）；两段都没出现 ⇒ ❌ 要求补文档，
  或把展示名登记进**显式豁免表**（`EXEMPTED_LABELS`，每项要写理由）；
* **文档 → 代码**：「已支持」段里的每个名字都必须能落回某个代码项 ⇒ 否则 ❌
  **「路线图说已支持，代码里找不到实现」**（幽灵能力名）；
* 「计划」段里的名字**不得**落到代码项上 ⇒ 落到就是上面那条漂移的另一侧；
* 代码项没有登记的展示名（新加了适配器却忘了填映射表）⇒ ❌（防止门闩静默失效）。

**限定词不入判据**：展示名比较时只取括号/反引号之前的**主名**（base label），
所以「`Latency-Aware（延迟感知，LatencyAwareLoadBalancer）`」按 `Latency-Aware` 比、
「`分布式限流（…；**依赖 Redis**）`」按 `分布式限流` 比 —— 「依赖 Redis」这类限定词
**不参与**对账，由人在映射表的注释里说明。

**反空转**：三类代码清单与两段文档清单都带「必须能检出已知存在的项」断言
（如 `gemini` / `RoundRobinLoadBalancer` / `TokenBucketRateLimiter` / `Ollama` / `Random`）——
否则解析规则一旦失效，「找不到就等于通过」会静默成立。

用法:
    python scripts/docs/check-capability-claims.py --project-root . --fail-on-error

退出码：0 = 无问题；1 = 有判红项（配合 `--fail-on-error`）或脚本自身出错。
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple

ROADMAP_DOCS = {
    "zh": "docs/zh/reference/roadmap.md",
    "en": "docs/en/reference/roadmap.md",
}
LANG_LABEL = {"zh": "zh 路线图", "en": "en 路线图"}

ADAPTER_REGISTRY = "src/main/java/org/unreal/modelrouter/router/adapter/AdapterRegistry.java"
LOADBALANCER_DIR = "src/main/java/org/unreal/modelrouter/router/loadbalancer"
RATELIMIT_DIR = "src/main/java/org/unreal/modelrouter/router/ratelimit"

# 段标题标记：**计划段必须先判**（「计划实现」里也含「实现」）
SUPPORTED_MARKERS = {"zh": ("已支持", "已实现"), "en": ("Supported", "Implemented")}
PLANNED_MARKERS = {"zh": ("计划",), "en": ("Planned",)}

# ---------------- 显式映射表（代码项 → 展示名；表本身也受检查守护） ----------------

# 适配器键 → 路线图里的展示名。键取自 AdapterRegistry 的字面量注册；展示名是**人写的映射**，
# 一旦任一侧改名，两侧集合对不上就会判红（这正是本表的意义）。
# `normal` = OpenAI 兼容适配器（NormalOpenAiAdapter），承载 OpenAI / Azure OpenAI / 阿里云百炼 /
# 腾讯云混元 / 百度智能云 —— 这些服务的适配器都落到 `normal`（见 AdapterRegistry.getAdapterName
# 的 `properties.getAdapter() … orElse("normal")` 兜底与 model-services-base.yml 的
# `adapter: … # 支持: normal, …` 注释）。
ADAPTER_LABELS: Dict[str, Dict[str, List[str]]] = {
    "normal": {"zh": ["OpenAI", "Azure OpenAI", "阿里云百炼", "腾讯云混元", "百度智能云"],
               "en": ["OpenAI", "Azure OpenAI", "Alibaba Bailian", "Tencent Hunyuan", "Baidu Cloud"]},
    "claude": {"zh": ["Anthropic Claude"], "en": ["Anthropic Claude"]},
    "gemini": {"zh": ["Google Gemini"], "en": ["Google Gemini"]},
    "gpustack": {"zh": ["GPUStack"], "en": ["GPUStack"]},
    "ollama": {"zh": ["Ollama"], "en": ["Ollama"]},
    "vllm": {"zh": ["vLLM"], "en": ["vLLM"]},
    "xinference": {"zh": ["Xinference"], "en": ["Xinference"]},
    "localai": {"zh": ["LocalAI"], "en": ["LocalAI"]},
}

# 负载均衡实现类 → 展示名。「Weighted Round Robin」落在 RoundRobinLoadBalancer 上：该类自带权重
# 路径（`hasWeight = instances.stream().anyMatch(i -> i.getWeight() != 1)` + CachedWeightedSelector）。
LOADBALANCER_LABELS: Dict[str, Dict[str, List[str]]] = {
    "RandomLoadBalancer": {"zh": ["Random"], "en": ["Random"]},
    "RoundRobinLoadBalancer": {"zh": ["Round Robin", "Weighted Round Robin"],
                               "en": ["Round Robin", "Weighted Round Robin"]},
    "LeastConnectionsLoadBalancer": {"zh": ["Least Connections"], "en": ["Least Connections"]},
    "IpHashLoadBalancer": {"zh": ["IP Hash"], "en": ["IP Hash"]},
    "ConsistentHashLoadBalancer": {"zh": ["Consistent Hash"], "en": ["Consistent Hash"]},
    "LatencyAwareLoadBalancer": {"zh": ["Latency-Aware"], "en": ["Latency-Aware"]},
    "StickyLoadBalancer": {"zh": ["Sticky"], "en": ["Sticky"]},
}

# 限流实现类 → 展示名。`分布式限流` 落到 RedisSlidingWindowRateLimiter（RateLimitManager 装配）。
RATELIMITER_LABELS: Dict[str, Dict[str, List[str]]] = {
    "TokenBucketRateLimiter": {"zh": ["Token Bucket"], "en": ["Token Bucket"]},
    "LeakyBucketRateLimiter": {"zh": ["Leaky Bucket"], "en": ["Leaky Bucket"]},
    "SlidingWindowRateLimiter": {"zh": ["Sliding Window"], "en": ["Sliding Window"]},
    "WarmUpRateLimiter": {"zh": ["Warm Up"], "en": ["Warm Up"]},
    "RedisSlidingWindowRateLimiter": {"zh": ["分布式限流"], "en": ["Distributed rate limiting"]},
}

# 横切能力：路线图上有声明、代码里**没有同名类**，但确实有实现载体 ⇒ 用显式锚点登记，
# 并**校验锚点符号真实存在**（否则「锚点」会变成绕过门闩的后门）。
EXTRA_ANCHORS: Dict[str, Dict[str, object]] = {
    "ApiKeyValidator#validateRateLimit": {
        "zh": ["API Key 级限流"],
        "en": ["API-key-level rate limiting"],
        "source": "src/main/java/org/unreal/modelrouter/auth/security/service/ApiKeyValidator.java",
        "symbol": "validateRateLimit",
    },
}

# **显式豁免表**：代码里有、但路线图两段都**有意不提**的代码项 ⇒ 展示名写在这里并说明理由。
# 实测当前为空：8 个适配器键、7 个负载均衡实现、5 个限流实现都在路线图里有对应行。
EXEMPTED_LABELS: Dict[str, str] = {}

AREAS: List[dict] = [
    {
        "key": "adapters",
        "title": "适配器",
        "headings": {"zh": ("适配器扩展",), "en": ("Adapter Extensions",)},
        "kind": "adapter",
        "labels": ADAPTER_LABELS,
        "labels_note": "键 → 展示名（normal 是 OpenAI 兼容适配器，承载 5 个平台）",
        "extra_anchors": {},
        "candidates": {"zh": "GPUStack", "en": "GPUStack"},
        "min_code_items": 8,
        "known_code_items": {"gemini", "claude", "ollama", "localai"},
    },
    {
        "key": "loadbalancer",
        "title": "负载均衡策略",
        "headings": {"zh": ("负载均衡策略",), "en": ("Load Balancing Strategies",)},
        "kind": "loadbalancer",
        "labels": LOADBALANCER_LABELS,
        "labels_note": "实现类 → 展示名（Weighted Round Robin 与 Round Robin 共用 RoundRobinLoadBalancer）",
        "extra_anchors": {},
        "candidates": {"zh": "Random", "en": "Random"},
        "min_code_items": 7,
        "known_code_items": {"RoundRobinLoadBalancer", "LatencyAwareLoadBalancer", "StickyLoadBalancer"},
    },
    {
        "key": "ratelimiter",
        "title": "限流算法",
        "headings": {"zh": ("限流算法",), "en": ("Rate Limiting Algorithms",)},
        "kind": "ratelimiter",
        "labels": RATELIMITER_LABELS,
        "labels_note": "实现类 → 展示名；「API Key 级限流」没有同名类，登记在 EXTRA_ANCHORS",
        "extra_anchors": EXTRA_ANCHORS,
        "candidates": {"zh": "Token Bucket", "en": "Token Bucket"},
        "min_code_items": 5,
        "known_code_items": {"TokenBucketRateLimiter", "RedisSlidingWindowRateLimiter"},
    },
]

# ---------------- 解析 ----------------

_ADAPTER_PUT = re.compile(r'adapters\.put\(\s*"([^"$]+)"\s*,')
_HEADING = re.compile(r"^(#{2,6})\s+(.*?)\s*$")
_TABLE_SEPARATOR = re.compile(r"^\s*\|[\s:\-|]+\|\s*$")
_BULLET = re.compile(r"^\s*[-*]\s+(.*)$")
_TABLE_ROW = re.compile(r"^\s*\|")
_QUALIFIER = re.compile(r"[（(]")


def strip_decorations(raw: str) -> str:
    """去掉行首的 ✅/📋 等标记与 markdown 装饰，保留正文。"""
    text = raw.strip()
    while text and not text[0].isalnum():
        text = text[1:].lstrip()
    return text.strip().rstrip("*` ").strip()


def base_label(raw: str) -> str:
    """取展示名的**主名**：截到第一个 `(` / `（` 之前，并去掉反引号与加粗。

    限定词（如「依赖 Redis」）通常写在括号里 ⇒ 一律不入判据。
    """
    text = strip_decorations(raw)
    text = _QUALIFIER.split(text, maxsplit=1)[0]
    return strip_decorations(text)


def normalize(name: str) -> str:
    return " ".join(name.split()).casefold()


def extract_adapter_keys(java_text: str) -> Set[str]:
    """`adapters.put("xxx", …)` 的**字面量**注册键（用变量注册的配置化适配器不算）。"""
    return {m.group(1).lower() for m in _ADAPTER_PUT.finditer(java_text)}


def extract_impl_classes(directory: Path, suffix: str) -> Set[str]:
    """抽实现类：文件名以 `suffix` 结尾、且文件里确实有 `class <Name>`、不是抽象类。"""
    if not directory.is_dir():
        return set()
    found: Set[str] = set()
    for path in sorted(directory.rglob("*" + suffix + ".java")):
        name = path.stem
        text = path.read_text(encoding="utf-8", errors="ignore")
        if not re.search(r"\bclass\s+" + re.escape(name) + r"\b", text):
            continue  # 接口 / 同名文件
        if re.search(r"\babstract\s+class\s+" + re.escape(name) + r"\b", text):
            continue  # 抽象基类不是一份可独立装配的实现
        found.add(name)
    return found


def extract_roadmap_sections(markdown: str, area: dict, lang: str) -> Tuple[Set[str], Set[str]]:
    """取某个能力区段里的（已支持/已实现 名单, 计划支持/计划实现 名单）。

    只认**区段标题下的表格行与列表项**：散文/引用块/变更日志表格里出现的同一个词
    **不**算能力声明（自测里的「非规划段落出现的能力词 ⇒ 不得红」守的就是这条）。
    """
    lines = markdown.splitlines()
    headings: List[Tuple[int, int, str]] = []
    for number, line in enumerate(lines):
        match = _HEADING.match(line)
        if match:
            headings.append((number, len(match.group(1)), match.group(2)))

    supported: Set[str] = set()
    planned: Set[str] = set()
    for position, (start, level, title) in enumerate(headings):
        if not any(marker in title for marker in area["headings"][lang]):
            continue
        end = len(lines)
        for next_start, next_level, _ in headings[position + 1:]:
            if next_level <= level:
                end = next_start
                break
        # 区段内逐个二级以下小标题
        for sub_start, sub_level, sub_title in headings[position + 1:]:
            if sub_start >= end:
                break
            if any(marker in sub_title for marker in PLANNED_MARKERS[lang]):
                bucket = planned
            elif any(marker in sub_title for marker in SUPPORTED_MARKERS[lang]):
                bucket = supported
            else:
                continue
            block_end = end
            for next_start, next_level, _ in headings[position + 1:]:
                if next_start > sub_start and next_level <= sub_level:
                    block_end = min(block_end, next_start)
                    break
            for offset in range(sub_start + 1, block_end):
                line = lines[offset]
                # 表格：跳过表头行（下一行是分隔行）
                if _TABLE_ROW.match(line):
                    if offset + 1 < len(lines) and _TABLE_SEPARATOR.match(lines[offset + 1] or ""):
                        continue
                    if _TABLE_SEPARATOR.match(line):
                        continue
                    cells = [c.strip() for c in line.strip().strip("|").split("|")]
                    if cells and cells[0]:
                        bucket.add(base_label(cells[0]))
                    continue
                bullet = _BULLET.match(line)
                if bullet:
                    label = base_label(bullet.group(1))
                    if label:
                        bucket.add(label)
    return supported, planned


def reconcile_area(area: dict, code_items: Set[str], supported: Set[str],
                   planned: Set[str], lang: str) -> List[str]:
    """三类对账的**双向**判据都在这里（自测直接调它）。"""
    prefix = f"[{area['title']}/{LANG_LABEL[lang]}]"
    labels: Dict[str, Dict[str, List[str]]] = area["labels"]
    extra: Dict[str, Dict[str, object]] = area.get("extra_anchors", {})
    supported_keys = {normalize(n) for n in supported}
    planned_keys = {normalize(n) for n in planned}
    exempt_keys = {normalize(n) for n in EXEMPTED_LABELS}
    problems: List[str] = []

    # 代码 → 映射表：代码项必须都登记了展示名
    for item in sorted(code_items):
        if item not in labels:
            problems.append(f"{prefix} 代码里有、显式映射表没登记: {item}（新增能力请补映射表）")

    # 反向索引：展示名 → 代码项 / 横切锚点
    reverse: Dict[str, str] = {}
    for item in sorted(code_items):
        for name in labels.get(item, {}).get(lang, []):
            reverse[normalize(name)] = item
    for anchor, spec in extra.items():
        for name in spec.get(lang, []):  # type: ignore[union-attr]
            reverse[normalize(str(name))] = anchor

    # 代码 → 文档：登记的展示名必须在「已支持」段
    for item in sorted(code_items):
        for name in labels.get(item, {}).get(lang, []):
            key = normalize(name)
            if key in planned_keys:
                problems.append(f"{prefix} ❌ 已实现却写在「计划」里: {name} ← {item}")
            elif key not in supported_keys and key not in exempt_keys:
                problems.append(f"{prefix} ❌ 路线图两段都没提: {name} ← {item}"
                                f"（补文档，或登记进 EXEMPTED_LABELS 并写理由）")
    for anchor, spec in extra.items():
        for name in spec.get(lang, []):  # type: ignore[union-attr]
            key = normalize(str(name))
            if key in planned_keys:
                problems.append(f"{prefix} ❌ 已实现却写在「计划」里: {name} ← {anchor}")
            elif key not in supported_keys and key not in exempt_keys:
                problems.append(f"{prefix} ❌ 路线图两段都没提: {name} ← {anchor}")

    # 文档 → 代码：这两段里的每个名字都要能落回代码
    for name in sorted(supported):
        if normalize(name) not in reverse:
            problems.append(f"{prefix} ❌ 路线图说「已支持」，代码里找不到实现: {name}")
    for name in sorted(planned):
        if normalize(name) in reverse:
            problems.append(f"{prefix} ❌ 已实现却写在「计划」里: {name} ← {reverse[normalize(name)]}")
    return problems


def sanity_problems(area: dict, lang: str, code_items: Set[str],
                    supported: Set[str], planned: Set[str]) -> List[str]:
    """反空转断言：同一手法必须能检出**已知存在**的项。"""
    prefix = f"[{area['title']}/{LANG_LABEL[lang]}] 反空转"
    problems: List[str] = []
    if len(code_items) < area["min_code_items"]:
        problems.append(f"{prefix}: 代码侧只抽出 {len(code_items)} 项"
                        f"（预期 ≥ {area['min_code_items']}）⇒ 解析规则可能已失效")
    for probe in sorted(area["known_code_items"]):
        if probe not in code_items:
            problems.append(f"{prefix}: 代码侧没抽到已知存在的项 {probe}")
    if not supported:
        problems.append(f"{prefix}: 「已支持/已实现」段没抽到任何名字 ⇒ 段标题或解析规则已变")
    if area["candidates"][lang] not in supported:
        problems.append(f"{prefix}: 文档侧没抽到已知存在的名字 {area['candidates'][lang]}")
    if not planned:
        problems.append(f"{prefix}: 「计划支持/计划实现」段没抽到任何名字 ⇒ 段标题或解析规则已变")
    return problems


def collect_code_items(root: Path) -> Dict[str, Set[str]]:
    """三类代码项（与语言无关）。"""
    registry = root / ADAPTER_REGISTRY
    adapters = extract_adapter_keys(registry.read_text(encoding="utf-8", errors="ignore")) \
        if registry.exists() else set()
    return {
        "adapters": adapters,
        "loadbalancer": extract_impl_classes(root / LOADBALANCER_DIR, "LoadBalancer"),
        "ratelimiter": extract_impl_classes(root / RATELIMIT_DIR, "RateLimiter"),
    }


def check_anchor_sources(root: Path) -> List[str]:
    """横切锚点必须指向真实存在的符号（否则锚点就是绕过门闩的后门）。"""
    problems: List[str] = []
    for anchor, spec in EXTRA_ANCHORS.items():
        source = root / str(spec["source"])
        if not source.exists():
            problems.append(f"[横切锚点] {anchor}: 源文件不存在 {spec['source']}")
        elif str(spec["symbol"]) not in source.read_text(encoding="utf-8", errors="ignore"):
            problems.append(f"[横切锚点] {anchor}: {spec['source']} 里找不到符号 {spec['symbol']}")
    return problems


def run(root: Path) -> int:
    code_items = collect_code_items(root)
    for key, items in code_items.items():
        print(f"🔍 代码侧 {key}: {len(items)} 项 —— {', '.join(sorted(items))}")

    problems = check_anchor_sources(root)
    for area in AREAS:
        items = code_items[area["key"]]
        for lang, doc_rel in ROADMAP_DOCS.items():
            doc_path = root / doc_rel
            if not doc_path.exists():
                print(f"❌ 找不到路线图: {doc_rel}")
                problems.append(f"[{area['title']}/{LANG_LABEL[lang]}] 找不到路线图 {doc_rel}")
                continue
            supported, planned = extract_roadmap_sections(
                doc_path.read_text(encoding="utf-8", errors="ignore"), area, lang)
            print(f"\n📄 {doc_rel} · {area['title']}（{area['labels_note']}）")
            print(f"   已支持/已实现 {len(supported)} 项：{', '.join(sorted(supported)) or '（空）'}")
            print(f"   计划支持/计划实现 {len(planned)} 项：{', '.join(sorted(planned)) or '（空）'}")
            area_problems = sanity_problems(area, lang, items, supported, planned)
            area_problems += reconcile_area(area, items, supported, planned, lang)
            if area_problems:
                for line in area_problems:
                    print(f"   {line}")
                problems += area_problems
            else:
                print("   ✅ 与代码双向一致")

    print()
    print("ℹ️  限定词（如「依赖 Redis」）写在括号里，比较时被截掉 ⇒ 不入判据，由映射表注释说明。")
    print(f"ℹ️  显式豁免表 EXEMPTED_LABELS: {len(EXEMPTED_LABELS)} 项"
          f"{'（当前为空：所有代码项在路线图里都有对应行）' if not EXEMPTED_LABELS else ''}")
    if problems:
        print(f"\n❌ 能力声明与代码共 {len(problems)} 处不一致"
              f"（先改代码，再同步路线图；确属有意不提的登记进 EXEMPTED_LABELS）")
        return 1
    print("\n✅ 路线图能力清单与代码三类能力双向一致")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="路线图能力清单与代码对账")
    parser.add_argument("--project-root", default=".", help="项目根目录 (默认: .)")
    parser.add_argument("--fail-on-error", action="store_true",
                        help="发现任何一侧不一致时退出码为 1")
    args = parser.parse_args()
    code = run(Path(args.project_root).resolve())
    if code and not args.fail_on_error:
        print("（未加 --fail-on-error ⇒ 退出码仍为 0）")
        return 0
    return code


def _entrypoint() -> int:
    try:
        return main()
    except Exception as exc:  # noqa: BLE001 - 脚本自身异常必须非 0 且打印清楚
        print(f"❌ 检查器自身异常: {type(exc).__name__}: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(_entrypoint())
