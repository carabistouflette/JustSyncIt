# Filesystem Scanner and Chunking System Architecture

## Overview

The Filesystem Scanner and Chunking System is a high-performance, SOLID-compliant Java implementation for scanning directories and chunking files. It provides recursive directory scanning, fixed-size chunking (64KB), buffered reading with ByteBuffer pools, sparse file detection, symlink handling, and AsynchronousFileChannel optimization for SSD/HDD performance.

## Architecture Components

### Core Interfaces

#### FilesystemScanner Interface
- **Purpose**: Provides recursive directory scanning capabilities
- **Key Methods**:
  - `scanDirectory(Path directory, ScanOptions options)`: Initiates directory scanning
  - `setFileVisitor(FileVisitor visitor)`: Sets the visitor for file processing
  - `setProgressListener(ProgressListener listener)`: Sets progress monitoring
- **Implementation**: `NioFilesystemScanner` using Java NIO `Files.walkFileTree()`

#### FileChunker Interface
- **Purpose**: Extends `ChunkStorage` for file chunking operations
- **Key Methods**:
  - `chunkFile(Path file, ChunkingOptions options)`: Chunks file into fixed-size pieces
  - `setBufferPool(BufferPool bufferPool)`: Memory management
  - `setChunkSize(int chunkSize)`: Configurable chunk size (default 64KB)
- **Implementation**: `FixedSizeFileChunker` with `AsynchronousFileChannel` optimization

#### BufferPool Interface
- **Purpose**: Manages ByteBuffer memory allocation and pooling
- **Key Methods**:
  - `acquire(int size)`: Acquires buffer of specified size
  - `release(ByteBuffer buffer)`: Returns buffer to pool
  - `clear()`: Clears all buffers
- **Implementation**: `ByteBufferPool` with direct ByteBuffers for optimal performance

#### FileVisitor Interface
- **Purpose**: Handles file processing during scanning
- **Key Methods**:
  - `visitFile(Path file, BasicFileAttributes attrs)`: Process individual files
  - `visitDirectory(Path dir, BasicFileAttributes attrs)`: Process directories
  - `visitFailed(Path file, IOException exc)`: Handle errors
- **Implementation**: Used by `FileProcessor` for chunking integration

### Configuration Classes

#### ScanOptions
- **Purpose**: Configuration for scanning operations
- **Builder Pattern**: Flexible configuration with method chaining
- **Key Options**:
  - `withIncludePattern(PathMatcher)`: File inclusion patterns
  - `withExcludePattern(PathMatcher)`: File exclusion patterns
  - `withSymlinkStrategy(SymlinkStrategy)`: Symlink handling (FOLLOW, RECORD, SKIP)
  - `withMaxDepth(int)`: Recursive depth limit
  - `withDetectSparseFiles(boolean)`: Sparse file detection
  - `withIncludeHiddenFiles(boolean)`: Hidden file inclusion

#### ChunkingOptions
- **Purpose**: Configuration for chunking operations
- **Key Options**:
  - `withChunkSize(int)`: Chunk size (default 64KB)
  - `withUseAsyncIO(boolean)`: Asynchronous I/O optimization
  - `withDetectSparseFiles(boolean)`: Sparse file detection
  - `withBufferCount(int)`: Buffer pool size
  - `withMaxConcurrentChunks(int)`: Concurrent chunking limit

#### SymlinkStrategy Enum
- **FOLLOW**: Follow symbolic links and process target files
- **RECORD**: Record symlinks as metadata without following
- **SKIP**: Ignore symbolic links entirely

### Result Classes

#### ScanResult
- **Purpose**: Contains scanning operation results
- **Key Data**:
  - `List<ScannedFile> scannedFiles`: Successfully scanned files
  - `List<ScanError> errors`: Encountered errors
  - `Instant startTime/endTime`: Timing information
  - `Map<String, Object> metadata`: Additional scan metadata

