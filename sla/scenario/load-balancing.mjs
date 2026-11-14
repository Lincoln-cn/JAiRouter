#!/usr/bin/env node
/**
 * load-balancing — 负载均衡策略矩阵。
 *
 * 回答的问题是「换一种策略后，请求真的按这种策略分了吗」。
 * 单看一次请求区分不出来（最少连接在空闲时并列），所以这里固定用并发打，
 * 并且用桩服务回报的 `mock_instance` 标记来源实例 —— 不靠推断，靠证据。
 *
 * 前置：sla/mockupstream/server.mjs 已在若干端口上跑起来（默认 9100/9101/9102）。
 * 本脚本会改实例配置，结束时恢复；任何一步失败也走 finally。
 */

import { parseArgs } from 'node:util';

import { api, joinUrl, login, unwrap } from '../lib/client.mjs';

const STRATEGIES = ['round-robin', 'random', 'least-connections', 'ip-hash', 'consistent-hash'];
const DEFAULT_MODEL = 'qwen3:4b';

function emit(line = '') {
  process.stdout.write(line + '\n');
}

function short(value, limit = 90) {
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  return text && text.length > limit ? text.slice(0, limit) + '…' : text;
}

async function read(base, path, token, options = {}) {
  return unwrap(await api(base, path, { token, ...options }));
}

async function write(base, path, token, body) {
  return unwrap(await api(base, path, { method: 'PUT', body, token }));
}

/** 发 N 个并发 chat 请求，按桩回报的实例标签计数。 */
async function sample(base, token, model, concurrency, rounds) {
  const hits = new Map();
  const statuses = new Map();
  for (let round = 0; round < rounds; round += 1) {
    await Promise.all(
      Array.from({ length: concurrency }, async () => {
        try {
          const res = await fetch(joinUrl(base, '/v1/chat/completions'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Jairouter-Token': token },
            body: JSON.stringify({ model, messages: [{ role: 'user', content: 'lb probe' }] }),
            signal: AbortSignal.timeout(20000),
          });
          statuses.set(res.status, (statuses.get(res.status) ?? 0) + 1);
          const text = await res.text().catch(() => '');
          let tag = null;
          try {
            tag = JSON.parse(text)?.mock_instance ?? null;
          } catch {
            tag = null;
          }
          if (tag) hits.set(tag, (hits.get(tag) ?? 0) + 1);
          else hits.set(`(no-tag ${res.status})`, (hits.get(`(no-tag ${res.status})`) ?? 0) + 1);
        } catch {
          hits.set('(error)', (hits.get('(error)') ?? 0) + 1);
        }
      }),
    );
  }
  return { hits: Object.fromEntries(hits), statuses: Object.fromEntries(statuses) };
}

async function waitHealthy(base, token, serviceType, expected, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const list = await read(base, `/api/config/instance/${serviceType}`, token).catch(() => []);
    const healthy = (Array.isArray(list) ? list : []).filter((i) => i?.healthStatus === 'HEALTHY');
    if (healthy.length >= expected) return healthy;
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  return (await read(base, `/api/config/instance/${serviceType}`, token).catch(() => [])).filter(
    (i) => i?.healthStatus === 'HEALTHY',
  );
}

