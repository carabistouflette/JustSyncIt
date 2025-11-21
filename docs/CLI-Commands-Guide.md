# JustSyncIt CLI Commands Guide

This guide provides comprehensive documentation for all JustSyncIt CLI commands, including the new snapshot management and network operation commands.

## Table of Contents

- [Basic Commands](#basic-commands)
- [Backup Commands](#backup-commands)
- [Restore Commands](#restore-commands)
- [Snapshot Management Commands](#snapshot-management-commands)
- [Network Operation Commands](#network-operation-commands)
- [Hash Commands](#hash-commands)
- [Verify Commands](#verify-commands)

## Basic Commands

### help
Display help information for commands.

```bash
justsyncit help [command]
```

**Examples:**
```bash
justsyncit help                    # Show all available commands
justsyncit help backup             # Show help for backup command
justsyncit help snapshots list      # Show help for snapshots list command
```

## Backup Commands

### backup
Backup a directory to the content store.

```bash
justsyncit backup <source-dir> [options]
```

**Arguments:**
- `source-dir`: Path to the directory to backup

**Options:**
- `--follow-symlinks`: Follow symbolic links instead of preserving them
- `--skip-symlinks`: Skip symbolic links entirely
- `--include-hidden`: Include hidden files and directories
- `--verify-integrity`: Verify integrity after backup (default)
- `--no-verify`: Skip integrity verification after backup
- `--chunk-size SIZE`: Set chunk size in bytes (default: 64KB)
- `--remote`: Enable remote backup to server
- `--server HOST:PORT`: Remote server address for remote backup
- `--transport TYPE`: Transport protocol (TCP|QUIC, default: TCP)
- `--help`: Show help message

**Examples:**
```bash
justsyncit backup /home/user/documents
justsyncit backup /home/user/documents --include-hidden --chunk-size 1048576
justsyncit backup /home/user/documents --follow-symlinks --no-verify
justsyncit backup /home/user/documents --remote --server 192.168.1.100:8080
justsyncit backup /home/user/documents --remote --server backup.example.com:8080 --transport QUIC
```

## Restore Commands

### restore
Restore a directory from a snapshot.

```bash
justsyncit restore <snapshot-id> <target-dir> [options]
```

**Arguments:**
- `snapshot-id`: ID of the snapshot to restore
- `target-dir`: Directory to restore files to

**Options:**
- `--overwrite`: Overwrite existing files
- `--backup-existing`: Backup existing files before overwriting
- `--no-verify`: Skip integrity verification after restore
- `--no-preserve-attributes`: Don't preserve file attributes
- `--include PATTERN`: Only restore files matching pattern
- `--exclude PATTERN`: Skip files matching pattern
- `--remote`: Enable remote restore from server
- `--server HOST:PORT`: Remote server address for remote restore
- `--transport TYPE`: Transport protocol (TCP|QUIC, default: TCP)
- `--help`: Show help message

**Examples:**
```bash
justsyncit restore abc123-def456 /home/user/restore
justsyncit restore abc123-def456 /home/user/restore --overwrite
justsyncit restore abc123-def456 /home/user/restore --include "*.txt" --exclude "*.tmp"
justsyncit restore abc123-def456 /home/user/restore --remote --server 192.168.1.100:8080
justsyncit restore abc123-def456 /home/user/restore --remote --server backup.example.com:8080 --transport QUIC
```

## Snapshot Management Commands

The `snapshots` command group provides comprehensive snapshot management capabilities.

### snapshots list
List all available snapshots with metadata.

```bash
justsyncit snapshots list [options]
```

**Options:**
- `--verbose, -v`: Show detailed information including descriptions
- `--sort-by-size`: Sort snapshots by size (largest first)
- `--sort-by-date`: Sort snapshots by creation date (newest first, default)
- `--help`: Show help message

**Examples:**
```bash
justsyncit snapshots list
justsyncit snapshots list --verbose
justsyncit snapshots list --sort-by-size
justsyncit snapshots list --verbose --sort-by-size
```

### snapshots info
Show detailed information about a specific snapshot.

```bash
justsyncit snapshots info <snapshot-id> [options]
```

**Arguments:**
- `snapshot-id`: ID of the snapshot to inspect

**Options:**
- `--show-files`: Show list of files in the snapshot
- `--file-limit NUM`: Limit number of files to show (default: 20)
- `--no-statistics`: Don't show file statistics
- `--help`: Show help message

**Examples:**
```bash
justsyncit snapshots info abc123-def456
justsyncit snapshots info abc123-def456 --show-files
justsyncit snapshots info abc123-def456 --show-files --file-limit 50
justsyncit snapshots info abc123-def456 --no-statistics
```

### snapshots delete
Delete a specific snapshot.

```bash
justsyncit snapshots delete <snapshot-id> [options]
```

**Arguments:**
- `snapshot-id`: ID of the snapshot to delete

**Options:**
- `--force, -f`: Delete without confirmation
- `--no-confirm`: Skip confirmation prompt
- `--help`: Show help message

**Notes:**
- This operation is irreversible
- File chunks are only deleted if not referenced by other snapshots
- Use with caution as deleted snapshots cannot be recovered

**Examples:**
```bash
justsyncit snapshots delete abc123-def456
justsyncit snapshots delete abc123-def456 --force
justsyncit snapshots delete abc123-def456 --no-confirm
```

### snapshots verify
Verify integrity of a snapshot.

```bash
justsyncit snapshots verify <snapshot-id> [options]
```

**Arguments:**
- `snapshot-id`: ID of the snapshot to verify

**Options:**
- `--no-chunk-verify`: Skip chunk integrity verification
- `--no-file-hash-verify`: Skip file hash verification
- `--quiet, -q`: Quiet mode with minimal output
- `--no-progress`: Don't show progress indicator
- `--help`: Show help message

**Notes:**
- Verification checks both chunk and file integrity
- This can be time-consuming for large snapshots
- Use `--no-chunk-verify` for faster verification

**Examples:**
```bash
justsyncit snapshots verify abc123-def456
justsyncit snapshots verify abc123-def456 --quiet
justsyncit snapshots verify abc123-def456 --no-chunk-verify
justsyncit snapshots verify abc123-def456 --no-progress
```

## Network Operation Commands

The `server` command group provides server management capabilities.

### server start
Start a backup server to accept remote backup requests.

```bash
justsyncit server start [options]
```

**Options:**
- `--port PORT`: Port to listen on (default: 8080)
- `--transport TYPE`: Transport protocol (TCP|QUIC, default: TCP)
- `--daemon, -d`: Run in daemon mode (background)
- `--quiet, -q`: Quiet mode with minimal output
- `--help`: Show help message

**Examples:**
```bash
justsyncit server start
justsyncit server start --port 9090
justsyncit server start --transport QUIC
justsyncit server start --port 9090 --transport QUIC --daemon
justsyncit server start --quiet
```

### server stop
Stop a running backup server.

```bash
justsyncit server stop [options]
```

**Options:**
- `--force`: Force stop even if transfers are in progress
- `--quiet, -q`: Quiet mode with minimal output
- `--help`: Show help message

**Examples:**
```bash
justsyncit server stop
justsyncit server stop --force
justsyncit server stop --quiet
```

### server status
Show server status and configuration.

```bash
justsyncit server status [options]
```

**Options:**
- `--verbose, -v`: Show detailed status information
- `--json`: Output status in JSON format
- `--help`: Show help message

**Examples:**
```bash
justsyncit server status
justsyncit server status --verbose
justsyncit server status --json
justsyncit server status --verbose --json
```

### transfer
Transfer a snapshot to another server.

```bash
justsyncit transfer <snapshot-id> --to <server> [options]
```

**Arguments:**
- `snapshot-id`: ID of the snapshot to transfer
- `--to SERVER`: Target server address (host:port) [required]

**Options:**
- `--transport TYPE`: Transport protocol (TCP|QUIC, default: TCP)
- `--no-verify`: Skip integrity verification after transfer
- `--compress`: Enable compression during transfer
- `--resume`: Resume interrupted transfer
- `--quiet, -q`: Quiet mode with minimal output
- `--help`: Show help message

**Examples:**
```bash
justsyncit transfer abc123-def456 --to 192.168.1.100:8080
justsyncit transfer abc123-def456 --to backup.example.com:8080 --transport QUIC
justsyncit transfer abc123-def456 --to 192.168.1.100:8080 --compress
justsyncit transfer abc123-def456 --to backup.example.com:8080 --no-verify
```

### sync
Synchronize local directory with remote server.

```bash
justsyncit sync <local-path> <remote-server> [options]
```

**Arguments:**
- `local-path`: Local directory to synchronize
- `remote-server`: Remote server address (host:port)

**Options:**
- `--transport TYPE`: Transport protocol (TCP|QUIC, default: TCP)
- `--one-way`: One-way sync (local to remote only)
- `--delete-extra`: Delete files that don't exist on source
- `--no-verify`: Skip integrity verification
- `--dry-run`: Show what would be synchronized without making changes
- `--quiet, -q`: Quiet mode with minimal output
- `--help`: Show help message

**Examples:**
```bash
justsyncit sync /home/user/documents 192.168.1.100:8080
justsyncit sync /home/user/documents backup.example.com:8080 --transport QUIC
justsyncit sync /home/user/documents 192.168.1.100:8080 --one-way
justsyncit sync /home/user/documents backup.example.com:8080 --delete-extra
justsyncit sync /home/user/documents 192.168.1.100:8080 --dry-run
```

## Hash Commands

### hash
Generate hash values for files and directories.

```bash
justsyncit hash <path> [options]
```

**Arguments:**
- `path`: Path to file or directory to hash

**Options:**
- `--algorithm ALG`: Hash algorithm (BLAKE3, SHA256)
- `--recursive`: Hash directories recursively
- `--output FORMAT`: Output format (hex, base64)
- `--help`: Show help message

**Examples:**
```bash
justsyncit hash /home/user/document.txt
justsyncit hash /home/user/documents --recursive
justsyncit hash /home/user/document.txt --algorithm SHA256
```

## Verify Commands

### verify
Verify file integrity using hash values.

```bash
justsyncit verify <path> <expected-hash> [options]
```

**Arguments:**
- `path`: Path to file to verify
- `expected-hash`: Expected hash value

**Options:**
- `--algorithm ALG`: Hash algorithm to use (BLAKE3, SHA256)
- `--help`: Show help message

**Examples:**
```bash
justsyncit verify /home/user/document.txt abc123def456
justsyncit verify /home/user/document.txt abc123def456 --algorithm BLAKE3
```

## Common Patterns

### Server Address Format
All server addresses use the format `host:port`:
- `192.168.1.100:8080` - IPv4 address
- `backup.example.com:8080` - Domain name
- `[2001:db8::1]:8080` - IPv6 address

### Transport Types
- `TCP` - Reliable TCP transport (default)
- `QUIC` - Modern UDP-based transport with better performance

### File Size Formats
The CLI displays file sizes in human-readable format:
- `B` - Bytes
- `KB` - Kilobytes
- `MB` - Megabytes  
- `GB` - Gigabytes

### Progress Indicators
Long-running operations show progress:
- Transfer operations show bytes transferred and percentage
- Verification operations show files/chunks processed
- Server operations show connection and transfer statistics

## Error Handling

All commands follow consistent error handling patterns:
1. Validate required arguments
2. Parse and validate options
3. Show descriptive error messages
4. Return appropriate exit codes
5. Provide help for error recovery

## Getting Help

Use the `help` command to get assistance:
```bash
justsyncit help                    # Show all commands
justsyncit help <command>          # Show help for specific command
justsyncit <command> --help         # Show command-specific help
```

For more detailed information about any command, use:
```bash
justsyncit help <command>