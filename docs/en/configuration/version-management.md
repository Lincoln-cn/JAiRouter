# Configuration Version Management

---
**Version:** 2.6.11  
**Last Updated:** 2026-06-09  
**API Endpoint:** `/api/config/version`
---

## Overview

The configuration version management feature allows users to manage and restore different configuration states of the system. It provides comprehensive version control capabilities including version listing, comparison, application, and deletion.

## REST API Endpoints

### Version Query
- `GET /api/config/version` - Get all configuration version list
- `GET /api/config/version/current` - Get current active version number
- `GET /api/config/version/{version}` - Get specific version configuration details
- `GET /api/config/version/info` - Get detailed information for all versions

### Version Operations
- `POST /api/config/version/apply/{version}` - Apply a specific version configuration
- `DELETE /api/config/version/{version}` - Delete a specific version (cannot delete current version)

### Version Comparison
- `GET /api/config/version/compare/{sourceVersion}/{targetVersion}` - Compare two versions
- `GET /api/config/version/compare/{version}` - View changes in a specific version (compared to previous version)

## Apply Operation

### Definition

The **Apply** operation copies the content of a specified historical version and sets it as the currently active configuration.

### Characteristics

- **Purpose**: Re-apply a previous configuration version as the current working configuration
- **Version History**: Does not alter the existing configuration version history structure
- **Semantics**: More like "use this configuration," emphasizing content reuse
- **Audit**: Logged as "Apply Configuration" in the operation log

### Use Cases

- When you want to reuse the content of a specific previous configuration
- When you need to temporarily switch to a historical configuration for testing
- When you have confirmed that a historical configuration is optimal and should become the current one

## Version Comparison

### Features

- **Side-by-side comparison**: Compare any two versions to identify differences
- **Change tracking**: View additions, deletions, and modifications
- **Quick view**: Compare a version with its predecessor to see what changed

### Use Cases

- Understanding configuration evolution
- Auditing configuration changes
- Troubleshooting configuration issues

## Best Practices

### When to Use Apply

1. When you want to test the effect of a historical configuration
2. When you have confirmed that a historical configuration represents the best practice
3. When you need to switch configurations temporarily for comparison

### When to Use Comparison

1. Before applying a historical version, compare it with current configuration
2. To understand what changed between two versions
3. For auditing and compliance purposes

## Operation Steps

### Apply a Configuration Version

1. Locate the target version in the version management interface
2. Click the **Apply** button
3. Confirm the operation prompt
4. Wait for the system to apply the configuration
5. The interface automatically refreshes to display the new current version

### Compare Versions

1. Select source and target versions
2. Click **Compare** button
3. Review the differences in the comparison view
4. Differences are categorized as: Added, Modified, Deleted

### Delete a Version

1. Select the version to delete (cannot delete current version)
2. Click **Delete** button
3. Confirm the operation
4. Version is removed from history