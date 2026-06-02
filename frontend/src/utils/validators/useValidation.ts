/**
 * 表单验证组合式函数
 * 提供表单验证的便捷方法
 */

import { ref, type Ref, type MaybeRef, unref } from 'vue'
import type { FormInstance } from 'element-plus'
import { rules, type ValidationRule } from './rules'

/**
 * 验证状态
 */
export interface ValidationState {
  isValid: boolean
  errors: Record<string, string[]>
  touched: Set<string>
}

/**
 * useValidation 组合式函数
 */
export function useValidation(formRef: Ref<FormInstance | undefined>) {
  const isValidating = ref(false)
  const validationState = ref<ValidationState>({
    isValid: true,
    errors: {},
    touched: new Set()
  })

  /**
   * 验证整个表单
   */
  const validate = async (): Promise<boolean> => {
    if (!formRef.value) return false
    
    isValidating.value = true
    try {
      await formRef.value.validate()
      validationState.value.isValid = true
      validationState.value.errors = {}
      return true
    } catch (fields) {
      validationState.value.isValid = false
      validationState.value.errors = fields as Record<string, string[]>
      return false
    } finally {
      isValidating.value = false
    }
  }

  /**
   * 验证单个字段
   */
  const validateField = async (prop: string): Promise<boolean> => {
    if (!formRef.value) return false
    
    try {
      await formRef.value.validateField(prop)
      delete validationState.value.errors[prop]
      return true
    } catch (error) {
      validationState.value.errors[prop] = error as string[]
      return false
    }
  }

  /**
   * 重置表单验证状态
   */
  const resetValidation = () => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
    validationState.value = {
      isValid: true,
      errors: {},
      touched: new Set()
    }
  }

  /**
   * 清除单个字段的验证状态
   */
  const clearFieldValidation = (prop: string) => {
    if (formRef.value) {
      formRef.value.clearValidate(prop)
    }
    delete validationState.value.errors[prop]
    validationState.value.touched.delete(prop)
  }

  /**
   * 标记字段为已触碰
   */
  const touchField = (prop: string) => {
    validationState.value.touched.add(prop)
  }

  /**
   * 获取字段错误信息
   */
  const getFieldError = (prop: string): string | undefined => {
    const errors = validationState.value.errors[prop]
    return errors && errors.length > 0 ? errors[0] : undefined
  }

  /**
   * 检查字段是否有错误
   */
  const hasFieldError = (prop: string): boolean => {
    return !!validationState.value.errors[prop]?.length
  }

  /**
   * 检查字段是否已触碰
   */
  const isFieldTouched = (prop: string): boolean => {
    return validationState.value.touched.has(prop)
  }

  return {
    isValidating,
    validationState,
    validate,
    validateField,
    resetValidation,
    clearFieldValidation,
    touchField,
    getFieldError,
    hasFieldError,
    isFieldTouched,
    rules
  }
}

/**
 * 创建动态验证规则
 */
export function createDynamicRules(
  getRules: () => ValidationRule[]
): Ref<ValidationRule[]> {
  return ref(getRules())
}

/**
 * 条件规则生成器
 */
export function useConditionalRules() {
  /**
   * 根据条件返回规则
   */
  const getRules = (
    condition: MaybeRef<boolean>,
    trueRules: ValidationRule[],
    falseRules: ValidationRule[] = []
  ): ValidationRule[] => {
    return unref(condition) ? trueRules : falseRules
  }

  /**
   * 当条件为真时应用规则
   */
  const when = (
    condition: MaybeRef<boolean>,
    ...ruleList: ValidationRule[]
  ): ValidationRule[] => {
    return getRules(condition, ruleList)
  }

  /**
   * 当条件为假时应用规则
   */
  const unless = (
    condition: MaybeRef<boolean>,
    ...ruleList: ValidationRule[]
  ): ValidationRule[] => {
    return getRules(condition, [], ruleList)
  }

  return { getRules, when, unless }
}

/**
 * 异步验证器工厂
 */
export function useAsyncValidation() {
  /**
   * 创建远程唯一性验证器
   * @param checkFn 检查函数，返回 true 表示已存在（不唯一）
   * @param message 错误消息
   */
  const unique = (
    checkFn: (value: any) => Promise<boolean>,
    message = '该值已存在'
  ): ValidationRule => ({
    validator: (rule, value, callback) => {
      if (!value) {
        callback()
        return
      }
      checkFn(value)
        .then(exists => {
          if (exists) {
            callback(new Error(message))
          } else {
            callback()
          }
        })
        .catch(() => {
          callback(new Error('验证失败'))
        })
    },
    trigger: 'blur'
  })

  /**
   * 创建异步验证器
   */
  const asyncValidate = (
    validateFn: (value: any) => Promise<boolean>,
    message = '验证失败'
  ): ValidationRule => ({
    validator: (rule, value, callback) => {
      if (!value) {
        callback()
        return
      }
      validateFn(value)
        .then(isValid => {
          if (isValid) {
            callback()
          } else {
            callback(new Error(message))
          }
        })
        .catch(() => {
          callback(new Error(message))
        })
    },
    trigger: 'blur'
  })

  return { unique, asyncValidate }
}

/**
 * 表单验证规则构建器
 */
export class RuleBuilder {
  private rules: ValidationRule[] = []

  /**
   * 添加必填规则
   */
  required(message?: string): this {
    this.rules.push(rules.required(message))
    return this
  }

  /**
   * 添加邮箱规则
   */
  email(message?: string): this {
    this.rules.push(rules.email(message))
    return this
  }

  /**
   * 添加最小长度规则
   */
  minLength(min: number, message?: string): this {
    this.rules.push(rules.minLength(min, message))
    return this
  }

  /**
   * 添加最大长度规则
   */
  maxLength(max: number, message?: string): this {
    this.rules.push(rules.maxLength(max, message))
    return this
  }

  /**
   * 添加自定义验证器
   */
  custom(validator: (value: any) => boolean | string, trigger: 'blur' | 'change' = 'blur'): this {
    this.rules.push({
      validator: (rule, value, callback) => {
        const result = validator(value)
        if (result === true) {
          callback()
        } else {
          callback(new Error(typeof result === 'string' ? result : '验证失败'))
        }
      },
      trigger
    })
    return this
  }

  /**
   * 添加模式匹配规则
   */
  pattern(pattern: RegExp, message?: string): this {
    this.rules.push({ pattern, message: message || '格式不正确', trigger: 'blur' })
    return this
  }

  /**
   * 构建规则数组
   */
  build(): ValidationRule[] {
    return [...this.rules]
  }
}

/**
 * 创建规则构建器
 */
export function createRules(): RuleBuilder {
  return new RuleBuilder()
}
