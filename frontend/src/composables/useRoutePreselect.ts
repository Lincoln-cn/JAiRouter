import { useRoute } from 'vue-router'

/* ------------------------------------------------------------------ */
/*  playgroundTargetRoute — map serviceType to named playground route  */
/* ------------------------------------------------------------------ */

const SERVICE_ROUTE_MAP: Record<string, string> = {
  chat: 'playground-chat',
  embedding: 'playground-embedding',
  rerank: 'playground-rerank',
  tts: 'playground-audio',
  stt: 'playground-audio',
  imgGen: 'playground-image',
  imgEdit: 'playground-image'
}

/**
 * Returns the target playground route for a given service type.
 *
 * - chat        → { name: 'playground-chat' }
 * - embedding   → { name: 'playground-embedding' }
 * - rerank      → { name: 'playground-rerank' }
 * - tts | stt   → { name: 'playground-audio' }
 * - imgGen | imgEdit → { name: 'playground-image' }
 * - other / undefined → null
 */
export function playgroundTargetRoute(
  serviceType?: string
): { name: string; query?: Record<string, string> } | null {
  if (!serviceType) return null
  const routeName = SERVICE_ROUTE_MAP[serviceType]
  return routeName ? { name: routeName } : null
}

/* ------------------------------------------------------------------ */
/*  useRoutePreselect — read serviceType from current route query      */
/* ------------------------------------------------------------------ */

/**
 * Composable that exposes the `serviceType` carried in the current
 * route's query string (used by playground containers to pre-select
 * configuration coming from the onboarding flow).
 */
export function useRoutePreselect(): {
  requestedServiceType: () => string | undefined
} {
  const route = useRoute()

  function requestedServiceType(): string | undefined {
    const val = route.query.serviceType
    return typeof val === 'string' ? val : undefined
  }

  return { requestedServiceType }
}

/* ------------------------------------------------------------------ */
/*  preselectInstanceName — pick the best instance from a list          */
/* ------------------------------------------------------------------ */

const HEALTH_PRIORITY: Record<string, number> = {
  HEALTHY: 0,
  UNKNOWN: 1,
  UNHEALTHY: 2
}

/**
 * Returns the `name` of the most-desirable instance from the given list.
 *
 * Priority: HEALTHY > UNKNOWN / missing healthStatus > UNHEALTHY.
 * Returns `undefined` when the list is empty or contains only UNHEALTHY
 * entries (none should be auto-selected).
 */
export function preselectInstanceName(
  instances: Array<{ name: string; healthStatus?: string }>
): string | undefined {
  if (instances.length === 0) return undefined

  let best: { name: string; healthStatus?: string } | undefined
  let bestPriority = Infinity

  for (const inst of instances) {
    const priority = HEALTH_PRIORITY[inst.healthStatus ?? 'UNKNOWN'] ?? 1
    if (priority < bestPriority) {
      bestPriority = priority
      best = inst
    }
  }

  // Don't auto-select if the best candidate is UNHEALTHY
  if (bestPriority >= 2) return undefined
  return best?.name
}
