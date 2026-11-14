#!/usr/bin/env node
/**
 * verify-post-upgrade — 升级/发版后的行为回归检查。
 *
 * 与 preflight 的分工：
 *   preflight 回答「这层契约还在不在」（只读，几秒钟）
 *   verify    回答「修过的行为有没有回来」（会写配置，需要显式授权）
 *
 * 这里的每一项都对应一个曾经被真实踩到、且**只看契约端点发现不了**的缺陷。
 * 每项都写清了「修复前会怎样」，所以它可以当验收证据用，也可以当回归守卫用。
 *
 * 设计约束（与 sla/ 其它工具一致）：
 *   - 只读项默认执行；任何写操作必须显式 `--allow-writes`；
 *   - 写过的配置在 finally 里恢复，恢复失败要报出来，不能静默；
 *   - 拿不到前提条件时 SKIP 并说明原因，不要假装通过。
 */

import { parseArgs } from 'node:util';

import { api, GatewayError, joinUrl, login, scrapeMetrics, unwrap } from '../lib/client.mjs';

const DEFAULT_MIN_HEAP_MB = 520; // 3.8.2 之前镜像固定 -Xmx512m

function emit(line = '') {
  process.stdout.write(line + '\n');
}

function emitErr(line) {
  process.stderr.write(line + '\n');
}

function short(value, limit = 120) {
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  if (text === undefined) return 'undefined';
  return text.length <= limit ? text : text.slice(0, limit) + '…';
}

class Context {
  constructor(options) {
    this.base = options.base;
    this.token = options.token;
    this.expect = options.expect;
    this.minHeapMb = options.minHeapMb;
    this.allowWrites = options.allowWrites;
    this.serviceType = options.serviceType;
    this.timeoutMs = options.timeoutMs;
    this.changes = [];
    this.warnings = [];
  }

  /** 读管理面接口，返回解包后的 data。 */
  async read(path, options = {}) {
    return unwrap(await api(this.base, path, { token: this.token, timeoutMs: this.timeoutMs, ...options }));
  }

  /** 写管理面接口；自动登记到变更清单以便恢复。 */
  async write(path, body) {
    return unwrap(
      await api(this.base, path, {
        method: 'PUT',
        body,
        token: this.token,
        timeoutMs: this.timeoutMs,
      }),
    );
  }

  noteChange(description, restore) {
    this.changes.push({ description, restore });
  }
}

