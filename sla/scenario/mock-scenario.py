#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""桩环境场景测试：负载均衡 / 熔断 / 限流 / 配额。

为什么要这个脚本：网关的「负载均衡、熔断、限流、配额」四件事，只有在**上游真的能通**的时候
才有意义。现场往往没有可用的模型后端（我们的远程测试机就是：localhost:11434 没有监听，
9 个实例全部 UNHEALTHY、全链路 503）。所以这里先用 mockupstream 桩把链路点亮，
再逐项验证，最后把改动恢复原样。

设计约束：
  1. 只读优先：先备份当前配置，任何写操作都有对应的逆操作，且放在 finally 里执行；
  2. 只改 baseUrl / 增删实例，不动 adapter、限流、熔断等策略；
  3. 判定用「桩侧计数」而不是响应头 —— 网关不保证透传上游响应头，但桩自己的 /__mock/stats 一定准；
  4. 每一步都打印实测数字，报告由数字拼出来，不做「看起来正常」的判断。

用法（在能访问网关的机器上跑，Python 3.8+，零第三方依赖）：
  python3 sla/scenario/mock-scenario.py \
    --base http://127.0.0.1:8080 \
    --user admin --pass 'xxx' \
    --mock-host 172.18.24.194 --ports 9100,9101,9102 \
    --out /opt/bundle/scenario

