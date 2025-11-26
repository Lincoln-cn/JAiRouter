package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.controller.response.RouterResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 适配器控制器 - 处理适配器相关信息查询接口
 */
@RestController
@RequestMapping("/api/config/adapter")
@CrossOrigin(origins = "*")
@Tag(name = "适配器管理", description = "提供适配器相关信息查询接口")
public class AdapterController {

    private static final Logger logger = LoggerFactory.getLogger(AdapterController.class);

    private final AdapterRegistry adapterRegistry;

    public AdapterController(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 获取所有支持的适配器
     */
    @GetMapping
    @Operation(summary = "获取所有适配器", description = "获取系统中所有支持的适配器信息")
    @ApiResponse(responseCode = "200", description = "成功获取适配器列表",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RouterResponse.class)))
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    public ResponseEntity<RouterResponse<List<Object>>> getAdapters() {
        try {
            Map<String, org.unreal.modelrouter.adapter.ServiceCapability> adapters = adapterRegistry.getAllAdapters();
            
            List<Object> adapterList = new ArrayList<>();
            for (Map.Entry<String, org.unreal.modelrouter.adapter.ServiceCapability> entry : adapters.entrySet()) {
                String adapterName = entry.getKey();
                org.unreal.modelrouter.adapter.ServiceCapability adapter = entry.getValue();
                
                // 创建适配器信息对象
                java.util.Map<String, Object> adapterInfo = new java.util.HashMap<>();
                adapterInfo.put("name", adapterName);
                adapterInfo.put("className", adapter.getClass().getSimpleName());
                adapterInfo.put("packageName", adapter.getClass().getPackageName());
                
                adapterList.add(adapterInfo);
            }
            
            return ResponseEntity.ok(RouterResponse.success(adapterList, "获取适配器列表成功"));
        } catch (Exception e) {
            logger.error("获取适配器列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(RouterResponse.error("获取适配器列表失败: " + e.getMessage()));
        }
    }
}