<template>
  <el-row :gutter="gutter">
    <el-col v-for="item in items" :key="item.key" :span="item.span || defaultSpan">
      <el-form-item :label="item.label" :prop="item.prop || item.key">
        <template v-if="item.type === 'input'">
          <el-input
            v-model="modelValue[item.key]"
            :placeholder="item.placeholder"
            :disabled="item.disabled"
            :maxlength="item.maxlength"
            show-word-limit
            clearable
          />
        </template>
        <template v-else-if="item.type === 'textarea'">
          <el-input
            v-model="modelValue[item.key]"
            type="textarea"
            :placeholder="item.placeholder"
            :disabled="item.disabled"
            :maxlength="item.maxlength"
            :rows="item.rows || 3"
            show-word-limit
          />
        </template>
        <template v-else-if="item.type === 'select'">
          <el-select
            v-model="modelValue[item.key]"
            :placeholder="item.placeholder"
            :disabled="item.disabled"
            :clearable="item.clearable"
            :filterable="item.filterable"
            style="width: 100%"
          >
            <el-option
              v-for="opt in item.options || []"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </template>
        <template v-else-if="item.type === 'number'">
          <el-input-number
            v-model="modelValue[item.key]"
            :min="item.min"
            :max="item.max"
            :step="item.step || 1"
            :disabled="item.disabled"
            style="width: 100%"
          />
        </template>
        <template v-else-if="item.type === 'switch'">
          <el-switch
            v-model="modelValue[item.key]"
            :active-text="item.activeText"
            :inactive-text="item.inactiveText"
            :disabled="item.disabled"
          />
        </template>
        <template v-else-if="item.type === 'datetime'">
          <el-date-picker
            v-model="modelValue[item.key]"
            type="datetime"
            :placeholder="item.placeholder"
            :disabled="item.disabled"
            :clearable="item.clearable"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </template>
        <template v-else-if="item.type === 'checkbox'">
          <el-checkbox-group v-model="modelValue[item.key]">
            <el-checkbox
              v-for="opt in item.options || []"
              :key="opt.value"
              :label="opt.value"
            >
              {{ opt.label }}
            </el-checkbox>
          </el-checkbox-group>
        </template>
        <template v-else-if="item.type === 'slot'">
          <slot :name="item.key" :model="modelValue" :item="item" />
        </template>
      </el-form-item>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
export interface FormFieldOption {
  label: string
  value: string | number | boolean
}

export interface FormField {
  key: string
  label: string
  type: 'input' | 'textarea' | 'select' | 'number' | 'switch' | 'datetime' | 'checkbox' | 'slot'
  prop?: string
  placeholder?: string
  disabled?: boolean
  maxlength?: number
  rows?: number
  clearable?: boolean
  filterable?: boolean
  options?: FormFieldOption[]
  min?: number
  max?: number
  step?: number
  activeText?: string
  inactiveText?: string
  span?: number
}

interface Props {
  modelValue: Record<string, any>
  items: FormField[]
  gutter?: number
  defaultSpan?: number
}

withDefaults(defineProps<Props>(), {
  gutter: 20,
  defaultSpan: 12
})
</script>

<style scoped>
.el-row {
  width: 100%;
}
</style>
