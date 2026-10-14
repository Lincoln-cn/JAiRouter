#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-links.py 的离线自测（issue #358）

覆盖「5xx/连接失败先重试、重试后按已登记抖动源决定判红还是警告」这条口径，
**不依赖真实外网**：用本地 stub 服务器扮演目标站点，逐条断言客户端可见结论。

用法: python scripts/docs/test_check_links.py
"""

import importlib.util
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

# 必须在导入被测脚本之前设置，避免留下 scripts/docs/__pycache__
sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parent / "check-links.py"


def load_module():
    spec = importlib.util.spec_from_file_location("check_links", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class _StubHandler(BaseHTTPRequestHandler):
    """三种形态（用路径选择，无需额外状态）：

    * ``/ok``        —— 200；
    * ``/flaky``     —— 前两次 503，第三次 200；
    * ``/always503`` —— 恒 503；
    * ``/notfound``  —— 404。
    """

    counts = {}

    def do_GET(self):  # noqa: N802 (stdlib 约定)
        path = self.path.split("?")[0]
        type(self).counts[path] = type(self).counts.get(path, 0) + 1
        hits = type(self).counts[path]

        if path.startswith("/ok"):
            self.send_response(200)
        elif path.startswith("/flaky"):
            self.send_response(200 if hits >= 3 else 503)
        elif path.startswith("/always503"):
            self.send_response(503)
        elif path.startswith("/notfound"):
            self.send_response(404)
        else:
            self.send_response(500)
        self.end_headers()
        self.wfile.write(b"stub")

    def log_message(self, *args):  # 静音
        pass


def main():
    module = load_module()
    # 只验证逻辑，不验证等待时长（真实默认是 1s / 3s）
    module.RETRY_BACKOFF_SECONDS = (0.05, 0.05)

    server = HTTPServer(("127.0.0.1", 0), _StubHandler)
    base = f"http://127.0.0.1:{server.server_address[1]}"
    threading.Thread(target=server.serve_forever, daemon=True).start()

    def new_checker():
        checker = module.LinkChecker(retries=2)
        # 把「已登记抖动源」指到 stub 的 /tolerated 前缀（真实值见脚本内的常量）
        checker.tolerated_flaky_prefixes = (base + "/tolerated",)
        return checker

    tests = []

    def case(name, func):
        tests.append((name, func))

    def t_ok():
        ok, msg, transient = new_checker().check_external_link(base + "/ok")
        assert ok and not transient, f"200 应判为有效: {ok} {msg} {transient}"

    def t_flaky_passes_after_retry():
        ok, msg, transient = new_checker().check_external_link(base + "/flaky")
        assert ok and not transient, f"503,503,200 应在重试后判为有效: {ok} {msg} {transient}"
        assert "已重试 2 次" in msg, f"必须如实标注重试次数: {msg}"

    def t_tolerated_5xx_is_warning():
        _StubHandler.counts.clear()
        ok, msg, transient = new_checker().check_external_link(base + "/tolerated/always503")
        assert (not ok) and transient, f"已登记抖动源的恒 503 应降级为警告: {ok} {msg} {transient}"
        assert _StubHandler.counts.get("/tolerated/always503") == 3, \
            f"应重试到 1+2 次请求: {_StubHandler.counts}"

    def t_untolerated_5xx_still_fails():
        ok, msg, transient = new_checker().check_external_link(base + "/always503")
        assert (not ok) and (not transient), f"未登记主机的恒 503 仍须判红: {ok} {msg} {transient}"
        assert "TOLERATED_FLAKY_URL_PREFIXES" in msg, f"失败信息应给出登记指引: {msg}"

    def t_404_stays_invalid_without_retry():
        _StubHandler.counts.clear()
        ok, msg, transient = new_checker().check_external_link(base + "/notfound")
        assert (not ok) and (not transient), f"404 应判红且不降级: {ok} {msg} {transient}"
        assert _StubHandler.counts.get("/notfound") == 1, \
            f"404 不应重试（门闩的牙齿）: {_StubHandler.counts}"

    case("200 判为有效", t_ok)
    case("503,503,200 重试后判为有效并标注重试次数", t_flaky_passes_after_retry)
    case("已登记抖动源的恒 503 ⇒ 警告（并确实重试了 2 次）", t_tolerated_5xx_is_warning)
    case("未登记主机的恒 503 ⇒ 仍然判红并给出登记指引", t_untolerated_5xx_still_fails)
    case("404 判红、不重试、不降级", t_404_stays_invalid_without_retry)

    failures = []
    try:
        for name, func in tests:
            try:
                func()
                print(f"✅ {name}")
            except AssertionError as exc:
                failures.append((name, str(exc)))
                print(f"❌ {name}: {exc}")
    finally:
        server.shutdown()

    if failures:
        print(f"\n❌ {len(failures)}/{len(tests)} 个测试失败")
        return 1
    print(f"\n✅ {len(tests)}/{len(tests)} 个测试通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