#### ChunkingResult
- **Purpose**: Contains chunking operation results
- **Key Data**:
  - `int chunkCount`: Number of chunks created
  - `long totalSize`: Total file size
  - ` `long sparseSize`: Sparse region size
  - `String fileHash`: BLAKE3 file hash
  - `List<String> chunkHashes`: Ordered chunk hashes

## Implementation Details

### NioFilesystemScanner
- **Recursive Walking**: Uses `Files.walkFileTree()` with `SimpleFileVisitor`
- **Pattern Matching**: Integrates `PathMatcher` for include/exclude filters
- **Symlink Handling**: Implements all three strategies with cycle detection
- **Sparse File Detection**: Uses `Files.getAttribute("unix:sparse")` on Unix systems
- **Progress Monitoring**: Real-time progress updates via listener interface
- **Error Handling**: Comprehensive exception handling with detailed error reporting

### FixedSizeFileChunker
- **Asynchronous I/O**: Uses `AsynchronousFileChannel` for optimal SSD/HDD performance
- **Buffer Management**: Integrates with `ByteBufferPool` for memory efficiency
- **Concurrent Processing**: Processes multiple chunks concurrently
- **Hash Calculation**: Integrates with BLAKE3 service for integrity verification
- **Error Recovery**: Robust error handling with fallback mechanisms

### ByteBufferPool
- **Direct Buffers**: Uses `ByteBuffer.allocateDirect()` for optimal I/O performance
- **Size Management**: Supports variable buffer sizes with automatic scaling
- **Thread Safety**: Concurrent access with proper synchronization
- **Memory Efficiency**: Minimizes GC pressure through buffer reuse

### FileProcessor
- **Orchestration**: Coordinates between scanner and chunker
- **Integration**: Seamlessly integrates with `ContentStore` and `MetadataService`
- **Async Processing**: Supports both synchronous and asynchronous workflows
- **Progress Tracking**: Comprehensive progress monitoring with detailed statistics

## Performance Optimizations

### SSD/HDD Optimization
- **AsynchronousFileChannel**: Non-blocking I/O for optimal throughput
- **Direct ByteBuffers**: Zero-copy operations for maximum speed
- **Concurrent Chunking**: Parallel processing of file chunks
- **Batch Operations**: Minimizes system call overhead

### Memory Management
- **Buffer Pooling**: Reuses ByteBuffers to reduce allocation overhead
- **Lazy Loading**: On-demand buffer allocation
- **Size Optimization**: Automatic buffer size scaling based on chunk size
- **GC Awareness**: Minimizes garbage collection impact

### Sparse File Handling
- **Unix Attribute Detection**: Uses `Files.getAttribute("unix:sparse")`
- **Size Comparison**: Detects sparse files by comparing allocated vs. actual size
- **Efficient Processing**: Skips sparse regions when possible
- **Cross-Platform**: Graceful fallback on non-Unix systems

## SOLID Principles Implementation

### Single Responsibility Principle
- **FilesystemScanner**: Only handles directory scanning
- **FileChunker**: Only handles file chunking
- **BufferPool**: Only manages memory buffers
- **FileProcessor**: Only orchestrates workflow

### Open/Closed Principle
- **Interface-Based Design**: All components implement well-defined interfaces
- **Extensible**: Easy to add new implementations
- **Pluggable**: Components can be swapped without code changes

### Liskov Substitution Principle
- **Interface Compliance**: All implementations can substitute their interfaces
- **Behavioral Consistency**: Maintains contract guarantees
- **Type Safety**: Strong typing with generics

### Interface Segregation Principle
- **Focused Interfaces**: Each interface has specific, cohesive methods
- **Minimal Dependencies**: No unnecessary method requirements
- **Client-Specific**: Clients depend only on needed methods

### Dependency Inversion Principle
- **Abstraction Dependencies**: Depends on interfaces, not implementations
- **Injection Ready**: Supports dependency injection frameworks
- **Testable**: Easy to mock and unit test

## Usage Patterns

### Basic Directory Scanning
```java
FilesystemScanner scanner = new NioFilesystemScanner();
ScanOptions options = new ScanOptions()
    .withIncludePattern(FileSystems.getDefault().getPathMatcher("glob:*.txt"))
    .withMaxDepth(10)
    .withIncludeHiddenFiles(false);

