import type { SavedConfiguration, ServiceType } from '../types/playground'

// 配置存储的键名
const STORAGE_KEY = 'playground-configs'
const STORAGE_VERSION_KEY = 'playground-configs-version'
const CURRENT_VERSION = '1.0.0'

// 配置数据验证接口
interface ConfigValidationResult {
  isValid: boolean
  errors: string[]
  warnings: string[]
}

// 配置迁移接口
interface ConfigMigration {
  fromVersion: string
  toVersion: string
  migrate: (configs: any[]) => SavedConfiguration[]
}

/**
 * 配置持久化服务
 */
export class ConfigService {
  private static instance: ConfigService
  private migrations: ConfigMigration[] = []

  private constructor() {
    this.initializeMigrations()
  }

  public static getInstance(): ConfigService {
    if (!ConfigService.instance) {
      ConfigService.instance = new ConfigService()
    }
    return ConfigService.instance
  }

  /**
   * 初始化配置迁移规则
   */
  private initializeMigrations(): void {
    // 示例迁移：从旧版本到1.0.0
    this.migrations.push({
      fromVersion: '0.0.0',
      toVersion: '1.0.0',
      migrate: (configs: any[]) => {
        return configs.map(config => ({
          ...config,
          id: config.id || this.generateId(),
          createdAt: config.createdAt || new Date().toISOString(),
          description: config.description || ''
        }))
      }
    })
  }

