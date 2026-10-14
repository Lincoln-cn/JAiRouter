#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-api-endpoints.py 的离线自测（issue #250 批次 8）

覆盖对账判据的要害（都是本轮真实踩过或差点踩到的）：
  ① 类级前缀 + 方法级映射的**重建**；② 注解**括号可省**（`@GetMapping` 单独一行）；
  ③ 类级前缀**缺前导斜杠**（`@RequestMapping("api/x")`）要归一；
  ④ 路径变量归一（`{id}` / `$ID` / `:id` → `{}`）；
  ⑤ 文档写**具体取值**而代码是路径变量 ⇒ 算命中；
  ⑥ 文档写**前缀**而代码是子路径 ⇒ 算命中；
  ⑦ 「已移除」类历史说明**不参与**对账；
  ⑧ 反向对照：真·不存在的路径**必须被揪出来**（证明断言不空转）。

用法: python scripts/docs/test_check_api_endpoints.py
"""

import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parent / "check-api-endpoints.py"

CONTROLLER = """
package demo;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    @GetMapping
    public Mono<?> list() { return Mono.empty(); }

    @GetMapping("/{id}")
    public Mono<?> get() { return Mono.empty(); }

    @PostMapping(value = "/items")
    public Mono<?> create() { return Mono.empty(); }
}
"""

CONTROLLER_NO_SLASH = """
@RestController
@RequestMapping("api/noslash")
public class NoSlashController {
    @GetMapping("/{keyId}")
    public Mono<?> get() { return Mono.empty(); }
}
"""

DOC = """
#### `GET /api/demo`
#### `GET /api/demo/{id}`
#### `POST /api/demo/items`
#### `GET /api/demo/chat`

通配写法 `/api/demo/**` 与 `$ID` 风格 `/api/demo/$ID`。

> `/api/legacy-configs` controller was removed in **v3.0.2**.
"""


def load_module():
    spec = importlib.util.spec_from_file_location("check_api_endpoints", SCRIPT)
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

    code_paths = module.extract_controller_paths(CONTROLLER)
    check("① 类级前缀 + 方法级映射重建（含无参 @GetMapping 落成类路径）",
          code_paths == {"/api/demo", "/api/demo/{}", "/api/demo/items"}, str(sorted(code_paths)))

    no_slash = module.extract_controller_paths(CONTROLLER_NO_SLASH)
    check("③ 类级前缀缺前导斜杠 ⇒ 归一为 / 开头",
          no_slash == {"/api/noslash/{}"}, str(sorted(no_slash)))

    doc_paths = module.extract_doc_paths(DOC)
    check("④ 路径变量风格归一（$ID → {}）", "/api/demo/{}" in doc_paths, str(sorted(doc_paths)))
    check("⑦ 通配写法不参与对账", not any("*" in p for p in doc_paths), str(sorted(doc_paths)))
    check("⑦ 「已移除」历史说明不参与对账（/api/legacy-configs 不得入列）",
          "/api/legacy-configs" not in doc_paths, str(sorted(doc_paths)))

    missing, _ = module.reconcile(doc_paths, code_paths)
    check("⑤ 文档具体取值（/api/demo/chat）对上代码路径变量 ⇒ 算命中",
          "/api/demo/chat" not in missing, str(sorted(missing)))
    check("⑥ 文档前缀（/api/demo）对上代码子路径 ⇒ 算命中",
          "/api/demo" not in missing, str(sorted(missing)))
    check("⑧ 反向对照：真·不存在的路径必须被揪出来",
          module.reconcile({"/api/definitely-not-there"}, code_paths)[0] == {"/api/definitely-not-there"},
          "reconcile 未报出假路径 ⇒ 断言空转")

    print()
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        return 1
    print("✅ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
