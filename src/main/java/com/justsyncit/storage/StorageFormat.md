# Content-Addressable Storage Format

This document describes the storage format used by the JustSyncIt content-addressable storage system.

## Overview

The storage system provides content-addressable storage with automatic deduplication using BLAKE3 hashes. Chunks are stored in a two-level directory structure with an index file for efficient lookup.

## SOLID Architecture

The storage system is designed following SOLID principles:

### Single Responsibility Principle (SRP)
- **ChunkPathGenerator**: Responsible only for generating file paths from hashes
- **IntegrityVerifier**: Responsible only for verifying data integrity using hashes
- **IndexPersistence**: Responsible only for persisting the chunk index to disk
- **ContentStore**: Responsible only for coordinating storage operations
- **ChunkIndex**: Responsible only for managing the chunk index

### Open/Closed Principle (OCP)
- **AbstractContentStore**: Base class that provides common functionality while allowing extension
- **Template Method Pattern**: Allows customization of specific operations without modifying base class
- **Strategy Pattern**: Different path generators and integrity verifiers can be plugged in

### Liskov Substitution Principle (LSP)
- **MemoryContentStore**: Can be substituted for FilesystemContentStore without breaking functionality
- **All implementations**: Maintain the same contracts as defined by interfaces

### Interface Segregation Principle (ISP)
- **ChunkStorage**: Basic storage operations (store, retrieve, exists)
- **StorageStatistics**: Statistics and monitoring operations
- **GarbageCollectible**: Garbage collection functionality
- **ClosableResource**: Resource cleanup functionality
- **ContentStore**: Composes multiple focused interfaces

### Dependency Inversion Principle (DIP)
- **ContentStoreFactory**: Creates instances depending on abstractions
- **ChunkIndexFactory**: Creates index instances depending on abstractions
- **IntegrityVerifierFactory**: Creates verifiers depending on abstractions
- **ChunkPathGeneratorFactory**: Creates path generators depending on abstractions

## Directory Structure

```
storage/
├── chunks/                    # Chunk storage directory
│   ├── ab/                   # First 2 characters of hash as subdirectory
│   │   └── cdef1234567890    # Remaining characters as filename
│   ├── cd/
│   │   └── ef1234567890ab
│   └── ...
└── index.txt                 # Chunk index file
```

## Chunk Storage Format

### File Naming
- **Subdirectory**: First 2 characters of the BLAKE3 hash (hexadecimal)
- **Filename**: Remaining characters of the BLAKE3 hash
- **Example**: For hash `abcdef1234567890...`, the file path would be `chunks/ab/cdef1234567890...`

### File Content
- Raw binary data of the chunk
- No metadata or headers in the chunk files
- File size equals the original chunk size

### Hash Algorithm
- **Algorithm**: BLAKE3 (256-bit output)
- **Encoding**: Hexadecimal (64 characters)
- **Case**: Lowercase

## Index File Format

The index file (`index.txt`) maps hashes to their file paths using a simple text format.

### Format Specification
```
<hash>|<relative_path>
```

- **hash**: Full BLAKE3 hash (64 hex characters)
- **relative_path**: Path relative to the storage directory
- **separator**: Pipe character `|`
- **line ending**: Unix-style `\n`

### Example
```
abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890|ab/cdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890
cdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab|cd/ef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab
```

### Index Operations
- **Atomic Updates**: Index is written to a temporary file first, then atomically moved
- **Persistence**: Index is persisted to disk on every modification
- **Recovery**: Index can be rebuilt by scanning the chunks directory if corrupted

## Garbage Collection

### Orphaned Chunks
- Chunks not referenced in the active hashes set are considered orphaned
- Orphaned chunks are removed during garbage collection
- Both the chunk file and index entry are removed

### GC Process
1. Load all hashes from the index
2. Compare with active hashes set
3. Remove chunks not in active set
4. Update index file
5. Record GC timestamp

## Integrity Verification

