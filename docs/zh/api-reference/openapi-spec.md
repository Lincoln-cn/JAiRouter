# OpenAPI 规范文档

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 使用 SpringDoc OpenAPI 3 自动生成 API 文档，提供交互式的 API 探索和测试体验。

## 访问方式

### Swagger UI 界面
当 JAiRouter 服务运行时，可以通过以下地址访问交互式 API 文档：

```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON 规范
获取完整的 OpenAPI 3.0 JSON 规范文档：

```
http://localhost:8080/v3/api-docs
```

### OpenAPI YAML 规范
获取 YAML 格式的 OpenAPI 规范文档：

```
http://localhost:8080/v3/api-docs.yaml
```

## 配置说明

JAiRouter 的 OpenAPI 配置位于 `application-dev.yml` 文件中：

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html          # Swagger UI 访问路径
    tags-sorter: alpha              # 标签按字母排序
    operations-sorter: method       # 操作按 HTTP 方法排序
  api-docs:
    path: /v3/api-docs             # OpenAPI JSON 文档路径
  packages-to-scan: org.unreal.modelrouter.controller  # 扫描的包路径
  show-actuator: true             # 显示 Actuator 端点
  cache:
    disabled: true                 # 开发环境禁用缓存
```

## API 分组

JAiRouter 的 API 按功能分为以下几个主要分组：

### 1. 统一模型接口 (Universal API)
- **标签**: `统一模型接口`
- **描述**: 提供兼容 OpenAI 格式的统一模型服务接口
- **路径前缀**: `/v1`
- **包含接口**:
  - 聊天完成 (`/v1/chat/completions`)
  - 文本嵌入 (`/v1/embeddings`)
  - 重排序 (`/v1/rerank`)
  - 文本转语音 (`/v1/audio/speech`)
  - 语音转文本 (`/v1/audio/transcriptions`)
  - 图像生成 (`/v1/images/generations`)
  - 图像编辑 (`/v1/images/edits`)

### 2. 服务类型管理
- **标签**: `服务类型管理`
- **描述**: 提供服务类型的增删改查及相关配置管理接口
- **路径前缀**: `/api/config/type`

### 3. 服务实例管理
- **标签**: `服务实例管理`
- **描述**: 提供服务实例的增删改查相关接口
- **路径前缀**: `/api/config/instance`

### 4. 监控管理
- **标签**: `监控管理`
- **描述**: 提供监控配置的查询和动态更新功能
- **路径前缀**: `/api/monitoring`

### 5. 模型信息接口
- **标签**: `模型信息接口`
- **描述**: 提供模型信息查询相关接口
- **路径前缀**: `/api/models`

### 6. 配置版本管理
- **标签**: `配置版本管理`
- **描述**: 提供配置版本的查询、回滚和删除等管理接口
- **路径前缀**: `/api/config/version`

### 7. 指标注册管理
- **标签**: `Metric Registration`
- **描述**: 动态指标注册和管理 API
- **路径前缀**: `/api/monitoring/metrics`

## 使用 Swagger UI

### 1. 接口探索
- 访问 `http://localhost:8080/swagger-ui.html`
- 浏览不同的 API 分组和接口
- 查看详细的请求参数和响应格式

### 2. 在线测试
- 点击任意接口的 "Try it out" 按钮
- 填写必需的参数
- 点击 "Execute" 执行请求
- 查看实际的响应结果

### 3. 认证配置
对于需要认证的接口，可以在 Swagger UI 中配置认证信息：

1. 点击页面右上角的 "Authorize" 按钮
2. 在 "Authorization" 字段中输入 Bearer Token：
   ```
   Bearer your-api-key
   ```
3. 点击 "Authorize" 确认

### 4. 模型和示例
Swagger UI 会自动显示：
- 请求和响应的数据模型
- 示例 JSON 数据
- 参数的类型和约束
- 错误响应格式

## 自定义 OpenAPI 配置

