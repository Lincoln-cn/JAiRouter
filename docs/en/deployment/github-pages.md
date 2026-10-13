# GitHub Pages Deployment Configuration

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



## Overview

JAiRouter documentation site is automatically deployed through GitHub Pages, providing online documentation access service.

## Configuration Steps

### 1. Enable GitHub Pages

1. Go to GitHub repository settings page
2. Scroll to "Pages" section
3. Under "Source", select "GitHub Actions"
4. Save settings

### 2. Custom Domain Configuration (Optional)

If you need to use a custom domain:

1. Set the domain in the `docs/CNAME` file (this repository already ships one)
2. Configure DNS records at your domain provider:
   - Type: CNAME
   - Name: docs (or your desired subdomain)
   - Value: lincoln-cn.github.io

The canonical URL is `https://jairouter.com/` — that is the `site_url` in `mkdocs.yml`, so links and the
sitemap are generated against it.

### 3. Automatic Deployment Process

Documentation is deployed by the `docs.yml` workflow, which runs on pushes to **`master`** (the default
branch of this repository):

1. Push to `master`
2. GitHub Actions builds the site with MkDocs
3. The built site is published to GitHub Pages

## Access URLs

- **Default URL**: https://lincoln-cn.github.io/JAiRouter
- **Custom Domain**: https://jairouter.com (requires DNS configuration)

## Deployment Status Check

You can check deployment status at:

1. "Actions" tab in GitHub repository
2. "Settings" > "Pages" page in GitHub repository
3. Green ✅ status will be displayed after successful deployment

## Troubleshooting

### Deployment Failure

1. Check GitHub Actions logs
2. Confirm mkdocs.yml configuration is correct
3. Verify all documentation file paths are correct

### Custom Domain Inaccessible

1. Check if DNS configuration is correct
2. Wait for DNS propagation (may take several hours)
3. Confirm CNAME file content is correct

### Page Display Issues

1. Clear browser cache
2. Check if links in documentation are correct
3. Verify image and resource file paths