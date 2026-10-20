/**
 * 登录页背景「路由拓扑」的几何与绘制（设计稿：登录页设计稿-动效版-2026-10-20 §5.3）。
 *
 * 本模块只做纯计算 + Canvas 2D 绘制：不碰定时器、DOM 生命周期与事件，
 * 那些由 `composables/useTopologyScene.ts` 负责。这样才能在不启动动画的前提下
 * 画出「静态海报」（`?static` / `prefers-reduced-motion: reduce` 两条退路）。
 *
 * 规格对齐（§5.3）：3 簇 26 节点（窄屏 13）、5 个光迹、2.2s 涟漪、
 * 簇间干线用二次贝塞尔大弧，节点呼吸与核心环脉冲。
 */

/** 一个拓扑节点：普通节点的 x/y 是基准位置，呼吸偏移在绘制时叠加 */
export interface TopologyNode {
  x: number
  y: number
  /** 半径（CSS px） */
  r: number
  /** 呼吸振幅（CSS px），核心节点为 0 */
  amp: number
  /** 呼吸相位，让各节点不同步 */
  phase: number
  /** 所属簇（0/1/2），核心环脉冲用它错相 */
  cluster: number
  core: boolean
}

/** 一条连线：二次贝塞尔，bend 是控制点沿法线方向的偏移系数 */
export interface TopologyWire {
  from: number
  to: number
  bend: number
  /** true = 簇间干线（更亮更粗，光迹优先走它） */
  trunk: boolean
}

export interface TopologyScene {
  nodes: TopologyNode[]
  wires: TopologyWire[]
  /** 场景宽高（CSS px，与 canvas 的 CSS 尺寸一致；DPR 缩放交给 ctx.transform） */
  width: number
  height: number
}

/** 光迹：沿某条连线从 from 走到 to */
export interface TopologyPacket {
  wire: TopologyWire
  /** 进度 0..1（>1 由驱动方回收重生） */
  t: number
  /** 每秒进度增量 */
  speed: number
  /** 拖尾长度（进度差） */
  trail: number
}

/** 涟漪：从节点处向外扩散的圆环 */
export interface TopologyRipple {
  x: number
  y: number
  r: number
  alpha: number
}

/** 从设计令牌解析出的颜色（画布不认 CSS 变量，必须先取色） */
export interface TopologyPalette {
  line: string
  trunk: string
  node: string
  core: string
  packet: string
  ripple: string
}

/**
 * 三簇的位置（相对视口宽高的比例）与散布半径。
 * 数值取自设计稿：left-top / left-middle / left-bottom，向右逐渐淡出。
 */
const CLUSTERS = [
  { cx: 0.095, cy: 0.135, spread: 105, reach: 0.26 },
  { cx: 0.285, cy: 0.52, spread: 150, reach: 0.32 },
  { cx: 0.095, cy: 0.82, spread: 120, reach: 0.28 }
] as const

/** 每簇普通节点数：8 + 8 + 7 + 3 个核心 = 26（§5.3「3 簇 26 节点」） */
const NODES_DESKTOP = [8, 8, 7] as const
/** 窄屏：4 + 3 + 3 + 3 个核心 = 13（§5.3「窄屏 13」） */
const NODES_NARROW = [4, 3, 3] as const

/** 簇间干线的弯曲系数：0↔1、1↔2、0↔2 */
const TRUNK_BENDS = [-0.3, -0.26, -0.34] as const

/** 窄屏断点：与 Login.vue 的布局断点保持一致 */
const NARROW_WIDTH = 900

/**
 * 固定种子的伪随机数（mulberry32）。
 *
 * 设计稿原型用 Math.random()，每次刷新拓扑形状都不同；这里改成「按视口尺寸取种子」：
 * 同一尺寸下画面稳定（静态海报可复现、尺寸变化也不会跳变），代价是刷新不再换形状。
 */
function createRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value))

/**
 * 构建一个视口尺寸下的拓扑场景（纯函数：同尺寸同结果）。
 */
