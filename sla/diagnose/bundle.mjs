#!/usr/bin/env node
/**
 * Incident bundle —— 把「事故当时是什么状态」固定成一份可带走的快照。
 *
 * 事故排查最贵的是时间：等你去复现时连接池已恢复、熔断器已半开、日志已滚动。
 * 这个脚本在拿到现场的第一时间跑一次，把能读到的状态都落到一个目录里，
 * 并额外生成一份 `summary.md`：先看那一页，再决定往下挖哪个文件。
 *
 * 设计约束：
 *   - 只读。不发写请求、不改配置、不触发复位、不触发 GC。
 *   - 单项失败不影响整体（写 null + error，继续往下）。
 *   - 默认脱敏：不落 API Key 明文、不落 JWT、不落上游 Authorization 头原文。
 *   - 采集面按「分析问题时真正要回答的问题」组织，不是为了把接口都打一遍。
 *
 * 用法:
 *   node sla/diagnose/bundle.mjs --base http://127.0.0.1:8080 --user admin --pass ***
 *   node sla/diagnose/bundle.mjs ... --out ./bundle-2026-11-07T14-20
 *   node sla/diagnose/bundle.mjs ... --no-latency      # 跳过延迟采样（现场很卡时用）
 *   node sla/diagnose/bundle.mjs ... --logs-file /var/log/jairouter.log   # 非容器部署
 *   node sla/diagnose/bundle.mjs ... --journal-unit jairouter             # systemd 部署
 *
 * 采集项与「能回答什么问题」的对应关系：
 *   health.json         服务面是否活着、哪个组件 DOWN
 *   info.json           版本 / profile / tracing 运行时
 *   instances.json      每个服务类型下有哪些实例、adapter、healthStatus、熔断状态
 *   stats.json          实例/模型计数（与 instances 交叉验证，暴露「统计漏算」类问题）
 *   metrics.txt         全量 Prometheus 文本（可离线用 grep 复盘）
 *   metrics-key.txt     只保留排障常用的那些样本
 *   metrics-summary.json 解析后的关键数值（堆/GC/线程/连接池/事件循环/错误/审计耗时）
 *   latency.json        分路径的 p50/p95（区分「服务慢」与「某条路径慢」）
 *   config.json         安全与配额相关的开关（判断「为什么这个功能没生效」）
 *   audit.json          最近审计事件（谁在什么时候做了什么）
 *   logs.json           日志里的 ERROR/WARN 摘要（--logs-file / --journal-unit / docker 三种来源）
 *   summary.md          一页纸结论 + 下一步该看哪个文件
 */

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { connect as netConnect } from 'node:net';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { api, joinUrl, login, unwrap } from '../lib/client.mjs';

const execFileAsync = promisify(execFile);

function parseArgs(argv) {
  const out = {
    base: 'http://127.0.0.1:8080',
    user: process.env.JAIR_USER || 'admin',
    pass: process.env.JAIR_PASS || '',
    out: null,
    timeoutMs: 20000,
    latencySamples: 5,
    noLatency: false,
    container: process.env.JAIR_CONTAINER || 'jairouter',
    logsFile: process.env.JAIR_LOGS_FILE || null,
    journalUnit: process.env.JAIR_JOURNAL_UNIT || null,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--base') out.base = argv[++i];
    else if (arg === '--user') out.user = argv[++i];
    else if (arg === '--pass') out.pass = argv[++i];
    else if (arg === '--out') out.out = argv[++i];
    else if (arg === '--timeout') out.timeoutMs = Number(argv[++i]);
    else if (arg === '--samples') out.latencySamples = Number(argv[++i]);
    else if (arg === '--no-latency') out.noLatency = true;
    else if (arg === '--container') out.container = argv[++i];
    else if (arg === '--logs-file') out.logsFile = argv[++i];
    else if (arg === '--journal-unit') out.journalUnit = argv[++i];
  }
  return out;
}

/** 只保留「看状态用得上的」指标，完整文本另存。 */
const KEY_METRICS = [
  /^jvm_(memory|gc|threads|classes|buffer)/,
  /^hikaricp_/,
  /^process_(cpu|start|uptime)/,
  /^system_load/,
  /^jairouter_/,
  /^http_server_requests/,
  /^spring_data_repository/,
];

function pickKeyMetrics(text) {
  const lines = text.split('\n');
  const kept = lines.filter((line) => line.startsWith('#') || KEY_METRICS.some((re) => re.test(line)));
  return kept.join('\n');
}

/** 解析 Prometheus 文本 → { name: [{labels, value}] }。 */
function parseMetrics(text) {
  const samples = {};
  for (const line of text.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+(\S+)$/);
    if (!match) continue;
    const [, name, labelText, valueText] = match;
    const value = Number(valueText);
    if (!Number.isFinite(value)) continue;
    const labels = {};
    if (labelText) {
      for (const pair of labelText.slice(1, -1).matchAll(/([a-zA-Z_][a-zA-Z0-9_]*)="((?:[^"\\]|\\.)*)"/g)) {
        labels[pair[1]] = pair[2];
      }
    }
    if (!samples[name]) samples[name] = [];
    samples[name].push({ labels, value });
  }
  return samples;
}

