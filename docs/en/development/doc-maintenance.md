# Documentation Development Guide

<!-- Version Info -->
> **Document Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /Version Info -->


This directory contains the complete documentation for the JAiRouter project.

## Documentation Management Tools

We provide unified documentation management scripts that integrate document serving, link checking, version management, and other features.

### Requirements

- Python 3.x
- pip (Python package manager)
- PowerShell (Windows) or Bash (Linux/macOS)

### Unified Management Scripts

Choose the script corresponding to your operating system:

- **Windows PowerShell**: `scripts\docs\docs-manager.ps1`
- **Windows Batch**: `scripts\docs\docs-manager.cmd`
- **Linux/macOS**: `scripts/docs/docs-manager.sh`

### Quick Start

#### Starting the Documentation Server

```bash
# Windows PowerShell
.\scripts\docs\docs-manager.ps1 serve

# Windows Batch
.\scripts\docs\docs-manager.cmd serve

# Linux/macOS
./scripts/docs/docs-manager.sh serve

# Custom port and address
.\scripts\docs\docs-manager.ps1 serve -HostAddress 0.0.0.0 -Port 3000
./scripts/docs/docs-manager.sh serve --host 0.0.0.0 --port 3000
```

#### Checking Documentation Links

```bash
# Check all links
.\scripts\docs\docs-manager.ps1 check-links

# Output report to file
.\scripts\docs\docs-manager.ps1 check-links -Output report.json

# Exit with code 1 when issues are found
.\scripts\docs\docs-manager.ps1 check-links -FailOnError
```

#### Fixing Broken Links

```bash
# Analyze and display fix suggestions
.\scripts\docs\docs-manager.ps1 fix-links

# Apply fix suggestions (interactive confirmation)
.\scripts\docs\docs-manager.ps1 fix-links -Apply

# Auto-fix without asking for confirmation
.\scripts\docs\docs-manager.ps1 fix-links -Apply -AutoFix
```

#### Version Management

```bash
# Scan and update document versions
.\scripts\docs\docs-manager.ps1 version -Scan

# Add version header information
.\scripts\docs\docs-manager.ps1 version -AddHeaders

# Export version data
.\scripts\docs\docs-manager.ps1 version -Export data.json

# Clean up change records older than 90 days
.\scripts\docs\docs-manager.ps1 version -Cleanup 90
```

#### Validating Documentation Structure

```bash
# Validate documentation structure and configuration
.\scripts\docs\docs-manager.ps1 validate
```

#### Checking Documentation Synchronization

```bash
# Check synchronization between docs and code
.\scripts\docs\docs-manager.ps1 check-sync

# Output report to file
.\scripts\docs\docs-manager.ps1 check-sync -Output sync-report.md
```

### Legacy Method (Manual Execution)

If you prefer to execute each step manually:

```bash
# Install dependencies
pip install -r requirements.txt

# Start development server
mkdocs serve

# Build static files
mkdocs build
```

#### Linux/macOS Users

```bash
# Start documentation service using the Shell script
./scripts/docs/docs-manager.sh serve

# Or execute manually
pip3 install -r requirements.txt
mkdocs serve
```

## Available Commands

### serve - Start Documentation Server

Starts the local development server with hot-reload support.

**Options:**
- `--host <address>`: Listening address (default: localhost)
- `--port <port>`: Listening port (default: 8000)

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 serve -HostAddress 0.0.0.0 -Port 3000
```

### check-links - Check Link Validity

Checks all links in the documentation, including internal and external links.

**Options:**
- `--output <file>`: Output report file path
- `--fail-on-error`: Exit with code 1 when broken links are found

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 check-links -Output link-report.json -FailOnError
```

### fix-links - Fix Broken Links

Based on the link check report, provides fix suggestions and automatic fix capabilities.

**Options:**
- `--apply`: Apply fix suggestions
- `--auto-fix`: Auto-fix without asking for confirmation

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 fix-links -Apply -AutoFix
```

### version - Version Management

Manages document version information and tracks document changes.

**Options:**
- `--scan`: Scan and update version information
- `--add-headers`: Add version header information to documents
- `--cleanup <days>`: Clean up change records older than specified days
- `--export <file>`: Export version data to file
- `--check-outdated <days>`: Days threshold for checking outdated documents

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 version -Scan -AddHeaders -Export version-data.json
```

