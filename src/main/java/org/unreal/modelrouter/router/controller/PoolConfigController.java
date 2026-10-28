package org.unreal.modelrouter.router.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.pool.PoolDefinitionProperties;
import org.unreal.modelrouter.router.pool.PoolPersistenceService;
import org.unreal.modelrouter.router.pool.PoolSelector;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;
import org.unreal.modelrouter.router.pool.model.PoolMember;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 资源池配置管理控制器
 * 提供资源池的增删改查;YAML 默认池 + 持久化用户池,持久化同 poolName 覆盖
 */
@RestController
@RequestMapping("/api/config/pools")
@CrossOrigin(origins = "*")
@Tag(name = "资源池管理", description = "资源池(池名即虚拟模型名,如 auto-model)的增删改查")
public class PoolConfigController {

    private static final Logger logger = LoggerFactory.getLogger(PoolConfigController.class);
    private static final Set<String> SUPPORTED_STRATEGIES = Set.of(
            "weighted-random", "round-robin", "least-connections", "ip-hash", "consistent-hash");

    private final PoolDefinitionProperties poolProperties;
    private final PoolPersistenceService persistenceService;
    private final PoolSelector poolSelector;
    private final ServiceTypeResolver serviceTypeResolver;

    // 当前生效资源池(YAML 默认 + 持久化用户池,按 poolName 唯一)
    private final CopyOnWriteArrayList<PoolDefinition> activePools = new CopyOnWriteArrayList<>();

    public PoolConfigController(final PoolDefinitionProperties poolProperties,
                                final PoolPersistenceService persistenceService,
                                final PoolSelector poolSelector,
                                final ServiceTypeResolver serviceTypeResolver) {
        this.poolProperties = poolProperties;
        this.persistenceService = persistenceService;
        this.poolSelector = poolSelector;
        this.serviceTypeResolver = serviceTypeResolver;
        reloadActivePools();
    }

    /**
     * 重新加载生效资源池(YAML 默认 + 持久化,持久化同 poolName 覆盖 YAML)并热生效
     */
    private synchronized void reloadActivePools() {
        List<PoolDefinition> yamlPools = poolProperties.getPools() != null
                ? poolProperties.getPools() : new ArrayList<>();
        List<PoolDefinition> persistedPools = persistenceService.loadAll();

        List<PoolDefinition> merged = new ArrayList<>(yamlPools);
        for (PoolDefinition pool : persistedPools) {
            merged.removeIf(p -> p.getPoolName() != null && p.getPoolName().equals(pool.getPoolName()));
            merged.add(pool);
        }

        activePools.clear();
        activePools.addAll(merged);
        poolSelector.reloadPools(merged);
        logger.info("资源池已加载: YAML {} 个 + 持久化 {} 个 = {} 个",
                yamlPools.size(), persistedPools.size(), merged.size());
    }

    /**
     * 获取所有资源池列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有资源池", description = "获取全部资源池(含 YAML 默认与持久化)")
    public ResponseEntity<RouterResponse<List<PoolDefinition>>> getAllPools() {
        List<PoolDefinition> sorted = new ArrayList<>(activePools);
        sorted.sort((a, b) -> {
            String ka = a.getPoolName() != null ? a.getPoolName() : "";
            String kb = b.getPoolName() != null ? b.getPoolName() : "";
            return ka.compareTo(kb);
        });
        return ResponseEntity.ok(RouterResponse.success(sorted));
    }

    /**
     * 获取单条资源池
     */
    @GetMapping("/{poolName}")
    @Operation(summary = "获取单条资源池", description = "按池名获取资源池详情")
    public ResponseEntity<RouterResponse<PoolDefinition>> getPool(@PathVariable final String poolName) {
        return ResponseEntity.ok(RouterResponse.success(findPool(poolName)));
    }

