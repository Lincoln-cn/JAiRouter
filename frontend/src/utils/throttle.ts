/**
 * Leading + trailing throttle：窗口内首次调用立即执行，其余合并为窗口末尾一次，
 * 保证最后一次调用不丢失（trailing）。与 lodash.throttle 默认行为一致，
 * 但不引入额外依赖，且暴露 cancel/flush 供收尾（如流式结束落盘）使用。
 */
export interface ThrottledFunction<T extends (...args: any[]) => void> {
  (...args: Parameters<T>): void
  cancel(): void
  flush(): void
}

export function throttle<T extends (...args: any[]) => void>(
  fn: T,
  wait: number
): ThrottledFunction<T> {
  // null = 尚未调用过，保证首次调用必然走 leading
  let lastInvokeTime: number | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let pendingArgs: Parameters<T> | null = null

  const invoke = () => {
    lastInvokeTime = Date.now()
    timer = null
    if (pendingArgs) {
      const args = pendingArgs
      pendingArgs = null
      fn(...args)
    }
  }

  const throttled = ((...args: Parameters<T>) => {
    pendingArgs = args
    const elapsed = lastInvokeTime === null ? Infinity : Date.now() - lastInvokeTime
    if (elapsed >= wait) {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
      invoke()
    } else if (timer === null) {
      timer = setTimeout(invoke, wait - elapsed)
    }
  }) as ThrottledFunction<T>

  throttled.cancel = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
    pendingArgs = null
    // 复位窗口，使下一次调用必然走 leading（流式重新开始时首个分片立即渲染）
    lastInvokeTime = null
  }

  throttled.flush = () => {
    if (timer !== null) {
      clearTimeout(timer)
      invoke()
    }
  }

  return throttled
}
