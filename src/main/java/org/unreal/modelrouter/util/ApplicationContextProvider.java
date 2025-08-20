package org.unreal.modelrouter.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring应用上下文提供者
 * 
 * 提供静态方法访问Spring应用上下文，用于在非Spring管理的类中获取Bean
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }
    
    /**
     * 获取Spring应用上下文
     * 
     * @return Spring应用上下文
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    /**
     * 根据类型获取Bean
     * 
     * @param clazz Bean类型
     * @param <T> Bean类型
     * @return Bean实例
     * @throws BeansException 如果Bean不存在或获取失败
     */
    public static <T> T getBean(Class<T> clazz) throws BeansException {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext has not been set");
        }
        return applicationContext.getBean(clazz);
    }
    
    /**
     * 根据名称获取Bean
     * 
     * @param name Bean名称
     * @return Bean实例
     * @throws BeansException 如果Bean不存在或获取失败
     */
    public static Object getBean(String name) throws BeansException {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext has not been set");
        }
        return applicationContext.getBean(name);
    }
    
    /**
     * 根据名称和类型获取Bean
     * 
     * @param name Bean名称
     * @param clazz Bean类型
     * @param <T> Bean类型
     * @return Bean实例
     * @throws BeansException 如果Bean不存在或获取失败
     */
    public static <T> T getBean(String name, Class<T> clazz) throws BeansException {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext has not been set");
        }
        return applicationContext.getBean(name, clazz);
    }
    
    /**
     * 检查是否包含指定名称的Bean
     * 
     * @param name Bean名称
     * @return 如果包含返回true
     */
    public static boolean containsBean(String name) {
        if (applicationContext == null) {
            return false;
        }
        return applicationContext.containsBean(name);
    }
    
    /**
     * 检查ApplicationContext是否已初始化
     * 
     * @return 如果已初始化返回true
     */
    public static boolean isInitialized() {
        return applicationContext != null;
    }
}