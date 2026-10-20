<template>
  <div ref="stageRef" class="login-stage" :class="{ 'is-static': isStatic }">
    <LoginBackdrop :static="isStatic" />

    <section class="brandwall">
      <div class="brandwall__inner">
        <img class="brandwall__mark" :src="logoBadge" alt="" width="52" height="52" />

        <p class="brandwall__eyebrow reveal d1">{{ t('login.eyebrow') }}</p>
        <h1 class="brandwall__title reveal d2">JAiRouter</h1>
        <p class="brandwall__lede reveal d3">{{ t('login.lede') }}</p>

        <ul class="brandwall__facts reveal d4">
          <li v-for="fact in facts" :key="fact.index">
            <span class="brandwall__num">{{ fact.index }}</span>
            <span class="brandwall__fact">
              <b>{{ fact.title }}</b>
              <span>{{ fact.desc }}</span>
            </span>
          </li>
        </ul>

        <footer class="brandwall__foot reveal d5">
          <span class="brandwall__version">v{{ appVersion }}</span>
          <a :href="DOCS_URL" target="_blank" rel="noopener noreferrer">{{ t('login.docs') }}</a>
          <a :href="RELEASES_URL" target="_blank" rel="noopener noreferrer">
            {{ t('login.releases') }}
          </a>
        </footer>
      </div>
    </section>

    <section class="formside">
      <!-- `.login-form` 容器 / 两个 input 的先后顺序 / `button.login-button`
           是 e2e/harness.mjs 的硬依赖（16 个 spec），重排时必须同步改 e2e，否则整组挂掉 -->
      <el-form
        ref="loginFormRef"
        class="login-form reveal d2"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        hide-required-asterisk
        @submit.prevent="handleLogin"
      >
        <h2 class="login-form__title">{{ t('login.consoleTitle') }}</h2>
        <p class="login-form__sub">{{ t('login.consoleSubtitle') }}</p>

        <div v-if="errorText" class="login-form__alert" role="alert">
          <el-icon class="login-form__alert-icon"><WarningFilled /></el-icon>
          <span>{{ errorText }}</span>
        </div>

        <el-form-item prop="username" label-for="login-username">
          <template #label>{{ t('login.username') }}</template>
          <el-input
            id="login-username"
            v-model="loginForm.username"
            size="large"
            prefix-icon="User"
            :placeholder="t('login.usernamePlaceholder')"
            autocomplete="username"
            @input="clearError"
          />
        </el-form-item>

        <el-form-item prop="password" label-for="login-password">
          <template #label>{{ t('login.password') }}</template>
          <el-input
            id="login-password"
            v-model="loginForm.password"
            type="password"
            size="large"
            show-password
            prefix-icon="Lock"
            :placeholder="t('login.passwordPlaceholder')"
            autocomplete="current-password"
            @input="clearError"
          />
        </el-form-item>

        <div class="login-form__row">
          <span class="login-form__hint">{{ t('login.forgotHint') }}</span>
        </div>

        <el-button
          class="login-button"
          type="primary"
          native-type="submit"
          size="large"
          :loading="loading"
        >
          <span v-if="!loading">{{ t('login.submit') }}</span>
          <span v-else>{{ t('login.submitting') }}</span>
        </el-button>

        <div class="login-form__foot">
          <span class="login-form__notice">
            <el-icon :size="13"><Lock /></el-icon>
            {{ t('login.auditNotice') }}
          </span>
          <span class="login-form__lang" role="group" :aria-label="t('login.language')">
            <button
              type="button"
              class="login-form__lang-btn"
              :class="{ 'is-active': locale === 'zh-CN' }"
              @click="setLocale('zh-CN')"
            >
              中文
            </button>
            <span class="login-form__lang-sep">/</span>
            <button
              type="button"
              class="login-form__lang-btn"
              :class="{ 'is-active': locale === 'en-US' }"
              @click="setLocale('en-US')"
            >
              EN
            </button>
          </span>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, WarningFilled } from '@element-plus/icons-vue'
import { version as appVersion } from '../../package.json'
import logoBadge from '@/assets/brand/logo-badge-256.png'
import { useUserStore } from '@/stores/user'
import { getErrorMessage } from '@/utils/error'
import { useLocale } from '@/composables/useLocale'
import LoginBackdrop from './login/components/LoginBackdrop.vue'
import { useParallax } from './login/composables/useParallax'
import { useStaticFallback } from './login/composables/useStaticFallback'

