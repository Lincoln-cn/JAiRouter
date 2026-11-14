#!/usr/bin/env node
/**
 * 上游桩服务（mock upstream）—— 把所有「模型后端」换成一个可控的假服务。
 *
 * 解决三个真实痛点：
 *   1. 现场上游没部署 / 不可达时，网关全部实例 UNHEALTHY、全链路 503，压测与功能验证都做不了；
 *      用桩把链路点亮，才能测「网关本身」的表现。
 *   2. 故障无法复现：上游偶发 500/429/慢响应/断连，靠等是等不来的；这里用概率与开关注入。
 *   3. 负载均衡与熔断看不到效果：多开几个桩实例（不同端口 + 不同 tag），就能观察流量分布；
 *      把某个桩设成持续 500，就能观察熔断器打开与半开恢复。
 *
 * 零第三方依赖，只用 node 内建 http。
 *
 * 用法：
 *   node sla/mockupstream/server.mjs --port 9100 --tag mock-a
 *   node sla/mockupstream/server.mjs --port 9101 --tag mock-b --latency 50
 *   node sla/mockupstream/server.mjs --port 9100 --error-rate 1      # 全量 500，模拟故障
 *
 * 控制面（运行时改行为，不用重启）：
 *   GET  /__mock/state                    看当前行为与统计
 *   POST /__mock/behavior  {json}         改行为（字段见 DEFAULT_BEHAVIOR）
 *   POST /__mock/reset                    行为复位 + 统计清零
 *   GET  /__mock/stats                    只要统计
 *
 * 支持的模型端点（全部 OpenAI / vLLM / GPUStack 风格）：
 *   POST /v1/chat/completions      支持 stream=true（SSE）与 stream=false
 *   POST /v1/embeddings
 *   POST /v1/rerank
 *   POST /v1/audio/speech          TTS（返回二进制假音频）
 *   POST /v1/audio/transcriptions  STT
 *   POST /v1/images/generations
 *   POST /v1/images/edits          支持 multipart 与 JSON
 *   GET  /v1/models
 *   GET  /health                   探活用（恒 200，不受故障注入影响）
 *
 * 行为字段：
 *   status            强制状态码（0 = 按端点正常返回）
 *   errorRate         0~1，按概率返回 500/429
 *   latencyMs         固定延迟
 *   jitterMs          额外随机延迟上限
 *   hangMs            挂起多少毫秒后断开（模拟超时）
 *   malformed         true 时返回非法 JSON
 *   closeConnection   true 时直接断开连接
 *   streamChunkDelayMs 流式分块间隔
 *   instanceTag       在响应头 X-Mock-Instance 与 body 里标记来源，用于验证负载均衡
 *   onlyModels        只对这些模型名生效（空数组 = 全部）
 */

import { createServer } from 'node:http';
import { parseArgs } from 'node:util';

const DEFAULT_BEHAVIOR = {
  status: 0,
  errorRate: 0,
  latencyMs: 0,
  jitterMs: 0,
  hangMs: 0,
  malformed: false,
  closeConnection: false,
  streamChunkDelayMs: 15,
  instanceTag: 'mock',
  onlyModels: [],
};

const { values } = parseArgs({
  options: {
    port: { type: 'string', default: '9100' },
    host: { type: 'string', default: '0.0.0.0' },
    tag: { type: 'string', default: 'mock' },
    latency: { type: 'string', default: '0' },
    'error-rate': { type: 'string', default: '0' },
    status: { type: 'string', default: '0' },
    hang: { type: 'string', default: '0' },
  },
});

const PORT = Number(values.port);
const HOST = values.host;

const state = {
  behavior: {
    ...DEFAULT_BEHAVIOR,
    instanceTag: values.tag,
    latencyMs: Number(values.latency),
    errorRate: Number(values['error-rate']),
    status: Number(values.status),
    hangMs: Number(values.hang),
  },
  stats: {
    startedAt: new Date().toISOString(),
    total: 0,
    byPath: {},
    byModel: {},
    byStatus: {},
    byInstance: {},
    lastRequests: [],
  },
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function bump(map, key) {
  map[key] = (map[key] || 0) + 1;
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 8 * 1024 * 1024) {
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', () => resolve(Buffer.alloc(0)));
  });
}

