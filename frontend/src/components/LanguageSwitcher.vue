<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
    <span class="lang-btn" tabindex="0">
      <el-icon :size="15">
        <Globe />
      </el-icon>
      <span class="lang-label">{{ labels[locale] }}</span>
      <el-icon :size="11" class="lang-caret">
        <ArrowDown />
      </el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu class="lang-menu">
        <el-dropdown-item
          v-for="opt in options"
          :key="opt"
          :command="opt"
          :class="{ 'lang-active': opt === locale }"
        >
          <span class="lang-item">
            <el-icon v-if="opt === locale" :size="13" class="lang-check">
              <Check />
            </el-icon>
            <span>{{ labels[opt] }}</span>
          </span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'
import type { AppLocale } from '@/utils/locale'

const { locale, setLocale, options, labels } = useLocale()

const handleCommand = (command: string | number | object) => {
  setLocale(command as AppLocale)
}
</script>

<style scoped>
/* 胶囊语言按钮：跟随主题令牌，亮/暗两态自动适配 */
.lang-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--ja-border);
  border-radius: 999px;
  background: var(--el-fill-color-blank);
  color: var(--ja-text-regular);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  user-select: none;
  outline: none;
  transition:
    color 0.2s,
    border-color 0.2s,
    background-color 0.2s;
}

.lang-btn:hover,
.lang-btn:focus-visible {
  color: var(--ja-primary);
  border-color: var(--ja-primary);
  background: var(--el-color-primary-light-9);
}

.lang-label {
  white-space: nowrap;
  font-weight: 500;
}

.lang-caret {
  opacity: 0.65;
  transition: transform 0.2s;
}

.lang-btn:hover .lang-caret,
.lang-btn:focus-visible .lang-caret {
  transform: rotate(180deg);
}
</style>

<!-- 下拉面板 teleport 至 body，scoped 不达 → 全局段 -->
<style>
.lang-menu .lang-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.lang-menu .lang-item .lang-check {
  color: var(--ja-primary);
}

.lang-menu .lang-active {
  color: var(--ja-primary);
  font-weight: 500;
}
</style>
