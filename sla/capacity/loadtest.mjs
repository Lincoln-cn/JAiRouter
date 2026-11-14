#!/usr/bin/env node
/**
 * 零依赖压测器（#944）。
 *
 * 为什么不用 k6 / wrk：仓库不引入新的压测依赖，Node 已随前端工具链存在，一个脚本即可复现。
 * 代价是没有花哨的编排能力 —— 这里只做「固定并发 + 固定时长 + 延迟分位」这一件事。
 *
 * 用法：
 *   node sla/capacity/loadtest.mjs --url http://127.0.0.1:8080/v1/chat/completions \
 *     --api-key sk-test-api-key-12345678 --concurrency 20 --duration 30
 *   node sla/capacity/loadtest.mjs ... --stream true
 *   node sla/capacity/loadtest.mjs --url http://127.0.0.1:8080/actuator/health --method GET
 *
 * 参数：
 *   --url          目标地址（必填）
 *   --method       HTTP 方法（默认 POST；GET 时不发请求体）
 *   --body         请求体（JSON 字符串；默认是 chat completions 的形状）
 *   --api-key      网关 API Key（放进 X-API-Key）
 *   --token        网关 JWT（放进 X-Jairouter-Token）—— 没有 API Key 时用登录拿到的 token
 *   --header       额外请求头，可重复：--header 'X-Foo: bar'
 *   --concurrency  并发连接数（默认 20）
 *   --duration     持续秒数（默认 30）
 *   --timeout      单请求超时秒数（默认 30）—— 没有它，一个挂死的下游会让压测永远结束不了
 *   --stream       true 表示流式请求（默认非流式）
 *   --model        请求体里的 model（默认 mock-model）
 *   --json         只输出 JSON（便于落库/比较）
 *
 * 输出：成功/失败数、RPS、成功请求的 p50/p95/p99、失败请求的耗时与样例。
 *
 * 关于延迟统计（SLA-TOOL-1）：**失败请求同样计入耗时统计**，并单独成组。
 * 早先只统计 2xx 的延迟，于是「全部请求 401」时会输出 p50/p95/p99 = 0ms —— 看起来
 * 延迟极好，实际是没有一条成功。那种输出会直接误导容量判断，所以这里分开报，
 * 且全失败时明确写「无成功样本」。
 */
import http from 'node:http'
import https from 'node:https'
import { performance } from 'node:perf_hooks'

const opts = parseArgs(process.argv.slice(2))
if (!opts.url) {
  console.error('缺少 --url')
  process.exit(2)
}

const target = new URL(opts.url)
const method = String(opts.method || 'POST').toUpperCase()
const concurrency = Number(opts.concurrency || 20)
const durationSec = Number(opts.duration || 30)
const timeoutMs = Number(opts.timeout || 30) * 1000
const stream = String(opts.stream || 'false') === 'true'
const model = opts.model || 'mock-model'
const apiKey = opts['api-key'] || ''
const token = opts.token || process.env.JAIR_TOKEN || ''
const extraHeaders = parseHeaderList(opts.headerArray || opts.header)
const jsonOnly = opts.json === 'true' || opts.json === ''

const body = method === 'GET'
  ? ''
  : (opts.body
    ? String(opts.body)
    : JSON.stringify({
      model,
      messages: [{ role: 'user', content: 'ping' }],
      stream
    }))

const transport = target.protocol === 'https:' ? https : http
const agent = new transport.Agent({ keepAlive: true, maxSockets: concurrency })

/** 成功请求的耗时 */
const okLatencies = []
/** 失败请求的耗时 —— 单独一组，绝不再和成功后混算 */
const failedLatencies = []
const failures = []
const errorKinds = new Map()
let ok = 0
let failed = 0
let deadline = 0

function recordFailure(kind, sample) {
  failed += 1
  errorKinds.set(kind, (errorKinds.get(kind) || 0) + 1)
  if (failures.length < 5) failures.push(sample)
}

function once() {
  return new Promise((resolve) => {
    const startedAt = performance.now()
    let settled = false
    const finish = (fn) => {
      if (settled) return
      settled = true
      fn()
      resolve()
    }

    const req = transport.request({
      host: target.hostname,
      port: target.port,
      path: target.pathname + target.search,
      method,
      agent,
      headers: {
        ...(method === 'GET' ? {} : {
          'content-type': 'application/json',
          'content-length': Buffer.byteLength(body)
        }),
        ...(apiKey ? { 'x-api-key': apiKey } : {}),
        // 没有 API Key 时用 JWT：网关的 /v1/** 只要求 authenticated，JWT 与 API Key 都能过
        ...(token ? { 'x-jairouter-token': token } : {}),
        ...extraHeaders
      }
    }, (res) => {
      let bytes = 0
      res.on('data', (chunk) => { bytes += chunk.length })
      res.on('end', () => {
        const elapsed = performance.now() - startedAt
        // 流式请求只在「2xx 且收到过数据」时算成功；错误体也当失败（避免把 4xx 记成成功）
        if (res.statusCode >= 200 && res.statusCode < 300 && bytes > 0) {
          ok += 1
          okLatencies.push(elapsed)
        } else {
          failedLatencies.push(elapsed)
          recordFailure(`http_${res.statusCode}`, {
            status: res.statusCode,
            bytes,
            ms: Math.round(elapsed)
          })
        }
        finish(() => {})
      })
    })

    // 没有这个，遇到不会回包的下游（黑 hole）时 worker 永远等下去，压测不会结束。
    req.setTimeout(timeoutMs, () => {
      req.destroy(Object.assign(new Error(`超时 ${timeoutMs}ms`), { code: 'ETIMEOUT' }))
    })

    req.on('error', (err) => {
      const elapsed = performance.now() - startedAt
      failedLatencies.push(elapsed)
      const code = err && err.code ? err.code : 'ERROR'
      recordFailure(code, {
        error: String(err && err.message ? err.message : err),
        ms: Math.round(elapsed)
      })
      finish(() => {})
    })

    req.end(body)
  })
}

