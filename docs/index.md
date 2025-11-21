# JustSyncIt Documentation

Welcome to the JustSyncIt documentation hub. JustSyncIt is a modern, high-performance backup solution built with Java 21+, featuring BLAKE3 hashing for efficient content verification and secure network transfers.

## Quick Navigation

### 🚀 Getting Started
- **[Getting Started Guide](getting-started.md)** - Complete installation and first-time setup tutorial
- **[CLI Reference](cli-reference.md)** - Comprehensive command reference with examples
- **[Technical Guide](technical-guide.md)** - Technical architecture and implementation details
- **[Installation Guide](guides/installation.md)** - Detailed installation instructions for all platforms

### 📚 User Guides
- **[Network Operations Guide](network-operations.md)** - Remote backup setup and optimization
- **[Snapshot Management Guide](snapshot-management.md)** - Advanced snapshot operations
- **[Performance Guide](performance-guide.md)** - Performance optimization and tuning
- **[Backup & Restore Guide](guides/backup-restore-guide.md)** - Comprehensive backup strategies

### 🛠️ Technical Documentation
- **[Technical Guide](technical-guide.md)** - Architecture, database schema, and implementation details
- **[Troubleshooting Guide](troubleshooting.md)** - Common issues and solutions
- **[Benchmarking Guide](benchmarking-guide.md)** - Performance testing and CI/CD integration

### 🤝 Community
- **[Contributing Guide](guides/contributing.md)** - How to contribute to JustSyncIt
- **[GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)** - Bug reports and feature requests
- **[GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)** - Community discussions

## Overview

JustSyncIt provides comprehensive backup and synchronization capabilities with the following key features:

### Core Features
- **BLAKE3 Hashing**: Fast and secure cryptographic hashing for content verification
- **Content-Addressable Storage**: Automatic deduplication with chunk-based storage
- **Incremental Backups**: Only backup changed files to save time and space
- **Snapshot Management**: Point-in-time snapshots with comprehensive metadata
- **Integrity Verification**: BLAKE3 hashes ensure data integrity at every step

### Network Capabilities
- **Remote Backups**: Backup to remote servers over TCP or QUIC protocols
- **Server Mode**: Run JustSyncIt as a backup server for centralized storage
- **Transfer Optimization**: Efficient chunk-based transfers with resume capability
- **Protocol Selection**: Choose between reliable TCP and modern QUIC transports

### Performance Features
- **SIMD Optimization**: Hardware-accelerated hashing on supported platforms
- **Parallel Processing**: Multi-threaded operations for maximum throughput
- **Configurable Chunking**: Optimize chunk sizes for different data types
- **Memory Management**: Efficient memory usage with configurable limits

## Quick Start

### Prerequisites
- Linux (Ubuntu 20.04+, CentOS 8+, or equivalent)
- Java 21 or higher
- 512MB RAM minimum, 2GB+ recommended

### Installation (5 minutes)

```bash
# 1. Install Java 21
sudo apt update && sudo apt install openjdk-21-jdk

# 2. Download JustSyncIt
wget https://github.com/carabistouflette/justsyncit/releases/latest/download/justsyncit.jar
chmod +x justsyncit.jar

# 3. Verify installation
java -jar justsyncit.jar --help
```

### Your First Backup (2 minutes)

```bash
# 1. Create test data
mkdir -p ~/test-data && echo "Hello, JustSyncIt!" > ~/test-data/test.txt

# 2. Create backup
java -jar justsyncit.jar backup ~/test-data

# 3. List snapshots
java -jar justsyncit.jar snapshots list

# 4. Restore backup
java -jar justsyncit.jar restore <snapshot-id> ~/restore-test
```

### Remote Backup Setup (3 minutes)

```bash
# 1. Start server
java -jar justsyncit.jar server start --daemon

# 2. Backup to remote server
java -jar justsyncit.jar backup ~/important-data --remote --server localhost:8080
```

## Common Use Cases

### Personal Document Backup
```bash
# Daily document backup with hidden files
java -jar justsyncit.jar backup ~/Documents --include-hidden

# Schedule with cron
echo "0 2 * * * java -jar ~/justsyncit/justsyncit.jar backup ~/Documents --include-hidden" | crontab -
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

### Enterprise Remote Backup
```bash
# Start backup server
java -jar justsyncit.jar server start --port 8080 --transport QUIC --daemon

# Client backup with optimization
java -jar justsyncit.jar backup ~/critical-data \
  --remote --server backup.company.com:8080 \
  --transport QUIC --compress \
  --chunk-size 2097152
```

## Performance Tips

### Chunk Size Optimization
- **Documents (many small files)**: `--chunk-size 262144` (256KB)
- **Media files (large files)**: `--chunk-size 4194304` (4MB)
- **Mixed content**: `--chunk-size 1048576` (1MB, default)

### Network Optimization
```bash
# Use QUIC for better performance
java -jar justsyncit.jar backup ~/data --remote --server backup.example.com:8080 --transport QUIC