### 1. 全局配置
可以通过 Java 配置类自定义 OpenAPI 信息：

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JAiRouter API")
                        .version("1.0.0")
                        .description("AI 模型服务路由和负载均衡网关 API 文档")
                        .contact(new Contact()
                                .name("JAiRouter Team")
                                .email("support@jairouter.com")
                                .url("https://github.com/Lincoln-cn/JAiRouter"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080").description("开发环境"),
                        new Server().url("https://api.jairouter.com").description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
```

### 2. 接口级别注解
使用 OpenAPI 注解增强接口文档：

```java
@Operation(
    summary = "聊天完成接口",
    description = "处理聊天完成请求，兼容OpenAI格式",
    tags = {"统一模型接口"}
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "请求处理成功",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ChatDTO.Response.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "请求参数错误",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class)
        )
    )
})
@SecurityRequirement(name = "bearerAuth")
public Mono<ResponseEntity<?>> chatCompletions(
    @Parameter(description = "认证令牌", required = false)
    @RequestHeader(value = "Authorization", required = false) String authorization,
    
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "聊天请求参数",
        required = true,
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ChatDTO.Request.class)
        )
    )
    @RequestBody ChatDTO.Request request
) {
    // 实现逻辑
}
```

## 导出和集成

### 1. 导出 OpenAPI 规范
可以通过以下方式导出 OpenAPI 规范文件：

```bash
# 导出 JSON 格式
curl http://localhost:8080/v3/api-docs > jairouter-openapi.json

# 导出 YAML 格式
curl http://localhost:8080/v3/api-docs.yaml > jairouter-openapi.yaml
```

### 2. 集成到文档站点
将 OpenAPI 规范集成到 MkDocs 文档站点：

#### 安装插件
```bash
pip install mkdocs-swagger-ui-tag
```

#### 配置 mkdocs.yml
```yaml
plugins:
  - swagger-ui-tag

markdown_extensions:
  - swagger-ui-tag
```

#### 在文档中嵌入
```markdown
# API 文档

{% swagger_ui_tag url="http://localhost:8080/v3/api-docs" %}
```

### 3. 生成客户端 SDK
使用 OpenAPI Generator 生成各种语言的客户端 SDK：

```bash
# 安装 OpenAPI Generator
npm install @openapitools/openapi-generator-cli -g

# 生成 Python 客户端
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g python \
  -o ./clients/python \
  --package-name jairouter_client

# 生成 JavaScript 客户端
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g javascript \
  -o ./clients/javascript

# 生成 Java 客户端
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./clients/java \
  --package-name com.jairouter.client
```

## 最佳实践

### 1. 文档质量
- 为所有公开接口添加详细的 `@Operation` 注解
- 使用 `@Parameter` 描述所有参数
- 提供完整的 `@ApiResponse` 定义
- 添加有意义的示例数据

### 2. 安全性
- 为需要认证的接口添加 `@SecurityRequirement`
- 在全局配置中定义安全方案
- 不要在文档中暴露敏感信息

### 3. 版本管理
- 使用语义化版本号
- 在重大变更时更新版本
- 保持向后兼容性

### 4. 性能优化
- 在生产环境启用缓存
- 限制文档访问权限
- 考虑使用 CDN 加速

## 故障排查

### 1. 常见问题

**问题**: Swagger UI 无法加载
**解决**: 检查 `springdoc.swagger-ui.path` 配置是否正确

**问题**: API 接口未显示在文档中
**解决**: 确认控制器类在 `packages-to-scan` 指定的包路径下

**问题**: 参数或响应模型显示不正确
**解决**: 检查 DTO 类的注解和字段定义

### 2. 调试技巧
- 启用 SpringDoc 调试日志：
  ```yaml
  logging:
    level:
      org.springdoc: DEBUG
  ```
- 检查 `/v3/api-docs` 端点的原始 JSON 输出
- 使用浏览器开发者工具检查网络请求

## 相关链接

- [SpringDoc OpenAPI 官方文档](https://springdoc.org/)
- [OpenAPI 3.0 规范](https://swagger.io/specification/)
- [Swagger UI 文档](https://swagger.io/tools/swagger-ui/)
- [OpenAPI Generator](https://openapi-generator.tech/)