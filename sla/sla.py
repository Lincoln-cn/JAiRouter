#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""SLA 工具链的纯 Python 实现（标准库，无第三方依赖）。

为什么有这个文件：客户环境里经常既没有 Node、也不允许装 Docker，
甚至只有发行版自带的 Python。`preflight.mjs` / `doctor.mjs` / `loadtest.mjs`
是权威实现（与仓库侧 SlaSurfaceContractTest 共用一份 contract.json），
本文件在「只有 Python」时提供同一套结论。

兼容 Python 2.7 与 Python 3.8+（两者都要能跑：老发行版自带 2.7，新发行版只有 3）。
因此这里刻意不用 f-string、不用类型注解、不用 statistics 模块，
所有 HTTP 调用经一层薄封装（urllib2 / urllib.request 差异只出现在文件顶部）。

设计约束（与 Node 版一致）：
  * 只读：不发任何写请求。
  * 失败要说清是哪一层，不能笼统报「服务异常」。
  * 宁可说「测不到」，不猜 —— 测不到的东西记为 WARN 并说明原因。
  * 版本未知时**不能静默通过**：契约自检必须能证明「工具与网关版本兼容」，
    证明不了就是未验证（SLA-TOOL-2）。
"""

from __future__ import division
from __future__ import print_function

import argparse
import json
import os
import re
import socket
import sys
import threading
import time

PY3 = sys.version_info[0] >= 3
if PY3:
    from urllib.request import Request, urlopen
    from urllib.error import HTTPError, URLError
    from urllib.parse import urlsplit
    import http.client as httpclient

    text_type = str
else:  # Python 2.7
    from urllib2 import Request, urlopen, HTTPError, URLError
    from urlparse import urlsplit
    import httplib as httpclient

    text_type = unicode  # noqa: F821 - only defined on py2


# Python 2.7 与 Python 3.8 都接受；更老的一律不认（用了 dict 推导与 with 语法）
MIN_PYTHON_2 = (2, 7)
MIN_PYTHON_3 = (3, 8)

TOKEN_HEADER = "X-Jairouter-Token"


def python_supported():
    major, minor = sys.version_info[0], sys.version_info[1]
    if major == 2:
        return (major, minor) >= MIN_PYTHON_2
    if major == 3:
        return (major, minor) >= MIN_PYTHON_3
    return False


def python_requirement_text():
    return u"Python 2.7+ 或 Python 3.8+（当前 %d.%d.%d）" % tuple(sys.version_info[:3])


def emit(text=u""):
    """跨 2/3 的 stdout 输出（py2 需要显式 UTF-8 编码，否则中文会抛 UnicodeEncodeError）。"""
    if not isinstance(text, text_type):
        try:
            text = text_type(text)
        except UnicodeDecodeError:
            text = text_type(str(text), "utf-8", "replace")
    line = text + u"\n"
    if PY3:
        sys.stdout.write(line)
    else:
        sys.stdout.write(line.encode("utf-8"))
    sys.stdout.flush()


def emit_err(text):
    if not isinstance(text, text_type):
        text = text_type(text)
    line = text + u"\n"
    if PY3:
        sys.stderr.write(line)
    else:
        sys.stderr.write(line.encode("utf-8"))
    sys.stderr.flush()


class GatewayError(Exception):
    """网关返回了错误，或连接不上。status 为 None 表示网络层失败。"""

    def __init__(self, message, status=None, body=None):
        Exception.__init__(self, message)
        self.status = status
        self.body = body


# ------------------------------------------------------------------ HTTP 基础

def normalize_bases(value):
    """把 --base 的取值归一成「去重保序」的列表。

    argparse 用 action="append" 时拿到的是 list；这里同时兼容单个字符串与逗号分隔，
    这样 `--base http://a,http://b` 与 `--base http://a --base http://b` 等价。
    """
    if value is None:
        return []
    raw = value if isinstance(value, list) else [value]
    bases = []
    for item in raw:
        for part in text_type(item).split(","):
            base = part.strip().rstrip("/")
            if base and base not in bases:
                bases.append(base)
    return bases


def replica_header(index, total, base):
    """多副本时给每个副本的输出加分节标题；单副本保持原来的纯文本输出。"""
    if total > 1:
        emit(u"")
        emit(u"=== 副本 %d/%d：%s ===" % (index, total, base))


def join_url(base, path):
    return base.rstrip("/") + (path if path.startswith("/") else "/" + path)


def _raw_request(method, url, body=None, headers=None, timeout=15):
    """返回 (status, raw_text)。网络层失败抛 GatewayError。"""
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body)
        if PY3 and not isinstance(data, bytes):
            data = data.encode("utf-8")
        if not PY3 and isinstance(data, text_type):
            data = data.encode("utf-8")
    req = Request(url, data=data, headers=headers or {})
    # urllib2 没有 method 参数，靠显式设置（GET 无 body 时不需要）
    if method and method.upper() != "GET":
        req.get_method = lambda: method.upper()
    try:
        resp = urlopen(req, timeout=timeout)
        try:
            status = getattr(resp, "status", None) or resp.getcode()
            raw = resp.read()
        finally:
            try:
                resp.close()
            except Exception:
                pass
        return status, _decode(raw)
    except HTTPError as exc:  # 4xx/5xx 也是「有响应」，要保留 body
        try:
            raw = exc.read()
        except Exception:
            raw = b""
        return exc.code, _decode(raw)
    except URLError as exc:
        reason = getattr(exc, "reason", exc)
        if isinstance(reason, socket.timeout):
            raise GatewayError(u"超时 %ss" % timeout)
        raise GatewayError(u"连接失败: %s" % (reason,))
    except socket.timeout:
        raise GatewayError(u"超时 %ss" % timeout)


def _decode(raw):
    if raw is None:
        return u""
    if isinstance(raw, text_type):
        return raw
    try:
        return raw.decode("utf-8", "replace")
    except Exception:
        return text_type(raw)


def http_json(base, path, method="GET", body=None, token=None, timeout=15):
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers[TOKEN_HEADER] = token
    status, text = _raw_request(method, join_url(base, path), body, headers, timeout)
    parsed = None
    if text:
        try:
            parsed = json.loads(text)
        except ValueError:
            parsed = text
    if status >= 400:
        message = u""
        if isinstance(parsed, dict):
            message = parsed.get("message") or parsed.get("error") or u""
        detail = u"HTTP %s %s %s" % (status, method, path)
        if message:
            detail += u" - " + text_type(message)
        raise GatewayError(detail, status=status, body=parsed)
    return parsed


def login(base, username, password, timeout=10):
    body = {"username": username, "password": password}
    try:
        parsed = http_json(base, "/api/auth/jwt/login", "POST", body, timeout=timeout)
    except GatewayError as exc:
        raise GatewayError(u"登录失败: %s" % text_type(exc), status=exc.status, body=exc.body)
    token = None
    if isinstance(parsed, dict):
        data = parsed.get("data") or {}
        if isinstance(data, dict):
            token = data.get("token")
    if not token:
        raise GatewayError(u"登录响应里没有 data.token（契约已变？）")
    return token


def scrape_metrics(base, timeout=10):
    """把 /actuator/prometheus 解析成 {样本名(含 label): 值}。"""
    headers = {"Accept": "text/plain"}
    status, text = _raw_request("GET", join_url(base, "/actuator/prometheus"), None, headers, timeout)
    if status >= 400:
        raise GatewayError(u"指标抓取失败: HTTP %s" % status, status=status)
    out = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        idx = line.rfind(" ")
        if idx <= 0:
            continue
        name = line[:idx].strip()
        try:
            value = float(line[idx + 1:])
        except ValueError:
            continue
        out[name] = value
    return out


# ------------------------------------------------------------------ 版本

def parse_version(text):
    m = re.search(r"(\d+)\.(\d+)\.(\d+)", text_type(text or u""))
    if not m:
        return None
    return tuple(int(x) for x in m.groups())


def in_range(version, rng):
    """Evaluate ">=3.7.0 <4.0.0" against a concrete version. 版本解析失败返回 None。"""
    v = parse_version(version)
    if not v:
        return None
    for clause in re.findall(r"[<>]=?\s*\d+\.\d+\.\d+", text_type(rng or u"")):
        m = re.match(r"^(>=|<=|>|<)\s*(\d+\.\d+\.\d+)", clause)
        if not m:
            continue
        op, target_text = m.group(1), m.group(2)
        target = parse_version(target_text)
        if op == ">=" and v < target:
            return False
        if op == "<=" and v > target:
            return False
        if op == ">" and v <= target:
            return False
        if op == "<" and v >= target:
            return False
    return True


def detect_version(base, token=None):
    """按可靠性顺序探测网关版本；返回 (version_or_None, 来源说明)。

    网关默认不导出 build 信息（/actuator/info 只有 tracing/security/...），
    所以这里必须多源尝试。探测不到就是「未验证」，不能当通过（SLA-TOOL-2）。
    """
    candidates = []

    try:
        info = http_json(base, "/actuator/info", timeout=8)
        if isinstance(info, dict):
            for key in ("build", "app", "git"):
                section = info.get(key)
                if isinstance(section, dict) and section.get("version"):
                    candidates.append((section["version"], u"/actuator/info %s.version" % key))
            if info.get("version"):
                candidates.append((info["version"], u"/actuator/info version"))
    except GatewayError:
        pass

    # 不从 prometheus 找版本：Spring Boot 默认不导出 application_info / build_info，
    # 实测 /actuator/prometheus 里唯一带 version 的样本是 jvm_info（那是 JVM 版本，
    # 不是产品版本）。与其靠猜的来源，不如让网关暴露 build.version
    # （pom.xml 的 spring-boot-maven-plugin build-info goal 生成 META-INF/build-info.properties）。
    # 另注：/actuator/info 的 config.serviceVersion 是 tracing 组件的配置值（默认 1.0.0），
    # 绝不能当产品版本用。

    for value, source in candidates:
        if parse_version(value):
            return value, source
    return None, u"未探测到（/actuator/info 没有 build.version；网关需开启 build-info）"


def load_contract(path):
    fh = open(path, "r")
    try:
        return json.load(fh)
    finally:
        fh.close()


# ------------------------------------------------------------------ preflight

def _get_path(obj, dotted):
    cur = obj
    for key in text_type(dotted).split("."):
        if cur is None:
            return None
        if isinstance(cur, dict):
            cur = cur.get(key)
        else:
            return None
    return cur


def _default_contract_path():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(here, "contract.json")


def _preflight_one(args, base, contract):
    """对一个副本跑一遍契约自检；打印交给调用方（多副本时要逐副本分节输出）。"""
    checks = []

    def push(check_id, ok, detail):
        checks.append({"id": check_id, "ok": ok, "detail": detail})

    token = None
    try:
        token = login(base, args.user, args.password)
        push("auth.login", True, u"data.token")
    except GatewayError as exc:
        push("auth.login", False, text_type(exc))

    service_type = args.service_type or "chat"

    for endpoint in contract.get("endpoints", []):
        kind = endpoint.get("kind")
        eid = endpoint.get("id")
        path = endpoint.get("path", "")
        try:
            if path == "/actuator/prometheus" or kind == "metric":
                metrics = scrape_metrics(base)
                if kind == "metric":
                    name = endpoint.get("name", "jairouter_")
                    hit = any(k.split("{", 1)[0].startswith(name) for k in metrics)
                    push(eid, hit, u"%s（共 %d 个样本）" % (name, len(metrics)))
                else:
                    push(eid, len(metrics) > 0, u"HTTP GET /actuator/prometheus（%d 个样本）" % len(metrics))
                continue
            real_path = path.replace("{type}", service_type)
            method = endpoint.get("method", "GET")
            if kind == "status":
                headers = {"Accept": "application/json"}
                if token:
                    headers[TOKEN_HEADER] = token
                _raw_request(method, join_url(base, real_path), None, headers, 15)
                push(eid, True, u"HTTP %s %s" % (method, real_path))
                continue
            body = http_json(base, real_path, method, None, token)
            value = _get_path(body, endpoint.get("expect", "data"))
            push(eid, value is not None, u"%s → %s" % (real_path, endpoint.get("expect")))
        except GatewayError as exc:
            push(eid, False, text_type(exc))
        except Exception as exc:  # noqa: BLE001 - 自检工具不能因单个检查崩掉
            push(eid, False, u"检查异常: %s" % text_type(exc))

    version = args.version
    version_source = u"--version"
    if not version:
        detected, source = detect_version(base, token)
        # 探测失败时也要把「未探测到」的说明带出来，否则报错文案会误写成「--version」。
        version, version_source = detected, source

    target_range = contract.get("targetRange")
    version_ok = in_range(version, target_range) if (version and target_range) else None

    if version_ok is None:
        # 证明不了兼容性 ⇒ 未验证。除非显式放宽，否则判失败（SLA-TOOL-2）。
        if args.allow_unknown_version:
            checks.append({"id": "version.range", "ok": True, "level": "WARN",
                           "detail": u"无法确认网关版本（%s），已按 --allow-unknown-version 放宽；适用范围 %s"
                                     % (version_source, target_range)})
            version_ok = None
        else:
            checks.append({"id": "version.range", "ok": False,
                           "detail": u"无法确认网关版本（%s）；适用范围 %s" % (version_source, target_range)})
            version_ok = False
    elif version_ok is False:
        checks.append({"id": "version.range", "ok": False,
                       "detail": u"%s ∉ %s（工具与网关版本不兼容，请升级工具链）" % (version, target_range)})
    else:
        checks.append({"id": "version.range", "ok": True,
                       "detail": u"%s ∈ %s（来源：%s）" % (version, target_range, version_source)})

    ok = all(c["ok"] for c in checks)
    return {
        "base": base,
        "checks": checks,
        "version": version,
        "targetRange": target_range,
        "versionOk": version_ok,
        "ok": ok,
    }


def _print_preflight_result(result):
    """单副本的文本输出；多副本时逐副本调用，格式与单副本完全一致。"""
    for check in result["checks"]:
        level = check.get("level")
        if level == "WARN":
            mark = u"[WARN]"
        else:
            mark = u"[ OK ]" if check["ok"] else u"[FAIL]"
        emit(u"%s %-22s %s" % (mark, check["id"], check.get("detail") or u""))
    passed = sum(1 for c in result["checks"] if c["ok"])
    emit(u"")
    if result["ok"]:
        emit(u"全部通过（%d 项）" % len(result["checks"]))
    else:
        emit(u"失败 %d 项 / 通过 %d 项" % (len(result["checks"]) - passed, passed))
    if result["versionOk"] is False:
        emit(u"      └ 这是工具与网关版本不匹配或无法确认版本，不是服务故障。")


def cmd_preflight(args):
    contract = load_contract(args.contract or _default_contract_path())
    bases = normalize_bases(args.base)
    if not bases:
        emit_err(u"至少需要一个 --base")
        return 2
    results = []
    for index, base in enumerate(bases, 1):
        replica_header(index, len(bases), base)
        result = _preflight_one(args, base, contract)
        results.append(result)
        _print_preflight_result(result)
    ok = all(r["ok"] for r in results)
    if len(bases) > 1:
        emit(u"")
        emit(u"=== 汇总（%d 个副本）===" % len(bases))
        for index, result in enumerate(results, 1):
            passed = sum(1 for c in result["checks"] if c["ok"])
            emit(u"副本 %d %-26s %s（%d/%d 项通过）" % (
                index, result["base"], u"通过" if result["ok"] else u"失败",
                passed, len(result["checks"])))
    if args.json:
        if len(bases) > 1:
            emit(json.dumps({"ok": ok, "replicas": results}, ensure_ascii=False))
        else:
            result = results[0]
            emit(json.dumps({"ok": result["ok"], "checks": result["checks"],
                             "version": result["version"],
                             "targetRange": result["targetRange"],
                             "versionOk": result["versionOk"]}, ensure_ascii=False))
    return 0 if ok else 2


# ------------------------------------------------------------------ doctor

class Reporter(object):
    ICON = {"OK": u" OK ", "WARN": u"WARN", "FAIL": u"FAIL"}

    def __init__(self):
        self.results = []

    def add(self, level, area, message, hint=None):
        self.results.append({"level": level, "area": area, "message": message, "hint": hint})

    def exit_code(self):
        if any(r["level"] == "FAIL" for r in self.results):
            return 2
        if any(r["level"] == "WARN" for r in self.results):
            return 1
        return 0

    def print_text(self):
        for r in self.results:
            emit(u"[%s] %-6s %s" % (self.ICON[r["level"]], r["area"], r["message"]))
            if r["hint"]:
                emit(u"%s└ %s" % (u" " * 15, r["hint"]))
        fail = sum(1 for r in self.results if r["level"] == "FAIL")
        warn = sum(1 for r in self.results if r["level"] == "WARN")
        emit(u"")
        emit(u"合计 %d 项：%d 失败 / %d 警告 / %d 通过"
             % (len(self.results), fail, warn, len(self.results) - fail - warn))


def split_base_url(base_url):
    try:
        parts = urlsplit(text_type(base_url or u""))
        host = parts.hostname
        if not host:
            return None
        port = parts.port or (443 if parts.scheme == "https" else 80)
        return {"host": host, "port": port,
                "scheme": parts.scheme or "http", "path": parts.path or "/"}
    except ValueError:
        return None


def tcp_probe(host, port, timeout=5):
    started = time.time()
    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        try:
            return True, int((time.time() - started) * 1000), None
        finally:
            sock.close()
    except socket.timeout:
        return False, int((time.time() - started) * 1000), u"连接超时 %ss" % timeout
    except OSError as exc:
        return False, int((time.time() - started) * 1000), text_type(getattr(exc, "strerror", None) or exc)
    except Exception as exc:  # noqa: BLE001
        return False, int((time.time() - started) * 1000), text_type(exc)


def _truncate(text, limit):
    text = text_type(text if text is not None else u"")
    return text if len(text) <= limit else text[:limit] + u"…"


def _doctor_one(args, base):
    rep = Reporter()

    # 1. 服务面
    try:
        started = time.time()
        status, text = _raw_request("GET", join_url(base, "/actuator/health"),
                                    None, {"Accept": "application/json"}, 10)
        elapsed = int((time.time() - started) * 1000)
        try:
            health = json.loads(text or u"{}")
        except ValueError:
            health = {}
        if status != 200:
            rep.add("FAIL", "服务面", u"/actuator/health 不可达（HTTP %s）" % status,
                    u"确认端口与 base-url")
            return rep
        state = health.get("status") if isinstance(health, dict) else None
        if state == "UP":
            rep.add("OK", "服务面", u"/actuator/health = UP (%sms)" % elapsed)
        else:
            rep.add("FAIL", "服务面", u"/actuator/health = %s" % text_type(state),
                    u"看 health 的 components 明细")
    except GatewayError as exc:
        rep.add("FAIL", "服务面", u"/actuator/health 不可达（%s）" % text_type(exc),
                u"确认端口与 base-url")
        return rep

    # 2. 版本（拿不到就是 WARN，不能说 OK —— SLA-TOOL-3）
    version, source = detect_version(base)
    if version:
        rep.add("OK", "服务面", u"版本 %s（来源：%s）" % (version, source))
    else:
        rep.add("WARN", "服务面", u"版本未知：%s" % source,
                u"契约自检会因「无法确认版本」判失败；可在网关开启 management.info.build.enabled")

    # 3. 认证
    token = args.token or None
    if not token and args.user and args.password:
        try:
            token = login(base, args.user, args.password)
        except GatewayError as exc:
            rep.add("WARN", "认证", u"用 --user/--pass 登录失败: %s" % text_type(exc),
                    u"配置类检查会跳过")
    if token:
        rep.add("OK", "认证", u"已获得读写配置的 token")
    else:
        rep.add("WARN", "认证", u"未提供 token，配置类检查会跳过", u"--token <JWT> 或 --user/--pass")

    # 4. 实例配置（默认覆盖全部服务类型，而不是只看 chat）
    if args.services:
        types = [t.strip() for t in text_type(args.services).split(",") if t.strip()]
    else:
        types = _list_service_types(base, token)
    instances = []
    if not types:
        rep.add("WARN", "实例配置", u"读不到服务类型列表", u"用 --services chat,embedding 指定")
    for service_type in types:
        try:
            body = http_json(base, "/api/config/instance/%s" % service_type, "GET", None, token)
        except GatewayError as exc:
            rep.add("WARN", "实例配置", u"读不到 %s 的实例（%s）" % (service_type, text_type(exc)),
                    u"确认服务类型名正确，或用 --services 指定")
            continue
        data = body.get("data") if isinstance(body, dict) else body
        if data is None:
            continue
        items = data if isinstance(data, list) else [data]
        for item in items:
            if isinstance(item, dict):
                merged = dict(item)
                merged["_serviceType"] = service_type
                instances.append(merged)

    if not instances and types:
        rep.add("WARN", "实例配置", u"一个实例都没读到",
                u"确认 /api/config/instance/<type> 有数据、token 有读权限")
    for inst in instances:
        label = u"%s/%s" % (inst.get("_serviceType"), inst.get("name"))
        missing = [k for k in ("name", "baseUrl", "path") if not inst.get(k)]
        if missing:
            rep.add("FAIL", "实例配置", u"%s 缺 %s" % (label, u"/".join(missing)),
                    u"网关会对这类实例直接 502")
            continue
        if not inst.get("adapter") or inst.get("adapterResolved") is False:
            rep.add("FAIL", "实例配置",
                    u"%s adapter=%s（未解析）" % (label, inst.get("adapter") or u"空"),
                    inst.get("adapterWarning") or u"在 model.services.<type>.adapter 或实例级 adapter 配置")
        else:
            rep.add("OK", "实例配置", u"%s adapter=%s status=%s"
                    % (label, inst.get("adapter"), inst.get("status")))
        status_text = inst.get("status")
        if status_text and status_text != "active":
            rep.add("WARN", "实例配置", u"%s status=%s" % (label, status_text),
                    u"非 active 的实例不会被选中")

    # 4b. 会话粘性 vs 多实例（#1126）：开了粘性又配多个实例时，负载均衡不会真正分流
    _check_sticky_ineffective(rep, base, token)

    # 5. 出站连通性（分层：DNS → TCP → HTTP；结论标注「本机视角」）
    for inst in instances:
        label = u"%s/%s" % (inst.get("_serviceType"), inst.get("name"))
        target = split_base_url(inst.get("baseUrl"))
        if not target:
            rep.add("FAIL", "出站连通性",
                    u"%s baseUrl 无法解析为 URL: %s" % (label, inst.get("baseUrl")))
            continue
        host = target["host"]
        is_literal = host.replace(".", "").isdigit() or ":" in host
        if is_literal:
            rep.add("OK", "出站连通性", u"%s 目标是 IP 字面量，跳过 DNS" % label)
        else:
            try:
                infos = socket.getaddrinfo(host, None)
                addresses = sorted(set([i[4][0] for i in infos]))
                rep.add("OK", "出站连通性", u"%s DNS %s → %s" % (label, host, u", ".join(addresses)))
            except Exception as exc:  # noqa: BLE001
                rep.add("WARN", "出站连通性",
                        u"%s DNS 解析失败: %s（%s）" % (label, host, text_type(exc)),
                        u"若下面「真实调用」通过，说明 doctor 与网关不在同一网络，这一项不代表网关")
                continue

        ok_tcp, ms, err = tcp_probe(host, target["port"])
        if not ok_tcp:
            rep.add("FAIL", "出站连通性",
                    u"%s TCP %s:%s 不通（%s）—— 这是 doctor 所在机器的视角"
                    % (label, host, target["port"], err),
                    u"doctor 与网关不在同一网络时，这项 FAIL 不代表网关；以「真实调用」为准")
            continue
        rep.add("OK", "出站连通性", u"%s TCP %s:%s %sms" % (label, host, target["port"], ms))

        probe_url = text_type(inst.get("baseUrl", u"")).rstrip("/") + "/health"
        try:
            started = time.time()
            status, _unused = _raw_request("GET", probe_url, None, None, 8)
            elapsed = int((time.time() - started) * 1000)
            if 200 <= status < 300:
                rep.add("OK", "出站连通性", u"%s HTTP %s (%sms)" % (label, status, elapsed))
            else:
                # 上游未必有 /health；404 不能记成 OK（SLA-TOOL-4）
                rep.add("WARN", "出站连通性", u"%s HTTP %s (%sms)" % (label, status, elapsed),
                        u"上游没有 /health 是常见情况，不代表网关有问题；看「真实调用」")
        except GatewayError:
            rep.add("WARN", "出站连通性",
                    u"%s HTTP 探活失败（上游可能没有 /health，不一定是问题）" % label)

    # 6. 熔断器（真实路径是 /circuit-breaker/states；拿不到要报，不能静默跳过 —— SLA-TOOL-5）
    try:
        body = http_json(base, "/api/config/instance/circuit-breaker/states", "GET", None, token)
        data = body.get("data") if isinstance(body, dict) else body
        items = data if isinstance(data, list) else []
        open_states = []
        for item in items:
            if not isinstance(item, dict):
                continue
            state = item.get("state") or item.get("circuitState")
            if state and state != "CLOSED":
                open_states.append((item.get("instanceId") or item.get("name"), state))
        if open_states:
            for name, state in open_states:
                rep.add("WARN", "熔断器", u"%s %s" % (name, state),
                        u"熔断器打开会把真实失败原因遮蔽成 503，先看日志里的下游错误")
        else:
            rep.add("OK", "熔断器", u"%d 个实例，无 OPEN" % len(items))
    except GatewayError as exc:
        rep.add("WARN", "熔断器", u"读不到熔断器状态（%s）" % text_type(exc),
                u"确认 RBAC 权限或端点是否改名")

    # 7. 真实调用
    if args.no_chat or not token:
        rep.add("WARN", "真实调用", u"跳过（无 token 或 --no-chat）")
    else:
        model = args.model
        if not model:
            # 只挑 chat 实例：instances 覆盖全部服务类型，首个实例很可能是图像/嵌入模型，
            # 用它去调 /v1/chat/completions 只会得到 "No instances found in service type 'chat'"，
            # 把「工具选错了模型」误报成「网关不可用」。
            for inst in instances:
                if inst.get("_serviceType") == "chat" and inst.get("name"):
                    model = inst.get("name")
                    break
        if not model:
            seen = sorted(set(text_type(i.get("_serviceType")) for i in instances if i.get("_serviceType")))
            rep.add("WARN", "真实调用", u"没有 chat 实例可打，跳过",
                    u"现有服务类型：%s；用 --model <name> 指定一个 chat 模型"
                    % (u",".join(seen) or u"无"))
        else:
            started = time.time()
            try:
                status, text = _raw_request(
                    "POST", join_url(base, "/v1/chat/completions"),
                    {"model": model, "messages": [{"role": "user", "content": "ping"}], "max_tokens": 1},
                    {"Content-Type": "application/json", TOKEN_HEADER: token}, 60)
                elapsed = int((time.time() - started) * 1000)
                if status == 200 and '"choices"' in text:
                    rep.add("OK", "真实调用", u"chat 200 (%sms)" % elapsed)
                else:
                    message = text
                    try:
                        parsed = json.loads(text)
                        message = ((parsed.get("error") or {}).get("message")
                                   or parsed.get("message") or text)
                    except ValueError:
                        pass
                    rep.add("FAIL", "真实调用",
                            u"chat %s (%sms): %s" % (status, elapsed, _truncate(message, 200)),
                            u"真实链路失败，先看这条 message，再对照日志")
            except GatewayError as exc:
                rep.add("FAIL", "真实调用", u"chat 异常: %s" % text_type(exc))

    return rep


def cmd_doctor(args):
    bases = normalize_bases(args.base)
    if not bases:
        emit_err(u"至少需要一个 --base")
        return 2
    if len(bases) == 1:
        rep = _doctor_one(args, bases[0])
        if args.format == "json":
            emit(json.dumps({"baseUrl": bases[0], "results": rep.results},
                            ensure_ascii=False, indent=2))
        else:
            rep.print_text()
        return rep.exit_code()

    reports = []
    for base in bases:
        reports.append((base, _doctor_one(args, base)))

    if args.format == "json":
        payload = {"replicas": [{"baseUrl": b, "results": r.results} for b, r in reports]}
        emit(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for base, rep in reports:
            fail = sum(1 for r in rep.results if r["level"] == "FAIL")
            warn = sum(1 for r in rep.results if r["level"] == "WARN")
            emit(u"%-30s 失败 %d / 警告 %d / 通过 %d"
                 % (base, fail, warn, len(rep.results) - fail - warn))
        emit(u"")
        emit(u"=== 问题明细（多副本时只列 WARN/FAIL）===")
        any_issue = False
        for base, rep in reports:
            issued = [r for r in rep.results if r["level"] in ("WARN", "FAIL")]
            if not issued:
                continue
            any_issue = True
            emit(u"")
            emit(u"[%s]" % base)
            for r in issued:
                emit(u"  [%s] %-6s %s" % (rep.ICON[r["level"]], r["area"], r["message"]))
                if r["hint"]:
                    emit(u"  %s└ %s" % (u" " * 13, r["hint"]))
        if not any_issue:
            emit(u"各副本均无 WARN/FAIL")
    codes = [rep.exit_code() for _, rep in reports]
    return 2 if 2 in codes else (1 if 1 in codes else 0)


def _finish_doctor(rep, args):
    if args.format == "json":
        emit(json.dumps({"baseUrl": args.base, "results": rep.results},
                        ensure_ascii=False, indent=2))
    else:
        rep.print_text()
    return rep.exit_code()


def _list_service_types(base, token):
    try:
        body = http_json(base, "/api/config/type/services", "GET", None, token)
        data = body.get("data") if isinstance(body, dict) else body
        if isinstance(data, list) and data:
            return [text_type(x) for x in data]
    except GatewayError:
        pass
    return ["chat"]


def _check_sticky_ineffective(rep, base, token):
    """检查「会话粘性开启 + 多实例」组合（#1126）。

    粘性开启后同一客户端会被固定到同一上游实例，任何负载均衡策略都不会真的分流，
    表现为「流量只走一个实例」。控制台、健康检查、路由监控都不会报错，所以放在 doctor 里。
    """
    try:
        body = http_json(base, "/api/config/type", "GET", None, token)
    except GatewayError as exc:
        rep.add("WARN", "会话粘性", u"读不到服务配置，跳过粘性检查（%s）" % text_type(exc),
                u"确认 token 有 config 读权限")
        return
    data = body.get("data") if isinstance(body, dict) else body
    services = (data or {}).get("services") or {}
    hits = []
    for service_type, cfg in services.items():
        if not isinstance(cfg, dict):
            continue
        sticky = cfg.get("sticky") or {}
        count = len(cfg.get("instances") or [])
        if sticky.get("enabled") is True and count > 1:
            hits.append((text_type(service_type), count))
    if not hits:
        rep.add("OK", "会话粘性", u"没有「粘性开启 + 多实例」的服务")
        return
    for service_type, count in sorted(hits):
        rep.add("WARN", "会话粘性",
                u"%s 开了 sticky.enabled=true 且有 %d 个实例：同一客户端固定到同一实例，负载均衡不会分流"
                % (service_type, count),
                u"如不需要会话亲和，关闭：PUT /api/config/type/services/%s {\"sticky\":{\"enabled\":false}}"
                % service_type)

# ------------------------------------------------------------------ loadtest

def cmd_loadtest(args):
    parts = urlsplit(text_type(args.url))
    host = parts.hostname
    if not host:
        emit_err(u"--url 无法解析")
        return 2
    port = parts.port or (443 if parts.scheme == "https" else 80)
    path = (parts.path or "/") + (("?" + parts.query) if parts.query else u"")
    method = text_type(args.method or "POST").upper()
    timeout = float(args.timeout)
    concurrency = int(args.concurrency)
    duration = float(args.duration)

    if method == "GET":
        body = b""
    elif args.body:
        body = text_type(args.body).encode("utf-8")
    else:
        payload = {
            "model": args.model or "mock-model",
            "messages": [{"role": "user", "content": "ping"}],
            "stream": text_type(args.stream).lower() == "true",
        }
        body = json.dumps(payload).encode("utf-8")

    headers = {}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        headers["Content-Length"] = str(len(body))
    if args.api_key:
        headers["X-API-Key"] = args.api_key

    lock = threading.Lock()
    ok_latencies = []
    failed_latencies = []
    failures = []
    error_kinds = {}
    counters = {"ok": 0, "failed": 0}
    deadline = time.time() + duration

    def record_failure(kind, sample, elapsed):
        lock.acquire()
        try:
            counters["failed"] += 1
            failed_latencies.append(elapsed)
            error_kinds[kind] = error_kinds.get(kind, 0) + 1
            if len(failures) < 5:
                failures.append(sample)
        finally:
            lock.release()

    def worker():
        conn = None
        while time.time() < deadline:
            started = time.time()
            try:
                if conn is None:
                    conn = httpclient.HTTPConnection(host, port, timeout=timeout)
                conn.request(method, path, body=body if method != "GET" else None, headers=headers)
                resp = conn.getresponse()
                payload = resp.read()
                elapsed = (time.time() - started) * 1000
                if 200 <= resp.status < 300 and len(payload) > 0:
                    lock.acquire()
                    try:
                        counters["ok"] += 1
                        ok_latencies.append(elapsed)
                    finally:
                        lock.release()
                else:
                    record_failure("http_%d" % resp.status,
                                   {"status": resp.status, "bytes": len(payload),
                                    "ms": int(elapsed)}, elapsed)
            except Exception as exc:  # noqa: BLE001
                elapsed = (time.time() - started) * 1000
                kind = type(exc).__name__
                if isinstance(exc, socket.timeout):
                    kind = "ETIMEOUT"
                record_failure(kind, {"error": text_type(exc), "ms": int(elapsed)}, elapsed)
                try:
                    if conn is not None:
                        conn.close()
                except Exception:
                    pass
                conn = None
        try:
            if conn is not None:
                conn.close()
        except Exception:
            pass

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    started_at = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = max(time.time() - started_at, 1e-6)

    def summarize(values):
        if not values:
            return {"samples": 0, "p50": None, "p95": None, "p99": None, "avg": None, "max": None}
        ordered = sorted(values)

        def pct(p):
            index = int((p * len(ordered) + 99) // 100) - 1
            if index < 0:
                index = 0
            if index > len(ordered) - 1:
                index = len(ordered) - 1
            return round(ordered[index], 1)

        return {"samples": len(values), "p50": pct(50), "p95": pct(95), "p99": pct(99),
                "avg": round(sum(ordered) / len(ordered), 1),
                "max": round(ordered[-1], 1)}

    total = counters["ok"] + counters["failed"]
    summary = {
        "target": args.url, "method": method, "concurrency": concurrency,
        "durationSec": int(duration), "timeoutSec": int(timeout),
        "total": total, "ok": counters["ok"], "failed": counters["failed"],
        "rps": round(total / wall, 1),
        "latencyMs": summarize(ok_latencies),
        "failedLatencyMs": summarize(failed_latencies),
        "errorRate": round((counters["failed"] / total * 100) if total else 0, 2),
        "errorKinds": error_kinds, "failures": failures,
    }

    if args.json:
        emit(json.dumps(summary, ensure_ascii=False))
        return 0

    def fmt(s):
        if s["samples"] == 0:
            return u"无成功样本"
        return u"p50 %sms  p95 %sms  p99 %sms  avg %sms  max %sms" % (
            s["p50"], s["p95"], s["p99"], s["avg"], s["max"])

    emit(u"=== 压测结果 ===")
    emit(u"目标        %s  [%s]" % (summary["target"], method))
    emit(u"并发/时长   %d / %ds（单请求超时 %ds）" % (concurrency, int(duration), int(timeout)))
    emit(u"请求数      %d（成功 %d，失败 %d）" % (total, counters["ok"], counters["failed"]))
    emit(u"RPS         %s" % summary["rps"])
    emit(u"成功延迟    %s" % fmt(summary["latencyMs"]))
    if counters["failed"] > 0:
        emit(u"失败延迟    %s" % fmt(summary["failedLatencyMs"]))
        emit(u"失败分类    %s" % json.dumps(error_kinds, ensure_ascii=False))
        emit(u"失败样例    %s" % json.dumps(failures, ensure_ascii=False))
    emit(u"错误率      %s%%" % summary["errorRate"])
    if counters["ok"] == 0 and counters["failed"] > 0:
        emit(u"")
        emit(u"注意        本轮没有任何成功请求，「成功延迟」为空不是「延迟很好」，")
        emit(u"            而是全部失败；容量结论要看「失败延迟」+「失败分类」。")
    return 0


# ------------------------------------------------------------------ main

# ==================== verify（升级/发版后的行为回归） ====================

DEFAULT_MIN_HEAP_MB = 520


def _v_short(value, limit=120):
    text = value if isinstance(value, text_type) else json.dumps(value, ensure_ascii=False)
    if text is None:
        return u"none"
    return text if len(text) <= limit else text[:limit] + u"…"


def _v_unwrap(parsed):
    if isinstance(parsed, dict) and u"data" in parsed:
        return parsed.get(u"data")
    return parsed


def _v_read(base, path, token, timeout=15, method=u"GET", body=None):
    return _v_unwrap(http_json(base, path, method=method, body=body, token=token, timeout=timeout))


def _v_write(base, path, token, body, timeout=15):
    return _v_unwrap(http_json(base, path, method=u"PUT", body=body, token=token, timeout=timeout))


def _v_raw(url, headers, method=u"GET", body=None, timeout=15):
    # 直接要状态码与响应体原文，所以不走 http_json（它在非 2xx 时抛异常）
    hdrs = dict(headers or {})
    if body is not None:
        hdrs[u"Content-Type"] = u"application/json"
    try:
        return _raw_request(method, url, body, hdrs, timeout)
    except GatewayError:
        return 0, u""


def _v_check_version(base, token, expect, timeout):
    code, text = _v_raw(base.rstrip(u"/") + u"/actuator/info", {}, timeout=timeout)
    if code != 200:
        return False, u"HTTP %d" % code
    try:
        info = json.loads(text)
    except ValueError:
        return False, u"info 不是合法 JSON"
    version = ((info or {}).get(u"build") or {}).get(u"version")
    if not version:
        return False, u"无 build 段；SLA 工具链无法自动确认版本（升级前行为）"
    if expect and version != expect:
        return False, u"build.version=%s，期望 %s" % (version, expect)
    return True, u"build.version=%s" % version


def _v_check_heap(base, token, min_heap_mb, timeout):
    metrics = scrape_metrics(base, timeout)
    heap_max = 0
    for name, value in metrics.items():
        if not name.startswith(u"jvm_memory_max_bytes"):
            continue
        if u'area="heap"' not in name:
            continue
        if value > heap_max:
            heap_max = value
    if not heap_max:
        return None, u"prometheus 未提供 jvm_memory_max_bytes"
    mb = int(round(heap_max / 1024.0 / 1024.0))
    ok = mb >= min_heap_mb
    detail = u"%d MB（阈值 %d MB）" % (mb, min_heap_mb)
    if not ok:
        detail += u"；升级前 -Xmx 覆盖了 MaxRAMPercentage，堆上限被钉死"
    return ok, detail


def _v_check_static(base, token, timeout):
    code, text = _v_raw(base.rstrip(u"/") + u"/admin/assets/__verify_missing__.js", {}, timeout=timeout)
    body = text or u""
    if code != 404:
        return False, u"HTTP %d；缺失产物不该返回 200" % code
    traceable = u"静态资源不存在" in body
    detail = u"404，body %d 字节" % len(body)
    if traceable:
        detail += u"（响应体与日志都能定位到具体路径）"
    else:
        detail += u"；升级前这里既无响应体也无日志"
    return traceable, detail


def _v_check_threaddump(base, token, timeout):
    code, text = _v_raw(
        base.rstrip(u"/") + u"/actuator/threaddump",
        {u"Accept": u"application/json", TOKEN_HEADER: token or u""},
        timeout=timeout,
    )
    if code == 200:
        try:
            payload = json.loads(text)
            threads = len(payload.get(u"threads") or [])
            return True, u"HTTP 200，%d 个线程" % threads
        except ValueError:
            return True, u"HTTP 200"
    return False, u"HTTP %d；进程卡死时没有 threaddump 就没有现场证据" % code


def _v_check_incremental(base, token, service_type, allow_writes, changes, timeout):
    if not allow_writes:
        return None, u"需要 --allow-writes（会改写服务配置，随后自动恢复）"
    path = u"/api/config/type/services/" + service_type
    before = _v_read(base, path, token, timeout) or {}
    adapter_before = before.get(u"adapter")
    lb_before = before.get(u"loadBalance")
    if adapter_before is None:
        return None, u"%s 读不到 adapter，无法对比" % service_type
    _v_write(base, path, token, {u"loadBalance": {u"type": u"round-robin"}}, timeout)
    changes.append(
        (
            u"%s.loadBalance 恢复" % service_type,
            lambda: _v_write(base, path, token, {u"loadBalance": lb_before or {u"type": u"least-connections"}}, timeout),
        )
    )
    after = _v_read(base, path, token, timeout) or {}
    adapter_after = after.get(u"adapter")
    ok = adapter_after == adapter_before
    detail = u"PUT 只传 loadBalance 后 adapter %s → %s" % (adapter_before, adapter_after)
    if not ok:
        detail += u"；升级前会把未提交的字段清空"
    return ok, detail


def _v_check_sticky(base, token, service_type, allow_writes, changes, timeout):
    if not allow_writes:
        return None, u"需要 --allow-writes（会改写 sticky 配置，随后自动恢复）"
    path = u"/api/config/type/services/" + service_type
    before = _v_read(base, path, token, timeout) or {}
    sticky_before = before.get(u"sticky")
    try:
        _v_write(base, path, token, {u"sticky": {u"enabled": False}}, timeout)
    except GatewayError as exc:
        return False, u"PUT sticky 失败: %s" % text_type(exc)
    # 增量语义下无法删掉整块，原本没有 sticky 时恢复成 {enabled: true}，与「缺省即开启」等价
    changes.append(
        (
            u"%s.sticky 恢复" % service_type,
            lambda: _v_write(
                base, path, token,
                {u"sticky": sticky_before if sticky_before else {u"enabled": True}},
                timeout,
            ),
        )
    )
    after = _v_read(base, path, token, timeout) or {}
    enabled_after = (after.get(u"sticky") or {}).get(u"enabled")
    ok = enabled_after is False
    detail = u"PUT sticky.enabled=false 后回读 %s" % json.dumps(after.get(u"sticky"), ensure_ascii=False)
    if not ok:
        detail += u"；升级前 UpdateServiceConfigRequest 没有 sticky 字段，写不进去（issue #1095）"
    return ok, detail


def _v_check_quota(base, token, service_type, allow_writes, changes, timeout):
    if not allow_writes:
        return None, u"需要 --allow-writes（会创建一个临时 Key，随后删除）"
    name = u"sla-verify-%d" % int(time.time())
    try:
        created = _v_read(
            base,
            u"/api/auth/api-keys",
            token,
            timeout,
            method=u"POST",
            body={u"name": name, u"serviceType": service_type, u"dailyRequestLimit": 2},
        ) or {}
    except GatewayError as exc:
        return None, u"创建临时 Key 失败: %s" % text_type(exc)
    key_id = created.get(u"keyId")
    key_value = created.get(u"keyValue")
    if not key_value:
        return None, u"创建成功但响应里没有明文 Key"
    if key_id:
        changes.append(
            (
                u"删除临时 Key %s" % key_id,
                lambda: _v_read(base, u"/api/auth/api-keys/" + key_id, token, timeout, method=u"DELETE"),
            )
        )
    statuses = []
    for _i in range(3):
        code, _text = _v_raw(
            base.rstrip(u"/") + u"/v1/chat/completions",
            {u"X-API-Key": key_value},
            method=u"POST",
            body={u"model": u"verify-model", u"messages": [{u"role": u"user", u"content": u"quota probe"}]},
            timeout=timeout,
        )
        statuses.append(code)
    blocked = 429 in statuses
    joined = u"/".join([text_type(s) for s in statuses])
    if not blocked and all(s == 401 for s in statuses):
        return None, u"Key 未被接受（%s），无法判断配额是否生效" % joined
    detail = u"额度 2、连打 3 次，状态=%s" % joined
    if not blocked:
        detail += u"；升级前账本默认关闭，额度从不检查"
    return blocked, detail


def _v_check_lb(base, token, service_type, timeout):
    instances = _v_read(base, u"/api/config/instance/" + service_type, token, timeout)
    items = instances if isinstance(instances, list) else []
    healthy = [x for x in items if (x or {}).get(u"healthStatus") == u"HEALTHY"]
    if len(healthy) < 2:
        return None, u"%s 健康实例 %d 个（需要 ≥2 个同名实例才能观察分散）" % (service_type, len(healthy))
    # 先判「负载均衡是不是根本没被调用」：粘性默认开启（issue #1095），
    # 亲和键恒定，此时无论配哪种策略都不会分流。必须放在发请求之前，
    # 否则会把「粘性覆盖」误判成「负载均衡坏了」。
    cfg = _v_read(base, u"/api/config/type/services/" + service_type, token, timeout) or {}
    sticky = cfg.get(u"sticky") or {}
    if sticky.get(u"enabled") is not False:
        return False, (
            u"负载均衡被会话粘性覆盖：健康实例 %d 个，但 %s.sticky 未显式关闭"
            u"（服务端默认开启，亲和键 apiKeyId|serviceType|modelName）⇒ 同一主体 + 同一模型的请求"
            u"恒落在同一实例，配置的负载均衡策略不会生效。"
            u"关闭方式：PUT /api/config/type/services/%s {\"sticky\":{\"enabled\":false}}"
            u"（3.8.3 起支持），或在 yml 设 model.services.%s.sticky.enabled=false 后重启"
        ) % (len(healthy), service_type, service_type, service_type)
    model = (healthy[0] or {}).get(u"name")
    if not model:
        return None, u"健康实例没有 name，无法按模型名路由"
    hits = {}
    for _i in range(30):
        code, text = _v_raw(
            base.rstrip(u"/") + u"/v1/chat/completions",
            {TOKEN_HEADER: token or u""},
            method=u"POST",
            body={u"model": model, u"messages": [{u"role": u"user", u"content": u"lb probe"}]},
            timeout=timeout,
        )
        tag = None
        try:
            tag = json.loads(text).get(u"mock_instance")
        except (ValueError, AttributeError):
            tag = None
        key = tag or (u"http-%d" % code)
        hits[key] = hits.get(key, 0) + 1
    spread = len([k for k in hits if not k.startswith(u"http-")])
    ok = spread >= 2
    detail = u"30 次分布=%s" % _v_short(hits, 160)
    if ok:
        detail += u"；粘性已关闭，策略生效"
    else:
        detail += (
            u"；粘性已关闭但仍未分散 ⇒ 这是负载均衡自身的问题"
            u"（查 least-connections 的起点轮转、round-robin 的计数器 key 是否每次新建），不是粘性造成的"
        )
    return ok, detail


def _verify_one(args, base):
    timeout = args.timeout
    try:
        token = args.token or login(base, args.user, args.password, timeout)
    except GatewayError as exc:
        return {
            u"base": base,
            u"results": [],
            u"restoreFailures": [],
            u"summary": {u"passed": 0, u"failed": 1, u"skipped": 0},
            u"error": u"登录失败: %s" % text_type(exc),
            u"exitCode": 2,
        }

    wanted = None
    if args.only:
        wanted = set([x.strip() for x in args.only.split(u",") if x.strip()])

    changes = []
    checks = [
        (u"version.build", lambda: _v_check_version(base, token, args.expect, timeout)),
        (u"heap.container-ratio", lambda: _v_check_heap(base, token, args.min_heap_mb, timeout)),
        (u"static.missing-asset-traceable", lambda: _v_check_static(base, token, timeout)),
        (u"actuator.threaddump", lambda: _v_check_threaddump(base, token, timeout)),
        (
            u"config.incremental-put",
            lambda: _v_check_incremental(base, token, args.service_type, args.allow_writes, changes, timeout),
        ),
        (
            u"sticky.toggleable",
            lambda: _v_check_sticky(base, token, args.service_type, args.allow_writes, changes, timeout),
        ),
        (
            u"quota.enforced",
            lambda: _v_check_quota(base, token, args.service_type, args.allow_writes, changes, timeout),
        ),
        (u"lb.spread", lambda: _v_check_lb(base, token, args.service_type, timeout)),
    ]

    rows = []
    for check_id, fn in checks:
        if wanted is not None and check_id not in wanted:
            continue
        try:
            ok, detail = fn()
        except Exception as exc:
            ok, detail = False, u"抛错: %s" % text_type(exc)
        status = u"SKIP" if ok is None else (u"PASS" if ok else u"FAIL")
        rows.append({u"id": check_id, u"status": status, u"detail": detail})
        if not args.json:
            emit(u"[%s] %-26s %s" % (status, check_id, detail))

    restore_failures = []
    for description, restore in reversed(changes):
        try:
            restore()
        except Exception as exc:
            restore_failures.append(u"%s: %s" % (description, text_type(exc)))

    passed = len([r for r in rows if r[u"status"] == u"PASS"])
    failed = len([r for r in rows if r[u"status"] == u"FAIL"])
    skipped = len([r for r in rows if r[u"status"] == u"SKIP"])

    return {
        u"base": base,
        u"results": rows,
        u"restoreFailures": restore_failures,
        u"summary": {u"passed": passed, u"failed": failed, u"skipped": skipped},
        u"exitCode": 2 if restore_failures else (0 if failed == 0 else 1),
    }


def cmd_verify(args):
    bases = normalize_bases(args.base)
    if not bases:
        emit_err(u"至少需要一个 --base")
        return 2
    results = []
    for index, base in enumerate(bases, 1):
        replica_header(index, len(bases), base)
        result = _verify_one(args, base)
        results.append(result)
        if not args.json:
            summary = result["summary"]
            emit(u"")
            emit(u"通过 %d / 失败 %d / 跳过 %d"
                 % (summary[u"passed"], summary[u"failed"], summary[u"skipped"]))
            if result["restoreFailures"]:
                for failure in result["restoreFailures"]:
                    emit_err(u"[恢复失败] " + failure)

    if args.json:
        if len(bases) > 1:
            emit(json.dumps({"replicas": results}, ensure_ascii=False, indent=2))
        else:
            emit(json.dumps(results[0], ensure_ascii=False, indent=2))
    else:
        if len(bases) > 1:
            emit(u"")
            emit(u"=== 汇总 ===")
            for result in results:
                summary = result["summary"]
                emit(u"%-30s 通过 %d / 失败 %d / 跳过 %d"
                     % (result["base"], summary[u"passed"], summary[u"failed"],
                        summary[u"skipped"]))
        emit(u"")
        emit(u"判据：失败项为 0 才算这次升级验收通过；跳过项要看原因，不要当成通过。")

    codes = [r["exitCode"] for r in results]
    return 2 if 2 in codes else (1 if 1 in codes else 0)


# ------------------------------------------------------------------ audit


def _audit_data(base, token, path):
    """取一个只读配置接口的 data；失败返回 None（单项缺失不应让整个体检失败）。"""
    try:
        body = http_json(base, path, "GET", None, token)
    except GatewayError:
        return None
    return body.get("data") if isinstance(body, dict) else body


def audit_services(rep, services):
    """逐服务检查配置组合，每条建议都带「当前值 → 怎么改」。"""
    for service_type in sorted(services.keys()):
        cfg = services.get(service_type) or {}
        instances = cfg.get("instances") or []
        sticky = cfg.get("sticky") or {}
        lb = cfg.get("loadBalance") or {}
        rate = cfg.get("rateLimit") or {}
        count = len(instances)
        prefix = u"%s：" % service_type

        if count == 0:
            rep.add("FAIL", u"配置", prefix + u"没有任何实例，该服务的请求会全部 503",
                    u"补一个实例，或从配置里移除该服务类型")
            continue

        if sticky.get("enabled") is True and count > 1:
            rep.add("WARN", u"配置",
                    prefix + u"sticky.enabled=true、%d 个实例、策略 %s → 同一客户端固定同一实例，"
                              u"负载均衡不会分流" % (count, lb.get("type") or u"未设置"),
                    u"不需要会话亲和就关掉：PUT /api/config/type/services/%s "
                    u"{\"sticky\":{\"enabled\":false}}" % service_type)

        active = [i for i in instances
                  if text_type(i.get("status") or "").lower() == "active"]
        if not active:
            rep.add("FAIL", u"配置", prefix + u"%d 个实例全部不是 active → 一律 503" % count,
                    u"在「实例管理」把它们改回 active")
        else:
            healthy = [i for i in active
                       if text_type(i.get("healthStatus") or "").upper() == "HEALTHY"]
            if not healthy:
                reason = active[0].get("errorMessage") or u"未给出原因"
                rep.add("WARN", u"配置",
                        prefix + u"%d 个 active 实例没有一个是 HEALTHY（首个：%s）"
                        % (len(active), reason),
                        u"先确认 baseUrl 从网关容器内可达 —— 容器里的 localhost 不是宿主机")

        seen = {}
        for inst in instances:
            key = (inst.get("baseUrl") or "", inst.get("path") or "")
            seen[key] = seen.get(key, 0) + 1
        duplicate_groups = [k for k, v in seen.items() if v > 1]
        if duplicate_groups:
            rep.add("WARN", u"配置",
                    prefix + u"有 %d 组实例的 baseUrl+path 完全相同，属于假多实例" % len(duplicate_groups),
                    u"多实例要指向不同上游地址，否则分流没有意义")

        zero_weight = [i for i in instances if (i.get("weight") or 0) <= 0]
        if zero_weight:
            rep.add("WARN", u"配置",
                    prefix + u"%d 个实例 weight<=0（加权比较里拿不到份额）" % len(zero_weight),
                    u"权重通常取正整数（1 起步），只有想做「权重为 0 的备胎」时才留 0")

        if count > 1 and not lb.get("type"):
            rep.add("WARN", u"配置", prefix + u"多实例但没有显式负载均衡策略",
                    u"显式指定 least-connections / round-robin / ip-hash，避免依赖默认值")

        if rate.get("enabled") is True:
            rate_value = rate.get("rate") or 0
            capacity = rate.get("capacity") or 0
            if rate_value <= 0:
                rep.add("WARN", u"配置",
                        prefix + u"限流 enabled=true 但 rate=%s" % rate_value,
                        u"rate 是每秒放行数，<=0 等于没配：要么设正数，要么关掉 enabled")
            if rate_value > 0 and capacity and capacity < rate_value:
                rep.add("WARN", u"配置",
                        prefix + u"限流 capacity=%s 小于 rate=%s，突发额度不足一秒" % (capacity, rate_value),
                        u"capacity 通常取 rate 的 1~2 倍")
            if text_type(rate.get("keyDimension") or "") == "client-ip" \
                    and rate.get("clientIpEnable") is not True:
                rep.add("WARN", u"配置",
                        prefix + u"限流按 client-ip 分桶但 clientIpEnable=false → 退化为 remoteAddress",
                        u"网关前没有 L7 代理时 remoteAddress 是节点 IP，所有人共用一个桶；"
                        u"要么打开 clientIpEnable，要么改 keyDimension")


def audit_quota(rep, quota):
    """配额账本的窗口集合是否与 v3.8.6 的分工一致。"""
    if quota is None:
        rep.add("WARN", u"配置", u"读不到 /api/config/quota，跳过配额配置检查",
                u"确认 token 有配置读权限")
        return
    windows = [text_type(w).upper() for w in (quota.get("windows") or [])]
    if "MINUTE" in windows:
        rep.add("WARN", u"配置",
                u"配额窗口含 MINUTE（windows=%s）→ v3.8.6 起账本不再判每分钟速率，"
                u"这一段不会产生计数" % (u",".join(windows)),
                u"把 MINUTE 从 jairouter.quota.windows 移除；每分钟限额由 "
                u"jairouter.ratelimit.api-key 负责")
    else:
        rep.add("OK", u"配置", u"配额窗口 %s，未含 MINUTE（每分钟限额由主链路限流器判定）"
                % (u",".join(windows) or u"空"))
    if quota.get("enabled") is False:
        rep.add("WARN", u"配置", u"配额账本 enabled=false → 小时/天/月窗口的限额都不判定",
                u"需要按窗口限流就打开 jairouter.quota.enabled")


def audit_ratelimit(rep, ratelimit):
    """API Key 级 / 终端用户级限流开关与分布式开关的组合。"""
    if ratelimit is None:
        rep.add("WARN", u"配置", u"读不到 /api/config/ratelimit，跳过限流配置检查",
                u"该端点需要 3.8.6 及以上版本")
        return
    api_key = ratelimit.get("apiKey") or {}
    end_user = ratelimit.get("endUser") or {}
    distributed = ratelimit.get("distributed") or {}
    if api_key.get("enabled") is False:
        rep.add("WARN", u"配置",
                u"API Key 每分钟限流 enabled=false → rateLimitPerMinute 不再判定",
                u"需要该限额就打开 jairouter.ratelimit.api-key.enabled")
    else:
        rep.add("OK", u"配置", u"API Key 每分钟限流已开启，后端 %s"
                % (distributed.get("apiKeyBackend") or u"未知"))
    if distributed.get("enabled") is not True:
        rep.add("WARN", u"配置",
                u"分布式限流 distributed.enabled=false → 每个副本各算一份，"
                u"实际放行量约为配置值 × 副本数",
                u"多副本部署请打开 jairouter.ratelimit.distributed.enabled，并确认 Redis 可达")
    if end_user.get("enabled") is True:
        rep.add("OK", u"配置", u"终端用户级限流已开启：%s 次/分/用户"
                % end_user.get("requestsPerMinute"))
    else:
        rep.add("OK", u"配置", u"终端用户级限流未开启（默认），只有 API Key 级限流生效")


def _audit_one(args, base):
    """对一个副本做配置体检，返回 (rep, services, quota, ratelimit)。"""
    rep = Reporter()
    token = args.token
    if not token and args.user and args.password:
        try:
            token = login(base, args.user, args.password)
        except GatewayError as exc:
            rep.add("FAIL", u"认证", u"登录失败：%s" % text_type(exc),
                    u"确认账号密码，或直接用 --token")
            return rep, None, None, None
    if not token:
        rep.add("FAIL", u"认证", u"audit 需要 --token 或 --user/--pass",
                u"例如 --user admin --pass <密码>")
        return rep, None, None, None

    payload = _audit_data(base, token, "/api/config/type")
    services = payload.get("services") if isinstance(payload, dict) else None
    if not services:
        rep.add("FAIL", u"配置", u"读不到 /api/config/type 的服务配置",
                u"确认 token 有配置读权限、网关版本 >= 3.7")
        return rep, None, None, None

    audit_services(rep, services)
    quota = _audit_data(base, token, "/api/config/quota")
    ratelimit = _audit_data(base, token, "/api/config/ratelimit")
    audit_quota(rep, quota)
    audit_ratelimit(rep, ratelimit)
    return rep, services, quota, ratelimit


def audit_cluster(rep, snapshots):
    """副本间一致性：实例数、粘性开关、限流后端。

    snapshots 是 [(base, services, quota, ratelimit)]；读不到配置的副本 services 为 None。
    只有传了 2 个及以上 --base 时才会被调用。
    """
    if len(snapshots) < 2:
        return
    usable = [s for s in snapshots if s[1]]
    if len(usable) < len(snapshots):
        missing = [b for b, services, _, _ in snapshots if not services]
        rep.add("FAIL", "集群", "有副本读不到服务配置：%s" % ", ".join(missing),
                "确认每个副本都存活，且 token 在所有副本上都能用")
    if len(usable) < 2:
        return

    # 1. 每个服务的实例数：不一致说明配置没同步到所有副本
    all_types = set()
    for _, services, _, _ in usable:
        all_types.update(services.keys())
    diffs = []
    for service_type in sorted(all_types):
        counts = [len(((s.get(service_type) or {}).get("instances") or []))
                  for _, s, _, _ in usable]
        if len(set(counts)) > 1:
            diffs.append(u"%s=%s" % (service_type, u"/".join(text_type(c) for c in counts)))
    if diffs:
        rep.add("WARN", "集群", "各副本的服务实例数不一致：%s" % u"；".join(diffs),
                "配置还没同步到所有副本 —— 提交后等几秒再看；持续不一致说明副本没共用同一配置库")
    else:
        rep.add("OK", "集群", "各副本的服务实例数一致（%d 个服务类型）" % len(all_types))

    # 2. 粘性开关
    sticky_states = []
    for base, services, _, _ in usable:
        on = sorted(st for st, cfg in services.items()
                    if (cfg.get("sticky") or {}).get("enabled"))
        sticky_states.append((base, tuple(on)))
    if len(set(st for _, st in sticky_states)) > 1:
        rep.add("WARN", "集群", "各副本的粘性开关不一致：%s" % u"；".join(
            u"%s: %s" % (b, ",".join(on) or "无") for b, on in sticky_states),
            "粘性只在部分副本生效时，同一客户端在不同副本上会被分到不同实例")
    else:
        rep.add("OK", "集群", "各副本的粘性开关一致")

    # 3. 限流后端：分布式没开时每个副本各算一份
    backends = []
    for base, _, _, ratelimit in usable:
        api_key = (ratelimit or {}).get("apiKey") or {}
        distributed = (ratelimit or {}).get("distributed") or {}
        backends.append((base, api_key.get("enabled"), distributed.get("enabled"),
                         distributed.get("apiKeyBackend")))
    if len(set((b[1], b[2], b[3]) for b in backends)) > 1:
        rep.add("WARN", "集群", "各副本的限流配置不一致：%s" % u"；".join(
            u"%s: apiKey=%s distributed=%s backend=%s" % b for b in backends),
            "限流判定按各副本自己的配置走，配置不一致会让实际放行量忽多忽少")
    elif backends[0][2] is not True:
        rep.add("WARN", "集群", "各副本限流配置一致，但分布式限流未开启（distributed.enabled=%s）"
                % backends[0][2],
                "多副本部署应打开 jairouter.ratelimit.distributed.enabled，否则每副本各算一份")
    else:
        rep.add("OK", "集群", "各副本限流配置一致，分布式后端 %s" % (backends[0][3] or "未知"))


def cmd_audit(args):
    bases = normalize_bases(args.base)
    if not bases:
        emit_err(u"至少需要一个 --base")
        return 2
    snapshots = []
    per_replica = []
    merged = Reporter()
    for index, base in enumerate(bases, 1):
        rep, services, quota, ratelimit = _audit_one(args, base)
        snapshots.append((base, services, quota, ratelimit))
        per_replica.append((base, rep))
        merged.results.extend(rep.results)
        replica_header(index, len(bases), base)
        rep.print_text()

    if len(bases) > 1:
        emit(u"")
        emit(u"=== 集群一致性 ===")
        cluster_rep = Reporter()
        audit_cluster(cluster_rep, snapshots)
        for r in cluster_rep.results:
            emit(u"[%s] %-6s %s" % (cluster_rep.ICON[r["level"]], r["area"], r["message"]))
            if r["hint"]:
                emit(u"%s└ %s" % (u" " * 15, r["hint"]))
        merged.results.extend(cluster_rep.results)

        emit(u"")
        emit(u"=== 汇总 ===")
        for base, rep in per_replica:
            fail = sum(1 for r in rep.results if r["level"] == "FAIL")
            warn = sum(1 for r in rep.results if r["level"] == "WARN")
            emit(u"%-30s 失败 %d / 警告 %d / 通过 %d"
                 % (base, fail, warn, len(rep.results) - fail - warn))
        total_fail = sum(1 for r in merged.results if r["level"] == "FAIL")
        total_warn = sum(1 for r in merged.results if r["level"] == "WARN")
        emit(u"合计 %d 项：%d 失败 / %d 警告 / %d 通过"
             % (len(merged.results), total_fail, total_warn,
                len(merged.results) - total_fail - total_warn))

    emit(u"")
    emit(u"判据：WARN 表示「配置能生效，但大概率不是你想要的效果」，按 hint 里的命令/配置项改即可；"
         u"FAIL 表示该服务当前不可用。")
    return merged.exit_code()

def build_parser():
    parser = argparse.ArgumentParser(
        prog="sla.py", description=u"JAiRouter SLA 工具链（纯 Python，标准库实现）")
    parser.add_argument("--version-check", action="store_true", help=u"只检查 Python 版本并退出")
    sub = parser.add_subparsers(dest="command")

    pf = sub.add_parser("preflight", help=u"契约自检")
    pf.add_argument("--base", "--base-url", dest="base", action="append", required=True)
    pf.add_argument("--user", default="admin")
    pf.add_argument("--pass", "--password", dest="password", required=True)
    pf.add_argument("--version", default=None)
    pf.add_argument("--service-type", default="chat")
    pf.add_argument("--contract", default=None)
    pf.add_argument("--allow-unknown-version", action="store_true")
    pf.add_argument("--json", action="store_true")
    pf.set_defaults(func=cmd_preflight)

    doc = sub.add_parser("doctor", help=u"现场诊断")
    doc.add_argument("--base", "--base-url", dest="base", action="append", required=True)
    doc.add_argument("--user", default="admin")
    doc.add_argument("--pass", "--password", dest="password", default=None)
    doc.add_argument("--token", default=None)
    doc.add_argument("--services", default=None)
    doc.add_argument("--model", default=None)
    doc.add_argument("--no-chat", action="store_true")
    doc.add_argument("--format", choices=["text", "json"], default="text")
    doc.set_defaults(func=cmd_doctor)

    lt = sub.add_parser("loadtest", help=u"压测")
    lt.add_argument("--url", required=True)
    lt.add_argument("--method", default="POST")
    lt.add_argument("--body", default=None)
    lt.add_argument("--api-key", dest="api_key", default="")
    lt.add_argument("--concurrency", default=20)
    lt.add_argument("--duration", default=30)
    lt.add_argument("--timeout", default=30)
    lt.add_argument("--stream", default="false")
    lt.add_argument("--model", default=None)
    lt.add_argument("--json", action="store_true")
    lt.set_defaults(func=cmd_loadtest)

    vf = sub.add_parser("verify", help=u"升级/发版后的行为回归")
    vf.add_argument("--base", "--base-url", dest="base", action="append", required=True)
    vf.add_argument("--user", default="admin")
    vf.add_argument("--pass", "--password", dest="password", required=True)
    vf.add_argument("--token", default=None)
    vf.add_argument("--expect", default=None)
    vf.add_argument("--min-heap-mb", dest="min_heap_mb", type=int, default=DEFAULT_MIN_HEAP_MB)
    vf.add_argument("--allow-writes", dest="allow_writes", action="store_true")
    vf.add_argument("--service-type", dest="service_type", default="chat")
    vf.add_argument("--only", default=None)
    vf.add_argument("--timeout", type=int, default=15)
    vf.add_argument("--json", action="store_true")
    vf.set_defaults(func=cmd_verify)


    au = sub.add_parser("audit", help=u"配置体检：逐项给出当前配置值与优化建议")
    au.add_argument("--base", "--base-url", dest="base", action="append", required=True)
    au.add_argument("--user", default="admin")
    au.add_argument("--pass", "--password", dest="password", default=None)
    au.add_argument("--token", default=None)
    au.set_defaults(func=cmd_audit)

    return parser


def main(argv=None):
    if not python_supported():
        emit_err(u"需要 " + python_requirement_text())
        return 3
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] == "--version-check":
        emit(u"python %d.%d.%d ok" % tuple(sys.version_info[:3]))
        return 0
    parser = build_parser()
    args = parser.parse_args(argv)
    if not getattr(args, "command", None):
        parser.print_help()
        return 2
    try:
        return args.func(args)
    except GatewayError as exc:
        emit_err(u"失败: %s" % text_type(exc))
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
