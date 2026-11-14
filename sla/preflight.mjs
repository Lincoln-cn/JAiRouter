#!/usr/bin/env node
/**
 * CLI for the contract preflight.
 *
 * Run this before any other SLA tool, and in every scheduled patrol. It
 * answers one question: "does this gateway still speak the contract the
 * tooling was written against?" If it fails, everything downstream is
 * meaningless — report it as a tooling incompatibility, not as an incident.
 *
 * Usage:
 *   node sla/preflight.mjs --base http://gw:8080 --user admin --pass secret
 *   node sla/preflight.mjs --base ... --version 3.7.3
 *   node sla/preflight.mjs --base ... --allow-unknown-version
 *
 * 版本判定：显式 `--version` 优先，其次自动读 `/actuator/info` 的 `build.version`。
 * 两者都拿不到时默认判 FAIL —— 无法确认版本就无法保证判据有效（网关超出 targetRange
 * 时应换新工具链，而不是把工具的不兼容报成服务故障）。确实要跳过用 --allow-unknown-version。
 */

import { parseArgs } from 'node:util';
import { preflight, formatPreflight } from './lib/preflight.mjs';

const { values } = parseArgs({
  options: {
    base: { type: 'string', default: process.env.SLA_BASE || 'http://127.0.0.1:8080' },
    user: { type: 'string', default: process.env.SLA_USER || 'admin' },
    pass: { type: 'string', default: process.env.SLA_PASS || '' },
    version: { type: 'string', default: process.env.SLA_VERSION },
    'allow-unknown-version': { type: 'boolean', default: false },
    json: { type: 'boolean', default: false },
  },
  allowPositionals: true,
});

const result = await preflight({
  base: values.base,
  username: values.user,
  password: values.pass,
  version: values.version,
  allowUnknownVersion: values['allow-unknown-version'],
});

if (values.json) {
  console.log(JSON.stringify(result, null, 2));
} else {
  console.log(formatPreflight(result));
}

// Use exitCode rather than process.exit(): on Windows, exiting while fetch
// keep-alive sockets are still open trips a libuv assertion
// (!(handle->flags & UV_HANDLE_CLOSING)).
process.exitCode = result.ok ? 0 : 2;
