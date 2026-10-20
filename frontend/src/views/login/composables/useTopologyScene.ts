import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import {
  buildScene,
  drawScene,
  resolvePalette,
  type TopologyPacket,
  type TopologyPalette,
  type TopologyRipple,
  type TopologyScene,
  type TopologyWire
} from '../topology'

/** 光迹数量（§5.3：5 个光迹） */
const PACKET_COUNT = 5
/** 涟漪间隔（§5.3：2.2s 一个） */
const RIPPLE_INTERVAL = 2.2
/** 同时存在的涟漪上限，防止长时间挂机堆积 */
const RIPPLE_LIMIT = 8
/** DPR 封顶（§5.3：DPR ≤ 1.5），高 DPR 屏上省一半像素填充 */
const MAX_DPR = 1.5
/** 单帧最大步进，标签页切回时避免「时间跳跃」把光迹一次推到底 */
const MAX_STEP = 0.05

/**
 * 登录页背景画布的驱动：尺寸/DPR、场景重建、光迹与涟漪状态、单条 rAF 循环、
 * `visibilitychange` 暂停、卸载清理。
 *
 * `isStatic` = `?static` 或 `prefers-reduced-motion: reduce`：此时只在挂载/尺寸变化时
 * **同步**画一帧静态海报（零 rAF），保证首帧不白屏、退路与动效版共用同一套几何。
 */
export function useTopologyScene(canvasRef: Ref<HTMLCanvasElement | null>, isStatic: Ref<boolean>) {
  let ctx: CanvasRenderingContext2D | null = null
  let scene: TopologyScene | null = null
  let palette: TopologyPalette | null = null
  let packets: TopologyPacket[] = []
  let ripples: TopologyRipple[] = []
  let dpr = 1
  let clock = 0
  let raf = 0
  let lastFrame = 0
  let running = false
  let nextRipple = RIPPLE_INTERVAL
  let resizeTimer = 0

  /** 从当前场景里挑一条连线（45% 概率走干线，与设计稿一致） */
  const pickWire = (): TopologyWire | null => {
    if (!scene || scene.wires.length === 0) return null
    const trunks = scene.wires.filter(wire => wire.trunk)
    const inner = scene.wires.filter(wire => !wire.trunk)
    const preferTrunk = Math.random() < 0.45
    const pool = preferTrunk && trunks.length > 0 ? trunks : inner.length > 0 ? inner : scene.wires
    return pool[Math.floor(Math.random() * pool.length)]
  }

  const createPacket = (): TopologyPacket | null => {
    const wire = pickWire()
    if (!wire) return null
    return {
      wire,
      t: Math.random() * 0.5,
      speed: 0.14 + Math.random() * 0.12,
      trail: wire.trunk ? 0.04 + Math.random() * 0.04 : 0.09 + Math.random() * 0.06
    }
  }

  const resetPackets = (): void => {
    if (isStatic.value) {
      packets = []
      return
    }
    packets = Array.from({ length: PACKET_COUNT }, createPacket).filter(
      (packet): packet is TopologyPacket => packet !== null
    )
  }

  const draw = (): void => {
    if (!ctx || !scene || !palette) return
    drawScene(ctx, scene, palette, {
      time: clock,
      breath: !isStatic.value,
      packets,
      ripples
    })
  }

  const resize = (): void => {
    const canvas = canvasRef.value
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const width = Math.max(1, Math.round(rect.width))
    const height = Math.max(1, Math.round(rect.height))
    dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR)

    canvas.width = Math.round(width * dpr)
    canvas.height = Math.round(height * dpr)
    ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    scene = buildScene(width, height)
    palette = resolvePalette(canvas)
    clock = 0
    ripples = []
    nextRipple = RIPPLE_INTERVAL
    resetPackets()
    // 首帧同步落笔：挂载后立刻可见，不等下一次 rAF（「首帧不能白屏」）
    draw()
  }

  const step = (delta: number): void => {
    clock += delta

    packets.forEach(packet => {
      packet.t += packet.speed * delta
      if (packet.t > 1.12) {
        const next = createPacket()
        if (next) Object.assign(packet, next)
      }
    })

    ripples.forEach(ripple => {
      ripple.r += delta * 46
      ripple.alpha -= delta * 0.55
    })
    ripples = ripples.filter(ripple => ripple.alpha > 0)

    nextRipple -= delta
    if (nextRipple <= 0 && scene && scene.nodes.length > 0) {
      nextRipple = RIPPLE_INTERVAL
      const node = scene.nodes[Math.floor(Math.random() * scene.nodes.length)]
      if (ripples.length < RIPPLE_LIMIT) {
        ripples.push({ x: node.x, y: node.y, r: 2, alpha: 0.55 })
      }
    }
  }

  const frame = (now: number): void => {
    if (!running) return
    const delta = lastFrame > 0 ? Math.min((now - lastFrame) / 1000, MAX_STEP) : 0
    lastFrame = now
    step(delta)
    draw()
    raf = requestAnimationFrame(frame)
  }

  const start = (): void => {
    if (running || isStatic.value) return
    running = true
    lastFrame = 0
    raf = requestAnimationFrame(frame)
  }

  const stop = (): void => {
    running = false
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  const onResize = (): void => {
    // 拖拽窗口时 resize 会连发：节流到 120ms，避免每帧重建场景
    if (resizeTimer) window.clearTimeout(resizeTimer)
    resizeTimer = window.setTimeout(() => {
      resizeTimer = 0
      resize()
    }, 120)
  }

  const onVisibilityChange = (): void => {
    if (document.hidden) {
      stop()
      return
    }
    if (!isStatic.value) start()
  }

  onMounted(() => {
    resize()
    if (!isStatic.value) start()
    window.addEventListener('resize', onResize, { passive: true })
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  watch(isStatic, value => {
    stop()
    resize()
    if (!value) start()
  })

  onBeforeUnmount(() => {
    stop()
    if (resizeTimer) window.clearTimeout(resizeTimer)
    resizeTimer = 0
    window.removeEventListener('resize', onResize)
    document.removeEventListener('visibilitychange', onVisibilityChange)
    packets = []
    ripples = []
    scene = null
    palette = null
    ctx = null
  })
}
