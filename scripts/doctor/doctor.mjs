#!/usr/bin/env node
/**
 * jairouter-doctor —— 只读诊断工具。
 *
 * 存在理由：健康检查说「好」、请求说「坏」、日志不说为什么，是这类网关最难查的一类故障。
 * 本项目已经踩过三次（adapter 未传递 / Netty 解析器与 glibc 不一致 / 熔断器遮蔽真实原因），
 * 共同点是「探测手段与实际调用路径不是同一条」。
 *
 * 所以这里全部用**与真实请求相同的路径**去测：
 *   - 实例配置取的是 API 真正返回的那份（不是配置文件）
 *   - 连通性分三层（DNS / TCP / HTTP），能区分「名字解析不了」「端口不通」「应用层不通」
 *   - 真实调用用一次最小 chat 请求，失败时打印响应体的 message（线上排障的关键线索）
 *
 * 只读：不发写请求、不改配置、不触发复位。
 *
 * 用法：
 *   node scripts/doctor/doctor.mjs --base-url http://127.0.0.1:8080 --token <JWT>
 *   node scripts/doctor/doctor.mjs --base-url ... --token ... --no-chat --format json
 *   JAIR_TOKEN=... node scripts/doctor/doctor.mjs --base-url ...
 *
 * 退出码：0 全通过 / 1 有警告 / 2 有失败
 */

import dns from 'node:dns/promises';
import net from 'node:net';
import process from 'node:process';

const args = parseArgs(process.argv.slice(2));
const BASE = (args['base-url'] || process.env.JAIR_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const TOKEN = args.token || process.env.JAIR_TOKEN || '';
const FORMAT = args.format || 'text';
const RUN_CHAT = args['no-chat'] !== true;

const results = [];

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (!a.startsWith('--')) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      out[key] = next;
      i += 1;
    } else {
      out[key] = true;
    }
  }
  return out;
}

function record(level, area, message, hint) {
  results.push({ level, area, message, hint: hint || null });
}