async function main() {
  const { values } = parseArgs({
    options: {
      base: { type: 'string', default: 'http://127.0.0.1:8080' },
      user: { type: 'string', default: 'admin' },
      pass: { type: 'string' },
      model: { type: 'string', default: DEFAULT_MODEL },
      'service-type': { type: 'string', default: 'chat' },
      'mock-hosts': { type: 'string', default: '127.0.0.1:9100,127.0.0.1:9101,127.0.0.1:9102' },
      concurrency: { type: 'string', default: '10' },
      rounds: { type: 'string', default: '3' },
      strategies: { type: 'string', default: STRATEGIES.join(',') },
      // 会话粘性默认开启，会把同一主体+同一模型的请求钉在同一实例上，
      // 那样测出来必然「不分散」——默认先关掉，结束后恢复。
      'keep-sticky': { type: 'boolean', default: false },
  'force-sticky': { type: 'boolean', default: false },
      json: { type: 'boolean', default: false },
    },
    allowPositionals: false,
  });

  const base = values.base;
  const serviceType = values['service-type'];
  const mockHosts = values['mock-hosts'].split(',').map((s) => s.trim()).filter(Boolean);
  const concurrency = Number(values.concurrency);
  const rounds = Number(values.rounds);
  const strategies = values.strategies.split(',').map((s) => s.trim()).filter(Boolean);
  const token = await login(base, values.user, values.pass ?? '');

  const created = [];
  let originalLb = null;
  let stickyWasEnabled = null;
  const results = [];

  try {
    const before = await read(base, `/api/config/type/services/${serviceType}`, token);
    originalLb = before?.loadBalance ?? null;
    const adapter = before?.adapter ?? null;

    // 粘性不关，测出来的「不分散」与负载均衡策略无关（issue #1095）
    if (!values['keep-sticky'] && !values['force-sticky'] && before?.sticky?.enabled !== false) {
      stickyWasEnabled = true;
      await write(base, `/api/config/type/services/${serviceType}`, token, {
        sticky: { enabled: false },
      });
      const after = await read(base, `/api/config/type/services/${serviceType}`, token);
      if (after?.sticky?.enabled !== false) {
        throw new Error('关闭会话粘性失败，回读仍是开启状态（服务端版本可能低于 3.8.3）');
      }
      emit('已关闭会话粘性（否则各策略都会钉在同一个实例上）');
    } else if (values['force-sticky']) {
    await api(base, '/api/config/type/services/' + serviceType, { method: 'PUT', body: { sticky: { enabled: true } }, token });
    const forced = unwrap(await api(base, '/api/config/type/services/' + serviceType, { token }));
    stickyWasEnabled = true;
    emit('按 --force-sticky 显式开启会话粘性（回读 enabled=' + (forced?.sticky?.enabled) + '）：本轮预期集中到单一实例，用于验证粘性开关确实生效');
  } else if (before?.sticky?.enabled !== false) {
      emit('按 --keep-sticky 保留会话粘性：3.8.6 起默认关闭，若未显式配置 sticky 则本轮仍会分散；若确实开启了粘性，本轮预期不分散，不能作为负载均衡结论');
    }

    const instances = await read(base, `/api/config/instance/${serviceType}`, token);
    const list = Array.isArray(instances) ? instances : [];
    if (!list.length) throw new Error(`${serviceType} 没有任何实例，先配一个再跑`);
    const primary = list[0];
    const originalBaseUrl = primary.baseUrl;
    if (!primary.name) throw new Error('主实例没有 name，负载均衡按模型名匹配，无法继续');

    emit(`主实例 ${primary.name} baseUrl ${originalBaseUrl} → 桩 ${mockHosts[0]}`);
    await write(base, `/api/config/instance/${serviceType}/${primary.instanceId}`, token, {
      baseUrl: `http://${mockHosts[0]}`,
    });

    // 其余桩注册成同名实例：网关按模型名匹配，名字必须一致才会进入同一个候选集
    for (const host of mockHosts.slice(1)) {
      const body = {
        name: primary.name,
        baseUrl: `http://${host}`,
        path: primary.path ?? '/v1/chat/completions',
        weight: 1,
        status: 'active',
        adapter: adapter ?? primary.adapter,
        // tags 在 DTO 里是 Map<String,String>，传数组会被 Jackson 拒绝成 400
        tags: { sla: 'matrix' },
      };
      const createdOne = await read(base, `/api/config/instance/${serviceType}`, token, {
        method: 'POST',
        body,
      });
      const id = createdOne?.instanceId ?? createdOne?.id ?? null;
      if (id) created.push(id);
      emit(`新增实例 ${host} → ${id ?? '(无 id)'}`);
    }

    const healthy = await waitHealthy(base, token, serviceType, mockHosts.length);
    emit(`健康实例 ${healthy.length} / ${mockHosts.length}`);
    if (healthy.length < mockHosts.length) {
      emit(
        '提示：健康实例不足，后面的分布数字不能当作负载均衡结论。最常见的原因是桩地址不可达 —— ' +
          '网关若跑在容器里，它看到的 127.0.0.1 是容器自己，必须传宿主机可达的地址' +
          '（服务器内网 IP，或 docker bridge 的宿主侧地址），例如 --mock-hosts 172.18.24.194:9100,172.18.24.194:9101,172.18.24.194:9102；' +
          '也可能是实例刚创建，还没走完一轮健康检查（约 20 秒）。'
      );
    }

    for (const strategy of strategies) {
      await write(base, `/api/config/type/services/${serviceType}`, token, {
        loadBalance: { type: strategy },
      });
      await new Promise((resolve) => setTimeout(resolve, 800));
      const { hits, statuses } = await sample(base, token, primary.name, concurrency, rounds);
      const total = Object.values(hits).reduce((a, b) => a + b, 0);
      const spread = Object.keys(hits).filter((k) => !k.startsWith('(')).length;
      results.push({ strategy, hits, statuses, total, spread });
      if (!values.json) {
        emit(`\n[${strategy}] 分布 ${short(hits, 200)}  状态 ${short(statuses)}`);
        emit(`            命中 ${spread} 个实例 / 共 ${total} 次`);
      }
    }
  } catch (error) {
    const detail = error?.body ? ` body=${short(error.body, 300)}` : '';
    emit(`执行失败: ${error.message}${detail}`);
    results.push({ error: `${error.message}${detail}` });
  } finally {
    // 恢复：删新增实例 → 主实例改回 → 恢复原策略
    for (const id of created) {
      await read(base, `/api/config/instance/${serviceType}/${id}`, token, { method: 'DELETE' }).catch(
        () => null,
      );
    }
    const first = await read(base, `/api/config/instance/${serviceType}`, token).catch(() => []);
    const primary = (Array.isArray(first) ? first : [])[0];
    if (primary?.instanceId) {
      await write(base, `/api/config/instance/${serviceType}/${primary.instanceId}`, token, {
        baseUrl: process.env.SLA_RESTORE_BASE_URL ?? 'http://localhost:11434',
      }).catch(() => null);
    }
    if (originalLb) {
      await write(base, `/api/config/type/services/${serviceType}`, token, {
        loadBalance: originalLb,
      }).catch(() => null);
    }
    if (stickyWasEnabled) {
      await write(base, `/api/config/type/services/${serviceType}`, token, {
        sticky: { enabled: true },
      }).catch(() => null);
      emit('已恢复会话粘性（原本为开启）');
    }
    emit(`\n已恢复：删除 ${created.length} 个新增实例，主实例 baseUrl 与负载均衡策略已还原`);
  }

  if (values.json) emit(JSON.stringify({ base, serviceType, results }, null, 2));
  const failed = results.filter((r) => r.error).length;
  return failed ? 1 : 0;
}

main()
  .then((code) => process.exit(code))
  .catch((error) => {
    process.stderr.write(`未预期错误: ${error?.stack ?? error}\n`);
    process.exit(2);
  });