export function buildScene(width: number, height: number): TopologyScene {
  const narrow = width < NARROW_WIDTH
  // 拓扑只占左侧：桌面 40% 宽 / 96% 高，窄屏 92% 宽 / 46% 高（对应竖向蒙版）
  const maxX = width * (narrow ? 0.92 : 0.4)
  const maxY = height * (narrow ? 0.46 : 0.96)
  const counts = narrow ? NODES_NARROW : NODES_DESKTOP
  const random = createRandom(Math.round(width) * 7919 + Math.round(height))

  // 近似高斯抖动：4 个均匀分布之和减 2，落在 [-1, 1] 且中间密集
  const gauss = (): number => (random() + random() + random() + random() - 2) / 2

  const nodes: TopologyNode[] = []
  const coreIndex: number[] = []

  CLUSTERS.forEach((cluster, clusterIndex) => {
    const cx = width * cluster.cx
    const cy = height * cluster.cy
    const spread = Math.min(cluster.spread, maxX * cluster.reach)

    coreIndex.push(nodes.length)
    nodes.push({
      x: cx,
      y: cy,
      r: 3.2,
      amp: 0,
      phase: clusterIndex * 1.7,
      cluster: clusterIndex,
      core: true
    })

    for (let i = 0; i < counts[clusterIndex]; i++) {
      nodes.push({
        x: clamp(cx + gauss() * spread, 8, maxX),
        y: clamp(cy + gauss() * spread, 8, maxY),
        r: 1.2 + random() * 0.8,
        amp: 1.5 + random() * 3,
        phase: random() * Math.PI * 2,
        cluster: clusterIndex,
        core: false
      })
    }
  })

  const wires: TopologyWire[] = []
  const maxGap = narrow ? 150 : 190

  // 簇内连线：每个普通节点连最近的 2 个（核心节点只作为同簇的候选，不反向连）
  nodes.forEach((node, index) => {
    if (node.core) return
    const nearest = nodes
      .map((other, otherIndex) => ({ other, otherIndex }))
      .filter(
        ({ other, otherIndex }) =>
          otherIndex !== index && (other.core ? other.cluster === node.cluster : true)
      )
      .map(({ other, otherIndex }) => ({
        index: otherIndex,
        distance: Math.hypot(other.x - node.x, other.y - node.y)
      }))
      .filter(entry => entry.distance <= maxGap)
      .sort((a, b) => a.distance - b.distance)
      .slice(0, 2)

    nearest.forEach(({ index: target }) => {
      const from = Math.min(index, target)
      const to = Math.max(index, target)
      if (wires.some(wire => wire.from === from && wire.to === to)) return
      wires.push({ from, to, bend: (random() * 2 - 1) * 0.16, trunk: false })
    })
  })

  // 簇间干线：三个核心两两相连，用大弧让结构「有骨架」
  const trunkPairs = [
    [0, 1],
    [1, 2],
    [0, 2]
  ] as const
  trunkPairs.forEach(([a, b], index) => {
    wires.push({
      from: coreIndex[a],
      to: coreIndex[b],
      bend: TRUNK_BENDS[index],
      trunk: true
    })
  })

  return { nodes, wires, width, height }
}

/** 二次贝塞尔控制点：中点沿法线方向偏移 bend × 弦长 */
export function controlPoint(
  a: { x: number; y: number },
  b: { x: number; y: number },
  bend: number
): { x: number; y: number } {
  const dx = b.x - a.x
  const dy = b.y - a.y
  return {
    x: (a.x + b.x) / 2 - dy * bend,
    y: (a.y + b.y) / 2 + dx * bend
  }
}

/** 二次贝塞尔上 t∈[0,1] 处的点 */
export function curvePoint(
  a: { x: number; y: number },
  b: { x: number; y: number },
  bend: number,
  t: number
): { x: number; y: number } {
  const control = controlPoint(a, b, bend)
  const inv = 1 - t
  return {
    x: inv * inv * a.x + 2 * inv * t * control.x + t * t * b.x,
    y: inv * inv * a.y + 2 * inv * t * control.y + t * t * b.y
  }
}

/**
 * 把设计令牌里的 `--ja-accent` 等颜色转成带透明度的 rgba。
 * 令牌是 hex（亮/暗两套），画布支持 rgba 但不认 CSS 变量 ⇒ 取色后在这里合成。
 */
export function withAlpha(color: string, alpha: number): string {
  const value = color.trim()
  if (value.startsWith('#')) {
    const raw = value.slice(1)
    const full =
      raw.length === 3
        ? raw
            .split('')
            .map(char => char + char)
            .join('')
        : raw
    const parsed = Number.parseInt(full.slice(0, 6), 16)
    if (Number.isNaN(parsed)) return value
    const r = (parsed >> 16) & 255
    const g = (parsed >> 8) & 255
    const b = parsed & 255
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
  }
  if (value.startsWith('rgb')) {
    const parts = value.match(/[\d.]+/g)
    if (parts && parts.length >= 3) return `rgba(${parts[0]}, ${parts[1]}, ${parts[2]}, ${alpha})`
  }
  return value
}

/**
 * 从元素上读取当前主题的令牌，合成画布色板。
 * 亮/暗两套令牌由 `html.dark` 切换 ⇒ 每次 resize 重新取色即可跟随（登录页没有主题切换入口）。
 */
export function resolvePalette(element: Element): TopologyPalette {
  const styles = getComputedStyle(element)
  const accent = styles.getPropertyValue('--ja-accent').trim() || '#34c8da'
  return {
    line: withAlpha(accent, 0.16),
    trunk: withAlpha(accent, 0.28),
    node: withAlpha(accent, 0.55),
    core: accent,
    packet: accent,
    ripple: accent
  }
}