/** 带鉴权头的 GET；不抛异常，统一返回 { status, body, ms, error }。 */
async function getJson(path, { auth = true, timeoutMs = 15000 } = {}) {
  const started = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${BASE}${path}`, {
      headers: auth && TOKEN ? { 'X-Jairouter-Token': TOKEN } : {},
      signal: controller.signal,
    });
    const text = await res.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }
    return { status: res.status, body, ms: Date.now() - started, error: null };
  } catch (e) {
    return { status: null, body: null, ms: Date.now() - started, error: e };
  } finally {
    clearTimeout(timer);
  }
}

/** 从 baseUrl 里拆出 host / port / scheme，无法解析时返回 null。 */
function splitBaseUrl(baseUrl) {
  try {
    const u = new URL(baseUrl);
    const port = u.port ? Number(u.port) : u.protocol === 'https:' ? 443 : 80;
    return { host: u.hostname, port, scheme: u.protocol.replace(':', ''), path: u.pathname };
  } catch {
    return null;
  }
}

/** TCP 建连测试：能连上返回耗时，否则返回错误。 */
function tcpProbe(host, port, timeoutMs = 5000) {
  return new Promise((resolve) => {
    const started = Date.now();
    const socket = net.connect({ host, port });
    const done = (ok, error) => {
      socket.destroy();
      resolve({ ok, ms: Date.now() - started, error: error || null });
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => done(true));
    socket.once('timeout', () => done(false, new Error(`连接超时 ${timeoutMs}ms`)));
    socket.once('error', (e) => done(false, e));
  });
}

// ---------------------------------------------------------------- 检查项

async function checkService() {
  const health = await getJson('/actuator/health', { auth: false });
  if (health.status !== 200) {
    record('FAIL', '服务面', `/actuator/health 不可达（${health.status ?? health.error?.message}）`, '确认端口与 base-url');
    return false;
  }
  const status = health.body?.status;
  if (status === 'UP') record('OK', '服务面', `/actuator/health = UP (${health.ms}ms)`);
  else record('FAIL', '服务面', `/actuator/health = ${status}`, '看 health 的 components 明细');

  const info = await getJson('/actuator/info', { auth: false });
  if (info.status === 200) {
    const v = info.body?.build?.version || info.body?.app?.version || 'unknown';
    record('OK', '服务面', `版本 ${v}`);
  }
  return true;
}

/** 服务类型：默认 chat，可用 --services chat,embedding 覆盖。 */
function serviceTypes() {
  const raw = args.services || 'chat';
  return String(raw)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

async function listInstances() {
  const out = [];
  for (const type of serviceTypes()) {
    const inst = await getJson(`/api/config/instance/${type}`);
    if (inst.status !== 200) {
      record('WARN', '实例配置', `读不到 ${type} 的实例（HTTP ${inst.status}）`, `确认服务类型名正确，或用 --services 指定`);
      continue;
    }
    const list = Array.isArray(inst.body?.data) ? inst.body.data : [inst.body?.data].filter(Boolean);
    for (const i of list) out.push({ serviceType: type, ...i });
  }
  return out;
}

async function checkInstances() {
  const instances = await listInstances();
  if (instances.length === 0) {
    record('WARN', '实例配置', '一个实例都没读到', '确认 /api/config/service/list 有数据、token 有读权限');
    return [];
  }
  for (const i of instances) {
    const label = `${i.serviceType}/${i.name}`;
    const missing = ['name', 'baseUrl', 'path'].filter((k) => !i[k]);
    if (missing.length) {
      record('FAIL', '实例配置', `${label} 缺 ${missing.join('/')}`, '网关会对这类实例直接 502');
      continue;
    }
    if (!i.adapter || i.adapterResolved === false) {
      record('FAIL', '实例配置', `${label} adapter=${i.adapter || '空'}（未解析）`, i.adapterWarning || '在 model.services.<type>.adapter 或实例级 adapter 配置');
    } else {
      record('OK', '实例配置', `${label} adapter=${i.adapter} status=${i.status}`);
    }
    if (i.status && i.status !== 'active') {
      record('WARN', '实例配置', `${label} status=${i.status}`, '非 active 的实例不会被选中');
    }
  }
  return instances;
}

async function checkConnectivity(instances) {
  for (const i of instances) {
    const label = `${i.serviceType}/${i.name}`;
    const target = splitBaseUrl(i.baseUrl);
    if (!target) {
      record('FAIL', '出站连通性', `${label} baseUrl 无法解析为 URL: ${i.baseUrl}`);
      continue;
    }

    // 第一层：DNS
    const isIpLiteral = /^\d{1,3}(\.\d{1,3}){3}$/.test(target.host) || target.host.includes(':');
    if (isIpLiteral) {
      record('OK', '出站连通性', `${label} 目标是 IP 字面量，跳过 DNS`);
    } else {
      try {
        const addresses = await dns.lookup(target.host, { all: true });
        record('OK', '出站连通性', `${label} DNS ${target.host} → ${addresses.map((a) => a.address).join(', ')}`);
      } catch (e) {
        record(
          'WARN',
          '出站连通性',
          `${label} DNS 解析失败: ${target.host}（${e.code || e.message}）`,
          '若下面「真实调用」通过，说明 doctor 与网关不在同一网络，这一项不代表网关；否则见 issue #1014',
        );
        continue;
      }
    }

    // 第二层：TCP
    const tcp = await tcpProbe(target.host, target.port);
    if (!tcp.ok) {
      record('FAIL', '出站连通性', `${label} TCP ${target.host}:${target.port} 不通（${tcp.error?.code || tcp.error?.message}）`);
      continue;
    }
    record('OK', '出站连通性', `${label} TCP ${target.host}:${target.port} ${tcp.ms}ms`);

    // 第三层：HTTP（用 GET 探一次上游，不强求一定是 /health）
    const started = Date.now();
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      const res = await fetch(`${i.baseUrl.replace(/\/+$/, '')}/health`, { signal: controller.signal });
      clearTimeout(timer);
      record('OK', '出站连通性', `${label} HTTP ${res.status} (${Date.now() - started}ms)`);
    } catch {
      record('WARN', '出站连通性', `${label} HTTP 探活失败（上游可能没有 /health，不一定是问题）`);
    }
  }
}

async function checkCircuitBreakers() {
  const res = await getJson('/api/config/instance/circuit-breaker/status');
  if (res.status !== 200) return;
  const list = Array.isArray(res.body?.data) ? res.body.data : [];
  for (const b of list) {
    const state = b.state || b.circuitState;
    if (state && state !== 'CLOSED') {
      record('WARN', '熔断器', `${b.instanceId || b.name} ${state}`, '熔断器打开会把真实失败原因遮蔽成 503，先看日志里的下游错误');
    }
  }
  if (list.length > 0) record('OK', '熔断器', `${list.length} 个实例，无 OPEN`);
}

let resolvedModel = null;

async function checkChat() {
  if (!RUN_CHAT || !TOKEN) {
    record('WARN', '真实调用', '跳过（无 token 或 --no-chat）');
    return;
  }
  const started = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 60000);
  try {
    const res = await fetch(`${BASE}/v1/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Jairouter-Token': TOKEN },
      body: JSON.stringify({
        model: args.model || resolvedModel || undefined,
        messages: [{ role: 'user', content: 'ping' }],
        max_tokens: 1,
      }),
      signal: controller.signal,
    });
    const text = await res.text();
    const ms = Date.now() - started;
    if (res.status === 200 && text.includes('"choices"')) {
      record('OK', '真实调用', `chat 200 (${ms}ms)`);
    } else {
      let message = text;
      try {
        const j = JSON.parse(text);
        message = j?.error?.message || j?.message || text;
      } catch {
        /* 保留原文 */
      }
      record('FAIL', '真实调用', `chat ${res.status} (${ms}ms): ${truncate(message, 200)}`, '真实链路失败，先看这条 message，再对照日志');
    }
  } catch (e) {
    record('FAIL', '真实调用', `chat 异常: ${e.message}`);
  } finally {
    clearTimeout(timer);
  }
}

