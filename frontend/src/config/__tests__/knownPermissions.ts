/**
 * 前端权限码白名单（唯一事实，`menu.spec.ts` / `permissionCodesBackend.spec.ts` /
 * `permissionDirectiveUsage.spec.ts` 共用）
 *
 * 为什么不从后端 import：前端构建产物不包含 Java 源码，运行时也没有权限码接口
 * （权限码随 JWT 下发，但前端需要一份静态清单用于「菜单/路由里写的码是否存在」这类离线断言）。
 * 因此这里手工维护一份，并由 `permissionCodesBackend.spec.ts` 与
 * `src/main/java/.../PermissionCodes.java` 的 `ALL_PERMISSION_CODES` 做**双向**比对：
 * 少了码（后端新增但前端没同步）或多出码（前端写错或后端已删）都会红。
 *
 * 维护方式：后端改 `PermissionCodes.java` 后，跑一次 `npm run test:unit`，
 * 红文会列出差异；按提示增删本清单，不要盲目放开断言。
 *
 * 清单自身另外恒等于 70 项（见 `menu.spec.ts`「白名单自身 = 70 个互不重复的权限码」）。
 */
export const KNOWN_PERMISSION_CODES: readonly string[] = [
  'overview:dashboard:read',
  'config:services:read',
  'config:services:write',
  'config:instances:read',
  'config:instances:write',
  'config:versions:read',
  'config:versions:write',
  'config:persistence:read',
  'config:persistence:write',
  'config:adapters:read',
  'config:adapters:write',
  'config:rules:read',
  'config:rules:write',
  'config:pools:read',
  'config:pools:write',
  'config:circuitbreaker:read',
  'config:circuitbreaker:write',
  'config:callhistory:read',
  'config:callhistory:write',
  'config:cache:read',
  'config:cache:write',
  'config:validation:read',
  'config:validation:write',
  'config:quota:read',
  'config:quota:write',
  'config:ratelimit:read',
  'config:ratelimit:write',
  'config:cluster:read',
  'config:cluster:write',
  'config:health:read',
  'config:health:write',
  'lb:monitoring:read',
  'lb:config:read',
  'lb:config:write',
  'cb:monitoring:read',
  'cb:history:read',
  'rl:monitoring:read',
  'callhistory:view',
  'monitoring:metrics:read',
  'monitoring:metrics:write',
  'monitoring:slowquery:read',
  'monitoring:slowquery:write',
  'monitoring:tokenusage:read',
  'monitoring:modelstats:read',
  'monitoring:routing:read',
  'monitoring:quota:read',
  'monitoring:exceptions:read',
  'monitoring:exceptions:write',
  'monitoring:console:read',
  'monitoring:config:write',
  'tracing:dashboard:read',
  'tracing:search:read',
  'tracing:config:read',
  'tracing:config:manage',
  'security:apikeys:read',
  'security:apikeys:manage',
  'security:jwttokens:read',
  'security:jwttokens:manage',
  'security:blacklist:read',
  'security:blacklist:manage',
  'security:audit:read',
  'security:audit:write',
  'security:sanitization:read',
  'security:sanitization:manage',
  'system:accounts:read',
  'system:accounts:manage',
  'system:permissions:read',
  'system:permissions:manage',
  'ai:playground:use',
  'actuator:admin:manage'
]
