// Playground 调试工具
import { getCachedInstances, getCachedModels } from '@/stores/playgroundCache'
import { getInstanceServiceType } from '@/api/models'

export const debugPlaygroundData = async () => {
  console.log('=== Playground 数据调试 ===')
  
  const serviceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imageGenerate', 'imageEdit']
  
  for (const playgroundType of serviceTypes) {
    const actualServiceType = getInstanceServiceType(playgroundType)
    console.log(`\n[${playgroundType}] -> [${actualServiceType}]`)
    
    try {
      const instances = await getCachedInstances(actualServiceType, true)
      const models = await getCachedModels(actualServiceType, true)
      
      console.log(`  实例数量: ${instances.length}`)
      console.log(`  模型数量: ${models.length}`)
      
      if (instances.length > 0) {
        console.log(`  实例列表:`, instances.map(i => ({ id: i.instanceId, name: i.name })))
      }
      
      if (models.length > 0) {
        console.log(`  模型列表:`, models)
      }
    } catch (error) {
      console.error(`  获取数据失败:`, error)
    }
  }
  
  console.log('=== 调试完成 ===')
}

// 在浏览器控制台中可以调用这个函数
if (typeof window !== 'undefined') {
  (window as any).debugPlaygroundData = debugPlaygroundData
}