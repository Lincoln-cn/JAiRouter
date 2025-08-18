# GitHub Pages 部署配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->


## 概述

JAiRouter 文档站点通过 GitHub Pages 自动部署，提供在线文档访问服务。

## 配置步骤

### 1. 启用 GitHub Pages

1. 进入 GitHub 仓库设置页面
2. 滚动到 "Pages" 部分
3. 在 "Source" 下选择 "GitHub Actions"
4. 保存设置

### 2. 自定义域名配置（可选）

如果需要使用自定义域名：

1. 在仓库根目录的 `docs/CNAME` 文件中配置域名
2. 在域名提供商处配置 DNS 记录：
   - 类型：CNAME
   - 名称：docs（或您希望的子域名）
   - 值：lincoln-cn.github.io

### 3. 自动部署流程

文档修改后会自动触发部署：

1. 推送到 main 分支
2. GitHub Actions 自动构建文档
3. 部署到 GitHub Pages

## 访问地址

- **默认地址**：https://lincoln-cn.github.io/JAiRouter
- **自定义域名**：https://docs.jairouter.com（需要配置 DNS）

## 部署状态检查

可以在以下位置检查部署状态：

1. GitHub 仓库的 "Actions" 标签页
2. GitHub 仓库的 "Settings" > "Pages" 页面
3. 部署完成后会显示绿色的 ✅ 状态

## 故障排查

### 部署失败

1. 检查 GitHub Actions 日志
2. 确认 mkdocs.yml 配置正确
3. 验证所有文档文件路径正确

### 自定义域名无法访问

1. 检查 DNS 配置是否正确
2. 等待 DNS 传播（可能需要几小时）
3. 确认 CNAME 文件内容正确

### 页面显示异常

1. 清除浏览器缓存
2. 检查文档中的链接是否正确
3. 验证图片和资源文件路径