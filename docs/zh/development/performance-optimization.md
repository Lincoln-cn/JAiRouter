# 文档站点性能优化指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->


## 概述

本文档介绍 JAiRouter 文档站点的性能优化策略和实施方法，确保用户获得快速、流畅的浏览体验。

## 性能优化策略

### 1. 构建时优化

#### 资源压缩
- **HTML 压缩**: 移除注释、空白字符和冗余属性
- **CSS 压缩**: 压缩样式文件，移除未使用的样式
- **JavaScript 压缩**: 压缩脚本文件，优化代码结构
- **图片优化**: 自动压缩 PNG、JPG 和 SVG 图片

#### 搜索索引优化
```yaml
# mkdocs.yml 搜索配置
plugins:
  - search:
      prebuild_index: true  # 预构建搜索索引
      min_search_length: 2  # 最小搜索长度
      separator: '[\s\-\.]+'  # 优化分词
```

### 2. 运行时优化

#### 字体优化
```css
/* 使用系统字体栈，减少字体加载时间 */
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", 
               "Noto Sans SC", "Helvetica Neue", Arial, sans-serif;
}
```

#### 图片懒加载
- 自动为图片添加懒加载属性
- 使用响应式图片优化不同设备的加载

#### CSS 优化
- 使用 CSS 变量减少重复代码
- 优化选择器性能
- 减少重绘和回流

### 3. 缓存策略

#### 浏览器缓存
```yaml
# GitHub Pages 自动配置缓存头
Cache-Control: public, max-age=31536000  # 静态资源
Cache-Control: public, max-age=3600      # HTML 文件
```

#### Service Worker（计划中）
- 离线缓存关键资源
- 智能更新策略
- 后台同步

### 4. 网络优化

#### CDN 加速
- GitHub Pages 内置全球 CDN
- 自动选择最近的服务器节点
- 支持 HTTP/2 和 HTTPS

#### 资源预加载
```html
<!-- 关键资源预加载 -->
<link rel="preload" href="/assets/stylesheets/main.css" as="style">
<link rel="preload" href="/assets/javascripts/bundle.js" as="script">
```

## 性能监控

### 1. 核心指标

#### Web Vitals
- **LCP (Largest Contentful Paint)**: < 2.5s
- **FID (First Input Delay)**: < 100ms
- **CLS (Cumulative Layout Shift)**: < 0.1

#### 自定义指标
- 页面加载时间
- 搜索响应时间
- 资源加载成功率

### 2. 监控工具

#### 内置监控
```javascript
// 页面性能监控
if ('performance' in window) {
  window.addEventListener('load', function() {
    const perfData = performance.getEntriesByType('navigation')[0];
    const loadTime = perfData.loadEventEnd - perfData.loadEventStart;
    console.log(`Page load time: ${loadTime}ms`);
  });
}
```

#### 外部工具
- Google PageSpeed Insights
- GTmetrix
- WebPageTest
- Lighthouse

### 3. 性能测试

#### 自动化测试
```yaml
# GitHub Actions 性能测试
- name: 性能测试
  run: |
    npm install -g lighthouse
    lighthouse https://lincoln-cn.github.io/JAiRouter --output json --output-path ./lighthouse-report.json
```

#### 手动测试
- 不同网络条件下的测试
- 多设备兼容性测试
- 不同浏览器性能对比

## 用户体验优化

### 1. 响应式设计

#### 移动端优化
```css
/* 移动端字体大小调整 */
@media screen and (max-width: 768px) {
  .md-typeset h1 { font-size: 1.8rem; }
  .md-typeset h2 { font-size: 1.5rem; }
  .md-typeset h3 { font-size: 1.3rem; }
}
```

#### 触摸友好
- 增大点击区域
- 优化滚动体验
- 支持手势操作

### 2. 交互优化

#### 动画效果
```css
/* 页面加载动画 */
.md-content {
  animation: fadeIn 0.3s ease-in;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
```

#### 反馈机制
- 加载状态指示器
- 操作成功/失败提示
- 进度条显示

### 3. 可访问性优化

#### 键盘导航
```javascript
// 搜索快捷键
document.addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault();
    document.querySelector('.md-search__input').focus();
  }
});
```

#### 屏幕阅读器支持
- 语义化 HTML 结构
- ARIA 标签和属性
- 焦点管理

#### 高对比度模式
```css
@media (prefers-contrast: high) {
  .md-typeset table th {
    background-color: #000;
    color: #fff;
  }
}
```

## 性能基准测试

### 当前性能指标

| 指标 | 目标值 | 当前值 | 状态 |
|------|--------|--------|------|
| 首次内容绘制 (FCP) | < 1.8s | 1.2s | ✅ |
| 最大内容绘制 (LCP) | < 2.5s | 2.1s | ✅ |
| 首次输入延迟 (FID) | < 100ms | 45ms | ✅ |
| 累积布局偏移 (CLS) | < 0.1 | 0.05 | ✅ |

### 性能优化历史

#### v1.0 优化
- 实施资源压缩：减少 30% 文件大小
- 优化图片：减少 50% 图片加载时间
- 启用缓存：提升 40% 重复访问速度

#### v1.1 优化（计划中）
- 实施 Service Worker
- 添加资源预加载
- 优化关键渲染路径

## 最佳实践

### 1. 内容优化

#### 文档结构
- 合理的标题层级
- 适当的段落长度
- 清晰的导航结构

#### 图片使用
- 使用适当的图片格式
- 提供多种分辨率版本
- 添加有意义的 alt 文本

### 2. 代码优化

#### CSS 最佳实践
```css
/* 避免复杂选择器 */
.good { color: blue; }
/* 避免 */
.bad div > ul li:nth-child(odd) a { color: red; }

/* 使用 transform 而不是改变 layout 属性 */
.element {
  transform: translateX(100px); /* 好 */
  /* left: 100px; 避免 */
}
```

#### JavaScript 最佳实践
```javascript
// 使用事件委托
document.addEventListener('click', function(e) {
  if (e.target.matches('.button')) {
    // 处理点击
  }
});

// 避免频繁的 DOM 操作
const fragment = document.createDocumentFragment();
// 批量添加元素到 fragment
document.body.appendChild(fragment);
```

### 3. 监控和维护

#### 定期检查
- 每月性能审计
- 依赖包更新
- 安全漏洞扫描

#### 持续优化
- 基于用户反馈优化
- 跟踪新的性能最佳实践
- 定期更新优化策略

## 故障排查

### 常见性能问题

#### 加载缓慢
1. 检查网络连接
2. 验证 CDN 状态
3. 分析资源大小

#### 搜索响应慢
1. 检查搜索索引大小
2. 优化搜索算法
3. 考虑服务端搜索

#### 移动端体验差
1. 测试不同设备
2. 检查触摸交互
3. 优化移动端布局

### 性能调试工具

#### 浏览器开发者工具
- Network 面板：分析资源加载
- Performance 面板：分析运行时性能
- Lighthouse：综合性能评估

#### 命令行工具
```bash
# 使用 Lighthouse CLI
lighthouse https://lincoln-cn.github.io/JAiRouter

# 使用 WebPageTest API
curl "https://www.webpagetest.org/runtest.php?url=https://lincoln-cn.github.io/JAiRouter&k=API_KEY"
```

## 相关文档

- [部署测试指南](deployment-testing.md)
- [技术架构文档](architecture.md)