package org.unreal.modelrouter.auth.security;

import org.unreal.modelrouter.auth.security.config.properties.*;

/**
 * 测试用安全配置属性类
 * 用于单元测试环境
 */
public class TestSecurityProperties extends SecurityProperties {

    public TestSecurityProperties() {
        super();
        // 测试环境默认启用安全功能
        this.setEnabled(true);
    }
}