function extractModel(raw, contentType) {
  const text = raw.toString('utf8');
  if (contentType.includes('application/json')) {
    try {
      return JSON.parse(text).model || '';
    } catch {
      return '';
    }
  }
  // multipart：抓 model 字段的文本
  const match = text.match(/name="model"\r?\n\r?\n([^\r\n]+)/);
  return match ? match[1] : '';
}

function sendJson(res, status, payload, tag) {
  const body = Buffer.from(JSON.stringify(payload));
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': body.length,
    'x-mock-instance': tag,
  });
  res.end(body);
}

function openAiError(message, type = 'server_error', code = '500') {
  return { error: { message, type, code } };
}

/** 按行为决定「这一次请求该怎么演」：正常 / 强制状态 / 概率故障 / 挂起 / 断连。 */
async function applyFault(behavior, model) {
  if (behavior.onlyModels.length > 0 && !behavior.onlyModels.includes(model)) {
    return { fault: null };
  }
  if (behavior.closeConnection) {
    return { fault: 'close' };
  }
  if (behavior.hangMs > 0) {
    return { fault: 'hang', hangMs: behavior.hangMs };
  }
  const extra = behavior.jitterMs > 0 ? Math.floor(Math.random() * behavior.jitterMs) : 0;
  if (behavior.latencyMs > 0 || extra > 0) {
    await sleep(behavior.latencyMs + extra);
  }
  if (behavior.status > 0) {
    return { fault: 'status', status: behavior.status };
  }
  if (behavior.errorRate > 0 && Math.random() < behavior.errorRate) {
    const status = Math.random() < 0.5 ? 500 : 429;
    return { fault: 'status', status };
  }
  if (behavior.malformed) {
    return { fault: 'malformed' };
  }
  return { fault: null };
}

// ---------- 各端点正常响应 ----------

function chatCompletion(model, tag) {
  return {
    id: `chatcmpl-mock-${Date.now()}`,
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model,
    mock_instance: tag,
    choices: [
      {
        index: 0,
        message: { role: 'assistant', content: `[${tag}] 模拟回复：收到，这是桩服务返回的内容。` },
        finish_reason: 'stop',
      },
    ],
    usage: { prompt_tokens: 12, completion_tokens: 18, total_tokens: 30 },
  };
}

function embeddings(model, tag) {
  const dim = 8;
  return {
    object: 'list',
    model,
    mock_instance: tag,
    data: [{ object: 'embedding', index: 0, embedding: Array.from({ length: dim }, (_, i) => Math.sin(i + 1) ** 2 / (dim + 1)) }],
    usage: { prompt_tokens: 6, total_tokens: 6 },
  };
}

function rerank(model, tag) {
  return {
    model,
    mock_instance: tag,
    results: [
      { index: 0, relevance_score: 0.93, document: { text: `[${tag}] 文档 A` } },
      { index: 1, relevance_score: 0.41, document: { text: `[${tag}] 文档 B` } },
    ],
    usage: { total_tokens: 9 },
  };
}

function transcription(tag) {
  return { text: `[${tag}] 这是一段模拟转写文本。`, mock_instance: tag, language: 'zh' };
}

function imageGeneration(model, tag) {
  // 1x1 透明 PNG 的 base64，避免桩返回 URL 时下游还要去拉
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  return {
    created: Math.floor(Date.now() / 1000),
    model,
    mock_instance: tag,
    data: [{ b64_json: png, revised_prompt: `[${tag}] 模拟绘图提示词` }],
  };
}

