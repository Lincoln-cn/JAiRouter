#!/usr/bin/env node
/**
 * 压测用的最小上游 mock（#944）。
 *
 * 目的：让「网关自身开销」可以被测量，而不是把上游模型的延迟混进结果里。
 * 返回 OpenAI 兼容形状，兼容 `/v1/models` 探活（网关默认健康检查路径）。
 *
 * 用法：
 *   node scripts/perf/mock-upstream.mjs
 *   MOCK_PORT=18080 MOCK_DELAY_MS=20 node scripts/perf/mock-upstream.mjs
 *
 * 端点：
 *   GET  /v1/models                  → 模型列表（健康检查只看状态码 < 500）
 *   POST /v1/chat/completions        → 非流式 JSON / 流式 SSE（按请求体 stream 字段）
 *   GET  /health                     → 200 ok
 */
import http from 'node:http'

const PORT = Number(process.env.MOCK_PORT || 18080)
const HOST = process.env.MOCK_HOST || '127.0.0.1'
const DELAY_MS = Number(process.env.MOCK_DELAY_MS || 20)
const CHUNK_DELAY_MS = Number(process.env.MOCK_CHUNK_DELAY_MS || 1)
const MODEL = 'mock-model'
const STREAM_CHUNKS = ['Hello', ' from', ' mock', '.']

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const server = http.createServer(async (req, res) => {
  const path = (req.url || '/').split('?')[0]

  if (req.method === 'GET' && path === '/v1/models') {
    res.writeHead(200, { 'content-type': 'application/json' })
    return res.end(JSON.stringify({ object: 'list', data: [{ id: MODEL, object: 'model', owned_by: 'mock' }] }))
  }

  if (req.method === 'GET' && path === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' })
    return res.end(JSON.stringify({ status: 'ok' }))
  }

  if (req.method === 'POST' && (path === '/v1/chat/completions' || path === '/chat/completions')) {
    let raw = ''
    for await (const chunk of req) raw += chunk
    let stream = false
    try {
      stream = Boolean(JSON.parse(raw).stream)
    } catch {
      // 体不是 JSON 时按非流式处理，交给网关去报错
    }
    await sleep(DELAY_MS)

    if (stream) {
      res.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
        connection: 'keep-alive'
      })
      for (const text of STREAM_CHUNKS) {
        res.write(`data: ${JSON.stringify(chatChunk(text, null))}\n\n`)
        await sleep(CHUNK_DELAY_MS)
      }
      res.write(`data: ${JSON.stringify(chatChunk('', 'stop'))}\n\n`)
      res.write('data: [DONE]\n\n')
      return res.end()
    }

    res.writeHead(200, { 'content-type': 'application/json' })
    return res.end(JSON.stringify({
      id: 'chatcmpl-mock',
      object: 'chat.completion',
      created: 0,
      model: MODEL,
      choices: [{ index: 0, message: { role: 'assistant', content: 'Hello from mock.' }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 8, completion_tokens: 4, total_tokens: 12 }
    }))
  }

  res.writeHead(404, { 'content-type': 'application/json' })
  res.end(JSON.stringify({ error: 'not found', path }))
})

function chatChunk(content, finishReason) {
  return {
    id: 'chatcmpl-mock',
    object: 'chat.completion.chunk',
    created: 0,
    model: MODEL,
    choices: [{ index: 0, delta: content ? { content } : {}, finish_reason: finishReason }]
  }
}

server.listen(PORT, HOST, () => {
  console.log(`[mock-upstream] http://${HOST}:${PORT}  delay=${DELAY_MS}ms  chunkDelay=${CHUNK_DELAY_MS}ms  model=${MODEL}`)
})