function truncate(s, n) {
  const str = String(s ?? '');
  return str.length > n ? `${str.slice(0, n)}…` : str;
}

// ---------------------------------------------------------------- 输出

const ICON = { OK: ' OK ', WARN: 'WARN', FAIL: 'FAIL' };

function printText() {
  const width = Math.max(...results.map((r) => r.message.length), 20);
  for (const r of results) {
    console.log(`[${ICON[r.level] || r.level}] ${r.area.padEnd(6)} ${r.message}`);
    if (r.hint) console.log(`${' '.repeat(15)}└ ${r.hint}`);
  }
  const fail = results.filter((r) => r.level === 'FAIL').length;
  const warn = results.filter((r) => r.level === 'WARN').length;
  console.log('');
  console.log(`合计 ${results.length} 项：${fail} 失败 / ${warn} 警告 / ${results.length - fail - warn} 通过`);
  return fail > 0 ? 2 : warn > 0 ? 1 : 0;
}

function printJson() {
  console.log(JSON.stringify({ baseUrl: BASE, results }, null, 2));
  return results.some((r) => r.level === 'FAIL') ? 2 : results.some((r) => r.level === 'WARN') ? 1 : 0;
}

// ---------------------------------------------------------------- main

async function main() {
  if (!TOKEN) {
    record('WARN', '认证', '未提供 token，配置类检查会跳过', '--token <JWT> 或 JAIR_TOKEN');
  }
  const alive = await checkService();
  if (!alive) {
    process.exit(FORMAT === 'json' ? printJson() : printText());
  }
  const instances = await checkInstances();
  resolvedModel = args.model || (instances.find((i) => i.name)?.name ?? null);
  await checkConnectivity(instances);
  await checkCircuitBreakers();
  await checkChat();

  const code = FORMAT === 'json' ? printJson() : printText();
  process.exit(code);
}

main().catch((e) => {
  console.error(`doctor 自身异常: ${e.stack || e.message}`);
  process.exit(2);
});
