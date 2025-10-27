// 测试模型API的工具函数
import { getModelsByServiceType, getAllModels, getInstanceServiceType } from '@/api/models'

export const testModelAPI = async () => {
  console.log('=== 测试模型API ===')
  
  try {
    // 测试服务类型映射
    console.log('1. 测试服务类型映射:')
    const mappings = [
      { playground: 'chat', instance: getInstanceServiceType('chat') },
      { playground: 'embedding', instance: getInstanceServiceType('embedding') },
      { playground: 'rerank', instance: getInstanceServiceType('rerank') },
      { playground: 'tts', instance: getInstanceServiceType('tts') },
      { playground: 'stt', instance: getInstanceServiceType('stt') },
      { playground: 'imageGenerate', instance: getInstanceServiceType('imageGenerate') },
      { playground: 'imageEdit', instance: getInstanceServiceType('imageEdit') }
    ]
    
    mappings.forEach(({ playground, instance }) => {
      console.log(`  ${playground} -> ${instance}`)
    })
    
    // 测试获取单个服务类型的模型
    console.log('\n2. 测试获取单个服务类型的模型:')
    for (const { playground, instance } of mappings) {
      try {
        const models = await getModelsByServiceType(instance)
        console.log(`  ${playground} (${instance}): ${models.length} 个模型`, models)
      } catch (error) {
        console.log(`  ${playground} (${instance}): 获取失败`, error)
      }
    }
    
    // 测试获取所有模型
    console.log('\n3. 测试获取所有模型:')
    const allModels = await getAllModels()
    console.log('  所有模型:', allModels)
    
    console.log('\n=== 测试完成 ===')
    return allModels
  } catch (error) {
    console.error('测试失败:', error)
    throw error
  }
}