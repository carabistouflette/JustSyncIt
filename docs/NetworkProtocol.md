# JustSyncIt Network Protocol Specification

## Overview

The JustSyncIt network protocol enables reliable file transfers between nodes using TCP connections with binary message framing and BLAKE3 integrity verification.

## Protocol Version

- **Current Version**: 1.0
- **Magic Number**: 0x4A53544E ("JSTN" - JustSyncIt Network)

## Message Format

All messages follow the same binary format:

```
+------------------+------------------+------------------+
| Header (16 bytes) | Payload (variable) |
+------------------+------------------+
```

### Header Format

```
+------------------+------------------+------------------+
| Magic (4 bytes) | Version (2 bytes) | Type (1 byte) | Flags (1 byte) | Message ID (4 bytes) | Payload Length (4 bytes) |
+------------------+------------------+------------------+
```

- **Magic**: 0x4A53544E - Protocol identifier
- **Version**: Protocol version (current: 1)
- **Type**: Message type identifier
- **Flags**: Reserved for future use (currently all 0)
- **Message ID**: Unique message identifier for tracking
- **Payload Length**: Length of payload in bytes (max 1GB)

## Message Types

| Type | ID | Description |
|-------|-----|-------------|
| HANDSHAKE | 0x01 | Initial protocol handshake |
| HANDSHAKE_RESPONSE | 0x02 | Response to handshake |
| FILE_TRANSFER_REQUEST | 0x10 | Request to transfer file |
| FILE_TRANSFER_RESPONSE | 0x11 | Response to file transfer request |
| CHUNK_DATA | 0x12 | Chunk of file data |
| CHUNK_ACK | 0x13 | Acknowledgment of chunk receipt |
| TRANSFER_COMPLETE | 0x14 | File transfer completion |
| ERROR | 0x15 | Error message |
| PING | 0x20 | Connection health check |
| PONG | 0x21 | Response to ping |

## Message Definitions

### Handshake Message

Sent when establishing a new connection.

**Payload Format**:
```
+------------------+------------------+------------------+
| Node ID Length | Node ID (variable) |
+------------------+------------------+
| Version Length | Version (variable) |
+------------------+------------------+
```

**Fields**:
- `nodeId`: Unique identifier for the sending node
- `version`: Protocol version supported by the sender

### Handshake Response Message

Response to a handshake message.

**Payload Format**:
```
+------------------+------------------+------------------+
| Node ID Length | Node ID (variable) |
+------------------+------------------+
| Version Length | Version (variable) |
+------------------+------------------+
| Accepted (1 byte) | Reserved |
+------------------+------------------+
| Reason Length | Reason (variable) |
+------------------+------------------+
```

**Fields**:
- `nodeId`: Unique identifier for the responding node
- `version`: Protocol version supported by the responder
- `accepted`: true if handshake accepted, false otherwise
- `reason`: Reason for rejection (null if accepted)

### File Transfer Request Message

Request to transfer a file to the remote node.

**Payload Format**:
```
+------------------+------------------+------------------+
| Transfer ID Length | Transfer ID (variable) |
+------------------+------------------+
| Filename Length | Filename (variable) |
+------------------+------------------+
| File Size (8 bytes) | Chunk Size (4 bytes) |
+------------------+------------------+
```

**Fields**:
- `transferId`: Unique identifier for the transfer
- `fileName`: Name of the file to transfer
- `fileSize`: Total size of the file in bytes
- `chunkSize`: Preferred chunk size for the transfer

### File Transfer Response Message

Response to a file transfer request.

**Payload Format**:
```
+------------------+------------------+------------------+
| Transfer ID Length | Transfer ID (variable) |
+------------------+------------------+
| Accepted (1 byte) | Reserved |
+------------------+------------------+
| Reason Length | Reason (variable) |
+------------------+------------------+
```

**Fields**:
- `transferId`: Transfer ID from the request
- `accepted`: true if transfer accepted, false otherwise
- `reason`: Reason for rejection (null if accepted)

### Chunk Data Message

Contains a chunk of file data with integrity verification.

**Payload Format**:
```
+------------------+------------------+------------------+
| Transfer ID Length | Transfer ID (variable) |
+------------------+------------------+
| Chunk Index (4 bytes) | Checksum Length | Checksum (variable) |
+------------------+------------------+
| Chunk Data Length | Chunk Data (variable) |
+------------------+------------------+
```

