package org.unreal.modelrouter.router.pool;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源池 YAML 配置属性
 * 从 pools.yml 的 model.pools 节加载默认资源池(可为空)
 */
@Configuration
@ConfigurationProperties(prefix = "model.pools")
public class PoolDefinitionProperties {

    private List<PoolDefinition> pools = new ArrayList<>();

    public List<PoolDefinition> getPools() {
        return pools;
    }

    public void setPools(final List<PoolDefinition> pools) {
        this.pools = pools;
    }
}
