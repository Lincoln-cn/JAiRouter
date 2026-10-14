#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-config-imports.py 的离线自测（issue #250 批次 11）

覆盖要害（都是本会话真实踩过或差点踩到的）：
  ① 导入清单解析；② **框线树**解析（裸文件名 + 父目录名 ⇒ 完整路径，批次 7 补的就是这种形态）；
  ③ `/app/config/...` 这类**外部挂载路径不得算作** `config/...` 的收录证据；
  ④ 双向对账（缺 / 多都要报）；⑤ 反向对照：真实文件未被收录时必须报出来。

用法: python scripts/docs/test_check_config_imports.py
"""

import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parent / "check-config-imports.py"

APP_YML = """
spring:
  config:
    import:
      - optional:file:/app/config/application.yml
      - classpath:config/common/server.yml
      - classpath:config/router/pools.yml
"""

DOC_IMPORTS = """
      # common 模块
      - classpath:config/common/server.yml

      # router 模块
      - classpath:config/router/pools.yml
"""

DOC_TREE = """
```
config/
├── common/              # 公共基础设施
│   ├── server.yml       # 服务器配置
│   └── cluster.yml      # 多副本（集群）配置
└── router/              # 路由服务
    └── pools.yml        # 资源池配置
```

外部挂载（Docker）结构：

```
/app/config/
├── application.yml
└── auth/
    └── jwt.yml
```
"""


def load_module():
    spec = importlib.util.spec_from_file_location("check_config_imports", SCRIPT)
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

    imports = module.parse_imports(APP_YML)
    check("① 导入清单解析（含 external/classpath 两类）",
          imports == {"optional:file:/app/config/application.yml",
                      "classpath:config/common/server.yml",
                      "classpath:config/router/pools.yml"},
          str(sorted(imports)))

    doc_imports = module.parse_doc_imports(DOC_IMPORTS)
    check("① 文档导入清单解析", doc_imports == {"classpath:config/common/server.yml",
                                                   "classpath:config/router/pools.yml"},
          str(sorted(doc_imports)))

    mentions = module.parse_doc_config_mentions(DOC_TREE)
    check("② 框线树解析出完整路径（裸文件名 + 父目录名）",
          {"config/common/server.yml", "config/common/cluster.yml", "config/router/pools.yml"} <= mentions,
          str(sorted(mentions)))
    check("③ 外部挂载路径 /app/config/... 不得算作 config/ 收录证据",
          not any(m in {"config/application.yml", "config/auth/jwt.yml"} for m in mentions),
          str(sorted(mentions)))

    missing, extra = module.reconcile(doc_imports, imports)
    check("④ 双向对账：缺项被报出（router/pools 在文档里、common/server 在文档里 ⇒ 缺 optional:file 那条）",
          missing == {"optional:file:/app/config/application.yml"} and extra == set(),
          f"missing={sorted(missing)} extra={sorted(extra)}")

    missing2, extra2 = module.reconcile({"config/common/server.yml"}, {"config/common/server.yml",
                                                                      "config/fake/ghost.yml"})
    check("⑤ 反向对照：真实文件未被收录必须报出来，幽灵文件也必须报出来",
          missing2 == {"config/fake/ghost.yml"} and extra2 == set(),
          f"missing={sorted(missing2)} extra={sorted(extra2)}")

    print()
    if failures:
        print(f"❌ {len(failures)} 项失败: {failures}")
        return 1
    print("✅ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
