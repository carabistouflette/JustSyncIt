# JustSyncIt Technical Architecture

This document provides a deep dive into the technical implementation of JustSyncIt, covering its core algorithms, data structures, and network protocols.

## 1. Content-Defined Chunking: FastCDC

JustSyncIt utilizes the **FastCDC** (Fast Content-Defined Chunking) algorithm to break files into chunks. This allows for efficient deduplication based on content rather than file boundaries.

### Algorithm Details
*   **Gear Hash**: Uses a rolling hash (Gear Hashing) to scan byte streams.
*   **Cut Points**: A chunk boundary is declared when the hash value matches a specific mask.
*   **Normalization**: The algorithm normalizes chunk sizes to avoid extremely small or large chunks.

### Configuration
The chunking process is governed by three parameters:
*   `minSize`: The absolute minimum size of a chunk (default: 4KB).
*   `averageSize`: The target average size (default: 64KB).
*   `maxSize`: The absolute maximum size (default: 256KB).

This approach ensures that small changes in a file (e.g., inserting a byte at the beginning) only affect the surrounding chunks, preserving deduplication for the rest of the file.

## 2. Changed Block Tracking (CBT)

For rapid incremental backups, JustSyncIt implements a Changed Block Tracking system. This avoids the need to re-scan the entire filesystem for every backup.

### Modification Journal
*   **Persistence**: File changes are recorded in a persistent journal located at `~/.justsyncit/journal/`.
*   **Event Debouncing**: Rapid changes to the same file are coalesced into a single event (500ms window) to reduce noise.
*   **Replay**: On startup, the service replays the journal to rebuild its in-memory state of "dirty" files.

### Workflow
1.  **WatchService**: An async file watcher monitors registered directories.
2.  **Journaling**: Events (`ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`) are written to the journal.
3.  **Query**: When a backup starts, the `BackupService` queries the CBT service for files changed since the last snapshot.
4.  **Verification**: Only changed files are re-chunked and hashed.

## 3. Network Protocol

JustSyncIt uses a custom binary protocol for efficient file transfers and node communication.

### Transport Layer Abstraction
The system supports pluggable transport layers:
*   **TCP**: Standard reliable stream transport.
*   **QUIC**: UDP-based, multiplexed transport for high-latency or lossy networks.

### Protocol Structure
Communication is message-based. Each message consists of:
*   **Header**: Type, Length, Request ID.
*   **Payload**: The serialized data (Protocol Buffers or custom binary format).

### Backup Flow
1.  **Connect**: Client establishes connection to Server.
2.  **Handshake**: Version negotiation and authentication.
3.  **Transfer**: Chunks are streamed to the server. The server verifies hashes on the fly.
4.  **Finalize**: A manifest is sent to commit the snapshot.

## 4. Storage Engine

The storage backend is a Content-Addressable Store (CAS).

*   **Addressing**: Chunks are addressed by their BLAKE3 hash.
*   **Deduplication**: Identical chunks (same hash) are stored only once, regardless of how many files use them.
*   **Integrity**: Use of cryptographic hashes ensures that data corruption is detected immediately.
*   **Backend**: Currently supports local filesystem storage and experimental SQLite/Database backends.
