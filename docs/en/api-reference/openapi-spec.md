# OpenAPI Specification Document

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


JAiRouter uses SpringDoc OpenAPI 3 to automatically generate API documentation, providing an interactive API exploration and testing experience.

## Access Methods

### Swagger UI Interface
When the JAiRouter service is running, you can access the interactive API documentation at the following address:

```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON Specification
Get the complete OpenAPI 3.0 JSON specification document:

```
http://localhost:8080/v3/api-docs
```

### OpenAPI YAML Specification
Get the YAML format OpenAPI specification document:

```
http://localhost:8080/v3/api-docs.yaml
```

## Configuration Instructions

JAiRouter's OpenAPI configuration is located in the [application-dev.yml](file://D:\IdeaProjects\model-router\target\classes\application-dev.yml) file:

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html          # Swagger UI access path
    tags-sorter: alpha              # Sort tags alphabetically
    operations-sorter: method       # Sort operations by HTTP method
  api-docs:
    path: /v3/api-docs             # OpenAPI JSON document path
  packages-to-scan: org.unreal.modelrouter.controller  # Package path to scan
  show-actuator: true             # Show Actuator endpoints
  cache:
    disabled: true                 # Disable cache in development environment
```

## API Grouping

JAiRouter's APIs are divided into the following main groups by function:

### 1. Universal Model Interface (Universal API)
- **Tag**: `Universal Model Interface`
- **Description**: Provides unified model service interfaces compatible with OpenAI format
- **Path Prefix**: `/v1`
- **Included Interfaces**:
  - Chat Completion (`/v1/chat/completions`)
  - Text Embedding (`/v1/embeddings`)
  - Reranking (`/v1/rerank`)
  - Text-to-Speech (`/v1/audio/speech`)
  - Speech-to-Text (`/v1/audio/transcriptions`)
  - Image Generation (`/v1/images/generations`)
  - Image Editing (`/v1/images/edits`)

### 2. Service Type Management
- **Tag**: `Service Type Management`
- **Description**: Provides CRUD interfaces for service types and related configuration management
- **Path Prefix**: `/api/config/type`

### 3. Service Instance Management
- **Tag**: `Service Instance Management`
- **Description**: Provides CRUD interfaces for service instances
- **Path Prefix**: `/api/config/instance`

### 4. Monitoring Management
- **Tag**: `Monitoring Management`
- **Description**: Provides query and dynamic update functions for monitoring configurations
- **Path Prefix**: `/api/monitoring`

### 5. Model Information Interface
- **Tag**: `Model Information Interface`
- **Description**: Provides model information query interfaces
- **Path Prefix**: `/api/models`

### 6. Configuration Version Management
- **Tag**: `Configuration Version Management`
- **Description**: Provides management interfaces for querying, rolling back, and deleting configuration versions
- **Path Prefix**: `/api/config/version`

### 7. Metric Registration Management
- **Tag**: `Metric Registration`
- **Description**: Dynamic metric registration and management API
- **Path Prefix**: `/api/monitoring/metrics`

## Using Swagger UI

### 1. Interface Exploration
- Visit `http://localhost:8080/swagger-ui.html`
- Browse different API groups and interfaces
- View detailed request parameters and response formats

### 2. Online Testing
- Click the "Try it out" button for any interface
- Fill in the required parameters
- Click "Execute" to run the request
- View the actual response results

### 3. Authentication Configuration
For interfaces requiring authentication, you can configure authentication information in Swagger UI:

1. Click the "Authorize" button in the top right corner of the page
2. Enter the Bearer Token in the "Authorization" field:
   ```
   Bearer your-api-key
   ```
3. Click "Authorize" to confirm

### 4. Models and Examples
Swagger UI automatically displays:
- Request and response data models
- Example JSON data
- Parameter types and constraints
- Error response formats

## Custom OpenAPI Configuration