/** 1x1 假音频（WAV 头 + 静音），用于 TTS/STT 链路验证 */
function fakeAudio() {
  const sampleRate = 8000;
  const samples = 800;
  const dataLength = samples * 2;
  const buffer = Buffer.alloc(44 + dataLength);
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataLength, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataLength, 40);
  return buffer;
}

/** 流式 SSE：按 OpenAI chunk 格式逐块推送，最后 [DONE]。 */
async function streamChat(req, res, model, tag) {
  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache',
    connection: 'keep-alive',
    'x-mock-instance': tag,
  });

  const pieces = [`[${tag}] 这是`, '模拟的', '流式', '回复。'];
  const base = {
    id: `chatcmpl-mock-${Date.now()}`,
    object: 'chat.completion.chunk',
    created: Math.floor(Date.now() / 1000),
    model,
  };

  for (let i = 0; i < pieces.length; i++) {
    if (res.writableEnded) return;
    const chunk = {
      ...base,
      choices: [{ index: 0, delta: i === 0 ? { role: 'assistant', content: pieces[i] } : { content: pieces[i] }, finish_reason: null }],
    };
    res.write(`data: ${JSON.stringify(chunk)}\n\n`);
    await sleep(state.behavior.streamChunkDelayMs);
  }
  res.write(`data: ${JSON.stringify({ ...base, choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] })}\n\n`);
  res.write('data: [DONE]\n\n');
  res.end();
}

// ---------- 控制面 ----------

function handleControl(req, res, url) {
  const tag = state.behavior.instanceTag;

  if (url.pathname === '/__mock/state') {
    sendJson(res, 200, { behavior: state.behavior, stats: state.stats }, tag);
    return;
  }
  if (url.pathname === '/__mock/stats') {
    sendJson(res, 200, state.stats, tag);
    return;
  }
  if (url.pathname === '/__mock/reset' && req.method === 'POST') {
    const tag2 = state.behavior.instanceTag;
    state.behavior = { ...DEFAULT_BEHAVIOR, instanceTag: tag2 };
    state.stats = {
      startedAt: new Date().toISOString(),
      total: 0, byPath: {}, byModel: {}, byStatus: {}, byInstance: {}, lastRequests: [],
    };
    sendJson(res, 200, { ok: true, behavior: state.behavior }, tag2);
    return;
  }
  if (url.pathname === '/__mock/behavior' && req.method === 'POST') {
    readBody(req).then((raw) => {
      let patch;
      try {
        patch = JSON.parse(raw.toString('utf8') || '{}');
      } catch (error) {
        sendJson(res, 400, openAiError(`行为参数不是合法 JSON: ${error.message}`, 'invalid_request_error', '400'), tag);
        return;
      }
      const allowed = Object.keys(DEFAULT_BEHAVIOR);
      const rejected = Object.keys(patch).filter((key) => !allowed.includes(key));
      for (const key of Object.keys(patch)) {
        if (allowed.includes(key)) state.behavior[key] = patch[key];
      }
      sendJson(res, 200, { ok: true, behavior: state.behavior, rejected }, tag);
    });
    return;
  }
  sendJson(res, 404, openAiError(`未知的控制端点: ${url.pathname}`, 'invalid_request_error', '404'), tag);
}

// ---------- 主处理 ----------

