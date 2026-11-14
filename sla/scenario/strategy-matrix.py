#!/usr/bin/env python3
"""策略矩阵测试（v2，只碰不会互相覆盖的写面）。

- 负载均衡：PUT /api/config/type/services/{type}，**必须带 adapter**（该接口是全量覆盖语义，见 issue #1088），
  每次写完立刻回读校验 adapter 未被清空，被清空就中止。
- 限流算法：PUT /api/config/instance/{type}/{instanceId}/rate-limit（实例级，独立存储，不影响服务配置）。
- 熔断：PUT /api/config/instance/{type}/{instanceId}/circuit-breaker（实例级）。

用法：
  python3 scenario/strategy-matrix.py --base http://127.0.0.1:8080 --user admin --pass ***
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

TIMEOUT = 20


def http(method, url, body=None, token=None, timeout=TIMEOUT):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if token:
        req.add_header("X-Jairouter-Token", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            try:
                return resp.status, json.loads(raw) if raw else None
            except ValueError:
                return resp.status, raw
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            return exc.code, json.loads(raw) if raw else None
        except ValueError:
            return exc.code, raw
    except Exception as exc:  # noqa: BLE001
        return 0, {"error": str(exc)}


def login(base, user, password):
    status, body = http("POST", base + "/api/auth/jwt/login", {"username": user, "password": password})
    if status != 200 or not body:
        raise SystemExit("登录失败: %s %s" % (status, body))
    return (body.get("data") or {})["token"]


def mock_total(mock_base):
    status, body = http("GET", mock_base + "/__mock/stats")
    if status == 200 and isinstance(body, dict):
        total = body.get("total") or body.get("requests")
        if isinstance(total, int):
            return total
        return sum(v for v in (body.get("byPath") or {}).values() if isinstance(v, int))
    return 0


def mock_reset(mock_base):
    http("POST", mock_base + "/__mock/reset", {})


def mock_behavior(mock_base, behavior):
    return http("POST", mock_base + "/__mock/behavior", behavior)


def chat_call(base, token, model):
    return http("POST", base + "/v1/chat/completions",
                {"model": model, "messages": [{"role": "user", "content": "ping"}]}, token)


def hammer(base, token, model, count, concurrency):
    codes = {}

    def one(_):
        return chat_call(base, token, model)[0]

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for status in pool.map(one, range(count)):
            codes[status] = codes.get(status, 0) + 1
    return codes


def service_config(base, token, service):
    status, body = http("GET", base + "/api/config/type/services/" + service, token=token)
    return (body or {}).get("data") or {} if status == 200 else {}


def put_service_config(base, token, service, adapter, load_balance, rate_limit=None):
    """该接口是全量覆盖（issue #1088），所以必须显式带上 adapter。"""
    payload = {"adapter": adapter, "loadBalance": load_balance, "rateLimit": rate_limit}
    status, body = http("PUT", base + "/api/config/type/services/" + service, payload, token=token)
    return status, body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--pass", dest="password", required=True)
    parser.add_argument("--mocks", default="http://127.0.0.1:9100,http://127.0.0.1:9101,http://127.0.0.1:9102")
    parser.add_argument("--mock-host", default="http://172.18.24.194",
                        help="网关容器访问桩用的宿主机地址（容器内不能用 127.0.0.1）")
    parser.add_argument("--mock-ports", default="9100,9101,9102")
    parser.add_argument("--model", default="qwen3:4b")
    parser.add_argument("--service", default="chat")
    parser.add_argument("--requests", type=int, default=30)
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--out", default="strategy-matrix-report.json")
    args = parser.parse_args()
    args.mock_ports = [int(x) for x in args.mock_ports.split(",") if x.strip()]

    base = args.base.rstrip("/")
    mocks = [m.strip().rstrip("/") for m in args.mocks.split(",") if m.strip()]
    token = login(base, args.user, args.password)
    print("登录成功，token %d 字符" % len(token))

    cfg = service_config(base, token, args.service)
    adapter = cfg.get("adapter") or "gpustack"
    original_lb = cfg.get("loadBalance") or {"type": "least-connections", "hashAlgorithm": "md5"}
    instances = cfg.get("instances") or []
    instance_id = instances[0].get("instanceId") if instances else None
    print("备份：adapter=%s lb=%s 实例数=%d" % (adapter, json.dumps(original_lb, ensure_ascii=False), len(instances)))

    report = {"adapter": adapter, "service": args.service, "model": args.model,
              "loadBalance": [], "rateLimit": [], "circuitBreaker": {}, "notes": []}

    created_ids = []
    original_instance_base_url = None
    primary_id = instance_id

    def prepare_instances():
        """让 3 个同名实例分别指向 3 个桩，负载均衡才有可分散的对象。"""
        nonlocal original_instance_base_url
        if not primary_id:
            raise SystemExit("没有可用实例，跳过矩阵")
        original_instance_base_url = instances[0].get("baseUrl")
        host = args.mock_host
        payload = {
            "name": args.model, "baseUrl": "%s:%s" % (host, args.mock_ports[0]),
            "path": "/v1/chat/completions", "weight": 1, "status": "ACTIVE", "adapter": adapter,
        }
        http("PUT", base + "/api/config/instance/%s/%s" % (args.service, primary_id), payload, token=token)
        for port in args.mock_ports[1:]:
            body = dict(payload, baseUrl="%s:%s" % (host, port))
            status, created = http("POST", base + "/api/config/instance/" + args.service, body, token=token)
            new_id = ((created or {}).get("data") or {}).get("instanceId")
            if status in (200, 201) and new_id:
                created_ids.append(new_id)
            print("新增实例 %s → %s" % (new_id, body["baseUrl"]))
        time.sleep(3)

    def restore_instances():
        for new_id in created_ids:
            http("DELETE", base + "/api/config/instance/%s/%s" % (args.service, new_id), token=token)
        if primary_id and original_instance_base_url:
            payload = {"name": args.model, "baseUrl": original_instance_base_url,
                       "path": "/v1/chat/completions", "weight": 1, "status": "ACTIVE", "adapter": adapter}
            http("PUT", base + "/api/config/instance/%s/%s" % (args.service, primary_id), payload, token=token)

    try:
        prepare_instances()
        # ---------- 1. 负载均衡矩阵 ----------
        strategies = [
            ("round-robin", "应分散到多个实例"),
            ("random", "应分散（单轮可能偏斜）"),
            ("least-connections", "并发下应分散；串行时连接数恒 0，靠 #1085 的 tie-break 轮转"),
            ("ip-hash", "单客户端 IP 固定命中同一实例，属预期"),
            ("consistent-hash", "同一 model 固定映射，属预期"),
        ]
        for strategy, expectation in strategies:
            for mock in mocks:
                mock_reset(mock)
            put_status, _ = put_service_config(base, token, args.service, adapter,
                                              {"type": strategy, "hashAlgorithm": "md5"})
            time.sleep(1.2)
            check = service_config(base, token, args.service)
            if (check.get("adapter") or None) != adapter:
                report["notes"].append("PUT 后 adapter 被清空（%s → %s），已中止" % (adapter, check.get("adapter")))
                raise SystemExit("abort: adapter 被 PUT 清空，见 issue #1088")
            before = [mock_total(m) for m in mocks]
            codes = hammer(base, token, args.model, args.requests, args.concurrency)
            time.sleep(0.6)
            after = [mock_total(m) for m in mocks]
            hits = [after[i] - before[i] for i in range(len(mocks))]
            entry = {"strategy": strategy, "expectation": expectation, "putStatus": put_status,
                     "hits": hits, "distinctInstances": sum(1 for h in hits if h > 0),
                     "statusCodes": codes, "adapterKept": True}
            report["loadBalance"].append(entry)
            print("LB %-17s hits=%-12s codes=%s" % (strategy, hits, codes))

        # ---------- 2. 限流算法矩阵（实例级，独立存储） ----------
        if instance_id:
            _s, rl_backup = http("GET", base + "/api/config/instance/%s/%s/rate-limit" % (args.service, instance_id), token=token)
            original_rl = (rl_backup or {}).get("data")
            print("原实例限流：%s" % json.dumps(original_rl, ensure_ascii=False)[:160])
            for algorithm in ("token-bucket", "leaky-bucket", "sliding-window"):
                body = {"instanceId": instance_id, "enabled": True, "algorithm": algorithm,
                        "capacity": 20, "rate": 5, "scope": "instance", "clientIpEnable": False}
                put_status, put_body = http(
                    "PUT", base + "/api/config/instance/%s/%s/rate-limit" % (args.service, instance_id), body, token=token)
                time.sleep(1.0)
                codes = hammer(base, token, args.model, 60, 20)
                report["rateLimit"].append({"algorithm": algorithm, "putStatus": put_status,
                                            "putSuccess": (put_body or {}).get("success"),
                                            "statusCodes": codes})
                print("限流 %-15s put=%s codes=%s" % (algorithm, put_status, codes))
                time.sleep(2.5)
            if original_rl:
                http("PUT", base + "/api/config/instance/%s/%s/rate-limit" % (args.service, instance_id),
                     original_rl, token=token)

        # ---------- 3. 熔断（实例级 + 对准承载流量的桩） ----------
        for mock in mocks:
            mock_reset(mock)
        before = [mock_total(m) for m in mocks]
        hammer(base, token, args.model, 6, 1)
        time.sleep(0.5)
        served = [mock_total(m) - before[i] for i, m in enumerate(mocks)]
        target = mocks[max(range(len(mocks)), key=lambda i: served[i])] if any(served) else mocks[0]
        report["circuitBreaker"]["served"] = served
        report["circuitBreaker"]["target"] = target
        print("承载流量的桩：%s（served=%s）" % (target, served))

        if instance_id:
            _s, cb_backup = http("GET", base + "/api/config/instance/%s/%s/circuit-breaker" % (args.service, instance_id), token=token)
            original_cb = (cb_backup or {}).get("data")
            http("PUT", base + "/api/config/instance/%s/%s/circuit-breaker" % (args.service, instance_id),
                 {"instanceId": instance_id, "enabled": True, "failureThreshold": 3,
                  "timeout": 5000, "successThreshold": 2}, token=token)
            mock_behavior(target, {"status": 500})
            log = []
            for i in range(15):
                codes = hammer(base, token, args.model, 2, 2)
                _s, states = http("GET", base + "/api/config/instance/circuit-breaker/states?serviceType=" + args.service, token=token)
                log.append({"round": i, "codes": codes, "states": (states or {}).get("data")})
                if any(k in (502, 503) for k in codes):
                    break
                time.sleep(0.4)
            report["circuitBreaker"]["openLog"] = log
            mock_behavior(target, {})
            recovery = []
            for i in range(10):
                time.sleep(2)
                codes = hammer(base, token, args.model, 2, 1)
                _s, states = http("GET", base + "/api/config/instance/circuit-breaker/states?serviceType=" + args.service, token=token)
                recovery.append({"round": i, "codes": codes, "states": (states or {}).get("data")})
                if codes.get(200, 0) > 0 and i > 1:
                    break
            report["circuitBreaker"]["recovery"] = recovery
            if original_cb:
                http("PUT", base + "/api/config/instance/%s/%s/circuit-breaker" % (args.service, instance_id),
                     original_cb, token=token)
            print("熔断：opened=%s recovered=%s" % (
                any(any(k in (502, 503) for k in r["codes"]) for r in log),
                any(r["codes"].get(200, 0) > 0 for r in recovery)))

    finally:
        restore_instances()
        put_service_config(base, token, args.service, adapter, original_lb, None)
        for mock in mocks:
            mock_behavior(mock, {})
        verify = service_config(base, token, args.service)
        report["restore"] = {"adapter": verify.get("adapter"),
                             "loadBalance": verify.get("loadBalance"),
                             "rateLimit": verify.get("rateLimit")}
        print("恢复：adapter=%s lb=%s" % (verify.get("adapter"),
                                         json.dumps(verify.get("loadBalance"), ensure_ascii=False)))
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2)
        print("报告写入 %s" % args.out)


if __name__ == "__main__":
    sys.exit(main())
