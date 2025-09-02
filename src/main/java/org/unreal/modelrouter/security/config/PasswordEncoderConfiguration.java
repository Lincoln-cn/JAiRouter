package org.unreal.modelrouter.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder配置类
 * 专门用于配置密码编码器Bean，避免循环依赖问题
 */
@Configuration
public class PasswordEncoderConfiguration {

    /**
     * 配置密码编码器
     * 使用DelegatingPasswordEncoder支持多种加密算法
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.password.DelegatingPasswordEncoder(
                "bcrypt", 
                java.util.Map.of(
                        "bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                        "noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance()
                )
        );
    }
}