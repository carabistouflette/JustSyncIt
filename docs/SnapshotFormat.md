# Snapshot Format Documentation

This document describes the internal format and structure of JustSyncIt snapshots.

## Overview

JustSyncIt uses a **Merkle tree-based snapshot system** that provides:
- Efficient incremental backups (only changed files are processed)
- Cryptographic integrity verification (BLAKE3 hashes)
- Fast diff operations between snapshots
- Snapshot chain validation

## Database Schema

Snapshots are stored in a SQLite database with the following tables:

### `snapshots` Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT PRIMARY KEY | Unique snapshot identifier (e.g., `backup-2025-12-17T10:00:00Z`) |
| `name` | TEXT | Human-readable name |
| `description` | TEXT | Optional description |
| `created_at` | INTEGER | Unix timestamp (milliseconds) |
| `total_files` | INTEGER | Number of files in snapshot |
| `total_size` | INTEGER | Total size in bytes |
| `merkle_root` | TEXT | Root hash of Merkle tree |
| `status` | TEXT | `PENDING`, `COMPLETED`, `FAILED` |

### `files` Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT PRIMARY KEY | File hash (BLAKE3) |
| `snapshot_id` | TEXT | Foreign key to snapshots |
| `path` | TEXT | Relative file path |
| `size` | INTEGER | File size in bytes |
| `modified_at` | INTEGER | Last modification timestamp |
| `permissions` | TEXT | File permissions (octal) |
| `encryption_mode` | TEXT | `NONE` or encryption algorithm |

### `chunks` Table

| Column | Type | Description |
|--------|------|-------------|
| `hash` | TEXT PRIMARY KEY | Chunk hash (BLAKE3) |
| `size` | INTEGER | Chunk size in bytes |
| `reference_count` | INTEGER | Deduplication reference count |

### `file_chunks` Table

| Column | Type | Description |
|--------|------|-------------|
| `file_id` | TEXT | Foreign key to files |
| `chunk_hash` | TEXT | Foreign key to chunks |
| `chunk_index` | INTEGER | Order of chunk in file |

### `merkle_nodes` Table

| Column | Type | Description |
|--------|------|-------------|
| `hash` | TEXT PRIMARY KEY | Node hash (BLAKE3) |
| `type` | TEXT | `FILE` or `DIRECTORY` |
| `name` | TEXT | File/directory name |
| `size` | INTEGER | Size in bytes |
| `children` | TEXT | JSON array of child hashes (may be compressed) |
| `file_id` | TEXT | Reference to files table (for FILE nodes) |
| `compression` | TEXT | `none` or `gzip` |

## Merkle Tree Structure

```
                    [Root Node]
                   hash: abc123...
                   type: DIRECTORY
                   name: "backup-root"
                        │
          ┌─────────────┼─────────────┐
          │             │             │
     [Dir Node]    [File Node]   [File Node]
     hash: def...   hash: ghi...  hash: jkl...
     type: DIR      type: FILE    type: FILE
     name: "src"    name: "a.txt" name: "b.txt"
          │
    ┌─────┴─────┐
    │           │
[File Node] [File Node]
type: FILE  type: FILE
```

### Node Hash Calculation

Each node's hash is computed deterministically:

**File Nodes:**
```
hash = BLAKE3(type + ":" + name + ":" + size + ":" + file_content_hash)
```

**Directory Nodes:**
```
sorted_children = sort(children by name)
children_hash = BLAKE3(concat(child_hashes))
hash = BLAKE3(type + ":" + name + ":" + children_hash)
```

## Compression

Merkle node children are compressed when they exceed 1KB:

1. **Format**: GZIP compression
2. **Encoding**: Base64 for storage
3. **Threshold**: 1024 bytes
4. **Column**: `compression = 'gzip'` when compressed

## Content Storage

Chunk data is stored in the filesystem:
```
storage/chunks/
├── ab/
│   ├── ab12cd34ef...  (chunk file)
│   └── ab98fe76dc...
├── cd/
│   └── cd45ab89...
└── ...
```

Path structure: `storage/chunks/{first-2-chars-of-hash}/{full-hash}`

## Snapshot Chain

Incremental snapshots form a chain:

```
[Snapshot 1] ──► [Snapshot 2] ──► [Snapshot 3]
  Full backup      Incremental     Incremental
  Root: aaa...     Root: bbb...    Root: ccc...
```

**Chain Validation** verifies:
1. Each snapshot's Merkle root exists
2. All nodes referenced by root are present
3. All file content hashes are valid
4. Chunk reference counts are consistent

## CLI Commands

| Command | Description |
|---------|-------------|
| `snapshots list` | List all snapshots |
| `snapshots info <id>` | Show snapshot details |
| `snapshots verify <id>` | Verify snapshot integrity |
| `snapshots verify-chain` | Validate entire chain |
| `snapshots diff <id1> [id2]` | Show differences |
| `snapshots prune` | Apply retention policies |
| `snapshots rollback <id> --target <dir>` | Restore to snapshot state |
| `snapshots delete <id>` | Delete a snapshot |

## Retention Policies

Two built-in policies:

1. **Count-based**: Keep last N snapshots
2. **Age-based**: Keep snapshots newer than X days

Policies use **KEEP-if-ANY** logic: a snapshot is retained if ANY policy wants to keep it.

## Performance Characteristics

| Metric | Target | Achieved |
|--------|--------|----------|
| Incremental snapshot creation | <1s | ✓ (via Merkle diff) |
| 100K files snapshot size | <500MB | ✓ (deduplication) |
| Size reduction vs full backup | 95%+ | ✓ (content-addressable) |
