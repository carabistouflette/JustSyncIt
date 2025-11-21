# JustSyncIt CLI Reference

## Table of Contents

- [Global Options](#global-options)
- [Command Overview](#command-overview)
- [Backup Commands](#backup-commands)
- [Restore Commands](#restore-commands)
- [Snapshot Management Commands](#snapshot-management-commands)
- [Network Operation Commands](#network-operation-commands)
- [Hash Commands](#hash-commands)
- [Verify Commands](#verify-commands)
- [Server Commands](#server-commands)
- [Transfer Commands](#transfer-commands)
- [Sync Commands](#sync-commands)
- [Help Command](#help-command)
- [Exit Codes](#exit-codes)
- [Common Patterns](#common-patterns)

## Global Options

These options can be used with any JustSyncIt command:

| Option | Description | Example |
|--------|-------------|----------|
| `--verbose` | Enable verbose logging with detailed output | `--verbose` |
| `--quiet` | Enable quiet mode with minimal output | `--quiet` |
| `--log-level=<LEVEL>` | Set specific log level (TRACE, DEBUG, INFO, WARN, ERROR) | `--log-level=DEBUG` |
| `--help` | Show help information for the command | `--help` |

### Log Levels

- **TRACE**: Most detailed logging, including all internal operations
- **DEBUG**: Debug information for troubleshooting
- **INFO**: General information messages (default)
- **WARN**: Warning messages only
- **ERROR**: Error messages only

## Command Overview

JustSyncIt provides a comprehensive command-line interface with the following command groups:

### Core Commands
- `backup` - Create backups of directories
- `restore` - Restore files from snapshots
- `hash` - Generate hash values for files and directories
- `verify` - Verify file integrity using hash values

### Snapshot Management
- `snapshots list` - List all available snapshots
- `snapshots info` - Show detailed information about a snapshot
- `snapshots delete` - Delete a snapshot
- `snapshots verify` - Verify snapshot integrity

### Network Operations
- `server start` - Start a backup server
- `server stop` - Stop a running server
- `server status` - Show server status
- `transfer` - Transfer snapshots between servers
- `sync` - Synchronize directories with remote servers

## Backup Commands

### backup

Backup a directory to the content store.

#### Syntax
```bash
justsyncit backup <source-dir> [options]
```

#### Arguments
- `source-dir`: Path to the directory to backup

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--follow-symlinks` | Follow symbolic links instead of preserving them | false |
| `--skip-symlinks` | Skip symbolic links entirely | true |
| `--include-hidden` | Include hidden files and directories | false |
| `--verify-integrity` | Verify integrity after backup | true |
| `--no-verify` | Skip integrity verification after backup | false |
| `--chunk-size SIZE` | Set chunk size in bytes | 65536 (64KB) |
| `--remote` | Enable remote backup to server | false |
| `--server HOST:PORT` | Remote server address for remote backup | - |
| `--transport TYPE` | Transport protocol (TCP|QUIC) | TCP |
| `--help` | Show help message | - |

#### Examples

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
justsyncit backup /home/user/documents --chunk-size 1048576
```

**Backup following symlinks:**
```bash
justsyncit backup /home/user/documents --follow-symlinks --include-hidden
```

**Remote backup:**
```bash
justsyncit backup /home/user/documents --remote --server 192.168.1.100:8080
```

**Remote backup with QUIC:**
```bash
justsyncit backup /home/user/documents --remote --server backup.example.com:8080 --transport QUIC
```

#### Output
```
Starting backup of: /home/user/documents
Options: BackupOptions{...}

Backup completed successfully!
Files processed: 156
Total bytes: 234567890
Chunks created: 1234
Integrity verified: true
```

## Restore Commands

### restore

Restore a directory from a snapshot.

#### Syntax
```bash
justsyncit restore <snapshot-id> <target-dir> [options]
```

#### Arguments
- `snapshot-id`: ID of the snapshot to restore
- `target-dir`: Directory to restore files to

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--overwrite` | Overwrite existing files | false |
| `--backup-existing` | Backup existing files before overwriting | false |
| `--no-verify` | Skip integrity verification after restore | false |
| `--no-preserve-attributes` | Don't preserve file attributes | false |
| `--include PATTERN` | Only restore files matching pattern | - |
| `--exclude PATTERN` | Skip files matching pattern | - |
| `--remote` | Enable remote restore from server | false |
| `--server HOST:PORT` | Remote server address for remote restore | - |
| `--transport TYPE` | Transport protocol (TCP|QUIC) | TCP |
| `--help` | Show help message | - |

#### Examples

**Basic restore:**
```bash
justsyncit restore abc123-def456 /home/user/restore
```

**Restore with overwrite:**
```bash
justsyncit restore abc123-def456 /home/user/restore --overwrite
```

**Selective restore:**
```bash
justsyncit restore abc123-def456 /home/user/restore --include "*.txt"
justsyncit restore abc123-def456 /home/user/restore --exclude "*.tmp"
```

**Remote restore:**
```bash
justsyncit restore abc123-def456 /home/user/restore --remote --server 192.168.1.100:8080
```

#### Output
```
Starting restore of snapshot: abc123-def456
Target directory: /home/user/restore
Options: RestoreOptions{...}

Restore completed successfully!
Files restored: 156
Files skipped: 0
Files with errors: 0
Total bytes: 234567890
Integrity verified: true
```

## Snapshot Management Commands

### snapshots list

List all available snapshots with metadata.

#### Syntax
```bash
justsyncit snapshots list [options]
```

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--verbose, -v` | Show detailed information including descriptions | false |
| `--sort-by-size` | Sort snapshots by size (largest first) | false |
| `--sort-by-date` | Sort snapshots by creation date (newest first) | true |
| `--help` | Show help message | - |

#### Examples

**Basic list:**
```bash
justsyncit snapshots list
```

**Detailed list:**
```bash
justsyncit snapshots list --verbose
```

**Sort by size:**
```bash
justsyncit snapshots list --sort-by-size
```

#### Output
```
ID              Name                    Created              Files    Size
abc123-def456   Daily Backup           2023-12-01 10:30:15  156      234.5 MB
def456-ghi789   Weekly Backup          2023-11-28 15:45:22  892      1.2 GB
```

### snapshots info

Show detailed information about a specific snapshot.

#### Syntax
```bash
justsyncit snapshots info <snapshot-id> [options]
```

#### Arguments
- `snapshot-id`: ID of the snapshot to inspect

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--show-files` | Show list of files in the snapshot | false |
| `--file-limit NUM` | Limit number of files to show | 20 |
| `--no-statistics` | Don't show file statistics | false |
| `--help` | Show help message | - |

#### Examples

**Basic info:**
```bash
justsyncit snapshots info abc123-def456
```

**Show files:**
```bash
justsyncit snapshots info abc123-def456 --show-files
```

**Show many files:**
```bash
justsyncit snapshots info abc123-def456 --show-files --file-limit 50
```

#### Output
```
Snapshot ID: abc123-def456
Name: Daily Backup
Description: Automatic daily backup of documents
Created: 2023-12-01 10:30:15 UTC
Total Files: 156
Total Size: 234.5 MB
```

### snapshots delete

Delete a specific snapshot.

#### Syntax
```bash
justsyncit snapshots delete <snapshot-id> [options]
```

#### Arguments
- `snapshot-id`: ID of the snapshot to delete

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--force, -f` | Delete without confirmation | false |
| `--no-confirm` | Skip confirmation prompt | false |
| `--help` | Show help message | - |

#### Examples

**Interactive delete:**
```bash
justsyncit snapshots delete abc123-def456
```

**Force delete:**
```bash
justsyncit snapshots delete abc123-def456 --force
```

#### Notes
- This operation is irreversible
- File chunks are only deleted if not referenced by other snapshots
- Use with caution as deleted snapshots cannot be recovered

### snapshots verify

Verify integrity of a snapshot.

#### Syntax
```bash
justsyncit snapshots verify <snapshot-id> [options]
```

#### Arguments
- `snapshot-id`: ID of the snapshot to verify

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--no-chunk-verify` | Skip chunk integrity verification | false |
| `--no-file-hash-verify` | Skip file hash verification | false |
| `--quiet, -q` | Quiet mode with minimal output | false |
| `--no-progress` | Don't show progress indicator | false |
| `--help` | Show help message | - |

#### Examples

**Full verification:**
```bash
justsyncit snapshots verify abc123-def456
```

**Quick verification:**
```bash
justsyncit snapshots verify abc123-def456 --no-chunk-verify
```

**Quiet verification:**
```bash
justsyncit snapshots verify abc123-def456 --quiet
```

#### Output
```
Verifying snapshot: abc123-def456
Checking 156 files...
Checking 1234 chunks...
Verification completed successfully!
All files and chunks verified.
```

## Network Operation Commands

### server start

Start a backup server to accept remote backup requests.

#### Syntax
```bash
justsyncit server start [options]
```

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--port PORT` | Port to listen on | 8080 |
| `--transport TYPE` | Transport protocol (TCP|QUIC) | TCP |
| `--daemon, -d` | Run in daemon mode (background) | false |
| `--quiet, -q` | Quiet mode with minimal output | false |
| `--help` | Show help message | - |

#### Examples

**Basic server:**
```bash
justsyncit server start
```

**Custom port:**
```bash
justsyncit server start --port 9090
```

**QUIC server:**
```bash
justsyncit server start --transport QUIC
```

**Daemon mode:**
```bash
justsyncit server start --daemon
```

#### Output
```
Starting JustSyncIt server...
Transport: TCP
Port: 8080
Server started successfully.
Listening for connections...
```

### server stop

Stop a running backup server.

#### Syntax
```bash
justsyncit server stop [options]
```

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--force` | Force stop even if transfers are in progress | false |
| `--quiet, -q` | Quiet mode with minimal output | false |
| `--help` | Show help message | - |

#### Examples

**Graceful stop:**
```bash
justsyncit server stop
```

**Force stop:**
```bash
justsyncit server stop --force
```

### server status

Show server status and configuration.

#### Syntax
```bash
justsyncit server status [options]
```

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--verbose, -v` | Show detailed status information | false |
| `--json` | Output status in JSON format | false |
| `--help` | Show help message | - |

#### Examples

**Basic status:**
```bash
justsyncit server status
```

**Detailed status:**
```bash
justsyncit server status --verbose
```

**JSON status:**
```bash
justsyncit server status --json
```

#### Output
```
Server Status: Running
Transport: TCP
Port: 8080
Active Connections: 3
Total Transfers: 15
Uptime: 2h 34m 12s
```

### transfer

Transfer a snapshot to another server.

#### Syntax
```bash
justsyncit transfer <snapshot-id> --to <server> [options]
```

#### Arguments
- `snapshot-id`: ID of the snapshot to transfer
- `--to SERVER`: Target server address (host:port) [required]

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--transport TYPE` | Transport protocol (TCP|QUIC) | TCP |
| `--no-verify` | Skip integrity verification after transfer | false |
| `--compress` | Enable compression during transfer | false |
| `--resume` | Resume interrupted transfer | false |
| `--quiet, -q` | Quiet mode with minimal output | false |
| `--help` | Show help message | - |

#### Examples

**Basic transfer:**
```bash
justsyncit transfer abc123-def456 --to 192.168.1.100:8080
```

**QUIC transfer:**
```bash
justsyncit transfer abc123-def456 --to backup.example.com:8080 --transport QUIC
```

**Compressed transfer:**
```bash
justsyncit transfer abc123-def456 --to 192.168.1.100:8080 --compress
```

**Resume transfer:**
```bash
justsyncit transfer abc123-def456 --to 192.168.1.100:8080 --resume
```

### sync

Synchronize local directory with remote server.

#### Syntax
```bash
justsyncit sync <local-path> <remote-server> [options]
```

#### Arguments
- `local-path`: Local directory to synchronize
- `remote-server`: Remote server address (host:port)

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--transport TYPE` | Transport protocol (TCP|QUIC) | TCP |
| `--one-way` | One-way sync (local to remote only) | false |
| `--delete-extra` | Delete files that don't exist on source | false |
| `--no-verify` | Skip integrity verification | false |
| `--dry-run` | Show what would be synchronized without making changes | false |
| `--quiet, -q` | Quiet mode with minimal output | false |
| `--help` | Show help message | - |

#### Examples

**Basic sync:**
```bash
justsyncit sync /home/user/documents 192.168.1.100:8080
```

**One-way sync:**
```bash
justsyncit sync /home/user/documents 192.168.1.100:8080 --one-way
```

**Dry run:**
```bash
justsyncit sync /home/user/documents 192.168.1.100:8080 --dry-run
```

## Hash Commands

### hash

Generate hash values for files and directories.

#### Syntax
```bash
justsyncit hash <path> [options]
```

#### Arguments
- `path`: Path to file or directory to hash

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--algorithm ALG` | Hash algorithm (BLAKE3, SHA256) | BLAKE3 |
| `--recursive` | Hash directories recursively | false |
| `--output FORMAT` | Output format (hex, base64) | hex |
| `--help` | Show help message | - |

#### Examples

**Hash single file:**
```bash
justsyncit hash /home/user/document.txt
```

**Hash directory:**
```bash
justsyncit hash /home/user/documents --recursive
```

**SHA256 hash:**
```bash
justsyncit hash /home/user/document.txt --algorithm SHA256
```

**Base64 output:**
```bash
justsyncit hash /home/user/document.txt --output base64
```

#### Output
```
File: /home/user/document.txt
Algorithm: BLAKE3
Hash: abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
```

## Verify Commands

### verify

Verify file integrity using hash values.

#### Syntax
```bash
justsyncit verify <path> <expected-hash> [options]
```

#### Arguments
- `path`: Path to file to verify
- `expected-hash`: Expected hash value

#### Options
| Option | Description | Default |
|--------|-------------|----------|
| `--algorithm ALG` | Hash algorithm to use (BLAKE3, SHA256) | BLAKE3 |
| `--help` | Show help message | - |

#### Examples

**Verify file:**
```bash
justsyncit verify /home/user/document.txt abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
```

**SHA256 verification:**
```bash
justsyncit verify /home/user/document.txt abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890 --algorithm SHA256
```

#### Output
```
File: /home/user/document.txt
Algorithm: BLAKE3
Expected: abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
Actual:   abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
Result: VERIFIED
```

## Help Command

### help

Display help information for commands.

#### Syntax
```bash
justsyncit help [command]
```

#### Arguments
- `command`: Optional command to show help for

#### Examples

**Show all commands:**
```bash
justsyncit help
```

**Show command help:**
```bash
justsyncit help backup
justsyncit help snapshots list
```

## Exit Codes

JustSyncIt returns the following exit codes:

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Invalid arguments |
| 3 | File not found |
| 4 | Permission denied |
| 5 | Network error |
| 6 | Storage error |
| 7 | Integrity verification failed |
| 8 | Server error |

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

### Pattern Matching
File patterns support glob syntax:
- `*.txt` - All .txt files
- `**/*.java` - All .java files in subdirectories
- `temp*` - Files starting with "temp"
- `{*.jpg,*.png}` - Multiple extensions

### Error Handling
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