/**
 * 取某个指标的全部样本之和；不存在返回 null（区分「0」与「没采到」）。
 *
 * labelMatch 用来把口径限定住：`jvm_memory_used_bytes` 同时导出 heap 与 nonheap 若干内存池，
 * 不限定 `area="heap"` 会把 Metaspace / CodeCache 一起算进堆占用（实测 401MB vs 真实 112MB）。
 */
function sumOf(samples, name, labelMatch = {}) {
  const list = samples[name];
  if (!list || list.length === 0) return null;
  const matched = list.filter(
    (item) => Object.entries(labelMatch).every(([k, v]) => item.labels[k] === v),
  );
  if (matched.length === 0) return null;
  return matched.reduce((acc, item) => acc + item.value, 0);
}

/** 取带指定标签的样本值。 */
function valueOf(samples, name, labelMatch = {}) {
  const list = samples[name];
  if (!list) return null;
  const hit = list.find((item) => Object.entries(labelMatch).every(([k, v]) => item.labels[k] === v));
  return hit ? hit.value : null;
}

/**
 * 取带指定标签的样本里「正数的最大值」。
 *
 * G1 的 Eden/Survivor 内存池 `max` 会导出 -1（未设上限），直接取第一个匹配样本会拿到 -1，
 * 进而算出 `-36121744800%` 这种荒谬的堆占用率。堆上限的正确来源是 Old Gen 那一项的
 * 5.37e8，所以这里过滤掉非正数再取最大。
 */
function maxPositive(samples, name, labelMatch = {}) {
  const list = samples[name];
  if (!list || list.length === 0) return null;
  const values = list
    .filter((item) => Object.entries(labelMatch).every(([k, v]) => item.labels[k] === v))
    .map((item) => item.value)
    .filter((value) => typeof value === 'number' && Number.isFinite(value) && value > 0);
  return values.length ? Math.max(...values) : null;
}

/** 按某个标签聚合计数（例：http_server_requests_seconds_count 按 status 求和）。 */
function countByLabel(samples, name, label) {
  const list = samples[name];
  if (!list || list.length === 0) return null;
  const out = {};
  for (const item of list) {
    const key = item.labels[label] ?? 'unknown';
    out[key] = Math.round((out[key] ?? 0) + item.value);
  }
  return out;
}

/**
 * 取 4xx/5xx 里次数最多的若干条。
 *
 * 「错误总数」只能说明有事发生，「哪个状态码 + 哪条路径」才能定位问题；
 * 例如 401 多说明调用方没带凭证，502/503 多说明上游或实例不可用。
 */
function topErrors(samples, name, limit) {
  const list = samples[name];
  if (!list || list.length === 0) return [];
  const out = {};
  for (const item of list) {
    const status = Number(item.labels.status);
    if (!Number.isFinite(status) || status < 400) continue;
    const key = `${status} ${item.labels.uri ?? '?'}`;
    out[key] = (out[key] ?? 0) + item.value;
  }
  return Object.entries(out)
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([key, count]) => ({ key, count: Math.round(count) }));
}

/**
 * 从实例配置里提取去重后的 host:port（实例的 baseUrl 就是上游地址）。
 *
 * 实测最常见的现场问题是「网关活着、所有实例 UNHEALTHY」——根因通常在网络这一层：
 * DNS 解析失败、端口没开、或者容器里写了 localhost 而服务在宿主机。
 */
function collectUpstreamTargets(instances) {
  const targets = new Map();
  for (const list of Object.values(instances ?? {})) {
    if (!Array.isArray(list)) continue;
    for (const item of list) {
      const baseUrl = item?.baseUrl;
      if (typeof baseUrl !== 'string' || baseUrl.length === 0) continue;
      try {
        const url = new URL(baseUrl);
        const port = url.port ? Number(url.port) : (url.protocol === 'https:' ? 443 : 80);
        const key = `${url.hostname}:${port}`;
        if (!targets.has(key)) targets.set(key, { hostname: url.hostname, port });
      } catch {
        // 非法 baseUrl 先跳过：它本身会在 instances.json 里可见。
      }
    }
  }
  return [...targets.entries()];
}

/** 单次 TCP 连接探测（不建 HTTP 会话，只看端口是否可连）。 */
function tcpProbe(host, port, timeoutMs) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    let settled = false;
    const socket = netConnect({ host, port });
    const finish = (result) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve({ ...result, elapsedMs: Date.now() - startedAt });
    };
    socket.setTimeout(timeoutMs, () => finish({ ok: false, error: 'ETIMEOUT' }));
    socket.once('connect', () => finish({ ok: true }));
    socket.once('error', (error) => finish({ ok: false, error: error?.code ?? String(error) }));
  });
}

