package org.unreal.modelrouter.dto;

/**
 * 模型请求接口
 * 所有模型服务请求 DTO 应实现此接口
 */
public interface ModelRequest {

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    String model();
}
