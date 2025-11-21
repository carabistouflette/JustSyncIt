# JustSyncIt

[![CI/CD Pipeline](https://github.com/carabistouflette/justsyncit/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![codecov](https://codecov.io/gh/justsyncit/justsyncit/branch/main/graph/badge.svg)](https://codecov.io/gh/justsyncit/justsyncit)
[![Security Scan](https://github.com/carabistouflette/justsyncit/workflows/Security%20Scan/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![Docker Hub](https://img.shields.io/badge/docker-ready-blue.svg)](https://hub.docker.com/r/justsyncit/app)

A modern, reliable backup solution built with Java 21+ featuring BLAKE3 hashing for efficient content verification and secure network transfers.

## 🚀 Quick Start

### Prerequisites
- **Linux** (Ubuntu 20.04+, CentOS 8+, or equivalent)
- **Java 21+** 
- **512MB RAM** minimum, 2GB+ recommended

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

## ✨ Features

### Core Backup Features
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

### Storage Options
- **Multiple Storage Backends**: Filesystem, SQLite, and in-memory storage options
- **Garbage Collection**: Automatic cleanup of unused chunks
- **Compression Support**: Optional compression to reduce storage requirements
- **Cross-Platform**: Runs on Linux with optimized performance

## 📖 Documentation

### 🚀 Getting Started
- **[Getting Started Guide](docs/getting-started.md)** - Complete installation and first-time setup tutorial
- **[CLI Reference](docs/cli-reference.md)** - Comprehensive command reference with examples

### 📚 User Guides
- **[Network Operations Guide](docs/network-operations.md)** - Remote backup setup and optimization
- **[Snapshot Management Guide](docs/snapshot-management.md)** - Advanced snapshot operations
- **[Performance Guide](docs/performance-guide.md)** - Performance optimization and tuning

### 🛠️ Technical Documentation
- **[Technical Guide](docs/technical-guide.md)** - Architecture, database schema, and implementation details
- **[Troubleshooting Guide](docs/troubleshooting.md)** - Common issues and solutions

### 📋 All Documentation
- **[Documentation Index](docs/index.md)** - Complete documentation hub with quick navigation

## 🛠️ Installation Options

### Option 1: Package Manager Installation (Recommended)

**Linux (Debian/Ubuntu):**
```bash
# Download and install DEB package
wget https://github.com/carabistouflette/justsyncit/releases/download/v0.1.0/justsyncit_0.1.0_all.deb
sudo dpkg -i justsyncit_0.1.0_all.deb

# Start the service
sudo systemctl start justsyncit
sudo systemctl enable justsyncit
```

**Linux (RHEL/CentOS/Fedora):**
```bash
# Download and install RPM package
wget https://github.com/carabistouflette/justsyncit/releases/download/v0.1.0/justsyncit-0.1.0-1.noarch.rpm
sudo rpm -i justsyncit-0.1.0-1.noarch.rpm

# Start the service
sudo systemctl start justsyncit
sudo systemctl enable justsyncit
```

### Option 2: Manual Installation

**Download Fat JAR (All-in-one):**
```bash
# Download executable JAR with all dependencies
wget https://github.com/carabistouflette/justsyncit/releases/download/v0.1.0/justsyncit-0.1.0-all.jar
chmod +x justsyncit-0.1.0-all.jar

# Verify installation
java -jar justsyncit-0.1.0-all.jar --help
```

### Option 3: Build from Source
```bash
# Clone the repository
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit

# Build the project
./gradlew releaseBuild

# Run the application
java -jar build/libs/justsyncit-0.1.0-all.jar
```

### Option 4: Docker
```bash
# Pull the latest image
docker pull justsyncit/app:0.1.0

# Run with Docker Compose
docker-compose up -d

# Or run standalone
docker run -p 8080:8080 justsyncit/app:0.1.0
```

## 💻 Usage Examples

### Basic Backup Operations

```bash
# Simple backup
justsyncit backup ~/documents

# Backup with options
justsyncit backup ~/documents \
  --include-hidden \
  --chunk-size 1048576 \
  --verify-integrity

# Remote backup
justsyncit backup ~/documents \
  --remote \
  --server backup.example.com:8080 \
  --transport QUIC
```

### Snapshot Management

```bash
# List all snapshots
justsyncit snapshots list --verbose

# Get snapshot details
justsyncit snapshots info <snapshot-id> --show-files

# Verify snapshot integrity
justsyncit snapshots verify <snapshot-id>

# Delete old snapshots
justsyncit snapshots delete <snapshot-id> --force
```

### Server Operations

```bash
# Start backup server
justsyncit server start --port 8080 --daemon

# Check server status
justsyncit server status --verbose

# Stop server
justsyncit server stop
```

### Performance Optimization

```bash
# High-performance backup
java -jar justsyncit.jar backup ~/data \
  --threads 8 \
  --chunk-size 2097152 \
  --memory-efficient

# Network-optimized backup
java -jar justsyncit.jar backup ~/data \
  --remote --server backup.example.com:8080 \
  --transport QUIC \
  --parallel-transfers 4 \
  --compress
```

## ⚡ Performance

### Benchmarks

JustSyncIt delivers high performance for backup and restore operations:

| Operation | Target Performance | Typical Performance |
|-----------|-------------------|-------------------|
| **Local Backup** | >50 MB/s | 60-120 MB/s |
| **Local Restore** | >100 MB/s | 120-250 MB/s |
| **Network Backup** | >80% bandwidth | 85-95% utilization |
| **Deduplication Overhead** | <10% | 5-8% |
| **Memory Usage** | <500 MB | 200-400 MB |

### Optimization Features

- **BLAKE3 Hashing**: Parallel hashing with SIMD acceleration
- **Chunked Storage**: Efficient I/O with configurable chunk sizes
- **Concurrent Operations**: Multi-threaded processing for maximum throughput
- **Memory Pooling**: Reduced garbage collection overhead
- **Network Optimization**: Protocol selection and transfer optimization

## 🔧 Configuration

### Basic Configuration

Create `~/.justsyncit/config.yaml`:

```yaml
# JustSyncIt Configuration
backup:
  default_chunk_size: 1048576  # 1MB
  verify_integrity: true
  include_hidden: false

server:
  default_port: 8080
  default_transport: TCP
  max_connections: 100

network:
  timeout: 30000  # 30 seconds
  retry_attempts: 3
  retry_delay: 1000  # 1 second

storage:
  data_directory: ~/.justsyncit/data
  gc_enabled: true
  gc_interval: 86400000  # 24 hours

logging:
  level: INFO
  file: ~/.justsyncit/logs/justsyncit.log
  max_size: 10485760  # 10MB
  max_files: 5
```

### Environment Variables

```bash
# Set default chunk size
export JUSTSYNCIT_CHUNK_SIZE=1048576

# Set default transport
export JUSTSYNCIT_TRANSPORT=QUIC

# Set default server port
export JUSTSYNCIT_SERVER_PORT=9090

# Set JVM options for performance
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC"
```

## 🏗️ Architecture

JustSyncIt is built with a modular architecture following SOLID principles:

### Core Modules
- **Hash Module**: Provides BLAKE3 hashing with SIMD optimizations
- **Network Module**: Handles client-server communication and file transfers
- **Storage Module**: Manages content-addressable storage with integrity verification
- **Scanner Module**: Filesystem scanning with configurable options
- **Command Module**: Comprehensive CLI with extensible command system

### Design Principles
- **Single Responsibility**: Each module has a single, well-defined purpose
- **Open/Closed**: Extensible through interfaces without modification
- **Liskov Substitution**: Implementations are interchangeable
- **Interface Segregation**: Focused, minimal interfaces
- **Dependency Inversion**: Depends on abstractions, not concretions

## 🔒 Security

### Data Protection
- **BLAKE3 Hashing**: Cryptographic integrity verification
- **Content Verification**: End-to-end data integrity checks
- **Secure Protocols**: TLS 1.3 with QUIC, optional TLS with TCP
- **Access Control**: Configurable authentication and authorization

### Best Practices
- **Regular Updates**: Keep JustSyncIt updated for security patches
- **Network Security**: Use secure networks for remote backups
- **Storage Security**: Proper permissions and encryption for sensitive data
- **Regular Verification**: Periodic integrity checks of backups

## 🐳 Docker Support

### Container Images
- **Multi-Architecture**: Support for x86_64 and ARM64
- **Optimized Layers**: Minimal image size with security scanning
- **Health Checks**: Built-in health monitoring
- **Volume Support**: Persistent data storage through volumes

### Docker Compose
```yaml
version: '3.8'

services:
  justsyncit:
    image: justsyncit/app:latest
    ports:
      - "8080:8080"
    volumes:
      - justsyncit-data:/data
      - ./config:/opt/justsyncit/config
    environment:
      - JAVA_OPTS=-Xmx2g
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "java", "-jar", "/opt/justsyncit/justsyncit.jar", "server", "status"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  justsyncit-data:
```

## 🧪 Development

### Build Commands

```bash
# Quick development build
./gradlew devBuild

# Test build with quality checks
./gradlew testBuild

# Full release build
./gradlew releaseBuild
```

### Testing and Quality

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport

# Run performance benchmarks
./gradlew jmh

# Run security checks
./gradlew dependencyCheckAggregate

# Run code quality checks
./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

### Development Setup

```bash
# Clone the repository
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit

# Install dependencies
./gradlew downloadDependencies

# Run development build
./gradlew devBuild

# Run tests
./gradlew test
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](docs/guides/contributing.md) for details.

### Code Quality
- **Checkstyle**: Enforces coding standards and formatting
- **SpotBugs**: Static analysis for bug detection
- **JUnit 5**: Comprehensive unit testing
- **JaCoCo**: Code coverage reporting
- **OWASP Dependency Check**: Security vulnerability scanning

## 📊 Monitoring

### Built-in Metrics
JustSyncIt provides comprehensive monitoring capabilities:

- **Performance Metrics**: Throughput, latency, and resource usage
- **Health Checks**: Storage integrity and service availability
- **Logging**: Structured logging with configurable levels
- **Export Support**: Prometheus-compatible metrics export

### External Monitoring
```bash
# Enable metrics export
export JUSTSYNCIT_METRICS_EXPORT=prometheus
export JUSTSYNCIT_METRICS_PORT=9090

# Start with monitoring
java -jar justsyncit.jar server start --monitoring
```

## 🐛 Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Permission denied | Check file permissions and run with appropriate user |
| Out of memory | Increase heap size with `-Xmx` option |
| Connection refused | Ensure server is running and check firewall |
| Checksum verification failed | Check storage media and retry backup |

### Getting Help

1. **Command Help**: Use `--help` with any command
2. **Verbose Logging**: Use `--verbose` for detailed output
3. **Log Files**: Check logs in `~/.justsyncit/logs/`
4. **Community**: Visit [GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)
5. **Issues**: [Report bugs and request features](https://github.com/carabistouflette/justsyncit/issues)

## 📋 Requirements

### System Requirements

- **Operating System**: Linux (Ubuntu 20.04+, CentOS 8+, or equivalent)
- **Java**: Java 21 or higher
- **Memory**: Minimum 512MB RAM, recommended 2GB+ for large operations
- **Disk**: Sufficient space for backups and temporary files
- **Network**: For remote operations (optional)

### Recommended Configuration

- **Operating System**: Ubuntu 22.04+ or CentOS 9+
- **Java**: Java 21 with latest updates
- **Memory**: 4GB+ RAM for large operations
- **Storage**: SSD storage for better performance
- **Network**: Gigabit Ethernet for remote backups

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [BLAKE3](https://github.com/BLAKE3-team/BLAKE3) for the fast cryptographic hash function
- [JUnit 5](https://junit.org/junit5/) for testing framework
- [Gradle](https://gradle.org/) for build automation
- [Docker](https://www.docker.com/) for containerization support

## 🗺️ Roadmap

### Planned Features
- [ ] Real-time synchronization
- [ ] Web UI for management
- [ ] Cloud storage integrations (AWS S3, Google Cloud, Azure)
- [ ] Advanced encryption options
- [ ] Plugin system for extensibility
- [ ] GUI application

### Future Improvements
- [ ] Enhanced compression algorithms
- [ ] Machine learning for deduplication
- [ ] Advanced scheduling and automation
- [ ] Multi-platform support (Windows, macOS)

## 📞 Support

### Getting Help
- **Documentation**: [JustSyncIt Documentation](docs/)
- **Issues**: [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)
- **Discussions**: [GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)

### Reporting Issues
When reporting issues, please include:
1. JustSyncIt version
2. Java version
3. Operating system and version
4. Steps to reproduce
5. Expected vs actual behavior
6. Any error messages or logs

### Community
- **GitHub**: [github.com/carabistouflette/justsyncit](https://github.com/carabistouflette/justsyncit)
- **Docker Hub**: [hub.docker.com/r/justsyncit/app](https://hub.docker.com/r/justsyncit/app)
- **Releases**: [GitHub Releases](https://github.com/carabistouflette/justsyncit/releases)

---

**JustSyncIt** - Modern backup solution for reliable data protection.

For detailed documentation and guides, visit the [docs/](docs/) directory.