async function probeUpstreams(instances, timeoutMs) {
  const targets = collectUpstreamTargets(instances);
  const out = {};
  for (const [key, target] of targets) {
    // eslint-disable-next-line no-await-in-loop -- 目标通常只有 1~3 个，串行更可读
    out[key] = await tcpProbe(target.hostname, target.port, timeoutMs);
  }
  return out;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return null;
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[index];
}

/** 导出用的脱敏：去掉 token / secret 一类字段，headers 只留键名，baseUrl 里的 userinfo 也抹掉。 */
function sanitize(value) {
  if (Array.isArray(value)) return value.map(sanitize);
  if (!value || typeof value !== 'object') return value;
  const out = {};
  for (const [key, item] of Object.entries(value)) {
    if (/^(token|password|secret|apiKey|key|authorization|headers)$/i.test(key)) {
      // headers 是上游鉴权头的载体：保留键名（知道配了哪些头有用），值一律抹掉。
      if (key === 'headers' && item && typeof item === 'object') {
        out[key] = Object.fromEntries(Object.keys(item).map((name) => [name, '***']));
      } else {
        out[key] = '***';
      }
    } else if (key === 'baseUrl' && typeof item === 'string') {
      out[key] = item.replace(/\/\/[^/@]+@/, '//***@');
    } else {
      out[key] = sanitize(item);
    }
  }
  return out;
}