const checks = [
  {
    id: 'version.build',
    title: 'actuator/info 暴露 build.version',
    needsWrite: false,
    async run(ctx) {
      const info = await api(ctx.base, '/actuator/info', { timeoutMs: ctx.timeoutMs });
      const version = info?.build?.version ?? null;
      if (!version) {
        // 3.8.2 之前的镜像只有 tracing 自己的 serviceVersion=1.0.0，那不是产品版本。
        return { ok: false, detail: '无 build 段；SLA 工具链无法自动确认版本（升级前行为）' };
      }
      if (ctx.expect && version !== ctx.expect) {
        return { ok: false, detail: `build.version=${version}，期望 ${ctx.expect}` };
      }
      return { ok: true, detail: `build.version=${version}` };
    },
  },
  {
    id: 'heap.container-ratio',
    title: '堆上限跟随容器内存',
    needsWrite: false,
    async run(ctx) {
      const metrics = await scrapeMetrics(ctx.base, { timeoutMs: ctx.timeoutMs });
      // G1 的 Eden / Survivor 内存池 max 是 -1，必须过滤掉。
      let heapMax = 0;
      for (const [name, value] of metrics) {
        if (!name.startsWith('jvm_memory_max_bytes') || !name.includes('area="heap"')) continue;
        if (value > heapMax) heapMax = value;
      }
      if (!heapMax) return { skip: true, detail: 'prometheus 未提供 jvm_memory_max_bytes' };
      const mb = Math.round(heapMax / 1024 / 1024);
      const ok = mb >= ctx.minHeapMb;
      return {
        ok,
        detail:
          `${mb} MB（阈值 ${ctx.minHeapMb} MB）` +
          (ok ? '' : '；升级前 -Xmx 覆盖了 MaxRAMPercentage，堆上限被钉死'),
      };
    },
  },
  {
    id: 'static.missing-asset-traceable',
    title: '缺失静态资源留下可追溯的痕迹',
    needsWrite: false,
    async run(ctx) {
      const res = await fetch(joinUrl(ctx.base, '/admin/assets/__verify_missing__.js'), {
        headers: { Accept: '*/*' },
        signal: AbortSignal.timeout(ctx.timeoutMs),
      });
      const type = res.headers.get('content-type') ?? '(none)';
      const text = await res.text().catch(() => '');
      if (res.status !== 404) {
        return { ok: false, detail: `HTTP ${res.status} ${type}；缺失产物不该返回 200` };
      }
      // 3.8.1 及更早：404 但响应体为空、没有 content-type、日志里也没有这一笔，
      // 排障时只能看到 Micrometer 聚合出的「/admin/** 404 ×N」，无法还原是哪个 chunk。
      const traceable = type.includes('text/plain') && text.includes('静态资源不存在');
      return {
        ok: traceable,
        detail:
          `404 ${type}，body ${text.length} 字节` +
          (traceable ? '（响应体与日志都能定位到具体路径）' : '；升级前这里既无响应体也无日志'),
      };
    },
  },
  {
    id: 'actuator.threaddump',
    title: '生产环境暴露 threaddump',
    needsWrite: false,
    async run(ctx) {
      try {
        const res = await fetch(joinUrl(ctx.base, '/actuator/threaddump'), {
          // /actuator/** 需要管理员身份：不带 token 会得到 401，会被误判成「没暴露」
          headers: { Accept: 'application/json', 'X-Jairouter-Token': ctx.token },
          signal: AbortSignal.timeout(ctx.timeoutMs),
        });
        if (res.ok) {
          const body = await res.json().catch(() => null);
          const threads = Array.isArray(body?.threads) ? body.threads.length : null;
          return { ok: true, detail: threads === null ? 'HTTP 200' : `HTTP 200，${threads} 个线程` };
        }
        return {
          ok: false,
          detail: `HTTP ${res.status}；进程卡死时没有 threaddump 就没有现场证据`,
        };
      } catch (error) {
        return { ok: false, detail: `请求失败: ${error.message}` };
      }
    },
  },
  {
    id: 'config.incremental-put',
    title: '服务配置写入是增量合并',
    needsWrite: true,
    async run(ctx) {
      if (!ctx.allowWrites) {
        return { skip: true, detail: '需要 --allow-writes（会改写服务配置，随后自动恢复）' };
      }
      const before = await ctx.read(`/api/config/type/services/${ctx.serviceType}`);
      const adapterBefore = before?.adapter ?? null;
      const lbBefore = before?.loadBalance ?? null;
      if (adapterBefore === null) {
        return { skip: true, detail: `${ctx.serviceType} 读不到 adapter，无法对比` };
      }
      await ctx.write(`/api/config/type/services/${ctx.serviceType}`, {
        loadBalance: { type: 'round-robin' },
      });
      ctx.noteChange(`${ctx.serviceType}.loadBalance 恢复为 ${short(lbBefore)}`, async () => {
        await ctx.write(`/api/config/type/services/${ctx.serviceType}`, {
          loadBalance: lbBefore ?? { type: 'least-connections' },
        });
      });
      const after = await ctx.read(`/api/config/type/services/${ctx.serviceType}`);
      const adapterAfter = after?.adapter ?? null;
      const ok = adapterAfter === adapterBefore;
      return {
        ok,
        detail: `PUT 只传 loadBalance 后 adapter ${adapterBefore} → ${adapterAfter}` +
          (ok ? '' : '；升级前会把未提交的字段清空'),
      };
    },
  },
  {
    id: 'sticky.toggleable',
    title: '会话粘性能通过配置接口关闭',
    needsWrite: true,
    async run(ctx) {
      if (!ctx.allowWrites) {
        return { skip: true, detail: '需要 --allow-writes（会改写 sticky 配置，随后自动恢复）' };
      }
      const before = await ctx.read(`/api/config/type/services/${ctx.serviceType}`);
      const stickyBefore = before?.sticky ?? null;
      try {
        await ctx.write(`/api/config/type/services/${ctx.serviceType}`, { sticky: { enabled: false } });
      } catch (error) {
        return { ok: false, detail: `PUT sticky 失败: ${short(error.message ?? error)}` };
      }
      // 增量语义下无法把块删掉，所以原本没有 sticky 块时恢复成 { enabled: true }。
      // 这与「缺省即开启」等价，不会改变路由行为。
      ctx.noteChange(
        `${ctx.serviceType}.sticky 恢复为 ${short(stickyBefore)}`,
        async () => {
          await ctx.write(`/api/config/type/services/${ctx.serviceType}`, {
            sticky: stickyBefore ?? { enabled: true },
          });
        },
      );
      const after = await ctx.read(`/api/config/type/services/${ctx.serviceType}`);
      const enabledAfter = after?.sticky?.enabled;
      const ok = enabledAfter === false;
      return {
        ok,
        detail: `PUT sticky.enabled=false 后回读 ${JSON.stringify(after?.sticky ?? null)}` +
          (ok ? '' : '；升级前 UpgradeServiceConfigRequest 没有 sticky 字段，写不进去（issue #1095）'),
      };
    },
  },
  {
    id: 'quota.enforced',
    title: '按 Key 的额度真正拦截',
    needsWrite: true,
    async run(ctx) {
      if (!ctx.allowWrites) {
        return { skip: true, detail: '需要 --allow-writes（会创建一个临时 Key，随后删除）' };
      }
      let created = null;
      try {
        created = await ctx.read('/api/auth/api-keys', {
          method: 'POST',
          body: {
            name: `sla-verify-${Date.now()}`,
            serviceType: ctx.serviceType,
            dailyRequestLimit: 2,
          },
        });
      } catch (error) {
        return { skip: true, detail: `创建临时 Key 失败: ${short(error.message ?? error)}` };
      }
      const keyId = created?.keyId ?? created?.id ?? null;
      const apiKey = created?.keyValue ?? created?.apiKey ?? created?.key ?? null;
      if (!apiKey) {
        return { skip: true, detail: '创建成功但响应里没有明文 Key（可能只返回一次）' };
      }
      ctx.noteChange(`删除临时 Key ${keyId ?? '(无 id)'}`, async () => {
        if (keyId) await ctx.read(`/api/auth/api-keys/${keyId}`, { method: 'DELETE' });
      });

      const statuses = [];
      for (let i = 0; i < 3; i += 1) {
        const res = await fetch(joinUrl(ctx.base, '/v1/chat/completions'), {
          method: 'POST',
          // 网关注册的 API Key 请求头是 X-API-Key（默认值，可配 jairouter.security.api-key.header-name），
          // 不是 OpenAI SDK 默认的 Authorization: Bearer —— 用 Bearer 会得到 401。
          headers: { 'Content-Type': 'application/json', 'X-API-Key': apiKey },
          body: JSON.stringify({
            model: 'verify-model',
            messages: [{ role: 'user', content: 'quota probe' }],
          }),
          signal: AbortSignal.timeout(ctx.timeoutMs),
        }).catch(() => null);
        statuses.push(res ? res.status : 0);
      }
      const blocked = statuses.includes(429);
      if (!blocked && statuses.every((s) => s === 401)) {
        return { skip: true, detail: `Key 未被接受（${statuses.join('/')}），无法判断配额是否生效` };
      }
      return {
        ok: blocked,
        detail: `额度 2、连打 3 次，状态=${statuses.join('/')}` +
          (blocked ? '' : '；升级前账本默认关闭，额度从不检查'),
      };
    },
  },
  {
    id: 'lb.spread',
    title: '负载均衡在多个实例间分散',
    needsWrite: false,
    async run(ctx) {
      const instances = await ctx.read(`/api/config/instance/${ctx.serviceType}`);
      const list = Array.isArray(instances) ? instances : [];
      const healthy = list.filter((item) => item?.healthStatus === 'HEALTHY');
      if (healthy.length < 2) {
        return {
          skip: true,
          detail: `${ctx.serviceType} 健康实例 ${healthy.length} 个（需要 ≥2 个同名实例才能观察分散）`,
        };
      }
      // 先判「负载均衡是不是根本没被调用」：会话粘性默认开启（issue #1095），
      // 亲和键 apiKeyId|serviceType|modelName 恒定，此时无论配哪种策略都不会分流。
      // 这一步必须放在发请求之前，否则会把「粘性覆盖」误判成「负载均衡坏了」。
      const serviceConfig = await ctx.read(`/api/config/type/services/${ctx.serviceType}`);
      const stickyEnabled = serviceConfig?.sticky?.enabled !== false;
      if (stickyEnabled) {
        return {
          ok: false,
          detail:
            `负载均衡被会话粘性覆盖：健康实例 ${healthy.length} 个，但 ${ctx.serviceType}.sticky 未显式关闭` +
            `（服务端默认开启，亲和键 apiKeyId|serviceType|modelName）⇒ 同一主体 + 同一模型的请求` +
            `恒落在同一实例，配置的负载均衡策略不会生效。` +
            `关闭方式：PUT /api/config/type/services/${ctx.serviceType} {"sticky":{"enabled":false}}` +
            `（3.8.3 起支持），或在 yml 设 model.services.${ctx.serviceType}.sticky.enabled=false 后重启`,
        };
      }
      const model = healthy[0]?.name;
      if (!model) return { skip: true, detail: '健康实例没有 name，无法按模型名路由' };
      const hits = new Map();
      for (let i = 0; i < 30; i += 1) {
        const res = await fetch(joinUrl(ctx.base, '/v1/chat/completions'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Jairouter-Token': ctx.token },
          body: JSON.stringify({ model, messages: [{ role: 'user', content: 'lb probe' }] }),
          signal: AbortSignal.timeout(ctx.timeoutMs),
        }).catch(() => null);
        if (!res) {
          hits.set('(network)', (hits.get('(network)') ?? 0) + 1);
          continue;
        }
        const text = await res.text().catch(() => '');
        let tag = null;
        try {
          tag = JSON.parse(text)?.mock_instance ?? null;
        } catch {
          tag = null;
        }
        const key = tag ?? `http-${res.status}`;
        hits.set(key, (hits.get(key) ?? 0) + 1);
      }
      const instancesHit = [...hits.keys()].filter((k) => !k.startsWith('http-') && k !== '(network)');
      const ok = instancesHit.length >= 2;
      return {
        ok,
        detail:
          `30 次分布=${short(Object.fromEntries(hits))}` +
          (ok
            ? '；粘性已关闭，策略生效'
            : '；粘性已关闭但仍未分散 ⇒ 这是负载均衡自身的问题（查 least-connections 的起点轮转、' +
              'round-robin 的计数器 key 是否每次新建），不是粘性造成的'),
      };
    },
  },
];