### 1. Global Configuration
You can customize OpenAPI information through Java configuration classes:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JAiRouter API")
                        .version("1.0.0")
                        .description("AI Model Service Routing and Load Balancing Gateway API Documentation")
                        .contact(new Contact()
                                .name("JAiRouter Team")
                                .email("support@jairouter.com")
                                .url("https://github.com/Lincoln-cn/JAiRouter"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080").description("Development Environment"),
                        new Server().url("https://api.jairouter.com").description("Production Environment")
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

### 2. Interface-Level Annotations
Use OpenAPI annotations to enhance interface documentation:

```java
@Operation(
    summary = "Chat Completion Interface",
    description = "Process chat completion requests, compatible with OpenAI format",
    tags = {"Universal Model Interface"}
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Request processed successfully",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ChatDTO.Response.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Request parameter error",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ErrorResponse.class)
        )
    )
})
@SecurityRequirement(name = "bearerAuth")
public Mono<ResponseEntity<?>> chatCompletions(
    @Parameter(description = "Authentication Token", required = false)
    @RequestHeader(value = "Authorization", required = false) String authorization,
    
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Chat Request Parameters",
        required = true,
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ChatDTO.Request.class)
        )
    )
    @RequestBody ChatDTO.Request request
) {
    // Implementation logic
}
```

## Export and Integration

### 1. Exporting OpenAPI Specification
You can export OpenAPI specification files in the following ways:

```bash
# Export JSON format
curl http://localhost:8080/v3/api-docs > jairouter-openapi.json

# Export YAML format
curl http://localhost:8080/v3/api-docs.yaml > jairouter-openapi.yaml
```

### 2. Integration with Documentation Sites
Integrate OpenAPI specification into MkDocs documentation sites:

#### Install Plugin
```bash
pip install mkdocs-swagger-ui-tag
```

#### Configure mkdocs.yml
```yaml
plugins:
  - swagger-ui-tag

markdown_extensions:
  - swagger-ui-tag
```

#### Embed in Documentation
```markdown
# API Documentation

{% swagger_ui_tag url="http://localhost:8080/v3/api-docs" %}
```

### 3. Generate Client SDKs
Use OpenAPI Generator to generate client SDKs in various languages:

```bash
# Install OpenAPI Generator
npm install @openapitools/openapi-generator-cli -g

# Generate Python client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g python \
  -o ./clients/python \
  --package-name jairouter_client

# Generate JavaScript client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g javascript \
  -o ./clients/javascript

# Generate Java client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./clients/java \
  --package-name com.jairouter.client
```

## Best Practices

### 1. Documentation Quality
- Add detailed `@Operation` annotations for all public interfaces
- Use `@Parameter` to describe all parameters
- Provide complete `@ApiResponse` definitions
- Add meaningful example data

### 2. Security
- Add `@SecurityRequirement` for interfaces requiring authentication
- Define security schemes in global configuration
- Do not expose sensitive information in documentation

### 3. Version Management
- Use semantic versioning
- Update version for major changes
- Maintain backward compatibility

### 4. Performance Optimization
- Enable caching in production environment
- Restrict documentation access permissions
- Consider using CDN acceleration

## Troubleshooting

### 1. Common Issues

**Issue**: Swagger UI fails to load
**Solution**: Check if `springdoc.swagger-ui.path` configuration is correct

**Issue**: API interfaces not showing in documentation
**Solution**: Confirm that controller classes are under the package path specified by `packages-to-scan`

**Issue**: Parameters or response models display incorrectly
**Solution**: Check DTO class annotations and field definitions

### 2. Debugging Tips
- Enable SpringDoc debug logging:
  ```yaml
  logging:
    level:
      org.springdoc: DEBUG
  ```
- Check the raw JSON output of the `/v3/api-docs` endpoint
- Use browser developer tools to inspect network requests

## Related Links

- [SpringDoc OpenAPI Official Documentation](https://springdoc.org/)
- [OpenAPI 3.0 Specification](https://swagger.io/specification/)
- [Swagger UI Documentation](https://swagger.io/tools/swagger-ui/)
- [OpenAPI Generator](https://openapi-generator.tech/)