  /**
   * 加载所有保存的配置
   */
  public loadConfigurations(): SavedConfiguration[] {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      const version = localStorage.getItem(STORAGE_VERSION_KEY) || '0.0.0'
      
      if (!stored) {
        return []
      }

      let configs = JSON.parse(stored)
      
      // 执行数据迁移
      if (version !== CURRENT_VERSION) {
        configs = this.migrateConfigurations(configs, version)
        this.saveConfigurations(configs)
        localStorage.setItem(STORAGE_VERSION_KEY, CURRENT_VERSION)
      }

      // 验证配置数据
      const validationResult = this.validateConfigurations(configs)
      if (!validationResult.isValid) {
        console.warn('配置数据验证失败:', validationResult.errors)
        // 过滤掉无效的配置
        configs = configs.filter((config: any) => this.validateSingleConfiguration(config).isValid)
      }

      return configs
    } catch (error) {
      console.error('加载配置失败:', error)
      return []
    }
  }

  /**
   * 保存配置到本地存储
   */
  public saveConfigurations(configurations: SavedConfiguration[]): boolean {
    try {
      // 验证配置数据
      const validationResult = this.validateConfigurations(configurations)
      if (!validationResult.isValid) {
        console.error('配置数据验证失败:', validationResult.errors)
        return false
      }

      // 序列化配置数据
      const serialized = this.serializeConfigurations(configurations)
      
      // 保存到localStorage
      localStorage.setItem(STORAGE_KEY, serialized)
      localStorage.setItem(STORAGE_VERSION_KEY, CURRENT_VERSION)
      
      return true
    } catch (error) {
      console.error('保存配置失败:', error)
      return false
    }
  }

  /**
   * 添加新配置
   */
  public addConfiguration(configuration: Omit<SavedConfiguration, 'id' | 'createdAt'>): SavedConfiguration {
    const newConfig: SavedConfiguration = {
      ...configuration,
      id: this.generateId(),
      createdAt: new Date().toISOString()
    }

    const configs = this.loadConfigurations()
    configs.push(newConfig)
    this.saveConfigurations(configs)

    return newConfig
  }

  /**
   * 更新配置
   */
  public updateConfiguration(configId: string, updates: Partial<SavedConfiguration>): boolean {
    const configs = this.loadConfigurations()
    const index = configs.findIndex(config => config.id === configId)
    
    if (index === -1) {
      return false
    }

    configs[index] = { ...configs[index], ...updates }
    return this.saveConfigurations(configs)
  }

  /**
   * 删除配置
   */
  public deleteConfiguration(configId: string): boolean {
    const configs = this.loadConfigurations()
    const filteredConfigs = configs.filter(config => config.id !== configId)
    
    if (filteredConfigs.length === configs.length) {
      return false // 配置不存在
    }

    return this.saveConfigurations(filteredConfigs)
  }

  /**
   * 根据ID获取配置
   */
  public getConfiguration(configId: string): SavedConfiguration | null {
    const configs = this.loadConfigurations()
    return configs.find(config => config.id === configId) || null
  }

  /**
   * 根据服务类型获取配置列表
   */
  public getConfigurationsByServiceType(serviceType: ServiceType): SavedConfiguration[] {
    const configs = this.loadConfigurations()
    return configs.filter(config => config.serviceType === serviceType)
  }

  /**
   * 导出配置
   */
  public exportConfigurations(configIds?: string[]): string {
    const configs = this.loadConfigurations()
    const exportConfigs = configIds 
      ? configs.filter(config => configIds.includes(config.id))
      : configs

    const exportData = {
      version: CURRENT_VERSION,
      exportDate: new Date().toISOString(),
      configurations: exportConfigs
    }

    return JSON.stringify(exportData, null, 2)
  }

  /**
   * 导入配置
   */
  public importConfigurations(importData: string): { success: number; errors: string[] } {
    try {
      const data = JSON.parse(importData)
      
      if (!data.configurations || !Array.isArray(data.configurations)) {
        return { success: 0, errors: ['无效的导入数据格式'] }
      }

      const existingConfigs = this.loadConfigurations()
      const errors: string[] = []
      let successCount = 0

      data.configurations.forEach((config: any, index: number) => {
        try {
          const validationResult = this.validateSingleConfiguration(config)
          if (!validationResult.isValid) {
            errors.push(`配置 ${index + 1}: ${validationResult.errors.join(', ')}`)
            return
          }

          // 处理重名冲突
          let name = config.name
          let counter = 1
          while (existingConfigs.some(existing => 
            existing.name === name && existing.serviceType === config.serviceType
          )) {
            name = `${config.name} (${counter})`
            counter++
          }

          const newConfig: SavedConfiguration = {
            ...config,
            id: this.generateId(),
            name,
            createdAt: new Date().toISOString()
          }

          existingConfigs.push(newConfig)
          successCount++
        } catch (error) {
          errors.push(`配置 ${index + 1}: ${(error as Error).message}`)
        }
      })

      if (successCount > 0) {
        this.saveConfigurations(existingConfigs)
      }

      return { success: successCount, errors }
    } catch (error) {
      return { success: 0, errors: ['导入数据解析失败: ' + (error as Error).message] }
    }
  }

  /**
   * 清空所有配置
   */
  public clearAllConfigurations(): boolean {
    try {
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem(STORAGE_VERSION_KEY)
      return true
    } catch (error) {
      console.error('清空配置失败:', error)
      return false
    }
  }

  /**
   * 获取存储使用情况
   */
  public getStorageInfo(): { used: number; total: number; percentage: number } {
    try {
      const configs = localStorage.getItem(STORAGE_KEY) || ''
      const used = new Blob([configs]).size
      const total = 5 * 1024 * 1024 // 假设localStorage限制为5MB
      const percentage = (used / total) * 100

      return { used, total, percentage }
    } catch (error) {
      return { used: 0, total: 0, percentage: 0 }
    }
  }

  /**
   * 验证配置数组
   */
  private validateConfigurations(configurations: any[]): ConfigValidationResult {
    const errors: string[] = []
    const warnings: string[] = []

    if (!Array.isArray(configurations)) {
      errors.push('配置数据必须是数组格式')
      return { isValid: false, errors, warnings }
    }

    configurations.forEach((config, index) => {
      const result = this.validateSingleConfiguration(config)
      if (!result.isValid) {
        errors.push(`配置 ${index + 1}: ${result.errors.join(', ')}`)
      }
      warnings.push(...result.warnings.map(w => `配置 ${index + 1}: ${w}`))
    })

    return {
      isValid: errors.length === 0,
      errors,
      warnings
    }
  }

  /**
   * 验证单个配置
   */
  private validateSingleConfiguration(config: any): ConfigValidationResult {
    const errors: string[] = []
    const warnings: string[] = []

    // 必需字段验证
    if (!config.id) errors.push('缺少配置ID')
    if (!config.name) errors.push('缺少配置名称')
    if (!config.serviceType) errors.push('缺少服务类型')
    if (!config.config) errors.push('缺少配置数据')
    if (!config.createdAt) errors.push('缺少创建时间')

    // 数据类型验证
    if (config.id && typeof config.id !== 'string') {
      errors.push('配置ID必须是字符串')
    }
    if (config.name && typeof config.name !== 'string') {
      errors.push('配置名称必须是字符串')
    }
    if (config.serviceType && !this.isValidServiceType(config.serviceType)) {
      errors.push('无效的服务类型')
    }
    if (config.config && typeof config.config !== 'object') {
      errors.push('配置数据必须是对象')
    }

    // 数据长度验证
    if (config.name && config.name.length > 100) {
      warnings.push('配置名称过长，建议不超过100个字符')
    }
    if (config.description && config.description.length > 500) {
      warnings.push('配置描述过长，建议不超过500个字符')
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings
    }
  }

  /**
   * 验证服务类型
   */
  private isValidServiceType(serviceType: string): serviceType is ServiceType {
    const validTypes: ServiceType[] = [
      'chat', 'embedding', 'rerank', 'tts', 'stt', 'imageGenerate', 'imageEdit'
    ]
    return validTypes.includes(serviceType as ServiceType)
  }

  /**
   * 序列化配置数据
   */
  private serializeConfigurations(configurations: SavedConfiguration[]): string {
    // 移除不需要序列化的临时属性
    const cleanConfigs = configurations.map(config => {
      const { editing, editName, ...cleanConfig } = config
      return cleanConfig
    })

    return JSON.stringify(cleanConfigs)
  }

  /**
   * 执行配置迁移
   */
  private migrateConfigurations(configs: any[], fromVersion: string): SavedConfiguration[] {
    let currentConfigs = configs
    let currentVersion = fromVersion

    // 按版本顺序执行迁移
    for (const migration of this.migrations) {
      if (currentVersion === migration.fromVersion) {
        try {
          currentConfigs = migration.migrate(currentConfigs)
          currentVersion = migration.toVersion
          console.log(`配置已从版本 ${migration.fromVersion} 迁移到 ${migration.toVersion}`)
        } catch (error) {
          console.error(`配置迁移失败 (${migration.fromVersion} -> ${migration.toVersion}):`, error)
          break
        }
      }
    }

    return currentConfigs
  }

  /**
   * 生成唯一ID
   */
  private generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2, 9)
  }
}

// 导出单例实例
export const configService = ConfigService.getInstance()