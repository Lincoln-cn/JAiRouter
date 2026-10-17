/**
 * JAiRouter 接口冒烟（可复跑资产，非一次性脚本）
 *
 * 为什么存在：本仓的对外语义在 `#404`（API Key 限流 401→429 并拆码）、`#405`（BUILTIN_READONLY
 * 400→403）、`#432`（缺请求体 500→400）、`#340`（`/actuator` 不再整段豁免）、`#448`（`/api/**`
 * 错误体形状）之后变化频繁，而这些恰恰是**客户端最先感知**的面。单元测试与前端 E2E 都不直接
 * 断言「线上返回的状态码与错误体形状」，所以单独留一份接口层冒烟。
 *
 * 用法：
 *   # 先起服务（dev profile 即可）
 *   node scripts/test/api-smoke.mjs
 *   node scripts/test/api-smoke.mjs http://127.0.0.1:8080
 *
 * 环境变量（与 frontend/e2e/harness.mjs 同口径）：
 *   E2E_BASE_URL  默认 http://127.0.0.1:8080
 *   E2E_USER      默认 admin
 *   E2E_PASS      默认 ChangeMeOnFirstStartup123456（dev profile 的出厂口令）
 *
 * 设计约束：
 *   - 只依赖 Node 内置 fetch（无 npm 依赖），因此不进 Maven/vitest 门闩也能在任何机器跑；
 *   - 会创建并删除一个临时 API Key（`rateLimitPerMinute: 5`）用于验证限流语义，跑完自行清理；
 *   - **只断言「应当成立」的契约**。已登记但未修的问题（`#454` 的 405、`#456` 的 custom-tags
 *     谎报成功、`#457` 的空 OpenAPI）**不在此断言**，避免把缺陷固化成期望值。
 *
 * ⚠️ 它需要一个**真实运行**的实例；本仓 CI 目前没有这一步（`#454` 建议把接口对账固化进守卫）。
 */
