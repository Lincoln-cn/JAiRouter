/**
 * JAiRouter 前端全量 E2E Runner
 *
 * 用法:
 *   cd frontend && npm run test:e2e
 *   node e2e/runner.mjs --only smoke
 *   node e2e/runner.mjs --only auth,deep
 *   node e2e/runner.mjs --list
 *
 * 环境变量:
 *   E2E_BASE_URL  默认 http://127.0.0.1:8080/admin
 *   E2E_USER / E2E_PASS
 *   CHROMIUM_PATH 可选 headless shell 路径
 */
import { readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { BASE, USER, PASS, createReporter, launchBrowser } from './harness.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SPECS_DIR = path.join(__dirname, 'specs')

function parseArgs(argv) {
  const args = { only: null, list: false, specs: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--list') args.list = true
    else if (a === '--only') args.only = String(argv[++i] || '').split(',').map(s => s.trim()).filter(Boolean)
    else if (a.startsWith('--only=')) args.only = a.slice(7).split(',').map(s => s.trim()).filter(Boolean)
    else if (!a.startsWith('-')) args.specs.push(a)
  }
  return args
}

async function loadSpecs() {
  const files = readdirSync(SPECS_DIR)
    .filter(f => f.endsWith('.mjs') || f.endsWith('.js'))
    .sort()
  const specs = []
  for (const f of files) {
    const mod = await import(pathToFileURL(path.join(SPECS_DIR, f)).href)
    const spec = mod.default
    if (!spec || typeof spec.run !== 'function') {
      console.warn(`SKIP invalid spec: ${f}`)
      continue
    }
    specs.push({ order: 100, ...spec, file: f })
  }
  specs.sort((a, b) => (a.order - b.order) || a.id.localeCompare(b.id))
  return specs
}

function filterSpecs(specs, args) {
  let out = specs
  if (args.only?.length) {
    out = out.filter(s => {
      const tags = [s.suite, s.id, s.file.replace(/\.(mjs|js)$/, '')]
      return args.only.some(w => tags.some(t => t === w || t.includes(w)))
    })
  }
  if (args.specs?.length) {
    out = out.filter(s => args.specs.some(w => s.file.includes(w) || s.id.includes(w)))
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const allSpecs = await loadSpecs()

if (args.list) {
  console.log('Available e2e specs:')
  for (const s of allSpecs) {
    console.log(`  [${s.suite || s.id}] ${s.id} — ${s.name} (${s.file})`)
  }
  process.exit(0)
}

const specs = filterSpecs(allSpecs, args)
if (!specs.length) {
  console.error('No specs matched. Use --list to see available specs.')
  process.exit(1)
}

console.log(`JAiRouter E2E | base=${BASE} user=${USER} specs=${specs.length}`)
console.log(`Specs: ${specs.map(s => s.id).join(', ')}`)

const browser = await launchBrowser()
const allResults = []

try {
  for (const spec of specs) {
    console.log(`\n======== RUN ${spec.id} | ${spec.name} ========`)
    const reporter = createReporter(spec.id)
    const ctx = {
      browser,
      check: reporter.check,
      results: reporter.results,
      base: BASE,
      user: USER,
      pass: PASS,
      spec
    }
    try {
      await spec.run(ctx)
    } catch (e) {
      reporter.check(`suite:${spec.id}:aborted`, false, e?.message || String(e))
    }
    allResults.push(...reporter.results)
  }
} finally {
  await browser.close().catch(() => {})
}

const failed = allResults.filter(r => !r.ok)
const passed = allResults.length - failed.length
console.log(`\n${'='.repeat(48)}`)
console.log(`E2E SUMMARY: ${passed}/${allResults.length} PASS`)
if (failed.length) {
  console.log(`Failed (${failed.length}):`)
  for (const f of failed) {
    console.log(`  - [${f.suite}] ${f.name} | ${f.detail}`)
  }
}
process.exit(failed.length ? 1 : 0)