# Enable compression during transfer
java -jar justsyncit.jar transfer <snapshot-id> --to backup.example.com:8080 --compress
```

### Memory Optimization
```bash
# Increase heap size for large operations
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC"
java -jar justsyncit.jar backup ~/large-dataset
```

## Documentation Structure

### User-Focused Documentation
- **Getting Started**: Installation, first backup, and basic usage
- **CLI Reference**: Complete command documentation with examples
- **Network Operations**: Remote backup setup and optimization
- **Snapshot Management**: Advanced snapshot operations and maintenance
- **Performance Guide**: Optimization tips and benchmarking

### Technical Documentation
- **Technical Guide**: Architecture, database schema, and implementation details
- **Troubleshooting**: Common issues, error resolution, and debugging
- **Benchmarking**: Performance testing and CI/CD integration

### Development Resources
- **Contributing Guide**: How to contribute to the project
- **Installation Guide**: Detailed installation instructions
- **Backup & Restore Guide**: Comprehensive backup strategies

## Command Quick Reference

### Basic Commands
```bash
justsyncit backup <source-dir>           # Create backup
justsyncit restore <snapshot-id> <dir>   # Restore from backup
justsyncit snapshots list                # List snapshots
justsyncit snapshots info <id>           # Snapshot details
```

### Server Commands
```bash
justsyncit server start                  # Start server
justsyncit server status                  # Check status
justsyncit server stop                    # Stop server
```

### Network Commands
```bash
justsyncit transfer <id> --to <server>   # Transfer snapshot
justsyncit sync <local> <remote>         # Sync directories
```

### Utility Commands
```bash
justsyncit hash <path>                   # Generate hash
justsyncit verify <path> <hash>          # Verify integrity
justsyncit help [command]                # Show help
```

## Configuration Examples

### Basic Configuration
```yaml
# ~/.justsyncit/config.yaml
backup:
  default_chunk_size: 1048576
  verify_integrity: true
  include_hidden: false

server:
  default_port: 8080
  default_transport: TCP

storage:
  data_directory: ~/.justsyncit/data
  gc_enabled: true
```

### Performance Configuration
```yaml
backup:
  default_chunk_size: 2097152  # 2MB for large files
  parallel_processing: true
  max_threads: 8

network:
  timeout: 60000
  retry_attempts: 5
  compression: true

storage:
  memory_pool_size: 134217728  # 128MB
  cache_size: 268435456       # 256MB
```

## Troubleshooting Common Issues

### Permission Denied
```bash
# Check permissions
ls -la ~/backup-source

# Fix permissions
chmod -R 755 ~/backup-source

# Run with appropriate user
sudo -u backup-user java -jar justsyncit.jar backup /path/to/data
```

### Out of Memory
```bash
# Increase heap size
export JAVA_OPTS="-Xmx4g"
java -jar justsyncit.jar backup ~/large-directory

# Or use smaller chunk size
java -jar justsyncit.jar backup ~/large-directory --chunk-size 262144
```

### Network Connection Issues
```bash
# Check server status
java -jar justsyncit.jar server status

# Test connectivity
telnet backup-server 8080

# Try different transport
java -jar justsyncit.jar backup ~/data --remote --server backup-server:8080 --transport TCP
```

## Getting Help

### Command Help
```bash
justsyncit help                    # Show all commands
justsyncit help backup             # Show backup help
justsyncit backup --help           # Show backup options
```

### Verbose Logging
```bash
# Enable verbose output
java -jar justsyncit.jar backup ~/data --verbose

# Set specific log level
java -jar justsyncit.jar backup ~/data --log-level DEBUG

# Quiet mode for scripts
java -jar justsyncit.jar backup ~/data --quiet
```

### Community Support
- **GitHub Issues**: [Report bugs and request features](https://github.com/carabistouflette/justsyncit/issues)
- **GitHub Discussions**: [Community discussions and Q&A](https://github.com/carabistouflette/justsyncit/discussions)
- **Documentation**: [Browse all documentation](https://github.com/carabistouflette/justsyncit/tree/main/docs)

## Version Information

### Current Version
- **Version**: 0.1.0
- **Java Requirement**: 21+
- **Supported Platforms**: Linux
- **License**: GPL-3.0

### Release Notes
See [GitHub Releases](https://github.com/carabistouflette/justsyncit/releases) for detailed release notes and download links.

### Changelog
- **v0.1.0**: Initial release with core backup functionality
- **v0.2.0**: Added QUIC transport support and performance improvements
- **v0.3.0**: Enhanced snapshot management and GUI improvements

## Contributing

We welcome contributions! Please see our [Contributing Guide](guides/contributing.md) for details on:
- Setting up development environment
- Code style and quality standards
- Submitting pull requests
- Reporting issues

### Development Setup
```bash
# Clone repository
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit

# Build project
./gradlew build

# Run tests
./gradlew test

# Run development build
./gradlew devBuild
```

---

**JustSyncIt** - Modern backup solution for reliable data protection.

For detailed documentation and guides, explore the links above or visit our [GitHub repository](https://github.com/carabistouflette/justsyncit).