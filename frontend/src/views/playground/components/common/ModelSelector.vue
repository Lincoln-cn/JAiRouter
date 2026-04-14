<template>
  <div class="model-selector">
    <el-select
      v-model="selectedModel"
      :placeholder="placeholder"
      :loading="loading"
      :disabled="disabled"
      :clearable="clearable"
      :filterable="true"
      class="model-select"
      @change="handleChange"
    >
      <template #prefix>
        <el-icon><Cpu /></el-icon>
      </template>
      <el-option-group
        v-for="group in groupedInstances"
        :key="group.label"
        :label="group.label"
      >
        <el-option
          v-for="instance in group.instances"
          :key="instance.id"
          :label="instance.name"
          :value="instance.id"
        >
          <div class="model-option">
            <span class="model-name">{{ instance.name }}</span>
            <el-tag
              v-if="instance.modelName"
              size="small"
              type="info"
              class="model-tag"
            >
              {{ instance.modelName }}
            </el-tag>
          </div>
        </el-option>
      </el-option-group>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Cpu } from '@element-plus/icons-vue'

export interface ModelInstance {
  id: string
  name: string
  modelName?: string
  type?: string
  status?: string
  endpoint?: string
}

interface Props {
  modelValue?: string
  instances?: ModelInstance[]
  loading?: boolean
  disabled?: boolean
  clearable?: boolean
  placeholder?: string
  groupByType?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  instances: () => [],
  loading: false,
  disabled: false,
  clearable: false,
  placeholder: '选择模型',
  groupByType: true
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string, instance: ModelInstance | undefined]
}>()

const selectedModel = ref(props.modelValue)

// 按类型分组的实例
const groupedInstances = computed(() => {
  if (!props.groupByType) {
    return [{ label: '全部模型', instances: props.instances }]
  }

  const groups: Record<string, ModelInstance[]> = {}
  for (const instance of props.instances) {
    const type = instance.type || '其他'
    if (!groups[type]) {
      groups[type] = []
    }
    groups[type].push(instance)
  }

  return Object.entries(groups).map(([label, instances]) => ({
    label,
    instances
  }))
})

// 监听外部值变化
watch(
  () => props.modelValue,
  (val) => {
    selectedModel.value = val
  }
)

// 处理选择变化
const handleChange = (val: string) => {
  emit('update:modelValue', val)
  const instance = props.instances.find((i) => i.id === val)
  emit('change', val, instance)
}
</script>

<style scoped>
.model-selector {
  width: 100%;
}

.model-select {
  width: 100%;
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.model-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-tag {
  margin-left: 8px;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>