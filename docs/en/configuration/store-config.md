# Store Configuration

JAiRouter uses a storage manager to persist configuration data and version control. This page details the storage-related configuration options.

## Storage Types

JAiRouter supports multiple storage types, with file storage as the default.

### File Storage (file)

File storage is the default storage method, saving configuration data in the local file system.

## Configuration Options

Storage configuration is configured through the `store` prefix and can be set in `application.yml` or related configuration files.

### Basic Configuration

store:
  type: file        # Storage type, default is file
  path: "config/"   # Storage path, default is config/
  auto-merge: true  # Whether to enable automatic merge function, default is true

### Configuration Description

| Configuration Item | Default Value | Description |
|--------------------|---------------|-------------|
| `store.type` | `file` | Storage type, currently only file storage is supported |
| `store.path` | `config/` | Directory path for configuration file storage |
| `store.auto-merge` | `true` | Whether to enable automatic merge function |

## Environment-Specific Configuration

Different environments can have different storage configurations:

### Development Environment (dev)

# application-dev.yml
store:
  path: "config-dev/"
  auto-merge: true

### Staging Environment (staging)

# application-staging.yml
store:
  path: "config-staging/"
  auto-merge: true

### Production Environment (prod)

# application-prod.yml
store:
  path: "config-prod/"
  auto-merge: true

## Auto-Merge Configuration

The auto-merge function is used to scan and process multi-version configuration files in the configuration directory.

### Function Description

When the auto-merge function is enabled, the system will periodically scan multi-version configuration files in the configuration directory and provide merge functionality.

### Usage Example

# Enable auto-merge function and specify configuration directory
store:
  type: file
  path: "/var/lib/jairouter/config/"
  auto-merge: true

## Notes

1. Ensure the storage path has appropriate read/write permissions
2. In production environments, it is recommended to use absolute paths to avoid path issues
3. Configuration directories should be backed up regularly to prevent data loss
4. Different environments should use different storage paths to avoid configuration conflicts

## Troubleshooting

### Storage Directory Does Not Exist

If the configured storage directory does not exist, the system will log warning messages but will not automatically create the directory. Please ensure the directory exists and has appropriate permissions.

### Permission Issues

If you encounter permission issues, please check:
- Read/write permissions of the application running user to the storage directory
- Execute permissions of the parent directory (permission to enter the directory)

### Insufficient Disk Space

Monitor the disk usage of the storage directory to ensure there is enough space to store configuration data and version history.

### Auto-Merge Function Not Working

If the auto-merge function is not working, please check:
- Whether the `store.auto-merge` configuration is set to true
- Whether the configuration directory exists and has read permissions
- Whether the configuration file naming conforms to the `model-router-config@<version>.json` format
