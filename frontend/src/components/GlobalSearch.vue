<template>
  <div
    v-if="visible"
    class="global-search"
    role="dialog"
    aria-modal="true"
    :aria-label="t('globalSearch.dialogLabel')"
    @mousedown.self="close"
  >
    <div class="global-search__panel">
      <div class="global-search__field">
        <el-icon class="global-search__field-icon" aria-hidden="true"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="query"
          class="global-search__input"
          type="text"
          autocomplete="off"
          spellcheck="false"
          role="combobox"
          aria-autocomplete="list"
          aria-controls="global-search-results"
          aria-expanded="true"
          :aria-label="t('globalSearch.inputLabel')"
          :aria-activedescendant="activeDescendant"
          :placeholder="t('globalSearch.placeholder')"
        />
        <span v-if="loading" class="global-search__spinner" aria-hidden="true" />
        <kbd class="global-search__kbd">Esc</kbd>
      </div>

      <div
        id="global-search-results"
        class="global-search__results"
        role="listbox"
        :aria-label="t('globalSearch.resultsLabel')"
      >
        <p v-if="showEmpty" class="global-search__empty">{{ t('globalSearch.empty') }}</p>

        <section v-for="group in groups" :key="group.key" class="global-search__group">
          <header class="global-search__group-head">
            <span class="global-search__group-title">{{ t(group.titleKey) }}</span>
            <span v-if="group.total > group.items.length" class="global-search__group-total">
              {{ t('globalSearch.total', { count: group.total }) }}
            </span>
          </header>

          <button
            v-for="item in group.items"
            :id="optionId(item.index)"
            :key="item.id"
            type="button"
            role="option"
            class="global-search__item"
            :class="{ 'is-active': item.index === activeIndex }"
            :aria-selected="item.index === activeIndex"
            :data-search-index="item.index"
            @mousemove="setActive(item.index)"
            @click="select(item)"
          >
            <span class="global-search__item-main">
              <span class="global-search__item-title">{{ item.title }}</span>
              <span v-if="item.subtitle" class="global-search__item-subtitle">
                {{ item.subtitle }}
              </span>
            </span>
            <span class="global-search__item-enter" aria-hidden="true">↵</span>
          </button>
        </section>
      </div>

      <footer class="global-search__footer">
        <span>{{ t('globalSearch.hint') }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ⌘K / Ctrl+K 命令面板。
 *
 * - 数据与防抖/缓存全部在 useGlobalSearch 内，本组件只负责可见性、键盘导航与焦点还原。
 * - 遮罩用 rgba(16,24,40,.45)（任务书明确允许的遮罩色，不是装饰色），其余配色一律 --ja-* 令牌。
 * - 不使用 Teleport：面板是 position:fixed 全屏遮罩，且这样单测可直接挂载断言。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import { useGlobalSearch, type SearchItem } from '@/composables/useGlobalSearch'

const { t } = useI18n()
const router = useRouter()
const { query, groups, flatItems, loading, onOpen, onClose } = useGlobalSearch()

const visible = ref(false)
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

let triggerEl: HTMLElement | null = null
let previousOverflow = ''

const showEmpty = computed(
  () => query.value.trim().length > 0 && groups.value.length === 0 && !loading.value
)

const optionId = (index: number): string => `global-search-option-${index}`

const activeDescendant = computed(() =>
  flatItems.value.length > 0 ? optionId(activeIndex.value) : undefined
)

const setActive = (index: number): void => {
  activeIndex.value = index
}

const scrollActiveIntoView = (): void => {
  const el = document.querySelector<HTMLElement>(`[data-search-index="${activeIndex.value}"]`)
  // happy-dom 未实现 scrollIntoView，单测里跳过即可
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ block: 'nearest' })
  }
}

const move = (delta: number): void => {
  const total = flatItems.value.length
  if (total === 0) {
    return
  }
  activeIndex.value = (activeIndex.value + delta + total) % total
  nextTick(scrollActiveIntoView)
}

const open = async (): Promise<void> => {
  if (!visible.value) {
    triggerEl = document.activeElement instanceof HTMLElement ? document.activeElement : null
    visible.value = true
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    activeIndex.value = 0
    query.value = ''
  }
  onOpen()
  await nextTick()
  inputRef.value?.focus()
}

