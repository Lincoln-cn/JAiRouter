#!/usr/bin/env node
/**
 * OpenAI 兼容的 mock 模型服务 —— 用于本地测试 JAiRouter。
 *
 * 用途：JAiRouter 的实例健康检查是「socket_connect 探测上游 host:port」，
 * 且路由/负载均衡/熔断/配额等链路都需要一个**真实可达**的上游。本服务提供
 * 一个零依赖的 OpenAI 兼容端点，把实例指过来即可跑通完整链路。
 *
 * 用法：
 *   node scripts/test/mock-model-server.mjs                       # 默认 127.0.0.1:9099
 *   node scripts/test/mock-model-server.mjs --port 9100 --tag B   # 多实例时用 tag 区分
 *   node scripts/test/mock-model-server.mjs --latency 800         # 模拟慢上游
 *   node scripts/test/mock-model-server.mjs --fail-rate 0.5       # 模拟故障（测熔断/降级）
 *
 * 在 JAiRouter 里加实例：
 *   POST /api/config/instance/chat
 *   { "name": "qwen3.8-flash", "baseUrl": "http://127.0.0.1:9099",
 *     "path": "/v1/chat/completions", "weight": 1, "status": "active", "adapter": "gpustack" }
 *
 * 支持端点（其余路径一律 200 + 通用 JSON，保证健康检查/探活通过）：
 *   GET  /v1/models
 *   POST /v1/chat/completions      （含 stream=true 的 SSE）
 *   POST /v1/embeddings
 *   POST /v1/rerank
 *   POST /v1/audio/speech          （TTS）
 *   POST /v1/audio/transcriptions  （STT）
 *   POST /v1/images/generations
 *   POST /v1/images/edits
 */

import http from 'node:http'

function readFlag(name, fallback) {
  const i = process.argv.indexOf(`--${name}`)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const PORT = Number(readFlag('port', process.env.MOCK_PORT || 9099))
const HOST = readFlag('host', process.env.MOCK_HOST || '127.0.0.1')
const TAG = readFlag('tag', process.env.MOCK_TAG || 'mock')
const LATENCY = Number(readFlag('latency', 0))
const FAIL_RATE = Number(readFlag('fail-rate', 0))
const REPLY = readFlag('reply', 'pong')

const sleep = ms => new Promise(r => setTimeout(r, ms))
const now = () => Math.floor(Date.now() / 1000)
const rid = () => `cmpl-${TAG}-${Math.random().toString(36).slice(2, 10)}`

const log = (level, msg) => {
  const ts = new Date().toISOString().slice(11, 23)
  console.log(`${ts} [${TAG}] ${level} ${msg}`)
}

function readBody(req) {
  return new Promise(resolve => {
    let raw = ''
    req.on('data', c => (raw += c))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

const json = (res, code, payload) => {
  const body = JSON.stringify(payload)
  res.writeHead(code, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body)
  })
  res.end(body)
}

/** 把回答按字符切块，用 SSE 推出去，模拟真实流式上游 */
async function streamChat(res, model, chunks) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive'
  })
  const id = rid()
  const frame = (delta, finish) =>
    `data: ${JSON.stringify({
      id,
      object: 'chat.completion.chunk',
      created: now(),
      model,
      choices: [{ index: 0, delta, finish_reason: finish }]
    })}\n\n`

  res.write(frame({ role: 'assistant', content: '' }, null))
  for (const piece of chunks) {
    await sleep(Math.max(LATENCY, 20))
    res.write(frame({ content: piece }, null))
  }
  res.write(frame({}, 'stop'))
  res.write('data: [DONE]\n\n')
  res.end()
}

const server = http.createServer(async (req, res) => {
  const started = Date.now()
  const url = req.url || '/'
  const path = url.split('?')[0]
  const method = req.method || 'GET'
  let note = ''

  try {
    if (method === 'GET' && path.endsWith('/models')) {
      json(res, 200, {
        object: 'list',
        data: [TAG, `${TAG}-small`].map(id => ({
          id,
          object: 'model',
          created: now(),
          owned_by: `mock-${TAG}`
        }))
      })
    } else if (method === 'POST' && path.includes('/chat/completions')) {
      const body = await readBody(req)
      const model = body.model || TAG
      note = `model=${model} stream=${!!body.stream}`
      if (LATENCY) await sleep(LATENCY)
      if (FAIL_RATE > 0 && Math.random() < FAIL_RATE) {
        note += ' [injected-failure]'
        json(res, 500, { error: { message: 'injected failure by mock', type: 'mock_error' } })
      } else if (body.stream) {
        await streamChat(res, model, REPLY.split(''))
      } else {
        json(res, 200, {
          id: rid(),
          object: 'chat.completion',
          created: now(),
          model,
          choices: [
            {
              index: 0,
              message: { role: 'assistant', content: REPLY },
              finish_reason: 'stop'
            }
          ],
          usage: { prompt_tokens: 1, completion_tokens: REPLY.length, total_tokens: REPLY.length + 1 }
        })
      }
    } else if (method === 'POST' && path.includes('/embeddings')) {
      const body = await readBody(req)
      const inputs = Array.isArray(body.input) ? body.input : [body.input ?? '']
      note = `model=${body.model} n=${inputs.length}`
      if (LATENCY) await sleep(LATENCY)
      json(res, 200, {
        object: 'list',
        model: body.model,
        data: inputs.map((_, i) => ({
          object: 'embedding',
          index: i,
          embedding: Array.from({ length: 8 }, (_, k) => Number(((i + k) / 100).toFixed(4)))
        })),
        usage: { prompt_tokens: inputs.length, total_tokens: inputs.length }
      })
    } else if (method === 'POST' && path.includes('/rerank')) {
      const body = await readBody(req)
      note = `model=${body.model}`
      json(res, 200, {
        model: body.model,
        results: [{ index: 0, relevance_score: 0.99, document: null }],
        usage: { total_tokens: 1 }
      })
    } else if (method === 'POST' && path.includes('/audio/speech')) {
      note = 'tts'
      res.writeHead(200, { 'Content-Type': 'audio/wav' })
      res.end(Buffer.from([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45]))
    } else if (method === 'POST' && path.includes('/audio/transcriptions')) {
      note = 'stt'
      json(res, 200, { text: REPLY })
    } else if (method === 'POST' && path.includes('/images/')) {
      const body = await readBody(req)
      note = `model=${body.model}`
      json(res, 200, {
        created: now(),
        data: [{ b64_json: Buffer.from(REPLY).toString('base64'), revised_prompt: 'mock' }]
      })
    } else {
      // 兜底：健康检查/探活走这里，一律 200
      json(res, 200, { status: 'ok', tag: TAG, path, method })
    }
  } catch (e) {
    log('ERROR', `${method} ${path} -> ${e.message}`)
    if (!res.headersSent) json(res, 500, { error: { message: e.message } })
  } finally {
    log('REQ', `${method} ${path}${note ? ` ${note}` : ''} -> ${res.statusCode} (${Date.now() - started}ms)`)
  }
})

server.listen(PORT, HOST, () => {
  log('INFO', `mock model server listening on http://${HOST}:${PORT}`)
  log('INFO', `tag=${TAG} latency=${LATENCY}ms failRate=${FAIL_RATE} reply="${REPLY}"`)
})
