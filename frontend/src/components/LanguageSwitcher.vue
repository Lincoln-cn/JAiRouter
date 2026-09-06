<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <span class="lang-switcher">
      <el-icon :size="16">
        <Globe />
      </el-icon>
      <span class="lang-label">{{ labels[locale] }}</span>
      <el-icon :size="12" class="lang-caret">
        <ArrowDown />
      </el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="opt in options"
          :key="opt"
          :command="opt"
          :class="{ 'lang-active': opt === locale }"
        >
          <el-icon v-if="opt === locale" :size="14">
            <Check />
          </el-icon>
          {{ labels[opt] }}
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
.lang-switcher {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--ja-text-regular);
  font-size: 13px;
  outline: none;
  user-select: none;
}

.lang-switcher:hover {
  color: var(--ja-primary);
}

.lang-label {
  white-space: nowrap;
}

.lang-caret {
  opacity: 0.6;
}

.lang-active {
  color: var(--ja-primary);
}
</style>
