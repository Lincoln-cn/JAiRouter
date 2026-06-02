/**
 * 表单验证规则定义
 * 包含常用的表单验证规则和自定义验证器
 */

import type { FormItemRule } from 'element-plus'

/**
 * 验证规则类型
 */
export interface ValidationRule extends FormItemRule {
  trigger?: 'blur' | 'change' | 'input'
}

/**
 * 内置验证规则
 */
export const rules = {
  // 必填
  required: (message = '此项为必填项'): ValidationRule => ({
    required: true,
    message,
    trigger: 'blur'
  }),

  // 邮箱
  email: (message = '请输入有效的邮箱地址'): ValidationRule => ({
    type: 'email',
    message,
    trigger: 'blur'
  }),

  // 手机号（中国）
  phone: (message = '请输入有效的手机号码'): ValidationRule => ({
    pattern: /^1[3-9]\d{9}$/,
    message,
    trigger: 'blur'
  }),

  // URL
  url: (message = '请输入有效的URL地址'): ValidationRule => ({
    type: 'url',
    message,
    trigger: 'blur'
  }),

  // 最小长度
  minLength: (min: number, message?: string): ValidationRule => ({
    min,
    message: message || `长度不能少于 ${min} 个字符`,
    trigger: 'blur'
  }),

  // 最大长度
  maxLength: (max: number, message?: string): ValidationRule => ({
    max,
    message: message || `长度不能超过 ${max} 个字符`,
    trigger: 'blur'
  }),

  // 长度范围
  lengthRange: (min: number, max: number, message?: string): ValidationRule => ({
    min,
    max,
    message: message || `长度应在 ${min} 到 ${max} 个字符之间`,
    trigger: 'blur'
  }),

  // 数字
  number: (message = '请输入数字'): ValidationRule => ({
    pattern: /^\d+(\.\d+)?$/,
    message,
    trigger: 'blur'
  }),

  // 整数
  integer: (message = '请输入整数'): ValidationRule => ({
    pattern: /^-?\d+$/,
    message,
    trigger: 'blur'
  }),

  // 正整数
  positiveInteger: (message = '请输入正整数'): ValidationRule => ({
    pattern: /^[1-9]\d*$/,
    message,
    trigger: 'blur'
  }),

  // 最小值
  minValue: (min: number, message?: string): ValidationRule => ({
    validator: (rule, value, callback) => {
      if (value !== '' && value !== null && value !== undefined) {
        const num = Number(value)
        if (isNaN(num) || num < min) {
          callback(new Error(message || `值不能小于 ${min}`))
        } else {
          callback()
        }
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }),

  // 最大值
  maxValue: (max: number, message?: string): ValidationRule => ({
    validator: (rule, value, callback) => {
      if (value !== '' && value !== null && value !== undefined) {
        const num = Number(value)
        if (isNaN(num) || num > max) {
          callback(new Error(message || `值不能大于 ${max}`))
        } else {
          callback()
        }
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }),

  // 值范围
  valueRange: (min: number, max: number, message?: string): ValidationRule => ({
    validator: (rule, value, callback) => {
      if (value !== '' && value !== null && value !== undefined) {
        const num = Number(value)
        if (isNaN(num) || num < min || num > max) {
          callback(new Error(message || `值应在 ${min} 到 ${max} 之间`))
        } else {
          callback()
        }
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }),

  // IP 地址
  ip: (message = '请输入有效的IP地址'): ValidationRule => ({
    pattern: /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/,
    message,
    trigger: 'blur'
  }),

  // 端口号
  port: (message = '请输入有效的端口号(1-65535)'): ValidationRule => ({
    validator: (rule, value, callback) => {
      const num = Number(value)
      if (value === '' || value === null || value === undefined) {
        callback()
      } else if (isNaN(num) || num < 1 || num > 65535 || !Number.isInteger(num)) {
        callback(new Error(message))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }),

  // API Key 格式
  apiKey: (message = 'API Key格式无效'): ValidationRule => ({
    pattern: /^[a-zA-Z0-9_-]{16,64}$/,
    message,
    trigger: 'blur'
  }),

  // 用户名（字母开头，允许字母数字下划线）
  username: (message = '用户名必须以字母开头，只能包含字母、数字和下划线'): ValidationRule => ({
    pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/,
    message,
    trigger: 'blur'
  }),

  // 密码强度（至少8位，包含大小写字母和数字）
  password: (message = '密码至少8位，需包含大小写字母和数字'): ValidationRule => ({
    pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,}$/,
    message,
    trigger: 'blur'
  }),

  // 十六进制颜色
  hexColor: (message = '请输入有效的十六进制颜色值'): ValidationRule => ({
    pattern: /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/,
    message,
    trigger: 'blur'
  }),

  // 不含特殊字符
  noSpecialChars: (message = '不能包含特殊字符'): ValidationRule => ({
    pattern: /^[\u4e00-\u9fa5a-zA-Z0-9_\-]+$/,
    message,
    trigger: 'blur'
  }),

  // 中文名
  chineseName: (message = '请输入有效的中文姓名'): ValidationRule => ({
    pattern: /^[\u4e00-\u9fa5]{2,10}$/,
    message,
    trigger: 'blur'
  }),

  // 英文名
  englishName: (message = '请输入有效的英文名'): ValidationRule => ({
    pattern: /^[a-zA-Z][a-zA-Z\s]{1,50}$/,
    message,
    trigger: 'blur'
  })
}

/**
 * 创建自定义验证器
 */
export function createValidator(
  validator: (value: any) => boolean | string,
  trigger: 'blur' | 'change' = 'blur'
): ValidationRule {
  return {
    validator: (rule, value, callback) => {
      const result = validator(value)
      if (result === true) {
        callback()
      } else {
        callback(new Error(typeof result === 'string' ? result : '验证失败'))
      }
    },
    trigger
  }
}

/**
 * 组合多个验证规则
 */
export function combineRules(...ruleArrays: ValidationRule[][]): ValidationRule[] {
  return ruleArrays.flat()
}

/**
 * 条件验证规则
 * @param condition 条件函数
 * @param rules 条件满足时应用的规则
 */
export function conditionalRules(
  condition: () => boolean,
  rules: ValidationRule[]
): ValidationRule[] {
  return rules.map(rule => {
    const originalValidator = rule.validator
    if (!originalValidator) {
      return rule
    }
    return {
      ...rule,
      validator: (r: any, value: any, callback: any, source: any, options: any) => {
        if (condition()) {
          originalValidator(r, value, callback, source, options)
        } else {
          callback()
        }
      }
    }
  })
}

/**
 * 异步验证器
 */
export function asyncValidator(
  validate: (value: any) => Promise<boolean | string>,
  trigger: 'blur' | 'change' = 'blur'
): ValidationRule {
  return {
    validator: (rule, value, callback) => {
      validate(value)
        .then(result => {
          if (result === true) {
            callback()
          } else {
            callback(new Error(typeof result === 'string' ? result : '验证失败'))
          }
        })
        .catch(() => {
          callback(new Error('验证异常'))
        })
    },
    trigger
  }
}

/**
 * 表单字段预设规则组合
 */
export const fieldRules = {
  // 服务名称
  serviceName: [
    rules.required('请输入服务名称'),
    rules.minLength(2),
    rules.maxLength(50),
    rules.noSpecialChars()
  ],

  // 服务地址
  serviceUrl: [
    rules.required('请输入服务地址'),
    rules.url()
  ],

  // API Key
  apiKey: [
    rules.required('请输入API Key'),
    rules.apiKey()
  ],

  // 端口
  port: [
    rules.required('请输入端口号'),
    rules.port()
  ],

  // 超时时间
  timeout: [
    rules.required('请输入超时时间'),
    rules.positiveInteger(),
    rules.valueRange(1, 60000, '超时时间应在1-60000毫秒之间')
  ],

  // 权重
  weight: [
    rules.required('请输入权重'),
    rules.positiveInteger(),
    rules.valueRange(1, 100, '权重应在1-100之间')
  ],

  // 描述
  description: [
    rules.maxLength(500)
  ],

  // 用户名
  username: [
    rules.required('请输入用户名'),
    rules.username()
  ],

  // 密码
  password: [
    rules.required('请输入密码'),
    rules.password()
  ],

  // 邮箱
  email: [
    rules.required('请输入邮箱'),
    rules.email()
  ],

  // 手机号
  phone: [
    rules.required('请输入手机号'),
    rules.phone()
  ]
}
