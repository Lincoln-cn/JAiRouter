import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'

/** 视差缓动系数（设计稿 0.05/帧） */
const EASE = 0.05
/** 收敛阈值：小于它就直接贴到目标值并停掉 rAF */
const EPSILON = 0.0005

/**
 * 登录页的指针视差驱动。
 *
 * 不把位移写进 Vue 响应式状态（每帧触发重渲染太贵），而是把归一化的指针位置写到
 * 根节点的 CSS 变量上，由 CSS 换成像素位移：
 * - `--login-par-x` / `--login-par-y`：-1..1，画布反向位移（-16/-11px），品牌墙正向（+5/+3px）
 * - `--login-glow-x` / `--login-glow-y`：指针绝对坐标，供光晕跟随
 * - `--login-glow-o`：光晕透明度开关（0/1，淡入淡出交给 CSS transition）
 *
 * `enabled` 为 false（`?static` / 减少动效）时完全不挂监听、不跑 rAF。
 */
export function useParallax(root: Ref<HTMLElement | null>, enabled: Ref<boolean>) {
  let raf = 0
  let targetX = 0
  let targetY = 0
  let currentX = 0
  let currentY = 0

  const writePosition = (): void => {
    const element = root.value
    if (!element) return
    element.style.setProperty('--login-par-x', currentX.toFixed(4))
    element.style.setProperty('--login-par-y', currentY.toFixed(4))
  }

  const tick = (): void => {
    const dx = targetX - currentX
    const dy = targetY - currentY
    const settled = Math.abs(dx) < EPSILON && Math.abs(dy) < EPSILON
    if (settled) {
      currentX = targetX
      currentY = targetY
    } else {
      currentX += dx * EASE
      currentY += dy * EASE
    }
    writePosition()
    raf = settled ? 0 : requestAnimationFrame(tick)
  }

  const schedule = (): void => {
    if (!raf) raf = requestAnimationFrame(tick)
  }

  const onPointerMove = (event: PointerEvent): void => {
    targetX = (event.clientX / window.innerWidth) * 2 - 1
    targetY = (event.clientY / window.innerHeight) * 2 - 1
    const element = root.value
    if (element) {
      element.style.setProperty('--login-glow-x', `${Math.round(event.clientX)}px`)
      element.style.setProperty('--login-glow-y', `${Math.round(event.clientY)}px`)
      element.style.setProperty('--login-glow-o', '1')
    }
    schedule()
  }

  const onPointerLeave = (): void => {
    root.value?.style.setProperty('--login-glow-o', '0')
  }

  const start = (): void => {
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    document.documentElement.addEventListener('pointerleave', onPointerLeave)
  }

  const stop = (): void => {
    window.removeEventListener('pointermove', onPointerMove)
    document.documentElement.removeEventListener('pointerleave', onPointerLeave)
    if (raf) cancelAnimationFrame(raf)
    raf = 0
    targetX = 0
    targetY = 0
    currentX = 0
    currentY = 0
    writePosition()
    root.value?.style.setProperty('--login-glow-o', '0')
  }

  onMounted(() => {
    if (enabled.value) start()
  })

  watch(enabled, value => {
    if (value) start()
    else stop()
  })

  onBeforeUnmount(stop)
}
