---
layout: guide
title: Quick Start
nav_order: 2
---

# Quick Start Guide

Get started with JustSyncIt in minutes! This guide will walk you through the basic setup and first synchronization.

## What You'll Learn

- Basic JustSyncIt concepts
- How to configure your first sync
- Running your first synchronization
- Monitoring sync status

## Core Concepts

JustSyncIt works with these key concepts:

- **Source**: The directory you want to synchronize
- **Destination**: Where synchronized files are stored
- **Chunks**: Files are broken into chunks for efficient transfer
- **Hashes**: BLAKE3 hashes ensure file integrity

## Step 1: Initialize JustSyncIt

First, create a configuration file:

```bash
java -jar justsyncit.jar init
```

This creates a default configuration at `~/.config/justsyncit/config.yml`.

## Step 2: Configure Your First Sync

Edit the configuration file or use the command line:

```bash
# Basic sync configuration
java -jar justsyncit.jar config \
  --source "/path/to/source" \
  --destination "/path/to/destination" \
  --name "my-first-sync"
```

## Step 3: Run Your First Synchronization

```bash
# Perform initial sync
java -jar justsyncit.jar sync --name "my-first-sync"
```

You'll see output like:
```
[INFO] Starting synchronization: my-first-sync
[INFO] Scanning source directory: /path/to/source
[INFO] Found 156 files (2.3 GB)
[INFO] Starting chunked transfer...
[INFO] Transfer completed: 156/156 files
[INFO] Verification: All files verified successfully
[INFO] Synchronization completed in 2m 34s
```

## Step 4: Verify the Sync

Check that files were synchronized correctly:

```bash
# Verify integrity
java -jar justsyncit.jar verify --name "my-first-sync"
```

## Step 5: Monitor Ongoing Syncs

For continuous synchronization:

```bash
# Start daemon mode
java -jar justsyncit.jar daemon --name "my-first-sync"
```

## Common Use Cases

### Backup Important Documents

```bash
java -jar justsyncit.jar config \
  --source "~/Documents" \
  --destination "/backup/documents" \
  --name "documents-backup" \
  --schedule "daily"
```

### Sync Project Files

```bash
java -jar justsyncit.jar config \
  --source "~/projects/my-app" \
  --destination "/shared/projects/my-app" \
  --name "project-sync" \
  --exclude "*.tmp,*.log"
```

### Network Synchronization

```bash
# Server mode
java -jar justsyncit.jar server --port 8080

# Client mode
java -jar justsyncit.jar client \
  --server "192.168.1.100:8080" \
  --destination "~/synced-files"
```

## Configuration Options

### Basic Options

| Option | Description | Example |
|---------|-------------|----------|
| `--source` | Source directory | `~/Documents` |
| `--destination` | Destination directory | `/backup` |
| `--name` | Sync configuration name | `my-backup` |
| `--exclude` | Files to exclude | `*.tmp,*.log` |

### Advanced Options

| Option | Description | Default |
|---------|-------------|----------|
| `--chunk-size` | Chunk size in MB | 64 |
| `--compression` | Enable compression | false |
| `--encryption` | Enable encryption | false |
| `--schedule` | Sync schedule | manual |

## Tips for Success

### Performance Tips

1. **Use SSD storage** for better chunk I/O performance
2. **Adjust chunk size** based on your network conditions
3. **Enable compression** for text-heavy directories
4. **Use exclusion patterns** to skip unnecessary files

### Reliability Tips

1. **Verify regularly** with the verify command
2. **Monitor logs** for any errors
3. **Test restores** before critical situations
4. **Keep configuration** in version control

## Troubleshooting

### Common Issues

**Sync is slow:**
- Check network bandwidth
- Reduce chunk size
- Disable compression for already compressed files

**Verification fails:**
- Check disk space
- Verify file permissions
- Check for corruption in source files

**Memory issues:**
- Increase heap size: `-Xmx2g`
- Reduce chunk size
- Enable incremental mode

## Next Steps

Now that you've completed your first sync:

1. Learn about [Advanced Configuration](configuration.md)
2. Explore [Network Synchronization](../network-protocol.md)
3. Read about [Performance Optimization](performance.md)
4. Check the [API Reference](../api/) for automation

## Need Help?

- [Troubleshooting Guide](troubleshooting.md)
- [Community Forums](https://github.com/carabistouflette/justsyncit/discussions)
- [Report Issues](https://github.com/carabistouflette/justsyncit/issues)