CompletableFuture<ScanResult> future = scanner.scanDirectory(directory, options);
ScanResult result = future.get();
```

### File Chunking
```java
Blake3Service blake3Service = ServiceFactory.createBlake3Service();
FileChunker chunker = new FixedSizeFileChunker(blake3Service);

ChunkingOptions options = new ChunkingOptions()
    .withChunkSize(64 * 1024) // 64KB
    .withUseAsyncIO(true)
    .withDetectSparseFiles(true);

CompletableFuture<ChunkingResult> future = chunker.chunkFile(file, options);
ChunkingResult result = future.get();
```

### Integrated Processing
```java
// Create components
FilesystemScanner scanner = new NioFilesystemScanner();
FileChunker chunker = new FixedSizeFileChunker(blake3Service);
ContentStore contentStore = ContentStoreFactory.createMemoryStore(blake3Service);
MetadataService metadataService = MetadataServiceFactory.createFileBasedService("metadata.db");

// Create processor
FileProcessor processor = new FileProcessor(scanner, chunker, contentStore, metadataService);

// Process directory
ScanOptions options = new ScanOptions()
    .withIncludePattern(FileSystems.getDefault().getPathMatcher("glob:*"))
    .withSymlinkStrategy(SymlinkStrategy.RECORD);

CompletableFuture<ProcessingResult> future = processor.processDirectory(directory, options);
ProcessingResult result = future.get();
```

## Performance Requirements

The system is designed to meet the following performance targets:

- **Disk Read Throughput**: >500 MB/s for large files
- **Memory Efficiency**: Minimal GC impact through buffer pooling
- **Concurrent Processing**: Parallel chunking for optimal CPU utilization
- **I/O Optimization**: Asynchronous operations for SSD/HDD performance

## Testing Strategy

### Unit Tests
- **Component Isolation**: Each component tested independently
- **Mock Dependencies**: Uses mocks for external dependencies
- **Edge Cases**: Comprehensive coverage of error conditions
- **Performance Tests**: Benchmarks for throughput verification

### Integration Tests
- **End-to-End**: Complete workflow testing
- **Real Components**: Uses actual storage and metadata services
- **Data Integrity**: Verifies chunking and storage consistency
- **Performance Validation**: Confirms throughput requirements

### Benchmark Tests
- **JMH Integration**: Java Microbenchmark Harness for accurate measurements
- **Multiple Scenarios**: Different file sizes and chunk sizes
- **Throughput Verification**: Validates >500 MB/s requirement
- **Regression Detection**: Performance monitoring over time

## Error Handling

### Comprehensive Coverage
- **File System Errors**: Permission denied, file not found, I/O errors
- **Memory Errors**: Buffer allocation failures, out of memory
- **Network Errors**: For distributed storage scenarios
- **Validation Errors**: Invalid parameters, malformed data

### Recovery Strategies
- **Graceful Degradation**: Fallback to synchronous I/O if async fails
- **Partial Success**: Continue processing when individual chunks fail
- **Resource Cleanup**: Automatic resource management
- **Detailed Reporting**: Comprehensive error information for debugging

## Future Enhancements

### Potential Improvements
- **Incremental Hashing**: Stream-based BLAKE3 for large files
- **Compression Integration**: Optional chunk compression
- **Distributed Processing**: Multi-machine chunking for very large files
- **Machine Learning**: Adaptive chunk sizing based on file patterns

### Extensibility Points
- **Custom Visitors**: Pluggable file processing logic
- **Alternative Storage**: Different storage backends
- **New Hash Algorithms**: Support for additional hash functions
- **Performance Monitoring**: Integration with monitoring systems