### Storage Integrity
- Every retrieval verifies the hash of the retrieved data
- If hash verification fails, a `StorageIntegrityException` is thrown
- Corrupted chunks are automatically removed from the index

### Hash Verification Process
1. Read chunk data from file
2. Calculate BLAKE3 hash of the data
3. Compare with expected hash
4. Return data if hashes match, otherwise throw exception

## Performance Considerations

### Directory Structure Benefits
- Two-level structure prevents too many files in a single directory
- Improves filesystem performance for large numbers of chunks
- Provides natural load balancing across subdirectories

### Index Benefits
- Fast hash-to-path lookup without filesystem scanning
- Enables efficient garbage collection
- Supports statistics and monitoring

### Concurrency
- Read-write locks provide thread-safe access
- Concurrent reads are allowed
- Writes are serialized to prevent corruption
- Index updates are atomic

## Storage Format Versioning

### Current Version: 1.0
- BLAKE3 256-bit hashes
- Two-level directory structure
- Text-based index file
- Atomic index updates

### Future Compatibility
- Format is designed to be backward compatible
- Version information could be added to index file header
- Migration utilities can be built for format changes

## Usage Examples

### Creating a Filesystem Content Store
```java
// Using factory with default components
Path storageDir = Paths.get("/backup/storage");
Blake3Service blake3Service = new Blake3ServiceImpl(...);
ContentStore contentStore = ContentStoreFactory.createFilesystemStore(storageDir, blake3Service);

// Using factory with custom components
IntegrityVerifier customVerifier = new CustomIntegrityVerifier();
ChunkPathGenerator customPathGenerator = new CustomPathGenerator();
ChunkIndex customIndex = ChunkIndexFactory.createFilesystemIndex(storageDir, indexFile);
ContentStore customStore = ContentStoreFactory.createFilesystemStore(
    storageDir, customIndex, customVerifier, customPathGenerator);
```

### Creating a Memory Content Store
```java
// For testing or temporary use
IntegrityVerifier verifier = IntegrityVerifierFactory.createBlake3Verifier(blake3Service);
ContentStore memoryStore = ContentStoreFactory.createMemoryStore(verifier);

// Using no-op verifier for testing
IntegrityVerifier noOpVerifier = IntegrityVerifierFactory.createNoOpVerifier();
ContentStore testStore = ContentStoreFactory.createMemoryStore(noOpVerifier);
```

### Creating Custom Components
```java
// Custom path generator
ChunkPathGenerator flatGenerator = ChunkPathGeneratorFactory.createFlatGenerator();
ChunkPathGenerator singleLevelGenerator = ChunkPathGeneratorFactory.createSingleLevelGenerator();

// Custom integrity verifier
IntegrityVerifier customVerifier = new CustomIntegrityVerifier();

// Memory index for testing
ChunkIndex memoryIndex = ChunkIndexFactory.createMemoryIndex();
```

## Security Considerations

### Hash Collision Resistance
- BLAKE3 provides strong collision resistance
- 256-bit output makes accidental collisions practically impossible
- Malicious collisions would require breaking BLAKE3

### Data Integrity
- Every read operation verifies data integrity
- Corrupted data is detected and rejected
- Index consistency is maintained through atomic updates

### Access Control
- Storage directory permissions should be restricted
- Index file contains sensitive hash information
- Consider encryption for sensitive data at rest

## Recovery Procedures

### Index Corruption
1. Stop all storage operations
2. Scan chunks directory and rebuild index
3. Verify all chunk hashes during rebuild
4. Resume normal operations

### Chunk Corruption
1. Corrupted chunks are detected during retrieval
2. Remove corrupted chunks from index
3. Run garbage collection to clean up orphaned files
4. Restore from backup if available

## Monitoring and Statistics

### Available Metrics
- Total number of stored chunks
- Total storage size in bytes
- Deduplication ratio
- Last garbage collection timestamp
- Number of orphaned chunks

### Performance Monitoring
- Storage operation latency
- Index lookup performance
- GC operation duration
- Concurrency contention metrics