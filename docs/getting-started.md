# JustSyncIt Getting Started Guide

## Table of Contents

- [System Requirements](#system-requirements)
- [Installation](#installation)
- [Quick Start Tutorial](#quick-start-tutorial)
- [Basic Usage](#basic-usage)
- [Configuration](#configuration)
- [Next Steps](#next-steps)

## System Requirements

### Minimum Requirements

- **Operating System**: Linux (Ubuntu 20.04+, CentOS 8+, Debian 10+, or equivalent)
- **Java**: Java 21 or higher
- **Memory**: 512MB RAM minimum, 2GB+ recommended
- **Disk Space**: Sufficient space for backups and temporary files
- **Network**: Internet connection for remote operations (optional)

### Recommended Requirements

- **Operating System**: Ubuntu 22.04+ or CentOS 9+
- **Java**: Java 21 with latest updates
- **Memory**: 4GB+ RAM for large operations
- **Storage**: SSD storage for better performance
- **Network**: Gigabit Ethernet for remote backups

### Supported Platforms

JustSyncIt is optimized for Linux environments:
- ✅ Ubuntu 20.04, 22.04+
- ✅ CentOS/RHEL 8, 9+
- ✅ Debian 10, 11+
- ✅ Fedora 35+
- ✅ Arch Linux
- ❌ Windows (not supported)
- ❌ macOS (not supported)

## Installation

### Step 1: Install Java 21

#### Ubuntu/Debian
```bash
# Update package index
sudo apt update

# Install OpenJDK 21
sudo apt install openjdk-21-jdk

# Verify installation
java -version
```

#### CentOS/RHEL/Fedora
```bash
# For CentOS/RHEL
sudo yum install java-21-openjdk-devel

# For Fedora
sudo dnf install java-21-openjdk-devel

# Verify installation
java -version
```

#### Using SDKMAN (Recommended for developers)
```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh

# Install Java 21
sdk install java 21.0.2-open

# Set as default
sdk default java 21.0.2-open

# Verify
java -version
```

### Step 2: Download JustSyncIt

#### Option A: Download Pre-built JAR (Recommended)
```bash
# Create directory for JustSyncIt
mkdir -p ~/justsyncit
cd ~/justsyncit

# Download latest release
wget https://github.com/carabistouflette/justsyncit/releases/latest/download/justsyncit.jar

# Make executable
chmod +x justsyncit.jar

# Verify installation
java -jar justsyncit.jar --help
```

#### Option B: Build from Source
```bash
# Install Git if not present
sudo apt install git  # Ubuntu/Debian
sudo yum install git  # CentOS/RHEL

# Clone repository
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit

# Build project
./gradlew build

# Copy JAR to convenient location
cp build/libs/justsyncit-*.jar ~/justsyncit/justsyncit.jar
cd ~/justsyncit

# Verify installation
java -jar justsyncit.jar --help
```

#### Option C: Docker Installation
```bash
# Pull Docker image
docker pull justsyncit/app:latest

# Create volume for data
docker volume create justsyncit-data

# Run container
docker run -d \
  --name justsyncit \
  -v justsyncit-data:/data \
  -p 8080:8080 \
  justsyncit/app:latest
```

### Step 3: Verify Installation

```bash
# Check JustSyncIt version
java -jar justsyncit.jar --help

# You should see help output with available commands
```

### Step 4: Create Configuration Directory

```bash
# Create configuration directory
mkdir -p ~/.justsyncit

# Create data directory for backups
mkdir -p ~/.justsyncit/data

# Create logs directory
mkdir -p ~/.justsyncit/logs
```

## Quick Start Tutorial

Let's create your first backup step by step.

### Step 1: Create Test Data

```bash
# Create test directory structure
mkdir -p ~/justsyncit-test/documents
mkdir -p ~/justsyncit-test/photos

# Create some test files
echo "Hello, JustSyncIt!" > ~/justsyncit-test/documents/readme.txt
echo "This is an important document." > ~/justsyncit-test/documents/important.txt
echo "Configuration file" > ~/justsyncit-test/documents/config.yaml

# Create some binary files
dd if=/dev/urandom of=~/justsyncit-test/photos/test1.jpg bs=1024 count=100
dd if=/dev/urandom of=~/justsyncit-test/photos/test2.png bs=1024 count=50

# List test data
find ~/justsyncit-test -type f
```

### Step 2: Run Your First Backup

```bash
# Navigate to JustSyncIt directory
cd ~/justsyncit

# Backup the test directory
java -jar justsyncit.jar backup ~/justsyncit-test
```

You should see output similar to:
```
Starting backup of: /home/user/justsyncit-test
Options: BackupOptions{symlinkStrategy=SKIP, includeHiddenFiles=false, verifyIntegrity=true, chunkSize=65536, remoteBackup=false}

Backup completed successfully!
Files processed: 5
Total bytes: 204800
Chunks created: 5
Integrity verified: true
```

### Step 3: List Your Snapshots

```bash
# List all snapshots
java -jar justsyncit.jar snapshots list
```

Output:
```
ID              Name                    Created              Files    Size
abc123def456   Backup 2023-12-01      2023-12-01 10:30:15  5        200.0 KB
```

### Step 4: Examine Snapshot Details

```bash
# Get detailed information about your snapshot
java -jar justsyncit.jar snapshots info abc123def456 --show-files
```

Output:
```
Snapshot ID: abc123def456
Name: Backup 2023-12-01
Description: null
Created: 2023-12-01 10:30:15 UTC
Total Files: 5
Total Size: 200.0 KB

Files:
/documents/readme.txt
/documents/important.txt
/documents/config.yaml
/photos/test1.jpg
/photos/test2.png
```

### Step 5: Verify Backup Integrity

```bash
# Verify the snapshot
java -jar justsyncit.jar snapshots verify abc123def456
```

Output:
```
Verifying snapshot: abc123def456
Checking 5 files...
Checking 5 chunks...
Verification completed successfully!
All files and chunks verified.
```

### Step 6: Restore Your Backup

```bash
# Create restore directory
mkdir -p ~/justsyncit-restore

# Restore the snapshot
java -jar justsyncit.jar restore abc123def456 ~/justsyncit-restore
```

Output:
```
Starting restore of snapshot: abc123def456
Target directory: /home/user/justsyncit-restore
Options: RestoreOptions{overwriteExisting=false, backupExisting=false, verifyIntegrity=true, preserveAttributes=true}

Restore completed successfully!
Files restored: 5
Files skipped: 0
Files with errors: 0
Total bytes: 204800
Integrity verified: true
```

### Step 7: Verify Restored Data

```bash
# Check restored files
find ~/justsyncit-restore -type f

# Verify content
cat ~/justsyncit-restore/documents/readme.txt
cat ~/justsyncit-restore/documents/important.txt

# Compare with original
diff -r ~/justsyncit-test ~/justsyncit-restore
```

The diff command should produce no output, indicating the files are identical.

## Basic Usage

### Common Backup Operations

#### Basic Backup
```bash
# Simple backup
java -jar justsyncit.jar backup ~/documents
```

#### Backup with Options
```bash
# Backup with hidden files and custom chunk size
java -jar justsyncit.jar backup ~/documents \
  --include-hidden \
  --chunk-size 1048576 \
  --verify-integrity
```

#### Backup Following Symlinks
```bash
# Backup following symbolic links
java -jar justsyncit.jar backup ~/documents \
  --follow-symlinks \
  --include-hidden
```

### Snapshot Management

#### List Snapshots
```bash
# Basic list
java -jar justsyncit.jar snapshots list

# Detailed list
java -jar justsyncit.jar snapshots list --verbose

# Sort by size
java -jar justsyncit.jar snapshots list --sort-by-size
```

#### Snapshot Information
```bash
# Get snapshot details
java -jar justsyncit.jar snapshots info <snapshot-id>

# Show files in snapshot
java -jar justsyncit.jar snapshots info <snapshot-id> --show-files
```

#### Delete Snapshots
```bash
# Interactive delete
java -jar justsyncit.jar snapshots delete <snapshot-id>

# Force delete
java -jar justsyncit.jar snapshots delete <snapshot-id> --force
```

### Restore Operations

#### Basic Restore
```bash
# Restore to directory
java -jar justsyncit.jar restore <snapshot-id> ~/restore
```

#### Restore with Options
```bash
# Restore with overwrite
java -jar justsyncit.jar restore <snapshot-id> ~/restore --overwrite

# Selective restore
java -jar justsyncit.jar restore <snapshot-id> ~/restore --include "*.txt"

# Restore excluding files
java -jar justsyncit.jar restore <snapshot-id> ~/restore --exclude "*.tmp"
```

### Server Operations

#### Start Server
```bash
# Start server on default port 8080
java -jar justsyncit.jar server start

# Start server on custom port
java -jar justsyncit.jar server start --port 9090

# Start server in daemon mode
java -jar justsyncit.jar server start --daemon --quiet
```

#### Check Server Status
```bash
# Basic status
java -jar justsyncit.jar server status

# Detailed status
java -jar justsyncit.jar server status --verbose

# JSON status
java -jar justsyncit.jar server status --json
```

#### Stop Server
```bash
# Graceful stop
java -jar justsyncit.jar server stop

# Force stop
java -jar justsyncit.jar server stop --force
```

### Remote Operations

#### Remote Backup
```bash
# Backup to remote server
java -jar justsyncit.jar backup ~/documents \
  --remote \
  --server backup.example.com:8080

# Remote backup with QUIC
java -jar justsyncit.jar backup ~/documents \
  --remote \
  --server backup.example.com:8080 \
  --transport QUIC
```

#### Remote Restore
```bash
# Restore from remote server
java -jar justsyncit.jar restore <snapshot-id> ~/restore \
  --remote \
  --server backup.example.com:8080
```

## Configuration

JustSyncIt can be configured through command-line options, environment variables, and configuration files.

### Command-Line Options

Most configuration is done through command-line options:

```bash
# Example with multiple options
java -jar justsyncit.jar backup ~/data \
  --include-hidden \
  --chunk-size 1048576 \
  --verify-integrity \
  --verbose
```

### Environment Variables

You can set default options using environment variables:

```bash
# Set default chunk size
export JUSTSYNCIT_CHUNK_SIZE=1048576

# Set default transport
export JUSTSYNCIT_TRANSPORT=QUIC

# Set default server port
export JUSTSYNCIT_SERVER_PORT=9090

# Use in commands
java -jar justsyncit.jar server start  # Will use port 9090
```

### Configuration File

Create a configuration file at `~/.justsyncit/config.yaml`:

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
  index_directory: ~/.justsyncit/index
  gc_enabled: true
  gc_interval: 86400000  # 24 hours

logging:
  level: INFO
  file: ~/.justsyncit/logs/justsyncit.log
  max_size: 10485760  # 10MB
  max_files: 5
```

### Performance Tuning

For optimal performance, consider these settings:

#### Memory Settings
```bash
# Increase heap size for large operations
export JAVA_OPTS="-Xmx4g -Xms2g"

# Use G1GC for better performance
export JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
```

#### Chunk Size Optimization
```bash
# For documents (many small files)
java -jar justsyncit.jar backup ~/documents --chunk-size 262144

# For media files (large files)
java -jar justsyncit.jar backup ~/media --chunk-size 4194304

# For mixed content
java -jar justsyncit.jar backup ~/mixed --chunk-size 1048576
```

#### Network Optimization
```bash
# Use QUIC for better performance
java -jar justsyncit.jar backup ~/data --remote --server backup.example.com:8080 --transport QUIC

# Enable compression
java -jar justsyncit.jar transfer <snapshot-id> --to backup.example.com:8080 --compress
```

## Next Steps

Congratulations! You've successfully:

1. ✅ Installed JustSyncIt
2. ✅ Created your first backup
3. ✅ Restored data from backup
4. ✅ Learned basic operations

### Continue Learning

Now that you have the basics down, explore these topics:

1. **Advanced Backup Strategies**
   - [CLI Reference](cli-reference.md) - Complete command reference
   - [Snapshot Management Guide](snapshot-management.md) - Advanced snapshot operations
   - [Technical Guide](technical-guide.md) - Technical architecture and implementation details

2. **Network Operations**
   - [Network Operations Guide](network-operations.md) - Remote backup setup and optimization
   - [Performance Guide](performance-guide.md) - Tuning and optimization tips

3. **Technical Details**
   - [Technical Guide](technical-guide.md) - Architecture and implementation details
   - [Troubleshooting Guide](troubleshooting.md) - Common issues and solutions

### Common Next Steps

#### Set Up Automated Backups

```bash
# Add to crontab for daily backup at 2 AM
crontab -e
# Add this line:
0 2 * * * java -jar ~/justsyncit/justsyncit.jar backup ~/important-data --include-hidden
```

#### Set Up Remote Backup Server

```bash
# Create systemd service for server
sudo tee /etc/systemd/system/justsyncit-server.service > /dev/null <<EOF
[Unit]
Description=JustSyncIt Backup Server
After=network.target

[Service]
Type=simple
User=backup
ExecStart=/usr/bin/java -jar /opt/justsyncit/justsyncit.jar server start --daemon --quiet
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
sudo systemctl enable justsyncit-server
sudo systemctl start justsyncit-server
```

#### Monitor Backup Health

```bash
# Create backup verification script
cat > ~/backup-verify.sh <<'EOF'
#!/bin/bash
BACKUP_DIR="$1"
SNAPSHOT_ID=$(java -jar ~/justsyncit/justsyncit.jar backup "$BACKUP_DIR" --quiet | grep "Snapshot ID:" | cut -d' ' -f3)

if java -jar ~/justsyncit/justsyncit.jar snapshots verify "$SNAPSHOT_ID" --quiet; then
    echo "✅ Backup verified: $SNAPSHOT_ID"
else
    echo "❌ Backup verification failed: $SNAPSHOT_ID"
    exit 1
fi
EOF

chmod +x ~/backup-verify.sh

# Use in scripts
~/backup-verify.sh ~/important-data
```

### Resources

- **Documentation**: [JustSyncIt Documentation](index.md)
- **GitHub Repository**: [github.com/carabistouflette/justsyncit](https://github.com/carabistouflette/justsyncit)
- **Issues**: [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)
- **Discussions**: [GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)

### Need Help?

If you encounter any issues:

1. Check the [Troubleshooting Guide](troubleshooting.md)
2. Search existing [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)
3. Create a new issue with detailed information
4. Join the community discussions

Welcome to JustSyncIt! 🎉