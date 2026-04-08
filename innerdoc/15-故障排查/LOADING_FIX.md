# Playground 加载状态修复

## 问题描述

用户反馈：接口正确返回了数据，但界面上还在显示加载状态，没有正确响应。

## 问题分析

通过分析代码，发现了以下问题：

### 1. 缓存逻辑问题 ❌
**问题**：当缓存正在加载时，`getCachedInstances` 函数直接返回空数据
```typescript
// 错误的逻辑
if (cache.loading) {
  return cache.data // 返回空数组，导致界面显示为空
}
```

**影响**：界面显示空白，用户看不到数据

### 2. 加载状态管理问题 ❌
**问题**：composable 中的加载状态与缓存加载状态不同步
- 缓存层有自己的 `loading` 状态
- composable 层也有自己的 `instancesLoading` 状态
- 两者没有正确同步

### 3. 导入错误 ❌
**问题**：缓存文件中错误导入了 `getModelsByServiceType`
```typescript
// 错误的导入
import { getServiceInstances, getModelsByServiceType } from '@/api/dashboard'
```

## 修复方案

### 1. 修复重复请求问题 ✅
**问题**：每个服务类型都发起了两次相同的请求
```typescript
// 原来的防抖机制有问题，会导致 Promise 不 resolve
```

**修复**：使用请求去重机制
```typescript
const dedupeRequest = <T>(key: string, fn: () => Promise<T>): Promise<T> => {
  if (pendingRequests.has(key)) {
    return pendingRequests.get(key)! // 复用进行中的请求
  }
  // 创建新请求并记录
}
```

### 2. 修复缓存等待逻辑 ✅
```typescript
// 修复后的逻辑
if (cache.loading) {
  return new Promise((resolve) => {
    const checkLoading = () => {
      if (!cache.loading) {
        resolve(cache.data)
      } else {
        setTimeout(checkLoading, 100)
      }
    }
    checkLoading()
  })
}
```

**效果**：当缓存正在加载时，等待加载完成再返回数据

### 2. 优化加载状态管理 ✅
```typescript
// 只有在必要时才显示加载状态
if (forceRefresh || availableInstances.value.length === 0) {
  instancesLoading.value = true
}
```

**效果**：避免不必要的加载状态显示，提升用户体验

### 3. 优化加载状态管理 ✅
**问题**：即使有缓存数据也显示加载状态
```typescript
// 总是显示加载状态
instancesLoading.value = true
```

**修复**：智能加载状态管理
```typescript
const shouldShowLoading = forceRefresh || availableInstances.value.length === 0
if (shouldShowLoading) {
  instancesLoading.value = true
}
```

### 4. 修复导入错误 ✅
```typescript
// 正确的导入
import { getServiceInstances } from '@/api/dashboard'
```

**效果**：消除编译错误，确保代码正常运行

### 5. 添加组件初始化优化 ✅
**问题**：组件可能在完全挂载前就开始数据初始化
```typescript
onMounted(() => {
  initializeData() // 可能太早执行
})
```

**修复**：等待组件完全挂载
```typescript
onMounted(async () => {
  await nextTick() // 等待下一个 tick
  await initializeData()
})
```

### 6. 添加调试工具 ✅
- 创建了 `debugPlayground.ts` 调试工具
- 在控制台添加了详细的日志输出
- 可以通过 `debugPlaygroundData()` 函数查看数据加载情况

## 修复后的流程

### 初始化流程
1. 组件 `onMounted` 调用 `initializeData()`
2. `initializeData()` 调用 `fetchData(false, false)`
3. `fetchData()` 并行调用 `getCachedInstances()` 和 `getCachedModels()`
4. 缓存函数检查是否有有效缓存
5. 如果没有缓存，发起 API 请求
6. 更新界面数据和加载状态

### 刷新流程
1. 用户点击刷新按钮
2. 调用 `fetchInstances(true)` 强制刷新
3. 显示加载状态
4. 发起 API 请求
5. 更新缓存和界面数据
6. 隐藏加载状态

## 调试方法

### 1. 浏览器控制台
```javascript
// 查看所有服务类型的数据加载情况
debugPlaygroundData()
```

### 2. 查看日志
- `[serviceType] 开始初始化数据...`
- `[Cache] 开始获取 serviceType 实例数据...`
- `[serviceType] 数据初始化完成: { instances: X, models: Y }`

### 3. 检查网络请求
- 打开开发者工具 Network 面板
- 查看 `/config/instance/type/{serviceType}` 请求
- 确认返回状态码为 200 且数据正确

## 预期效果

修复后，用户应该看到：
1. ✅ 页面加载时正确显示实例列表
2. ✅ 加载状态正确显示和隐藏
3. ✅ 缓存机制正常工作
4. ✅ 刷新按钮功能正常
5. ✅ 切换选项卡时数据正确显示

## 测试建议

1. **清除浏览器缓存**后重新加载页面
2. **切换不同的选项卡**（对话、文本嵌入、重排序等）
3. **点击刷新按钮**测试强制刷新功能
4. **在控制台运行** `debugPlaygroundData()` 查看数据状态
5. **检查网络面板**确认 API 请求正常

如果问题仍然存在，请提供：
- 浏览器控制台的错误信息
- 网络请求的详细信息
- `debugPlaygroundData()` 的输出结果