# JustSyncIt User Guide

This guide provides comprehensive instructions for installing, configuring, and using JustSyncIt for your backup needs.

## 1. Installation

### Prerequisites
*   **Operating System**: Linux (Ubuntu 20.04+, CentOS 8+, or equivalent)
*   **Java Runtime**: Java 21 or higher
*   **Memory**: Minimum 512MB RAM (2GB+ recommended for large datasets)

### Installation Methods

#### Option A: Standalone JAR (Universal)
1.  Download the latest `justsyncit.jar` from the releases page.
2.  Make it executable:
    ```bash
    chmod +x justsyncit.jar
    ```
3.  Run it:
    ```bash
    java -jar justsyncit.jar --help
    ```

#### Option B: Docker
1.  Pull the image:
    ```bash
    docker pull justsyncit/app:latest
    ```
2.  Run a container:
    ```bash
    docker run -v $(pwd)/data:/data justsyncit/app:latest backup /data
    ```

## 2. Core Workflows

### Creating a Backup
The primary function of JustSyncIt is to create secure, deduplicated backups.

**Basic Usage:**
```bash
java -jar justsyncit.jar backup /path/to/source
```

By default, this will:
*   Scan the directory.
*   Deduplicate content using FastCDC.
*   Store data in the local content store (default: `~/.justsyncit/storage`).
*   Verify integrity after backup.

**Common Options:**
*   `--include-hidden`: Backup hidden files and directories.
*   `--no-verify`: Skip the post-backup integrity check (faster, but less safe).
*   `--chunk-size <bytes>`: Manually set the target chunk size (default: dynamic).

### Listing Snapshots
View your backup history:

```bash
java -jar justsyncit.jar snapshots list
```
Use `--verbose` for more details, including total size and file count.

### Restoring Files
Restore data from a specific point in time.

1.  Find the snapshot ID:
    ```bash
    java -jar justsyncit.jar snapshots list
    ```
2.  Run the restore command:
    ```bash
    java -jar justsyncit.jar restore <snapshot-id> /path/to/restore/destination
    ```

## 3. Network Backups

JustSyncIt supports backing up to a remote server.

### Server Setup (Target)
On the machine that will receive backups:

```bash
java -jar justsyncit.jar server start 8080
```

### Client Setup (Source)
To backup directly to the remote server:

```bash
java -jar justsyncit.jar backup /path/to/source --remote --server <server-ip>:8080
```

**Transport Protocols:**
*   **TCP** (Default): Reliable, standard transport.
*   **QUIC**: High-performance, low-latency transport (requires UDP port access). Use `--transport QUIC`.

## 4. Configuration

JustSyncIt is designed to work with sensible defaults, but it can be fully configured via CLI flags or environment variables.

See the [CLI Reference](CLI_Reference.md) for a complete list of flags.
