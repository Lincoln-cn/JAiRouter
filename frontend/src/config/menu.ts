/**
 * 菜单配置（v2.9.8 Phase 4 数据驱动，11 组 → 8 组）
 *
 * 8 组结构（开发计划2026 L1158）：
 * - 概览(1)：仪表板
 * - 模型服务(4)：服务 / 实例 / 版本 / Adapter
 * - 流量治理(8)：规则 / LB监控 / LB策略 / CB监控 / CB历史 / CB配置 / 限流 / 资源池
 * - 数据记录(6)：调用历史 4 + 异常 2
 * - 链路追踪(3)：dashboard / search / management
 * - 安全管理(4)：api-keys / jwt-tokens / blacklist / audit-logs
 * - 系统管理(3)：账户 / 权限(new) / 状态持久化（配置验证无对应路由，暂不挂载）
 * - AI 试验场(5)：chat / embedding / rerank / audio / image
 *
 * 每项新增可选 `permission` 字段（权限码，格式 `module:resource:action`，
 * 与后端 PermissionCodes 43 权限码体系一致，无 ROLE_ 前缀）。
 *
 * 权限映射原则：
 * - 与后端 PermissionRuleRegistry 已登记的 URL 权限规则对齐
 *   （如 /api/call-history/** → callhistory:view、/api/token-usage/** → monitoring:tokenusage:read）
 * - 菜单可见性采用"读"语义码（如 config:services:read），写操作由后端 URL 规则另行拦截
 * - 后端未登记权限规则（回退 authenticated）的组不设 permission 字段（如异常管理）
 * - security / system 组沿用原 ADMIN-only 语义（security:*:manage / system:accounts:manage）
 *
 * `icon` 为 Element Plus 图标 kebab-case 名称，Layout.vue 中映射为图标组件。
 */

export interface MenuItem {
  /** 路由路径（el-menu-item index） */
  path: string
  /** 菜单标题 */
  title: string
  /** 权限码（module:resource:action）；缺省表示所有已登录用户可见 */
  permission?: string
  /** 子项图标名（Element Plus 图标 kebab-case 名，可选） */
  icon?: string
}

export interface MenuGroup {
  /** 组标识（el-sub-menu index，defaultOpeneds 引用） */
  index: string
  /** 组标题 */
  title: string
  /** 组图标名（Element Plus 图标 kebab-case 名） */
  icon: string
  children: MenuItem[]
}

/** 菜单配置：8 组 34 项（v2.9.8 Phase 4 重分类，与 Layout.vue 数据驱动渲染一一对应） */
export const menuGroups: MenuGroup[] = [
  {
    index: 'dashboard',
    title: '概览',
    icon: 'house',
    children: [
      { path: '/dashboard/main', title: '仪表板', permission: 'overview:dashboard:read' }
    ]
  },
  {
    index: 'model-services',
    title: '模型服务',
    icon: 'setting',
    children: [
      { path: '/config/services', title: '服务管理', permission: 'config:services:read' },
      { path: '/config/instances', title: '实例管理', permission: 'config:instances:read' },
      { path: '/config/versions', title: '版本管理', permission: 'config:versions:read' },
      { path: '/config/adapters', title: 'Adapter管理', permission: 'config:adapters:read' }
    ]
  },
  {
    index: 'traffic',
    title: '流量治理',
    icon: 'connection',
    children: [
      { path: '/config/rules', title: '路由规则', permission: 'config:rules:read' },
      { path: '/load-balancers/monitoring', title: '负载均衡监控', permission: 'lb:monitoring:read' },
      { path: '/load-balancers/strategy-config', title: '负载均衡策略', permission: 'lb:config:write' },
      { path: '/circuit-breakers/monitoring', title: '熔断器监控', permission: 'cb:monitoring:read' },
      { path: '/circuit-breakers/history', title: '熔断器历史', permission: 'cb:history:read' },
      { path: '/circuit-breakers/global-config', title: '熔断器配置', permission: 'config:circuitbreaker:read' },
      { path: '/rate-limiters/monitoring', title: '限流监控', permission: 'rl:monitoring:read' },
      { path: '/config/pools', title: '资源池', permission: 'config:pools:read' }
    ]
  },
  {
    index: 'records',
    title: '数据记录',
    icon: 'document',
    children: [
      { path: '/call-history/dashboard', title: '调用历史仪表盘', permission: 'callhistory:view' },
      { path: '/call-history/list', title: '调用列表', permission: 'callhistory:view' },
      { path: '/call-history/token-usage', title: 'Token 统计', permission: 'monitoring:tokenusage:read' },
      { path: '/call-history/slow-calls', title: '慢调用', permission: 'monitoring:slowquery:read' },
      // 后端 /api/exceptions/** 未登记权限规则（回退 authenticated），无需权限控制
      { path: '/exceptions/list', title: '异常事件管理' },
      { path: '/exceptions/statistics', title: '异常统计分析' }
    ]
  },
  {
    index: 'tracing',
    title: '链路追踪',
    icon: 'position',
    children: [
      { path: '/tracing/dashboard', title: '追踪仪表盘', permission: 'tracing:dashboard:read' },
      { path: '/tracing/search', title: '追踪搜索', permission: 'tracing:search:read' },
      { path: '/tracing/management', title: '追踪配置', permission: 'tracing:config:manage' }
    ]
  },
  {
    index: 'security',
    title: '安全管理',
    icon: 'lock',
    children: [
      { path: '/security/api-keys', title: 'API密钥管理', permission: 'security:apikeys:manage' },
      { path: '/security/jwt-tokens', title: 'JWT令牌管理', permission: 'security:jwttokens:manage' },
      { path: '/security/blacklist', title: '黑名单管理', permission: 'security:blacklist:manage' },
      { path: '/security/audit-logs', title: '审计日志', permission: 'security:audit:read' }
    ]
  },
  {
    index: 'system',
    title: '系统管理',
    icon: 'user',
    children: [
      { path: '/system/accounts', title: '账户管理', permission: 'system:accounts:manage' },
      { path: '/system/permissions', title: '权限管理', permission: 'system:permissions:manage' },
      { path: '/config/state-persistence', title: '状态持久化', permission: 'config:persistence:read' }
    ]
  },
  {
    index: 'playground',
    title: 'AI 试验场',
    icon: 'monitor',
    children: [
      { path: '/playground/chat', title: '对话测试', permission: 'ai:playground:use', icon: 'chat-dot-round' },
      { path: '/playground/embedding', title: '向量生成', permission: 'ai:playground:use', icon: 'data-line' },
      { path: '/playground/rerank', title: '重排序', permission: 'ai:playground:use', icon: 'sort' },
      { path: '/playground/audio', title: '语音服务', permission: 'ai:playground:use', icon: 'headset' },
      { path: '/playground/image', title: '图像服务', permission: 'ai:playground:use', icon: 'picture' }
    ]
  }
]