const close = (): void => {
  if (!visible.value) {
    return
  }
  visible.value = false
  document.body.style.overflow = previousOverflow
  onClose()

  const target = triggerEl
  triggerEl = null
  // 关闭后把焦点还给触发元素（body 不抢焦点）
  if (target && target.isConnected && target !== document.body) {
    target.focus()
  }
}

const select = (item: SearchItem): void => {
  close()
  void router.push(item.query ? { path: item.to, query: item.query } : item.to)
}

const onDocumentKeydown = (event: KeyboardEvent): void => {
  if (!visible.value) {
    return
  }
  switch (event.key) {
    case 'Escape':
      event.preventDefault()
      event.stopPropagation()
      close()
      break
    case 'ArrowDown':
      event.preventDefault()
      move(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      move(-1)
      break
    case 'Enter': {
      const item = flatItems.value[activeIndex.value]
      if (item) {
        event.preventDefault()
        select(item)
      }
      break
    }
    default:
      break
  }
}

watch(query, () => {
  activeIndex.value = 0
})

watch(
  () => flatItems.value.length,
  length => {
    if (activeIndex.value >= length) {
      activeIndex.value = 0
    }
  }
)

onMounted(() => {
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
  if (visible.value) {
    document.body.style.overflow = previousOverflow
    onClose()
  }
})

defineExpose({ open, close })
</script>

<style scoped>
.global-search {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 10vh var(--ja-space-4) var(--ja-space-4);
  background: rgba(16, 24, 40, 0.45);
}

.global-search__panel {
  display: flex;
  flex-direction: column;
  width: min(640px, 100%);
  max-height: 70vh;
  overflow: hidden;
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
  box-shadow: var(--ja-shadow-xl);
}

.global-search__field {
  display: flex;
  gap: var(--ja-space-2);
  align-items: center;
  padding: var(--ja-space-3) var(--ja-space-4);
  border-bottom: 1px solid var(--ja-border);
}

.global-search__field-icon {
  color: var(--ja-ink-3);
}

.global-search__input {
  flex: 1;
  min-width: 0;
  padding: 0;
  font-family: inherit;
  font-size: 15px;
  color: var(--ja-ink);
  background: transparent;
  border: 0;
  outline: none;
}

.global-search__input::placeholder {
  color: var(--ja-ink-3);
}

.global-search__spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--ja-border);
  border-top-color: var(--ja-primary);
  border-radius: 50%;
  animation: global-search-spin 0.7s linear infinite;
}

.global-search__kbd {
  padding: 1px 5px;
  font-family: inherit;
  font-size: 11px;
  color: var(--ja-ink-3);
  background: var(--ja-surface-2);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-sm);
}

.global-search__results {
  flex: 1;
  padding: var(--ja-space-1) 0;
  overflow-y: auto;
}

.global-search__group-head {
  display: flex;
  justify-content: space-between;
  padding: var(--ja-space-2) var(--ja-space-4) var(--ja-space-1);
}

.global-search__group-title,
.global-search__group-total {
  font-size: 11px;
  color: var(--ja-ink-3);
}

.global-search__group-title {
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.global-search__item {
  display: flex;
  gap: var(--ja-space-3);
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: var(--ja-space-2) var(--ja-space-4);
  font: inherit;
  color: var(--ja-ink);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.global-search__item.is-active {
  background: var(--ja-surface-2);
}

.global-search__item-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.global-search__item-title,
.global-search__item-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-search__item-title {
  font-size: 14px;
}

.global-search__item-subtitle {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.global-search__item-enter {
  font-size: 12px;
  color: var(--ja-ink-3);
  opacity: 0;
}

.global-search__item.is-active .global-search__item-enter {
  opacity: 1;
}

.global-search__empty {
  margin: 0;
  padding: var(--ja-space-6) var(--ja-space-4);
  font-size: 13px;
  color: var(--ja-ink-3);
  text-align: center;
}

.global-search__footer {
  padding: var(--ja-space-2) var(--ja-space-4);
  font-size: 11px;
  color: var(--ja-ink-3);
  border-top: 1px solid var(--ja-border);
}

@media (max-width: 767px) {
  .global-search {
    padding-top: 6vh;
  }

  .global-search__panel {
    max-height: 80vh;
  }
}

@keyframes global-search-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
