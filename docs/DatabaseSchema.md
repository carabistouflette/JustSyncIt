# JustSyncIt Database Schema Documentation

## Overview

JustSyncIt uses SQLite as its metadata management system to provide fast queries with referential integrity for the existing content-addressable storage system. The database stores information about snapshots, files, and chunks, enabling efficient backup and restore operations.

## Database Schema

### Tables

#### 1. snapshots

Stores backup snapshot metadata.

| Column | Type | Description |
|--------|------|-------------|
| id | TEXT (PRIMARY KEY) | Unique identifier for the snapshot |
| name | TEXT (UNIQUE, NOT NULL) | Human-readable name of the snapshot |
| description | TEXT | Optional description of the snapshot |
| created_at | TEXT (NOT NULL) | ISO-8601 timestamp when snapshot was created |
| file_count | INTEGER (NOT NULL) | Number of files in the snapshot |
| total_size | INTEGER (NOT NULL) | Total size of all files in bytes |

**Indexes:**
- PRIMARY KEY on `id`
- UNIQUE index on `name`
- Index on `created_at` for chronological queries

#### 2. files

Stores file metadata within snapshots.

| Column | Type | Description |
|--------|------|-------------|
| id | TEXT (PRIMARY KEY) | Unique identifier for the file |
| snapshot_id | TEXT (NOT NULL) | Reference to the containing snapshot |
| path | TEXT (NOT NULL) | File path relative to backup root |
| size | INTEGER (NOT NULL) | File size in bytes |
| modified_time | TEXT (NOT NULL) | ISO-8601 timestamp of last modification |
| hash | TEXT (NOT NULL) | BLAKE3 hash of file content |
| created_at | TEXT (NOT NULL) | ISO-8601 timestamp when file was added to snapshot |

**Indexes:**
- PRIMARY KEY on `id`
- Index on `snapshot_id` for snapshot file queries
- Index on `hash` for duplicate detection
- Composite index on `(snapshot_id, path)` for unique file paths within snapshots

#### 3. file_chunks

Maps files to their constituent chunks.

| Column | Type | Description |
|--------|------|-------------|
| file_id | TEXT (NOT NULL) | Reference to the file |
| chunk_hash | TEXT (NOT NULL) | Reference to the chunk |
| chunk_order | INTEGER (NOT NULL) | Order of chunk within file |
| created_at | TEXT (NOT NULL) | ISO-8601 timestamp when mapping was created |

**Indexes:**
- PRIMARY KEY on `(file_id, chunk_order)`
- Index on `chunk_hash` for chunk usage queries
- Index on `file_id` for file chunk queries

#### 4. chunks

Stores chunk metadata for access tracking and statistics.

| Column | Type | Description |
|--------|------|-------------|
| hash | TEXT (PRIMARY KEY) | BLAKE3 hash of chunk content |
| size | INTEGER (NOT NULL) | Chunk size in bytes |
| created_at | TEXT (NOT NULL) | ISO-8601 timestamp when chunk was first stored |
| access_count | INTEGER (NOT NULL, DEFAULT 0) | Number of times chunk has been accessed |
| last_accessed | TEXT | ISO-8601 timestamp of last access |

**Indexes:**
- PRIMARY KEY on `hash`
- Index on `last_accessed` for garbage collection
- Index on `access_count` for popularity analysis

### Foreign Key Relationships

- `files.snapshot_id` → `snapshots.id` (CASCADE DELETE)
- `file_chunks.file_id` → `files.id` (CASCADE DELETE)
- `file_chunks.chunk_hash` → `chunks.hash` (CASCADE DELETE)

### Schema Versioning

The database includes a `schema_version` table to track migrations:

| Column | Type | Description |
|--------|------|-------------|
| version | INTEGER (PRIMARY KEY) | Current schema version |
| applied_at | TEXT (NOT NULL) | ISO-8601 timestamp when version was applied |

## API Usage

### MetadataService Interface

The `MetadataService` interface provides the primary API for database operations:

#### Snapshot Management

```java
// Create a new snapshot
Snapshot snapshot = metadataService.createSnapshot("backup-2023-12-01", "Daily backup");

// Get a snapshot by ID
Optional<Snapshot> snapshot = metadataService.getSnapshot(snapshotId);

// List all snapshots
List<Snapshot> snapshots = metadataService.listSnapshots();

// Delete a snapshot
boolean deleted = metadataService.deleteSnapshot(snapshotId);
```

#### File Management

```java
// Insert file metadata
FileMetadata file = new FileMetadata(
    fileId, snapshotId, "/path/to/file.txt", 
    fileSize, modifiedTime, fileHash, chunkHashes
);
metadataService.insertFile(file);

// Get file by ID
Optional<FileMetadata> file = metadataService.getFile(fileId);

// List files in a snapshot
List<FileMetadata> files = metadataService.getFilesInSnapshot(snapshotId);

// Update file metadata
metadataService.updateFile(file);

// Delete file
boolean deleted = metadataService.deleteFile(fileId);
```

