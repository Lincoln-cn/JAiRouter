以下是将文件内容总结成的 Markdown 文档：

# Sitemaps 协议

## 简介
本文档描述了 Sitemap 协议的 XML 架构，旨在帮助搜索引擎更好地抓取网站内容。

## Sitemap XML 格式

### 基本要求
- 文件必须以 UTF-8 编码保存。
- 所有数据值必须进行实体转义。
- 必须以 `<urlset>` 标签开头和结尾，并在其中指定命名空间。
- 每个 URL 必须包含在 `<url>` 标签中，并且每个 `<url>` 标签必须包含一个 `<loc>` 子标签。

### 示例
```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
   <url>
      <loc>http://www.example.com/</loc>
      <lastmod>2005-01-01</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.8</priority>
   </url>
</urlset>
```

## XML 标签定义

### 必选标签
- `<urlset>`：封装整个文件并引用当前协议标准。
- `<url>`：每个 URL 的父标签。
- `<loc>`：页面的 URL，必须以协议开头，长度不超过 2048 个字符。

### 可选标签
- `<lastmod>`：页面最后修改的日期，格式为 W3C Datetime（如 YYYY-MM-DD）。
- `<changefreq>`：页面更改的频率，有效值包括 always、hourly、daily、weekly、monthly、yearly、never。
- `<priority>`：该 URL 相对于站点内其他 URL 的优先级，范围为 0.0 到 1.0，默认值为 0.5。

## 实体转义
- 所有数据值（包括 URL）必须使用实体转义代码，例如：
    - `&` 转义为 `&amp;`
    - `'` 转义为 `&apos;`
    - `"` 转义为 `&quot;`
    - `>` 转义为 `&gt;`
    - `<` 转义为 `&lt;`

## Sitemap 索引文件
- 如果有多个 Sitemap 文件，每个文件最多包含 50,000 个 URL，文件大小不超过 50MB。
- 可以使用 gzip 压缩 Sitemap 文件，但解压后大小不得超过 50MB。
- Sitemap 索引文件必须以 `<sitemapindex>` 标签开头和结尾，并包含每个 Sitemap 的 `<sitemap>` 标签。

### 示例
```xml
<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
   <sitemap>
      <loc>http://www.example.com/sitemap1.xml.gz</loc>
      <lastmod>2004-10-01T18:23:17+00:00</lastmod>
   </sitemap>
   <sitemap>
      <loc>http://www.example.com/sitemap2.xml.gz</loc>
      <lastmod>2005-01-01</lastmod>
   </sitemap>
</sitemapindex>
```

## 其他 Sitemap 格式
- **RSS/Atom**：可以提供 RSS 2.0 或 Atom 0.3/1.0 格式的订阅源。
- **文本文件**：可以提供一个简单的文本文件，每行一个 URL，最多 50,000 个 URL，文件大小不超过 50MB。

## Sitemap 文件位置
- Sitemap 文件的位置决定了可以包含的 URL 范围。
- Sitemap 文件必须位于与 URL 同一主机上，并且所有 URL 必须使用相同的协议。

## 验证 Sitemap
- 可以使用 XML Schema 验证 Sitemap 文件的结构。
- 验证工具可在 [W3C](http://www.w3.org/XML/Schema#Tools) 和 [XML.com](http://www.xml.com/pub/a/2000/12/13/schematools.html) 找到。

## 扩展 Sitemap 协议
- 可以通过添加自定义命名空间来扩展 Sitemap 协议。

## 通知搜索引擎
- 可以通过搜索引擎的提交界面、robots.txt 文件或 HTTP 请求来通知搜索引擎 Sitemap 的位置。

## 排除内容
- 使用 robots.txt 文件或 robots 元标签来告诉搜索引擎不想被索引的内容。

## 更新日期
- 最后更新日期：2016 年 11 月 21 日