export interface SceneFrameOptions {
  /** 动画时钟（秒）；静态海报传 0 */
  time: number
  /** false = 静态海报（节点不呼吸、核心环不脉冲） */
  breath: boolean
  packets?: readonly TopologyPacket[]
  ripples?: readonly TopologyRipple[]
}

/**
 * 画一帧。调用方负责 canvas 尺寸与 ctx.transform(DPR)；
 * 本函数只按 CSS px 坐标系绘制。
 */
export function drawScene(
  ctx: CanvasRenderingContext2D,
  scene: TopologyScene,
  palette: TopologyPalette,
  options: SceneFrameOptions
): void {
  ctx.clearRect(0, 0, scene.width, scene.height)
  ctx.lineCap = 'round'

  // 节点当前坐标（呼吸偏移只影响绘制，不回写场景）
  const positions = scene.nodes.map(node => {
    if (!options.breath || node.core) return { x: node.x, y: node.y }
    return {
      x: node.x + Math.sin(options.time * 0.55 + node.phase) * node.amp,
      y: node.y + Math.cos(options.time * 0.44 + node.phase) * node.amp * 0.8
    }
  })

  // ① 连线：先画簇内细线，再画干线，让骨架压在细线之上
  const drawWire = (wire: TopologyWire): void => {
    const from = positions[wire.from]
    const to = positions[wire.to]
    const control = controlPoint(from, to, wire.bend)
    ctx.beginPath()
    ctx.moveTo(from.x, from.y)
    ctx.quadraticCurveTo(control.x, control.y, to.x, to.y)
    ctx.lineWidth = wire.trunk ? 1.9 : 1.5
    ctx.strokeStyle = wire.trunk ? palette.trunk : palette.line
    ctx.stroke()
  }
  scene.wires.forEach(wire => {
    if (!wire.trunk) drawWire(wire)
  })
  scene.wires.forEach(wire => {
    if (wire.trunk) drawWire(wire)
  })

  // ② 节点：核心 = 脉冲环 + 实心点；普通 = 小圆点
  scene.nodes.forEach((node, index) => {
    const point = positions[index]
    if (node.core) {
      const pulse = options.breath
        ? 1 + Math.sin(options.time * 1.6 + node.cluster * 1.3) * 0.12
        : 1
      ctx.beginPath()
      ctx.arc(point.x, point.y, 7 * pulse, 0, Math.PI * 2)
      ctx.lineWidth = 1
      ctx.strokeStyle = withAlpha(palette.core, 0.4)
      ctx.stroke()

      ctx.beginPath()
      ctx.arc(point.x, point.y, 2.6, 0, Math.PI * 2)
      ctx.fillStyle = palette.core
      ctx.fill()
      return
    }
    ctx.beginPath()
    ctx.arc(point.x, point.y, node.r, 0, Math.PI * 2)
    ctx.fillStyle = palette.node
    ctx.fill()
  })

  // ③ 光迹：曲线上的拖尾渐变 + 端点圆点（端点带一点辉光）
  options.packets?.forEach(packet => {
    const from = positions[packet.wire.from]
    const to = positions[packet.wire.to]
    const headT = Math.min(packet.t, 1)
    const tailT = Math.max(0, headT - packet.trail)
    if (headT <= 0) return
    const head = curvePoint(from, to, packet.wire.bend, headT)
    const tail = curvePoint(from, to, packet.wire.bend, tailT)

    const trail = ctx.createLinearGradient(tail.x, tail.y, head.x, head.y)
    trail.addColorStop(0, withAlpha(palette.packet, 0))
    trail.addColorStop(1, withAlpha(palette.packet, 0.85))
    ctx.beginPath()
    ctx.moveTo(tail.x, tail.y)
    ctx.lineTo(head.x, head.y)
    ctx.lineWidth = packet.wire.trunk ? 1.9 : 1.5
    ctx.strokeStyle = trail
    ctx.stroke()

    ctx.beginPath()
    ctx.arc(head.x, head.y, packet.wire.trunk ? 2.2 : 1.8, 0, Math.PI * 2)
    ctx.fillStyle = palette.packet
    ctx.shadowColor = palette.packet
    ctx.shadowBlur = 9
    ctx.fill()
    ctx.shadowBlur = 0
  })

  // ④ 涟漪：从节点向外扩散并淡出
  options.ripples?.forEach(ripple => {
    if (ripple.alpha <= 0 || ripple.r <= 0) return
    ctx.beginPath()
    ctx.arc(ripple.x, ripple.y, ripple.r, 0, Math.PI * 2)
    ctx.lineWidth = 1
    ctx.strokeStyle = withAlpha(palette.ripple, ripple.alpha * 0.5)
    ctx.stroke()
  })
}