    /**
     * 创建资源池
     */
    @PostMapping
    @Operation(summary = "创建资源池", description = "创建新资源池并热生效")
    public ResponseEntity<RouterResponse<PoolDefinition>> createPool(@RequestBody final PoolDefinition pool) {
        validatePool(pool);
        if (activePools.stream().anyMatch(p -> p.getPoolName() != null
                && p.getPoolName().equals(pool.getPoolName()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pool '" + pool.getPoolName() + "' already exists");
        }
        persistenceService.save(pool);
        reloadActivePools();
        return ResponseEntity.status(HttpStatus.CREATED).body(RouterResponse.success(pool));
    }

    /**
     * 更新资源池
     */
    @PutMapping("/{poolName}")
    @Operation(summary = "更新资源池", description = "按池名更新资源池并热生效")
    public ResponseEntity<RouterResponse<PoolDefinition>> updatePool(@PathVariable final String poolName,
                                                                     @RequestBody final PoolDefinition pool) {
        PoolDefinition existing = findPool(poolName);
        validatePool(pool);
        pool.setPoolName(existing.getPoolName());
        persistenceService.save(pool);
        reloadActivePools();
        return ResponseEntity.ok(RouterResponse.success(pool));
    }

    /**
     * 删除资源池
     *
     * <p>只删除持久化层的资源池。YAML 里定义的资源池是只读配置源，删除请求返回 409 而不是
     * 假装成功（此前 {@code remove} 静默 no-op 后 reload 又把 YAML 池并回来，接口却回 200）。</p>
     *
     * <p>删除持久化池本身仍是 200：它确实删掉了，只是同池名的 YAML 定义会随之重新生效，
     * 这一点通过响应体的 {@code yamlPoolActive} 告知调用方。</p>
     */
    @DeleteMapping("/{poolName}")
    @Operation(summary = "删除资源池",
            description = "按池名删除持久化资源池并热生效;YAML 定义的池只读,删除返回 409;"
                    + "若同池名的 YAML 池因此重新生效,响应体 yamlPoolActive=true")
    public ResponseEntity<RouterResponse<Map<String, Object>>> deletePool(@PathVariable final String poolName) {
        findPool(poolName);
        boolean yamlDefined = isYamlPool(poolName);
        boolean removed = persistenceService.remove(poolName);
        if (!removed) {
            if (yamlDefined) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Pool '" + poolName + "' is defined in YAML and is read-only through this API; "
                                + "edit the pools YAML file, or create a persisted pool with the same name to override it");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pool '" + poolName + "' is not present in the persisted pool store; "
                            + "refusing to report a delete that would not take effect");
        }
        reloadActivePools();
        boolean yamlPoolActive = isYamlPool(poolName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poolName", poolName);
        result.put("deleted", true);
        result.put("yamlPoolActive", yamlPoolActive);
        if (yamlPoolActive) {
            result.put("message", "持久化资源池已删除,但 YAML 中同池名的只读池随即重新生效;"
                    + "如需彻底停用,请在 YAML 中删除该池或改为写同池名的持久化覆盖项");
            logger.info("删除持久化资源池 {} 后 YAML 同池名资源池重新生效", poolName);
        }
        return ResponseEntity.ok(RouterResponse.success(result));
    }

    private boolean isYamlPool(final String poolName) {
        List<PoolDefinition> yamlPools = poolProperties.getPools();
        return yamlPools != null && yamlPools.stream()
                .anyMatch(p -> p.getPoolName() != null && p.getPoolName().equals(poolName));
    }

    private PoolDefinition findPool(final String poolName) {
        return activePools.stream()
                .filter(p -> p.getPoolName() != null && p.getPoolName().equals(poolName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool not found: " + poolName));
    }

    private void validatePool(final PoolDefinition pool) {
        if (pool == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pool body must not be null");
        }
        if (pool.getPoolName() == null || pool.getPoolName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pool name (virtual model name) must not be blank");
        }
        if (pool.getServiceType() == null || pool.getServiceType().isBlank()
                || serviceTypeResolver.parseServiceType(pool.getServiceType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid serviceType: " + pool.getServiceType());
        }
        if (pool.getStrategy() != null && !SUPPORTED_STRATEGIES.contains(pool.getStrategy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported strategy: " + pool.getStrategy()
                            + " (supported: " + SUPPORTED_STRATEGIES + ")");
        }
        if (pool.getMembers() != null) {
            for (PoolMember member : pool.getMembers()) {
                if (member.getInstanceId() == null || member.getInstanceId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Pool member instanceId must not be blank");
                }
            }
        }
    }
}
