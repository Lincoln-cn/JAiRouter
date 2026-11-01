#!/usr/bin/env node
/**
 * 零依赖压测器（#944）。
 *
 * 为什么不用 k6 / wrk：仓库不引入新的压测依赖，Node 已随前端工具链存在，一个脚本即可复现。
 * 代价是没有花哨的编排能力 —— 这里只做「固定并发 + 固定时长 + 延迟分位」这一件事。
 *
 * 用法：
 *   node scripts/perf/loadtest.mjs --url http://127.0.0.1:8080/v1/chat/completions \
 *     --api-key sk-test-api-key-12345678 --concurrency 20 --duration 30
 *   node scripts/perf/loadtest.mjs ... --stream true
 *
 * 参数：
 *   --url          目标地址（必填）
 *   --api-key      网关 API Key（放进 X-API-Key）
 *   --concurrency  并发连接数（默认 20）
 *   --duration     持续秒数（默认 30）
 *   --stream       true 表示流式请求（默认非流式）
 *   --model        请求体里的 model（默认 mock-model）
 *   --json         只输出 JSON（便于落库/比较）
 *
 * 输出：成功/失败数、RPS、p50/p95/p99、失败样例（最多 3 条）。
 */
import http from 'node:http'
import { performance } from 'node:perf_hooks'

const opts = parseArgs(process.argv.slice(2))
if (!opts.url) {
  console.error('缺少 --url')
  process.exit(2)
}

const target = new URL(opts.url)
const concurrency = Number(opts.concurrency || 20)
const durationSec = Number(opts.duration || 30)
const stream = String(opts.stream || 'false') === 'true'
const model = opts.model || 'mock-model'
const apiKey = opts['api-key'] || ''
const jsonOnly = opts.json === 'true' || opts.json === ''

const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency })
const body = JSON.stringify({
  model,
  messages: [{ role: 'user', content: 'ping' }],
  stream
})

const latencies = []
const failures = []
let ok = 0
let failed = 0
let deadline = 0

function once() {
  return new Promise((resolve) => {
    const startedAt = performance.now()
    const req = http.request({
      host: target.hostname,
      port: target.port,
      path: target.pathname + target.search,
      method: 'POST',
      agent,
      headers: {
        'content-type': 'application/json',
        'content-length': Buffer.byteLength(body),
        ...(apiKey ? { 'x-api-key': apiKey } : {})
      }
    }, (res) => {
      let bytes = 0
      res.on('data', (chunk) => { bytes += chunk.length })
      res.on('end', () => {
        const elapsed = performance.now() - startedAt
        // 流式请求只在「200 且收到过数据」时算成功；错误体我们也当作失败（避免把 4xx 记成成功）
        if (res.statusCode >= 200 && res.statusCode < 300 && bytes > 0) {
          ok += 1
          latencies.push(elapsed)
        } else {
          failed += 1
          if (failures.length < 3) {
            failures.push({ status: res.statusCode, bytes, ms: Math.round(elapsed) })
          }
        }
        resolve()
      })
    })
    req.on('error', (err) => {
      failed += 1
      if (failures.length < 3) {
        failures.push({ error: String(err && err.message ? err.message : err) })
      }
      resolve()
    })
    req.end(body)
  })
}

async function worker() {
  while (performance.now() < deadline) {
    await once()
  }
}

async function main() {
  deadline = performance.now() + durationSec * 1000
  const startedAt = performance.now()
  await Promise.all(Array.from({ length: concurrency }, () => worker()))
  const wallMs = performance.now() - startedAt
  agent.destroy()

  latencies.sort((a, b) => a - b)
  const percentile = (p) => {
    if (latencies.length === 0) return 0
    const index = Math.min(latencies.length - 1, Math.ceil((p / 100) * latencies.length) - 1)
    return latencies[index]
  }
  const summary = {
    target: opts.url,
    stream,
    concurrency,
    durationSec,
    total: ok + failed,
    ok,
    failed,
    rps: Number(((ok + failed) / (wallMs / 1000)).toFixed(1)),
    latencyMs: {
      p50: Number(percentile(50).toFixed(1)),
      p95: Number(percentile(95).toFixed(1)),
      p99: Number(percentile(99).toFixed(1))
    },
    errorRate: Number((((ok + failed) === 0 ? 0 : failed / (ok + failed)) * 100).toFixed(2)),
    failures
  }

  if (jsonOnly) {
    console.log(JSON.stringify(summary))
  } else {
    console.log('=== 压测结果 ===')
    console.log(`目标        ${summary.target}${stream ? '  (stream)' : ''}`)
    console.log(`并发/时长   ${concurrency} / ${durationSec}s`)
    console.log(`请求数      ${summary.total}（成功 ${ok}，失败 ${failed}）`)
    console.log(`RPS         ${summary.rps}`)
    console.log(`延迟        p50 ${summary.latencyMs.p50}ms  p95 ${summary.latencyMs.p95}ms  p99 ${summary.latencyMs.p99}ms`)
    console.log(`错误率      ${summary.errorRate}%`)
    if (failures.length > 0) {
      console.log('失败样例     ' + JSON.stringify(failures))
    }
  }
}

function parseArgs(argv) {
  const out = {}
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    const next = argv[i + 1]
    if (next === undefined || next.startsWith('--')) {
      out[key] = true
    } else {
      out[key] = next
      i += 1
    }
  }
  return out
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
