/**
 * Shared helpers for SLA tooling: login, API calls, metric scraping.
 *
 * Every tool under sla/ goes through this module so that "how we talk to a
 * gateway" is defined once. When the gateway's auth or response envelope
 * changes, this is the only file that has to change.
 */

export class GatewayError extends Error {
  constructor(message, { status, body } = {}) {
    super(message);
    this.name = 'GatewayError';
    this.status = status;
    this.body = body;
  }
}

export function joinUrl(base, path) {
  return base.replace(/\/+$/, '') + (path.startsWith('/') ? path : '/' + path);
}

/**
 * Log in with username/password and return the bearer token.
 *
 * The gateway's login endpoint returns the token nested under `data.token`
 * and expects it back on outbound calls as `X-Jairouter-Token` (not the
 * usual `Authorization: Bearer`).
 */
export async function login(base, username, password, { timeoutMs = 10000 } = {}) {
  const res = await fetch(joinUrl(base, '/api/auth/jwt/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const body = await res.json().catch(() => ({}));
  const token = body?.data?.token;
  if (!res.ok || !token) {
    throw new GatewayError(`登录失败: HTTP ${res.status} ${body?.message ?? ''}`.trim(), {
      status: res.status,
      body,
    });
  }
  return token;
}

/** Call an admin API endpoint with an optional token; returns the parsed body. */
export async function api(base, path, { method = 'GET', body, token, timeoutMs = 15000 } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['X-Jairouter-Token'] = token;

  const res = await fetch(joinUrl(base, path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await res.text();
  let parsed;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = text;
  }
  if (!res.ok) {
    throw new GatewayError(`HTTP ${res.status} ${method} ${path}`, { status: res.status, body: parsed });
  }
  return parsed;
}

/** Unwrap a RouterResponse<T> down to T. */
export function unwrap(envelope) {
  if (envelope && typeof envelope === 'object' && 'data' in envelope) return envelope.data;
  return envelope;
}

/** Scrape /actuator/prometheus into a Map of sample name -> value. */
export async function scrapeMetrics(base, { timeoutMs = 10000 } = {}) {
  const res = await fetch(joinUrl(base, '/actuator/prometheus'), {
    // The actuator endpoint returns text/plain/exposition format; without an
    // explicit Accept header Spring negotiates application/json and answers 406.
    headers: { Accept: 'text/plain' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new GatewayError(`指标抓取失败: HTTP ${res.status}`, { status: res.status });
  const text = await res.text();
  const out = new Map();
  for (const line of text.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    const idx = line.lastIndexOf(' ');
    if (idx <= 0) continue;
    const name = line.slice(0, idx).trim();
    const value = Number(line.slice(idx + 1));
    if (!Number.isNaN(value)) out.set(name, value);
  }
  return out;
}
