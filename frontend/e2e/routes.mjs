/**
 * 全前端路由清单（与 src/router/index.ts + src/config/menu.ts 对齐）
 * type: auth-smoke | app | param
 * suite: 冒烟/深测归属
 */

export const ALL_ROUTES = [
  // 概览
  { path: '/dashboard/main', name: 'dashboard-main', group: 'overview', expectAny: ['.stats-wrap', '.card-panel', '.el-card'] },

  // 模型服务 / 配置
  { path: '/config/services', name: 'service-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/instances', name: 'instance-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/versions', name: 'version-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/state-persistence', name: 'state-persistence-config', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },
  { path: '/config/adapters', name: 'adapter-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/rules', name: 'rule-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/pools', name: 'pool-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/config/cache', name: 'response-cache-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },
  { path: '/config/quota', name: 'quota-config-management', group: 'config', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },

  // 流量治理
  { path: '/load-balancers/monitoring', name: 'lb-monitoring', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/load-balancers/strategy-config', name: 'lb-strategy', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-form', '.el-button'] },
  { path: '/circuit-breakers/monitoring', name: 'cb-monitoring', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/circuit-breakers/history', name: 'cb-history', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/circuit-breakers/global-config', name: 'cb-global-config', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-form', '.el-button'] },
  { path: '/rate-limiters/monitoring', name: 'rl-monitoring', group: 'traffic', expectAny: ['.el-table', '.el-card', '.el-button'] },

  // 数据记录 / 监控
  { path: '/call-history/dashboard', name: 'call-history-dashboard', group: 'records', expectAny: ['.el-card', '.el-table', '.el-statistic', 'canvas'] },
  { path: '/call-history/list', name: 'call-history-list', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/call-history/slow-calls', name: 'call-history-slow-calls', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/call-history/token-usage', name: 'call-history-token-usage', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button', 'canvas'] },
  { path: '/monitoring/slow-queries', name: 'slow-query-analysis', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/monitoring/quota', name: 'quota-usage-monitoring', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/exceptions/list', name: 'exception-list', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/exceptions/statistics', name: 'exception-statistics', group: 'records', expectAny: ['.el-table', '.el-card', '.el-button', 'canvas'] },

  // 链路追踪
  { path: '/tracing/dashboard', name: 'tracing-dashboard', group: 'tracing', expectAny: ['.el-table', '.el-card', '.el-button', 'canvas'] },
  { path: '/tracing/search', name: 'tracing-search', group: 'tracing', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },
  { path: '/tracing/management', name: 'tracing-management', group: 'tracing', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },

  // 安全
  { path: '/security/api-keys', name: 'api-key-management', group: 'security', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/security/sanitization', name: 'sanitization-management', group: 'security', expectAny: ['.el-table', '.el-card', '.el-button', '.el-form'] },
  { path: '/security/jwt-tokens', name: 'jwt-token-management', group: 'security', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/security/blacklist', name: 'blacklist-management', group: 'security', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/security/audit-logs', name: 'audit-log-management', group: 'security', expectAny: ['.el-table', '.el-card', '.el-button'] },

  // 系统
  { path: '/system/accounts', name: 'account-management', group: 'system', expectAny: ['.el-table', '.el-card', '.el-button'] },
  { path: '/system/permissions', name: 'permission-management', group: 'system', expectAny: ['.el-table', '.el-card', '.el-button'] },

  // AI 试验场
  { path: '/playground/chat', name: 'playground-chat', group: 'playground', expectAny: ['textarea', '.el-textarea', '.el-card', '.el-button'] },
  { path: '/playground/embedding', name: 'playground-embedding', group: 'playground', expectAny: ['textarea', '.el-textarea', '.el-card', '.el-button'] },
  { path: '/playground/rerank', name: 'playground-rerank', group: 'playground', expectAny: ['textarea', '.el-textarea', '.el-card', '.el-button'] },
  { path: '/playground/audio', name: 'playground-audio', group: 'playground', expectAny: ['.el-card', '.el-button', '.el-tabs', '.el-upload'] },
  { path: '/playground/image', name: 'playground-image', group: 'playground', expectAny: ['.el-card', '.el-button', '.el-tabs', '.el-upload'] },

  // 开发者工具
  { path: '/tools/client-access', name: 'client-access-guide', group: 'tools', expectAny: ['.el-card', '.el-tabs', '.el-button', 'pre', 'code'] }
]

/** 公开路由（无需登录） */
export const PUBLIC_ROUTES = [
  { path: '/login', name: 'login', expectAny: ['.login-form', '.login-button', 'input'] }
]
