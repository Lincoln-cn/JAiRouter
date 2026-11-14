#!/usr/bin/env node
/**
 * Synthetic transaction probe — 「一次真实对话能不能成功」.
 *
 * 为什么不用 /actuator/health 判断：本项目的两次全链路 502 期间它始终是 UP。
 *   - #1004 实例 adapter 为空：健康检查走 socket_connect，转发走适配器
 *   - #1014 Netty 解析不了 compose 服务名：健康检查走 glibc，转发走 DNS resolver
 * 两条路径不同，所以判据必须走到「适配器 + 上游」这一层。
 *
 * 用法:
 *   node sla/probes/synthetic.mjs --base http://127.0.0.1:8080 --user admin --pass ***
 *   node sla/probes/synthetic.mjs ... --json
 *
 * 退出码：0 全部通过；2 有失败（便于 cron / CI 直接判）。
 */

import { api, joinUrl, login, unwrap } from '../lib/client.mjs';

function parseArgs(argv) {
  const out = { base: 'http://127.0.0.1:8080', user: process.env.JAIR_USER || 'admin', pass: process.env.JAIR_PASS || '', json: false, timeoutMs: 60000, skipStream: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--json') out.json = true;
    else if (arg === '--skip-stream') out.skipStream = true;
    else if (arg === '--base') out.base = argv[++i];
    else if (arg === '--user') out.user = argv[++i];
    else if (arg === '--pass') out.pass = argv[++i];
    else if (arg === '--timeout') out.timeoutMs = Number(argv[++i]);
  }
  return out;
}

async function pickModel(base, token) {
  const list = unwrap(await api(base, '/api/config/instance/chat', { token }));
  const usable = (list ?? []).filter((item) => item?.name && item?.status !== 'inactive');
  if (usable.length === 0) throw new Error('没有可用的对话实例（先去配置里加一个）');
  return usable[0].name;
}

async function chatOnce(base, token, model, timeoutMs) {
  const started = Date.now();
  const body = await api(base, '/v1/chat/completions', {
    method: 'POST',
    token,
    timeoutMs,
    body: { model, messages: [{ role: 'user', content: 'ping' }], max_tokens: 8, stream: false },
  });
  const ms = Date.now() - started;
  const content = body?.choices?.[0]?.message?.content;
  if (!content) throw new Error('响应里没有 choices[0].message.content');
  return { ms, chars: content.length };
}

async function chatStream(base, token, model, timeoutMs) {
  const started = Date.now();
  const res = await fetch(joinUrl(base, '/v1/chat/completions'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', 'X-Jairouter-Token': token },
    body: JSON.stringify({ model, messages: [{ role: 'user', content: 'ping' }], max_tokens: 8, stream: true }),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  if (!res.body) throw new Error('没有可读的响应体');

  const decoder = new TextDecoder();
  const reader = res.body.getReader();
  let buffer = '';
  let chunks = 0;
  let sawDone = false;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let index;
    while ((index = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, index).trim();
      buffer = buffer.slice(index + 1);
      if (!line.startsWith('data:')) continue;
      const payload = line.slice(5).trim();
      if (payload === '[DONE]') sawDone = true;
      else if (payload) chunks++;
    }
  }
  const ms = Date.now() - started;
  if (chunks === 0) throw new Error('流式响应没有数据块');
  return { ms, chunks, sawDone };
}

function table(rows) {
  const lines = [];
  for (const row of rows) {
    const mark = row.ok ? '[ OK ]' : '[FAIL]';
    const detail = row.ok
      ? Object.entries(row).filter(([k]) => !['name', 'ok', 'ms'].includes(k)).map(([k, v]) => `${k}=${v}`).join(' ')
      : row.error;
    lines.push(`${mark} ${row.name.padEnd(18)} ${String(row.ms).padStart(6)}ms  ${detail ?? ''}`);
  }
  return lines.join('\n');
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.pass) {
    console.error('缺少密码：用 --pass 或设 JAIR_PASS');
    process.exitCode = 2;
    return;
  }

  const steps = [];
  const run = async (name, fn) => {
    const started = Date.now();
    try {
      const detail = await fn();
      steps.push({ name, ok: true, ms: Date.now() - started, ...detail });
    } catch (error) {
      steps.push({ name, ok: false, ms: Date.now() - started, error: error?.message ?? String(error) });
    }
  };

  let token = null;
  let model = null;

  await run('auth.login', async () => {
    const started = Date.now();
    token = await login(args.base, args.user, args.pass);
    return { ms: Date.now() - started };
  });

  await run('config.instance', async () => {
    model = await pickModel(args.base, token);
    return { model };
  });

  await run('chat.completion', async () => {
    if (!token || !model) throw new Error('前置步骤未通过');
    return chatOnce(args.base, token, model, args.timeoutMs);
  });

  if (!args.skipStream) {
    await run('chat.stream', async () => {
      if (!token || !model) throw new Error('前置步骤未通过');
      const result = await chatStream(args.base, token, model, args.timeoutMs);
      if (!result.sawDone) throw new Error('流式响应没有收到 [DONE]');
      return result;
    });
  }

  const failed = steps.filter((step) => !step.ok).length;
  const report = {
    ok: failed === 0,
    base: args.base,
    model,
    failed,
    total: steps.length,
    steps,
    finishedAt: new Date().toISOString(),
  };

  if (args.json) {
    console.log(JSON.stringify(report, null, 2));
  } else {
    console.log(`合成事务 ${args.base}${model ? `（模型 ${model}）` : ''}`);
    console.log(table(steps));
    console.log(failed === 0 ? `\n通过 ${steps.length} 项` : `\n失败 ${failed} / ${steps.length} 项`);
  }

  process.exitCode = failed === 0 ? 0 : 2;
}

main().catch((error) => {
  console.error(error?.stack ?? String(error));
  process.exitCode = 2;
});
