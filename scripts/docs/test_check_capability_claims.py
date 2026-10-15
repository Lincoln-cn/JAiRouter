#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-capability-claims.py 的离线自测（issue #425）

覆盖三类对账的判据要害：
  ① **幽灵能力名 ⇒ 判红**：路线图「已支持」里有名字、代码里找不到实现；
  ② **非规划段落出现的能力词 ⇒ 不得判红**：散文 / 引用块 / 变更日志表格里的同一个词
     不算能力声明（否则「Random」「Gemini」这类常见词会到处误报）；
  ③ **已实现却写在计划里 ⇒ 判红**（#425 正文抓到的那类漂移；A/B 反证用的就是这条）；
  ④ **反空转**：代码侧/文档侧的解析手法必须能检出**已知存在**的项
     （`gemini` / `RoundRobinLoadBalancer` / `TokenBucketRateLimiter` / `Ollama` / `Random`）；
  ⑤ 实现类抽取要排除**接口与抽象基类**，并校验横切锚点指向的符号真实存在；
  ⑥ 限定词不入判据：`Latency-Aware（延迟感知，…）` 只按主名 `Latency-Aware` 比。

用法: python scripts/docs/test_check_capability_claims.py
"""

import contextlib
import importlib.util
import io
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parent / "check-capability-claims.py"
REPO_ROOT = Path(__file__).resolve().parents[2]

ROADMAP_SAMPLE = """
# 发展路线图

## 功能特性规划

### 适配器扩展

#### 已支持 ✅
| 适配器 | 类型 | 状态 |
|--------|------|------|
| GPUStack | Chat/Embedding | ✅ |
| Ollama | Chat/Embedding | ✅ |

#### 计划支持 📋
- 📋 Cohere API（版本待定）

> **审计更正**：`Adaptive Thing` 早已实现，见某处。

## 更新日志

| 版本 | 说明 |
|------|------|
| v1.0 | 新增 Random Decoration 支持 |
"""


def load_module():
    spec = importlib.util.spec_from_file_location("check_capability_claims", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    module = load_module()
    failures = []

    def check(name, condition, detail=""):
        print(("✅ " if condition else "❌ ") + name + ("" if condition else f"  → {detail}"))
        if not condition:
            failures.append(name)

    adapters_area = next(a for a in module.AREAS if a["key"] == "adapters")
    lb_area = next(a for a in module.AREAS if a["key"] == "loadbalancer")

    supported, planned = module.extract_roadmap_sections(ROADMAP_SAMPLE, adapters_area, "zh")
    check("区段抽取：只取「已支持/计划支持」小标题下的表格行与列表项",
          supported == {"GPUStack", "Ollama"} and planned == {"Cohere API"},
          f"supported={sorted(supported)} planned={sorted(planned)}")
    check("② 引用块里出现的能力词不算声明（Adaptive Thing）",
          "Adaptive Thing" not in (supported | planned), str(sorted(supported | planned)))
    check("② 非规划段落（变更日志表格）里出现的能力词不算声明（Random Decoration）",
          "Random Decoration" not in (supported | planned), str(sorted(supported | planned)))
    lb_supported, lb_planned = module.extract_roadmap_sections(ROADMAP_SAMPLE, lb_area, "zh")
    check("② 与本次无关的能力区段不会被别处的词污染",
          lb_supported == set() and lb_planned == set(),
          f"{sorted(lb_supported)} / {sorted(lb_planned)}")

    code_items = {"gpustack", "ollama"}
    clean = module.reconcile_area(adapters_area, code_items, supported, planned, "zh")
    check("映射表与路线图一致时无判红（证明判据不是恒真）", clean == [], str(clean))

    # ① 幽灵能力名 ⇒ 红
    ghost = module.reconcile_area(adapters_area, code_items,
                                  supported | {"Ghost Capability"}, planned, "zh")
    check("① 路线图「已支持」里的幽灵能力名 ⇒ 判红",
          any("代码里找不到实现: Ghost Capability" in p for p in ghost), str(ghost))

    # ③ 已实现却写在计划里 ⇒ 红（Gemini 从已支持移到计划支持，就是 A/B 反证那一手）
    drifted = module.reconcile_area(
        adapters_area, code_items | {"gemini"},
        supported - {"Ollama"}, planned | {"Ollama"}, "zh")
    check("③ 把已实现的能力挪进「计划支持」⇒ 判红（两侧方向都要报）",
          any("已实现却写在「计划」里: Ollama" in p for p in drifted), str(drifted))

    check("限定词不入判据：只按主名比较",
          module.base_label("Latency-Aware（延迟感知，`LatencyAwareLoadBalancer`）") == "Latency-Aware"
          and module.base_label("- ✅ API-key-level rate limiting (per-key `x`; requires Redis)") 
          == "API-key-level rate limiting",
          module.base_label("- ✅ API-key-level rate limiting (per-key `x`; requires Redis)"))

    # ④ 反空转：真实仓库的代码侧
    real_items = module.collect_code_items(REPO_ROOT)
    check("④ 反空转：适配器注册键能检出已知存在的 gemini（8 项）",
          "gemini" in real_items["adapters"] and len(real_items["adapters"]) >= 8,
          str(sorted(real_items["adapters"])))
    check("④ 反空转：负载均衡实现类能检出 RoundRobinLoadBalancer（7 项）",
          "RoundRobinLoadBalancer" in real_items["loadbalancer"]
          and len(real_items["loadbalancer"]) >= 7,
          str(sorted(real_items["loadbalancer"])))
    check("④ 反空转：限流实现类能检出 TokenBucketRateLimiter（5 项）",
          "TokenBucketRateLimiter" in real_items["ratelimiter"]
          and len(real_items["ratelimiter"]) >= 5,
          str(sorted(real_items["ratelimiter"])))
    check("④ 反空转断言本身有检测力：代码侧为空时必须报问题",
          module.sanity_problems(adapters_area, "zh", set(), supported, planned) != [],
          "空集合竟然通过")

    # ⑤ 实现类抽取：接口与抽象基类不算实现
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        (base / "FooLoadBalancer.java").write_text(
            "package demo;\npublic interface FooLoadBalancer { }\n", encoding="utf-8")
        (base / "AbstractBarLoadBalancer.java").write_text(
            "package demo;\npublic abstract class AbstractBarLoadBalancer { }\n", encoding="utf-8")
        (base / "RealBazLoadBalancer.java").write_text(
            "package demo;\npublic class RealBazLoadBalancer implements FooLoadBalancer { }\n",
            encoding="utf-8")
        extracted = module.extract_impl_classes(base, "LoadBalancer")
        check("⑤ 实现类抽取排除接口与抽象基类",
              extracted == {"RealBazLoadBalancer"}, str(sorted(extracted)))

    check("⑤ 横切锚点指向的符号真实存在（否则锚点会变成绕过门闩的后门）",
          module.check_anchor_sources(REPO_ROOT) == [],
          str(module.check_anchor_sources(REPO_ROOT)))

    # ⑥ 真实文档 + 真实代码必须全绿（本门闩的日常形态）
    with contextlib.redirect_stdout(io.StringIO()) as quiet:
        problems = module.run(REPO_ROOT)
    check("⑥ 真实仓库跑一遍：路线图能力清单与代码双向一致", problems == 0,
          f"退出码 {problems}；输出片段: {quiet.getvalue().strip().splitlines()[-1:]}")

    print()
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        return 1
    print("✅ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