注意 --mock-host：网关容器里访问宿主要用宿主 IP（容器内 127.0.0.1 是容器自己）。
"""

import argparse
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime

TOKEN_HEADER = "X-Jairouter-Token"


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True, help="网关地址，例如 http://127.0.0.1:8080")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--pass", dest="password", required=True)
    ap.add_argument("--service", default="chat", help="要改成多实例的服务类型（默认 chat）")
    ap.add_argument("--model", default="qwen3:4b",
                    help="请求体里的 model，必须与实例 name 一致；负载均衡只在同名多实例之间发生")
    ap.add_argument("--mock-host", required=True, help="网关访问桩用的主机地址（容器内不能用 127.0.0.1）")
    ap.add_argument("--ports", default="9100,9101,9102")
    ap.add_argument("--out", default="/tmp/mock-scenario")
    ap.add_argument("--skip-quota", action="store_true")
    ap.add_argument("--skip-circuit", action="store_true")
    return ap.parse_args()


class Client(object):
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None
        self.log = []

    def call(self, method, path, body=None, token=None, timeout=30, extra_headers=None):
        url = self.base + path
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        t = token if token is not None else self.token
        if t:
            headers[TOKEN_HEADER] = t
        for k, v in (extra_headers or {}).items():
            headers[k] = v
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        started = time.time()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                status = resp.status
                resp_headers = dict(resp.headers)
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            status = exc.code
            resp_headers = dict(exc.headers)
        except Exception as exc:  # 连接失败等
            return {"status": 0, "error": str(exc), "ms": int((time.time() - started) * 1000),
                    "body": None, "headers": {}}
        elapsed = int((time.time() - started) * 1000)
        parsed = None
        text = raw.decode("utf-8", "replace")
        try:
            parsed = json.loads(text)
        except Exception:
            parsed = text
        return {"status": status, "body": parsed, "ms": elapsed, "headers": resp_headers}

    def login(self, user, password):
        r = self.call("POST", "/api/auth/jwt/login", {"username": user, "password": password}, token="")
        if r["status"] != 200 or not isinstance(r["body"], dict):
            raise RuntimeError("登录失败: HTTP %s %s" % (r["status"], r["body"]))
        self.token = r["body"]["data"]["token"]
        return self.token


def get_instances(client, service):
    r = client.call("GET", "/api/config/instance/%s" % service)
    if r["status"] != 200:
        raise RuntimeError("读取实例失败: %s" % r["body"])
    body = r["body"]
    return body.get("data") or []


def mock_stats(host, port):
    req = urllib.request.Request("http://%s:%s/__mock/stats" % (host, port))
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def mock_set(host, port, patch):
    data = json.dumps(patch).encode("utf-8")
    req = urllib.request.Request("http://%s:%s/__mock/behavior" % (host, port), data=data,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def mock_reset(host, port):
    req = urllib.request.Request("http://%s:%s/__mock/reset" % (host, port), data=b"{}",
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def chat_once(client, model, token=None, timeout=30):
    body = {"model": model, "messages": [{"role": "user", "content": "ping"}]}
    r = client.call("POST", "/v1/chat/completions", body, token=token, timeout=timeout)
    return r


def wait_healthy(client, service, expect_count, timeout_s=90):
    deadline = time.time() + timeout_s
    last = []
    while time.time() < deadline:
        last = get_instances(client, service)
        healthy = [i for i in last if i.get("healthStatus") == "HEALTHY"]
        if len(last) >= expect_count and len(healthy) >= expect_count:
            return last, healthy
        time.sleep(5)
    return last, [i for i in last if i.get("healthStatus") == "HEALTHY"]


def parallel_burst(client, model, total, concurrency, token=None):
    """并发打 total 个请求，返回状态码分布与耗时。"""
    results = []
    lock = threading.Lock()

    def worker(n):
        for _ in range(n):
            r = chat_once(client, model, token=token)
            with lock:
                results.append(r["status"])

    per = max(1, total // concurrency)
    threads = [threading.Thread(target=worker, args=(per,)) for _ in range(concurrency)]
    started = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - started
    dist = {}
    for s in results:
        dist[str(s)] = dist.get(str(s), 0) + 1
    return {"total": len(results), "elapsed_s": round(elapsed, 2),
            "rps": round(len(results) / elapsed, 1) if elapsed > 0 else 0, "status": dist}


def main():
    args = parse_args()
    ports = [p.strip() for p in args.ports.split(",") if p.strip()]
    client = Client(args.base)
    report = {"startedAt": datetime.now().isoformat(timespec="seconds"),
              "base": args.base, "service": args.service, "ports": ports, "steps": []}

    def step(name, **kw):
        entry = {"name": name, "at": datetime.now().isoformat(timespec="seconds")}
        entry.update(kw)
        report["steps"].append(entry)
        print("\n--- %s ---" % name)
        for k, v in kw.items():
            print("  %s: %s" % (k, json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else v))
        return entry

    os.makedirs(args.out, exist_ok=True)
    backup_file = os.path.join(args.out, "config-backup.json")
    created_ids = []
    original = {}
    try:
        client.login(args.user, args.password)

        # ---------- 0. 备份 ----------
        cur = client.call("GET", "/api/config/version/current")
        version = cur["body"]["data"] if cur["status"] == 200 and isinstance(cur["body"], dict) else None
        full = client.call("GET", "/api/config/version/%s" % version) if version is not None else None
        with open(backup_file, "w", encoding="utf-8") as f:
            json.dump({"version": version, "config": full["body"]["data"] if full and full["status"] == 200 else None},
                      f, ensure_ascii=False, indent=2)
        step("备份当前配置", version=version, file=backup_file,
             configKeys=len(full["body"]["data"]) if full and full["status"] == 200 and isinstance(full["body"]["data"], dict) else None)

        # ---------- 1. 切到桩：现有实例改 baseUrl + 新增实例 ----------
        before = get_instances(client, args.service)
        for inst in before:
            original[inst["instanceId"]] = inst.get("baseUrl")
        step("改造前实例", instances=[{"id": i["instanceId"][:8], "name": i.get("name"),
                                  "baseUrl": i.get("baseUrl"), "health": i.get("healthStatus")} for i in before])

        # 第一个桩：改现有实例
        if before:
            first = before[0]
            r = client.call("PUT", "/api/config/instance/%s/%s" % (args.service, first["instanceId"]),
                            {"baseUrl": "http://%s:%s" % (args.mock_host, ports[0])})
            step("改现有实例指向桩",
                 instanceId=first["instanceId"][:8], from_=original[first["instanceId"]],
                 to="http://%s:%s" % (args.mock_host, ports[0]), status=r["status"])

        # 其余桩：新增实例
        for idx, port in enumerate(ports[1:], start=2):
            r = client.call("POST", "/api/config/instance/%s" % args.service,
                            {"name": args.model,
                             "baseUrl": "http://%s:%s" % (args.mock_host, port),
                             "path": "/v1/chat/completions",
                             "weight": 1,
                             "status": "active",
                             "adapter": "gpustack"})
            new_id = None
            if r["status"] == 200 and isinstance(r["body"], dict) and r["body"].get("data"):
                new_id = r["body"]["data"].get("instanceId")
                created_ids.append(new_id)
            step("新增桩实例", port=port, status=r["status"], instanceId=(new_id or "")[:8],
                 body=None if r["status"] == 200 else r["body"])

        # ---------- 2. 等健康检查 ----------
        insts, healthy = wait_healthy(client, args.service, len(ports), timeout_s=100)
        step("等待实例变健康", total=len(insts), healthy=len(healthy), timeoutSeconds=100,
             detail=[{"name": i.get("name"), "health": i.get("healthStatus"), "err": i.get("errorMessage")}
                     for i in insts])
        if len(healthy) < len(ports):
            raise RuntimeError("实例没有全部变健康，后续测试无意义；健康状态=%s" %
                               [(i.get("name"), i.get("healthStatus")) for i in insts])

        # ---------- 3. 负载均衡 ----------
        for p in ports:
            mock_reset(args.mock_host, p)
        time.sleep(0.5)
        chat_ok = 0
        chat_status = {}
        for _ in range(30):
            r = chat_once(client, args.model)
            chat_status[str(r["status"])] = chat_status.get(str(r["status"]), 0) + 1
            if r["status"] == 200:
                chat_ok += 1
        per_port = {}
        for p in ports:
            s = mock_stats(args.mock_host, p)
            per_port[p] = s["byPath"].get("POST /v1/chat/completions", 0)
        chat_once(client, args.model)  # 让一个真实请求再出现一次，便于确认链路
        counts = list(per_port.values())
        lb_ok = chat_ok >= 25 and all(c > 0 for c in counts) and (max(counts) - min(counts)) <= max(3, sum(counts) // 3)
        step("负载均衡", requests=30, http_ok=chat_ok, http_status=chat_status,
             hitByPort=per_port, balanced=lb_ok,
             criterion="30 次请求每个桩都命中且最大/最小差不超过 1/3（least-connections 下允许有偏斜）")

        # ---------- 4. 限流 ----------
        burst = parallel_burst(client, args.model, 200, 20)
        limited = burst["status"].get("429", 0)
        step("限流（服务级 10 req/s，capacity 100）", **burst, http429=limited,
             limited_ok=limited > 0,
             note="chat 服务级限流配置 rate=10/s capacity=100；并发 20 打 200 个请求应出现 429")

        # ---------- 5. 熔断 ----------
        if not args.skip_circuit:
            target_port = ports[0]
            mock_set(args.mock_host, target_port, {"status": 500})
            time.sleep(0.5)
            for p in ports:
                mock_reset(args.mock_host, p) if p != target_port else None
            # 重新置 target 为 500（reset 会清空行为）
            mock_set(args.mock_host, target_port, {"status": 500})
            before_hits = {p: mock_stats(args.mock_host, p)["byPath"].get("POST /v1/chat/completions", 0) for p in ports}
            fails = 0
            for _ in range(30):
                r = chat_once(client, args.model)
                if r["status"] != 200:
                    fails += 1
            time.sleep(1)
            after_hits = {p: mock_stats(args.mock_host, p)["byPath"].get("POST /v1/chat/completions", 0) for p in ports}
            delta = {p: after_hits[p] - before_hits[p] for p in ports}
            states = client.call("GET", "/api/config/instance/circuit-breaker/states")
            step("熔断（桩 A 全量 500，失败阈值 5）",
                 failedRequests=fails, hitsDuringFault=delta,
                 circuitStates=(states["body"] or {}).get("data") if states["status"] == 200 else states,
                 open_ok=(delta.get(target_port, 0) < 10
                          and max([delta.get(p, 0) for p in ports if p != target_port] or [0]) > 0),
                 _hitsTotal=sum(delta.values()),
                 note="桩 A 持续 500；若熔断生效，后续请求应被跳过，A 的命中数应明显少于其余桩")

            mock_set(args.mock_host, target_port, {"status": 0})
            step("熔断恢复（桩 A 复位为正常）", restored=True,
                 note="默认 timeout=60s 后半开；本脚本不等待 60 秒，恢复行为由 SLA 的 doctor/后续请求验证")

        # ---------- 6. 配额 ----------
        if not args.skip_quota:
            q = client.call("POST", "/api/auth/api-keys",
                            {"description": "sla-quota-probe", "permissions": ["read", "write"],
                             "dailyRequestLimit": 5})
            key = None
            if q["status"] == 200 and isinstance(q["body"], dict) and q["body"].get("data"):
                key = q["body"]["data"].get("keyValue") or q["body"]["data"].get("key")
            if not key:
                step("配额", created=False, status=q["status"], body=q["body"])
            else:
                status_dist = {}
                for _ in range(8):
                    r = client.call("POST", "/v1/chat/completions",
                                    {"model": args.model,
                                     "messages": [{"role": "user", "content": "quota-probe"}]},
                                    token=None, extra_headers={"X-API-Key": key})
                    status_dist[str(r["status"])] = status_dist.get(str(r["status"]), 0) + 1
                usage = client.call("GET", "/api/auth/api-keys/%s/quota" % q["body"]["data"].get("keyId"))
                step("配额（dailyRequestLimit=5，打 8 次）", status=status_dist,
                     quotaRejected=status_dist.get("429", 0) + status_dist.get("403", 0),
                     quota_ok=(status_dist.get("429", 0) + status_dist.get("403", 0)) >= 1,
                     usage=usage["body"]["data"] if usage["status"] == 200 else usage["body"],
                     note="超过每日请求配额后应被拒绝，且 quota 用量应停在 5")

    except Exception as exc:
        step("异常中止", error=str(exc))
        report["error"] = str(exc)
    finally:
        # ---------- 7. 恢复 ----------
        try:
            restore = []
            for instance_id in created_ids:
                if not instance_id:
                    continue
                r = client.call("DELETE", "/api/config/instance/%s/%s" % (args.service, instance_id))
                restore.append({"deleted": instance_id[:8], "status": r["status"]})
            for instance_id, base_url in original.items():
                if not base_url:
                    continue
                r = client.call("PUT", "/api/config/instance/%s/%s" % (args.service, instance_id),
                                {"baseUrl": base_url})
                restore.append({"restored": instance_id[:8], "to": base_url, "status": r["status"]})
            after = get_instances(client, args.service)
            step("恢复配置", actions=restore,
                 instancesAfter=[{"id": i["instanceId"][:8], "name": i.get("name"),
                                  "baseUrl": i.get("baseUrl")} for i in after],
                 restored_ok=len(after) == len(original))
        except Exception as exc:
            step("恢复失败（需要人工介入）", error=str(exc))
            report["restoreError"] = str(exc)

    report["finishedAt"] = datetime.now().isoformat(timespec="seconds")
    out = os.path.join(args.out, "scenario-report.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("\n报告已写入 %s" % out)
    return 0 if not report.get("error") else 1


if __name__ == "__main__":
    sys.exit(main())
