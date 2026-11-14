/**
 * Contract preflight: verify that the gateway still exposes everything the
 * SLA tooling depends on.
 *
 * Why this exists: tools under sla/ are not in the repository, so no CI job
 * can run them. They drift silently whenever the gateway's API changes. This
 * module is the runtime half of the drift guard; the repository-side half is
 * SlaSurfaceContractTest, which snapshots the same surface and fails the pull
 * request when it moves.
 *
 * The rule enforced here: a missing field must produce "这个工具与当前网关版本
 * 不兼容", never a downstream "检查失败". Reporting an incompatibility as a
 * product defect wastes an incident's worth of time.
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { api, login, scrapeMetrics } from './client.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_CONTRACT = join(HERE, '..', 'contract.json');

export function loadContract(path = DEFAULT_CONTRACT) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

function parseVersion(text) {
  const m = String(text || '').match(/(\d+)\.(\d+)\.(\d+)/);
  return m ? m.slice(1).map(Number) : null;
}

function compareVersions(a, b) {
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] - b[i];
  }
  return 0;
}

/** Evaluate a range like ">=3.7.0 <4.0.0" against a concrete version. */
export function inRange(version, range) {
  const v = parseVersion(version);
  if (!v) return null;
  const clauses = String(range || '').match(/[<>]=?\s*\d+\.\d+\.\d+/g) || [];
  for (const clause of clauses) {
    const op = clause.match(/^(>=|<=|>|<)/)[1];
    const target = parseVersion(clause);
    const cmp = compareVersions(v, target);
    if (op === '>=' && cmp < 0) return false;
    if (op === '<=' && cmp > 0) return false;
    if (op === '>' && cmp <= 0) return false;
    if (op === '<' && cmp >= 0) return false;
  }
  return true;
}

function getPath(obj, dotted) {
  return dotted.split('.').reduce((acc, key) => (acc == null ? acc : acc[key]), obj);
}

async function resolve(ctx, endpoint) {
  if (endpoint.path === '/actuator/prometheus') {
    // Prometheus exposition is plain text; the generic JSON client would get 406.
    const metrics = await scrapeMetrics(ctx.base);
    return metrics.size > 0 ? { data: true } : {};
  }
  return api(ctx.base, endpoint.path.replace('{type}', ctx.serviceType ?? 'chat'), {
    method: endpoint.method || 'GET',
    token: ctx.token,
  });
}

/**
 * Run every contract check. Returns { ok, version, checks[] }.
 *
 * A check is one of:
 *   kind: "endpoint" — the body must contain the dotted field
 *   kind: "status"   — the endpoint must respond 2xx
 *   kind: "metric"   — the metric sample name must be present
 */

/**
 * 探测网关版本。只认「产品版本」，绝不采用 `/actuator/info` 的 `config.serviceVersion`
 * —— 那是 tracing 组件的配置值（TracingConfiguration.serviceVersion，默认 1.0.0），
 * 拿它当产品版本会得出「1.0.0 不兼容」的错误结论。
 *
 * 优先 `/actuator/info` 的 `build.version`（pom.xml 的 spring-boot-maven-plugin build-info
 * 生成 META-INF/build-info.properties 后才有），其次 `app.version`。
 * 探测不到返回 null —— 由调用方决定这是否算失败。
 */
export async function detectVersion(base, token) {
  try {
    const info = await api(base, '/actuator/info', { token });
    const candidates = [
      ['build.version', getPath(info, 'build.version')],
      ['app.version', getPath(info, 'app.version')],
    ];
    for (const [path, value] of candidates) {
      if (typeof value === 'string' && /\d+\.\d+\.\d+/.test(value)) {
        return { version: value, source: `/actuator/info ${path}` };
      }
    }
  } catch {
    // 版本探测失败不是服务故障；下面的判定会把它变成一条写明出路的 FAIL。
  }
  return null;
}

export async function preflight({ base, username, password, contract = loadContract(), version, allowUnknownVersion = false } = {}) {
  const checks = [];
  const push = (id, ok, detail) => checks.push({ id, ok, detail });

  let token = null;
  try {
    token = await login(base, username, password);
    push('auth.login', true, 'data.token');
  } catch (err) {
    push('auth.login', false, err.message);
  }

  const ctx = { base, token };

  for (const endpoint of contract.endpoints || []) {
    if (endpoint.kind === 'metric') {
      try {
        const metrics = await scrapeMetrics(base);
        const hit = [...metrics.keys()].some((name) => name.startsWith(endpoint.name));
        push(endpoint.id, hit, endpoint.name);
      } catch (err) {
        push(endpoint.id, false, err.message);
      }
      continue;
    }
    try {
      const body = await resolve(ctx, endpoint);
      if (endpoint.kind === 'status') {
        push(endpoint.id, true, `HTTP ${endpoint.method || 'GET'} ${endpoint.path}`);
        continue;
      }
      const value = getPath(body, endpoint.expect);
      push(endpoint.id, value !== undefined, `${endpoint.path} → ${endpoint.expect}`);
    } catch (err) {
      push(endpoint.id, false, err.message);
    }
  }

  // 版本判定：显式 --version 优先，其次自动探测。契约的 targetRange 是硬门槛 ——
  // 网关版本超出范围时，工具的判据本身可能已经不成立，必须换新工具链而不是报「服务故障」。
  const detected = version ? { version, source: '--version' } : await detectVersion(base, token);
  const effectiveVersion = detected?.version ?? null;
  const rangeOk = effectiveVersion && contract.targetRange ? inRange(effectiveVersion, contract.targetRange) : null;
  const versionBlocks = rangeOk === false || (rangeOk === null && !allowUnknownVersion);

  return {
    ok: checks.every((c) => c.ok) && !versionBlocks,
    version: effectiveVersion,
    versionSource: detected?.source ?? null,
    targetRange: contract.targetRange ?? null,
    versionOk: rangeOk,
    versionBlocks,
    checks,
  };
}

export function formatPreflight(result) {
  const lines = [];
  for (const check of result.checks) {
    const mark = check.ok ? '[ OK ]' : '[FAIL]';
    lines.push(`${mark} ${check.id.padEnd(22)} ${check.detail ?? ''}`.trimEnd());
  }
  const range = result.targetRange || '(未声明)';
  if (result.versionOk === false) {
    lines.push(`[FAIL] version.range         当前 ${result.version} ∉ ${range}（来源 ${result.versionSource}）`);
    lines.push('       └ 网关版本超出本工具链支持范围：请换用与该版本匹配的 sla/ 工具链，'
      + '不要把它当成服务故障。');
  } else if (result.versionOk === true) {
    lines.push(`[ OK ] version.range         ${result.version} ∈ ${range}（来源 ${result.versionSource}）`);
  } else if (result.versionBlocks) {
    lines.push(`[FAIL] version.range         无法确认网关版本，适用范围 ${range}`);
    lines.push('       └ 无法确认版本就无法保证判据有效。优先让网关暴露 build.version'
      + '（/actuator/info，由 spring-boot-maven-plugin 的 build-info 生成），'
      + '或显式传 --version 3.8.1；确实要跳过时用 --allow-unknown-version。');
  } else {
    lines.push(`[WARN] version.range         无法确认网关版本（已用 --allow-unknown-version 跳过），范围 ${range}`);
  }
  return lines.join('\n');
}
