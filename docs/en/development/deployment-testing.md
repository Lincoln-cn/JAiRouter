# Deployment Testing Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: f47f2607  
> **作者**: Lincoln
<!-- /版本信息 -->



## Overview

This document describes how to test the automated deployment process for JAiRouter documentation, ensuring that documentation can be correctly built and published to GitHub Pages.

## Test Scripts

The project provides test scripts for multiple platforms:

- **Windows PowerShell**: `scripts/test-deployment.ps1`
- **Windows CMD**: `scripts/test-deployment.cmd`
- **Linux/macOS**: `scripts/test-deployment.sh`

## Local Testing

### Windows Environment

Using PowerShell:
```powershell
# Full test
.\scripts\test-deployment.ps1

# Skip build test
.\scripts\test-deployment.ps1 -SkipBuild

# Include link checking
.\scripts\test-deployment.ps1 -CheckLinks

# Specify language test
.\scripts\test-deployment.ps1 -Language "en"
```

Using CMD:
```cmd
# Full test
scripts\test-deployment.cmd
```

### Linux/macOS Environment

```bash
# Full test
./scripts/test-deployment.sh

# Skip build test
./scripts/test-deployment.sh --skip-build

# Include link checking
./scripts/test-deployment.sh --check-links

# Specify language test
./scripts/test-deployment.sh --language en
```

## Automated Testing

### GitHub Actions Workflows

The project configures the following automated testing workflows:

1. **Deployment Test Validation** (`.github/workflows/deployment-test.yml`)
   - Validate configuration files
   - Check documentation structure
   - Test build process
   - Verify multi-language support
   - Cross-platform compatibility testing

2. **Documentation Build and Deploy** (`.github/workflows/docs.yml`)
   - Automatically build documentation
   - Deploy to GitHub Pages

### Trigger Conditions

Automated tests are triggered in the following situations:

- Push to main/master branch
- Create Pull Request
- Modify documentation-related files
- Manual workflow trigger

## Test Checklist

### Prerequisites Check

- Python environment
- pip package manager
- Required dependency packages

### Configuration Validation

- `mkdocs.yml` syntax correctness
- Plugin configuration validity
- Navigation structure completeness

### Documentation Structure Check

- Required directory existence
- Key file completeness
- Multi-language directory structure

### Build Testing

- Documentation build success
- Output file generation
- Multi-language version correctness

### Content Validation

- Page content correctness
- Navigation functionality
- Search functionality
- Language switching functionality

## Test Result Interpretation

### Success Indicators

- ✓ Green checkmark: Test passed
- 🎉 Celebration icon: All tests passed

### Failure Indicators

- ✗ Red cross: Test failed
- ⚠ Yellow warning: Issues requiring attention

### Test Report

After testing completion, a detailed report is generated including:

- Results of each test
- Build artifact information
- Error details (if any)
- Fix recommendations

## Common Issue Troubleshooting

### Build Failures

1. **Dependency Issues**
   ```bash
   # Reinstall dependencies
   pip install --upgrade mkdocs-material
   pip install --upgrade mkdocs-git-revision-date-localized-plugin
   ```

2. **Configuration Errors**
   ```bash
   # Validate configuration
   mkdocs config
   ```

3. **Missing Files**
   - Check if required documentation files exist
   - Verify path configurations are correct

### Multi-language Issues

1. **Missing Language Versions**
   - Confirm `docs/zh/` and `docs/en/` directories exist
   - Check corresponding `index.md` files

2. **Navigation Translation Issues**
   - Verify `nav_translations` configuration in `mkdocs.yml`
   - Ensure all navigation items have corresponding translations

### Deployment Issues

1. **GitHub Pages Not Enabled**
   - Enable GitHub Pages in repository settings
   - Select "GitHub Actions" as source

2. **Permission Issues**
   - Ensure workflow has sufficient permissions
   - Check `GITHUB_TOKEN` permission settings

## Performance Optimization Recommendations

### Build Optimization

- Use caching to accelerate dependency installation
- Parallel processing of multi-language versions
- Optimize images and resource files

### Deployment Optimization

- Enable CDN acceleration
- Configure appropriate caching strategies
- Compress static resources

## Continuous Improvement

### Monitoring Metrics

- Build time
- Deployment success rate
- Page loading speed
- User access statistics

### Automation Extensions

- Add more quality checks
- Integrate performance testing
- Implement automated rollback