async function worker() {
  while (performance.now() < deadline) {
    await once()
  }
}

function percentile(sorted, p) {
  if (sorted.length === 0) return null
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return Number(sorted[index].toFixed(1))
}

function summarize(list) {
  const sorted = [...list].sort((a, b) => a - b)
  if (sorted.length === 0) {
    return { samples: 0, p50: null, p95: null, p99: null, avg: null, max: null }
  }
  const sum = sorted.reduce((a, b) => a + b, 0)
  return {
    samples: sorted.length,
    p50: percentile(sorted, 50),
    p95: percentile(sorted, 95),
    p99: percentile(sorted, 99),
    avg: Number((sum / sorted.length).toFixed(1)),
    max: Number(sorted[sorted.length - 1].toFixed(1))
  }
}

async function main() {
  deadline = performance.now() + durationSec * 1000
  const startedAt = performance.now()
  await Promise.all(Array.from({ length: concurrency }, () => worker()))
  const wallMs = performance.now() - startedAt
  agent.destroy()

  const total = ok + failed
  const summary = {
    target: opts.url,
    method,
    stream,
    concurrency,
    durationSec,
    timeoutSec: timeoutMs / 1000,
    total,
    ok,
    failed,
    rps: Number((total / (wallMs / 1000)).toFixed(1)),
    // 成功请求的延迟才是「服务延迟」；全部失败时它是 null，不是 0
    latencyMs: summarize(okLatencies),
    // 失败请求的耗时说明「失败得多快」：快速 401 与超时是两种完全不同的故障
    failedLatencyMs: summarize(failedLatencies),
    errorRate: Number(((total === 0 ? 0 : failed / total) * 100).toFixed(2)),
    errorKinds: Object.fromEntries(errorKinds),
    failures
  }

  if (jsonOnly) {
    console.log(JSON.stringify(summary))
    return
  }

  const fmt = (s) => (s.samples === 0
    ? '无成功样本'
    : `p50 ${s.p50}ms  p95 ${s.p95}ms  p99 ${s.p99}ms  avg ${s.avg}ms  max ${s.max}ms`)

  console.log('=== 压测结果 ===')
  const authLabel = apiKey ? 'API Key' : token ? 'JWT' : '无凭证'
  console.log(`目标        ${summary.target}${stream ? '  (stream)' : ''}  [${method}]  认证 ${authLabel}`)
  console.log(`并发/时长   ${concurrency} / ${durationSec}s（单请求超时 ${timeoutMs / 1000}s）`)
  console.log(`请求数      ${summary.total}（成功 ${ok}，失败 ${failed}）`)
  console.log(`RPS         ${summary.rps}`)
  console.log(`成功延迟    ${fmt(summary.latencyMs)}`)
  if (failed > 0) {
    console.log(`失败延迟    ${fmt(summary.failedLatencyMs)}`)
    console.log(`失败分类    ${JSON.stringify(summary.errorKinds)}`)
    console.log(`失败样例    ${JSON.stringify(failures)}`)
  }
  console.log(`错误率      ${summary.errorRate}%`)
  if (ok === 0 && failed > 0) {
    console.log('')
    console.log('注意        本轮没有任何成功请求，上面的「成功延迟」为空 —— 这不是「延迟很好」，')
    console.log('            而是所有请求都失败了。容量结论要用「失败延迟」+「失败分类」来读。')
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
      // --header 允许重复，其余参数后出现者覆盖先出现者
      if (key === 'header') {
        out.headerArray = [...(out.headerArray || []), next]
      } else {
        out[key] = next
      }
      i += 1
    }
  }
  return out
}

/** `--header 'X-Foo: bar'`（可重复）→ 小写键的对象。 */
function parseHeaderList(raw) {
  const list = raw === undefined ? [] : Array.isArray(raw) ? raw : [raw]
  const headers = {}
  for (const item of list) {
    if (!item || item === true) continue
    const index = String(item).indexOf(':')
    if (index <= 0) continue
    headers[String(item).slice(0, index).trim().toLowerCase()] = String(item).slice(index + 1).trim()
  }
  return headers
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
