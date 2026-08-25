<template>
  <el-dialog v-model="visible" title="从模板创建规则" width="680px">
    <el-form label-width="80px">
      <el-form-item label="规则名称">
        <el-input v-model="name" placeholder="为从模板创建的规则命名" />
      </el-form-item>
    </el-form>

    <div class="template-grid">
      <div
        v-for="t in templates"
        :key="t.id"
        class="template-card"
        :class="{ active: selectedId === t.id }"
        @click="selectedId = t.id"
      >
        <div class="template-name">{{ t.name }}</div>
        <div class="template-desc">{{ t.description }}</div>
        <div class="template-meta">
          <el-tag size="small" type="info">{{ t.category }}</el-tag>
          <span class="template-tip">{{ t.usageTip }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="creating" :disabled="!selectedId || !name.trim()" @click="handleNext">
        下一步:编辑并保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createRuleFromTemplate, getRuleTemplates, type RuleDefinition, type RuleTemplate } from '@/api/rules'

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  created: [draft: RuleDefinition]
}>()

const templates = ref<RuleTemplate[]>([])
const selectedId = ref('')
const name = ref('')
const creating = ref(false)

const loadTemplates = async () => {
  try {
    const res = await getRuleTemplates()
    templates.value = res.data?.data || []
  } catch (e) {
    ElMessage.error('获取模板列表失败')
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
    ElMessage.error('模板创建失败')
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
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-card:hover {
  border-color: #409eff;
}

.template-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.template-name {
  font-weight: 600;
  margin-bottom: 4px;
}

.template-desc {
  font-size: 12px;
  color: #606266;
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
  color: #909399;
}
</style>