async function scrapeMetrics(base, timeoutMs) {
  const res = await fetch(joinUrl(base, '/actuator/prometheus'), {
    headers: { Accept: 'text/plain' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

/** 分路径延迟采样：每条约 n 次，给 min/p50/p95/max。 */
async function measureLatency(base, token, paths, samples) {
  const results = [];
  for (const path of paths) {
    const durations = [];
    const statuses = [];
    for (let i = 0; i < samples; i++) {
      const started = Date.now();
      try {
        const res = await fetch(joinUrl(base, path), {
          headers: token ? { 'X-Jairouter-Token': token } : {},
          signal: AbortSignal.timeout(10000),
        });
        statuses.push(res.status);
        await res.arrayBuffer();
      } catch (error) {
        statuses.push(error?.name === 'TimeoutError' ? 'timeout' : 'error');
      }
      durations.push(Date.now() - started);
    }
    const sorted = [...durations].sort((a, b) => a - b);
    results.push({
      path,
      samples: durations.length,
      statuses: [...new Set(statuses)],
      minMs: sorted[0] ?? null,
      p50Ms: percentile(sorted, 50),
      p95Ms: percentile(sorted, 95),
      maxMs: sorted[sorted.length - 1] ?? null,
    });
  }
  return results;
}

/**
 * 采集日志里的 ERROR/WARN 摘要。
 *
 * 按「显式指定 → 自动探测」的顺序尝试，任何一步读不到都继续下一步，最后才报错：
 *   1. `--logs-file <path>`     服务器上已存在的日志文件（非容器部署、日志已轮转到文件时最通用）
 *   2. `--journal-unit <unit>`  `journalctl -u <unit>`
 *   3. `docker logs <container>` 容器部署（原行为，默认）
 *   4. `journalctl -u jairouter.service` 容器读不到时兜底（不少人用 systemd 裸跑 jar）
 *
 * 只读、失败不抛错：读不到就把每个来源的失败原因串起来交给 summary 显示。
 */
async function collectLogs({ container, logsFile, journalUnit }) {
  const attempts = [];
  if (logsFile) {
    attempts.push({ source: 'file', target: logsFile, run: async () => readFile(logsFile, 'utf8') });
  }
  if (journalUnit) {
    attempts.push({
      source: 'journalctl',
      target: journalUnit,
      run: () => execFileAsync('journalctl', ['-u', journalUnit, '-n', '400', '--no-pager'], { timeout: 15000 }),
    });
  }
  attempts.push({
    source: 'docker',
    target: container,
    run: () => execFileAsync('docker', ['logs', '--tail', '400', container], { timeout: 15000 }),
  });
  attempts.push({
    source: 'journalctl',
    target: 'jairouter.service',
    run: () => execFileAsync('journalctl', ['-u', 'jairouter.service', '-n', '400', '--no-pager'], { timeout: 15000 }),
  });

  const failures = [];
  for (const attempt of attempts) {
    try {
      const result = await attempt.run();
      const text = typeof result === 'string' ? result : result?.stdout ?? '';
      // `docker logs` 把 stderr 混在 stdout 里；journalctl 空结果也可能是「无日志」而不是失败
      if (!text.trim()) {
        failures.push(`${attempt.source}:${attempt.target} → 空输出`);
        continue;
      }
      return summarizeLogText(attempt.source, attempt.target, text);
    } catch (error) {
      failures.push(`${attempt.source}:${attempt.target} → ${error?.message ?? String(error)}`);
    }
  }
  return { ok: false, error: failures.join('; ') };
}

/** 从一份日志文本里统计 ERROR/WARN/异常行，并保留最后 40 条可疑行。 */
function summarizeLogText(source, target, text) {
  const lines = String(text).split('\n').filter(Boolean);
  const interesting = lines.filter((line) => /\b(ERROR|WARN|Exception|Caused by)\b/.test(line));
  const counts = {};
  for (const line of interesting) {
    const level = /\bERROR\b/.test(line) ? 'ERROR' : /\bWARN\b/.test(line) ? 'WARN' : 'OTHER';
    counts[level] = (counts[level] || 0) + 1;
  }
  return { ok: true, source, target, tailLines: lines.length, counts, sample: interesting.slice(-40) };
}

async function collect(args, token) {
  const read = async (path) => {
    try {
      return { ok: true, value: unwrap(await api(args.base, path, { token, timeoutMs: args.timeoutMs })) };
    } catch (error) {
      return { ok: false, error: error?.message ?? String(error) };
    }
  };

  const files = {};

  files['health.json'] = await read('/actuator/health');
  files['info.json'] = await read('/actuator/info');

  // 服务类型必须问网关，不能硬编码：实际类型是 chat/embedding/rerank/tts/stt/imgGen/imgEdit，
  // 早期版本这里写的 image/audio 并不存在，会漏掉图像两个类型。
  const typeList = await read('/api/config/type/services');
  const serviceTypes = typeList.ok && Array.isArray(typeList.value) && typeList.value.length > 0
    ? typeList.value
    : ['chat', 'embedding', 'rerank', 'tts', 'stt'];
  files['service-types.json'] = { ok: true, value: serviceTypes };

  const instances = {};
  const circuitBreakers = {};
  const serviceConfigs = {};
  for (const type of serviceTypes) {
    const result = await read(`/api/config/instance/${type}`);
    if (result.ok) instances[type] = sanitize(result.value);
    const breaker = await read(`/api/config/instance/circuit-breaker/states?serviceType=${encodeURIComponent(type)}`);
    if (breaker.ok) circuitBreakers[type] = sanitize(breaker.value);
    // 服务级配置（含 sticky）：必须和实例一起采，否则「为什么不分流」无法回答
    const cfg = await read(`/api/config/type/services/${encodeURIComponent(type)}`);
    if (cfg.ok) serviceConfigs[type] = sanitize(cfg.value);
  }
  files['instances.json'] = { ok: true, value: instances };
  if (Object.keys(serviceConfigs).length > 0) files['service-config.json'] = { ok: true, value: serviceConfigs };
  if (Object.keys(circuitBreakers).length > 0) files['circuit-breakers.json'] = { ok: true, value: circuitBreakers };

  files['stats.json'] = await read('/api/models/stats');
  try {
    files['outbound.json'] = { ok: true, value: await probeUpstreams(instances, 3000) };
  } catch (error) {
    files['outbound.json'] = { ok: false, error: error?.message ?? String(error) };
  }
  files['config-version.json'] = await read('/api/config/version/current');
  files['rate-limiter.json'] = await read('/api/rate-limiter/summary');
  files['quota-usage.json'] = await read('/api/monitoring/quota/status');
  files['audit.json'] = await read('/api/security/audit/extended/api-keys');

  let metricsText = null;
  try {
    metricsText = await scrapeMetrics(args.base, args.timeoutMs);
    files['metrics.txt'] = { ok: true, value: metricsText };
    files['metrics-key.txt'] = { ok: true, value: pickKeyMetrics(metricsText) };
  } catch (error) {
    files['metrics.txt'] = { ok: false, error: error?.message ?? String(error) };
  }

  if (metricsText) {
    const samples = parseMetrics(metricsText);
    const heapUsed = sumOf(samples, 'jvm_memory_used_bytes', { area: 'heap' });
    const heapMax = maxPositive(samples, 'jvm_memory_max_bytes', { area: 'heap' });
    const gcCount = sumOf(samples, 'jvm_gc_pause_seconds_count');
    const gcTime = sumOf(samples, 'jvm_gc_pause_seconds_sum');
    const infoValue = files['info.json'].ok ? files['info.json'].value : null;
    // 产品把自身运行状态挂在 info 的 tracing 段下；profile 只有那里能读到。
    const tracingInfo = infoValue?.tracing ?? null;
    const summary = {
      version: infoValue?.build?.version ?? null,
      profile: tracingInfo?.config?.serviceNamespace ?? infoValue?.config?.serviceNamespace ?? null,
      jvm: {
        heapUsedBytes: heapUsed,
        heapMaxBytes: heapMax,
        heapUsedPercent: heapUsed != null && heapMax ? Number(((heapUsed / heapMax) * 100).toFixed(2)) : null,
        threadsLive: valueOf(samples, 'jvm_threads_live_threads'),
        classesLoaded: valueOf(samples, 'jvm_classes_loaded_classes'),
        gcCount,
        gcPauseSecondsTotal: gcTime != null ? Number(gcTime.toFixed(3)) : null,
        gcPauseAvgMs: gcCount ? Number(((gcTime / gcCount) * 1000).toFixed(2)) : null,
      },
      process: {
        cpuUsage: valueOf(samples, 'process_cpu_usage'),
        startTimeMs: valueOf(samples, 'process_start_time_seconds') != null
          ? valueOf(samples, 'process_start_time_seconds') * 1000 : null,
        uptimeSeconds: valueOf(samples, 'process_uptime_seconds'),
        systemLoad1m: valueOf(samples, 'system_load_average_1m'),
      },
      http: {
        hikariActive: sumOf(samples, 'hikaricp_connections_active'),
        hikariIdle: sumOf(samples, 'hikaricp_connections_idle'),
        hikariPending: sumOf(samples, 'hikaricp_connections_pending'),
        hikariMax: sumOf(samples, 'hikaricp_connections_max'),
        hikariAcquireSecondsTotal: sumOf(samples, 'hikaricp_connections_acquire_seconds_sum'),
      },
      gateway: {
        eventLoopDelayMaxMs: valueOf(samples, 'jairouter_eventloop_delay_seconds_max') != null
          ? Number((valueOf(samples, 'jairouter_eventloop_delay_seconds_max') * 1000).toFixed(3)) : null,
        eventLoopDelaySamples: sumOf(samples, 'jairouter_eventloop_delay_seconds_count'),
        outboundInflight: sumOf(samples, 'jairouter_outbound_requests_inflight'),
        errorsTotal: sumOf(samples, 'jairouter_errors_total'),
        auditWriteCount: sumOf(samples, 'jairouter_audit_event_write_duration_seconds_count'),
        auditWriteAvgMs: (() => {
          const count = sumOf(samples, 'jairouter_audit_event_write_duration_seconds_count');
          const total = sumOf(samples, 'jairouter_audit_event_write_duration_seconds_sum');
          return count && total != null ? Number(((total / count) * 1000).toFixed(2)) : null;
        })(),
        tracingMemoryMb: valueOf(samples, 'tracing_memory_usage'),
        quotaCounterRedisEnabled: valueOf(samples, 'jairouter_quota_counter_redis_enabled'),
        // 产品自报的 tracing 运行态：与 prometheus 是两条独立通道，可用来对账，
        // 也覆盖了 prometheus 看不到的「采集丢弃率」——丢弃率高时现场看到的是「tracing 正常」，
        // 实际大量 span 没落库，事后复盘会缺证据。
        tracingRuntime: tracingInfo?.runtime
          ? {
            processedCount: tracingInfo.runtime.processing?.processedCount ?? null,
            droppedCount: tracingInfo.runtime.processing?.droppedCount ?? null,
            dropRate: tracingInfo.runtime.processing?.dropRate ?? null,
            queueSize: tracingInfo.runtime.processing?.queueSize ?? null,
            cacheSize: tracingInfo.runtime.memory?.cacheSize ?? null,
            pressureLevel: tracingInfo.runtime.memory?.pressureLevel ?? null,
            selfReportedHeapUsagePercent: tracingInfo.runtime.memory?.heapUsagePercent ?? null,
          }
          : null,
      },
      metricSampleCount: Object.keys(samples).length,
      statusCounts: countByLabel(samples, 'http_server_requests_seconds_count', 'status'),
      errorUris: topErrors(samples, 'http_server_requests_seconds_count', 8),
    };
    files['metrics-summary.json'] = { ok: true, value: summary };
  }

  if (!args.noLatency) {
    const latency = await measureLatency(
      args.base,
      token,
      ['/actuator/health', '/admin/', '/api/models/stats', '/v1/models'],
      args.latencySamples,
    );
    files['latency.json'] = { ok: true, value: latency };
  }

  files['logs.json'] = {
    ok: true,
    value: await collectLogs({
      container: args.container,
      logsFile: args.logsFile,
      journalUnit: args.journalUnit,
    }),
  };

  return { files, serviceTypes };
}

/** 把采集到的数值翻译成「一页纸结论」。只陈述事实与阈值，不做未见证据的推断。 */
function buildSummary({ meta, files, serviceTypes }) {
  const lines = [];
  const get = (name) => (files[name]?.ok ? files[name].value : null);

  lines.push(`# 现场快照 ${meta.collectedAt}`);
  lines.push('');
  lines.push(`目标：${meta.base}　版本：${get('metrics-summary.json')?.version ?? '未知'}　`
    + `profile：${get('metrics-summary.json')?.profile ?? '未知'}`);
  lines.push('');

  const health = get('health.json');
  if (health) {
    lines.push(`## 服务面：${health.status}`);
    for (const [name, component] of Object.entries(health.components || {})) {
      lines.push(`- ${name}: ${component.status}`);
    }
    lines.push('');
  } else {
    lines.push('## 服务面：/actuator/health 不可读（先解决这一点，其余结论都要打折）');
    lines.push('');
  }

  const stats = get('stats.json');
  const instances = get('instances.json');
  if (stats || instances) {
    lines.push('## 实例与模型');
    if (stats) {
      lines.push(`- 总计：服务 ${stats.totalServices ?? '?'} 个 / 实例 ${stats.totalInstances ?? '?'} 个 / 模型 ${stats.totalModels ?? '?'} 个`);
      const declared = JSON.stringify(stats.instancesByService ?? {});
      const actual = {};
      for (const type of serviceTypes) {
        const list = instances?.[type];
        actual[type] = Array.isArray(list) ? list.length : 0;
      }
      lines.push(`- /api/models/stats 报的实例数：${declared}`);
      lines.push(`- /api/config/instance/<type> 实际条数：${JSON.stringify(actual)}`);
      const mismatch = serviceTypes.filter((type) => (stats.instancesByService?.[type] ?? -1) !== actual[type]);
      if (mismatch.length > 0) {
        lines.push(`- **不一致的服务类型**：${mismatch.join(', ')}（统计口径与实际实例条数不同，先查这个）`);
      }
    }
    if (instances) {
      for (const type of serviceTypes) {
        const list = instances[type];
        if (!Array.isArray(list) || list.length === 0) {
          lines.push(`- ${type}: 0 个实例`);
          continue;
        }
        const detail = list.map((item) => {
          const name = item.name ?? item.instanceId ?? '?';
          const status = item.healthStatus ?? item.status ?? '?';
          const adapter = item.adapterType ?? item.adapter ?? '?';
          // 健康检查的失败原因就在 errorMessage 里（例：HTTP 探活失败: ConnectException），
          // 不展示它等于把「上游为什么不通」这条第一手线索丢掉。
          const reason = item.errorMessage ? ` — ${String(item.errorMessage).slice(0, 80)}` : '';
          return `${name} [${status}/${adapter}]${reason}`;
        });
        lines.push(`- ${type}: ${detail.join('、')}`);
      }
      const allInstances = serviceTypes.flatMap((type) => (Array.isArray(instances[type]) ? instances[type] : []));
      const unhealthyCount = allInstances.filter(
        (item) => String(item.healthStatus ?? '').toUpperCase() === 'UNHEALTHY',
      ).length;
      if (allInstances.length > 0 && unhealthyCount === allInstances.length) {
        lines.push(`- **全部 ${allInstances.length} 个实例 UNHEALTHY**：网关自身是活的，先查上游模型服务是否可达——`
          + '这是业务链路不通的首要原因，不要先去调网关参数');
      }
    }
    lines.push('');
  }

  // 负载均衡与会话粘性必须一起看：粘性开启时负载均衡策略根本不会被调用，
  // 只看「策略配的是什么」会得出「策略不生效」的错误结论（issue #1095）。
  const serviceConfigs = get('service-config.json')?.value;
  if (instances) {
    const stickyLines = [];
    const overriddenTypes = [];
    for (const type of serviceTypes) {
      const list = Array.isArray(instances[type]) ? instances[type] : [];
      if (list.length === 0) continue;
      const healthy = list.filter(
        (item) => String(item.healthStatus ?? '').toUpperCase() === 'HEALTHY',
      ).length;
      const stickyEnabled = serviceConfigs?.[type]?.sticky?.enabled !== false;
      if (stickyEnabled) {
        stickyLines.push(`- ${type}: 实例 ${list.length} 个（健康 ${healthy}），**会话粘性开启（缺省值）**`
          + (healthy > 1
            ? ' ⇒ 同一主体 + 同一模型的请求恒落同一实例，负载均衡策略不会生效'
            : ''));
        if (healthy > 1) overriddenTypes.push(type);
      } else {
        stickyLines.push(`- ${type}: 实例 ${list.length} 个（健康 ${healthy}），粘性已关闭 ⇒ 按策略分流`);
      }
    }
    if (stickyLines.length > 0) {
      lines.push('## 负载均衡与会话粘性');
      lines.push(...stickyLines);
      if (overriddenTypes.length > 0) {
        lines.push(`- **${overriddenTypes.join(', ')} 的负载均衡被会话粘性覆盖**：多实例却不分流，`
          + '与负载均衡策略配置无关。关闭方式：'
          + 'PUT /api/config/type/services/<type> {"sticky":{"enabled":false}}（3.8.3 起支持），'
          + '或在 yml 设 model.services.<type>.sticky.enabled=false 后重启');
      }
      lines.push('');
    }
  }

  const metrics = get('metrics-summary.json');
  if (metrics) {
    lines.push('## 运行时水位');
    const heapText = metrics.jvm.heapUsedPercent != null
      ? `${metrics.jvm.heapUsedPercent}%（${metrics.jvm.heapUsedBytes ?? '?'} / ${metrics.jvm.heapMaxBytes ?? '?'} 字节）`
      : `已用 ${metrics.jvm.heapUsedBytes ?? '?'} 字节（堆上限读不到，通常是内存池 max=-1）`;
    lines.push(`- 堆：${heapText}`);
    lines.push(`- 线程 ${metrics.jvm.threadsLive ?? '?'} / 类 ${metrics.jvm.classesLoaded ?? '?'}`
      + ` / GC ${metrics.jvm.gcCount ?? '?'} 次，累计 ${metrics.jvm.gcPauseSecondsTotal ?? '?'}s（均 ${metrics.jvm.gcPauseAvgMs ?? '?'}ms）`);
    lines.push(`- CPU ${metrics.process.cpuUsage ?? '?'}　load1 ${metrics.process.systemLoad1m ?? '?'}　uptime ${metrics.process.uptimeSeconds ?? '?'}s`);
    lines.push(`- 连接池：active ${metrics.http.hikariActive ?? '?'} / idle ${metrics.http.hikariIdle ?? '?'}`
      + ` / pending ${metrics.http.hikariPending ?? '?'} / max ${metrics.http.hikariMax ?? '?'}`);
    lines.push(`- 事件循环最大延迟 ${metrics.gateway.eventLoopDelayMaxMs ?? '?'}ms（样本 ${metrics.gateway.eventLoopDelaySamples ?? '?'}）`);
    lines.push(`- 出站 in-flight ${metrics.gateway.outboundInflight ?? '?'}　errors_total ${metrics.gateway.errorsTotal ?? '?'}`);
    lines.push(`- 审计写入 ${metrics.gateway.auditWriteCount ?? '?'} 次，平均 ${metrics.gateway.auditWriteAvgMs ?? '?'}ms`);
    lines.push(`- tracing 内存 ${metrics.gateway.tracingMemoryMb ?? '?'}MB　配额计数器走 Redis：${metrics.gateway.quotaCounterRedisEnabled === 1 ? '是' : '否'}`);
    const tracingRuntime = metrics.gateway.tracingRuntime;
    if (tracingRuntime) {
      lines.push(`- tracing 处理：已处理 ${tracingRuntime.processedCount ?? '?'} / 丢弃 ${tracingRuntime.droppedCount ?? '?'}`
        + `（丢弃率 ${tracingRuntime.dropRate ?? '?'}）　队列 ${tracingRuntime.queueSize ?? '?'}　压力 ${tracingRuntime.pressureLevel ?? '?'}`);
    }
    lines.push('');

    const warnings = [];
    if (metrics.jvm.heapUsedPercent != null && metrics.jvm.heapUsedPercent > 80) {
      warnings.push(`堆使用率 ${metrics.jvm.heapUsedPercent}%（>80%），先看是不是缓存/追踪无界增长`);
    }
    if (metrics.http.hikariPending > 0) {
      warnings.push(`有 ${metrics.http.hikariPending} 个请求在等数据库连接，连接池已见过载`);
    }
    if (metrics.gateway.eventLoopDelayMaxMs != null && metrics.gateway.eventLoopDelayMaxMs > 100) {
      warnings.push(`事件循环最大延迟 ${metrics.gateway.eventLoopDelayMaxMs}ms（>100ms），说明有阻塞操作跑在事件循环上`);
    }
    const tracingDropRate = metrics.gateway.tracingRuntime?.dropRate;
    const tracingDropRatePercent = tracingDropRate != null ? Number.parseFloat(String(tracingDropRate)) : Number.NaN;
    if (!Number.isNaN(tracingDropRatePercent) && tracingDropRatePercent > 10) {
      warnings.push(`tracing 采集丢弃率 ${tracingDropRate}（>10%）：现场看着「tracing 正常」，`
        + '但大量 span 没落库，事后复盘会缺证据；先确认采样率与缓冲配置是不是预期');
    }
    if (warnings.length > 0) {
      lines.push('## 需要先看的信号');
      for (const warning of warnings) lines.push(`- ${warning}`);
      lines.push('');
    }

    if (metrics.statusCounts) {
      lines.push('## HTTP 请求分布（进程启动以来累计）');
      const byStatus = Object.entries(metrics.statusCounts).sort((a, b) => b[1] - a[1]);
      lines.push(`- ${byStatus.map(([code, count]) => `${code}: ${count}`).join('　')}`);
      if (Array.isArray(metrics.errorUris) && metrics.errorUris.length > 0) {
        lines.push('- 报错最多的路径：');
        for (const item of metrics.errorUris) lines.push(`  - ${item.key} × ${item.count}`);
      }
      lines.push('');
    }
  }

  const circuitBreakers = get('circuit-breakers.json');
  if (circuitBreakers && typeof circuitBreakers === 'object') {
    const entries = Object.entries(circuitBreakers);
    const nonEmpty = entries.filter(([, value]) => value && typeof value === 'object' && Object.keys(value).length > 0);
    if (entries.length > 0 && nonEmpty.length === 0) {
      lines.push('## 熔断器');
      lines.push(`- 端点返回了 ${entries.length} 个服务类型，但每条都是空对象：说明没有实例登记进熔断器（或该端点形状已变）。`);
      lines.push('- **不要把「空」读成「全部闭合」**；先用 `circuit-breakers.json` 原文确认，再看实例是否真的在承担流量。');
      lines.push('');
    }
  }

  const outbound = get('outbound.json');
  if (outbound && typeof outbound === 'object') {
    const entries = Object.entries(outbound);
    if (entries.length > 0) {
      lines.push('## 上游连通性（从采集端做 TCP 探测）');
      for (const [target, result] of entries) {
        lines.push(result.ok
          ? `- ${target}: 可连（${result.elapsedMs}ms）`
          : `- ${target}: **不可连**（${result.error}，${result.elapsedMs}ms）`);
      }
      const unreachable = entries.filter(([, result]) => !result.ok).map(([target]) => target);
      if (unreachable.length > 0) {
        lines.push(`- 结论：${unreachable.join('、')} 从采集端连不上，实例 UNHEALTHY 与 503 由此而来；`
          + '若网关跑在容器里，确认这里看到的是容器网络视角（容器内 localhost ≠ 宿主机）');
      }
      lines.push('');
    }
  }

  const latency = get('latency.json');
  if (Array.isArray(latency) && latency.length > 0) {
    lines.push('## 分路径延迟（同一台机器、连续采样）');
    for (const item of latency) {
      lines.push(`- ${item.path}: p50 ${item.p50Ms ?? '?'}ms / p95 ${item.p95Ms ?? '?'}ms / max ${item.maxMs ?? '?'}ms`
        + `　状态 ${item.statuses.join(',')}`);
    }
    lines.push('');
  }

  const logs = get('logs.json');
  if (logs) {
    if (logs.ok) {
      const where = logs.source === 'file'
        ? `文件 ${logs.target}`
        : logs.source === 'journalctl'
          ? `journalctl -u ${logs.target}`
          : `容器 ${logs.target}`;
      lines.push(`## 日志（${where}，最近 ${logs.tailLines} 行）`);
      lines.push(`- ERROR ${logs.counts.ERROR ?? 0} / WARN ${logs.counts.WARN ?? 0} / 其它异常行 ${logs.counts.OTHER ?? 0}`);
      lines.push('');
    } else {
      lines.push('## 日志：读不到');
      lines.push(`- 尝试过的来源与失败原因：${logs.error}`);
      lines.push('- 非容器部署请用 `--logs-file /path/to/jairouter.log` 或 `--journal-unit jairouter` 指定来源；'
        + '容器部署可用 `--container <名字>` 指定容器名');
      lines.push('');
    }
  }

  lines.push('## 下一步');
  lines.push('- 先看本文件里标了「不一致」「需要先看的信号」的条目，它们指向具体文件。');
  lines.push('- `metrics-key.txt` 用于 grep 具体指标；`metrics.txt` 是完整原文。');
  lines.push('- `instances.json` 看实例配置（headers 已抹成 ***）；`health.json` 看组件级状态。');

  return lines.join('\n');
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.pass) {
    console.error('缺少密码：用 --pass 或设 JAIR_PASS');
    process.exitCode = 2;
    return;
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outDir = args.out ?? `bundle-${stamp}`;
  await mkdir(outDir, { recursive: true });

  const meta = {
    collectedAt: new Date().toISOString(),
    base: args.base,
    note: '只读采集；token/密钥/上游 Authorization 均未落盘；headers 只保留键名',
  };

  let token = null;
  try {
    token = await login(args.base, args.user, args.pass);
    meta.authenticated = true;
  } catch (error) {
    meta.authenticated = false;
    meta.authError = error?.message ?? String(error);
  }

  const { files, serviceTypes } = token ? await collect(args, token) : { files: {}, serviceTypes: [] };
  await writeFile(join(outDir, 'meta.json'), JSON.stringify(meta, null, 2), 'utf8');

  const summaryLines = [];
  for (const [name, payload] of Object.entries(files)) {
    const body = typeof payload.value === 'string'
      ? payload.value
      : JSON.stringify(payload.value ?? payload, null, 2);
    await writeFile(join(outDir, name), body, 'utf8');
    summaryLines.push(`${payload.ok ? '[ OK ]' : '[FAIL]'} ${name}${payload.ok ? '' : `  ${payload.error}`}`);
  }

  const summaryMd = buildSummary({ meta, files, serviceTypes });
  await writeFile(join(outDir, 'summary.md'), summaryMd, 'utf8');

  await writeFile(join(outDir, 'README.txt'), [
    `快照时间 ${meta.collectedAt}`,
    `目标 ${args.base}`,
    '',
    '先看 summary.md（一页纸结论），再按它指向的文件往下挖：',
    '  metrics-summary.json  解析后的关键数值（堆/GC/连接池/事件循环/错误/审计耗时）',
    '  metrics-key.txt       排障常用指标原文',
    '  instances.json        实例配置（headers 已抹成 ***）',
    '  latency.json          分路径 p50/p95',
    '  logs.json             日志 ERROR/WARN 摘要（--logs-file / --journal-unit / docker）',
    '',
    '这份快照只读采集，不含 token 与密钥。',
  ].join('\n'), 'utf8');

  console.log(`已写入 ${outDir}`);
  for (const line of summaryLines) console.log(`  ${line}`);
  console.log('\n--- summary.md ---\n');
  console.log(summaryMd);
  process.exitCode = 0;
}

main().catch((error) => {
  console.error(error?.stack ?? String(error));
  process.exitCode = 2;
});
