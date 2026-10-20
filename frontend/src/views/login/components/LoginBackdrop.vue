<template>
  <div class="login-backdrop" :class="{ 'is-static': props.static }" aria-hidden="true">
    <div class="login-backdrop__base"></div>
    <canvas ref="canvasRef" class="login-backdrop__mesh"></canvas>
    <div class="login-backdrop__glow"></div>
    <div class="login-backdrop__grain" :style="grainStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import grainUrl from '@/assets/brand/grain.png'
import { useTopologyScene } from '../composables/useTopologyScene'

const props = defineProps<{ static: boolean }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

// 画布只负责「动」；静止时它画一帧海报（同一套几何），不再跑 rAF
useTopologyScene(canvasRef, toRef(props, 'static'))

const grainStyle = computed(() => ({ backgroundImage: `url(${grainUrl})` }))
</script>

<style scoped>
/* 整层固定：内容滚动/超长时背景不跟着动，也不会撑出滚动条 */
.login-backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

/* 底：左半 surface（品牌墙）/ 右半 paper（表单侧），切分比例由 Login.vue 的 --login-split 决定 */
.login-backdrop__base {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(
      90deg,
      var(--ja-surface) 0 var(--login-split, 44%),
      transparent var(--login-split, 44%)
    ),
    var(--ja-paper);
}

/* 左上角品牌冷色微光（设计稿 rgba(52,200,218,.07)，这里用令牌的 accent-soft 压到 45% 不透明度） */
.login-backdrop__base::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(1100px 680px at 4% -8%, var(--ja-accent-soft), transparent 62%);
  opacity: 0.45;
}

/* 拓扑画布：蒙版只让它在品牌墙一侧显形，右侧表单区域看不到线 */
.login-backdrop__mesh {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  transform: translate3d(
    calc(var(--login-par-x, 0) * -16px),
    calc(var(--login-par-y, 0) * -11px),
    0
  );
  will-change: transform;
  mask-image: linear-gradient(100deg, #000 0%, #000 22%, rgba(0, 0, 0, 0.45) 31%, transparent 38%);
  -webkit-mask-image: linear-gradient(
    100deg,
    #000 0%,
    #000 22%,
    rgba(0, 0, 0, 0.45) 31%,
    transparent 38%
  );
}

/* 跟随指针的品牌光晕（设计稿 .glow 620×620） */
.login-backdrop__glow {
  position: absolute;
  left: var(--login-glow-x, 50%);
  top: var(--login-glow-y, 50%);
  width: 620px;
  height: 620px;
  margin: -310px 0 0 -310px;
  background: radial-gradient(circle, var(--ja-accent-soft), transparent 70%);
  opacity: var(--login-glow-o, 0);
  transition: opacity 0.45s ease;
  will-change: opacity;
}

/* 颗粒：160px 平铺 + soft-light，亮/暗两态各自压亮度 */
.login-backdrop__grain {
  position: absolute;
  inset: 0;
  background-repeat: repeat;
  background-size: 160px 160px;
  opacity: 0.05;
  mix-blend-mode: soft-light;
}

html.dark .login-backdrop__grain {
  opacity: 0.075;
}

/* 窄屏：蒙版改成竖向（顶部品牌条显形），光晕在触屏上没有意义 */
@media (max-width: 900px) {
  .login-backdrop__mesh {
    mask-image: linear-gradient(#000 0%, rgba(0, 0, 0, 0.45) 26%, transparent 40%);
    -webkit-mask-image: linear-gradient(#000 0%, rgba(0, 0, 0, 0.45) 26%, transparent 40%);
  }

  .login-backdrop__glow {
    display: none;
  }
}

/* 静止退路：光晕与颗粒层保留（海报的一部分），但去掉任何会动的东西 */
.login-backdrop.is-static .login-backdrop__glow {
  display: none;
}

.login-backdrop.is-static .login-backdrop__mesh {
  will-change: auto;
}

@media (prefers-reduced-motion: reduce) {
  .login-backdrop__glow {
    display: none;
  }
}
</style>
