package org.unreal.modelrouter.config.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 循环依赖配置类
 *
 * @deprecated 此类为临时解决方案，用于解决应用启动时的循环依赖问题。
 *             <p>背景说明：</p>
 *             <ul>
 *               <li>循环依赖是由于某些组件之间的相互引用导致的架构问题</li>
 *               <li>Spring Boot 2.6+ 默认禁止循环依赖，此配置强制启用</li>
 *               <li>正确的解决方案是通过重构代码消除循环依赖</li>
 *             </ul>
 *             <p>重构方向：</p>
 *             <ul>
 *               <li>使用事件驱动模式替代直接依赖</li>
 *               <li>引入中介者模式解耦组件</li>
 *               <li>使用 @Lazy 注解延迟初始化</li>
 *             </ul>
 *             此类将在 v3.0 版本中移除，届时应完成架构重构。
 * @author JAiRouter Team
 * @since 1.0.0 临时引入
 */
@Deprecated(since = "2.5.7", forRemoval = true)
@Configuration
public class CircularReferenceConfig {
    
    /**
     * 配置允许循环依赖
     *
     * @deprecated 此方法为临时解决方案，通过设置系统属性允许循环依赖。
     *             <p>建议重构方案：</p>
     *             <ul>
     *               <li>ConfigurationService 与 InstanceManager 之间的循环依赖</li>
     *               <li>使用 ConfigChangeEvent 替代直接方法调用</li>
     *               <li>引入 ConfigurationMediator 中介者类</li>
     *             </ul>
     *             此方法将在 v3.0 版本中移除。
     * @return 布尔值（实际不影响运行，仅用于触发系统属性设置）
     */
    @Deprecated(since = "2.5.7", forRemoval = true)
    @Bean
    public boolean allowCircularReferences() {
        // 这个Bean不会被使用，只是为了让Spring知道我们需要处理循环依赖
        System.setProperty("spring.main.allow-circular-references", "true");
        return true;
    }
}