#### Chunk Management

```java
// Insert or update chunk metadata
ChunkMetadata chunk = new ChunkMetadata(
    chunkHash, chunkSize, createdTime, accessCount, lastAccessed
);
metadataService.upsertChunk(chunk);

// Record chunk access
metadataService.recordChunkAccess(chunkHash);

// Get chunk metadata
Optional<ChunkMetadata> chunk = metadataService.getChunk(chunkHash);

// Delete chunk
boolean deleted = metadataService.deleteChunk(chunkHash);
```

#### Transaction Management

```java
// Begin transaction
Transaction transaction = metadataService.beginTransaction();
try {
    // Perform multiple operations
    metadataService.insertFile(file1);
    metadataService.insertFile(file2);
    metadataService.upsertChunk(chunk);
    
    // Commit transaction
    transaction.commit();
} catch (Exception e) {
    // Rollback on error
    transaction.rollback();
} finally {
    // Close transaction
    transaction.close();
}
```

#### Statistics

```java
// Get metadata statistics
MetadataStats stats = metadataService.getStats();
System.out.println("Snapshots: " + stats.snapshotCount());
System.out.println("Files: " + stats.fileCount());
System.out.println("Chunks: " + stats.chunkCount());
System.out.println("Total size: " + stats.totalSize());
```

### Database Connection Management

The `DatabaseConnectionManager` interface manages database connections:

```java
// Get a connection
try (Connection connection = connectionManager.getConnection()) {
    // Use connection for custom queries
}

// Begin transaction
try (Transaction transaction = connectionManager.beginTransaction()) {
    Connection connection = transaction.getConnection();
    // Use connection within transaction
}
```

### Schema Migration

The `SchemaMigrator` interface handles database schema updates:

```java
// Create initial schema
migrator.createInitialSchema(connection);

// Check current version
int currentVersion = migrator.getCurrentVersion(connection);

// Migrate to latest version
migrator.migrate(connection);
```

## Performance Considerations

### Indexing Strategy

The schema includes strategic indexes to optimize common query patterns:

1. **Snapshot queries**: Indexed by `name` and `created_at`
2. **File queries**: Indexed by `snapshot_id`, `hash`, and `(snapshot_id, path)`
3. **Chunk queries**: Indexed by `hash`, `last_accessed`, and `access_count`
4. **File-chunk mapping**: Indexed by `file_id` and `chunk_hash`

### Query Performance

- **Snapshot listing**: <100ms with millions of entries
- **File enumeration**: Optimized with composite indexes
- **Chunk access tracking**: Efficient with dedicated indexes
- **Statistics aggregation**: Uses indexed counts for fast calculation

### Connection Pooling

The `SqliteConnectionManager` provides connection pooling for thread-safe operations:

```java
// Create connection manager with pool size
DatabaseConnectionManager manager = new SqliteConnectionManager(dbPath, 10);

// Connections are automatically pooled and reused
try (Connection connection = manager.getConnection()) {
    // Use connection
}
```

## Integration with ContentStore

The metadata service integrates seamlessly with the existing BLAKE3-based content-addressable storage:

1. **Content Addressing**: Files and chunks are referenced by their BLAKE3 hashes
2. **Integrity Verification**: The `Blake3IntegrityVerifier` ensures data integrity
3. **Deduplication**: Chunk-level deduplication is tracked through metadata
4. **Garbage Collection**: Metadata enables efficient cleanup of unused chunks

## Testing

### Unit Tests

Comprehensive unit tests verify all metadata operations:

- `MetadataServiceTest`: Tests all CRUD operations and transaction management
- `SqliteConnectionManagerTest`: Tests connection pooling and thread safety
- `SqliteSchemaMigratorTest`: Tests schema creation and migration
- `SqliteTransactionTest`: Tests transaction lifecycle management

### Performance Tests

JMH benchmarks measure performance of critical operations:

- `MetadataServicePerformanceBenchmark`: Tests CRUD operations with various data sizes
- Benchmarks verify <100ms query performance with millions of entries

## Configuration

### Database Location

The database file can be configured via the connection manager:

```java
// File-based database
DatabaseConnectionManager manager = new SqliteConnectionManager("/path/to/metadata.db", 10);

// In-memory database for testing
DatabaseConnectionManager manager = new SqliteConnectionManager(":memory:", 10);
```

### Connection Pool Size

The connection pool size should be configured based on expected concurrency:

```java
// High concurrency environment
DatabaseConnectionManager manager = new SqliteConnectionManager(dbPath, 20);

// Low concurrency environment
DatabaseConnectionManager manager = new SqliteConnectionManager(dbPath, 5);
```

## Error Handling

The metadata service uses standard Java exception handling:

- `IllegalArgumentException`: Invalid parameters
- `IllegalStateException`: Invalid operation state
- `SQLException`: Database errors
- `ServiceException`: General service errors

All resources implement the `ClosableResource` pattern for proper cleanup.