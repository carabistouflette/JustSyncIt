# JustSyncIt Backup and Restore Guide

## Overview

JustSyncIt is a high-performance backup solution that provides efficient deduplication, integrity verification, and fast restore capabilities. This guide will help you get started with backing up and restoring your data.

## Quick Start

### Basic Backup

To backup a directory:

```bash
./justsyncit backup /path/to/source/directory
```

### Basic Restore

To restore a snapshot:

```bash
./justsyncit restore <snapshot-id> /path/to/restore/directory
```

## Backup Command

### Syntax

```bash
justsyncit backup <source-dir> [options]
```

### Arguments

- `source-dir`: Path to the directory you want to backup

### Options

| Option | Description |
|--------|-------------|
| `--follow-symlinks` | Follow symbolic links instead of preserving them |
| `--skip-symlinks` | Skip symbolic links entirely (default) |
| `--include-hidden` | Include hidden files and directories |
| `--no-verify` | Skip integrity verification after backup |
| `--chunk-size SIZE` | Set chunk size in bytes (default: 1MB) |
| `--help` | Show detailed help information |

### Examples

**Basic backup:**
```bash
justsyncit backup /home/user/documents
```

**Backup with hidden files:**
```bash
justsyncit backup /home/user/documents --include-hidden
```

**Backup with custom chunk size:**
```bash
justsyncit backup /home/user/documents --chunk-size 2097152
```

**Backup following symlinks:**
```bash
justsyncit backup /home/user/documents --follow-symlinks --include-hidden
```

## Restore Command

### Syntax

```bash
justsyncit restore <snapshot-id> <target-dir> [options]
```

### Arguments

- `snapshot-id`: ID of the snapshot to restore
- `target-dir`: Directory where files will be restored

### Options

| Option | Description |
|--------|-------------|
| `--overwrite` | Overwrite existing files |
| `--backup-existing` | Backup existing files before overwriting |
| `--no-verify` | Skip integrity verification after restore |
| `--no-preserve-attributes` | Don't preserve file attributes |
| `--include PATTERN` | Only restore files matching pattern |
| `--exclude PATTERN` | Skip files matching pattern |
| `--help` | Show detailed help information |

### Examples

**Basic restore:**
```bash
justsyncit restore abc123-def456 /home/user/restore
```

**Restore with overwrite:**
```bash
justsyncit restore abc123-def456 /home/user/restore --overwrite
```

**Restore specific file types:**
```bash
justsyncit restore abc123-def456 /home/user/restore --include "*.txt"
```

**Restore excluding temporary files:**
```bash
justsyncit restore abc123-def456 /home/user/restore --exclude "*.tmp"
```

## Working with Snapshots

### Listing Snapshots

To list available snapshots:

```bash
justsyncit list-snapshots
```

### Snapshot Information

To get detailed information about a snapshot:

```bash
justsyncit snapshot-info <snapshot-id>
```

### Deleting Snapshots

To delete a snapshot:

```bash
justsyncit delete-snapshot <snapshot-id>
```

## Best Practices

### Backup Strategy

1. **Regular Backups**: Schedule regular backups to ensure data protection
2. **Include Hidden Files**: Use `--include-hidden` for complete system backups
3. **Verify Integrity**: Keep integrity verification enabled for data safety
4. **Appropriate Chunk Size**: Use larger chunks for large files, smaller chunks for many small files

### Chunk Size Recommendations

| Use Case | Recommended Chunk Size |
|-----------|----------------------|
| Documents (many small files) | 256KB - 512KB |
| Mixed content | 1MB (default) |
| Large media files | 2MB - 4MB |
| Database files | 4MB - 8MB |

### Restore Strategy

1. **Test Restores**: Periodically test restore procedures
2. **Backup Existing Files**: Use `--backup-existing` when restoring to production directories
3. **Selective Restore**: Use include/exclude patterns for targeted restores
4. **Verify Integrity**: Keep integrity verification enabled for critical data

## Performance Tips

### Backup Performance

- Use SSD storage for better I/O performance
- Ensure adequate memory for chunking operations
- Consider network bandwidth for remote storage
- Use appropriate chunk sizes for your data

### Restore Performance

- Restore to fast storage when possible
- Use selective restore for partial recovery
- Consider disk space requirements
- Monitor system resources during large restores

## Troubleshooting

### Common Issues

**Permission Denied:**
```bash
sudo justsyncit backup /root/directory
```

**Out of Memory:**
```bash
export JAVA_OPTS="-Xmx4g"
justsyncit backup /large/directory --chunk-size 2097152
```

**Network Timeout:**
```bash
justsyncit backup /directory --chunk-size 4194304
```

### Error Messages

| Error | Cause | Solution |
|-------|--------|----------|
| "Source directory does not exist" | Invalid path | Verify the source directory path |
| "Insufficient permissions" | File system permissions | Run with appropriate permissions |
| "Storage full" | Insufficient disk space | Free up disk space or use different storage |
| "Integrity verification failed" | Data corruption | Check storage media and retry backup |

### Log Files

JustSyncIt creates log files in the following locations:
- Linux/Mac: `~/.justsyncit/logs/`
- Windows: `%APPDATA%\JustSyncIt\logs\`

Log levels can be controlled with:
- `--quiet`: Minimal output
- `--verbose`: Detailed information
- `--debug`: Debug information

## Advanced Usage

### Custom Storage Configuration

JustSyncIt supports multiple storage backends. Configure storage in `~/.justsyncit/config.yaml`:

```yaml
storage:
  type: filesystem
  path: /path/to/storage
  encryption: true
  
  # Alternative: S3 storage
  # type: s3
  # bucket: my-backup-bucket
  # region: us-west-2
```

### Automation

#### Cron Job (Linux/Mac)

Add to crontab for daily backups:

```bash
0 2 * * * /usr/local/bin/justsyncit backup /home/user/documents --include-hidden
```

#### Task Scheduler (Windows)

Create a scheduled task to run:
```cmd
"C:\Program Files\JustSyncIt\justsyncit.exe" backup "C:\Users\user\Documents" --include-hidden
```

### Scripting

JustSyncIt can be integrated into scripts:

```bash
#!/bin/bash
SOURCE_DIR="/home/user/documents"
BACKUP_DIR="/backup/justsyncit"
DATE=$(date +%Y%m%d)

# Create backup
SNAPSHOT_ID=$(justsyncit backup "$SOURCE_DIR" --include-hidden | grep "Snapshot ID:" | cut -d' ' -f3)

# Verify backup
if justsyncit verify "$SNAPSHOT_ID"; then
    echo "Backup successful: $SNAPSHOT_ID"
else
    echo "Backup verification failed"
    exit 1
fi
```

## Security Considerations

### Data Protection

- JustSyncit uses BLAKE3 for cryptographic integrity verification
- Consider encrypting sensitive data before backup
- Secure storage locations with appropriate permissions
- Regularly test restore procedures

### Access Control

- Run JustSyncIt with minimal required permissions
- Use dedicated backup accounts where possible
- Audit backup and restore operations
- Implement network security for remote storage

## Support

For additional help:

1. Check the log files for error details
2. Review this documentation for common solutions
3. Visit the project wiki at https://github.com/justsyncit/justsyncit/wiki
4. Open an issue on GitHub at https://github.com/justsyncit/justsyncit/issues

## Version History

### v0.1.0
- Initial release with basic backup and restore functionality
- Command-line interface
- Progress tracking
- Integrity verification
- Deduplication support