# Playground 网络请求优化指南

## 问题分析

原有的 Playground 组件存在以下网络请求问题：

1. **重复请求**：`fetchInstances()` 和 `fetchAvailableModels()` 实际上调用同一个 API 端点
2. **无缓存机制**：每次切换选项卡都会重新发起网络请求
3. **频繁请求**：用户快速点击刷新按钮会导致多个并发请求
4. **资源浪费**：每个组件独立管理相同的数据

## 优化方案

### 1. 缓存管理 (`playgroundCache.ts`)

- 实现了统一的缓存管理机制
- 5分钟缓存过期时间
- 防抖机制避免重复请求
- 智能数据复用（模型数据从实例数据中提取）

### 2. 数据管理 Composable (`usePlaygroundData.ts`)

- 封装了通用的数据获取逻辑
- 提供响应式状态管理
- 统一的错误处理和用户提示

### 3. 全局预加载

- 在 PlaygroundMain 组件挂载时预加载常用数据
- 减少用户首次访问的等待时间

## 迁移步骤

### 步骤 1：替换数据管理逻辑

**原有代码：**
```typescript
// 每个组件都有这些重复代码
const availableInstances = ref([])
const availableModels = ref([])
const instancesLoading = ref(false)
const modelsLoading = ref(false)

const fetchInstances = async () => {
  instancesLoading.value = true
  // ... 重复的请求逻辑
}

const fetchAvailableModels = async () => {
  modelsLoading.value = true
  // ... 重复的请求逻辑
}

onMounted(() => {
  fetchInstances()
  fetchAvailableModels() // 重复请求！
})
```

**优化后代码：**
```typescript
import { usePlaygroundData } from '@/composables/usePlaygroundData'

const {
  availableInstances,
  availableModels,
  instancesLoading,
  modelsLoading,
  selectedInstanceId,
  selectedInstanceInfo,
  onInstanceChange,
  initializeData,
  refreshData
} = usePlaygroundData('chat') // 传入服务类型

onMounted(() => {
  initializeData() // 一次调用获取所有数据
})
```

### 步骤 2：更新刷新按钮逻辑

**原有代码：**
```vue
<el-button @click="fetchInstances" :loading="instancesLoading">
  刷新实例
</el-button>
<el-button @click="fetchAvailableModels" :loading="modelsLoading">
  刷新模型
</el-button>
```

**优化后代码：**
```vue
<el-button @click="refreshData" :loading="instancesLoading || modelsLoading">
  刷新数据
</el-button>
```

### 步骤 3：移除重复的事件监听

**原有代码：**
```typescript
// 每个组件都监听相同的事件
document.addEventListener('playground-refresh-models', () => {
  fetchInstances()
  fetchAvailableModels()
})
```

**优化后代码：**
```typescript
document.addEventListener('playground-refresh-models', refreshData)
```

## 性能提升

### 网络请求优化

- **减少 50% 的网络请求**：消除了重复的 API 调用
- **缓存命中率**：5分钟内的重复访问直接使用缓存
- **防抖保护**：300ms 内的重复请求会被合并

### 用户体验提升

- **更快的响应速度**：缓存数据的即时加载
- **减少加载状态**：预加载机制减少用户等待
- **智能错误处理**：网络失败时优雅降级到缓存数据

### 代码质量提升

- **减少重复代码**：从 ~200 行重复逻辑减少到 ~20 行
- **统一状态管理**：所有组件共享相同的数据状态
- **更好的可维护性**：集中的缓存和错误处理逻辑

## 监控和调试

### 缓存状态查看

```typescript
import { getCacheStatus } from '@/stores/playgroundCache'

// 在开发者工具中查看缓存状态
console.log(getCacheStatus())
```

### 手动清除缓存

```typescript
import { clearCache } from '@/stores/playgroundCache'

// 清除特定服务类型的缓存
clearCache('chat')

// 清除所有缓存
clearCache()
```

## 注意事项

1. **向后兼容**：新的 composable 与现有组件 API 保持兼容
2. **渐进迁移**：可以逐个组件进行迁移，不需要一次性全部更改
3. **错误处理**：保持了原有的错误提示机制
4. **类型安全**：所有新代码都有完整的 TypeScript 类型定义

## 下一步计划

1. 将所有 Playground 组件迁移到新的数据管理方案
2. 添加更多的性能监控指标
3. 考虑实现离线缓存机制
4. 优化大数据量场景下的性能表现