const BASE = process.argv[2] || process.env.E2E_BASE_URL || 'http://127.0.0.1:8080'
const USER = process.env.E2E_USER || 'admin'
const PASS = process.env.E2E_PASS || 'ChangeMeOnFirstStartup123456'

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail: String(detail ?? '') })
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail ?? ''}`)
}

async function req(path, { method = 'GET', token, apiKey, body, raw, headers = {} } = {}) {
  const h = { ...headers }
  if (token) h['Jairouter_Token'] = token
  // 网关凭据头是 X-API-Key（Authorization: Bearer 是给下游 provider 的）
  if (apiKey) h['X-API-Key'] = apiKey
  let payload
  if (raw !== undefined) payload = raw
  else if (body !== undefined) {
    h['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`${BASE}${path}`, { method, headers: h, body: payload })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* non-JSON */ }
  return { status: res.status, text, json, headers: res.headers }
}

const log = (s) => console.log(`\n--- ${s} ---`)

// ---------------------------------------------------------------- 1. 登录
log('1. 登录并取 JWT')
const login = await req('/api/auth/jwt/login', {
  method: 'POST',
  body: { username: USER, password: PASS }
})
const token = login.json?.data?.token
check('login:200', login.status === 200, `status=${login.status}`)
check('login:token', !!token, token ? `len=${token.length}` : 'no token')

const badLogin = await req('/api/auth/jwt/login', {
  method: 'POST',
  body: { username: USER, password: 'definitely-wrong-password' }
})
check('login:wrong-password-not-200', badLogin.status !== 200, `status=${badLogin.status} code=${badLogin.json?.errorCode}`)
check('login:wrong-password-has-code', !!badLogin.json?.errorCode, String(badLogin.json?.errorCode))

// ------------------------------------------------- 2. 认证入口 / 401 语义
log('2. 认证入口（401 而非 403/302）与静态面')
const noTok = await req('/api/config/adapter/list')
check('api:no-token-401', noTok.status === 401, `status=${noTok.status}`)
const badTok = await req('/api/config/adapter/list', { token: 'not-a-real-token' })
check('api:bad-token-401', badTok.status === 401, `status=${badTok.status}`)
const okTok = await req('/api/config/adapter/list', { token })
check('api:good-token-200', okTok.status === 200, `status=${okTok.status}`)

const adminHtml = await req('/admin/')
check('admin:static-anonymous-200', adminHtml.status === 200, `status=${adminHtml.status}`)
const spaDeep = await req('/admin/config/instances')
check('admin:spa-deeplink-200', spaDeep.status === 200, `status=${spaDeep.status}`)

// ------------------------------------------- 3. /actuator 认证面（#340）
log('3. /actuator 认证面（#340 取消整段豁免，只留白名单）')
// 这里断言的是「匿名可达」（认证面），不是健康结论：聚合健康可以是 503（DOWN），
// 那属于业务判定，不能与「被认证拦住（401）」混为一谈。
for (const p of ['/actuator/health', '/actuator/info', '/actuator/prometheus']) {
  const r = await req(p)
  check(`actuator:anonymous-reachable${p}`, r.status !== 401, `status=${r.status}`)
}
const aggHealth = await req('/actuator/health')
if (aggHealth.status === 503) {
  const down = Object.entries(aggHealth.json?.components || {})
    .filter(([, v]) => v?.status === 'DOWN')
    .map(([k, v]) => `${k}:${v?.details?.issue || v?.details?.reason || ''}`)
  check('actuator:aggregate-health-up', false, `503 DOWN components=${down.join(' | ')}`)
} else {
  check('actuator:aggregate-health-up', aggHealth.status === 200, `status=${aggHealth.status}`)
}
for (const p of ['/actuator/env', '/actuator/beans', '/actuator/configprops']) {
  const anon = await req(p)
  check(`actuator:anon-denied${p}`, anon.status === 401, `status=${anon.status}`)
  const withTok = await req(p, { token })
  check(`actuator:admin-allowed${p}`, withTok.status === 200, `status=${withTok.status}`)
}

// ----------------------------------- 4. /v1 协议错误体形状（OpenAI/Anthropic）
log('4. /v1 协议错误体形状')
const v1chat = await req('/v1/chat/completions', {
  method: 'POST',
  body: { model: 'x', messages: [{ role: 'user', content: 'hi' }] }
})
check('v1:chat-unauth-401', v1chat.status === 401, `status=${v1chat.status}`)
check('v1:chat-openai-shape', !!v1chat.json?.error && typeof v1chat.json.error.message === 'string',
  JSON.stringify(v1chat.json)?.slice(0, 160))
check('v1:chat-openai-type', v1chat.json?.error?.type === 'authentication_error',
  String(v1chat.json?.error?.type))

const v1models = await req('/v1/models')
check('v1:models-unauth-401', v1models.status === 401, `status=${v1models.status}`)
check('v1:models-openai-shape', !!v1models.json?.error, JSON.stringify(v1models.json)?.slice(0, 120))

const v1messages = await req('/v1/messages', {
  method: 'POST',
  body: { model: 'x', max_tokens: 16, messages: [{ role: 'user', content: 'hi' }] }
})
check('v1:messages-unauth-401', v1messages.status === 401, `status=${v1messages.status}`)
check('v1:messages-anthropic-shape', v1messages.json?.type === 'error' && !!v1messages.json?.error,
  JSON.stringify(v1messages.json)?.slice(0, 160))
const anthType = v1messages.json?.error?.type
check('v1:messages-anthropic-error-type', anthType === 'authentication_error', String(anthType))

// 管理面错误体形状：/api/** 上「谁写的」决定形状（#448 已按实测写进文档）。
// 认证链自己写的 401/429 是协议形状；授权层拦下的 403 是 text/plain；controller 抛的才是 RouterResponse。
const api401 = noTok // GET /api/config/adapter/list 无凭据
check('api:401-auth-chain-shape', api401.json?.error?.code === 'AUTH_MISSING',
  `body=${api401.text.slice(0, 140)}`)
check('api:401-auth-type', api401.json?.error?.type === 'authentication_error',
  String(api401.json?.error?.type))

// 授权层 403：用低权限账号打 ADMIN 端点（应为 text/plain，非 RouterResponse）
if (token) {
  const userLogin = await req('/api/auth/jwt/login', {
    method: 'POST', body: { username: 'user', password: 'user123' }
  })
  const userToken = userLogin.json?.data?.token
  if (userToken) {
    const rbac403 = await req('/api/auth/api-keys', { token: userToken })
    check('api:rbac-403-status', rbac403.status === 403, `status=${rbac403.status}`)
    check('api:rbac-403-text-plain', /access denied/i.test(rbac403.text) && !rbac403.json,
      `body=${rbac403.text.slice(0, 80)} ct=${rbac403.headers.get('content-type')}`)
  } else {
    check('api:rbac-403-status', false, `低权限账号登录失败: ${userLogin.status}`)
  }
}

// 对照：controller 抛的 403 仍是 RouterResponse（BUILTIN_READONLY，见第 5 节）
// 对照：controller 抛的 400 仍是 RouterResponse（第 6 节已断言 errorCode）

// --------------------------- 5. 内置 adapter 只读语义（#405：403 + BUILTIN_READONLY）
log('5. 内置 adapter 只读语义（#405 由 400 改 403）')
const adapterList = await req('/api/config/adapter/list', { token })
const builtin = (adapterList.json?.data || []).find((a) => a.source === 'builtin')
check('adapter:builtin-discovered', !!builtin, builtin ? builtin.name : 'none found')

if (builtin) {
  const put = await req(`/api/config/adapter/${encodeURIComponent(builtin.name)}`, {
    method: 'PUT',
    token,
    body: { name: builtin.name, type: 'openai-compatible', baseUrl: 'http://127.0.0.1:9099' }
  })
  check('adapter:put-builtin-403', put.status === 403, `status=${put.status}`)
  check('adapter:put-builtin-code', put.json?.errorCode === 'BUILTIN_READONLY',
    `code=${put.json?.errorCode}`)

  const del = await req(`/api/config/adapter/${encodeURIComponent(builtin.name)}`, { method: 'DELETE', token })
  check('adapter:delete-builtin-403', del.status === 403, `status=${del.status}`)
  check('adapter:delete-builtin-code', del.json?.errorCode === 'BUILTIN_READONLY',
    `code=${del.json?.errorCode}`)
}

// ---------------------- 6. 缺失请求体 → 400（#432，不得被兜底 catch 做成 500）
log('6. 缺失请求体 → 400（#432）')
const noBodyCases = [
  ['POST', '/api/config/adapter'],
  ['PUT', '/api/config/adapter/some-nonexistent-adapter'],
  ['PUT', '/api/loadbalancer/config/chat']
]
for (const [method, path] of noBodyCases) {
  const r = await req(path, { method, token })
  check(`nobody:${method} ${path}:400`, r.status === 400, `status=${r.status} code=${r.json?.errorCode} msg=${r.json?.message}`)
  check(`nobody:${method} ${path}:not-500`, r.status !== 500, `status=${r.status}`)
}

// 非法 JSON 体（不是「缺失」但同一前置校验族）
const badJson = await req('/api/config/adapter', {
  method: 'POST', token, raw: '{ this is not json', headers: { 'Content-Type': 'application/json' }
})
check('badjson:POST /api/config/adapter:400', badJson.status === 400, `status=${badJson.status}`)

// --------------------------------- 7. API Key 全链 + 限流拆码（#404）
log('7. API Key 创建 → /v1 使用 → 限流 429 + RATE_LIMITED_API_KEY')
const keyName = `e2e-smoke-${Date.now().toString(36)}`
const created = await req('/api/auth/api-keys', {
  method: 'POST',
  token,
  body: { description: keyName, permissions: ['READ'], enabled: true, rateLimitPerMinute: 5 }
})
const apiKeyValue = created.json?.data?.keyValue
const apiKeyId = created.json?.data?.keyId || created.json?.data?.id
check('apikey:create-200', created.status === 200, `status=${created.status} msg=${created.json?.message}`)
// 刻意不打印密钥的任何片段（哪怕前 8 位）：CodeQL 把「记录 API Key 值」判为 high 级告警，
// 而这个脚本是要入库的。只断言「拿到了」。
check('apikey:value-returned', !!apiKeyValue, apiKeyValue ? 'returned (value not logged)' : 'none')

if (apiKeyValue) {
  const models = await req('/v1/models', { apiKey: apiKeyValue })
  check('apikey:models-200', models.status === 200, `status=${models.status} body=${models.text.slice(0, 120)}`)

  // 每分钟限流 5：连打 12 次，必须出现 429，且错误码/协议 type 正确
  let firstLimit = null
  let hits = 0
  for (let i = 0; i < 12; i++) {
    const r = await req('/v1/models', { apiKey: apiKeyValue })
    if (r.status === 429) { hits++; if (!firstLimit) firstLimit = r }
  }
  check('apikey:ratelimit-429-observed', hits > 0, `429 hits=${hits}/12`)
  if (firstLimit) {
    check('apikey:ratelimit-429-not-401', firstLimit.status === 429, `status=${firstLimit.status}`)
    check('apikey:ratelimit-code', firstLimit.json?.error?.code === 'RATE_LIMITED_API_KEY',
      `code=${firstLimit.json?.error?.code}`)
    check('apikey:ratelimit-openai-type', firstLimit.json?.error?.type === 'rate_limit_error',
      `type=${firstLimit.json?.error?.type}`)
  } else {
    check('apikey:ratelimit-code', false, 'no 429 observed')
  }
}

// --------------------------------------------------- 8. 清理临时 API Key
log('8. 清理')
if (apiKeyId) {
  const del = await req(`/api/auth/api-keys/${encodeURIComponent(apiKeyId)}`, { method: 'DELETE', token })
  check('cleanup:apikey-deleted', del.status === 200, `status=${del.status}`)
} else {
  check('cleanup:apikey-deleted', false, 'no keyId to delete')
}

// ---------------------------------------------------------------- 汇总
const failed = results.filter((r) => !r.ok)
console.log(`\n${'='.repeat(60)}`)
console.log(`API SMOKE: ${results.length - failed.length}/${results.length} PASS`)
if (failed.length) {
  console.log(`Failed (${failed.length}):`)
  for (const f of failed) console.log(`  - ${f.name} | ${f.detail}`)
}
process.exit(failed.length ? 1 : 0)
