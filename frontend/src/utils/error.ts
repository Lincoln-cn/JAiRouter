/**
 * 统一的「从未知异常里取文案」工具（#525）。
 *
 * 背景：`catch (error: any)` 在 UI 层被用了 20 多处，实际只读两个东西 ——
 * `error.response.data.message`（后端业务错误）和 `error.message`（网络/框架错误）。
 * 把这两处收成一个具名类型 + 一个函数后，`catch` 可以改为 `catch (error)`（`unknown`），
 * 其它任何错误的属性访问都不再被放行。
 */

/** catch 到的东西在 UI 层唯一需要的形状（axios 风格错误或普通 Error） */
export interface CaughtErrorShape {
  message?: string
  response?: {
    data?: {
      message?: string
    }
  }
}

/**
 * 取异常自带的文案：优先后端 `response.data.message`，其次 `Error.message`。
 * 两者都没有（或 catch 到的不是对象）时返回 `undefined`，由调用方回落 i18n 文案。
 */
export function getErrorMessage(error: unknown): string | undefined {
  if (typeof error !== 'object' || error === null) return undefined
  const e = error as CaughtErrorShape
  return e.response?.data?.message ?? e.message
}
