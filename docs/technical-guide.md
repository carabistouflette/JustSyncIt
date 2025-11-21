# JustSyncIt Technical Guide

## Table of Contents

- [Overview](#overview)
- [Database Schema](#database-schema)
- [Filesystem Scanner Architecture](#filesystem-scanner-architecture)
- [Network Protocol](#network-protocol)
- [Storage Format](#storage-format)
- [Performance Considerations](#performance-considerations)
- [Security Architecture](#security-architecture)

## Overview

JustSyncIt is built with a modular architecture following SOLID principles, providing efficient backup and synchronization capabilities through several core components:

- **Content-Addressable Storage**: Files are broken into chunks and stored by their content hash
- **BLAKE3 Hashing**: Fast cryptographic hashing with SIMD optimization
- **Network Protocols**: Support for TCP and QUIC transports
- **SQLite Metadata**: Efficient metadata management with SQLite database
- **Chunking System**: Configurable chunk sizes for optimal performance

## Database Schema

JustSyncIt uses SQLite as its metadata storage backend, providing efficient querying and ACID compliance for backup operations.

### Core Tables

#### snapshots
Stores metadata for backup snapshots:

```sql
CREATE TABLE snapshots (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    created_at INTEGER NOT NULL,
    file_count INTEGER NOT NULL,
    total_size INTEGER NOT NULL,
    chunk_count INTEGER NOT NULL
);
```

**Fields:**
- `id`: Unique snapshot identifier (BLAKE3 hash)
- `name`: Human-readable snapshot name
- `description`: Optional snapshot description
- `created_at`: Unix timestamp of creation
- `file_count`: Total number of files in snapshot
- `total_size`: Total size in bytes
- `chunk_count`: Number of unique chunks

#### files
Stores file metadata for each snapshot:

```sql
CREATE TABLE files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_id TEXT NOT NULL,
    path TEXT NOT NULL,
    size INTEGER NOT NULL,
    modified_time INTEGER NOT NULL,
    file_hash TEXT NOT NULL,
    is_symlink BOOLEAN DEFAULT FALSE,
    symlink_target TEXT,
    FOREIGN KEY (snapshot_id) REFERENCES snapshots(id)
);
```

**Fields:**
- `id`: Auto-incrementing primary key
- `snapshot_id`: Reference to parent snapshot
- `path`: Relative file path within snapshot
- `size`: File size in bytes
- `modified_time`: Last modification timestamp
- `file_hash`: BLAKE3 hash of file content
- `is_symlink`: Boolean flag for symbolic links
- `symlink_target`: Target path for symbolic links

#### chunks
Stores chunk metadata and references:

```sql
CREATE TABLE chunks (
    hash TEXT PRIMARY KEY,
    size INTEGER NOT NULL,
    reference_count INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL
);
```

**Fields:**
- `hash`: BLAKE3 hash of chunk content
- `size`: Chunk size in bytes
- `reference_count`: Number of files referencing this chunk
- `created_at`: Unix timestamp when chunk was first stored

#### file_chunks
Maps files to their constituent chunks:

```sql
CREATE TABLE file_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    chunk_hash TEXT NOT NULL,
    chunk_order INTEGER NOT NULL,
    FOREIGN KEY (file_id) REFERENCES files(id),
    FOREIGN KEY (chunk_hash) REFERENCES chunks(hash)
);
```

**Fields:**
- `id`: Auto-incrementing primary key
- `file_id`: Reference to parent file
- `chunk_hash`: Hash of the chunk
- `chunk_order`: Order of chunk within file (0-based)

### Indexes

Optimized indexes for common query patterns:

```sql
-- Snapshot lookup indexes
CREATE INDEX idx_snapshots_created_at ON snapshots(created_at);
CREATE INDEX idx_snapshots_name ON snapshots(name);

-- File lookup indexes
CREATE INDEX idx_files_snapshot_id ON files(snapshot_id);
CREATE INDEX idx_files_path ON files(path);
CREATE INDEX idx_files_file_hash ON files(file_hash);

-- Chunk reference indexes
CREATE INDEX idx_chunks_reference_count ON chunks(reference_count);
CREATE INDEX idx_chunks_created_at ON chunks(created_at);

-- File-chunk mapping indexes
CREATE INDEX idx_file_chunks_file_id ON file_chunks(file_id);
CREATE INDEX idx_file_chunks_chunk_hash ON file_chunks(chunk_hash);
CREATE INDEX idx_file_chunks_order ON file_chunks(file_id, chunk_order);
```

### Database Operations

#### Connection Management
```java
// Database connection with connection pooling
public class DatabaseManager {
    private final DataSource dataSource;
    
    public DatabaseManager(String dbPath) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(10);
        this.dataSource = new HikariDataSource(config);
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
```

#### Transaction Management
```java
// Example transaction for snapshot creation
public void createSnapshot(Snapshot snapshot, List<FileMetadata> files) {
    try (Connection conn = getConnection()) {
        conn.setAutoCommit(false);
        
        try {
            // Insert snapshot metadata
            insertSnapshot(conn, snapshot);
            
            // Insert file metadata
            for (FileMetadata file : files) {
                insertFile(conn, snapshot.getId(), file);
            }
            
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new StorageException("Failed to create snapshot", e);
        }
    }
}
```

#### Query Optimization
```java
// Efficient chunk deduplication query
public List<String> findExistingChunks(List<String> chunkHashes) {
    String sql = "SELECT hash FROM chunks WHERE hash IN (" + 
                 String.join(",", Collections.nCopies(chunkHashes.size(), "?")) + ")";
    
    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        
        for (int i = 0; i < chunkHashes.size(); i++) {
            stmt.setString(i + 1, chunkHashes.get(i));
        }
        
        ResultSet rs = stmt.executeQuery();
        List<String> existing = new ArrayList<>();
        while (rs.next()) {
            existing.add(rs.getString("hash"));
        }
        return existing;
    }
}
```

### Performance Considerations

- **Batch Operations**: Use batch inserts for multiple files/chunks
- **Connection Pooling**: Reuse connections to reduce overhead
- **Prepared Statements**: Cache prepared statements for repeated queries
- **Index Strategy**: Optimize indexes based on query patterns
- **Vacuum Strategy**: Regular database vacuuming to maintain performance

## Filesystem Scanner Architecture

The filesystem scanner is responsible for traversing directories, identifying files, and preparing them for chunking and backup.

### Core Components

#### FileVisitor Interface
```java
public interface FileVisitor {
    void visitFile(Path path, BasicFileAttributes attrs);
    void visitDirectory(Path path, BasicFileAttributes attrs);
    void visitSymlink(Path path, BasicFileAttributes attrs);
    void visitFailed(Path path, IOException exc);
}
```

#### NioFilesystemScanner Implementation
```java
public class NioFilesystemScanner implements FilesystemScanner {
    private final FileVisitor visitor;
    private final ScanOptions options;
    
    @Override
    public ScanResult scan(Path rootPath) {
        try {
            Files.walkFileTree(rootPath, createFileVisitor());
            return buildResult();
        } catch (IOException e) {
            throw new ScannerException("Failed to scan filesystem", e);
        }
    }
    
    private SimpleFileVisitor<Path> createFileVisitor() {
        return new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (shouldIncludeFile(file)) {
                    visitor.visitFile(file, attrs);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldIncludeDirectory(dir)) {
                    visitor.visitDirectory(dir, attrs);
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        };
    }
}
```

### Filtering and Selection

#### Path Filtering
```java
public class PathFilter {
    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;
    
    public boolean shouldInclude(Path path) {
        String pathStr = path.toString();
        
        // Check exclusions first
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(pathStr).matches()) {
                return false;
            }
        }
        
        // Check inclusions
        if (includePatterns.isEmpty()) {
            return true; // Include everything if no includes specified
        }
        
        for (Pattern pattern : includePatterns) {
            if (pattern.matcher(pathStr).matches()) {
                return true;
            }
        }
        
        return false;
    }
}
```

#### Symlink Handling
```java
public enum SymlinkStrategy {
    FOLLOW,     // Follow symbolic links
    SKIP,       // Skip symbolic links entirely
    PRESERVE    // Preserve as symbolic links
}

public class SymlinkHandler {
    private final SymlinkStrategy strategy;
    
    public void handleSymlink(Path symlink, FileVisitor visitor) {
        switch (strategy) {
            case FOLLOW:
                handleFollowSymlink(symlink, visitor);
                break;
            case SKIP:
                // Skip the symlink entirely
                break;
            case PRESERVE:
                handlePreserveSymlink(symlink, visitor);
                break;
        }
    }
    
    private void handleFollowSymlink(Path symlink, FileVisitor visitor) {
        try {
            Path target = Files.readSymbolicLink(symlink);
            if (Files.exists(target)) {
                // Visit the target instead
                Files.walkFileTree(target, createFileVisitor());
            }
        } catch (IOException e) {
            visitor.visitFailed(symlink, e);
        }
    }
}
```

### File Processing Pipeline

#### FileProcessor
```java
public class FileProcessor {
    private final FileChunker chunker;
    private final HashService hashService;
    
    public FileMetadata processFile(Path filePath) {
        try {
            // Get file attributes
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            
            // Create file metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setPath(filePath);
            metadata.setSize(attrs.size());
            metadata.setModifiedTime(attrs.lastModifiedTime().toMillis());
            
            // Process file content
            if (attrs.isRegularFile()) {
                processFileContent(filePath, metadata);
            } else if (attrs.isSymbolicLink()) {
                processSymlink(filePath, metadata);
            }
            
            return metadata;
        } catch (IOException e) {
            throw new FileProcessingException("Failed to process file: " + filePath, e);
        }
    }
    
    private void processFileContent(Path filePath, FileMetadata metadata) {
        try (InputStream in = Files.newInputStream(filePath)) {
            // Chunk the file
            List<Chunk> chunks = chunker.chunkFile(in);
            metadata.setChunks(chunks);
            
            // Calculate file hash
            String fileHash = hashService.hashFile(filePath);
            metadata.setHash(fileHash);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to process file content", e);
        }
    }
}
```

### Performance Optimizations

#### Parallel Processing
```java
public class ParallelFileProcessor {
    private final ExecutorService executor;
    private final int parallelism;
    
    public ScanResult processFiles(List<Path> files) {
        List<Future<FileMetadata>> futures = new ArrayList<>();
        
        for (Path file : files) {
            futures.add(executor.submit(() -> processFile(file)));
        }
        
        List<FileMetadata> results = new ArrayList<>();
        for (Future<FileMetadata> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                throw new FileProcessingException("Failed to process file", e);
            }
        }
        
        return new ScanResult(results);
    }
}
```

#### Memory Management
```java
public class MemoryEfficientScanner {
    private final int bufferSize;
    private final ByteBufferPool bufferPool;
    
    public void scanLargeFile(Path largeFile) {
        try (InputStream in = Files.newInputStream(largeFile);
             ByteBuffer buffer = bufferPool.acquire()) {
            
            byte[] chunkBuffer = new byte[bufferSize];
            int bytesRead;
            
            while ((bytesRead = in.read(chunkBuffer)) != -1) {
                // Process chunk without loading entire file into memory
                processChunk(chunkBuffer, bytesRead);
            }
        } catch (IOException e) {
            throw new ScannerException("Failed to scan large file", e);
        }
    }
}
```

## Network Protocol

JustSyncIt implements a binary protocol for efficient network communication between clients and servers, supporting both TCP and QUIC transports.

### Protocol Overview

The protocol is message-based with the following characteristics:
- **Binary Format**: Compact binary messages for efficiency
- **Message Types**: Typed messages with specific purposes
- **Chunked Transfer**: Large data transferred in chunks
- **Integrity Verification**: Built-in checksums for message integrity
- **Flow Control**: Backpressure management for large transfers

### Message Format

#### Protocol Header
```
+--------+--------+--------+--------+--------+--------+--------+--------+
| Magic (4 bytes) | Version (1 byte) | Type (1 byte) | Flags (2 bytes) |
+--------+--------+--------+--------+--------+--------+--------+--------+
| Length (4 bytes) | Checksum (4 bytes) | Message ID (8 bytes)     |
+--------+--------+--------+--------+--------+--------+--------+--------+
```

**Fields:**
- `Magic`: Protocol identifier (0x4A535454 - "JSTT")
- `Version`: Protocol version (current: 1)
- `Type`: Message type identifier
- `Flags`: Message flags (compression, encryption, etc.)
- `Length`: Message payload length
- `Checksum`: CRC32 checksum of payload
- `Message ID`: Unique message identifier for request/response correlation

#### Message Types
```java
public enum MessageType {
    // Connection management
    HANDSHAKE(0x01),
    HANDSHAKE_RESPONSE(0x02),
    PING(0x03),
    PONG(0x04),
    
    // File transfer operations
    FILE_TRANSFER_REQUEST(0x10),
    FILE_TRANSFER_RESPONSE(0x11),
    CHUNK_DATA(0x12),
    CHUNK_ACK(0x13),
    TRANSFER_COMPLETE(0x14),
    
    // Error handling
    ERROR(0xFF);
}
```

### Connection Establishment

#### Handshake Process
```java
public class HandshakeMessage extends AbstractProtocolMessage {
    private final String clientVersion;
    private final String[] supportedProtocols;
    private final Map<String, String> capabilities;
    
    @Override
    protected void serializePayload(ByteBuffer buffer) {
        writeString(buffer, clientVersion);
        writeStringArray(buffer, supportedProtocols);
        writeStringMap(buffer, capabilities);
    }
    
    @Override
    protected void deserializePayload(ByteBuffer buffer) {
        this.clientVersion = readString(buffer);
        this.supportedProtocols = readStringArray(buffer);
        this.capabilities = readStringMap(buffer);
    }
}
```

#### Server Handshake Response
```java
public class HandshakeResponseMessage extends AbstractProtocolMessage {
    private final String serverVersion;
    private final String selectedProtocol;
    private final Map<String, String> serverCapabilities;
    private final boolean accepted;
    
    // Implementation similar to HandshakeMessage
}
```

### File Transfer Protocol

#### Transfer Request
```java
public class FileTransferRequestMessage extends AbstractProtocolMessage {
    private final String snapshotId;
    private final String filePath;
    private final long fileSize;
    private final String[] chunkHashes;
    private final TransferOptions options;
    
    @Override
    protected void serializePayload(ByteBuffer buffer) {
        writeString(buffer, snapshotId);
        writeString(buffer, filePath);
        writeLong(buffer, fileSize);
        writeStringArray(buffer, chunkHashes);
        writeTransferOptions(buffer, options);
    }
}
```

#### Chunk Data Transfer
```java
public class ChunkDataMessage extends AbstractProtocolMessage {
    private final String chunkHash;
    private final int chunkIndex;
    private final byte[] chunkData;
    private final boolean compressed;
    
    @Override
    protected void serializePayload(ByteBuffer buffer) {
        writeString(buffer, chunkHash);
        writeInt(buffer, chunkIndex);
        writeByteArray(buffer, chunkData);
        writeBoolean(buffer, compressed);
    }
}
```

#### Chunk Acknowledgment
```java
public class ChunkAckMessage extends AbstractProtocolMessage {
    private final String chunkHash;
    private final int chunkIndex;
    private final boolean received;
    private final String errorMessage;
    
    @Override
    protected void serializePayload(ByteBuffer buffer) {
        writeString(buffer, chunkHash);
        writeInt(buffer, chunkIndex);
        writeBoolean(buffer, received);
        writeString(buffer, errorMessage);
    }
}
```

### Protocol Implementation

#### Message Factory
```java
public class MessageFactory {
    private static final Map<MessageType, Supplier<ProtocolMessage>> MESSAGE_CREATORS;
    
    static {
        MESSAGE_CREATORS = Map.of(
            MessageType.HANDSHAKE, HandshakeMessage::new,
            MessageType.HANDSHAKE_RESPONSE, HandshakeResponseMessage::new,
            MessageType.FILE_TRANSFER_REQUEST, FileTransferRequestMessage::new,
            MessageType.FILE_TRANSFER_RESPONSE, FileTransferResponseMessage::new,
            MessageType.CHUNK_DATA, ChunkDataMessage::new,
            MessageType.CHUNK_ACK, ChunkAckMessage::new
        );
    }
    
    public static ProtocolMessage createMessage(MessageType type) {
        Supplier<ProtocolMessage> creator = MESSAGE_CREATORS.get(type);
        if (creator == null) {
            throw new ProtocolException("Unknown message type: " + type);
        }
        return creator.get();
    }
}
```

#### Protocol Handler
```java
public class ProtocolHandler {
    private final Map<MessageType, MessageHandler> handlers;
    
    public ProtocolHandler() {
        this.handlers = new HashMap<>();
        registerDefaultHandlers();
    }
    
    public void handleMessage(Connection connection, ProtocolMessage message) {
        MessageHandler handler = handlers.get(message.getType());
        if (handler == null) {
            sendErrorResponse(connection, "Unknown message type");
            return;
        }
        
        try {
            handler.handle(connection, message);
        } catch (Exception e) {
            sendErrorResponse(connection, "Error handling message: " + e.getMessage());
        }
    }
    
    private void registerDefaultHandlers() {
        handlers.put(MessageType.HANDSHAKE, this::handleHandshake);
        handlers.put(MessageType.FILE_TRANSFER_REQUEST, this::handleFileTransferRequest);
        handlers.put(MessageType.CHUNK_DATA, this::handleChunkData);
        handlers.put(MessageType.CHUNK_ACK, this::handleChunkAck);
    }
}
```

### Transport Layer

#### TCP Transport
```java
public class TcpTransport implements Transport {
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    
    @Override
    public void start(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        while (true) {
            selector.select();
            processSelectedKeys();
        }
    }
    
    private void processSelectedKeys() throws IOException {
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            keys.remove();
            
            if (key.isAcceptable()) {
                handleAccept(key);
            } else if (key.isReadable()) {
                handleRead(key);
            } else if (key.isWritable()) {
                handleWrite(key);
            }
        }
    }
}
```

#### QUIC Transport
```java
public class QuicTransport implements Transport {
    private final QuicServer quicServer;
    private final QuicConfiguration config;
    
    @Override
    public void start(int port) throws IOException {
        quicServer.start(new InetSocketAddress(port), config);
    }
    
    @Override
    public void send(Connection connection, ProtocolMessage message) {
        QuicConnection quicConn = (QuicConnection) connection;
        byte[] messageData = message.serialize();
        
        QuicStream stream = quicConn.createStream();
        stream.write(messageData);
        stream.close();
    }
}
```

## Storage Format

JustSyncIt uses a content-addressable storage system where data is identified and retrieved by its content hash, enabling automatic deduplication and integrity verification.

### Directory Structure

#### Storage Layout
```
.justsyncit/
├── data/                    # Content-addressable chunk storage
│   ├── ab/                  # First two characters of hash
│   │   └── abc123...        # Full hash as filename
│   ├── de/
│   │   └── def456...
│   └── ...
├── metadata/                # Metadata and indexes
│   ├── snapshots.db         # SQLite database
│   ├── chunks.index         # Chunk index file
│   └── files.index          # File index file
├── logs/                    # Log files
└── config/                  # Configuration files
```

#### Chunk Storage Format
```
data/
├── ab/
│   ├── abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
│   ├── abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567891
│   └── ...
├── cd/
│   └── cdef567890abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
└── ...
```

### Chunk Format

#### Chunk Header
```
+--------+--------+--------+--------+--------+--------+--------+--------+
| Magic (4 bytes) | Version (1 byte) | Compression (1 byte) | Flags (2 bytes) |
+--------+--------+--------+--------+--------+--------+--------+--------+
| Original Size (8 bytes) | Compressed Size (8 bytes) | Checksum (4 bytes) |
+--------+--------+--------+--------+--------+--------+--------+--------+
| BLAKE3 Hash (32 bytes) |
+--------+--------+--------+--------+--------+--------+--------+--------+
```

**Fields:**
- `Magic`: Chunk identifier (0x4A53434B - "JSCK")
- `Version`: Chunk format version (current: 1)
- `Compression`: Compression algorithm used (0=none, 1=lz4, 2=zstd)
- `Flags`: Chunk flags (encrypted, etc.)
- `Original Size`: Original uncompressed size
- `Compressed Size`: Compressed size (0 if uncompressed)
- `Checksum`: CRC32 checksum of data
- `BLAKE3 Hash`: BLAKE3 hash of original data

#### Chunk Data
```
+-----------------------+
| Compressed Data        |
| (variable length)      |
+-----------------------+
```

### Storage Implementation

#### Chunk Storage Interface
```java
public interface ChunkStorage {
    InputStream getChunk(String hash) throws StorageException;
    void storeChunk(String hash, InputStream data) throws StorageException;
    boolean hasChunk(String hash);
    void deleteChunk(String hash) throws StorageException;
    long getChunkSize(String hash);
}
```

#### Filesystem Chunk Storage
```java
public class FilesystemChunkStorage implements ChunkStorage {
    private final Path storageRoot;
    private final CompressionService compressionService;
    
    @Override
    public void storeChunk(String hash, InputStream data) throws StorageException {
        Path chunkPath = getChunkPath(hash);
        
        try {
            // Create parent directories if needed
            Files.createDirectories(chunkPath.getParent());
            
            // Read data and calculate hash
            byte[] chunkData = data.readAllBytes();
            String calculatedHash = hashService.hash(chunkData);
            
            if (!calculatedHash.equals(hash)) {
                throw new StorageException("Hash mismatch: expected " + hash + ", got " + calculatedHash);
            }
            
            // Compress if needed
            byte[] storedData = compressionService.compress(chunkData);
            
            // Write chunk with header
            try (OutputStream out = Files.newOutputStream(chunkPath)) {
                writeChunkHeader(out, chunkData.length, storedData.length, hash);
                out.write(storedData);
            }
            
        } catch (IOException e) {
            throw new StorageException("Failed to store chunk: " + hash, e);
        }
    }
    
    @Override
    public InputStream getChunk(String hash) throws StorageException {
        Path chunkPath = getChunkPath(hash);
        
        if (!Files.exists(chunkPath)) {
            throw new StorageException("Chunk not found: " + hash);
        }
        
        try {
            InputStream in = Files.newInputStream(chunkPath);
            
            // Read and verify header
            ChunkHeader header = readChunkHeader(in);
            
            if (!header.getHash().equals(hash)) {
                throw new StorageException("Chunk hash mismatch");
            }
            
            // Decompress if needed
            byte[] compressedData = in.readAllBytes();
            byte[] decompressedData = compressionService.decompress(compressedData);
            
            return new ByteArrayInputStream(decompressedData);
            
        } catch (IOException e) {
            throw new StorageException("Failed to read chunk: " + hash, e);
        }
    }
    
    private Path getChunkPath(String hash) {
        String prefix = hash.substring(0, 2);
        return storageRoot.resolve("data").resolve(prefix).resolve(hash);
    }
}
```

### Garbage Collection

#### Reference Tracking
```java
public class GarbageCollector {
    private final ChunkStorage chunkStorage;
    private final MetadataManager metadataManager;
    
    public void collectGarbage() {
        // Get all referenced chunks
        Set<String> referencedChunks = metadataManager.getReferencedChunks();
        
        // Get all stored chunks
        Set<String> storedChunks = chunkStorage.getAllChunkHashes();
        
        // Find unreferenced chunks
        Set<String> unreferencedChunks = new HashSet<>(storedChunks);
        unreferencedChunks.removeAll(referencedChunks);
        
        // Delete unreferenced chunks
        for (String chunkHash : unreferencedChunks) {
            try {
                chunkStorage.deleteChunk(chunkHash);
                logger.info("Deleted unreferenced chunk: " + chunkHash);
            } catch (StorageException e) {
                logger.error("Failed to delete chunk: " + chunkHash, e);
            }
        }
    }
}
```

### Integrity Verification

#### Chunk Verification
```java
public class IntegrityVerifier {
    private final ChunkStorage chunkStorage;
    private final HashService hashService;
    
    public VerificationResult verifyChunk(String expectedHash) {
        try (InputStream in = chunkStorage.getChunk(expectedHash)) {
            byte[] chunkData = in.readAllBytes();
            String actualHash = hashService.hash(chunkData);
            
            if (!expectedHash.equals(actualHash)) {
                return VerificationResult.failed("Hash mismatch: expected " + expectedHash + ", got " + actualHash);
            }
            
            return VerificationResult.success();
            
        } catch (Exception e) {
            return VerificationResult.failed("Verification failed: " + e.getMessage());
        }
    }
    
    public VerificationResult verifySnapshot(String snapshotId) {
        SnapshotMetadata snapshot = metadataManager.getSnapshot(snapshotId);
        List<String> failedChunks = new ArrayList<>();
        
        for (String chunkHash : snapshot.getChunkHashes()) {
            VerificationResult result = verifyChunk(chunkHash);
            if (!result.isSuccess()) {
                failedChunks.add(chunkHash);
            }
        }
        
        if (failedChunks.isEmpty()) {
            return VerificationResult.success();
        } else {
            return VerificationResult.failed("Failed chunks: " + String.join(", ", failedChunks));
        }
    }
}
```

## Performance Considerations

### I/O Optimization

#### Buffered Operations
```java
public class BufferedChunkReader implements ChunkReader {
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024; // 64KB
    
    @Override
    public void readChunk(String hash, ChunkHandler handler) {
        Path chunkPath = getChunkPath(hash);
        
        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(chunkPath), DEFAULT_BUFFER_SIZE)) {
            
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                handler.handleChunk(buffer, bytesRead);
            }
            
        } catch (IOException e) {
            throw new StorageException("Failed to read chunk: " + hash, e);
        }
    }
}
```

#### Memory Mapping
```java
public class MappedChunkReader implements ChunkReader {
    @Override
    public void readChunk(String hash, ChunkHandler handler) {
        Path chunkPath = getChunkPath(hash);
        
        try (FileChannel channel = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            
            handler.handleChunk(buffer);
            
        } catch (IOException e) {
            throw new StorageException("Failed to memory map chunk: " + hash, e);
        }
    }
}
```

### Concurrency

#### Thread Pool Configuration
```java
public class StorageExecutor {
    private final ExecutorService readExecutor;
    private final ExecutorService writeExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    
    public StorageExecutor(int readThreads, int writeThreads) {
        this.readExecutor = Executors.newFixedThreadPool(readThreads, 
            new ThreadFactoryBuilder().setNameFormat("storage-read-%d").build());
        
        this.writeExecutor = Executors.newFixedThreadPool(writeThreads,
            new ThreadFactoryBuilder().setNameFormat("storage-write-%d").build());
        
        this.maintenanceExecutor = Executors.newScheduledThreadPool(2,
            new ThreadFactoryBuilder().setNameFormat("storage-maint-%d").build());
    }
    
    public CompletableFuture<Void> readChunkAsync(String hash, ChunkHandler handler) {
        return CompletableFuture.runAsync(() -> {
            try {
                readChunk(hash, handler);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, readExecutor);
    }
}
```

## Security Architecture

### Hash-Based Security

JustSyncIt uses BLAKE3 as its primary hash function for several security-critical operations:

#### Content Integrity
- **File Verification**: Each file is hashed to detect corruption
- **Chunk Verification**: Each chunk is individually verified
- **Snapshot Verification**: Entire snapshots can be verified recursively

#### Deduplication Security
- **Collision Resistance**: BLAKE3 provides strong collision resistance
- **Pre-image Resistance**: Impossible to find data for a given hash
- **Second Pre-image Resistance**: Hard to find different data with same hash

#### Implementation
```java
public class Blake3HashService implements HashService {
    private final boolean useSIMD;
    
    public Blake3HashService() {
        this.useSIMD = SimdDetector.isBlake3SIMDSupported();
    }
    
    @Override
    public String hashFile(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            return hashStream(in);
        } catch (IOException e) {
            throw new HashException("Failed to hash file: " + filePath, e);
        }
    }
    
    @Override
    public String hashStream(InputStream in) {
        Blake3 hasher = useSIMD ? new Blake3SIMD() : new Blake3Portable();
        
        byte[] buffer = new byte[64 * 1024]; // 64KB buffer
        int bytesRead;
        
        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                hasher.update(buffer, 0, bytesRead);
            }
            return hasher.finalize();
        } catch (IOException e) {
            throw new HashException("Failed to hash stream", e);
        }
    }
}
```

### Access Control

#### File Permissions
```java
public class SecureFileAccess {
    private final Set<PosixFilePermission> securePermissions;
    
    public SecureFileAccess() {
        // Secure default permissions: owner read/write only
        this.securePermissions = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        );
    }
    
    public void secureFile(Path filePath) {
        try {
            Files.setPosixFilePermissions(filePath, securePermissions);
        } catch (IOException e) {
            throw new SecurityException("Failed to secure file: " + filePath, e);
        }
    }
    
    public void verifyFilePermissions(Path filePath) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(filePath);
            
            if (!permissions.equals(securePermissions)) {
                throw new SecurityException("Insecure file permissions on: " + filePath);
            }
        } catch (IOException e) {
            throw new SecurityException("Failed to check file permissions: " + filePath, e);
        }
    }
}
```

### Network Security

#### Transport Security
```java
public class SecureTransport implements Transport {
    private final SSLContext sslContext;
    private final boolean requireClientAuth;
    
    @Override
    public void start(int port) throws IOException {
        SSLServerSocket serverSocket = (SSLServerSocket) 
            sslContext.getServerSocketFactory().createServerSocket(port);
        
        if (requireClientAuth) {
            serverSocket.setNeedClientAuth(true);
        }
        
        // Accept secure connections
        while (true) {
            SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
            handleSecureConnection(clientSocket);
        }
    }
    
    private void handleSecureConnection(SSLSocket socket) {
        try {
            // Verify peer certificate
            SSLSession session = socket.getSession();
            verifyPeerCertificate(session);
            
            // Handle connection
            Connection connection = new SecureConnection(socket);
            connectionHandler.handle(connection);
            
        } catch (Exception e) {
            logger.error("Secure connection failed", e);
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
```

This technical guide provides comprehensive documentation of JustSyncIt's internal architecture, implementation details, and security considerations. It serves as a reference for developers who need to understand or extend the system's core functionality.