async function main() {
  const { values } = parseArgs({
    options: {
      base: { type: 'string', default: 'http://127.0.0.1:8080' },
      user: { type: 'string', default: 'admin' },
      pass: { type: 'string' },
      token: { type: 'string' },
      expect: { type: 'string' },
      'min-heap-mb': { type: 'string', default: String(DEFAULT_MIN_HEAP_MB) },
      'allow-writes': { type: 'boolean', default: false },
      'service-type': { type: 'string', default: 'chat' },
      only: { type: 'string' },
      timeout: { type: 'string', default: '15000' },
      json: { type: 'boolean', default: false },
    },
    allowPositionals: false,
  });

  const ctx = new Context({
    base: values.base,
    expect: values.expect ?? null,
    minHeapMb: Number(values['min-heap-mb']),
    allowWrites: values['allow-writes'],
    serviceType: values['service-type'],
    timeoutMs: Number(values.timeout),
  });

  try {
    ctx.token = values.token ?? (await login(ctx.base, values.user, values.pass ?? ''));
  } catch (error) {
    emitErr(`登录失败: ${error.message}`);
    return 2;
  }

  const wanted = values.only ? new Set(values.only.split(',').map((s) => s.trim())) : null;
  const selected = checks.filter((check) => !wanted || wanted.has(check.id));

  const results = [];
  for (const check of selected) {
    let row;
    try {
      row = await check.run(ctx);
    } catch (error) {
      row = { ok: false, detail: `抛错: ${error.message}` };
    }
    const status = row.skip ? 'SKIP' : row.ok ? 'PASS' : 'FAIL';
    results.push({ id: check.id, title: check.title, status, detail: row.detail });
    if (!values.json) emit(`[${status}] ${check.id.padEnd(26)} ${row.detail}`);
  }

  // 恢复变更：逆序执行，失败要单独报出来
  const restoreFailures = [];
  for (const change of ctx.changes.reverse()) {
    try {
      await change.restore();
    } catch (error) {
      restoreFailures.push(`${change.description}: ${error.message}`);
    }
  }

  const passed = results.filter((r) => r.status === 'PASS').length;
  const failed = results.filter((r) => r.status === 'FAIL').length;
  const skipped = results.filter((r) => r.status === 'SKIP').length;

  if (values.json) {
    emit(
      JSON.stringify(
        { base: ctx.base, results, restoreFailures, summary: { passed, failed, skipped } },
        null,
        2,
      ),
    );
  } else {
    emit('');
    emit(`通过 ${passed} / 失败 ${failed} / 跳过 ${skipped}`);
    if (ctx.changes.length) emit(`已恢复 ${ctx.changes.length} 项写入变更`);
    for (const failure of restoreFailures) emitErr(`[恢复失败] ${failure}`);
    emit('');
    emit('判据：失败项为 0 才算这次升级验收通过；跳过项要看原因，不要当成通过。');
  }

  if (restoreFailures.length) return 2;
  return failed === 0 ? 0 : 1;
}

main()
  .then((code) => process.exit(code))
  .catch((error) => {
    emitErr(`未预期错误: ${error?.stack ?? error}`);
    process.exit(2);
  });
