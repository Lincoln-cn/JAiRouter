# JAiRouter 文档站点谷歌广告集成说明

## 概述

本文档说明了如何在 JAiRouter 文档站点中集成和管理谷歌广告（Google AdSense）。

## 功能特性

### 1. 智能广告位置
- **导航栏下方**: 广告会自动显示在左侧导航栏的下方
- **响应式设计**: 移动端自动隐藏广告，确保用户体验
- **自适应大小**: 广告会根据容器大小自动调整

### 2. 广告拦截器检测
- 自动检测用户是否启用了广告拦截器
- 如果检测到广告拦截器，不会强制显示广告

### 3. 性能优化
- 延迟加载: 广告在页面主要内容加载完成后再加载
- 性能监控: 监控广告加载时间和性能指标
- 错误处理: 优雅处理广告加载失败的情况

### 4. 用户体验
- 移动端友好: 在小屏幕设备上隐藏广告
- 深色模式支持: 广告样式适配深色主题
- 打印友好: 打印时自动隐藏广告

## 配置文件

### 1. MkDocs 配置 (mkdocs.yml)
```yaml
extra_javascript:
  - javascripts/language-switcher.js
  - javascripts/google-ads.js
  - https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-8229708772289943
```

### 2. CSS 样式 (docs/stylesheets/extra.css)
广告相关样式已添加到现有的 CSS 文件中，包括：
- `.google-ad`: 广告容器样式
- `.google-ad-sidebar`: 侧边栏广告特定样式
- `.google-ad-title`: 广告标题样式
- 响应式媒体查询
- 深色模式适配

### 3. JavaScript 逻辑 (docs/javascripts/google-ads.js)
主要功能包括：
- 广告容器创建和插入
- 广告拦截器检测
- 响应式广告管理
- 性能监控
- 路由变化处理

## 广告客户端 ID

当前使用的广告客户端 ID: `ca-pub-8229708772289943`

如需更改，请修改以下位置：
1. `mkdocs.yml` 中的 AdSense 脚本 URL
2. `docs/javascripts/google-ads.js` 中的 `AD_CONFIG.client`

## 管理功能

### JavaScript API
广告系统提供了全局 API 供管理使用：

```javascript
// 启用广告
window.GoogleAdsConfig.enable();

// 禁用广告
window.GoogleAdsConfig.disable();

// 检查广告状态
console.log(window.GoogleAdsConfig.isEnabled());
```

### 配置选项
在 `google-ads.js` 中的 `AD_CONFIG` 对象可以调整：
- `enabled`: 是否启用广告
- `client`: AdSense 客户端 ID
- `placements`: 广告位置配置

## 最佳实践

### 1. 性能优化
- 广告脚本使用异步加载
- 延迟初始化避免阻塞页面渲染
- 响应式设计减少移动端资源消耗

### 2. 用户体验
- 广告不会干扰主要内容阅读
- 移动端隐藏广告确保良好的移动体验
- 深色模式适配确保视觉一致性

### 3. 合规性
- 广告明确标识为"赞助内容"
- 尊重用户的广告拦截器选择
- 打印时自动隐藏广告

## 故障排除

### 1. 广告不显示
- 检查 AdSense 账户是否激活
- 确认客户端 ID 是否正确
- 检查浏览器控制台是否有错误信息

### 2. 广告位置异常
- 确认导航栏元素是否正确加载
- 检查 CSS 样式是否被其他样式覆盖
- 验证 JavaScript 是否正常执行

### 3. 性能问题
- 检查广告加载时间监控信息
- 确认是否启用了广告拦截器
- 验证网络连接状况

## 维护建议

1. **定期监控**: 检查广告展示和收入情况
2. **性能审查**: 定期查看页面加载性能
3. **用户反馈**: 关注用户对广告的反馈
4. **技术更新**: 跟进 AdSense 和 MkDocs 的技术更新

## 联系信息

如有问题或建议，请通过项目 GitHub 仓库提交 Issue。