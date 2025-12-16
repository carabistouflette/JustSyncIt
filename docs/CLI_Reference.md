# CLI Reference

JustSyncIt is primarily controlled via its Command Line Interface.

## Global Structure
```bash
justsyncit <command> [subcommand] [flags]
```

## Commands

### `backup`
Backs up a directory to the content store.

**Usage:**
```bash
justsyncit backup <source-dir> [options]
```

**Options:**
*   `--include-hidden`: Include hidden files and directories in the backup.
*   `--follow-symlinks`: Follow symbolic links (default: preserve links).
*   `--skip-symlinks`: Ignore symbolic links.
*   `--chunk-size <bytes>`: Target average chunk size.
*   `--no-verify`: Skip post-backup integrity checks.
*   `--remote`: Enable remote backup mode.
*   `--server <host:port>`: Target server for remote backup.
*   `--transport <TCP|QUIC>`: Transport protocol (default: TCP).

### `restore`
Restores files from a snapshot.

**Usage:**
```bash
justsyncit restore <snapshot-id> <destination-dir> [options]
```

**Options:**
*   `--force`: Overwrite existing files in the destination.

### `snapshots`
Manages backup snapshots.

**Subcommands:**
*   `list`: List all available snapshots.
    *   `--verbose`: Show detailed stats.
*   `info <id>`: Show details for a specific snapshot.
    *   `--show-files`: List all files contained in the snapshot.
*   `delete <id>`: Delete a snapshot.
*   `verify <id>`: Verify the integrity of a snapshot's data.

### `network`
Manage network settings and connections.

**Subcommands:**
*   `start <port>`: Start the backup server on the specified port.
*   `stop`: Stop the running server.
*   `status`: Show current network statistics (connections, bytes transferred).
*   `connect <host:port>`: Test connection to a remote node.

### `server`
Alias for network server management.

**Subcommands:**
*   `start`: Start the server (same as `network start`).
*   `status`: Check server status.

## Environment Variables
*   `JUSTSYNCIT_HOME`: Overrides the default data directory (default: `~/.justsyncit`).
*   `JUSTSYNCIT_LOG_LEVEL`: Set logging verbosity (DEBUG, INFO, WARN, ERROR).