async function handle(req, res) {
  const url = new URL(req.url || '/', 'http://mock');
  const tag = state.behavior.instanceTag;

  if (url.pathname.startsWith('/__mock/')) {
    handleControl(req, res, url);
    return;
  }

  // 探活恒 200：否则「桩自身健康」与「故障注入」会混在一起，分不清是谁挂了
  if (url.pathname === '/health' || url.pathname === '/') {
    sendJson(res, 200, { status: 'ok', instance: tag }, tag);
    return;
  }

  state.stats.total += 1;
  bump(state.stats.byPath, `${req.method} ${url.pathname}`);
  bump(state.stats.byInstance, tag);

  const raw = await readBody(req);
  const contentType = String(req.headers['content-type'] || '');
  const model = extractModel(raw, contentType);
  if (model) bump(state.stats.byModel, model);

  const record = {
    at: new Date().toISOString(),
    method: req.method,
    path: url.pathname,
    model,
    instance: tag,
    status: 0,
  };

  const decision = await applyFault(state.behavior, model);

  if (decision.fault === 'close') {
    record.status = -1;
    record.note = 'closeConnection';
    pushRecord(record);
    req.socket.destroy();
    return;
  }
  if (decision.fault === 'hang') {
    record.status = -2;
    record.note = `hang ${decision.hangMs}ms`;
    pushRecord(record);
    await sleep(decision.hangMs);
    req.socket.destroy();
    return;
  }
  if (decision.fault === 'malformed') {
    record.status = 200;
    record.note = 'malformed';
    pushRecord(record);
    bump(state.stats.byStatus, '200');
    const body = Buffer.from('{"broken": ');
    res.writeHead(200, { 'content-type': 'application/json', 'content-length': body.length, 'x-mock-instance': tag });
    res.end(body);
    return;
  }
  if (decision.fault === 'status') {
    record.status = decision.status;
    pushRecord(record);
    bump(state.stats.byStatus, String(decision.status));
    const type = decision.status === 429 ? 'rate_limit_error' : decision.status === 401 ? 'authentication_error' : 'server_error';
    sendJson(res, decision.status, openAiError(`[${tag}] 模拟故障：HTTP ${decision.status}`, type, String(decision.status)), tag);
    return;
  }

  record.status = 200;
  pushRecord(record);
  bump(state.stats.byStatus, '200');

  switch (url.pathname) {
    case '/v1/chat/completions': {
      let wantsStream = false;
      try {
        wantsStream = JSON.parse(raw.toString('utf8') || '{}').stream === true;
      } catch {
        wantsStream = false;
      }
      if (wantsStream) {
        await streamChat(req, res, model, tag);
      } else {
        sendJson(res, 200, chatCompletion(model, tag), tag);
      }
      return;
    }
    case '/v1/embeddings':
      sendJson(res, 200, embeddings(model, tag), tag);
      return;
    case '/v1/rerank':
      sendJson(res, 200, rerank(model, tag), tag);
      return;
    case '/v1/audio/speech':
    case '/v1/audio/transcriptions': {
      if (url.pathname === '/v1/audio/transcriptions') {
        sendJson(res, 200, transcription(tag), tag);
        return;
      }
      const audio = fakeAudio();
      res.writeHead(200, {
        'content-type': 'audio/wav',
        'content-length': audio.length,
        'x-mock-instance': tag,
      });
      res.end(audio);
      return;
    }
    case '/v1/images/generations':
    case '/v1/images/edits':
      sendJson(res, 200, imageGeneration(model, tag), tag);
      return;
    case '/v1/models':
      sendJson(res, 200, {
        object: 'list',
        mock_instance: tag,
        data: [
          'qwen3:4b', 'bge-large-zh-v1.5', 'nomic-embed-text-v1.5', 'bge-reranker-v2-m3',
          'cosyvoice-300m', 'faster-whisper-tiny', 'stable-diffusion-2-1',
        ].map((id) => ({ id, object: 'model', owned_by: 'mock' })),
      }, tag);
      return;
    default:
      sendJson(res, 404, openAiError(`桩服务未实现该端点: ${url.pathname}`, 'invalid_request_error', '404'), tag);
  }
}

function pushRecord(record) {
  state.stats.lastRequests.push(record);
  if (state.stats.lastRequests.length > 50) state.stats.lastRequests.shift();
}

const server = createServer((req, res) => {
  handle(req, res).catch((error) => {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    sendJson(res, 500, openAiError(`桩服务内部错误: ${error.message}`), state.behavior.instanceTag);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`[mock-upstream] ${state.behavior.instanceTag} 监听 http://${HOST}:${PORT}`);
  console.log(`[mock-upstream] 控制面: GET /__mock/state | POST /__mock/behavior | POST /__mock/reset`);
});
