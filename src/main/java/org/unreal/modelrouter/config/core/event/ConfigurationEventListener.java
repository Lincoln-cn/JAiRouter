package org.unreal.modelrouter.config.core.event;

/**
 * 配置事件监听器接口
 */
public interface ConfigurationEventListener {

    /**
     * 处理配置变更事件
     *
     * @param event 配置变更事件
     */
    void onConfigurationChanged(ConfigurationChangedEvent event);
}
