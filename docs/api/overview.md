---
layout: api
title: API Overview
nav_order: 1
---

# API Overview

JustSyncIt provides a comprehensive Java API for programmatic file synchronization, storage management, and network operations.

## Core Packages

### [`com.justsyncit.hash`](hash/)
BLAKE3 hashing implementation with SIMD optimizations.

- `Blake3Service` - Main hashing service
- `Blake3FileHasher` - File hashing utilities
- `Blake3StreamHasher` - Stream hashing
- `Blake3BufferHasher` - Buffer-based hashing

### [`com.justsyncit.network`](network/)
Network protocol and client-server communication.

- `NetworkService` - Network operations interface
- `NetworkServiceImpl` - Network implementation
- `TcpServer` - TCP server implementation
- `TcpClient` - TCP client implementation

### [`com.justsyncit.storage`](storage/)
Content-addressable storage with integrity verification.

- `ContentStore` - Storage interface
- `FilesystemContentStore` - Filesystem implementation
- `MemoryContentStore` - In-memory implementation
- `ChunkIndex` - Chunk indexing

### [`com.justsyncit.simd`](simd/)
SIMD detection and hardware acceleration.

- `SimdDetectionService` - SIMD detection
- `X86SimdDetector` - x86 SIMD detection
- `ArmSimdDetector` - ARM SIMD detection

## Getting Started

### Basic Usage

```java
// Create hashing service
Blake3Service hashService = new Blake3ServiceImpl();

// Hash a file
Path file = Paths.get("example.txt");
String hash = hashService.hashFile(file);

// Create content store
ContentStore store = new FilesystemContentStore(Paths.get("./storage"));

// Store content
InputStream content = Files.newInputStream(file);
String contentHash = store.store(content);

// Retrieve content
InputStream retrieved = store.retrieve(contentHash);
```

### Network Operations

```java
// Start server
NetworkService server = new NetworkServiceImpl();
server.startServer(8080);

// Connect to server
NetworkService client = new NetworkServiceImpl();
client.connectToServer("localhost", 8080);

// Transfer file
FileTransferResult result = client.transferFile(file, destination);
```

## Configuration

### System Properties

| Property | Default | Description |
|-----------|----------|-------------|
| `justsyncit.chunk.size` | 64MB | Default chunk size |
| `justsyncit.threads` | CPU cores | Thread pool size |
| `justsyncit.cache.size` | 100MB | In-memory cache size |

### Programmatic Configuration

```java
// Configure hashing service
Blake3Config config = Blake3Config.builder()
    .withSimdEnabled(true)
    .withParallelism(4)
    .build();

Blake3Service hashService = new Blake3ServiceImpl(config);

// Configure storage
StorageConfig storageConfig = StorageConfig.builder()
    .withChunkSize(64 * 1024 * 1024) // 64MB
    .withCompressionEnabled(true)
    .withIntegrityCheckEnabled(true)
    .build();

ContentStore store = new FilesystemContentStore(storageDir, storageConfig);
```

## Error Handling

JustSyncIt uses standard Java exceptions with custom exception types:

```java
try {
    String hash = hashService.hashFile(file);
} catch (HashingException e) {
    // Handle hashing errors
    logger.error("Failed to hash file: " + e.getMessage());
} catch (IOException e) {
    // Handle I/O errors
    logger.error("I/O error: " + e.getMessage());
}
```

## Performance Considerations

### Hashing Performance

- Use SIMD when available for better performance
- Adjust parallelism based on CPU cores
- Consider memory usage with large files

### Storage Performance

- Configure appropriate chunk sizes
- Enable compression for text data
- Use memory store for temporary operations

### Network Performance

- Configure appropriate buffer sizes
- Use compression for network transfers
- Consider bandwidth limitations

## Thread Safety

Most JustSyncIt components are thread-safe:

- `Blake3Service` - Thread-safe
- `ContentStore` - Thread-safe for concurrent reads
- `NetworkService` - Thread-safe with connection pooling

## Examples

See the [Examples](examples/) directory for complete working examples of common use cases.

## Migration Guide

If you're upgrading from a previous version, see the [Migration Guide](migration.md) for breaking changes and migration steps.