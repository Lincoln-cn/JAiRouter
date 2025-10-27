# Headers 配置调试指南

## 问题描述
实例管理中的 headers 配置没有被正确持久化到配置文件中。

## 已添加的调试功能

### 后端调试日志
在 `ConfigurationService.java` 中添加了以下调试日志：

1. **添加实例时**：
   - 转换后的实例配置Map
   - 实例配置中的headers字段

2. **更新实例时**：
   - 转换后的实例配置Map
   - 合并前后的headers字段
   - 验证后的headers字段

3. **验证和标准化时**：
   - 输入和输出的配置
   - headers字段的值

### 前端调试日志
在 `InstanceManagement.vue` 中添加了以下调试日志：

1. **请求头变化时**：
   - customHeadersList的值
   - 生成的headers对象
   - form.headers的值

2. **保存实例时**：
   - 构造的实例数据
   - form.headers的值
   - headers条件判断结果

3. **获取实例数据时**：
   - 原始数据和处理后数据

## 测试步骤

### 1. 启用调试日志
确保后端应用的日志级别设置为 DEBUG，以便看到调试信息。

### 2. 前端测试
1. 打开浏览器开发者工具的控制台
2. 进入实例管理页面
3. 点击"添加实例"
4. 填写基本信息
5. 在请求头配置部分：
   - 点击"添加Authorization"按钮
   - 或者点击"添加请求头"手动添加
6. 观察控制台输出的调试信息
7. 保存实例
8. 检查配置文件是否包含headers

### 3. 后端测试
使用提供的测试脚本：
```bash
./test_headers_simple.sh
```

### 4. 检查配置文件
查看生成的配置文件（如 `config/model-router-config@*.json`），确认实例的 headers 字段是否包含正确的值。

## 可能的问题点

### 1. 前端数据格式
- 检查 form.headers 是否正确初始化
- 确认 customHeadersList 和 form.headers 的同步
- 验证发送到后端的数据格式

### 2. 后端转换逻辑
- `convertInstanceToMap` 方法是否正确处理 headers 字段
- `validateAndNormalizeInstanceConfig` 是否保留 headers 字段
- `mergeInstanceConfig` 是否正确合并 headers

### 3. 序列化问题
- Jackson 序列化是否正确处理 Map<String, String> 类型
- 配置保存时是否丢失 headers 字段

## 预期的调试输出

### 前端控制台
```
请求头变化 - customHeadersList: [{key: "Authorization", value: "Bearer test-key"}]
请求头变化 - 生成的headers: {Authorization: "Bearer test-key"}
请求头变化 - form.headers: {Authorization: "Bearer test-key"}
构造的实例数据: {name: "test", baseUrl: "...", headers: {Authorization: "Bearer test-key"}}
```

### 后端日志
```
转换后的实例配置Map: {name=test, baseUrl=..., headers={Authorization=Bearer test-key}}
实例配置中的headers字段: {Authorization=Bearer test-key}
验证和标准化实例配置 - headers字段: {Authorization=Bearer test-key}
```

## 修复方向

如果调试显示某个环节丢失了 headers 数据，可以针对性地修复：

1. **前端问题**：检查数据绑定和同步逻辑
2. **转换问题**：修复 `convertInstanceToMap` 或相关方法
3. **验证问题**：确保验证过程不会移除 headers 字段
4. **序列化问题**：检查 Jackson 配置或添加自定义序列化器

## 清理调试代码
测试完成后，记得移除添加的调试日志，避免生产环境输出过多日志。