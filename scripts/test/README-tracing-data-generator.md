# 追踪数据生成测试脚本

这些脚本用于生成追踪数据，测试追踪概览页面的数据显示功能。

## 快速开始

1. **测试API Key是否有效**:
   ```bash
   ./test-api-key.sh
   ```

2. **生成追踪数据**:
   ```bash
   # Bash (Linux/macOS)
   ./generate-tracing-data.sh
   
   # PowerShell (Windows)
   .\generate-tracing-data.ps1
   
   # Python (跨平台)
   python generate-tracing-data.py
   ```

3. **查看结果**: 访问 `http://localhost:8080` 查看追踪概览页面

## 脚本列表

- `generate-tracing-data.sh` - Bash脚本 (Linux/macOS)
- `generate-tracing-data.ps1` - PowerShell脚本 (Windows)
- `generate-tracing-data.py` - Python脚本 (跨平台)
- `test-api-key.sh` - API Key测试脚本 (快速验证)

## 功能特性

- ✅ 生成多种类型的API请求 (聊天、嵌入、模型列表、健康检查)
- ✅ 模拟真实的用户行为模式
- ✅ 随机生成错误请求 (约10%概率)
- ✅ 控制并发数避免服务器过载
- ✅ 使用正确的API Key认证方式 (`X-API-Key` 头部)
- ✅ 验证生成的追踪数据
- ✅ 彩色输出和进度显示

## 使用方法

### Bash脚本 (Linux/macOS)

```bash
# 使用默认参数 (localhost:8080, 50个请求)
./generate-tracing-data.sh

# 指定服务器和请求数量
./generate-tracing-data.sh http://localhost:8080 100

# 向远程服务器发送请求
./generate-tracing-data.sh https://your-server.com 200

# 显示帮助信息
./generate-tracing-data.sh --help
```

### PowerShell脚本 (Windows)

```powershell
# 使用默认参数
.\generate-tracing-data.ps1

# 指定参数
.\generate-tracing-data.ps1 -BaseUrl "http://localhost:8080" -Count 100

# 设置并发数
.\generate-tracing-data.ps1 -BaseUrl "http://localhost:8080" -Count 200 -ConcurrentRequests 10

# 显示帮助信息
.\generate-tracing-data.ps1 -Help
```

### Python脚本 (跨平台)

```bash
# 安装依赖
pip install requests

# 使用默认参数
python generate-tracing-data.py

# 指定参数
python generate-tracing-data.py http://localhost:8080 100

# 设置并发数
python generate-tracing-data.py --concurrent 10 http://localhost:8080 200

# 显示帮助信息
python generate-tracing-data.py --help-usage
```

## 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| base_url | http://localhost:8080 | 服务器地址 |
| count | 50 | 生成请求数量 |
| concurrent | 5 | 并发请求数 |

## 生成的请求类型

脚本会随机生成以下类型的API请求：

### 1. 聊天完成请求 (`/v1/chat/completions`)
```json
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "user",
      "content": "Hello, how are you today?"
    }
  ],
  "max_tokens": 100,
  "temperature": 0.7
}
```

### 2. 嵌入请求 (`/v1/embeddings`)
```json
{
  "model": "text-embedding-ada-002",
  "input": "This is a sample text for embedding"
}
```

### 3. 模型列表请求 (`/v1/models`)
- GET请求，无请求体

### 4. 健康检查请求 (`/actuator/health`)
- GET请求，用于测试系统监控端点

## 认证方式

所有请求都使用API Key认证方式：

```
X-API-Key: dev-admin-12345-abcde-67890-fghij
```

脚本使用开发环境的管理员API Key进行认证。这个API Key在 `config-dev/security.api-keys.json` 文件中定义。

### 自定义API Key

如果需要使用不同的API Key，可以修改脚本中的以下部分：

**Bash脚本**: 修改 `generate-tracing-data.sh` 中的 `-H "X-API-Key: ..."` 行
**PowerShell脚本**: 修改 `generate-tracing-data.ps1` 中的 `"X-API-Key" = "..."` 行  
**Python脚本**: 修改 `generate-tracing-data.py` 中的 `"X-API-Key": "..."` 行

