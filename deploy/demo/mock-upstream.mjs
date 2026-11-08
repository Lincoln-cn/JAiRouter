#!/usr/bin/env node
/**
 * 演示用的最小上游 mock。
 *
 * 目的：让「不需要任何模型服务也能把网关跑通一遍」成立 —— 它返回 OpenAI 兼容形状，
 * 网关把它当成一个模型后端，演示脚本就能完整走完 调用 / 换模型 / 缓存 / 脱敏 四步。
 * 由 deploy/demo/docker-compose.yml 挂载进 mock-upstream 容器。
 *
 * 用法（通常不用手动起）：
 *   docker compose -f deploy/demo/docker-compose.yml up -d
 *   MOCK_PORT=18080 node deploy/demo/mock-upstream.mjs
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
    let requestedModel = MODEL
    let lastUser = ''
    try {
      const body = JSON.parse(raw)
      stream = Boolean(body.stream)
      requestedModel = body.model || MODEL
      const messages = Array.isArray(body.messages) ? body.messages : []
      const last = [...messages].reverse().find((m) => m && m.role === 'user')
      lastUser = typeof last?.content === 'string' ? last.content : ''
    } catch {
      // 体不是 JSON 时按非流式处理，交给网关去报错
    }
    logRequest(stream, requestedModel, lastUser)
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

/**
 * 打一行请求摘要：只记「上游收到了什么形状」，不记明文。
 *
 * 可逆脱敏开启时上游应当收到 `[PII_1]`、`[PII_2]` 这样的占位符，没开启时收到的是明文。
 * 演示脚本第 4 步验证的就是这一行 —— 明文本身不打印：打印明文等于把 PII 写进容器日志，
 * 而判定只需要知道「有没有占位符」，所以占位符清单或明文字节数就够了。
 */
function logRequest(stream, requestedModel, lastUser) {
  const placeholders = [...new Set(lastUser.match(/\[PII_\d+\]/g) || [])]
  const shape = placeholders.length
    ? `placeholders=${placeholders.join(',')}`
    : `plaintext(${Buffer.byteLength(lastUser, 'utf8')}B)`
  console.log(`[mock-upstream] ${stream ? 'stream' : 'json'} model=${requestedModel} lastUser ${shape}`)
}

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