**Fields**:
- `transferId`: Transfer identifier
- `chunkIndex`: Zero-based index of the chunk
- `checksum`: BLAKE3 hash of the chunk data
- `chunkData`: Raw chunk data

### Chunk Acknowledgment Message

Acknowledgment of chunk receipt and verification.

**Payload Format**:
```
+------------------+------------------+------------------+
| Transfer ID Length | Transfer ID (variable) |
+------------------+------------------+
| Chunk Index (4 bytes) | Success (1 byte) | Reserved |
+------------------+------------------+
| Error Length | Error Message (variable) |
+------------------+------------------+
```

**Fields**:
- `transferId`: Transfer identifier
- `chunkIndex`: Index of the acknowledged chunk
- `success`: true if chunk verified successfully, false otherwise
- `errorMessage`: Error description (null if successful)

### Transfer Complete Message

Signals completion of a file transfer.

**Payload Format**:
```
+------------------+------------------+------------------+
| Transfer ID Length | Transfer ID (variable) |
+------------------+------------------+
| Success (1 byte) | Reserved |
+------------------+------------------+
| Error Length | Error Message (variable) |
+------------------+------------------+
```

**Fields**:
- `transferId`: Transfer identifier
- `success`: true if transfer completed successfully, false otherwise
- `errorMessage`: Error description (null if successful)

### Error Message

General error reporting message.

**Payload Format**:
```
+------------------+------------------+------------------+
| Error Code (4 bytes) | Error Message Length | Error Message (variable) |
+------------------+------------------+
```

**Fields**:
- `errorCode`: Application-specific error code
- `errorMessage`: Human-readable error description

### Ping Message

Connection health check message.

**Payload Format**:
```
+------------------+------------------+------------------+
| Timestamp (8 bytes) | Reserved |
+------------------+------------------+
```

**Fields**:
- `timestamp`: Unix timestamp in milliseconds

### Pong Message

Response to a ping message.

**Payload Format**:
```
+------------------+------------------+------------------+
| Timestamp (8 bytes) | Reserved |
+------------------+------------------+
```

**Fields**:
- `timestamp`: Original timestamp from the ping message

## Connection Establishment

1. Client connects to server TCP port
2. Client sends HANDSHAKE message with node ID and version
3. Server responds with HANDSHAKE_RESPONSE
4. If accepted, connection is established for data exchange
5. If rejected, connection is closed

## File Transfer Flow

1. Sender sends FILE_TRANSFER_REQUEST with file metadata
2. Receiver responds with FILE_TRANSFER_RESPONSE
3. If accepted:
   - Sender splits file into chunks (default 64KB)
   - For each chunk:
     - Compute BLAKE3 checksum
     - Send CHUNK_DATA with checksum
     - Wait for CHUNK_ACK
     - If ACK indicates failure, retry chunk
   - After all chunks sent, send TRANSFER_COMPLETE
   - Wait for TRANSFER_COMPLETE acknowledgment

## Error Handling

### Error Codes

| Code | Description |
|-------|-------------|
| 1001 | Protocol version mismatch |
| 1002 | Invalid message format |
| 1003 | Unsupported message type |
| 1004 | Checksum verification failed |
| 1005 | Transfer cancelled |
| 1006 | Insufficient disk space |
| 1007 | Transfer timeout |
| 1008 | Connection lost |

## Performance Considerations

- **Chunk Size**: Default 64KB provides good balance between latency and throughput
- **Concurrent Chunks**: Up to 10 chunks per transfer for parallel processing
- **Buffer Sizes**: Socket buffers should be tuned for network conditions
- **Backoff Strategy**: Exponential backoff with 1.5x multiplier, 60s max delay

## Security Considerations

- **Integrity**: All chunks protected with BLAKE3 checksums
- **Authentication**: Node IDs should be validated against known peers
- **Authorization**: File transfer requests should be authorized
- **Rate Limiting**: Implement transfer rate limits to prevent abuse

## Implementation Notes

- Use non-blocking I/O with Java NIO for scalability
- Implement proper message framing to handle partial reads
- Use connection pooling to reduce overhead
- Implement graceful degradation for poor network conditions
- Log all protocol violations for debugging

## Version History

- **1.0**: Initial protocol version
  - Basic file transfer with chunking
  - BLAKE3 integrity verification
  - TCP transport with NIO
  - Connection management with reconnection