## 错误模拟

脚本会随机模拟错误情况（约10%概率），通过添加特殊头部：

```
X-Simulate-Error: true
```

这有助于测试错误追踪和统计功能。

## 输出示例

```
========================================
        追踪数据生成测试脚本
========================================

[INFO] 检查服务器连接: http://localhost:8080
[SUCCESS] 服务器连接正常
[INFO] 开始生成追踪数据...
[INFO] 目标服务器: http://localhost:8080
[INFO] 请求数量: 50
[INFO] 并发数: 5
✓ Request 1: /v1/chat/completions - 200 (245ms)
✓ Request 2: /v1/embeddings - 200 (189ms)
⚠ Request 3: /v1/models - 404 (56ms) [Client Error]
✓ Request 4: /actuator/health - 200 (12ms)
[INFO] 已发送 10/50 个请求...
...
[SUCCESS] 追踪数据生成完成！
[INFO] 验证追踪数据...
[INFO] 追踪统计结果:
[INFO]   总追踪数: 50
[INFO]   错误追踪数: 5
[INFO]   平均耗时: 156ms
[SUCCESS] 追踪数据验证成功！
[INFO] 服务统计: 发现 1 个服务
[INFO] 服务列表:
[INFO]   - jairouter: 50 traces, 5 errors, 156ms avg
[SUCCESS] 测试完成！
```

## 验证结果

脚本执行完成后会自动验证生成的追踪数据：

1. **追踪统计验证** - 调用 `/api/tracing/query/statistics` 接口
2. **服务统计验证** - 调用 `/api/tracing/query/services` 接口

## 查看结果

测试完成后，可以通过以下方式查看生成的追踪数据：

1. **前端页面**: 访问 `http://localhost:8080` 查看追踪概览页面
2. **API接口**: 
   - 统计数据: `GET /api/tracing/query/statistics`
   - 服务数据: `GET /api/tracing/query/services`
   - 最近追踪: `GET /api/tracing/query/recent`

## 依赖要求

### Bash脚本
- `curl` - HTTP客户端工具
- `jq` - JSON处理工具 (可选，用于格式化输出)
- `bc` - 计算器工具 (用于随机延迟)

### PowerShell脚本
- PowerShell 5.0 或更高版本
- Windows 或 PowerShell Core (跨平台)

### Python脚本
- Python 3.6 或更高版本
- `requests` 库: `pip install requests`

## 故障排除

### 连接失败
```
[ERROR] 无法连接到服务器: http://localhost:8080
```
**解决方案**: 
- 确保服务器正在运行
- 检查端口是否正确
- 检查防火墙设置

### 认证失败
```
⚠ Request 1: /v1/chat/completions - 401 (12ms) [Client Error]
```
**解决方案**:
- 检查API Key是否正确: `dev-admin-12345-abcde-67890-fghij`
- 确保系统启用了API Key认证
- 验证API Key是否已过期
- 检查API Key是否有相应的权限

**验证API Key**:
```bash
# 使用专用测试脚本
./test-api-key.sh

# 或手动测试
curl -H "X-API-Key: dev-admin-12345-abcde-67890-fghij" \
     http://localhost:8080/actuator/health
```

### 无追踪数据
```
[WARNING] 未检测到追踪数据，可能需要等待数据处理完成
```
**解决方案**:
- 等待几秒钟后重新检查
- 确保追踪系统已启用
- 检查追踪配置是否正确

## 注意事项

1. **API Key配置**: 脚本使用开发环境的API Key，确保目标服务器已加载相应的API Key配置
2. **服务器负载**: 建议不要设置过高的并发数，避免对服务器造成过大压力
3. **网络延迟**: 远程服务器测试时注意网络延迟对结果的影响
4. **数据清理**: 测试数据会存储在内存中，重启服务器后会清空
5. **认证配置**: 确保目标服务器已正确配置API Key认证并启用安全功能

## 扩展功能

如需添加更多测试场景，可以修改脚本中的以下部分：

- 添加新的API端点
- 修改请求参数范围
- 调整错误模拟概率
- 增加新的认证方式测试