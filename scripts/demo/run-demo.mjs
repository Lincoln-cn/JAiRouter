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

/**
 * 某类服务下的实例名。
 *
 * `/v1/models` 把所有类型混在一起列（对话、embedding、rerank、TTS、ASR、图像），
 * 但只有对话实例能走 /v1/chat/completions —— 拿别的去对话会 404。
 * 想知道「有几个能对话的模型」，只能问管理端。
 */
async function instances(type) {
  const resp = await fetch(`${BASE}/api/config/instance/${type}`, {
    headers: { 'X-Jairouter-Token': token },
  })
  if (!resp.ok) return []
  const body = await resp.json()
  return (body?.data || []).map((i) => i.name).filter(Boolean)
}

async function main() {
  console.log(`演示目标：${BASE}`)
  await login()
  console.log('管理员登录成功')

  // ── 1. 基础调用 ────────────────────────────────────────────────
  const list = await models()
  section(1, '基础调用', `网关暴露了 ${list.length} 个模型：${list.slice(0, 4).join(', ')}${list.length > 4 ? ' …' : ''}`)
  const chatable = await instances('chat')
  const first = chatable.find((m) => m.startsWith('qwen')) || chatable[0] || list[0]
  const r1 = await chat(first, '你好，介绍一下你自己')
  expect(`向 ${first} 发一次请求返回 200`, r1.status === 200, `HTTP ${r1.status}，耗时 ${r1.elapsed}ms`)
  console.log(`     响应片段：${r1.text.slice(0, 120)}`)

  // ── 2. 同一个问题打两个模型 ────────────────────────────────────
  const second = chatable.find((m) => m !== first)
  section(2, '同一问题打两个模型', '网关的价值之一：调用方不用改代码就能换模型 / 做对比')
  if (!second) {
    // 演示环境默认只配了一个对话模型（其余是 embedding / rerank）。
    // 这一步说明的是「多模型并存时怎么换」，没有第二个模型就如实说没有，不判失败。
    console.log(`  ℹ️  当前只暴露了 ${chatable.length} 个对话模型（${first}），这一步需要至少两个`)
    console.log(`     在 services.yml 的 chat 实例列表里再加一个，重启即可；客户端代码不用改。`)
  } else {
    const [a, b] = await Promise.all([
      chat(first, '用一句话解释什么是 API 网关'),
      chat(second, '用一句话解释什么是 API 网关'),
    ])
    expect(`两个模型都返回 200（${first} / ${second}）`, a.status === 200 && b.status === 200,
      `${first}=${a.status}(${a.elapsed}ms) ${second}=${b.status}(${b.elapsed}ms)`)
  }

  // ── 3. 缓存命中 ────────────────────────────────────────────────
  section(3, '重复请求走缓存', '同样的问题再问一次：第二次不该打到上游')
  const r2 = await chat(first, '你好，介绍一下你自己')
  expect('第二次请求返回 200', r2.status === 200, `HTTP ${r2.status}`)
  console.log(`     第一次耗时 ${r1.elapsed}ms，第二次耗时 ${r2.elapsed}ms`)
  if (r1.status !== 200 || r2.status !== 200) {
    console.log('  ℹ️  两次请求里有一次没成功，缓存对比不成立（先看第 1 步的失败原因）')
  } else if (r2.elapsed < r1.elapsed) {
    expect(`缓存生效（${r1.elapsed}ms → ${r2.elapsed}ms）`, true)
  } else {
    console.log('  ℹ️  这次没看出提速：缓存条目可能已过期（默认 10 分钟），或多副本分摊到了未命中的实例')
  }

  // ── 4. 可逆脱敏 ────────────────────────────────────────────────
  section(4, '可逆脱敏', '发一段带身份证号和手机号的文本：开启后上游只该收到 [PII_n] 占位符，客户端拿回原文')
  const withPii = await chat(first, '记一下：身份证 110101199003071234，手机 13812345678')
  expect('请求返回 200', withPii.status === 200, `HTTP ${withPii.status}`)
  console.log(`     响应片段：${withPii.text.slice(0, 120)}`)
  // 这一步的判定在上游侧，不在客户端：可逆脱敏开启与否，客户端拿回的都是原文
  // （这正是它的用途），所以只能看 mock 上游收到了什么形状。
  console.log('     可逆脱敏默认关闭。看上游收到的形状来判定：')
  console.log('       bash:       docker logs jairouter-demo-upstream | grep lastUser')
  console.log('       PowerShell: docker logs jairouter-demo-upstream | Select-String lastUser')
  console.log('       lastUser placeholders=[PII_1],[PII_2]  → 上游没拿到明文（脱敏生效）')
  console.log('       lastUser plaintext(NN B)                → 上游拿到了明文（未开启，或没生效）')
  console.log('     开启方式：在 deploy/demo/docker-compose.yml 的 jairouter.environment 里加')
  console.log('       JAIRouter_PII_REVERSIBLE_ENABLED: "true"')
  console.log('     再 docker compose -f deploy/demo/docker-compose.yml up -d 重启网关。')


  // ── 汇总 ──────────────────────────────────────────────────────
  console.log(`\n${'='.repeat(64)}`)
  console.log(`通过 ${passed} 项，失败 ${failed} 项`)
  if (failed > 0) {
    console.log('有失败项，先确认两个容器都 healthy：docker compose -f deploy/demo/docker-compose.yml ps')
    process.exit(1)
  }
  console.log(`\n接下来可以打开控制台看这次调用：${BASE}/admin/`)
  // 不回显口令：值来自 deploy/demo/docker-compose.yml 的 INITIAL_ADMIN_PASSWORD，
  // 或你运行前设置的 $DEMO_PASSWORD。写进日志会被 CodeQL 的 clear-text-logging 判为明文泄漏。
  console.log(`账号 ${USER}`)
  console.log('密码见 deploy/demo/docker-compose.yml 的 INITIAL_ADMIN_PASSWORD；用 $DEMO_PASSWORD 覆盖时就是你设的那个')
}

main().catch((e) => {
  console.error(`\n演示中断：${e.message}`)
  console.error('排查顺序：docker compose -f deploy/demo/docker-compose.yml ps → docker logs jairouter-demo')
  process.exit(1)
})
