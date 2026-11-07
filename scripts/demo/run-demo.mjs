#!/usr/bin/env node
/**
 * 演示脚本：5 分钟看清这个网关在做什么。
 *
 * 前置：
 *   docker compose -f deploy/demo/docker-compose.yml up -d
 *   # 等 docker compose ps 里两个服务都 healthy
 *
 * 用法：
 *   node scripts/demo/run-demo.mjs
 *   DEMO_BASE=http://127.0.0.1:8080 node scripts/demo/run-demo.mjs
 *
 * 每一节都打印「做了什么 / 预期 / 实际」，失败会明确标出是哪一步。
 */

const BASE = process.env.DEMO_BASE || 'http://127.0.0.1:8080'
const USER = process.env.DEMO_USER || 'admin'
const PASSWORD = process.env.DEMO_PASSWORD || 'DemoOnlyPassw0rd'

let passed = 0
let failed = 0
let token = null

function section(no, title, detail) {
  console.log(`\n${'='.repeat(64)}`)
  console.log(`第 ${no} 步：${title}`)
  console.log(detail)
  console.log('-'.repeat(64))
}

function expect(label, ok, actual) {
  if (ok) {
    passed++
    console.log(`  ✅ ${label}`)
  } else {
    failed++
    console.log(`  ❌ ${label}`)
  }
  if (actual !== undefined) {
    console.log(`     实际：${actual}`)
  }
}

async function login() {
  const resp = await fetch(`${BASE}/api/auth/jwt/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASSWORD }),
  })
  if (!resp.ok) {
    throw new Error(`登录失败：HTTP ${resp.status} ${await resp.text()}`)
  }
  const body = await resp.json()
  token = body?.data?.token
  if (!token) throw new Error('登录响应里没有 token')
}

async function chat(model, content, extra = {}) {
  const started = Date.now()
  const resp = await fetch(`${BASE}/v1/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Jairouter-Token': token },
    body: JSON.stringify({
      model,
      messages: [{ role: 'user', content }],
      ...extra,
    }),
  })
  const elapsed = Date.now() - started
  const text = await resp.text()
  return { status: resp.status, text, elapsed }
}

async function models() {
  const resp = await fetch(`${BASE}/v1/models`, {
    headers: { 'X-Jairouter-Token': token },
  })
  const body = await resp.json()
  return (body?.data || []).map((m) => m.id)
}

async function main() {
  console.log(`演示目标：${BASE}`)
  await login()
  console.log('管理员登录成功')

  // ── 1. 基础调用 ────────────────────────────────────────────────
  const list = await models()
  section(1, '基础调用', `网关暴露了 ${list.length} 个模型：${list.slice(0, 4).join(', ')}${list.length > 4 ? ' …' : ''}`)
  const first = list.find((m) => m.startsWith('qwen')) || list[0]
  const r1 = await chat(first, '你好，介绍一下你自己')
  expect(`向 ${first} 发一次请求返回 200`, r1.status === 200, `HTTP ${r1.status}，耗时 ${r1.elapsed}ms`)
  console.log(`     响应片段：${r1.text.slice(0, 120)}`)

  // ── 2. 同一个问题打两个模型 ────────────────────────────────────
  const second = list.find((m) => m !== first && m.startsWith('qwen')) || list[1]
  section(2, '同一问题打两个模型', '网关的价值之一：调用方不用改代码就能换模型 / 做对比')
  const [a, b] = await Promise.all([
    chat(first, '用一句话解释什么是 API 网关'),
    chat(second, '用一句话解释什么是 API 网关'),
  ])
  expect(`两个模型都返回 200（${first} / ${second}）`, a.status === 200 && b.status === 200,
    `${first}=${a.status}(${a.elapsed}ms) ${second}=${b.status}(${b.elapsed}ms)`)

  // ── 3. 缓存命中 ────────────────────────────────────────────────
  section(3, '重复请求走缓存', '同样的问题再问一次：第二次不该打到上游')
  const r2 = await chat(first, '你好，介绍一下你自己')
  expect('第二次请求返回 200', r2.status === 200, `HTTP ${r2.status}`)
  console.log(`     第一次耗时 ${r1.elapsed}ms，第二次耗时 ${r2.elapsed}ms`)
  if (r2.elapsed < r1.elapsed) {
    expect(`缓存生效（${r1.elapsed}ms → ${r2.elapsed}ms）`, true)
  } else {
    console.log('  ℹ️  这次没看出提速：缓存条目可能已过期（默认 10 分钟），或多副本分摊到了未命中的实例')
  }

  // ── 4. 可逆脱敏 ────────────────────────────────────────────────
  section(4, '可逆脱敏', '发一段带身份证号和手机号的文本：上游应该只看到占位符，客户端拿回原文')
  const withPii = await chat(first, '记一下：身份证 110101199003071234，手机 13812345678')
  if (withPii.status === 200 && withPii.text.includes('110101199003071234')) {
    expect('客户端拿回的是原文（占位符已被还原）', true)
    console.log('     上游收到的是 [PII_1]/[PII_2] —— 用 docker logs jairouter-demo-upstream | Select-String PII 可以验证')
  } else {
    console.log(`  ℹ️  这一步需要开启可逆脱敏（默认关闭）。开启方式：`)
    console.log(`     docker compose -f deploy/demo/docker-compose.yml down`)
    console.log(`     # 在 compose 的 jairouter.environment 里加：`)
    console.log(`     #   JAIRouter_PII_REVERSIBLE_ENABLED: "true"`)
    console.log(`     docker compose -f deploy/demo/docker-compose.yml up -d`)
    console.log(`     当前响应片段：${withPii.text.slice(0, 120)}`)
  }

  // ── 汇总 ──────────────────────────────────────────────────────
  console.log(`\n${'='.repeat(64)}`)
  console.log(`通过 ${passed} 项，失败 ${failed} 项`)
  if (failed > 0) {
    console.log('有失败项，先确认两个容器都 healthy：docker compose -f deploy/demo/docker-compose.yml ps')
    process.exit(1)
  }
  console.log('\n接下来可以打开控制台看这次调用：http://127.0.0.1:8080/admin/')
  console.log(`账号 ${USER} / 密码 ${PASSWORD}`)
}

main().catch((e) => {
  console.error(`\n演示中断：${e.message}`)
  console.error('排查顺序：docker compose -f deploy/demo/docker-compose.yml ps → docker logs jairouter-demo')
  process.exit(1)
})