// 页脚外链（与 README 事实块一致）。仓库内没有「状态页」，第二项用 Releases 代替设计稿的「状态」
const DOCS_URL = 'https://jairouter.com'
const RELEASES_URL = 'https://github.com/Lincoln-cn/JAiRouter/releases'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const { locale, setLocale } = useLocale()

const { isStatic } = useStaticFallback()

const stageRef = ref<HTMLElement | null>(null)
// 指针视差 → 根节点 CSS 变量；静止退路下完全不挂监听（composable 内部判 enabled）
useParallax(
  stageRef,
  computed(() => !isStatic.value)
)

const loginFormRef = ref<FormInstance | null>(null)
const loading = ref(false)
/** 登录失败文案（inline alert）；校验类失败只走字段下方提示 */
const errorText = ref('')

const loginForm = reactive({
  username: '',
  password: ''
})

// v2.10.3: computed 化，语言切换后校验文案即时更新
const loginRules = computed<FormRules<typeof loginForm>>(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

const facts = computed(() => [
  { index: '01', title: t('login.factProtocol'), desc: t('login.factProtocolDesc') },
  { index: '02', title: t('login.factGovernance'), desc: t('login.factGovernanceDesc') },
  { index: '03', title: t('login.factAudit'), desc: t('login.factAuditDesc') }
])

const clearError = () => {
  errorText.value = ''
}

const handleLogin = async () => {
  clearError()

  if (!loginFormRef.value) return

  // 只有「校验通过」之后的失败才进 inline alert：必填校验失败时字段下方已有提示
  let validated = false

  try {
    await loginFormRef.value.validate()
    validated = true
    loading.value = true

    // 调用用户 store 的登录方法
    await userStore.login(loginForm.username, loginForm.password)

    // 跳转到仪表板 (将使用Layout组件)
    router.push({ name: 'dashboard-main' })
  } catch (error) {
    console.error('登录失败:', error)
    // 显示错误消息给用户
    const message = getErrorMessage(error) || t('login.failed')
    // 必填校验失败时字段下方已有提示；真正的登录失败用页内 alert 常显，
    // 不再叠一条 toast（同一个错误提示两遍没有信息增量）
    if (validated) {
      errorText.value = message
    }
  } finally {
    loading.value = false
  }
}

// 页面加载时检查是否已经登录
onMounted(() => {
  if (userStore.isAuthenticated()) {
    // 如果已经登录，直接跳转到仪表板
    router.push({ name: 'dashboard-main' })
  }
})
</script>

<style scoped>
/* ============ 骨架：44 / 56 两栏，切分比例同时喂给背景层（--login-split） ============ */
.login-stage {
  --login-split: 44%;
  position: relative;
  display: grid;
  grid-template-columns: var(--login-split) 1fr;
  min-height: 100vh;
  isolation: isolate;
  color: var(--ja-ink);
}

/* ============ 左：品牌墙 ============ */
.brandwall {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding: 72px 56px 48px;
  border-right: 1px solid var(--ja-border);
  /* canvas −16/−11px ↔ 品牌墙 +5/+3px：反向位移才有「前后两层」的错觉 */
  transform: translate3d(calc(var(--login-par-x, 0) * 5px), calc(var(--login-par-y, 0) * 3px), 0);
  will-change: transform;
}

/* 左上角提亮，让徽标与标题从冷色微光里浮出来（设计稿 .brandwall::before 的 scrim） */
.brandwall::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(120% 70% at 0% 0%, var(--ja-paper) 0%, transparent 58%);
  opacity: 0.35;
  pointer-events: none;
}

.brandwall__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.brandwall__mark {
  display: block;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  box-shadow:
    0 0 0 1px var(--ja-border),
    0 8px 20px -12px rgba(8, 20, 32, 0.55);
}

.brandwall__eyebrow {
  margin: 40px 0 10px;
  color: var(--ja-ink-3);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.brandwall__title {
  margin: 0;
  color: var(--ja-ink);
  font-size: 38px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.015em;
}

.brandwall__lede {
  max-width: 34ch;
  margin: 16px 0 0;
  color: var(--ja-ink-2);
  font-size: 16px;
  line-height: 1.7;
}

.brandwall__facts {
  max-width: 460px;
  margin: 56px 0 0;
  padding: 0;
  list-style: none;
}

.brandwall__facts li {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--ja-border);
}

