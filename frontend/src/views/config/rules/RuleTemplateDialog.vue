<template>
  <el-dialog v-model="visible" :title="t('rule.template.title')" width="680px">
    <el-form label-width="80px">
      <el-form-item :label="t('rule.template.name')">
        <el-input v-model="name" :placeholder="t('rule.template.namePlaceholder')" />
      </el-form-item>
    </el-form>

    <div class="template-grid">
      <div
        v-for="tpl in templates"
        :key="tpl.id"
        class="template-card"
        :class="{ active: selectedId === tpl.id }"
        @click="selectedId = tpl.id"
      >
        <div class="template-name">{{ tpl.name }}</div>
        <div class="template-desc">{{ tpl.description }}</div>
        <div class="template-meta">
          <el-tag size="small" type="info">{{ tpl.category }}</el-tag>
          <span class="template-tip">{{ tpl.usageTip }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ t('rule.cancel') }}</el-button>
      <el-button type="primary" :loading="creating" :disabled="!selectedId || !name.trim()" @click="handleNext">
        {{ t('rule.template.nextStep') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { createRuleFromTemplate, getRuleTemplates, type RuleDefinition, type RuleTemplate } from '@/api/rules'

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  created: [draft: RuleDefinition]
}>()

const { t } = useI18n()

const templates = ref<RuleTemplate[]>([])
const selectedId = ref('')
const name = ref('')
const creating = ref(false)

const loadTemplates = async () => {
  try {
    const res = await getRuleTemplates()
    templates.value = res.data?.data || []
  } catch (e) {
    ElMessage.error(t('rule.template.fetchListFailed'))
  }
}

const handleNext = async () => {
  if (!selectedId.value || !name.value.trim()) return
  creating.value = true
  try {
    const res = await createRuleFromTemplate(selectedId.value, { name: name.value.trim() })
    const draft = res.data?.data
    if (draft) {
      visible.value = false
      name.value = ''
      selectedId.value = ''
      emit('created', draft)
    }
  } catch (e) {
    ElMessage.error(t('rule.template.createFailed'))
  } finally {
    creating.value = false
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  max-height: 380px;
  overflow-y: auto;
}

.template-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-card:hover {
  border-color: var(--ja-primary);
}

.template-card.active {
  border-color: var(--ja-primary);
  background: var(--ja-primary-light-9);
}

.template-name {
  font-weight: 600;
  margin-bottom: 4px;
}

.template-desc {
  font-size: 12px;
  color: var(--ja-text-regular);
  margin-bottom: 8px;
  min-height: 32px;
}

.template-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.template-tip {
  font-size: 12px;
  color: var(--ja-text-secondary);
}
</style>
