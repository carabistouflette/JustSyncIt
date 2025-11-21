# JustSyncIt User Guide

## Table of Contents

- [Introduction](#introduction)
- [Key Features](#key-features)
- [Basic Concepts](#basic-concepts)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Common Use Cases](#common-use-cases)
- [Advanced Usage](#advanced-usage)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Introduction

JustSyncIt is a modern, high-performance backup solution designed for Linux environments. Built with Java 21+, it provides efficient data synchronization and backup capabilities with advanced features like BLAKE3 hashing, content-addressable storage, and network transfers.

### What Makes JustSyncIt Different?

- **Content-Addressable Storage**: Files are broken into chunks and stored by their content hash, enabling automatic deduplication
- **BLAKE3 Hashing**: Fast cryptographic hashing with SIMD optimization for integrity verification
- **Network Capabilities**: Built-in support for remote backups with TCP and QUIC protocols
- **Snapshot Management**: Point-in-time snapshots with comprehensive metadata
- **Performance Optimized**: Designed for high-throughput operations with minimal overhead

## Key Features

### Core Backup Features
- **Incremental Backups**: Only backup changed files to save time and space
- **Deduplication**: Automatic detection and storage of identical content only once
- **Integrity Verification**: BLAKE3 hashes ensure data integrity at every step
- **Flexible Chunking**: Configurable chunk sizes optimized for different use cases

### Network Features
- **Remote Backups**: Backup to remote servers over TCP or QUIC protocols
- **Server Mode**: Run JustSyncIt as a backup server for centralized storage
- **Transfer Optimization**: Efficient chunk-based transfers with resume capability
- **Protocol Selection**: Choose between reliable TCP and modern QUIC transports

### Storage Features
- **Multiple Storage Backends**: Filesystem, SQLite, and in-memory storage options
- **Garbage Collection**: Automatic cleanup of unused chunks
- **Compression Support**: Optional compression to reduce storage requirements
- **Encryption Ready**: Architecture supports encryption extensions

### Management Features
- **Snapshot Management**: List, inspect, verify, and delete snapshots
- **Progress Tracking**: Real-time progress indicators for long operations
- **Detailed Logging**: Comprehensive logging with configurable levels
- **Command-Line Interface**: Full-featured CLI with extensive options

## Basic Concepts

### Snapshots
A snapshot represents a point-in-time backup of a directory. Each snapshot contains:
- Unique identifier (ID)
- Human-readable name and description
- Creation timestamp
- List of files and their metadata
- References to data chunks

### Chunks
Files are broken into chunks for efficient storage and transfer:
- Default chunk size: 64KB (configurable)
- Each chunk is identified by its BLAKE3 hash
- Identical chunks are stored only once (deduplication)
- Chunks can be transferred independently over the network

### Content-Addressable Storage
Storage system where data is addressed by its content hash:
- `hash → data` mapping instead of `filename → data`
- Automatic deduplication of identical content
- Integrity verification through hash comparison
- Efficient garbage collection of unused data

### Transport Protocols
JustSyncIt supports two network protocols:
- **TCP**: Reliable, connection-oriented protocol (default)
- **QUIC**: Modern UDP-based protocol with better performance and built-in encryption

## Installation

### System Requirements

- **Operating System**: Linux (Ubuntu 20.04+, CentOS 8+, or equivalent)
- **Java**: Java 21 or higher
- **Memory**: Minimum 512MB RAM, recommended 2GB+ for large operations
- **Disk**: Sufficient space for backups and temporary files
- **Network**: For remote operations (optional)

### Installing Java 21

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

#### CentOS/RHEL
```bash
sudo yum install java-21-openjdk-devel
java -version
```

#### Using SDKMAN
```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.2-open
sdk use java 21.0.2-open
```

### Installing JustSyncIt

#### Option 1: Download Pre-built JAR
```bash
wget https://github.com/carabistouflette/justsyncit/releases/latest/download/justsyncit.jar
chmod +x justsyncit.jar
```

#### Option 2: Build from Source
```bash
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit
./gradlew build
cp build/libs/justsyncit-*.jar justsyncit.jar
```

#### Option 3: Docker
```bash
docker pull justsyncit/app:latest
docker run -v /path/to/data:/data justsyncit/app:latest
```

### Verification
```bash
java -jar justsyncit.jar --help
```

## Quick Start

### Your First Backup

1. **Create a test directory**:
```bash
mkdir -p ~/test-backup/source
echo "Hello, JustSyncIt!" > ~/test-backup/source/test.txt
cp -r /etc/hostname ~/test-backup/source/
```

2. **Run your first backup**:
```bash
java -jar justsyncit.jar backup ~/test-backup/source
```

You should see output like:
```
Starting backup of: /home/user/test-backup/source
Options: BackupOptions{...}

Backup completed successfully!
Files processed: 2
Total bytes: 45
Chunks created: 2
Integrity verified: true
```

3. **List your snapshots**:
```bash
java -jar justsyncit.jar snapshots list
```

4. **Restore your backup**:
```bash
mkdir -p ~/test-backup/restore
java -jar justsyncit.jar restore <snapshot-id> ~/test-backup/restore
```

### Your First Remote Backup

1. **Start a backup server**:
```bash
java -jar justsyncit.jar server start --port 8080
```

2. **From another terminal, backup to the server**:
```bash
java -jar justsyncit.jar backup ~/test-backup/source --remote --server localhost:8080
```

## Common Use Cases

### Daily Document Backup

```bash
# Backup documents with hidden files
java -jar justsyncit.jar backup ~/Documents --include-hidden

# Schedule with cron (daily at 2 AM)
echo "0 2 * * * java -jar /path/to/justsyncit.jar backup ~/Documents --include-hidden" | crontab -
```

### Project Directory Backup

```bash
# Backup project with custom chunk size
java -jar justsyncit.jar backup ~/my-project --chunk-size 1048576

# Exclude build artifacts
java -jar justsyncit.jar backup ~/my-project --exclude "target/,build/,*.tmp"
```

### System Configuration Backup

```bash
# Backup system configuration
sudo java -jar justsyncit.jar backup /etc --include-hidden --follow-symlinks

# Backup user configuration
java -jar justsyncit.jar backup ~/.config --include-hidden
```

### Remote Backup Setup

1. **Server setup**:
```bash
# Start server on backup machine
java -jar justsyncit.jar server start --port 8080 --daemon
```

2. **Client backup**:
```bash
# Backup to remote server
java -jar justsyncit.jar backup ~/important-data --remote --server backup-server:8080

# Use QUIC for better performance
java -jar justsyncit.jar backup ~/important-data --remote --server backup-server:8080 --transport QUIC
```

### Selective Restore

```bash
# Restore only specific file types
java -jar justsyncit.jar restore <snapshot-id> ~/restore --include "*.txt,*.md"

# Restore excluding temporary files
java -jar justsyncit.jar restore <snapshot-id> ~/restore --exclude "*.tmp,*.log"
```

## Advanced Usage

### Chunk Size Optimization

Different chunk sizes work better for different data types:

```bash
# Small files (documents, source code)
java -jar justsyncit.jar backup ~/documents --chunk-size 262144

# Large files (media, databases)
java -jar justsyncit.jar backup ~/media --chunk-size 4194304

# Mixed content
java -jar justsyncit.jar backup ~/mixed --chunk-size 1048576
```

### Network Transfer Optimization

```bash
# Enable compression during transfer
java -jar justsyncit.jar transfer <snapshot-id> --to remote-server:8080 --compress

# Resume interrupted transfer
java -jar justsyncit.jar transfer <snapshot-id> --to remote-server:8080 --resume

# Sync directory with remote server
java -jar justsyncit.jar sync ~/local-dir remote-server:8080 --one-way
```

### Snapshot Management

```bash
# List snapshots with details
java -jar justsyncit.jar snapshots list --verbose

# Get detailed snapshot information
java -jar justsyncit.jar snapshots info <snapshot-id> --show-files

# Verify snapshot integrity
java -jar justsyncit.jar snapshots verify <snapshot-id>

# Delete old snapshots
java -jar justsyncit.jar snapshots delete <snapshot-id> --force
```

### Performance Monitoring

```bash
# Enable verbose logging
java -jar justsyncit.jar backup ~/data --verbose

# Set specific log level
java -jar justsyncit.jar backup ~/data --log-level DEBUG

# Quiet mode for scripts
java -jar justsyncit.jar backup ~/data --quiet
```

## Best Practices

### Backup Strategy

1. **Regular Schedule**: Set up automated backups using cron or systemd timers
2. **Multiple Locations**: Keep backups in different physical locations
3. **Test Restores**: Regularly test restore procedures to ensure data integrity
4. **Monitor Storage**: Watch disk space and clean up old snapshots as needed

### Performance Optimization

1. **Appropriate Chunk Sizes**: Use smaller chunks for documents, larger for media files
2. **SSD Storage**: Use SSD for backup storage when possible
3. **Network Bandwidth**: Consider bandwidth limitations for remote backups
4. **Resource Allocation**: Ensure adequate memory for large operations

### Security Considerations

1. **File Permissions**: Ensure proper permissions on backup directories
2. **Network Security**: Use secure networks for remote backups
3. **Access Control**: Run with minimal required permissions
4. **Data Encryption**: Consider encryption for sensitive data

### Organization

1. **Descriptive Names**: Use meaningful names for snapshots
2. **Regular Cleanup**: Delete unnecessary snapshots to save space
3. **Documentation**: Keep records of backup configurations
4. **Monitoring**: Set up alerts for backup failures

## Troubleshooting

### Common Issues

#### Permission Denied
```bash
# Check file permissions
ls -la ~/backup-source

# Fix permissions if needed
chmod -R 755 ~/backup-source

# Run with appropriate user
sudo -u backup-user java -jar justsyncit.jar backup /path/to/data
```

#### Out of Memory
```bash
# Increase heap size
export JAVA_OPTS="-Xmx4g"
java -jar justsyncit.jar backup ~/large-directory

# Or use smaller chunk size
java -jar justsyncit.jar backup ~/large-directory --chunk-size 262144
```

#### Network Connection Issues
```bash
# Check server status
java -jar justsyncit.jar server status

# Test connectivity
telnet backup-server 8080

# Try different transport
java -jar justsyncit.jar backup ~/data --remote --server backup-server:8080 --transport TCP
```

#### Slow Performance
```bash
# Enable verbose logging to identify bottlenecks
java -jar justsyncit.jar backup ~/data --verbose

# Try different chunk size
java -jar justsyncit.jar backup ~/data --chunk-size 1048576

# Check disk I/O
iostat -x 1
```

### Getting Help

1. **Command Help**: Use `--help` with any command
2. **Verbose Logging**: Use `--verbose` for detailed output
3. **Log Files**: Check logs in `~/.justsyncit/logs/`
4. **Community**: Visit [GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)

### Error Messages

| Error | Cause | Solution |
|--------|-------|----------|
| "Source directory does not exist" | Invalid path | Verify the source directory path |
| "Insufficient permissions" | File system permissions | Run with appropriate permissions |
| "Connection refused" | Server not running | Start the backup server |
| "Checksum verification failed" | Data corruption | Check storage media and retry |
| "Out of memory" | Insufficient heap size | Increase JVM heap size |

## Next Steps

Now that you understand the basics:

1. Read the [Getting Started Guide](getting-started.md) for detailed setup instructions
2. Check the [CLI Reference](cli-reference.md) for all available commands
3. Explore the [Network Operations Guide](network-operations.md) for remote backup setup
4. Review the [Performance Guide](performance-guide.md) for optimization tips
5. Visit the [Snapshot Management Guide](snapshot-management.md) for advanced snapshot operations

For additional help:
- [Troubleshooting Guide](troubleshooting.md)
- [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)
- [Community Discussions](https://github.com/carabistouflette/justsyncit/discussions)