.brandwall__facts li:last-child {
  border-bottom: 1px solid var(--ja-border);
}

.brandwall__num {
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.7;
  font-variant-numeric: tabular-nums;
}

.brandwall__fact {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brandwall__fact b {
  color: var(--ja-ink);
  font-size: 14px;
  font-weight: 600;
}

.brandwall__fact span {
  color: var(--ja-ink-2);
  font-size: 13px;
  line-height: 1.6;
}

.brandwall__foot {
  display: flex;
  gap: 18px;
  margin-top: auto;
  padding-top: 40px;
  color: var(--ja-ink-3);
  font-size: 13px;
}

.brandwall__foot a {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition:
    color 0.18s ease,
    border-color 0.18s ease;
}

.brandwall__foot a:hover,
.brandwall__foot a:focus-visible {
  color: var(--ja-ink-2);
  border-bottom-color: var(--ja-border);
}

.brandwall__version {
  font-variant-numeric: tabular-nums;
}

/* ============ 右：表单 ============ */
.formside {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
}

/* 中缝：从 surface 渐隐到 paper，弱化左右两块的硬切边 */
.formside::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(90deg, var(--ja-surface) 0%, transparent 34%);
  opacity: 0.5;
  pointer-events: none;
}

.login-form {
  position: relative;
  z-index: 1;
  width: min(400px, 100%);
}

.login-form__title {
  margin: 0;
  color: var(--ja-ink);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.login-form__sub {
  margin: 6px 0 28px;
  color: var(--ja-ink-2);
  font-size: 14px;
}

/* inline alert：与设计稿一致的左侧 2px 竖线 + 淡底 */
.login-form__alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 20px;
  padding: 10px 12px;
  border-left: 2px solid var(--ja-danger);
  border-radius: 0 6px 6px 0;
  background: var(--el-color-danger-light-9);
  color: var(--ja-ink-2);
  font-size: 13px;
  line-height: 1.5;
}

.login-form__alert-icon {
  flex: none;
  margin-top: 1px;
  color: var(--ja-danger);
  font-size: 16px;
}

.login-form__row {
  display: flex;
  justify-content: flex-end;
  margin: 2px 0 20px;
}

/* 没有自助找回密码通道（后端未提供），这里只做提示，不做成可点的假链接 */
.login-form__hint {
  color: var(--ja-ink-3);
  font-size: 13px;
}

.login-form__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  color: var(--ja-ink-3);
  font-size: 12px;
}

.login-form__notice {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.login-form__lang {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 2px;
}

.login-form__lang-sep {
  color: var(--ja-ink-3);
}

.login-form__lang-btn {
  padding: 2px 4px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ja-ink-3);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.18s ease;
}

.login-form__lang-btn:hover,
.login-form__lang-btn:focus-visible {
  color: var(--ja-ink);
}

.login-form__lang-btn.is-active {
  color: var(--ja-ink);
  font-weight: 600;
}

.login-form__lang-btn:focus-visible {
  outline: 2px solid var(--ja-accent);
  outline-offset: 1px;
}

/* ============ Element Plus 表单控件改造（只覆盖登录页这一处） ============ */
.login-form :deep(.el-form-item) {
  /* 30px：设计稿「输入框 → 下一个 label」≈28px，并给下方绝对定位的报错文案留出呼吸空间 */
  margin-bottom: 30px;
}

.login-form :deep(.el-form-item__label) {
  display: block;
  height: auto;
  margin-bottom: 7px;
  padding: 0;
  color: var(--ja-ink-2);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}

.login-form :deep(.el-form-item__content) {
  line-height: normal;
}

/* 错误文案绝对定位在控件下方，靠 form-item 的 18px 外边距留位（不挤动布局） */
.login-form :deep(.el-form-item__error) {
  padding-top: 6px;
  color: var(--ja-danger);
  font-size: 12px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  padding: 0 12px;
  border-radius: var(--ja-radius);
  background-color: var(--ja-paper);
  box-shadow: 0 0 0 1px var(--ja-border) inset;
  transition: box-shadow 0.18s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--ja-ink-3) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--ja-accent) inset,
    0 0 0 3px var(--ja-accent-soft);
}

