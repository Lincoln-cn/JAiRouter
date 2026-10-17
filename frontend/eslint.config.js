import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

/**
 * ESLint flat config（issue #462 A 方案）。
 *
 * 迁移要点：
 * - eslint 10 已不再支持 `.eslintrc.*`，旧配置让它直接报错 ⇒ 前端 lint 门闩此前完全不可用。
 * - `@rushstack/eslint-patch` 只服务于 eslintrc 的模块解析；flat config 下不需要它，
 *   但依赖暂时保留在 devDependencies 里（避免动 package-lock，后续可随其他依赖一起清理）。
 * - 忽略项从 `.eslintignore`（flat 下失效）搬进本文件的 `ignores`。
 */
export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/node_modules/**', '**/*.d.ts', '**/coverage/**'],
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    name: 'app/rules',
    rules: {
      // Vue
      'vue/multi-word-component-names': 'off',
      'vue/no-unused-vars': 'error',
      'vue/no-mutating-props': 'error',
      'vue/require-default-prop': 'error',
      'vue/require-prop-types': 'error',

      // TypeScript
      // 未使用变量已于 #494 清零（原 93 处：75 个 import 项 + 18 个局部变量/未用接口）
      // ⇒ 规则恢复为 error，任何新增未使用变量都会直接让门闩变红。
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          // 下划线前缀 = 有意不用的占位参数（如 vite 代理回调里的 req/res、e2e 里保留签名的 tag）
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          // JS 的 catch 绑定无法省略时才可用 `catch {}`，这里不强制改造存量代码
          caughtErrors: 'none',
          ignoreRestSiblings: true,
        },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',

      // General
      'no-console': 'off',
      'no-debugger': 'warn',
      // 正确性类，保留 error
      'prefer-const': 'error',
      'no-var': 'error',
      // 纯风格类：存量里用字符串拼接的地方很多，且 eslint 的自动修法会产出
      // `${  x  }` 这种双空格（还要连带改 6 个文件），本 PR 不混入格式化重构 ⇒ 降为 warn。
      // 修法：跑 `npm run lint:fix` 会改，但建议配合 prettier 一起做。
      'object-shorthand': 'warn',
      'prefer-template': 'warn',
    },
  },
)