### check-sync - Check Synchronization

Checks the synchronization between documentation content and code, verifying the accuracy of configuration examples and API documentation.

**Options:**
- `--output <file>`: Output report file path
- `--fail-on-error`: Exit with code 1 when critical issues are found

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 check-sync -Output sync-report.md -FailOnError
```

### validate - Validate Documentation

Validates the documentation structure and MkDocs configuration file correctness.

**Example:**
```bash
.\scripts\docs\docs-manager.ps1 validate
```

## Documentation Structure

```
docs/
├── zh/                     # Chinese documentation
│   ├── index.md           # Homepage
│   ├── getting-started/   # Getting Started
│   ├── configuration/     # Configuration Guide
│   ├── api-reference/     # API Reference
│   ├── deployment/        # Deployment Guide
│   ├── monitoring/        # Monitoring Guide
│   ├── development/       # Development Guide
│   ├── troubleshooting/   # Troubleshooting
│   └── reference/         # Reference Materials
├── en/                     # English documentation
│   └── (same structure as Chinese)
├── assets/                 # Static assets
├── CNAME                   # GitHub Pages domain configuration
└── README.md              # This file
```

## Development Workflow

1. **Start the Development Server**
   ```bash
   .\scripts\docs\docs-manager.ps1 serve
   ```

2. **Edit Documentation Content**
   - Edit Markdown files in the corresponding language directory
   - Auto-reload on save

3. **Check Link Validity**
   ```bash
   .\scripts\docs\docs-manager.ps1 check-links
   ```

4. **Fix Discovered Issues**
   ```bash
   .\scripts\docs\docs-manager.ps1 fix-links -Apply
   ```

5. **Update Version Information**
   ```bash
   .\scripts\docs\docs-manager.ps1 version -Scan -AddHeaders
   ```

6. **Validate Documentation Structure**
   ```bash
   .\scripts\docs\docs-manager.ps1 validate
   ```

## Deployment

Documentation is automatically deployed to GitHub Pages via GitHub Actions. Every push to the `main` branch automatically triggers a build and deployment.

### Manual Deployment

To deploy manually:

```bash
# Build documentation
mkdocs build

# Deploy to GitHub Pages
mkdocs gh-deploy
```

## Contributing Guide

1. Create a feature branch
2. Edit documentation content
3. Run documentation check tools
4. Submit a Pull Request

### Documentation Writing Standards

- Use Markdown format
- Use Chinese punctuation for Chinese documentation
- Use English punctuation for English documentation
- Specify language type for code blocks
- Use relative paths for links
- Place images in the `assets/` directory

### Quality Checks

Before submitting, please run the following checks:

```bash
# Check link validity
.\scripts\docs\docs-manager.ps1 check-links -FailOnError

# Check documentation synchronization
.\scripts\docs\docs-manager.ps1 check-sync -FailOnError

# Validate documentation structure
.\scripts\docs\docs-manager.ps1 validate
```

## Troubleshooting

### Common Issues

1. **Python dependency installation fails**
   - Ensure Python 3.x is installed
   - Try using `pip3` instead of `pip`
   - Check network connection

2. **MkDocs server fails to start**
   - Check `mkdocs.yml` configuration file syntax
   - Ensure all navigation files exist
   - Run `.\scripts\docs\docs-manager.ps1 validate` to check configuration

3. **Link check fails**
   - Check network connection
   - Some external links may have anti-scraping protection
   - For internal links, verify that file paths are correct

4. **Script execution permission issues (Linux/macOS)**
   ```bash
   chmod +x scripts/docs/docs-manager.sh
   ```

### Getting Help

If you encounter issues, you can:

1. View script help information:
   ```bash
   .\scripts\docs\docs-manager.ps1 help
   ```

2. Check project Issues
3. Contact project maintainers

## Changelog

- **v1.1.0** (2025-08-18): Integrated documentation management scripts, simplified usage workflow
- **v1.0.0** (2025-08-18): Initial version, basic documentation structure