.login-form :deep(.el-input__inner) {
  height: 42px;
  color: var(--ja-ink);
  font-size: 14px;
}

.login-form :deep(.el-input__prefix) {
  color: var(--ja-ink-3);
}

.login-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--ja-danger) inset;
}

/* 报错态下仍要看得见焦点：优先级高于上一条，红边 + 淡红焦点环 */
.login-form :deep(.el-form-item.is-error .el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--ja-danger) inset,
    0 0 0 3px var(--el-color-danger-light-8);
}

/* 提交按钮：主色沿用令牌层 --el-color-primary（= --ja-primary），不引入设计稿的临时品牌蓝 */
.login-form :deep(.login-button) {
  width: 100%;
  height: 44px;
  border-radius: var(--ja-radius);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  transition:
    filter 0.18s ease,
    transform 0.12s ease;
}

.login-form :deep(.login-button:hover) {
  filter: brightness(1.06);
}

.login-form :deep(.login-button:active) {
  transform: translateY(1px);
}

/* ============ 入场 ============ */
@keyframes loginFadeUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.reveal {
  animation: loginFadeUp 0.55s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.d1 {
  animation-delay: 0.04s;
}

.d2 {
  animation-delay: 0.1s;
}

.d3 {
  animation-delay: 0.16s;
}

.d4 {
  animation-delay: 0.22s;
}

.d5 {
  animation-delay: 0.28s;
}

/* ============ 两条静止退路：?static 与 prefers-reduced-motion 表现必须一致 ============ */
.login-stage.is-static .reveal {
  animation: none;
}

/* 品牌墙的视差位移在静止态归零（画布那边的同类规则在 LoginBackdrop.vue 自己的作用域里） */
.login-stage.is-static .brandwall {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .reveal {
    animation: none;
  }

  .brandwall {
    transform: none;
  }
}

/* ============ 响应式 ============ */
@media (max-width: 1199px) {
  .login-stage {
    --login-split: 40%;
  }

  .brandwall {
    padding: 56px 40px 40px;
  }

  .brandwall__title {
    font-size: 30px;
  }
}

@media (max-width: 900px) {
  .login-stage {
    --login-split: 0%;
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  /* 单列时品牌墙退回横条：表面色用半透明遮罩上（留一点画布透出来），表单侧铺 paper 压住背景 */
  .brandwall {
    flex-direction: row;
    align-items: center;
    padding: 20px 24px;
    border-right: 0;
    border-bottom: 1px solid var(--ja-border);
    background: none;
    transform: none;
  }

  /* 桌面那条径向 scrim 在窄屏改成「surface 半透遮罩」：82% 不透明度让 13 节点拓扑隐约透出 */
  .brandwall::before {
    display: block;
    background: var(--ja-surface);
    opacity: 0.82;
  }

  .brandwall__inner {
    flex-direction: row;
    flex: 1;
    align-items: center;
    gap: 14px;
  }

  .brandwall__mark {
    width: 32px;
    height: 32px;
    border-radius: 8px;
  }

  .brandwall__title {
    font-size: 20px;
  }

  .brandwall__eyebrow,
  .brandwall__lede,
  .brandwall__facts,
  .brandwall__foot {
    display: none;
  }

  .formside {
    align-items: flex-start;
    padding: 40px 24px 48px;
    /* 表单侧整块铺纸：竖向蒙版下画布会淡出到这片区域，铺纸后输入框背后彻底干净 */
    background: var(--ja-paper);
  }

  .formside::before {
    display: none;
  }
}

@media (max-width: 480px) {
  .formside {
    padding: 32px 16px 40px;
  }

  .login-form__title {
    font-size: 18px;
  }

  .login-form__foot {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}

/* 矮屏（笔记本 1280×720 一类）：收紧左栏纵向节奏，避免内容把页脚顶出视口 */
@media (min-width: 901px) and (max-height: 760px) {
  .brandwall {
    padding: 40px 56px 32px;
  }

  .brandwall__facts {
    margin-top: 32px;
  }

  .brandwall__foot {
    padding-top: 24px;
  }
}
</style>
